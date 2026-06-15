package com.DockerOps.dto.container;

import java.util.List;

public record MinifiedContainerMountsDTO(
        String name,
        List<String> mounts
) {}
