package com.DockerOps.dto.container;

import com.github.dockerjava.api.command.InspectContainerResponse;

import java.util.ArrayList;
import java.util.List;

public record ContainerVolumeDTO(String volumeName, String target, boolean readOnly) {

    public static List<ContainerVolumeDTO> listFrom(InspectContainerResponse inspect) {
        List<ContainerVolumeDTO> volumes = new ArrayList<>();
        if (inspect.getMounts() == null) return volumes;
        for (InspectContainerResponse.Mount m : inspect.getMounts()) {
            if (m.getName() == null || m.getDestination() == null) continue;
            boolean readOnly = Boolean.FALSE.equals(m.getRW());
            volumes.add(new ContainerVolumeDTO(m.getName(), m.getDestination().getPath(), readOnly));
        }
        return volumes;
    }
}
