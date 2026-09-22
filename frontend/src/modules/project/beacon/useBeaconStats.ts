import { useEffect, useState } from 'react';
import type { BeaconRange, BeaconStats } from './types';

export function useBeaconStats(projectId: string, range: BeaconRange = '7d') {
    const [retry, setRetry] = useState(0);
    const [state, setState] = useState<{ key: string; stats: BeaconStats | null; loading: boolean; error: boolean }>({ key: '', stats: null, loading: true, error: false });
    const key = `${projectId}:${range}`;
    useEffect(() => {
        const controller = new AbortController();
        setState({ key, stats: null, loading: true, error: false });
        fetch(`/integrations/beacon/${encodeURIComponent(projectId)}.json?range=${range}`, { signal: controller.signal })
            .then(async response => {
                if (!response.ok) throw new Error('Unable to load activity');
                const data = await response.json();
                if (!controller.signal.aborted) setState({ key, stats: data.stats, loading: false, error: false });
            })
            .catch(() => {
                if (!controller.signal.aborted) setState({ key, stats: null, loading: false, error: true });
            });
        return () => controller.abort();
    }, [projectId, range, retry, key]);
    return { ...(state.key === key ? state : { stats: null, loading: true, error: false }), refresh: () => setRetry(value => value + 1) };
}
