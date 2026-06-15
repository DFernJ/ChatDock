export class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
        super(message);
        this.status = status;
    }
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const res = await fetch(`${path}`, { credentials: "include", ...init });
    if (!res.ok) {
        const text = await res.text().catch(() => "");
        throw new ApiError(res.status, text || `Fail petition (${res.status})`);
    }
    const contentType = res.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
        return (await res.json()) as T;
    }
    return undefined as T;
}

export function searchParams(params: Record<string, string | number | boolean | undefined>): string {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
        if (value !== undefined) search.set(key, String(value));
    }
    const s = search.toString();
    return s ? `?${s}` : "";
}