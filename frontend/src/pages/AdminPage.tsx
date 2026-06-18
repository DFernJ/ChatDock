import { useCallback, useEffect, useState } from "react";
import type { ChangeEvent, ReactNode, SubmitEvent } from "react";
import { Topbar } from "../components/Topbar.tsx";
import { ApiError } from "../lib/api.ts";
import {
    createUser,
    deleteCode,
    deleteUser,
    generateCode,
    listCodes,
    listUsers,
    setUserAuthRole,
    setUserEnabled,
    setUserPermissionRole,
} from "../lib/api/admin.ts";
import { useAuth } from "../context/AuthContext.tsx";
import { canDelete } from "../lib/permissions.ts";
import type { AuthRole, PermissionRole } from "../types/auth.ts";
import type { AdminUserDTO, CodeDTO, CodeType } from "../types/admin.ts";
import {
    ActionBtn,
    ConfirmDialog,
    EmptyState,
    ErrorBanner,
    Field,
    Icon,
    Modal,
    Section,
} from "../components/Ui.tsx";

function Select({ value, onChange, options }: {
    value: string;
    onChange: (v: string) => void;
    options: { value: string; label: string }[];
}) {
    return (
        <div className="flex items-stretch border border-ink-700 bg-ink-900/60 focus-within:border-accent">
            <select
                value={value}
                onChange={(e: ChangeEvent<HTMLSelectElement>) => onChange(e.target.value)}
                className="flex-1 bg-transparent border-0 text-ink-50 font-mono text-[13px] tracking-[0.1em] uppercase px-3.5 py-3 outline-none"
            >
                {options.map(o => (
                    <option key={o.value} value={o.value} className="bg-ink-800 uppercase">{o.label}</option>
                ))}
            </select>
        </div>
    );
}

function StaticBox({ children, className = "" }: { children: ReactNode; className?: string }) {
    return (
        <div className="flex items-stretch border border-ink-700 bg-ink-900/60">
            <span className={`flex-1 font-mono text-[13px] px-3.5 py-3 ${className}`}>{children}</span>
        </div>
    );
}

function Toggle({ checked, onChange, disabled }: { checked: boolean; onChange: () => void; disabled?: boolean }) {
    return (
        <button
            type="button"
            role="switch"
            aria-checked={checked}
            onClick={onChange}
            disabled={disabled}
            className={`relative w-14 h-7 border transition disabled:opacity-40 disabled:cursor-not-allowed ${
                checked ? "bg-accent border-accent-line" : "bg-ink-900/60 border-ink-700"
            }`}
        >
            <span
                className={`absolute top-0.5 left-0.5 w-5 h-5 transition-transform ${
                    checked ? "translate-x-7 bg-ink-900" : "translate-x-0 bg-ink-500"
                }`}
            ></span>
        </button>
    );
}

function formatDate(iso: string): string {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? "—" : d.toLocaleDateString();
}

const CODE_TYPE_META: Record<CodeType, string> = {
    register: "text-accent",
    discord: "text-sky-300",
};

function CodeRow({ c, onDelete, canMutate }: { c: CodeDTO; onDelete: (c: CodeDTO) => void; canMutate: boolean }) {
    return (
        <div className="grid grid-cols-[1.6fr_1fr_1fr_auto_auto] items-center gap-6 px-5 py-3.5 border-b border-ink-700/70 hover:bg-ink-800/40 transition">
            <span className="font-mono text-[13px] text-ink-50 tracking-[0.1em]">{c.code}</span>
            <StaticBox className={`tracking-[0.16em] uppercase ${CODE_TYPE_META[c.codeType]}`}>{c.codeType}</StaticBox>
            <StaticBox className="text-ink-300">{c.remainUses} uses</StaticBox>
            <span className="font-mono text-[12px] text-ink-500">{formatDate(c.createdAt)}</span>
            <ActionBtn icon="trash" label="Revoke code" variant="danger" disabled={!canMutate} onClick={() => onDelete(c)} />
        </div>
    );
}

function UserRow({ u, selfId, canMutate, onToggleEnabled, onChangeAuthRole, onChangePermissionRole, onDelete }: {
    u: AdminUserDTO;
    selfId?: string;
    canMutate: boolean;
    onToggleEnabled: (u: AdminUserDTO) => void;
    onChangeAuthRole: (u: AdminUserDTO, role: AuthRole) => void;
    onChangePermissionRole: (u: AdminUserDTO, role: PermissionRole) => void;
    onDelete: (u: AdminUserDTO) => void;
}) {
    const isSelf = u.id === selfId;
    return (
        <div className="grid grid-cols-[1.6fr_1fr_1fr_auto_auto] items-center gap-6 px-5 py-3.5 border-b border-ink-700/70 hover:bg-ink-800/40 transition">
            <div className="flex flex-col min-w-0">
                <span className="font-mono text-[13px] text-ink-50 truncate">{u.username}</span>
                <span className="font-mono text-[11px] text-ink-500 truncate">{u.email}</span>
            </div>
            <Select
                value={u.permissionRole}
                onChange={(v) => onChangePermissionRole(u, v as PermissionRole)}
                options={[
                    { value: "viewer", label: "viewer" },
                    { value: "editor", label: "editor" },
                    { value: "root", label: "root" },
                ]}
            />
            <Select
                value={u.authRole}
                onChange={(v) => onChangeAuthRole(u, v as AuthRole)}
                options={[
                    { value: "user", label: "user" },
                    { value: "admin", label: "admin" },
                ]}
            />
            <Toggle checked={u.enabled} disabled={!canMutate || isSelf} onChange={() => onToggleEnabled(u)} />
            <ActionBtn icon="trash" label="Delete user" variant="danger" disabled={!canMutate || isSelf} onClick={() => onDelete(u)} />
        </div>
    );
}

const MIN_PASSWORD_LENGTH = 8;

function NewUserModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
    const [username, setUsername] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [authRole, setAuthRole] = useState<AuthRole>("user");
    const [permissionRole, setPermissionRole] = useState<PermissionRole>("viewer");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const score: number = (() => {
        let s = 0;
        if (password.length >= MIN_PASSWORD_LENGTH) s++;
        if (/[A-Z]/.test(password)) s++;
        if (/\d/.test(password)) s++;
        if (/[^A-Za-z0-9]/.test(password)) s++;
        return s;
    })();
    const strengthLabels = ["empty", "weak", "ok", "strong", "excellent"];
    const strengthColors = ["bg-ink-700", "bg-rose-400", "bg-amber-300", "bg-lime-300", "bg-accent"];

    const submit = async (e: SubmitEvent) => {
        e.preventDefault();
        if (password.length < MIN_PASSWORD_LENGTH) {
            setError(`Password must be at least ${MIN_PASSWORD_LENGTH} characters`);
            return;
        }
        setBusy(true);
        setError(null);
        try {
            await createUser({ username, email, password, authRole, permissionRole });
            onCreated();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not create the operator");
        } finally {
            setBusy(false);
        }
    };

    return (
        <Modal
            title="New operator"
            subtitle="Manually provision an operator account."
            path="admin/users/new"
            onClose={onClose}
            footer={
                <>
                    <button type="button" onClick={onClose} disabled={busy} className="px-4 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-ink-900 disabled:opacity-50">
                        Cancel
                    </button>
                    <button type="submit" form="new-user-form" disabled={busy} className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 disabled:opacity-60">
                        {busy ? "Creating…" : "Create operator"}
                    </button>
                </>
            }
        >
            <form id="new-user-form" onSubmit={submit} className="flex flex-col gap-4">
                <Field label="Operator name">
                    <input
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        placeholder="ops-lead"
                        spellCheck={false}
                        className="flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600 focus:border-accent"
                    />
                </Field>
                <Field label="Email">
                    <input
                        type="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        placeholder="you@chat.ops"
                        spellCheck={false}
                        className="flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600 focus:border-accent"
                    />
                </Field>
                <Field label="Password" hint={`min. ${MIN_PASSWORD_LENGTH} characters`}>
                    <input
                        type="password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        placeholder="••••••••••••"
                        className="flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600 focus:border-accent"
                    />
                    <div className="flex items-center gap-2 mt-1.5">
                        <div className="flex gap-1 flex-1">
                            {[0, 1, 2, 3].map(i => (
                                <span key={i} className={`h-0.75 flex-1 ${i < score ? strengthColors[score] : "bg-ink-700"}`}></span>
                            ))}
                        </div>
                        <span className="text-[10px] uppercase tracking-[0.18em] text-ink-500 w-20 text-right">{strengthLabels[score]}</span>
                    </div>
                </Field>
                <div className="grid grid-cols-2 gap-3">
                    <Field label="Role">
                        <Select value={authRole} onChange={(v) => setAuthRole(v as AuthRole)} options={[{ value: "user", label: "user" }, { value: "admin", label: "admin" }]} />
                    </Field>
                    <Field label="Permissions">
                        <Select
                            value={permissionRole}
                            onChange={(v) => setPermissionRole(v as PermissionRole)}
                            options={[{ value: "viewer", label: "viewer" }, { value: "editor", label: "editor" }, { value: "root", label: "root" }]}
                        />
                    </Field>
                </div>
                {error && <p className="text-[12px] text-rose-400 font-mono">{error}</p>}
            </form>
        </Modal>
    );
}

function GenerateCodeModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
    const [codeType, setCodeType] = useState<CodeType>("register");
    const [uses, setUses] = useState(1);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const submit = async (e: SubmitEvent) => {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
            await generateCode({ codeType, uses });
            onCreated();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not generate the code");
        } finally {
            setBusy(false);
        }
    };

    return (
        <Modal
            title="Generate registration code"
            subtitle="The code can be used to onboard new operators."
            path="admin/codes/new"
            onClose={onClose}
            footer={
                <>
                    <button type="button" onClick={onClose} disabled={busy} className="px-4 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-ink-900 disabled:opacity-50">
                        Cancel
                    </button>
                    <button type="submit" form="new-code-form" disabled={busy} className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 disabled:opacity-60">
                        {busy ? "Generating…" : "Generate code"}
                    </button>
                </>
            }
        >
            <form id="new-code-form" onSubmit={submit} className="flex flex-col gap-4">
                <Field label="Type">
                    <Select
                        value={codeType}
                        onChange={(v) => setCodeType(v as CodeType)}
                        options={[{ value: "register", label: "register" }, { value: "discord", label: "discord" }]}
                    />
                </Field>
                <Field label="Uses">
                    <input
                        type="number"
                        min={1}
                        value={uses}
                        onChange={(e) => setUses(Math.max(1, Number(e.target.value)))}
                        className="flex-1 w-full bg-ink-900/60 border border-ink-700 text-ink-50 font-mono text-[13px] px-3.5 py-3 focus:border-accent"
                    />
                </Field>
                {error && <p className="text-[12px] text-rose-400 font-mono">{error}</p>}
            </form>
        </Modal>
    );
}

export default function AdminPage() {
    const { user } = useAuth();

    const [users, setUsers] = useState<AdminUserDTO[]>([]);
    const [codes, setCodes] = useState<CodeDTO[]>([]);
    const [usersLoading, setUsersLoading] = useState(false);
    const [codesLoading, setCodesLoading] = useState(false);
    const [usersError, setUsersError] = useState<string | null>(null);
    const [codesError, setCodesError] = useState<string | null>(null);

    const [showNewUser, setShowNewUser] = useState(false);
    const [showNewCode, setShowNewCode] = useState(false);
    const [pendingCode, setPendingCode] = useState<CodeDTO | null>(null);
    const [pendingDeleteUser, setPendingDeleteUser] = useState<AdminUserDTO | null>(null);
    const [busy, setBusy] = useState(false);

    const refreshUsers = useCallback(async () => {
        setUsersLoading(true);
        setUsersError(null);
        try {
            setUsers(await listUsers());
        } catch (e) {
            setUsersError(e instanceof ApiError ? e.message : "Could not load users");
        } finally {
            setUsersLoading(false);
        }
    }, []);

    const refreshCodes = useCallback(async () => {
        setCodesLoading(true);
        setCodesError(null);
        try {
            setCodes(await listCodes());
        } catch (e) {
            setCodesError(e instanceof ApiError ? e.message : "Could not load codes");
        } finally {
            setCodesLoading(false);
        }
    }, []);

    useEffect(() => {
        refreshUsers();
        refreshCodes();
    }, [refreshUsers, refreshCodes]);

    const toggleEnabled = async (u: AdminUserDTO) => {
        setUsersError(null);
        try {
            await setUserEnabled(u.id, !u.enabled);
            refreshUsers();
        } catch (e) {
            setUsersError(e instanceof ApiError ? e.message : "Could not update the user");
        }
    };

    const changeAuthRole = async (u: AdminUserDTO, role: AuthRole) => {
        setUsersError(null);
        try {
            await setUserAuthRole(u.id, role);
            refreshUsers();
        } catch (e) {
            setUsersError(e instanceof ApiError ? e.message : "Could not update the user");
        }
    };

    const changePermissionRole = async (u: AdminUserDTO, role: PermissionRole) => {
        setUsersError(null);
        try {
            await setUserPermissionRole(u.id, role);
            refreshUsers();
        } catch (e) {
            setUsersError(e instanceof ApiError ? e.message : "Could not update the user");
        }
    };

    const confirmDeleteUser = async () => {
        if (!pendingDeleteUser) return;
        setBusy(true);
        setUsersError(null);
        try {
            await deleteUser(pendingDeleteUser.id);
            setPendingDeleteUser(null);
            refreshUsers();
        } catch (e) {
            setUsersError(e instanceof ApiError ? e.message : "Could not delete the user");
        } finally {
            setBusy(false);
        }
    };

    const confirmDeleteCode = async () => {
        if (!pendingCode) return;
        setBusy(true);
        setCodesError(null);
        try {
            await deleteCode(pendingCode.id);
            setPendingCode(null);
            refreshCodes();
        } catch (e) {
            setCodesError(e instanceof ApiError ? e.message : "Could not revoke the code");
        } finally {
            setBusy(false);
        }
    };

    const canMutate = canDelete(user?.permissionRole);

    return (
        <div className="relative min-h-screen w-full bg-stage bg-grid overflow-hidden">
            <Topbar variant="auth" activeView="adminPanel" />

            <main className="relative z-10 max-w-[1320px] mx-auto px-6 py-8 space-y-6">
                <div className="flex flex-wrap items-end justify-between gap-4">
                    <div>
                        <h1 className="font-sans text-[36px] leading-[1.05] tracking-tight text-ink-50">Admin panel</h1>
                    </div>
                    <div className="flex items-center gap-3">
                        <button
                            type="button"
                            onClick={() => setShowNewCode(true)}
                            disabled={!canMutate}
                            className="flex items-center gap-2.5 border border-ink-700 text-ink-100 font-mono text-[11px] tracking-[0.18em] uppercase px-4 py-2.5 hover:bg-ink-800 hover:border-ink-600 transition disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                            <Icon name="plus" className="w-3.5 h-3.5" />
                            Generate code
                        </button>
                        <button
                            type="button"
                            onClick={() => setShowNewUser(true)}
                            disabled={!canMutate}
                            className="flex items-center gap-2.5 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase px-4 py-2.5 hover:brightness-110 active:translate-y-px transition disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                            <Icon name="plus" className="w-3.5 h-3.5" />
                            New user
                        </button>
                    </div>
                </div>

                <Section title="Registration codes" subtitle="Invitation codes for new signups." badge="codes" count={codes.length}>
                    {codesError && <ErrorBanner message={codesError} />}
                    {codesLoading && codes.length === 0 ? (
                        <div className="px-5 py-10 text-center font-mono text-[12px] text-ink-500">loading…</div>
                    ) : codes.length === 0 ? (
                        <EmptyState title="No codes" message="No registration codes have been generated yet." />
                    ) : (
                        <div className="max-h-[420px] overflow-y-scroll overflow-x-hidden scrollbar">
                            {codes.map(c => <CodeRow key={c.id} c={c} canMutate={canMutate} onDelete={setPendingCode} />)}
                        </div>
                    )}
                </Section>

                <Section title="Users" subtitle="Registered accounts and their permissions." badge="users" count={users.length}>
                    {usersError && <ErrorBanner message={usersError} />}
                    {usersLoading && users.length === 0 ? (
                        <div className="px-5 py-10 text-center font-mono text-[12px] text-ink-500">loading…</div>
                    ) : users.length === 0 ? (
                        <EmptyState title="No users" message="No operators registered." />
                    ) : (
                        <div className="max-h-[420px] overflow-y-scroll overflow-x-hidden scrollbar">
                            {users.map(u => (
                                <UserRow
                                    key={u.id}
                                    u={u}
                                    selfId={user?.id}
                                    canMutate={canMutate}
                                    onToggleEnabled={toggleEnabled}
                                    onChangeAuthRole={changeAuthRole}
                                    onChangePermissionRole={changePermissionRole}
                                    onDelete={setPendingDeleteUser}
                                />
                            ))}
                        </div>
                    )}
                </Section>
            </main>

            {showNewUser && (
                <NewUserModal onClose={() => setShowNewUser(false)} onCreated={() => { setShowNewUser(false); refreshUsers(); }} />
            )}
            {showNewCode && (
                <GenerateCodeModal onClose={() => setShowNewCode(false)} onCreated={() => { setShowNewCode(false); refreshCodes(); }} />
            )}

            <ConfirmDialog
                open={!!pendingCode}
                title="Revoke code"
                body="The code will no longer be usable to onboard new operators."
                confirmLabel="Revoke"
                danger
                busy={busy}
                onCancel={() => setPendingCode(null)}
                onConfirm={confirmDeleteCode}
            />
            <ConfirmDialog
                open={!!pendingDeleteUser}
                title="Delete user"
                body={`The account "${pendingDeleteUser?.username ?? ""}" will be deleted. This action cannot be undone.`}
                confirmLabel="Delete"
                danger
                busy={busy}
                onCancel={() => setPendingDeleteUser(null)}
                onConfirm={confirmDeleteUser}
            />
        </div>
    );
}
