import { useNewsPost } from '../api/useNews';
import { NewsBody } from '../components/NewsBody';
import React from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { NEWS_INDEX_PATH } from '@/data/news';
import type { User } from '@/types';
import { NewsArticleLayout } from '../components/NewsLayout';
import { NewsArticleSkeleton } from '../components/NewsSkeleton';

export const NewsArticle: React.FC<{ currentUser?: User | null }> = () => {
    const { slug } = useParams();
    const { post, error, missing } = useNewsPost(slug);
    if (missing) return <Navigate to={NEWS_INDEX_PATH} replace />;
    if (!post) return <NewsArticleLayout>
        {error ? <div className="py-12" role="alert">News is temporarily unavailable. Please try again shortly.</div> : <NewsArticleSkeleton />}
    </NewsArticleLayout>;
    return (
        <NewsArticleLayout>
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
        </NewsArticleLayout>
    );
};
