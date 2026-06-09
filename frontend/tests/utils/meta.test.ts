import { describe, expect, it } from 'vitest';
import { generateProjectMeta, generateUserMeta } from '@/utils/meta';

describe('meta utils', () => {
    it('returns null when metadata inputs are missing', () => {
        expect(generateProjectMeta(null)).toBeNull();
        expect(generateUserMeta(null)).toBeNull();
    });

    it('builds project metadata from markdown-heavy about text', () => {
        const result = generateProjectMeta({
            title: 'Sky Tools',
            author: 'Ada',
            downloadCount: 1234,
            classification: 'MODPACK',
            about: '# Heading\n**Bold** text with [docs](https://example.com) and `code`\n- bullet\n![hero](https://example.com/hero.png)'
        });

        expect(result).toEqual({
            title: 'Sky Tools | Modtale',
            author: 'Ada',
            description: 'Heading Bold text with docs and code bullet — ⬇️ 1,234  🏷️ Modpack  👤 Ada'
        });
    });

    it('falls back to description text and unknown author when about is unavailable', () => {
        const result = generateProjectMeta({
            title: 'World Builder',
            description: 'Craft *bigger* builds with #style',
            downloadCount: 0,
            classification: 'SAVE'
        });

        expect(result).toEqual({
            title: 'World Builder | Modtale',
            author: 'Unknown',
            description: 'Craft bigger builds with style — ⬇️ 0  🏷️ World  👤 Unknown'
        });
    });

    it('truncates long project descriptions before appending the stats line', () => {
        const longText = 'a'.repeat(170);
        const result = generateProjectMeta({
            title: 'Longform',
            author: 'Ada',
            downloadCount: 10,
            classification: 'DATA',
            about: longText
        });

        expect(result?.description.startsWith(`${'a'.repeat(150)}... — ⬇️ 10  🏷️ Data  👤 Ada`)).toBe(true);
    });

    it('uses display names and pluralizes follower counts for user metadata', () => {
        expect(generateUserMeta({
            username: 'ada',
            displayName: 'Ada Lovelace',
            followerIds: ['u1']
        })).toEqual({
            title: 'Ada Lovelace | Modtale',
            description: '👥 1 follower'
        });

        expect(generateUserMeta({
            username: 'grace',
            followerIds: ['u1', 'u2']
        })).toEqual({
            title: 'grace | Modtale',
            description: '👥 2 followers'
        });
    });
});
