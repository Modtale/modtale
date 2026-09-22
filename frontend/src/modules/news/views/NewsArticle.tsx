import { useNewsPost } from '../api/useNews';
import { NewsBody } from '../components/NewsBody';
import React from 'react';
import { ArrowLeft, Rss } from 'lucide-react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { NEWS_INDEX_PATH, NEWS_RSS_PATH } from '@/data/news';
import type { User } from '@/types';
import '../styles/news.css';
import '../styles/article-viewer.css';

export const NewsArticle: React.FC<{ currentUser?: User | null }> = () => {
    const { slug } = useParams();
    const { post, error, missing } = useNewsPost(slug);
    if (missing) return <Navigate to={NEWS_INDEX_PATH} replace />;
    if (!post) return <main className="news-page news-editorial"><div className="news-wrap py-12" role={error ? 'alert' : 'status'}>{error ? 'News is temporarily unavailable. Please try again shortly.' : 'Loading article…'}</div></main>;
    return (
        <main className="news-page news-editorial">
            <div className="news-wrap">
                <div className="news-masthead">
                    <Link to={NEWS_INDEX_PATH}>
                        <ArrowLeft /> All news
                    </Link>
                    <a href={NEWS_RSS_PATH}>
                        <Rss /> RSS feed
                    </a>
                </div>
                <article>
                    <div className="news-article-intro">
                    <header className="news-heading">
                        <h1>{post.title}</h1>
                        <p className="news-deck">{post.description}</p>
                        <div className="news-byline">
                            <img src="/assets/favicon.svg" alt="" />
                            <strong>{post.author}</strong>
                            <i />
                            <time dateTime={post.publishedAt}>
                                {new Intl.DateTimeFormat('en-US', { month: 'long', day: 'numeric', year: 'numeric', timeZone: 'America/New_York' }).format(new Date(post.publishedAt))}
                            </time>
                            <i />
                            <span>{post.readingTime}</span>
                        </div>
                    </header>
                    <figure className="news-article-hero">
                        <img
                            src={post.heroImage}
                            alt={post.heroAlt}
                            width="2400"
                            height="1260"
                            fetchPriority="high"
                        />
                    </figure>
                    </div>
                    <div className="news-reading-layout">
                    <div className="news-copy news-prose">
                        <NewsBody content={post.body || ''} />
                        <footer className="news-article-footer">
                            <Link to={NEWS_INDEX_PATH}>
                                More from Modtale{' '}
                                <span aria-hidden="true">→</span>
                            </Link>
                        </footer>
                    </div>
                    </div>
                </article>
            </div>
        </main>
    );
};
