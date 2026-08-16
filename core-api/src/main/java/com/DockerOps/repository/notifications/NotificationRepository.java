package com.DockerOps.repository.notifications;

import com.DockerOps.model.notifications.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findAllByOrderByCreatedAtDesc();
    boolean existsByContainerIdAndFinishedAt(String containerId, Instant finishedAt);
}
