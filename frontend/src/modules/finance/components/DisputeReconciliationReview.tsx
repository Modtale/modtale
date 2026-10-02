import { useEffect, useRef, useState } from 'react';
import { financeClient } from '@/modules/finance/api/financeClient';
import { extractApiErrorMessage } from '@/utils/api';
import { theme } from '@/styles/theme';

interface Balance { id: string; source: string; type: string; currency: string; status: string; amount: number | null; fee: number | null; net: number | null }
interface Dispute { id: string; disputeId: string; chargeId: string; creatorId: string; currency: string; testMode: boolean; providerStatus: string; disputedCents: number; actualFeeCents: number | null; reviewStatus: string; evidenceReady: boolean; evidenceDigest: string | null; principalMovementCents: number | null; balanceTransactions: Balance[]; updatedAt: string }
interface Decision { id: string; providerStatus: string; creatorFeeCents: number; actualFeeCents: number; reviewerId: string; reason: string; createdAt: string; evidenceDigest: string }
const money = (amount: number | null, currency: string) => amount === null ? 'Unverified' : new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount / 100);
const parseFee = (value: string) => /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) ? Number(value) : null;

export function DisputeReconciliationReview() {
    const [rows, setRows] = useState<Dispute[]>([]);
    const [selected, setSelected] = useState<Dispute | null>(null);
    const [history, setHistory] = useState<Decision[]>([]);
    const [historyReady, setHistoryReady] = useState(false);
    const [fee, setFee] = useState('');
    const [reason, setReason] = useState('');
    const [reviewed, setReviewed] = useState(false);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const active = useRef(false); const sequence = useRef(0); const busyRef = useRef(false);
    const load = async () => {
        const current = ++sequence.current; setLoading(true); setSelected(null); setError('');
        try { const response = await financeClient.getDisputeReconciliationCases(); if (active.current && sequence.current === current) setRows(Array.isArray(response) ? response : []); }
        catch (failure) { if (active.current && sequence.current === current) setError(extractApiErrorMessage(failure, 'Could not load dispute evidence.')); }
        finally { if (active.current && sequence.current === current) setLoading(false); }
    };
    useEffect(() => { active.current = true; void load(); return () => { active.current = false; sequence.current++; }; }, []);
    const open = async (row: Dispute, duringRefresh = false) => {
        if (busyRef.current && !duringRefresh) return;
        const current = ++sequence.current; setSelected(row); setHistory([]); setHistoryReady(false); setFee(''); setReason(''); setReviewed(false); setError(''); setNotice('');
        try { const response = await financeClient.getDisputeDecisions(row.id); if (active.current && sequence.current === current) { setHistory(Array.isArray(response) ? response : []); setHistoryReady(true); } }
        catch (failure) { if (active.current && sequence.current === current) setError(extractApiErrorMessage(failure, 'Could not load prior decisions. Refresh before reviewing this case.')); }
    };
    const refreshProvider = async () => {
        if (!selected || busyRef.current) return;
        busyRef.current = true; setBusy(true); setError(''); setReviewed(false);
        try {
            const refreshed = await financeClient.refreshDisputeCase(selected.id);
            if (!active.current) return;
            setRows(current => current.map(row => row.id === refreshed.id ? refreshed : row));
            await open(refreshed, true);
        } catch (failure) { if (active.current) { setHistoryReady(false); setError(extractApiErrorMessage(failure, 'Provider evidence could not be refreshed.')); } }
        finally { busyRef.current = false; if (active.current) setBusy(false); }
    };
    const feeCents = parseFee(fee);
    const eligible = historyReady && selected?.evidenceReady && selected.reviewStatus === 'POLICY_REVIEW_REQUIRED' && /^[a-f0-9]{64}$/.test(selected.evidenceDigest || '');
    const valid = !!eligible && feeCents !== null && selected!.actualFeeCents !== null && feeCents <= selected!.actualFeeCents! && !!reason.trim() && reviewed;
    const resolve = async () => {
        if (!selected || !valid || busyRef.current || feeCents === null) return;
        busyRef.current = true; setBusy(true); setError(''); setNotice('');
        try {
            await financeClient.resolveDispute({ caseId: selected.id, expectedEvidenceDigest: selected.evidenceDigest!, creatorFeeCents: feeCents, reason: reason.trim() });
            if (!active.current) return;
            setNotice('Decision recorded. Only this reviewed dispute’s hold can close; unrelated holds remain. No bank debit or new payment was made.');
            await load();
        } catch (failure) { if (active.current) { setReviewed(false); setError(extractApiErrorMessage(failure, 'Could not resolve this case. Refresh its evidence before trying again.')); } }
        finally { busyRef.current = false; if (active.current) setBusy(false); }
    };
    return <section aria-labelledby="dispute-review-title" className={theme.components.panel + ' space-y-4 p-5 md:p-6'}>
        <header className="flex flex-wrap justify-between gap-3"><div><h2 id="dispute-review-title" className="text-xl font-black text-slate-900 dark:text-white">Dispute reconciliation</h2><p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Review settled provider evidence and explicitly allocate actual dispute fees.</p></div><button type="button" onClick={() => void load()} disabled={busy || loading} className={theme.components.buttonSecondary}>Refresh disputes</button></header>
        <p className="rounded-xl border border-amber-300 p-3 text-sm text-amber-900 dark:text-amber-100">No discretionary fee is assigned automatically. A won status alone cannot restore money; linked settled return evidence is required. Use non-secret provider references in review notes.</p>
        {error && <p role="alert" className="text-sm text-red-700 dark:text-red-300">{error}</p>}{notice && <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">{notice}</p>}
        {loading && <p role="status" className="text-sm text-slate-500">Loading dispute evidence…</p>}
        {!loading && rows.length === 0 && <p className="text-sm text-slate-500 dark:text-slate-400">No dispute cases recorded.</p>}
        <div className="grid gap-2 md:grid-cols-2">{rows.map(row => <button type="button" key={row.id} disabled={busy} onClick={() => void open(row)} className="rounded-xl border border-slate-200 p-3 text-left dark:border-white/10"><span className="block break-all font-bold text-slate-800 dark:text-slate-100">{row.disputeId} · {row.testMode ? 'Test' : 'Live'}</span><span className="mt-1 block text-xs text-slate-500 dark:text-slate-400">{row.providerStatus} · {row.reviewStatus.replaceAll('_', ' ')} · {money(row.disputedCents, row.currency)}</span></button>)}</div>
        {selected && <div className="space-y-4 rounded-xl border border-slate-200 p-4 dark:border-white/10">
            <div className="grid gap-2 text-sm text-slate-600 dark:text-slate-300 md:grid-cols-3"><p>Disputed principal: <strong>{money(selected.disputedCents, selected.currency)}</strong></p><p>Net principal movement: <strong>{money(selected.principalMovementCents, selected.currency)}</strong></p><p>Actual dispute fees: <strong>{money(selected.actualFeeCents, selected.currency)}</strong></p></div>
            <button type="button" disabled={busy} onClick={() => void refreshProvider()} className={theme.components.buttonSecondary}>Refresh provider evidence</button>
            <p className="break-all text-xs text-slate-500">Charge {selected.chargeId} · Creator {selected.creatorId} · Updated {new Date(selected.updatedAt).toLocaleString()}</p>
            <details className="rounded-lg border border-slate-200 p-3 text-sm dark:border-white/10"><summary className="cursor-pointer font-bold text-slate-700 dark:text-slate-200">Verified balance references</summary><div className="mt-3 space-y-2">{(selected.balanceTransactions || []).map(balance => <p key={balance.id} className="break-all text-xs text-slate-500 dark:text-slate-400">{balance.id} · {balance.status} · Principal {money(balance.amount, selected.currency)} · Fee {money(balance.fee, selected.currency)} · Net {money(balance.net, selected.currency)}</p>)}{!selected.balanceTransactions?.length && <p className="text-xs text-slate-500">No balance movements in the recorded provider response.</p>}</div></details>
            {!eligible ? <p role="status" className="text-sm text-slate-500 dark:text-slate-400">{!historyReady ? 'Prior decisions must finish loading before this case can be reviewed.' : selected.reviewStatus === 'RESOLVED' ? 'This evidence has already been reviewed. Later provider updates can open a new review.' : 'Evidence is incomplete or the dispute is still active. Its hold cannot be cleared here.'}</p> : <div className="space-y-3">
                <label className="block text-sm font-semibold text-slate-700 dark:text-slate-200">Dispute fee allocated to creator ({selected.currency.toUpperCase()} cents)<input inputMode="numeric" value={fee} disabled={busy} onChange={e => setFee(e.target.value)} className={theme.components.inputField + ' mt-1'} /></label>
                <p className="text-xs text-slate-500 dark:text-slate-400">Enter an explicit amount from 0 to {selected.actualFeeCents} cents. Modtale absorbs the remainder. This is the cumulative fee decision for this dispute, not an additional charge.</p>
                {fee && (feeCents === null || feeCents > (selected.actualFeeCents ?? -1)) && <p role="alert" className="text-xs text-red-700 dark:text-red-300">Use whole cents within the verified actual fee.</p>}
                <label className="block text-sm font-semibold text-slate-700 dark:text-slate-200">Policy basis and review reason<textarea value={reason} maxLength={1000} disabled={busy} onChange={e => setReason(e.target.value)} className={theme.components.inputField + ' mt-1'} /></label>
                <label className="flex items-start gap-2 text-sm text-slate-600 dark:text-slate-300"><input type="checkbox" checked={reviewed} disabled={busy} onChange={e => setReviewed(e.target.checked)} className="mt-1" />I reviewed this evidence and the policy basis for the fee allocation.</label>
                <div className="flex flex-wrap gap-2"><button type="button" disabled={busy || !valid} onClick={() => void resolve()} className={theme.components.buttonPrimary}>{busy ? 'Verifying evidence…' : 'Record reviewed decision'}</button><button type="button" disabled={busy} onClick={() => setSelected(null)} className={theme.components.buttonSecondary}>Cancel dispute review</button></div>
            </div>}
            <div className="space-y-2"><h4 className="font-bold text-slate-800 dark:text-slate-100">Prior decisions</h4>{history.length === 0 && <p className="text-xs text-slate-500">No prior decision loaded.</p>}{history.map(decision => <div key={decision.id} className="border-l-2 border-slate-200 pl-3 text-xs text-slate-500 dark:border-white/10 dark:text-slate-400"><p>Creator fee {money(decision.creatorFeeCents, selected.currency)} of actual {money(decision.actualFeeCents, selected.currency)} · {decision.providerStatus}</p><p>{decision.reason}</p><p>{new Date(decision.createdAt).toLocaleString()} · Reviewer {decision.reviewerId}</p></div>)}</div>
        </div>}
    </section>;
}
