import { describe, expect, it } from 'vitest';
import { HTML_CACHE, PUBLIC_SHELL_PATHS, isPrivateHtmlRequest, setNoStore, setPublicHtmlCache } from '@/utils/htmlCache';

describe('public HTML cache policy', () => {
    it.each(Object.entries(HTML_CACHE))('separates browser revalidation from bounded CDN SWR for %s', (_, policy) => {
        const headers = new Headers();
        setPublicHtmlCache(headers, policy);
        expect(headers.get('Cache-Control')).toBe('public, max-age=0, must-revalidate');
        const edge = headers.get('CDN-Cache-Control')!;
        expect(edge).toContain(`max-age=${policy.maxAge}`);
        expect(edge).toContain(`stale-while-revalidate=${policy.staleWhileRevalidate}`);
        expect(edge).toContain('stale-if-error=0');
        expect(edge).not.toMatch(/s-maxage|must-revalidate|proxy-revalidate|no-cache/);
        expect(policy.maxAge + policy.staleWhileRevalidate).toBeLessThanOrEqual(900);
    });
    it('removes all cache permissions for private or degraded HTML', () => {
        const headers = new Headers();
        setPublicHtmlCache(headers, HTML_CACHE.content);
        headers.set('Cloudflare-CDN-Cache-Control', 'public, max-age=86400');
        setNoStore(headers);
        expect(headers.get('Cache-Control')).toBe('private, no-store');
        expect(headers.get('CDN-Cache-Control')).toBe('no-store');
        expect(headers.get('Cloudflare-CDN-Cache-Control')).toBe('no-store');
    });
    it.each(['/login', '/verify', '/reset-password', '/mfa', '/upload', '/dashboard/profile', '/admin/news',
        '/account', '/settings', '/launcher/auth', '/lists/shared-id', '/mod/demo/edit', '/project/demo/edit/',
        '/%64ashboard/profile', '/mod/demo/%65dit'])('blocks private route %s', path => {
        expect(isPrivateHtmlRequest(new URL(path, 'https://modtale.net'))).toBe(true);
    });
    it.each(['token', 'access_token', 'code', 'state', 'password', 'apiKey', 'session'])('blocks authentication query %s', key => {
        expect(isPrivateHtmlRequest(new URL(`/?${key}=private`, 'https://modtale.net'))).toBe(true);
    });
    it('blocks authenticated authorization headers even on a public path', () => {
        const request = new Request('https://modtale.net/', { headers: { Authorization: 'Bearer test' } });
        expect(isPrivateHtmlRequest(new URL(request.url), request)).toBe(true);
    });
    it.each(['/', '/mods?q=sky', '/creator/ada', '/mod/demo/changelog', '/mod/demo/wiki/install', '/news/demo', '/launcher'])('allows public path %s', path => {
        expect(isPrivateHtmlRequest(new URL(path, 'https://modtale.net'))).toBe(false);
    });
    it('uses an explicit narrow shell allowlist', () => {
        expect([...PUBLIC_SHELL_PATHS]).toEqual(['/terms', '/privacy', '/api-docs', '/api-docs/swagger', '/launcher']);
    });
});
