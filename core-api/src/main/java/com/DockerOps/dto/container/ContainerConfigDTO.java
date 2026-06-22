package com.DockerOps.dto.container;

import java.util.List;

public record ContainerConfigDTO(
        String id,
        Long memoryBytes,
        Long nanoCPUs,
        String restartPolicyName,
        Integer restartPolicyMaxRetryCount,
        List<String> networks,
        List<ContainerVolumeDTO> volumes
) {}
