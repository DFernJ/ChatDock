import type { AuthRole, PermissionRole } from "./auth.ts";

export type CodeType = "register" | "discord";

export interface AdminUserDTO {
    id: string;
    username: string;
    email: string;
    authRole: AuthRole;
    permissionRole: PermissionRole;
    enabled: boolean;
    createdAt: string;
}

export interface CodeDTO {
    id: string;
    code: string;
    codeType: CodeType;
    remainUses: number;
    createdAt: string;
}

export interface CreateUserRequest {
    username: string;
    email: string;
    password: string;
    authRole: AuthRole;
    permissionRole: PermissionRole;
}

export interface GenerateCodeRequest {
    codeType: CodeType;
    uses: number;
}
