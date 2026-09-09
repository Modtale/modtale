import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { NEWS_POSTS, NEWS_INDEX_PATH, getNewsPostPath } from '@/data/news';
import './home-news.css';

const posts = [...NEWS_POSTS]
    .sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt))
    .slice(0, 2);
const dateFormat = new Intl.DateTimeFormat('en-US', {
    month: 'long', day: 'numeric', year: 'numeric', timeZone: 'America/New_York',
});

export const HomeNewsSection = () => {
    if (!posts.length) return null;
    return (
        <section id="news" className="home-news" aria-labelledby="home-news-title">
            <div className="home-news-wrap">
                <header className="home-news-heading">
                    <div>
                        <h2 id="home-news-title">The latest from Modtale.</h2>
                    </div>
                    <Link className="home-news-all" to={NEWS_INDEX_PATH}>All news <ArrowRight size={18} aria-hidden="true" /></Link>
                </header>
                <div className="home-news-grid">
                    {posts.map(post => (
                        <article key={post.slug}>
                            <Link className="home-news-story" to={getNewsPostPath(post)}>
                                <div className="home-news-image">
                                    <img src={post.heroImage} alt={post.heroAlt} width={2400} height={1260} loading="lazy" decoding="async" />
                                </div>
                                <div className="home-news-story-copy">
                                    <div className="home-news-date"><time dateTime={post.publishedAt}>{dateFormat.format(new Date(post.publishedAt))}</time><span>{post.readingTime}</span></div>
                                    <h3>{post.title}</h3>
                                    <p>{post.excerpt}</p>
                                    <span className="home-news-read">Read the story <ArrowRight size={17} aria-hidden="true" /></span>
                                </div>
                            </Link>
                        </article>
                    ))}
                </div>
            </div>
        </section>
    );
};
