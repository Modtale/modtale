import mediaVersions from './newsMediaVersions.json';

export interface NewsPost {
    slug: string;
    title: string;
    description: string;
    excerpt: string;
    publishedAt: string;
    updatedAt: string;
    author: string;
    tags: string[];
    readingTime: string;
    heroImage: string;
    heroAlt: string;
    socialImage: string;
    socialImageAlt: string;
}

export const SITE_URL = 'https://modtale.net';
export const NEWS_INDEX_PATH = '/news';
export const NEWS_RSS_PATH = '/rss.xml';

export const NEWS_POSTS: NewsPost[] = [
    {
        slug: 'modtale-launcher',
        title: 'Meet the Modtale Launcher',
        description:
            'A native home for your Hytale projects, worlds, and next adventure.',
        excerpt:
            'Discover real projects, give each world its own mod selection, and make room for a little more you. Modtale comes to your desktop.',
        publishedAt: '2026-09-07T12:00:00-04:00',
        updatedAt: '2026-09-07T12:00:00-04:00',
        author: 'Modtale Team',
        tags: ['Product Update', 'Launcher', 'Hytale'],
        readingTime: '7 min read',
        heroImage: `/assets/news/launcher-thumbnail.png?v=${mediaVersions['launcher-thumbnail']}`,
        heroAlt:
            'Modtale Launcher showing its Play page, on a blue background.',
        socialImage: `/assets/news/launcher-thumbnail.png?v=${mediaVersions['launcher-thumbnail']}`,
        socialImageAlt:
            'Modtale Launcher showing its Play page, on a blue background.',
    },
    {
        slug: 'modpacks-v2',
        title: 'Modpacks v2: from your world to theirs',
        description:
            'Build and share Hytale modpacks with chosen versions, per-mod configs, and projects from Modtale and CurseForge.',
        excerpt:
            'Build packs on the website, attach configs to individual mods, include CurseForge projects, and turn a shared setup into a release.',
        publishedAt: '2026-09-07T11:00:00-04:00',
        updatedAt: '2026-09-08T20:30:00-04:00',
        author: 'Modtale Team',
        tags: ['Product Update', 'Modpacks', 'Hytale'],
        readingTime: '7 min read',
        heroImage: `/assets/news/modpacks-thumbnail.png?v=${mediaVersions['modpacks-thumbnail']}`,
        heroAlt:
            'Modtale Modpacks v2 beside a field of blue hexagons filled with real mod icons, including LevelingCore, Ev0s Smokable Herbs, and Hexcode.',
        socialImage: `/assets/news/modpacks-thumbnail.png?v=${mediaVersions['modpacks-thumbnail']}`,
        socialImageAlt:
            'Modtale Modpacks v2 beside a field of blue hexagons filled with real mod icons, including LevelingCore, Ev0s Smokable Herbs, and Hexcode.',
    },
];

export const getNewsPostPath = (post: NewsPost) =>
    `${NEWS_INDEX_PATH}/${post.slug}`;

export const getAbsoluteUrl = (path: string) => {
    if (path.startsWith('http')) return path;
    return `${SITE_URL}${path.startsWith('/') ? path : `/${path}`}`;
};

export const getNewsPostUrl = (post: NewsPost) =>
    getAbsoluteUrl(getNewsPostPath(post));

export const getNewsPostBySlug = (slug: string | undefined) =>
    NEWS_POSTS.find((post) => post.slug === slug);

export const getLatestNewsPostDate = () =>
    NEWS_POSTS.map((post) => new Date(post.updatedAt).getTime())
        .filter((timestamp) => Number.isFinite(timestamp))
        .sort((a, b) => b - a)[0];
