package com.DockerOps.dto.response;

import java.util.List;

public record ImportResultDTO(String kind, String image, String message, List<ComposeServiceDTO> services) {}
