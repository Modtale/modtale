import { describe, expect, it } from 'vitest';
import { parseDependencyEntry, serializeDependencyEntry, serializeProjectDependency } from '@/modules/project/utils/dependencyEntries';

describe('dependencyEntries', () => {
    it('parses optional and embedded flags in any order', () => {
        expect(parseDependencyEntry('dep-1:1.2.3:embedded:optional')).toEqual({
            projectId: 'dep-1',
            versionNumber: '1.2.3',
            isOptional: true,
            isEmbedded: true
        });
    });

    it('serializes dependency flags without dropping compatibility for empty flags', () => {
        expect(serializeDependencyEntry({
            projectId: 'dep-1',
            versionNumber: '1.2.3',
            isOptional: false,
            isEmbedded: false
        })).toBe('dep-1:1.2.3');

        expect(serializeDependencyEntry({
            projectId: 'dep-1',
            versionNumber: '1.2.3',
            isOptional: true,
            isEmbedded: true
        })).toBe('dep-1:1.2.3:optional:embedded');
    });

    it('serializes project dependency objects from API data', () => {
        expect(serializeProjectDependency({
            projectId: 'dep-1',
            projectTitle: 'Dependency One',
            versionNumber: '1.2.3',
            isOptional: true,
            isEmbedded: true
        })).toBe('dep-1:1.2.3:optional:embedded');
    });
});
