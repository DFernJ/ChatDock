import { createContext, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import {
    clearNotifications,
    deleteNotification,
    listNotifications,
    markAllNotificationsRead,
} from "../lib/api/notifications.ts";
import type { NotificationDTO } from "../types/notifications.ts";

export type AppNotification = NotificationDTO;

export interface ToastMessage {
    id: string;
    message: string;
}

interface NotificationsContextValue {
    notifications: AppNotification[];
    unreadCount: number;
    toasts: ToastMessage[];
    markAllRead: () => void;
    dismiss: (id: string) => void;
    clearAll: () => void;
    dismissToast: (id: string) => void;
}

const NotificationsContext = createContext<NotificationsContextValue | null>(null);

const TOAST_DURATION_MS = 8000;

export function NotificationsProvider({ children }: { children: ReactNode }) {
    const [notifications, setNotifications] = useState<AppNotification[]>([]);
    const [toasts, setToasts] = useState<ToastMessage[]>([]);

    useEffect(() => {
        listNotifications().then(setNotifications).catch(() => {});
    }, []);

    useEffect(() => {
        const source = new EventSource("/api/docker/containers/events", { withCredentials: true });
        source.addEventListener("container-failure", (event: MessageEvent) => {
            const data = JSON.parse(event.data) as {
                id: string;
                containerId: string;
                containerName: string;
                exitCode: number | null;
                finishedAt: string;
                message: string;
            };

            setNotifications(curr => curr.some(n => n.id === data.id) ? curr : [
                {
                    id: data.id,
                    containerId: data.containerId,
                    containerName: data.containerName,
                    exitCode: data.exitCode,
                    finishedAt: data.finishedAt,
                    message: data.message,
                    createdAt: new Date().toISOString(),
                    read: false,
                },
                ...curr,
            ]);

            setToasts(curr => curr.some(t => t.id === data.id) ? curr : [...curr, { id: data.id, message: data.message }]);
            window.setTimeout(() => {
                setToasts(curr => curr.filter(t => t.id !== data.id));
            }, TOAST_DURATION_MS);
        });
        return () => source.close();
    }, []);

    const unreadCount = useMemo(() => notifications.filter(n => !n.read).length, [notifications]);

    const markAllRead = () => {
        if (unreadCount === 0) return;
        setNotifications(curr => curr.map(n => n.read ? n : { ...n, read: true }));
        markAllNotificationsRead().catch(() => {});
    };

    const dismiss = (id: string) => {
        setNotifications(curr => curr.filter(n => n.id !== id));
        deleteNotification(id).catch(() => {});
    };

    const clearAll = () => {
        setNotifications([]);
        clearNotifications().catch(() => {});
    };

    const dismissToast = (id: string) => setToasts(curr => curr.filter(t => t.id !== id));

    return (
        <NotificationsContext.Provider value={{ notifications, unreadCount, toasts, markAllRead, dismiss, clearAll, dismissToast }}>
            {children}
        </NotificationsContext.Provider>
    );
}

export function useNotifications() {
    const ctx = useContext(NotificationsContext);
    if (!ctx) throw new Error("useNotifications must be used within a NotificationsProvider");
    return ctx;
}
