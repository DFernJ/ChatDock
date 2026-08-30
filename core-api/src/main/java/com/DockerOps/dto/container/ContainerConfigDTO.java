package com.DockerOps.dto.container;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.RestartPolicy;

import java.util.ArrayList;
import java.util.List;

public record ContainerConfigDTO(
        String id,
        Long memoryBytes,
        Long nanoCPUs,
        String restartPolicyName,
        Integer restartPolicyMaxRetryCount,
        List<String> networks,
        List<ContainerVolumeDTO> volumes
) {

    public static ContainerConfigDTO from(String id, InspectContainerResponse inspect) {
        HostConfig hostConfig = inspect.getHostConfig();
        RestartPolicy restartPolicy = hostConfig.getRestartPolicy();
        List<String> networks = new ArrayList<>(inspect.getNetworkSettings().getNetworks().keySet());
        return new ContainerConfigDTO(
                id,
                hostConfig.getMemory(),
                hostConfig.getNanoCPUs(),
                restartPolicy != null ? restartPolicy.getName() : null,
                restartPolicy != null ? restartPolicy.getMaximumRetryCount() : null,
                networks,
                ContainerVolumeDTO.listFrom(inspect)
        );
    }
}
