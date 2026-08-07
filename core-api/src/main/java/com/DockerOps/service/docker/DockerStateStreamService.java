package com.DockerOps.service.docker;

import com.DockerOps.dto.container.ContainerDTO;
import com.DockerOps.dto.container.ContainerStatsDTO;
import com.DockerOps.dto.image.ImageDTO;
import com.DockerOps.dto.network.NetworkDTO;
import com.DockerOps.dto.response.StackResponse;
import com.DockerOps.dto.volume.VolumeDTO;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class DockerStateStreamService {

    private static final long BROADCAST_DELAY_MS = 4_000;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private final ExecutorService sendExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    private ContainerService containerService;
    @Autowired
    private VolumeService volumeService;
    @Autowired
    private NetworkService networkService;
    @Autowired
    private ImageService imageService;

    private record DockerState(
            List<ContainerDTO> containers,
            List<StackResponse> stacks,
            List<ContainerStatsDTO> stats,
            List<VolumeDTO> volumes,
            List<NetworkDTO> networks,
            List<ImageDTO> images
    ) {}

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            sendState(emitter, collectState());
        } catch (RuntimeException e) {
            log.warn("Failed to collect Docker state for new SSE subscriber: {}", e.getMessage());
        }
        return emitter;
    }

    @Scheduled(fixedDelay = BROADCAST_DELAY_MS)
    public void broadcastState() {
        if (emitters.isEmpty()) return;

        DockerState state;
        try {
            state = collectState();
        } catch (RuntimeException e) {
            log.warn("Failed to collect Docker state for SSE broadcast: {}", e.getMessage());
            return;
        }

        for (SseEmitter emitter : emitters) {
            sendExecutor.execute(() -> sendState(emitter, state));
        }
    }

    @PreDestroy
    public void shutdown() {
        sendExecutor.shutdown();
    }

    private DockerState collectState() {
        return new DockerState(
                containerService.listContainers(),
                containerService.listStacks(),
                containerService.listStats(),
                volumeService.listVolumes(),
                networkService.listNetworks(),
                imageService.listImages()
        );
    }

    private void sendState(SseEmitter emitter, DockerState state) {
        try {
            emitter.send(SseEmitter.event().name("containers").data(state.containers()));
            emitter.send(SseEmitter.event().name("stacks").data(state.stacks()));
            emitter.send(SseEmitter.event().name("stats").data(state.stats()));
            emitter.send(SseEmitter.event().name("volumes").data(state.volumes()));
            emitter.send(SseEmitter.event().name("networks").data(state.networks()));
            emitter.send(SseEmitter.event().name("images").data(state.images()));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
    }
}
