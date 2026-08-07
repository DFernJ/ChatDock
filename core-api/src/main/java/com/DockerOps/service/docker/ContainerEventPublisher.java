package com.DockerOps.service.docker;

import com.DockerOps.dto.response.ContainerFailureEvent;
import com.DockerOps.dto.response.ContainerLifecycleEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class ContainerEventPublisher {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final RestClient restClient;

    @Value("${app.bot-service.url}")
    private String botServiceUrl;
    @Value("${app.security.internal-token}")
    private String internalToken;

    public ContainerEventPublisher() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(3_000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    public void publishContainerFailure(UUID id, String containerId, String containerName, Long exitCode, Instant finishedAt, String message) {
        ContainerFailureEvent event = new ContainerFailureEvent(id, containerId, containerName, exitCode, finishedAt, message);
        broadcastToSse(event);
        sendWebhook(event);
    }

    private void broadcastToSse(ContainerFailureEvent event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("container-failure").data(event));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                emitter.completeWithError(e);
            }
        }
    }

    private void sendWebhook(ContainerFailureEvent event) {
        try {
            restClient.post()
                    .uri(botServiceUrl + "/api/internal/container-failure")
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Failed to deliver container-failure webhook to bot-service for container '{}': {}",
                    event.containerName(), e.getMessage());
        }
    }

    public void publishContainerCreated(String containerName) {
        sendLifecycleWebhook("/api/internal/container-created", containerName);
    }

    public void publishContainerDeleted(String containerName) {
        sendLifecycleWebhook("/api/internal/container-deleted", containerName);
    }

    private void sendLifecycleWebhook(String path, String containerName) {
        try {
            restClient.post()
                    .uri(botServiceUrl + path)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ContainerLifecycleEvent(containerName))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Failed to deliver {} webhook to bot-service for container '{}': {}", path, containerName, e.getMessage());
        }
    }
}
