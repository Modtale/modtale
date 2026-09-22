import { fetchNewsPosts } from '@/modules/news/api/newsClient';
import type { APIRoute } from 'astro';
import { buildNewsRssHeadResponse, buildNewsRssResponse } from '@/utils/newsRss';

export const GET: APIRoute = async ({ url }) => {
    try { return buildNewsRssResponse(url.origin, await fetchNewsPosts()); }
    catch { return new Response('News feed temporarily unavailable.', { status: 503 }); }
};
export const HEAD: APIRoute = async () => buildNewsRssHeadResponse();
