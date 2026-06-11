import { describe, expect, it } from 'vitest';
import { BROWSE_VIEWS, GLOBAL_TAGS, LICENSES, PROJECT_TYPES } from '@/data/categories';

describe('category data', () => {
    it('keeps global tags sorted alphabetically', () => {
        expect(GLOBAL_TAGS).toEqual([...GLOBAL_TAGS].sort());
    });

    it('exposes the expected browse view ids and default sorts', () => {
        expect(BROWSE_VIEWS.map(view => view.id)).toEqual([
            'all',
            'popular',
            'trending',
            'new',
            'updated',
            'hidden_gems',
            'favorites'
        ]);
        expect(BROWSE_VIEWS.every(view => typeof view.defaultSort === 'string' && view.defaultSort.length > 0)).toBe(true);
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
        expect(LICENSES.some(license => license.id === 'Unlicense')).toBe(true);
    });
});
