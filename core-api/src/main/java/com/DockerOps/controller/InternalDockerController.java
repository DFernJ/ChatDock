package com.DockerOps.controller;

import com.DockerOps.dto.container.ContainerDTO;
import com.DockerOps.dto.container.ContainerStatsDTO;
import com.DockerOps.dto.response.AiDiagnosisResponse;
import com.DockerOps.model.users.User;
import com.DockerOps.model.users.enums.UserPermissions;
import com.DockerOps.service.ai.AiDiagnosisService;
import com.DockerOps.service.docker.ContainerService;
import com.DockerOps.service.profile.ProfileService;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/internal/docker")
public class InternalDockerController {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Value("${app.security.internal-token}")
    private String internalToken;

    @Autowired
    private ContainerService containerService;
    @Autowired
    private AiDiagnosisService aiDiagnosisService;
    @Autowired
    private ProfileService profileService;

    @GetMapping("/containers")
    public ResponseEntity<List<ContainerDTO>> getContainers(@RequestHeader(INTERNAL_TOKEN_HEADER) String token) {
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(containerService.listContainers());
    }

    @PostMapping("/containers/{name}/start")
    public ResponseEntity<?> start(@RequestHeader(INTERNAL_TOKEN_HEADER) String token,
                                    @PathVariable String name,
                                    @RequestParam Long discordId) {
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requirePermission(discordId, UserPermissions.EDITOR);
        containerService.startContainer(findContainerByName(name).id());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/containers/{name}/stop")
    public ResponseEntity<?> stop(@RequestHeader(INTERNAL_TOKEN_HEADER) String token,
                                   @PathVariable String name,
                                   @RequestParam Long discordId) {
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requirePermission(discordId, UserPermissions.EDITOR);
        containerService.stopContainer(findContainerByName(name).id());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/containers/{name}/restart")
    public ResponseEntity<?> restart(@RequestHeader(INTERNAL_TOKEN_HEADER) String token,
                                      @PathVariable String name,
                                      @RequestParam Long discordId) {
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requirePermission(discordId, UserPermissions.EDITOR);
        containerService.restartContainer(findContainerByName(name).id());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/containers/{name}")
    public ResponseEntity<?> delete(@RequestHeader(INTERNAL_TOKEN_HEADER) String token,
                                     @PathVariable String name,
                                     @RequestParam Long discordId,
                                     @RequestParam(defaultValue = "false") boolean force,
                                     @RequestParam(defaultValue = "false") boolean removeVolumes) {
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requirePermission(discordId, UserPermissions.ROOT);
        containerService.deleteContainer(findContainerByName(name).id(), force, removeVolumes);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/containers/{name}/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> logs(@RequestHeader(INTERNAL_TOKEN_HEADER) String token,
                                        @PathVariable String name,
                                        @RequestParam Long discordId) {
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requirePermission(discordId, UserPermissions.VIEWER);
        byte[] logs = containerService.getLogs(findContainerByName(name).id());
        return ResponseEntity.ok(new String(logs, StandardCharsets.UTF_8));
    }

    @GetMapping("/containers/{name}/stats")
    public ResponseEntity<ContainerStatsDTO> stats(@RequestHeader(INTERNAL_TOKEN_HEADER) String token,
                                                     @PathVariable String name,
                                                     @RequestParam Long discordId) {
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requirePermission(discordId, UserPermissions.VIEWER);
        return ResponseEntity.ok(containerService.getStats(findContainerByName(name).id()));
    }

    @PostMapping("/containers/{name}/diagnosis")
    public ResponseEntity<AiDiagnosisResponse> diagnosis(@RequestHeader(INTERNAL_TOKEN_HEADER) String token,
                                                           @PathVariable String name,
                                                           @RequestParam Long discordId) {
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requirePermission(discordId, UserPermissions.VIEWER);
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(DockerException.class)
    public ResponseEntity<String> handleDockerException(DockerException e) {
        return ResponseEntity.status(e.getHttpStatus()).body(e.getMessage());
    }

    @ExceptionHandler(DockerClientException.class)
    public ResponseEntity<String> handleDockerClientException(DockerClientException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Docker client error: " + e.getMessage());
    }

    @ExceptionHandler({NonTransientAiException.class, TransientAiException.class})
    public ResponseEntity<String> handleAiClientException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("AI diagnosis failed: " + e.getMessage());
    }
}
