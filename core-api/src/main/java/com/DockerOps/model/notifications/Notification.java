package com.DockerOps.model.notifications;

import com.DockerOps.model.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends Auditable {

    @Id
    private UUID id;

    @Column(nullable = false, name = "container_id")
    private String containerId;

    @Column(nullable = false, name = "container_name")
    private String containerName;

    @Column(name = "exit_code")
    private Long exitCode;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false)
    private String message;
}
