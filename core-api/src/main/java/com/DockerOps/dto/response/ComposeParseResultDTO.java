package com.DockerOps.dto.response;

import java.util.List;

public record ComposeParseResultDTO(List<ComposeServiceDTO> services, List<ComposeNetworkDTO> networks) {}
