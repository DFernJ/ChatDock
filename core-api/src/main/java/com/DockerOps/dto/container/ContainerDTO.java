package com.DockerOps.dto.container;

import java.util.List;

public record ContainerDTO(
        String id,
        String name,
        String image,
        List<String> ports,
        String status,
        String state,
        List<String> networks,
        List<String> mounts,
        boolean essential,
        String stackName,
        boolean failed,
        String subdomainUrl
) {}
