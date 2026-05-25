package com.DockerOps.controller;

import com.DockerOps.dto.container.ContainerDTO;
import com.DockerOps.dto.container.ContainerStatsDTO;
import com.DockerOps.service.DockerService;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DockerController {

    @Autowired
    private DockerService dockerService;

    @GetMapping("/docker/containers")
    public List<ContainerDTO> getContainers() {
        return dockerService.listContainers();
    }

    @GetMapping("/docker/containers/{containerId}")
    public ContainerStatsDTO getContainerInfo(@PathVariable String containerId) {
        return dockerService.getStats(containerId);
    }

    @PostMapping("/docker/containers/{containerId}/start")
    public ResponseEntity<String> startContainer(@PathVariable String containerId) {
        dockerService.startContainer(containerId);
        return ResponseEntity.ok("Container started successfully");
    }

    @PostMapping("/docker/containers/{containerId}/stop")
    public ResponseEntity<String> stopContainer(@PathVariable String containerId) {
        dockerService.stopContainer(containerId);
        return ResponseEntity.ok("Container stopped successfully");
    }

     @PostMapping("/docker/containers/{containerId}/restart")
    public ResponseEntity<String> restartContainer(@PathVariable String containerId) {
                dockerService.restartContainer(containerId);
                return ResponseEntity.ok("Container restarted successfully");
    }

    @PostMapping("/docker/containers/{containerId}/logs")
    public ResponseEntity<byte[]> getLogFile(@PathVariable String containerId) {
        byte[] logs = dockerService.getLogs(containerId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + containerId + ".log\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(logs);
    }

    @DeleteMapping("/docker/containers/{containerId}/delete")
    public ResponseEntity<?> deleteContainer(
            @PathVariable String containerId,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(defaultValue = "false") boolean removeVolumes) {
        dockerService.deleteContainer(containerId, force, removeVolumes);
        return ResponseEntity.ok("Container delete successfully");
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
