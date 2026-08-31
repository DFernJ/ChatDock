package com.DockerOps.controller;

import com.DockerOps.dto.request.CloneRepositoryRequest;
import com.DockerOps.dto.request.CompleteZipImportRequest;
import com.DockerOps.dto.request.DeployComposeRequest;
import com.DockerOps.dto.request.PseudoDockerfileRequest;
import com.DockerOps.dto.response.ComposeDeployResultDTO;
import com.DockerOps.dto.response.GitHubRepoDTO;
import com.DockerOps.dto.response.ImportResultDTO;
import com.DockerOps.model.users.User;
import com.DockerOps.service.docker.ImportService;
import com.DockerOps.service.profile.GitHubOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/imports")
public class ImportController {

    @Autowired
    private ImportService importService;
    @Autowired
    private GitHubOAuthService gitHubOAuthService;

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/zip/{uploadId}/chunks/{chunkIndex}")
    public ResponseEntity<Void> uploadChunk(
            @PathVariable String uploadId,
            @PathVariable int chunkIndex,
            HttpServletRequest request) throws IOException {
        log.info("Received chunk {} for uploadId={}", chunkIndex, uploadId);
        importService.storeChunk(uploadId, chunkIndex, request.getInputStream());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/zip/{uploadId}/complete")
    public ImportResultDTO completeZipImport(@PathVariable String uploadId, @RequestBody CompleteZipImportRequest request) {
        log.info("Completing zip import uploadId={}", uploadId);
        return importService.completeZipImport(uploadId, request.totalChunks());
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/zip/{uploadId}/build-dockerfile")
    public ImportResultDTO buildFromPseudoDockerfile(@PathVariable String uploadId, @RequestBody PseudoDockerfileRequest request) {
        log.info("Building image from pseudo-Dockerfile for uploadId={}", uploadId);
        return importService.buildFromPseudoDockerfile(uploadId, request);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/zip/{uploadId}/deploy-compose")
    public ComposeDeployResultDTO deployCompose(@PathVariable String uploadId, @RequestBody DeployComposeRequest request, Authentication authentication) {
        User current = currentUser(authentication);
        log.info("Deploying compose stack for uploadId={} requested by username={}", uploadId, current.getUsername());
        return importService.deployCompose(uploadId, request, current);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @GetMapping("/github/repos")
    public List<GitHubRepoDTO> listGithubRepos(Authentication authentication) {
        User current = currentUser(authentication);
        log.info("Listing GitHub repositories for username={}", current.getUsername());
        requireGithubToken(authentication);
        return gitHubOAuthService.listRepositories(current);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @GetMapping("/github/branches")
    public List<String> listGithubBranches(@RequestParam String repository, Authentication authentication) {
        log.info("Listing branches for repository={} requested by username={}", repository, currentUser(authentication).getUsername());
        return gitHubOAuthService.listBranches(requireGithubToken(authentication), repository);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/github/clone")
    public ImportResultDTO cloneRepository(@RequestBody CloneRepositoryRequest request, Authentication authentication) {
        User current = currentUser(authentication);
        log.info("Cloning repository={} ref={} requested by username={}", request.repository(), request.ref(), current.getUsername());
        return importService.startGitImport(request.importId(), request.repository(), request.ref(), current);
    }

    private String requireGithubToken(Authentication authentication) {
        String token = currentUser(authentication).getGithubAccessToken();
        if (token == null) {
            throw new IllegalArgumentException("Link your GitHub account in your profile first.");
        }
        return token;
    }

    private User currentUser(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
