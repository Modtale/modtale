import type { APIRoute } from 'astro';
import { BACKEND_URL } from '../utils/api';

export const GET: APIRoute = async () => {
    try {
        const response = await fetch(`${BACKEND_URL}/sitemap.xml`);

        if (!response.ok) {
            return new Response(`Error fetching sitemap: ${response.statusText}`, {
                status: response.status
            });
        }

        const xml = await response.text();

        return new Response(xml, {
            headers: {
                'Content-Type': 'application/xml',
                'Cache-Control': 'public, max-age=3600'
            }
        });
    } catch (error) {
        console.error('Sitemap proxy error:', error);
        return new Response('Internal Server Error', { status: 500 });
    }
};