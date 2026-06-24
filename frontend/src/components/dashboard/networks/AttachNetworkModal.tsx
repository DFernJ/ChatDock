import { useState } from "react";
import type { ChangeEvent } from "react";
import { ApiError } from "../../../lib/api.ts";
import { connectContainerToNetwork } from "../../../lib/api/docker.ts";
import type { ContainerDTO, NetworkDTO } from "../../../types/docker.ts";
import { Field, Modal } from "../../Ui.tsx";

export default function AttachNetworkModal({ network, availableContainers, onClose, onAttached }: {
    network: NetworkDTO;
    availableContainers: ContainerDTO[];
    onClose: () => void;
    onAttached: (containerName: string) => void;
}) {
    const [containerId, setContainerId] = useState(availableContainers[0]?.id ?? "");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const submit = async () => {
        if (!containerId) return;
        setBusy(true);
        setError(null);
        try {
            await connectContainerToNetwork(network.id, containerId);
            const container = availableContainers.find(c => c.id === containerId);
            onAttached(container?.name ?? "");
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not attach the container.");
        } finally {
            setBusy(false);
        }
    };

    return (
        <Modal
            title="Attach container"
            subtitle={`Attach an existing host container to the "${network.name}" network.`}
            path={`networks/${network.name}/attach`}
            onClose={onClose}
            footer={
                <>
                    <button type="button" onClick={onClose} disabled={busy} className="px-4 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-ink-900 disabled:opacity-50">
                        Cancel
                    </button>
                    <button
                        type="button"
                        onClick={submit}
                        disabled={busy || !containerId}
                        className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 disabled:opacity-60"
                    >
                        {busy ? "Attaching…" : "Attach"}
                    </button>
                </>
            }
        >
            {availableContainers.length === 0 ? (
                <p className="text-[12px] text-ink-500 font-mono">
                    Every container on this host is already attached to this network.
                </p>
            ) : (
                <div className="flex flex-col gap-4">
                    <Field label="Container">
                        <select
                            value={containerId}
                            onChange={(e: ChangeEvent<HTMLSelectElement>) => setContainerId(e.target.value)}
                            className="w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 outline-none"
                        >
                            {availableContainers.map(c => (
                                <option key={c.id} value={c.id} className="bg-ink-800">{c.name}</option>
                            ))}
                        </select>
                    </Field>
                    {error && <p className="text-[12px] text-rose-400 font-mono">{error}</p>}
                </div>
            )}
        </Modal>
    );
}
