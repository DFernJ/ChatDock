package com.DockerOps.service.docker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * No per-app DNS management. Meant for setups that rely on a wildcard record
 * (e.g. {@code *.example.com -> server IP}) created once by hand, such as the
 * direct edge layer that does not go through Cloudflare.
 */
@Service
@ConditionalOnProperty(name = "app.dns.provider", havingValue = "none")
public class NoopDnsProvider implements DnsProvider {

    @Override
    public void createRecord(String subdomain) {
        // Nothing to do: the wildcard DNS record already covers every subdomain.
    }

    @Override
    public void deleteRecord(String subdomain) {
        // Nothing to do.
    }
}
