package com.DockerOps.dto.response;

import com.DockerOps.model.users.User;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String authRole,
        String permissionRole
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getAuthRole().name().toLowerCase(),
                user.getPermissions().name().toLowerCase()
        );
    }
}
