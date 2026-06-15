import type { PermissionRole } from "../types/auth.ts";

const RANK: Record<PermissionRole, number> = { viewer: 0, editor: 1, root: 2 };

export function canEdit(role: PermissionRole | undefined): boolean {
    return !!role && RANK[role] >= RANK.editor;
}

export function canDelete(role: PermissionRole | undefined): boolean {
    return !!role && RANK[role] >= RANK.root;
}
