import { Link } from "react-router-dom";
import { Topbar } from "../components/Topbar.tsx";
import { SectionText } from "../components/Ui.tsx";

interface TocItem {
    id: string;
    label: string;
}

const SECTIONS: TocItem[] = [
    { id: "who-we-are", label: "Who we are" },
    { id: "data-we-collect", label: "Data we collect" },
    { id: "how-we-use-it", label: "How we use it" },
    { id: "github-access", label: "GitHub access" },
    { id: "host-secrets-logs", label: "Host, secrets & logs" },
    { id: "ai-diagnosis", label: "AI-assisted diagnosis" },
    { id: "legal-basis", label: "Legal basis" },
    { id: "retention", label: "Retention" },
    { id: "sharing", label: "Sharing with third parties" },
    { id: "security", label: "Security" },
    { id: "your-rights", label: "Your rights" },
    { id: "cookies-sessions", label: "Cookies & sessions" },
    { id: "changes", label: "Changes" },
    { id: "contact", label: "Contact" },
];

export default function LegalPage() {
    return (
        <div className="relative min-h-screen w-full bg-stage bg-grid overflow-hidden">
            <Topbar variant={"no-auth"}/>

            <main className="relative z-10 max-w-[1040px] mx-auto px-6 py-10 sm:py-14">
                <div className="mb-8 fade d1">
                    <h1 className="font-sans text-[34px] leading-[1.05] tracking-tight text-ink-50">Privacy Policy</h1>
                    <p className="text-[14px] text-ink-500 leading-relaxed mt-3 max-w-[820px]">
                        How ChatOps collects, uses and protects the data of accounts registered on this self-hosted instance.
                    </p>
                    <p className="font-mono text-[11px] text-ink-600 mt-4">Last updated: August 19, 2026</p>
                </div>

                <div className="relative bg-ink-800 border border-ink-700 shadow-card corners fade d2">
                    <div className="flex items-center gap-2.5 px-5 py-3 border-b border-ink-700 bg-ink-900/60 text-[11px] text-ink-500">
                        <div className="flex gap-1.5">
                            <span className="w-2.5 h-2.5 bg-ink-600"></span>
                            <span className="w-2.5 h-2.5 bg-ink-600"></span>
                            <span className="w-2.5 h-2.5 bg-ink-600"></span>
                        </div>
                        <div className="font-mono text-[14px]">
                            <span>chatops://</span><span className="text-accent">ops.local</span><span>/legal</span>
                        </div>
                    </div>

                    <nav className="flex flex-wrap gap-2 px-7 sm:px-8 py-5 border-b border-ink-700 bg-ink-900/40">
                        {SECTIONS.map((s, i) => (
                            <a
                                key={s.id}
                                href={`#${s.id}`}
                                className="text-[11px] font-mono text-ink-400 hover:text-accent border border-ink-700 px-2.5 py-1 hover:border-accent-line transition"
                            >
                                {String(i + 1).padStart(2, "0")} {s.label}
                            </a>
                        ))}
                    </nav>

                    <div className="px-7 sm:px-8">
                        <SectionText id="who-we-are" index="01" title="Who we are">
                            <p>
                                ChatOps is a self-hosted Docker operations platform. This instance is operated by its
                                administrator ("we", "us", "the operator"), under the project maintained at{" "}
                                <a href="https://github.com/DFernJ/ChatDock" className="text-accent border-b border-dashed border-accent-line">
                                    github.com/DFernJ/ChatDock
                                </a>. For any question about this policy or your data, see{" "}
                                <a href="#contact" className="text-accent border-b border-dashed border-accent-line">Contact</a> below.
                            </p>
                        </SectionText>

                        <SectionText id="data-we-collect" index="02" title="Data we collect">
                            <p>We only collect the minimum data needed to operate your account:</p>
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>Email address, used as your account identifier and for account-related communication.</li>
                                <li>Password, stored only as a salted hash — we never store or have access to your plain-text password.</li>
                                <li>Discord user ID, if you link a Discord account, used to connect your ChatOps account with the ChatOps bot.</li>
                                <li>GitHub user ID, if you link a GitHub account, used to authorize repository access for stack imports.</li>
                            </ul>
                            <p>We do not collect names, addresses, payment details, or any analytics/tracking data beyond what is described in this policy.</p>
                        </SectionText>

                        <SectionText id="how-we-use-it" index="03" title="How we use it">
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>Authenticate you and keep you signed in via a session cookie.</li>
                                <li>Enforce your assigned permission role across the platform.</li>
                                <li>Link your Discord ID so the ChatOps bot can associate its interactions with your account.</li>
                                <li>Link your GitHub ID so you can browse and import stack definitions from your repositories.</li>
                                <li>Keep basic operational records (e.g. which account performed an action) for accountability on the host machine you manage.</li>
                            </ul>
                        </SectionText>

                        <SectionText id="github-access" index="04" title="GitHub access">
                            <p>
                                When you link a GitHub account, ChatOps can read both public and private repositories
                                associated with that GitHub ID, in order to let you browse and import Docker Compose /
                                stack definitions from them.
                            </p>
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>We only read repository contents you explicitly open or import through the platform.</li>
                                <li>We do not write to, delete, or modify your repositories.</li>
                                <li>We do not share the contents of your repositories with any third party.</li>
                                <li>You can unlink your GitHub account at any time from your profile; access is revoked immediately.</li>
                            </ul>
                        </SectionText>

                        <SectionText id="host-secrets-logs" index="05" title="Access to the host machine, secrets and logs">
                            <p>
                                ChatOps exists to operate Docker resources — containers, images, networks, volumes and
                                stacks — on the host machine it is installed on. This is a core function of the
                                platform, not incidental data collection, and depending on your permission role it can include:
                            </p>
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>Environment variables and secrets configured for your stacks. Secrets are encrypted at rest, and are only decrypted when needed to run or display a stack you manage.</li>
                                <li>Container logs, which may include any information your applications write to stdout/stderr.</li>
                            </ul>
                            <p>
                                Because this platform is designed to manage a real host machine, any secret or log
                                content you provision through it should be treated as visible to the instance's administrators.
                            </p>
                        </SectionText>

                        <SectionText id="ai-diagnosis" index="06" title="AI-assisted diagnosis">
                            <p>
                                When you choose to request a diagnosis for a failing container, its logs are sent to
                                Google's Gemini API to generate an explanation of the likely cause and a suggested
                                fix, delivered to you via Discord. This analysis is only triggered by your own action —
                                logs are not sent automatically in the background.
                            </p>
                        </SectionText>

                        <SectionText id="legal-basis" index="07" title="Legal basis for processing (GDPR)">
                            <p>We process your data on the following legal bases, as set out in Article 6 of the GDPR:</p>
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li><span className="text-ink-100">Performance of a contract</span> — to create and operate your account and provide the service you request.</li>
                                <li><span className="text-ink-100">Legitimate interest</span> — to secure the platform, prevent abuse, and maintain operational logs of the host machine.</li>
                            </ul>
                        </SectionText>

                        <SectionText id="retention" index="08" title="Data retention">
                            <p>
                                Your account data is kept for as long as your account remains active. If your account
                                is deleted, your email, password hash, and linked Discord/GitHub IDs are permanently
                                removed, except where retention is required by applicable law.
                            </p>
                        </SectionText>

                        <SectionText id="sharing" index="09" title="Sharing with third parties">
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>Discord and GitHub act as identity providers; we only exchange the identifiers needed to link your account, never your ChatOps password.</li>
                                <li>When you request AI-assisted diagnosis, the relevant container logs are transferred to Google's Gemini API (Google LLC, United States) for that single request.</li>
                                <li>We do not sell your data, and we do not share it with third parties for advertising or marketing purposes.</li>
                                <li>Data may be disclosed if required to comply with a legal obligation.</li>
                            </ul>
                        </SectionText>

                        <SectionText id="security" index="10" title="Security measures">
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>Passwords are never stored in plain text; only a salted hash is kept.</li>
                                <li>Secrets are encrypted at rest (AES-GCM) and only decrypted when required to operate your stacks.</li>
                                <li>Access to administrative functions is restricted by role-based permissions.</li>
                            </ul>
                        </SectionText>

                        <SectionText id="your-rights" index="11" title="Your rights (GDPR)">
                            <p>As a data subject under the GDPR, you have the right to:</p>
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>Access the personal data we hold about you.</li>
                                <li>Rectify inaccurate data.</li>
                                <li>Request erasure of your account and associated data.</li>
                                <li>Restrict or object to certain processing.</li>
                                <li>Request portability of your data in a structured format.</li>
                            </ul>
                            <p>To exercise any of these rights, see <a href="#contact" className="text-accent border-b border-dashed border-accent-line">Contact</a> below.</p>
                        </SectionText>

                        <SectionText id="cookies-sessions" index="12" title="Cookies and sessions">
                            <p>
                                ChatOps uses a single session cookie to keep you signed in. This cookie is strictly
                                necessary for the service to function and is not used for tracking or advertising purposes.
                            </p>
                        </SectionText>

                        <SectionText id="changes" index="13" title="Changes to this policy">
                            <p>
                                We may update this policy as the platform evolves. Material changes will be reflected
                                by updating the "last updated" date above.
                            </p>
                        </SectionText>

                        <SectionText id="contact" index="14" title="Contact">
                            <p>
                                For any privacy-related question, open an issue at{" "}
                                <a href="https://github.com/DFernJ/ChatDock/issues" className="text-accent border-b border-dashed border-accent-line">
                                    github.com/DFernJ/ChatDock/issues
                                </a>{" "}or contact the administrator who provisioned your account.
                            </p>
                        </SectionText>
                    </div>
                </div>

                <p className="text-center text-[11px] text-ink-600 font-mono mt-6">
                    See also the <Link to="/terms" target="_blank" rel="noopener noreferrer" className="text-ink-300 hover:text-accent border-b border-dashed border-ink-700">Terms of Service</Link>
                </p>
            </main>
        </div>
    );
}
