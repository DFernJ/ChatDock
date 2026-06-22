import { useCallback, useEffect, useState } from "react";
import type { ChangeEvent } from "react";
import { ApiError } from "../../../lib/api.ts";
import {
    createContainerSecret,
    deleteContainerSecret,
    getContainerConfig,
    getContainerSecretValue,
    listContainerSecrets,
    listNetworks,
    listVolumes,
    updateContainer,
    updateContainerSecret,
} from "../../../lib/api/docker.ts";
import type { AppSecretDTO, ContainerDTO, ContainerVolumeDTO, NetworkDTO, VolumeDTO } from "../../../types/docker.ts";
import { ConfirmDialog, Field, Icon, Modal } from "../../Ui.tsx";

type RestartPolicyName = "no" | "always" | "unless-stopped" | "on-failure";

const RESTART_POLICY_OPTIONS: { value: RestartPolicyName; label: string }[] = [
    { value: "no", label: "no" },
    { value: "always", label: "always" },
    { value: "unless-stopped", label: "unless stopped" },
    { value: "on-failure", label: "on failure" },
];

function bytesToMB(bytes: number | null): string {
    return bytes && bytes > 0 ? String(Math.round(bytes / (1024 * 1024))) : "";
}

function nanoCpusToCores(nanoCPUs: number | null): string {
    return nanoCPUs && nanoCPUs > 0 ? String(nanoCPUs / 1_000_000_000) : "";
}

function TextInput({ value, onChange, placeholder, type = "text" }: {
    value: string; onChange: (v: string) => void; placeholder?: string; type?: string;
}) {
    return (
        <input
            type={type}
            value={value}
            onChange={(e: ChangeEvent<HTMLInputElement>) => onChange(e.target.value)}
            placeholder={placeholder}
            className="flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600 focus:border-accent"
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

function pathToSegments(path: string): string[] {
    const parts = path.split("/").filter(Boolean);
    return parts.length > 0 ? parts : [""];
}

function segmentsToPath(segments: string[]): string {
    return "/" + segments.map(s => s.trim()).filter(Boolean).join("/");
}

function PathSegmentsInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
    const [segments, setSegments] = useState<string[]>(() => pathToSegments(value));

    const commit = (next: string[]) => {
        setSegments(next);
        onChange(segmentsToPath(next));
    };

    return (
        <div className="flex items-center gap-1 flex-wrap p-2 border border-ink-700 bg-ink-900/60 focus-within:border-accent">
            <span className="font-mono text-ink-500 text-[13px] px-1">/</span>
            {segments.map((seg, i) => (
                <div key={i} className="flex items-center gap-1">
                    {i > 0 && <span className="font-mono text-ink-500 text-[13px]">/</span>}
                    <input
                        value={seg}
                        onChange={(e: ChangeEvent<HTMLInputElement>) => commit(segments.map((s, idx) => idx === i ? e.target.value : s))}
                        placeholder="folder"
                        size={Math.max(6, seg.length || 6)}
                        className="bg-ink-800/60 border border-ink-700 text-ink-50 font-mono text-[12px] px-2 py-1.5 placeholder:text-ink-600 focus:border-accent focus:outline-none"
                    />
                    {segments.length > 1 && (
                        <button
                            type="button"
                            onClick={() => commit(segments.filter((_, idx) => idx !== i))}
                            className="text-ink-600 hover:text-rose-300"
                        >
                            <Icon name="x" className="w-2.5 h-2.5" />
                        </button>
                    )}
                </div>
            ))}
            <button
                type="button"
                onClick={() => commit([...segments, ""])}
                className="w-6 h-6 grid place-items-center border border-ink-700 text-ink-400 hover:text-ink-100 hover:border-ink-600 transition"
            >
                <Icon name="plus" className="w-2.5 h-2.5" />
            </button>
        </div>
    );
}

export default function EditContainerModal({ container, onClose, onUpdated }: {
    container: ContainerDTO;
    onClose: () => void;
    onUpdated: () => void;
}) {
    const [loading, setLoading] = useState(true);
    const [loaded, setLoaded] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const [memoryMB, setMemoryMB] = useState("");
    const [cpuCores, setCpuCores] = useState("");
    const [restartPolicy, setRestartPolicy] = useState<RestartPolicyName>("no");
    const [maxRetryCount, setMaxRetryCount] = useState("0");

    const [availableNetworks, setAvailableNetworks] = useState<NetworkDTO[]>([]);
    const [selectedNetworks, setSelectedNetworks] = useState<string[]>([]);

    const [availableVolumes, setAvailableVolumes] = useState<VolumeDTO[]>([]);
    const [volumeRows, setVolumeRows] = useState<ContainerVolumeDTO[]>([]);

    const [secrets, setSecrets] = useState<AppSecretDTO[]>([]);
    const [secretsLoading, setSecretsLoading] = useState(true);
    const [secretsUnavailable, setSecretsUnavailable] = useState(false);
    const [secretsError, setSecretsError] = useState<string | null>(null);
    const [secretBusy, setSecretBusy] = useState(false);
    const [newSecretName, setNewSecretName] = useState("");
    const [newSecretValue, setNewSecretValue] = useState("");
    const [editingSecretId, setEditingSecretId] = useState<string | null>(null);
    const [editingSecretValue, setEditingSecretValue] = useState("");
    const [pendingDeleteSecret, setPendingDeleteSecret] = useState<AppSecretDTO | null>(null);
    const [revealedSecrets, setRevealedSecrets] = useState<Record<string, string>>({});
    const [revealBusy, setRevealBusy] = useState<string | null>(null);

    const refreshSecrets = useCallback(() => {
        setSecretsLoading(true);
        setSecretsError(null);
        listContainerSecrets(container.id)
            .then(list => { setSecrets(list); setSecretsUnavailable(false); })
            .catch(e => {
                if (e instanceof ApiError && e.status === 400) {
                    setSecretsUnavailable(true);
                } else {
                    setSecretsError(e instanceof ApiError ? e.message : "Could not load secrets.");
                }
            })
            .finally(() => setSecretsLoading(false));
    }, [container.id]);

    useEffect(() => {
        refreshSecrets();
    }, [refreshSecrets]);

    const addSecret = async () => {
        if (!newSecretName.trim() || !newSecretValue.trim()) return;
        setSecretBusy(true);
        setSecretsError(null);
        try {
            await createContainerSecret(container.id, newSecretName.trim(), newSecretValue);
            setNewSecretName("");
            setNewSecretValue("");
            refreshSecrets();
        } catch (e) {
            setSecretsError(e instanceof ApiError ? e.message : "Could not create the secret.");
        } finally {
            setSecretBusy(false);
        }
    };

    const saveSecretValue = async (secret: AppSecretDTO) => {
        if (!editingSecretValue.trim()) return;
        setSecretBusy(true);
        setSecretsError(null);
        try {
            await updateContainerSecret(container.id, secret.id, editingSecretValue);
            setEditingSecretId(null);
            setEditingSecretValue("");
            setRevealedSecrets(curr => {
                const next = { ...curr };
                delete next[secret.id];
                return next;
            });
            refreshSecrets();
        } catch (e) {
            setSecretsError(e instanceof ApiError ? e.message : "Could not update the secret.");
        } finally {
            setSecretBusy(false);
        }
    };

    const confirmDeleteSecret = async () => {
        if (!pendingDeleteSecret) return;
        setSecretBusy(true);
        setSecretsError(null);
        try {
            await deleteContainerSecret(container.id, pendingDeleteSecret.id);
            setPendingDeleteSecret(null);
            refreshSecrets();
        } catch (e) {
            setSecretsError(e instanceof ApiError ? e.message : "Could not delete the secret.");
        } finally {
            setSecretBusy(false);
        }
    };

    const toggleRevealSecret = async (secret: AppSecretDTO) => {
        if (secret.id in revealedSecrets) {
            setRevealedSecrets(curr => {
                const next = { ...curr };
                delete next[secret.id];
                return next;
            });
            return;
        }
        setRevealBusy(secret.id);
        setSecretsError(null);
        try {
            const { secretValue } = await getContainerSecretValue(container.id, secret.id);
            setRevealedSecrets(curr => ({ ...curr, [secret.id]: secretValue }));
        } catch (e) {
            setSecretsError(e instanceof ApiError ? e.message : "Could not reveal the secret.");
        } finally {
            setRevealBusy(null);
        }
    };

    useEffect(() => {
        let cancelled = false;
        Promise.all([getContainerConfig(container.id), listNetworks(), listVolumes()])
            .then(([config, networks, volumes]) => {
                if (cancelled) return;
                setMemoryMB(bytesToMB(config.memoryBytes));
                setCpuCores(nanoCpusToCores(config.nanoCPUs));
                setRestartPolicy((config.restartPolicyName as RestartPolicyName) ?? "no");
                setMaxRetryCount(String(config.restartPolicyMaxRetryCount ?? 0));
                setSelectedNetworks(config.networks);
                setVolumeRows(config.volumes);
                setAvailableNetworks(networks);
                setAvailableVolumes(volumes);
                setLoaded(true);
            })
            .catch(e => setError(e instanceof ApiError ? e.message : "Could not load the container configuration."))
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [container.id]);

    const toggleNetwork = (name: string) => {
        setSelectedNetworks(curr => curr.includes(name) ? curr.filter(n => n !== name) : [...curr, name]);
    };

    const addVolumeRow = () => {
        setVolumeRows(curr => [...curr, { volumeName: availableVolumes[0]?.name ?? "", target: "", readOnly: false }]);
    };
    const updateVolumeRow = (index: number, patch: Partial<ContainerVolumeDTO>) => {
        setVolumeRows(curr => curr.map((v, i) => i === index ? { ...v, ...patch } : v));
    };
    const removeVolumeRow = (index: number) => {
        setVolumeRows(curr => curr.filter((_, i) => i !== index));
    };

    const submit = async () => {
        setBusy(true);
        setError(null);
        try {
            await updateContainer(container.id, {
                memoryBytes: memoryMB.trim() ? Math.round(Number(memoryMB) * 1024 * 1024) : 0,
                nanoCPUs: cpuCores.trim() ? Math.round(Number(cpuCores) * 1_000_000_000) : 0,
                restartPolicyName: restartPolicy,
                restartPolicyMaxRetryCount: restartPolicy === "on-failure" ? (Number(maxRetryCount) || 0) : null,
                networks: selectedNetworks,
                volumes: volumeRows.filter(v => v.volumeName && v.target),
            });
            onUpdated();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not update the container.");
        } finally {
            setBusy(false);
        }
    };

    return (
        <>
        <Modal
            title="Modify container"
            subtitle={`Resources, restart policy, networks, volumes and secrets for ${container.name}. Changing volumes recreates the container.`}
            path={`containers/${container.name}/edit`}
            onClose={onClose}
            footer={
                <>
                    <button type="button" onClick={onClose} disabled={busy} className="px-4 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-ink-900 disabled:opacity-50">
                        Cancel
                    </button>
                    <button
                        type="button"
                        onClick={submit}
                        disabled={busy || loading || !loaded}
                        className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 disabled:opacity-60"
                    >
                        {busy ? "Saving…" : "Save changes"}
                    </button>
                </>
            }
        >
            {loading ? (
                <p className="text-[12px] text-ink-500 font-mono">Loading…</p>
            ) : !loaded ? (
                <p className="text-[12px] text-rose-400 font-mono">{error}</p>
            ) : (
                <div className="flex flex-col gap-5">
                    <div className="grid grid-cols-2 gap-3">
                        <Field label="Memory limit" hint="MB · 0 = unlimited">
                            <TextInput type="number" value={memoryMB} onChange={setMemoryMB} placeholder="unlimited" />
                        </Field>
                        <Field label="CPU limit" hint="cores · 0 = unlimited">
                            <TextInput type="number" value={cpuCores} onChange={setCpuCores} placeholder="unlimited" />
                        </Field>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                        <Field label="Restart policy">
                            <Select value={restartPolicy} onChange={(v) => setRestartPolicy(v as RestartPolicyName)} options={RESTART_POLICY_OPTIONS} />
                        </Field>
                        {restartPolicy === "on-failure" && (
                            <Field label="Max retries">
                                <TextInput type="number" value={maxRetryCount} onChange={setMaxRetryCount} placeholder="0" />
                            </Field>
                        )}
                    </div>

                    <Field label="Networks">
                        <div className="flex flex-wrap gap-2">
                            {availableNetworks.length === 0 && (
                                <span className="text-[12px] text-ink-500 font-mono">No networks available.</span>
                            )}
                            {availableNetworks.map(n => {
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
                            })}
                        </div>
                    </Field>

                    <Field label="Volumes" hint="recreates the container if changed">
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
                                        <PathSegmentsInput value={v.target} onChange={(value) => updateVolumeRow(i, { target: value })} />
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

                    <Field label="Secrets" hint="stored encrypted · use Show to reveal a value">
                        {secretsLoading ? (
                            <p className="text-[12px] text-ink-500 font-mono">Loading…</p>
                        ) : secretsUnavailable ? (
                            <p className="text-[12px] text-ink-500 font-mono">This container isn't linked to a managed app yet, so secrets aren't available.</p>
                        ) : (
                            <div className="flex flex-col gap-2">
                                {secrets.length === 0 && (
                                    <p className="text-[12px] text-ink-500 font-mono">No secrets configured.</p>
                                )}
                                {secrets.map(s => (
                                    <div key={s.id} className="border border-ink-700 bg-ink-900/30 p-2 flex flex-col gap-2">
                                        <div className="flex items-center gap-2">
                                            <span className="flex-1 min-w-0 font-mono text-[13px] text-ink-100 truncate">{s.secretName}</span>
                                            {editingSecretId === s.id ? (
                                                <>
                                                    <div className="flex-1 min-w-0">
                                                        <TextInput
                                                            type="password"
                                                            value={editingSecretValue}
                                                            onChange={setEditingSecretValue}
                                                            placeholder="new value"
                                                        />
                                                    </div>
                                                    <button
                                                        type="button"
                                                        disabled={secretBusy || !editingSecretValue.trim()}
                                                        onClick={() => saveSecretValue(s)}
                                                        className="px-3 py-1.5 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.1em] uppercase hover:brightness-110 disabled:opacity-60 shrink-0"
                                                    >
                                                        Save
                                                    </button>
                                                    <button
                                                        type="button"
                                                        disabled={secretBusy}
                                                        onClick={() => { setEditingSecretId(null); setEditingSecretValue(""); }}
                                                        className="px-3 py-1.5 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.1em] uppercase hover:bg-ink-900 disabled:opacity-50 shrink-0"
                                                    >
                                                        Cancel
                                                    </button>
                                                </>
                                            ) : (
                                                <>
                                                    <button
                                                        type="button"
                                                        disabled={revealBusy === s.id}
                                                        onClick={() => toggleRevealSecret(s)}
                                                        title={s.id in revealedSecrets ? "Hide" : "Show"}
                                                        className="w-8 h-8 shrink-0 grid place-items-center border border-ink-700 text-ink-300 hover:bg-ink-800 hover:border-ink-600 transition disabled:opacity-50"
                                                    >
                                                        <Icon name={s.id in revealedSecrets ? "eyeOff" : "eye"} className="w-3.5 h-3.5" />
                                                    </button>
                                                    <button
                                                        type="button"
                                                        onClick={() => { setEditingSecretId(s.id); setEditingSecretValue(""); }}
                                                        className="w-8 h-8 shrink-0 grid place-items-center border border-ink-700 text-ink-300 hover:bg-ink-800 hover:border-ink-600 transition"
                                                    >
                                                        <Icon name="edit" className="w-3.5 h-3.5" />
                                                    </button>
                                                    <button
                                                        type="button"
                                                        onClick={() => setPendingDeleteSecret(s)}
                                                        className="w-8 h-8 shrink-0 grid place-items-center border border-ink-700 text-rose-300 hover:bg-rose-500/10 hover:border-rose-400/50 transition"
                                                    >
                                                        <Icon name="trash" className="w-3.5 h-3.5" />
                                                    </button>
                                                </>
                                            )}
                                        </div>
                                        {editingSecretId !== s.id && s.id in revealedSecrets && (
                                            <div className="font-mono text-[12px] text-accent break-all bg-ink-900/60 border border-ink-700 px-2 py-1.5">
                                                {revealedSecrets[s.id]}
                                            </div>
                                        )}
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
                                        disabled={secretBusy || !newSecretName.trim() || !newSecretValue.trim()}
                                        onClick={addSecret}
                                        className="w-9 shrink-0 grid place-items-center border border-ink-700 text-ink-300 hover:bg-ink-800 hover:border-ink-600 transition disabled:opacity-40 disabled:cursor-not-allowed"
                                    >
                                        <Icon name="plus" className="w-3 h-3" />
                                    </button>
                                </div>
                                {secretsError && <p className="text-[12px] text-rose-400 font-mono">{secretsError}</p>}
                            </div>
                        )}
                    </Field>
                </div>
            )}
            {loaded && error && <p className="text-[12px] text-rose-400 font-mono mt-1">{error}</p>}
        </Modal>

        <ConfirmDialog
            open={!!pendingDeleteSecret}
            title="Delete secret"
            body={`The secret "${pendingDeleteSecret?.secretName ?? ""}" will be permanently deleted. Containers using it won't pick up the removal until redeployed.`}
            confirmLabel="Delete"
            danger
            busy={secretBusy}
            onCancel={() => setPendingDeleteSecret(null)}
            onConfirm={confirmDeleteSecret}
        />
        </>
    );
}
