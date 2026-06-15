import type { AdminUserDTO, CodeDTO, CreateUserRequest, GenerateCodeRequest } from "../../types/admin.ts";
import { request } from "../api.ts";

/*
 * Backed by AdminController (core-api), all routes gated to PERM_ROOT:
 *   GET    /api/admin/users                 -> AdminUserResponse[]
 *   POST   /api/admin/users                 <- CreateUserRequest       -> AdminUserResponse
 *   PATCH  /api/admin/users/{id}             <- UpdateUserRequest       -> AdminUserResponse
 *   DELETE /api/admin/users/{id}
 *   GET    /api/admin/codes                 -> CodeResponse[]
 *   POST   /api/admin/codes                 <- GenerateCodeRequest     -> CodeResponse
 *   DELETE /api/admin/codes/{id}
 */
const AdminPath: string = "/api/admin";

export const listUsers = () =>
    request<AdminUserDTO[]>(`${AdminPath}/users`, {
        method: "GET"
    });

export const createUser = (body: CreateUserRequest) =>
    request<AdminUserDTO>(`${AdminPath}/users`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    });

export const setUserEnabled = (id: string, enabled: boolean) =>
    request<AdminUserDTO>(`${AdminPath}/users/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ enabled })
    });

export const setUserAuthRole = (id: string, authRole: string) =>
    request<AdminUserDTO>(`${AdminPath}/users/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ authRole })
    });

export const setUserPermissionRole = (id: string, permissionRole: string) =>
    request<AdminUserDTO>(`${AdminPath}/users/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ permissionRole })
    });

export const deleteUser = (id: string) =>
    request<void>(`${AdminPath}/users/${id}`, {
        method: "DELETE"
    });

export const listCodes = () =>
    request<CodeDTO[]>(`${AdminPath}/codes`, {
        method: "GET"
    });

export const generateCode = (body: GenerateCodeRequest) =>
    request<CodeDTO>(`${AdminPath}/codes`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    });

export const deleteCode = (id: string) =>
    request<void>(`${AdminPath}/codes/${id}`, {
        method: "DELETE"
    });
