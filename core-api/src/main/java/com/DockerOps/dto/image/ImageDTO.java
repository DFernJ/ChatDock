package com.DockerOps.dto.image;

public record ImageDTO(
        String id,
        String image,
        long diskUsage,
        int usedInContainers
) {}
