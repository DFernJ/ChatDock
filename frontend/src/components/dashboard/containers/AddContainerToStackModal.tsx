import { useState } from "react";
import type { ChangeEvent } from "react";
import { ApiError } from "../../../lib/api.ts";
import { assignContainerToStack } from "../../../lib/api/docker.ts";
import type { ContainerDTO } from "../../../types/docker.ts";
import { Field, Modal } from "../../Ui.tsx";

export default function AddContainerToStackModal({ stackName, availableContainers, onClose, onAssigned }: {
    stackName: string;
    availableContainers: ContainerDTO[];
    onClose: () => void;
    onAssigned: () => void;
}) {
    const [containerId, setContainerId] = useState(availableContainers[0]?.id ?? "");
    const [appName, setAppName] = useState(availableContainers[0]?.name ?? "");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const canSubmit = containerId.length > 0 && appName.trim().length > 0;

    const submit = async () => {
        if (!canSubmit) return;
        setBusy(true);
        setError(null);
        try {
            await assignContainerToStack(containerId, appName.trim(), stackName);
            onAssigned();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not add the container.");
        } finally {
            setBusy(false);
        }
    };

    return (
        <Modal
            title="Add container"
            subtitle={`Add an existing host container to the "${stackName}" stack.`}
            path={`containers/stacks/${stackName}/add-container`}
            onClose={onClose}
            footer={
                <>
                    <button type="button" onClick={onClose} disabled={busy} className="px-4 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-ink-900 disabled:opacity-50">
                        Cancel
                    </button>
                    <button
                        type="button"
                        onClick={submit}
                        disabled={busy || !canSubmit}
                        className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 disabled:opacity-60"
                    >
                        {busy ? "Adding…" : "Add container"}
                    </button>
                </>
            }
        >
            {availableContainers.length === 0 ? (
                <p className="text-[12px] text-ink-500 font-mono">
                    Every container on this host is already assigned to a stack.
                </p>
            ) : (
                <div className="flex flex-col gap-4">
                    <Field label="Container">
                        <select
                            value={containerId}
                            onChange={(e: ChangeEvent<HTMLSelectElement>) => {
                                const id = e.target.value;
                                setContainerId(id);
                                const c = availableContainers.find(c => c.id === id);
                                if (c) setAppName(c.name);
                            }}
                            className="w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 outline-none"
                        >
                            {availableContainers.map(c => (
                                <option key={c.id} value={c.id} className="bg-ink-800">{c.name}</option>
                            ))}
                        </select>
                    </Field>
                    <Field label="App name">
                        <input
                            value={appName}
                            onChange={(e: ChangeEvent<HTMLInputElement>) => setAppName(e.target.value)}
                            spellCheck={false}
                            className="flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600 focus:border-accent"
                        />
                    </Field>
                    {error && <p className="text-[12px] text-rose-400 font-mono">{error}</p>}
                </div>
            )}
        </Modal>
    );
}
