package com.DockerOps.dto.request;

public record PortMappingDTO(Integer hostPort, Integer containerPort, String protocol) {}
