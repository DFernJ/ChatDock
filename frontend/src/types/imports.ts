import type { ContainerVolumeDTO, PortMappingRequest, SecretDraftRequest } from "./docker.ts";

export interface ComposeServiceDTO {
    name: string;
    image: string | null;
    buildSubdir: string | null;
    ports: PortMappingRequest[];
    volumes: ContainerVolumeDTO[];
    dependsOn: string[];
    restartPolicy: string;
    secrets: SecretDraftRequest[];
    supported: boolean;
    unsupportedReason: string | null;
}

export interface ImportResultDTO {
    kind: "dockerfile" | "compose" | "none";
    image: string | null;
    message: string | null;
    services: ComposeServiceDTO[] | null;
}

export interface PseudoDockerfileRequest {
    baseImage: string;
    workdir: string;
    buildCommand: string;
    runCommand: string;
}

export interface ComposeServiceOverride {
    subdomain: string | null;
    stdin: boolean;
    secrets: SecretDraftRequest[];
}

export interface DeployComposeRequest {
    projectName: string;
    services: Record<string, ComposeServiceOverride>;
}

export interface ComposeDeployResultDTO {
    stackName: string;
    createdContainers: string[];
}

export interface GitHubRepoDTO {
    fullName: string;
    defaultBranch: string;
    isPrivate: boolean;
}
