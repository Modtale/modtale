import { describe, expect, it } from 'vitest';
import { onRequest } from '@/middleware';
import { HTML_CACHE, setPublicHtmlCache } from '@/utils/htmlCache';

describe('security middleware', () => {
    it('allows privacy-enhanced YouTube embeds in the production CSP', async () => {
        const response = await (onRequest as any)(
            { url: new URL('https://modtale.net/project/example') },
            async () => new Response('<html></html>', {
                headers: { 'content-type': 'text/html; charset=utf-8' }
            })
        );

        expect(response.headers.get('Content-Security-Policy')).toContain(
            'frame-src https://www.youtube-nocookie.com'
        );
    });
});

describe('deployment caching', () => {
    const run = (policy: string, type = 'text/html', options: { url?: string; status?: number; headers?: HeadersInit; edge?: string; requestHeaders?: HeadersInit } = {}) => {
        const url = new URL(options.url || 'https://modtale.net/');
        const headers = new Headers({ 'content-type': type, 'cache-control': policy });
        if (options.edge) headers.set('CDN-Cache-Control', options.edge);
        new Headers(options.headers).forEach((value, key) => headers.set(key, value));
        return (onRequest as any)(
            { url, request: new Request(url, { headers: options.requestHeaders }) },
            async () => new Response('content', { status: options.status || 200, headers })
        );
    };
    it('keeps CDN max-age/SWR separate from browser revalidation', async () => {
        const headers = new Headers();
        setPublicHtmlCache(headers, HTML_CACHE.listings);
        const edge = headers.get('CDN-Cache-Control')!;
        const response = await run(headers.get('Cache-Control')!, 'text/html', { edge });
        expect(response.headers.get('CDN-Cache-Control')).toBe(edge);
        expect(response.headers.get('Cache-Control')).toBe('public, max-age=0, must-revalidate');
        expect(response.headers.get('Cache-Tag')).toBe('modtale-html-modtale.net');
    });
    it.each(['private, no-store', 'no-store', 'public, max-age=0, s-maxage=0, must-revalidate', 'public, max-age=300, s-maxage=86400, stale-while-revalidate=86400'])(
        'requires explicit CDN opt-in instead of storing browser policy: %s', async policy => {
            const response = await run(policy);
            expect(response.headers.get('CDN-Cache-Control')).toBe('no-store');
            expect(response.headers.get('Cache-Control')).toBe('private, no-store');
        }
    );
    it.each([301, 302, 404, 410, 500, 503])('keeps status %s out of shared caches even with public opt-in', async status => {
        const response = await run('public, max-age=0', 'text/html', { status, edge: 'public, max-age=600' });
        expect(response.headers.get('CDN-Cache-Control')).toBe('no-store');
    });
    it.each(['/dashboard', '/login?redirect=/dashboard', '/mod/demo/edit', '/launcher/auth', '/?token=private'])(
        'blocks private route %s even with a public edge policy', async path => {
            const response = await run('public, max-age=0', 'text/html', { url: `https://modtale.net${path}`, edge: 'public, max-age=600' });
            expect(response.headers.get('Cache-Control')).toBe('private, no-store');
            expect(response.headers.get('CDN-Cache-Control')).toBe('no-store');
            expect(response.headers.get('Cloudflare-CDN-Cache-Control')).toBe('no-store');
        }
    );
    it('does not cache HTML that sets a cookie', async () => {
        const response = await run('public, max-age=0', 'text/html', { edge: 'public, max-age=600', headers: { 'Set-Cookie': 'session=test' } });
        expect(response.headers.get('CDN-Cache-Control')).toBe('no-store');
    });
    it('does not cache authenticated authorization requests', async () => {
        const response = await run('public, max-age=0', 'text/html', { edge: 'public, max-age=600', requestHeaders: { Authorization: 'Bearer test' } });
        expect(response.headers.get('CDN-Cache-Control')).toBe('no-store');
    });
    it('leaves immutable assets untouched and outside the HTML purge', async () => {
        const policy = 'public, max-age=31536000, immutable';
        const response = await run(policy, 'application/javascript');
        expect(response.headers.get('Cache-Control')).toBe(policy);
        expect(response.headers.has('Cache-Tag')).toBe(false);
        expect(response.headers.has('CDN-Cache-Control')).toBe(false);
    });
});
