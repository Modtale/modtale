import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { NEWS_POSTS, NEWS_INDEX_PATH } from '@/data/news';
import './home-news.css';
import { NewsCard } from '@/modules/news/components/NewsCard';

const posts = [...NEWS_POSTS]
    .sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt))
    .slice(0, 2);


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
                    {posts.map(post => <NewsCard key={post.slug} post={post} heading="h3" />)}
                </div>
            </div>
        </section>
    );
};
