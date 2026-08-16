package com.DockerOps.repository.notifications;

import com.DockerOps.model.notifications.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
public interface NotificationReadRepository extends JpaRepository<NotificationRead, UUID> {

    @Query("select nr.notificationId from NotificationRead nr where nr.userId = :userId")
    Set<UUID> findReadNotificationIdsByUserId(@Param("userId") UUID userId);

    boolean existsByNotificationIdAndUserId(UUID notificationId, UUID userId);

    void deleteByNotificationId(UUID notificationId);

    void deleteAllByNotificationIdIn(Set<UUID> notificationIds);
}
