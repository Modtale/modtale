/** Accept a slug or a pasted jam URL without keeping a host/query in the slug. */
export function normalizeJamSlug(value: string): string {
    let slug = value.trim();
    if (/^(https?:\/\/|\/jam\/|[^/]+\/jam\/)/i.test(slug)) {
        const match = slug.match(/\/jam\/([^/?#]*)/i);
        if (match) slug = match[1];
    }
    return slug.toLowerCase().replace(/[^a-z0-9-]+/g, '-').replace(/-{2,}/g, '-');
}

export function jamSlugFromTitle(title: string): string {
    return normalizeJamSlug(title).replace(/^-+|-+$/g, '').slice(0, 50).replace(/-+$/g, '');
}

export function validateJamSlug(value: string): string | null {
    if (!value) return 'A jam URL is required.';
    if (!/^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$/.test(value)) {
        return 'Use 3–50 lowercase letters, numbers or dashes, with no leading or trailing dash.';
    }
    return null;
}
