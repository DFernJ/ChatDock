package com.chatops.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhoAmIResponse(String username, String authRole, String permissionRole) {
}
