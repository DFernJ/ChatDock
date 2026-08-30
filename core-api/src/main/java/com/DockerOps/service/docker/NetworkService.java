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
            response.add(NetworkDTO.from(network, containers));
        }
        return response;
    }

    public NetworkDTO getNetwork(String networkId) {
        Network network = dockerClient.inspectNetworkCmd().withNetworkId(networkId).exec();
        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .withNetworkFilter(List.of(networkId))
                .exec();
        return NetworkDTO.from(network, containers);
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
}
