import { afterEach, describe, expect, it, vi } from 'vitest';
import { GET } from '@/pages/__modjam_ui_review/[view]';

afterEach(() => vi.unstubAllEnvs());
describe('read-only synthetic branch review', () => {
    it.each(['production', 'development', ''])('stays unavailable outside a disposable branch preview: %s', async environment => {
        vi.stubEnv('LOG_ENVIRONMENT', environment);
        const response = await GET({ params: { view: 'detail' } } as any);
        expect(response.status).toBe(404);
    });
    it('serves clearly labelled component markup with no scripts or form actions', async () => {
        vi.stubEnv('LOG_ENVIRONMENT', 'branch-preview');
        const response = await GET({ params: { view: 'builder' } } as any);
        expect(response.status).toBe(200);
        expect(response.headers.get('Cache-Control')).toBe('private, no-store');
        expect(response.headers.get('Content-Security-Policy')).toContain("script-src 'none'");
        expect(response.headers.get('Content-Security-Policy')).toContain("form-action 'none'");
        const html = await response.text();
        expect(html).toContain('Synthetic design fixture');
        expect(html).toContain('Worldbuilders Weekend');
        expect(html).toContain(' inert');
        expect(html).not.toContain('<script');
    });
    it('does not resolve arbitrary review paths', async () => {
        vi.stubEnv('LOG_ENVIRONMENT', 'branch-preview');
        expect((await GET({ params: { view: '../../private' } } as any)).status).toBe(404);
    });
});
