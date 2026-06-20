export const MIN_PASSWORD_LENGTH = 8;

export const PASSWORD_STRENGTH_LABELS = ["empty", "weak", "ok", "strong", "excellent"];
export const PASSWORD_STRENGTH_COLORS = ["bg-ink-700", "bg-rose-400", "bg-amber-300", "bg-lime-300", "bg-accent"];

export function passwordStrength(password: string): number {
    let score = 0;
    if (password.length >= MIN_PASSWORD_LENGTH) score++;
    if (/[A-Z]/.test(password)) score++;
    if (/\d/.test(password)) score++;
    if (/[^A-Za-z0-9]/.test(password)) score++;
    return score;
}
