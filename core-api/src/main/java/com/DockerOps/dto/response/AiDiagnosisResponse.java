package com.DockerOps.dto.response;

import java.time.Instant;

public record AiDiagnosisResponse(String diagnosis, Instant generatedAt, boolean cached) {}
