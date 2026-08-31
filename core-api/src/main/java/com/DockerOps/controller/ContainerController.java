package com.DockerOps.controller;

import com.DockerOps.dto.container.ContainerConfigDTO;
import com.DockerOps.dto.container.ContainerDTO;
import com.DockerOps.dto.container.ContainerStatsDTO;
import com.DockerOps.dto.response.AiDiagnosisResponse;
import com.DockerOps.dto.response.CountResponseDTO;
import com.DockerOps.dto.request.CreateContainerRequest;
import com.DockerOps.dto.request.UpdateContainerRequest;
import com.DockerOps.model.users.User;
import com.DockerOps.service.ai.AiDiagnosisService;
import com.DockerOps.service.docker.ContainerEventPublisher;
import com.DockerOps.service.docker.ContainerService;
import com.DockerOps.service.docker.DockerStateStreamService;
import com.DockerOps.service.docker.ImageService;
import com.DockerOps.service.docker.NetworkService;
import com.DockerOps.service.docker.VolumeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/docker")
public class ContainerController {

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

    private User currentUser(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
