import type { Project, ProjectVersionChangelog } from '@/types';

export const projectNeedsChangelogHydration = (project: Project | null | undefined) => (
    (project?.versions || []).some(version => version.changelog == null)
);

export const mergeProjectVersionChangelogs = (
    project: Project,
    changelogs: ProjectVersionChangelog[]
): Project => {
    if (!project.versions?.length || !changelogs.length) return project;

    const byId = new Map(changelogs.map(changelog => [changelog.id, changelog]));
    const byVersion = new Map(changelogs.map(changelog => [changelog.versionNumber, changelog]));
    let changed = false;

    const versions = project.versions.map(version => {
        const changelog = byId.get(version.id) || byVersion.get(version.versionNumber);
        if (!changelog) return version;

        const nextChangelog = changelog.changelog || '';
        if (version.changelog === nextChangelog) return version;

        changed = true;
        return { ...version, changelog: nextChangelog };
    });

    return changed ? { ...project, versions } : project;
};
