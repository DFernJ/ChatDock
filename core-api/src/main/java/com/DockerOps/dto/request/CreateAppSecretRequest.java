package com.DockerOps.dto.request;

public record CreateAppSecretRequest(String secretName, String secretValue) {}
