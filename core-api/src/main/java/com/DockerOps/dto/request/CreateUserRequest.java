package com.DockerOps.dto.request;

public record CreateUserRequest(String username, String email, String password, String authRole, String permissionRole) {
}
