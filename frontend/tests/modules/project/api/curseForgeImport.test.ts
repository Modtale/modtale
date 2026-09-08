import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadCurseForgeImport, parseCurseForgeReference } from '@/modules/project/api/curseForgeImport';

const project = {
    id: 42, game_id: 70216, is_available: true, name: 'My Mod', summary: 'A useful Hytale mod.',
    links: { website: 'https://www.curseforge.com/hytale/mods/my-mod' },
    logo: { url: 'https://media.forgecdn.net/avatars/1/icon.png' },
};
const ok = (data: unknown) => ({ ok: true, json: async () => data });

describe('CurseForge draft import', () => {
    afterEach(() => vi.unstubAllGlobals());

    it.each(['0', '-1', '9007199254740992', 'https://evil.test/hytale/mods/my-mod',
        'https://www.curseforge.com.evil.test/hytale/mods/my-mod',
        'https://user@www.curseforge.com/hytale/mods/my-mod',
        'https://www.curseforge.com/minecraft/mc-mods/my-mod'])('rejects invalid references: %s', input => {
        expect(() => parseCurseForgeReference(input)).toThrow('Hytale CurseForge');
    });

    it('resolves an exact URL slug and loads editable details without fetching releases', async () => {
        const fetcher = vi.fn()
            .mockResolvedValueOnce(ok({ data: [{ id: 9, slug: 'my-mod-extra' }, { id: 42, slug: 'my-mod' }] }))
            .mockResolvedValueOnce(ok(project))
            .mockResolvedValueOnce(ok({ description: '<h2>Features</h2><p>Useful stuff.</p>' }));
        vi.stubGlobal('fetch', fetcher);
        const imported = await loadCurseForgeImport(project.links.website);
        expect(imported).toMatchObject({ title: 'My Mod', summary: project.summary, classification: 'PLUGIN',
            about: '<h2>Features</h2><p>Useful stuff.</p>', sourceUrl: project.links.website,
            imageUrl: project.logo.url, warnings: [] });
        expect(fetcher).toHaveBeenCalledTimes(3);
        expect(fetcher.mock.calls[1][0]).toContain('/mods/42');
        expect(fetcher.mock.calls.every(([, options]) => options.credentials === 'omit')).toBe(true);
    });

    it.each([{ game_id: 432 }, { id: 43 }, { is_available: false },
        { links: { website: 'https://evil.test/hytale/mods/my-mod' } }])('rejects invalid provider metadata %j', async patch => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ok({ ...project, ...patch })));
        await expect(loadCurseForgeImport('42')).rejects.toThrow('Only available Hytale');
    });

    it('preserves metadata with a visible warning if the description fails', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(ok(project)).mockRejectedValueOnce(new Error('offline')));
        const imported = await loadCurseForgeImport('42');
        expect(imported.title).toBe('My Mod');
        expect(imported.about).toBe('');
        expect(imported.warnings).toHaveLength(1);
    });

    it('maps worlds, bounds imported text, and drops unapproved icon hosts', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(ok({ ...project, name: 'x'.repeat(101),
            summary: 'y'.repeat(251), logo: { url: 'https://forgecdn.net.evil.test/icon.png' },
            links: { website: 'https://www.curseforge.com/hytale/worlds/my-world' } }))
            .mockResolvedValueOnce(ok({ description: 'z'.repeat(50001) })));
        const imported = await loadCurseForgeImport('42');
        expect(imported.classification).toBe('SAVE');
        expect(imported.title).toHaveLength(100);
        expect(imported.summary).toHaveLength(250);
        expect(imported.about).toHaveLength(50000);
        expect(imported.imageUrl).toBeUndefined();
        expect(imported.warnings).toHaveLength(1);
    });

    it('does not silently select a similarly named project', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ok({ data: [{ id: 9, slug: 'my-mod-extra' }] })));
        await expect(loadCurseForgeImport(project.links.website)).rejects.toThrow('numeric CurseForge project ID');
    });

    it('reports rate limiting with a retry message', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 429 }));
        await expect(loadCurseForgeImport('42')).rejects.toThrow('try again shortly');
    });
});
