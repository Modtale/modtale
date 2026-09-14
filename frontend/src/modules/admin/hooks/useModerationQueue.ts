import { useCallback, useEffect, useRef, useState } from 'react';
import { getModerationQueuePage, type QueuePage } from '../api/moderationQueue';
import { extractApiErrorMessage } from '@/utils/api';
const empty: QueuePage = { items: [], nextCursor: null, unavailableItems: 0, order: 'PROJECT_VERSION' };
export function useModerationQueue(enabled: boolean, subject: string) {
    const [page, setPage] = useState(empty);
    const [pageSubject, setPageSubject] = useState(subject);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [loaded, setLoaded] = useState(false);
    const access = useRef({ enabled, subject }); access.current = { enabled, subject };
    const generation = useRef(0);
    const request = useRef<AbortController | null>(null);
    const cursor = useRef<string | null>(null);
    const nextCursor = useRef<string | null>(null);
    const attemptedCursor = useRef<string | null>(null);
    const failed = useRef(false);
    const load = useCallback(async (position: string | null, background = false) => {
        if (!access.current.enabled || (background && (request.current || failed.current))) return;
        request.current?.abort(); const controller = new AbortController(); request.current = controller;
        const epoch = ++generation.current; const owner = access.current.subject;
        attemptedCursor.current = position;
        setLoading(true);
        const current = () => generation.current === epoch && access.current.enabled && access.current.subject === owner;
        try {
            const response = await getModerationQueuePage(position, controller.signal);
            if (!current()) return;
            cursor.current = position; nextCursor.current = response.nextCursor;
            setPage(response); setPageSubject(owner); failed.current = false; setError(null); setLoaded(true);
        } catch (failure) {
            if (current() && !controller.signal.aborted) { failed.current = true; setError(extractApiErrorMessage(failure, 'We could not load this queue page.')); }
        } finally {
            if (generation.current === epoch) { request.current = null; setLoading(false); }
        }
    }, []);
    useEffect(() => {
        generation.current++; request.current?.abort(); request.current = null;
        cursor.current = null; nextCursor.current = null; attemptedCursor.current = null; failed.current = false;
        setPage(empty); setPageSubject(subject); setError(null); setLoaded(false); setLoading(false);
        if (enabled) void load(null);
        return () => { generation.current++; request.current?.abort(); request.current = null; };
    }, [enabled, subject, load]);
    const refresh = useCallback((background = false) => load(cursor.current, background), [load]);
    const restart = useCallback(() => load(null), [load]);
    const next = useCallback(() => { if (!request.current && nextCursor.current) void load(nextCursor.current); }, [load]);
    const retry = useCallback(() => load(attemptedCursor.current), [load]);
    const visible = enabled && pageSubject === subject;
    return { page: visible ? page : empty, loading, error: visible ? error : null, loaded: visible && loaded, refresh, restart, next, retry };
}
