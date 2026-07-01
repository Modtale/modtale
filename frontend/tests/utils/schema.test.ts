import { describe, expect, it } from 'vitest';
import {
    generateBreadcrumbSchema,
    generateCollectionPageSchema,
    generateFaqSchema,
    generateItemListSchema,
    generateOrganizationSchema,
    generateProjectSchema,
    generateProjectSchemas,
    generateProjectWebPageSchema,
    getProjectOgImageUrl,
    generateWebsiteSchema,
    getBreadcrumbsForClassification,
} from '@/utils/schema';

describe('schema utils', () => {
    it('returns null when no item list or breadcrumbs are provided', () => {
        expect(generateItemListSchema([] as any)).toBeNull();
        expect(generateBreadcrumbSchema([])).toBeNull();
        expect(generateFaqSchema([])).toBeNull();
    });

    it('builds item list schema entries with project urls and backend image urls', () => {
        const schema = generateItemListSchema([
            {
                id: 'p1',
                title: 'Sky Tools',
                description: 'Build faster',
                author: 'Ada',
                imageUrl: '/api/media/sky-tools.png',
                classification: 'PLUGIN',
            } as any,
            {
                id: 'p2',
                title: 'World Builder',
                slug: 'world-builder-p2',
                description: 'Shape a world',
                author: 'Grace',
                imageUrl: 'https://cdn.modtale.net/world.png',
                classification: 'SAVE',
            } as any,
        ]);

        expect(schema).toEqual({
            '@context': 'https://schema.org',
            '@type': 'ItemList',
            itemListOrder: 'https://schema.org/ItemListOrderAscending',
            numberOfItems: 2,
            itemListElement: [
                {
                    '@type': 'ListItem',
                    position: 1,
                    url: 'https://modtale.net/mod/sky-tools~p1',
                    name: 'Sky Tools',
                    description: 'Build faster',
                    image: 'http://localhost:8080/api/media/sky-tools.png',
                    author: {
                        '@type': 'Person',
                        name: 'Ada',
                    },
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
                        name: 'Grace',
                    },
                },
            ],
        });
    });

    it('maps classification breadcrumbs to the correct browse routes', () => {
        expect(getBreadcrumbsForClassification('PLUGIN')).toEqual([
            { name: 'Home', url: '/' },
            { name: 'Plugins', url: '/plugins' },
        ]);

        expect(getBreadcrumbsForClassification('ART')).toEqual([
            { name: 'Home', url: '/' },
            { name: 'Art Assets', url: '/art' },
        ]);

        expect(getBreadcrumbsForClassification('All')).toEqual([
            { name: 'Home', url: '/' },
        ]);
    });

    it('builds breadcrumb schema and normalizes relative urls to absolute ones', () => {
        const schema = generateBreadcrumbSchema([
            { name: 'Home', url: '/' },
            { name: 'Docs', url: 'https://docs.modtale.net' },
        ]);

        expect(schema).toEqual({
            '@context': 'https://schema.org',
            '@type': 'BreadcrumbList',
            itemListElement: [
                {
                    '@type': 'ListItem',
                    position: 1,
                    name: 'Home',
                    item: 'https://modtale.net/',
                },
                {
                    '@type': 'ListItem',
                    position: 2,
                    name: 'Docs',
                    item: 'https://docs.modtale.net',
                },
            ],
        });
    });

    it('builds collection, faq, website, and organization schemas', () => {
        expect(generateCollectionPageSchema({
            name: 'Hytale Mods',
            description: 'Browse Hytale mods.',
            path: '/mods',
            keywords: 'hytale mods, hytale modding',
            about: ['Hytale mods', 'Hytale plugins'],
        })).toEqual({
            '@context': 'https://schema.org',
            '@type': 'CollectionPage',
            name: 'Hytale Mods',
            headline: 'Hytale Mods',
            description: 'Browse Hytale mods.',
            url: 'https://modtale.net/mods',
            keywords: 'hytale mods, hytale modding',
            isPartOf: {
                '@type': 'WebSite',
                name: 'Modtale',
                url: 'https://modtale.net/',
            },
            about: [
                { '@type': 'Thing', name: 'Hytale mods' },
                { '@type': 'Thing', name: 'Hytale plugins' },
            ],
        });

        expect(generateFaqSchema([
            { question: 'Where can I find Hytale mods?', answer: 'On Modtale.' },
        ])).toEqual({
            '@context': 'https://schema.org',
            '@type': 'FAQPage',
            mainEntity: [
                {
                    '@type': 'Question',
                    name: 'Where can I find Hytale mods?',
                    acceptedAnswer: {
                        '@type': 'Answer',
                        text: 'On Modtale.',
                    },
                },
            ],
        });

        expect(generateWebsiteSchema()).toMatchObject({
            '@context': 'https://schema.org',
            '@type': 'WebSite',
            name: 'Modtale',
            url: 'https://modtale.net/',
        });

        expect(generateOrganizationSchema()).toMatchObject({
            '@context': 'https://schema.org',
            '@type': 'Organization',
            name: 'Modtale',
            url: 'https://modtale.net/',
        });
    });

    it('builds rich project schema with canonical url, offer, author, and engagement facts', () => {
        const project = {
            id: 'p1',
            title: 'LevelingCore',
            slug: 'levelingcore',
            description: 'Adds levels and progression.',
            authorId: 'u1',
            author: 'AzureDoom',
            imageUrl: '/api/media/levelingcore.png',
            classification: 'PLUGIN',
            tags: ['rpg', 'server'],
            downloadCount: 1234,
            favoriteCount: 56,
            repositoryUrl: 'https://github.com/example/levelingcore',
            updatedAt: '2026-06-18T12:00:00',
            createdAt: '2026-06-01',
            versions: [{ versionNumber: '1.2.0', reviewStatus: 'APPROVED' }],
        } as any;

        expect(getProjectOgImageUrl('p1')).toBe('http://localhost:8080/api/v1/og/project/p1.png');
        expect(generateProjectSchema(project)).toMatchObject({
            '@context': 'https://schema.org',
            '@type': 'SoftwareApplication',
            '@id': 'https://modtale.net/mod/levelingcore#software',
            name: 'LevelingCore',
            url: 'https://modtale.net/mod/levelingcore',
            applicationCategory: 'GameApplication',
            applicationSubCategory: 'Hytale plugin',
            operatingSystem: 'Windows, macOS, Linux',
            isAccessibleForFree: true,
            image: [
                'http://localhost:8080/api/media/levelingcore.png',
                'http://localhost:8080/api/v1/og/project/p1.png',
            ],
            author: {
                '@type': 'Person',
                name: 'AzureDoom',
                url: 'https://modtale.net/creator/AzureDoom',
            },
            offers: {
                '@type': 'Offer',
                price: 0,
                priceCurrency: 'USD',
                availability: 'https://schema.org/InStock',
                url: 'https://modtale.net/mod/levelingcore',
            },
            keywords: 'rpg, server',
            codeRepository: 'https://github.com/example/levelingcore',
            softwareVersion: '1.2.0',
            datePublished: '2026-06-01',
            dateModified: '2026-06-18T12:00:00',
        });

        expect(generateProjectSchema(project)).toMatchObject({
            interactionStatistic: [
                {
                    '@type': 'InteractionCounter',
                    interactionType: { '@type': 'DownloadAction' },
                    userInteractionCount: 1234,
                },
                {
                    '@type': 'InteractionCounter',
                    interactionType: { '@type': 'LikeAction' },
                    userInteractionCount: 56,
                },
            ],
        });
    });

    it('builds project page and breadcrumb schemas as a reusable graph', () => {
        const project = {
            id: 'p2',
            title: 'Sky Atlas',
            description: 'A curated pack.',
            author: 'Grace',
            classification: 'MODPACK',
            downloadCount: 0,
            favoriteCount: 0,
        } as any;

        expect(generateProjectWebPageSchema(project)).toMatchObject({
            '@context': 'https://schema.org',
            '@type': 'WebPage',
            '@id': 'https://modtale.net/modpack/sky-atlas~p2#webpage',
            url: 'https://modtale.net/modpack/sky-atlas~p2',
            headline: 'Sky Atlas',
            mainEntity: {
                '@id': 'https://modtale.net/modpack/sky-atlas~p2#software',
            },
        });

        const graph = generateProjectSchemas(project);
        expect(graph).toHaveLength(3);
        expect(graph.map((entry: any) => entry['@type'])).toEqual([
            'WebPage',
            'SoftwareApplication',
            'BreadcrumbList',
        ]);
        expect(graph[2]).toMatchObject({
            itemListElement: [
                { name: 'Home', item: 'https://modtale.net/' },
                { name: 'Modpacks', item: 'https://modtale.net/modpacks' },
                { name: 'Sky Atlas', item: 'https://modtale.net/modpack/sky-atlas~p2' },
            ],
        });
    });
});
