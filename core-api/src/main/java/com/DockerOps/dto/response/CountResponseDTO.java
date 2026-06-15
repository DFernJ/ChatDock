package com.DockerOps.dto.response;

public record CountResponseDTO(
        int containers,
        int images,
        int volumes,
        int networks
) {}
