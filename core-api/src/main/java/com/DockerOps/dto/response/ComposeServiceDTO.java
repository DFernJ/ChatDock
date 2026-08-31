package com.DockerOps.dto.response;

import com.DockerOps.dto.container.ContainerVolumeDTO;
import com.DockerOps.dto.request.HealthcheckDTO;
import com.DockerOps.dto.request.PortMappingDTO;
import com.DockerOps.dto.request.SecretDraftDTO;

import java.util.List;

public record ComposeServiceDTO(
        String name,
        String image,
        String buildSubdir,
        String dockerfile,
        List<PortMappingDTO> ports,
        List<ContainerVolumeDTO> volumes,
        List<String> dependsOn,
        String restartPolicy,
        Integer restartPolicyMaxRetryCount,
        List<SecretDraftDTO> secrets,
        List<String> networks,
        List<String> command,
        List<String> entrypoint,
        HealthcheckDTO healthcheck,
        boolean supported,
        String unsupportedReason
) {}
