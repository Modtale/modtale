import type { APIRoute } from 'astro';

const BACKEND_SITEMAP_URL = 'https://api.modtale.net/sitemap.xml';

export const GET: APIRoute = async () => {
    const lastmod = new Date().toISOString();
    const body = `<?xml version="1.0" encoding="UTF-8"?>
<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <sitemap>
    <loc>https://modtale.net/sitemap-static.xml</loc>
    <lastmod>${lastmod}</lastmod>
  </sitemap>
  <sitemap>
    <loc>${BACKEND_SITEMAP_URL}</loc>
    <lastmod>${lastmod}</lastmod>
  </sitemap>
</sitemapindex>`;

    return new Response(body, {
        headers: {
            'Content-Type': 'application/xml',
            'Cache-Control': 'public, max-age=3600, s-maxage=14400, stale-while-revalidate=86400',
        },
    });
};
