import { useState } from "react";
import type { ChangeEvent } from "react";
import { ApiError } from "../../../lib/api.ts";
import { createStack } from "../../../lib/api/docker.ts";
import { Field, Modal } from "../../Ui.tsx";

export default function CreateStackModal({ onClose, onCreated }: {
    onClose: () => void;
    onCreated: () => void;
}) {
    const [stackName, setStackName] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const submit = async () => {
        if (!stackName.trim()) return;
        setBusy(true);
        setError(null);
        try {
            await createStack(stackName.trim());
            onCreated();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not create the stack.");
        } finally {
            setBusy(false);
        }
    };

    return (
        <Modal
            title="New stack"
            subtitle="Group related containers under a named, persisted stack."
            path="containers/stacks/new"
            onClose={onClose}
            footer={
                <>
                    <button type="button" onClick={onClose} disabled={busy} className="px-4 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-ink-900 disabled:opacity-50">
                        Cancel
                    </button>
                    <button
                        type="button"
                        onClick={submit}
                        disabled={busy || !stackName.trim()}
                        className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 disabled:opacity-60"
                    >
                        {busy ? "Creating…" : "Create stack"}
                    </button>
                </>
            }
        >
            <Field label="Stack name">
                <input
                    value={stackName}
                    onChange={(e: ChangeEvent<HTMLInputElement>) => setStackName(e.target.value)}
                    placeholder="my-app"
                    spellCheck={false}
                    className="flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600 focus:border-accent"
                />
            </Field>
            {error && <p className="text-[12px] text-rose-400 font-mono mt-3">{error}</p>}
        </Modal>
    );
}
