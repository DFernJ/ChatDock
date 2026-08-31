package com.DockerOps.dto.response;

public record ComposeNetworkDTO(String key, String name, String driver, boolean external) {}
