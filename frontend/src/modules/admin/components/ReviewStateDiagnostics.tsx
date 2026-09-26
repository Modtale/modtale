import { ReviewRepairPanel } from './ReviewRepairPanel';
import type { RepairTarget } from '../api/reviewRepair';
import { useEffect, useRef, useState } from 'react';
import { diagnosticReasons, getDiagnosticPage, type DiagnosticPage } from '../api/reviewDiagnostics';

export function ReviewStateDiagnostics({ subject, canRepair = false }: { subject: string; canRepair?: boolean }) {
    return <DiagnosticPanel key={`${subject}:${canRepair}`} subject={subject} canRepair={canRepair} />;
}
function DiagnosticPanel({ subject, canRepair }: { subject: string; canRepair: boolean }) {
    const [open, setOpen] = useState(false);
    return <section className="mt-8 border-t border-slate-200 pt-6 dark:border-white/10" aria-label="Review state diagnostics">
        <button type="button" aria-expanded={open} aria-controls="review-state-diagnostic-content" onClick={() => setOpen(!open)} className="rounded-lg border px-3 py-2 text-sm font-bold">
            {open ? 'Hide scan diagnostics' : 'Inspect scan diagnostics'}
        </button>
        {open && <DiagnosticContents subject={subject} canRepair={canRepair} />}
    </section>;
}
function DiagnosticContents({ subject, canRepair }: { subject: string; canRepair: boolean }) {
    const [selected, setSelected] = useState<RepairTarget | null>(null);
    const [repairAvailable, setRepairAvailable] = useState(false);
    const [repairLocked, setRepairLocked] = useState(false);
    const [page, setPage] = useState<DiagnosticPage | null>(null);
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
            const result = await getDiagnosticPage(cursor, controller.signal);
            if (generation.current !== epoch) return;
            setPage(result); setError(false);
            if (focus) setNavigation(n => ({ revision: n.revision + 1, failed: false }));
        } catch {
            if (generation.current !== epoch || controller.signal.aborted) return;
            setError(true);
            if (focus) setNavigation(n => ({ revision: n.revision + 1, failed: true }));
        } finally {
            if (generation.current === epoch) { request.current = null; setLoading(false); }
        }
    }
    useEffect(() => {
        void load(null, false);
        return () => { generation.current++; request.current?.abort(); request.current = null; };
    }, []);
    useEffect(() => {
        if (navigation.revision) (navigation.failed ? failure.current : heading.current)?.focus();
    }, [navigation]);
    return <div id="review-state-diagnostic-content" className="mt-4 space-y-4">
        <h2 ref={heading} tabIndex={-1} className="text-xl font-bold">Scan state diagnostics</h2>
        <p className="text-sm text-slate-600 dark:text-slate-300">Find stored scan records that need operator attention. These checks examine review state, not whether a mod is safe. Record positions can change; an operator must recheck current state before repairing it.</p>
        {canRepair && <ReviewRepairPanel subject={subject} selected={selected} onReady={setRepairAvailable} onLock={setRepairLocked} onDismiss={() => setSelected(null)} />}
        {error && <div ref={failure} tabIndex={-1} role="alert" className="rounded-lg border border-amber-400 p-3">
            <p>We could not load this diagnostic page. Any entries below are from the last successful load.</p>
            <button type="button" disabled={loading} onClick={() => void load(attempted.current)} className="mt-2 rounded-lg border px-3 py-2">Retry diagnostics</button>
        </div>}
        <div className="flex flex-wrap items-center gap-3">
            <button type="button" disabled={loading} onClick={() => void load(null)} className="rounded-lg border px-3 py-2 disabled:opacity-50">Refresh diagnostics from start</button>
            <button type="button" disabled={loading || error || !page?.nextCursor} onClick={() => void load(page!.nextCursor)} className="rounded-lg border px-3 py-2 disabled:opacity-50">Next diagnostic page</button>
            <span role="status" className="text-sm">{loading ? 'Loading diagnostics…' : page ? `${page.items.length} diagnostic entries · ${page.examinedSlots} version slots examined on this page` : 'Diagnostics unavailable'}</span>
        </div>
        {page && !page.items.length && <p className="text-sm">{page.nextCursor ? 'No structural problems found in these slots. Continue to the next page.' : 'No diagnostic entries on this page. Refresh from start to check for changes.'}</p>}
        {page && page.items.length > 0 && <ul className="space-y-3" aria-label="Diagnostic records">
            {page.items.map(item => <li key={JSON.stringify(item.position)} className="rounded-xl border border-amber-300 p-4 text-sm">
                <p className="break-all"><strong>Project record:</strong> <bdi>{JSON.stringify(item.position.projectId)}</bdi> ({item.position.projectIdType === 'STRING' ? 'string ID' : 'ObjectId'})</p>
                <p><strong>Stored version position:</strong> {item.position.versionIndex} (zero-based)</p>
                <p className="break-all"><strong>Version:</strong> <bdi>{item.versionId ?? 'Identifier unavailable'}</bdi></p>
                {canRepair && repairAvailable && <button type="button" disabled={loading || error || repairLocked || !item.versionId} onClick={() => setSelected({ position: item.position, versionId: item.versionId! })} className="mt-2 rounded-lg border px-3 py-2 disabled:opacity-50">Preview local isolation</button>}
                <ul className="mt-2 list-disc space-y-1 pl-5">{item.reasons.map(reason => <li key={reason}>{diagnosticReasons[reason]}</li>)}</ul>
            </li>)}
        </ul>}
    </div>;
}
