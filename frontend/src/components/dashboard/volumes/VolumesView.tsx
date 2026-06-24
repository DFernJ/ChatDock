import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../../../context/AuthContext.tsx";
import { canDelete, canEdit } from "../../../lib/permissions.ts";
import { ApiError } from "../../../lib/api.ts";
import { deleteVolume, listVolumes } from "../../../lib/api/docker.ts";
import type { VolumeDTO } from "../../../types/docker.ts";
import {
    ActionBtn,
    ConfirmDialog,
    EmptyState,
    ErrorBanner,
    Icon,
    Section,
    Toolbar,
} from "../../Ui.tsx";
import CreateVolumeModal from "./CreateVolumeModal.tsx";

const PERMISSION_DELETE_REASON = "You need root permissions to delete volumes.";

function VolumeRow({ volume, canRemove, onDelete }: {
    volume: VolumeDTO;
    canRemove: boolean;
    onDelete: (volume: VolumeDTO) => void;
}) {
    const [collapsed, setCollapsed] = useState(false);
    const hasMounts = volume.mountPoints.length > 0;

    return (
        <div className="border-b border-ink-700/70 last:border-b-0">
            <div className="grid grid-cols-[24px_minmax(200px,1.6fr)_140px_160px_auto] items-center gap-4 px-5 py-3.5 hover:bg-ink-800/40 transition">
                <button
                    type="button"
                    onClick={() => setCollapsed(c => !c)}
                    disabled={!hasMounts}
                    className="text-ink-500 hover:text-ink-100 disabled:opacity-30 disabled:cursor-not-allowed"
                >
                    <Icon name="chev" className={`w-3 h-3 transition-transform ${collapsed ? "" : "rotate-90"}`} />
                </button>

                <div className="flex flex-col min-w-0">
                    <span className="font-mono text-[13px] text-ink-50 truncate">{volume.name}</span>
                    <span className="font-mono text-[11px] text-ink-600 truncate">
                        {hasMounts ? `${volume.mountPoints.length} mount point(s)` : "no mount points"}
                    </span>
                </div>

                <div className="flex flex-col gap-0.5">
                    <span className="font-mono text-[11px] tracking-[0.16em] uppercase text-ink-300">{volume.driver}</span>
                    <span className="font-mono text-[10px] text-ink-600">driver</span>
                </div>

                <div className="flex flex-col gap-0.5">
                    <span className="font-mono text-[11px] text-ink-300">
                        {volume.usedInContainers} container{volume.usedInContainers === 1 ? "" : "s"}
                    </span>
                    <span className="font-mono text-[10px] text-ink-600">in use</span>
                </div>

                <div className="flex items-center gap-1.5 justify-end">
                    <ActionBtn
                        icon="trash"
                        label="Delete"
                        variant="danger"
                        disabled={!canRemove}
                        disabledReason={!canRemove ? PERMISSION_DELETE_REASON : undefined}
                        onClick={() => onDelete(volume)}
                    />
                </div>
            </div>

            {!collapsed && hasMounts && (
                <div className="px-5 pb-3.5 pl-[52px] flex flex-col gap-2">
                    {volume.mountPoints.map(mp => (
                        <div key={mp.name} className="border border-ink-700 bg-ink-900/30 p-2.5">
                            <div className="font-mono text-[12px] text-ink-100">{mp.name}</div>
                            <div className="flex flex-col gap-0.5 mt-1">
                                {mp.mounts.map((m, i) => (
                                    <span key={i} className="font-mono text-[11px] text-ink-500 truncate">{m}</span>
                                ))}
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

export default function VolumesView() {
    const { user } = useAuth();
    const canMutate = canEdit(user?.permissionRole);
    const canRemove = canDelete(user?.permissionRole);

    const [volumes, setVolumes] = useState<VolumeDTO[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [search, setSearch] = useState("");
    const [pending, setPending] = useState<VolumeDTO | null>(null);
    const [busy, setBusy] = useState(false);
    const [showCreate, setShowCreate] = useState(false);

    const refresh = useCallback(async () => {
        setError(null);
        try {
            setVolumes(await listVolumes());
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not load volumes");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        setLoading(true);
        refresh();
        const id = window.setInterval(refresh, 5000);
        return () => window.clearInterval(id);
    }, [refresh]);

    const filtered = useMemo(() => {
        const q = search.trim().toLowerCase();
        const list = q ? volumes.filter(v => v.name.toLowerCase().includes(q)) : volumes;
        return [...list].sort((a, b) => a.name.localeCompare(b.name));
    }, [volumes, search]);

    const confirmDelete = async () => {
        if (!pending) return;
        setBusy(true);
        setError(null);
        try {
            await deleteVolume(pending.name);
            setPending(null);
            await refresh();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not delete the volume");
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="h-full flex flex-col gap-6 min-h-0">
            <Toolbar
                search={search}
                onSearchChange={setSearch}
                onRefresh={refresh}
                refreshing={loading}
                searchPlaceholder="Search volume name…"
                primaryLabel="Create volume"
                onPrimary={() => setShowCreate(true)}
                primaryDisabled={!canMutate}
            />

            {error && <ErrorBanner message={error} />}

            <Section
                title="Volumes"
                subtitle="Named volumes managed by the Docker daemon on this host."
                badge="storage"
                count={filtered.length}
                className="flex-1 min-h-0"
            >
                {loading && volumes.length === 0 ? (
                    <div className="px-5 py-10 text-center font-mono text-[12px] text-ink-500">loading…</div>
                ) : filtered.length === 0 ? (
                    <EmptyState title="No volumes found" message="Click Create volume to add your first one." />
                ) : (
                    filtered.map(v => (
                        <VolumeRow
                            key={v.name}
                            volume={v}
                            canRemove={canRemove}
                            onDelete={setPending}
                        />
                    ))
                )}
            </Section>

            <ConfirmDialog
                open={!!pending}
                title="Delete volume"
                body={
                    pending
                        ? pending.usedInContainers > 0
                            ? `"${pending.name}" is currently used by ${pending.usedInContainers} container(s). Detach or remove those containers first, or this deletion will fail.`
                            : `"${pending.name}" will be permanently deleted. This can't be undone.`
                        : ""
                }
                confirmLabel="Delete"
                danger
                busy={busy}
                onCancel={() => setPending(null)}
                onConfirm={confirmDelete}
            />

            {showCreate && (
                <CreateVolumeModal
                    onClose={() => setShowCreate(false)}
                    onCreated={() => { setShowCreate(false); refresh(); }}
                />
            )}
        </div>
    );
}
