package com.DockerOps.dto.response;

import com.DockerOps.model.apps.AppStack;

import java.time.Instant;
import java.util.UUID;

public record StackResponse(UUID id, String stackName, int appCount, Instant createdAt) {
    public static StackResponse from(AppStack stack, int appCount) {
        return new StackResponse(
                stack.getId(),
                stack.getStackName(),
                appCount,
                stack.getCreatedAt()
        );
    }
}
