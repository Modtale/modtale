import React, { useState, useEffect, useMemo, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import { Search, Upload, ChevronRight, Github, Code } from 'lucide-react';
import { api } from '@/utils/api';
import type { Project, User } from '@/types';
import { SiteRoutes } from '@/utils/routes';

import { AnimatedCounter } from '../components/AnimatedCounter';
import { MarqueeColumn, MarqueeRow } from '../components/HeroMarquee';
import { InlineDependencyUI, InlineDownloadUI, InlineNotificationUI } from '../components/FeaturePreviews';
import { GLASS_CARD } from '../styles';

const getInitialData = () => {
    if (typeof window !== 'undefined' && (window as any).INITIAL_DATA) {
        return (window as any).INITIAL_DATA;
    }
    return null;
}

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

export const Home: React.FC<{ user?: User | null }> = ({ user }) => {
    const ssrData = getInitialData();

    const [isDesktop, setIsDesktop] = useState(typeof window !== 'undefined' ? window.innerWidth >= 1024 : true);
    const [projects, setProjects] = useState<Project[]>(ssrData?.homeProjects || []);
    const [stats, setStats] = useState(ssrData?.stats || { totalProjects: 0, totalDownloads: 0, totalUsers: 0 });
    const [readyForHeavyUI, setReadyForHeavyUI] = useState(false);

    useEffect(() => {
        const handleResize = () => setIsDesktop(window.innerWidth >= 1024);
        window.addEventListener('resize', handleResize, { passive: true });

        const idleTimer = setTimeout(() => {
            setReadyForHeavyUI(true);

            if (!ssrData?.homeProjects?.length) {
                api.get('/projects', { params: { size: 16, sort: 'trending' } })
                    .then(res => {
                        if (res.data?.content) setProjects(res.data.content);
                    })
                    .catch(() => {});
            }

            if (!ssrData?.stats?.totalProjects) {
                api.get('/analytics/platform/stats')
                    .then(res => setStats(res.data))
                    .catch(() => {});
            }
        }, 100);

        return () => {
            window.removeEventListener('resize', handleResize);
            clearTimeout(idleTimer);
        };
    }, []);

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

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-[#0B1120] text-slate-900 dark:text-slate-300 relative selection:bg-blue-500 selection:text-white overflow-x-hidden transition-colors duration-300">
            <Helmet>
                <title>Modtale - The Hytale Community Repository</title>
                <meta name="description" content="The community repository for Hytale. Discover, download, and share Hytale worlds, plugins, asset packs, worlds, and projectpacks." />
                <link rel="preload" as="image" href="/assets/logo_light.svg" />
                <link rel="preload" as="image" href="/assets/logo.svg" />
                <style>{`
                    @keyframes marquee-up {
                        from { transform: translateY(0); }
                        to { transform: translateY(calc(-50% - 0.75rem)); }
                    }
                    .animate-marquee-up {
                        animation: marquee-up var(--marquee-duration, 40s) linear infinite;
                        will-change: transform;
                    }
                    @keyframes marquee-left {
                        from { transform: translateX(0); }
                        to { transform: translateX(-50%); }
                    }
                    .animate-marquee-left {
                        animation: marquee-left var(--marquee-duration, 35s) linear infinite;
                        will-change: transform;
                    }
                    @keyframes marquee-right {
                        from { transform: translateX(-50%); }
                        to { transform: translateX(0); }
                    }
                    .animate-marquee-right {
                        animation: marquee-right var(--marquee-duration, 35s) linear infinite;
                        will-change: transform;
                    }
                    .contain-content {
                        contain: content;
                    }
                `}</style>
            </Helmet>

            <main className="relative z-10 contain-content">
                <section className="relative w-full min-h-[85vh] 2xl:min-h-[90vh] flex flex-col items-center justify-center pt-12 sm:pt-16 lg:pt-16 2xl:pt-36 pb-16 lg:pb-20 border-b border-slate-200 dark:border-white/5 overflow-hidden">
                    <div className="absolute inset-0 bg-[repeating-linear-gradient(45deg,transparent,transparent_10px,rgba(59,130,246,0.05)_10px,rgba(59,130,246,0.05)_11px)] dark:bg-[repeating-linear-gradient(45deg,transparent,transparent_10px,rgba(255,255,255,0.03)_10px,rgba(255,255,255,0.03)_11px)] [mask-image:radial-gradient(ellipse_60%_60%_at_50%_50%,#000_70%,transparent_100%)] pointer-events-none transform-gpu" />

                    <div className="absolute top-1/4 -left-1/4 w-[800px] h-[800px] bg-blue-500/10 dark:bg-blue-600/15 rounded-full blur-[120px] mix-blend-multiply dark:mix-blend-screen pointer-events-none transform-gpu" />
                    <div className="absolute bottom-1/4 -right-1/4 w-[600px] h-[600px] bg-indigo-500/10 dark:bg-indigo-600/15 rounded-full blur-[120px] mix-blend-multiply dark:mix-blend-screen pointer-events-none transform-gpu" />

                    <div className="relative z-20 w-full max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 grid grid-cols-1 lg:grid-cols-2 gap-8 sm:gap-12 2xl:gap-20 items-stretch">

                        <div className="flex flex-col items-center lg:items-start text-center lg:text-left w-full max-w-2xl lg:max-w-xl 2xl:max-w-2xl justify-center mx-auto lg:mx-0">
                            <img
                                src="/assets/logo_light.svg"
                                alt="Modtale Logo"
                                className="h-14 sm:h-16 md:h-20 lg:h-24 mb-6 sm:mb-10 object-contain drop-shadow-[0_0_30px_rgba(59,130,246,0.2)] hidden dark:block"
                                fetchPriority="high"
                            />
                            <img
                                src="/assets/logo.svg"
                                alt="Modtale Logo"
                                className="h-14 sm:h-16 md:h-20 lg:h-24 mb-6 sm:mb-10 object-contain drop-shadow-sm block dark:hidden"
                                fetchPriority="high"
                            />

                            <h1 className="text-4xl sm:text-5xl lg:text-6xl 2xl:text-[5.5rem] font-black text-slate-900 dark:text-white tracking-tighter leading-[1.05] mb-4 sm:mb-6 2xl:mb-8">
                                The Hytale <br className="hidden lg:block" />
                                <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-600 via-indigo-500 to-blue-500 dark:from-blue-400 dark:via-indigo-400 dark:to-blue-300">
                                    Community Repository.
                                </span>
                            </h1>

                            <p className="text-base sm:text-lg 2xl:text-xl text-slate-600 dark:text-slate-300 max-w-2xl lg:max-w-lg 2xl:max-w-xl mb-8 sm:mb-10 2xl:mb-12 font-medium leading-relaxed">
                                Discover, download, and seamlessly share Hytale projects, worlds, plugins, asset packs, and projectpacks.
                            </p>

                            <nav aria-label="Primary Actions" className="flex flex-col sm:flex-row items-center gap-3 sm:gap-4 w-full sm:w-auto mb-8 sm:mb-10 2xl:mb-14">
                                <Link
                                    to={SiteRoutes.browse()}
                                    className="flex items-center justify-center px-6 sm:px-10 h-14 sm:h-16 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-2xl transition-all shadow-[0_8px_32px_rgba(37,99,235,0.25),inset_0_1px_0_rgba(255,255,255,0.2)] hover:shadow-[0_16px_48px_rgba(37,99,235,0.3),inset_0_1px_0_rgba(255,255,255,0.2)] hover:-translate-y-0.5 w-full sm:w-auto text-base sm:text-lg ring-1 ring-blue-500 transform-gpu"
                                >
                                    <Search className="w-5 h-5 mr-2 sm:mr-3" aria-hidden="true" />
                                    Discover Projects
                                </Link>
                                <Link
                                    to={SiteRoutes.upload()}
                                    className="flex items-center justify-center px-6 sm:px-10 h-14 sm:h-16 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 text-slate-900 dark:text-white font-bold rounded-2xl hover:bg-slate-50 dark:hover:bg-slate-700 transition-all w-full sm:w-auto text-base sm:text-lg shadow-sm hover:shadow-md hover:-translate-y-0.5 transform-gpu"
                                >
                                    <Upload className="w-5 h-5 mr-2 sm:mr-3 text-slate-400 dark:text-slate-500" aria-hidden="true" />
                                    Publish Work
                                </Link>
                            </nav>

                            <div className={`${GLASS_CARD} flex flex-row items-center justify-between sm:justify-start gap-2 sm:gap-10 2xl:gap-14 w-full sm:w-fit p-4 sm:p-6 lg:p-8 shadow-sm lg:-ml-1.5 contain-content`}>
                                <div className="flex flex-col items-center lg:items-start flex-1 sm:flex-none">
                                    <span className="text-xl sm:text-3xl lg:text-4xl font-black text-slate-900 dark:text-white tracking-tight">
                                        {readyForHeavyUI ? <AnimatedCounter value={stats.totalProjects} /> : 0}
                                    </span>
                                    <span className="text-[9px] sm:text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest mt-1 sm:mt-2">Projects</span>
                                </div>
                                <div className="w-px h-8 sm:h-12 bg-slate-200 dark:bg-white/10" aria-hidden="true" />
                                <div className="flex flex-col items-center lg:items-start flex-1 sm:flex-none">
                                    <span className="text-xl sm:text-3xl lg:text-4xl font-black text-slate-900 dark:text-white tracking-tight">
                                        {readyForHeavyUI ? <AnimatedCounter value={stats.totalDownloads} /> : 0}
                                    </span>
                                    <span className="text-[9px] sm:text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest mt-1 sm:mt-2">Downloads</span>
                                </div>
                                <div className="w-px h-8 sm:h-12 bg-slate-200 dark:bg-white/10" aria-hidden="true" />
                                <div className="flex flex-col items-center lg:items-start flex-1 sm:flex-none">
                                    <span className="text-xl sm:text-3xl lg:text-4xl font-black text-slate-900 dark:text-white tracking-tight">
                                        {readyForHeavyUI ? <AnimatedCounter value={stats.totalUsers} /> : 0}
                                    </span>
                                    <span className="text-[9px] sm:text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest mt-1 sm:mt-2">Creators</span>
                                </div>
                            </div>
                        </div>

                        {isDesktop ? (
                            <div className="relative hidden lg:block w-full lg:min-h-[600px] 2xl:min-h-[750px]">
                                {validFeaturedProjects.length > 0 && (
                                    <aside
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
                            <div className="-mx-6 sm:-mx-12 md:-mx-16 flex flex-col gap-4 lg:hidden mt-8 sm:mt-12 mb-4 contain-content">
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
                                <h2 className="text-3xl sm:text-4xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">Install Hytale Projects with Confidence.</h2>
                                <p className="text-base sm:text-lg 2xl:text-xl text-slate-600 dark:text-slate-300 leading-relaxed font-medium max-w-2xl">
                                    Finding the right file shouldn't be a puzzle. Modtale makes it easy to find projects for your game version and review changelogs before you hit download.
                                </p>
                                <Link to={SiteRoutes.browse()} className="inline-flex items-center font-bold text-blue-600 hover:text-blue-500 dark:text-blue-400 dark:hover:text-blue-300 transition-colors group text-base sm:text-lg mx-auto lg:mx-0">
                                    Start browsing <ChevronRight className="w-5 h-5 ml-2 group-hover:translate-x-1.5 transition-transform" aria-hidden="true" />
                                </Link>
                            </div>
                            <div className="flex-1 w-full relative mt-4 lg:mt-0">
                                <div className="absolute -inset-10 bg-gradient-to-tr from-blue-400/20 to-transparent dark:from-blue-500/20 blur-3xl rounded-full z-0 pointer-events-none transform-gpu" />
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
                                <h2 className="text-3xl sm:text-4xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">Automated Hytale Project Dependencies.</h2>
                                <p className="text-base sm:text-lg 2xl:text-xl text-slate-600 dark:text-slate-300 leading-relaxed font-medium max-w-2xl">
                                    Forget hunting down core libraries or confusing projectpacks. Modtale allows you to seamlessly download all required projects in one swift action.
                                </p>
                            </div>
                            <div className="flex-1 w-full relative mt-4 lg:mt-0">
                                <div className="absolute -inset-10 bg-gradient-to-tl from-emerald-400/20 to-transparent dark:from-emerald-500/20 blur-3xl rounded-full z-0 pointer-events-none transform-gpu" />
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
                            <div className="flex-1 w-full relative mt-4 lg:mt-0">
                                <div className="absolute -inset-10 bg-gradient-to-tl from-amber-400/20 to-transparent dark:from-amber-500/20 blur-3xl rounded-full z-0 pointer-events-none transform-gpu" />
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
                                Modtale is 100% open-source. We believe a projectding repository should exist purely to serve its ecosystem, free from corporate interests. Explore our source code or utilize our public API to build your own tools.
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