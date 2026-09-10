import React from 'react';
import { ArrowLeft, Rss } from 'lucide-react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { NEWS_INDEX_PATH, NEWS_RSS_PATH, getNewsPostBySlug } from '@/data/news';
import type { User } from '@/types';
import { LauncherStory } from './LauncherStory';
import { ModpacksStory } from './ModpacksStory';
import '../styles/news.css';

export const NewsArticle: React.FC<{ currentUser?: User | null }> = () => {
    const { slug } = useParams();
    const post = getNewsPostBySlug(slug);
    if (!post) return <Navigate to={NEWS_INDEX_PATH} replace />;
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
                    <header className="news-heading">
                        <h1>{post.title}</h1>
                        <p className="news-deck">{post.description}</p>
                        <div className="news-byline">
                            <img src="/assets/favicon.svg" alt="" />
                            <strong>{post.author}</strong>
                            <i />
                            <time dateTime={post.publishedAt}>
                                September 7, 2026
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
                    <div className="news-copy news-prose">
                        {post.slug === 'modtale-launcher' ? (
                            <LauncherStory />
                        ) : (
                            <ModpacksStory />
                        )}
                        <footer className="news-article-footer">
                            <Link to={NEWS_INDEX_PATH}>
                                More from Modtale{' '}
                                <span aria-hidden="true">→</span>
                            </Link>
                        </footer>
                    </div>
                </article>
            </div>
        </main>
    );
};
