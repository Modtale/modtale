import type { Mod, Modpack, World } from '../types';
import { getProjectUrl } from './slug';

const API_URL = import.meta.env.PUBLIC_API_URL || 'https://api.modtale.net/api/v1';
const BACKEND_URL = new URL(API_URL).origin;

export const generateItemListSchema = (items: (Mod | Modpack | World)[]) => {
    if (!items || items.length === 0) return null;

    return {
        "@context": "https://schema.org",
        "@type": "ItemList",
        "itemListElement": items.map((item, index) => {
            const url = `https://modtale.net${getProjectUrl(item)}`;
            const imageUrl = item.imageUrl
                ? (item.imageUrl.startsWith('/api') ? `${BACKEND_URL}${item.imageUrl}` : item.imageUrl)
                : undefined;

            return {
                "@type": "ListItem",
                "position": index + 1,
                "url": url,
                "name": item.title,
                "description": item.description,
                "image": imageUrl,
                "author": {
                    "@type": "Person",
                    "name": item.author
                }
            };
        })
    };
};

export const getBreadcrumbsForClassification = (classification: string | 'All') => {
    const home = { name: 'Home', url: '/' };
    switch (classification) {
        case 'PLUGIN': return [home, { name: 'Plugins', url: '/plugins' }];
        case 'MODPACK': return [home, { name: 'Modpacks', url: '/modpacks' }];
        case 'SAVE': return [home, { name: 'Worlds', url: '/worlds' }];
        case 'ART': return [home, { name: 'Art Assets', url: '/art' }];
        case 'DATA': return [home, { name: 'Data Assets', url: '/data' }];
        default: return [home];
    }
};

export const generateBreadcrumbSchema = (breadcrumbs: { name: string; url: string }[]) => {
    if (!breadcrumbs || breadcrumbs.length === 0) return null;

    return {
        "@context": "https://schema.org",
        "@type": "BreadcrumbList",
        "itemListElement": breadcrumbs.map((item, index) => ({
            "@type": "ListItem",
            "position": index + 1,
            "name": item.name,
            "item": item.url.startsWith('http') ? item.url : `https://modtale.net${item.url}`
        }))
    };
};