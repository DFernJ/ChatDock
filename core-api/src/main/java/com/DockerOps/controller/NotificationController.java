package com.DockerOps.controller;

import com.DockerOps.dto.response.NotificationDTO;
import com.DockerOps.service.notifications.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping
    public List<NotificationDTO> list() {
        log.info("Listing notifications");
        return notificationService.list();
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @PostMapping("/read")
    public ResponseEntity<Void> markAllRead() {
        log.info("Marking all notifications as read");
        notificationService.markAllRead();
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("Deleting notification id={}", id);
        notificationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        log.info("Deleting all notifications");
        notificationService.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
