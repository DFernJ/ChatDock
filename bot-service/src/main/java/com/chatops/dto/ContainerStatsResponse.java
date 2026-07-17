package com.chatops.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContainerStatsResponse(
        double cpuPercent,
        double memPercent,
        long memUsedBytes,
        long memLimitBytes,
        long diskReadBytes,
        long diskWriteBytes
) {
}
