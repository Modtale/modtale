// @vitest-environment node
import { describe, expect, it } from 'vitest';
import JSZip from 'jszip';
import { buildModpackOverrides, configPath, importModpackConfigs, CONFIG_MANIFEST, type ModConfig } from '../../../../src/modules/project/utils/modpackConfigs';
import type { VersionFormData } from '../../../../src/modules/project/components/FormShared';

const config = (destination = 'Universe/mods/ExamplePlugin'): ModConfig => ({
    id: 'config-1', projectId: 'mod-1', file: new File(['{"enabled":true}'], 'config.json'), destination
});
const data = (configs: ModConfig[], file: File | null = null): VersionFormData => ({
    dependencies: [{ projectId: 'mod-1', projectTitle: 'Example', versionNumber: '1.0.0' }],
    incompatibleProjectIds: [], versionNumber: '1.0.0', gameVersions: [], changelog: '', file, modConfigs: configs
});

describe('modpack config attachments', () => {
    it('exports deterministic bytes and round trips ownership, nested paths, and original content', async () => {
        const state = data([{ ...config(), relativePath: 'nested/config.json' }]);
        const first = await buildModpackOverrides(state);
        const second = await buildModpackOverrides(state);
        expect(new Uint8Array(await first!.arrayBuffer())).toEqual(new Uint8Array(await second!.arrayBuffer()));
        const zip = await JSZip.loadAsync(await first!.arrayBuffer());
        const manifest = JSON.parse(await zip.file(CONFIG_MANIFEST)!.async('string'));
        expect(manifest.configs[0]).toMatchObject({ projectId: 'mod-1', source: 'MODTALE', path: 'overrides/Universe/mods/ExamplePlugin/nested/config.json' });
        const imported = await importModpackConfigs(first!, state.dependencies);
        expect(imported.configs[0].relativePath).toBe('nested/config.json');
        expect(await imported.configs[0].file.text()).toBe('{"enabled":true}');
    });
    it('rejects a bundle whose bytes no longer match its manifest', async () => {
        const file = await buildModpackOverrides(data([config()]));
        const zip = await JSZip.loadAsync(await file!.arrayBuffer());
        zip.file('overrides/Universe/mods/ExamplePlugin/config.json', '{}');
        const changed = new File([await zip.generateAsync({ type: 'arraybuffer' })], 'changed.zip');
        await expect(importModpackConfigs(changed, data([]).dependencies)).rejects.toThrow('checksum mismatch');
    });

    it('packages config contents at the universe-relative destination consumed by the generator', async () => {
        const result = await buildModpackOverrides(data([config()]));
        const zip = await JSZip.loadAsync(await result!.arrayBuffer());
        expect(await zip.file('overrides/Universe/mods/ExamplePlugin/config.json')!.async('string')).toBe('{"enabled":true}');
    });
    it('rejects shared archives and save destinations', async () => {
        const bundle = new File([''], 'overrides.zip');
        await expect(buildModpackOverrides(data([config()], bundle))).rejects.toThrow('Shared files and saves');
        expect(() => configPath(config('Saves/MyWorld/mods/Example'))).toThrow();
        expect(() => configPath(config('Mods/Example'))).toThrow();
        await expect(buildModpackOverrides(data([config(), config()]))).rejects.toThrow('Two configs');
    });
    it('omits attachments belonging to removed mods', async () => {
        const state = data([config()]);
        state.dependencies = [];
        expect(await buildModpackOverrides(state)).toBeNull();
    });
    it.each(['', '../Mods/Example', 'Mods/../Example', '/Mods/Example', 'Mods/Example\\Nested', 'Mods/CON', 'Mods/Example.', 'Saves/<world>/mods/Example'])('rejects unsafe or incomplete destinations: %s', destination => {
        expect(() => configPath(config(destination))).toThrow();
    });
    it('rejects executable attachments', () => {
        expect(() => configPath({ ...config(), file: new File([''], 'plugin.jar') })).toThrow('not a supported config');
    });
});
