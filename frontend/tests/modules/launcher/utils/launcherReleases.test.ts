import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchLauncherRelease, isStableLauncherRelease, isLauncherReleaseForChannel, launcherChannelForHostname, launcherReleasesUrl } from '../../../../src/modules/launcher/utils/launcherReleases';

afterEach(() => vi.unstubAllGlobals());

describe('stable launcher downloads', () => {
    it('excludes develop, drafts, prereleases and unrelated releases', () => {
        expect(isStableLauncherRelease({ tag_name: 'launcher-stable-v1.2.3' })).toBe(true);
        expect(isStableLauncherRelease({ tag_name: 'launcher-v1.2.3' })).toBe(true);
        for (const release of [
            { tag_name: 'launcher-develop-v1.2.4-develop.10.1' },
            { tag_name: 'launcher-stable-v1.2.3', draft: true },
            { tag_name: 'launcher-stable-v1.2.3', prerelease: true },
            { tag_name: 'v9.0.0', name: 'Backend' },
        ]) expect(isStableLauncherRelease(release)).toBe(false);
    });

    it('finds stable even after a full page of develop releases', async () => {
        const stable = { tag_name: 'launcher-stable-v1.2.3' };
        const fetchMock = vi.fn()
            .mockResolvedValueOnce({ ok: true, json: async () => Array(100).fill({ tag_name: 'launcher-develop-v2.0.0', prerelease: true }) })
            .mockResolvedValueOnce({ ok: true, json: async () => [stable] });
        vi.stubGlobal('fetch', fetchMock);
        expect(await fetchLauncherRelease(new AbortController().signal)).toEqual(stable);
        expect(fetchMock.mock.calls[1][0]).toContain('page=2');
    });

    it('returns no download when only develop exists', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [{ tag_name: 'launcher-develop-v1.0.0', prerelease: true }] }));
        expect(await fetchLauncherRelease(new AbortController().signal)).toBeNull();
    });
});


describe('development site launcher downloads', () => {
    it('selects develop only for the development deployment', () => {
        expect(launcherChannelForHostname('dev.modtale.net')).toBe('develop');
        for (const hostname of ['modtale.net', 'www.modtale.net', 'localhost', 'preview.modtale.net']) {
            expect(launcherChannelForHostname(hostname)).toBe('stable');
        }
        expect(launcherReleasesUrl('develop')).toContain('releases?q=launcher-develop-v');
        expect(launcherReleasesUrl('stable')).toContain('releases/latest');
    });

    it('accepts only published develop prereleases', () => {
        expect(isLauncherReleaseForChannel({ tag_name: 'launcher-develop-v1.2.3', prerelease: true }, 'develop')).toBe(true);
        for (const release of [
            { tag_name: 'launcher-v1.2.3' },
            { tag_name: 'launcher-develop-v1.2.3', prerelease: true, draft: true },
            { tag_name: 'launcher-develop-v1.2.3', prerelease: false },
            { tag_name: 'v1.2.3-beta', prerelease: true },
        ]) expect(isLauncherReleaseForChannel(release, 'develop')).toBe(false);
    });

    it('returns develop installer URLs on dev even when stable appears first', async () => {
        const develop = {
            tag_name: 'launcher-develop-v1.2.3', prerelease: true,
            assets: [{ name: 'launcher.exe', browser_download_url: 'https://github.com/Modtale/modtale/releases/download/launcher-develop-v1.2.3/launcher.exe' }],
        };
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [
            { tag_name: 'launcher-v1.2.3' }, develop,
        ] }));
        expect(await fetchLauncherRelease(new AbortController().signal, launcherChannelForHostname('dev.modtale.net'))).toEqual(develop);
    });

    it('does not fall back to stable when develop is unavailable', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [{ tag_name: 'launcher-v1.2.3' }] }));
        expect(await fetchLauncherRelease(new AbortController().signal, 'develop')).toBeNull();
    });
});
