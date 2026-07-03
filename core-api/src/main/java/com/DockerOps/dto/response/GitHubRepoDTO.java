package com.DockerOps.dto.response;

public record GitHubRepoDTO(String fullName, String defaultBranch, boolean isPrivate) {}
