package com.DockerOps.dto.response;

import com.DockerOps.dto.container.ContainerVolumeDTO;
import com.DockerOps.dto.request.PortMappingDTO;
import com.DockerOps.dto.request.SecretDraftDTO;

import java.util.List;

public record ComposeServiceDTO(
        String name,
        String image,
        String buildSubdir,
        List<PortMappingDTO> ports,
        List<ContainerVolumeDTO> volumes,
        List<String> dependsOn,
        String restartPolicy,
        List<SecretDraftDTO> secrets,
        boolean supported,
        String unsupportedReason
) {}
