import { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { AuthStatus, User, AuthContextValue } from "../types/auth.ts";
import { authMe, logout } from "../lib/api/auth.ts";

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<User | null>(null);
    const [status, setStatus] = useState<AuthStatus>('loading');

    useEffect(() => {
        authMe().then(data => {
            setUser(data)
            setStatus(data ? 'auth' : 'no-auth')
            })
            .catch(() => setStatus('no-auth'));
    }, []);

    const login = (user: User) => {
        setUser(user);
        setStatus('auth');
    };

    const logoutAuth = () => {
        logout().catch(() => {})
        console.log("Logging out user:", user);
        setUser(null);
        setStatus('no-auth');
    };

    return (
        <AuthContext.Provider value={{ user, status, login, logoutAuth }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
    return ctx;
}