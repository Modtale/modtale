import type { DependencySource, ProjectDependency } from '@/types';
import type { WorldModList, WorldModListItem } from '@/modules/worldlist/api/worldListClient';

const dependencyId = (prefix: string, value: string) => `${prefix}-${value.replace(/[^A-Za-z0-9_-]+/g, '-').replace(/^-|-$/g, '') || 'item'}`;

const sourceValue = (source?: string): DependencySource => {
    if (source === 'CURSEFORGE' || source === 'GITHUB' || source === 'WEBSITE' || source === 'OTHER') return source;
    return 'MODTALE';
};

const externalProjectId = (item: WorldModListItem) => {
    const source = sourceValue(item.source);
    const externalId = item.externalId || item.modId || item.title;
    return `${source.toLowerCase()}:${externalId}`;
};

const itemToDependency = (item: WorldModListItem): ProjectDependency | null => {
    if (item.projectId) {
        return {
            id: dependencyId('seed', item.projectId),
            projectId: item.projectId,
            projectTitle: item.title || item.projectId,
            versionNumber: item.versionNumber || 'latest',
            dependencyType: 'REQUIRED',
            source: 'MODTALE',
            externalId: item.externalId,
            externalUrl: item.externalUrl,
            icon: item.icon,
            title: item.title,
            classification: item.classification,
            slug: item.slug
        };
    }

    if (!item.externalUrl) {
        return null;
    }

    const source = sourceValue(item.source);
    const projectId = externalProjectId(item);
    return {
        id: dependencyId('seed', projectId),
        projectId,
        projectTitle: item.title || item.externalId || item.modId || 'External mod',
        versionNumber: item.versionNumber || 'latest',
        dependencyType: 'REQUIRED',
        source,
        externalId: item.externalId || item.modId || item.title,
        externalUrl: item.externalUrl,
        hytaleProjectConfirmed: true,
        icon: item.icon,
        title: item.title,
        classification: item.classification,
        slug: item.slug
    };
};

export const worldListToProjectDependencies = (list: WorldModList): ProjectDependency[] => {
    const seen = new Set<string>();
    const dependencies: ProjectDependency[] = [];

    for (const item of list.mods || []) {
        const dependency = itemToDependency(item);
        if (!dependency) continue;
        const key = `${dependency.source || 'MODTALE'}:${dependency.projectId}`;
        if (seen.has(key)) continue;
        seen.add(key);
        dependencies.push(dependency);
    }

    return dependencies;
};

export const skippedWorldListItems = (list: WorldModList) => (
    (list.mods || []).filter(item => !item.projectId && !item.externalUrl)
);

/** Preserve shared config defaults when a list becomes a modpack version. */
export const worldListToOverrideFile = async (list: WorldModList): Promise<File | undefined> => {
    if (!list.configs?.length) return undefined;
    const { default: JSZip } = await import('jszip');
    const zip = new JSZip();
    let worldName = (list.worldName || 'Shared World').replace(/[<>:"/\\|?*\u0000-\u001f]/g, '-').replace(/[. ]+$/g, '') || 'Shared World';
    if (/^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(worldName)) worldName = `World-${worldName}`;
    for (const config of list.configs) {
        if (!['GLOBAL', 'WORLD'].includes(config.scope) || config.path.split('/').some(part => !part || part === '.' || part === '..' || /[\\:]/.test(part))) {
            throw new Error('The shared list contains an unsafe config path.');
        }
        const root = config.scope === 'GLOBAL' ? 'Mods' : `Saves/${worldName}/mods`;
        zip.file(`overrides/${root}/${config.path}`, config.content);
    }
    return new File([await zip.generateAsync({ type: 'blob' })], 'shared-list-configs.zip', { type: 'application/zip' });
};
