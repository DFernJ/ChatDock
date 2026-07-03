package com.DockerOps.dto.request;

import java.util.List;

public record ComposeServiceOverride(String subdomain, boolean stdin, List<SecretDraftDTO> secrets) {}
