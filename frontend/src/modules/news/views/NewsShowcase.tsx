import React from 'react';
import { ArrowUpRight, Rss } from 'lucide-react';
import { Link } from 'react-router-dom';
import { MarqueeRow } from '@/modules/home/components/HeroMarquee';
import { ProjectCard, ProjectCardSkeleton } from '@/modules/project/components/ProjectCard';
import { SiteRoutes } from '@/utils/routes';
import type { Project } from '@/types';

const ProjectSkeletonStrip = () => (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <ProjectCardSkeleton />
        <ProjectCardSkeleton />
    </div>
);

export const NewsHeroShowcase = ({
    projects,
    loading,
}: {
    projects: Project[];
    loading: boolean;
}) => {
    const marqueeProjects = projects.filter((project) => Boolean(project.imageUrl)).slice(0, 8);
    const cardProjects = projects.slice(0, 2);

    return (
        <div className="relative overflow-hidden rounded-[2rem] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-950 shadow-2xl shadow-slate-200/70 dark:shadow-black/30">
            <div className="absolute inset-0 pointer-events-none bg-[linear-gradient(135deg,rgba(59,130,246,0.14),rgba(16,185,129,0.08)_46%,transparent_76%)]" />
            <div className="relative p-5 sm:p-7">
                <div className="flex items-center justify-between gap-4 mb-5">
                    <img src="/assets/logo.svg" alt="Modtale" className="h-8 w-auto dark:hidden" />
                    <img src="/assets/logo_light.svg" alt="Modtale" className="hidden h-8 w-auto dark:block" />
                    <a
                        href="/rss.xml"
                        className="inline-flex items-center gap-2 rounded-full bg-orange-50 dark:bg-orange-500/10 px-3 py-1.5 text-xs font-black uppercase tracking-wide text-orange-700 dark:text-orange-300"
                    >
                        <Rss className="w-3.5 h-3.5" />
                        RSS
                    </a>
                </div>

                <div className="rounded-3xl bg-slate-100/80 dark:bg-white/[0.04] border border-slate-200 dark:border-white/10 p-4 sm:p-5 overflow-hidden">
                    {marqueeProjects.length >= 2 ? (
                        <div className="space-y-4">
                            <MarqueeRow projects={marqueeProjects} duration="42s" />
                            <MarqueeRow projects={[...marqueeProjects].reverse()} duration="48s" reverse />
                        </div>
                    ) : loading || cardProjects.length === 0 ? (
                        <ProjectSkeletonStrip />
                    ) : (
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            {cardProjects.map((project, index) => (
                                <ProjectCard
                                    key={project.id}
                                    project={project}
                                    isFavorite={false}
                                    onToggleFavorite={() => {}}
                                    isLoggedIn={false}
                                    priority={index === 0}
                                    viewStyle="grid"
                                />
                            ))}
                        </div>
                    )}
                </div>

                <div className="mt-5 grid gap-3 sm:grid-cols-3">
                    <Link to={SiteRoutes.browse()} className="rounded-2xl bg-blue-50 dark:bg-blue-500/10 p-4 group">
                        <span className="text-xs font-black uppercase tracking-wider text-blue-600 dark:text-blue-300">Discover</span>
                        <strong className="mt-1 flex items-center justify-between text-slate-950 dark:text-white">
                            Browse projects <ArrowUpRight className="w-4 h-4 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
                        </strong>
                    </Link>
                    <Link to={SiteRoutes.upload()} className="rounded-2xl bg-emerald-50 dark:bg-emerald-500/10 p-4 group">
                        <span className="text-xs font-black uppercase tracking-wider text-emerald-600 dark:text-emerald-300">Create</span>
                        <strong className="mt-1 flex items-center justify-between text-slate-950 dark:text-white">
                            Publish work <ArrowUpRight className="w-4 h-4 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
                        </strong>
                    </Link>
                    <Link to={SiteRoutes.apiDocs()} className="rounded-2xl bg-slate-100 dark:bg-white/10 p-4 group">
                        <span className="text-xs font-black uppercase tracking-wider text-slate-500 dark:text-slate-300">Build</span>
                        <strong className="mt-1 flex items-center justify-between text-slate-950 dark:text-white">
                            API docs <ArrowUpRight className="w-4 h-4 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
                        </strong>
                    </Link>
                </div>
            </div>
        </div>
    );
};
