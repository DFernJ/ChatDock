package com.DockerOps.dto.request;

public record UpdateUserRequest(Boolean enabled, String authRole, String permissionRole) {
}
