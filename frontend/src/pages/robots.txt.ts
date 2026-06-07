import type { APIRoute } from 'astro';

const PROD_ROBOTS = `User-agent: *
Allow: /
Allow: /api/v1/og/
Disallow: /api/
Disallow: /oauth2/
Disallow: /upload
Disallow: /analytics
Disallow: /admin
Disallow: /settings

Sitemap: https://api.modtale.net/sitemap.xml
`;

const DEV_ROBOTS = `User-agent: *
Disallow: /
`;

export const GET: APIRoute = ({ url }) => {
    const body = url.hostname === 'dev.modtale.net' ? DEV_ROBOTS : PROD_ROBOTS;

    return new Response(body, {
        headers: {
            'Content-Type': 'text/plain; charset=utf-8',
            'Cache-Control': 'public, max-age=300'
        }
    });
};
