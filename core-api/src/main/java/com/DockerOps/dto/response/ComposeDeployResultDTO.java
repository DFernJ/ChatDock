package com.DockerOps.dto.response;

import java.util.List;

public record ComposeDeployResultDTO(String stackName, List<String> createdContainers) {}
