import type { Project } from '../types';
import type { SeoFaq } from '../data/seo-constants';
import { SiteRoutes } from './routes';
import { BACKEND_URL } from '@/utils/api';
import { generateProjectMeta } from '@/utils/meta';

const SITE_URL = 'https://modtale.net';
const SITE_LOGO = `${SITE_URL}/assets/logo.svg`;

const toAbsoluteUrl = (url?: string | null) => {
    if (!url) return undefined;
    if (/^https?:\/\//i.test(url)) return url;
    if (url.startsWith('/api')) return `${BACKEND_URL}${url}`;
    if (url.startsWith('/')) return `${SITE_URL}${url}`;
    return url;
};

const uniqueDefined = <T>(items: Array<T | null | undefined>) => (
    Array.from(new Set(items.filter(Boolean) as T[]))
);

const getProjectKindLabel = (classification?: string) => {
    switch (classification) {
        case 'MODPACK':
            return 'Hytale modpack';
        case 'SAVE':
            return 'Hytale world save';
        case 'ART':
            return 'Hytale art asset';
        case 'DATA':
            return 'Hytale data asset';
        case 'PLUGIN':
        default:
            return 'Hytale plugin';
    }
};

export const getProjectOgImageUrl = (projectId?: string | null) => (
    projectId ? `${BACKEND_URL}/api/v1/og/project/${projectId}.png` : SITE_LOGO
);

export const generateWebsiteSchema = () => ({
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: 'Modtale',
    url: `${SITE_URL}/`,
    description: 'Community hub for Hytale mods, Hytale plugins, and Hytale modpacks.',
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

export const generateProjectSchema = (project: Project | any) => {
    if (!project) return null;

    const meta = generateProjectMeta(project);
    const projectUrl = `${SITE_URL}${SiteRoutes.project(project)}`;
    const imageUrls = uniqueDefined([
        toAbsoluteUrl(project.imageUrl),
        getProjectOgImageUrl(project.id),
    ]);
    const authorName = project.author || 'Unknown';
    const tags = Array.isArray(project.tags) ? project.tags.filter(Boolean) : [];
    const interactionStatistic = uniqueDefined([
        project.downloadCount > 0 ? {
            '@type': 'InteractionCounter',
            interactionType: { '@type': 'DownloadAction' },
            userInteractionCount: project.downloadCount,
        } : null,
        project.favoriteCount > 0 ? {
            '@type': 'InteractionCounter',
            interactionType: { '@type': 'LikeAction' },
            userInteractionCount: project.favoriteCount,
        } : null,
    ]);
    const latestVersion = Array.isArray(project.versions)
        ? project.versions.find((version: any) => version?.reviewStatus === 'APPROVED') || project.versions[0]
        : null;
    const sameAs = uniqueDefined([
        project.repositoryUrl,
        project.links?.WEBSITE,
    ]);

    const schema: Record<string, any> = {
        '@context': 'https://schema.org',
        '@type': 'SoftwareApplication',
        '@id': `${projectUrl}#software`,
        name: project.title,
        url: projectUrl,
        description: meta?.description || project.description,
        image: imageUrls.length > 1 ? imageUrls : imageUrls[0],
        author: {
            '@type': 'Person',
            name: authorName,
            ...(project.authorId ? { url: `${SITE_URL}${SiteRoutes.creator(project.authorId, project.author)}` } : {}),
        },
        publisher: {
            '@type': 'Organization',
            name: 'Modtale',
            url: `${SITE_URL}/`,
        },
        applicationCategory: 'GameApplication',
        applicationSubCategory: getProjectKindLabel(project.classification),
        operatingSystem: 'Windows, macOS, Linux',
        isAccessibleForFree: true,
        offers: {
            '@type': 'Offer',
            price: 0,
            priceCurrency: 'USD',
            availability: 'https://schema.org/InStock',
            url: projectUrl,
        },
        ...(tags.length > 0 ? { keywords: tags.join(', ') } : {}),
        ...(project.createdAt ? { datePublished: project.createdAt } : {}),
        ...(project.updatedAt ? { dateModified: project.updatedAt } : {}),
        ...(project.license ? { license: project.license } : {}),
        ...(project.repositoryUrl ? { codeRepository: project.repositoryUrl } : {}),
        ...(latestVersion?.versionNumber ? { softwareVersion: latestVersion.versionNumber } : {}),
        ...(interactionStatistic.length > 0 ? { interactionStatistic } : {}),
        ...(sameAs.length > 0 ? { sameAs } : {}),
    };

    return schema;
};

export const generateProjectWebPageSchema = (project: Project | any) => {
    if (!project) return null;

    const meta = generateProjectMeta(project);
    const projectUrl = `${SITE_URL}${SiteRoutes.project(project)}`;
    const image = getProjectOgImageUrl(project.id);

    return {
        '@context': 'https://schema.org',
        '@type': 'WebPage',
        '@id': `${projectUrl}#webpage`,
        url: projectUrl,
        name: meta?.title || `${project.title} | Modtale`,
        headline: project.title,
        description: meta?.description || project.description,
        image,
        isPartOf: {
            '@type': 'WebSite',
            name: 'Modtale',
            url: `${SITE_URL}/`,
        },
        mainEntity: {
            '@id': `${projectUrl}#software`,
        },
        ...(project.createdAt ? { datePublished: project.createdAt } : {}),
        ...(project.updatedAt ? { dateModified: project.updatedAt } : {}),
    };
};

export const generateProjectSchemas = (project: Project | any) => {
    if (!project) return [];

    const projectUrl = SiteRoutes.project(project);
    const breadcrumbs = [
        ...getBreadcrumbsForClassification(project.classification || 'PLUGIN'),
        { name: project.title, url: projectUrl },
    ];

    return uniqueDefined([
        generateProjectWebPageSchema(project),
        generateProjectSchema(project),
        generateBreadcrumbSchema(breadcrumbs),
    ]);
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
