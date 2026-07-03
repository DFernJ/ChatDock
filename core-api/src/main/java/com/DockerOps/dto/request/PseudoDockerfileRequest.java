package com.DockerOps.dto.request;

public record PseudoDockerfileRequest(String baseImage, String workdir, String buildCommand, String runCommand) {}
