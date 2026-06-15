package com.DockerOps.dto.response;

import com.DockerOps.model.users.User;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String username,
        String email,
        String authRole,
        String permissionRole,
        boolean enabled,
        Instant createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAuthRole().name().toLowerCase(),
                user.getPermissions().name().toLowerCase(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
