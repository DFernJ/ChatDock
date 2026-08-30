package com.DockerOps.service.docker;

import com.DockerOps.dto.container.ContainerVolumeDTO;
import com.DockerOps.dto.request.ComposeServiceOverride;
import com.DockerOps.dto.request.CreateContainerRequest;
import com.DockerOps.dto.request.DeployComposeRequest;
import com.DockerOps.dto.request.PseudoDockerfileRequest;
import com.DockerOps.dto.request.SecretDraftDTO;
import com.DockerOps.dto.response.ComposeDeployResultDTO;
import com.DockerOps.dto.response.ComposeServiceDTO;
import com.DockerOps.dto.response.ImportResultDTO;
import com.DockerOps.model.users.User;
import com.DockerOps.repository.apps.AppStackRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.util.CompressArchiveUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ImportService {

    private static final Pattern UPLOAD_ID_PATTERN = Pattern.compile("^[0-9a-fA-F-]{36}$");
    private static final Pattern PROJECT_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9-]+$");
    private static final Pattern REPOSITORY_PATTERN = Pattern.compile("^[\\w.-]+/[\\w.-]+$");

    @Autowired
    private DockerClient dockerClient;
    @Autowired
    private ComposeService composeService;
    @Autowired
    private ContainerService containerService;
    @Autowired
    private NetworkService networkService;
    @Autowired
    private VolumeService volumeService;
    @Autowired
    private AppStackRepository appStackRepository;

    @Value("${app.import.image}")
    private String importerImage;
    @Value("${app.import.max-zip-bytes}")
    private long maxZipBytes;
    @Value("${app.import.sandbox-wait-seconds}")
    private long sandboxWaitSeconds;
    @Value("${app.import.build-timeout-seconds}")
    private long buildTimeoutSeconds;
    @Value("${app.import.image-build-timeout-seconds}")
    private long importerBuildTimeoutSeconds;
    @Value("${app.import.sandbox-memory-bytes}")
    private long sandboxMemoryBytes;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void storeChunk(String uploadId, int chunkIndex, InputStream body) {
        Path dir = uploadDir(uploadId);
        try {
            Files.createDirectories(dir);
            Files.copy(body, dir.resolve(chunkIndex + ".part"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ImportResultDTO completeZipImport(String uploadId, int totalChunks) {
        log.info("[import {}] starting, totalChunks={}", uploadId, totalChunks);
        Path dir = uploadDir(uploadId);
        Path zipPath = dir.resolve("input.zip");
        String containerId = null;
        boolean keepUploadDir = false;
        try {
            reassembleChunks(dir, zipPath, totalChunks);
            log.info("[import {}] chunks reassembled into {}", uploadId, zipPath);

            ensureImporterImageBuilt();
            log.info("[import {}] importer image ready", uploadId);

            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withNetworkMode("none")
                    .withMemory(sandboxMemoryBytes)
                    .withPidsLimit(256L)
                    .withCapDrop(Capability.ALL)
                    .withSecurityOpts(List.of("no-new-privileges"));

            CreateContainerResponse created = dockerClient.createContainerCmd(importerImage)
                    .withHostConfig(hostConfig)
                    .withCmd("sleep", String.valueOf(sandboxWaitSeconds + 30))
                    .exec();
            containerId = created.getId();
            log.info("[import {}] sandbox container created: {}", uploadId, containerId);
            dockerClient.startContainerCmd(containerId).exec();
            log.info("[import {}] sandbox container started", uploadId);

            Path tarPath = dir.resolve("input.tar");
            try {
                CompressArchiveUtil.tar(zipPath, tarPath, false, false);
                log.info("[import {}] tar built at {} ({} bytes), copying into container…", uploadId, tarPath, Files.size(tarPath));
                try (InputStream tarStream = Files.newInputStream(tarPath)) {
                    dockerClient.copyArchiveToContainerCmd(containerId)
                            .withTarInputStream(tarStream)
                            .withRemotePath("/work")
                            .exec();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            log.info("[import {}] input.zip copied into container", uploadId);

            String execId = dockerClient.execCreateCmd(containerId)
                    .withCmd("python3", "/import.py", "zip", "/work/input.zip", "/work/out")
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec()
                    .getId();
            log.info("[import {}] exec created: {}", uploadId, execId);
            String execOutput = readExecOutput(execId);
            log.info("[import {}] exec finished, output={}", uploadId, execOutput.trim());
            Long exitCode = dockerClient.inspectExecCmd(execId).exec().getExitCodeLong();
            log.info("[import {}] exec exit code={}", uploadId, exitCode);
            if (exitCode == null || exitCode != 0) {
                throw new IllegalArgumentException("Could not scan the uploaded file: " + execOutput.trim());
            }

            JsonNode manifest = readManifest(containerId);
            ManifestOutcome outcome = resolveManifestOutcome(uploadId, dir, containerId, manifest);
            keepUploadDir = outcome.keepDir();
            if (keepUploadDir) {
                try {
                    Files.deleteIfExists(zipPath);
                    Files.deleteIfExists(dir.resolve("input.tar"));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return outcome.result();
        } catch (RuntimeException e) {
            log.error("[import {}] failed", uploadId, e);
            throw e;
        } finally {
            if (containerId != null) {
                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            }
            if (!keepUploadDir) {
                deleteRecursively(dir);
            }
            log.info("[import {}] cleanup done", uploadId);
        }
    }

    public ImportResultDTO buildFromPseudoDockerfile(String uploadId, PseudoDockerfileRequest req) {
        Path dir = uploadDir(uploadId);
        Path contextDir = dir.resolve("context");
        if (!Files.isDirectory(contextDir)) {
            throw new IllegalArgumentException("Upload expired or already used — re-upload the zip.");
        }
        if (req.baseImage() == null || req.baseImage().isBlank()) {
            throw new IllegalArgumentException("Base image is required");
        }
        if (req.runCommand() == null || req.runCommand().isBlank()) {
            throw new IllegalArgumentException("Run command is required");
        }
        String workdir = req.workdir() != null && !req.workdir().isBlank() ? req.workdir().trim() : "/app";

        StringBuilder dockerfile = new StringBuilder();
        dockerfile.append("FROM ").append(req.baseImage().trim()).append('\n');
        dockerfile.append("WORKDIR ").append(workdir).append('\n');
        dockerfile.append("COPY . .\n");
        if (req.buildCommand() != null && !req.buildCommand().isBlank()) {
            dockerfile.append("RUN ").append(req.buildCommand().trim()).append('\n');
        }
        dockerfile.append("CMD [\"sh\", \"-c\", \"").append(jsonEscape(req.runCommand().trim())).append("\"]\n");

        try {
            Files.writeString(contextDir.resolve("Dockerfile"), dockerfile.toString());

            Path tarPath = dir.resolve("build.tar");
            String tag = "chatops/import-" + UUID.randomUUID().toString().substring(0, 8) + ":latest";
            try {
                CompressArchiveUtil.tar(contextDir, tarPath, false, true);
                try (InputStream tarStream = Files.newInputStream(tarPath)) {
                    dockerClient.buildImageCmd()
                            .withTarInputStream(tarStream)
                            .withTags(Set.of(tag))
                            .exec(new BuildImageResultCallback())
                            .awaitImageId(buildTimeoutSeconds, TimeUnit.SECONDS);
                }
            } finally {
                Files.deleteIfExists(tarPath);
            }
            return new ImportResultDTO("dockerfile", tag, null, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteRecursively(dir);
        }
    }

    private record ManifestOutcome(ImportResultDTO result, boolean keepDir) {}

    private ManifestOutcome resolveManifestOutcome(String logId, Path dir, String containerId, JsonNode manifest) {
        String kind = manifest.path("kind").asText("none");
        log.info("[import {}] manifest kind={}", logId, kind);

        if ("dockerfile".equals(kind)) {
            String tag = "chatops/import-" + UUID.randomUUID().toString().substring(0, 8) + ":latest";
            try (InputStream contextTar = dockerClient.copyArchiveFromContainerCmd(containerId, "/work/out/context/.").exec()) {
                log.info("[import {}] building image {}", logId, tag);
                dockerClient.buildImageCmd()
                        .withTarInputStream(contextTar)
                        .withTags(Set.of(tag))
                        .exec(new BuildImageResultCallback())
                        .awaitImageId(buildTimeoutSeconds, TimeUnit.SECONDS);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            log.info("[import {}] image built: {}", logId, tag);
            return new ManifestOutcome(new ImportResultDTO("dockerfile", tag, null, null), false);
        }

        Path contextDir = dir.resolve("context");
        try (InputStream contextTar = dockerClient.copyArchiveFromContainerCmd(containerId, "/work/out/context/.").exec()) {
            extractTarToDirectory(contextTar, contextDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if ("compose".equals(kind)) {
            Path composeFile = findComposeFile(contextDir);
            List<ComposeServiceDTO> services = composeService.parse(composeFile, contextDir);
            return new ManifestOutcome(new ImportResultDTO("compose", null, "Review the services below before deploying.", services), true);
        }
        return new ManifestOutcome(new ImportResultDTO("none", null, "No Dockerfile or docker-compose.yml found at the project root. Fill in the details below to build one.", null), true);
    }

    public ImportResultDTO startGitImport(String importId, String repository, String ref, User owner) {
        if (repository == null || !REPOSITORY_PATTERN.matcher(repository).matches()) {
            throw new IllegalArgumentException("Invalid repository (expected format: owner/repo)");
        }
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("Branch is required");
        }
        if (owner.getGithubAccessToken() == null) {
            throw new IllegalArgumentException("Link your GitHub account in your profile first.");
        }
        log.info("[import {}] starting git clone repository={} ref={}", importId, repository, ref);
        Path dir = uploadDir(importId);
        String containerId = null;
        boolean keepUploadDir = false;
        try {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            ensureImporterImageBuilt();
            log.info("[import {}] importer image ready", importId);

            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withMemory(sandboxMemoryBytes)
                    .withPidsLimit(256L)
                    .withCapDrop(Capability.ALL)
                    .withSecurityOpts(List.of("no-new-privileges"));

            CreateContainerResponse created = dockerClient.createContainerCmd(importerImage)
                    .withHostConfig(hostConfig)
                    .withCmd("sleep", String.valueOf(sandboxWaitSeconds + 30))
                    .exec();
            containerId = created.getId();
            log.info("[import {}] sandbox container created: {}", importId, containerId);
            dockerClient.startContainerCmd(containerId).exec();
            log.info("[import {}] sandbox container started", importId);

            String execId = dockerClient.execCreateCmd(containerId)
                    .withCmd("python3", "/import.py", "git", repository, ref, "/work/out")
                    .withEnv(List.of("GIT_TOKEN=" + owner.getGithubAccessToken()))
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec()
                    .getId();
            log.info("[import {}] exec created: {}", importId, execId);
            String execOutput = readExecOutput(execId);
            log.info("[import {}] exec finished, output={}", importId, execOutput.trim());
            Long exitCode = dockerClient.inspectExecCmd(execId).exec().getExitCodeLong();
            log.info("[import {}] exec exit code={}", importId, exitCode);
            if (exitCode == null || exitCode != 0) {
                throw new IllegalArgumentException("Could not clone the repository: " + execOutput.trim());
            }

            JsonNode manifest = readManifest(containerId);
            ManifestOutcome outcome = resolveManifestOutcome(importId, dir, containerId, manifest);
            keepUploadDir = outcome.keepDir();
            return outcome.result();
        } catch (RuntimeException e) {
            log.error("[import {}] failed", importId, e);
            throw e;
        } finally {
            if (containerId != null) {
                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            }
            if (!keepUploadDir) {
                deleteRecursively(dir);
            }
            log.info("[import {}] cleanup done", importId);
        }
    }

    private Path findComposeFile(Path contextDir) {
        Path yml = contextDir.resolve("docker-compose.yml");
        if (Files.exists(yml)) return yml;
        Path yaml = contextDir.resolve("docker-compose.yaml");
        if (Files.exists(yaml)) return yaml;
        throw new IllegalArgumentException("docker-compose.yml not found");
    }

    public ComposeDeployResultDTO deployCompose(String uploadId, DeployComposeRequest req, User owner) {
        Path dir = uploadDir(uploadId);
        Path contextDir = dir.resolve("context");
        try {
            if (!Files.isDirectory(contextDir)) {
                throw new IllegalArgumentException("Upload expired or already used — re-upload the zip.");
            }
            String projectName = req.projectName() != null ? req.projectName().trim() : "";
            if (projectName.isEmpty() || !PROJECT_NAME_PATTERN.matcher(projectName).matches()) {
                throw new IllegalArgumentException("Project name can only contain letters, numbers and hyphens");
            }
            if (appStackRepository.findByStackNameIgnoreCase(projectName).isPresent()) {
                throw new IllegalArgumentException("A stack named '" + projectName + "' already exists");
            }

            List<ComposeServiceDTO> allServices = composeService.parse(findComposeFile(contextDir), contextDir);
            Map<String, ComposeServiceDTO> byName = new HashMap<>();
            for (ComposeServiceDTO s : allServices) byName.put(s.name(), s);

            Set<String> included = req.services() != null ? req.services().keySet() : Set.of();
            if (included.isEmpty()) {
                throw new IllegalArgumentException("Select at least one service to deploy");
            }
            for (String name : included) {
                ComposeServiceDTO service = byName.get(name);
                if (service == null) {
                    throw new IllegalArgumentException("Unknown service: " + name);
                }
                if (!service.supported()) {
                    throw new IllegalArgumentException("Service '" + name + "' isn't supported: " + service.unsupportedReason());
                }
            }
            composeService.validateIncluded(allServices, included);
            List<String> order = composeService.topologicalOrder(allServices, included);

            String networkName = projectName + "-net";
            networkService.createNetwork(networkName, "bridge");

            Set<String> volumeNames = new HashSet<>();
            for (String name : included) {
                for (ContainerVolumeDTO v : byName.get(name).volumes()) volumeNames.add(v.volumeName());
            }
            for (String volumeName : volumeNames) {
                volumeService.createVolume(volumeName, "local");
            }

            List<String> createdContainers = new ArrayList<>();
            for (String serviceName : order) {
                ComposeServiceDTO service = byName.get(serviceName);
                String image = service.image();
                if (service.buildSubdir() != null) {
                    image = buildComposeServiceImage(dir, contextDir, projectName, serviceName, service.buildSubdir());
                }

                ComposeServiceOverride override = req.services().get(serviceName);
                List<SecretDraftDTO> secrets = override != null && override.secrets() != null ? override.secrets() : service.secrets();
                String containerName = projectName + "-" + serviceName;

                CreateContainerRequest createReq = new CreateContainerRequest(
                        image,
                        containerName,
                        override != null ? override.subdomain() : null,
                        override != null && override.stdin(),
                        service.restartPolicy(),
                        null,
                        List.of(networkName),
                        service.volumes(),
                        service.ports(),
                        null,
                        secrets,
                        projectName
                );
                containerService.createContainer(createReq, owner);
                createdContainers.add(containerName);
                log.info("[compose {}] created container {}", uploadId, containerName);
            }

            return new ComposeDeployResultDTO(projectName, createdContainers);
        } finally {
            deleteRecursively(dir);
        }
    }

    private String buildComposeServiceImage(Path dir, Path contextDir, String projectName, String serviceName, String buildSubdir) {
        Path buildContext = contextDir.resolve(buildSubdir).normalize();
        if (!buildContext.startsWith(contextDir.normalize())) {
            throw new IllegalArgumentException("Build context escapes the uploaded project for service '" + serviceName + "'");
        }
        String tag = "chatops/" + projectName + "-" + serviceName + "-" + UUID.randomUUID().toString().substring(0, 8) + ":latest";
        Path tarPath = dir.resolve(serviceName + "-build.tar");
        try {
            CompressArchiveUtil.tar(buildContext, tarPath, false, true);
            try (InputStream tarStream = Files.newInputStream(tarPath)) {
                dockerClient.buildImageCmd()
                        .withTarInputStream(tarStream)
                        .withTags(Set.of(tag))
                        .exec(new BuildImageResultCallback())
                        .awaitImageId(buildTimeoutSeconds, TimeUnit.SECONDS);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                Files.deleteIfExists(tarPath);
            } catch (IOException ignored) {
            }
        }
        return tag;
    }

    private String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void reassembleChunks(Path dir, Path zipPath, int totalChunks) {
        try (var out = Files.newOutputStream(zipPath)) {
            for (int i = 0; i < totalChunks; i++) {
                Path part = dir.resolve(i + ".part");
                if (!Files.exists(part)) {
                    throw new IllegalArgumentException("Missing chunk " + i + " of " + totalChunks);
                }
                Files.copy(part, out);
                Files.delete(part);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            if (Files.size(zipPath) > maxZipBytes) {
                throw new IllegalArgumentException("The uploaded file is too large (max " + (maxZipBytes / (1024 * 1024)) + " MB)");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private JsonNode readManifest(String containerId) {
        try (InputStream manifestTar = dockerClient.copyArchiveFromContainerCmd(containerId, "/work/out/manifest.json").exec();
             var tarIn = new TarArchiveInputStream(manifestTar)) {
            tarIn.getNextEntry();
            return objectMapper.readTree(tarIn.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void extractTarToDirectory(InputStream tarStream, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (var tarIn = new TarArchiveInputStream(tarStream)) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                Path target = targetDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(targetDir)) continue;
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(tarIn, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void ensureImporterImageBuilt() {
        try {
            dockerClient.inspectImageCmd(importerImage).exec();
            return;
        } catch (NotFoundException ignored) {
            // build below
        }
        log.info("Importer image not found, building it now");
        try {
            Path buildDir = Files.createTempDirectory("chatops-importer-build");
            copyResource("/importer/Dockerfile", buildDir.resolve("Dockerfile"));
            copyResource("/importer/import.py", buildDir.resolve("import.py"));

            Path tarPath = buildDir.resolveSibling(buildDir.getFileName() + ".tar");
            CompressArchiveUtil.tar(buildDir, tarPath, false, true);
            try (InputStream tarStream = Files.newInputStream(tarPath)) {
                dockerClient.buildImageCmd()
                        .withTarInputStream(tarStream)
                        .withTags(Set.of(importerImage))
                        .exec(new BuildImageResultCallback())
                        .awaitImageId(importerBuildTimeoutSeconds, TimeUnit.SECONDS);
            } finally {
                deleteRecursively(buildDir);
                Files.deleteIfExists(tarPath);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void copyResource(String classpathPath, Path target) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(classpathPath)) {
            if (in == null) throw new IOException("Missing bundled resource " + classpathPath);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String readExecOutput(String execId) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            dockerClient.execStartCmd(execId)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            try {
                                out.write(frame.getPayload());
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    })
                    .awaitCompletion(sandboxWaitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private Path uploadDir(String uploadId) {
        if (!UPLOAD_ID_PATTERN.matcher(uploadId).matches()) {
            throw new IllegalArgumentException("Invalid upload id");
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "chatops-imports", uploadId);
    }

    private void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
