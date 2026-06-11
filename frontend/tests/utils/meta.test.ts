import { describe, expect, it } from 'vitest';
import { generateProjectMeta, generateUserMeta } from '@/utils/meta';

describe('meta utils', () => {
    it('returns null when metadata inputs are missing', () => {
        expect(generateProjectMeta(null)).toBeNull();
        expect(generateUserMeta(null)).toBeNull();
    });

    it('builds plugin metadata from markdown-heavy about text', () => {
        const result = generateProjectMeta({
            title: 'Sky Tools',
            author: 'Ada',
            downloadCount: 1234,
            classification: 'PLUGIN',
            about: '# Heading\n**Bold** text with [docs](https://example.com) and `code`\n- bullet\n![hero](https://example.com/hero.png)',
        });

        expect(result).toEqual({
            title: 'Sky Tools | Hytale Plugin | Modtale',
            author: 'Ada',
            description: 'Download Sky Tools, a Hytale server plugin by Ada, on Modtale. Heading Bold text with docs and code bullet 1,234 downloads.',
        });
    });

    it('uses category-aware labels for non-plugin projects', () => {
        const result = generateProjectMeta({
            title: 'World Builder',
            description: 'Craft *bigger* builds with #style',
            downloadCount: 0,
            classification: 'SAVE',
        });

        expect(result).toEqual({
            title: 'World Builder | Hytale World | Modtale',
            author: 'Unknown',
            description: 'Download World Builder, a Hytale world save by Unknown, on Modtale. Craft bigger builds with style 0 downloads.',
        });
    });

    it('truncates long project descriptions before appending the download count', () => {
        const longText = 'a'.repeat(170);
        const result = generateProjectMeta({
            title: 'Longform',
            author: 'Ada',
            downloadCount: 10,
            classification: 'DATA',
            about: longText,
        });

        expect(result?.title).toBe('Longform | Hytale Data Asset | Modtale');
        expect(result?.description).toContain('Download Longform, a Hytale data asset by Ada, on Modtale.');
        expect(result?.description).toContain('10 downloads.');
        expect(result?.description?.length).toBeLessThanOrEqual(160);
    });

    it('uses creator wording for user metadata', () => {
        expect(generateUserMeta({
            username: 'ada',
            displayName: 'Ada Lovelace',
            followerIds: ['u1'],
        })).toEqual({
            title: 'Ada Lovelace | Modtale Creator',
            description: 'Creator profile on Modtale with 1 follower.',
        });

        expect(generateUserMeta({
            username: 'grace',
            followerIds: ['u1', 'u2'],
        })).toEqual({
            title: 'grace | Modtale Creator',
            description: 'Creator profile on Modtale with 2 followers.',
        });
    });
});
