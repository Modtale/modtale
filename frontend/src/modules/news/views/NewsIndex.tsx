import { useNewsPosts } from '../api/useNews';
import React from 'react';
import { Rss } from 'lucide-react';
import { NEWS_RSS_PATH } from '@/data/news';
import { NewsCard } from '../components/NewsCard';


export const NewsIndex: React.FC = () => {
    const { posts, loading, error } = useNewsPosts();
    return (
    <main className="min-h-[70vh] bg-slate-50 dark:bg-slate-900 text-slate-900 dark:text-white">
        <div className="max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 py-10 sm:py-12">
            <header className="flex flex-col sm:flex-row sm:items-center justify-between gap-5 mb-8">
                <div>
                    <h1 className="text-3xl sm:text-4xl font-extrabold leading-tight">News from Modtale</h1>
                </div>
                <a href={NEWS_RSS_PATH} className="inline-flex self-start sm:self-auto shrink-0 items-center gap-2 px-4 py-2.5 rounded-xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-800 text-sm font-bold text-slate-600 dark:text-slate-300 hover:text-blue-600 dark:hover:text-blue-400 hover:border-blue-400 transition-colors focus-visible:outline-2 focus-visible:outline-blue-400 focus-visible:outline-offset-4">
                    <Rss size={16} aria-hidden="true" /> RSS feed
                </a>
            </header>
            {loading && <p role="status">Loading news…</p>}
            {error && <p role="alert">News is temporarily unavailable. Please try again shortly.</p>}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {posts.map((post, index) => <NewsCard key={post.slug} post={post} priority={index === 0} />)}
            </div>
        </div>
    </main>
);
};
