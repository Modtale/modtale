import { afterEach, describe, expect, it, vi } from 'vitest';
import { getCloudflareUrl, getSteppedWidth } from '@/utils/images';

const setWindowLocation = (hostname: string, origin: string) => {
    vi.stubGlobal('window', {
        location: {
            hostname,
            origin
        }
    });
};

describe('image utils', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('steps widths to the nearest supported Cloudflare bucket', () => {
        expect(getSteppedWidth(32)).toBe(64);
        expect(getSteppedWidth(64)).toBe(64);
        expect(getSteppedWidth(65)).toBe(128);
        expect(getSteppedWidth(255)).toBe(256);
        expect(getSteppedWidth(400)).toBe(640);
        expect(getSteppedWidth(1000)).toBe(1280);
        expect(getSteppedWidth(4000)).toBe(1920);
    });

    it('returns raw urls for empty, svg, and blob sources', () => {
        expect(getCloudflareUrl('', 640, 80)).toBe('');
        expect(getCloudflareUrl('/assets/logo.svg', 640, 80)).toBe('/assets/logo.svg');
        expect(getCloudflareUrl('blob:https://modtale.net/123', 640, 80)).toBe('blob:https://modtale.net/123');
    });

    it('skips rewriting in local environments', () => {
        setWindowLocation('localhost', 'http://localhost:4321');
        expect(getCloudflareUrl('/images/hero.png', 640, 75)).toBe('/images/hero.png');
    });

    it('does not proxy third-party absolute urls', () => {
        setWindowLocation('modtale.net', 'https://modtale.net');
        expect(getCloudflareUrl('https://example.com/image.png', 640, 75)).toBe('https://example.com/image.png');
    });

    it('rewrites relative first-party urls through Cloudflare', () => {
        setWindowLocation('modtale.net', 'https://modtale.net');
        expect(getCloudflareUrl('/images/hero.png', 500, 75))
            .toBe('https://modtale.net/cdn-cgi/image/width=640,quality=75,format=auto,onerror=redirect/https://modtale.net/images/hero.png');
    });

    it('rewrites first-party absolute urls, including the CDN host', () => {
        setWindowLocation('modtale.net', 'https://modtale.net');
        expect(getCloudflareUrl('https://cdn.modtale.net/images/hero.png', 80, 90))
            .toBe('https://modtale.net/cdn-cgi/image/width=128,quality=90,format=auto,onerror=redirect/https://cdn.modtale.net/images/hero.png');
    });

    it('returns raw urls on Cloud Run preview hosts that cannot serve Cloudflare image resizing', () => {
        setWindowLocation('modtale-frontend-launcher-ptpi2wdeva-uc.a.run.app', 'https://modtale-frontend-launcher-ptpi2wdeva-uc.a.run.app');

        expect(getCloudflareUrl('https://cdn.modtale.net/images/hero.png', 80, 90))
            .toBe('https://cdn.modtale.net/images/hero.png');
        expect(getCloudflareUrl('/images/hero.png', 500, 75))
            .toBe('/images/hero.png');
    });

    it('builds a fallback absolute url in SSR mode for relative paths', () => {
        vi.stubGlobal('window', undefined);
        expect(getCloudflareUrl('images/hero.png', 70, 80))
            .toBe('https://modtale.net/cdn-cgi/image/width=128,quality=80,format=auto,onerror=redirect/https://modtale.net/images/hero.png');
    });
});
