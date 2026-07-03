import type { ComposeDeployResultDTO, DeployComposeRequest, GitHubRepoDTO, ImportResultDTO, PseudoDockerfileRequest } from "../../types/imports.ts";
import { ApiError, request, searchParams } from "../api.ts";

const ImportsPath: string = "/api/imports";

export async function uploadZipChunk(uploadId: string, chunkIndex: number, chunk: Blob): Promise<void> {
    const res = await fetch(`${ImportsPath}/zip/${uploadId}/chunks/${chunkIndex}`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/octet-stream" },
        body: chunk
    });
    if (!res.ok) throw new ApiError(res.status, await res.text().catch(() => "Could not upload the chunk"));
}

export const completeZipImport = (uploadId: string, totalChunks: number) =>
    request<ImportResultDTO>(`${ImportsPath}/zip/${uploadId}/complete`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ totalChunks })
    });

export const buildFromPseudoDockerfile = (uploadId: string, body: PseudoDockerfileRequest) =>
    request<ImportResultDTO>(`${ImportsPath}/zip/${uploadId}/build-dockerfile`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    });

export const deployCompose = (uploadId: string, body: DeployComposeRequest) =>
    request<ComposeDeployResultDTO>(`${ImportsPath}/zip/${uploadId}/deploy-compose`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    });

export const listGithubRepos = () =>
    request<GitHubRepoDTO[]>(`${ImportsPath}/github/repos`);

export const listGithubBranches = (repository: string) =>
    request<string[]>(`${ImportsPath}/github/branches${searchParams({ repository })}`);

export const cloneRepository = (importId: string, repository: string, ref: string) =>
    request<ImportResultDTO>(`${ImportsPath}/github/clone`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ importId, repository, ref })
    });
