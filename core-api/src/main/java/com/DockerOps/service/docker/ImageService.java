package com.DockerOps.service.docker;

import com.DockerOps.dto.image.ImageDTO;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PruneResponse;
import com.github.dockerjava.api.model.PruneType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ImageService {

    @Autowired
    private DockerClient dockerClient;

    public List<ImageDTO> listImages() {
        List<Image> images = dockerClient.listImagesCmd().withShowAll(true).exec();
        List<ImageDTO> response = new ArrayList<>();
        for (Image image : images) {
            response.add(formatImage(image.getId(), image.getRepoTags(), image.getSize(), image.getContainers()));
        }
        return response;
    }

    public void pullImage(String repository, String tag) {
        try {
            dockerClient.pullImageCmd(repository)
                    .withTag(tag)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion(120, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while pulling image", e);
        }
    }

    public void deleteImage(String imageId, boolean force) {
        dockerClient.removeImageCmd(imageId)
                .withForce(force)
                .exec();
    }

    public long pruneImages() {
        PruneResponse response = dockerClient.pruneCmd(PruneType.IMAGES)
                .withDangling(false)
                .exec();
        Long reclaimed = response.getSpaceReclaimed();
        return reclaimed != null ? reclaimed : 0;
    }

    private ImageDTO formatImage(String id, String[] repoTags, Long size, Integer containers) {
        String image = (repoTags != null && repoTags.length > 0) ? repoTags[0] : "<none>:<none>";
        long diskUsage = size != null ? size : 0;
        int usedInContainers = (containers != null && containers >= 0) ? containers : 0;
        return new ImageDTO(id, image, diskUsage, usedInContainers);
    }
}
