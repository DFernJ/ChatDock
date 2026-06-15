package com.DockerOps.dto.response;

import com.DockerOps.model.users.Code;

import java.time.Instant;
import java.util.UUID;

public record CodeResponse(
        UUID id,
        String code,
        String codeType,
        int remainUses,
        Instant createdAt
) {
    public static CodeResponse from(Code code) {
        return new CodeResponse(
                code.getId(),
                code.getCode(),
                code.getCodeType().name().toLowerCase(),
                code.getRemainUses(),
                code.getCreatedAt()
        );
    }
}
