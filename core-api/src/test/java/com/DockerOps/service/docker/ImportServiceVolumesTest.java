package com.DockerOps.service.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImportServiceVolumesTest {

    private final VolumeService volumes = mock(VolumeService.class);

    @Test
    void anExistingVolumeIsReusedAndNeverRecordedAsCreated() {
        when(volumes.volumeExists("Gifted-db-data")).thenReturn(true);
        List<String> created = new ArrayList<>();

        ImportService.createMissingVolumes(volumes, List.of("Gifted-db-data"), created);

        verify(volumes, never()).createVolume(anyString(), anyString());
        // Si no está en la lista, el rollback de un despliegue fallido no puede borrar sus datos
        assertThat(created).isEmpty();
    }

    @Test
    void aMissingVolumeIsCreatedAndRecordedSoTheRollbackCanRemoveIt() {
        when(volumes.volumeExists("Gifted-new-data")).thenReturn(false);
        List<String> created = new ArrayList<>();

        ImportService.createMissingVolumes(volumes, List.of("Gifted-new-data"), created);

        verify(volumes).createVolume("Gifted-new-data", "local");
        assertThat(created).containsExactly("Gifted-new-data");
    }

    @Test
    void onlyTheMissingOnesAreRecordedWhenThereIsAMix() {
        when(volumes.volumeExists("Gifted-db-data")).thenReturn(true);
        when(volumes.volumeExists("Gifted-uploads-data")).thenReturn(false);
        List<String> created = new ArrayList<>();

        ImportService.createMissingVolumes(volumes, List.of("Gifted-db-data", "Gifted-uploads-data"), created);

        assertThat(created).containsExactly("Gifted-uploads-data");
    }

    @Test
    void volumesCreatedBeforeAFailureStayRecorded() {
        when(volumes.volumeExists(anyString())).thenReturn(false);
        when(volumes.createVolume("b", "local")).thenThrow(new IllegalStateException("docker error"));
        List<String> created = new ArrayList<>();

        assertThatThrownBy(() -> ImportService.createMissingVolumes(volumes, List.of("a", "b"), created))
                .isInstanceOf(IllegalStateException.class);

        // El rollback debe poder quitar "a", que sí creó este despliegue
        assertThat(created).containsExactly("a");
    }
}
