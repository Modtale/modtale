import type { DependencyType, ProjectDependency } from '@/types';

export const getDependencyType = (dependency?: Pick<ProjectDependency, 'dependencyType'>): DependencyType =>
    dependency?.dependencyType || 'REQUIRED';

export const isOptionalDependency = (dependency?: Pick<ProjectDependency, 'dependencyType'>): boolean =>
    getDependencyType(dependency) === 'OPTIONAL';

export const isEmbeddedDependency = (dependency?: Pick<ProjectDependency, 'dependencyType'>): boolean =>
    getDependencyType(dependency) === 'EMBEDDED';

export const isExternalDependency = (dependency?: Pick<ProjectDependency, 'source'>): boolean =>
    (dependency?.source || 'MODTALE') !== 'MODTALE';

export const normalizeDependencyReference = (dependency: ProjectDependency): ProjectDependency => ({
    ...dependency,
    dependencyType: dependency.dependencyType || 'REQUIRED',
    source: dependency.source || 'MODTALE'
});

export const dependencyProjectKey = (dependency: ProjectDependency): string =>
    `${dependency.source || 'MODTALE'}:${dependency.projectId}`;
