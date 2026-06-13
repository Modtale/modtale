import { describe, expect, it } from 'vitest';
import {
    dependencyProjectKey,
    getDependencyType,
    isEmbeddedDependency,
    isExternalDependency,
    isOptionalDependency,
    normalizeDependencyReference
} from '@/modules/project/utils/dependencyEntries';
import type { ProjectDependency } from '@/types';

describe('dependencyEntries', () => {
    it('normalizes missing dependency shape defaults', () => {
        const dependency = normalizeDependencyReference({
            projectId: 'dep-1',
            projectTitle: 'Dependency One',
            versionNumber: '1.2.3'
        });

        expect(dependency.dependencyType).toBe('REQUIRED');
        expect(dependency.source).toBe('MODTALE');
    });

    it('classifies dependency types from structured fields', () => {
        const optional = dependency('OPTIONAL');
        const embedded = dependency('EMBEDDED');

        expect(getDependencyType(optional)).toBe('OPTIONAL');
        expect(isOptionalDependency(optional)).toBe(true);
        expect(isEmbeddedDependency(optional)).toBe(false);
        expect(isEmbeddedDependency(embedded)).toBe(true);
    });

    it('keeps source in the dependency key so external IDs do not collide with Modtale IDs', () => {
        expect(dependencyProjectKey(dependency('REQUIRED', 'MODTALE'))).toBe('MODTALE:dep-1');
        expect(dependencyProjectKey(dependency('REQUIRED', 'GITHUB'))).toBe('GITHUB:dep-1');
        expect(isExternalDependency(dependency('REQUIRED', 'GITHUB'))).toBe(true);
    });
});

const dependency = (
    dependencyType: ProjectDependency['dependencyType'],
    source: ProjectDependency['source'] = 'MODTALE'
): ProjectDependency => ({
    projectId: 'dep-1',
    projectTitle: 'Dependency One',
    versionNumber: '1.2.3',
    dependencyType,
    source
});
