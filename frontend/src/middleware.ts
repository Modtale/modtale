import type { MiddlewareHandler } from 'astro';

const YEAR_IN_SECONDS = 60 * 60 * 24 * 365;

const SECURITY_CSP = [
    "default-src 'self'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    "object-src 'none'",
    "img-src 'self' data: blob: https:",
    "script-src 'self' 'unsafe-inline' https://static.cloudflareinsights.com",
    "style-src 'self' 'unsafe-inline'",
    "font-src 'self' data: https:",
    "connect-src 'self' https:",
    "frame-src https://www.youtube-nocookie.com",
    "upgrade-insecure-requests",
    "require-trusted-types-for 'script'",
    "trusted-types default",
].join('; ');

const isLocalHostname = (hostname: string) => {
    return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '::1';
};

const isDevModtaleHostname = (hostname: string) => hostname === 'dev.modtale.net';

export const onRequest: MiddlewareHandler = async ({ url }, next) => {
    const response = await next();
    const contentType = response.headers.get('content-type') || '';
    const isLocal = isLocalHostname(url.hostname);
    const isDevModtale = isDevModtaleHostname(url.hostname);

    if (isDevModtale) {
        response.headers.set('X-Robots-Tag', 'noindex, nofollow, noarchive, nosnippet, noimageindex');
    }

    if (contentType.includes('text/html')) {
        const policy = response.headers.get('Cache-Control') || 'no-store';
        // Keep the origin's edge policy, but never retain an old release in browsers.
        response.headers.set('CDN-Cache-Control', policy);
        response.headers.set('Cache-Control', /\b(?:private|no-store)\b/i.test(policy)
            ? policy : 'public, max-age=0, must-revalidate');
        response.headers.set('Cache-Tag', `modtale-html-${url.hostname}`);
        if (process.env.MODTALE_DEPLOYMENT_REVISION) {
            response.headers.set('X-Modtale-Revision', process.env.MODTALE_DEPLOYMENT_REVISION);
        }
    }

    if (contentType.includes('text/html') && !isLocal) {
        response.headers.set('Content-Security-Policy', SECURITY_CSP);
        response.headers.set('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
        response.headers.set('Cross-Origin-Opener-Policy', 'same-origin');
        response.headers.set('X-Frame-Options', 'DENY');
        response.headers.set('X-Content-Type-Options', 'nosniff');
        response.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');
    }

    if (url.pathname === '/assets/logo.svg' || url.pathname === '/assets/logo_light.svg') {
        response.headers.set('Cache-Control', `public, max-age=${YEAR_IN_SECONDS}, immutable`);
    }

    return response;
};
