package com.DockerOps.dto.image;

public record DockerHubImageDTO(
        String name,
        String description,
        boolean official,
        boolean automated,
        long starCount
) {}
