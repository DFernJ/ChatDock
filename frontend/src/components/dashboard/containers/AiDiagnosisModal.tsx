import { useState } from "react";
import { ApiError } from "../../../lib/api.ts";
import { requestAiDiagnosis } from "../../../lib/api/docker.ts";
import type { ContainerDTO } from "../../../types/docker.ts";
import { Modal } from "../../Ui.tsx";

type Step = "consent" | "loading" | "result" | "error";

export default function AiDiagnosisModal({ container, onClose }: {
    container: ContainerDTO;
    onClose: () => void;
}) {
    const [step, setStep] = useState<Step>("consent");
    const [consent, setConsent] = useState(false);
    const [diagnosis, setDiagnosis] = useState("");
    const [generatedAt, setGeneratedAt] = useState<string | null>(null);
    const [cached, setCached] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const runAnalysis = async () => {
        setStep("loading");
        setError(null);
        try {
            const result = await requestAiDiagnosis(container.id);
            setDiagnosis(result.diagnosis);
            setGeneratedAt(result.generatedAt);
            setCached(result.cached);
            setStep("result");
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not complete the AI analysis.");
            setStep("error");
        }
    };

    const download = () => {
        const blob = new Blob([diagnosis], { type: "text/plain" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `${container.name}-ai-diagnosis.txt`;
        a.click();
        URL.revokeObjectURL(url);
    };

    if (step === "result") {
        return (
            <Modal
                title="AI-assisted diagnosis"
                subtitle={
                    (generatedAt ? `Generated ${new Date(generatedAt).toLocaleString()} · ` : "") +
                    (cached ? "Cached result for this failure · " : "") +
                    "AI-generated response — not guaranteed to be accurate."
                }
                path={`containers/${container.name}/ai-diagnosis`}
                onClose={onClose}
                footer={
                    <>
                        <button
                            type="button"
                            onClick={download}
                            className="px-4 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-ink-900"
                        >
                            Download
                        </button>
                        <button
                            type="button"
                            onClick={onClose}
                            className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110"
                        >
                            Close
                        </button>
                    </>
                }
            >
                <pre className="bg-ink-900 border border-ink-700 p-4 font-mono text-[11px] leading-[1.6] text-ink-200 whitespace-pre-wrap break-words max-h-[420px] overflow-y-auto scrollbar">
                    {diagnosis}
                </pre>
            </Modal>
        );
    }

    if (step === "error") {
        return (
            <Modal
                title="AI-assisted diagnosis"
                subtitle={`Analysis failed for "${container.name}".`}
                path={`containers/${container.name}/ai-diagnosis`}
                onClose={onClose}
                footer={
                    <button
                        type="button"
                        onClick={onClose}
                        className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110"
                    >
                        Close
                    </button>
                }
            >
                <p className="text-[12px] text-rose-400 font-mono">{error}</p>
            </Modal>
        );
    }

    const loading = step === "loading";

    return (
        <Modal
            title="AI-assisted diagnosis"
            subtitle={`Analyze recent logs from "${container.name}" with an external AI.`}
            path={`containers/${container.name}/ai-diagnosis`}
            onClose={onClose}
            footer={
                <>
                    <button
                        type="button"
                        onClick={onClose}
                        disabled={loading}
                        className="px-4 py-2 border border-ink-700 text-ink-300 font-mono text-[11px] tracking-[0.18em] uppercase hover:bg-ink-900 disabled:opacity-50"
                    >
                        Cancel
                    </button>
                    <button
                        type="button"
                        onClick={runAnalysis}
                        disabled={!consent || loading}
                        className="px-5 py-2 bg-accent text-ink-900 font-mono text-[11px] font-semibold tracking-[0.18em] uppercase hover:brightness-110 disabled:opacity-60"
                    >
                        {loading ? "Analyzing…" : "Run analysis"}
                    </button>
                </>
            }
        >
            <p className="text-[13px] text-ink-300 leading-relaxed">
                The last portion of this container's logs (up to 10 MB) will be sent to Google's Gemini API for
                analysis. The AI-generated response may be incomplete or inaccurate — always verify its
                conclusions before acting on them.
            </p>
            <label className="flex items-start gap-2.5 mt-4 cursor-pointer">
                <input
                    type="checkbox"
                    checked={consent}
                    onChange={e => setConsent(e.target.checked)}
                    disabled={loading}
                    className="mt-0.5 accent-accent w-4 h-4 shrink-0"
                />
                <span className="text-[12px] text-ink-400 leading-relaxed">
                    I consent to sending this container's logs to an external AI service, and I understand the
                    response is AI-generated and may not always be reliable.
                </span>
            </label>
        </Modal>
    );
}
