package com.DockerOps.dto.request;

public record DiscordConsumeLinkRequest(String code, Long discordId, String discordUsername) {
}
