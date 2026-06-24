import { useState } from "react";
import type { ChangeEvent } from "react";
import { ApiError } from "../../../lib/api.ts";
import { createVolume } from "../../../lib/api/docker.ts";
import { Field, Modal } from "../../Ui.tsx";

const VOLUME_DRIVERS = ["local", "nfs", "cifs"];

export default function CreateVolumeModal({ onClose, onCreated }: {
    onClose: () => void;
    onCreated: () => void;
}) {
    const [name, setName] = useState("");
    const [driver, setDriver] = useState("local");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const submit = async () => {
        if (!name.trim()) return;
        setBusy(true);
        setError(null);
        try {
            await createVolume(name.trim(), driver.trim() || "local");
            onCreated();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not create the volume.");
        } finally {
            setBusy(false);
        }
    };

    return (
        <Modal
            title="New volume"
            subtitle="Create a named Docker volume that containers can mount."
            path="volumes/new"
            onClose={onClose}
            footer={
                <>
                    <button type="button" onClick={onClose} disabled={busy} className="px-4 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-ink-900 disabled:opacity-50">
                        Cancel
                    </button>
                    <button
                        type="button"
                        onClick={submit}
                        disabled={busy || !name.trim()}
                        className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 disabled:opacity-60"
                    >
                        {busy ? "Creating…" : "Create volume"}
                    </button>
                </>
            }
        >
            <div className="flex flex-col gap-4">
                <Field label="Volume name">
                    <input
                        value={name}
                        onChange={(e: ChangeEvent<HTMLInputElement>) => setName(e.target.value)}
                        placeholder="pgdata"
                        spellCheck={false}
                        className="flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600 focus:border-accent"
                    />
                </Field>
                <Field label="Driver">
                    <select
                        value={driver}
                        onChange={(e: ChangeEvent<HTMLSelectElement>) => setDriver(e.target.value)}
                        className="w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 outline-none"
                    >
                        {VOLUME_DRIVERS.map(d => (
                            <option key={d} value={d} className="bg-ink-800">{d}</option>
                        ))}
                    </select>
                </Field>
                {error && <p className="text-[12px] text-rose-400 font-mono">{error}</p>}
            </div>
        </Modal>
    );
}
