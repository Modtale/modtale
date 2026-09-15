import { useCallback, useEffect, useRef, useState } from 'react';
import { getModerationQueuePage, type QueuePage, type QueueFilter } from '../api/moderationQueue';
import { extractApiErrorMessage } from '@/utils/api';
const empty: QueuePage = { items: [], nextCursor: null, unavailableItems: 0, order: 'PROJECT_VERSION' };
export function useModerationQueue(enabled: boolean, subject: string, filter: QueueFilter = 'ALL') {
    const [page, setPage] = useState(empty);
    const [pageSubject, setPageSubject] = useState(subject);
    const [pageFilter, setPageFilter] = useState(filter);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [loaded, setLoaded] = useState(false);
    const [navigation, setNavigation] = useState({ revision: 0, target: 'page' as 'page' | 'error' });
    const access = useRef({ enabled, subject, filter }); access.current = { enabled, subject, filter };
    const generation = useRef(0);
    const request = useRef<AbortController | null>(null);
    const cursor = useRef<string | null>(null);
    const nextCursor = useRef<string | null>(null);
    const attemptedCursor = useRef<string | null>(null);
    const failed = useRef(false);
    const load = useCallback(async (position: string | null, background = false, focus = false) => {
        if (!access.current.enabled || (background && (request.current || failed.current))) return;
        request.current?.abort(); const controller = new AbortController(); request.current = controller;
        const epoch = ++generation.current; const owner = access.current.subject; const selectedFilter = access.current.filter;
        attemptedCursor.current = position;
        setLoading(true);
        const current = () => generation.current === epoch && access.current.enabled && access.current.subject === owner && access.current.filter === selectedFilter;
        try {
            const response = await getModerationQueuePage(position, controller.signal, selectedFilter);
            if (!current()) return;
            cursor.current = position; nextCursor.current = response.nextCursor;
            setPage(response); setPageSubject(owner); setPageFilter(selectedFilter); failed.current = false; setError(null); setLoaded(true);
            if (focus) setNavigation(previous => ({ revision: previous.revision + 1, target: 'page' }));
        } catch (failure) {
            if (current() && !controller.signal.aborted) { failed.current = true; setError(extractApiErrorMessage(failure, 'We could not load this queue page.'));
                if (focus) setNavigation(previous => ({ revision: previous.revision + 1, target: 'error' }));
            }
        } finally {
            if (generation.current === epoch) { request.current = null; setLoading(false); }
        }
    }, []);
    useEffect(() => {
        generation.current++; request.current?.abort(); request.current = null;
        cursor.current = null; nextCursor.current = null; attemptedCursor.current = null; failed.current = false;
        setPage(empty); setPageSubject(subject); setPageFilter(filter); setError(null); setLoaded(false); setLoading(false); setNavigation({ revision: 0, target: 'page' });
        if (enabled) void load(null);
        return () => { generation.current++; request.current?.abort(); request.current = null; };
    }, [enabled, subject, filter, load]);
    const refresh = useCallback((background = false) => load(cursor.current, background), [load]);
    const restart = useCallback(() => load(null, false, true), [load]);
    const next = useCallback(() => { if (!request.current && nextCursor.current) void load(nextCursor.current, false, true); }, [load]);
    const retry = useCallback(() => load(attemptedCursor.current, false, true), [load]);
    const visible = enabled && pageSubject === subject && pageFilter === filter;
    return { page: visible ? page : empty, loading, error: visible ? error : null, loaded: visible && loaded, refresh, restart, next, retry, navigation };
}
