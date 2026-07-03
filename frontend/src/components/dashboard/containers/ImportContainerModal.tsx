import { forwardRef, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import type { ChangeEvent, DragEvent, FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../../../lib/api.ts";
import { createContainer, listImages, listNetworks, listStacks, listVolumes, searchDockerHubImages } from "../../../lib/api/docker.ts";
import { buildFromPseudoDockerfile, cloneRepository, completeZipImport, deployCompose, listGithubBranches, listGithubRepos, uploadZipChunk } from "../../../lib/api/imports.ts";
import { getProfile } from "../../../lib/api/profile.ts";
import type { ContainerVolumeDTO, DockerHubImageDTO, ImageDTO, NetworkDTO, StackDTO, VolumeDTO } from "../../../types/docker.ts";
import type { ComposeServiceOverride, GitHubRepoDTO, ImportResultDTO } from "../../../types/imports.ts";
import type { ProfileDTO } from "../../../types/profile.ts";
import { Field, formatBytes, Icon } from "../../Ui.tsx";

const CREATE_CONTAINER_FORM_ID = "create-container-form";
const ZIP_IMPORT_FORM_ID = "zip-import-form";
const VCS_IMPORT_FORM_ID = "vcs-import-form";

type ImportPhase = "start" | "dockerfile-form" | "compose-review";

type ImportSource = "vcs" | "zip" | "image";

const SOURCE_TABS: { key: ImportSource; label: string }[] = [
    { key: "vcs", label: "Version control" },
    { key: "zip", label: "ZIP file" },
    { key: "image", label: "Image" },
];

const CHUNK_SIZE = 20 * 1024 * 1024;
const MAX_ZIP_BYTES = 300 * 1024 * 1024;

interface ZipChunk {
    index: number;
    size: number;
}

function buildChunks(file: File): ZipChunk[] {
    const chunks: ZipChunk[] = [];
    let start = 0;
    let index = 0;
    while (start < file.size) {
        const end = Math.min(start + CHUNK_SIZE, file.size);
        chunks.push({ index, size: end - start });
        start = end;
        index += 1;
    }
    return chunks;
}

interface ImportOutcomePanelHandle {
    submit: () => Promise<void>;
}

const ImportOutcomePanel = forwardRef<ImportOutcomePanelHandle, {
    uploadId: string;
    result: ImportResultDTO;
    onImageBuilt: (reference: string) => void;
    onComposeDeployed: () => void;
    onError: (message: string | null) => void;
}>(function ImportOutcomePanel({ uploadId, result, onImageBuilt, onComposeDeployed, onError }, ref) {
    const [baseImage, setBaseImage] = useState("");
    const [workdir, setWorkdir] = useState("/app");
    const [buildCommand, setBuildCommand] = useState("");
    const [runCommand, setRunCommand] = useState("");

    const [hubResults, setHubResults] = useState<DockerHubImageDTO[]>([]);
    const [hubSearching, setHubSearching] = useState(false);
    const [showBaseImageResults, setShowBaseImageResults] = useState(false);
    const baseImageBoxRef = useRef<HTMLDivElement>(null);

    const [projectName, setProjectName] = useState("");
    const [composeIncluded, setComposeIncluded] = useState<Record<string, boolean>>(() => {
        const init: Record<string, boolean> = {};
        if (result.kind === "compose" && result.services) {
            for (const svc of result.services) init[svc.name] = svc.supported;
        }
        return init;
    });
    const [composeSubdomains, setComposeSubdomains] = useState<Record<string, string>>({});
    const [composeStdins, setComposeStdins] = useState<Record<string, boolean>>({});
    const [composeSecrets, setComposeSecrets] = useState<Record<string, SecretDraft[]>>(() => {
        const init: Record<string, SecretDraft[]> = {};
        if (result.kind === "compose" && result.services) {
            for (const svc of result.services) init[svc.name] = svc.secrets;
        }
        return init;
    });

    useEffect(() => {
        const query = baseImage.trim();
        if (query.length < 2) {
            setHubResults([]);
            setHubSearching(false);
            return;
        }
        setHubSearching(true);
        const timeoutId = window.setTimeout(async () => {
            try {
                setHubResults(await searchDockerHubImages(query));
            } catch {
                setHubResults([]);
            } finally {
                setHubSearching(false);
            }
        }, 400);
        return () => window.clearTimeout(timeoutId);
    }, [baseImage]);

    useEffect(() => {
        if (!showBaseImageResults) return;
        const onClickOutside = (e: MouseEvent) => {
            if (baseImageBoxRef.current && !baseImageBoxRef.current.contains(e.target as Node)) {
                setShowBaseImageResults(false);
            }
        };
        document.addEventListener("mousedown", onClickOutside);
        return () => document.removeEventListener("mousedown", onClickOutside);
    }, [showBaseImageResults]);

    const submitPseudoDockerfile = async () => {
        if (!baseImage.trim() || !runCommand.trim()) {
            onError("Base image and run command are required.");
            return;
        }
        const outcome = await buildFromPseudoDockerfile(uploadId, {
            baseImage: baseImage.trim(),
            workdir: workdir.trim() || "/app",
            buildCommand: buildCommand.trim(),
            runCommand: runCommand.trim(),
        });
        if (outcome.kind === "dockerfile" && outcome.image) {
            onImageBuilt(outcome.image);
        }
    };

    const submitComposeDeploy = async () => {
        if (!result.services) return;
        if (!projectName.trim()) {
            onError("Project name is required.");
            return;
        }
        const includedNames = result.services.filter(s => composeIncluded[s.name]).map(s => s.name);
        if (includedNames.length === 0) {
            onError("Select at least one service to deploy.");
            return;
        }
        const includedSet = new Set(includedNames);
        for (const svc of result.services) {
            if (!includedSet.has(svc.name)) continue;
            for (const dep of svc.dependsOn) {
                if (!includedSet.has(dep)) {
                    onError(`Service '${svc.name}' depends on '${dep}', which is excluded.`);
                    return;
                }
            }
        }
        const services: Record<string, ComposeServiceOverride> = {};
        for (const name of includedNames) {
            services[name] = {
                subdomain: composeSubdomains[name]?.trim() || null,
                stdin: composeStdins[name] ?? false,
                secrets: composeSecrets[name] ?? [],
            };
        }
        await deployCompose(uploadId, { projectName: projectName.trim(), services });
        onComposeDeployed();
    };

    useImperativeHandle(ref, () => ({
        submit: async () => {
            if (result.kind === "none") {
                await submitPseudoDockerfile();
            } else if (result.kind === "compose") {
                await submitComposeDeploy();
            }
        }
    }));

    if (result.kind === "compose" && result.services) {
        return (
            <div className="flex flex-col gap-4">
                <div className="border border-amber-400/40 bg-amber-500/5 p-4">
                    <div className="font-mono text-[11px] tracking-[0.18em] uppercase text-amber-300 mb-1.5">docker-compose detected</div>
                    <p className="text-[12px] text-ink-300 leading-relaxed">{result.message}</p>
                </div>

                <Field label="Project name" hint="used for the stack, the shared network and each container's name">
                    <TextInput value={projectName} onChange={setProjectName} placeholder="my-project" />
                </Field>

                <div className="flex flex-col gap-3">
                    {result.services.map(svc => (
                        <div
                            key={svc.name}
                            className={`border ${svc.supported ? "border-ink-700" : "border-rose-400/40"} bg-ink-900/30 p-4 flex flex-col gap-3`}
                        >
                            <div className="flex items-center justify-between gap-3">
                                <label className="flex items-center gap-2 font-mono text-[13px] text-ink-50 select-none">
                                    <input
                                        type="checkbox"
                                        checked={!!composeIncluded[svc.name]}
                                        disabled={!svc.supported}
                                        onChange={(e) => setComposeIncluded(curr => ({ ...curr, [svc.name]: e.target.checked }))}
                                    />
                                    {svc.name}
                                </label>
                                <span className="font-mono text-[11px] text-ink-500 truncate">
                                    {svc.buildSubdir ? `builds from ./${svc.buildSubdir}` : svc.image}
                                </span>
                            </div>

                            {!svc.supported && (
                                <p className="text-[12px] text-rose-300 font-mono">{svc.unsupportedReason}</p>
                            )}

                            <div className="flex flex-wrap gap-x-4 gap-y-1 font-mono text-[11px] text-ink-500">
                                {svc.ports.length > 0 && (
                                    <span>ports: {svc.ports.map(p => `${p.hostPort}:${p.containerPort}/${p.protocol}`).join(", ")}</span>
                                )}
                                {svc.volumes.length > 0 && (
                                    <span>volumes: {svc.volumes.map(v => `${v.volumeName}:${v.target}`).join(", ")}</span>
                                )}
                                {svc.dependsOn.length > 0 && (
                                    <span>depends on: {svc.dependsOn.join(", ")}</span>
                                )}
                                <span>restart: {svc.restartPolicy}</span>
                            </div>

                            {svc.supported && composeIncluded[svc.name] && (
                                <div className="flex flex-col gap-3 pt-3 border-t border-ink-700/70">
                                    <Field label="Subdomain" hint="optional">
                                        <TextInput
                                            value={composeSubdomains[svc.name] ?? ""}
                                            onChange={(v) => setComposeSubdomains(curr => ({ ...curr, [svc.name]: v }))}
                                            placeholder={svc.name}
                                        />
                                    </Field>
                                    <label className="flex items-center gap-2 font-mono text-[11px] uppercase tracking-[0.14em] text-ink-300 select-none">
                                        <input
                                            type="checkbox"
                                            checked={!!composeStdins[svc.name]}
                                            onChange={(e) => setComposeStdins(curr => ({ ...curr, [svc.name]: e.target.checked }))}
                                        />
                                        Keep STDIN open
                                    </label>
                                    <Field label="Secrets">
                                        <SecretsEditor
                                            secrets={composeSecrets[svc.name] ?? []}
                                            onChange={(secrets) => setComposeSecrets(curr => ({ ...curr, [svc.name]: secrets }))}
                                        />
                                    </Field>
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            </div>
        );
    }

    if (result.kind === "none") {
        return (
            <div className="flex flex-col gap-4">
                <div className="border border-amber-400/40 bg-amber-500/5 p-4">
                    <p className="text-[12px] text-ink-300 leading-relaxed">{result.message}</p>
                </div>
                <div ref={baseImageBoxRef} className="relative">
                    <Field label="Base image">
                        <input
                            value={baseImage}
                            onChange={(e: ChangeEvent<HTMLInputElement>) => { setBaseImage(e.target.value); setShowBaseImageResults(true); }}
                            onFocus={() => setShowBaseImageResults(true)}
                            placeholder="node:20-alpine"
                            spellCheck={false}
                            className="flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600 focus:border-accent"
                        />
                    </Field>

                    {showBaseImageResults && baseImage.trim().length >= 2 && (
                        <div className="absolute left-0 right-0 top-full mt-1 max-h-64 overflow-y-auto scrollbar border border-ink-700 bg-ink-800 shadow-card z-10">
                            {hubSearching ? (
                                <div className="px-3.5 py-3 font-mono text-[12px] text-ink-500">searching…</div>
                            ) : hubResults.length === 0 ? (
                                <div className="px-3.5 py-3 font-mono text-[12px] text-ink-500">No images found on Docker Hub.</div>
                            ) : (
                                hubResults.map(r => (
                                    <button
                                        key={r.name}
                                        type="button"
                                        onClick={() => { setBaseImage(`${r.name}:latest`); setShowBaseImageResults(false); }}
                                        className="w-full text-left px-3.5 py-2.5 border-b border-ink-700 last:border-b-0 hover:bg-ink-700 transition"
                                    >
                                        <div className="flex items-center gap-2">
                                            <span className="font-mono text-[13px] text-ink-50 truncate">{r.name}</span>
                                            {r.official && (
                                                <span className="shrink-0 text-[9px] tracking-[0.16em] uppercase px-1.5 py-0.5 bg-accent-soft border border-accent-line text-accent">Official</span>
                                            )}
                                            <span className="shrink-0 font-mono text-[11px] text-ink-500 ml-auto">★ {r.starCount}</span>
                                        </div>
                                        {r.description && (
                                            <p className="font-mono text-[11px] text-ink-500 truncate mt-0.5">{r.description}</p>
                                        )}
                                    </button>
                                ))
                            )}
                        </div>
                    )}
                </div>
                <Field label="Working directory">
                    <TextInput value={workdir} onChange={setWorkdir} placeholder="/app" />
                </Field>
                <Field label="Build command" hint="optional — runs once while building the image">
                    <TextInput value={buildCommand} onChange={setBuildCommand} placeholder="npm install && npm run build" />
                </Field>
                <Field label="Run command">
                    <TextInput value={runCommand} onChange={setRunCommand} placeholder="npm start" />
                </Field>
            </div>
        );
    }

    return null;
});

function VcsPanel({ busy, onBusyChange, onImageBuilt, onPhaseChange, onReadyChange, onComposeDeployed, onClose }: {
    busy: boolean;
    onBusyChange: (v: boolean) => void;
    onImageBuilt: (reference: string) => void;
    onPhaseChange: (phase: ImportPhase) => void;
    onReadyChange: (ready: boolean) => void;
    onComposeDeployed: () => void;
    onClose: () => void;
}) {
    const navigate = useNavigate();
    const [profile, setProfile] = useState<ProfileDTO | null>(null);
    const [loadingProfile, setLoadingProfile] = useState(true);
    const [profileError, setProfileError] = useState<string | null>(null);

    const [repos, setRepos] = useState<GitHubRepoDTO[]>([]);
    const [loadingRepos, setLoadingRepos] = useState(false);
    const [reposError, setReposError] = useState<string | null>(null);
    const [search, setSearch] = useState("");

    const [selectedRepo, setSelectedRepo] = useState<GitHubRepoDTO | null>(null);
    const [branches, setBranches] = useState<string[]>([]);
    const [loadingBranches, setLoadingBranches] = useState(false);
    const [branch, setBranch] = useState("");

    const [error, setError] = useState<string | null>(null);
    const [cloning, setCloning] = useState(false);
    const [result, setResult] = useState<ImportResultDTO | null>(null);
    const [importId, setImportId] = useState<string | null>(null);
    const outcomeRef = useRef<ImportOutcomePanelHandle>(null);

    useEffect(() => {
        let cancelled = false;
        getProfile()
            .then(p => { if (!cancelled) setProfile(p); })
            .catch(e => { if (!cancelled) setProfileError(e instanceof ApiError ? e.message : "Could not load your profile."); })
            .finally(() => { if (!cancelled) setLoadingProfile(false); });
        return () => { cancelled = true; };
    }, []);

    useEffect(() => {
        if (!profile?.githubLinked) return;
        let cancelled = false;
        setLoadingRepos(true);
        listGithubRepos()
            .then(r => { if (!cancelled) setRepos(r); })
            .catch(e => { if (!cancelled) setReposError(e instanceof ApiError ? e.message : "Could not load your repositories."); })
            .finally(() => { if (!cancelled) setLoadingRepos(false); });
        return () => { cancelled = true; };
    }, [profile?.githubLinked]);

    useEffect(() => {
        if (result?.kind === "none") onPhaseChange("dockerfile-form");
        else if (result?.kind === "compose") onPhaseChange("compose-review");
        else onPhaseChange("start");
    }, [result]);

    useEffect(() => {
        if (!profile?.githubLinked) {
            onReadyChange(false);
            return;
        }
        onReadyChange(!!result || (!!selectedRepo && !!branch));
    }, [profile?.githubLinked, result, selectedRepo, branch]);

    const selectRepo = (repo: GitHubRepoDTO) => {
        setSelectedRepo(repo);
        setBranch(repo.defaultBranch);
        setBranches([]);
        setError(null);
        setLoadingBranches(true);
        listGithubBranches(repo.fullName)
            .then(setBranches)
            .catch(e => setError(e instanceof ApiError ? e.message : "Could not load branches."))
            .finally(() => setLoadingBranches(false));
    };

    const filteredRepos = useMemo(() => {
        const q = search.trim().toLowerCase();
        if (!q) return repos;
        return repos.filter(r => r.fullName.toLowerCase().includes(q));
    }, [repos, search]);

    const submitClone = async () => {
        if (!selectedRepo || !branch) return;
        const id = crypto.randomUUID();
        setCloning(true);
        try {
            const outcome = await cloneRepository(id, selectedRepo.fullName, branch);
            if (outcome.kind === "dockerfile" && outcome.image) {
                onImageBuilt(outcome.image);
                return;
            }
            setImportId(id);
            setResult(outcome);
        } finally {
            setCloning(false);
        }
    };

    const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        if (busy) return;
        setError(null);
        onBusyChange(true);
        try {
            if (result) {
                await outcomeRef.current?.submit();
            } else {
                await submitClone();
            }
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not import the repository.");
        } finally {
            onBusyChange(false);
        }
    };

    const goToProfile = () => {
        onClose();
        navigate("/profile");
    };

    if (loadingProfile) {
        return <div className="px-5 py-10 text-center font-mono text-[12px] text-ink-500">loading…</div>;
    }

    if (profileError) {
        return <p className="text-[12px] text-rose-400 font-mono">{profileError}</p>;
    }

    if (!profile?.githubLinked) {
        return (
            <div className="border border-amber-400/40 bg-amber-500/5 p-5 flex flex-col gap-3">
                <div className="font-mono text-[11px] tracking-[0.18em] uppercase text-amber-300">Version control not configured</div>
                <p className="text-[12px] text-ink-300 leading-relaxed">
                    Link a GitHub account in your profile to import a container from one of your repositories.
                </p>
                <button
                    type="button"
                    onClick={goToProfile}
                    className="self-start px-4 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 active:translate-y-px transition"
                >
                    Configure in profile
                </button>
            </div>
        );
    }

    return (
        <form id={VCS_IMPORT_FORM_ID} onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="px-4 py-3 border border-ink-700 bg-ink-900/30">
                <span className="font-mono text-[11px] text-ink-500">Linked as <span className="text-ink-100">{profile.githubUsername}</span></span>
            </div>

            {!result && !selectedRepo && (
                <>
                    <div className="flex items-center border border-ink-700 bg-ink-900/60 focus-within:border-accent">
                        <span className="grid place-items-center px-3 text-ink-500"><Icon name="search" /></span>
                        <input
                            value={search}
                            onChange={(e: ChangeEvent<HTMLInputElement>) => setSearch(e.target.value)}
                            placeholder="Search your repositories…"
                            spellCheck={false}
                            className="flex-1 bg-transparent border-0 text-ink-50 font-mono text-[13px] py-2.5 pr-3 placeholder:text-ink-600"
                        />
                    </div>
                    <div className="border border-ink-700 bg-ink-900/30">
                        <div className="max-h-64 overflow-y-auto scrollbar">
                            {loadingRepos ? (
                                <div className="px-4 py-4 font-mono text-[12px] text-ink-500">loading…</div>
                            ) : reposError ? (
                                <div className="px-4 py-4 font-mono text-[12px] text-rose-400">{reposError}</div>
                            ) : filteredRepos.length === 0 ? (
                                <div className="px-4 py-4 font-mono text-[12px] text-ink-500">No repositories found.</div>
                            ) : (
                                filteredRepos.map(r => (
                                    <button
                                        key={r.fullName}
                                        type="button"
                                        onClick={() => selectRepo(r)}
                                        className="w-full text-left px-4 py-2.5 border-b border-ink-700/70 last:border-b-0 hover:bg-ink-800 transition flex items-center gap-2"
                                    >
                                        <span className="font-mono text-[13px] text-ink-50 truncate">{r.fullName}</span>
                                        {r.isPrivate && (
                                            <span className="shrink-0 text-[9px] tracking-[0.16em] uppercase px-1.5 py-0.5 bg-ink-800 border border-ink-700 text-ink-400">Private</span>
                                        )}
                                    </button>
                                ))
                            )}
                        </div>
                    </div>
                </>
            )}

            {error && <p className="text-[12px] text-rose-400 font-mono">{error}</p>}

            {selectedRepo && !result && (
                <div className="border border-ink-700 bg-ink-900/30">
                    <div className="px-4 py-3 border-b border-ink-700 flex items-center justify-between gap-3">
                        <div className="min-w-0">
                            <div className="font-mono text-[13px] text-ink-50 truncate">{selectedRepo.fullName}</div>
                            <div className="font-mono text-[11px] text-ink-500">{cloning ? "cloning and scanning…" : "ready to clone"}</div>
                        </div>
                        <button
                            type="button"
                            onClick={() => { setSelectedRepo(null); setBranches([]); setError(null); }}
                            disabled={busy}
                            className="shrink-0 px-3 py-1.5 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.1em] uppercase hover:bg-ink-900 disabled:opacity-50"
                        >
                            Change repo
                        </button>
                    </div>
                    <div className="p-4">
                        <Field label="Branch">
                            {loadingBranches ? (
                                <span className="text-[12px] text-ink-500 font-mono">loading…</span>
                            ) : (
                                <Select value={branch} onChange={setBranch} options={branches.map(b => ({ value: b, label: b }))} />
                            )}
                        </Field>
                    </div>
                </div>
            )}

            {result && importId && (
                <ImportOutcomePanel
                    ref={outcomeRef}
                    uploadId={importId}
                    result={result}
                    onImageBuilt={onImageBuilt}
                    onComposeDeployed={onComposeDeployed}
                    onError={setError}
                />
            )}
        </form>
    );
}

function ZipPanel({ busy, onBusyChange, onImageBuilt, onPhaseChange, onComposeDeployed }: {
    busy: boolean;
    onBusyChange: (v: boolean) => void;
    onImageBuilt: (reference: string) => void;
    onPhaseChange: (phase: ImportPhase) => void;
    onComposeDeployed: () => void;
}) {
    const [file, setFile] = useState<File | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [dragActive, setDragActive] = useState(false);
    const [uploadedChunks, setUploadedChunks] = useState(0);
    const [scanning, setScanning] = useState(false);
    const [result, setResult] = useState<ImportResultDTO | null>(null);
    const [uploadId, setUploadId] = useState<string | null>(null);
    const inputRef = useRef<HTMLInputElement>(null);
    const outcomeRef = useRef<ImportOutcomePanelHandle>(null);

    const chunks = useMemo(() => (file ? buildChunks(file) : []), [file]);

    useEffect(() => {
        if (result?.kind === "none") onPhaseChange("dockerfile-form");
        else if (result?.kind === "compose") onPhaseChange("compose-review");
        else onPhaseChange("start");
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [result]);

    const acceptFile = (candidate: File | undefined) => {
        if (!candidate) return;
        if (!candidate.name.toLowerCase().endsWith(".zip")) {
            setError("Only .zip files are supported.");
            return;
        }
        if (candidate.size > MAX_ZIP_BYTES) {
            setError(`The file is too large (max ${formatBytes(MAX_ZIP_BYTES)}).`);
            return;
        }
        setError(null);
        setResult(null);
        setUploadId(null);
        setUploadedChunks(0);
        setFile(candidate);
    };

    const onDrop = (e: DragEvent<HTMLDivElement>) => {
        e.preventDefault();
        setDragActive(false);
        acceptFile(e.dataTransfer.files[0]);
    };

    const submitUpload = async () => {
        if (!file) return;
        const id = crypto.randomUUID();
        for (const chunk of chunks) {
            const start = chunk.index * CHUNK_SIZE;
            const blob = file.slice(start, start + chunk.size);
            await uploadZipChunk(id, chunk.index, blob);
            setUploadedChunks(n => n + 1);
        }
        setScanning(true);
        const outcome = await completeZipImport(id, chunks.length);
        if (outcome.kind === "dockerfile" && outcome.image) {
            onImageBuilt(outcome.image);
            return;
        }
        setUploadId(id);
        setResult(outcome);
    };

    const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        if (busy) return;
        setError(null);
        onBusyChange(true);
        try {
            if (result) {
                await outcomeRef.current?.submit();
            } else {
                await submitUpload();
            }
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not import the file.");
        } finally {
            setScanning(false);
            onBusyChange(false);
        }
    };

    return (
        <form id={ZIP_IMPORT_FORM_ID} onSubmit={handleSubmit} className="flex flex-col gap-4">
            {!result && (
                <div
                    onDragOver={(e) => { e.preventDefault(); if (!busy) setDragActive(true); }}
                    onDragLeave={() => setDragActive(false)}
                    onDrop={busy ? undefined : onDrop}
                    onClick={() => { if (!busy) inputRef.current?.click(); }}
                    className={`border border-dashed px-5 py-10 text-center transition ${busy ? "opacity-50 cursor-not-allowed" : "cursor-pointer"} ${
                        dragActive ? "border-accent bg-accent-soft" : "border-ink-700 hover:border-ink-600"
                    }`}
                >
                    <input
                        ref={inputRef}
                        type="file"
                        accept=".zip"
                        disabled={busy}
                        className="hidden"
                        onChange={(e: ChangeEvent<HTMLInputElement>) => acceptFile(e.target.files?.[0])}
                    />
                    <Icon name="import" className="w-6 h-6 mx-auto text-ink-500" />
                    <div className="font-sans text-[15px] text-ink-200 mt-3">Drag and drop a .zip file here</div>
                    <p className="text-[12px] text-ink-500 mt-1.5 font-mono">or click to browse — the file will be split into chunks before upload.</p>
                </div>
            )}

            {error && <p className="text-[12px] text-rose-400 font-mono">{error}</p>}

            {file && !result && (
                <div className="border border-ink-700 bg-ink-900/30">
                    <div className="px-4 py-3 border-b border-ink-700 flex items-center justify-between gap-3">
                        <div className="min-w-0">
                            <div className="font-mono text-[13px] text-ink-50 truncate">{file.name}</div>
                            <div className="font-mono text-[11px] text-ink-500">
                                {formatBytes(file.size)} · {chunks.length} chunk{chunks.length === 1 ? "" : "s"}
                                {busy && !scanning && ` · uploading ${uploadedChunks}/${chunks.length}`}
                                {scanning && " · scanning and building…"}
                            </div>
                        </div>
                        <button
                            type="button"
                            onClick={() => {
                                setFile(null);
                                setResult(null);
                                setUploadId(null);
                                setError(null);
                            }}
                            disabled={busy}
                            className="shrink-0 px-3 py-1.5 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.1em] uppercase hover:bg-ink-900 disabled:opacity-50"
                        >
                            Remove
                        </button>
                    </div>
                    <div className="max-h-40 overflow-y-auto scrollbar">
                        {chunks.map(c => (
                            <div key={c.index} className="flex items-center justify-between px-4 py-2 border-b border-ink-700/70 last:border-b-0 font-mono text-[11px] text-ink-500">
                                <span>chunk {c.index + 1}{busy && c.index < uploadedChunks ? " ✓" : ""}</span>
                                <span>{formatBytes(c.size)}</span>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {result && uploadId && (
                <ImportOutcomePanel
                    ref={outcomeRef}
                    uploadId={uploadId}
                    result={result}
                    onImageBuilt={onImageBuilt}
                    onComposeDeployed={onComposeDeployed}
                    onError={setError}
                />
            )}
        </form>
    );
}

interface SelectedImage {
    reference: string;
}

interface PortMapping {
    hostPort: string;
    containerPort: string;
    protocol: "tcp" | "udp";
}

interface HealthcheckDraft {
    enabled: boolean;
    command: string;
    intervalSeconds: string;
    timeoutSeconds: string;
    retries: string;
}

interface SecretDraft {
    name: string;
    value: string;
}

type RestartPolicyName = "no" | "always" | "unless-stopped" | "on-failure";

const RESTART_POLICY_OPTIONS: { value: RestartPolicyName; label: string }[] = [
    { value: "no", label: "no" },
    { value: "always", label: "always" },
    { value: "unless-stopped", label: "unless stopped" },
    { value: "on-failure", label: "on failure" },
];

const NEW_STACK_OPTION = "__new__";
const NO_STACK_OPTION = "__none__";

function TextInput({ value, onChange, placeholder, type = "text", noSpinner = false }: {
    value: string; onChange: (v: string) => void; placeholder?: string; type?: string; noSpinner?: boolean;
}) {
    return (
        <input
            type={type}
            value={value}
            onChange={(e: ChangeEvent<HTMLInputElement>) => onChange(e.target.value)}
            placeholder={placeholder}
            spellCheck={false}
            className={`flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600 focus:border-accent ${
                noSpinner ? "[appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none" : ""
            }`}
        />
    );
}

function Select({ value, onChange, options }: {
    value: string;
    onChange: (v: string) => void;
    options: { value: string; label: string }[];
}) {
    return (
        <div className="relative flex items-stretch border border-ink-700 bg-ink-900/60 focus-within:border-accent">
            <select
                value={value}
                onChange={(e: ChangeEvent<HTMLSelectElement>) => onChange(e.target.value)}
                className="flex-1 min-w-0 appearance-none bg-transparent border-0 text-ink-50 font-mono text-[13px] pl-3.5 pr-8 py-3 outline-none"
            >
                {options.map(o => (
                    <option key={o.value} value={o.value} className="bg-ink-800">{o.label}</option>
                ))}
            </select>
            <Icon name="chev" className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 w-3 h-3 rotate-90 text-ink-500" />
        </div>
    );
}

function SecretsEditor({ secrets, onChange }: { secrets: SecretDraft[]; onChange: (secrets: SecretDraft[]) => void }) {
    const [newSecretName, setNewSecretName] = useState("");
    const [newSecretValue, setNewSecretValue] = useState("");
    const [revealedSecrets, setRevealedSecrets] = useState<number[]>([]);

    const addSecret = () => {
        if (!newSecretName.trim() || !newSecretValue.trim()) return;
        onChange([...secrets, { name: newSecretName.trim(), value: newSecretValue }]);
        setNewSecretName("");
        setNewSecretValue("");
    };
    const updateSecretValue = (index: number, value: string) =>
        onChange(secrets.map((s, i) => i === index ? { ...s, value } : s));
    const removeSecret = (index: number) => {
        onChange(secrets.filter((_, i) => i !== index));
        setRevealedSecrets(curr => curr.filter(i => i !== index));
    };
    const toggleRevealSecret = (index: number) =>
        setRevealedSecrets(curr => curr.includes(index) ? curr.filter(i => i !== index) : [...curr, index]);

    return (
        <div className="flex flex-col gap-2">
            {secrets.length === 0 && (
                <p className="text-[12px] text-ink-500 font-mono">No secrets added yet.</p>
            )}
            {secrets.map((s, i) => (
                <div key={i} className="border border-ink-700 bg-ink-900/30 p-2 flex items-center gap-2">
                    <span className="flex-1 min-w-0 font-mono text-[13px] text-ink-100 truncate">{s.name}</span>
                    <div className="flex-1 min-w-0">
                        <TextInput
                            type={revealedSecrets.includes(i) ? "text" : "password"}
                            value={s.value}
                            onChange={(v) => updateSecretValue(i, v)}
                        />
                    </div>
                    <button
                        type="button"
                        onClick={() => toggleRevealSecret(i)}
                        title={revealedSecrets.includes(i) ? "Hide" : "Show"}
                        className="w-8 h-8 shrink-0 grid place-items-center border border-ink-700 text-ink-300 hover:bg-ink-800 hover:border-ink-600 transition"
                    >
                        <Icon name={revealedSecrets.includes(i) ? "eyeOff" : "eye"} className="w-3.5 h-3.5" />
                    </button>
                    <button
                        type="button"
                        onClick={() => removeSecret(i)}
                        className="w-8 h-8 shrink-0 grid place-items-center border border-ink-700 text-rose-300 hover:bg-rose-500/10 hover:border-rose-400/50 transition"
                    >
                        <Icon name="trash" className="w-3.5 h-3.5" />
                    </button>
                </div>
            ))}
            <div className="flex items-stretch gap-2">
                <div className="flex-1 min-w-0">
                    <TextInput value={newSecretName} onChange={setNewSecretName} placeholder="SECRET_NAME" />
                </div>
                <div className="flex-1 min-w-0">
                    <TextInput type="password" value={newSecretValue} onChange={setNewSecretValue} placeholder="value" />
                </div>
                <button
                    type="button"
                    disabled={!newSecretName.trim() || !newSecretValue.trim()}
                    onClick={addSecret}
                    className="w-9 shrink-0 grid place-items-center border border-ink-700 text-ink-300 hover:bg-ink-800 hover:border-ink-600 transition disabled:opacity-40 disabled:cursor-not-allowed"
                >
                    <Icon name="plus" className="w-3 h-3" />
                </button>
            </div>
        </div>
    );
}

function ContainerCreateForm({ image, onBack, busy, onBusyChange, onCreated }: {
    image: SelectedImage;
    onBack: () => void;
    busy: boolean;
    onBusyChange: (v: boolean) => void;
    onCreated: () => void;
}) {
    const [name, setName] = useState("");
    const [subdomain, setSubdomain] = useState("");
    const [stdin, setStdin] = useState(false);

    const [restartPolicy, setRestartPolicy] = useState<RestartPolicyName>("no");
    const [maxRetryCount, setMaxRetryCount] = useState("0");

    const [availableStacks, setAvailableStacks] = useState<StackDTO[]>([]);
    const [stackChoice, setStackChoice] = useState(NO_STACK_OPTION);
    const [newStackName, setNewStackName] = useState("");

    const [availableNetworks, setAvailableNetworks] = useState<NetworkDTO[]>([]);
    const [selectedNetworks, setSelectedNetworks] = useState<string[]>([]);

    const [availableVolumes, setAvailableVolumes] = useState<VolumeDTO[]>([]);
    const [volumeRows, setVolumeRows] = useState<ContainerVolumeDTO[]>([]);

    const [portRows, setPortRows] = useState<PortMapping[]>([]);

    const [healthcheck, setHealthcheck] = useState<HealthcheckDraft>({
        enabled: false,
        command: "",
        intervalSeconds: "30",
        timeoutSeconds: "5",
        retries: "3",
    });

    const [secretRows, setSecretRows] = useState<SecretDraft[]>([]);

    const [loadingOptions, setLoadingOptions] = useState(true);
    const [optionsError, setOptionsError] = useState<string | null>(null);
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [subdomainError, setSubdomainError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;
        Promise.all([listNetworks(), listVolumes(), listStacks()])
            .then(([networks, volumes, stacks]) => {
                if (cancelled) return;
                setAvailableNetworks(networks);
                setAvailableVolumes(volumes);
                setAvailableStacks(stacks);
            })
            .catch(e => { if (!cancelled) setOptionsError(e instanceof ApiError ? e.message : "Could not load networks, volumes or stacks."); })
            .finally(() => { if (!cancelled) setLoadingOptions(false); });
        return () => { cancelled = true; };
    }, []);

    const toggleNetwork = (networkName: string) => {
        setSelectedNetworks(curr => curr.includes(networkName) ? curr.filter(n => n !== networkName) : [...curr, networkName]);
    };

    const addVolumeRow = () => setVolumeRows(curr => [...curr, { volumeName: availableVolumes[0]?.name ?? "", target: "", readOnly: false }]);
    const updateVolumeRow = (index: number, patch: Partial<ContainerVolumeDTO>) =>
        setVolumeRows(curr => curr.map((v, i) => i === index ? { ...v, ...patch } : v));
    const removeVolumeRow = (index: number) => setVolumeRows(curr => curr.filter((_, i) => i !== index));

    const addPortRow = () => setPortRows(curr => [...curr, { hostPort: "", containerPort: "", protocol: "tcp" }]);
    const updatePortRow = (index: number, patch: Partial<PortMapping>) =>
        setPortRows(curr => curr.map((p, i) => i === index ? { ...p, ...patch } : p));
    const removePortRow = (index: number) => setPortRows(curr => curr.filter((_, i) => i !== index));

    const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        setSubmitError(null);
        setSubdomainError(null);

        if (!name.trim()) {
            setSubmitError("Name is required.");
            return;
        }
        if (stackChoice === NEW_STACK_OPTION && !newStackName.trim()) {
            setSubmitError("New stack name is required.");
            return;
        }
        if (stackChoice === NO_STACK_OPTION && secretRows.length > 0) {
            setSubmitError("Assign the container to a stack to add secrets.");
            return;
        }
        if (subdomain.trim() && !/^[A-Za-z0-9-]+$/.test(subdomain.trim())) {
            setSubdomainError("Subdomain can only contain letters, numbers and hyphens.");
            return;
        }

        const stackName = stackChoice === NO_STACK_OPTION ? null : stackChoice === NEW_STACK_OPTION ? newStackName.trim() : stackChoice;

        onBusyChange(true);
        try {
            await createContainer({
                image: image.reference,
                name: name.trim(),
                subdomain: subdomain.trim() || null,
                stdin,
                restartPolicyName: restartPolicy,
                restartPolicyMaxRetryCount: restartPolicy === "on-failure" ? (Number(maxRetryCount) || 0) : null,
                networks: selectedNetworks,
                volumes: volumeRows.filter(v => v.volumeName && v.target),
                ports: portRows
                    .filter(p => p.hostPort.trim() && p.containerPort.trim())
                    .map(p => ({ hostPort: Number(p.hostPort), containerPort: Number(p.containerPort), protocol: p.protocol })),
                healthcheck: healthcheck.enabled && healthcheck.command.trim() ? {
                    enabled: true,
                    command: healthcheck.command.trim(),
                    intervalSeconds: Number(healthcheck.intervalSeconds) || 0,
                    timeoutSeconds: Number(healthcheck.timeoutSeconds) || 0,
                    retries: Number(healthcheck.retries) || 0,
                } : null,
                secrets: secretRows,
                stackName,
            });
            onCreated();
        } catch (e) {
            const message = e instanceof ApiError ? e.message : "Could not create the container.";
            if (message.toLowerCase().includes("subdomain")) {
                setSubdomainError(message);
            } else {
                setSubmitError(message);
            }
        } finally {
            onBusyChange(false);
        }
    };

    return (
        <form id={CREATE_CONTAINER_FORM_ID} onSubmit={handleSubmit} className="flex flex-col gap-5">
            <div className="flex items-center justify-between border border-ink-700 bg-ink-900/30 px-4 py-3">
                <div className="min-w-0">
                    <div className="font-mono text-[10px] tracking-[0.22em] uppercase text-ink-500">image</div>
                    <div className="font-mono text-[13px] text-ink-50 truncate">{image.reference}</div>
                </div>
                <button type="button" onClick={onBack} disabled={busy} className="shrink-0 px-3 py-1.5 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.1em] uppercase hover:bg-ink-900 disabled:opacity-50">
                    Change image
                </button>
            </div>

            <Field label="Name">
                <TextInput value={name} onChange={setName} placeholder="my-app" />
            </Field>

            <Field label="Subdomain" hint="routes this container through the reverse proxy">
                <TextInput value={subdomain} onChange={setSubdomain} placeholder="my-app" />
            </Field>
            {subdomainError && <p className="text-[12px] text-rose-400 font-mono -mt-3">{subdomainError}</p>}

            <Field label="Stack" hint="group this container under a managed stack">
                <Select
                    value={stackChoice}
                    onChange={setStackChoice}
                    options={[
                        { value: NO_STACK_OPTION, label: "No stack" },
                        { value: NEW_STACK_OPTION, label: "+ New stack…" },
                        ...availableStacks.map(s => ({ value: s.stackName, label: s.stackName })),
                    ]}
                />
            </Field>
            {stackChoice === NEW_STACK_OPTION && (
                <Field label="New stack name">
                    <TextInput value={newStackName} onChange={setNewStackName} placeholder="my-stack" />
                </Field>
            )}

            <label className="flex items-center gap-2 font-mono text-[11px] uppercase tracking-[0.14em] text-ink-300 select-none">
                <input type="checkbox" checked={stdin} onChange={(e) => setStdin(e.target.checked)} />
                Keep STDIN open
            </label>

            {optionsError && <p className="text-[12px] text-rose-400 font-mono">{optionsError}</p>}

            <div className="grid grid-cols-2 gap-3">
                <Field label="Restart policy">
                    <Select value={restartPolicy} onChange={(v) => setRestartPolicy(v as RestartPolicyName)} options={RESTART_POLICY_OPTIONS} />
                </Field>
                {restartPolicy === "on-failure" && (
                    <Field label="Max retries">
                        <TextInput type="number" noSpinner value={maxRetryCount} onChange={setMaxRetryCount} placeholder="0" />
                    </Field>
                )}
            </div>

            <Field label="Networks">
                <div className="flex flex-wrap gap-2">
                    {loadingOptions ? (
                        <span className="text-[12px] text-ink-500 font-mono">loading…</span>
                    ) : availableNetworks.length === 0 ? (
                        <span className="text-[12px] text-ink-500 font-mono">No networks available.</span>
                    ) : (
                        availableNetworks.map(n => {
                            const active = selectedNetworks.includes(n.name);
                            return (
                                <button
                                    key={n.id}
                                    type="button"
                                    onClick={() => toggleNetwork(n.name)}
                                    className={`px-3 py-1.5 border font-mono text-[11px] tracking-[0.1em] transition ${
                                        active
                                            ? "border-accent-line bg-accent-soft text-accent"
                                            : "border-ink-700 text-ink-400 hover:text-ink-100 hover:border-ink-600"
                                    }`}
                                >
                                    {n.name}
                                </button>
                            );
                        })
                    )}
                </div>
            </Field>

            <Field label="Volumes">
                <div className="flex flex-col gap-2">
                    {volumeRows.map((v, i) => (
                        <div key={i} className="border border-ink-700 bg-ink-900/30 p-3 flex flex-col gap-3">
                            <Field label="Volume">
                                <Select
                                    value={v.volumeName}
                                    onChange={(value) => updateVolumeRow(i, { volumeName: value })}
                                    options={availableVolumes.map(vol => ({ value: vol.name, label: vol.name }))}
                                />
                            </Field>
                            <Field label="Path" hint="in container">
                                <TextInput value={v.target} onChange={(value) => updateVolumeRow(i, { target: value })} placeholder="/data" />
                            </Field>
                            <div className="flex items-center justify-between">
                                <label className="flex items-center gap-1.5 font-mono text-[10px] uppercase tracking-[0.1em] text-ink-400 select-none">
                                    <input type="checkbox" checked={v.readOnly} onChange={(e) => updateVolumeRow(i, { readOnly: e.target.checked })} />
                                    Read-only
                                </label>
                                <button
                                    type="button"
                                    onClick={() => removeVolumeRow(i)}
                                    className="flex items-center gap-2 px-3 py-1.5 border border-ink-700 text-rose-300 font-mono text-[11px] tracking-[0.1em] uppercase hover:bg-rose-500/10 hover:border-rose-400/50 transition"
                                >
                                    <Icon name="x" className="w-3 h-3" />
                                    Remove
                                </button>
                            </div>
                        </div>
                    ))}
                    <button
                        type="button"
                        onClick={addVolumeRow}
                        disabled={availableVolumes.length === 0}
                        className="self-start flex items-center gap-2 px-3 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.14em] uppercase hover:bg-ink-800 hover:border-ink-600 transition disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                        <Icon name="plus" className="w-3 h-3" />
                        Add volume
                    </button>
                </div>
            </Field>

            <Field label="Ports">
                <div className="flex flex-col gap-2">
                    {portRows.map((p, i) => (
                        <div key={i} className="flex items-stretch gap-2">
                            <div className="flex-1 min-w-0">
                                <TextInput type="number" noSpinner value={p.hostPort} onChange={(v) => updatePortRow(i, { hostPort: v })} placeholder="host e.g. 8080" />
                            </div>
                            <span className="grid place-items-center text-ink-500 font-mono text-[13px]">:</span>
                            <div className="flex-1 min-w-0">
                                <TextInput type="number" noSpinner value={p.containerPort} onChange={(v) => updatePortRow(i, { containerPort: v })} placeholder="container e.g. 80" />
                            </div>
                            <div className="w-24 shrink-0">
                                <Select
                                    value={p.protocol}
                                    onChange={(v) => updatePortRow(i, { protocol: v as "tcp" | "udp" })}
                                    options={[{ value: "tcp", label: "tcp" }, { value: "udp", label: "udp" }]}
                                />
                            </div>
                            <button
                                type="button"
                                onClick={() => removePortRow(i)}
                                className="w-9 shrink-0 grid place-items-center border border-ink-700 text-rose-300 hover:bg-rose-500/10 hover:border-rose-400/50 transition"
                            >
                                <Icon name="x" className="w-3 h-3" />
                            </button>
                        </div>
                    ))}
                    <button
                        type="button"
                        onClick={addPortRow}
                        className="self-start flex items-center gap-2 px-3 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.14em] uppercase hover:bg-ink-800 hover:border-ink-600 transition"
                    >
                        <Icon name="plus" className="w-3 h-3" />
                        Add port
                    </button>
                </div>
            </Field>

            <Field label="Healthcheck">
                <div className="flex flex-col gap-3">
                    <label className="flex items-center gap-2 font-mono text-[11px] uppercase tracking-[0.14em] text-ink-300 select-none">
                        <input
                            type="checkbox"
                            checked={healthcheck.enabled}
                            onChange={(e) => setHealthcheck(curr => ({ ...curr, enabled: e.target.checked }))}
                        />
                        Enable custom healthcheck
                    </label>
                    {healthcheck.enabled && (
                        <div className="border border-ink-700 bg-ink-900/30 p-3 flex flex-col gap-3">
                            <Field label="Command" hint="e.g. curl -f http://localhost/ || exit 1">
                                <TextInput
                                    value={healthcheck.command}
                                    onChange={(v) => setHealthcheck(curr => ({ ...curr, command: v }))}
                                    placeholder="curl -f http://localhost/ || exit 1"
                                />
                            </Field>
                            <div className="grid grid-cols-3 gap-3">
                                <Field label="Interval" hint="seconds">
                                    <TextInput type="number" value={healthcheck.intervalSeconds} onChange={(v) => setHealthcheck(curr => ({ ...curr, intervalSeconds: v }))} />
                                </Field>
                                <Field label="Timeout" hint="seconds">
                                    <TextInput type="number" value={healthcheck.timeoutSeconds} onChange={(v) => setHealthcheck(curr => ({ ...curr, timeoutSeconds: v }))} />
                                </Field>
                                <Field label="Retries">
                                    <TextInput type="number" value={healthcheck.retries} onChange={(v) => setHealthcheck(curr => ({ ...curr, retries: v }))} />
                                </Field>
                            </div>
                        </div>
                    )}
                </div>
            </Field>

            <Field label="Secrets" hint="stored encrypted once the container is created">
                <SecretsEditor secrets={secretRows} onChange={setSecretRows} />
            </Field>

            {submitError && <p className="text-[12px] text-rose-400 font-mono">{submitError}</p>}
        </form>
    );
}

function ImagePanel({ onSelectImage }: { onSelectImage: (image: SelectedImage) => void }) {
    const [localImages, setLocalImages] = useState<ImageDTO[]>([]);
    const [loadingLocal, setLoadingLocal] = useState(true);
    const [localError, setLocalError] = useState<string | null>(null);
    const [search, setSearch] = useState("");

    const [hubResults, setHubResults] = useState<DockerHubImageDTO[]>([]);
    const [hubSearching, setHubSearching] = useState(false);

    useEffect(() => {
        listImages()
            .then(setLocalImages)
            .catch(e => setLocalError(e instanceof ApiError ? e.message : "Could not load local images."))
            .finally(() => setLoadingLocal(false));
    }, []);

    useEffect(() => {
        const query = search.trim();
        if (query.length < 2) {
            setHubResults([]);
            setHubSearching(false);
            return;
        }
        setHubSearching(true);
        const timeoutId = window.setTimeout(async () => {
            try {
                setHubResults(await searchDockerHubImages(query));
            } catch {
                setHubResults([]);
            } finally {
                setHubSearching(false);
            }
        }, 400);
        return () => window.clearTimeout(timeoutId);
    }, [search]);

    const filteredLocal = useMemo(() => {
        const q = search.trim().toLowerCase();
        if (!q) return localImages;
        return localImages.filter(i => i.image.toLowerCase().includes(q));
    }, [localImages, search]);

    return (
        <div className="flex flex-col gap-4">
            <div className="flex items-center border border-ink-700 bg-ink-900/60 focus-within:border-accent">
                <span className="grid place-items-center px-3 text-ink-500"><Icon name="search" /></span>
                <input
                    value={search}
                    onChange={(e: ChangeEvent<HTMLInputElement>) => setSearch(e.target.value)}
                    placeholder="Search local images or Docker Hub…"
                    spellCheck={false}
                    className="flex-1 bg-transparent border-0 text-ink-50 font-mono text-[13px] py-2.5 pr-3 placeholder:text-ink-600"
                />
            </div>

            <div className="border border-ink-700 bg-ink-900/30">
                <div className="px-4 py-2.5 border-b border-ink-700 text-[10px] tracking-[0.22em] uppercase text-ink-500">
                    Local images
                </div>
                <div className="max-h-40 overflow-y-auto scrollbar">
                    {loadingLocal ? (
                        <div className="px-4 py-4 font-mono text-[12px] text-ink-500">loading…</div>
                    ) : localError ? (
                        <div className="px-4 py-4 font-mono text-[12px] text-rose-400">{localError}</div>
                    ) : filteredLocal.length === 0 ? (
                        <div className="px-4 py-4 font-mono text-[12px] text-ink-500">No local images found.</div>
                    ) : (
                        filteredLocal.map(i => (
                            <button
                                key={i.id}
                                type="button"
                                onClick={() => onSelectImage({ reference: i.image })}
                                className="w-full text-left px-4 py-2.5 border-b border-ink-700/70 last:border-b-0 hover:bg-ink-800 transition"
                            >
                                <div className="font-mono text-[13px] text-ink-50 truncate">{i.image}</div>
                                <div className="font-mono text-[11px] text-ink-600">{formatBytes(i.diskUsage)}</div>
                            </button>
                        ))
                    )}
                </div>
            </div>

            <div className="border border-ink-700 bg-ink-900/30">
                <div className="px-4 py-2.5 border-b border-ink-700 text-[10px] tracking-[0.22em] uppercase text-ink-500">
                    Docker Hub
                </div>
                <div className="max-h-40 overflow-y-auto scrollbar">
                    {search.trim().length < 2 ? (
                        <div className="px-4 py-4 font-mono text-[12px] text-ink-500">Type at least 2 characters to search Docker Hub.</div>
                    ) : hubSearching ? (
                        <div className="px-4 py-4 font-mono text-[12px] text-ink-500">searching…</div>
                    ) : hubResults.length === 0 ? (
                        <div className="px-4 py-4 font-mono text-[12px] text-ink-500">No images found on Docker Hub.</div>
                    ) : (
                        hubResults.map(r => (
                            <button
                                key={r.name}
                                type="button"
                                onClick={() => onSelectImage({ reference: `${r.name}:latest` })}
                                className="w-full text-left px-4 py-2.5 border-b border-ink-700/70 last:border-b-0 hover:bg-ink-800 transition"
                            >
                                <div className="flex items-center gap-2">
                                    <span className="font-mono text-[13px] text-ink-50 truncate">{r.name}</span>
                                    {r.official && (
                                        <span className="shrink-0 text-[9px] tracking-[0.16em] uppercase px-1.5 py-0.5 bg-accent-soft border border-accent-line text-accent">Official</span>
                                    )}
                                </div>
                            </button>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
}

export default function ImportContainerModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
    const [source, setSource] = useState<ImportSource>("vcs");
    const [selectedImage, setSelectedImage] = useState<SelectedImage | null>(null);
    const [creating, setCreating] = useState(false);
    const [zipBusy, setZipBusy] = useState(false);
    const [zipPhase, setZipPhase] = useState<ImportPhase>("start");
    const [vcsBusy, setVcsBusy] = useState(false);
    const [vcsPhase, setVcsPhase] = useState<ImportPhase>("start");
    const [vcsReady, setVcsReady] = useState(false);

    const handleImageBuilt = (reference: string) => {
        setSelectedImage({ reference });
        setSource("image");
    };

    const handleComposeDeployed = () => {
        onCreated();
        onClose();
    };

    return (
        <div className="fixed inset-0 z-40 grid place-items-center p-4 bg-black/60">
            <div className="relative w-full max-w-[720px] max-h-[85vh] bg-ink-800 border border-ink-700 shadow-card corners flex flex-col">
                <div className="flex items-center justify-between px-5 py-3 border-b border-ink-700 bg-ink-900/60 shrink-0">
                    <div className="font-mono text-[11px] text-ink-500">chatops://ops.local/<span className="text-accent">containers/import/{source}</span></div>
                    <button type="button" onClick={onClose} className="w-7 h-7 grid place-items-center border border-ink-700 text-ink-300 hover:text-ink-50">
                        <Icon name="x" />
                    </button>
                </div>

                <div className="px-6 pt-6 shrink-0">
                    <h2 className="font-sans text-[22px] tracking-tight text-ink-50">Import container</h2>
                    <p className="text-[12px] text-ink-500 mt-1.5">Choose where to import the container from.</p>
                </div>

                <div className="grid grid-cols-3 border-y border-ink-700 mt-5 shrink-0">
                    {SOURCE_TABS.map((t, i) => (
                        <button
                            key={t.key}
                            type="button"
                            onClick={() => setSource(t.key)}
                            className={`py-3 font-mono text-[11px] tracking-[0.18em] uppercase ${i < SOURCE_TABS.length - 1 ? "border-r border-ink-700" : ""} ${
                                source === t.key ? "text-ink-50 bg-ink-900 shadow-[inset_0_-2px_0_#bef264]" : "text-ink-500 hover:text-ink-200"
                            }`}
                        >
                            {t.label}
                        </button>
                    ))}
                </div>

                <div className="flex-1 overflow-y-auto scrollbar p-6">
                    {source === "vcs" && (
                        <VcsPanel
                            busy={vcsBusy}
                            onBusyChange={setVcsBusy}
                            onImageBuilt={handleImageBuilt}
                            onPhaseChange={setVcsPhase}
                            onReadyChange={setVcsReady}
                            onComposeDeployed={handleComposeDeployed}
                            onClose={onClose}
                        />
                    )}
                    {source === "zip" && (
                        <ZipPanel
                            busy={zipBusy}
                            onBusyChange={setZipBusy}
                            onImageBuilt={handleImageBuilt}
                            onPhaseChange={setZipPhase}
                            onComposeDeployed={handleComposeDeployed}
                        />
                    )}
                    {source === "image" && (
                        selectedImage ? (
                            <ContainerCreateForm
                                image={selectedImage}
                                onBack={() => setSelectedImage(null)}
                                busy={creating}
                                onBusyChange={setCreating}
                                onCreated={() => { onCreated(); onClose(); }}
                            />
                        ) : (
                            <ImagePanel onSelectImage={setSelectedImage} />
                        )
                    )}
                </div>

                <div className="px-6 py-4 border-t border-ink-700 flex items-center justify-end gap-2 shrink-0">
                    <button type="button" onClick={onClose} disabled={creating || zipBusy || vcsBusy} className="px-4 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-ink-900 disabled:opacity-50">
                        Close
                    </button>
                    {source === "image" && selectedImage && (
                        <button
                            type="submit"
                            form={CREATE_CONTAINER_FORM_ID}
                            disabled={creating}
                            className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 disabled:opacity-60"
                        >
                            {creating ? "Creating…" : "Create container"}
                        </button>
                    )}
                    {source === "zip" && (
                        <button
                            type="submit"
                            form={ZIP_IMPORT_FORM_ID}
                            disabled={zipBusy}
                            className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 disabled:opacity-60"
                        >
                            {zipBusy
                                ? "Working…"
                                : zipPhase === "dockerfile-form"
                                    ? "Build image"
                                    : zipPhase === "compose-review"
                                        ? "Deploy stack"
                                        : "Upload & scan"}
                        </button>
                    )}
                    {source === "vcs" && (
                        <button
                            type="submit"
                            form={VCS_IMPORT_FORM_ID}
                            disabled={vcsBusy || !vcsReady}
                            className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 disabled:opacity-60"
                        >
                            {vcsBusy
                                ? "Working…"
                                : vcsPhase === "dockerfile-form"
                                    ? "Build image"
                                    : vcsPhase === "compose-review"
                                        ? "Deploy stack"
                                        : "Clone & scan"}
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}
