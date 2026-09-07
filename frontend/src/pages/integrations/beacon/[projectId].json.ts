import type { APIRoute } from 'astro';
import { getBeaconStats } from '../../../modules/project/beacon/server';
import type { BeaconRange } from '../../../modules/project/beacon/types';

export const GET: APIRoute = async ({ params, url }) => {
    const projectId = params.projectId || '';
    const range = url.searchParams.get('range') || '7d';
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(projectId) || !['24h', '7d', '30d'].includes(range)) {
        return Response.json({ error: 'Invalid project or range' }, { status: 400 });
    }
    try {
        const stats = await getBeaconStats(projectId, range as BeaconRange);
        return Response.json({ stats }, { headers: { 'Cache-Control': 'public, max-age=60, s-maxage=300' } });
    } catch {
        return Response.json({ error: 'Community activity is temporarily unavailable.' }, { status: 503, headers: { 'Cache-Control': 'no-store' } });
    }
};
