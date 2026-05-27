import { createContext, useContext, useEffect, useState } from "react";

import type { ReactNode } from "react";

type AuthRole = 'user' | 'admin';
type PermissionRole = 'viewer' | 'editor' | 'root';

interface User {id: string; username: string; authRole: AuthRole; permissionRole: PermissionRole}

type AuthStatus = 'loading' | 'no-auth' | 'auth';

interface AuthContextValue {
    user: User | null;
    status: AuthStatus;
    login: (user: User) => void;
    logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<User | null>(null);
    const [status, setStatus] = useState<AuthStatus>('loading');

    useEffect(() => {
        fetch('/api/auth/me', { credentials: 'include' })
            .then(res => (res.ok ? res.json() : null))
            .then(data => {
                setUser(data);
                setStatus(data ? 'auth' : 'no-auth');
            })
            .catch(() => setStatus('no-auth'));
    }, []);

    const login = (user: User) => {
        setUser(user);
        setStatus('auth');
    };

    const logout = () => {
        fetch('/api/auth/logout', { method: 'POST', credentials: 'include' }).catch(() => {});
        setUser(null);
        setStatus('no-auth');
    };

    return (
        <AuthContext.Provider value={{ user, status, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
    return ctx;
}