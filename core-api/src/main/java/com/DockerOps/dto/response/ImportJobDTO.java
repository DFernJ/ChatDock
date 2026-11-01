package com.DockerOps.dto.response;

public record ImportJobDTO(String id, String status, Object result, String error) {}
