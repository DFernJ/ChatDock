package com.DockerOps.controller;

import com.DockerOps.dto.container.ContainerDTO;
import com.DockerOps.dto.container.ContainerStatsDTO;
import com.DockerOps.dto.response.AiDiagnosisResponse;
import com.DockerOps.model.users.User;
import com.DockerOps.model.users.enums.UserPermissions;
import com.DockerOps.service.ai.AiDiagnosisService;
import com.DockerOps.service.docker.ContainerService;
import com.DockerOps.service.profile.ProfileService;
import com.DockerOps.util.InternalHeader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/internal/docker")
public class InternalDockerController {

    @Value("${app.security.internal-token}")
    private String internalToken;

    @Autowired
    private ContainerService containerService;
    @Autowired
    private AiDiagnosisService aiDiagnosisService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private InternalHeader internalHeader;

    @GetMapping("/containers")
    public ResponseEntity<List<ContainerDTO>> getContainers(@RequestHeader HttpHeaders headers) {
        if (!internalHeader.matches(internalToken, headers.getFirst(internalHeader.getName()))) {
            log.warn("Rejected list containers request: invalid internal token");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("Listing containers");
        return ResponseEntity.ok(containerService.listContainers());
    }

    @PostMapping("/containers/{name}/start")
    public ResponseEntity<?> start(@RequestHeader HttpHeaders headers,
                                    @PathVariable String name,
                                    @RequestParam Long discordId) {
        if (!internalHeader.matches(internalToken, headers.getFirst(internalHeader.getName()))) {
            log.warn("Rejected start request for container '{}': invalid internal token", name);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requirePermission(discordId, UserPermissions.EDITOR);
        log.info("Starting container '{}' requested by discordId={}", name, discordId);
        containerService.startContainer(findContainerByName(name).id());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/containers/{name}/stop")
    public ResponseEntity<?> stop(@RequestHeader HttpHeaders headers,
                                   @PathVariable String name,
                                   @RequestParam Long discordId) {
        if (!internalHeader.matches(internalToken, headers.getFirst(internalHeader.getName()))) {
            log.warn("Rejected stop request for container '{}': invalid internal token", name);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requirePermission(discordId, UserPermissions.EDITOR);
        log.info("Stopping container '{}' requested by discordId={}", name, discordId);
        containerService.stopContainer(findContainerByName(name).id());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/containers/{name}/restart")
    public ResponseEntity<?> restart(@RequestHeader HttpHeaders headers,
                                      @PathVariable String name,
                                      @RequestParam Long discordId) {
        if (!internalHeader.matches(internalToken, headers.getFirst(internalHeader.getName()))) {
            log.warn("Rejected restart request for container '{}': invalid internal token", name);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requirePermission(discordId, UserPermissions.EDITOR);
        log.info("Restarting container '{}' requested by discordId={}", name, discordId);
        containerService.restartContainer(findContainerByName(name).id());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/containers/{name}")
    public ResponseEntity<?> delete(@RequestHeader HttpHeaders headers,
                                     @PathVariable String name,
                                     @RequestParam Long discordId,
                                     @RequestParam(defaultValue = "false") boolean force,
                                     @RequestParam(defaultValue = "false") boolean removeVolumes) {
        if (!internalHeader.matches(internalToken, headers.getFirst(internalHeader.getName()))) {
            log.warn("Rejected delete request for container '{}': invalid internal token", name);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requirePermission(discordId, UserPermissions.ROOT);
        log.info("Deleting container '{}' (force={}, removeVolumes={}) requested by discordId={}", name, force, removeVolumes, discordId);
        containerService.deleteContainer(findContainerByName(name).id(), force, removeVolumes);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/containers/{name}/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> logs(@RequestHeader HttpHeaders headers,
                                        @PathVariable String name,
                                        @RequestParam Long discordId) {
        if (!internalHeader.matches(internalToken, headers.getFirst(internalHeader.getName()))) {
            log.warn("Rejected logs request for container '{}': invalid internal token", name);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requirePermission(discordId, UserPermissions.VIEWER);
        log.info("Fetching logs for container '{}' requested by discordId={}", name, discordId);
        byte[] logs = containerService.getLogs(findContainerByName(name).id());
        return ResponseEntity.ok(new String(logs, StandardCharsets.UTF_8));
    }

    @GetMapping("/containers/{name}/stats")
    public ResponseEntity<ContainerStatsDTO> stats(@RequestHeader HttpHeaders headers,
                                                     @PathVariable String name,
                                                     @RequestParam Long discordId) {
        if (!internalHeader.matches(internalToken, headers.getFirst(internalHeader.getName()))) {
            log.warn("Rejected stats request for container '{}': invalid internal token", name);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requirePermission(discordId, UserPermissions.VIEWER);
        log.info("Fetching stats for container '{}' requested by discordId={}", name, discordId);
        return ResponseEntity.ok(containerService.getStats(findContainerByName(name).id()));
    }

    @PostMapping("/containers/{name}/diagnosis")
    public ResponseEntity<AiDiagnosisResponse> diagnosis(@RequestHeader HttpHeaders headers,
                                                           @PathVariable String name,
                                                           @RequestParam Long discordId) {
        if (!internalHeader.matches(internalToken, headers.getFirst(internalHeader.getName()))) {
            log.warn("Rejected diagnosis request for container '{}': invalid internal token", name);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requirePermission(discordId, UserPermissions.VIEWER);
        log.info("Running AI diagnosis for container '{}' requested by discordId={}", name, discordId);
        return ResponseEntity.ok(aiDiagnosisService.diagnose(findContainerByName(name).id()));
    }

    private ContainerDTO findContainerByName(String name) {
        return containerService.listContainers().stream()
                .filter(container -> container.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No container found for channel \"" + name + "\"."));
    }

    private void requirePermission(Long discordId, UserPermissions required) {
        User user = profileService.findByDiscordId(discordId)
                .orElseThrow(() -> new IllegalArgumentException("Your Discord account isn't linked to a ChatOps account."));
        if (user.getPermissions().ordinal() < required.ordinal()) {
            throw new IllegalArgumentException("You don't have permission to perform this action.");
        }
    }
}
