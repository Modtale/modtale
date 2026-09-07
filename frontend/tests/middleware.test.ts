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
