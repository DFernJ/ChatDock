package com.DockerOps.service.ai;

import com.DockerOps.dto.response.AiDiagnosisResponse;
import com.DockerOps.model.apps.App;
import com.DockerOps.repository.apps.AppRepository;
import com.DockerOps.service.docker.ContainerFailureTracker;
import com.DockerOps.service.docker.ContainerService;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

@Service
public class AiDiagnosisService {

    private static final int MAX_LOG_BYTES = 10 * 1024 * 1024;

    private static final String SYSTEM_PROMPT = """
            You are a senior site reliability engineer diagnosing a Docker container failure from its logs.

            Rules:
            - Base your answer strictly on the log content provided. Never invent errors, stack traces, file paths, \
            line numbers or root causes that aren't evidenced in the text.
            - If the logs don't contain enough information to identify a cause, say so explicitly instead of guessing.
            - Quote or reference the specific log lines that support each conclusion.

            Respond in exactly this format:
            ## Likely cause
            <one or two sentences>

            ## Evidence
            <the specific log lines that support your conclusion>

            ## Suggested fix
            <concrete, actionable steps>

            ## Confidence
            <low | medium | high, with a one-sentence justification>
            """;

    @Autowired
    private ContainerService containerService;
    @Autowired
    private AppRepository appRepository;
    @Autowired
    private ContainerFailureTracker containerFailureTracker;
    @Value("${spring.ai.google.genai.api-key}")
    private String API_KEY;
    @Value("${spring.ai.google.genai.chat.options.model}")
    private String MODEL;

    private Client client;

    @PostConstruct
    private void init() {
        this.client = Client.builder().apiKey(API_KEY).build();
    }

    public AiDiagnosisResponse diagnose(String containerId) {
        if (!containerFailureTracker.hasFailedRecently(containerId)) {
            throw new IllegalArgumentException("This container hasn't failed recently — nothing to diagnose");
        }

        Optional<String> cached = containerFailureTracker.getCachedDiagnosis(containerId);
        if (cached.isPresent()) {
            return new AiDiagnosisResponse(cached.get(), Instant.now(), true);
        }

        Optional<String> persisted = getPersistedDiagnosisForCurrentFailure(containerId);
        if (persisted.isPresent()) {
            containerFailureTracker.cacheDiagnosis(containerId, persisted.get());
            return new AiDiagnosisResponse(persisted.get(), Instant.now(), true);
        }

        byte[] logs = containerService.getLogs(containerId);
        byte[] tail = tailBytes(logs, MAX_LOG_BYTES);
        String logsText = new String(tail, StandardCharsets.UTF_8);

        if (logsText.isBlank()) {
            throw new IllegalArgumentException("This container has no logs to analyze");
        }
        GenerateContentResponse response = client.models.generateContent(MODEL, SYSTEM_PROMPT + logsText, null);
        String diagnosis = response.text();

        containerFailureTracker.cacheDiagnosis(containerId, diagnosis);
        persistDiagnosis(containerId, diagnosis);

        return new AiDiagnosisResponse(diagnosis, Instant.now(), false);
    }

    private byte[] tailBytes(byte[] data, int maxBytes) {
        if (data.length <= maxBytes) return data;
        return Arrays.copyOfRange(data, data.length - maxBytes, data.length);
    }

    private Optional<String> getPersistedDiagnosisForCurrentFailure(String containerId) {
        Optional<Instant> currentFinishedAt = containerFailureTracker.getFinishedAt(containerId);
        if (currentFinishedAt.isEmpty()) {
            return Optional.empty();
        }
        return containerService.findLinkedApp(containerId)
                .filter(app -> app.getLastAIDiagnosis() != null)
                .filter(app -> currentFinishedAt.get().equals(app.getLastAIDiagnosisFinishedAt()))
                .map(App::getLastAIDiagnosis);
    }

    private void persistDiagnosis(String containerId, String diagnosis) {
        Optional<Instant> finishedAt = containerFailureTracker.getFinishedAt(containerId);
        containerService.findLinkedApp(containerId).ifPresent(app -> {
            app.setLastAIDiagnosis(diagnosis);
            app.setLastAIDiagnosisTimestamp(Instant.now());
            app.setLastAIDiagnosisFinishedAt(finishedAt.orElse(null));
            appRepository.save(app);
        });
    }
}
