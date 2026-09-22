import { useNewsPosts } from '../api/useNews';
import React from 'react';
import { NewsCard } from '../components/NewsCard';
import { NewsIndexLayout } from '../components/NewsLayout';
import { NewsFeedSkeleton } from '../components/NewsSkeleton';

export const NewsIndex: React.FC = () => {
    const { posts, loading, error } = useNewsPosts();
    return (
        <NewsIndexLayout>
            {error && <p role="alert">News is temporarily unavailable. Please try again shortly.</p>}
            {loading && posts.length === 0 ? <NewsFeedSkeleton /> : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    {posts.map((post, index) => <NewsCard key={post.slug} post={post} priority={index === 0} />)}
                </div>
            )}
        </NewsIndexLayout>
    );
};
