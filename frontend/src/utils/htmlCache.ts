// Public HTML is bounded even when content purges are delayed or unavailable.
// Short home/browse TTLs keep ranking/stat changes fresh without a 24-hour freeze.
export const HTML_CACHE = {
    listings: { maxAge: 300, staleWhileRevalidate: 300 },
    content: { maxAge: 600, staleWhileRevalidate: 300 },
    shell: { maxAge: 600, staleWhileRevalidate: 300 },
} as const;

export const PUBLIC_SHELL_PATHS = new Set(['/terms', '/privacy', '/api-docs', '/api-docs/swagger', '/launcher']);

export const setPublicHtmlCache = (headers: Headers, policy: { maxAge: number; staleWhileRevalidate: number }) => {
    headers.set('Cache-Control', 'public, max-age=0, must-revalidate');
    // s-maxage implies proxy-revalidate at Cloudflare and disables SWR. Target
    // the CDN separately so browser must-revalidate does not disable edge SWR.
    headers.set('CDN-Cache-Control', `public, max-age=${policy.maxAge}, stale-while-revalidate=${policy.staleWhileRevalidate}, stale-if-error=0`);
};

export const setNoStore = (headers: Headers) => {
    headers.set('Cache-Control', 'private, no-store');
    headers.set('CDN-Cache-Control', 'no-store');
    headers.set('Cloudflare-CDN-Cache-Control', 'no-store');
};

export const isPrivateHtmlRequest = (url: URL, request?: Request) => {
    let path = url.pathname;
    try { path = decodeURIComponent(path); } catch { return true; }
    path = path.replace(/\/+$/, '') || '/';
    if (/^\/(?:login|verify|reset-password|mfa|upload|dashboard|admin|account|settings|lists)(?:\/|$)/i.test(path)
        || /^\/launcher\/auth(?:\/|$)/i.test(path)
        || /^\/(?:project|mod|modpack|world)\/[^/]+\/edit(?:\/|$)/i.test(path)) return true;
    if (Array.from(url.searchParams.keys()).some(key => /(?:token|secret|password|session|auth|code|state|key)/i.test(key))) return true;
    return Boolean(request?.headers.has('authorization'));
};
