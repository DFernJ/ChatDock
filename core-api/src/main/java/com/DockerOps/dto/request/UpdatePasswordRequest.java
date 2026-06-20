package com.DockerOps.dto.request;

public record UpdatePasswordRequest(String currentPassword, String newPassword) {
}
