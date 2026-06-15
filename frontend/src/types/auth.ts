export type AuthRole = 'user' | 'admin';
export type PermissionRole = 'viewer' | 'editor' | 'root';
export type AuthStatus = 'loading' | 'no-auth' | 'auth';

export interface User {
    id: string;
    username: string;
    authRole: AuthRole;
    permissionRole: PermissionRole
}

export interface AuthContextValue {
    user: User | null;
    status: AuthStatus;
    login: (user: User) => void;
    logoutAuth: () => void;
}

