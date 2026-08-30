package com.DockerOps.dto.container;

import com.github.dockerjava.api.model.ContainerMount;
import com.github.dockerjava.api.model.ContainerPort;

import java.util.ArrayList;
import java.util.List;

public record ContainerDTO(
        String id,
        String name,
        String image,
        List<String> ports,
        String status,
        String state,
        List<String> networks,
        List<String> mounts,
        boolean essential,
        String stackName,
        boolean failed,
        String subdomainUrl
) {

    public static List<String> formatPorts(ContainerPort[] ports) {
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

    public static List<String> formatNetworks(String[] networks) {
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

    public static List<String> formatMounts(ContainerMount[] mounts) {
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
}
