import { useState } from "react";
import type { ChangeEvent, ReactNode } from "react";

export function clamp(n: number, lo = 0, hi = 100) {
    return Math.max(lo, Math.min(hi, n));
}

export function formatBytes(bytes: number): string {
    if (!bytes || bytes <= 0) return "0 B";
    const units = ["B", "KB", "MB", "GB", "TB"];
    const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)));
    return `${(bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

type IconName =
    | "play" | "stop" | "reset" | "trash" | "open" | "import"
    | "search" | "x" | "chev" | "download" | "link" | "unlink" | "refresh" | "plus";

export function Icon({ name, className = "w-3.5 h-3.5" }: { name: IconName; className?: string }) {
    switch (name) {
        case "play":     return <svg viewBox="0 0 16 16" className={className} fill="currentColor"><path d="M4 3l9 5-9 5z"/></svg>;
        case "stop":     return <svg viewBox="0 0 16 16" className={className} fill="currentColor"><rect x="4" y="4" width="8" height="8"/></svg>;
        case "reset":    return <svg viewBox="0 0 16 16" className={className} fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M3 8a5 5 0 1 0 1.5-3.5"/><path d="M3 3v3h3"/></svg>;
        case "refresh":  return <svg viewBox="0 0 16 16" className={className} fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M3 8a5 5 0 1 0 1.5-3.5"/><path d="M3 3v3h3"/></svg>;
        case "trash":    return <svg viewBox="0 0 16 16" className={className} fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M3 4h10M6 4V3a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v1M4.5 4l.6 9a1 1 0 0 0 1 .9h3.8a1 1 0 0 0 1-.9l.6-9"/></svg>;
        case "open":     return <svg viewBox="0 0 16 16" className={className} fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M9 3h4v4M13 3l-6 6M7 5H4a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1V9"/></svg>;
        case "import":   return <svg viewBox="0 0 16 16" className={className} fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M8 2v8M5 7l3 3 3-3M3 12v2h10v-2"/></svg>;
        case "download": return <svg viewBox="0 0 16 16" className={className} fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M8 2v8M5 7l3 3 3-3M3 12v2h10v-2"/></svg>;
        case "search":   return <svg viewBox="0 0 16 16" className={className} fill="none" stroke="currentColor" strokeWidth="1.4"><circle cx="7" cy="7" r="4"/><path d="M10 10l3 3"/></svg>;
        case "x":        return <svg viewBox="0 0 16 16" className={className} fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M4 4l8 8M12 4l-8 8"/></svg>;
        case "chev":     return <svg viewBox="0 0 16 16" className={className} fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M6 4l4 4-4 4"/></svg>;
        case "link":     return <svg viewBox="0 0 16 16" className={className} fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M6.5 9.5l3-3M6 5.5H4.5A2.5 2.5 0 0 0 2 8v0a2.5 2.5 0 0 0 2.5 2.5H6M10 5.5h1.5A2.5 2.5 0 0 1 14 8v0a2.5 2.5 0 0 1-2.5 2.5H10"/></svg>;
        case "unlink":   return <svg viewBox="0 0 16 16" className={className} fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M2 2l12 12M6 5.5H4.5A2.5 2.5 0 0 0 2 8v0a2.5 2.5 0 0 0 2.5 2.5H6M10 5.5h1.5A2.5 2.5 0 0 1 14 8v0a2.5 2.5 0 0 1-2.5 2.5H10"/></svg>;
        case "plus":     return <svg viewBox="0 0 16 16" className={className} fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M8 3v10M3 8h10"/></svg>;
        default:         return null;
    }
}

export function Bar({ value, accent = false }: { value: number; accent?: boolean }) {
    return (
        <div className="w-full h-1.5 bg-ink-900 border border-ink-700/80 relative overflow-hidden">
            <div
                className={`h-full ${accent ? "bg-accent" : value > 70 ? "bg-amber-300" : "bg-ink-300"}`}
                style={{ width: `${clamp(value)}%` }}
            ></div>
        </div>
    );
}

type ActionVariant = "default" | "primary" | "danger";

export function ActionBtn({ icon, label, onClick, disabled, variant = "default" }: {
    icon: IconName; label: string; onClick?: () => void; disabled?: boolean; variant?: ActionVariant;
}) {
    const styles: Record<ActionVariant, string> = {
        default: "text-ink-300 hover:text-ink-50 hover:bg-ink-800 hover:border-ink-600",
        primary: "text-accent hover:bg-accent-soft hover:border-accent-line",
        danger:  "text-rose-300 hover:bg-rose-500/10 hover:border-rose-400/50",
    };
    return (
        <button
            type="button"
            onClick={onClick}
            disabled={disabled}
            title={label}
            aria-label={label}
            className={`group relative w-8 h-8 grid place-items-center border border-ink-700 bg-ink-900/40 transition disabled:opacity-30 disabled:cursor-not-allowed ${styles[variant]}`}
        >
            <Icon name={icon} />
            <span className="pointer-events-none absolute -top-7 left-1/2 -translate-x-1/2 px-2 py-0.5 bg-ink-700 text-[10px] tracking-[0.14em] uppercase text-ink-100 opacity-0 group-hover:opacity-100 transition whitespace-nowrap">
                {label}
            </span>
        </button>
    );
}

export function Section({ title, subtitle, badge, count, children }: {
    title: string; subtitle: string; badge: string; count: number; children: ReactNode;
}) {
    const [collapsed, setCollapsed] = useState(false);
    return (
        <section className="border border-ink-700 bg-ink-800/40 fade d2">
            <header className="flex items-center justify-between px-5 py-4 border-b border-ink-700">
                <div className="flex items-start gap-4">
                    <button type="button" onClick={() => setCollapsed(c => !c)} className="text-ink-500 hover:text-ink-100 mt-1">
                        <Icon name="chev" className={`w-3 h-3 transition-transform ${collapsed ? "" : "rotate-90"}`} />
                    </button>
                    <div>
                        <div className="flex items-baseline gap-3">
                            <span className="text-[10px] tracking-[0.22em] uppercase px-2 py-0.5 bg-ink-900 border border-ink-700 text-ink-300 self-center">
                                {badge}
                            </span>
                            <h2 className="font-sans text-[18px] tracking-tight text-ink-50">{title}</h2>
                            <span className="font-mono text-[12px] text-ink-500 leading-none">{count}</span>
                        </div>
                        <p className="text-[11px] text-ink-500 mt-0.5 font-mono">{subtitle}</p>
                    </div>
                </div>
            </header>
            {!collapsed && children}
        </section>
    );
}

export function EmptyState({ title, message }: { title: string; message: ReactNode }) {
    return (
        <div className="px-5 py-10 text-center">
            <div className="font-sans text-[16px] text-ink-200">{title}</div>
            <p className="text-[12px] text-ink-500 mt-1.5 font-mono">{message}</p>
        </div>
    );
}

export function ErrorBanner({ message }: { message: string }) {
    return (
        <div className="px-5 py-3 border-b border-ink-700 bg-rose-500/5 text-[12px] font-mono text-rose-300">
            {message}
        </div>
    );
}

export function Toolbar({
    search, onSearchChange, onRefresh, refreshing,
    primaryLabel, onPrimary, primaryDisabled,
    secondaryLabel, onSecondary, secondaryDisabled,
}: {
    search: string;
    onSearchChange: (v: string) => void;
    onRefresh: () => void;
    refreshing: boolean;
    primaryLabel?: string;
    onPrimary?: () => void;
    primaryDisabled?: boolean;
    secondaryLabel?: string;
    onSecondary?: () => void;
    secondaryDisabled?: boolean;
}) {
    return (
        <div className="flex flex-wrap items-center gap-3 fade d1">
            <div className="flex items-center border border-ink-700 bg-ink-900/60 focus-within:border-accent">
                <span className="grid place-items-center px-3 text-ink-500"><Icon name="search" /></span>
                <input
                    value={search}
                    onChange={(e: ChangeEvent<HTMLInputElement>) => onSearchChange(e.target.value)}
                    placeholder="buscar nombre o imagen…"
                    className="bg-transparent border-0 text-ink-50 font-mono text-[12px] py-2 pr-3 w-56 placeholder:text-ink-600"
                />
            </div>
            <ActionBtn icon="refresh" label="Refrescar" onClick={onRefresh} disabled={refreshing} />
            <div className="flex-1" />
            {secondaryLabel && onSecondary && (
                <button
                    type="button"
                    onClick={onSecondary}
                    disabled={secondaryDisabled}
                    className="px-4 py-2.5 border border-ink-700 text-rose-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-rose-500/10 hover:border-rose-400/50 transition disabled:opacity-40 disabled:cursor-not-allowed"
                >
                    {secondaryLabel}
                </button>
            )}
            {primaryLabel && onPrimary && (
                <button
                    type="button"
                    onClick={onPrimary}
                    disabled={primaryDisabled}
                    className="flex items-center gap-2.5 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase px-4 py-2.5 hover:brightness-110 active:translate-y-px transition disabled:opacity-40 disabled:cursor-not-allowed"
                >
                    <Icon name="plus" className="w-3.5 h-3.5" />
                    {primaryLabel}
                </button>
            )}
        </div>
    );
}

export function ConfirmDialog({ open, title, body, confirmLabel, danger, busy, onCancel, onConfirm }: {
    open: boolean;
    title: string;
    body: string;
    confirmLabel: string;
    danger?: boolean;
    busy?: boolean;
    onCancel: () => void;
    onConfirm: () => void;
}) {
    if (!open) return null;
    return (
        <div className="fixed inset-0 z-50 grid place-items-center p-4 bg-black/60">
            <div className="w-full max-w-[440px] bg-ink-800 border border-ink-700 shadow-card">
                <div className="px-5 py-4 border-b border-ink-700 font-mono text-[11px] text-ink-500">confirmar</div>
                <div className="p-6">
                    <h3 className="font-sans text-[18px] tracking-tight text-ink-50">{title}</h3>
                    <p className="text-[12px] text-ink-400 leading-relaxed mt-2">{body}</p>
                    <div className="mt-5 flex justify-end gap-2">
                        <button type="button" onClick={onCancel} disabled={busy} className="px-4 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-ink-900 disabled:opacity-50">
                            Cancelar
                        </button>
                        <button
                            type="button"
                            onClick={onConfirm}
                            disabled={busy}
                            className={`px-5 py-2 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase disabled:opacity-60 ${
                                danger ? "bg-rose-400 text-ink-900 hover:brightness-110" : "bg-accent text-ink-900 hover:brightness-110"
                            }`}
                        >
                            {busy ? "…" : confirmLabel}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
    return (
        <label className="flex flex-col gap-1.5">
            <div className="flex justify-between items-baseline">
                <span className="text-[10px] tracking-[0.2em] uppercase text-ink-500">{label}</span>
                {hint && <span className="text-[11px] text-ink-500">{hint}</span>}
            </div>
            {children}
        </label>
    );
}

export function Input({ icon, value, onChange, placeholder }: {
    icon?: string; value: string; onChange: (v: string) => void; placeholder?: string;
}) {
    return (
        <div className="flex items-stretch border border-ink-700 bg-ink-900/60 focus-within:border-accent">
            {icon && (
                <span className="grid place-items-center px-3 text-accent font-mono text-xs border-r border-ink-700 bg-black/20 select-none">{icon}</span>
            )}
            <input
                value={value}
                onChange={(e: ChangeEvent<HTMLInputElement>) => onChange(e.target.value)}
                placeholder={placeholder}
                spellCheck={false}
                className="flex-1 bg-transparent border-0 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600"
            />
        </div>
    );
}

export function Modal({ title, subtitle, path, onClose, children, footer }: {
    title: string; subtitle?: string; path: string; onClose: () => void; children: ReactNode; footer: ReactNode;
}) {
    return (
        <div className="fixed inset-0 z-40 grid place-items-center p-4 bg-black/60">
            <div className="relative w-full max-w-[560px] bg-ink-800 border border-ink-700 shadow-card corners">
                <div className="flex items-center justify-between px-5 py-3 border-b border-ink-700 bg-ink-900/60">
                    <div className="font-mono text-[11px] text-ink-500">chatops://ops.local/<span className="text-accent">{path}</span></div>
                    <button type="button" onClick={onClose} className="w-7 h-7 grid place-items-center border border-ink-700 text-ink-300 hover:text-ink-50">
                        <Icon name="x" />
                    </button>
                </div>
                <div className="p-6">
                    <h2 className="font-sans text-[22px] tracking-tight text-ink-50">{title}</h2>
                    {subtitle && <p className="text-[12px] text-ink-500 mt-1.5 mb-5">{subtitle}</p>}
                    <div className="flex flex-col gap-3 mt-4">{children}</div>
                    <div className="mt-6 flex items-center justify-end gap-2">{footer}</div>
                </div>
            </div>
        </div>
    );
}
