package com.chatops.service;

import com.chatops.dto.ContainerStatsResponse;
import com.chatops.dto.ContainerSummaryDto;
import com.chatops.dto.DiagnosisResponse;
import com.chatops.dto.DiscordLinkRequest;
import com.chatops.dto.DiscordLinkResponse;
import com.chatops.dto.WhoAmIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
public class ApiService {

    private static final Logger log = LoggerFactory.getLogger(ApiService.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestTemplate restTemplate;
    @Value("${backend.url}")
    private String backendUrl;
    @Value("${security.internal-token}")
    private String internalToken;

    public ApiService() {
        this.restTemplate = new RestTemplate();
    }

    public List<String> fetchContainerNames() {
        log.info("Fetching container list from core-api");
        String url = backendUrl + "/api/internal/docker/containers";
        ContainerSummaryDto[] containers = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(internalHeaders()), ContainerSummaryDto[].class).getBody();
        if (containers == null) {
            return List.of();
        }
        return Arrays.stream(containers).map(ContainerSummaryDto::name).toList();
    }

    public String linkDiscordAccount(String code, long discordId, String discordUsername) {
        log.info("Requesting Discord account link for discordId={}, username={}", discordId, discordUsername);
        String url = backendUrl + "/api/internal/discord/link";
        DiscordLinkRequest request = new DiscordLinkRequest(code, discordId, discordUsername);
        DiscordLinkResponse response = restTemplate.postForObject(url, new HttpEntity<>(request, internalHeaders()), DiscordLinkResponse.class);
        return response != null ? response.username() : null;
    }

    public WhoAmIResponse fetchWhoAmI(long discordId) {
        log.info("Requesting whoami for discordId={}", discordId);
        String url = backendUrl + "/api/internal/discord/whoami?discordId=" + discordId;
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(internalHeaders()), WhoAmIResponse.class).getBody();
    }

    public void startContainer(String containerName, long discordId) {
        log.info("Requesting start of container '{}' for discordId={}", containerName, discordId);
        String url = containerActionUrl(containerName, "start", discordId);
        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(internalHeaders()), Void.class);
    }

    public void stopContainer(String containerName, long discordId) {
        log.info("Requesting stop of container '{}' for discordId={}", containerName, discordId);
        String url = containerActionUrl(containerName, "stop", discordId);
        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(internalHeaders()), Void.class);
    }

    public void restartContainer(String containerName, long discordId) {
        log.info("Requesting restart of container '{}' for discordId={}", containerName, discordId);
        String url = containerActionUrl(containerName, "restart", discordId);
        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(internalHeaders()), Void.class);
    }

    public void deleteContainer(String containerName, long discordId) {
        log.info("Requesting deletion of container '{}' for discordId={}", containerName, discordId);
        String url = backendUrl + "/api/internal/docker/containers/" + containerName + "?discordId=" + discordId;
        restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(internalHeaders()), Void.class);
    }

    public String fetchLogs(String containerName, long discordId) {
        log.info("Requesting logs for container '{}' for discordId={}", containerName, discordId);
        String url = containerActionUrl(containerName, "logs", discordId);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(internalHeaders()), String.class).getBody();
    }

    public String fetchDiagnosis(String containerName, long discordId) {
        log.info("Requesting AI diagnosis for container '{}' for discordId={}", containerName, discordId);
        String url = containerActionUrl(containerName, "diagnosis", discordId);
        DiagnosisResponse response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(internalHeaders()), DiagnosisResponse.class).getBody();
        return response != null ? response.diagnosis() : null;
    }

    public ContainerStatsResponse fetchStats(String containerName, long discordId) {
        log.info("Requesting stats for container '{}' for discordId={}", containerName, discordId);
        String url = containerActionUrl(containerName, "stats", discordId);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(internalHeaders()), ContainerStatsResponse.class).getBody();
    }

    private String containerActionUrl(String containerName, String action, long discordId) {
        return backendUrl + "/api/internal/docker/containers/" + containerName + "/" + action + "?discordId=" + discordId;
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_TOKEN_HEADER, internalToken);
        return headers;
    }
}
