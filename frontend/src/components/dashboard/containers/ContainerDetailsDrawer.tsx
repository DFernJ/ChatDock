import { useEffect, useState } from "react";
import { ApiError } from "../../../lib/api.ts";
import { getContainerLogsText, getContainerStats } from "../../../lib/api/docker.ts";
import type { ContainerDTO } from "../../../types/docker.ts";
import { formatBytes, Icon } from "../../Ui.tsx";
import TerminalPane from "./TerminalPane.tsx";
import ConsolePane from "./ConsolePane.tsx";

type Tab = "stats" | "terminal" | "console" | "logs";

interface StatSample {
    cpu: number;
    mem: number;
    diskRead: number;
    diskWrite: number;
}

const MAX_SAMPLES = 30;
const POLL_MS = 5000;

function Sparkline({ points, color }: { points: number[]; color: string }) {
    const w = 100, h = 32;
    if (points.length < 2) {
        return <svg viewBox={`0 0 ${w} ${h}`} className="w-full h-8" />;
    }
    const max = 100;
    const line = points
        .map((p, i) => `${i === 0 ? "M" : "L"} ${(i / (points.length - 1)) * w} ${h - (Math.min(p, max) / max) * h}`)
        .join(" ");
    const fill = `${line} L ${w} ${h} L 0 ${h} Z`;
    return (
        <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" className="w-full h-8">
            <path d={fill} fill={color} opacity="0.15" />
            <path d={line} fill="none" stroke={color} strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
        </svg>
    );
}

function DualSparkline({ a, b, colorA, colorB }: { a: number[]; b: number[]; colorA: string; colorB: string }) {
    const w = 100, h = 32;
    if (a.length < 2) {
        return <svg viewBox={`0 0 ${w} ${h}`} className="w-full h-8" />;
    }
    const max = Math.max(...a, ...b, 1);
    const toPath = (points: number[]) => points
        .map((p, i) => `${i === 0 ? "M" : "L"} ${(i / (points.length - 1)) * w} ${h - (p / max) * h}`)
        .join(" ");
    return (
        <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" className="w-full h-8">
            <path d={toPath(a)} fill="none" stroke={colorA} strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
            <path d={toPath(b)} fill="none" stroke={colorB} strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
        </svg>
    );
}

function Metric({ label, value }: { label: string; value: string }) {
    return (
        <div className="border border-ink-700 bg-ink-900/40 p-3">
            <div className="text-[10px] tracking-[0.22em] uppercase text-ink-500">{label}</div>
            <div className="font-mono text-[16px] mt-1 text-ink-50 truncate">{value}</div>
        </div>
    );
}

export default function ContainerDetailsDrawer({ container, canMutate, onClose }: {
    container: ContainerDTO;
    canMutate: boolean;
    onClose: () => void;
}) {
    const [tab, setTab] = useState<Tab>("stats");
    const isRunning = container.state === "running";

    const [samples, setSamples] = useState<StatSample[]>([]);
    const [statsError, setStatsError] = useState<string | null>(null);

    useEffect(() => {
        if (tab !== "stats" || !isRunning) return;
        let cancelled = false;
        const poll = () => {
            getContainerStats(container.id)
                .then(stats => {
                    if (cancelled) return;
                    setStatsError(null);
                    setSamples(curr => [...curr, {
                        cpu: stats.cpuPercent,
                        mem: stats.memPercent,
                        diskRead: stats.diskReadBytes,
                        diskWrite: stats.diskWriteBytes,
                    }].slice(-MAX_SAMPLES));
                })
                .catch(e => { if (!cancelled) setStatsError(e instanceof ApiError ? e.message : "Could not load stats."); });
        };
        poll();
        const id = window.setInterval(poll, POLL_MS);
        return () => { cancelled = true; window.clearInterval(id); };
    }, [tab, isRunning, container.id]);

    const [logs, setLogs] = useState("");
    const [logsLoading, setLogsLoading] = useState(false);
    const [logsError, setLogsError] = useState<string | null>(null);

    const refreshLogs = () => {
        setLogsLoading(true);
        setLogsError(null);
        getContainerLogsText(container.id)
            .then(setLogs)
            .catch(e => setLogsError(e instanceof ApiError ? e.message : "Could not load logs."))
            .finally(() => setLogsLoading(false));
    };

    useEffect(() => {
        if (tab === "logs") refreshLogs();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [tab, container.id]);

    const latest = samples[samples.length - 1];

    return (
        <div className="fixed inset-0 z-30 flex">
            <div className="flex-1 bg-black/40" onClick={onClose}></div>
            <aside className="relative w-full max-w-[960px] bg-ink-800 border-l border-ink-700 shadow-card flex flex-col">
                <div className="px-6 py-4 border-b border-ink-700 flex items-start justify-between shrink-0">
                    <div className="min-w-0">
                        <div className="font-mono text-[10px] tracking-[0.22em] uppercase text-ink-500 mb-1">container details</div>
                        <h2 className="font-sans text-[20px] tracking-tight text-ink-50 truncate">{container.name}</h2>
                        <div className="font-mono text-[11px] text-ink-500 mt-1 truncate">{container.image}</div>
                    </div>
                    <button type="button" onClick={onClose} className="w-8 h-8 shrink-0 grid place-items-center border border-ink-700 text-ink-300 hover:text-ink-50 hover:bg-ink-900">
                        <Icon name="x" />
                    </button>
                </div>

                <div className="grid grid-cols-4 border-b border-ink-700 shrink-0">
                    {(["stats", "terminal", "console", "logs"] as Tab[]).map((t, i) => (
                        <button
                            key={t}
                            type="button"
                            onClick={() => setTab(t)}
                            className={`py-3 font-mono text-[11px] tracking-[0.18em] uppercase capitalize ${i < 3 ? "border-r border-ink-700" : ""} ${
                                tab === t ? "text-ink-50 bg-ink-900 shadow-[inset_0_-2px_0_#bef264]" : "text-ink-500 hover:text-ink-200"
                            }`}
                        >
                            {t}
                        </button>
                    ))}
                </div>

                {tab === "stats" && (
                    <div className="flex-1 overflow-y-auto scrollbar p-6 space-y-6">
                        {!isRunning ? (
                            <p className="text-[12px] text-ink-500 font-mono">The container isn't running — no live stats available.</p>
                        ) : (
                            <>
                                <div className="grid grid-cols-3 gap-3">
                                    <Metric label="CPU" value={latest ? `${Math.round(latest.cpu)}%` : "—"} />
                                    <Metric label="Memory" value={latest ? `${Math.round(latest.mem)}%` : "—"} />
                                    <Metric label="Uptime" value={container.status || "—"} />
                                    <Metric label="Disk read" value={latest ? formatBytes(latest.diskRead) : "—"} />
                                    <Metric label="Disk write" value={latest ? formatBytes(latest.diskWrite) : "—"} />
                                </div>
                                <div className="border border-ink-700 bg-ink-900/40">
                                    <div className="px-4 py-2.5 border-b border-ink-700 flex justify-between items-center">
                                        <span className="text-[10px] tracking-[0.22em] uppercase text-ink-500">CPU · every {POLL_MS / 1000}s</span>
                                        <span className="font-mono text-[11px] text-ink-400">{latest ? `${Math.round(latest.cpu)}%` : "—"}</span>
                                    </div>
                                    <div className="p-4"><Sparkline points={samples.map(s => s.cpu)} color="#bef264" /></div>
                                </div>
                                <div className="border border-ink-700 bg-ink-900/40">
                                    <div className="px-4 py-2.5 border-b border-ink-700 flex justify-between items-center">
                                        <span className="text-[10px] tracking-[0.22em] uppercase text-ink-500">Memory · every {POLL_MS / 1000}s</span>
                                        <span className="font-mono text-[11px] text-ink-400">{latest ? `${Math.round(latest.mem)}%` : "—"}</span>
                                    </div>
                                    <div className="p-4"><Sparkline points={samples.map(s => s.mem)} color="#7dd3fc" /></div>
                                </div>
                                <div className="border border-ink-700 bg-ink-900/40">
                                    <div className="px-4 py-2.5 border-b border-ink-700 flex justify-between items-center">
                                        <span className="text-[10px] tracking-[0.22em] uppercase text-ink-500">Disk I/O · every {POLL_MS / 1000}s</span>
                                        <span className="flex items-center gap-3 font-mono text-[11px] text-ink-400">
                                            <span className="flex items-center gap-1.5"><span className="w-2 h-2 inline-block" style={{ backgroundColor: "#7dd3fc" }}></span>read</span>
                                            <span className="flex items-center gap-1.5"><span className="w-2 h-2 inline-block" style={{ backgroundColor: "#fcd34d" }}></span>write</span>
                                        </span>
                                    </div>
                                    <div className="p-4">
                                        <DualSparkline
                                            a={samples.map(s => s.diskRead)}
                                            b={samples.map(s => s.diskWrite)}
                                            colorA="#7dd3fc"
                                            colorB="#fcd34d"
                                        />
                                    </div>
                                </div>
                                {statsError && <p className="text-[12px] text-rose-400 font-mono">{statsError}</p>}
                            </>
                        )}
                    </div>
                )}

                {tab === "logs" && (
                    <div className="flex-1 overflow-y-auto scrollbar p-6">
                        <div className="flex items-center justify-between mb-2">
                            <span className="text-[10px] tracking-[0.22em] uppercase text-ink-500">stdout · stderr</span>
                            <button
                                type="button"
                                onClick={refreshLogs}
                                disabled={logsLoading}
                                className="px-3 py-1.5 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.1em] uppercase hover:bg-ink-900 disabled:opacity-50"
                            >
                                {logsLoading ? "Loading…" : "Refresh"}
                            </button>
                        </div>
                        {logsError && <p className="text-[12px] text-rose-400 font-mono mb-2">{logsError}</p>}
                        <pre className="bg-ink-900 border border-ink-700 p-4 font-mono text-[11px] leading-[1.6] text-ink-200 whitespace-pre-wrap break-all">
                            {logs || (logsLoading ? "" : "No logs.")}
                        </pre>
                    </div>
                )}

                {tab === "console" && (
                    <div className="flex-1 flex flex-col min-h-0">
                        <div className="flex items-center justify-between px-4 py-2 border-b border-ink-700 shrink-0">
                            <span className="text-[10px] tracking-[0.22em] uppercase text-ink-500">attached to process stdio</span>
                        </div>
                        <div className="flex-1 min-h-0 p-2 bg-ink-900">
                            {!canMutate ? (
                                <p className="text-[12px] text-ink-500 font-mono p-3">You need editor permissions or higher to use the console.</p>
                            ) : !isRunning ? (
                                <p className="text-[12px] text-ink-500 font-mono p-3">The container isn't running — no console output to attach to.</p>
                            ) : (
                                <ConsolePane containerId={container.id} />
                            )}
                        </div>
                    </div>
                )}

                {tab === "terminal" && (
                    <div className="flex-1 flex flex-col min-h-0">
                        <div className="flex items-center justify-between px-4 py-2 border-b border-ink-700 shrink-0">
                            <span className="text-[10px] tracking-[0.22em] uppercase text-ink-500">/bin/sh</span>
                        </div>
                        <div className="flex-1 min-h-0 p-2 bg-ink-900">
                            {!canMutate ? (
                                <p className="text-[12px] text-ink-500 font-mono p-3">You need editor permissions or higher to use the terminal.</p>
                            ) : !isRunning ? (
                                <p className="text-[12px] text-ink-500 font-mono p-3">The container isn't running — start it to open a shell.</p>
                            ) : (
                                <TerminalPane containerId={container.id} />
                            )}
                        </div>
                    </div>
                )}
            </aside>
        </div>
    );
}
