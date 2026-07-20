import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../../../context/AuthContext.tsx";
import { canDelete, canEdit } from "../../../lib/permissions.ts";
import { ApiError } from "../../../lib/api.ts";
import {
    deleteContainer,
    downloadContainerLogs,
    getContainersStats,
    listContainers,
    listStacks,
    restartContainer,
    startContainer,
    stopContainer,
} from "../../../lib/api/docker.ts";
import type { ContainerDTO, ContainerStatsDTO, StackDTO } from "../../../types/docker.ts";
import {
    ActionBtn,
    Bar,
    ConfirmDialog,
    EmptyState,
    ErrorBanner,
    Icon,
    Section,
    Toolbar,
} from "../../Ui.tsx";
import EditContainerModal from "./EditContainerModal.tsx";
import CreateStackModal from "./CreateStackModal.tsx";
import EditStackModal from "./EditStackModal.tsx";
import AssignStackModal from "./AssignStackModal.tsx";
import AddContainerToStackModal from "./AddContainerToStackModal.tsx";
import ContainerDetailsDrawer from "./ContainerDetailsDrawer.tsx";
import ImportContainerModal from "./ImportContainerModal.tsx";
import AiDiagnosisModal from "./AiDiagnosisModal.tsx";

type LifecycleAction = "start" | "stop" | "restart" | "delete";

interface PendingAction {
    container: ContainerDTO;
    action: LifecycleAction;
}

const STATUS_META: Record<string, { label: string; dot: string; text: string }> = {
    running: { label: "running", dot: "bg-accent", text: "text-accent" },
    paused: { label: "paused", dot: "bg-amber-300", text: "text-amber-300" },
    restarting: { label: "restarting", dot: "bg-sky-300", text: "text-sky-300" },
    exited: { label: "stopped", dot: "bg-ink-500", text: "text-ink-400" },
    created: { label: "created", dot: "bg-ink-500", text: "text-ink-400" },
    dead: { label: "dead", dot: "bg-rose-400", text: "text-rose-400" },
};

function statusMeta(state: string) {
    return STATUS_META[state] ?? { label: state || "unknown", dot: "bg-ink-500", text: "text-ink-400" };
}

const ACTION_COPY: Record<LifecycleAction, { title: string; body: string; confirmLabel: string; danger?: boolean }> = {
    start: { title: "Start container", body: "The container will be started.", confirmLabel: "Start" },
    stop: { title: "Stop container", body: "The container will be gracefully stopped.", confirmLabel: "Stop" },
    restart: {
        title: "Restart container",
        body: "The container will be restarted. Active connections will be dropped briefly.",
        confirmLabel: "Restart",
    },
    delete: {
        title: "Delete container",
        body: "The container will be stopped and removed. Any unmounted volume data will be lost.",
        confirmLabel: "Delete",
        danger: true,
    },
};

const PERMISSION_EDIT_REASON = "You need editor permissions or higher to manage containers.";
const PERMISSION_DELETE_REASON = "You need root permissions to delete containers.";
const ESSENTIAL_EDIT_REASON = "Essential containers can't be modified from the UI.";

function ContainerRow({ container, stats, canMutate, canRemove, onAction, onEdit, onAssign, onViewDetails, onDownloadLogs, onDiagnose }: {
    container: ContainerDTO;
    stats?: ContainerStatsDTO;
    canMutate: boolean;
    canRemove: boolean;
    onAction: (container: ContainerDTO, action: LifecycleAction) => void;
    onEdit: (container: ContainerDTO) => void;
    onAssign: (container: ContainerDTO) => void;
    onViewDetails: (container: ContainerDTO) => void;
    onDownloadLogs: (container: ContainerDTO) => void;
    onDiagnose: (container: ContainerDTO) => void;
}) {
    const meta = statusMeta(container.state);
    const isRunning = container.state === "running";
    const isRestarting = container.state === "restarting";
    const canStop = isRunning || isRestarting;
    const cpu = stats ? Math.round(stats.cpuPercent) : 0;
    const mem = stats ? Math.round(stats.memPercent) : 0;
    const canDiagnose = container.failed;

    return (
        <div className="grid grid-cols-[24px_minmax(200px,1.6fr)_110px_140px_140px_auto] items-center gap-4 px-5 py-3.5 border-b border-ink-700/70 hover:bg-ink-800/40 transition">
            <span className="flex items-center justify-center">
                <span className={`w-2 h-2 ${meta.dot} ${isRunning ? `shadow-[0_0_8px_currentColor] ${meta.text}` : ""}`}></span>
            </span>

            <div className="flex flex-col min-w-0">
                <span className="font-mono text-[13px] text-ink-50 truncate">{container.name}</span>
                {container.subdomainUrl ? (
                    <a
                        href={container.subdomainUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="font-mono text-[11px] text-accent hover:underline truncate"
                    >
                        {container.subdomainUrl.replace(/^https?:\/\//, "")}
                    </a>
                ) : (
                    <span className="font-mono text-[11px] text-ink-600 truncate">no subdomain linked</span>
                )}
            </div>

            <div className="flex flex-col gap-0.5 min-w-0">
                <span className={`font-mono text-[11px] tracking-[0.16em] uppercase ${meta.text}`}>{meta.label}</span>
                <span className="font-mono text-[10px] text-ink-600 truncate">{container.status || "—"}</span>
            </div>

            <div className="flex flex-col gap-1.5">
                <div className="flex justify-between text-[10px] tracking-[0.16em] uppercase">
                    <span className="text-ink-500">cpu</span>
                    <span className="text-ink-300 font-mono">{isRunning ? `${cpu}%` : "—"}</span>
                </div>
                <Bar value={isRunning ? cpu : 0} />
            </div>

            <div className="flex flex-col gap-1.5">
                <div className="flex justify-between text-[10px] tracking-[0.16em] uppercase">
                    <span className="text-ink-500">mem</span>
                    <span className="text-ink-300 font-mono">{isRunning ? `${mem}%` : "—"}</span>
                </div>
                <Bar value={isRunning ? mem : 0} />
            </div>

            <div className="flex items-center gap-1.5 justify-end flex-wrap">
                <ActionBtn icon="open" label="View details" onClick={() => onViewDetails(container)} />
                <ActionBtn
                    icon="edit"
                    label="Modify"
                    disabled={!canMutate || container.essential}
                    disabledReason={container.essential ? ESSENTIAL_EDIT_REASON : !canMutate ? PERMISSION_EDIT_REASON : undefined}
                    onClick={() => onEdit(container)}
                />
                <ActionBtn
                    icon="play"
                    label="Start"
                    variant="primary"
                    disabled={!canMutate || isRunning}
                    disabledReason={isRunning ? "The container is already running." : !canMutate ? PERMISSION_EDIT_REASON : undefined}
                    onClick={() => onAction(container, "start")}
                />
                <ActionBtn
                    icon="stop"
                    label="Stop"
                    disabled={!canMutate || !canStop}
                    disabledReason={!canStop ? "The container is not running." : !canMutate ? PERMISSION_EDIT_REASON : undefined}
                    onClick={() => onAction(container, "stop")}
                />
                <ActionBtn
                    icon="reset"
                    label="Restart"
                    disabled={!canMutate || !isRunning}
                    disabledReason={!isRunning ? "The container is not running." : !canMutate ? PERMISSION_EDIT_REASON : undefined}
                    onClick={() => onAction(container, "restart")}
                />
                <ActionBtn icon="download" label="Download logs" onClick={() => onDownloadLogs(container)} />
                <ActionBtn
                    icon="pulse"
                    label="Diagnose failure"
                    disabled={!canDiagnose}
                    disabledReason={!canDiagnose ? "AI-assisted analysis is only available for containers that failed recently." : undefined}
                    onClick={() => onDiagnose(container)}
                />
                {!container.essential && !container.stackName && (
                    <ActionBtn
                        icon="link"
                        label="Assign to stack"
                        disabled={!canMutate}
                        disabledReason={!canMutate ? PERMISSION_EDIT_REASON : undefined}
                        onClick={() => onAssign(container)}
                    />
                )}
                <ActionBtn
                    icon="trash"
                    label="Delete"
                    variant="danger"
                    disabled={!canRemove || container.essential}
                    disabledReason={container.essential ? "Essential containers can't be deleted from the UI." : !canRemove ? PERMISSION_DELETE_REASON : undefined}
                    onClick={() => onAction(container, "delete")}
                />
            </div>
        </div>
    );
}

function StackGroup({ stack, items, stats, canMutate, canRemove, onAction, onEdit, onAssign, onAddContainer, onEditStack, onViewDetails, onDownloadLogs, onDiagnose }: {
    stack: StackDTO;
    items: ContainerDTO[];
    stats: Record<string, ContainerStatsDTO>;
    canMutate: boolean;
    canRemove: boolean;
    onAction: (container: ContainerDTO, action: LifecycleAction) => void;
    onEdit: (container: ContainerDTO) => void;
    onAssign: (container: ContainerDTO) => void;
    onAddContainer: (stack: StackDTO) => void;
    onEditStack: (stack: StackDTO) => void;
    onViewDetails: (container: ContainerDTO) => void;
    onDownloadLogs: (container: ContainerDTO) => void;
    onDiagnose: (container: ContainerDTO) => void;
}) {
    const [collapsed, setCollapsed] = useState(false);
    const running = items.filter(c => c.state === "running").length;

    return (
        <div className="border-b border-ink-700/70 last:border-b-0">
            <div className="grid grid-cols-[24px_minmax(200px,1.6fr)_110px_140px_140px_auto] items-center gap-4 px-5 py-3 bg-ink-900/40 border-b border-ink-700/70">
                <button
                    type="button"
                    onClick={() => setCollapsed(c => !c)}
                    className="text-ink-500 hover:text-ink-100"
                >
                    <Icon name="chev" className={`w-3 h-3 transition-transform ${collapsed ? "" : "rotate-90"}`} />
                </button>
                <div className="flex items-baseline gap-3 min-w-0">
                    <span className="font-sans text-[14px] text-ink-50 truncate">{stack.stackName}</span>
                    <span className="font-mono text-[12px] text-ink-500 leading-none">{items.length}</span>
                </div>
                <span className="font-mono text-[11px] text-ink-500">{running}/{items.length} running</span>
                <span />
                <span />
                <div className="flex items-center gap-1.5 justify-end">
                    <ActionBtn
                        icon="plus"
                        label="Add container"
                        disabled={!canMutate}
                        disabledReason={!canMutate ? PERMISSION_EDIT_REASON : undefined}
                        onClick={() => onAddContainer(stack)}
                    />
                    <ActionBtn
                        icon="edit"
                        label="Edit stack"
                        disabled={!canMutate}
                        disabledReason={!canMutate ? PERMISSION_EDIT_REASON : undefined}
                        onClick={() => onEditStack(stack)}
                    />
                </div>
            </div>
            {!collapsed && items.map(c => (
                <ContainerRow
                    key={c.id}
                    container={c}
                    stats={stats[c.id]}
                    canMutate={canMutate}
                    canRemove={canRemove}
                    onAction={onAction}
                    onEdit={onEdit}
                    onAssign={onAssign}
                    onViewDetails={onViewDetails}
                    onDownloadLogs={onDownloadLogs}
                    onDiagnose={onDiagnose}
                />
            ))}
        </div>
    );
}

export default function ContainersView() {
    const { user } = useAuth();
    const canMutate = canEdit(user?.permissionRole);
    const canRemove = canDelete(user?.permissionRole);

    const [containers, setContainers] = useState<ContainerDTO[]>([]);
    const [stacksList, setStacksList] = useState<StackDTO[]>([]);
    const [stats, setStats] = useState<Record<string, ContainerStatsDTO>>({});
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [search, setSearch] = useState("");
    const [pending, setPending] = useState<PendingAction | null>(null);
    const [busy, setBusy] = useState(false);
    const [editing, setEditing] = useState<ContainerDTO | null>(null);
    const [showCreateStack, setShowCreateStack] = useState(false);
    const [assigning, setAssigning] = useState<ContainerDTO | null>(null);
    const [editingStack, setEditingStack] = useState<StackDTO | null>(null);
    const [addingToStack, setAddingToStack] = useState<StackDTO | null>(null);
    const [viewingDetailsId, setViewingDetailsId] = useState<string | null>(null);
    const [showImport, setShowImport] = useState(false);
    const [diagnosing, setDiagnosing] = useState<ContainerDTO | null>(null);
    const viewingDetails = viewingDetailsId ? containers.find(c => c.id === viewingDetailsId) ?? null : null;

    const refresh = useCallback(async () => {
        setError(null);
        try {
            const [list, statsList, stacksResult] = await Promise.all([listContainers(), getContainersStats(), listStacks()]);
            setContainers(list);
            setStacksList(stacksResult);
            const next: Record<string, ContainerStatsDTO> = {};
            for (const s of statsList) next[s.containerId] = s;
            setStats(next);
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not load containers");
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
        if (!q) return containers;
        return containers.filter(c => c.name.toLowerCase().includes(q) || c.image.toLowerCase().includes(q));
    }, [containers, search]);

    const essentials = useMemo(() => filtered.filter(c => c.essential), [filtered]);
    const stackGroups = useMemo(() => {
        const containersByStack = new Map<string, ContainerDTO[]>();
        for (const c of filtered) {
            if (c.essential || !c.stackName) continue;
            const bucket = containersByStack.get(c.stackName);
            if (bucket) bucket.push(c); else containersByStack.set(c.stackName, [c]);
        }
        return stacksList.map(s => ({
            stack: s,
            items: containersByStack.get(s.stackName) ?? [],
        }));
    }, [filtered, stacksList]);
    const deployedAtHost = useMemo(
        () => filtered.filter(c => !c.essential && !c.stackName),
        [filtered]
    );

    const runAction = async (container: ContainerDTO, action: LifecycleAction) => {
        setBusy(true);
        setError(null);
        try {
            if (action === "start") await startContainer(container.id);
            else if (action === "stop") await stopContainer(container.id);
            else if (action === "restart") await restartContainer(container.id);
            else await deleteContainer(container.id, true, false);
            setPending(null);
            await refresh();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : `Could not ${action} the container`);
        } finally {
            setBusy(false);
        }
    };

    const handleDownloadLogs = async (container: ContainerDTO) => {
        setError(null);
        try {
            await downloadContainerLogs(container.id, container.name);
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not download the logs");
        }
    };

    const copy = pending ? ACTION_COPY[pending.action] : null;

    return (
        <div className="h-full flex flex-col gap-6 min-h-0">
            <Toolbar
                search={search}
                onSearchChange={setSearch}
                onRefresh={refresh}
                refreshing={loading}
                primaryLabel="Import container"
                onPrimary={() => setShowImport(true)}
            />

            {error && <ErrorBanner message={error} />}

            <Section
                title="Essential"
                subtitle="Containers required for ChatOps to function. Cannot be deleted from the UI."
                badge="locked"
                count={essentials.length}
                className="flex-1 min-h-0"
            >
                {loading && containers.length === 0 ? (
                    <div className="px-5 py-10 text-center font-mono text-[12px] text-ink-500">loading…</div>
                ) : essentials.length === 0 ? (
                    <EmptyState title="No essential containers" message="No system containers were found on the daemon." />
                ) : (
                    essentials.map(c => (
                        <ContainerRow
                            key={c.id}
                            container={c}
                            stats={stats[c.id]}
                            canMutate={canMutate}
                            canRemove={canRemove}
                            onAction={(container, action) => setPending({ container, action })}
                            onEdit={setEditing}
                            onAssign={setAssigning}
                            onViewDetails={c => setViewingDetailsId(c.id)}
                            onDownloadLogs={handleDownloadLogs}
                            onDiagnose={setDiagnosing}
                        />
                    ))
                )}
            </Section>

            <Section
                title="Stacks"
                subtitle="Apps grouped into persisted stacks."
                badge="persisted"
                count={stackGroups.length}
                className="flex-1 min-h-0"
                headerAction={
                    <button
                        type="button"
                        onClick={() => setShowCreateStack(true)}
                        disabled={!canMutate}
                        className="flex items-center gap-2.5 border border-ink-700 text-ink-100 font-mono text-[11px] tracking-[0.18em] uppercase px-4 py-2.5 hover:bg-ink-800 hover:border-ink-600 transition disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                        <Icon name="plus" className="w-3.5 h-3.5" />
                        New stack
                    </button>
                }
            >
                {loading && stacksList.length === 0 ? (
                    <div className="px-5 py-10 text-center font-mono text-[12px] text-ink-500">loading…</div>
                ) : stackGroups.length === 0 ? (
                    <EmptyState title="No stacks yet" message="Click New stack to create your first one." />
                ) : (
                    stackGroups.map(g => (
                        <StackGroup
                            key={g.stack.id}
                            stack={g.stack}
                            items={g.items}
                            stats={stats}
                            canMutate={canMutate}
                            canRemove={canRemove}
                            onAction={(container, action) => setPending({ container, action })}
                            onEdit={setEditing}
                            onAssign={setAssigning}
                            onAddContainer={setAddingToStack}
                            onEditStack={setEditingStack}
                            onViewDetails={c => setViewingDetailsId(c.id)}
                            onDownloadLogs={handleDownloadLogs}
                            onDiagnose={setDiagnosing}
                        />
                    ))
                )}
            </Section>

            <Section
                title="Deployed at host"
                subtitle="Containers running on this node that aren't part of any stack."
                badge="host"
                count={deployedAtHost.length}
                className="flex-1 min-h-0"
            >
                {loading && containers.length === 0 ? (
                    <div className="px-5 py-10 text-center font-mono text-[12px] text-ink-500">loading…</div>
                ) : deployedAtHost.length === 0 ? (
                    <EmptyState title="Nothing deployed outside a stack" message="Use Import container to add one." />
                ) : (
                    deployedAtHost.map(c => (
                        <ContainerRow
                            key={c.id}
                            container={c}
                            stats={stats[c.id]}
                            canMutate={canMutate}
                            canRemove={canRemove}
                            onAction={(container, action) => setPending({ container, action })}
                            onEdit={setEditing}
                            onAssign={setAssigning}
                            onViewDetails={c => setViewingDetailsId(c.id)}
                            onDownloadLogs={handleDownloadLogs}
                            onDiagnose={setDiagnosing}
                        />
                    ))
                )}
            </Section>

            <ConfirmDialog
                open={!!pending}
                title={copy?.title ?? ""}
                body={pending ? `${copy!.body} (${pending.container.name})` : ""}
                confirmLabel={copy?.confirmLabel ?? ""}
                danger={copy?.danger}
                busy={busy}
                onCancel={() => setPending(null)}
                onConfirm={() => pending && runAction(pending.container, pending.action)}
            />

            {editing && (
                <EditContainerModal
                    container={editing}
                    onClose={() => setEditing(null)}
                    onUpdated={() => { setEditing(null); refresh(); }}
                />
            )}

            {showCreateStack && (
                <CreateStackModal
                    onClose={() => setShowCreateStack(false)}
                    onCreated={() => { setShowCreateStack(false); refresh(); }}
                />
            )}

            {assigning && (
                <AssignStackModal
                    container={assigning}
                    onClose={() => setAssigning(null)}
                    onAssigned={() => { setAssigning(null); refresh(); }}
                />
            )}

            {editingStack && (
                <EditStackModal
                    stack={editingStack}
                    onClose={() => setEditingStack(null)}
                    onUpdated={() => { setEditingStack(null); refresh(); }}
                    onDeleted={() => { setEditingStack(null); refresh(); }}
                    onAppRemoved={refresh}
                />
            )}

            {addingToStack && (
                <AddContainerToStackModal
                    stackName={addingToStack.stackName}
                    availableContainers={deployedAtHost}
                    onClose={() => setAddingToStack(null)}
                    onAssigned={() => { setAddingToStack(null); refresh(); }}
                />
            )}

            {viewingDetails && (
                <ContainerDetailsDrawer
                    container={viewingDetails}
                    canMutate={canMutate}
                    onClose={() => setViewingDetailsId(null)}
                />
            )}

            {showImport && (
                <ImportContainerModal onClose={() => setShowImport(false)} onCreated={refresh} />
            )}

            {diagnosing && (
                <AiDiagnosisModal container={diagnosing} onClose={() => setDiagnosing(null)} />
            )}
        </div>
    );
}
