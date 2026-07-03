package com.DockerOps.dto.request;

import java.util.Map;

public record DeployComposeRequest(String projectName, Map<String, ComposeServiceOverride> services) {}
