import { useLocation } from "react-router-dom";
import { resolveDashboardView } from "../components/Topbar.tsx";
import type { DashboardView } from "../components/Topbar.tsx";
import ContainersView from "../components/dashboard/containers/ContainersView.tsx";
import VolumesView from "../components/dashboard/volumes/VolumesView.tsx";
import NetworksView from "../components/dashboard/networks/NetworksView.tsx";

const VIEW_TITLE: Record<DashboardView, string> = {
    containers: "Containers",
    images: "Images",
    networks: "Networks",
    volumes: "Volumes",
};

export default function DashboardPage() {
    const view = resolveDashboardView(useLocation().state);

    return (
        <main className="relative z-10 max-w-[1320px] mx-auto px-6 py-8 h-[calc(100vh-4rem)] flex flex-col overflow-hidden">
            <h1 className="shrink-0 font-sans text-[36px] leading-[1.05] tracking-tight text-ink-50">
                {VIEW_TITLE[view]}
            </h1>

            <div className="flex-1 min-h-0 mt-6">
                {view === "containers" ? (
                    <ContainersView />
                ) : view === "volumes" ? (
                    <VolumesView />
                ) : view === "networks" ? (
                    <NetworksView />
                ) : (
                    <div className="px-5 py-10 text-center font-mono text-[12px] text-ink-500 border border-ink-700 bg-ink-800/40">
                        Coming soon.
                    </div>
                )}
            </div>
        </main>
    );
}
