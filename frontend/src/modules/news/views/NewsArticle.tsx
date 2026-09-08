import React, { useMemo } from 'react';
import { ArrowRight, Rss } from 'lucide-react';
import { Link, Navigate, useParams } from 'react-router-dom';
import {
    DirectDownloadsSection,
    ProjectAnalyticsSection,
    SmartDependenciesSection,
    TrendingProjectsSection,
    CommunityThreadsSection,
} from '@/modules/home/components/FeaturePreviews';
import { ProjectCard, ProjectCardSkeleton } from '@/modules/project/components/ProjectCard';
import { NEWS_INDEX_PATH, NEWS_RSS_PATH, getNewsPostBySlug } from '@/data/news';
import { SiteRoutes } from '@/utils/routes';
import type { Project, User } from '@/types';
import { NewsHeroShowcase } from './NewsShowcase';
import { useNewsProjects } from './newsProjects';

const ArticleSection = ({ children }: { children: React.ReactNode }) => (
    <section className="rounded-[2rem] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-5 sm:p-7 lg:p-9 shadow-sm">
        {children}
    </section>
);

const ProjectStrip = ({ projects }: { projects: Project[] }) => (
    <div className="grid gap-4 md:grid-cols-3">
        {projects.length > 0 ? projects.slice(0, 3).map((project, index) => (
            <ProjectCard
                key={project.id}
                project={project}
                isFavorite={false}
                onToggleFavorite={() => {}}
                isLoggedIn={false}
                priority={index === 0}
                viewStyle="grid"
            />
        )) : (
            <>
                <ProjectCardSkeleton />
                <ProjectCardSkeleton />
                <ProjectCardSkeleton />
            </>
        )}
    </div>
);

export const NewsArticle: React.FC<{ currentUser?: User | null }> = ({ currentUser = null }) => {
    const { slug } = useParams();
    const post = getNewsPostBySlug(slug);
    const { projects, loading } = useNewsProjects();

    const projectPool = useMemo(() => projects.filter((project) => Boolean(project?.id)), [projects]);
    const primaryProject = projectPool.find((project) => Boolean(project.imageUrl)) || projectPool[0];
    const dependencyProjects = projectPool.slice(0, 3);

    if (!post) {
        return <Navigate to={NEWS_INDEX_PATH} replace />;
    }

    const publishedDate = new Date(post.publishedAt).toLocaleDateString('en-US', {
        month: 'long',
        day: 'numeric',
        year: 'numeric',
    });

    return (
        <main className="bg-slate-50 dark:bg-modtale-dark min-h-screen">
            <article>
                <header className="border-b border-slate-200 dark:border-white/10 bg-white dark:bg-slate-950">
                    <div className="max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 py-12 lg:py-20 grid lg:grid-cols-[minmax(0,0.9fr)_minmax(480px,1.1fr)] gap-10 lg:gap-14 items-center">
                        <div>
                            <Link to={NEWS_INDEX_PATH} className="text-sm font-black text-blue-600 dark:text-sky-300 hover:text-blue-700 dark:hover:text-sky-200">
                                News
                            </Link>
                            <p className="mt-6 text-sm font-black uppercase tracking-wider text-slate-500 dark:text-slate-400">
                                Product Update &middot; {publishedDate} &middot; {post.readingTime}
                            </p>
                            <h1 className="mt-4 text-4xl md:text-6xl font-black tracking-tight leading-[1.04] text-slate-950 dark:text-white">
                                {post.title}
                            </h1>
                            <p className="mt-6 max-w-3xl text-lg md:text-xl leading-8 text-slate-600 dark:text-slate-300">
                                {post.description}
                            </p>
                            <div className="mt-7 flex flex-wrap gap-2">
                                {post.tags.map((tag) => (
                                    <span key={tag} className="rounded-full bg-blue-50 dark:bg-sky-400/10 px-3 py-1 text-xs font-black uppercase tracking-wide text-blue-700 dark:text-sky-300">
                                        {tag}
                                    </span>
                                ))}
                            </div>
                        </div>

                        <NewsHeroShowcase projects={projectPool} loading={loading} />
                    </div>
                </header>

                <div className="max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 py-12 lg:py-16 grid xl:grid-cols-[minmax(0,820px)_minmax(300px,1fr)] gap-8 lg:gap-12 items-start">
                    <div className="space-y-8">
                        <ArticleSection>
                            <div className="prose prose-slate dark:prose-invert prose-lg max-w-none prose-headings:font-black prose-headings:tracking-tight prose-a:font-black">
                                <p>
                                    We tightened the path from "I found something cool" to "I understand it, trust it, and can share it." The update tour now uses the same Modtale surfaces that players and creators already touch: real project cards, the featured project marquee, dependency previews, analytics, comments, API docs, and RSS.
                                </p>
                                <p>
                                    That means the screenshots and graphics are not a separate product fantasy. When the backend has project data available, this page pulls from the same project feed used on the home page and browse pages.
                                </p>
                            </div>
                        </ArticleSection>

                        <ArticleSection>
                            <div className="mb-7">
                                <p className="text-xs font-black uppercase tracking-wider text-blue-600 dark:text-sky-300">Real Discovery Components</p>
                                <h2 className="mt-2 text-3xl sm:text-4xl font-black tracking-tight text-slate-950 dark:text-white">Discovery uses the actual project card system.</h2>
                                <p className="mt-4 text-base leading-7 text-slate-600 dark:text-slate-300">
                                    The cards below are the same reusable project cards used around Modtale. With live data, they show real project titles, icons, banners, authors, categories, download counts, and route behavior.
                                </p>
                            </div>
                            <ProjectStrip projects={projectPool} />
                        </ArticleSection>

                        <ArticleSection>
                            <TrendingProjectsSection projects={projectPool.slice(0, 8)} loading={loading} />
                        </ArticleSection>

                        <ArticleSection>
                            <DirectDownloadsSection />
                        </ArticleSection>

                        <ArticleSection>
                            <SmartDependenciesSection randomProject={primaryProject} previewProjects={dependencyProjects} />
                        </ArticleSection>

                        <ArticleSection>
                            <ProjectAnalyticsSection showConversionRate={Boolean(primaryProject)} />
                        </ArticleSection>

                        <ArticleSection>
                            <CommunityThreadsSection project={primaryProject} currentUser={currentUser} />
                        </ArticleSection>

                        <ArticleSection>
                            <div className="grid gap-6 lg:grid-cols-[minmax(0,0.95fr)_minmax(280px,0.7fr)] items-center">
                                <div>
                                    <p className="text-xs font-black uppercase tracking-wider text-orange-600 dark:text-orange-300">Portable Updates</p>
                                    <h2 className="mt-2 text-3xl sm:text-4xl font-black tracking-tight text-slate-950 dark:text-white">News posts embed cleanly outside Modtale.</h2>
                                    <p className="mt-4 text-base leading-7 text-slate-600 dark:text-slate-300">
                                        Each news post has canonical URLs, article metadata, Open Graph and Twitter card images, JSON-LD, and a feed item. The post can travel into community chats with a useful preview instead of a bare URL.
                                    </p>
                                    <div className="mt-6 flex flex-col sm:flex-row gap-3">
                                        <a href={NEWS_RSS_PATH} className="inline-flex items-center justify-center rounded-2xl bg-orange-500 px-5 py-3 text-sm font-black text-white transition-all hover:-translate-y-0.5 hover:bg-orange-400">
                                            <Rss className="w-4 h-4 mr-2" />
                                            RSS Feed
                                        </a>
                                        <Link to={SiteRoutes.apiDocs()} className="inline-flex items-center justify-center rounded-2xl border border-slate-300 dark:border-white/10 px-5 py-3 text-sm font-black text-slate-700 dark:text-slate-200 transition-colors hover:border-blue-400 hover:text-blue-600 dark:hover:text-sky-300">
                                            API Docs
                                            <ArrowRight className="w-4 h-4 ml-2" />
                                        </Link>
                                    </div>
                                </div>
                                <div className="rounded-3xl border border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-slate-950 p-4">
                                    <img
                                        src={post.socialImage}
                                        alt={post.socialImageAlt}
                                        width="1200"
                                        height="630"
                                        loading="lazy"
                                        className="aspect-[1200/630] w-full rounded-2xl object-cover"
                                    />
                                    <div className="mt-4">
                                        <strong className="text-slate-950 dark:text-white">{post.title}</strong>
                                        <p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">{post.description}</p>
                                        <p className="mt-3 text-sm font-black text-blue-600 dark:text-sky-300">modtale.net</p>
                                    </div>
                                </div>
                            </div>
                        </ArticleSection>
                    </div>

                    <aside className="hidden xl:block sticky top-28 space-y-4">
                        <div className="rounded-3xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-6">
                            <p className="text-xs font-black uppercase tracking-wider text-slate-500 dark:text-slate-400">News Post</p>
                            <h2 className="mt-2 text-xl font-black text-slate-950 dark:text-white">{post.title}</h2>
                            <p className="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">{post.excerpt}</p>
                            <a href={NEWS_RSS_PATH} className="mt-5 inline-flex items-center text-sm font-black text-orange-600 dark:text-orange-300">
                                <Rss className="w-4 h-4 mr-2" />
                                RSS Feed
                            </a>
                        </div>
                        <div className="rounded-3xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-6">
                            <p className="text-xs font-black uppercase tracking-wider text-slate-500 dark:text-slate-400">Powered By</p>
                            <div className="mt-4 grid gap-3">
                                <Link to={SiteRoutes.browse()} className="font-black text-slate-700 dark:text-slate-200 hover:text-blue-600 dark:hover:text-sky-300">Project discovery</Link>
                                <Link to={SiteRoutes.upload()} className="font-black text-slate-700 dark:text-slate-200 hover:text-blue-600 dark:hover:text-sky-300">Creator publishing</Link>
                                <Link to={SiteRoutes.apiDocs()} className="font-black text-slate-700 dark:text-slate-200 hover:text-blue-600 dark:hover:text-sky-300">Developer API</Link>
                            </div>
                        </div>
                    </aside>
                </div>
            </article>
        </main>
    );
};
