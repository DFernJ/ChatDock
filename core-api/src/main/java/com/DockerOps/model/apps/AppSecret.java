package com.DockerOps.model.apps;

import com.DockerOps.converter.SecretValueConverter;
import com.DockerOps.model.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "app_secret")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AppSecret extends Auditable{

    @Id
    private UUID id;

    @Column(nullable = false, name = "secret_name")
    private String secretName;

    @Convert(converter = SecretValueConverter.class)
    @Column(nullable = false, name = "secret_value")
    private String secretValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", nullable = false)
    private App app;
}
