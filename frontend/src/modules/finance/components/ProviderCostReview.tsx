import { useEffect, useRef, useState } from 'react';
import { financeClient } from '@/modules/finance/api/financeClient';
import { extractApiErrorMessage } from '@/utils/api';
import { theme } from '@/styles/theme';

export interface ProviderCostEvidence {
    id: string; providerAccountId: string; testMode: boolean; balanceTransactionId: string; currency: string;
    costMinorUnits: number; allocationStatus: string; recordedBy: string; reason: string; recordedAt: string;
}
// Provider minor-unit conventions can differ from Intl/ISO defaults (for example ISK and UGX).
// This integration settles USD; preserve other currencies' exact units rather than guessing a scale.
const money = (amount: number, currency: string) => currency.toLowerCase() === 'usd'
    ? new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(amount / 100)
    : `${amount.toLocaleString()} ${currency.toUpperCase()} provider minor units`;
export function ProviderCostReview() {
    const [rows, setRows] = useState<ProviderCostEvidence[]>([]);
    const [transaction, setTransaction] = useState(''); const [account, setAccount] = useState('');
    const [mode, setMode] = useState(''); const [reason, setReason] = useState('');
    const [busy, setBusy] = useState(false); const [loading, setLoading] = useState(true);
    const [error, setError] = useState(''); const [notice, setNotice] = useState('');
    const mounted = useRef(false); const inFlight = useRef(false); const sequence = useRef(0);
    const load = async () => {
        const request = ++sequence.current; setLoading(true);
        try { const data = await financeClient.getProviderCosts(); if (mounted.current && sequence.current === request) setRows(Array.isArray(data) ? data : []); }
        catch (failure) { if (mounted.current && sequence.current === request) setError(extractApiErrorMessage(failure, 'Could not load cost evidence.')); }
        finally { if (mounted.current && sequence.current === request) setLoading(false); }
    };
    useEffect(() => { mounted.current = true; void load(); return () => { mounted.current = false; sequence.current++; }; }, []);
    const valid = /^txn_[A-Za-z0-9]+$/.test(transaction.trim()) && /^acct_[A-Za-z0-9]+$/.test(account.trim()) && ['test', 'live'].includes(mode) && !!reason.trim();
    const submit = async () => {
        if (!valid || inFlight.current) return;
        inFlight.current = true; setBusy(true); setError(''); setNotice('');
        try {
            await financeClient.importStripeCost({ balanceTransactionId: transaction.trim(), expectedAccountId: account.trim(), expectedTestMode: mode === 'test', reason: reason.trim() });
            if (!mounted.current) return;
            setTransaction(''); setReason(''); setNotice('Provider evidence recorded without allocating costs or changing creator balances.'); await load();
        } catch (failure) { if (mounted.current) setError(extractApiErrorMessage(failure, 'The provider fee could not be verified. No creator balance was changed.')); }
        finally { inFlight.current = false; if (mounted.current) setBusy(false); }
    };
    return <section aria-labelledby="provider-cost-title" className={theme.components.panel + ' space-y-4 p-5 md:p-6'}>
        <header className="flex flex-wrap justify-between gap-3"><div><h2 id="provider-cost-title" className="text-xl font-black text-slate-900 dark:text-white">Provider costs</h2><p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Keep separate Billing and Connect service-fee evidence for review.</p></div><button type="button" disabled={busy || loading} onClick={() => { setError(''); void load(); }} className={theme.components.buttonSecondary}>Refresh cost evidence</button></header>
        <p className="rounded-xl border border-amber-300 p-3 text-sm text-amber-900 dark:text-amber-100">Import verifies a settled standalone Stripe service-fee transaction. Amounts come from Stripe. Aggregate costs remain unallocated; this never charges a creator or changes a wallet. Payment, refund, dispute, FX and tax records use separate reconciliation paths.</p>
        {error && <p role="alert" className="text-sm text-red-700 dark:text-red-300">{error}</p>}
        {notice && <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">{notice}</p>}
        <div className="grid gap-3 md:grid-cols-2">
            <label className="text-sm font-semibold text-slate-700 dark:text-slate-200">Stripe balance transaction<input disabled={busy} value={transaction} maxLength={100} onChange={event => setTransaction(event.target.value)} placeholder="txn_…" className={theme.components.inputField + ' mt-1'} /></label>
            <label className="text-sm font-semibold text-slate-700 dark:text-slate-200">Expected platform account<input disabled={busy} value={account} maxLength={100} onChange={event => setAccount(event.target.value)} placeholder="acct_…" className={theme.components.inputField + ' mt-1'} /></label>
            <label className="text-sm font-semibold text-slate-700 dark:text-slate-200">Provider environment<select disabled={busy} value={mode} onChange={event => setMode(event.target.value)} className={theme.components.inputField + ' mt-1'}><option value="">Choose explicitly</option><option value="test">Test</option><option value="live">Live</option></select></label>
            <label className="text-sm font-semibold text-slate-700 dark:text-slate-200">Evidence review reason<textarea disabled={busy} value={reason} maxLength={1000} onChange={event => setReason(event.target.value)} className={theme.components.inputField + ' mt-1'} /></label>
        </div>
        <button type="button" disabled={busy || !valid} onClick={() => void submit()} className={theme.components.buttonPrimary}>{busy ? 'Verifying fee…' : 'Verify and import fee evidence'}</button>
        {loading ? <p role="status">Loading evidence…</p> : rows.length === 0 ? <p className="text-sm text-slate-500">No provider cost evidence recorded.</p> : <div className="space-y-3"><p className="text-xs text-slate-500">Latest {rows.length} records, up to 100. Credits appear as negative costs.</p>{rows.map(row => <article key={row.id} className="space-y-1 rounded-xl border border-slate-200 p-3 text-sm dark:border-white/10"><h3 className="font-bold text-slate-900 dark:text-white">{money(row.costMinorUnits, row.currency)} · {row.testMode ? 'Test' : 'Live'} · Unallocated</h3><p className="break-all text-slate-600 dark:text-slate-300">{row.balanceTransactionId} · {row.providerAccountId}</p><p className="text-slate-500">{row.reason}</p><p className="break-all text-xs text-slate-500">Recorded by {row.recordedBy} · {new Date(row.recordedAt).toLocaleString()}</p></article>)}</div>}
    </section>;
}
