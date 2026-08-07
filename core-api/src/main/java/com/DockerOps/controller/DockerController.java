package com.DockerOps.controller;

import com.DockerOps.dto.container.ContainerConfigDTO;
import com.DockerOps.dto.container.ContainerDTO;
import com.DockerOps.dto.container.ContainerStatsDTO;
import com.DockerOps.dto.response.AiDiagnosisResponse;
import com.DockerOps.dto.image.DockerHubImageDTO;
import com.DockerOps.dto.image.ImageDTO;
import com.DockerOps.dto.network.NetworkDTO;
import com.DockerOps.dto.request.AssignContainerRequest;
import com.DockerOps.dto.request.CreateAppSecretRequest;
import com.DockerOps.dto.request.CreateContainerRequest;
import com.DockerOps.dto.request.CreateStackRequest;
import com.DockerOps.dto.request.UpdateAppSecretRequest;
import com.DockerOps.dto.request.UpdateContainerRequest;
import com.DockerOps.dto.response.AppSecretResponse;
import com.DockerOps.dto.response.AppSummaryResponse;
import com.DockerOps.dto.response.CountResponseDTO;
import com.DockerOps.dto.response.SecretValueResponse;
import com.DockerOps.dto.response.StackResponse;
import com.DockerOps.dto.volume.VolumeDTO;
import com.DockerOps.model.users.User;
import com.DockerOps.service.ai.AiDiagnosisService;
import com.DockerOps.service.docker.ContainerEventPublisher;
import com.DockerOps.service.docker.ContainerService;
import com.DockerOps.service.docker.DockerStateStreamService;
import com.DockerOps.service.docker.ImageService;
import com.DockerOps.service.docker.NetworkService;
import com.DockerOps.service.docker.VolumeService;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/docker")
public class DockerController {

    @Autowired
    private ContainerService containerService;
    @Autowired
    private VolumeService volumeService;
    @Autowired
    private ImageService imageService;
    @Autowired
    private NetworkService networkService;
    @Autowired
    private AiDiagnosisService aiDiagnosisService;
    @Autowired
    private ContainerEventPublisher containerEventPublisher;
    @Autowired
    private DockerStateStreamService dockerStateStreamService;

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/count")
    public CountResponseDTO countDocker() {
        log.info("Fetching Docker resource counts");
        return new CountResponseDTO(containerService.countContainers(), imageService.countImages(),
                volumeService.countVolumes(), networkService.countNetworks());
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/containers")
    public List<ContainerDTO> getContainers() {
        log.info("Listing containers");
        return containerService.listContainers();
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/containers")
    public ContainerDTO createContainer(@RequestBody CreateContainerRequest request, Authentication authentication) {
        User current = currentUser(authentication);
        log.info("Creating container requested by username={}", current.getUsername());
        return containerService.createContainer(request, current);
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping(path = "/containers/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamContainerEvents() {
        log.info("Subscribing to container events stream");
        return sseResponse(containerEventPublisher.subscribe());
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/containers/stats")
    public List<ContainerStatsDTO> getContainersStats() {
        log.info("Listing container stats");
        return containerService.listStats();
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping(path = "/state/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamDockerState() {
        log.info("Subscribing to Docker state stream");
        return sseResponse(dockerStateStreamService.subscribe());
    }

    private ResponseEntity<SseEmitter> sseResponse(SseEmitter emitter) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/containers/{containerId}")
    public ContainerStatsDTO getContainerInfo(@PathVariable String containerId) {
        log.info("Fetching stats for container id={}", containerId);
        return containerService.getStats(containerId);
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/containers/{containerId}/config")
    public ContainerConfigDTO getContainerConfig(@PathVariable String containerId) {
        log.info("Fetching config for container id={}", containerId);
        return containerService.getContainerConfig(containerId);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PatchMapping("/containers/{containerId}")
    public ContainerDTO updateContainer(@PathVariable String containerId, @RequestBody UpdateContainerRequest request) {
        log.info("Updating container id={}", containerId);
        return containerService.updateContainer(containerId, request);
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/stacks")
    public List<StackResponse> getStacks() {
        log.info("Listing stacks");
        return containerService.listStacks();
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/stacks")
    public StackResponse createStack(@RequestBody CreateStackRequest request, Authentication authentication) {
        User current = currentUser(authentication);
        log.info("Creating stack requested by username={}", current.getUsername());
        return containerService.createStack(request, current);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PatchMapping("/stacks/{stackId}")
    public StackResponse renameStack(@PathVariable UUID stackId, @RequestBody CreateStackRequest request) {
        log.info("Renaming stack id={}", stackId);
        return containerService.renameStack(stackId, request);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @DeleteMapping("/stacks/{stackId}")
    public ResponseEntity<String> deleteStack(@PathVariable UUID stackId) {
        log.info("Deleting stack id={}", stackId);
        containerService.deleteStack(stackId);
        return ResponseEntity.ok("Stack deleted successfully");
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/stacks/{stackId}/apps")
    public List<AppSummaryResponse> listStackApps(@PathVariable UUID stackId) {
        log.info("Listing apps for stack id={}", stackId);
        return containerService.listAppsForStack(stackId);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @DeleteMapping("/stacks/{stackId}/apps/{appId}")
    public ResponseEntity<String> removeApp(@PathVariable UUID stackId, @PathVariable UUID appId) {
        log.info("Removing app id={} from stack id={}", appId, stackId);
        containerService.removeApp(stackId, appId);
        return ResponseEntity.ok("App removed from stack successfully");
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/containers/{containerId}/assign")
    public ResponseEntity<String> assignContainer(
            @PathVariable String containerId,
            @RequestBody AssignContainerRequest request,
            Authentication authentication) {
        User current = currentUser(authentication);
        log.info("Assigning container id={} to a stack requested by username={}", containerId, current.getUsername());
        containerService.assignContainer(containerId, request, current);
        return ResponseEntity.status(HttpStatus.CREATED).body("Container assigned to stack successfully");
    }

    private User currentUser(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/containers/{containerId}/secrets")
    public List<AppSecretResponse> listSecrets(@PathVariable String containerId) {
        log.info("Listing secrets for container id={}", containerId);
        return containerService.listSecrets(containerId);
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/containers/{containerId}/secrets/{secretId}/value")
    public SecretValueResponse getSecretValue(@PathVariable String containerId, @PathVariable UUID secretId) {
        log.info("Revealing secret id={} for container id={}", secretId, containerId);
        return new SecretValueResponse(containerService.getSecretValue(containerId, secretId));
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/containers/{containerId}/secrets")
    public AppSecretResponse createSecret(@PathVariable String containerId, @RequestBody CreateAppSecretRequest request) {
        log.info("Creating secret for container id={}", containerId);
        return containerService.createSecret(containerId, request);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PatchMapping("/containers/{containerId}/secrets/{secretId}")
    public AppSecretResponse updateSecret(
            @PathVariable String containerId,
            @PathVariable UUID secretId,
            @RequestBody UpdateAppSecretRequest request) {
        log.info("Updating secret id={} for container id={}", secretId, containerId);
        return containerService.updateSecret(containerId, secretId, request);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @DeleteMapping("/containers/{containerId}/secrets/{secretId}")
    public ResponseEntity<String> deleteSecret(@PathVariable String containerId, @PathVariable UUID secretId) {
        log.info("Deleting secret id={} for container id={}", secretId, containerId);
        containerService.deleteSecret(containerId, secretId);
        return ResponseEntity.ok("Secret deleted successfully");
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/containers/{containerId}/start")
    public ResponseEntity<String> startContainer(@PathVariable String containerId) {
        log.info("Starting container id={}", containerId);
        containerService.startContainer(containerId);
        return ResponseEntity.ok("Container started successfully");
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/containers/{containerId}/stop")
    public ResponseEntity<String> stopContainer(@PathVariable String containerId) {
        log.info("Stopping container id={}", containerId);
        containerService.stopContainer(containerId);
        return ResponseEntity.ok("Container stopped successfully");
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
     @PostMapping("/containers/{containerId}/restart")
    public ResponseEntity<String> restartContainer(@PathVariable String containerId) {
                log.info("Restarting container id={}", containerId);
                containerService.restartContainer(containerId);
                return ResponseEntity.ok("Container restarted successfully");
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @PostMapping("/containers/{containerId}/logs")
    public ResponseEntity<byte[]> getLogFile(@PathVariable String containerId) {
        log.info("Downloading logs for container id={}", containerId);
        byte[] logs = containerService.getLogs(containerId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + containerId + ".log\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(logs);
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @PostMapping("/containers/{containerId}/ai-diagnosis")
    public AiDiagnosisResponse diagnoseContainer(@PathVariable String containerId) {
        log.info("Running AI diagnosis for container id={}", containerId);
        return aiDiagnosisService.diagnose(containerId);
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @DeleteMapping("/containers/{containerId}/delete")
    public ResponseEntity<?> deleteContainer(
            @PathVariable String containerId,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(defaultValue = "false") boolean removeVolumes) {
        log.info("Deleting container id={} (force={}, removeVolumes={})", containerId, force, removeVolumes);
        containerService.deleteContainer(containerId, force, removeVolumes);
        return ResponseEntity.ok("Container delete successfully");
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/images")
    public List<ImageDTO> getImages() {
        log.info("Listing images");
        return imageService.listImages();
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/images/search")
    public List<DockerHubImageDTO> searchDockerHubImages(@RequestParam String query) {
        log.info("Searching Docker Hub for query={}", query);
        return imageService.searchDockerHub(query);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/images/pull")
    public ResponseEntity<String> pullImage(
            @RequestParam String repository,
            @RequestParam(defaultValue = "latest") String tag) {
        log.info("Pulling image repository={} tag={}", repository, tag);
        imageService.pullImage(repository, tag);
        return ResponseEntity.ok("Image pulled successfully");
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<String> deleteImage(
            @PathVariable String imageId,
            @RequestParam(defaultValue = "false") boolean force) {
        log.info("Deleting image id={} (force={})", imageId, force);
        imageService.deleteImage(imageId, force);
        return ResponseEntity.ok("Image deleted successfully");
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @PostMapping("/images/prune")
    public ResponseEntity<String> pruneImages() {
        log.info("Pruning unused images");
        long spaceReclaimed = imageService.pruneImages();
        return ResponseEntity.ok("Images pruned successfully. Space reclaimed: " + spaceReclaimed + " bytes");
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/volumes")
    public List<VolumeDTO> getVolumes() {
        log.info("Listing volumes");
        return volumeService.listVolumes();
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/volumes/{name}")
    public VolumeDTO getVolumeInfo(@PathVariable String name) {
        log.info("Fetching volume name={}", name);
        return volumeService.getVolume(name);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/volumes")
    public ResponseEntity<VolumeDTO> createVolume(
            @RequestParam String name,
            @RequestParam(defaultValue = "local") String driver) {
        log.info("Creating volume name={} driver={}", name, driver);
        return ResponseEntity.ok(volumeService.createVolume(name, driver));
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @DeleteMapping("/volumes/{name}")
    public ResponseEntity<String> deleteVolume(@PathVariable String name) {
        log.info("Deleting volume name={}", name);
        volumeService.deleteVolume(name);
        return ResponseEntity.ok("Volume deleted successfully");
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/networks")
    public List<NetworkDTO> getNetworks() {
        log.info("Listing networks");
        return networkService.listNetworks();
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/networks/{networkId}")
    public NetworkDTO getNetworkInfo(@PathVariable String networkId) {
        log.info("Fetching network id={}", networkId);
        return networkService.getNetwork(networkId);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/networks")
    public ResponseEntity<NetworkDTO> createNetwork(
            @RequestParam String name,
            @RequestParam(defaultValue = "bridge") String driver) {
        log.info("Creating network name={} driver={}", name, driver);
        return ResponseEntity.ok(networkService.createNetwork(name, driver));
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @DeleteMapping("/networks/{networkId}")
    public ResponseEntity<String> deleteNetwork(@PathVariable String networkId) {
        log.info("Deleting network id={}", networkId);
        networkService.deleteNetwork(networkId);
        return ResponseEntity.ok("Network deleted successfully");
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/networks/{networkId}/connect")
    public ResponseEntity<String> connectContainerToNetwork(
            @PathVariable String networkId,
            @RequestParam String containerId) {
        log.info("Connecting container id={} to network id={}", containerId, networkId);
        networkService.connectContainer(networkId, containerId);
        return ResponseEntity.ok("Container connected successfully");
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/networks/{networkId}/disconnect")
    public ResponseEntity<String> disconnectContainerFromNetwork(
            @PathVariable String networkId,
            @RequestParam String containerId,
            @RequestParam(defaultValue = "false") boolean force) {
        log.info("Disconnecting container id={} from network id={} (force={})", containerId, networkId, force);
        networkService.disconnectContainer(networkId, containerId, force);
        return ResponseEntity.ok("Container disconnected successfully");
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @PostMapping("/networks/prune")
    public ResponseEntity<String> pruneNetworks() {
        log.info("Pruning unused networks");
        int networksPruned = networkService.pruneNetworks();
        return ResponseEntity.ok("Networks pruned successfully. Networks pruned: " + networksPruned);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Rejected request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(DockerException.class)
    public ResponseEntity<String> handleDockerException(DockerException e) {
        log.error("Docker API error", e);
        return ResponseEntity.status(e.getHttpStatus()).body(e.getMessage());
    }

    @ExceptionHandler(DockerClientException.class)
    public ResponseEntity<String> handleDockerClientException(DockerClientException e) {
        log.error("Docker client error", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Docker client error: " + e.getMessage());
    }

    @ExceptionHandler({NonTransientAiException.class, TransientAiException.class})
    public ResponseEntity<String> handleAiClientException(RuntimeException e) {
        log.error("AI diagnosis failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("AI diagnosis failed: " + e.getMessage());
    }

}
