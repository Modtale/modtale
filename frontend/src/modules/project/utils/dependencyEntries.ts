import type { ProjectDependency } from '@/types';

export interface ParsedDependencyEntry {
    projectId: string;
    versionNumber: string;
    isOptional: boolean;
    isEmbedded: boolean;
}

export const parseDependencyEntry = (entry: string): ParsedDependencyEntry => {
    const [projectId = '', versionNumber = '', ...flagParts] = entry.split(':');
    const flags = new Set(flagParts.map((flag) => flag.trim().toLowerCase()).filter(Boolean));

    return {
        projectId,
        versionNumber,
        isOptional: flags.has('optional'),
        isEmbedded: flags.has('embedded')
    };
};

export const serializeDependencyEntry = ({
    projectId,
    versionNumber,
    isOptional,
    isEmbedded
}: ParsedDependencyEntry): string => {
    const flags: string[] = [];
    if (isOptional) flags.push('optional');
    if (isEmbedded) flags.push('embedded');
    return [projectId, versionNumber, ...flags].join(':');
};

export const serializeProjectDependency = (dependency: ProjectDependency): string =>
    serializeDependencyEntry({
        projectId: dependency.projectId,
        versionNumber: dependency.versionNumber,
        isOptional: Boolean(dependency.isOptional),
        isEmbedded: Boolean(dependency.isEmbedded)
    });
