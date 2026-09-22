import { describe, expect, it } from 'vitest';
import { onRequest } from '@/middleware';

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
    const run = (policy: string, type = 'text/html') => (onRequest as any)(
        { url: new URL('https://modtale.net/') },
        async () => new Response('content', { headers: { 'content-type': type, 'cache-control': policy } })
    );
    it('keeps aggressive edge caching while requiring browser revalidation', async () => {
        const policy = 'public, max-age=300, s-maxage=86400, stale-while-revalidate=86400';
        const response = await run(policy);
        expect(response.headers.get('CDN-Cache-Control')).toBe(policy);
        expect(response.headers.get('Cache-Control')).toBe('public, max-age=0, must-revalidate');
        expect(response.headers.get('Cache-Tag')).toBe('modtale-html-modtale.net');
    });
    it.each(['private, no-store', 'no-store', 'public, max-age=0, s-maxage=0, must-revalidate'])(
        'does not make restricted responses edge-cacheable: %s', async policy => {
            const response = await run(policy);
            expect(response.headers.get('CDN-Cache-Control')).toBe(policy);
            if (policy.includes('no-store')) expect(response.headers.get('Cache-Control')).toBe(policy);
        }
    );
    it('leaves immutable assets untouched and outside the HTML purge', async () => {
        const policy = 'public, max-age=31536000, immutable';
        const response = await run(policy, 'application/javascript');
        expect(response.headers.get('Cache-Control')).toBe(policy);
        expect(response.headers.has('Cache-Tag')).toBe(false);
        expect(response.headers.has('CDN-Cache-Control')).toBe(false);
    });
});
