import { useEffect, useRef, useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext.tsx";
import { getCounts } from "../lib/api/docker.ts";
import type { CountDTO } from "../types/docker.ts";

type TopbarVariantPage = 'no-auth' | 'auth'
export type DashboardView = 'containers' | 'images' | 'networks' | 'volumes' | 'adminPanel';

interface TopbarProps {
    variant: TopbarVariantPage;
    activeView?: DashboardView;
    onSelectView?: (view: DashboardView) => void;
}
interface NavItem {
    label: string;
    view: DashboardView;
}

const NavItems: NavItem[] = [
    { label: "Containers", view: "containers" },
    { label: "Images", view: "images" },
    { label: "Networks", view: "networks" },
    { label: "Volumes", view: "volumes" },
    { label: "Admin panel", view: "adminPanel"}
]

function Logo() {
    return (
        <div className="flex items-center gap-3">
            <div className="relative">
                <div className="w-8 h-8 grid place-items-center bg-accent text-ink-900 font-mono font-bold text-[13px] shadow-[0_0_0_1px_rgba(190,242,100,0.4),0_8px_24px_-8px_#bef264]">
                    /&gt;
                </div>
                <div className="absolute -inset-0.75 border border-dashed border-accent-line pointer-events-none"></div>
            </div>
            <div className="font-mono text-[20px] tracking-tight text-ink-50">
                Chat<span className="font-bold">Ops</span>
            </div>
        </div>
    );
}

function UserMenu() {
    const [open, setOpen] = useState(false);
    const menuRef = useRef<HTMLDivElement>(null);
    const navigate = useNavigate();
    const { user, logoutAuth } = useAuth();

    useEffect(() => {
        if (!open) return;
        const onClickOutside = (e: MouseEvent) => {
            if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
                setOpen(false);
            }
        };
        document.addEventListener("mousedown", onClickOutside);
        return () => document.removeEventListener("mousedown", onClickOutside);
    }, [open]);

    const handleLogout = () => {
        setOpen(false);
        logoutAuth();
        navigate("/");
    };

    return (
        <div className="flex items-center gap-2 font-mono text-[18px]">
            <NavLink
                to="/profile"
                className={({ isActive }) =>
                    `text-ink-300 hover:text-ink-50 transition ${isActive ? "text-ink-50 font-semibold" : ""}`
                }
            >
                {user?.username}
            </NavLink>
            <div className="relative" ref={menuRef}>
                <button
                    type="button"
                    onClick={() => setOpen(o => !o)}
                    className="w-10 h-10 grid place-items-center border border-ink-700 bg-ink-900/60 text-accent font-mono font-bold text-[17px] hover:border-ink-600 transition"
                >
                    @
                </button>
                {open && (
                    <div className="absolute right-0 top-full mt-3 w-max border border-ink-700 bg-ink-800 shadow-card font-mono text-[14px] z-20">
                        <button
                            type="button"
                            onClick={() => { setOpen(false); navigate("/profile"); }}
                            className="w-full text-right px-3 py-2 text-ink-300 hover:bg-ink-700 hover:text-accent transition border-b border-ink-700"
                        >
                            Profile
                        </button>
                        <button
                            type="button"
                            onClick={handleLogout}
                            className="w-full text-right px-3 py-2 text-ink-300 hover:bg-ink-700 hover:text-rose-400 transition"
                        >
                            Logout
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}

export function Topbar({ variant, activeView, onSelectView }: TopbarProps) {
    const { user } = useAuth();
    const navigate = useNavigate();
    const navItems = NavItems.filter(item => item.view !== 'adminPanel' || user?.authRole === 'admin');
    const [counts, setCounts] = useState<CountDTO | null>(null);

    useEffect(() => {
        if (variant !== 'auth') return;
        getCounts().then(setCounts).catch(() => {});
    }, [variant]);

    return (
        <header className="relative z-10 flex items-center justify-between gap-8 px-6 h-16 border-b border-ink-700/70">
            {variant === 'no-auth' && (
                <Logo />
            )}
            {variant === 'auth' && (
                <div className={"flex items-center justify-between w-full"}>
                    <div className={"flex items-center gap-7"}>
                        <Logo />
                        <nav className="flex items-center gap-1 font-mono text-[16px]">
                            {navItems.map((item) => {
                                const isActive = activeView === item.view;
                                const count = item.view === 'adminPanel' ? undefined : counts?.[item.view];
                                return (
                                    <button
                                        key={item.view}
                                        type="button"
                                        onClick={() => item.view === 'adminPanel' ? navigate('/admin') : onSelectView?.(item.view)}
                                        className={[
                                            "flex items-center gap-1.5 px-3 py-1.5 transition",
                                            isActive
                                                ? "border border-ink-700 bg-ink-800 text-ink-50 font-semibold"
                                                : "text-ink-400 hover:text-ink-100",
                                        ].join(" ")}
                                    >
                                        <span>{item.label}</span>
                                        {count !== undefined && (
                                            <span className={isActive ? "text-ink-400" : "text-ink-600"}>{count}</span>
                                        )}
                                    </button>
                                );
                            })}
                        </nav>
                    </div>
                    <div className={"flex items-center gap-7"}>
                        <UserMenu />
                    </div>
                </div>
            )}
        </header>
    )
}