import type { BeaconRange, BeaconStats } from './types';

const BASE = 'https://modstats.io/api/v1/stats/projects';
const TTL = 5 * 60_000;
const cache = new Map<string, { expires: number; value: unknown }>();
const pending = new Map<string, Promise<unknown>>();

async function readPublic(path: string): Promise<any> {
    const cached = cache.get(path);
    if (cached && cached.expires > Date.now()) return cached.value;
    if (pending.has(path)) return pending.get(path);
    const request = (async () => {
        const response = await fetch(`${BASE}${path}`, {
            signal: AbortSignal.timeout(8000),
            headers: { Accept: 'application/json' },
            redirect: 'error',
        });
        if (!response.ok) throw new Error('ModStats is unavailable');
        const value = await response.json();
        if (cache.size >= 200) cache.delete(cache.keys().next().value!);
        cache.set(path, { expires: Date.now() + TTL, value });
        return value;
    })().finally(() => pending.delete(path));
    pending.set(path, request);
    return request;
}

const count = (value: unknown): number => {
    if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) throw new Error('Invalid ModStats count');
    return value;
};

export async function getBeaconStats(projectId: string, range: BeaconRange): Promise<BeaconStats | null> {
    const index = await readPublic('');
    if (!Array.isArray(index.projects)) throw new Error('Invalid ModStats project index');
    // Match the provider's resolved Modtale ID, never a display name or a user-supplied URL.
    const matches = index.projects.filter((project: any) => project.modProfile?.modtaleProjectId === projectId);
    if (matches.length !== 1) return null;
    const match = matches[0];
    if (typeof match.slug !== 'string' || !/^[a-zA-Z0-9_-]+$/.test(match.slug)) throw new Error('Invalid ModStats slug');
    const data = await readPublic(`/${encodeURIComponent(match.slug)}/dashboard?activityRange=${range}&includeBreakdowns=false`);
    if (data.project?.modProfile?.modtaleProjectId !== projectId) return null;
    const summary = data.dashboard?.summary;
    const activity = data.dashboard?.charts?.activity?.data;
    if (!summary || !Array.isArray(activity)) throw new Error('Invalid ModStats dashboard');
    return {
        slug: match.slug,
        displayName: typeof data.project.displayName === 'string' ? data.project.displayName : match.slug,
        activeServers: count(summary.activeServers),
        activePlayers: count(summary.activePlayers),
        recordServers: count(summary.recordServers),
        recordPlayers: count(summary.recordPlayers),
        lastHeartbeatAt: typeof summary.lastHeartbeatAt === 'string' && Number.isFinite(Date.parse(summary.lastHeartbeatAt)) ? summary.lastHeartbeatAt : null,
        activity: activity.map((point: any) => {
            if (typeof point.bucketStart !== 'string' || !Number.isFinite(Date.parse(point.bucketStart))) throw new Error('Invalid ModStats timestamp');
            return { bucketStart: point.bucketStart, servers: count(point.servers), players: count(point.players) };
        }).sort((a, b) => Date.parse(a.bucketStart) - Date.parse(b.bucketStart)),
    };
}
