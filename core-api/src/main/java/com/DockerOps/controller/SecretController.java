package com.DockerOps.controller;

import com.DockerOps.dto.request.CreateAppSecretRequest;
import com.DockerOps.dto.request.UpdateAppSecretRequest;
import com.DockerOps.dto.response.AppSecretResponse;
import com.DockerOps.dto.response.SecretValueResponse;
import com.DockerOps.service.apps.AppSecretService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/docker")
public class SecretController {

    @Autowired
    private AppSecretService appSecretService;

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/containers/{containerId}/secrets")
    public List<AppSecretResponse> listSecrets(@PathVariable String containerId) {
        log.info("Listing secrets for container id={}", containerId);
        return appSecretService.listSecrets(containerId);
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/containers/{containerId}/secrets/{secretId}/value")
    public SecretValueResponse getSecretValue(@PathVariable String containerId, @PathVariable UUID secretId) {
        log.info("Revealing secret id={} for container id={}", secretId, containerId);
        return new SecretValueResponse(appSecretService.getSecretValue(containerId, secretId));
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/containers/{containerId}/secrets")
    public AppSecretResponse createSecret(@PathVariable String containerId, @RequestBody CreateAppSecretRequest request) {
        log.info("Creating secret for container id={}", containerId);
        return appSecretService.createSecret(containerId, request);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PatchMapping("/containers/{containerId}/secrets/{secretId}")
    public AppSecretResponse updateSecret(
            @PathVariable String containerId,
            @PathVariable UUID secretId,
            @RequestBody UpdateAppSecretRequest request) {
        log.info("Updating secret id={} for container id={}", secretId, containerId);
        return appSecretService.updateSecret(containerId, secretId, request);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @DeleteMapping("/containers/{containerId}/secrets/{secretId}")
    public ResponseEntity<String> deleteSecret(@PathVariable String containerId, @PathVariable UUID secretId) {
        log.info("Deleting secret id={} for container id={}", secretId, containerId);
        appSecretService.deleteSecret(containerId, secretId);
        return ResponseEntity.ok("Secret deleted successfully");
    }
}
