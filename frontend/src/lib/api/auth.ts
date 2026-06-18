import { User } from "../../types/auth.ts"
import {request} from "../api.ts";

const AuthPath: string = "/api/auth"

export const signIn = (email: string, password: string) =>
    request<User>(`${AuthPath}/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email, password: password })
    })

export const register = (username: string, email: string, password: string, code: string) =>
    request<User>(`${AuthPath}/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: username, email: email, password: password, code: code })
    })

export const authMe = () =>
    request<User>(`${AuthPath}/me`, {
        method: "GET"
    })

export const logout = () =>
    request<void>(`${AuthPath}/logout`, {
        method: "POST"
    })
