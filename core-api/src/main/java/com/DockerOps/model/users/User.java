package com.DockerOps.model.users;

import com.DockerOps.converter.SecretValueConverter;
import com.DockerOps.model.Auditable;
import com.DockerOps.model.users.enums.UserAuthRole;
import com.DockerOps.model.users.enums.UserPermissions;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "user")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(nullable = false, name = "username", unique = true)
    private String username;

    @Column(nullable = false, name = "email", unique = true)
    private String email;

    @Column(nullable = false, name = "password_hash")
    private String password_hash;

    @Column(nullable = false, name = "user_auth_role")
    @Enumerated(EnumType.STRING)
    private UserAuthRole authRole;

    @Column(nullable = false, name = "user_permissions")
    @Enumerated(EnumType.STRING)
    private UserPermissions permissions;

    @Column(nullable = false, name = "enabled")
    private boolean enabled;

    @Column(name = "github_id", unique = true)
    private Long githubId;

    @Column(name = "github_username")
    private String githubUsername;

    @Column(name = "github_access_token", columnDefinition = "TEXT")
    private String githubAccessToken;

    @Column(name = "github_installation_id")
    private Long installationId;

    @Column(name = "discord_id", unique = true)
    private Long discordId;

    @Column(name = "discord_username")
    private String discordUsername;
}
