package com.DockerOps.model.users;

import com.DockerOps.model.Auditable;
import com.DockerOps.model.users.enums.CodeType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "code")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Code extends Auditable {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, name = "code", length = 12)
    private String code;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, name = "remain_uses")
    private int remainUses;

    @Column(nullable = false, name = "code_type")
    @Enumerated(EnumType.STRING)
    private CodeType codeType;
}
