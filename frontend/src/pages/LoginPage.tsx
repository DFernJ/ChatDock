import { useEffect, useState } from "react";
import type { ChangeEvent, SubmitEvent, ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import {Topbar} from "../components/Topbar.tsx";
import { useAuth } from "../context/AuthContext.tsx";
import  {ApiError} from  "../lib/api.ts";
import { register, signIn} from "../lib/api/auth.ts";
import { MIN_PASSWORD_LENGTH, PASSWORD_STRENGTH_COLORS, PASSWORD_STRENGTH_LABELS, passwordStrength } from "../lib/passwordStrength.ts";

type Tab = "signin" | "register";

interface FieldProps {
    label: string;
    hint?: string;
    hintHref?: string;
    children: ReactNode;
}

interface InputProps {
    icon?: ReactNode;
    type?: string;
    value: string;
    onChange: (e: ChangeEvent<HTMLInputElement>) => void;
    placeholder?: string;
    autoComplete?: string;
    trailing?: ReactNode;
}

interface TabsProps {
    value: Tab;
    onChange: (t: Tab) => void;
}

function Field({ label, hint, hintHref, children }: FieldProps) {
    return (
        <label className="flex flex-col gap-1.5">
            <div className="flex justify-between items-baseline">
                <span className="text-[10px] tracking-[0.2em] uppercase text-ink-500">{label}</span>
                {hint && (
                    hintHref
                        ? <a href={hintHref} className="text-[11px] text-ink-400 hover:text-accent border-b border-dashed border-ink-700">{hint}</a>
                        : <span className="text-[11px] text-ink-400">{hint}</span>
                )}
            </div>
            {children}
        </label>
    );
}

function Input({ icon, type = "text", value, onChange, placeholder, autoComplete, trailing }: InputProps) {
    return (
        <div className="flex items-stretch border border-ink-700 bg-ink-900/60 focus-within:border-accent focus-within:ring-2 focus-within:ring-accent-soft transition">
            {icon && (
                <span className="grid place-items-center px-3 text-accent font-mono text-xs border-r border-ink-700 bg-black/20 select-none">
          {icon}
        </span>
            )}
            <input
                type={type}
                value={value}
                onChange={onChange}
                placeholder={placeholder}
                autoComplete={autoComplete}
                spellCheck={false}
                className="flex-1 bg-transparent border-0 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600"
            />
            {trailing}
        </div>
    );
}

function Tabs({ value, onChange }: TabsProps) {
    const tabs: { id: Tab; label: string }[] = [
        { id: "signin", label: "Sign in" },
        { id: "register", label: "Register" },
    ];
    return (
        <div className="grid grid-cols-2 border border-ink-700 bg-ink-900/60 mb-6">
            {tabs.map((t, i) => (
                <button
                    key={t.id}
                    type="button"
                    onClick={() => onChange(t.id)}
                    className={[
                        "py-2.5 px-3 font-mono text-[11px] tracking-[0.18em] uppercase transition flex items-center justify-center gap-2",
                        i === 0 ? "border-r border-ink-700" : "",
                        value === t.id
                            ? "text-ink-50 bg-ink-800 shadow-[inset_0_-2px_0_#bef264]"
                            : "text-ink-500 hover:text-ink-200",
                    ].join(" ")}
                >
                    <span className={`w-1.5 h-1.5 ${value === t.id ? "bg-accent" : "bg-ink-600"}`}></span>
                    {t.label}
                </button>
            ))}
        </div>
    );
}

function SignIn() {
    const [user, setUser] = useState("");
    const [pass, setPass] = useState("");
    const [show, setShow] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const { login } = useAuth();
    const navigate = useNavigate();

    const submit = async (e: SubmitEvent) => {
        e.preventDefault();
        setLoading(true);
        setError(null);
        try {
            login(await signIn(user, pass));
            navigate("/");
        } catch (e) {
              // setError(e instanceof ApiError ? "Invalid operator or secret": "Could not reach the daemon");
                setError(e instanceof ApiError ? e.message : "Could not reach the daemon");
        } finally {
            setLoading(false);
        }
    };

    return (
        <form onSubmit={submit} className="flex flex-col gap-4">
            <Field label="Operator" hint="email">
                <Input icon="@" value={user} onChange={(e) => setUser(e.target.value)} placeholder="email" autoComplete="email" />
            </Field>
            <Field label="Secret" /*hint="forgot your password?"*/ hintHref="#">
                <Input
                    icon="∗"
                    type={show ? "text" : "password"}
                    value={pass}
                    onChange={(e) => setPass(e.target.value)}
                    placeholder="••••••••••••"
                    autoComplete="current-password"
                    trailing={
                        <button type="button" onClick={() => setShow(s => !s)}
                                className="px-3.5 text-[10px] tracking-[0.16em] uppercase text-ink-500 hover:text-ink-100 border-l border-ink-700">
                            {show ? "hide" : "show"}
                        </button>
                    }
                />
            </Field>
            {error && <p className="text-[12px] text-rose-400 font-mono">{error}</p>}
            <button
                type="submit"
                disabled={loading}
                className="mt-2 w-full bg-accent text-ink-900 font-mono text-[12px] font-semibold tracking-[0.18em] uppercase py-3.5 px-4 flex items-center justify-between hover:brightness-110 active:translate-y-px transition disabled:opacity-70"
            >
                <span>{loading ? "Authenticating…" : "Initiate session"}</span>
            </button>
        </form>
    );
}

function Register({ onDone }: { onDone: () => void }) {
    const [name, setName] = useState("");
    const [email, setEmail] = useState("");
    const [pass, setPass] = useState("");
    const [code, setCode] = useState("");
    const [agree, setAgree] = useState(false);
    const [show, setShow] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const { login } = useAuth();
    const navigate = useNavigate();

    const score = passwordStrength(pass);

    const submit = async (e: SubmitEvent) => {
        e.preventDefault();
        if (pass.length < MIN_PASSWORD_LENGTH) {
            setError(`Password must be at least ${MIN_PASSWORD_LENGTH} characters`);
            return;
        }
        setLoading(true);
        setError(null);
        try {
            login(await register(name, email, pass, code));
            navigate("/");
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not reach the daemon");
        } finally {
            setLoading(false);
        }
    };

    return (
        <form onSubmit={submit} className="flex flex-col gap-4">
            <Field label="Operator name">
                <Input icon="@" value={name} onChange={(e) => setName(e.target.value)} placeholder="ops-lead" autoComplete="username" />
            </Field>
            <Field label="Email">
                <Input icon="✉" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@chat.ops" autoComplete="email" />
            </Field>
            <Field label="Set a password">
                <Input
                    icon="∗"
                    type={show ? "text" : "password"}
                    value={pass}
                    onChange={(e) => setPass(e.target.value)}
                    placeholder="min. 8 characters"
                    autoComplete="new-password"
                    trailing={
                        <button type="button" onClick={() => setShow(s => !s)}
                                className="px-3.5 text-[10px] tracking-[0.16em] uppercase text-ink-500 hover:text-ink-100 border-l border-ink-700">
                            {show ? "hide" : "show"}
                        </button>
                    }
                />
                <div className="flex items-center gap-2 mt-1.5">
                    <div className="flex gap-1 flex-1">
                        {[0, 1, 2, 3].map(i => (
                            <span key={i} className={`h-0.75 flex-1 ${i < score ? PASSWORD_STRENGTH_COLORS[score] : "bg-ink-700"}`}></span>
                        ))}
                    </div>
                    <span className="text-[10px] uppercase tracking-[0.18em] text-ink-500 w-20 text-right">{PASSWORD_STRENGTH_LABELS[score]}</span>
                </div>
            </Field>
            <Field label="Registration code" hint="required to join">
                <Input icon="#" value={code} onChange={(e) => setCode(e.target.value)} placeholder="invite code" autoComplete="off" />
            </Field>
            <label className="flex items-center gap-2.5 mt-1 cursor-pointer select-none">
                <input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} className="hidden peer" />
                <span className="w-3.5 h-3.5 border border-ink-600 bg-ink-900 grid place-items-center peer-checked:bg-accent peer-checked:border-accent transition shrink-0">
                    <span className={`w-1.5 h-1.5 ${agree ? "bg-ink-900" : "bg-transparent"}`}></span>
                </span>
                <span className="text-[14px] text-ink-300 leading-relaxed">
                    I understand this is a self-hosted service and accept the{" "}
                    <Link className="text-accent border-b border-dashed border-accent-line" to="/terms" target="_blank" rel="noopener noreferrer">Terms of Service</Link>
                    {" "}and{" "}
                    <Link className="text-accent border-b border-dashed border-accent-line" to="/legal" target="_blank" rel="noopener noreferrer">Privacy Policy</Link>.
                </span>
            </label>

            {error && <p className="text-[12px] text-rose-400 font-mono">{error}</p>}
            <button
                type="submit"
                disabled={loading || !agree}
                className="mt-1 w-full bg-accent text-ink-900 font-mono text-[12px] font-semibold tracking-[0.18em] uppercase py-3.5 px-4 flex items-center justify-between hover:brightness-110 active:translate-y-px transition disabled:opacity-50 disabled:cursor-not-allowed"
            >
                <span>{loading ? "Provisioning…" : "Create operator"}</span>
            </button>

            <p className="text-[14px] text-ink-500 text-center mt-1">
                Already have access?{" "}
                <button type="button" onClick={onDone} className="text-ink-200 hover:text-accent border-b border-dashed border-ink-700">
                    Sign in instead
                </button>
            </p>
        </form>
    );
}

export default function LoginPage() {
    const [tab, setTab] = useState<Tab>("signin");
    const [typed, setTyped] = useState("");
    const target = tab === "signin"
        ? "ssh operator@chatops --auth"
        : "chatops register --new-operator";

    useEffect(() => {
        setTyped("");
        let i = 0;
        const id = window.setInterval(() => {
            i++;
            setTyped(target.slice(0, i));
            if (i >= target.length) window.clearInterval(id);
        }, 28);
        return () => window.clearInterval(id);
    }, [tab]);

    return (
        <div className="relative min-h-screen w-full bg-stage bg-grid overflow-hidden">
            <Topbar variant={"no-auth"} />
            <main className="relative z-10 grid place-items-center px-4 py-10 sm:py-16">
                <div className="w-full max-w-105">
                    <div className="text-center mb-8 fade d1">
                        <div className="text-[10px] tracking-[0.22em] uppercase text-ink-500 mb-3">Self-hosted · v0.0.1</div>
                        <h1 className="font-sans text-[34px] leading-[1.05] tracking-tight text-ink-50">
                            Talk to your <em className="not-italic text-accent font-normal" style={{ fontStyle: "italic" }}>containers</em>.
                        </h1>
                    </div>

                    <div className="relative bg-ink-800 border border-ink-700 shadow-card corners fade d2">
                        <div className="flex items-center gap-2.5 px-5 py-3 border-b border-ink-700 bg-ink-900/60 text-[11px] text-ink-500">
                            <div className="flex gap-1.5">
                                <span className="w-2.5 h-2.5 bg-ink-600"></span>
                                <span className="w-2.5 h-2.5 bg-ink-600"></span>
                                <span className="w-2.5 h-2.5 bg-ink-600"></span>
                            </div>
                            <div className="font-mono text-[14px]">
                                <span>chatops://</span><span className="text-accent">ops.local</span><span>/auth</span>
                            </div>
                        </div>

                        <div className="p-7 sm:p-8">
                            <div className="font-mono text-[14px] text-ink-500 mb-1 flex items-center gap-2">
                                <span className="text-accent">~/chatops $</span>
                                <span className="text-ink-100">{typed}</span>
                                <span className="caret"></span>
                            </div>
                            <h2 className="font-sans text-[24px] tracking-tight text-ink-50 mt-1.5">
                                {tab === "signin" ? "Sign into your operator account" : "Create your operator account"}
                            </h2>
                            <p className="text-[14px] text-ink-500 leading-relaxed mt-1.5 mb-6">
                                {tab === "signin"
                                    ? "Authenticate to manage containers, networks and volumes."
                                    : "First time? Provision your operator account to take control of the daemon."}
                            </p>

                            <Tabs value={tab} onChange={setTab} />

                            <div className="fade d3" key={tab}>
                                {tab === "signin" ? <SignIn /> : <Register onDone={() => setTab("signin")} />}
                            </div>

                            <div className="mt-6 pt-4 border-t border-dashed border-ink-700 flex justify-end items-center gap-3 text-[11px] text-ink-500">
                                <Link to="/legal" target="_blank" rel="noopener noreferrer" className="text-ink-300 hover:text-accent border-b border-dashed border-ink-700">Privacy Policy</Link>
                                <span className="text-ink-700">·</span>
                                <Link to="/terms" target="_blank" rel="noopener noreferrer" className="text-ink-300 hover:text-accent border-b border-dashed border-ink-700">Terms of Service</Link>
                            </div>
                        </div>
                    </div>

                    <p className="text-center text-[11px] text-ink-600 font-mono mt-6">
                        ChatOps · docker daemon listening
                    </p>
                </div>
            </main>
        </div>
    );
}
