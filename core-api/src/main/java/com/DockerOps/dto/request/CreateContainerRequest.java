package com.DockerOps.dto.request;

import com.DockerOps.dto.container.ContainerVolumeDTO;

import java.util.List;

public record CreateContainerRequest(
        String image,
        String name,
        String subdomain,
        boolean stdin,
        String restartPolicyName,
        Integer restartPolicyMaxRetryCount,
        List<String> networks,
        List<ContainerVolumeDTO> volumes,
        List<PortMappingDTO> ports,
        HealthcheckDTO healthcheck,
        List<String> command,
        List<String> entrypoint,
        List<SecretDraftDTO> secrets,
        String stackName
) {}
