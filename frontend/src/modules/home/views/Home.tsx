import React, { useState, useEffect, useMemo, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import { Search, Upload, Github, Code } from 'lucide-react';
import '@fontsource-variable/inter';
import { api } from '@/utils/api';
import { ROUTE_SEO } from '@/data/seo-constants';
import type { Project, User } from '@/types';
import { SiteRoutes } from '@/utils/routes';
import { useSSRData } from '@/context/SSRContext';

import { MarqueeColumn } from '../components/HeroMarquee';
import {
    TrendingProjectsSection,
    NewReleasesSection,
    DirectDownloadsSection,
    SmartDependenciesSection,
    ProjectAnalyticsSection,
    CommunityThreadsSection,
    RealTimeAlertsSection,
    AccountPreferencesSection
} from '../components/FeaturePreviews';
import { GLASS_CARD } from '../styles';

const LazySection = ({ children, minHeight }: { children: React.ReactNode, minHeight: string }) => {
    const [isVisible, setIsVisible] = useState(false);
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const observer = new IntersectionObserver(([entry]) => {
            if (entry.isIntersecting) {
                if ('requestIdleCallback' in window) {
                    (window as any).requestIdleCallback(() => setIsVisible(true));
                } else {
                    setTimeout(() => setIsVisible(true), 50);
                }
                observer.disconnect();
            }
        }, { rootMargin: '600px' });

        if (ref.current) observer.observe(ref.current);
        return () => observer.disconnect();
    }, []);

    return (
        <div ref={ref} style={{ minHeight: isVisible ? 'auto' : minHeight }} className="w-full contain-content">
            {isVisible && children}
        </div>
    );
};

const dedupeProjects = (items: Project[]) => Array.from(new Map(items.map((project) => [project.id, project])).values());

const FeatureShowcaseSection = ({
    children,
    glowFrom,
    glowTo,
    align = 'left',
}: {
    children: React.ReactNode;
    glowFrom: string;
    glowTo: string;
    align?: 'left' | 'right';
}) => {
    const primaryGlowPosition = align === 'left'
        ? { left: '-8rem', right: 'auto' }
        : { right: '-8rem', left: 'auto' };
    const secondaryGlowPosition = align === 'left'
        ? { right: '-6rem', left: 'auto' }
        : { left: '-6rem', right: 'auto' };

    return (
        <section className="relative isolate overflow-hidden py-20 sm:py-28 border-t border-slate-200/60 dark:border-white/[0.04]">
            <div className="absolute inset-0 bg-gradient-to-b from-[#0b1220] via-[#08111d] to-[#070e19]" />
            <div
                className="absolute top-[-4rem] h-56 sm:h-72 w-56 sm:w-72 rounded-full blur-3xl opacity-35 pointer-events-none"
                style={{
                    ...primaryGlowPosition,
                    background: `radial-gradient(circle, ${glowFrom} 0%, transparent 72%)`
                }}
            />
            <div
                className="absolute bottom-[-5rem] h-64 sm:h-80 w-64 sm:w-80 rounded-full blur-3xl opacity-25 pointer-events-none"
                style={{
                    ...secondaryGlowPosition,
                    background: `radial-gradient(circle, ${glowTo} 0%, transparent 74%)`
                }}
            />
            <div
                className="absolute inset-0 opacity-100 pointer-events-none"
                style={{
                    backgroundImage: `
                        radial-gradient(circle at 1px 1px, rgba(148, 163, 184, 0.09) 1px, transparent 0),
                        linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 18%, transparent 78%, rgba(255, 255, 255, 0.015)),
                        linear-gradient(120deg, rgba(59, 130, 246, 0) 0%, rgba(59, 130, 246, 0.045) 48%, rgba(59, 130, 246, 0) 100%)
                    `,
                    backgroundSize: '20px 20px, 100% 100%, 100% 100%'
                }}
            />
            <div
                className="absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-black/25 to-transparent pointer-events-none"
            />
            <div
                className="absolute inset-0 pointer-events-none"
                style={{
                    background: 'linear-gradient(90deg, rgba(15, 23, 42, 0.12), transparent 12%, transparent 88%, rgba(15, 23, 42, 0.12))'
                }}
            />

            <div className="max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 relative z-20">
                <LazySection minHeight="450px">
                    {children}
                </LazySection>
            </div>
        </section>
    );
};

export const Home: React.FC<{
    likedProjectIds?: string[];
    onToggleFavorite?: (projectId: string) => void;
    isLoggedIn?: boolean;
    currentUser?: User | null;
}> = ({
    likedProjectIds = [],
    onToggleFavorite = () => {},
    isLoggedIn = false,
    currentUser = null,
}) => {
    const DESKTOP_BREAKPOINT = 1024;
    const DESKTOP_HERO_MIN_WIDTH_ENTER = 1260;
    const DESKTOP_HERO_MIN_WIDTH_EXIT = 1180;
    const HERO_MARQUEE_PROJECT_LIMIT = 8;

    const { initialData: ssrData } = useSSRData();
    const homeSeo = ROUTE_SEO['/'];
    const initialTrendingProjects = ssrData?.homeTrendingProjects || ssrData?.homeProjects || [];
    const initialNewestProjects = ssrData?.homeNewestProjects || [];

    const [isDesktop, setIsDesktop] = useState(false);
    const [useDesktopHeroLayout, setUseDesktopHeroLayout] = useState(false);
    const [projects, setProjects] = useState<Project[]>(initialTrendingProjects);
    const [newestProjects, setNewestProjects] = useState<Project[]>(initialNewestProjects);
    const [stats, setStats] = useState(ssrData?.stats || { totalProjects: 0, totalDownloads: 0, totalUsers: 0 });
    const heroGridRef = useRef<HTMLDivElement>(null);
    const heroTextColumnRef = useRef<HTMLDivElement>(null);
    const heroMarqueeDesktopRef = useRef<HTMLDivElement>(null);
    const heroActionsRef = useRef<HTMLElement>(null);
    const desktopHeroRetryGridWidthRef = useRef<number | null>(null);

    const formatMetric = (value?: number) => (value || 0).toLocaleString();

    useEffect(() => {
        const handleResize = () => setIsDesktop(window.innerWidth >= DESKTOP_BREAKPOINT);
        handleResize();
        window.addEventListener('resize', handleResize, { passive: true });

        const shouldFetchFallbackProjects = !initialTrendingProjects.length;
        const shouldFetchFallbackNewest = !initialNewestProjects.length;
        const shouldFetchFallbackStats = !ssrData?.stats?.totalProjects;

        if (shouldFetchFallbackProjects) {
            api.get('/projects', { params: { size: 16, sort: 'relevance', category: 'trending' } })
                .then(res => {
                    if (res.data?.content) setProjects(res.data.content);
                })
                .catch(() => {});
        }

        if (shouldFetchFallbackNewest) {
            api.get('/projects', { params: { size: 12, sort: 'newest' } })
                .then(res => {
                    if (res.data?.content) setNewestProjects(res.data.content);
                })
                .catch(() => {});
        }

        if (shouldFetchFallbackStats) {
            api.get('/analytics/platform/stats')
                .then(res => setStats(res.data))
                .catch(() => {});
        }

        return () => {
            window.removeEventListener('resize', handleResize);
        };
    }, [DESKTOP_BREAKPOINT, initialNewestProjects.length, initialTrendingProjects.length, ssrData?.stats?.totalProjects]);

    useEffect(() => {
        let frameId = 0;
        const DESKTOP_HERO_RETRY_BUFFER = 24;

        const shouldUseDesktopLayout = (currentDesktopLayout: boolean) => {
            const viewportWidth = window.innerWidth;
            if (viewportWidth < DESKTOP_BREAKPOINT) {
                desktopHeroRetryGridWidthRef.current = null;
                return false;
            }

            const gridEl = heroGridRef.current;
            const gridWidth = gridEl?.clientWidth ?? viewportWidth;
            const widthThreshold = currentDesktopLayout ? DESKTOP_HERO_MIN_WIDTH_EXIT : DESKTOP_HERO_MIN_WIDTH_ENTER;
            if (gridWidth < widthThreshold) return false;

            const blockedRetryGridWidth = desktopHeroRetryGridWidthRef.current;
            if (
                !currentDesktopLayout
                && blockedRetryGridWidth !== null
                && gridWidth <= blockedRetryGridWidth + DESKTOP_HERO_RETRY_BUFFER
            ) {
                return false;
            }

            const actionsEl = heroActionsRef.current;
            const textEl = heroTextColumnRef.current;
            const marqueeEl = heroMarqueeDesktopRef.current;

            let hasWrappedButtons = false;
            if (actionsEl) {
                const actionItems = Array.from(actionsEl.children) as HTMLElement[];
                hasWrappedButtons = actionItems.some((item) => item.scrollHeight > item.clientHeight + 1)
                    || (actionsEl.scrollWidth > actionsEl.clientWidth + 1)
                    || (actionItems.length > 1 && actionItems.some((item) => item.offsetTop !== actionItems[0].offsetTop));
            }

            const hasTextOverflow = !!textEl
                && (textEl.scrollWidth > textEl.clientWidth + 1 || textEl.scrollHeight > textEl.clientHeight + 1);

            let hasHeroOverlap = false;
            if (currentDesktopLayout && textEl && marqueeEl) {
                const textRect = textEl.getBoundingClientRect();
                const marqueeRect = marqueeEl.getBoundingClientRect();
                const hasCollision = !(
                    textRect.right <= marqueeRect.left
                    || textRect.left >= marqueeRect.right
                    || textRect.bottom <= marqueeRect.top
                    || textRect.top >= marqueeRect.bottom
                );
                const horizontalGap = marqueeRect.left - textRect.right;
                hasHeroOverlap = hasCollision || horizontalGap < 32;
            }

            const nextDesktopLayout = !hasWrappedButtons && !hasTextOverflow && !hasHeroOverlap;

            if (nextDesktopLayout) {
                desktopHeroRetryGridWidthRef.current = null;
            } else if (currentDesktopLayout) {
                desktopHeroRetryGridWidthRef.current = gridWidth;
            }

            return nextDesktopLayout;
        };

        const recomputeLayout = () => {
            frameId = 0;
            setUseDesktopHeroLayout((currentDesktopLayout) => {
                const nextDesktopLayout = shouldUseDesktopLayout(currentDesktopLayout);
                return currentDesktopLayout === nextDesktopLayout ? currentDesktopLayout : nextDesktopLayout;
            });
        };

        const scheduleRecomputeLayout = () => {
            if (frameId) return;
            frameId = window.requestAnimationFrame(recomputeLayout);
        };

        recomputeLayout();
        scheduleRecomputeLayout();

        const observer = new ResizeObserver(() => scheduleRecomputeLayout());
        if (heroGridRef.current) observer.observe(heroGridRef.current);
        if (heroTextColumnRef.current) observer.observe(heroTextColumnRef.current);
        if (heroMarqueeDesktopRef.current) observer.observe(heroMarqueeDesktopRef.current);
        if (heroActionsRef.current) observer.observe(heroActionsRef.current);

        window.addEventListener('resize', scheduleRecomputeLayout, { passive: true });

        return () => {
            if (frameId) window.cancelAnimationFrame(frameId);
            observer.disconnect();
            window.removeEventListener('resize', scheduleRecomputeLayout);
        };
    }, [
        DESKTOP_BREAKPOINT,
        DESKTOP_HERO_MIN_WIDTH_ENTER,
        DESKTOP_HERO_MIN_WIDTH_EXIT,
        projects.length,
        stats.totalDownloads,
        stats.totalProjects,
        stats.totalUsers
    ]);

    const validFeaturedProjects = useMemo(
        () => projects.filter(p => Boolean(p.bannerUrl) && Boolean(p.imageUrl) && !p.imageUrl?.includes('favicon')),
        [projects]
    );

    const heroMarqueeProjects = useMemo(
        () => validFeaturedProjects.slice(0, HERO_MARQUEE_PROJECT_LIMIT),
        [HERO_MARQUEE_PROJECT_LIMIT, validFeaturedProjects]
    );

    const combinedProjectPool = useMemo(
        () => dedupeProjects([...projects, ...newestProjects]),
        [newestProjects, projects]
    );

    const previewProject = combinedProjectPool.find((project) => Boolean(project.imageUrl)) || validFeaturedProjects[0];
    const trendingSpotlightProjects = useMemo(
        () => dedupeProjects(projects).slice(0, 6),
        [projects]
    );
    const newestSpotlightProjects = useMemo(() => {
        if (newestProjects.length > 0) {
            return dedupeProjects(newestProjects).slice(0, 6);
        }

        return [...combinedProjectPool]
            .sort((a, b) => new Date(b.createdAt || b.updatedAt || 0).getTime() - new Date(a.createdAt || a.updatedAt || 0).getTime())
            .slice(0, 6);
    }, [combinedProjectPool, newestProjects]);
    const col1Projects = useMemo(() => heroMarqueeProjects.filter((_, i) => i % 2 === 0), [heroMarqueeProjects]);
    const col2Projects = useMemo(() => heroMarqueeProjects.filter((_, i) => i % 2 === 1), [heroMarqueeProjects]);
    const isDesktopHeroLayout = isDesktop && useDesktopHeroLayout;

    return (
        <div
            className="min-h-screen bg-slate-50 dark:bg-[#0B1120] text-slate-900 dark:text-slate-300 relative selection:bg-blue-500 selection:text-white overflow-x-hidden transition-colors duration-300"
            style={{ fontFamily: '"Inter Variable", "Inter", system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif' }}
        >
            <Helmet>
                <title>{homeSeo.title}</title>
                <meta name="description" content={homeSeo.description} />
                <meta name="keywords" content={homeSeo.keywords} />
                <link rel="preload" as="image" href="/assets/logo.svg" />
                <style>{`
                    @keyframes marquee-up {
                        from { transform: translateY(20px); }
                        to { transform: translateY(calc(-50% + 20px)); }
                    }
                    .animate-marquee-up {
                        animation: marquee-up var(--marquee-duration, 40s) linear infinite;
                        will-change: transform;
                    }
                    @keyframes marquee-left {
                        from { transform: translateX(24px); }
                        to { transform: translateX(calc(-50% + 24px)); }
                    }
                    .animate-marquee-left {
                        animation: marquee-left var(--marquee-duration, 35s) linear infinite;
                        will-change: transform;
                    }
                    @keyframes marquee-right {
                        from { transform: translateX(calc(-50% - 24px)); }
                        to { transform: translateX(-24px); }
                    }
                    .animate-marquee-right {
                        animation: marquee-right var(--marquee-duration, 35s) linear infinite;
                        will-change: transform;
                    }
                    .contain-content {
                        contain: content;
                    }
                    @media (max-height: 900px) {
                        .home-hero {
                            padding-top: clamp(3.75rem, 4vh, 5rem) !important;
                            padding-bottom: 4vh !important;
                        }
                        .home-hero-copy {
                            gap: 0.75rem !important;
                        }
                        .home-hero-copy h1 {
                            margin-bottom: 0.75rem !important;
                            line-height: 1.02 !important;
                        }
                        .home-hero-copy p {
                            margin-bottom: 1rem !important;
                        }
                        .home-hero-copy nav {
                            margin-bottom: 1rem !important;
                        }
                    }
                    @media (max-height: 760px) {
                        .home-hero {
                            padding-top: clamp(3.5rem, 2vh, 4.5rem) !important;
                            padding-bottom: 2.5vh !important;
                        }
                        .home-hero-copy h1 {
                            font-size: clamp(1.85rem, 5.2vh, 2.7rem) !important;
                            margin-bottom: 0.5rem !important;
                        }
                        .home-hero-copy p {
                            font-size: 0.95rem !important;
                            margin-bottom: 0.75rem !important;
                        }
                        .home-hero-copy nav {
                            margin-bottom: 0.75rem !important;
                        }
                    }
                    @media (max-width: 640px) and (max-height: 700px) {
                        .home-hero {
                            padding-top: 2.75rem !important;
                            padding-bottom: 1.15rem !important;
                            justify-content: flex-start !important;
                        }
                        .home-hero-copy {
                            gap: 0.55rem !important;
                        }
                        .home-hero-logo {
                            height: 3rem !important;
                            margin-bottom: 0.65rem !important;
                        }
                        .home-hero-copy h1 {
                            font-size: clamp(2rem, 8vw, 2.7rem) !important;
                            line-height: 0.98 !important;
                            margin-bottom: 0.45rem !important;
                        }
                        .home-hero-copy p {
                            font-size: 0.93rem !important;
                            line-height: 1.45 !important;
                            margin-bottom: 0.65rem !important;
                        }
                        .home-hero-copy nav {
                            gap: 0.6rem !important;
                            margin-bottom: 0.65rem !important;
                        }
                        .home-hero-copy nav a {
                            height: 3rem !important;
                            font-size: 0.95rem !important;
                            border-radius: 0.9rem !important;
                        }
                        .home-hero-copy nav a svg {
                            width: 0.95rem !important;
                            height: 0.95rem !important;
                            margin-right: 0.4rem !important;
                        }
                        .home-hero-stats {
                            padding: 0.75rem 0.9rem !important;
                            gap: 0.7rem !important;
                        }
                        .home-hero-stat-group {
                            min-width: 0;
                        }
                        .home-hero-stat-value {
                            font-size: 1.35rem !important;
                            line-height: 1 !important;
                        }
                        .home-hero-stat-label {
                            font-size: 0.62rem !important;
                            margin-top: 0.28rem !important;
                            letter-spacing: 0.1em !important;
                        }
                        .home-hero-stat-divider {
                            height: 2.25rem !important;
                        }
                    }
                    @media (min-width: 390px) and (max-width: 640px) and (min-height: 701px) {
                        .home-hero {
                            padding-top: 8rem !important;
                            padding-bottom: 1.1rem !important;
                            justify-content: flex-start !important;
                        }
                        .home-hero-copy {
                            min-height: 0 !important;
                            justify-content: flex-start !important;
                            gap: 0.55rem !important;
                        }
                        .home-hero-copy-primary {
                            display: flex;
                            flex-direction: column;
                            align-items: center;
                            gap: 0.4rem !important;
                            transform: translateY(0.5rem);
                        }
                        .home-hero-logo {
                            height: 2.85rem !important;
                            margin-bottom: 0.4rem !important;
                        }
                        .home-hero-copy h1 {
                            font-size: clamp(2rem, 6.6vw, 2.55rem) !important;
                            line-height: 0.99 !important;
                            margin-bottom: 0.3rem !important;
                        }
                        .home-hero-copy p {
                            font-size: 0.92rem !important;
                            margin-bottom: 0.45rem !important;
                        }
                        .home-hero-copy nav {
                            margin-bottom: 0.4rem !important;
                        }
                        .home-hero-stats {
                            padding: 0.72rem 0.85rem !important;
                            margin-top: 4.8rem !important;
                            width: 100% !important;
                        }
                        .home-hero-stat-value {
                            font-size: 1.35rem !important;
                        }
                        .home-hero-stat-label {
                            font-size: 0.6rem !important;
                        }
                    }
                    @media (min-width: 390px) and (max-width: 414px) and (min-height: 701px) {
                        .home-hero {
                            padding-top: 5rem !important;
                        }
                        .home-hero-copy-primary {
                            transform: translateY(0.25rem);
                        }
                        .home-hero-stats {
                            margin-top: 2.6rem !important;
                        }
                    }
                    @media (min-width: 1024px) {
                        .home-hero.home-hero-desktop {
                            min-height: calc(100vh - 6rem) !important;
                            padding-top: clamp(2.5rem, 3.5vh, 4rem) !important;
                            padding-bottom: clamp(1.25rem, 2.5vh, 2.75rem) !important;
                        }
                        .home-hero-copy.home-hero-copy-desktop {
                            gap: 0.5rem !important;
                        }
                    }
                    @media (min-width: 1600px) and (max-height: 900px) {
                        .home-hero.home-hero-desktop {
                            min-height: calc(100vh - 6rem) !important;
                            padding-top: clamp(2rem, 2.2vh, 2.75rem) !important;
                            padding-bottom: clamp(1rem, 1.8vh, 1.75rem) !important;
                        }
                        .home-hero-desktop-grid {
                            gap: 1.5rem !important;
                        }
                        .home-hero-copy.home-hero-copy-desktop h1 {
                            font-size: clamp(2.9rem, 6vh, 4.6rem) !important;
                            line-height: 1.02 !important;
                            margin-bottom: 0.75rem !important;
                        }
                        .home-hero-copy.home-hero-copy-desktop .home-hero-logo {
                            height: clamp(4.8rem, 9.2vh, 6.5rem) !important;
                            margin-bottom: 1.4rem !important;
                        }
                        .home-hero-copy.home-hero-copy-desktop p {
                            font-size: 1.08rem !important;
                            margin-bottom: 1rem !important;
                            max-width: 38rem !important;
                        }
                        .home-hero-copy.home-hero-copy-desktop nav {
                            margin-bottom: 1rem !important;
                        }
                        .home-hero-copy.home-hero-copy-desktop nav a {
                            height: 3.15rem !important;
                            font-size: 0.98rem !important;
                            padding-left: 1.55rem !important;
                            padding-right: 1.55rem !important;
                        }
                        .home-hero-copy.home-hero-copy-desktop nav a svg {
                            width: 1.05rem !important;
                            height: 1.05rem !important;
                        }
                        .home-hero-copy.home-hero-copy-desktop nav a svg + * {
                            margin-left: 0.55rem !important;
                        }
                        .home-hero-copy.home-hero-copy-desktop .home-hero-stats {
                            padding: 0.9rem 1.25rem !important;
                            gap: 1.2rem !important;
                        }
                        .home-hero-copy.home-hero-copy-desktop .home-hero-stats .home-hero-stat-group {
                            padding: 0.4rem 0.5rem !important;
                        }
                        .home-hero-copy.home-hero-copy-desktop .home-hero-stats span {
                            letter-spacing: inherit;
                        }
                        .home-hero-copy.home-hero-copy-desktop .home-hero-stats .home-hero-stat-value {
                            font-size: 2.15rem !important;
                            line-height: 1 !important;
                            padding: 0.38rem 0.5rem !important;
                        }
                        .home-hero-copy.home-hero-copy-desktop .home-hero-stats .home-hero-stat-divider {
                            height: 3rem !important;
                        }
                        .home-hero-copy.home-hero-copy-desktop .home-hero-stats .home-hero-stat-label {
                            font-size: 0.69rem !important;
                            margin-top: 0.5rem !important;
                            letter-spacing: 0.12em !important;
                            padding-left: 0.5rem !important;
                            padding-right: 0.5rem !important;
                        }
                        .home-hero-desktop-marquee {
                            min-height: 470px !important;
                        }
                    }
                `}</style>
            </Helmet>

            <main className="relative z-10 contain-content">
                <section className={`home-hero ${isDesktopHeroLayout ? 'home-hero-desktop lg:min-h-[92vh] 2xl:min-h-[90vh] lg:pt-[7vh] 2xl:pt-36 lg:pb-[6vh]' : ''} relative w-full min-h-[100dvh] flex flex-col items-center justify-center pt-12 sm:pt-[7vh] pb-6 sm:pb-[5vh] overflow-hidden`}>
                    <div className="absolute inset-0 bg-[repeating-linear-gradient(45deg,transparent,transparent_10px,rgba(59,130,246,0.05)_10px,rgba(59,130,246,0.05)_11px)] dark:bg-[repeating-linear-gradient(45deg,transparent,transparent_10px,rgba(255,255,255,0.03)_10px,rgba(255,255,255,0.03)_11px)] [mask-image:radial-gradient(ellipse_60%_60%_at_50%_50%,#000_70%,transparent_100%)] pointer-events-none transform-gpu" />

                    <div className="absolute top-1/4 -left-1/4 w-[800px] h-[800px] bg-blue-500/10 dark:bg-blue-600/15 rounded-full blur-[120px] mix-blend-multiply dark:mix-blend-screen pointer-events-none transform-gpu" />
                    <div className="absolute bottom-1/4 -right-1/4 w-[600px] h-[600px] bg-indigo-500/10 dark:bg-indigo-600/15 rounded-full blur-[120px] mix-blend-multiply dark:mix-blend-screen pointer-events-none transform-gpu" />

                    <div ref={heroGridRef} className={`relative z-20 w-full max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 ${isDesktopHeroLayout ? 'home-hero-desktop-grid lg:px-20 xl:px-28 lg:grid-cols-2 2xl:gap-20' : ''} grid grid-cols-1 ${isDesktopHeroLayout ? '' : 'lg:grid-cols-1'} gap-6 sm:gap-12 items-stretch`}>

                        <div
                            ref={heroTextColumnRef}
                            className={`home-hero-copy ${isDesktopHeroLayout ? 'home-hero-copy-desktop' : ''} flex flex-col items-center ${isDesktopHeroLayout ? 'lg:items-start text-center lg:text-left lg:mx-0 lg:max-w-xl' : 'text-center lg:text-center lg:mx-auto lg:max-w-2xl'} w-full max-w-2xl 2xl:max-w-2xl justify-center mx-auto`}
                        >
                            <div className="home-hero-copy-primary w-full flex flex-col items-center">
                                <div className="shrink-0">
                                    <img
                                        src="/assets/logo.svg"
                                        alt="Modtale Logo"
                                        width={853}
                                        height={128}
                                        className={`home-hero-logo h-14 sm:h-16 md:h-20 ${isDesktopHeroLayout ? 'lg:h-[6.75rem]' : ''} w-auto mb-4 sm:mb-10 object-contain drop-shadow-sm shrink-0 dark:hidden`}
                                        fetchPriority="high"
                                        decoding="async"
                                    />
                                    <img
                                        src="/assets/logo_light.svg"
                                        alt="Modtale Logo"
                                        width={853}
                                        height={128}
                                        className={`home-hero-logo hidden h-14 sm:h-16 md:h-20 ${isDesktopHeroLayout ? 'lg:h-[6.75rem]' : ''} w-auto mb-4 sm:mb-10 object-contain drop-shadow-sm shrink-0 dark:block`}
                                        fetchPriority="high"
                                        decoding="async"
                                    />
                                </div>

                                <h1 className={`text-4xl sm:text-5xl ${isDesktopHeroLayout ? 'lg:text-6xl 2xl:text-[5.5rem] 2xl:mb-8' : ''} font-black text-slate-900 dark:text-white tracking-tighter leading-[1.05] mb-3 sm:mb-6`}>
                                    The Hytale<br />
                                    <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-600 via-indigo-500 to-blue-500 dark:from-blue-400 dark:via-indigo-400 dark:to-blue-300">
                                        Community<br />Repository
                                    </span>
                                </h1>

                                <p className={`text-base sm:text-lg ${isDesktopHeroLayout ? '2xl:text-xl lg:max-w-lg 2xl:max-w-xl 2xl:mb-12' : ''} text-slate-600 dark:text-slate-300 max-w-2xl mb-6 sm:mb-10 font-medium leading-relaxed`}>
                                    Discover, download, and seamlessly share Hytale projects, worlds, plugins, asset packs, and modpacks.
                                </p>

                                <nav ref={heroActionsRef} aria-label="Primary Actions" className="flex flex-col sm:flex-row items-center gap-3 sm:gap-4 w-full sm:w-auto mb-6 sm:mb-10 2xl:mb-14">
                                    <Link
                                        to={SiteRoutes.browse()}
                                        className={`flex items-center justify-center px-6 ${isDesktopHeroLayout ? 'sm:px-10 h-14 sm:h-16 text-base sm:text-lg' : 'sm:px-8 h-14 sm:h-16 text-base sm:text-lg'} bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-2xl transition-all shadow-[0_8px_32px_rgba(37,99,235,0.25),inset_0_1px_0_rgba(255,255,255,0.2)] hover:shadow-[0_16px_48px_rgba(37,99,235,0.3),inset_0_1px_0_rgba(255,255,255,0.2)] hover:-translate-y-0.5 w-full sm:w-auto ring-1 ring-blue-500 transform-gpu whitespace-nowrap`}
                                    >
                                        <Search className="w-5 h-5 mr-2 sm:mr-3" aria-hidden="true" />
                                        Discover Projects
                                    </Link>
                                    <Link
                                        to={SiteRoutes.upload()}
                                        className={`flex items-center justify-center px-6 ${isDesktopHeroLayout ? 'sm:px-10 h-14 sm:h-16 text-base sm:text-lg' : 'sm:px-8 h-14 sm:h-16 text-base sm:text-lg'} bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 text-slate-900 dark:text-white font-bold rounded-2xl hover:bg-slate-50 dark:hover:bg-slate-700 transition-all w-full sm:w-auto shadow-sm hover:shadow-md hover:-translate-y-0.5 transform-gpu whitespace-nowrap`}
                                    >
                                        <Upload className="w-5 h-5 mr-2 sm:mr-3 text-slate-400 dark:text-slate-500" aria-hidden="true" />
                                        Publish Work
                                    </Link>
                                </nav>
                            </div>

                            <div className={`${GLASS_CARD} home-hero-stats flex flex-row items-center justify-between sm:justify-start gap-2 sm:gap-10 2xl:gap-14 w-full sm:w-fit p-3.5 sm:p-6 lg:p-8 shadow-sm lg:-ml-1.5 contain-content`}>
                                <div className="home-hero-stat-group flex flex-col items-center lg:items-start flex-1 sm:flex-none">
                                    <span className="home-hero-stat-value text-xl sm:text-3xl lg:text-4xl font-black text-slate-900 dark:text-white tracking-tight">
                                        {formatMetric(stats.totalProjects)}
                                    </span>
                                    <span className="home-hero-stat-label text-[9px] sm:text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest mt-1 sm:mt-2">Projects</span>
                                </div>
                                <div className="home-hero-stat-divider w-px h-8 sm:h-12 bg-slate-200 dark:bg-white/10" aria-hidden="true" />
                                <div className="home-hero-stat-group flex flex-col items-center lg:items-start flex-1 sm:flex-none">
                                    <span className="home-hero-stat-value text-xl sm:text-3xl lg:text-4xl font-black text-slate-900 dark:text-white tracking-tight">
                                        {formatMetric(stats.totalDownloads)}
                                    </span>
                                    <span className="home-hero-stat-label text-[9px] sm:text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest mt-1 sm:mt-2">Downloads</span>
                                </div>
                                <div className="home-hero-stat-divider w-px h-8 sm:h-12 bg-slate-200 dark:bg-white/10" aria-hidden="true" />
                                <div className="home-hero-stat-group flex flex-col items-center lg:items-start flex-1 sm:flex-none">
                                    <span className="home-hero-stat-value text-xl sm:text-3xl lg:text-4xl font-black text-slate-900 dark:text-white tracking-tight">
                                        {formatMetric(stats.totalUsers)}
                                    </span>
                                    <span className="home-hero-stat-label text-[9px] sm:text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest mt-1 sm:mt-2">Creators</span>
                                </div>
                            </div>
                        </div>

                        {isDesktopHeroLayout ? (
                            <div className="home-hero-desktop-marquee relative w-full lg:min-h-[520px] 2xl:min-h-[680px]">
                                {validFeaturedProjects.length > 0 && (
                                    <aside
                                        ref={heroMarqueeDesktopRef}
                                        className="absolute -inset-x-4 xl:-inset-x-8 -inset-y-4 px-4 xl:px-8 py-4 flex gap-6 2xl:gap-10 justify-end overflow-hidden"
                                        style={{
                                            maskImage: 'linear-gradient(to bottom, transparent 0, black 120px, black calc(100% - 120px), transparent 100%)',
                                            WebkitMaskImage: 'linear-gradient(to bottom, transparent 0, black 120px, black calc(100% - 120px), transparent 100%)'
                                        }}
                                        aria-label="Trending Hytale Projects Showcase"
                                    >
                                        <MarqueeColumn projects={col1Projects} duration="35s" />
                                        <MarqueeColumn projects={col2Projects} duration="45s" />
                                    </aside>
                                )}
                            </div>
                        ) : null}
                    </div>

                    <div className="absolute bottom-0 left-0 right-0 h-[150px] bg-gradient-to-t from-slate-100/30 dark:from-[#080d19] to-transparent pointer-events-none z-10" />
                </section>

                <div className="w-full bg-slate-50 dark:bg-[#080d19] relative overflow-hidden z-20">
                    <FeatureShowcaseSection glowFrom="rgba(59, 130, 246, 0.08)" glowTo="rgba(148, 163, 184, 0.07)" align="left">
                        <div className="grid grid-cols-1 lg:grid-cols-2 gap-16 xl:gap-24 items-start">
                            <TrendingProjectsSection
                                projects={trendingSpotlightProjects}
                                likedProjectIds={likedProjectIds}
                                onToggleFavorite={onToggleFavorite}
                                isLoggedIn={isLoggedIn}
                            />
                            <NewReleasesSection
                                projects={newestSpotlightProjects}
                                likedProjectIds={likedProjectIds}
                                onToggleFavorite={onToggleFavorite}
                                isLoggedIn={isLoggedIn}
                            />
                        </div>
                    </FeatureShowcaseSection>

                    <FeatureShowcaseSection glowFrom="rgba(168, 85, 247, 0.1)" glowTo="rgba(236, 72, 153, 0.08)" align="left">
                        <DirectDownloadsSection />
                    </FeatureShowcaseSection>

                    <FeatureShowcaseSection glowFrom="rgba(16, 185, 129, 0.1)" glowTo="rgba(20, 184, 166, 0.08)" align="right">
                        <SmartDependenciesSection randomProject={previewProject} />
                    </FeatureShowcaseSection>

                    <FeatureShowcaseSection glowFrom="rgba(59, 130, 246, 0.1)" glowTo="rgba(99, 102, 241, 0.08)" align="left">
                        <ProjectAnalyticsSection />
                    </FeatureShowcaseSection>

                    <FeatureShowcaseSection glowFrom="rgba(99, 102, 241, 0.1)" glowTo="rgba(168, 85, 247, 0.08)" align="right">
                        <CommunityThreadsSection project={previewProject} currentUser={currentUser} />
                    </FeatureShowcaseSection>

                    <FeatureShowcaseSection glowFrom="rgba(245, 158, 11, 0.1)" glowTo="rgba(249, 115, 22, 0.08)" align="left">
                        <RealTimeAlertsSection />
                    </FeatureShowcaseSection>

                    <FeatureShowcaseSection glowFrom="rgba(148, 163, 184, 0.12)" glowTo="rgba(100, 116, 139, 0.08)" align="right">
                        <AccountPreferencesSection />
                    </FeatureShowcaseSection>
                </div>

                <LazySection minHeight="300px">
                    <section className="relative z-20 overflow-hidden border-t border-slate-200/60 dark:border-white/[0.04]">
                        <div className="absolute inset-0 bg-gradient-to-b from-[#09101c] via-[#070d17] to-[#060b14]" />
                        <div className="absolute -top-10 left-[10%] h-56 w-56 rounded-full blur-3xl opacity-20 pointer-events-none" style={{ background: 'radial-gradient(circle, rgba(59, 130, 246, 0.22) 0%, transparent 72%)' }} />
                        <div className="absolute -bottom-16 right-[8%] h-72 w-72 rounded-full blur-3xl opacity-15 pointer-events-none" style={{ background: 'radial-gradient(circle, rgba(16, 185, 129, 0.18) 0%, transparent 74%)' }} />
                        <div
                            className="absolute inset-0 opacity-100 pointer-events-none"
                            style={{
                                backgroundImage: `
                                    radial-gradient(circle at 1px 1px, rgba(148, 163, 184, 0.08) 1px, transparent 0),
                                    linear-gradient(135deg, rgba(255, 255, 255, 0) 0%, rgba(59, 130, 246, 0.05) 45%, rgba(255, 255, 255, 0) 100%)
                                `,
                                backgroundSize: '22px 22px, 100% 100%'
                            }}
                        />
                        <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-white/10 to-transparent pointer-events-none" />
                        <div className="absolute inset-x-0 bottom-0 h-28 bg-gradient-to-t from-black/30 to-transparent pointer-events-none" />

                        <div className="relative z-20 py-24 sm:py-32 lg:py-40 max-w-5xl mx-auto px-6 text-center">
                            <div className="flex justify-center mb-10 sm:mb-12">
                                <img
                                    src="/assets/logo.svg"
                                    alt="Modtale"
                                    className="h-10 sm:h-12 w-auto object-contain drop-shadow-sm dark:hidden"
                                    loading="lazy"
                                />
                                <img
                                    src="/assets/logo_light.svg"
                                    alt="Modtale"
                                    className="hidden h-10 sm:h-12 w-auto object-contain drop-shadow-sm dark:block"
                                    loading="lazy"
                                />
                            </div>

                            <h2 className="text-4xl sm:text-5xl lg:text-6xl font-black text-slate-900 dark:text-white mb-6 sm:mb-8 tracking-tighter leading-[1.05]">
                                Built by the community,<br />
                                <span className="text-modtale-accent">
                                    for the community.
                                </span>
                            </h2>

                            <p className="text-base sm:text-lg lg:text-xl text-slate-600 dark:text-slate-300 mb-10 sm:mb-14 font-medium max-w-2xl mx-auto leading-relaxed">
                                Modtale is 100% open-source. We believe a modding repository should exist purely to serve its ecosystem, free from corporate interests.
                            </p>

                            <nav aria-label="Footer Actions" className="flex flex-col sm:flex-row items-center justify-center gap-4 sm:gap-5">
                                <a
                                    href="https://github.com/Modtale/Modtale"
                                    target="_blank"
                                    rel="noreferrer"
                                    className="inline-flex items-center justify-center px-8 h-14 sm:h-16 text-base sm:text-lg font-bold rounded-2xl transition-all gap-3 w-full sm:w-auto text-slate-900 dark:text-white bg-white dark:bg-slate-800/80 border border-slate-200 dark:border-white/10 shadow-[0_4px_24px_rgba(0,0,0,0.06)] dark:shadow-[0_4px_24px_rgba(0,0,0,0.3)] hover:shadow-[0_8px_32px_rgba(0,0,0,0.1)] dark:hover:shadow-[0_8px_32px_rgba(0,0,0,0.4)] hover:-translate-y-0.5 transform-gpu backdrop-blur-sm"
                                >
                                    <Github className="w-5 h-5 sm:w-6 sm:h-6" aria-hidden="true" /> View Source Code
                                </a>
                                <Link
                                    to={SiteRoutes.apiDocs()}
                                    className="inline-flex items-center justify-center px-8 h-14 sm:h-16 text-base sm:text-lg font-bold rounded-2xl transition-all gap-3 w-full sm:w-auto bg-blue-600 hover:bg-blue-500 text-white shadow-[0_8px_32px_rgba(37,99,235,0.25),inset_0_1px_0_rgba(255,255,255,0.2)] hover:shadow-[0_16px_48px_rgba(37,99,235,0.3),inset_0_1px_0_rgba(255,255,255,0.2)] hover:-translate-y-0.5 transform-gpu ring-1 ring-blue-500"
                                >
                                    <Code className="w-5 h-5 sm:w-6 sm:h-6" aria-hidden="true" /> View API Docs
                                </Link>
                            </nav>
                        </div>
                    </section>
                </LazySection>
            </main>
        </div>
    );
};
