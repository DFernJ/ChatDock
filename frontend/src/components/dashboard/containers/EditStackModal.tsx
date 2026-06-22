import { useCallback, useEffect, useState } from "react";
import type { ChangeEvent } from "react";
import { ApiError } from "../../../lib/api.ts";
import { deleteStack, listStackApps, removeAppFromStack, updateStack } from "../../../lib/api/docker.ts";
import type { AppSummaryDTO, StackDTO } from "../../../types/docker.ts";
import { ConfirmDialog, Field, Icon, Modal } from "../../Ui.tsx";

export default function EditStackModal({ stack, onClose, onUpdated, onDeleted, onAppRemoved }: {
    stack: StackDTO;
    onClose: () => void;
    onUpdated: () => void;
    onDeleted: () => void;
    onAppRemoved?: () => void;
}) {
    const [stackName, setStackName] = useState(stack.stackName);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const [apps, setApps] = useState<AppSummaryDTO[]>([]);
    const [appsLoading, setAppsLoading] = useState(true);
    const [appsError, setAppsError] = useState<string | null>(null);
    const [appBusy, setAppBusy] = useState(false);
    const [pendingRemove, setPendingRemove] = useState<AppSummaryDTO | null>(null);

    const [pendingDeleteStack, setPendingDeleteStack] = useState(false);
    const [deleteBusy, setDeleteBusy] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const refreshApps = useCallback(() => {
        setAppsLoading(true);
        setAppsError(null);
        listStackApps(stack.id)
            .then(setApps)
            .catch(e => setAppsError(e instanceof ApiError ? e.message : "Could not load apps."))
            .finally(() => setAppsLoading(false));
    }, [stack.id]);

    useEffect(() => {
        refreshApps();
    }, [refreshApps]);

    const submit = async () => {
        if (!stackName.trim()) return;
        setBusy(true);
        setError(null);
        try {
            await updateStack(stack.id, stackName.trim());
            onUpdated();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not update the stack.");
        } finally {
            setBusy(false);
        }
    };

    const confirmRemoveApp = async () => {
        if (!pendingRemove) return;
        setAppBusy(true);
        setAppsError(null);
        try {
            await removeAppFromStack(stack.id, pendingRemove.id);
            setPendingRemove(null);
            refreshApps();
            onAppRemoved?.();
        } catch (e) {
            setAppsError(e instanceof ApiError ? e.message : "Could not remove the app.");
        } finally {
            setAppBusy(false);
        }
    };

    const confirmDeleteStack = async () => {
        setDeleteBusy(true);
        setDeleteError(null);
        try {
            await deleteStack(stack.id);
            setPendingDeleteStack(false);
            onDeleted();
        } catch (e) {
            setDeleteError(e instanceof ApiError ? e.message : "Could not delete the stack.");
        } finally {
            setDeleteBusy(false);
        }
    };

    return (
        <>
        <Modal
            title="Edit stack"
            subtitle={`Rename the "${stack.stackName}" stack, remove its apps, or delete it.`}
            path={`containers/stacks/${stack.stackName}/edit`}
            onClose={onClose}
            footer={
                <div className="flex items-center justify-between w-full">
                    <div className="flex items-center gap-2">
                        <button
                            type="button"
                            onClick={() => setPendingDeleteStack(true)}
                            disabled={busy || apps.length > 0}
                            className="px-4 py-2 border border-ink-700 text-rose-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-rose-500/10 hover:border-rose-400/50 transition disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                            Delete stack
                        </button>
                        {apps.length > 0 && (
                            <span className="text-[11px] text-ink-600 font-mono">remove all apps first</span>
                        )}
                    </div>
                    <div className="flex items-center gap-2">
                        <button type="button" onClick={onClose} disabled={busy} className="px-4 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-ink-900 disabled:opacity-50">
                            Cancel
                        </button>
                        <button
                            type="button"
                            onClick={submit}
                            disabled={busy || !stackName.trim()}
                            className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 disabled:opacity-60"
                        >
                            {busy ? "Saving…" : "Save changes"}
                        </button>
                    </div>
                </div>
            }
        >
            <div className="flex flex-col gap-4">
                <Field label="Stack name">
                    <input
                        value={stackName}
                        onChange={(e: ChangeEvent<HTMLInputElement>) => setStackName(e.target.value)}
                        spellCheck={false}
                        className="flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600 focus:border-accent"
                    />
                </Field>
                {error && <p className="text-[12px] text-rose-400 font-mono">{error}</p>}
                {deleteError && <p className="text-[12px] text-rose-400 font-mono">{deleteError}</p>}

                <Field label="Apps" hint="removing an app unassigns its container — the container itself keeps running">
                    {appsLoading ? (
                        <p className="text-[12px] text-ink-500 font-mono">Loading…</p>
                    ) : apps.length === 0 ? (
                        <p className="text-[12px] text-ink-500 font-mono">No apps assigned to this stack.</p>
                    ) : (
                        <div className="flex flex-col gap-2">
                            {apps.map(a => (
                                <div key={a.id} className="border border-ink-700 bg-ink-900/30 p-2 flex items-center gap-2">
                                    <div className="flex-1 min-w-0">
                                        <div className="font-mono text-[13px] text-ink-100 truncate">{a.name}</div>
                                        <div className="font-mono text-[11px] text-ink-600 truncate">{a.containerName}</div>
                                    </div>
                                    <button
                                        type="button"
                                        onClick={() => setPendingRemove(a)}
                                        className="w-8 h-8 shrink-0 grid place-items-center border border-ink-700 text-rose-300 hover:bg-rose-500/10 hover:border-rose-400/50 transition"
                                    >
                                        <Icon name="trash" className="w-3.5 h-3.5" />
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}
                    {appsError && <p className="text-[12px] text-rose-400 font-mono mt-2">{appsError}</p>}
                </Field>
            </div>
        </Modal>

        <ConfirmDialog
            open={!!pendingRemove}
            title="Remove app from stack"
            body={pendingRemove ? `"${pendingRemove.name}" (${pendingRemove.containerName}) will be unassigned from this stack. The container itself won't be stopped or deleted.` : ""}
            confirmLabel="Remove"
            danger
            busy={appBusy}
            onCancel={() => setPendingRemove(null)}
            onConfirm={confirmRemoveApp}
        />

        <ConfirmDialog
            open={pendingDeleteStack}
            title="Delete stack"
            body={`The "${stack.stackName}" stack will be permanently deleted. This can't be undone.`}
            confirmLabel="Delete"
            danger
            busy={deleteBusy}
            onCancel={() => setPendingDeleteStack(false)}
            onConfirm={confirmDeleteStack}
        />
        </>
    );
}
