import React from 'react';
import { Helmet } from 'react-helmet-async';
import { useLocation } from 'react-router-dom';
import { NEWS_POSTS, getAbsoluteUrl, getNewsPostBySlug } from '@/data/news';
import { DEFAULT_SEO, ROUTE_SEO, generateDynamicSEO } from '@/data/seo-constants';
import { buildCanonicalUrl, getRobotsDirective, isBrowseRoutePath, normalizeSeoPath } from '@/utils/seo';

export const SEOHead: React.FC = () => {
    const location = useLocation();
    const path = normalizeSeoPath(location.pathname);
    const searchParams = new URLSearchParams(location.search);

    let title = DEFAULT_SEO.title;
    let description = DEFAULT_SEO.description;
    let keywords = DEFAULT_SEO.keywords;

    const routeSeo = ROUTE_SEO[path === '' ? '/' : path];
    if (routeSeo) {
        title = routeSeo.title;
        description = routeSeo.description;
        keywords = routeSeo.keywords;
    }

    if (isBrowseRoutePath(path)) {
        const page = Number.parseInt(searchParams.get('page') || '0', 10) || 0;
        const sort = searchParams.get('sort') || '';
        const query = searchParams.get('q') || '';
        const dynamicSEO = generateDynamicSEO({ title, description }, page, sort, query);
        title = dynamicSEO.title;
        description = dynamicSEO.description;
    }

    const newsPost = path.startsWith('/news/') ? getNewsPostBySlug(path.slice('/news/'.length)) : undefined;
    const isNews = path === '/news' || Boolean(newsPost);
    if (path === '/news') {
        title = 'Modtale News | Product Updates and Creator Notes';
        description = 'Read Modtale product updates, creator notes, feature tours, and platform news for Hytale mods, plugins, worlds, assets, and modpacks.';
    } else if (newsPost) {
        title = `${newsPost.title} | Modtale News`;
        description = newsPost.description;
        keywords = newsPost.tags.join(', ');
    }
    const newsImage = isNews ? getAbsoluteUrl((newsPost || NEWS_POSTS[0]).socialImage) : undefined;
    const canonicalUrl = buildCanonicalUrl(path, searchParams);
    const robots = getRobotsDirective(path, searchParams);

    return (
        <Helmet>
            <title>{title}</title>
            <meta name="description" content={description} />
            <meta name="keywords" content={keywords} />
            <meta name="robots" content={robots} />
            <meta name="googlebot" content={robots} />

            <meta property="og:title" content={title} />
            <meta property="og:description" content={description} />
            <meta property="og:url" content={canonicalUrl} />
            <meta property="og:site_name" content="Modtale" />

            {isNews && <meta property="og:type" content={newsPost ? 'article' : 'website'} />}
            {newsImage && <meta property="og:image" content={newsImage} />}
            {newsImage && <meta name="twitter:image" content={newsImage} />}
            {newsPost && <meta property="article:published_time" content={newsPost.publishedAt} />}
            {newsPost && <meta property="article:modified_time" content={newsPost.updatedAt} />}
            {newsPost && <meta property="article:author" content={newsPost.author} />}

            <meta name="twitter:title" content={title} />
            <meta name="twitter:description" content={description} />

            <link rel="canonical" href={canonicalUrl} />
        </Helmet>
    );
};
