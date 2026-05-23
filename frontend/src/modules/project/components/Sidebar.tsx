import React, { useState, useMemo, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Heart, Download, Gamepad2, Tag, Scale, Hash, Copy, Check, Users, Box, Link as LinkIcon } from 'lucide-react';
import { SidebarSection } from '@/modules/project/components/ProjectLayout';
import { OptimizedImage } from '@/components/ui/OptimizedImage';
import { getLicenseInfo } from '@/utils/modHelpers';
import { SiteRoutes } from '@/utils/routes';
import { BACKEND_URL } from '@/utils/api';
import type { Project, User, ProjectDependency } from '@/types';

interface SidebarProps {
    project: Project;
    dependencies?: ProjectDependency[];
    depMeta: Record<string, { icon: string, title: string, classification?: string, slug?: string }>;
    navigate: (path: string) => void;
    contributors: User[];
    orgMembers: User[];
    author: User | null;
}

export const Sidebar: React.FC<SidebarProps> = React.memo(({
                                                               project, dependencies, depMeta, navigate, contributors, orgMembers, author
                                                           }) => {
    const [copiedId, setCopiedId] = useState(false);

    const gameVersions = useMemo(() => {
        const set = new Set<string>();
        (project.versions || []).forEach(v => v.gameVersions?.forEach(gv => set.add(gv)));
        return Array.from(set).sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
    }, [project.versions]);

    const licenseInfo = useMemo(() => {
        if (project.links?.LICENSE) {
            return { name: project.license || 'Custom License', url: project.links.LICENSE };
        }
        return project.license ? getLicenseInfo(project.license) : null;
    }, [project.license, project.links]);

    const handleCopyId = useCallback(() => {
        navigator.clipboard.writeText(project.id);
        setCopiedId(true);
        setTimeout(() => setCopiedId(false), 2000);
    }, [project.id]);

    const isModpack = project.classification === 'MODPACK';
    const getIconUrl = (path?: string) => path ? (path.startsWith('http') ? path : `${BACKEND_URL}${path}`) : null;

    return (
        <div className="flex flex-col gap-8">
            <div className="grid grid-cols-2 gap-2 py-2">
                <div className="flex flex-col items-center justify-start">
                    <div suppressHydrationWarning className="text-3xl font-black text-slate-900 dark:text-white tracking-tighter leading-none mb-1">
                        {(project.favoriteCount || 0).toLocaleString()}
                    </div>
                    <div className="flex items-center justify-center mt-1 h-5 w-full gap-1.5 text-slate-500 dark:text-slate-400">
                        <Heart className="w-3.5 h-3.5" aria-hidden="true" />
                        <span className="text-[10px] font-bold uppercase tracking-widest pt-0.5">Favorites</span>
                    </div>
                </div>

                <div className="flex flex-col items-center justify-start border-l border-slate-200 dark:border-white/5">
                    <div suppressHydrationWarning className="text-3xl font-black text-slate-900 dark:text-white tracking-tighter leading-none mb-1">
                        {(project.downloadCount || 0).toLocaleString()}
                    </div>
                    <div className="flex items-center gap-1.5 mt-1 h-5">
                        <Download className="w-3.5 h-3.5 text-slate-500 dark:text-slate-400" aria-hidden="true" />
                        <span className="text-[10px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest pt-0.5">Downloads</span>
                    </div>
                </div>
            </div>

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

            {author && author.accountType === 'ORGANIZATION' && orgMembers.length > 0 && (
                <SidebarSection title="Organization Members" icon={Users}>
                    <div className="flex flex-col gap-2">
                        {orgMembers.map(member => {
                            const orgMembership = author.organizationMembers?.find(m => m.userId === member.id);
                            const role = author.organizationRoles?.find(r => r.id === orgMembership?.roleId);
                            const roleName = role?.name || 'Member';

                            return (
                                <Link
                                    key={member.id}
                                    to={SiteRoutes.creator(member.username)}
                                    className="flex items-center gap-3 p-2 rounded-xl hover:bg-slate-100 dark:hover:bg-white/5 transition-colors group"
                                >
                                    <OptimizedImage
                                        src={member.avatarUrl}
                                        alt={`${member.username} Avatar`}
                                        baseWidth={32}
                                        className="w-8 h-8 rounded-lg border border-slate-200 dark:border-white/5 shrink-0"
                                    />
                                    <div className="min-w-0">
                                        <div className="text-xs font-bold text-slate-800 dark:text-slate-200 group-hover:text-modtale-accent truncate">{member.username}</div>
                                        <div className="flex items-center gap-1.5 mt-0.5">
                                            <span className="text-[10px] text-slate-500 uppercase font-bold tracking-wider truncate">{roleName}</span>
                                        </div>
                                    </div>
                                </Link>
                            );
                        })}
                    </div>
                </SidebarSection>
            )}

            {contributors.length > 0 && (
                <SidebarSection title={author?.accountType === 'ORGANIZATION' ? "Project Contributors" : "Team Members"} icon={Users}>
                    <div className="flex flex-col gap-2">
                        {contributors.map(contributor => {
                            const teamMembership = project.teamMembers?.find(m => m.userId === contributor.id);
                            const role = project.projectRoles?.find(r => r.id === teamMembership?.roleId);

                            return (
                                <Link
                                    key={contributor.id}
                                    to={SiteRoutes.creator(contributor.username)}
                                    className="flex items-center gap-3 p-2 rounded-xl hover:bg-slate-100 dark:hover:bg-white/5 transition-colors group"
                                >
                                    <OptimizedImage
                                        src={contributor.avatarUrl}
                                        alt={`${contributor.username} Avatar`}
                                        baseWidth={32}
                                        className="w-8 h-8 rounded-lg border border-slate-200 dark:border-white/5 shrink-0"
                                    />
                                    <div className="min-w-0">
                                        <div className="text-xs font-bold text-slate-800 dark:text-slate-200 group-hover:text-modtale-accent truncate">{contributor.username}</div>
                                        {role ? (
                                            <div className="flex items-center gap-1.5 mt-0.5">
                                                <div className="w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ backgroundColor: role.color }} />
                                                <span className="text-[10px] text-slate-500 uppercase font-bold tracking-wider truncate">{role.name}</span>
                                            </div>
                                        ) : (
                                            <div className="flex items-center gap-1.5 mt-0.5">
                                                <span className="text-[10px] text-slate-500 uppercase font-bold tracking-wider truncate">Contributor</span>
                                            </div>
                                        )}
                                    </div>
                                </Link>
                            );
                        })}
                    </div>
                </SidebarSection>
            )}

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
                                            <span className="font-mono opacity-75">v{dep.versionNumber}</span>
                                        </div>
                                    </div>
                                </Link>
                            );
                        })}
                    </div>
                </SidebarSection>
            )}

            {licenseInfo && (
                <SidebarSection title="License" icon={Scale}>
                    <div className="text-xs font-bold">
                        {licenseInfo.url ? (
                            <a href={licenseInfo.url} target="_blank" rel="noopener noreferrer" className="block text-center p-2 rounded-lg bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/5 text-slate-700 dark:text-slate-300 hover:text-modtale-accent hover:border-modtale-accent transition-all truncate">
                                {licenseInfo.name}
                            </a>
                        ) : (
                            <div className="text-center p-2 rounded-lg bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/5 text-slate-700 dark:text-slate-300 truncate">
                                {licenseInfo.name}
                            </div>
                        )}
                    </div>
                </SidebarSection>
            )}

            <SidebarSection title="Project ID" icon={Hash}>
                <div className="flex items-center justify-between group bg-white dark:bg-slate-900/50 p-3 rounded-xl border border-slate-200 dark:border-white/5">
                    <code className="text-xs font-mono text-slate-700 dark:text-slate-300">{project.id}</code>
                    <button onClick={handleCopyId} aria-label="Copy Project ID" className="text-slate-500 hover:text-modtale-accent transition-colors">
                        {copiedId ? <Check className="w-4 h-4 text-green-600 dark:text-green-500" /> : <Copy className="w-4 h-4" />}
                    </button>
                </div>
            </SidebarSection>
        </div>
    );
});
Sidebar.displayName = 'Sidebar';