import { createPortal } from "react-dom";
import { useNotifications } from "../context/NotificationsContext.tsx";
import { Icon } from "./Ui.tsx";

export default function NotificationsPanel({ onClose }: { onClose: () => void }) {
    const { notifications, dismiss, clearAll } = useNotifications();

    return createPortal(
        <div className="fixed inset-0 z-40 flex">
            <div className="flex-1 bg-black/40" onClick={onClose}></div>
            <aside className="relative w-full max-w-[380px] bg-ink-800 border-l border-ink-700 shadow-card flex flex-col">
                <div className="px-5 py-4 border-b border-ink-700 flex items-center justify-between shrink-0">
                    <div>
                        <div className="font-mono text-[10px] tracking-[0.22em] uppercase text-ink-500 mb-1">notifications</div>
                        <h2 className="font-sans text-[18px] tracking-tight text-ink-50">Container alerts</h2>
                    </div>
                    <button type="button" onClick={onClose} className="w-8 h-8 shrink-0 grid place-items-center border border-ink-700 text-ink-300 hover:text-ink-50 hover:bg-ink-900">
                        <Icon name="x" />
                    </button>
                </div>

                {notifications.length > 0 && (
                    <div className="px-5 py-2.5 border-b border-ink-700 flex justify-end shrink-0">
                        <button
                            type="button"
                            onClick={clearAll}
                            className="font-mono text-[11px] tracking-[0.14em] uppercase text-ink-500 hover:text-rose-300 transition"
                        >
                            Clear all
                        </button>
                    </div>
                )}

                <div className="flex-1 overflow-y-auto scrollbar">
                    {notifications.length === 0 ? (
                        <div className="px-5 py-10 text-center">
                            <p className="text-[12px] text-ink-500 font-mono">No notifications yet.</p>
                        </div>
                    ) : (
                        notifications.map(n => (
                            <div key={n.id} className="px-5 py-3.5 border-b border-ink-700/70 flex items-start gap-3">
                                <span className="w-2 h-2 mt-1.5 shrink-0 bg-rose-400"></span>
                                <div className="flex-1 min-w-0">
                                    <p className="text-[12px] text-ink-100 leading-relaxed font-mono">{n.message}</p>
                                    <span className="text-[10px] text-ink-600 font-mono">{new Date(n.createdAt).toLocaleString()}</span>
                                </div>
                                <button
                                    type="button"
                                    onClick={() => dismiss(n.id)}
                                    aria-label="Dismiss notification"
                                    className="w-6 h-6 shrink-0 grid place-items-center text-ink-500 hover:text-rose-300 transition"
                                >
                                    <Icon name="trash" className="w-3.5 h-3.5" />
                                </button>
                            </div>
                        ))
                    )}
                </div>
            </aside>
        </div>,
        document.body
    );
}
