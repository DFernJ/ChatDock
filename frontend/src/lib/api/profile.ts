import type { ProfileDTO } from "../../types/profile.ts";
import { request } from "../api.ts";

const ProfilePath: string = "/api/profile";

export const getProfile = () =>
    request<ProfileDTO>(`${ProfilePath}`, {
        method: "GET"
    })

export const updateUsername = (username: string) =>
    request<ProfileDTO>(`${ProfilePath}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username })
    })

export const updatePassword = (currentPassword: string, newPassword: string) =>
    request<void>(`${ProfilePath}/password`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ currentPassword, newPassword })
    })

export const createDiscordLinkCode = () =>
    request<{ code: string; expiresAt: string }>(`${ProfilePath}/discord/link-code`, {
        method: "POST"
    })

export const unlinkDiscord = () =>
    request<void>(`${ProfilePath}/discord`, {
        method: "DELETE"
    })

export const githubAuthorizeUrl = `${ProfilePath}/github/authorize`;

export const unlinkGithub = () =>
    request<void>(`${ProfilePath}/github`, {
        method: "DELETE"
    })
