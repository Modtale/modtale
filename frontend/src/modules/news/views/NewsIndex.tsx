import React from 'react';
import { ArrowRight, Rss } from 'lucide-react';
import { Link } from 'react-router-dom';
import { NEWS_POSTS, NEWS_RSS_PATH, getNewsPostPath } from '@/data/news';
import { ProjectCard, ProjectCardSkeleton } from '@/modules/project/components/ProjectCard';
import { SiteRoutes } from '@/utils/routes';
import { NewsHeroShowcase } from './NewsShowcase';
import { useNewsProjects } from './newsProjects';

export const NewsIndex: React.FC = () => {
    const [latestPost] = NEWS_POSTS;
    const { projects, loading } = useNewsProjects();
    const previewProjects = projects.slice(0, 3);

    return (
        <main className="bg-slate-50 dark:bg-modtale-dark min-h-screen">
            <section className="border-b border-slate-200 dark:border-white/10 bg-white dark:bg-slate-950">
                <div className="max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 py-14 lg:py-20 grid lg:grid-cols-[minmax(0,0.9fr)_minmax(480px,1.1fr)] gap-10 lg:gap-14 items-center">
                    <div>
                        <p className="text-sm font-black uppercase tracking-wider text-blue-600 dark:text-sky-300 mb-4">
                            Modtale News
                        </p>
                        <h1 className="text-4xl md:text-6xl font-black tracking-tight leading-[1.04] text-slate-950 dark:text-white">
                            Product notes for the Hytale creator community.
                        </h1>
                        <p className="mt-6 max-w-2xl text-lg leading-8 text-slate-600 dark:text-slate-300">
                            Feature tours, platform updates, and small build notes from Modtale, shown with the same project surfaces players and creators already use.
                        </p>
                        <div className="mt-8 flex flex-col sm:flex-row gap-3">
                            <Link
                                to={getNewsPostPath(latestPost)}
                                className="inline-flex items-center justify-center rounded-2xl bg-blue-600 px-6 py-3.5 text-sm font-black text-white shadow-lg shadow-blue-600/20 transition-all hover:-translate-y-0.5 hover:bg-blue-500"
                            >
                                Read latest update
                                <ArrowRight className="w-4 h-4 ml-2" />
                            </Link>
                            <a
                                href={NEWS_RSS_PATH}
                                className="inline-flex items-center justify-center rounded-2xl border border-slate-300 dark:border-white/10 px-6 py-3.5 text-sm font-black text-slate-700 dark:text-slate-200 transition-colors hover:border-orange-400 hover:text-orange-600 dark:hover:text-orange-300"
                            >
                                <Rss className="w-4 h-4 mr-2" />
                                RSS Feed
                            </a>
                        </div>
                    </div>

                    <NewsHeroShowcase projects={projects} loading={loading} />
                </div>
            </section>

            <section className="max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 py-14">
                <div className="grid gap-8 lg:grid-cols-[minmax(0,0.9fr)_minmax(360px,0.55fr)] items-start">
                    <article className="overflow-hidden rounded-3xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-sm">
                        <Link to={getNewsPostPath(latestPost)} className="block">
                            <img
                                src={latestPost.socialImage}
                                alt={latestPost.socialImageAlt}
                                width="1200"
                                height="630"
                                loading="lazy"
                                className="aspect-[1200/630] w-full object-cover"
                            />
                            <div className="p-6 sm:p-8">
                                <p className="text-xs font-black uppercase tracking-wider text-blue-600 dark:text-sky-300">
                                    {new Date(latestPost.publishedAt).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })} &middot; {latestPost.readingTime}
                                </p>
                                <h2 className="mt-3 text-3xl font-black tracking-tight text-slate-950 dark:text-white">
                                    {latestPost.title}
                                </h2>
                                <p className="mt-4 max-w-3xl text-base leading-7 text-slate-600 dark:text-slate-300">
                                    {latestPost.excerpt}
                                </p>
                            </div>
                        </Link>
                    </article>

                    <aside className="rounded-3xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-5 sm:p-6">
                        <div className="flex items-center justify-between gap-4 mb-5">
                            <div>
                                <p className="text-xs font-black uppercase tracking-wider text-slate-500 dark:text-slate-400">Live Projects</p>
                                <h2 className="text-xl font-black text-slate-950 dark:text-white">From the same surfaces</h2>
                            </div>
                            <Link to={SiteRoutes.browse()} className="text-sm font-black text-blue-600 dark:text-sky-300">
                                Browse
                            </Link>
                        </div>
                        <div className="grid gap-4">
                            {previewProjects.length > 0 ? previewProjects.map((project) => (
                                <ProjectCard
                                    key={project.id}
                                    project={project}
                                    isFavorite={false}
                                    onToggleFavorite={() => {}}
                                    isLoggedIn={false}
                                    viewStyle="compact"
                                />
                            )) : (
                                <>
                                    <ProjectCardSkeleton />
                                    <ProjectCardSkeleton />
                                </>
                            )}
                        </div>
                    </aside>
                </div>
            </section>
        </main>
    );
};
