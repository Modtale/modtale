import { useEffect, useRef, useState } from 'react';
import { FileCheck2, LockKeyhole, RefreshCw } from 'lucide-react';
import { financeClient } from '@/modules/finance/api/financeClient';
import { extractApiErrorMessage } from '@/utils/api';
import { theme } from '@/styles/theme';

export interface AdStageReport {
    provider: string; providerAccount: string; reportId: string; depositId: string; currency: string;
    from: string; through: string; reportedCollectedCents: number; reportSha256: string;
}
interface Activity {
    projectId: string; creatorId: string; title: string; pageviews: number; launcherDownloads: number;
    frontendDownloads: number; apiDownloads: number; provisionalPoints: number; provisionalCreatorCents: number; eligibilityNote: string;
}
interface Snapshot {
    revision: number; from: string; through: string; reportedCollectedCents: number; reportSha256: string; activityDigest: string;
    creatorShareBps: number; provisionalCreatorPoolCents: number; provisionalPlatformCents: number; unallocatedCreatorCents: number;
    activityRule: string; projects: Activity[]; capturedAt: string;
}
export interface AdStage {
    id: string; provider: string; providerAccount: string; reportId: string; depositId: string; currency: string; revision: number; status: string;
    snapshots: Snapshot[]; audit: Array<{ operationId: string; action: string; revision: number; actorId: string; reason: string; createdAt: string }>;
}
const initialReport: AdStageReport = { provider: '', providerAccount: '', reportId: '', depositId: '', currency: 'usd', from: '', through: '', reportedCollectedCents: 0, reportSha256: '' };
const money = (cents: number) => new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(cents / 100);

export function AdSettlementReview() {
    const [rows, setRows] = useState<any[]>([]);
    const [selected, setSelected] = useState<AdStage | null>(null);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const busyRef = useRef(false);
    const sequence = useRef(0);
    const operation = useRef<{ fingerprint: string; id: string } | null>(null);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [reason, setReason] = useState('');
    const [draft, setDraft] = useState<AdStageReport>(initialReport);
    const [amending, setAmending] = useState(false);
    const latest = selected?.snapshots.at(-1);

    const load = async () => {
        const version = ++sequence.current; setLoading(true); setError('');
        try {
            const result = await financeClient.getAdSettlementStages();
            if (sequence.current !== version) return;
            setRows(Array.isArray(result) ? result : []);
        } catch (error) { if (sequence.current === version) setError(extractApiErrorMessage(error, 'Could not load staged reports.')); }
        finally { if (sequence.current === version) setLoading(false); }
    };
    useEffect(() => { void load(); return () => { sequence.current++; }; }, []);
    const open = async (id: string) => {
        if (busyRef.current) return;
        const version = ++sequence.current; setLoading(true); setSelected(null); setError(''); setNotice(''); setReason(''); setAmending(false);
        try {
            const stage = await financeClient.getAdSettlementStage(id);
            if (sequence.current === version) setSelected(stage);
        } catch (error) { if (sequence.current === version) setError(extractApiErrorMessage(error, 'Could not load the report.')); }
        finally { if (sequence.current === version) setLoading(false); }
    };
    const mutate = async (kind: 'create' | 'amend' | 'review', decision?: string) => {
        if (busyRef.current) return;
        if (kind !== 'create' && (!selected || !reason.trim())) { setError('Add a review reason before continuing.'); return; }
        if (kind !== 'review' && (!Number.isSafeInteger(draft.reportedCollectedCents) || draft.reportedCollectedCents <= 0)) { setError('Enter positive whole cents for the reported deposit.'); return; }
        busyRef.current = true; setBusy(true); setError(''); setNotice('');
        const fingerprint = JSON.stringify({ kind, id: selected?.id, revision: selected?.revision, decision, reason, draft });
        if (operation.current?.fingerprint !== fingerprint) operation.current = { fingerprint, id: crypto.randomUUID() };
        try {
            let result: AdStage;
            if (kind === 'create') result = await financeClient.stageAdSettlement(draft);
            else if (kind === 'amend') result = await financeClient.amendAdSettlement(selected!.id, { expectedRevision: selected!.revision, operationId: operation.current.id, report: draft, reason });
            else result = await financeClient.reviewAdSettlement(selected!.id, { expectedRevision: selected!.revision, operationId: operation.current.id, decision, reason });
            setSelected(result); setReason(''); setAmending(false); operation.current = null;
            setNotice('Review evidence saved. Creator credit release remains locked.');
            await load();
        } catch (error) { setError(extractApiErrorMessage(error, 'The report could not be saved.')); }
        finally { busyRef.current = false; setBusy(false); }
    };
    const startAmendment = () => {
        if (!selected || !latest) return;
        setDraft({ provider: selected.provider, providerAccount: selected.providerAccount, reportId: selected.reportId, depositId: selected.depositId,
            currency: selected.currency, from: latest.from, through: latest.through, reportedCollectedCents: latest.reportedCollectedCents, reportSha256: latest.reportSha256 });
        setReason(''); setAmending(true);
    };
    const field = (name: keyof AdStageReport, label: string, type = 'text', locked = false) => <label className="block text-sm font-bold text-slate-700 dark:text-slate-200" key={name}>{label}
        <input type={type} value={draft[name]} disabled={busy || locked} maxLength={name === 'reportSha256' ? 64 : 120} min={type === 'number' ? 1 : undefined} step={type === 'number' ? 1 : undefined}
            onChange={event => setDraft(current => ({ ...current, [name]: type === 'number' ? Number(event.target.value) : event.target.value }))} className={theme.components.inputField + ' mt-2'} />
    </label>;
    const reportForm = (amend: boolean) => <div className="space-y-4">
        <p className="text-sm text-slate-500 dark:text-slate-400">Use non-secret report and deposit references. A pasted identifier or report digest does not verify receipt of funds. Dates use closed UTC reporting days.</p>
        <div className="grid gap-4 md:grid-cols-2">{field('provider', 'Provider key', 'text', amend)}{field('providerAccount', 'Publisher account reference', 'text', amend)}{field('reportId', 'Report reference', 'text', amend)}{field('depositId', 'Deposit reference', 'text', amend)}{field('from', 'Period starts', 'date')}{field('through', 'Period ends', 'date')}{field('reportedCollectedCents', 'Reported deposit (USD cents)', 'number')}{field('reportSha256', 'Source report SHA-256 digest')}</div>
        {amend && <label className="block text-sm font-bold text-slate-700 dark:text-slate-200">Amendment reason<textarea value={reason} maxLength={1000} onChange={event => setReason(event.target.value)} className={theme.components.inputField + ' mt-2'} /></label>}
        <button type="button" disabled={busy} onClick={() => void mutate(amend ? 'amend' : 'create')} className={theme.components.buttonPrimary}>{busy ? 'Saving…' : amend ? 'Save new revision' : 'Stage for review'}</button>
    </div>;
    return <section className={theme.components.panel + ' space-y-6 p-5 md:p-6'} aria-labelledby="ad-settlement-title">
        <header className="flex flex-wrap items-start justify-between gap-4"><div><h2 id="ad-settlement-title" className="flex items-center gap-2 text-xl font-black text-slate-900 dark:text-white"><FileCheck2 className="h-5 w-5 text-modtale-accent" />Ad settlement review</h2><p className="mt-2 max-w-3xl text-sm text-slate-500 dark:text-slate-400">Inspect reported deposits, provisional activity and a frozen 75% creator allocation before a provider-specific verification process is connected.</p></div><button type="button" disabled={busy || loading} onClick={() => void load()} className={theme.components.buttonSecondary}><RefreshCw className="h-4 w-4" />Refresh</button></header>
        <div className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-950 dark:border-amber-800 dark:bg-amber-950/25 dark:text-amber-100"><p className="flex items-center gap-2 font-bold"><LockKeyhole className="h-4 w-4" />Credit release locked</p><p className="mt-1">Reported deposits are unverified. Traffic is provisional, including bot filtering and event-time ownership checks. Staging, reviewing or amending a report cannot add creator earnings or transfer money.</p></div>
        {error && <p role="alert" className="rounded-xl border border-red-300 p-3 text-sm text-red-700 dark:text-red-300">{error}</p>}
        {notice && <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">{notice}</p>}
        <details className="rounded-xl border border-slate-200 p-4 dark:border-white/10"><summary className="cursor-pointer font-bold text-slate-800 dark:text-slate-100" onClick={() => { if (amending) { setAmending(false); setDraft(initialReport); } }}>Stage a report</summary><div className="mt-5">{reportForm(false)}</div></details>
        {loading && <p role="status" className="text-sm text-slate-500">Loading review evidence…</p>}
        {!loading && rows.length === 0 && <p className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500 dark:border-white/10">No reported deposits staged yet. Estimates and ad clicks never create payable revenue.</p>}
        <div className="grid gap-3 md:grid-cols-2">{rows.map(row => <button key={row.id} type="button" disabled={busy || loading} onClick={() => void open(row.id)} className={`rounded-xl border p-4 text-left ${selected?.id === row.id ? 'border-modtale-accent bg-modtale-accent/5' : 'border-slate-200 dark:border-white/10'}`}><span className="block font-bold text-slate-900 dark:text-white">{row.provider} · {row.reportId}</span><span className="mt-1 block text-sm text-slate-500 dark:text-slate-400">{money(row.reportedCollectedCents)} reported · {String(row.status).replaceAll('_', ' ').toLowerCase()}</span><span className="mt-1 block text-xs text-slate-500">{row.from} – {row.through} · Revision {row.revision}</span></button>)}</div>
        {selected && latest && <div className="space-y-5 border-t border-slate-200 pt-5 dark:border-white/10">
            <div><h3 className="text-lg font-black text-slate-900 dark:text-white">{selected.reportId} · Revision {selected.revision}</h3><p className="mt-1 break-all text-xs text-slate-500">Deposit reference: {selected.depositId} · Funding unverified</p></div>
            <div className="grid gap-3 sm:grid-cols-3">{[['Reported deposit', latest.reportedCollectedCents], ['Provisional creator pool · 75%', latest.provisionalCreatorPoolCents], ['Provisional platform share · 25%', latest.provisionalPlatformCents]].map(([label, value]) => <div className="rounded-xl bg-slate-50 p-4 dark:bg-white/5" key={label}><p className="text-xs font-bold text-slate-500 dark:text-slate-400">{label}</p><p className="mt-2 text-2xl font-black text-slate-900 dark:text-white">{money(Number(value))}</p></div>)}</div>
            <p className="text-sm text-slate-500 dark:text-slate-400">Provisional points use recorded pageviews and launcher downloads. Ad clicks, web downloads and generic API downloads earn no points here. Current opt-in and owner IDs are snapshotted; historical eligibility still needs validation.</p>
            {latest.unallocatedCreatorCents > 0 && <p className="text-sm text-amber-700 dark:text-amber-300">{money(latest.unallocatedCreatorCents)} of the creator pool remains unallocated because no provisional eligible activity is available.</p>}
            <div className="space-y-3">{latest.projects.map(row => <div key={row.projectId} className="rounded-xl border border-slate-200 p-4 dark:border-white/10"><div className="flex flex-wrap justify-between gap-2"><h4 className="font-bold text-slate-900 dark:text-white">{row.title}</h4><strong className="text-slate-900 dark:text-white">{money(row.provisionalCreatorCents)} provisional</strong></div><p className="mt-1 text-xs text-slate-500">{row.pageviews.toLocaleString()} pageviews · {row.launcherDownloads.toLocaleString()} launcher downloads · {row.provisionalPoints.toLocaleString()} provisional points</p><p className="mt-2 text-sm text-slate-500 dark:text-slate-400">{row.eligibilityNote}</p></div>)}</div>
            <details className="text-xs text-slate-500"><summary className="cursor-pointer font-bold">Source fingerprints and immutable revisions</summary><p className="mt-2 break-all">Report SHA-256: {latest.reportSha256}</p><p className="mt-2 break-all">Activity SHA-256: {latest.activityDigest}</p>{selected.snapshots.map(snapshot => <p key={snapshot.revision} className="mt-2">Revision {snapshot.revision}: {money(snapshot.reportedCollectedCents)} reported, {snapshot.from} – {snapshot.through}</p>)}</details>
            {selected.status === 'AWAITING_REVIEW' && !amending && <div className="space-y-3"><label className="block text-sm font-bold text-slate-700 dark:text-slate-200">Review reason<textarea value={reason} maxLength={1000} disabled={busy} onChange={event => setReason(event.target.value)} className={theme.components.inputField + ' mt-2'} /></label><div className="flex flex-wrap gap-2"><button type="button" disabled={busy || !reason.trim()} onClick={() => void mutate('review', 'REVIEWED_PROVISIONAL')} className={theme.components.buttonPrimary}>Mark provisionally reviewed</button><button type="button" disabled={busy || !reason.trim()} onClick={() => void mutate('review', 'REJECTED')} className={theme.components.buttonSecondary}>Reject report</button></div></div>}
            <button type="button" disabled={busy || selected.revision >= 10} onClick={startAmendment} className={theme.components.buttonSecondary}>Create an amendment</button>
            {amending && reportForm(true)}
            <div className="space-y-3"><h4 className="font-bold text-slate-900 dark:text-white">Review history</h4>{selected.audit.map(event => <div key={event.operationId} className="border-l-2 border-slate-200 pl-3 text-sm dark:border-white/10"><p className="font-semibold text-slate-800 dark:text-slate-200">{event.action.replaceAll('_', ' ')} · Revision {event.revision}</p><p className="mt-1 text-slate-600 dark:text-slate-400">{event.reason}</p><p className="mt-1 text-xs text-slate-500">{new Date(event.createdAt).toLocaleString()} · Reviewer {event.actorId}</p></div>)}</div>
        </div>}
    </section>;
}
