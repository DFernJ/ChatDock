package com.DockerOps.controller;

import com.DockerOps.dto.request.AssignContainerRequest;
import com.DockerOps.dto.request.CreateStackRequest;
import com.DockerOps.dto.response.AppSummaryResponse;
import com.DockerOps.dto.response.StackResponse;
import com.DockerOps.model.users.User;
import com.DockerOps.service.apps.AppStackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/docker")
public class StackController {

    @Autowired
    private AppStackService appStackService;

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/stacks")
    public List<StackResponse> getStacks() {
        log.info("Listing stacks");
        return appStackService.listStacks();
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/stacks")
    public StackResponse createStack(@RequestBody CreateStackRequest request, Authentication authentication) {
        User current = currentUser(authentication);
        log.info("Creating stack requested by username={}", current.getUsername());
        return appStackService.createStack(request, current);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PatchMapping("/stacks/{stackId}")
    public StackResponse renameStack(@PathVariable UUID stackId, @RequestBody CreateStackRequest request) {
        log.info("Renaming stack id={}", stackId);
        return appStackService.renameStack(stackId, request);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @DeleteMapping("/stacks/{stackId}")
    public ResponseEntity<String> deleteStack(@PathVariable UUID stackId) {
        log.info("Deleting stack id={}", stackId);
        appStackService.deleteStack(stackId);
        return ResponseEntity.ok("Stack deleted successfully");
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/stacks/{stackId}/apps")
    public List<AppSummaryResponse> listStackApps(@PathVariable UUID stackId) {
        log.info("Listing apps for stack id={}", stackId);
        return appStackService.listAppsForStack(stackId);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @DeleteMapping("/stacks/{stackId}/apps/{appId}")
    public ResponseEntity<String> removeApp(@PathVariable UUID stackId, @PathVariable UUID appId) {
        log.info("Removing app id={} from stack id={}", appId, stackId);
        appStackService.removeApp(stackId, appId);
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
        appStackService.assignContainer(containerId, request, current);
        return ResponseEntity.status(HttpStatus.CREATED).body("Container assigned to stack successfully");
    }

    private User currentUser(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
