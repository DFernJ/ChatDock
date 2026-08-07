import { createContext, useCallback, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import {
    getContainersStats,
    listContainers,
    listImages,
    listNetworks,
    listStacks,
    listVolumes,
} from "../lib/api/docker.ts";
import type {
    ContainerDTO,
    ContainerStatsDTO,
    ImageDTO,
    NetworkDTO,
    StackDTO,
    VolumeDTO,
} from "../types/docker.ts";

interface DockerStateContextValue {
    containers: ContainerDTO[];
    stacksList: StackDTO[];
    stats: Record<string, ContainerStatsDTO>;
    volumes: VolumeDTO[];
    networks: NetworkDTO[];
    images: ImageDTO[];
    loading: boolean;
    refresh: () => Promise<void>;
}

const DockerStateContext = createContext<DockerStateContextValue | null>(null);

function toStatsRecord(list: ContainerStatsDTO[]): Record<string, ContainerStatsDTO> {
    const next: Record<string, ContainerStatsDTO> = {};
    for (const s of list) next[s.containerId] = s;
    return next;
}

// One shared EventSource for the whole dashboard, so switching between the Containers/Volumes/
// Networks/Images tabs doesn't tear down and reopen the SSE connection on every switch.
export function DockerStateProvider({ children }: { children: ReactNode }) {
    const [containers, setContainers] = useState<ContainerDTO[]>([]);
    const [stacksList, setStacksList] = useState<StackDTO[]>([]);
    const [stats, setStats] = useState<Record<string, ContainerStatsDTO>>({});
    const [volumes, setVolumes] = useState<VolumeDTO[]>([]);
    const [networks, setNetworks] = useState<NetworkDTO[]>([]);
    const [images, setImages] = useState<ImageDTO[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const source = new EventSource("/api/docker/state/stream", { withCredentials: true });
        source.addEventListener("containers", (event: MessageEvent) => {
            setContainers(JSON.parse(event.data) as ContainerDTO[]);
            setLoading(false);
        });
        source.addEventListener("stacks", (event: MessageEvent) => {
            setStacksList(JSON.parse(event.data) as StackDTO[]);
        });
        source.addEventListener("stats", (event: MessageEvent) => {
            setStats(toStatsRecord(JSON.parse(event.data) as ContainerStatsDTO[]));
        });
        source.addEventListener("volumes", (event: MessageEvent) => {
            setVolumes(JSON.parse(event.data) as VolumeDTO[]);
        });
        source.addEventListener("networks", (event: MessageEvent) => {
            setNetworks(JSON.parse(event.data) as NetworkDTO[]);
        });
        source.addEventListener("images", (event: MessageEvent) => {
            setImages(JSON.parse(event.data) as ImageDTO[]);
        });
        source.onerror = () => setLoading(false);
        return () => source.close();
    }, []);

    const refresh = useCallback(async () => {
        const [containersList, statsList, stacksResult, volumesList, networksList, imagesList] = await Promise.all([
            listContainers(),
            getContainersStats(),
            listStacks(),
            listVolumes(),
            listNetworks(),
            listImages(),
        ]);
        setContainers(containersList);
        setStacksList(stacksResult);
        setStats(toStatsRecord(statsList));
        setVolumes(volumesList);
        setNetworks(networksList);
        setImages(imagesList);
    }, []);

    return (
        <DockerStateContext.Provider value={{ containers, stacksList, stats, volumes, networks, images, loading, refresh }}>
            {children}
        </DockerStateContext.Provider>
    );
}

export function useDockerState() {
    const ctx = useContext(DockerStateContext);
    if (!ctx) throw new Error("useDockerState must be used within a DockerStateProvider");
    return ctx;
}
