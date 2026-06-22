package com.DockerOps.model.apps;

import com.DockerOps.model.Auditable;
import com.DockerOps.model.users.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "app_stack")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AppStack extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(nullable = false, unique = true, name = "stack_name")
    private String stackName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "stack", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<App> apps;
}
