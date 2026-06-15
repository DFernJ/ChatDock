import { CountDTO, ContainerDTO, ContainerStatsDTO, ImageDTO, NetworkDTO, VolumeDTO } from "../../types/docker.ts"
import {ApiError, request, searchParams} from "../api.ts";

const DockerPath: string = "/api/docker"

export const getCounts = () =>
    request<CountDTO>(`${DockerPath}/count`, {
        method: "GET"
    });

export const listContainers = () =>
    request<ContainerDTO[]>(`${DockerPath}/containers`, {
        method: "GET"
    });

export const getContainerStats = (id: string) =>
    request<ContainerStatsDTO>(`${DockerPath}/containers/${id}`, {
        method: "GET"
    });

export const startContainer = (id: string) =>
    request<void>(`${DockerPath}/containers/${id}/start`, {
        method: "POST"
    });

export const stopContainer = (id: string) =>
    request<void>(`${DockerPath}/containers/${id}/stop`, {
        method: "POST"
    });

export const restartContainer = (id: string) =>
    request<void>(`${DockerPath}/containers/${id}/restart`, {
        method: "POST"
    });

export const deleteContainer = (id: string, force = false, removeVolumes = false) =>
    request<void>(`${DockerPath}/containers/${id}/delete${searchParams({ force, removeVolumes })}`, {
        method: "DELETE"
    });

export async function downloadContainerLogs(id: string, name: string): Promise<void> {
    const res = await fetch(`${DockerPath}/containers/${id}/logs`, {
        method: "POST",
        credentials: "include"
    });
    if (!res.ok) throw new ApiError(res.status, await res.text().catch(() => "Unable to fetch logs"));
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${name}.log`;
    a.click();
    URL.revokeObjectURL(url);
}

export const listImages = () =>
    request<ImageDTO[]>(`${DockerPath}/images`, {
        method: "GET"
    });

export const pullImage = (repository: string, tag = "latest") =>
    request<void>(`${DockerPath}/images/pull${searchParams({ repository, tag })}`, {
        method: "POST"
    });

export const deleteImage = (id: string, force = false) =>
    request<void>(`${DockerPath}/images/${id}${searchParams({ force })}`, {
        method: "DELETE"
    });

export const pruneImages = () =>
    request<void>(`${DockerPath}/images/prune`, {
        method: "POST"
    });

export const listNetworks = () =>
    request<NetworkDTO[]>(`${DockerPath}/networks`, {
        method: "GET"
    });

export const createNetwork = (name: string, driver = "bridge") =>
    request<NetworkDTO>(`${DockerPath}/networks${searchParams({ name, driver })}`, {
        method: "POST"
    });

export const deleteNetwork = (id: string) =>
    request<void>(`${DockerPath}/networks/${id}`, {
        method: "DELETE"
    });

export const connectContainerToNetwork = (networkId: string, containerId: string) =>
    request<void>(`${DockerPath}/networks/${networkId}/connect${searchParams({ containerId })}`, {
        method: "POST"
    });

export const disconnectContainerFromNetwork = (networkId: string, containerId: string, force = false) =>
    request<void>(`${DockerPath}/networks/${networkId}/disconnect${searchParams({ containerId, force })}`, {
        method: "POST"
    });

export const pruneNetworks = () =>
    request<void>(`${DockerPath}/networks/prune`, {
        method: "POST"
    });

export const getNetwork = (networkId: string) =>
    request<NetworkDTO>(`${DockerPath}/networks/${networkId}`, {
        method: "GET"
    })

export const listVolumes = () =>
    request<VolumeDTO[]>(`${DockerPath}/volumes`, {
        method: "GET"
    });

export const getVolume = (name: string) =>
    request<VolumeDTO>(`${DockerPath}/volumes/${name}`, {
        method: "GET"
    });

export const createVolume = (name: string, driver = "local") =>
    request<VolumeDTO>(`${DockerPath}/volumes${searchParams({ name, driver })}`, {
        method: "POST"
    });

export const deleteVolume = (name: string) =>
    request<void>(`${DockerPath}/volumes/${name}`, {
        method: "DELETE"
    });
