// @vitest-environment node
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';

const id = '24ddb6d2-ec47-4efb-b95d-b4a3c5ff8e45';
const project = { slug: 'example', displayName: 'Example', modProfile: { modtaleProjectId: id } };
const dashboard = {
    project,
    dashboard: { summary: { activeServers: 0, activePlayers: 0, recordServers: 12, recordPlayers: 24, lastHeartbeatAt: null }, charts: { activity: { data: [{ bucketStart: '2026-09-01T00:00:00Z', servers: 0, players: 0 }] } } },
    privateData: 'must not pass through',
};
let fetchMock: ReturnType<typeof vi.fn>;
beforeEach(() => { vi.resetModules(); fetchMock = vi.fn(); vi.stubGlobal('fetch', fetchMock); });
afterEach(() => vi.unstubAllGlobals());
const respond = (body: unknown) => fetchMock.mockResolvedValueOnce(Response.json(body));

describe('Beacon public data adapter', () => {
    it('matches the resolved Modtale ID and returns only aggregates, retaining zero counts', async () => {
        respond({ projects: [project] }); respond(dashboard);
        const { getBeaconStats } = await import('@/modules/project/beacon/server');
        const stats = await getBeaconStats(id, '7d');
        expect(stats).toEqual({ slug: 'example', displayName: 'Example', activeServers: 0, activePlayers: 0, recordServers: 12, recordPlayers: 24, lastHeartbeatAt: null, activity: dashboard.dashboard.charts.activity.data });
        expect(fetchMock.mock.calls[1][0]).toContain('activityRange=7d&includeBreakdowns=false');
    });
    it('does not match names, unknown projects, or ambiguous provider links', async () => {
        respond({ projects: [{ ...project, modProfile: {} }, project, project] });
        const { getBeaconStats } = await import('@/modules/project/beacon/server');
        expect(await getBeaconStats(id, '24h')).toBeNull();
        expect(await getBeaconStats('unknown', '24h')).toBeNull();
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });
    it('does not return a dashboard whose project mapping changed', async () => {
        respond({ projects: [project] }); respond({ ...dashboard, project: { ...project, modProfile: { modtaleProjectId: 'different' } } });
        const { getBeaconStats } = await import('@/modules/project/beacon/server');
        expect(await getBeaconStats(id, '7d')).toBeNull();
    });
    it('deduplicates requests and caches the index and each range', async () => {
        respond({ projects: [project] }); respond(dashboard); respond(dashboard);
        const { getBeaconStats } = await import('@/modules/project/beacon/server');
        await Promise.all([getBeaconStats(id, '7d'), getBeaconStats(id, '7d')]);
        await getBeaconStats(id, '7d');
        expect(fetchMock).toHaveBeenCalledTimes(2);
        await getBeaconStats(id, '30d');
        expect(fetchMock).toHaveBeenCalledTimes(3);
    });
    it('rejects malformed counts rather than presenting them as zero', async () => {
        respond({ projects: [project] }); respond({ ...dashboard, dashboard: { ...dashboard.dashboard, summary: { ...dashboard.dashboard.summary, activePlayers: -1 } } });
        const { getBeaconStats } = await import('@/modules/project/beacon/server');
        await expect(getBeaconStats(id, '7d')).rejects.toThrow('Invalid ModStats count');
    });
    it('does not cache upstream failures', async () => {
        fetchMock.mockResolvedValueOnce(new Response('', { status: 503 })); respond({ projects: [] });
        const { getBeaconStats } = await import('@/modules/project/beacon/server');
        await expect(getBeaconStats(id, '7d')).rejects.toThrow();
        expect(await getBeaconStats(id, '7d')).toBeNull();
    });
    it('validates public endpoint parameters before requesting upstream data', async () => {
        const { GET } = await import('@/pages/integrations/beacon/[projectId].json');
        const result = await GET({ params: { projectId: id }, url: new URL('http://localhost/?range=invalid') } as any);
        expect(result.status).toBe(400);
        expect(fetchMock).not.toHaveBeenCalled();
    });
    it('returns an explicit unavailable response without cache on upstream failures', async () => {
        fetchMock.mockRejectedValueOnce(new Error('timeout'));
        const { GET } = await import('@/pages/integrations/beacon/[projectId].json');
        const result = await GET({ params: { projectId: id }, url: new URL('http://localhost/') } as any);
        expect(result.status).toBe(503);
        expect(result.headers.get('Cache-Control')).toBe('no-store');
    });
});
