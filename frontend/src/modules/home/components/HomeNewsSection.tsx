import { useNewsPosts } from '@/modules/news/api/useNews';
import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { NEWS_INDEX_PATH } from '@/data/news';
import './home-news.css';
import { NewsCard } from '@/modules/news/components/NewsCard';

export const HomeNewsSection = () => {
    const { posts: allPosts } = useNewsPosts();
    const posts = allPosts.slice(0, 2);
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
