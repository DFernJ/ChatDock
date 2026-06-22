package com.DockerOps.model.apps;

import com.DockerOps.model.Auditable;
import com.DockerOps.model.users.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "app")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class App extends Auditable {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, name = "app_name")
    private String name;

    @Column(nullable = false, name = "container_name", unique = true)
    private String containerName;

    @Column(nullable = true, name = "last_ai_diagnosis")
    private String lastAIDiagnosis;

    @Column(nullable = true, name = "last_ai_diagnosis_timestamp")
    private Instant lastAIDiagnosisTimestamp;

    @OneToMany(mappedBy = "app", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AppSecret> secrets;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stack_id", nullable = false)
    private AppStack stack;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_owner_id", nullable = false)
    private User appOwner;
}
