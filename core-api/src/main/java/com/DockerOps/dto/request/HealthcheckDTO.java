package com.DockerOps.dto.request;

public record HealthcheckDTO(boolean enabled, String command, Integer intervalSeconds, Integer timeoutSeconds, Integer retries) {}
