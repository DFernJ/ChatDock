package com.DockerOps.service.notifications;

import com.DockerOps.dto.response.NotificationDTO;
import com.DockerOps.model.notifications.Notification;
import com.DockerOps.model.notifications.NotificationRead;
import com.DockerOps.repository.notifications.NotificationReadRepository;
import com.DockerOps.repository.notifications.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationReadRepository notificationReadRepository;

    public List<NotificationDTO> list(UUID userId) {
        List<Notification> notifications = notificationRepository.findAllByOrderByCreatedAtDesc();
        Set<UUID> readNotificationIds = notificationReadRepository.findReadNotificationIdsByUserId(userId);
        return notifications.stream()
                .map(n -> NotificationDTO.from(n, readNotificationIds))
                .toList();
    }

    public Optional<Notification> recordContainerFailure(String containerId, String containerName, Long exitCode, Instant finishedAt) {
        if (notificationRepository.existsByContainerIdAndFinishedAt(containerId, finishedAt)) {
            return Optional.empty();
        }
        String message = exitCode != null
                ? "\"" + containerName + "\" exited with an error (code " + exitCode + ")."
                : "\"" + containerName + "\" failed.";
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .containerId(containerId)
                .containerName(containerName)
                .exitCode(exitCode)
                .finishedAt(finishedAt)
                .message(message)
                .build();
        return Optional.of(notificationRepository.save(notification));
    }

    public void markAllRead(UUID userId) {
        Set<UUID> alreadyRead = notificationReadRepository.findReadNotificationIdsByUserId(userId);
        Instant now = Instant.now();
        List<NotificationRead> newlyRead = notificationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(Notification::getId)
                .filter(id -> !alreadyRead.contains(id))
                .map(id -> NotificationRead.builder()
                        .notificationId(id)
                        .userId(userId)
                        .readAt(now)
                        .build())
                .toList();
        notificationReadRepository.saveAll(newlyRead);
    }

    public void delete(UUID id) {
        notificationReadRepository.deleteByNotificationId(id);
        notificationRepository.deleteById(id);
    }

    public void deleteAll() {
        Set<UUID> allIds = notificationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(Notification::getId)
                .collect(Collectors.toSet());
        notificationReadRepository.deleteAllByNotificationIdIn(allIds);
        notificationRepository.deleteAll();
    }
}
