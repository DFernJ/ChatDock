package com.DockerOps.dto.request;

public record CloneRepositoryRequest(String importId, String repository, String ref) {}
