export interface CountDTO {
    containers: number;
    images: number;
    volumes: number;
    networks: number;
}

export interface ContainerDTO {
    id: string;
    name: string;
    image: string;
    ports: string[];
    status: string;
    state: string;
    networks: string[];
    mounts: string[];
}

export interface ContainerStatsDTO {
    containerId: string;
    cpuPercent: number;
    memPercent: number;
    memUsedBytes: number;
    memLimitBytes: number;
    diskReadBytes: number;
    diskWriteBytes: number;
    timestamp: string;
}

export interface MinifiedContainerMountsDTO {
    name: string;
    mounts: string[];
}

export interface ImageDTO {
    id: string;
    image: string;
    diskUsage: number;
    usedInContainers: number;
}

export interface VolumeDTO {
    name: string;
    driver: string;
    usedInContainers: number;
    mountPoints: MinifiedContainerMountsDTO[];
}

export interface NetworkDTO {
    id: string;
    name: string;
    driver: string;
    scope: string;
    attachedToContainers: number;
    connectedContainers: string[];
}