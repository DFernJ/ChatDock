package com.DockerOps.service.docker;

import com.DockerOps.dto.container.MinifiedContainerMountsDTO;
import com.DockerOps.dto.volume.VolumeDTO;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateVolumeResponse;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerMount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VolumeService {

    @Autowired
    private DockerClient dockerClient;

    public List<VolumeDTO> listVolumes() {
        List<InspectVolumeResponse> volumes = dockerClient.listVolumesCmd().exec().getVolumes();
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        List<VolumeDTO> response = new ArrayList<>();
        for (InspectVolumeResponse volume : volumes) {
            response.add(new VolumeDTO(volume.getName(), volume.getDriver(), countUsedInContainers(volume.getName(), containers), formatMountPoints(containers)));
        }
        return response;
    }

    public VolumeDTO getVolume(String name) {
        InspectVolumeResponse volume = dockerClient.inspectVolumeCmd(name).exec();
        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .withVolumeFilter(List.of(name))
                .exec();
        return new VolumeDTO(volume.getName(), volume.getDriver(), containers.size(), formatMountPoints(containers));
    }

    public VolumeDTO createVolume(String name, String driver) {
        CreateVolumeResponse volume = dockerClient.createVolumeCmd()
                .withName(name)
                .withDriver(driver)
                .exec();
        return new VolumeDTO(volume.getName(), volume.getDriver(), 0, List.of());
    }

    public void deleteVolume(String name) {
        dockerClient.removeVolumeCmd(name).exec();
    }

    public int countVolumes() {
        return dockerClient.listVolumesCmd().withDanglingFilter(true).exec().getVolumes().size();
    }

    private int countUsedInContainers(String volumeName, List<Container> containers) {
        int count = 0;
        for (Container container : containers) {
            for (ContainerMount mount : container.getMounts()) {
                if (volumeName.equals(mount.getName())) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private List<MinifiedContainerMountsDTO> formatMountPoints(List<Container> containers) {
        List<MinifiedContainerMountsDTO> mountPoints = new ArrayList<>();
        for (Container container : containers) {
            mountPoints.add(new MinifiedContainerMountsDTO(container.getNames()[0].replace("/", ""), formatMounts(container.getMounts().toArray(new ContainerMount[0]))));
        }
        return mountPoints;
    }

    private List<String> formatMounts(ContainerMount[] mounts) {
        List<String> responseMounts = new ArrayList<>();
        if (mounts != null) {
            for (ContainerMount m : mounts) {
                String formatted = String.format("mode:%s - %s:%s", m.getMode(), m.getSource(), m.getDestination());
                if (!responseMounts.contains(formatted)) {
                    responseMounts.add(formatted);
                }
            }
        }
        return responseMounts;
    }
}
