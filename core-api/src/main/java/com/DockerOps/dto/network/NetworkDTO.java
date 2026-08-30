package com.DockerOps.dto.network;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.Network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record NetworkDTO(
        String id,
        String name,
        String driver,
        String scope,
        int attachedToContainers,
        List<String> connectedContainers
) {

    public static NetworkDTO from(Network network, List<Container> containers) {
        return new NetworkDTO(
                network.getId(),
                network.getName(),
                network.getDriver(),
                network.getScope(),
                countAttachedToContainer(network.getId(), network.getName(), containers),
                formatConnectedContainers(network.getId(), network.getName(), containers)
        );
    }

    private static boolean isAttachedToNetwork(Container container, String networkId, String networkName) {
        Map<String, ContainerNetwork> networks = container.getNetworkSettings().getNetworks();
        if (networks == null) return false;
        if (networks.containsKey(networkName)) return true;
        for (ContainerNetwork containerNetwork : networks.values()) {
            if (networkId.equals(containerNetwork.getNetworkID())) {
                return true;
            }
        }
        return false;
    }

    private static int countAttachedToContainer(String networkId, String networkName, List<Container> containers) {
        int count = 0;
        for (Container container : containers) {
            if (isAttachedToNetwork(container, networkId, networkName)) {
                count++;
            }
        }
        return count;
    }

    private static List<String> formatConnectedContainers(String networkId, String networkName, List<Container> containers) {
        List<String> connected = new ArrayList<>();
        for (Container container : containers) {
            if (isAttachedToNetwork(container, networkId, networkName)) {
                connected.add(container.getNames()[0].replace("/", ""));
            }
        }
        return connected;
    }
}
