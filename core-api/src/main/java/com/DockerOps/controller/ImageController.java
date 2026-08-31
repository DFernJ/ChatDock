package com.DockerOps.controller;

import com.DockerOps.dto.image.DockerHubImageDTO;
import com.DockerOps.dto.image.ImageDTO;
import com.DockerOps.service.docker.ImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/docker")
public class ImageController {

    @Autowired
    private ImageService imageService;

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/images")
    public List<ImageDTO> getImages() {
        log.info("Listing images");
        return imageService.listImages();
    }

    @PreAuthorize("hasAuthority('PERM_VIEWER')")
    @GetMapping("/images/search")
    public List<DockerHubImageDTO> searchDockerHubImages(@RequestParam String query) {
        log.info("Searching Docker Hub for query={}", query);
        return imageService.searchDockerHub(query);
    }

    @PreAuthorize("hasAuthority('PERM_EDITOR')")
    @PostMapping("/images/pull")
    public ResponseEntity<String> pullImage(
            @RequestParam String repository,
            @RequestParam(defaultValue = "latest") String tag) {
        log.info("Pulling image repository={} tag={}", repository, tag);
        imageService.pullImage(repository, tag);
        return ResponseEntity.ok("Image pulled successfully");
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<String> deleteImage(
            @PathVariable String imageId,
            @RequestParam(defaultValue = "false") boolean force) {
        log.info("Deleting image id={} (force={})", imageId, force);
        imageService.deleteImage(imageId, force);
        return ResponseEntity.ok("Image deleted successfully");
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @PostMapping("/images/prune")
    public ResponseEntity<String> pruneImages() {
        log.info("Pruning unused images");
        long spaceReclaimed = imageService.pruneImages();
        return ResponseEntity.ok("Images pruned successfully. Space reclaimed: " + spaceReclaimed + " bytes");
    }
}
