package com.DockerOps.service.docker;

import com.DockerOps.dto.network.NetworkDTO;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NetworkService {

    @Autowired
    private DockerClient dockerClient;

    public List<NetworkDTO> listNetworks() {
        List<Network> networks = dockerClient.listNetworksCmd().exec();
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        List<NetworkDTO> response = new ArrayList<>();
        for (Network network : networks) {
            response.add(formatNetwork(network, containers));
        }
        return response;
    }

    public NetworkDTO getNetwork(String networkId) {
        Network network = dockerClient.inspectNetworkCmd().withNetworkId(networkId).exec();
        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .withNetworkFilter(List.of(networkId))
                .exec();
        return formatNetwork(network, containers);
    }

    public NetworkDTO createNetwork(String name, String driver) {
        CreateNetworkResponse created = dockerClient.createNetworkCmd()
                .withName(name)
                .withDriver(driver)
                .exec();
        return getNetwork(created.getId());
    }

    public void deleteNetwork(String networkId) {
        dockerClient.removeNetworkCmd(networkId).exec();
    }

    public void connectContainer(String networkId, String containerId) {
        dockerClient.connectToNetworkCmd()
                .withNetworkId(networkId)
                .withContainerId(containerId)
                .exec();
    }

    public void disconnectContainer(String networkId, String containerId, boolean force) {
        dockerClient.disconnectFromNetworkCmd()
                .withNetworkId(networkId)
                .withContainerId(containerId)
                .withForce(force)
                .exec();
    }

    public int pruneNetworks() {
        int numNetworksBeforePrune = dockerClient.listNetworksCmd().exec().size();
        dockerClient.pruneCmd(PruneType.NETWORKS).exec();
        int numNetworksAfterPrune = dockerClient.listNetworksCmd().exec().size();
        return numNetworksBeforePrune - numNetworksAfterPrune;
    }

    public int countNetworks() {
        return dockerClient.listNetworksCmd().exec().size();
    }

    private NetworkDTO formatNetwork(Network network, List<Container> containers) {
        return new NetworkDTO(
                network.getId(),
                network.getName(),
                network.getDriver(),
                network.getScope(),
                countAttachedToContainer(network.getId(), containers),
                formatConnectedContainers(network.getId(), containers)
        );
    }

    private boolean isAttachedToNetwork(Container container, String networkId) {
        for (ContainerNetwork containerNetwork : container.getNetworkSettings().getNetworks().values()) {
            if (networkId.equals(containerNetwork.getNetworkID())) {
                return true;
            }
        }
        return false;
    }

    private int countAttachedToContainer(String networkId, List<Container> containers) {
        int count = 0;
        for (Container container : containers) {
            if (isAttachedToNetwork(container, networkId)) {
                count++;
            }
        }
        return count;
    }

    private List<String> formatConnectedContainers(String networkId, List<Container> containers) {
        List<String> connected = new ArrayList<>();
        for (Container container : containers) {
            if (isAttachedToNetwork(container, networkId)) {
                connected.add(container.getNames()[0].replace("/", ""));
            }
        }
        return connected;
    }
}
