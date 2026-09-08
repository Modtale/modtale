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
        slug: 'fresh-feature-showcase',
        title: 'Freshly Added: A Visual Tour of Modtale Features',
        description: 'See the newest-feeling Modtale workflows in motion: discovery, project pages, creator tools, API docs, embeds, and RSS.',
        excerpt: 'A visual update tour of the real Modtale features that help players discover projects and help creators publish, explain, and share their work.',
        publishedAt: '2026-06-16T12:00:00-04:00',
        updatedAt: '2026-06-16T12:00:00-04:00',
        author: 'Modtale Team',
        tags: ['Product Update', 'Hytale Mods', 'Creator Tools', 'RSS'],
        readingTime: '5 min read',
        heroImage: '/assets/news/fresh-feature-showcase-hero.svg',
        heroAlt: 'Modtale feature showcase with branded project cards, creator analytics, and RSS previews.',
        socialImage: '/assets/news/fresh-feature-showcase-og.png',
        socialImageAlt: 'Modtale feature showcase social preview.',
    },
];

export const getNewsPostPath = (post: NewsPost) => `${NEWS_INDEX_PATH}/${post.slug}`;

export const getAbsoluteUrl = (path: string) => {
    if (path.startsWith('http')) return path;
    return `${SITE_URL}${path.startsWith('/') ? path : `/${path}`}`;
};

export const getNewsPostUrl = (post: NewsPost) => getAbsoluteUrl(getNewsPostPath(post));

export const getNewsPostBySlug = (slug: string | undefined) => (
    NEWS_POSTS.find((post) => post.slug === slug)
);

export const getLatestNewsPostDate = () => (
    NEWS_POSTS
        .map((post) => new Date(post.updatedAt).getTime())
        .filter((timestamp) => Number.isFinite(timestamp))
        .sort((a, b) => b - a)[0]
);
