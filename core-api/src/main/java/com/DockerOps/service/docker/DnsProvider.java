package com.DockerOps.service.docker;

/**
 * Publishes and removes the public DNS record of an imported app's subdomain.
 * Selected with {@code app.dns.provider} ({@code cloudflare} or {@code none}).
 */
public interface DnsProvider {

    void createRecord(String subdomain);

    void deleteRecord(String subdomain);
}
