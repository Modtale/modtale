import { useEffect, useRef, useState } from 'react';
import { financeClient } from '@/modules/finance/api/financeClient';
import { extractApiErrorMessage } from '@/utils/api';
import { theme } from '@/styles/theme';

interface Recipient { userId: string; accountId: string; amountCents: number; transferId?: string; authorizedAt?: string; confirmedBy?: string }
interface Payout { id: string; creatorId: string; providerAccountId?: string; testMode: boolean; currency: string; amountCents: number; status: string; transferGroup?: string; createdAt: string; reviewReason?: string; recipients: Recipient[] }
const money = (amount: number, currency: string) => new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount / 100);

export function PayoutReconciliationReview() {
    const [rows, setRows] = useState<Payout[]>([]);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [selected, setSelected] = useState<{ requestId: string; recipientIndex: number } | null>(null);
    const [transferId, setTransferId] = useState('');
    const [reason, setReason] = useState('');
    const [reviewed, setReviewed] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const active = useRef(false);
    const busyRef = useRef(false);
    const sequence = useRef(0);
    const load = async () => {
        const current = ++sequence.current; setLoading(true);
        try {
            const response = await financeClient.getPayoutReconciliationQueue();
            if (active.current && sequence.current === current) setRows(Array.isArray(response) ? response : []);
        } catch (failure) {
            if (active.current && sequence.current === current) setError(extractApiErrorMessage(failure, 'Could not load transfer reviews.'));
        } finally { if (active.current && sequence.current === current) setLoading(false); }
    };
    useEffect(() => { active.current = true; void load(); return () => { active.current = false; sequence.current++; }; }, []);
    const choose = (requestId: string, recipientIndex: number) => {
        if (busyRef.current) return;
        setSelected({ requestId, recipientIndex }); setTransferId(''); setReason(''); setReviewed(false); setError(''); setNotice('');
    };
    const confirm = async () => {
        if (!selected || busyRef.current || !reviewed || !/^tr_[A-Za-z0-9]+$/.test(transferId.trim()) || !reason.trim()) return;
        busyRef.current = true; setBusy(true); setError(''); setNotice('');
        try {
            await financeClient.confirmExistingPayoutTransfer({ ...selected, transferId: transferId.trim(), reason: reason.trim() });
            if (!active.current) return;
            setSelected(null); setTransferId(''); setReason(''); setReviewed(false);
            setNotice('Existing transfer verified and recorded. This confirms funds reached the connected Stripe account, not the recipient’s bank.');
            await load();
        } catch (failure) { if (active.current) setError(extractApiErrorMessage(failure, 'Transfer could not be verified. Keep the reservation for review.')); }
        finally { busyRef.current = false; if (active.current) setBusy(false); }
    };
    return <section aria-labelledby="payout-review-title" className={theme.components.panel + ' space-y-4 p-5 md:p-6'}>
        <header className="flex flex-wrap items-start justify-between gap-3"><div><h2 id="payout-review-title" className="text-xl font-black text-slate-900 dark:text-white">Transfer reconciliation</h2><p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Resolve an uncertain response using the original provider request and a known transfer ID.</p></div><button type="button" disabled={busy || loading} onClick={() => { setError(''); void load(); }} className={theme.components.buttonSecondary}>Refresh transfers</button></header>
        <p className="rounded-xl border border-amber-300 p-3 text-sm text-amber-900 dark:text-amber-100">This review never sends another transfer. A missing or mismatched transfer cannot release reserved funds. Keep the original request and provider evidence; an empty search is not proof that nothing was sent.</p>
        {error && <p role="alert" className="text-sm text-red-700 dark:text-red-300">{error}</p>}
        {notice && <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">{notice}</p>}
        {loading && <p role="status" className="text-sm text-slate-500">Loading transfer reviews…</p>}
        {!loading && rows.length === 0 && <p className="text-sm text-slate-500 dark:text-slate-400">No unresolved transfers in this review queue.</p>}
        {rows.map(row => <article key={row.id} className="space-y-3 rounded-xl border border-slate-200 p-4 dark:border-white/10">
            <div className="flex flex-wrap justify-between gap-2"><h3 className="font-bold text-slate-900 dark:text-white">{money(row.amountCents, row.currency)} · {row.testMode ? 'Test' : 'Live'} · {row.status.replaceAll('_', ' ')}</h3><span className="text-xs text-slate-500">{new Date(row.createdAt).toLocaleString()}</span></div>
            <div className="grid gap-1 break-all text-xs text-slate-500 dark:text-slate-400"><div>Request: {row.id}</div><div>Funding account: {row.providerAccountId || 'Unverified legacy scope'}</div><div>Transfer group: {row.transferGroup || 'Missing original evidence'}</div></div>
            {row.reviewReason && <p className="text-sm text-amber-700 dark:text-amber-300">{row.reviewReason}</p>}
            {(row.recipients || []).map((recipient, index) => <div key={`${row.id}:${index}`} className="space-y-2 border-t border-slate-200 pt-3 dark:border-white/10">
                <p className="break-all text-sm text-slate-700 dark:text-slate-200">{money(recipient.amountCents, row.currency)} → {recipient.accountId} {recipient.transferId ? `· Recorded ${recipient.transferId}` : '· Unconfirmed'}</p>
                {!recipient.transferId && <button type="button" disabled={busy || !recipient.authorizedAt || !row.providerAccountId || !row.transferGroup} onClick={() => choose(row.id, index)} className={theme.components.buttonSecondary}>Review recipient {index + 1}</button>}
                {!recipient.authorizedAt && !recipient.transferId && <p className="text-xs text-slate-500">No saved outbound authorization. Do not attach an unrelated transfer.</p>}
                {selected?.requestId === row.id && selected.recipientIndex === index && <div className="space-y-3 rounded-lg bg-slate-50 p-3 dark:bg-slate-950/50">
                    <label className="block text-sm font-semibold text-slate-700 dark:text-slate-200">Existing Stripe transfer ID<input value={transferId} onChange={e => setTransferId(e.target.value)} disabled={busy} maxLength={100} placeholder="tr_…" className={theme.components.inputField + ' mt-1'} /></label>
                    <label className="block text-sm font-semibold text-slate-700 dark:text-slate-200">Reconciliation reason<textarea value={reason} onChange={e => setReason(e.target.value)} disabled={busy} maxLength={1000} className={theme.components.inputField + ' mt-1'} /></label>
                    <label className="flex items-start gap-2 text-sm text-slate-600 dark:text-slate-300"><input type="checkbox" checked={reviewed} onChange={e => setReviewed(e.target.checked)} disabled={busy} className="mt-1" />I checked the original provider request and saved transfer group, including any timeout or retry.</label>
                    <div className="flex flex-wrap gap-2"><button type="button" disabled={busy || !reviewed || !reason.trim() || !/^tr_[A-Za-z0-9]+$/.test(transferId.trim())} onClick={() => void confirm()} className={theme.components.buttonPrimary}>{busy ? 'Verifying…' : 'Verify and record existing transfer'}</button><button type="button" disabled={busy} onClick={() => setSelected(null)} className={theme.components.buttonSecondary}>Cancel review</button></div>
                </div>}
            </div>)}
        </article>)}
    </section>;
}
