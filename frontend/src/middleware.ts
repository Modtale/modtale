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
    "upgrade-insecure-requests",
    "require-trusted-types-for 'script'",
    "trusted-types default",
].join('; ');

export const onRequest: MiddlewareHandler = async ({ url }, next) => {
    const response = await next();
    const contentType = response.headers.get('content-type') || '';

    if (contentType.includes('text/html')) {
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
