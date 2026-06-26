import { useEffect, useRef, useState } from "react";
import type { ChangeEvent } from "react";
import { ApiError } from "../../../lib/api.ts";
import { pullImage, searchDockerHubImages } from "../../../lib/api/docker.ts";
import type { DockerHubImageDTO } from "../../../types/docker.ts";
import { Field, Modal } from "../../Ui.tsx";

export default function PullImageModal({ onClose, onPulled }: {
    onClose: () => void;
    onPulled: () => void;
}) {
    const [repository, setRepository] = useState("");
    const [tag, setTag] = useState("latest");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const [results, setResults] = useState<DockerHubImageDTO[]>([]);
    const [searching, setSearching] = useState(false);
    const [showResults, setShowResults] = useState(false);
    const searchBoxRef = useRef<HTMLDivElement>(null);
    const requestIdRef = useRef(0);

    useEffect(() => {
        const query = repository.trim();
        if (query.length < 2) {
            setResults([]);
            setSearching(false);
            return;
        }
        const requestId = ++requestIdRef.current;
        setSearching(true);
        const timeoutId = window.setTimeout(async () => {
            try {
                const images = await searchDockerHubImages(query);
                if (requestIdRef.current === requestId) setResults(images);
            } catch {
                if (requestIdRef.current === requestId) setResults([]);
            } finally {
                if (requestIdRef.current === requestId) setSearching(false);
            }
        }, 400);
        return () => window.clearTimeout(timeoutId);
    }, [repository]);

    useEffect(() => {
        if (!showResults) return;
        const onClickOutside = (e: MouseEvent) => {
            if (searchBoxRef.current && !searchBoxRef.current.contains(e.target as Node)) {
                setShowResults(false);
            }
        };
        document.addEventListener("mousedown", onClickOutside);
        return () => document.removeEventListener("mousedown", onClickOutside);
    }, [showResults]);

    const selectImage = (image: DockerHubImageDTO) => {
        setRepository(image.name);
        setShowResults(false);
    };

    const submit = async () => {
        if (!repository.trim()) return;
        setBusy(true);
        setError(null);
        try {
            await pullImage(repository.trim(), tag.trim() || "latest");
            onPulled();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not pull the image.");
        } finally {
            setBusy(false);
        }
    };

    return (
        <Modal
            title="Pull image"
            subtitle="Search Docker Hub or type a repository to download onto this host."
            path="images/pull"
            onClose={onClose}
            footer={
                <>
                    <button type="button" onClick={onClose} disabled={busy} className="px-4 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-ink-900 disabled:opacity-50">
                        Cancel
                    </button>
                    <button
                        type="button"
                        onClick={submit}
                        disabled={busy || !repository.trim()}
                        className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 disabled:opacity-60"
                    >
                        {busy ? "Pulling…" : "Pull image"}
                    </button>
                </>
            }
        >
            <div className="flex flex-col gap-4">
                <div ref={searchBoxRef} className="relative">
                    <Field label="Repository">
                        <input
                            value={repository}
                            onChange={(e: ChangeEvent<HTMLInputElement>) => { setRepository(e.target.value); setShowResults(true); }}
                            onFocus={() => setShowResults(true)}
                            placeholder="nginx"
                            spellCheck={false}
                            className="flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600 focus:border-accent"
                        />
                    </Field>

                    {showResults && repository.trim().length >= 2 && (
                        <div className="absolute left-0 right-0 top-full mt-1 max-h-64 overflow-y-auto scrollbar border border-ink-700 bg-ink-800 shadow-card z-10">
                            {searching ? (
                                <div className="px-3.5 py-3 font-mono text-[12px] text-ink-500">searching…</div>
                            ) : results.length === 0 ? (
                                <div className="px-3.5 py-3 font-mono text-[12px] text-ink-500">No images found on Docker Hub.</div>
                            ) : (
                                results.map(r => (
                                    <button
                                        key={r.name}
                                        type="button"
                                        onClick={() => selectImage(r)}
                                        className="w-full text-left px-3.5 py-2.5 border-b border-ink-700 last:border-b-0 hover:bg-ink-700 transition"
                                    >
                                        <div className="flex items-center gap-2">
                                            <span className="font-mono text-[13px] text-ink-50 truncate">{r.name}</span>
                                            {r.official && (
                                                <span className="shrink-0 text-[9px] tracking-[0.16em] uppercase px-1.5 py-0.5 bg-accent-soft border border-accent-line text-accent">Official</span>
                                            )}
                                            <span className="shrink-0 font-mono text-[11px] text-ink-500 ml-auto">★ {r.starCount}</span>
                                        </div>
                                        {r.description && (
                                            <p className="font-mono text-[11px] text-ink-500 truncate mt-0.5">{r.description}</p>
                                        )}
                                    </button>
                                ))
                            )}
                        </div>
                    )}
                </div>

                <Field label="Tag">
                    <input
                        value={tag}
                        onChange={(e: ChangeEvent<HTMLInputElement>) => setTag(e.target.value)}
                        placeholder="latest"
                        spellCheck={false}
                        className="flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600 focus:border-accent"
                    />
                </Field>
                {error && <p className="text-[12px] text-rose-400 font-mono">{error}</p>}
            </div>
        </Modal>
    );
}
