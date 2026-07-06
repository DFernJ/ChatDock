import { request } from "../api.ts";
import type { NotificationDTO } from "../../types/notifications.ts";

const NotificationsPath = "/api/notifications";

export const listNotifications = () =>
    request<NotificationDTO[]>(NotificationsPath, {
        method: "GET"
    });

export const markAllNotificationsRead = () =>
    request<void>(`${NotificationsPath}/read`, {
        method: "POST"
    });

export const deleteNotification = (id: string) =>
    request<void>(`${NotificationsPath}/${id}`, {
        method: "DELETE"
    });

export const clearNotifications = () =>
    request<void>(NotificationsPath, {
        method: "DELETE"
    });
