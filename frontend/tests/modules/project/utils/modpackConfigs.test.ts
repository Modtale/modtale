// @vitest-environment node
import { describe, expect, it } from 'vitest';
import JSZip from 'jszip';
import { buildModpackOverrides, configPath, type ModConfig } from '../../../../src/modules/project/utils/modpackConfigs';
import type { VersionFormData } from '../../../../src/modules/project/components/FormShared';

const config = (destination = 'Saves/MyWorld/mods/ExamplePlugin'): ModConfig => ({
    id: 'config-1', projectId: 'mod-1', file: new File(['{"enabled":true}'], 'config.json'), destination
});
const data = (configs: ModConfig[], file: File | null = null): VersionFormData => ({
    dependencies: [{ projectId: 'mod-1', projectTitle: 'Example', versionNumber: '1.0.0' }],
    incompatibleProjectIds: [], versionNumber: '1.0.0', gameVersions: [], changelog: '', file, modConfigs: configs
});

describe('modpack config attachments', () => {
    it('packages config contents at the exact world destination consumed by the generator', async () => {
        const result = await buildModpackOverrides(data([config()]));
        const zip = await JSZip.loadAsync(await result!.arrayBuffer());
        expect(await zip.file('overrides/Saves/MyWorld/mods/ExamplePlugin/config.json')!.async('string')).toBe('{"enabled":true}');
    });
    it('preserves advanced overrides alongside mod configs', async () => {
        const existing = new JSZip().file('overrides/Mods/Other/settings.json', '{}');
        const bundle = new File([await existing.generateAsync({ type: 'arraybuffer' })], 'overrides.zip');
        const result = await buildModpackOverrides(data([config('Mods/ExamplePlugin')], bundle));
        const zip = await JSZip.loadAsync(await result!.arrayBuffer());
        expect(zip.file('overrides/Mods/Other/settings.json')).not.toBeNull();
        expect(zip.file('overrides/Mods/ExamplePlugin/config.json')).not.toBeNull();
    });
    it('rejects collisions including files in the advanced ZIP', async () => {
        const existing = new JSZip().file('overrides/mods/exampleplugin/CONFIG.JSON', '{}');
        const bundle = new File([await existing.generateAsync({ type: 'arraybuffer' })], 'overrides.zip');
        await expect(buildModpackOverrides(data([config('Mods/ExamplePlugin')], bundle))).rejects.toThrow('More than one config');
        await expect(buildModpackOverrides(data([config(), config()]))).rejects.toThrow('More than one config');
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
