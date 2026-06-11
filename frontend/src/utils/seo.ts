import {
    BROWSE_ROUTE_PATHS,
    UTILITY_NOINDEX_EXACT_PATHS,
    UTILITY_NOINDEX_PREFIXES,
} from '@/data/seo-constants';

const INDEXABLE_QUERY_PARAMS = new Set(['page']);

export const isBotUserAgent = (userAgent: string | null): boolean => {
    if (!userAgent) return false;
    const lower = userAgent.toLowerCase();

    if (/(bot|spider|crawler|preview|snippet|slurp|facebookexternalhit|whatsapp|telegram|discord|skype|vkshare)/i.test(lower)) {
        return true;
    }

    const specificBots = [
        'googlebot', 'bingbot', 'yandexbot', 'duckduckbot', 'baiduspider',
        'sogou', 'exabot', 'facebot', 'twitterbot', 'linkedinbot',
        'embedly', 'quora link preview', 'pinterest', 'slackbot',
        'redditbot', 'applebot', 'ahrefsbot', 'semrushbot', 'mj12bot',
        'dotbot', 'petalbot', 'archive.org_bot', 'googleother',
        'google-extended', 'bingpreview', 'yahoo',
    ];

    return specificBots.some((bot) => lower.includes(bot));
};

const toSearchParams = (search?: string | URLSearchParams) => {
    if (!search) return new URLSearchParams();
    if (search instanceof URLSearchParams) return new URLSearchParams(search);
    return new URLSearchParams(search.startsWith('?') ? search.slice(1) : search);
};

export const normalizeSeoPath = (path: string) => {
    const normalized = (path || '/').replace(/\/$/, '');
    return normalized || '/';
};

export const isProjectSubrouteNoIndex = (path: string) => {
    const normalizedPath = normalizeSeoPath(path);

    if (/^\/(mod|modpack|world)\/[^/]+\/(download|changelog|gallery|edit)$/.test(normalizedPath)) {
        return true;
    }

    return /^\/(mod|modpack|world)\/[^/]+\/wiki(?:\/.*)?$/.test(normalizedPath);
};

export const isUtilityNoIndexPath = (path: string) => {
    const normalizedPath = normalizeSeoPath(path);

    if (UTILITY_NOINDEX_EXACT_PATHS.has(normalizedPath)) {
        return true;
    }

    if (UTILITY_NOINDEX_PREFIXES.some((prefix) => normalizedPath === prefix || normalizedPath.startsWith(`${prefix}/`))) {
        return true;
    }

    return isProjectSubrouteNoIndex(normalizedPath);
};

export const hasNoIndexQueryFilters = (search?: string | URLSearchParams) => {
    const searchParams = toSearchParams(search);
    return Array.from(searchParams.keys()).some((key) => !INDEXABLE_QUERY_PARAMS.has(key));
};

export const getRobotsDirective = (path: string, search?: string | URLSearchParams) => {
    if (isUtilityNoIndexPath(path) || hasNoIndexQueryFilters(search)) {
        return 'noindex,follow';
    }

    return 'index,follow';
};

export const buildCanonicalUrl = (path: string, search?: string | URLSearchParams) => {
    const normalizedPath = normalizeSeoPath(path);
    const searchParams = toSearchParams(search);
    const canonicalParams = new URLSearchParams();

    const pageParam = searchParams.get('page');
    const page = pageParam ? Number.parseInt(pageParam, 10) : 0;
    if (Number.isFinite(page) && page > 0) {
        canonicalParams.set('page', String(page));
    }

    const query = canonicalParams.toString();
    return `https://modtale.net${normalizedPath}${query ? `?${query}` : ''}`;
};

export const isBrowseRoutePath = (path: string) => BROWSE_ROUTE_PATHS.has(normalizeSeoPath(path));
