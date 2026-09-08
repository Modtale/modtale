import React, { memo } from 'react';
import { Link } from 'react-router-dom';
import { Download } from 'lucide-react';
import { BACKEND_URL } from '@/utils/api';
import { SiteRoutes } from '@/utils/routes';
import type { Project } from '@/types';

const resolveAssetUrl = (asset?: string | null) => {
    if (!asset) return '/assets/favicon.svg';
    return asset.startsWith('/api') ? `${BACKEND_URL}${asset}` : asset;
};

export const FeaturedModCard = memo(({ project, priority = false }: { project: Project, priority?: boolean }) => {
    const iconUrl = project.imageUrl
        ? resolveAssetUrl(project.imageUrl)
        : '/assets/favicon.svg';

    const bannerUrl = project.bannerUrl
        ? resolveAssetUrl(project.bannerUrl)
        : null;

    const projectUrl = SiteRoutes.project(project);

    return (
        <article className="group relative flex flex-col w-full shrink-0 bg-white dark:bg-slate-900 border border-slate-300 dark:border-white/20 rounded-2xl overflow-hidden isolate hover:-translate-y-1.5 transition-all duration-500 shadow-lg hover:shadow-2xl dark:shadow-xl hover:ring-[3px] hover:ring-blue-600 dark:hover:ring-blue-500 hover:border-transparent">
            <Link
                to={projectUrl}
                className="absolute inset-0 z-30 focus:outline-none"
                aria-label={`Download ${project.title} Hytale Mod`}
            />

            <div className={`w-full aspect-[3/1] relative border-b border-slate-100 dark:border-white/5 rounded-t-2xl overflow-hidden shrink-0 ${bannerUrl ? 'bg-transparent' : 'bg-slate-200 dark:bg-slate-800'}`}>
                {bannerUrl ? (
                    <img
                        src={bannerUrl}
                        alt={`${project.title} Banner`}
                        loading={priority ? 'eager' : 'lazy'}
                        fetchPriority={priority ? 'high' : 'auto'}
                        decoding="async"
                        className="w-full h-full opacity-80 group-hover:opacity-100 group-hover:scale-105 transition-all duration-700 bg-transparent object-cover"
                    />
                ) : (
                    <div className="absolute inset-0 bg-gradient-to-t from-white via-white/20 dark:from-slate-900 dark:via-slate-900/20 to-transparent pointer-events-none" />
                )}
            </div>

            <div className="px-4 sm:px-6 pb-4 sm:pb-6 relative flex flex-col flex-1 bg-transparent">
                <div className="w-12 h-12 sm:w-16 sm:h-16 rounded-xl sm:rounded-2xl absolute -top-6 sm:-top-8 group-hover:-translate-y-1 transition-transform duration-500 z-20 overflow-hidden border-4 border-white dark:border-slate-800 shadow-xl bg-transparent backdrop-blur-md">
                    <img
                        src={iconUrl}
                        alt={`${project.title} Icon`}
                        loading={priority ? 'eager' : 'lazy'}
                        fetchPriority={priority ? 'high' : 'auto'}
                        decoding="async"
                        className="w-full h-full bg-transparent object-cover"
                    />
                </div>

                <div className="mt-8 sm:mt-10 flex-1 relative z-20 pointer-events-none">
                    <h3 className="text-lg sm:text-xl font-black text-slate-900 dark:text-white group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors truncate tracking-normal">
                        {project.title}
                    </h3>
                    <div className="flex items-center gap-1 text-xs sm:text-sm text-slate-500 dark:text-slate-400 font-medium truncate mt-1">
                        <span>By</span>
                        <Link
                            to={SiteRoutes.creator(project.authorId, project.author)}
                            className="hover:text-blue-600 dark:hover:text-blue-400 hover:underline focus:outline-none relative z-40 pointer-events-auto"
                            aria-label={`View profile for ${project.author}`}
                            onClick={(e) => e.stopPropagation()}
                        >
                            {project.author}
                        </Link>
                    </div>
                </div>

                <div className="mt-3 sm:mt-4 flex items-center gap-2 relative z-20 pointer-events-none text-slate-500 dark:text-slate-400 uppercase tracking-widest font-bold">
                    <Download className="w-3.5 h-3.5 sm:w-4 sm:h-4 shrink-0" aria-hidden="true" />
                    <span className="text-[11px] sm:text-[13px] leading-none translate-y-[1px]">{project.downloadCount?.toLocaleString() || 0}</span>
                </div>
            </div>
        </article>
    );
});
FeaturedModCard.displayName = 'FeaturedModCard';

export const MarqueeColumn = ({ projects, duration }: { projects: Project[], duration: string }) => (
    <div className="flex flex-col w-[260px] 2xl:w-[320px] shrink-0">
        <div className="flex flex-col gap-6 animate-marquee-up will-change-transform" style={{ '--marquee-duration': duration } as any}>
            {[...projects, ...projects].map((mod, index) => (
                <FeaturedModCard key={`${mod.id}-${index}`} project={mod} priority={index === 0} />
            ))}
        </div>
    </div>
);

export const MarqueeRow = ({ projects, duration, reverse = false }: { projects: Project[], duration: string, reverse?: boolean }) => (
    <div className="flex w-full overflow-hidden shrink-0" style={{
        maskImage: 'linear-gradient(to right, transparent 0, black 20px, black calc(100% - 20px), transparent 100%)',
        WebkitMaskImage: 'linear-gradient(to right, transparent 0, black 20px, black calc(100% - 20px), transparent 100%)'
    }}>
        <div className={`flex w-max ${reverse ? 'animate-marquee-right' : 'animate-marquee-left'} will-change-transform`} style={{ '--marquee-duration': duration } as any}>
            <div className="flex gap-4 pr-4">
                {projects.map((mod, index) => (
                    <div key={`row1-${mod.id}-${index}`} className="w-[220px] sm:w-[280px] shrink-0">
                        <FeaturedModCard project={mod} priority={index === 0 && !reverse} />
                    </div>
                ))}
            </div>
            <div className="flex gap-4 pr-4" aria-hidden="true">
                {projects.map((mod, index) => (
                    <div key={`row2-${mod.id}-${index}`} className="w-[220px] sm:w-[280px] shrink-0">
                        <FeaturedModCard project={mod} priority={false} />
                    </div>
                ))}
            </div>
        </div>
    </div>
);
