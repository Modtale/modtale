import React from 'react';
import { Helmet } from 'react-helmet-async';
import { useLocation } from 'react-router-dom';
import { DEFAULT_SEO, ROUTE_SEO } from '../data/seo-constants';

export const SEOHead: React.FC = () => {
    const location = useLocation();
    const path = location.pathname.replace(/\/$/, "");

    let title = DEFAULT_SEO.title;
    let description = DEFAULT_SEO.description;
    let keywords = DEFAULT_SEO.keywords;

    if (ROUTE_SEO[path]) {
        title = ROUTE_SEO[path].title;
        description = ROUTE_SEO[path].description;
        if (ROUTE_SEO[path].keywords) {
            keywords = ROUTE_SEO[path].keywords;
        }
    }

    return (
        <Helmet>
            <title>{title}</title>
            <meta name="description" content={description} />
            <meta name="keywords" content={keywords} />

            <meta property="og:title" content={title} />
            <meta property="og:description" content={description} />

            <link rel="canonical" href={`https://modtale.net${path === '' ? '/' : path}`} />
        </Helmet>
    );
};