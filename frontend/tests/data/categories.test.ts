import { describe, expect, it } from 'vitest';
import { BROWSE_SORTS, GLOBAL_TAGS, LICENSES, PROJECT_TYPES } from '@/data/categories';

describe('category data', () => {
    it('keeps global tags sorted alphabetically', () => {
        expect(GLOBAL_TAGS).toEqual([...GLOBAL_TAGS].sort());
    });

    it('exposes the expected browse sort ids', () => {
        expect(BROWSE_SORTS.map(sort => sort.id)).toEqual([
            'relevance',
            'popular',
            'trending',
            'downloads',
            'favorites',
            'newest',
            'updated',
        ]);
    });

    it('lists supported project types and license options', () => {
        expect(PROJECT_TYPES.map(type => type.id)).toEqual([
            'All',
            'PLUGIN',
            'DATA',
            'ART',
            'SAVE',
            'MODPACK'
        ]);
        expect(LICENSES.some(license => license.id === 'MIT')).toBe(true);
        expect(LICENSES.some(license => license.id === 'CC-BY-NC-SA-4.0')).toBe(true);
        expect(LICENSES.some(license => license.id === 'Unlicense')).toBe(true);
    });
});
