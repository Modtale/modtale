import { describe, expect, it } from 'vitest';
import JSZip from 'jszip';
import { worldListToOverrideFile } from '@/modules/worldlist/utils/modpackSeed';
import type { WorldModList } from '@/modules/worldlist/api/worldListClient';

const base = { id: 'list', worldName: 'My World', mods: [] } as unknown as WorldModList;
const read = (file: Blob) => new Promise<ArrayBuffer>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as ArrayBuffer);
    reader.onerror = reject;
    reader.readAsArrayBuffer(file);
});

describe('shared list config seeding', () => {
    it('keeps old lists without configs unchanged', async () => {
        expect(await worldListToOverrideFile(base)).toBeUndefined();
    });
    it('preserves config contents and Hytale global and world destinations', async () => {
        const file = await worldListToOverrideFile({ ...base, configs: [
            { scope: 'GLOBAL', path: 'Example/settings.toml', content: 'value=2\r\n' },
            { scope: 'WORLD', path: 'Example/config.json', content: '{"world":true}' },
        ] });
        const zip = await JSZip.loadAsync(await read(file!));
        expect(await zip.file('overrides/Mods/Example/settings.toml')!.async('string')).toBe('value=2\r\n');
        expect(await zip.file('overrides/Saves/My World/mods/Example/config.json')!.async('string')).toBe('{"world":true}');
    });
    it('rejects unsafe paths', async () => {
        await expect(worldListToOverrideFile({ ...base, configs: [
            { scope: 'WORLD', path: '../config.json', content: '{}' },
        ] })).rejects.toThrow('unsafe');
    });
});
