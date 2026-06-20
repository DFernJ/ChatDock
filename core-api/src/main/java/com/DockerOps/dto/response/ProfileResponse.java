package com.DockerOps.dto.response;

import com.DockerOps.model.users.User;

import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String username,
        String authRole,
        String permissionRole,
        boolean discordLinked,
        String discordUsername,
        boolean githubLinked,
        String githubUsername
) {
    public static ProfileResponse from(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getAuthRole().name().toLowerCase(),
                user.getPermissions().name().toLowerCase(),
                user.getDiscordId() != null,
                user.getDiscordUsername(),
                user.getGithubId() != null,
                user.getGithubUsername()
        );
    }
}
