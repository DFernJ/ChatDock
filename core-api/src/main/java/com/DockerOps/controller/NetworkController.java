package com.DockerOps.controller;

import com.DockerOps.dto.network.NetworkDTO;
import com.DockerOps.service.docker.NetworkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/docker")
public class NetworkController {

    @Autowired
    private NetworkService networkService;

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/networks")
    public List<NetworkDTO> getNetworks() {
        log.info("Listing networks");
        return networkService.listNetworks();
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/networks/{networkId}")
    public NetworkDTO getNetworkInfo(@PathVariable String networkId) {
        log.info("Fetching network id={}", networkId);
        return networkService.getNetwork(networkId);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/networks")
    public ResponseEntity<NetworkDTO> createNetwork(
            @RequestParam String name,
            @RequestParam(defaultValue = "bridge") String driver) {
        log.info("Creating network name={} driver={}", name, driver);
        return ResponseEntity.ok(networkService.createNetwork(name, driver));
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @DeleteMapping("/networks/{networkId}")
    public ResponseEntity<String> deleteNetwork(@PathVariable String networkId) {
        log.info("Deleting network id={}", networkId);
        networkService.deleteNetwork(networkId);
        return ResponseEntity.ok("Network deleted successfully");
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/networks/{networkId}/connect")
    public ResponseEntity<String> connectContainerToNetwork(
            @PathVariable String networkId,
            @RequestParam String containerId) {
        log.info("Connecting container id={} to network id={}", containerId, networkId);
        networkService.connectContainer(networkId, containerId);
        return ResponseEntity.ok("Container connected successfully");
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/networks/{networkId}/disconnect")
    public ResponseEntity<String> disconnectContainerFromNetwork(
            @PathVariable String networkId,
            @RequestParam String containerId,
            @RequestParam(defaultValue = "false") boolean force) {
        log.info("Disconnecting container id={} from network id={} (force={})", containerId, networkId, force);
        networkService.disconnectContainer(networkId, containerId, force);
        return ResponseEntity.ok("Container disconnected successfully");
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @PostMapping("/networks/prune")
    public ResponseEntity<String> pruneNetworks() {
        log.info("Pruning unused networks");
        int networksPruned = networkService.pruneNetworks();
        return ResponseEntity.ok("Networks pruned successfully. Networks pruned: " + networksPruned);
    }
}
