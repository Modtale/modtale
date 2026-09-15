import { useEffect, useRef, useState } from 'react';
import { getOriginPage, originLabels, type OriginPage } from '../api/reviewOrigins';

export function ReviewOrigins({ subject }: { subject: string }) { return <OriginPanel key={subject} />; }
function OriginPanel() {
    const [open, setOpen] = useState(false);
    return <section className="mt-6 border-t border-slate-200 pt-6 dark:border-white/10" aria-label="Historical review origins">
        <button type="button" aria-expanded={open} aria-controls="review-origin-content" onClick={() => setOpen(!open)} className="rounded-lg border px-3 py-2 text-sm font-bold">
            {open ? 'Hide review origins' : 'Inspect review origins'}
        </button>
        {open && <OriginContents />}
    </section>;
}
function OriginContents() {
    const [page, setPage] = useState<OriginPage | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const request = useRef<AbortController | null>(null);
    const generation = useRef(0);
    const attempted = useRef<string | null>(null);
    const heading = useRef<HTMLHeadingElement>(null);
    const failure = useRef<HTMLDivElement>(null);
    const [navigation, setNavigation] = useState({ revision: 0, failed: false });
    async function load(cursor: string | null, focus = true) {
        request.current?.abort(); const controller = new AbortController(); request.current = controller;
        const epoch = ++generation.current; attempted.current = cursor; setLoading(true);
        try {
            const result = await getOriginPage(cursor, controller.signal);
            if (generation.current !== epoch) return;
            setPage(result); setError(false);
            if (focus) setNavigation(n => ({ revision: n.revision + 1, failed: false }));
        } catch {
            if (generation.current !== epoch || controller.signal.aborted) return;
            setError(true);
            if (focus) setNavigation(n => ({ revision: n.revision + 1, failed: true }));
        } finally { if (generation.current === epoch) { request.current = null; setLoading(false); } }
    }
    useEffect(() => { void load(null, false); return () => { generation.current++; request.current?.abort(); }; }, []);
    useEffect(() => { if (navigation.revision) (navigation.failed ? failure.current : heading.current)?.focus(); }, [navigation]);
    return <div id="review-origin-content" className="mt-4 space-y-4">
        <h2 ref={heading} tabIndex={-1} className="text-xl font-bold">Historical review origins</h2>
        <p className="text-sm text-slate-600 dark:text-slate-300">Inspect retained review references, including failed and completed versions. A recorded service identity does not establish that a mod is safe or that the original job has stopped. Missing origins need operator investigation; the current service cannot establish a historical origin.</p>
        <p className="text-sm text-slate-600 dark:text-slate-300">This view does not change review state. Deleted versions and references retained only in repair archives are outside this inventory; use repair operation history for known isolation records.</p>
        {error && <div ref={failure} tabIndex={-1} role="alert" className="rounded-lg border border-amber-400 p-3">
            <p>We could not load this origin page. Any entries below are from the last successful load.</p>
            <button type="button" disabled={loading} onClick={() => void load(attempted.current)} className="mt-2 rounded-lg border px-3 py-2">Retry origin inventory</button>
        </div>}
        <div className="flex flex-wrap items-center gap-3">
            <button type="button" disabled={loading} onClick={() => void load(null)} className="rounded-lg border px-3 py-2 disabled:opacity-50">Refresh origins from start</button>
            <button type="button" disabled={loading || error || !page?.nextCursor} onClick={() => void load(page!.nextCursor)} className="rounded-lg border px-3 py-2 disabled:opacity-50">Next origin page</button>
            <span role="status" className="text-sm">{loading ? 'Loading review origins…' : page ? `${page.items.length} retained references · ${page.examinedSlots} version slots examined on this page` : 'Origin inventory unavailable'}</span>
        </div>
        {page && !page.items.length && <p className="text-sm">{page.nextCursor ? 'No retained references in these slots. Continue to the next page.' : 'No retained references on this page. Refresh from start to check for changes.'}</p>}
        {page && page.items.length > 0 && <ul className="space-y-3" aria-label="Retained review references">
            {page.items.map(item => <li key={JSON.stringify(item.position)} className="rounded-xl border border-slate-300 p-4 text-sm dark:border-white/10">
                <p className="font-bold">{originLabels[item.originState]}</p>
                <p className="break-all"><strong>Project record:</strong> <bdi>{JSON.stringify(item.position.projectId)}</bdi> ({item.position.projectIdType === 'STRING' ? 'string ID' : 'ObjectId'})</p>
                <p><strong>Stored version position:</strong> {item.position.versionIndex} (zero-based)</p>
                <p className="break-all"><strong>Version:</strong> <bdi>{item.versionId ?? 'Identifier unavailable'}</bdi></p>
                {item.ambiguousVersion && <p className="mt-1 font-semibold text-amber-700 dark:text-amber-300">Version identity is ambiguous. An operator must inspect the stored record before any action.</p>}
                <p className="break-all"><strong>Original request:</strong> {item.requestId ?? 'Unavailable'}</p>
                <p className="break-all"><strong>Original job:</strong> {item.jobId ?? 'Unavailable'}</p>
            </li>)}
        </ul>}
    </div>;
}
