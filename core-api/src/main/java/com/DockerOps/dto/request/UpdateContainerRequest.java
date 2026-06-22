package com.DockerOps.dto.request;

import com.DockerOps.dto.container.ContainerVolumeDTO;

import java.util.List;

public record UpdateContainerRequest(
        Long memoryBytes,
        Long nanoCPUs,
        String restartPolicyName,
        Integer restartPolicyMaxRetryCount,
        List<String> networks,
        List<ContainerVolumeDTO> volumes
) {}
