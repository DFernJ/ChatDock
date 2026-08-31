package com.DockerOps.service.apps;

import com.DockerOps.dto.request.CreateAppSecretRequest;
import com.DockerOps.dto.request.UpdateAppSecretRequest;
import com.DockerOps.dto.response.AppSecretResponse;
import com.DockerOps.model.apps.App;
import com.DockerOps.model.apps.AppSecret;
import com.DockerOps.repository.apps.AppRepository;
import com.DockerOps.repository.apps.AppSecretRepository;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AppSecretService {

    private static final String ESSENTIAL_CONTAINER_PREFIX = "essential-";

    @Autowired
    private DockerClient dockerClient;
    @Autowired
    private AppRepository appRepository;
    @Autowired
    private AppSecretRepository appSecretRepository;

    public List<AppSecretResponse> listSecrets(String containerId) {
        App app = resolveApp(containerId);
        return appSecretRepository.findByApp_Id(app.getId()).stream()
                .map(AppSecretResponse::from)
                .toList();
    }

    public AppSecretResponse createSecret(String containerId, CreateAppSecretRequest req) {
        App app = resolveApp(containerId);
        if (req.secretName() == null || req.secretName().isBlank()) {
            throw new IllegalArgumentException("Secret name is required");
        }
        if (req.secretValue() == null || req.secretValue().isBlank()) {
            throw new IllegalArgumentException("Secret value is required");
        }
        boolean exists = appSecretRepository.findByApp_Id(app.getId()).stream()
                .anyMatch(s -> s.getSecretName().equalsIgnoreCase(req.secretName()));
        if (exists) {
            throw new IllegalArgumentException("A secret named '" + req.secretName() + "' already exists for this container");
        }
        AppSecret secret = AppSecret.builder()
                .id(UUID.randomUUID())
                .secretName(req.secretName())
                .secretValue(req.secretValue())
                .app(app)
                .build();
        return AppSecretResponse.from(appSecretRepository.save(secret));
    }

    public AppSecretResponse updateSecret(String containerId, UUID secretId, UpdateAppSecretRequest req) {
        App app = resolveApp(containerId);
        if (req.secretValue() == null || req.secretValue().isBlank()) {
            throw new IllegalArgumentException("Secret value is required");
        }
        AppSecret secret = appSecretRepository.findById(secretId)
                .filter(s -> s.getApp().getId().equals(app.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Secret not found for this container"));
        secret.setSecretValue(req.secretValue());
        return AppSecretResponse.from(appSecretRepository.save(secret));
    }

    public void deleteSecret(String containerId, UUID secretId) {
        App app = resolveApp(containerId);
        AppSecret secret = appSecretRepository.findById(secretId)
                .filter(s -> s.getApp().getId().equals(app.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Secret not found for this container"));
        appSecretRepository.delete(secret);
    }

    public String getSecretValue(String containerId, UUID secretId) {
        App app = resolveApp(containerId);
        AppSecret secret = appSecretRepository.findById(secretId)
                .filter(s -> s.getApp().getId().equals(app.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Secret not found for this container"));
        return secret.getSecretValue();
    }

    private App resolveApp(String containerId) {
        InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
        String name = inspect.getName().replace("/", "");
        if (name.startsWith(ESSENTIAL_CONTAINER_PREFIX)) {
            throw new IllegalArgumentException("Essential containers don't have secrets");
        }
        return appRepository.findByContainerName(name)
                .orElseThrow(() -> new IllegalArgumentException("This container isn't linked to a managed app yet — secrets aren't available"));
    }
}
