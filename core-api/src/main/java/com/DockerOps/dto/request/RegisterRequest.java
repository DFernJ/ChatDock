package com.DockerOps.dto.request;

public record RegisterRequest(String username, String email, String password, String code) {
}
