package com.DockerOps.service.docker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SubdomainRoutingService {

    @Autowired
    private NginxConfigService nginxConfigService;
    @Autowired
    private DnsProvider dnsProvider;

    public void provision(String subdomain, String containerName, int port) {
        nginxConfigService.writeConfig(subdomain, containerName, port);
        try {
            dnsProvider.createRecord(subdomain);
        } catch (RuntimeException e) {
            nginxConfigService.removeConfig(subdomain);
            throw e;
        }
    }

    public void deprovision(String subdomain) {
        try {
            nginxConfigService.removeConfig(subdomain);
        } catch (RuntimeException e) {
            log.warn("Could not remove the Nginx config for subdomain '{}'", subdomain, e);
        }
        try {
            dnsProvider.deleteRecord(subdomain);
        } catch (RuntimeException e) {
            log.warn("Could not delete the DNS record for subdomain '{}'", subdomain, e);
        }
    }
}
