import React from 'react';
import { Helmet } from 'react-helmet-async';
import { useLocation } from 'react-router-dom';
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
        const view = searchParams.get('view') || '';
        const query = searchParams.get('q') || '';
        const dynamicSEO = generateDynamicSEO({ title, description }, page, sort, view, query);
        title = dynamicSEO.title;
        description = dynamicSEO.description;
    }

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

            <meta name="twitter:title" content={title} />
            <meta name="twitter:description" content={description} />

            <link rel="canonical" href={canonicalUrl} />
        </Helmet>
    );
};
