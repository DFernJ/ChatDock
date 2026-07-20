package com.DockerOps.service.docker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class CloudflareDnsService {

    private static final String API_BASE = "https://api.cloudflare.com/client/v4";

    @Value("${app.cloudflare.api-token}")
    private String apiToken;
    @Value("${app.cloudflare.zone-id}")
    private String zoneId;
    @Value("${app.cloudflare.tunnel-id}")
    private String tunnelId;
    @Value("${app.cloudflare.base-domain}")
    private String baseDomain;

    private final RestClient restClient = RestClient.create();

    public void createRecord(String subdomain) {
        String hostname = subdomain + "." + baseDomain;
        CloudflareResponse response = restClient.post()
                .uri(API_BASE + "/zones/{zoneId}/dns_records", zoneId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new DnsRecordRequest("CNAME", hostname, tunnelId + ".cfargotunnel.com", true, 1))
                .retrieve()
                .body(CloudflareResponse.class);
        if (response == null || !response.success()) {
            throw new IllegalStateException("Could not create the DNS record for '" + hostname + "'");
        }
    }

    public void deleteRecord(String subdomain) {
        String hostname = subdomain + "." + baseDomain;
        CloudflareListResponse list = restClient.get()
                .uri(API_BASE + "/zones/{zoneId}/dns_records?type=CNAME&name={hostname}", zoneId, hostname)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .retrieve()
                .body(CloudflareListResponse.class);
        if (list == null || list.result() == null) {
            return;
        }
        for (DnsRecord record : list.result()) {
            restClient.delete()
                    .uri(API_BASE + "/zones/{zoneId}/dns_records/{id}", zoneId, record.id())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    private record DnsRecordRequest(String type, String name, String content, boolean proxied, int ttl) {}

    private record DnsRecord(String id) {}

    private record CloudflareResponse(boolean success) {}

    private record CloudflareListResponse(boolean success, List<DnsRecord> result) {}
}
