package com.DockerOps.dto.response;

import com.DockerOps.model.users.Code;

import java.time.Instant;

public record DiscordLinkCodeResponse(String code, Instant expiresAt) {
    public static DiscordLinkCodeResponse from(Code code) {
        return new DiscordLinkCodeResponse(code.getCode(), code.getExpiresAt());
    }
}
