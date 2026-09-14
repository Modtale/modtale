import { useEffect, useRef, useState } from 'react';
import { ReviewRepairHistory } from './ReviewRepairHistory';
import { executeRepair, inspectRepair, prepareRepair, repairAvailable, repairReceipt, restorePending, type PreparedRepair, type RepairPreview, type RepairResult, type RepairTarget } from '../api/reviewRepair';

type RepairPanelProps = { subject: string; selected: RepairTarget | null; onReady: (ready: boolean) => void; onLock: (locked: boolean) => void; onDismiss: () => void };
export function ReviewRepairPanel(props: RepairPanelProps) { return <RepairPanel key={props.subject} {...props} />; }
function RepairPanel({ subject, selected, onReady, onLock, onDismiss }: RepairPanelProps) {
    const [available, setAvailable] = useState<boolean | null>(null);
    const [availabilityError, setAvailabilityError] = useState(false);
    const [target, setTarget] = useState<RepairTarget | null>(null);
    const [preview, setPreview] = useState<RepairPreview | null>(null);
    const [prepared, setPrepared] = useState<PreparedRepair | null>(null);
    const [result, setResult] = useState<RepairResult | null>(null);
    const [stage, setStage] = useState('idle');
    const [message, setMessage] = useState('');
    const request = useRef<AbortController | null>(null);
    const epoch = useRef(0); const busy = useRef(false); const prepareId = useRef('');
    const heading = useRef<HTMLHeadingElement>(null);
    const storageKey = `review-repair:${encodeURIComponent(subject)}`;
    const terminal = result !== null && result.state !== 'UNKNOWN';
    const working = ['inspecting', 'preparing', 'executing', 'checking'].includes(stage);
    const locked = working || stage === 'corrupt' || (prepared !== null && !terminal);

    useEffect(() => { onReady(available === true); }, [available, onReady]);
    useEffect(() => { onLock(locked); }, [locked, onLock]);
    useEffect(() => {
        if (['preview', 'ready', 'result', 'error', 'corrupt'].includes(stage)) heading.current?.focus();
    }, [stage]);
    useEffect(() => {
        const controller = new AbortController(); let current = true;
        repairAvailable(controller.signal).then(value => { if (current) setAvailable(value); }).catch(() => { if (current) setAvailabilityError(true); });
        try {
            const raw = sessionStorage.getItem(storageKey);
            if (raw) { const pending = restorePending(raw); setTarget(pending.target); setPrepared(pending.prepared); setResult({ state: 'UNKNOWN', afterSha256: null }); setStage('result'); }
        } catch { setStage('corrupt'); setMessage('The saved operation reference could not be read. Resolve it before starting another repair.'); }
        return () => { current = false; controller.abort(); epoch.current++; request.current?.abort(); };
    }, [storageKey]);
    async function run<T>(task: (signal: AbortSignal) => Promise<T>, accept: (value: T) => void, fail: () => void) {
        if (busy.current) return;
        busy.current = true; request.current?.abort(); const controller = new AbortController(); request.current = controller; const generation = ++epoch.current;
        try { const value = await task(controller.signal); if (epoch.current === generation && !controller.signal.aborted) accept(value); }
        catch { if (epoch.current === generation && !controller.signal.aborted) fail(); }
        finally { if (epoch.current === generation) { busy.current = false; request.current = null; } }
    }
    useEffect(() => {
        if (!selected || available !== true || locked) return;
        setTarget(selected); setPreview(null); setPrepared(null); setResult(null); setMessage(''); prepareId.current = ''; setStage('inspecting');
        void run(signal => inspectRepair(selected, signal), value => { setPreview(value); setStage('preview'); }, () => { setMessage('The current record could not be inspected. Refresh diagnostics before trying again.'); setStage('error'); });
        // Selection is deliberate; availability and state changes must never restart an inspection.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selected]);
    function acceptResult(value: RepairResult) {
        setResult(value); setStage('result'); setMessage('');
        if (value.state !== 'UNKNOWN') { try { sessionStorage.removeItem(storageKey); } catch { /* A retained reference remains safe to look up again. */ } }
    }
    function prepare() {
        if (!preview?.eligible || busy.current) return;
        prepareId.current ||= crypto.randomUUID(); setStage('preparing'); setMessage('');
        void run(signal => prepareRepair(preview, prepareId.current, signal), value => { setPrepared(value); setStage('ready'); }, () => { setMessage('Preparation could not be confirmed. No isolation was submitted. You can retry preparation using this inspection.'); setStage('preview'); });
    }
    function execute() {
        if (!prepared || !target || busy.current || stage !== 'ready') return;
        try {
            const saved = JSON.stringify({ target, prepared }); sessionStorage.setItem(storageKey, saved);
            if (sessionStorage.getItem(storageKey) !== saved) throw new Error();
        } catch { setMessage('The operation reference could not be saved. Isolation has not started.'); return; }
        setStage('executing'); setMessage('');
        void run(signal => executeRepair(prepared, signal), acceptResult, () => acceptResult({ state: 'UNKNOWN', afterSha256: null }));
    }
    function checkReceipt() {
        if (!prepared || !target || busy.current || available !== true) return;
        setStage('checking'); setMessage('');
        void run(signal => repairReceipt(prepared, target, signal), acceptResult, () => { setResult({ state: 'UNKNOWN', afterSha256: null }); setMessage('The receipt is unavailable. The outcome remains unconfirmed.'); setStage('result'); });
    }
    function dismiss() { setTarget(null); setPreview(null); setPrepared(null); setResult(null); setStage('idle'); setMessage(''); onDismiss(); }
    return <section aria-label="Repair controls" className="space-y-3">
        {availabilityError && <p role="status">Repair availability could not be checked. Reopen diagnostics to try again.</p>}
        {available === false && <p className="text-sm">Repair tools are not enabled on this server.</p>}
        {(target || stage === 'corrupt') && <div className="rounded-xl border border-amber-400 p-4 space-y-3">
            <h3 ref={heading} tabIndex={-1} className="font-bold text-lg">Local review isolation</h3>
            {target && <p className="break-all">Project <bdi>{JSON.stringify(target.position.projectId)}</bdi> ({target.position.projectIdType}), position {target.position.versionIndex}, version <bdi>{target.versionId}</bdi></p>}
            <p>Isolation moves this version to review service attention. The version remains pending, findings and blocking decisions are preserved, and the original record is archived. It does not approve the mod, cancel its remote job, or start a new scan.</p>
            {preview && <p className="break-all text-xs">Inspected record SHA-256: {preview.sha256}</p>}
            {prepared && <><p className="break-all text-xs">Operation reference: {prepared.id}</p><p className="text-sm">Prepared until {new Date(prepared.expiresAt).toLocaleString()}.</p></>}
            {working && <p role="status">{stage === 'executing' ? 'Submitting isolation… Closing this view does not cancel a submitted operation.' : stage === 'checking' ? 'Checking the operation receipt…' : 'Inspecting or preparing the record…'}</p>}
            {message && <p role="alert">{message}</p>}
            {stage === 'preview' && (preview?.eligible ? <button type="button" className="rounded-lg border px-3 py-2" onClick={prepare}>Prepare isolation</button> : preview && <p>This record is not eligible for local isolation. An operator must resolve its remaining state.</p>)}
            {stage === 'ready' && <><p>Confirm isolation of the exact inspected record. If it has changed, the server will refuse the change.</p><button type="button" className="rounded-lg border border-amber-500 px-3 py-2 font-bold" onClick={execute}>Confirm isolation</button></>}
            {result && stage === 'result' && <div role="status">
                {result.state === 'APPLIED' ? 'Local review isolated. Refresh diagnostics and the review queue to see its current state.' : result.state === 'UNKNOWN' ? 'The outcome is unconfirmed. Check the receipt; do not submit another isolation for this record. This operation reference is saved in this browser tab.' : 'Isolation was not applied. Refresh diagnostics and inspect the current record.'}
            </div>}
            {result?.state === 'UNKNOWN' && <button type="button" disabled={working || available !== true} className="rounded-lg border px-3 py-2 disabled:opacity-50" onClick={checkReceipt}>Check operation receipt</button>}
            {!working && stage !== 'corrupt' && result?.state !== 'UNKNOWN' && <button type="button" className="ml-2 rounded-lg border px-3 py-2" onClick={dismiss}>{terminal ? 'Close repair result' : 'Cancel repair preview'}</button>}
        </div>}
        {available === true && <ReviewRepairHistory />}
    </section>;
}
