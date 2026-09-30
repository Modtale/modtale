import { useEffect, useRef, useState } from 'react';
import { financeClient } from '@/modules/finance/api/financeClient';
import { extractApiErrorMessage } from '@/utils/api';
import { theme } from '@/styles/theme';

export interface StripeReadinessReport {
    mode: string; apiVersion: string; livePaymentsEnabled: boolean; providerConfigurationVerified: boolean;
    verifiedAt?: string; checks: Array<{ code: string; passed: boolean; action: string }>; remainingChecks: string[];
}
export function StripeReadiness() {
    const [report, setReport] = useState<StripeReadinessReport | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const sequence = useRef(0); const mounted = useRef(false); const inFlight = useRef(false);
    useEffect(() => {
        mounted.current = true; const request = ++sequence.current;
        void financeClient.getStripeReadiness().then(value => { if (mounted.current && sequence.current === request) setReport(value); })
            .catch(failure => { if (mounted.current && sequence.current === request) setError(extractApiErrorMessage(failure, 'Could not load payment configuration.')); });
        return () => { mounted.current = false; sequence.current++; };
    }, []);
    const verify = async () => {
        if (inFlight.current) return;
        inFlight.current = true; sequence.current++; setBusy(true); setError(''); setReport(current => current ? { ...current, providerConfigurationVerified: false, verifiedAt: undefined } : null);
        try { const value = await financeClient.verifyStripeReadiness(); if (mounted.current) setReport(value); }
        catch (failure) { if (mounted.current) setError(extractApiErrorMessage(failure, 'Provider checks failed. Configuration remains unverified.')); }
        finally { inFlight.current = false; if (mounted.current) setBusy(false); }
    };
    return <section aria-labelledby="stripe-readiness-title" className={theme.components.panel + ' space-y-4 p-5 md:p-6'}>
        <header className="flex flex-wrap items-start justify-between gap-3"><div><h2 id="stripe-readiness-title" className="text-xl font-black text-slate-900 dark:text-white">Payment setup</h2><p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Configure secrets through the deployment environment. Never paste keys into this page.</p></div><button type="button" disabled={busy || !report} onClick={() => void verify()} className={theme.components.buttonSecondary}>{busy ? 'Checking provider…' : 'Verify provider configuration'}</button></header>
        {error && <p role="alert" className="text-sm text-red-700 dark:text-red-300">{error}</p>}
        {!report && !error && <p role="status">Loading configuration…</p>}
        {report && <>
            <p className="text-sm text-slate-700 dark:text-slate-200">Mode: <strong>{report.mode}</strong> · API: {report.apiVersion} · Live activation: {report.livePaymentsEnabled ? 'Enabled' : 'Off'}</p>
            <p role="status" className="rounded-xl border border-amber-300 p-3 text-sm text-amber-900 dark:text-amber-100">{report.providerConfigurationVerified ? 'Provider configuration checks passed. Signed delivery, complete sandbox flows and launch approvals still need verification.' : 'Provider configuration has not passed every check. Keep payment activation off until the required checks are complete.'}</p>
            {report.verifiedAt && <p className="text-xs text-slate-500">Last provider check: {new Date(report.verifiedAt).toLocaleString()}</p>}
            <ul className="space-y-2">{report.checks.map(check => <li key={check.code} className="rounded-xl border border-slate-200 p-3 text-sm dark:border-white/10"><strong className={check.passed ? 'text-emerald-700 dark:text-emerald-300' : 'text-amber-700 dark:text-amber-300'}>{check.passed ? 'Passed' : 'Required'} · {check.code.replaceAll('_', ' ')}</strong><p className="mt-1 text-slate-600 dark:text-slate-300">{check.action}</p></li>)}</ul>
            <details className="text-sm text-slate-600 dark:text-slate-300"><summary className="cursor-pointer font-semibold">Remaining delivery and launch checks</summary><ul className="mt-3 list-disc space-y-2 pl-5">{report.remainingChecks.map(check => <li key={check}>{check}</li>)}</ul></details>
        </>}
    </section>;
}
