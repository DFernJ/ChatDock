package com.DockerOps.controller;

import com.DockerOps.dto.volume.VolumeDTO;
import com.DockerOps.service.docker.VolumeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/docker")
public class VolumeController {

    @Autowired
    private VolumeService volumeService;

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/volumes")
    public List<VolumeDTO> getVolumes() {
        log.info("Listing volumes");
        return volumeService.listVolumes();
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/volumes/{name}")
    public VolumeDTO getVolumeInfo(@PathVariable String name) {
        log.info("Fetching volume name={}", name);
        return volumeService.getVolume(name);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/volumes")
    public ResponseEntity<VolumeDTO> createVolume(
            @RequestParam String name,
            @RequestParam(defaultValue = "local") String driver) {
        log.info("Creating volume name={} driver={}", name, driver);
        return ResponseEntity.ok(volumeService.createVolume(name, driver));
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @DeleteMapping("/volumes/{name}")
    public ResponseEntity<String> deleteVolume(@PathVariable String name) {
        log.info("Deleting volume name={}", name);
        volumeService.deleteVolume(name);
        return ResponseEntity.ok("Volume deleted successfully");
    }
}
