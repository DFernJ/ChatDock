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
    essential: boolean;
    stackName: string | null;
}

export interface ContainerVolumeDTO {
    volumeName: string;
    target: string;
    readOnly: boolean;
}

export interface ContainerConfigDTO {
    id: string;
    memoryBytes: number | null;
    nanoCPUs: number | null;
    restartPolicyName: string | null;
    restartPolicyMaxRetryCount: number | null;
    networks: string[];
    volumes: ContainerVolumeDTO[];
}

export interface UpdateContainerRequest {
    memoryBytes: number | null;
    nanoCPUs: number | null;
    restartPolicyName: string | null;
    restartPolicyMaxRetryCount: number | null;
    networks: string[] | null;
    volumes: ContainerVolumeDTO[] | null;
}

export interface AppSecretDTO {
    id: string;
    secretName: string;
    updatedAt: string;
}

export interface StackDTO {
    id: string;
    stackName: string;
    appCount: number;
    createdAt: string;
}

export interface AppSummaryDTO {
    id: string;
    name: string;
    containerName: string;
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

export interface DockerHubImageDTO {
    name: string;
    description: string;
    official: boolean;
    automated: boolean;
    starCount: number;
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