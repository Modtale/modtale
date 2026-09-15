import { useEffect, useRef, useState } from 'react';
import { cancellationAvailable, previewCancellation, prepareCancellation, executeCancellation, recoverCancellation, checkCancellation, cancellationCheckReceipt, restoreCancellation, saveCancellation, cancellationStorageKey, type CancellationPreview, type CancellationIntent, type CancellationReceipt, type CheckReceipt, type CancellationObservation } from '../api/reviewCancellation';

type Props = { subject: string; isolationId: string };
export function ReviewCancellationPanel(props: Props) { return <Panel key={JSON.stringify([props.subject, props.isolationId])} {...props} />; }
function Panel({ subject, isolationId }: Props) {
    const owner = JSON.stringify([subject, isolationId]);
    const [available, setAvailable] = useState<boolean | null>(null);
    const [preview, setPreview] = useState<CancellationPreview | null>(null);
    const [original, setOriginal] = useState<CancellationIntent | null>(null);
    const [receipt, setReceipt] = useState<CancellationReceipt | null>(null);
    const [later, setLater] = useState<CheckReceipt[]>([]);
    const [checkId, setCheckId] = useState<string | null>(null);
    const [confirmed, setConfirmed] = useState(false);
    const [corrupt, setCorrupt] = useState(false);
    const [message, setMessage] = useState('');
    const [working, setWorking] = useState(false);
    const [revision, setRevision] = useState(0);
    const request = useRef<AbortController | null>(null);
    const heading = useRef<HTMLHeadingElement>(null);
    useEffect(() => {
        const controller = new AbortController();
        cancellationAvailable(controller.signal).then(value => { if (!controller.signal.aborted) setAvailable(value); }).catch(() => { if (!controller.signal.aborted) setMessage('Cancellation availability could not be checked. Reopen this operation to retry.'); });
        try {
            const raw = sessionStorage.getItem(cancellationStorageKey(owner));
            if (raw) {
                const saved = restoreCancellation(raw); if (saved.original.isolationId !== isolationId) throw new Error('Mismatched reference');
                setPreview(saved.preview); setOriginal(saved.original); setCheckId(saved.checkId);
                setMessage('A saved reference was found. Recover its original receipt before taking further action.');
            }
        } catch { setCorrupt(true); setMessage('The saved cancellation reference could not be verified. Use original receipt recovery; no remote action is enabled.'); }
        return () => { controller.abort(); request.current?.abort(); };
    }, [owner, isolationId]);
    useEffect(() => { if (revision) heading.current?.focus(); }, [revision]);
    async function run(task: (signal: AbortSignal) => Promise<void>) {
        if (request.current || available !== true) return;
        const controller = new AbortController(); request.current = controller; setWorking(true); setMessage('');
        try { await task(controller.signal); }
        catch { if (!controller.signal.aborted) { setConfirmed(false); setMessage('The response could not be verified. Recover the retained receipt; do not repeat the request.'); } }
        finally { if (!controller.signal.aborted) { request.current = null; setWorking(false); setRevision(v => v + 1); } }
    }
    function persist(p: CancellationPreview, intent: CancellationIntent, id: string | null) { saveCancellation(sessionStorage, owner, { preview: p, original: intent, checkId: id }); }
    function inspect() {
        void run(async signal => { const value = await previewCancellation(isolationId, signal); if (!signal.aborted) { setPreview(value); setMessage('Review the original job before preparing cancellation.'); } });
    }
    function prepare() {
        if (!preview || original || corrupt) return;
        void run(async signal => {
            const value = await prepareCancellation(preview, signal); if (signal.aborted) return;
            setOriginal(value); persist(preview, value, null); setConfirmed(true);
        });
    }
    function execute() {
        if (!preview || !original || !confirmed || corrupt) return;
        setConfirmed(false);
        void run(async signal => {
            persist(preview, original, checkId);
            const value = await executeCancellation(preview, original, signal); if (signal.aborted) return;
            setReceipt(value.receipt); setMessage(value.receipt ? '' : `Cancellation outcome: ${value.state}. Recover the original receipt before another action.`);
        });
    }
    function recover() {
        setConfirmed(false);
        void run(async signal => {
            const fresh = await previewCancellation(isolationId, signal); if (signal.aborted) return;
            const value = await recoverCancellation(fresh, signal); if (signal.aborted) return;
            if (original && (value.prepared.createdAt !== original.createdAt || value.prepared.expiresAt !== original.expiresAt || value.prepared.targetSha256 !== original.targetSha256)) throw new Error('Changed intent');
            setPreview(fresh); setOriginal(value.prepared); setReceipt(value);
            // A corrupt check reference cannot be silently replaced; original recovery remains read-only.
            if (!corrupt) persist(fresh, value.prepared, checkId);
        });
    }
    function retainLater(value: CheckReceipt) { setLater(items => [...items.filter(item => item.id !== value.id), value].slice(-20)); }
    function newCheck() {
        if (!preview || !original || !receipt || receipt.state === 'PREPARED' || corrupt || checkId || later.length >= 20) return;
        void run(async signal => {
            const id = crypto.randomUUID(); setCheckId(id); persist(preview, original, id);
            const value = await checkCancellation(preview, { id, original }, signal); if (!signal.aborted) retainLater(value);
        });
    }
    function recoverCheck() {
        if (!preview || !original || !checkId || corrupt) return;
        void run(async signal => {
            const value = await cancellationCheckReceipt(preview, { id: checkId, original }, signal); if (!signal.aborted) retainLater(value);
        });
    }
    function finishCheck() {
        if (!preview || !original || !checkId || !later.some(v => v.id === checkId && ['OBSERVED', 'UNKNOWN'].includes(v.state))) return;
        try { persist(preview, original, null); setCheckId(null); setRevision(v => v + 1); } catch { setMessage('The reference could not be saved. Keep using receipt recovery.'); }
    }
    if (available === false) return null;
    const disabled = working || available !== true;
    return <section aria-label="Original remote review" className="rounded-xl border p-4 space-y-3">
        <h4 ref={heading} tabIndex={-1} className="font-bold">Original remote review</h4>
        <p>Cancellation concerns the original remote job. It does not approve this mod, replace its review, or prove that work already in flight stopped.</p>
        {message && <p role="alert">{message}</p>}{working && <p role="status">Checking original review…</p>}
        {preview && <dl className="break-all text-sm">
            <dt>Original project and version</dt><dd><bdi>{JSON.stringify(preview.projectId)}</bdi> ({preview.projectIdType}), position {preview.versionIndex}, version <bdi>{preview.versionId}</bdi></dd>
            <dt>Original job</dt><dd>{preview.jobId}</dd><dt>Original request</dt><dd>{preview.requestId}</dd>
            <dt>Artifact SHA-256</dt><dd>{preview.artifactSha256}</dd><dt>Target SHA-256</dt><dd>{preview.targetSha256}</dd>
        </dl>}
        {!preview && !corrupt && <button disabled={disabled} onClick={inspect} type="button">Inspect original job</button>}
        {preview && !original && !corrupt && <button disabled={disabled} onClick={prepare} type="button">Prepare original job cancellation</button>}
        {receipt?.state === 'PREPARED' && !confirmed && !corrupt && <button disabled={disabled} onClick={() => { setConfirmed(true); setRevision(v => v + 1); }} type="button">Review cancellation confirmation</button>}
        {confirmed && <div><p>Confirm cancellation of the original job shown above. The server checks the signed target and original expiry.</p><button disabled={disabled} onClick={execute} type="button">Confirm original job cancellation</button><button disabled={disabled} onClick={() => setConfirmed(false)} type="button">Keep job unchanged</button></div>}
        <button disabled={disabled} onClick={recover} type="button">Recover original cancellation receipt</button>
        {receipt && <div><h5>Original cancellation outcome</h5><p>{receipt.state}</p><Observation value={receipt.observation} />{receipt.observedAt !== null && <p>Receipt retained: {receipt.observedAt} milliseconds since Unix epoch</p>}</div>}
        {receipt && receipt.state !== 'PREPARED' && !corrupt && !checkId && <button disabled={disabled || later.length >= 20} onClick={newCheck} type="button">Request a new status observation</button>}
        {checkId && <div><p>Saved observation reference: {checkId}</p><button disabled={disabled || corrupt} onClick={recoverCheck} type="button">Recover observation receipt</button>
            {later.some(v => v.id === checkId && ['OBSERVED', 'UNKNOWN'].includes(v.state)) && <button disabled={disabled} onClick={finishCheck} type="button">Finish this observation lookup</button>}</div>}
        {later.length > 0 && <div><h5>Later status observations</h5><p>These separate responses do not replace the original outcome or prove which request caused a state change. Up to 20 observations are shown in this view; server history discovery is not available here.</p>
            <ul>{later.map(item => <li key={item.id} className="break-all">{item.id}: {item.state}<Observation value={item.observation} />{item.receivedAt !== null && <p>Receipt retained: {item.receivedAt} milliseconds since Unix epoch</p>}</li>)}</ul></div>}
    </section>;
}
function Observation({ value }: { value: CancellationObservation | null }) {
    if (!value) return <p>No verified remote observation is retained in this receipt.</p>;
    if (value.kind === 'REMOTE_STATUS') return <p>Remote job state: {value.status!.state}. Artifact retained: {value.status!.artifactRetained ? 'yes' : 'no'}.</p>;
    return <p>Observation: {value.kind}{value.httpStatus === null ? '' : ` (HTTP ${value.httpStatus})`}. This does not establish that the job stopped.</p>;
}
