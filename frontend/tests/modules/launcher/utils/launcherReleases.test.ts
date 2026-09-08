import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchStableLauncherRelease, isStableLauncherRelease } from '../../../../src/modules/launcher/utils/launcherReleases';

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
        expect(await fetchStableLauncherRelease(new AbortController().signal)).toEqual(stable);
        expect(fetchMock.mock.calls[1][0]).toContain('page=2');
    });

    it('returns no download when only develop exists', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [{ tag_name: 'launcher-develop-v1.0.0', prerelease: true }] }));
        expect(await fetchStableLauncherRelease(new AbortController().signal)).toBeNull();
    });
});
