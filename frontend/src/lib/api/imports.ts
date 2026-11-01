import type { ComposeDeployResultDTO, DeployComposeRequest, GitHubRepoDTO, ImportJobDTO, ImportResultDTO, PseudoDockerfileRequest } from "../../types/imports.ts";
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

const JobPollIntervalMs: number = 2000;

// Long operations (image builds) run in the background: start the job, then poll until it ends.
async function runJob<T>(path: string, body: unknown): Promise<T> {
    let job = await request<ImportJobDTO<T>>(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    });
    while (job.status === "RUNNING") {
        await new Promise(resolve => setTimeout(resolve, JobPollIntervalMs));
        job = await request<ImportJobDTO<T>>(`${ImportsPath}/jobs/${job.id}`);
    }
    if (job.status === "FAILED") throw new ApiError(400, job.error ?? "The operation failed");
    return job.result as T;
}

export const buildFromPseudoDockerfile = (uploadId: string, body: PseudoDockerfileRequest) =>
    runJob<ImportResultDTO>(`${ImportsPath}/zip/${uploadId}/build-dockerfile`, body);

export const deployCompose = (uploadId: string, body: DeployComposeRequest) =>
    runJob<ComposeDeployResultDTO>(`${ImportsPath}/zip/${uploadId}/deploy-compose`, body);

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
