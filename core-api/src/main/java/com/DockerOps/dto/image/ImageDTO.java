package com.DockerOps.dto.image;

public record ImageDTO(
        String id,
        String image,
        long diskUsage,
        int usedInContainers
) {

    public static ImageDTO from(String id, String[] repoTags, Long size, Integer containers) {
        String image = (repoTags != null && repoTags.length > 0) ? repoTags[0] : "<none>:<none>";
        long diskUsage = size != null ? size : 0;
        int usedInContainers = (containers != null && containers >= 0) ? containers : 0;
        return new ImageDTO(id, image, diskUsage, usedInContainers);
    }
}
