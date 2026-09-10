import React from 'react';
import { ArrowRight, Rss } from 'lucide-react';
import { Link } from 'react-router-dom';
import { NEWS_POSTS, NEWS_RSS_PATH, getNewsPostPath } from '@/data/news';
import '../styles/news.css';

export const NewsIndex: React.FC = () => (
    <main className="news-page">
        <div className="news-wrap news-index-wrap">
            <div className="news-masthead">
                <span className="news-wordmark">From the Modtale team</span>
                <a href={NEWS_RSS_PATH}>
                    <Rss /> RSS feed
                </a>
            </div>
            <header className="news-index-heading">
                <h1>News from Modtale</h1>
                <p>
                    News, new features, and the things we’re building for your
                    next world.
                </p>
            </header>
            {NEWS_POSTS.map((post) => (
                <article key={post.slug}>
                    <Link to={getNewsPostPath(post)} className="news-featured">
                        <img
                            src={post.heroImage}
                            alt={post.heroAlt}
                            width="2400"
                            height="1260"
                            fetchPriority="high"
                        />
                        <div className="news-featured-copy">
                            <div className="news-eyebrow">
                                Product update <i />
                                <time dateTime={post.publishedAt}>
                                    {new Date(
                                        post.publishedAt,
                                    ).toLocaleDateString('en-US', {
                                        month: 'long',
                                        day: 'numeric',
                                        year: 'numeric',
                                        timeZone: 'America/New_York',
                                    })}
                                </time>
                            </div>
                            <h2>{post.title}</h2>
                            <p>{post.excerpt}</p>
                            <span>
                                Read the story <ArrowRight />
                            </span>
                        </div>
                    </Link>
                </article>
            ))}
            <div className="news-index-footer">
                <p>
                    Good things are taking shape. Follow along with the Modtale
                    news feed.
                </p>
                <a href={NEWS_RSS_PATH}>
                    <Rss /> Subscribe via RSS
                </a>
            </div>
        </div>
    </main>
);
