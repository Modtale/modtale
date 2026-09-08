import { describe, expect, it } from 'vitest';
import { configOwnerLabel } from '@/modules/worldlist/utils/configOwner';
import type { WorldModListItem } from '@/modules/worldlist/api/worldListClient';
const mod = (modId: string, title = 'Marketplace title') => ({ modId, title }) as WorldModListItem;
describe('Hytale config ownership', () => {
    it('uses exact Group_Name from mod ID, preserving dots and underscores', () => {
        expect(configOwnerLabel('org.example_mods_Fancy_Mod/config.json', [mod('org.example_mods:Fancy_Mod')]))
            .toBe('Marketplace title (org.example_mods:Fancy_Mod)');
    });
    it('does not guess from titles, case differences or custom directories', () => {
        const mods = [mod('Author:Plugin', 'Plugin')];
        for (const path of ['Plugin/config.json', 'author_Plugin/config.json', 'custom/config.json']) {
            expect(configOwnerLabel(path, mods)).toBe('Unattributed');
        }
    });
    it('keeps underscore collisions ambiguous and deduplicates identical IDs', () => {
        expect(configOwnerLabel('A_B_C/config.json', [mod('A_B:C'), mod('A:B_C')])).toBe('Ambiguous mod');
        expect(configOwnerLabel('A_B/config.json', [mod('A:B'), mod('A:B')])).toBe('Marketplace title (A:B)');
    });
});
