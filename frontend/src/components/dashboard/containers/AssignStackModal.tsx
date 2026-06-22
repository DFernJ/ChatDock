import { useEffect, useState } from "react";
import type { ChangeEvent } from "react";
import { ApiError } from "../../../lib/api.ts";
import { assignContainerToStack, listStacks } from "../../../lib/api/docker.ts";
import type { ContainerDTO, StackDTO } from "../../../types/docker.ts";
import { Field, Modal } from "../../Ui.tsx";

const NEW_STACK_OPTION = "__new__";

export default function AssignStackModal({ container, onClose, onAssigned }: {
    container: ContainerDTO;
    onClose: () => void;
    onAssigned: () => void;
}) {
    const [appName, setAppName] = useState(container.name);
    const [stacks, setStacks] = useState<StackDTO[]>([]);
    const [loading, setLoading] = useState(true);
    const [stackChoice, setStackChoice] = useState(NEW_STACK_OPTION);
    const [newStackName, setNewStackName] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        listStacks()
            .then(list => {
                setStacks(list);
                if (list.length > 0) setStackChoice(list[0].stackName);
            })
            .catch(() => {})
            .finally(() => setLoading(false));
    }, []);

    const resolvedStackName = stackChoice === NEW_STACK_OPTION ? newStackName.trim() : stackChoice;
    const canSubmit = appName.trim().length > 0 && resolvedStackName.length > 0;

    const submit = async () => {
        if (!canSubmit) return;
        setBusy(true);
        setError(null);
        try {
            await assignContainerToStack(container.id, appName.trim(), resolvedStackName);
            onAssigned();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not assign the container.");
        } finally {
            setBusy(false);
        }
    };

    return (
        <Modal
            title="Assign to stack"
            subtitle={`Turn ${container.name} into a managed app under a stack.`}
            path={`containers/${container.name}/assign`}
            onClose={onClose}
            footer={
                <>
                    <button type="button" onClick={onClose} disabled={busy} className="px-4 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-ink-900 disabled:opacity-50">
                        Cancel
                    </button>
                    <button
                        type="button"
                        onClick={submit}
                        disabled={busy || loading || !canSubmit}
                        className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 disabled:opacity-60"
                    >
                        {busy ? "Assigning…" : "Assign"}
                    </button>
                </>
            }
        >
            <div className="flex flex-col gap-4">
                <Field label="App name">
                    <input
                        value={appName}
                        onChange={(e: ChangeEvent<HTMLInputElement>) => setAppName(e.target.value)}
                        spellCheck={false}
                        className="flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600 focus:border-accent"
                    />
                </Field>
                <Field label="Stack">
                    <select
                        value={stackChoice}
                        onChange={(e: ChangeEvent<HTMLSelectElement>) => setStackChoice(e.target.value)}
                        className="w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 outline-none"
                    >
                        <option value={NEW_STACK_OPTION} className="bg-ink-800">+ New stack…</option>
                        {stacks.map(s => (
                            <option key={s.id} value={s.stackName} className="bg-ink-800">{s.stackName}</option>
                        ))}
                    </select>
                </Field>
                {stackChoice === NEW_STACK_OPTION && (
                    <Field label="New stack name">
                        <input
                            value={newStackName}
                            onChange={(e: ChangeEvent<HTMLInputElement>) => setNewStackName(e.target.value)}
                            placeholder="my-app"
                            spellCheck={false}
                            className="flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600 focus:border-accent"
                        />
                    </Field>
                )}
                {error && <p className="text-[12px] text-rose-400 font-mono">{error}</p>}
            </div>
        </Modal>
    );
}
