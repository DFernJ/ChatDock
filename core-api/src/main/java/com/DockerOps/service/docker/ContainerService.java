package com.DockerOps.service.docker;

import com.DockerOps.dto.container.ContainerDTO;
import com.DockerOps.dto.container.ContainerStatsDTO;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Service
public class ContainerService {

    @Autowired
    private DockerClient dockerClient;

    public List<ContainerDTO> listContainers() {
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        List<ContainerDTO> containerResponse = new ArrayList<>();
        for (Container c : containers) {
            containerResponse.add(formatContainer(c));
        }
        return containerResponse;
    }

    public ContainerStatsDTO getStats(String id) {
        Statistics[] result = new Statistics[1];
        Throwable[] error = new Throwable[1];
        CountDownLatch latch = new CountDownLatch(1);
        dockerClient.statsCmd(id)
                .withNoStream(true)  // snapshot único, no stream
                .exec(new ResultCallback.Adapter<>() {
                    @Override
                    public void onNext(Statistics stats) {
                        result[0] = stats;
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        error[0] = throwable;
                        latch.countDown();
                    }
                });

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for stats", e);
        }

        if (error[0] != null) {
            throw (error[0] instanceof RuntimeException re) ? re : new RuntimeException(error[0]);
        }
        return mapToDTO(id, result[0]);
    }

    public void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
    }

    public void restartContainer(String containerId) {
        dockerClient.restartContainerCmd(containerId).exec();
    }

    public void stopContainer(String containerId) {
        dockerClient.stopContainerCmd(containerId).exec();
    }

    public byte[] getLogs(String containerId) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Throwable[] error = new Throwable[1];
        CountDownLatch latch = new CountDownLatch(1);

        dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withTimestamps(true)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        try {
                            outputStream.write(frame.getPayload());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        error[0] = throwable;
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for logs", e);
        }

        if (error[0] != null) {
            throw (error[0] instanceof RuntimeException re) ? re : new RuntimeException(error[0]);
        }
        return outputStream.toByteArray();
    }

    public void deleteContainer(String containerId, boolean force, boolean removeVolumes) {
        dockerClient.removeContainerCmd(containerId)
                .withForce(force)
                .withRemoveVolumes(removeVolumes)
                .exec();
    }

    public int countContainers() {
        return dockerClient.listContainersCmd().withShowAll(true).exec().size();
    }

    private ContainerDTO formatContainer(Container c) {
        return new ContainerDTO(
                c.getId(),
                c.getNames()[0].replace("/", ""),
                c.getImage(),
                formatPorts(c.getPorts()),
                c.getStatus(),
                c.getState(),
                formatNetworks(c.getNetworkSettings().getNetworks().keySet().toArray(new String[0])),
                formatMounts(c.getMounts().toArray(new ContainerMount[0]))

        );
    }

    private List<String> formatPorts(ContainerPort[] ports) {
        List<String> responsePorts = new ArrayList<>();
        if (ports != null) {
            for (ContainerPort port : ports) {
                String formatted = String.format("%d:%d/%s", port.getPublicPort(), port.getPrivatePort(), port.getType());
                if (!responsePorts.contains(formatted)) {
                    responsePorts.add(formatted);
                }
            }
        }
        return responsePorts;
    }

    private List<String> formatNetworks(String[] networks) {
        List<String> responseNetworks = new ArrayList<>();
        if (networks != null) {
            for (String n : networks) {
                if (!responseNetworks.contains(n)) {
                    responseNetworks.add(n);
                }
            }
        }
        return responseNetworks;
    }

    private List<String> formatMounts(ContainerMount[] mounts) {
        List<String> responseMounts = new ArrayList<>();
        if (mounts != null) {
            for (ContainerMount m : mounts) {
                String formatted = String.format("mode:%s - %s:%s", m.getMode(), m.getSource(), m.getDestination());
                if (!responseMounts.contains(formatted)) {
                    responseMounts.add(formatted);
                }
            }
        }
        return responseMounts;
    }

    private ContainerStatsDTO mapToDTO(String id, Statistics s) {
        // CPU %
        long cpuDelta = s.getCpuStats().getCpuUsage().getTotalUsage()
                - s.getPreCpuStats().getCpuUsage().getTotalUsage();
        long systemDelta = s.getCpuStats().getSystemCpuUsage()
                - s.getPreCpuStats().getSystemCpuUsage();
        long numCpus = s.getCpuStats().getOnlineCpus();
        double cpuPercent = (cpuDelta / (double) systemDelta) * 100.0 * numCpus;

        // RAM %
        long used = s.getMemoryStats().getUsage();
        long limit = s.getMemoryStats().getLimit();
        double memPercent = (used / (double) limit) * 100.0;

        // Disco I/O (bytes leídos/escritos acumulados)
        long blkRead = 0, blkWrite = 0;
        for (var entry : s.getBlkioStats().getIoServiceBytesRecursive()) {
            if ("Read".equalsIgnoreCase(entry.getOp()))  blkRead  += entry.getValue();
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
