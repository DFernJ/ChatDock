package com.DockerOps.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ContainerFailureEvent(
        UUID id,
        String containerId,
        String containerName,
        Long exitCode,
        Instant finishedAt,
        String message
) {}
