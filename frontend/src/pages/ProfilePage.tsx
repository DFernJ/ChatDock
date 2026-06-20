import { useEffect, useState } from "react";
import type { ChangeEvent, ReactNode, SubmitEvent } from "react";
import { Topbar } from "../components/Topbar.tsx";
import { useAuth } from "../context/AuthContext.tsx";
import { ApiError } from "../lib/api.ts";
import {
    createDiscordLinkCode,
    getProfile,
    githubAuthorizeUrl,
    unlinkDiscord,
    unlinkGithub,
    updatePassword,
    updateUsername,
} from "../lib/api/profile.ts";
import type { ProfileDTO } from "../types/profile.ts";
import { ConfirmDialog, Field, Input, Modal } from "../components/Ui.tsx";
import { MIN_PASSWORD_LENGTH, PASSWORD_STRENGTH_COLORS, PASSWORD_STRENGTH_LABELS, passwordStrength } from "../lib/passwordStrength.ts";

function formatRemaining(ms: number): string {
    const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
    const m = Math.floor(totalSeconds / 60);
    const s = totalSeconds % 60;
    return `${m}:${s.toString().padStart(2, "0")}`;
}

function SettingsCard({ badge, title, subtitle, children }: {
    badge: string; title: string; subtitle: string; children: ReactNode;
}) {
    return (
        <section className="border border-ink-700 bg-ink-800/40 fade d2">
            <header className="px-5 py-4 border-b border-ink-700">
                <div className="flex items-baseline gap-3">
                    <span className="text-[10px] tracking-[0.22em] uppercase px-2 py-0.5 bg-ink-900 border border-ink-700 text-ink-300">
                        {badge}
                    </span>
                    <h2 className="font-sans text-[18px] tracking-tight text-ink-50">{title}</h2>
                </div>
                <p className="text-[11px] text-ink-500 mt-1.5 font-mono">{subtitle}</p>
            </header>
            <div className="p-5">{children}</div>
        </section>
    );
}

function PasswordField({ label, value, onChange, autoComplete, placeholder, strengthScore }: {
    label: string;
    value: string;
    onChange: (e: ChangeEvent<HTMLInputElement>) => void;
    autoComplete: string;
    placeholder?: string;
    strengthScore?: number;
}) {
    const [show, setShow] = useState(false);
    return (
        <Field label={label}>
            <div className="flex items-stretch border border-ink-700 bg-ink-900/60 focus-within:border-accent focus-within:ring-2 focus-within:ring-accent-soft transition">
                <span className="grid place-items-center px-3 text-accent font-mono text-xs border-r border-ink-700 bg-black/20 select-none">
                    ∗
                </span>
                <input
                    type={show ? "text" : "password"}
                    value={value}
                    onChange={onChange}
                    placeholder={placeholder ?? "••••••••••••"}
                    autoComplete={autoComplete}
                    spellCheck={false}
                    className="flex-1 bg-transparent border-0 text-ink-50 font-mono text-[13px] px-3.5 py-3 placeholder:text-ink-600"
                />
                <button
                    type="button"
                    onClick={() => setShow(s => !s)}
                    className="px-3.5 text-[10px] tracking-[0.16em] uppercase text-ink-500 hover:text-ink-100 border-l border-ink-700"
                >
                    {show ? "hide" : "show"}
                </button>
            </div>
            {strengthScore !== undefined && (
                <div className="flex items-center gap-2 mt-1.5">
                    <div className="flex gap-1 flex-1">
                        {[0, 1, 2, 3].map(i => (
                            <span key={i} className={`h-0.75 flex-1 ${i < strengthScore ? PASSWORD_STRENGTH_COLORS[strengthScore] : "bg-ink-700"}`}></span>
                        ))}
                    </div>
                    <span className="text-[10px] uppercase tracking-[0.18em] text-ink-500 w-20 text-right">{PASSWORD_STRENGTH_LABELS[strengthScore]}</span>
                </div>
            )}
        </Field>
    );
}

function LinkedAccountRow({ label, description, linked, linkedUsername, busy, disabled, hint, actionLabel = "Link", onAction, onUnlink }: {
    label: string;
    description: string;
    linked: boolean;
    linkedUsername?: string | null;
    busy?: boolean;
    disabled?: boolean;
    hint?: string;
    actionLabel?: string;
    onAction?: () => void;
    onUnlink?: () => void;
}) {
    return (
        <div className="flex items-center justify-between gap-4 px-5 py-4 border-b border-ink-700/70 last:border-b-0">
            <div>
                <div className="font-mono text-[13px] text-ink-50">{label}</div>
                <div className="text-[12px] text-ink-500 mt-0.5">{description}</div>
            </div>
            {linked ? (
                <div className="flex items-center gap-3">
                    <span className="flex items-center gap-1.5 text-[11px] tracking-[0.16em] uppercase text-accent">
                        <span className="w-1.5 h-1.5 bg-accent"></span>
                        {linkedUsername ? `Linked as ${linkedUsername}` : "Linked account"}
                    </span>
                    <button
                        type="button"
                        onClick={onUnlink}
                        disabled={busy}
                        className="px-3.5 py-2 border border-ink-700 text-rose-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-rose-500/10 hover:border-rose-400/50 transition disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                        Unlink
                    </button>
                </div>
            ) : (
                <div className="flex flex-col items-end gap-1.5">
                    <button
                        type="button"
                        onClick={onAction}
                        disabled={disabled || busy}
                        className="px-4 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 active:translate-y-px transition disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                        {actionLabel}
                    </button>
                    {hint && <span className="text-[10px] text-ink-600">{hint}</span>}
                </div>
            )}
        </div>
    );
}

export default function ProfilePage() {
    const { user, login } = useAuth();

    const [profile, setProfile] = useState<ProfileDTO | null>(null);
    const [profileError, setProfileError] = useState<string | null>(null);

    const [name, setName] = useState("");
    const [nameSaving, setNameSaving] = useState(false);
    const [nameError, setNameError] = useState<string | null>(null);
    const [nameSuccess, setNameSuccess] = useState(false);

    const [currentPassword, setCurrentPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [passwordSaving, setPasswordSaving] = useState(false);
    const [passwordError, setPasswordError] = useState<string | null>(null);
    const [passwordSuccess, setPasswordSuccess] = useState(false);

    const [discordBusy, setDiscordBusy] = useState(false);
    const [discordError, setDiscordError] = useState<string | null>(null);
    const [pairingCode, setPairingCode] = useState<string | null>(null);
    const [pairingExpiresAt, setPairingExpiresAt] = useState<number | null>(null);
    const [showPairingModal, setShowPairingModal] = useState(false);
    const [now, setNow] = useState(() => Date.now());
    const [pendingUnlinkDiscord, setPendingUnlinkDiscord] = useState(false);

    const [githubBusy, setGithubBusy] = useState(false);
    const [githubError, setGithubError] = useState<string | null>(null);
    const [pendingUnlinkGithub, setPendingUnlinkGithub] = useState(false);

    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const result = params.get("github");
        if (!result) return;
        if (result === "error") setGithubError("Could not link the GitHub account.");
        params.delete("github");
        const query = params.toString();
        window.history.replaceState({}, "", window.location.pathname + (query ? `?${query}` : ""));
    }, []);

    useEffect(() => {
        if (!pairingExpiresAt) return;
        const id = window.setInterval(() => setNow(Date.now()), 1000);
        return () => window.clearInterval(id);
    }, [pairingExpiresAt]);

    useEffect(() => {
        if (pairingExpiresAt && now >= pairingExpiresAt) {
            setPairingCode(null);
            setPairingExpiresAt(null);
            setShowPairingModal(false);
        }
    }, [now, pairingExpiresAt]);

    const remainingLabel = pairingExpiresAt ? formatRemaining(pairingExpiresAt - now) : null;

    const refreshProfile = () => {
        getProfile()
            .then(p => { setProfile(p); setName(p.username); })
            .catch(e => setProfileError(e instanceof ApiError ? e.message : "Could not load the profile."));
    };

    useEffect(() => {
        refreshProfile();
    }, []);

    const submitName = async (e: SubmitEvent) => {
        e.preventDefault();
        setNameError(null);
        setNameSuccess(false);
        if (!name.trim()) {
            setNameError("Name cannot be empty.");
            return;
        }
        setNameSaving(true);
        try {
            const updated = await updateUsername(name.trim());
            setProfile(updated);
            if (user) login({ ...user, username: updated.username });
            setNameSuccess(true);
        } catch (e) {
            setNameError(e instanceof ApiError ? e.message : "Could not update the name.");
        } finally {
            setNameSaving(false);
        }
    };

    const submitPassword = async (e: SubmitEvent) => {
        e.preventDefault();
        setPasswordError(null);
        setPasswordSuccess(false);
        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            setPasswordError(`New password must be at least ${MIN_PASSWORD_LENGTH} characters.`);
            return;
        }
        if (newPassword !== confirmPassword) {
            setPasswordError("New passwords do not match.");
            return;
        }
        setPasswordSaving(true);
        try {
            await updatePassword(currentPassword, newPassword);
            setCurrentPassword("");
            setNewPassword("");
            setConfirmPassword("");
            setPasswordSuccess(true);
        } catch (e) {
            setPasswordError(e instanceof ApiError ? e.message : "Could not update the password.");
        } finally {
            setPasswordSaving(false);
        }
    };

    const startDiscordLink = async () => {
        setDiscordError(null);
        setDiscordBusy(true);
        try {
            const { code, expiresAt } = await createDiscordLinkCode();
            setPairingCode(code);
            setPairingExpiresAt(new Date(expiresAt).getTime());
            setShowPairingModal(true);
        } catch (e) {
            setDiscordError(e instanceof ApiError ? e.message : "Could not generate the link code.");
        } finally {
            setDiscordBusy(false);
        }
    };

    const closePairingModal = () => {
        setShowPairingModal(false);
        refreshProfile();
    };

    const confirmUnlinkDiscord = async () => {
        setDiscordBusy(true);
        try {
            await unlinkDiscord();
            setPendingUnlinkDiscord(false);
            refreshProfile();
        } catch (e) {
            setDiscordError(e instanceof ApiError ? e.message : "Could not unlink the Discord account.");
        } finally {
            setDiscordBusy(false);
        }
    };

    const confirmUnlinkGithub = async () => {
        setGithubBusy(true);
        try {
            await unlinkGithub();
            setPendingUnlinkGithub(false);
            refreshProfile();
        } catch (e) {
            setGithubError(e instanceof ApiError ? e.message : "Could not unlink the GitHub account.");
        } finally {
            setGithubBusy(false);
        }
    };

    return (
        <div className="relative min-h-screen w-full bg-stage bg-grid overflow-hidden">
            <Topbar variant={"auth"} />

            <main className="relative z-10 max-w-[720px] mx-auto px-6 py-8 space-y-6">
                <div>
                    <h1 className="font-sans text-[36px] leading-[1.05] tracking-tight text-ink-50">Profile</h1>
                    <p className="text-[14px] text-ink-500 mt-1.5">Manage your account details and linked accounts.</p>
                </div>

                {profileError && <p className="text-[12px] text-rose-400 font-mono">{profileError}</p>}

                <SettingsCard badge="account" title="Account details" subtitle="Name shown across the platform.">
                    <form onSubmit={submitName} className="flex flex-col gap-4">
                        <Field label="Name">
                            <Input value={name} onChange={setName} placeholder="ops-lead" />
                        </Field>
                        {nameError && <p className="text-[12px] text-rose-400 font-mono">{nameError}</p>}
                        {nameSuccess && <p className="text-[12px] text-accent font-mono">Name updated.</p>}
                        <button
                            type="submit"
                            disabled={nameSaving}
                            className="self-start px-5 py-2.5 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 active:translate-y-px transition disabled:opacity-60"
                        >
                            {nameSaving ? "Saving…" : "Save changes"}
                        </button>
                    </form>
                </SettingsCard>

                <SettingsCard badge="security" title="Password" subtitle="Change your account's sign-in password.">
                    <form onSubmit={submitPassword} className="flex flex-col gap-4">
                        <PasswordField
                            label="Current password"
                            value={currentPassword}
                            onChange={(e) => setCurrentPassword(e.target.value)}
                            autoComplete="current-password"
                        />
                        <PasswordField
                            label="New password"
                            value={newPassword}
                            onChange={(e) => setNewPassword(e.target.value)}
                            autoComplete="new-password"
                            placeholder={`min. ${MIN_PASSWORD_LENGTH} characters`}
                            strengthScore={passwordStrength(newPassword)}
                        />
                        <PasswordField
                            label="Confirm new password"
                            value={confirmPassword}
                            onChange={(e) => setConfirmPassword(e.target.value)}
                            autoComplete="new-password"
                        />
                        {passwordError && <p className="text-[12px] text-rose-400 font-mono">{passwordError}</p>}
                        {passwordSuccess && <p className="text-[12px] text-accent font-mono">Password updated.</p>}
                        <button
                            type="submit"
                            disabled={passwordSaving}
                            className="self-start px-5 py-2.5 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 active:translate-y-px transition disabled:opacity-60"
                        >
                            {passwordSaving ? "Saving…" : "Update password"}
                        </button>
                    </form>
                </SettingsCard>

                <SettingsCard badge="links" title="Linked accounts" subtitle="Connect external accounts to your operator.">
                    {discordError && <p className="text-[12px] text-rose-400 font-mono mb-3">{discordError}</p>}
                    {githubError && <p className="text-[12px] text-rose-400 font-mono mb-3">{githubError}</p>}
                    <div className="border border-ink-700 bg-ink-900/30">
                        <LinkedAccountRow
                            label="Discord"
                            description="Manage your containers from the ChatOps bot."
                            linked={profile?.discordLinked ?? false}
                            linkedUsername={profile?.discordUsername}
                            busy={discordBusy}
                            actionLabel={pairingCode ? "Show" : "Link"}
                            hint={pairingCode ? `Expires in ${remainingLabel}` : undefined}
                            onAction={pairingCode ? () => setShowPairingModal(true) : startDiscordLink}
                            onUnlink={() => setPendingUnlinkDiscord(true)}
                        />
                        <LinkedAccountRow
                            label="GitHub"
                            description="Import and deploy repositories directly."
                            linked={profile?.githubLinked ?? false}
                            linkedUsername={profile?.githubUsername}
                            busy={githubBusy}
                            onAction={() => { window.location.href = githubAuthorizeUrl; }}
                            onUnlink={() => setPendingUnlinkGithub(true)}
                        />
                    </div>
                </SettingsCard>
            </main>

            {pairingCode && showPairingModal && (
                <Modal
                    title="Link Discord"
                    subtitle="Enter this code in the ChatOps bot to complete the link."
                    path="profile/discord/link"
                    onClose={closePairingModal}
                    footer={
                        <button
                            type="button"
                            onClick={closePairingModal}
                            className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110"
                        >
                            Done
                        </button>
                    }
                >
                    <div className="flex flex-col gap-3">
                        <div className="border border-ink-700 bg-ink-900/60 px-4 py-3 text-center font-mono text-[20px] tracking-[0.3em] text-accent">
                            {pairingCode}
                        </div>
                        <p className="text-[12px] text-ink-400 font-mono leading-relaxed">
                            Run <span className="text-ink-100">/link {pairingCode}</span> in the Discord bot. The code is single-use and expires in <span className="text-ink-100">{remainingLabel}</span>.
                        </p>
                    </div>
                </Modal>
            )}

            <ConfirmDialog
                open={pendingUnlinkDiscord}
                title="Unlink Discord"
                body="The ChatOps bot will stop recognizing your Discord account until you link it again."
                confirmLabel="Unlink"
                danger
                busy={discordBusy}
                onCancel={() => setPendingUnlinkDiscord(false)}
                onConfirm={confirmUnlinkDiscord}
            />
            <ConfirmDialog
                open={pendingUnlinkGithub}
                title="Unlink GitHub"
                body="You will need to link your GitHub account again to import or deploy repositories from it."
                confirmLabel="Unlink"
                danger
                busy={githubBusy}
                onCancel={() => setPendingUnlinkGithub(false)}
                onConfirm={confirmUnlinkGithub}
            />
        </div>
    )
}
