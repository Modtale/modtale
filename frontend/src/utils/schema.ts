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