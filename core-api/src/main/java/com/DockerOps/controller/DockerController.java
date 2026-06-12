package com.DockerOps.controller;

import com.DockerOps.dto.container.ContainerDTO;
import com.DockerOps.dto.container.ContainerStatsDTO;
import com.DockerOps.dto.image.ImageDTO;
import com.DockerOps.dto.network.NetworkDTO;
import com.DockerOps.dto.volume.VolumeDTO;
import com.DockerOps.service.docker.ContainerService;
import com.DockerOps.service.docker.ImageService;
import com.DockerOps.service.docker.NetworkService;
import com.DockerOps.service.docker.VolumeService;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/docker")
public class DockerController {

    @Autowired
    private ContainerService containerService;
    @Autowired
    private VolumeService volumeService;
    @Autowired
    private ImageService imageService;
    @Autowired
    private NetworkService networkService;

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/containers")
    public List<ContainerDTO> getContainers() {
        return containerService.listContainers();
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/containers/{containerId}")
    public ContainerStatsDTO getContainerInfo(@PathVariable String containerId) {
        return containerService.getStats(containerId);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/containers/{containerId}/start")
    public ResponseEntity<String> startContainer(@PathVariable String containerId) {
        containerService.startContainer(containerId);
        return ResponseEntity.ok("Container started successfully");
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/containers/{containerId}/stop")
    public ResponseEntity<String> stopContainer(@PathVariable String containerId) {
        containerService.stopContainer(containerId);
        return ResponseEntity.ok("Container stopped successfully");
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
     @PostMapping("/containers/{containerId}/restart")
    public ResponseEntity<String> restartContainer(@PathVariable String containerId) {
                containerService.restartContainer(containerId);
                return ResponseEntity.ok("Container restarted successfully");
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @PostMapping("/containers/{containerId}/logs")
    public ResponseEntity<byte[]> getLogFile(@PathVariable String containerId) {
        byte[] logs = containerService.getLogs(containerId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + containerId + ".log\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(logs);
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @DeleteMapping("/containers/{containerId}/delete")
    public ResponseEntity<?> deleteContainer(
            @PathVariable String containerId,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(defaultValue = "false") boolean removeVolumes) {
        containerService.deleteContainer(containerId, force, removeVolumes);
        return ResponseEntity.ok("Container delete successfully");
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/images")
    public List<ImageDTO> getImages() {
        return imageService.listImages();
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/images/pull")
    public ResponseEntity<String> pullImage(
            @RequestParam String repository,
            @RequestParam(defaultValue = "latest") String tag) {
        imageService.pullImage(repository, tag);
        return ResponseEntity.ok("Image pulled successfully");
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<String> deleteImage(
            @PathVariable String imageId,
            @RequestParam(defaultValue = "false") boolean force) {
        imageService.deleteImage(imageId, force);
        return ResponseEntity.ok("Image deleted successfully");
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @PostMapping("/images/prune")
    public ResponseEntity<String> pruneImages() {
        long spaceReclaimed = imageService.pruneImages();
        return ResponseEntity.ok("Images pruned successfully. Space reclaimed: " + spaceReclaimed + " bytes");
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/volumes")
    public List<VolumeDTO> getVolumes() {
        return volumeService.listVolumes();
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/volumes/{name}")
    public VolumeDTO getVolumeInfo(@PathVariable String name) {
        return volumeService.getVolume(name);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/volumes")
    public ResponseEntity<VolumeDTO> createVolume(
            @RequestParam String name,
            @RequestParam(defaultValue = "local") String driver) {
        return ResponseEntity.ok(volumeService.createVolume(name, driver));
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @DeleteMapping("/volumes/{name}")
    public ResponseEntity<String> deleteVolume(@PathVariable String name) {
        volumeService.deleteVolume(name);
        return ResponseEntity.ok("Volume deleted successfully");
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/networks")
    public List<NetworkDTO> getNetworks() {
        return networkService.listNetworks();
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/networks/{networkId}")
    public NetworkDTO getNetworkInfo(@PathVariable String networkId) {
        return networkService.getNetwork(networkId);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/networks")
    public ResponseEntity<NetworkDTO> createNetwork(
            @RequestParam String name,
            @RequestParam(defaultValue = "bridge") String driver) {
        return ResponseEntity.ok(networkService.createNetwork(name, driver));
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @DeleteMapping("/networks/{networkId}")
    public ResponseEntity<String> deleteNetwork(@PathVariable String networkId) {
        networkService.deleteNetwork(networkId);
        return ResponseEntity.ok("Network deleted successfully");
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/networks/{networkId}/connect")
    public ResponseEntity<String> connectContainerToNetwork(
            @PathVariable String networkId,
            @RequestParam String containerId) {
        networkService.connectContainer(networkId, containerId);
        return ResponseEntity.ok("Container connected successfully");
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/networks/{networkId}/disconnect")
    public ResponseEntity<String> disconnectContainerFromNetwork(
            @PathVariable String networkId,
            @RequestParam String containerId,
            @RequestParam(defaultValue = "false") boolean force) {
        networkService.disconnectContainer(networkId, containerId, force);
        return ResponseEntity.ok("Container disconnected successfully");
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @PostMapping("/networks/prune")
    public ResponseEntity<String> pruneNetworks() {
        int networksPruned = networkService.pruneNetworks();
        return ResponseEntity.ok("Networks pruned successfully. Networks pruned: " + networksPruned);
    }

    @ExceptionHandler(DockerException.class)
    public ResponseEntity<String> handleDockerException(DockerException e) {
        return ResponseEntity.status(e.getHttpStatus()).body(e.getMessage());
    }

    @ExceptionHandler(DockerClientException.class)
    public ResponseEntity<String> handleDockerClientException(DockerClientException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Docker client error: " + e.getMessage());
    }

}
