import { describe, expect, it } from 'vitest';
import { generateBreadcrumbSchema, generateItemListSchema, getBreadcrumbsForClassification } from '@/utils/schema';

describe('schema utils', () => {
    it('returns null when no item list or breadcrumbs are provided', () => {
        expect(generateItemListSchema([] as any)).toBeNull();
        expect(generateBreadcrumbSchema([])).toBeNull();
    });

    it('builds item list schema entries with project urls and backend image urls', () => {
        const schema = generateItemListSchema([
            {
                id: 'p1',
                title: 'Sky Tools',
                description: 'Build faster',
                author: 'Ada',
                imageUrl: '/api/media/sky-tools.png',
                classification: 'PLUGIN'
            } as any,
            {
                id: 'p2',
                title: 'World Builder',
                slug: 'world-builder-p2',
                description: 'Shape a world',
                author: 'Grace',
                imageUrl: 'https://cdn.modtale.net/world.png',
                classification: 'SAVE'
            } as any
        ]);

        expect(schema).toEqual({
            '@context': 'https://schema.org',
            '@type': 'ItemList',
            itemListElement: [
                {
                    '@type': 'ListItem',
                    position: 1,
                    url: 'https://modtale.net/mod/sky-tools-p1',
                    name: 'Sky Tools',
                    description: 'Build faster',
                    image: 'http://localhost:8080/api/media/sky-tools.png',
                    author: {
                        '@type': 'Person',
                        name: 'Ada'
                    }
                },
                {
                    '@type': 'ListItem',
                    position: 2,
                    url: 'https://modtale.net/world/world-builder-p2',
                    name: 'World Builder',
                    description: 'Shape a world',
                    image: 'https://cdn.modtale.net/world.png',
                    author: {
                        '@type': 'Person',
                        name: 'Grace'
                    }
                }
            ]
        });
    });

    it('maps classification breadcrumbs to the correct browse routes', () => {
        expect(getBreadcrumbsForClassification('PLUGIN')).toEqual([
            { name: 'Home', url: '/' },
            { name: 'Plugins', url: '/plugins' }
        ]);

        expect(getBreadcrumbsForClassification('ART')).toEqual([
            { name: 'Home', url: '/' },
            { name: 'Art Assets', url: '/art' }
        ]);

        expect(getBreadcrumbsForClassification('All')).toEqual([
            { name: 'Home', url: '/' }
        ]);
    });

    it('builds breadcrumb schema and normalizes relative urls to absolute ones', () => {
        const schema = generateBreadcrumbSchema([
            { name: 'Home', url: '/' },
            { name: 'Docs', url: 'https://docs.modtale.net' }
        ]);

        expect(schema).toEqual({
            '@context': 'https://schema.org',
            '@type': 'BreadcrumbList',
            itemListElement: [
                {
                    '@type': 'ListItem',
                    position: 1,
                    name: 'Home',
                    item: 'https://modtale.net/'
                },
                {
                    '@type': 'ListItem',
                    position: 2,
                    name: 'Docs',
                    item: 'https://docs.modtale.net'
                }
            ]
        });
    });
});
