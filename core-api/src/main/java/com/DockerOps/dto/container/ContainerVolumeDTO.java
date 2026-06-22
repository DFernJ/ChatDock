package com.DockerOps.dto.container;

public record ContainerVolumeDTO(String volumeName, String target, boolean readOnly) {}
