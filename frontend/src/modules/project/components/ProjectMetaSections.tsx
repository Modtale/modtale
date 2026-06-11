import React from 'react';
import { Link } from 'react-router-dom';
import { Box, Gamepad2, Link as LinkIcon, Tag } from 'lucide-react';

import { SidebarSection } from '@/modules/project/components/ProjectLayout';
import { OptimizedImage } from '@/components/ui/OptimizedImage';
import { SiteRoutes } from '@/utils/routes';
import { BACKEND_URL } from '@/utils/api';
import type { Project, ProjectDependency } from '@/types';

interface ProjectMetaSectionsProps {
    project: Project;
    dependencies?: ProjectDependency[];
    depMeta: Record<string, { icon: string; title: string; classification?: string; slug?: string }>;
}

export const ProjectMetaSections: React.FC<ProjectMetaSectionsProps> = React.memo(({
    project,
    dependencies,
    depMeta
}) => {
    const gameVersions = React.useMemo(() => {
        const set = new Set<string>();
        (project.versions || []).forEach(v => v.gameVersions?.forEach(gv => set.add(gv)));
        return Array.from(set).sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
    }, [project.versions]);

    const isModpack = project.classification === 'MODPACK';
    const getIconUrl = (path?: string) => path ? (path.startsWith('http') ? path : `${BACKEND_URL}${path}`) : null;

    return (
        <>
            {gameVersions.length > 0 && (
                <SidebarSection title="Supported Versions" icon={Gamepad2}>
                    <div className="flex flex-wrap gap-2">
                        {gameVersions.map(v => (
                            <span key={v} className="px-2.5 py-1 bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 rounded-md text-[10px] font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide">
                                {v}
                            </span>
                        ))}
                    </div>
                </SidebarSection>
            )}

            <SidebarSection title="Tags" icon={Tag}>
                <div className="flex flex-wrap gap-2">
                    {project.tags?.map((tag) => (
                        <span key={tag} className="px-3 py-1.5 bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/10 rounded-lg text-xs font-bold text-slate-700 dark:text-slate-300 hover:text-modtale-accent hover:border-modtale-accent transition-all cursor-default">
                            {tag}
                        </span>
                    ))}
                    {(!project.tags || project.tags.length === 0) && <span className="text-xs text-slate-500 italic">No tags.</span>}
                </div>
            </SidebarSection>

            {dependencies && dependencies.length > 0 && (
                <SidebarSection title={isModpack ? "Included Projects" : "Dependencies"} icon={isModpack ? Box : LinkIcon}>
                    <div className="space-y-2">
                        {dependencies.map((dep, idx) => {
                            const meta = depMeta[dep.projectId];
                            const iconUrl = getIconUrl(meta?.icon);
                            const title = meta?.title || dep.projectTitle || dep.projectId;

                            const targetProjectParams = {
                                id: dep.projectId,
                                title: title,
                                slug: meta?.slug,
                                classification: meta?.classification
                            };

                            const path = SiteRoutes.project(targetProjectParams);

                            return (
                                <Link
                                    key={idx}
                                    to={path}
                                    className="w-full flex items-center gap-3 p-3 rounded-xl bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/5 hover:border-modtale-accent/50 hover:shadow-md transition-all group text-left block"
                                >
                                    <div className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-black/20 flex items-center justify-center text-slate-400 group-hover:text-modtale-accent transition-colors overflow-hidden shrink-0">
                                        {iconUrl ? (
                                            <OptimizedImage
                                                src={iconUrl}
                                                alt={`${title} Icon`}
                                                baseWidth={32}
                                                className="w-full h-full"
                                            />
                                        ) : <Box className="w-4 h-4" aria-hidden="true" />}
                                    </div>
                                    <div className="min-w-0">
                                        <div className="text-xs font-bold text-slate-800 dark:text-slate-200 group-hover:text-modtale-accent truncate">{title}</div>
                                        <div className="text-[10px] text-slate-600 dark:text-slate-400 flex items-center gap-2">
                                            {!isModpack && <span className={dep.isOptional ? '' : 'text-amber-600 dark:text-amber-500 font-bold'}>{dep.isOptional ? 'Optional' : 'Required'}</span>}
                                            {dep.isEmbedded && <span className="text-emerald-600 dark:text-emerald-400 font-bold">Embedded</span>}
                                            <span className="font-mono opacity-75">v{dep.versionNumber}</span>
                                        </div>
                                    </div>
                                </Link>
                            );
                        })}
                    </div>
                </SidebarSection>
            )}
        </>
    );
});

ProjectMetaSections.displayName = 'ProjectMetaSections';
