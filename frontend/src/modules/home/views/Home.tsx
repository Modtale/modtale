import React, { useState, useEffect, useMemo, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import { Search, Upload, ChevronRight, Github, Code } from 'lucide-react';
import '@fontsource-variable/inter';
import { api } from '@/utils/api';
import { ROUTE_SEO } from '@/data/seo-constants';
import type { Project } from '@/types';
import { SiteRoutes } from '@/utils/routes';
import { useSSRData } from '@/context/SSRContext';

import { AnimatedCounter } from '../components/AnimatedCounter';
import { MarqueeColumn, MarqueeRow } from '../components/HeroMarquee';
import { InlineDependencyUI, InlineDownloadUI, InlineNotificationUI } from '../components/FeaturePreviews';
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

export const Home: React.FC = () => {
    const DESKTOP_BREAKPOINT = 1024;
    const DESKTOP_HERO_MIN_WIDTH_ENTER = 1260;
    const DESKTOP_HERO_MIN_WIDTH_EXIT = 1180;

    const { initialData: ssrData } = useSSRData();
    const homeSeo = ROUTE_SEO['/'];

    const [isDesktop, setIsDesktop] = useState(typeof window !== 'undefined' ? window.innerWidth >= DESKTOP_BREAKPOINT : true);
    const [useDesktopHeroLayout, setUseDesktopHeroLayout] = useState(typeof window !== 'undefined' ? window.innerWidth >= DESKTOP_HERO_MIN_WIDTH_ENTER : true);
    const [projects, setProjects] = useState<Project[]>(ssrData?.homeProjects || []);
    const [stats, setStats] = useState(ssrData?.stats || { totalProjects: 0, totalDownloads: 0, totalUsers: 0 });
    const [readyForHeavyUI, setReadyForHeavyUI] = useState(false);
    const heroGridRef = useRef<HTMLDivElement>(null);
    const heroTextColumnRef = useRef<HTMLDivElement>(null);
    const heroMarqueeDesktopRef = useRef<HTMLDivElement>(null);
    const heroActionsRef = useRef<HTMLElement>(null);

    const formatMetric = (value?: number) => (value || 0).toLocaleString();

    useEffect(() => {
        const handleResize = () => setIsDesktop(window.innerWidth >= DESKTOP_BREAKPOINT);
        window.addEventListener('resize', handleResize, { passive: true });

        let hasRunHeavyUi = false;
        const enableHeavyUi = () => {
            if (hasRunHeavyUi) return;
            hasRunHeavyUi = true;
            setReadyForHeavyUI(true);
        };

        let uiIdleHandle: number | null = null;
        if ('requestIdleCallback' in window) {
            uiIdleHandle = (window as Window & { requestIdleCallback: (cb: IdleRequestCallback, opts?: IdleRequestOptions) => number })
                .requestIdleCallback(() => enableHeavyUi(), { timeout: 1000 });
        }
        const uiFallbackTimer = window.setTimeout(() => enableHeavyUi(), 250);

        const shouldFetchFallbackProjects = !ssrData?.homeProjects?.length;
        const shouldFetchFallbackStats = !ssrData?.stats?.totalProjects;

        let hasRunFallbackRequests = false;
        const runFallbackRequests = () => {
            if (hasRunFallbackRequests) return;
            hasRunFallbackRequests = true;

            if (shouldFetchFallbackProjects) {
                api.get('/projects', { params: { size: 16, sort: 'relevance', category: 'trending' } })
                    .then(res => {
                        if (res.data?.content) setProjects(res.data.content);
                    })
                    .catch(() => {});
            }

            if (shouldFetchFallbackStats) {
                api.get('/analytics/platform/stats')
                    .then(res => setStats(res.data))
                    .catch(() => {});
            }
        };

        let fetchIdleHandle: number | null = null;
        const scheduleFallbackRequests = () => {
            if (!shouldFetchFallbackProjects && !shouldFetchFallbackStats) return;
            if ('requestIdleCallback' in window) {
                fetchIdleHandle = (window as Window & { requestIdleCallback: (cb: IdleRequestCallback, opts?: IdleRequestOptions) => number })
                    .requestIdleCallback(() => runFallbackRequests(), { timeout: 6000 });
            }
        };

        if (document.readyState === 'complete') {
            scheduleFallbackRequests();
        } else {
            window.addEventListener('load', scheduleFallbackRequests, { once: true });
        }

        const requestFallbackTimer = window.setTimeout(() => {
            runFallbackRequests();
        }, 7000);

        return () => {
            window.removeEventListener('resize', handleResize);
            window.removeEventListener('load', scheduleFallbackRequests);
            window.clearTimeout(uiFallbackTimer);
            window.clearTimeout(requestFallbackTimer);
            if (uiIdleHandle !== null && 'cancelIdleCallback' in window) {
                (window as Window & { cancelIdleCallback: (handle: number) => void }).cancelIdleCallback(uiIdleHandle);
            }
            if (fetchIdleHandle !== null && 'cancelIdleCallback' in window) {
                (window as Window & { cancelIdleCallback: (handle: number) => void }).cancelIdleCallback(fetchIdleHandle);
            }
        };
    }, [DESKTOP_BREAKPOINT, ssrData?.homeProjects?.length, ssrData?.stats?.totalProjects]);

    useEffect(() => {
        let frameId = 0;

        const shouldUseDesktopLayout = (currentDesktopLayout: boolean) => {
            const viewportWidth = window.innerWidth;
            if (viewportWidth < DESKTOP_BREAKPOINT) return false;

            const gridEl = heroGridRef.current;
            const gridWidth = gridEl?.clientWidth ?? viewportWidth;
            const widthThreshold = currentDesktopLayout ? DESKTOP_HERO_MIN_WIDTH_EXIT : DESKTOP_HERO_MIN_WIDTH_ENTER;
            if (gridWidth < widthThreshold) return false;

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

            return !hasWrappedButtons && !hasTextOverflow && !hasHeroOverlap;
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
        stats.totalUsers,
        readyForHeavyUI
    ]);

    const validFeaturedProjects = useMemo(() => {
        if (!readyForHeavyUI) return [];
        return projects.filter(p => Boolean(p.bannerUrl) && Boolean(p.imageUrl) && !p.imageUrl?.includes('favicon'));
    }, [projects, readyForHeavyUI]);

    const randomDisplayProject = useMemo(() => {
        if (!readyForHeavyUI || validFeaturedProjects.length === 0) return undefined;
        return validFeaturedProjects[Math.floor(Math.random() * validFeaturedProjects.length)];
    }, [validFeaturedProjects, readyForHeavyUI]);

    const col1Projects = useMemo(() => validFeaturedProjects.filter((_, i) => i % 2 === 0), [validFeaturedProjects]);
    const col2Projects = useMemo(() => validFeaturedProjects.filter((_, i) => i % 2 === 1), [validFeaturedProjects]);
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
                <section className={`home-hero ${isDesktopHeroLayout ? 'home-hero-desktop lg:min-h-[92vh] 2xl:min-h-[90vh] lg:pt-[7vh] 2xl:pt-36 lg:pb-[6vh]' : ''} relative w-full min-h-[100vh] flex flex-col items-center justify-center pt-16 sm:pt-[7vh] pb-[5vh] border-b border-slate-200 dark:border-white/5 overflow-hidden`}>
                    <div className="absolute inset-0 bg-[repeating-linear-gradient(45deg,transparent,transparent_10px,rgba(59,130,246,0.05)_10px,rgba(59,130,246,0.05)_11px)] dark:bg-[repeating-linear-gradient(45deg,transparent,transparent_10px,rgba(255,255,255,0.03)_10px,rgba(255,255,255,0.03)_11px)] [mask-image:radial-gradient(ellipse_60%_60%_at_50%_50%,#000_70%,transparent_100%)] pointer-events-none transform-gpu" />

                    <div className="absolute top-1/4 -left-1/4 w-[800px] h-[800px] bg-blue-500/10 dark:bg-blue-600/15 rounded-full blur-[120px] mix-blend-multiply dark:mix-blend-screen pointer-events-none transform-gpu" />
                    <div className="absolute bottom-1/4 -right-1/4 w-[600px] h-[600px] bg-indigo-500/10 dark:bg-indigo-600/15 rounded-full blur-[120px] mix-blend-multiply dark:mix-blend-screen pointer-events-none transform-gpu" />

                    <div ref={heroGridRef} className={`relative z-20 w-full max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 ${isDesktopHeroLayout ? 'home-hero-desktop-grid lg:px-20 xl:px-28 lg:grid-cols-2 2xl:gap-20' : ''} grid grid-cols-1 ${isDesktopHeroLayout ? '' : 'lg:grid-cols-1'} gap-8 sm:gap-12 items-stretch`}>

                        <div
                            ref={heroTextColumnRef}
                            className={`home-hero-copy ${isDesktopHeroLayout ? 'home-hero-copy-desktop' : ''} flex flex-col items-center ${isDesktopHeroLayout ? 'lg:items-start text-center lg:text-left lg:mx-0 lg:max-w-xl' : 'text-center lg:text-center lg:mx-auto lg:max-w-2xl'} w-full max-w-2xl 2xl:max-w-2xl justify-center mx-auto`}
                        >
                            <div className="shrink-0">
                                <img
                                    src="/assets/logo.svg"
                                    alt="Modtale Logo"
                                    width={853}
                                    height={128}
                                    className={`home-hero-logo h-14 sm:h-16 md:h-20 ${isDesktopHeroLayout ? 'lg:h-[6.75rem]' : ''} w-auto mb-6 sm:mb-10 object-contain drop-shadow-sm shrink-0 dark:hidden`}
                                    fetchPriority="high"
                                    decoding="async"
                                />
                                <img
                                    src="/assets/logo_light.svg"
                                    alt="Modtale Logo"
                                    width={853}
                                    height={128}
                                    className={`home-hero-logo hidden h-14 sm:h-16 md:h-20 ${isDesktopHeroLayout ? 'lg:h-[6.75rem]' : ''} w-auto mb-6 sm:mb-10 object-contain drop-shadow-sm shrink-0 dark:block`}
                                    fetchPriority="high"
                                    decoding="async"
                                />
                            </div>

                            <h1 className={`text-4xl sm:text-5xl ${isDesktopHeroLayout ? 'lg:text-6xl 2xl:text-[5.5rem] 2xl:mb-8' : ''} font-black text-slate-900 dark:text-white tracking-tighter leading-[1.05] mb-4 sm:mb-6`}>
                                The Hytale<br />
                                <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-600 via-indigo-500 to-blue-500 dark:from-blue-400 dark:via-indigo-400 dark:to-blue-300">
                                    Community<br />Repository
                                </span>
                            </h1>

                            <p className={`text-base sm:text-lg ${isDesktopHeroLayout ? '2xl:text-xl lg:max-w-lg 2xl:max-w-xl 2xl:mb-12' : ''} text-slate-600 dark:text-slate-300 max-w-2xl mb-8 sm:mb-10 font-medium leading-relaxed`}>
                                Discover, download, and seamlessly share Hytale projects, worlds, plugins, asset packs, and modpacks.
                            </p>

                            <nav ref={heroActionsRef} aria-label="Primary Actions" className="flex flex-col sm:flex-row items-center gap-3 sm:gap-4 w-full sm:w-auto mb-8 sm:mb-10 2xl:mb-14">
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

                            <div className={`${GLASS_CARD} home-hero-stats flex flex-row items-center justify-between sm:justify-start gap-2 sm:gap-10 2xl:gap-14 w-full sm:w-fit p-4 sm:p-6 lg:p-8 shadow-sm lg:-ml-1.5 contain-content`}>
                                <div className="home-hero-stat-group flex flex-col items-center lg:items-start flex-1 sm:flex-none">
                                    <span className="home-hero-stat-value text-xl sm:text-3xl lg:text-4xl font-black text-slate-900 dark:text-white tracking-tight">
                                        {readyForHeavyUI ? <AnimatedCounter value={stats.totalProjects} /> : formatMetric(stats.totalProjects)}
                                    </span>
                                    <span className="home-hero-stat-label text-[9px] sm:text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest mt-1 sm:mt-2">Projects</span>
                                </div>
                                <div className="home-hero-stat-divider w-px h-8 sm:h-12 bg-slate-200 dark:bg-white/10" aria-hidden="true" />
                                <div className="home-hero-stat-group flex flex-col items-center lg:items-start flex-1 sm:flex-none">
                                    <span className="home-hero-stat-value text-xl sm:text-3xl lg:text-4xl font-black text-slate-900 dark:text-white tracking-tight">
                                        {readyForHeavyUI ? <AnimatedCounter value={stats.totalDownloads} /> : formatMetric(stats.totalDownloads)}
                                    </span>
                                    <span className="home-hero-stat-label text-[9px] sm:text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest mt-1 sm:mt-2">Downloads</span>
                                </div>
                                <div className="home-hero-stat-divider w-px h-8 sm:h-12 bg-slate-200 dark:bg-white/10" aria-hidden="true" />
                                <div className="home-hero-stat-group flex flex-col items-center lg:items-start flex-1 sm:flex-none">
                                    <span className="home-hero-stat-value text-xl sm:text-3xl lg:text-4xl font-black text-slate-900 dark:text-white tracking-tight">
                                        {readyForHeavyUI ? <AnimatedCounter value={stats.totalUsers} /> : formatMetric(stats.totalUsers)}
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
                                        className="absolute -inset-x-4 xl:-inset-x-8 inset-y-0 px-4 xl:px-8 flex gap-6 2xl:gap-10 justify-end overflow-hidden"
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
                        ) : (
                            <div className="-mx-6 sm:-mx-12 md:-mx-16 flex flex-col gap-4 mt-8 sm:mt-12 mb-4 contain-content">
                                {validFeaturedProjects.length > 0 && (
                                    <>
                                        <MarqueeRow projects={col1Projects} duration="35s" />
                                        <MarqueeRow projects={col2Projects} duration="45s" reverse={true} />
                                    </>
                                )}
                            </div>
                        )}
                    </div>
                </section>

                <div className="max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 space-y-16 sm:space-y-24 lg:space-y-32 2xl:space-y-40 py-16 sm:py-24 lg:py-32 2xl:py-40 relative z-20">
                    <LazySection minHeight="400px">
                        <section className="flex flex-col lg:flex-row items-center gap-8 sm:gap-12 lg:gap-16 2xl:gap-24">
                            <div className="flex-1 space-y-5 sm:space-y-6 2xl:space-y-8 flex flex-col items-center text-center lg:items-start lg:text-left">
                                <span className="text-blue-600 dark:text-blue-400 font-bold tracking-widest uppercase text-xs sm:text-sm mb-1 sm:mb-2 block bg-blue-50 dark:bg-blue-500/10 w-fit px-3 py-1 rounded-full border border-blue-100 dark:border-blue-500/20 mx-auto lg:mx-0">Version Management</span>
                                <h2 className="text-3xl sm:text-4xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">Install Hytale Mods with Confidence.</h2>
                                <p className="text-base sm:text-lg 2xl:text-xl text-slate-600 dark:text-slate-300 leading-relaxed font-medium max-w-2xl">
                                    Finding the right file shouldn't be a puzzle. Modtale makes it easy to find projects for your game version and review changelogs before you hit download.
                                </p>
                                <Link to={SiteRoutes.browse()} className="inline-flex items-center font-bold text-blue-600 hover:text-blue-500 dark:text-blue-400 dark:hover:text-blue-300 transition-colors group text-base sm:text-lg mx-auto lg:mx-0">
                                    Start browsing <ChevronRight className="w-5 h-5 ml-2 group-hover:translate-x-1.5 transition-transform" aria-hidden="true" />
                                </Link>
                            </div>
                            <div className="flex-1 w-full relative mt-4 lg:mt-0 overflow-visible">
                                <div
                                    className="absolute inset-0 z-0 pointer-events-none transform-gpu"
                                    aria-hidden="true"
                                    style={{
                                        background: 'radial-gradient(110% 90% at 22% 35%, rgba(59, 130, 246, 0.18), transparent 58%), radial-gradient(95% 85% at 78% 72%, rgba(125, 211, 252, 0.14), transparent 62%)',
                                        filter: 'blur(56px)',
                                        transform: 'translate3d(0, 0, 0) scale(1.22)'
                                    }}
                                />
                                <div className="relative z-10 w-full max-w-lg mx-auto lg:ml-auto">
                                    <InlineDownloadUI />
                                </div>
                            </div>
                        </section>
                    </LazySection>

                    <LazySection minHeight="400px">
                        <section className="flex flex-col lg:flex-row-reverse items-center gap-8 sm:gap-12 lg:gap-16 2xl:gap-24">
                            <div className="flex-1 space-y-5 sm:space-y-6 2xl:space-y-8 flex flex-col items-center text-center lg:items-start lg:text-left">
                                <span className="text-emerald-600 dark:text-emerald-400 font-bold tracking-widest uppercase text-xs sm:text-sm mb-1 sm:mb-2 block bg-emerald-50 dark:bg-emerald-500/10 w-fit px-3 py-1 rounded-full border border-emerald-100 dark:border-emerald-500/20 mx-auto lg:mx-0">Library Resolution</span>
                                <h2 className="text-3xl sm:text-4xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">Automated Hytale Mods Dependencies.</h2>
                                <p className="text-base sm:text-lg 2xl:text-xl text-slate-600 dark:text-slate-300 leading-relaxed font-medium max-w-2xl">
                                    Forget hunting down core libraries or confusing modpacks. Modtale allows you to seamlessly download all required projects in one swift action.
                                </p>
                            </div>
                            <div className="flex-1 w-full relative mt-4 lg:mt-0 overflow-visible">
                                <div
                                    className="absolute inset-0 z-0 pointer-events-none transform-gpu"
                                    aria-hidden="true"
                                    style={{
                                        background: 'radial-gradient(115% 92% at 76% 34%, rgba(16, 185, 129, 0.18), transparent 56%), radial-gradient(90% 82% at 24% 76%, rgba(52, 211, 153, 0.13), transparent 60%)',
                                        filter: 'blur(58px)',
                                        transform: 'translate3d(0, 0, 0) scale(1.24)'
                                    }}
                                />
                                <div className="relative z-10 w-full max-w-lg mx-auto lg:mr-auto">
                                    <InlineDependencyUI randomProject={randomDisplayProject} />
                                </div>
                            </div>
                        </section>
                    </LazySection>

                    <LazySection minHeight="400px">
                        <section className="flex flex-col lg:flex-row items-center gap-8 sm:gap-12 lg:gap-16 2xl:gap-24">
                            <div className="flex-1 space-y-5 sm:space-y-6 2xl:space-y-8 flex flex-col items-center text-center lg:items-start lg:text-left">
                                <span className="text-amber-600 dark:text-amber-400 font-bold tracking-widest uppercase text-xs sm:text-sm mb-1 sm:mb-2 block bg-amber-50 dark:bg-amber-500/10 w-fit px-3 py-1 rounded-full border border-amber-100 dark:border-amber-500/20 mx-auto lg:mx-0">Community Hub</span>
                                <h2 className="text-3xl sm:text-4xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">Always in the loop.</h2>
                                <p className="text-base sm:text-lg 2xl:text-xl text-slate-600 dark:text-slate-300 leading-relaxed font-medium max-w-2xl">
                                    Modtale keeps the Hytale community connected. Receive real-time alerts when tracked projects drop new updates, or when developers reply directly to your feedback.
                                </p>
                            </div>
                            <div className="flex-1 w-full relative mt-4 lg:mt-0 overflow-visible">
                                <div
                                    className="absolute inset-0 z-0 pointer-events-none transform-gpu"
                                    aria-hidden="true"
                                    style={{
                                        background: 'radial-gradient(112% 90% at 26% 30%, rgba(251, 191, 36, 0.18), transparent 56%), radial-gradient(92% 80% at 74% 74%, rgba(245, 158, 11, 0.12), transparent 60%)',
                                        filter: 'blur(56px)',
                                        transform: 'translate3d(0, 0, 0) scale(1.22)'
                                    }}
                                />
                                <div className="relative z-10 w-full max-w-lg mx-auto lg:mr-auto">
                                    <InlineNotificationUI />
                                </div>
                            </div>
                        </section>
                    </LazySection>

                </div>

                <LazySection minHeight="300px">
                    <section className="py-16 sm:py-20 lg:py-32 border-t border-slate-200 dark:border-white/5 bg-slate-50/50 dark:bg-slate-900/20 relative z-20">
                        <div className="max-w-4xl mx-auto px-6 text-center">
                            <div className="w-16 h-16 sm:w-20 sm:h-20 bg-slate-200 dark:bg-slate-800 rounded-2xl sm:rounded-3xl mx-auto mb-6 sm:mb-8 flex items-center justify-center shadow-inner border border-slate-300/50 dark:border-white/5">
                                <Code className="w-8 h-8 sm:w-10 sm:h-10 text-slate-500 dark:text-slate-400" aria-hidden="true" />
                            </div>
                            <h2 className="text-3xl sm:text-4xl lg:text-5xl font-black text-slate-900 dark:text-white mb-6 sm:mb-8 tracking-tight">Built by the community,<br className="sm:hidden" /> for the community.</h2>
                            <p className="text-base sm:text-lg lg:text-xl text-slate-600 dark:text-slate-300 mb-8 sm:mb-12 font-medium max-w-3xl mx-auto leading-relaxed">
                                Modtale is 100% open-source. We believe a modding repository should exist purely to serve its ecosystem, free from corporate interests. Explore our source code or utilize our public API to build your own tools.
                            </p>
                            <nav aria-label="Footer Actions" className="flex flex-col sm:flex-row items-center justify-center gap-4 sm:gap-6">
                                <a href="https://github.com/Modtale/Modtale" target="_blank" rel="noreferrer" className="inline-flex items-center justify-center px-6 sm:px-8 h-14 sm:h-16 text-base sm:text-lg font-bold rounded-2xl transition-all gap-3 w-full sm:w-auto text-slate-900 dark:text-white bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 shadow-sm hover:shadow-md hover:-translate-y-0.5 transform-gpu">
                                    <Github className="w-5 h-5 sm:w-6 sm:h-6" aria-hidden="true" /> View Source Code
                                </a>
                                <Link to={SiteRoutes.apiDocs()} className="inline-flex items-center justify-center px-6 sm:px-8 h-14 sm:h-16 text-base sm:text-lg font-bold rounded-2xl transition-all gap-3 w-full sm:w-auto text-blue-700 dark:text-blue-400 bg-blue-50 dark:bg-blue-500/10 border border-blue-200 dark:border-blue-500/20 hover:bg-blue-100 dark:hover:bg-blue-500/20 hover:-translate-y-0.5 transform-gpu">
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
