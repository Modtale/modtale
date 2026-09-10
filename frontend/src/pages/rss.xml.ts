import type { APIRoute } from 'astro';
import { buildNewsRssHeadResponse, buildNewsRssResponse } from '@/utils/newsRss';

export const GET: APIRoute = async ({ url }) => buildNewsRssResponse(url.origin);
export const HEAD: APIRoute = async () => buildNewsRssHeadResponse();
