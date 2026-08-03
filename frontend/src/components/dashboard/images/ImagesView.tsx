import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../../../context/AuthContext.tsx";
import { canDelete, canEdit } from "../../../lib/permissions.ts";
import { ApiError } from "../../../lib/api.ts";
import { deleteImage, listImages, pruneImages } from "../../../lib/api/docker.ts";
import type { ImageDTO } from "../../../types/docker.ts";
import {
    ActionBtn,
    ConfirmDialog,
    EmptyState,
    ErrorBanner,
    Section,
    Toolbar,
    formatBytes,
} from "../../Ui.tsx";
import PullImageModal from "./PullImageModal.tsx";

const PERMISSION_DELETE_REASON = "You need root permissions to delete images.";

function ImageRow({ image, canRemove, onDelete }: {
    image: ImageDTO;
    canRemove: boolean;
    onDelete: (image: ImageDTO) => void;
}) {
    return (
        <div className="border-b border-ink-700/70 last:border-b-0">
            <div className="grid grid-cols-[minmax(200px,1.6fr)_140px_160px_auto] items-center gap-4 px-5 py-3.5 hover:bg-ink-800/40 transition">
                <div className="flex flex-col min-w-0">
                    <span className="font-mono text-[13px] text-ink-50 truncate">{image.image}</span>
                    <span className="font-mono text-[11px] text-ink-600 truncate">{image.id.slice(0, 12)}</span>
                </div>

                <div className="flex flex-col gap-0.5">
                    <span className="font-mono text-[11px] tracking-[0.16em] uppercase text-ink-300">{formatBytes(image.diskUsage)}</span>
                    <span className="font-mono text-[10px] text-ink-600">size</span>
                </div>

                <div className="flex flex-col gap-0.5">
                    <span className="font-mono text-[11px] text-ink-300">
                        {image.usedInContainers} container{image.usedInContainers === 1 ? "" : "s"}
                    </span>
                    <span className="font-mono text-[10px] text-ink-600">in use</span>
                </div>

                <div className="flex items-center gap-1.5 justify-end">
                    <ActionBtn
                        icon="trash"
                        label="Delete"
                        variant="danger"
                        disabled={!canRemove}
                        disabledReason={!canRemove ? PERMISSION_DELETE_REASON : undefined}
                        onClick={() => onDelete(image)}
                    />
                </div>
            </div>
        </div>
    );
}

export default function ImagesView() {
    const { user } = useAuth();
    const canMutate = canEdit(user?.permissionRole);
    const canRemove = canDelete(user?.permissionRole);

    const [images, setImages] = useState<ImageDTO[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [search, setSearch] = useState("");
    const [pending, setPending] = useState<ImageDTO | null>(null);
    const [busy, setBusy] = useState(false);
    const [showPull, setShowPull] = useState(false);
    const [pendingPrune, setPendingPrune] = useState(false);

    const refresh = useCallback(async () => {
        setError(null);
        try {
            setImages(await listImages());
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not load images");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        setLoading(true);
        const source = new EventSource("/api/docker/state/stream", { withCredentials: true });
        source.addEventListener("images", (event: MessageEvent) => {
            setImages(JSON.parse(event.data) as ImageDTO[]);
            setLoading(false);
        });
        source.onerror = () => setLoading(false);
        return () => source.close();
    }, []);

    const filtered = useMemo(() => {
        const q = search.trim().toLowerCase();
        const list = q ? images.filter(i => i.image.toLowerCase().includes(q)) : images;
        return [...list].sort((a, b) => a.image.localeCompare(b.image));
    }, [images, search]);

    const confirmDelete = async () => {
        if (!pending) return;
        setBusy(true);
        setError(null);
        try {
            await deleteImage(pending.id);
            setPending(null);
            await refresh();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not delete the image");
        } finally {
            setBusy(false);
        }
    };

    const confirmPrune = async () => {
        setBusy(true);
        setError(null);
        try {
            await pruneImages();
            setPendingPrune(false);
            await refresh();
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "Could not prune images");
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="h-full flex flex-col gap-6 min-h-0">
            <Toolbar
                search={search}
                onSearchChange={setSearch}
                onRefresh={refresh}
                refreshing={loading}
                searchPlaceholder="Search image name…"
                primaryLabel="Pull image"
                onPrimary={() => setShowPull(true)}
                primaryDisabled={!canMutate}
                secondaryLabel="Prune unused"
                onSecondary={() => setPendingPrune(true)}
                secondaryDisabled={!canRemove}
            />

            {error && <ErrorBanner message={error} />}

            <Section
                title="Images"
                subtitle="Images stored by the Docker daemon on this host."
                badge="image"
                count={filtered.length}
                className="flex-1 min-h-0"
            >
                {loading && images.length === 0 ? (
                    <div className="px-5 py-10 text-center font-mono text-[12px] text-ink-500">loading…</div>
                ) : filtered.length === 0 ? (
                    <EmptyState title="No images found" message="Click Pull image to download your first one." />
                ) : (
                    filtered.map(i => (
                        <ImageRow
                            key={i.id}
                            image={i}
                            canRemove={canRemove}
                            onDelete={setPending}
                        />
                    ))
                )}
            </Section>

            <ConfirmDialog
                open={!!pending}
                title="Delete image"
                body={
                    pending
                        ? pending.usedInContainers > 0
                            ? `"${pending.image}" is currently used by ${pending.usedInContainers} container(s). Remove those containers first, or this deletion will fail.`
                            : `"${pending.image}" will be permanently deleted. This can't be undone.`
                        : ""
                }
                confirmLabel="Delete"
                danger
                busy={busy}
                onCancel={() => setPending(null)}
                onConfirm={confirmDelete}
            />

            <ConfirmDialog
                open={pendingPrune}
                title="Prune unused images"
                body="All images not used by at least one container will be permanently removed. This can't be undone."
                confirmLabel="Prune"
                danger
                busy={busy}
                onCancel={() => setPendingPrune(false)}
                onConfirm={confirmPrune}
            />

            {showPull && (
                <PullImageModal
                    onClose={() => setShowPull(false)}
                    onPulled={() => { setShowPull(false); refresh(); }}
                />
            )}
        </div>
    );
}
