package com.DockerOps.dto.container;

import java.time.Instant;

public record ContainerStatsDTO(
        String containerId,
        double cpuPercent,
        double memPercent,
        long memUsedBytes,
        long memLimitBytes,
        long diskReadBytes,
        long diskWriteBytes,
        Instant timestamp
) {}
