package com.DockerOps.service.apps;

import com.DockerOps.dto.request.AssignContainerRequest;
import com.DockerOps.dto.request.CreateStackRequest;
import com.DockerOps.dto.response.AppSummaryResponse;
import com.DockerOps.dto.response.StackResponse;
import com.DockerOps.model.apps.App;
import com.DockerOps.model.apps.AppStack;
import com.DockerOps.model.users.User;
import com.DockerOps.repository.apps.AppRepository;
import com.DockerOps.repository.apps.AppStackRepository;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AppStackService {

    private static final String ESSENTIAL_CONTAINER_PREFIX = "essential-";

    @Autowired
    private DockerClient dockerClient;
    @Autowired
    private AppRepository appRepository;
    @Autowired
    private AppStackRepository appStackRepository;

    public List<StackResponse> listStacks() {
        return appStackRepository.findAll().stream()
                .map(stack -> StackResponse.from(stack, appRepository.findByStack_Id(stack.getId()).size()))
                .toList();
    }

    public StackResponse createStack(CreateStackRequest req, User owner) {
        if (req.stackName() == null || req.stackName().isBlank()) {
            throw new IllegalArgumentException("Stack name is required");
        }
        if (appStackRepository.findByStackNameIgnoreCase(req.stackName()).isPresent()) {
            throw new IllegalArgumentException("A stack named '" + req.stackName() + "' already exists");
        }
        AppStack stack = AppStack.builder()
                .stackName(req.stackName())
                .owner(owner)
                .build();
        return StackResponse.from(appStackRepository.save(stack), 0);
    }

    public StackResponse renameStack(UUID stackId, CreateStackRequest req) {
        if (req.stackName() == null || req.stackName().isBlank()) {
            throw new IllegalArgumentException("Stack name is required");
        }
        AppStack stack = appStackRepository.findById(stackId)
                .orElseThrow(() -> new IllegalArgumentException("Stack not found"));
        if (!stack.getStackName().equalsIgnoreCase(req.stackName())
                && appStackRepository.findByStackNameIgnoreCase(req.stackName()).isPresent()) {
            throw new IllegalArgumentException("A stack named '" + req.stackName() + "' already exists");
        }
        stack.setStackName(req.stackName());
        AppStack saved = appStackRepository.save(stack);
        return StackResponse.from(saved, appRepository.findByStack_Id(saved.getId()).size());
    }

    public void deleteStack(UUID stackId) {
        AppStack stack = appStackRepository.findById(stackId)
                .orElseThrow(() -> new IllegalArgumentException("Stack not found"));
        if (!appRepository.findByStack_Id(stackId).isEmpty()) {
            throw new IllegalArgumentException("Remove all apps from this stack before deleting it");
        }
        appStackRepository.delete(stack);
    }

    public List<AppSummaryResponse> listAppsForStack(UUID stackId) {
        return appRepository.findByStack_Id(stackId).stream()
                .map(AppSummaryResponse::from)
                .toList();
    }

    public void removeApp(UUID stackId, UUID appId) {
        App app = appRepository.findByIdAndStack_Id(appId, stackId)
                .orElseThrow(() -> new IllegalArgumentException("App not found in this stack"));
        appRepository.delete(app);
    }

    public void assignContainer(String containerId, AssignContainerRequest req, User owner) {
        InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
        String name = inspect.getName().replace("/", "");
        if (name.startsWith(ESSENTIAL_CONTAINER_PREFIX)) {
            throw new IllegalArgumentException("Essential containers can't be assigned to a stack");
        }
        if (appRepository.findByContainerName(name).isPresent()) {
            throw new IllegalArgumentException("This container is already assigned to a stack");
        }
        if (req.appName() == null || req.appName().isBlank()) {
            throw new IllegalArgumentException("App name is required");
        }
        if (appRepository.existsByName(req.appName())) {
            throw new IllegalArgumentException("An app named '" + req.appName() + "' already exists");
        }
        if (req.stackName() == null || req.stackName().isBlank()) {
            throw new IllegalArgumentException("Stack name is required");
        }
        AppStack stack = appStackRepository.findByStackNameIgnoreCase(req.stackName())
                .orElseGet(() -> appStackRepository.save(
                        AppStack.builder().stackName(req.stackName()).owner(owner).build()
                ));
        App app = App.builder()
                .id(UUID.randomUUID())
                .name(req.appName())
                .containerName(name)
                .stack(stack)
                .appOwner(owner)
                .build();
        appRepository.save(app);
    }
}
