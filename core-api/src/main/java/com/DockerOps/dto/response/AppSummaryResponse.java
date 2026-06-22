package com.DockerOps.dto.response;

import com.DockerOps.model.apps.App;

import java.util.UUID;

public record AppSummaryResponse(UUID id, String name, String containerName) {
    public static AppSummaryResponse from(App app) {
        return new AppSummaryResponse(app.getId(), app.getName(), app.getContainerName());
    }
}
