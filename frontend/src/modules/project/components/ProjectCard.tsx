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
    viewStyle?: ProjectCardViewStyle;
    onReady?: (projectId: string) => void;
    isVisible?: boolean;
    disableNavigation?: boolean;
}

export type ProjectCardViewStyle = 'grid' | 'list' | 'compact';

interface ProjectCardSkeletonsProps {
    viewStyle: ProjectCardViewStyle;
    count?: number;
}

type IdleWindow = Window & typeof globalThis & {
    requestIdleCallback?: (callback: () => void, options?: { timeout: number }) => number;
    cancelIdleCallback?: (handle: number) => void;
};

const skeletonShimmer = 'absolute inset-0 -translate-x-full animate-[shimmer_1.5s_infinite] bg-gradient-to-r from-transparent via-white/40 dark:via-white/10 to-transparent';
const skeletonPulse = 'animate-pulse bg-slate-200/80 dark:bg-slate-800/60';

export const ProjectCardSkeleton = () => (
    <div className="relative overflow-hidden rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900">
        <div className={`aspect-[3/1] ${skeletonPulse}`} />
        <div className="px-6 pb-6">
            <div className="-mt-10 mb-3 h-20 w-20 rounded-2xl border-4 border-white dark:border-slate-800 bg-slate-300/80 dark:bg-slate-700/70" />
            <div className={`h-6 w-3/5 rounded-lg ${skeletonPulse}`} />
            <div className={`mt-2 h-4 w-2/5 rounded-md ${skeletonPulse}`} />
            <div className={`mt-4 h-4 w-full rounded-md ${skeletonPulse}`} />
            <div className={`mt-2 h-4 w-4/5 rounded-md ${skeletonPulse}`} />
            <div className="mt-5 flex items-center justify-between">
                <div className="flex gap-3">
                    <div className={`h-4 w-16 rounded-md ${skeletonPulse}`} />
                    <div className={`h-4 w-14 rounded-md ${skeletonPulse}`} />
                </div>
                <div className={`h-4 w-16 rounded-md ${skeletonPulse}`} />
            </div>
        </div>
        <div className={skeletonShimmer} />
    </div>
);

export const ListProjectCardSkeleton = () => (
    <div className="relative overflow-hidden rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-4 sm:p-5">
        <div className="flex items-center sm:items-start gap-4 sm:gap-6">
            <div className={`h-24 w-24 sm:h-32 sm:w-32 rounded-xl shrink-0 ${skeletonPulse}`} />
            <div className="flex-1 min-w-0">
                <div className="flex items-start justify-between gap-4">
                    <div className="w-full">
                        <div className={`h-6 w-2/5 rounded-lg ${skeletonPulse}`} />
                        <div className={`mt-2 h-4 w-1/4 rounded-md ${skeletonPulse}`} />
                    </div>
                    <div className={`hidden sm:block h-8 w-24 rounded-lg ${skeletonPulse}`} />
                </div>
                <div className={`mt-3 h-4 w-full rounded-md ${skeletonPulse}`} />
                <div className={`mt-2 h-4 w-4/5 rounded-md ${skeletonPulse}`} />
                <div className="mt-5 flex gap-4 sm:gap-6">
                    <div className={`h-4 w-16 rounded-md ${skeletonPulse}`} />
                    <div className={`h-4 w-14 rounded-md ${skeletonPulse}`} />
                    <div className={`h-4 w-16 rounded-md ${skeletonPulse}`} />
                </div>
            </div>
        </div>
        <div className={skeletonShimmer} />
    </div>
);

export const CompactProjectCardSkeleton = () => (
    <div className="relative overflow-hidden rounded-xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-3">
        <div className="flex items-center gap-4">
            <div className={`h-12 w-12 rounded-lg shrink-0 ${skeletonPulse}`} />
            <div className="flex-1 min-w-0">
                <div className={`h-4 w-1/2 rounded-md ${skeletonPulse}`} />
                <div className={`mt-2 h-3 w-1/3 rounded-md ${skeletonPulse}`} />
            </div>
            <div className="hidden sm:flex flex-col gap-1.5 items-end">
                <div className={`h-3 w-20 rounded-md ${skeletonPulse}`} />
                <div className={`h-3 w-16 rounded-md ${skeletonPulse}`} />
            </div>
            <div className={`h-4 w-4 rounded ${skeletonPulse}`} />
        </div>
        <div className={skeletonShimmer} />
    </div>
);

export const ProjectCardSkeletons: React.FC<ProjectCardSkeletonsProps> = ({ viewStyle, count = 1 }) => {
    const containerClassName = viewStyle === 'grid'
        ? 'grid grid-cols-1 md:grid-cols-2 min-[1800px]:grid-cols-3 gap-4 md:gap-6 mt-4'
        : viewStyle === 'compact'
            ? 'grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3 mt-4'
            : 'space-y-4 mt-4';

    return (
        <div className={containerClassName} aria-hidden="true">
            {[...Array(count)].map((_, index) => (
                <div key={index}>
                    {viewStyle === 'grid' && <ProjectCardSkeleton />}
                    {viewStyle === 'list' && <ListProjectCardSkeleton />}
                    {viewStyle === 'compact' && <CompactProjectCardSkeleton />}
                </div>
            ))}
        </div>
    );
};

export const ProjectCard: React.FC<ProjectCardProps> = React.memo(({ project, path, isFavorite, onToggleFavorite, isLoggedIn, priority = false, viewStyle = 'grid', onReady, isVisible = true, disableNavigation = false }) => {
    const title = project.title || 'Untitled Project';
    const author = project.author || 'Unknown';
    const authorPath = project.authorId ? SiteRoutes.creator(project.authorId, author) : null;

    const canonicalPath = path || SiteRoutes.project(project);

    const desc = project.description ? project.description : 'No description provided.';
    const classification = project.classification || 'PLUGIN';

    const downloads = (project.downloadCount || 0).toLocaleString();

    const timeAgo = project.updatedAt ? formatTimeAgo(project.updatedAt) : null;
    const childCount = (project.childProjectIds || []).length;
    const displayClassification = toTitleCase(classification);
    const baseFavoriteCount = project.favoriteCount || 0;
    const [displayFavoriteCount, setDisplayFavoriteCount] = useState(baseFavoriteCount);
    const favoriteSyncRef = useRef({
        projectId: project.id,
        favoriteCount: baseFavoriteCount,
        isFavorite
    });

    const resolveUrl = (url: string) => {
        if (!url) return '';
        if (url.startsWith('/api')) return `${BACKEND_URL}${url}`;
        return url;
    };

    const resolvedImage = project.imageUrl ? resolveUrl(project.imageUrl) : '/assets/favicon.svg';
    const resolvedBanner = project.bannerUrl ? resolveUrl(project.bannerUrl) : null;
    const handleMouseEnter = () => {
        if (!disableNavigation) {
            prefetchProject(project.id);
        }
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
        if (!isVisible || !priority) return;
        prefetchProject(project.id);
    }, [isVisible, priority, project.id]);

    useEffect(() => {
        hasReportedReady.current = false;
    }, [project.id]);

    useEffect(() => {
        const nextFavoriteCount = project.favoriteCount || 0;
        setDisplayFavoriteCount(nextFavoriteCount);
        favoriteSyncRef.current = {
            projectId: project.id,
            favoriteCount: nextFavoriteCount,
            isFavorite
        };
    }, [project.id, project.favoriteCount]);

    useEffect(() => {
        const previous = favoriteSyncRef.current;
        if (previous.projectId !== project.id) {
            favoriteSyncRef.current = {
                projectId: project.id,
                favoriteCount: project.favoriteCount || 0,
                isFavorite
            };
            return;
        }
        if (previous.isFavorite === isFavorite) return;

        setDisplayFavoriteCount(current => Math.max(0, current + (isFavorite ? 1 : -1)));
        favoriteSyncRef.current = {
            ...previous,
            isFavorite
        };
    }, [isFavorite, project.id, project.favoriteCount]);

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
            idleHandle = idleWindow.requestIdleCallback(scheduleBannerLoad, { timeout: priority ? 200 : 400 });
        } else {
            timeoutHandle = window.setTimeout(scheduleBannerLoad, priority ? 50 : 120);
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

    const favorites = displayFavoriteCount.toLocaleString();

    if (viewStyle === 'compact') {
        return (
            <div
                onMouseEnter={handleMouseEnter}
                className={`group relative flex items-center gap-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl p-3 shadow-sm transform-gpu hover:shadow-md hover:shadow-modtale-accent/5 hover:-translate-y-1.5 transition-[opacity,transform] duration-300 ${BASE_HOVER_CLASSES} ${VISIBILITY_CLASSES}`}
            >
                <div className="absolute inset-0 z-50 pointer-events-none rounded-xl ring-0 group-hover:ring-2 group-hover:ring-inset group-hover:ring-blue-600 dark:group-hover:ring-blue-500 transition-all duration-300" aria-hidden="true" />
                {!disableNavigation && <Link to={canonicalPath} state={{ project }} className="absolute inset-0 z-10" />}

                {disableNavigation ? (
                    <span className="w-12 h-12 rounded-lg bg-transparent backdrop-blur-md shadow-sm border-2 border-white dark:border-slate-800 ring-1 ring-black/5 dark:ring-white/10 overflow-hidden transform-gpu shrink-0 group-hover:-translate-y-1 transition-transform duration-500 relative z-30">
                        <OptimizedImage src={resolvedImage} alt={title} baseWidth={48} priority={priority} className="w-full h-full bg-transparent object-cover" initialQuality="standard" onFirstLoad={reportReady} />
                    </span>
                ) : (
                    <Link to={canonicalPath} state={{ project }} aria-label={`View ${title}`} className="w-12 h-12 rounded-lg bg-transparent backdrop-blur-md shadow-sm border-2 border-white dark:border-slate-800 ring-1 ring-black/5 dark:ring-white/10 overflow-hidden transform-gpu shrink-0 group-hover:-translate-y-1 transition-transform duration-500 relative z-30 focus:outline-none">
                        <OptimizedImage src={resolvedImage} alt={title} baseWidth={48} priority={priority} className="w-full h-full bg-transparent object-cover" initialQuality="standard" onFirstLoad={reportReady} />
                    </Link>
                )}
                <div className="flex-1 min-w-0 relative z-20 pointer-events-none">
                    <h2 className="text-sm font-bold text-slate-900 dark:text-white truncate leading-tight group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-all">
                        {title}
                    </h2>
                    <div className="flex items-center gap-1 text-xs font-medium text-slate-500 mt-1">
                        <span>by</span>
                        {authorPath ? (
                            disableNavigation ? (
                                <span className="relative z-30 font-bold truncate">
                                    {author}
                                </span>
                            ) : (
                                <Link to={authorPath} onClick={(e) => e.stopPropagation()} className="relative z-30 font-bold hover:text-blue-600 dark:hover:text-blue-400 hover:underline truncate pointer-events-auto">
                                    {author}
                                </Link>
                            )
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
                {!disableNavigation && <Link to={canonicalPath} state={{ project }} className="absolute inset-0 z-10" />}

                {disableNavigation ? (
                    <span className="w-24 h-24 sm:w-32 sm:h-32 rounded-xl bg-transparent backdrop-blur-md shadow-xl border-2 sm:border-4 border-white dark:border-slate-800 ring-1 ring-black/5 dark:ring-white/10 overflow-hidden transform-gpu shrink-0 group-hover:-translate-y-1 transition-transform duration-500 relative z-30">
                        <OptimizedImage src={resolvedImage} alt={title} baseWidth={128} priority={priority} className="w-full h-full bg-transparent object-cover group-hover:scale-105 transition-transform duration-700" initialQuality="standard" onFirstLoad={reportReady} />
                    </span>
                ) : (
                    <Link to={canonicalPath} state={{ project }} aria-label={`View ${title}`} className="w-24 h-24 sm:w-32 sm:h-32 rounded-xl bg-transparent backdrop-blur-md shadow-xl border-2 sm:border-4 border-white dark:border-slate-800 ring-1 ring-black/5 dark:ring-white/10 overflow-hidden transform-gpu shrink-0 group-hover:-translate-y-1 transition-transform duration-500 relative z-30 focus:outline-none">
                        <OptimizedImage src={resolvedImage} alt={title} baseWidth={128} priority={priority} className="w-full h-full bg-transparent object-cover group-hover:scale-105 transition-transform duration-700" initialQuality="standard" onFirstLoad={reportReady} />
                    </Link>
                )}

                <div className="flex-1 min-w-0 flex flex-col justify-center sm:justify-start relative z-20 pointer-events-none">
                    <div className="flex justify-between items-start gap-2 sm:gap-4">
                        <div className="min-w-0 flex-1">
                            <h2 className="text-base sm:text-xl font-black text-slate-900 dark:text-white group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors truncate tracking-tight">
                                {disableNavigation ? (
                                    <span className="relative z-30">{title}</span>
                                ) : (
                                    <Link to={canonicalPath} className="focus:outline-none relative z-30 pointer-events-auto">
                                        {title}
                                    </Link>
                                )}
                            </h2>
                            <div className="flex items-center gap-1 text-xs sm:text-sm font-bold text-slate-500 mt-0.5 sm:mt-1">
                                <span>by</span>
                                {authorPath ? (
                                    disableNavigation ? (
                                        <span className="relative z-30 truncate block">{author}</span>
                                    ) : (
                                        <Link to={authorPath} onClick={(e) => e.stopPropagation()} className="relative z-30 hover:text-blue-600 dark:hover:text-blue-400 hover:underline truncate block pointer-events-auto">
                                            {author}
                                        </Link>
                                    )
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
                            aria-label={`${favorites} favorites`}
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

            {!disableNavigation && (
                <Link
                    to={canonicalPath}
                    state={{ project }}
                    className="absolute inset-0 z-10 focus:outline-none"
                    aria-hidden="true"
                    tabIndex={-1}
                />
            )}

            <div className={`w-full aspect-[3/1] relative border-b border-slate-100 dark:border-white/5 overflow-hidden rounded-t-2xl transform-gpu shrink-0 z-20 ${resolvedBanner ? 'bg-slate-200 dark:bg-slate-800' : 'bg-slate-200 dark:bg-slate-800'} pointer-events-none`}>
                {resolvedBanner && shouldLoadBanner ? (
                    <OptimizedImage
                        src={resolvedBanner}
                        alt=""
                        priority={false}
                        baseWidth={640}
                        className="w-full h-full group-hover:scale-105 transition-all duration-700 bg-transparent object-cover"
                    />
                ) : null}

                {!resolvedBanner && (
                    <div className="absolute inset-0 bg-gradient-to-t from-white via-white/20 dark:from-slate-900 dark:via-slate-900/20 to-transparent pointer-events-none" />
                )}

                <div className="absolute top-3 right-3 z-40">
                    <div className="bg-white/90 dark:bg-slate-900/95 backdrop-blur-md text-slate-800 dark:text-white text-[11px] font-bold px-2.5 py-1.5 rounded-lg flex items-center border border-slate-200 dark:border-white/10 shadow-sm relative pointer-events-none">
                        <span className="mr-1.5 text-blue-600 dark:text-blue-400">{getClassificationIcon(classification)}</span>
                        <span>{displayClassification}</span>
                    </div>
                </div>
            </div>

            <div className="px-6 pb-6 relative flex flex-col flex-1 bg-transparent z-20 pointer-events-none">
                {disableNavigation ? (
                    <span className="w-20 h-20 rounded-2xl bg-transparent backdrop-blur-md shadow-xl border-4 border-white dark:border-slate-800 overflow-hidden absolute -top-10 group-hover:-translate-y-1 transition-transform duration-500 ring-1 ring-black/5 dark:ring-white/10 z-30 pointer-events-auto">
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
                    </span>
                ) : (
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
                )}

                <div className="mt-12">
                    <h2 className="text-xl font-black text-slate-900 dark:text-white group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors truncate tracking-tight" title={title}>
                        {disableNavigation ? (
                            <span className="relative z-30">{title}</span>
                        ) : (
                            <Link
                                to={canonicalPath}
                                className="relative z-30 focus:outline-none pointer-events-auto"
                            >
                                {title}
                            </Link>
                        )}
                    </h2>

                    <div className="flex items-center gap-1 text-sm text-slate-500 dark:text-slate-400 font-medium truncate mt-1">
                        <span>By</span>
                        {authorPath ? (
                            disableNavigation ? (
                                <span className="relative z-30">{author}</span>
                            ) : (
                                <Link
                                    to={authorPath}
                                    onClick={(e) => e.stopPropagation()}
                                    className="hover:text-blue-600 dark:hover:text-blue-400 hover:underline focus:outline-none relative z-30 pointer-events-auto"
                                >
                                    {author}
                                </Link>
                            )
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
                            aria-label={`${favorites} favorites`}
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
        p.project.title === n.project.title &&
        p.project.description === n.project.description &&
        p.project.slug === n.project.slug &&
        p.project.author === n.project.author &&
        p.project.authorId === n.project.authorId &&
        p.project.imageUrl === n.project.imageUrl &&
        p.project.bannerUrl === n.project.bannerUrl &&
        p.project.classification === n.project.classification &&
        p.project.downloadCount === n.project.downloadCount &&
        p.project.updatedAt === n.project.updatedAt &&
        p.project.favoriteCount === n.project.favoriteCount &&
        p.project.projectIds === n.project.projectIds &&
        p.project.childProjectIds === n.project.childProjectIds &&
        p.path === n.path &&
        p.isFavorite === n.isFavorite &&
        p.isLoggedIn === n.isLoggedIn &&
        p.priority === n.priority &&
        p.viewStyle === n.viewStyle &&
        p.isVisible === n.isVisible &&
        p.disableNavigation === n.disableNavigation
    );
});

ProjectCard.displayName = 'ProjectCard';
