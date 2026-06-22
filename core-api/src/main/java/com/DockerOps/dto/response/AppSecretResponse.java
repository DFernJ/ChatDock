package com.DockerOps.dto.response;

import com.DockerOps.model.apps.AppSecret;

import java.time.Instant;
import java.util.UUID;

public record AppSecretResponse(UUID id, String secretName, Instant updatedAt) {
    public static AppSecretResponse from(AppSecret secret) {
        return new AppSecretResponse(secret.getId(), secret.getSecretName(), secret.getUpdatedAt());
    }
}
