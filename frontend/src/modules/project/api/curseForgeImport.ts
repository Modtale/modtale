import type { Classification } from '@/data/categories';

const BASE_URL = 'https://nyocf.junyo.dev/api/v1/hytale';
const PROJECT_PATH = /^\/hytale\/(mods|prefabs|worlds|bootstrap|translations)\/([a-z0-9-]+)\/?$/;

export interface CurseForgeImport {
    title: string;
    summary: string;
    about: string;
    classification: Classification;
    sourceUrl: string;
    imageUrl?: string;
    warnings: string[];
}

export function parseCurseForgeReference(input: string): { id?: number; projectClass?: string; slug?: string } {
    const value = input.trim();
    if (/^[1-9]\d*$/.test(value) && Number.isSafeInteger(Number(value))) return { id: Number(value) };
    try {
        const url = new URL(value);
        const match = url.pathname.match(PROJECT_PATH);
        if (url.protocol === 'https:' && ['www.curseforge.com', 'curseforge.com'].includes(url.hostname)
            && !url.username && !url.password && !url.port && match) {
            return { projectClass: match[1], slug: match[2] };
        }
    } catch { /* Report the same actionable error for every invalid reference. */ }
    throw new Error('Enter a Hytale CurseForge project URL or a positive numeric project ID.');
}

async function get(path: string, signal: AbortSignal) {
    const response = await fetch(`${BASE_URL}${path}`, { signal, credentials: 'omit' });
    if (!response.ok) {
        throw new Error(response.status === 404 ? 'That CurseForge project was not found.'
            : response.status === 429 ? 'CurseForge is busy. Please try again shortly.'
                : 'Could not load CurseForge details. Please try again.');
    }
    return response.json();
}

function approvedIcon(value: unknown): string | undefined {
    if (typeof value !== 'string' || value.length > 2048) return undefined;
    return /^https:\/\/(?:[a-zA-Z0-9-]+\.)*forgecdn\.net\/[^\s?#]+$/.test(value) ? value : undefined;
}

export async function loadCurseForgeImport(input: string): Promise<CurseForgeImport> {
    const reference = parseCurseForgeReference(input);
    const signal = AbortSignal.timeout(30000);
    let id = reference.id;
    if (!id) {
        const results = await get(`/${reference.projectClass}/search?q=${encodeURIComponent(reference.slug!)}&limit=50`, signal);
        const match = Array.isArray(results.data)
            ? results.data.find((item: { slug?: string }) => item.slug === reference.slug) : undefined;
        id = match?.id;
    }
    if (!id || !Number.isSafeInteger(id) || id <= 0) {
        throw new Error('That project URL could not be resolved. Try its numeric CurseForge project ID.');
    }
    // Like the launcher, nyoCF resolves all Hytale project classes through this metadata endpoint.
    const project = await get(`/mods/${id}`, signal);
    const sourceUrl = project.links?.website;
    let source;
    try { source = parseCurseForgeReference(sourceUrl ?? ''); } catch { /* Rejected below. */ }
    if (project.id !== id || project.game_id !== 70216 || project.is_available !== true || !source?.slug
        || (reference.slug && (source.slug !== reference.slug || source.projectClass !== reference.projectClass))
        || typeof project.name !== 'string' || !project.name.trim()) {
        throw new Error('Only available Hytale CurseForge projects can be imported.');
    }
    const warnings: string[] = [];
    let about = '';
    try {
        const description = await get(`/mods/${id}/description`, signal);
        if (typeof description.description === 'string') about = description.description;
        else warnings.push('The full description was unavailable. Add it in the editor.');
    } catch {
        warnings.push('The full description could not be loaded. You can add it in the editor.');
    }
    const summary = typeof project.summary === 'string' ? project.summary : '';
    if (project.name.length > 100 || summary.length > 250 || about.length > 50000) {
        warnings.push('Some imported text was shortened to fit Modtale’s limits. Review it before continuing.');
    }
    return {
        title: project.name.slice(0, 100), summary: summary.slice(0, 250), about: about.slice(0, 50000),
        classification: ['prefabs', 'worlds'].includes(source.projectClass!) ? 'SAVE'
            : source.projectClass === 'translations' ? 'DATA' : 'PLUGIN',
        sourceUrl: `https://www.curseforge.com/hytale/${source.projectClass}/${source.slug}`,
        imageUrl: approvedIcon(project.logo?.url) ?? approvedIcon(project.logo?.thumbnail_url),
        warnings,
    };
}
