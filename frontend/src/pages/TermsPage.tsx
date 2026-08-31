import { Link } from "react-router-dom";
import { Topbar } from "../components/Topbar.tsx";
import { SectionText } from "../components/Ui.tsx";

interface TocItem {
    id: string;
    label: string;
}

const SECTIONS: TocItem[] = [
    { id: "acceptance", label: "Acceptance" },
    { id: "the-service", label: "The service" },
    { id: "registration", label: "Registration" },
    { id: "acceptable-use", label: "Acceptable use" },
    { id: "linked-accounts", label: "Linked accounts" },
    { id: "your-resources", label: "Your resources" },
    { id: "ai-diagnosis", label: "AI-assisted diagnosis" },
    { id: "availability", label: "Availability" },
    { id: "termination", label: "Termination" },
    { id: "liability", label: "Liability" },
    { id: "governing-law", label: "Governing law" },
    { id: "changes", label: "Changes" },
    { id: "contact", label: "Contact" },
];

export default function TermsPage() {
    return (
        <div className="relative min-h-screen w-full bg-stage bg-grid overflow-hidden">
            <Topbar variant={"no-auth"}/>

            <main className="relative z-10 max-w-[1040px] mx-auto px-6 py-10 sm:py-14">
                <div className="mb-8 fade d1">
                    <h1 className="font-sans text-[34px] leading-[1.05] tracking-tight text-ink-50">Terms of Service</h1>
                    <p className="text-[14px] text-ink-500 leading-relaxed mt-3 max-w-[820px]">
                        The rules that apply to registering and operating containers, stacks and linked accounts on this self-hosted instance.
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
                            <span>chatops://</span><span className="text-accent">ops.local</span><span>/terms</span>
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
                        <SectionText id="acceptance" index="01" title="Acceptance of these terms">
                            <p>
                                By creating an account or otherwise using this ChatOps instance, you agree to these
                                Terms of Service and to the{" "}
                                <Link to="/legal" target="_blank" rel="noopener noreferrer" className="text-accent border-b border-dashed border-accent-line">Privacy Policy</Link>{" "}
                                referenced throughout this document. If you do not agree, do not use the platform.
                            </p>
                        </SectionText>

                        <SectionText id="the-service" index="02" title="Description of the service">
                            <p>
                                ChatOps is a self-hosted, single-node platform for operating Docker resources —
                                containers, images, networks, volumes and stacks — on the host machine it is installed
                                on. It also offers a Discord bot integration and on-demand, AI-assisted diagnosis of
                                container failures. This instance is not a public multi-tenant SaaS product; it is
                                operated privately by its administrator.
                            </p>
                        </SectionText>

                        <SectionText id="registration" index="03" title="Account registration and eligibility">
                            <p>
                                Registration on this instance requires an invitation code issued by an administrator.
                                You must provide a valid email address and set a password; you are responsible for
                                keeping your credentials confidential and for all activity performed under your
                                account.
                            </p>
                        </SectionText>

                        <SectionText id="acceptable-use" index="04" title="Acceptable use">
                            <p>You agree not to:</p>
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>Use the platform to deploy or operate content that is illegal, infringing, or that you do not have the right to run.</li>
                                <li>Attempt to access containers, stacks, secrets, or administrative functions beyond those granted by your assigned permission role.</li>
                                <li>Interfere with the availability or security of the host machine or of other users' resources.</li>
                                <li>Use the platform to attack, scan, or gain unauthorized access to systems outside your own deployments.</li>
                            </ul>
                        </SectionText>

                        <SectionText id="linked-accounts" index="05" title="Linked accounts (Discord and GitHub)">
                            <p>
                                You may link a Discord account to interact with the ChatOps bot, and a GitHub account
                                to import stack definitions from your repositories. You must only link accounts you
                                own or are authorized to use. Linking a GitHub account grants the platform read access
                                to both your public and private repositories associated with that account, solely to
                                let you browse and import stacks; you can unlink it at any time.
                            </p>
                        </SectionText>

                        <SectionText id="your-resources" index="06" title="Your responsibility for deployed resources, secrets and logs">
                            <p>
                                Everything you deploy through this platform — containers, secrets/environment
                                variables, and the logs they produce — runs on a real host machine and is operable
                                and visible to the instance's administrators as part of normal platform operation. You
                                are responsible for the content of what you deploy and log, and must not use the
                                platform's secrets or logging features to store data you are not authorized to hold.
                            </p>
                        </SectionText>

                        <SectionText id="ai-diagnosis" index="07" title="AI-assisted diagnosis">
                            <p>
                                When you choose to request a diagnosis for a failing container, its logs are sent to
                                Google's Gemini API to generate an explanation of the likely cause and a suggested
                                fix, delivered to you via Discord. This analysis only happens when you trigger it —
                                logs are not sent automatically.
                            </p>
                        </SectionText>

                        <SectionText id="availability" index="08" title="Availability">
                            <p>
                                This platform is provided on an "as is" and "as available" basis. As a self-hosted,
                                single-node deployment, no uptime guarantee is made, and the service may be
                                interrupted for maintenance, updates, or causes outside the administrator's control.
                            </p>
                        </SectionText>

                        <SectionText id="termination" index="09" title="Suspension and termination">
                            <p>
                                An administrator may suspend or terminate your account if you violate these terms,
                                misuse the platform, or endanger the security or stability of the host machine. You
                                may request the deletion of your account and associated data at any time, as
                                described in the{" "}
                                <Link to="/legal" target="_blank" rel="noopener noreferrer" className="text-accent border-b border-dashed border-accent-line">Privacy Policy</Link>.
                            </p>
                        </SectionText>

                        <SectionText id="liability" index="10" title="Limitation of liability">
                            <p>
                                To the extent permitted by law, the platform is provided without warranties of any
                                kind, and the operator is not liable for indirect, incidental, or consequential
                                damages arising from your use of the service, including loss of data hosted on your
                                containers or volumes.
                            </p>
                        </SectionText>

                        <SectionText id="governing-law" index="11" title="Governing law">
                            <p>
                                These terms are governed by the laws of Spain, without prejudice to any mandatory
                                consumer or data protection rights you may have under EU law.
                            </p>
                        </SectionText>

                        <SectionText id="changes" index="12" title="Changes to these terms">
                            <p>
                                These terms may be updated as the platform evolves. Material changes will be
                                reflected by updating the "last updated" date above.
                            </p>
                        </SectionText>

                        <SectionText id="contact" index="13" title="Contact">
                            <p>
                                For any question about these terms, open an issue at{" "}
                                <a href="https://github.com/DFernJ/ChatDock/issues" className="text-accent border-b border-dashed border-accent-line">
                                    github.com/DFernJ/ChatDock/issues
                                </a>{" "}or contact the administrator who provisioned your account.
                            </p>
                        </SectionText>
                    </div>
                </div>

                <p className="text-center text-[11px] text-ink-600 font-mono mt-6">
                    See also the <Link to="/legal" target="_blank" rel="noopener noreferrer" className="text-ink-300 hover:text-accent border-b border-dashed border-ink-700">Privacy Policy</Link>
                </p>
            </main>
        </div>
    );
}
