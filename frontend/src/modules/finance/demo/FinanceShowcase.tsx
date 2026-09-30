import { useEffect, useRef, useState } from 'react';
import { FinanceManager } from '@/modules/user/views/FinanceManager';
import { DonationPromptModal } from '@/modules/project/components/dialogs/DonationPromptModal';
import { financeClient } from '@/modules/finance/api/financeClient';
import { api } from '@/utils/api';
import { AdSettlementReview } from '@/modules/finance/components/AdSettlementReview';
import { createAdSettlementFixture } from './adSettlementFixture';

export default function FinanceShowcase() {
    const [scenario, setScenario] = useState('unavailable');
    const [view, setView] = useState('creator');
    const scenarioRef = useRef(scenario);
    scenarioRef.current = scenario;
    const [ready, setReady] = useState(false);
    const [dark, setDark] = useState(false);
    const [showSupport, setShowSupport] = useState(false);
    const [notice, setNotice] = useState('');
    const [processing, setProcessing] = useState(false);
    useEffect(() => {
        const previous = { ...financeClient };
        let adStage = createAdSettlementFixture();
        financeClient.getAdSettlementStages = async () => [{ ...adStage, ...adStage.snapshots.at(-1), snapshots: undefined, audit: undefined }];
        financeClient.getAdSettlementStage = async () => adStage;
        financeClient.stageAdSettlement = async () => { throw new Error('Demo only: no report is stored.'); };
        financeClient.amendAdSettlement = async () => { throw new Error('Demo only: no report is amended.'); };
        financeClient.reviewAdSettlement = async (_id, review) => {
            adStage = { ...adStage, status: review.decision || 'REVIEWED_PROVISIONAL', audit: [...adStage.audit, { operationId: review.operationId, action: review.decision || 'REVIEWED_PROVISIONAL', revision: 1, actorId: 'demo-reviewer', reason: review.reason, createdAt: new Date().toISOString() }] };
            return adStage;
        };
        const interceptor = api.interceptors.request.use(() => { throw new Error('Network access is disabled in the finance demo.'); });
        financeClient.getFinanceContexts = async () => [{ id: 'synthetic-creator', username: 'Demo creator', isPersonal: true }];
        financeClient.getSupportSubscriptions = async () => [{ id: 'demo-subscription', projectId: 'demo-project', projectTitle: 'Demo monthly support', projectUrl: '', amountCents: 500, currency: 'usd', status: 'active', cancelAtPeriodEnd: true, testMode: true }];
        financeClient.openSupportBillingPortal = async () => { throw new Error('Demo only: a real support plan is managed in Stripe’s secure billing portal.'); };
        financeClient.requestPayout = async (amountCents?: number) => ({ amountCents: amountCents ?? 2500, status: 'RESERVED', testMode: true });
        financeClient.getCreatorOverview = async () => {
        const scenario = scenarioRef.current;
        const disabled = scenario === 'unavailable';
        const settledEarnings = (scenario === 'settled' ? [1000, 1200, 1500, 2800] : scenario === 'pending' ? [250, 750] : [])
            .map((count, index) => ({ date: `2026-09-${26 + index}`, count }));
        const ads = settledEarnings.map(point => ({ ...point, count: scenario === 'settled' ? 375 : 125 }));
        const support = settledEarnings.map((point, index) => ({ ...point, count: point.count - ads[index].count }));
        if (scenario === 'error') throw new Error('Synthetic finance service interruption.');
        return { ownerId: 'synthetic-creator', ownerAccountType: 'PERSONAL', currency: 'usd', testMode: true,
            withdrawalsEnabled: !disabled, onboardingEnabled: false, stripeConnected: scenario === 'settled', stripeOnboardingComplete: scenario === 'settled', stripePayoutsEnabled: scenario === 'settled',
            availableCents: 0, testAvailableCents: scenario === 'settled' ? 2500 : 0,
            pendingCents: scenario === 'pending' ? 1850 : 0, paidOutCents: scenario === 'settled' ? 4000 : scenario === 'pending' ? 1000 : 0,
            adjustmentOwedCents: scenario === 'refund' ? 45 : 0, payoutHold: scenario === 'refund',
            minPayoutCents: 1000, adCreatorSplitPercent: 75,
            periodAdRevenueCents: ads.reduce((total, point) => total + point.count, 0),
            periodDonationRevenueCents: support.reduce((total, point) => total + point.count, 0),
            availabilityMessage: 'Synthetic preview data only. No account, payment, bank transfer or reminder email is created.',
            payoutRequests: scenario === 'settled' || scenario === 'pending' ? [{ id: 'demo-transfer', amountCents: scenario === 'settled' ? 4000 : 1000, status: 'TRANSFERRED', createdAt: '2026-09-29T15:00:00Z' }]
                : scenario === 'refund' ? [{ id: 'demo-held-transfer', amountCents: 1000, status: 'REQUIRES_REVIEW', createdAt: '2026-09-29T15:00:00Z', reviewReason: 'Synthetic provider reconciliation is still in progress.' }] : [],
            earningsChart: settledEarnings, donationsChart: support, adsChart: ads };
        };
        setReady(true);
        return () => { Object.assign(financeClient, previous); api.interceptors.request.eject(interceptor); };
    }, []);
    if (!ready) return <p>Preparing synthetic finance demo…</p>;
    return <div className={dark ? 'dark' : ''}><div className="min-h-screen bg-slate-50 p-4 text-slate-900 dark:bg-slate-950 dark:text-white md:p-8">
        <div className="mx-auto max-w-6xl">
            <header className="mb-6 rounded-2xl border-2 border-dashed border-blue-500 bg-blue-50 p-4 dark:bg-blue-950/30">
                <h1 className="font-black">Finance demo · No payments</h1><p className="mt-1 text-sm">Synthetic component showcase. Account and payment network requests are disabled. This page is excluded from normal production access.</p>
                <div className="mt-4 flex flex-wrap gap-2">
                    <button type="button" className="rounded border px-3 py-2 text-sm" onClick={() => setView(value => value === 'creator' ? 'admin' : 'creator')}>{view === 'creator' ? 'Show ad review' : 'Show creator dashboard'}</button>
                    <label className="text-sm">Dashboard state <select value={scenario} onChange={event => setScenario(event.target.value)} className="rounded border bg-white px-2 py-2 text-slate-900"><option value="unavailable">Unavailable</option><option value="pending">Pending earnings</option><option value="settled">Settled earnings</option><option value="refund">Refund adjustment</option><option value="error">Service error</option></select></label>
                    <button className="rounded border px-3 py-2 text-sm" onClick={() => setDark(value => !value)}>Toggle theme</button>
                    <button className="rounded bg-blue-600 px-3 py-2 text-sm text-white" onClick={() => { setNotice(''); setShowSupport(true); }}>Open support dialog</button>
                </div>
            </header>
            {notice && <p role="status" className="mb-4 rounded-xl border border-blue-300 p-3 text-sm">{notice}</p>}
            {view === 'creator' ? <FinanceManager key={scenario} /> : <AdSettlementReview />}
            <DonationPromptModal show={showSupport} suggestedAmountCents={500} recurringDefault={false} allowRecurring platformCutPercent={10} testMode isProcessing={processing}
                onClose={() => { setShowSupport(false); setNotice('Demo: dialog closed; the free download continues.'); }}
                onSkip={() => { setShowSupport(false); setNotice('Demo: download continues without a tip.'); }}
                onDonate={(cents, recurring) => {
                    setProcessing(true);
                    window.setTimeout(() => { setProcessing(false); setShowSupport(false); setNotice(`Demo: ${(cents / 100).toFixed(2)} USD ${recurring ? 'monthly support selected' : 'one-time tip selected'}. No checkout, payment or subscription was created.`); }, 600);
                }} />
        </div>
    </div></div>;
}
