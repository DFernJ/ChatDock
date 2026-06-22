import { AppSecretDTO, AppSummaryDTO, CountDTO, ContainerConfigDTO, ContainerDTO, ContainerStatsDTO, ImageDTO, NetworkDTO, StackDTO, UpdateContainerRequest, VolumeDTO } from "../../types/docker.ts"
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

export const getContainersStats = () =>
    request<ContainerStatsDTO[]>(`${DockerPath}/containers/stats`, {
        method: "GET"
    });

export const getContainerStats = (id: string) =>
    request<ContainerStatsDTO>(`${DockerPath}/containers/${id}`, {
        method: "GET"
    });

export const getContainerConfig = (id: string) =>
    request<ContainerConfigDTO>(`${DockerPath}/containers/${id}/config`, {
        method: "GET"
    });

export const updateContainer = (id: string, body: UpdateContainerRequest) =>
    request<ContainerDTO>(`${DockerPath}/containers/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    });

export const listContainerSecrets = (id: string) =>
    request<AppSecretDTO[]>(`${DockerPath}/containers/${id}/secrets`, {
        method: "GET"
    });

export const createContainerSecret = (id: string, secretName: string, secretValue: string) =>
    request<AppSecretDTO>(`${DockerPath}/containers/${id}/secrets`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ secretName, secretValue })
    });

export const updateContainerSecret = (id: string, secretId: string, secretValue: string) =>
    request<AppSecretDTO>(`${DockerPath}/containers/${id}/secrets/${secretId}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ secretValue })
    });

export const deleteContainerSecret = (id: string, secretId: string) =>
    request<void>(`${DockerPath}/containers/${id}/secrets/${secretId}`, {
        method: "DELETE"
    });

export const getContainerSecretValue = (id: string, secretId: string) =>
    request<{ secretValue: string }>(`${DockerPath}/containers/${id}/secrets/${secretId}/value`, {
        method: "GET"
    });

export const listStacks = () =>
    request<StackDTO[]>(`${DockerPath}/stacks`, {
        method: "GET"
    });

export const createStack = (stackName: string) =>
    request<StackDTO>(`${DockerPath}/stacks`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ stackName })
    });

export const updateStack = (stackId: string, stackName: string) =>
    request<StackDTO>(`${DockerPath}/stacks/${stackId}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ stackName })
    });

export const deleteStack = (stackId: string) =>
    request<void>(`${DockerPath}/stacks/${stackId}`, {
        method: "DELETE"
    });

export const listStackApps = (stackId: string) =>
    request<AppSummaryDTO[]>(`${DockerPath}/stacks/${stackId}/apps`, {
        method: "GET"
    });

export const removeAppFromStack = (stackId: string, appId: string) =>
    request<void>(`${DockerPath}/stacks/${stackId}/apps/${appId}`, {
        method: "DELETE"
    });

export const assignContainerToStack = (id: string, appName: string, stackName: string) =>
    request<void>(`${DockerPath}/containers/${id}/assign`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ appName, stackName })
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

export async function getContainerLogsText(id: string): Promise<string> {
    const res = await fetch(`${DockerPath}/containers/${id}/logs`, {
        method: "POST",
        credentials: "include"
    });
    if (!res.ok) throw new ApiError(res.status, await res.text().catch(() => "Unable to fetch logs"));
    return res.text();
}

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
