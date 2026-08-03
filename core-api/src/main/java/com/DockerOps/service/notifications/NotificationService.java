package com.DockerOps.service.notifications;

import com.DockerOps.dto.response.NotificationDTO;
import com.DockerOps.model.notifications.Notification;
import com.DockerOps.repository.notifications.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    public List<NotificationDTO> list() {
        return notificationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(NotificationDTO::from)
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
                .read(false)
                .build();
        return Optional.of(notificationRepository.save(notification));
    }

    public void markAllRead() {
        List<Notification> unread = notificationRepository.findByReadFalse();
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    public void delete(UUID id) {
        notificationRepository.deleteById(id);
    }

    public void deleteAll() {
        notificationRepository.deleteAll();
    }
}
