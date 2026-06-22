import { useLocation } from "react-router-dom";
import { resolveDashboardView } from "../components/Topbar.tsx";
import type { DashboardView } from "../components/Topbar.tsx";
import ContainersView from "../components/dashboard/containers/ContainersView.tsx";

const VIEW_TITLE: Record<DashboardView, string> = {
    containers: "Containers",
    images: "Images",
    networks: "Networks",
    volumes: "Volumes",
};

export default function DashboardPage() {
    const view = resolveDashboardView(useLocation().state);

    return (
        <main className="relative z-10 max-w-[1320px] mx-auto px-6 py-8 space-y-6">
            <h1 className="font-sans text-[36px] leading-[1.05] tracking-tight text-ink-50">
                {VIEW_TITLE[view]}
            </h1>

            {view === "containers" ? (
                <ContainersView />
            ) : (
                <div className="px-5 py-10 text-center font-mono text-[12px] text-ink-500 border border-ink-700 bg-ink-800/40">
                    Coming soon.
                </div>
            )}
        </main>
    );
}
