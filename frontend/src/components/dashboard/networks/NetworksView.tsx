import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../../../context/AuthContext.tsx";
import { canDelete, canEdit } from "../../../lib/permissions.ts";
import { ApiError } from "../../../lib/api.ts";
import {
    deleteNetwork,
    disconnectContainerFromNetwork,
    listContainers,
    listNetworks,
    pruneNetworks,
} from "../../../lib/api/docker.ts";
import type { ContainerDTO, NetworkDTO } from "../../../types/docker.ts";
import {
    ActionBtn,
    ConfirmDialog,
    EmptyState,
    ErrorBanner,
    Icon,
    Section,
    Toolbar,
} from "../../Ui.tsx";
import CreateNetworkModal from "./CreateNetworkModal.tsx";
import AttachNetworkModal from "./AttachNetworkModal.tsx";

interface PendingDetach {
    network: NetworkDTO;
    containerName: string;
    containerId?: string;
}

const PERMISSION_EDIT_REASON = "You need editor permissions or higher to manage networks.";
const PERMISSION_DELETE_REASON = "You need root permissions to delete networks.";

function NetworkRow({ network, canMutate, canRemove, containerIdByName, onAttach, onDelete, onDetach }: {
    network: NetworkDTO;
    canMutate: boolean;
    canRemove: boolean;
    containerIdByName: Map<string, string>;
    onAttach: (network: NetworkDTO) => void;
    onDelete: (network: NetworkDTO) => void;
    onDetach: (network: NetworkDTO, containerName: string) => void;
}) {
    const [collapsed, setCollapsed] = useState(false);
    const hasConnected = network.connectedContainers.length > 0;

    return (
        <div className="border-b border-ink-700/70 last:border-b-0">
            <div className="grid grid-cols-[24px_minmax(200px,1.6fr)_120px_100px_140px_auto] items-center gap-4 px-5 py-3.5 hover:bg-ink-800/40 transition">
                <button
                    type="button"
                    onClick={() => setCollapsed(c => !c)}
                    disabled={!hasConnected}
                    className="text-ink-500 hover:text-ink-100 disabled:opacity-30 disabled:cursor-not-allowed"
                >
                    <Icon name="chev" className={`w-3 h-3 transition-transform ${collapsed ? "" : "rotate-90"}`} />
                </button>

                <div className="flex flex-col min-w-0">
                    <span className="font-mono text-[13px] text-ink-50 truncate">{network.name}</span>
                    <span className="font-mono text-[11px] text-ink-600 truncate">{network.id.slice(0, 12)}</span>
                </div>

                <div className="flex flex-col gap-0.5">
                    <span className="font-mono text-[11px] tracking-[0.16em] uppercase text-ink-300">{network.driver}</span>
                    <span className="font-mono text-[10px] text-ink-600">driver</span>
                </div>

                <div className="flex flex-col gap-0.5">
                    <span className="font-mono text-[11px] tracking-[0.16em] uppercase text-ink-300">{network.scope}</span>
                    <span className="font-mono text-[10px] text-ink-600">scope</span>
                </div>

                <div className="flex flex-col gap-0.5">
                    <span className="font-mono text-[11px] text-ink-300">
                        {network.attachedToContainers} container{network.attachedToContainers === 1 ? "" : "s"}
                    </span>
                    <span className="font-mono text-[10px] text-ink-600">attached</span>
                </div>

                <div className="flex items-center gap-1.5 justify-end">
                    <ActionBtn
                        icon="link"
                        label="Attach container"
                        disabled={!canMutate}
                        disabledReason={!canMutate ? PERMISSION_EDIT_REASON : undefined}
                        onClick={() => onAttach(network)}
                    />
                    <ActionBtn
                        icon="trash"
                        label="Delete"
                        variant="danger"
                        disabled={!canRemove}
                        disabledReason={!canRemove ? PERMISSION_DELETE_REASON : undefined}
                        onClick={() => onDelete(network)}
                    />
                </div>
            </div>

            {!collapsed && hasConnected && (
                <div className="px-5 pb-3.5 pl-[52px] flex flex-col gap-2">
                    {network.connectedContainers.map(name => (
                        <div key={name} className="border border-ink-700 bg-ink-900/30 p-2 flex items-center gap-2">
                            <div className="flex-1 min-w-0 font-mono text-[12px] text-ink-100 truncate">{name}</div>
                            <ActionBtn
                                icon="unlink"
                                label="Detach"
                                variant="danger"
                                disabled={!canMutate || !containerIdByName.has(name)}
                                disabledReason={
                                    !canMutate
                                        ? PERMISSION_EDIT_REASON
                                        : !containerIdByName.has(name)
                                            ? "This container no longer exists on the host."
                                            : undefined
                                }
                                onClick={() => onDetach(network, name)}
                            />
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

export default function NetworksView() {
    const { user } = useAuth();
    const canMutate = canEdit(user?.permissionRole);
    const canRemove = canDelete(user?.permissionRole);

    const [networks, setNetworks] = useState<NetworkDTO[]>([]);
    const [containers, setContainers] = useState<ContainerDTO[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [search, setSearch] = useState("");
    const [busy, setBusy] = useState(false);
    const [showCreate, setShowCreate] = useState(false);
    const [attachingTo, setAttachingTo] = useState<NetworkDTO | null>(null);
    const [pendingDelete, setPendingDelete] = useState<NetworkDTO | null>(null);
    const [pendingDetach, setPendingDetach] = useState<PendingDetach | null>(null);
    const [pendingPrune, setPendingPrune] = useState(false);

    const refresh = useCallback(async () => {
        setError(null);
        try {
            const [networksList, containersList] = await Promise.all([listNetworks(), listContainers()]);
            setNetworks(networksList);
            setContainers(containersList);
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not load networks");
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
        const list = q ? networks.filter(n => n.name.toLowerCase().includes(q)) : networks;
        return [...list].sort((a, b) => a.name.localeCompare(b.name));
    }, [networks, search]);

    const containerIdByName = useMemo(() => {
        const map = new Map<string, string>();
        for (const c of containers) map.set(c.name, c.id);
        return map;
    }, [containers]);

    const availableContainers = useMemo(() => {
        if (!attachingTo) return [];
        const connected = new Set(attachingTo.connectedContainers);
        return containers.filter(c => !connected.has(c.name));
    }, [attachingTo, containers]);

    const confirmDelete = async () => {
        if (!pendingDelete) return;
        setBusy(true);
        setError(null);
        try {
            await deleteNetwork(pendingDelete.id);
            setPendingDelete(null);
            await refresh();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not delete the network");
        } finally {
            setBusy(false);
        }
    };

    const confirmDetach = async () => {
        if (!pendingDetach) return;
        const containerId = containerIdByName.get(pendingDetach.containerName);
        if (!containerId) {
            setPendingDetach(null);
            return;
        }
        setBusy(true);
        setError(null);
        try {
            await disconnectContainerFromNetwork(pendingDetach.network.id, containerId, true);
            setPendingDetach(null);
            await refresh();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not detach the container");
        } finally {
            setBusy(false);
        }
    };

    const confirmPrune = async () => {
        setBusy(true);
        setError(null);
        try {
            await pruneNetworks();
            setPendingPrune(false);
            await refresh();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not prune networks");
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
                searchPlaceholder="Search network name…"
                primaryLabel="Create network"
                onPrimary={() => setShowCreate(true)}
                primaryDisabled={!canMutate}
                secondaryLabel="Prune unused"
                onSecondary={() => setPendingPrune(true)}
                secondaryDisabled={!canRemove}
            />

            {error && <ErrorBanner message={error} />}

            <Section
                title="Networks"
                subtitle="Networks managed by the Docker daemon on this host."
                badge="network"
                count={filtered.length}
                className="flex-1 min-h-0"
            >
                {loading && networks.length === 0 ? (
                    <div className="px-5 py-10 text-center font-mono text-[12px] text-ink-500">loading…</div>
                ) : filtered.length === 0 ? (
                    <EmptyState title="No networks found" message="Click Create network to add your first one." />
                ) : (
                    filtered.map(n => (
                        <NetworkRow
                            key={n.id}
                            network={n}
                            canMutate={canMutate}
                            canRemove={canRemove}
                            containerIdByName={containerIdByName}
                            onAttach={setAttachingTo}
                            onDelete={setPendingDelete}
                            onDetach={(network, containerName) => setPendingDetach({ network, containerName })}
                        />
                    ))
                )}
            </Section>

            <ConfirmDialog
                open={!!pendingDelete}
                title="Delete network"
                body={
                    pendingDelete
                        ? pendingDelete.attachedToContainers > 0
                            ? `"${pendingDelete.name}" is currently attached to ${pendingDelete.attachedToContainers} container(s). Detach those containers first, or this deletion will fail.`
                            : `"${pendingDelete.name}" will be permanently deleted. This can't be undone.`
                        : ""
                }
                confirmLabel="Delete"
                danger
                busy={busy}
                onCancel={() => setPendingDelete(null)}
                onConfirm={confirmDelete}
            />

            <ConfirmDialog
                open={!!pendingDetach}
                title="Detach container"
                body={pendingDetach ? `"${pendingDetach.containerName}" will be detached from the "${pendingDetach.network.name}" network.` : ""}
                confirmLabel="Detach"
                danger
                busy={busy}
                onCancel={() => setPendingDetach(null)}
                onConfirm={confirmDetach}
            />

            <ConfirmDialog
                open={pendingPrune}
                title="Prune unused networks"
                body="All networks not used by at least one container will be permanently removed. This can't be undone."
                confirmLabel="Prune"
                danger
                busy={busy}
                onCancel={() => setPendingPrune(false)}
                onConfirm={confirmPrune}
            />

            {showCreate && (
                <CreateNetworkModal
                    onClose={() => setShowCreate(false)}
                    onCreated={() => { setShowCreate(false); refresh(); }}
                />
            )}

            {attachingTo && (
                <AttachNetworkModal
                    network={attachingTo}
                    availableContainers={availableContainers}
                    onClose={() => setAttachingTo(null)}
                    onAttached={(containerName) => {
                        const networkId = attachingTo.id;
                        setAttachingTo(null);
                        if (containerName) {
                            setNetworks(curr => curr.map(n => n.id === networkId && !n.connectedContainers.includes(containerName)
                                ? {
                                    ...n,
                                    attachedToContainers: n.attachedToContainers + 1,
                                    connectedContainers: [...n.connectedContainers, containerName],
                                }
                                : n
                            ));
                        }
                        refresh();
                    }}
                />
            )}
        </div>
    );
}
