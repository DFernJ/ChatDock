package com.DockerOps.dto.container;

import com.github.dockerjava.api.model.Statistics;

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
) {

    public static ContainerStatsDTO from(String id, Statistics s) {
        long cpuDelta = s.getCpuStats().getCpuUsage().getTotalUsage()
                - s.getPreCpuStats().getCpuUsage().getTotalUsage();
        long systemDelta = s.getCpuStats().getSystemCpuUsage()
                - s.getPreCpuStats().getSystemCpuUsage();
        long numCpus = s.getCpuStats().getOnlineCpus();
        double cpuPercent = (cpuDelta / (double) systemDelta) * 100.0 * numCpus;

        long used = s.getMemoryStats().getUsage();
        long limit = s.getMemoryStats().getLimit();
        double memPercent = (used / (double) limit) * 100.0;

        long blkRead = 0, blkWrite = 0;
        for (var entry : s.getBlkioStats().getIoServiceBytesRecursive()) {
            if ("Read".equalsIgnoreCase(entry.getOp())) blkRead += entry.getValue();
            if ("Write".equalsIgnoreCase(entry.getOp())) blkWrite += entry.getValue();
        }
        return new ContainerStatsDTO(
                id,
                cpuPercent,
                Math.round(memPercent * 10.0) / 10.0,
                used, limit,
                blkRead, blkWrite,
                Instant.now()
        );
    }
}
