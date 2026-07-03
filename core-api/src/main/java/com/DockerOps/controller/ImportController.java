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
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

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
        importService.storeChunk(uploadId, chunkIndex, request.getInputStream());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/zip/{uploadId}/complete")
    public ImportResultDTO completeZipImport(@PathVariable String uploadId, @RequestBody CompleteZipImportRequest request) {
        return importService.completeZipImport(uploadId, request.totalChunks());
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/zip/{uploadId}/build-dockerfile")
    public ImportResultDTO buildFromPseudoDockerfile(@PathVariable String uploadId, @RequestBody PseudoDockerfileRequest request) {
        return importService.buildFromPseudoDockerfile(uploadId, request);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/zip/{uploadId}/deploy-compose")
    public ComposeDeployResultDTO deployCompose(@PathVariable String uploadId, @RequestBody DeployComposeRequest request, Authentication authentication) {
        return importService.deployCompose(uploadId, request, currentUser(authentication));
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @GetMapping("/github/repos")
    public List<GitHubRepoDTO> listGithubRepos(Authentication authentication) {
        return gitHubOAuthService.listRepositories(requireGithubToken(authentication));
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @GetMapping("/github/branches")
    public List<String> listGithubBranches(@RequestParam String repository, Authentication authentication) {
        return gitHubOAuthService.listBranches(requireGithubToken(authentication), repository);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/github/clone")
    public ImportResultDTO cloneRepository(@RequestBody CloneRepositoryRequest request, Authentication authentication) {
        return importService.startGitImport(request.importId(), request.repository(), request.ref(), currentUser(authentication));
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(DockerException.class)
    public ResponseEntity<String> handleDockerException(DockerException e) {
        return ResponseEntity.status(e.getHttpStatus()).body(e.getMessage());
    }

    @ExceptionHandler(DockerClientException.class)
    public ResponseEntity<String> handleDockerClientException(DockerClientException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Docker client error: " + e.getMessage());
    }
}
