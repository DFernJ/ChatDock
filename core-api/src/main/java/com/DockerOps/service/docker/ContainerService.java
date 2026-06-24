package com.DockerOps.service.docker;

import com.DockerOps.dto.container.ContainerConfigDTO;
import com.DockerOps.dto.container.ContainerDTO;
import com.DockerOps.dto.container.ContainerStatsDTO;
import com.DockerOps.dto.container.ContainerVolumeDTO;
import com.DockerOps.dto.request.AssignContainerRequest;
import com.DockerOps.dto.request.CreateAppSecretRequest;
import com.DockerOps.dto.request.CreateStackRequest;
import com.DockerOps.dto.request.UpdateAppSecretRequest;
import com.DockerOps.dto.request.UpdateContainerRequest;
import com.DockerOps.dto.response.AppSecretResponse;
import com.DockerOps.dto.response.AppSummaryResponse;
import com.DockerOps.dto.response.StackResponse;
import com.DockerOps.model.apps.App;
import com.DockerOps.model.apps.AppSecret;
import com.DockerOps.model.apps.AppStack;
import com.DockerOps.model.users.User;
import com.DockerOps.repository.apps.AppRepository;
import com.DockerOps.repository.apps.AppSecretRepository;
import com.DockerOps.repository.apps.AppStackRepository;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Service
public class ContainerService {

    private static final String ESSENTIAL_CONTAINER_PREFIX = "essential-";

    @Autowired
    private DockerClient dockerClient;
    @Autowired
    private AppRepository appRepository;
    @Autowired
    private AppSecretRepository appSecretRepository;
    @Autowired
    private AppStackRepository appStackRepository;
    @Autowired
    private NetworkService networkService;

    public List<ContainerDTO> listContainers() {
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        List<ContainerDTO> containerResponse = new ArrayList<>();
        for (Container c : containers) {
            containerResponse.add(formatContainer(c));
        }
        return containerResponse;
    }

    public List<ContainerStatsDTO> listStats() {
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        List<CompletableFuture<ContainerStatsDTO>> futures = new ArrayList<>();
        for (Container c : containers) {
            if (!"running".equalsIgnoreCase(c.getState())) continue;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return getStats(c.getId());
                } catch (RuntimeException e) {
                    return null;
                }
            }));
        }
        List<ContainerStatsDTO> statsResponse = new ArrayList<>();
        for (CompletableFuture<ContainerStatsDTO> future : futures) {
            ContainerStatsDTO stats = future.join();
            if (stats != null) statsResponse.add(stats);
        }
        return statsResponse;
    }

    public ContainerStatsDTO getStats(String id) {
        Statistics[] result = new Statistics[1];
        Throwable[] error = new Throwable[1];
        CountDownLatch latch = new CountDownLatch(1);
        dockerClient.statsCmd(id)
                .withNoStream(true)  // snapshot único, no stream
                .exec(new ResultCallback.Adapter<>() {
                    @Override
                    public void onNext(Statistics stats) {
                        result[0] = stats;
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        error[0] = throwable;
                        latch.countDown();
                    }
                });

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for stats", e);
        }

        if (error[0] != null) {
            throw (error[0] instanceof RuntimeException re) ? re : new RuntimeException(error[0]);
        }
        return mapToDTO(id, result[0]);
    }

    public void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
    }

    public void restartContainer(String containerId) {
        dockerClient.restartContainerCmd(containerId).exec();
    }

    public void stopContainer(String containerId) {
        dockerClient.stopContainerCmd(containerId).exec();
    }

    public byte[] getLogs(String containerId) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Throwable[] error = new Throwable[1];
        CountDownLatch latch = new CountDownLatch(1);

        dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withTimestamps(true)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        try {
                            outputStream.write(frame.getPayload());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        error[0] = throwable;
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for logs", e);
        }

        if (error[0] != null) {
            throw (error[0] instanceof RuntimeException re) ? re : new RuntimeException(error[0]);
        }
        return outputStream.toByteArray();
    }

    public void deleteContainer(String containerId, boolean force, boolean removeVolumes) {
        dockerClient.removeContainerCmd(containerId)
                .withForce(force)
                .withRemoveVolumes(removeVolumes)
                .exec();
    }

    public int countContainers() {
        return dockerClient.listContainersCmd().withShowAll(true).exec().size();
    }

    public ContainerConfigDTO getContainerConfig(String id) {
        InspectContainerResponse inspect = dockerClient.inspectContainerCmd(id).exec();
        HostConfig hostConfig = inspect.getHostConfig();
        RestartPolicy restartPolicy = hostConfig.getRestartPolicy();
        List<String> networks = new ArrayList<>(inspect.getNetworkSettings().getNetworks().keySet());
        return new ContainerConfigDTO(
                id,
                hostConfig.getMemory(),
                hostConfig.getNanoCPUs(),
                restartPolicy != null ? restartPolicy.getName() : null,
                restartPolicy != null ? restartPolicy.getMaximumRetryCount() : null,
                networks,
                namedVolumeMounts(inspect)
        );
    }

    public ContainerDTO updateContainer(String id, UpdateContainerRequest req) {
        InspectContainerResponse inspect = dockerClient.inspectContainerCmd(id).exec();
        String name = inspect.getName().replace("/", "");
        if (name.startsWith(ESSENTIAL_CONTAINER_PREFIX)) {
            throw new IllegalArgumentException("Essential containers can't be edited");
        }

        RestartPolicy restartPolicy = req.restartPolicyName() != null
                ? buildRestartPolicy(req.restartPolicyName(), req.restartPolicyMaxRetryCount())
                : null;

        String currentId = id;
        if (req.volumes() != null && volumesDiffer(inspect, req.volumes())) {
            currentId = recreateWithVolumes(inspect, req, restartPolicy);
        } else {
            dockerClient.updateContainerCmd(id)
                    .withMemory(req.memoryBytes())
                    .withMemorySwap(req.memoryBytes() != null ? -1L : null)
                    .withNanoCPUs(req.nanoCPUs())
                    .withRestartPolicy(restartPolicy)
                    .exec();
        }

        if (req.networks() != null) {
            Set<String> currentNetworks = dockerClient.inspectContainerCmd(currentId).exec()
                    .getNetworkSettings().getNetworks().keySet();
            applyNetworkDiff(currentId, currentNetworks, req.networks());
        }

        String finalId = currentId;
        return listContainers().stream()
                .filter(c -> c.id().equals(finalId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Container not found after update"));
    }

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

    private List<ContainerVolumeDTO> namedVolumeMounts(InspectContainerResponse inspect) {
        List<ContainerVolumeDTO> volumes = new ArrayList<>();
        if (inspect.getMounts() == null) return volumes;
        for (InspectContainerResponse.Mount m : inspect.getMounts()) {
            if (m.getName() == null || m.getDestination() == null) continue;
            boolean readOnly = Boolean.FALSE.equals(m.getRW());
            volumes.add(new ContainerVolumeDTO(m.getName(), m.getDestination().getPath(), readOnly));
        }
        return volumes;
    }

    private boolean volumesDiffer(InspectContainerResponse inspect, List<ContainerVolumeDTO> desired) {
        Set<String> current = new HashSet<>();
        for (ContainerVolumeDTO v : namedVolumeMounts(inspect)) {
            current.add(v.volumeName() + "->" + v.target());
        }
        Set<String> requested = new HashSet<>();
        for (ContainerVolumeDTO v : desired) {
            requested.add(v.volumeName() + "->" + v.target());
        }
        return !current.equals(requested);
    }

    private RestartPolicy buildRestartPolicy(String name, Integer maxRetryCount) {
        return switch (name) {
            case "no" -> RestartPolicy.noRestart();
            case "always" -> RestartPolicy.alwaysRestart();
            case "unless-stopped" -> RestartPolicy.unlessStoppedRestart();
            case "on-failure" -> RestartPolicy.onFailureRestart(maxRetryCount != null ? maxRetryCount : 0);
            default -> throw new IllegalArgumentException("Unknown restart policy: " + name);
        };
    }

    private void applyNetworkDiff(String containerId, Set<String> current, List<String> desired) {
        Set<String> desiredSet = new HashSet<>(desired);
        for (String network : desiredSet) {
            if (!current.contains(network)) {
                networkService.connectContainer(network, containerId);
            }
        }
        for (String network : current) {
            if (!desiredSet.contains(network)) {
                networkService.disconnectContainer(network, containerId, true);
            }
        }
    }

    private String recreateWithVolumes(InspectContainerResponse inspect, UpdateContainerRequest req, RestartPolicy restartPolicy) {
        String originalId = inspect.getId();
        String originalName = inspect.getName().replace("/", "");
        boolean wasRunning = Boolean.TRUE.equals(inspect.getState().getRunning());

        List<Bind> binds = new ArrayList<>();
        if (inspect.getMounts() != null) {
            for (InspectContainerResponse.Mount m : inspect.getMounts()) {
                if (m.getName() != null || m.getSource() == null || m.getDestination() == null) continue;
                AccessMode accessMode = Boolean.FALSE.equals(m.getRW()) ? AccessMode.ro : AccessMode.rw;
                binds.add(new Bind(m.getSource(), new Volume(m.getDestination().getPath()), accessMode));
            }
        }
        for (ContainerVolumeDTO v : req.volumes()) {
            binds.add(new Bind(v.volumeName(), new Volume(v.target()), v.readOnly() ? AccessMode.ro : AccessMode.rw));
        }

        HostConfig hostConfig = inspect.getHostConfig().withBinds(binds.toArray(new Bind[0]));
        if (req.memoryBytes() != null) {
            hostConfig.withMemory(req.memoryBytes());
            hostConfig.withMemorySwap(-1L);
        }
        if (req.nanoCPUs() != null) hostConfig.withNanoCPUs(req.nanoCPUs());
        if (restartPolicy != null) hostConfig.withRestartPolicy(restartPolicy);

        if (wasRunning) {
            stopContainer(originalId);
        }

        String backupName = originalName + "__replaced__" + System.currentTimeMillis();
        dockerClient.renameContainerCmd(originalId).withName(backupName).exec();

        ContainerConfig config = inspect.getConfig();
        CreateContainerResponse created;
        try {
            CreateContainerCmd createCmd = dockerClient.createContainerCmd(config.getImage())
                    .withName(originalName)
                    .withHostConfig(hostConfig);
            if (config.getCmd() != null) createCmd = createCmd.withCmd(config.getCmd());
            if (config.getEntrypoint() != null) createCmd = createCmd.withEntrypoint(config.getEntrypoint());
            if (config.getEnv() != null) createCmd = createCmd.withEnv(config.getEnv());
            if (config.getLabels() != null) createCmd = createCmd.withLabels(config.getLabels());
            if (config.getWorkingDir() != null) createCmd = createCmd.withWorkingDir(config.getWorkingDir());
            if (config.getExposedPorts() != null) createCmd = createCmd.withExposedPorts(Arrays.asList(config.getExposedPorts()));
            created = createCmd.exec();
        } catch (RuntimeException e) {
            dockerClient.renameContainerCmd(originalId).withName(originalName).exec();
            throw e;
        }

        if (wasRunning) {
            dockerClient.startContainerCmd(created.getId()).exec();
        }
        dockerClient.removeContainerCmd(originalId).withForce(true).exec();
        return created.getId();
    }

    private ContainerDTO formatContainer(Container c) {
        String name = c.getNames()[0].replace("/", "");
        boolean essential = name.startsWith(ESSENTIAL_CONTAINER_PREFIX);
        String stackName = essential ? null : appRepository.findByContainerNameWithStack(name)
                .map(app -> app.getStack().getStackName())
                .orElse(null);
        return new ContainerDTO(
                c.getId(),
                name,
                c.getImage(),
                formatPorts(c.getPorts()),
                c.getStatus(),
                c.getState(),
                formatNetworks(c.getNetworkSettings().getNetworks().keySet().toArray(new String[0])),
                formatMounts(c.getMounts().toArray(new ContainerMount[0])),
                essential,
                stackName
        );
    }

    private List<String> formatPorts(ContainerPort[] ports) {
        List<String> responsePorts = new ArrayList<>();
        if (ports != null) {
            for (ContainerPort port : ports) {
                String formatted = String.format("%d:%d/%s", port.getPublicPort(), port.getPrivatePort(), port.getType());
                if (!responsePorts.contains(formatted)) {
                    responsePorts.add(formatted);
                }
            }
        }
        return responsePorts;
    }

    private List<String> formatNetworks(String[] networks) {
        List<String> responseNetworks = new ArrayList<>();
        if (networks != null) {
            for (String n : networks) {
                if (!responseNetworks.contains(n)) {
                    responseNetworks.add(n);
                }
            }
        }
        return responseNetworks;
    }

    private List<String> formatMounts(ContainerMount[] mounts) {
        List<String> responseMounts = new ArrayList<>();
        if (mounts != null) {
            for (ContainerMount m : mounts) {
                String formatted = String.format("mode:%s - %s:%s", m.getMode(), m.getSource(), m.getDestination());
                if (!responseMounts.contains(formatted)) {
                    responseMounts.add(formatted);
                }
            }
        }
        return responseMounts;
    }

    private ContainerStatsDTO mapToDTO(String id, Statistics s) {
        // CPU %
        long cpuDelta = s.getCpuStats().getCpuUsage().getTotalUsage()
                - s.getPreCpuStats().getCpuUsage().getTotalUsage();
        long systemDelta = s.getCpuStats().getSystemCpuUsage()
                - s.getPreCpuStats().getSystemCpuUsage();
        long numCpus = s.getCpuStats().getOnlineCpus();
        double cpuPercent = (cpuDelta / (double) systemDelta) * 100.0 * numCpus;

        // RAM %
        long used = s.getMemoryStats().getUsage();
        long limit = s.getMemoryStats().getLimit();
        double memPercent = (used / (double) limit) * 100.0;

        // Disco I/O (bytes leídos/escritos acumulados)
        long blkRead = 0, blkWrite = 0;
        for (var entry : s.getBlkioStats().getIoServiceBytesRecursive()) {
            if ("Read".equalsIgnoreCase(entry.getOp()))  blkRead  += entry.getValue();
            if ("Write".equalsIgnoreCase(entry.getOp())) blkWrite += entry.getValue();
        }
        return new ContainerStatsDTO(
                id,
                cpuPercent,
                Math.round(memPercent * 10.0) / 10.0,
                used, limit,
                blkRead, blkWrite,
                Instant.now()
        );
    }
}
