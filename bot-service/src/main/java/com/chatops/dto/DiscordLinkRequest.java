package com.chatops.dto;

public record DiscordLinkRequest(String code, Long discordId, String discordUsername) {
}
