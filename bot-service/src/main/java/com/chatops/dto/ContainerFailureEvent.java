package com.chatops.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContainerFailureEvent(
        UUID id,
        String containerId,
        String containerName,
        Long exitCode,
        Instant finishedAt,
        String message
) {
}
