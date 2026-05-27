import { useEffect, useRef, useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext.tsx";

type TopbarVariantPage = 'no-auth' | 'auth'
interface TopbarProps {
    variant: TopbarVariantPage;
}
interface NavItem {
    label: string;
    to: string;
    count?: number;
}

const NavItems: NavItem[] = [
    { label: "Containers", to: "/containers", count: 14 },
    { label: "Images", to: "/images", count: 38 },
    { label: "Networks", to: "/networks", count: 5 },
    { label: "Volumes", to: "/volumes", count: 11 },
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
    const { user, logout } = useAuth();

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
        logout();
        navigate("/");
    };

    return (
        <div className="flex items-center gap-2 font-mono text-[16px]">
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
                    className="w-8 h-8 grid place-items-center border border-ink-700 bg-ink-900/60 text-accent font-mono font-bold text-[13px] hover:border-ink-600 transition"
                >
                    @
                </button>
                {open && (
                    <div className="absolute right-0 top-full mt-2 w-40 border border-ink-700 bg-ink-800 shadow-card font-mono text-[12px] z-20">
                        <button
                            type="button"
                            onClick={handleLogout}
                            className="w-full text-left px-3 py-2 text-ink-300 hover:bg-ink-700 hover:text-rose-400 transition"
                        >
                            Cerrar sesión
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}

export function Topbar({ variant }: TopbarProps) {
    const { user } = useAuth();

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
                            {NavItems.map((item) => (
                                <NavLink
                                    key={item.to}
                                    to={item.to}
                                    className={({ isActive }) =>
                                        [
                                            "flex items-center gap-1.5 px-3 py-1.5 transition",
                                            isActive
                                                ? "border border-ink-700 bg-ink-800 text-ink-50 font-semibold"
                                                : "text-ink-400 hover:text-ink-100",
                                        ].join(" ")
                                    }
                                >
                                    {({ isActive }) => (
                                        <>
                                            <span>{item.label}</span>
                                            {item.count !== undefined && (
                                                <span className={isActive ? "text-ink-400" : "text-ink-600"}>{item.count}</span>
                                            )}
                                        </>
                                    )}
                                </NavLink>
                            ))}
                            {user?.authRole === 'admin' && (
                                <NavLink
                                    key={"Administrator"}
                                    to={"/admin"}
                                    className={({ isActive }) =>
                                        [
                                            "flex items-center gap-1.5 px-3 py-1.5 transition",
                                            isActive
                                                ? "border border-ink-700 bg-ink-800 text-ink-50 font-semibold"
                                                : "text-ink-400 hover:text-ink-100",
                                        ].join(" ")
                                    }
                                >
                                    <span>{"Administrator"}</span>

                                </NavLink>
                            ) }
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