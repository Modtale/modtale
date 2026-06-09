import type { Project } from '../types';
import type { SeoFaq } from '../data/seo-constants';
import { SiteRoutes } from './routes';
import { BACKEND_URL } from '@/utils/api';

const SITE_URL = 'https://modtale.net';
const SITE_LOGO = `${SITE_URL}/assets/logo.svg`;

export const generateWebsiteSchema = () => ({
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: 'Modtale',
    url: `${SITE_URL}/`,
    description: 'Community hub for Hytale mods, Hytale plugins, and Hytale modding projects.',
    potentialAction: {
        '@type': 'SearchAction',
        target: `${SITE_URL}/mods?q={search_term_string}`,
        'query-input': 'required name=search_term_string',
    },
});

export const generateOrganizationSchema = () => ({
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name: 'Modtale',
    url: `${SITE_URL}/`,
    logo: SITE_LOGO,
    description: 'Community platform for browsing and publishing Hytale mods, plugins, and creator projects.',
    sameAs: [
        'https://github.com/Modtale/modtale',
        'https://x.com/modtalenet',
        'https://bsky.app/profile/modtale.net',
        'https://discord.gg/PcFaDVYqVe',
    ],
});

export const generateItemListSchema = (items: Project[]) => {
    if (!items || items.length === 0) return null;

    return {
        '@context': 'https://schema.org',
        '@type': 'ItemList',
        itemListOrder: 'https://schema.org/ItemListOrderAscending',
        numberOfItems: items.length,
        itemListElement: items.map((item, index) => {
            const url = `${SITE_URL}${SiteRoutes.project(item)}`;
            const imageUrl = item.imageUrl
                ? (item.imageUrl.startsWith('/api') ? `${BACKEND_URL}${item.imageUrl}` : item.imageUrl)
                : undefined;

            return {
                '@type': 'ListItem',
                position: index + 1,
                url,
                name: item.title,
                description: item.description,
                image: imageUrl,
                author: {
                    '@type': 'Person',
                    name: item.author,
                },
            };
        }),
    };
};

export const getBreadcrumbsForClassification = (classification: string | 'All') => {
    const home = { name: 'Home', url: SiteRoutes.home() };
    switch (classification) {
        case 'PLUGIN':
            return [home, { name: 'Plugins', url: SiteRoutes.browse('PLUGIN') }];
        case 'MODPACK':
            return [home, { name: 'Modpacks', url: SiteRoutes.browse('MODPACK') }];
        case 'SAVE':
            return [home, { name: 'Worlds', url: SiteRoutes.browse('SAVE') }];
        case 'ART':
            return [home, { name: 'Art Assets', url: SiteRoutes.browse('ART') }];
        case 'DATA':
            return [home, { name: 'Data Assets', url: SiteRoutes.browse('DATA') }];
        default:
            return [home];
    }
};

export const generateBreadcrumbSchema = (breadcrumbs: { name: string; url: string }[]) => {
    if (!breadcrumbs || breadcrumbs.length === 0) return null;

    return {
        '@context': 'https://schema.org',
        '@type': 'BreadcrumbList',
        itemListElement: breadcrumbs.map((item, index) => ({
            '@type': 'ListItem',
            position: index + 1,
            name: item.name,
            item: item.url.startsWith('http') ? item.url : `${SITE_URL}${item.url}`,
        })),
    };
};

export const generateCollectionPageSchema = ({
    name,
    description,
    path,
    keywords,
    about = [],
}: {
    name: string;
    description: string;
    path: string;
    keywords: string;
    about?: string[];
}) => ({
    '@context': 'https://schema.org',
    '@type': 'CollectionPage',
    name,
    headline: name,
    description,
    url: `${SITE_URL}${path}`,
    keywords,
    isPartOf: {
        '@type': 'WebSite',
        name: 'Modtale',
        url: `${SITE_URL}/`,
    },
    about: about.map((item) => ({
        '@type': 'Thing',
        name: item,
    })),
});

export const generateFaqSchema = (faq: SeoFaq[]) => {
    if (!faq || faq.length === 0) return null;

    return {
        '@context': 'https://schema.org',
        '@type': 'FAQPage',
        mainEntity: faq.map((item) => ({
            '@type': 'Question',
            name: item.question,
            acceptedAnswer: {
                '@type': 'Answer',
                text: item.answer,
            },
        })),
    };
};
