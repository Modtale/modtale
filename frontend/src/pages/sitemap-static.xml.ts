import type { APIRoute } from 'astro';

const STATIC_ROUTES = [
    { path: '/', changefreq: 'daily', priority: '1.0' },
    { path: '/mods', changefreq: 'daily', priority: '0.95' },
    { path: '/plugins', changefreq: 'daily', priority: '0.9' },
    { path: '/modpacks', changefreq: 'daily', priority: '0.8' },
    { path: '/art', changefreq: 'weekly', priority: '0.75' },
    { path: '/data', changefreq: 'weekly', priority: '0.75' },
    { path: '/worlds', changefreq: 'daily', priority: '0.75' },
];

export const GET: APIRoute = async () => {
    const lastmod = new Date().toISOString();
    const urls = STATIC_ROUTES.map(({ path, changefreq, priority }) => `  <url>
    <loc>https://modtale.net${path}</loc>
    <lastmod>${lastmod}</lastmod>
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
