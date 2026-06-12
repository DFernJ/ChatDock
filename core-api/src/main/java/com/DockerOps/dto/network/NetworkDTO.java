package com.DockerOps.dto.network;


import java.util.List;

public record NetworkDTO(
        String id,
        String name,
        String driver,
        String scope,
        int attachedToContainers,
        List<String> connectedContainers
) {}
