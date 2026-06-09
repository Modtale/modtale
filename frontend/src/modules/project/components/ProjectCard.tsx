import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Download, Calendar, Heart, Box, ChevronRight } from 'lucide-react';
import { BACKEND_URL } from '@/utils/api';
import { Link } from 'react-router-dom';
import { SiteRoutes } from '@/utils/routes';
import { prefetchProject } from '@/utils/prefetch';
import { OptimizedImage } from '@/components/ui/OptimizedImage';
import { formatTimeAgo, getClassificationIcon, toTitleCase } from '@/utils/modHelpers';
import type { Project } from '@/types';

interface ProjectCardProps {
    project: Project;
    path?: string;
    isFavorite: boolean;
    onToggleFavorite: (projectId: string) => void;
    isLoggedIn: boolean;
    priority?: boolean;
    viewStyle?: 'grid' | 'list' | 'compact';
    onReady?: (projectId: string) => void;
    isVisible?: boolean;
}

type IdleWindow = Window & typeof globalThis & {
    requestIdleCallback?: (callback: () => void, options?: { timeout: number }) => number;
    cancelIdleCallback?: (handle: number) => void;
};

export const ProjectCard: React.FC<ProjectCardProps> = React.memo(({ project, path, isFavorite, onToggleFavorite, isLoggedIn, priority = false, viewStyle = 'grid', onReady, isVisible = true }) => {
    const title = project.title || 'Untitled Project';
    const author = project.author || 'Unknown';
    const authorPath = project.authorId ? SiteRoutes.creator(project.authorId, author) : null;

    const canonicalPath = path || SiteRoutes.project(project);

    const desc = project.description ? project.description : 'No description provided.';
    const classification = project.classification || 'PLUGIN';

    const downloads = (project.downloadCount || 0).toLocaleString();
    const favorites = (project.favoriteCount || 0).toLocaleString();

    const timeAgo = project.updatedAt ? formatTimeAgo(project.updatedAt) : null;
    const childCount = (project.projectIds || project.childProjectIds || []).length;
    const displayClassification = toTitleCase(classification);

    const resolveUrl = (url: string) => {
        if (!url) return '';
        if (url.startsWith('/api')) return `${BACKEND_URL}${url}`;
        return url;
    };

    const resolvedImage = project.imageUrl ? resolveUrl(project.imageUrl) : '/assets/favicon.svg';
    const resolvedBanner = project.bannerUrl ? resolveUrl(project.bannerUrl) : null;
    const handleMouseEnter = () => {
        prefetchProject(project.id);
    };

    const BASE_HOVER_CLASSES = "hover:border-transparent transition-all duration-300 z-0 hover:z-10";
    const VISIBILITY_CLASSES = isVisible ? 'opacity-100 translate-y-0 pointer-events-auto' : 'opacity-0 translate-y-2 pointer-events-none';
    const hasReportedReady = useRef(false);
    const [shouldLoadBanner, setShouldLoadBanner] = useState(false);

    const reportReady = useCallback(() => {
        if (hasReportedReady.current) return;
        hasReportedReady.current = true;
        onReady?.(project.id);
    }, [onReady, project.id]);

    useEffect(() => {
        hasReportedReady.current = false;
    }, [project.id]);

    useEffect(() => {
        if (!resolvedBanner || !isVisible || typeof window === 'undefined') {
            setShouldLoadBanner(false);
            return;
        }

        const idleWindow = window as IdleWindow;
        let idleHandle: number | null = null;
        let timeoutHandle: number | null = null;

        const scheduleBannerLoad = () => setShouldLoadBanner(true);

        if (idleWindow.requestIdleCallback) {
            idleHandle = idleWindow.requestIdleCallback(scheduleBannerLoad, { timeout: priority ? 900 : 1400 });
        } else {
            timeoutHandle = window.setTimeout(scheduleBannerLoad, priority ? 180 : 320);
        }

        return () => {
            if (idleHandle !== null && idleWindow.cancelIdleCallback) {
                idleWindow.cancelIdleCallback(idleHandle);
            }
            if (timeoutHandle !== null) {
                window.clearTimeout(timeoutHandle);
            }
        };
    }, [isVisible, priority, resolvedBanner]);

    if (viewStyle === 'compact') {
        return (
            <div
                onMouseEnter={handleMouseEnter}
                className={`group relative flex items-center gap-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl p-3 shadow-sm transform-gpu hover:shadow-md hover:shadow-modtale-accent/5 hover:-translate-y-1.5 transition-[opacity,transform] duration-300 ${BASE_HOVER_CLASSES} ${VISIBILITY_CLASSES}`}
            >
                <div className="absolute inset-0 z-50 pointer-events-none rounded-xl ring-0 group-hover:ring-2 group-hover:ring-inset group-hover:ring-blue-600 dark:group-hover:ring-blue-500 transition-all duration-300" aria-hidden="true" />
                <Link to={canonicalPath} className="absolute inset-0 z-10" />

                <Link to={canonicalPath} aria-label={`View ${title}`} className="w-12 h-12 rounded-lg bg-transparent backdrop-blur-md shadow-sm border-2 border-white dark:border-slate-800 ring-1 ring-black/5 dark:ring-white/10 overflow-hidden transform-gpu shrink-0 group-hover:-translate-y-1 transition-transform duration-500 relative z-30 focus:outline-none">
                    <OptimizedImage src={resolvedImage} alt={title} baseWidth={48} priority={priority} className="w-full h-full bg-transparent object-cover" initialQuality="standard" onFirstLoad={reportReady} />
                </Link>
                <div className="flex-1 min-w-0 relative z-20 pointer-events-none">
                    <h3 className="text-sm font-bold text-slate-900 dark:text-white truncate leading-tight group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-all">
                        {title}
                    </h3>
                    <div className="flex items-center gap-1 text-xs font-medium text-slate-500 mt-1">
                        <span>by</span>
                        {authorPath ? (
                            <Link to={authorPath} onClick={(e) => e.stopPropagation()} className="relative z-30 font-bold hover:text-blue-600 dark:hover:text-blue-400 hover:underline truncate pointer-events-auto">
                                {author}
                            </Link>
                        ) : (
                            <span className="font-bold truncate">{author}</span>
                        )}
                    </div>
                </div>
                <div className="hidden sm:flex flex-col items-end gap-1.5 shrink-0 text-[10px] font-bold text-slate-400 uppercase tracking-tight relative z-20">
                    <div className="flex items-center gap-3">
                        <span className="flex items-center gap-1"><Download className="w-3 h-3" /> {downloads}</span>
                        <span className="flex items-center gap-1"><Heart className={`w-3 h-3 ${isFavorite ? 'text-red-500 fill-current' : ''}`} /> {favorites}</span>
                    </div>
                    <span className="flex items-center gap-1"><Calendar className="w-3 h-3" /> {timeAgo}</span>
                </div>
                <ChevronRight className="w-4 h-4 text-slate-300 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors shrink-0 relative z-20 pointer-events-none" />
            </div>
        );
    }

    if (viewStyle === 'list') {
        return (
            <div
                onMouseEnter={handleMouseEnter}
                className={`group relative flex flex-row items-center sm:items-start gap-4 sm:gap-6 bg-white dark:bg-slate-900 border border-slate-300 dark:border-white/10 rounded-2xl overflow-hidden transform-gpu hover:-translate-y-1.5 shadow-lg hover:shadow-2xl dark:shadow-xl hover:shadow-modtale-accent/10 p-4 sm:p-5 transition-[opacity,transform] duration-300 ${BASE_HOVER_CLASSES} ${VISIBILITY_CLASSES}`}
            >
                <div className="absolute inset-0 z-50 pointer-events-none rounded-2xl ring-0 group-hover:ring-[3px] group-hover:ring-inset group-hover:ring-blue-600 dark:group-hover:ring-blue-500 transition-all duration-300" aria-hidden="true" />
                <Link to={canonicalPath} className="absolute inset-0 z-10" />

                <Link to={canonicalPath} aria-label={`View ${title}`} className="w-24 h-24 sm:w-32 sm:h-32 rounded-xl bg-transparent backdrop-blur-md shadow-xl border-2 sm:border-4 border-white dark:border-slate-800 ring-1 ring-black/5 dark:ring-white/10 overflow-hidden transform-gpu shrink-0 group-hover:-translate-y-1 transition-transform duration-500 relative z-30 focus:outline-none">
                    <OptimizedImage src={resolvedImage} alt={title} baseWidth={128} priority={priority} className="w-full h-full bg-transparent object-cover group-hover:scale-105 transition-transform duration-700" initialQuality="standard" onFirstLoad={reportReady} />
                </Link>

                <div className="flex-1 min-w-0 flex flex-col justify-center sm:justify-start relative z-20 pointer-events-none">
                    <div className="flex justify-between items-start gap-2 sm:gap-4">
                        <div className="min-w-0 flex-1">
                            <h3 className="text-base sm:text-xl font-black text-slate-900 dark:text-white group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors truncate tracking-tight">
                                <Link to={canonicalPath} className="focus:outline-none relative z-30 pointer-events-auto">
                                    {title}
                                </Link>
                            </h3>
                            <div className="flex items-center gap-1 text-xs sm:text-sm font-bold text-slate-500 mt-0.5 sm:mt-1">
                                <span>by</span>
                                {authorPath ? (
                                    <Link to={authorPath} onClick={(e) => e.stopPropagation()} className="relative z-30 hover:text-blue-600 dark:hover:text-blue-400 hover:underline truncate block pointer-events-auto">
                                        {author}
                                    </Link>
                                ) : (
                                    <span className="truncate block">{author}</span>
                                )}
                            </div>
                        </div>
                        <div className="hidden sm:flex bg-slate-100 dark:bg-white/5 px-2.5 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-wider items-center gap-1.5 shrink-0 border border-slate-200 dark:border-white/5 pointer-events-none">
                            <span className="text-blue-600 dark:text-blue-400">{getClassificationIcon(classification)}</span>
                            {displayClassification}
                        </div>
                    </div>
                    <p className="mt-1.5 sm:mt-3 text-xs sm:text-sm text-slate-600 dark:text-slate-400 line-clamp-2 flex-1 leading-relaxed pointer-events-none">{desc}</p>
                    <div className="mt-3 sm:mt-5 flex items-center gap-4 sm:gap-6 text-[10px] sm:text-[11px] font-bold text-slate-400 uppercase tracking-widest">
                        <span className="flex items-center gap-1 sm:gap-1.5 pointer-events-none"><Download className="w-3.5 h-3.5 sm:w-4 sm:h-4" /> {downloads}</span>
                        <button
                            disabled={!isLoggedIn}
                            onClick={(e) => {
                                e.preventDefault();
                                e.stopPropagation();
                                if(isLoggedIn) onToggleFavorite(project.id);
                            }}
                            className={`flex items-center gap-1 sm:gap-1.5 transition-colors relative z-30 pointer-events-auto ${
                                !isLoggedIn
                                    ? 'text-slate-300 dark:text-white/10 cursor-not-allowed'
                                    : isFavorite
                                        ? 'text-red-500'
                                        : 'text-slate-400 hover:text-red-400'
                            }`}
                        >
                            <Heart className={`w-3.5 h-3.5 sm:w-4 sm:h-4 ${isFavorite ? 'fill-current' : ''}`} /> {favorites}
                        </button>
                        <span className="flex items-center gap-1 sm:gap-1.5 pointer-events-none"><Calendar className="w-3.5 h-3.5 sm:w-4 sm:h-4" /> {timeAgo}</span>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div
            onMouseEnter={handleMouseEnter}
            className={`group relative flex flex-col h-full bg-white dark:bg-slate-900 border border-slate-300 dark:border-white/20 rounded-2xl overflow-hidden transform-gpu hover:-translate-y-1.5 shadow-lg hover:shadow-2xl dark:shadow-xl hover:shadow-modtale-accent/10 transition-[opacity,transform] duration-300 ${BASE_HOVER_CLASSES} ${VISIBILITY_CLASSES}`}
            role="article"
            aria-label={`Project: ${title} by ${author}`}
        >
            <div className="absolute inset-0 z-50 pointer-events-none rounded-2xl ring-0 group-hover:ring-[3px] group-hover:ring-inset group-hover:ring-blue-600 dark:group-hover:ring-blue-500 transition-all duration-300" aria-hidden="true" />

            <Link
                to={canonicalPath}
                className="absolute inset-0 z-10 focus:outline-none"
                aria-hidden="true"
                tabIndex={-1}
            />

            <div className={`w-full aspect-[3/1] relative border-b border-slate-100 dark:border-white/5 overflow-hidden rounded-t-2xl transform-gpu shrink-0 z-20 ${resolvedBanner ? 'bg-slate-200 dark:bg-slate-800' : 'bg-slate-200 dark:bg-slate-800'} pointer-events-none`}>
                {resolvedBanner && shouldLoadBanner ? (
                    <OptimizedImage
                        src={resolvedBanner}
                        alt=""
                        priority={false}
                        baseWidth={640}
                        className="w-full h-full opacity-80 group-hover:opacity-100 group-hover:scale-105 transition-all duration-700 bg-transparent object-cover"
                    />
                ) : null}

                <div className="absolute inset-0 bg-gradient-to-t from-white via-white/20 dark:from-slate-900 dark:via-slate-900/20 to-transparent pointer-events-none" />

                <div className="absolute top-3 right-3 z-40">
                    <div className="bg-white/90 dark:bg-slate-900/95 backdrop-blur-md text-slate-800 dark:text-white text-[11px] font-bold px-2.5 py-1.5 rounded-lg flex items-center border border-slate-200 dark:border-white/10 shadow-sm relative pointer-events-none">
                        <span className="mr-1.5 text-blue-600 dark:text-blue-400">{getClassificationIcon(classification)}</span>
                        <span>{displayClassification}</span>
                    </div>
                </div>
            </div>

            <div className="px-6 pb-6 relative flex flex-col flex-1 bg-transparent z-20 pointer-events-none">
                <Link to={canonicalPath} aria-label={`View ${title}`} className="w-20 h-20 rounded-2xl bg-transparent backdrop-blur-md shadow-xl border-4 border-white dark:border-slate-800 overflow-hidden absolute -top-10 group-hover:-translate-y-1 transition-transform duration-500 ring-1 ring-black/5 dark:ring-white/10 z-30 pointer-events-auto focus:outline-none">
                    <OptimizedImage
                        src={resolvedImage}
                        alt={title}
                        baseWidth={80}
                        priority={priority}
                        className="w-full h-full bg-transparent object-cover"
                        initialQuality="standard"
                        onFirstLoad={reportReady}
                    />
                    {classification === 'MODPACK' && childCount > 0 && (
                        <div className="absolute bottom-0 right-0 bg-slate-900/75 backdrop-blur-sm text-white text-[10px] font-bold px-1 py-0.5 rounded-tl-xl flex items-center">
                            <Box className="w-3 h-3 mr-0.5" /> {childCount}
                        </div>
                    )}
                </Link>

                <div className="mt-12">
                    <h3 className="text-xl font-black text-slate-900 dark:text-white group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors truncate tracking-tight" title={title}>
                        <Link
                            to={canonicalPath}
                            className="relative z-30 focus:outline-none pointer-events-auto"
                        >
                            {title}
                        </Link>
                    </h3>

                    <div className="flex items-center gap-1 text-sm text-slate-500 dark:text-slate-400 font-medium truncate mt-1">
                        <span>By</span>
                        {authorPath ? (
                            <Link
                                to={authorPath}
                                onClick={(e) => e.stopPropagation()}
                                className="hover:text-blue-600 dark:hover:text-blue-400 hover:underline focus:outline-none relative z-30 pointer-events-auto"
                            >
                                {author}
                            </Link>
                        ) : (
                            <span>{author}</span>
                        )}
                    </div>
                </div>

                <div className="mt-3 flex-1 relative z-0 pointer-events-none">
                    <p className="text-slate-600 dark:text-slate-400 text-sm line-clamp-2 leading-relaxed">
                        {desc}
                    </p>
                </div>

                <div className="mt-5 flex items-center justify-between text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest">
                    <div className="flex items-center gap-4">
                        <span className="flex items-center gap-1.5 pointer-events-none"><Download className="w-4 h-4" /> {downloads}</span>

                        <button
                            disabled={!isLoggedIn}
                            onClick={(e) => {
                                e.preventDefault();
                                e.stopPropagation();
                                if(isLoggedIn) onToggleFavorite(project.id);
                            }}
                            className={`flex items-center gap-1.5 transition-colors relative z-30 pointer-events-auto ${
                                !isLoggedIn
                                    ? 'text-slate-300 dark:text-white/10 cursor-not-allowed'
                                    : isFavorite
                                        ? 'text-red-500'
                                        : 'text-slate-400 hover:text-red-400'
                            }`}
                        >
                            <Heart className={`w-4 h-4 ${isFavorite ? 'fill-current' : ''}`} />
                            {favorites}
                        </button>
                    </div>
                    <div className="flex items-center gap-1.5 pointer-events-none">
                        <Calendar className="w-4 h-4" />
                        <span suppressHydrationWarning>{timeAgo || 'Unknown'}</span>
                    </div>
                </div>
            </div>
        </div>
    );
}, (p, n) => {
    return (
        p.project.id === n.project.id &&
        p.project.updatedAt === n.project.updatedAt &&
        p.project.favoriteCount === n.project.favoriteCount &&
        p.isFavorite === n.isFavorite &&
        p.isLoggedIn === n.isLoggedIn &&
        p.priority === n.priority &&
        p.viewStyle === n.viewStyle &&
        p.isVisible === n.isVisible
    );
});

ProjectCard.displayName = 'ProjectCard';
