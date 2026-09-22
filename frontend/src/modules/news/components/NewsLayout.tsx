import type { ReactNode } from 'react';
import { ArrowLeft, Rss } from 'lucide-react';
import { Link } from 'react-router-dom';
import { NEWS_INDEX_PATH, NEWS_RSS_PATH } from '@/data/news';
import '../styles/news.css';
import '../styles/article-viewer.css';

export function NewsIndexLayout({ children }: { children: ReactNode }) {
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
                {children}
            </div>
        </main>
    );
}

export function NewsArticleLayout({ children }: { children: ReactNode }) {
    return <main className="news-page news-editorial">
        <div className="news-wrap">
            <div className="news-masthead">
                <Link to={NEWS_INDEX_PATH}>
                    <ArrowLeft /> All news
                </Link>
                <a href={NEWS_RSS_PATH}>
                    <Rss /> RSS feed
                </a>
            </div>
            {children}
        </div>
    </main>;
}
