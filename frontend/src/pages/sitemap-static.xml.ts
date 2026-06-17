import type { APIRoute } from 'astro';
import { NEWS_INDEX_PATH, NEWS_POSTS, getNewsPostPath, getLatestNewsPostDate } from '../data/news';

const STATIC_ROUTES: Array<{ path: string; changefreq: string; priority: string; lastmod?: number }> = [
    { path: '/', changefreq: 'daily', priority: '1.0' },
    { path: '/mods', changefreq: 'daily', priority: '0.95' },
    { path: '/plugins', changefreq: 'daily', priority: '0.9' },
    { path: '/modpacks', changefreq: 'daily', priority: '0.8' },
    { path: '/art', changefreq: 'weekly', priority: '0.75' },
    { path: '/data', changefreq: 'weekly', priority: '0.75' },
    { path: '/worlds', changefreq: 'daily', priority: '0.75' },
    {
        path: NEWS_INDEX_PATH,
        changefreq: 'weekly',
        priority: '0.72',
        lastmod: getLatestNewsPostDate(),
    },
    ...NEWS_POSTS.map((post) => ({
        path: getNewsPostPath(post),
        changefreq: 'monthly',
        priority: '0.68',
        lastmod: new Date(post.updatedAt).getTime(),
    })),
];

export const GET: APIRoute = async () => {
    const defaultLastmod = new Date().toISOString();
    const urls = STATIC_ROUTES.map(({ path, changefreq, priority, lastmod }) => `  <url>
    <loc>https://modtale.net${path}</loc>
    <lastmod>${typeof lastmod === 'number' && Number.isFinite(lastmod) ? new Date(lastmod).toISOString() : defaultLastmod}</lastmod>
    <changefreq>${changefreq}</changefreq>
    <priority>${priority}</priority>
  </url>`).join('\n');

    const body = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls}
</urlset>`;

    return new Response(body, {
        headers: {
            'Content-Type': 'application/xml',
            'Cache-Control': 'public, max-age=3600, s-maxage=14400, stale-while-revalidate=86400',
        },
    });
};
