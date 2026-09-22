import React from 'react';
import { Link } from 'react-router-dom';
import { getNewsPostPath, type NewsPost } from '@/data/news';
import '../styles/news-card.css';

const dateFormat = new Intl.DateTimeFormat('en-US', {
    month: 'long', day: 'numeric', year: 'numeric', timeZone: 'America/New_York',
});

export function NewsCard({ post, heading: Heading = 'h2', priority = false }: {
    post: NewsPost; heading?: 'h2' | 'h3'; priority?: boolean;
}) {
    return (
        <article>
            <Link className="news-card" to={getNewsPostPath(post)}>
                <div className="news-card-image">
                    <img src={post.heroImage} alt={post.heroAlt} width={2400} height={1260}
                        loading={priority ? 'eager' : 'lazy'} fetchPriority={priority ? 'high' : undefined} decoding="async" />
                </div>
                <div className="news-card-copy">
                    <time dateTime={post.publishedAt}>{dateFormat.format(new Date(post.publishedAt))}</time>
                    <Heading>{post.title}</Heading>
                </div>
            </Link>
        </article>
    );
}
