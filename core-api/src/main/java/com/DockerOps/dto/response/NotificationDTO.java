package com.DockerOps.dto.response;

import com.DockerOps.model.notifications.Notification;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record NotificationDTO(
        UUID id,
        String containerId,
        String containerName,
        Long exitCode,
        Instant finishedAt,
        String message,
        Instant createdAt,
        boolean read
) {
    public static NotificationDTO from(Notification n, Set<UUID> readNotificationIds) {
        return new NotificationDTO(
                n.getId(),
                n.getContainerId(),
                n.getContainerName(),
                n.getExitCode(),
                n.getFinishedAt(),
                n.getMessage(),
                n.getCreatedAt(),
                readNotificationIds.contains(n.getId())
        );
    }
}
