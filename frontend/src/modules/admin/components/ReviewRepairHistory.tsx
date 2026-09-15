import { useEffect, useRef, useState } from 'react';
import { ReviewCancellationPanel } from './ReviewCancellationPanel';
import { closeExpiredRepair, recoverRepair, repairOperations, type RecoveredRepair, type RepairOperationPage } from '../api/reviewRepair';

export function ReviewRepairHistory({ subject }: { subject?: string }) {
    const [page, setPage] = useState<RepairOperationPage | null>(null);
    const [receipt, setReceipt] = useState<RecoveredRepair | null>(null);
    const [loading, setLoading] = useState(false);
    const [confirmClose, setConfirmClose] = useState(false);
    const [message, setMessage] = useState('');
    const [revision, setRevision] = useState(0);
    const request = useRef<AbortController | null>(null);
    const heading = useRef<HTMLHeadingElement>(null);
    useEffect(() => () => { request.current?.abort(); }, []);
    useEffect(() => { if (revision) heading.current?.focus(); }, [revision]);
    async function run<T>(task: (signal: AbortSignal) => Promise<T>, accept: (value: T) => void) {
        if (request.current) return;
        const controller = new AbortController(); request.current = controller; setLoading(true); setMessage(''); setReceipt(null); setConfirmClose(false);
        try { const value = await task(controller.signal); if (!controller.signal.aborted) accept(value); }
        catch { if (!controller.signal.aborted) setMessage('The operation receipt could not be verified. The outcome remains unconfirmed; check the receipt again.'); }
        finally { if (!controller.signal.aborted) { request.current = null; setLoading(false); setRevision(value => value + 1); } }
    }
    function load(cursor: string | null) {
        if (request.current) return;
        setPage(null); void run(signal => repairOperations(cursor, signal), setPage);
    }
    return <section aria-label="Your repair operation history" className="rounded-xl border p-4 space-y-3">
        <h3 ref={heading} tabIndex={-1} className="font-bold">Your repair operation history</h3>
        <p className="text-sm">Find submitted operations even if their browser-tab reference was lost. References are ordered by ID, not date. Refresh to include new operations. Opening a reference only checks its historical receipt.</p>
        <button type="button" disabled={loading} className="rounded-lg border px-3 py-2 disabled:opacity-50" onClick={() => load(null)}>{page ? 'Refresh operation history' : 'Load operation history'}</button>
        {loading && <p role="status">Checking operation history…</p>}
        {message && <p role="alert">{message}</p>}
        {page && <>
            {page.items.length === 0 && <p>No submitted operations found on this page.</p>}
            <ul className="space-y-2">{page.items.map(item => <li key={item.id} className="break-all">
                <button type="button" disabled={loading} className="underline disabled:opacity-50" onClick={() => void run(signal => recoverRepair(item.id, signal), setReceipt)}>Check receipt {item.id}</button>
                <span> — Recorded state: {item.recordedState}. Verify the receipt for the outcome.</span>
            </li>)}</ul>
            {page.nextCursor && <button type="button" disabled={loading} className="rounded-lg border px-3 py-2 disabled:opacity-50" onClick={() => load(page.nextCursor)}>Next operation page</button>}
        </>}
        {receipt && <div role="status" className="space-y-2 break-all">
            <p>Operation reference: {receipt.prepared.id}</p>
            <p>Original project <bdi>{JSON.stringify(receipt.target.position.projectId)}</bdi> ({receipt.target.position.projectIdType}), position {receipt.target.position.versionIndex}, version <bdi>{receipt.target.versionId}</bdi>.</p>
            <p>Original record SHA-256: {receipt.prepared.sha256}</p>
            {receipt.result.state === 'UNKNOWN' && <div className="space-y-2">
                <p>An expired attempt can be closed without repeating isolation. The server checks expiry; a committed isolation result is preserved. This does not approve the mod or cancel its remote job.</p>
                {!confirmClose ? <button type="button" disabled={loading} className="rounded-lg border px-3 py-2" onClick={() => setConfirmClose(true)}>Close expired attempt</button> : <>
                    <p>Confirm closing operation {receipt.prepared.id}. Closing a pending attempt prevents it from applying isolation. Any completed result stays unchanged. Refresh diagnostics before considering another action.</p>
                    <button type="button" disabled={loading} className="rounded-lg border px-3 py-2" onClick={() => void run(signal => closeExpiredRepair(receipt, signal), result => setReceipt({ ...receipt, result }))}>Confirm closing expired attempt</button>
                    <button type="button" disabled={loading} className="rounded-lg border px-3 py-2" onClick={() => setConfirmClose(false)}>Keep attempt unchanged</button>
                </>}
            </div>}
            <p>{receipt.result.state === 'APPLIED' ? 'The receipt confirms local isolation was applied. It does not establish the current version state or approve the mod.' : receipt.result.state === 'NOT_APPLIED' ? 'The receipt confirms isolation was not applied.' : 'The outcome remains unconfirmed. Do not repeat isolation. An expired attempt may be closed; otherwise check its receipt again.'}</p>
        </div>}
        {subject && receipt?.result.state === 'APPLIED' && <ReviewCancellationPanel subject={subject} isolationId={receipt.prepared.id} />}
    </section>;
}
