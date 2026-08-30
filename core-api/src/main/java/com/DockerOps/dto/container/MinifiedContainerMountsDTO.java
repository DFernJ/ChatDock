package com.DockerOps.dto.container;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerMount;

import java.util.ArrayList;
import java.util.List;

public record MinifiedContainerMountsDTO(
        String name,
        List<String> mounts
) {

    public static List<MinifiedContainerMountsDTO> listFor(String volumeName, List<Container> containers) {
        List<MinifiedContainerMountsDTO> mountPoints = new ArrayList<>();
        for (Container container : containers) {
            List<String> mounts = formatMounts(volumeName, container.getMounts().toArray(new ContainerMount[0]));
            if (!mounts.isEmpty()) {
                mountPoints.add(new MinifiedContainerMountsDTO(container.getNames()[0].replace("/", ""), mounts));
            }
        }
        return mountPoints;
    }

    private static List<String> formatMounts(String volumeName, ContainerMount[] mounts) {
        List<String> responseMounts = new ArrayList<>();
        if (mounts != null) {
            for (ContainerMount m : mounts) {
                if (!volumeName.equals(m.getName())) continue;
                String formatted = String.format("mode:%s - %s:%s", m.getMode(), m.getSource(), m.getDestination());
                if (!responseMounts.contains(formatted)) {
                    responseMounts.add(formatted);
                }
            }
        }
        return responseMounts;
    }
}
