package com.DockerOps.dto.volume;

import com.DockerOps.dto.container.MinifiedContainerMountsDTO;

import java.util.List;

public record VolumeDTO(
        String name,
        String driver,
        int usedInContainers,
        List<MinifiedContainerMountsDTO> mountPoints
) {}
