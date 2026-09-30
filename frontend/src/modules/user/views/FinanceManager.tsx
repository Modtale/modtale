import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { AlertTriangle, BadgeDollarSign, Building2, CalendarClock, ChevronDown, CreditCard, RefreshCw, Wallet } from 'lucide-react';
import { financeClient } from '@/modules/finance/api/financeClient';
import { extractApiErrorMessage } from '@/utils/api';
import { parseSupportAmount } from '@/modules/finance/api/financeTypes';
import { SupportSubscriptions } from '@/modules/finance/components/SupportSubscriptions';
import { LineChart } from '@/components/ui/charts/LineChart';
import { StatusModal } from '@/components/ui/StatusModal';
import { theme } from '@/styles/theme';

interface OrgPolicyMember {
    userId: string;
    username: string;
    stripeConnected: boolean;
    stripePayoutsEnabled: boolean;
}

const inputNoNativeUi = `${theme.components.inputField} appearance-none [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none`;

const SummaryCard = ({ title, value, subtitle, icon: Icon, color }: any) => (
    <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 shadow-sm hover:shadow-md transition-all relative overflow-hidden group backdrop-blur-md flex flex-col justify-between p-6">
        <div className="mb-3 flex items-center justify-between">
            <div className={`p-3 rounded-2xl ${color} bg-opacity-10 shadow-inner`}>
                <Icon className={`w-6 h-6 ${color}`} />
            </div>
            <h3 className="text-[10px] font-black uppercase tracking-wider text-slate-500 dark:text-slate-400">{title}</h3>
        </div>
        <div className="text-3xl md:text-4xl font-black text-slate-900 dark:text-white tracking-tighter leading-none">{value}</div>
        {subtitle && <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">{subtitle}</p>}
    </div>
);

const FinanceManagerSkeleton = () => (
    <div className="space-y-6 animate-pulse">
        <div className={theme.components.panel + ' p-5'}>
            <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
                <div className="space-y-2">
                    <div className="h-9 w-72 rounded-xl bg-slate-200 dark:bg-white/10" />
                    <div className="h-4 w-56 rounded-lg bg-slate-200 dark:bg-white/10" />
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                    <div className="h-11 w-[250px] rounded-xl bg-slate-200 dark:bg-white/10" />
                    <div className="h-11 w-44 rounded-xl bg-slate-200 dark:bg-white/10" />
                </div>
            </div>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
            {[0, 1, 2, 3].map((i) => (
                <div key={i} className="rounded-3xl border border-slate-200 bg-white/40 p-6 dark:border-white/10 dark:bg-white/5">
                    <div className="mb-4 flex items-center justify-between">
                        <div className="h-12 w-12 rounded-2xl bg-slate-200 dark:bg-white/10" />
                        <div className="h-3 w-20 rounded bg-slate-200 dark:bg-white/10" />
                    </div>
                    <div className="h-10 w-32 rounded-xl bg-slate-200 dark:bg-white/10" />
                    <div className="mt-3 h-3 w-24 rounded bg-slate-200 dark:bg-white/10" />
                </div>
            ))}
        </div>

        <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
            {[0, 1].map((i) => (
                <div key={i} className={theme.components.panel + ' p-5'}>
                    <div className="mb-4 h-4 w-36 rounded bg-slate-200 dark:bg-white/10" />
                    <div className="h-[320px] rounded-2xl bg-slate-200 dark:bg-white/10" />
                </div>
            ))}
        </div>

        <div className={theme.components.panel + ' space-y-4 p-5'}>
            <div className="flex items-center justify-between">
                <div className="space-y-2">
                    <div className="h-6 w-44 rounded-lg bg-slate-200 dark:bg-white/10" />
                    <div className="h-4 w-64 rounded bg-slate-200 dark:bg-white/10" />
                </div>
                <div className="flex gap-2">
                    <div className="h-10 w-44 rounded-xl bg-slate-200 dark:bg-white/10" />
                    <div className="h-10 w-44 rounded-xl bg-slate-200 dark:bg-white/10" />
                </div>
            </div>
            <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                {[0, 1, 2].map((i) => <div key={i} className="h-20 rounded-xl bg-slate-200 dark:bg-white/10" />)}
            </div>
            <div className="grid grid-cols-1 gap-3 md:grid-cols-[1fr_auto] md:items-end">
                <div className="h-20 rounded-xl bg-slate-200 dark:bg-white/10" />
                <div className="h-[46px] w-44 rounded-xl bg-slate-200 dark:bg-white/10" />
            </div>
        </div>
    </div>
);

export const FinanceManager: React.FC = () => {
    const [range, setRange] = useState('30d');
    const [loading, setLoading] = useState(true);
    const [data, setData] = useState<any>(null);
    const [payoutAmount, setPayoutAmount] = useState('');
    const [loadError, setLoadError] = useState('');
    const [busyAction, setBusyAction] = useState('');
    const [onboardingCountry, setOnboardingCountry] = useState('');
    const requestVersionRef = useRef(0);
    const actionInFlightRef = useRef(false);
    const payoutRequestKeyRef = useRef<string | null>(null);
    const [contexts, setContexts] = useState<any[]>([]);
    const [selectedOwnerId, setSelectedOwnerId] = useState('');
    const [isContextDropdownOpen, setIsContextDropdownOpen] = useState(false);
    const [orgMembers, setOrgMembers] = useState<OrgPolicyMember[]>([]);
    const [orgPayoutMode, setOrgPayoutMode] = useState<'DIRECT_TO_ORG_STRIPE' | 'DISTRIBUTE_TO_MEMBERS'>('DIRECT_TO_ORG_STRIPE');
    const [orgShares, setOrgShares] = useState<Record<string, number>>({});
    const [savingOrgPolicy, setSavingOrgPolicy] = useState(false);
    const [status, setStatus] = useState<{ type: 'success' | 'error' | 'warning' | 'info'; title: string; msg: string } | null>(null);
    const contextDropdownRef = useRef<HTMLDivElement>(null);

    const currency = (data?.currency || 'usd').toUpperCase();
    const availableCents = Number(data?.testMode ? data?.testAvailableCents || 0 : data?.availableCents || 0);
    const isOrgContext = data?.ownerAccountType === 'ORGANIZATION';

    const formatMoney = (cents: number) => new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: currency.length === 3 ? currency : 'USD'
    }).format((cents || 0) / 100);

    const loadContexts = async () => {
        const available = await financeClient.getFinanceContexts();
        setContexts(Array.isArray(available) ? available : []);
        if (!Array.isArray(available) || available.length === 0) { setLoadError('No finance accounts are available for this account.'); setLoading(false); }
        if (!selectedOwnerId && Array.isArray(available) && available.length > 0) setSelectedOwnerId(available[0].id);
    };

    const load = async (selectedRange = range, ownerId = selectedOwnerId) => {
        if (!ownerId) return;
        const requestVersion = ++requestVersionRef.current;
        setLoading(true);
        setLoadError('');
        try {
            const overview = await financeClient.getCreatorOverview(selectedRange, ownerId);
            if (requestVersion !== requestVersionRef.current) return;
            setData(overview);
            const storageKey = `finance-payout-request:${ownerId}`;
            const unresolvedKey = sessionStorage.getItem(storageKey);
            if (unresolvedKey && (overview?.payoutRequests || []).some((request: any) => request.id?.endsWith(`:${unresolvedKey}`))) {
                sessionStorage.removeItem(storageKey); payoutRequestKeyRef.current = null;
            }

            if (overview?.ownerAccountType === 'ORGANIZATION') {
                const policy = await financeClient.getOrgPayoutPolicy(ownerId);
                if (requestVersion !== requestVersionRef.current) return;
                const members = (policy?.members || []) as OrgPolicyMember[];
                setOrgMembers(members);
                setOrgPayoutMode(policy?.payoutMode || 'DIRECT_TO_ORG_STRIPE');

                const nextShares: Record<string, number> = {};
                for (const member of members) {
                    const existing = (policy?.shares || []).find((item: any) => item.userId === member.userId);
                    nextShares[member.userId] = Number(existing?.percent || 0);
                }
                setOrgShares(nextShares);
            } else {
                setOrgMembers([]);
                setOrgPayoutMode('DIRECT_TO_ORG_STRIPE');
                setOrgShares({});
            }
        } catch (e: any) {
            if (requestVersion === requestVersionRef.current) setLoadError(extractApiErrorMessage(e, 'Could not load finance data.'));
        } finally {
            if (requestVersion === requestVersionRef.current) setLoading(false);
        }
    };

    useEffect(() => {
        loadContexts().catch(() => { setLoadError('Could not load finance accounts. Please try again.'); setLoading(false); });
        return () => { requestVersionRef.current += 1; };
    }, []);

    useEffect(() => {
        payoutRequestKeyRef.current = null;
        setPayoutAmount('');
        if (selectedOwnerId) load(range, selectedOwnerId);
    }, [range, selectedOwnerId]);

    useEffect(() => {
        const onClickOutside = (event: MouseEvent) => {
            if (contextDropdownRef.current && !contextDropdownRef.current.contains(event.target as Node)) {
                setIsContextDropdownOpen(false);
            }
        };
        document.addEventListener('mousedown', onClickOutside);
        return () => document.removeEventListener('mousedown', onClickOutside);
    }, []);

    const chartData = useMemo(() => {
        const mapSeries = (series: any[]) => (series || []).map(point => ({ date: point.date, value: Number(point.count || 0) }));
        return {
            earnings: [{ id: 'earnings', label: 'Creator Earnings', color: '#2563eb', data: mapSeries(data?.earningsChart) }],
            donations: [{ id: 'donations', label: 'Donations', color: '#16a34a', data: mapSeries(data?.donationsChart) }],
            ads: [{ id: 'ads', label: 'Ads', color: '#f97316', data: mapSeries(data?.adsChart) }]
        };
    }, [data]);

    const handleConnectStripe = async () => {
        if (actionInFlightRef.current || !data?.onboardingEnabled) return;
        actionInFlightRef.current = true; setBusyAction('connect');
        const onboardingWindow = window.open('about:blank', '_blank');
        if (onboardingWindow) onboardingWindow.opener = null;
        try {
            const res = await financeClient.createStripeOnboardingLink('/dashboard/finance', selectedOwnerId || undefined, onboardingCountry);
            if (!res?.onboardingUrl || !onboardingWindow) throw new Error('Allow a new tab to complete onboarding.');
            const url = new URL(res.onboardingUrl);
            if (url.protocol !== 'https:' || url.hostname !== 'connect.stripe.com') throw new Error('The provider returned an invalid onboarding link.');
            onboardingWindow.location.replace(url.href);
            setStatus({ type: 'info', title: 'Stripe Onboarding', msg: 'Stripe opened in a new tab. Complete it, then click "Refresh Stripe Status".' });
        } catch (e: any) {
            onboardingWindow?.close();
            setStatus({ type: 'error', title: 'Stripe Error', msg: extractApiErrorMessage(e, 'Could not start Stripe onboarding.') });
        } finally { actionInFlightRef.current = false; setBusyAction(''); }
    };

    const handleRefreshStripe = async () => {
        try {
            await financeClient.refreshStripeStatus(selectedOwnerId || undefined);
            await load(range, selectedOwnerId);
            setStatus({ type: 'success', title: 'Stripe Updated', msg: 'Stripe account status refreshed.' });
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Refresh Failed', msg: e?.response?.data || 'Could not refresh Stripe status.' });
        }
    };

    const handleRequestPayout = async () => {
        if (actionInFlightRef.current || !data?.withdrawalsEnabled) return;
        const parsed = payoutAmount.trim() ? parseSupportAmount(payoutAmount, data?.minPayoutCents || 1000, availableCents) : undefined;
        if (parsed === null) { setStatus({ type: 'warning', title: 'Check Amount', msg: 'Enter a valid amount between the minimum payout and your available balance.' }); return; }
        actionInFlightRef.current = true; setBusyAction('payout');
        try {
            const storageKey = `finance-payout-request:${selectedOwnerId}`;
            payoutRequestKeyRef.current = payoutRequestKeyRef.current || sessionStorage.getItem(storageKey) || crypto.randomUUID();
            sessionStorage.setItem(storageKey, payoutRequestKeyRef.current);
            const res = await financeClient.requestPayout(parsed, selectedOwnerId || undefined, payoutRequestKeyRef.current);
            sessionStorage.removeItem(storageKey);
            payoutRequestKeyRef.current = null;
            setStatus({ type: 'success', title: 'Transfer Queued', msg: `${formatMoney(res.amountCents || 0)} reserved for transfer to your payout account. This is not a confirmation of bank receipt.` });
            setPayoutAmount('');
            await load(range, selectedOwnerId);
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Payout Failed', msg: extractApiErrorMessage(e, 'Could not request payout.') });
        } finally { actionInFlightRef.current = false; setBusyAction(''); }
    };

    const saveOrgPayoutPolicy = async () => {
        if (!isOrgContext) return;
        const shares = Object.entries(orgShares)
            .map(([userId, percent]) => ({ userId, percent: Math.max(0, Math.round(percent || 0)) }))
            .filter(item => item.percent > 0);

        const total = shares.reduce((sum, item) => sum + item.percent, 0);
        if (orgPayoutMode === 'DISTRIBUTE_TO_MEMBERS' && total !== 100) {
            setStatus({ type: 'warning', title: 'Invalid Distribution', msg: 'Distributed payout shares must total exactly 100%.' });
            return;
        }

        setSavingOrgPolicy(true);
        try {
            await financeClient.updateOrgPayoutPolicy(selectedOwnerId, { payoutMode: orgPayoutMode, shares });
            setStatus({ type: 'success', title: 'Saved', msg: 'Organization payout policy updated.' });
            await load(range, selectedOwnerId);
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Save Failed', msg: e?.response?.data || 'Could not update organization payout policy.' });
        } finally {
            setSavingOrgPolicy(false);
        }
    };

    if (loading) return <FinanceManagerSkeleton />;
    if (loadError) return <div role="alert" className={theme.components.panel + ' p-6 space-y-3'}><h2 className="text-lg font-bold text-slate-900 dark:text-white">Finance is temporarily unavailable</h2><p className="text-sm text-slate-600 dark:text-slate-300">{loadError}</p><button className={theme.components.buttonSecondary} onClick={() => selectedOwnerId ? load() : loadContexts().catch(() => setLoadError('Could not load finance accounts. Please try again.'))}>Try again</button></div>;

    const selectedContext = contexts.find(ctx => ctx.id === selectedOwnerId);
    const orgShareTotal = Object.values(orgShares).reduce((sum, n) => sum + Math.max(0, Math.round(Number(n || 0))), 0);
    const ranges = ['30d', '90d', '1y'];
    const activeRangeIndex = ranges.indexOf(range);

    return (
        <div className="space-y-6">
            {status && typeof document !== 'undefined' && createPortal(
                <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} />,
                document.body
            )}

            <div className={theme.components.panel + ' p-5'}>
                <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
                    <div>
                        <h1 className="text-3xl font-black tracking-tight text-slate-900 dark:text-white">Modtale Finance Manager</h1>
                        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                            Managing: <span className="font-bold">{selectedContext?.username || 'Personal'}</span>
                            {isOrgContext && <span className="ml-2 inline-flex items-center gap-1"><Building2 className="h-3 w-3" /> Organization</span>}
                        </p>
                    </div>

                    <div className="flex flex-col gap-2 sm:flex-row">
                        <div className="relative" ref={contextDropdownRef}>
                            <button
                                type="button"
                                onClick={() => setIsContextDropdownOpen((prev) => !prev)}
                                className={`${theme.components.inputField} min-w-[250px] flex items-center justify-between text-left`}
                            >
                                <span className="truncate text-slate-900 dark:text-white font-medium">
                                    {selectedContext
                                        ? `${selectedContext.username} ${selectedContext.isPersonal ? '(Personal)' : '(Organization)'}`
                                        : 'Select context'}
                                </span>
                                <ChevronDown className={`h-4 w-4 text-slate-500 transition-transform ${isContextDropdownOpen ? 'rotate-180' : ''}`} />
                            </button>
                            {isContextDropdownOpen && (
                                <div className="absolute z-50 mt-2 w-full overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl dark:border-white/10 dark:bg-slate-900">
                                    {contexts.map((ctx: any) => (
                                        <button
                                            key={ctx.id}
                                            type="button"
                                            onClick={() => {
                                                setSelectedOwnerId(ctx.id);
                                                setIsContextDropdownOpen(false);
                                            }}
                                            className={`w-full px-3 py-2.5 text-left text-sm transition-colors ${
                                                selectedOwnerId === ctx.id
                                                    ? 'bg-modtale-accent text-white font-bold'
                                                    : 'text-slate-700 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-white/5'
                                            }`}
                                        >
                                            {ctx.username} {ctx.isPersonal ? '(Personal)' : '(Organization)'}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>

                        <div className="relative flex bg-white/60 dark:bg-black/20 p-1 rounded-xl shadow-inner border border-slate-200 dark:border-white/10 shrink-0 w-fit">
                            <div
                                className="absolute top-1 bottom-1 w-14 rounded-lg transition-transform duration-300 ease-out bg-modtale-accent shadow-sm shadow-modtale-accent/30 border border-transparent"
                                style={{ transform: `translateX(${activeRangeIndex * 100}%)` }}
                            />
                            {ranges.map(option => (
                                <button
                                    key={option}
                                    onClick={() => setRange(option)}
                                    className={`relative z-10 w-14 py-2 text-xs font-bold transition-colors duration-300 ${
                                        range === option ? 'text-white' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300'
                                    }`}
                                >
                                    {option}
                                </button>
                            ))}
                        </div>
                    </div>
                </div>
            </div>

            {(!data?.withdrawalsEnabled || data?.testMode) && <div role="status" className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950 dark:border-amber-900/50 dark:bg-amber-950/20 dark:text-amber-100"><p className="font-bold">{data?.testMode ? 'Finance preview' : 'Creator payments are being prepared'}</p><p className="mt-1">{data?.availabilityMessage || 'Withdrawals are currently unavailable.'} Earned balances do not expire.</p></div>}
            {(data?.payoutHold || Number(data?.adjustmentOwedCents || 0) > 0) && <div role="status" className="rounded-2xl border border-amber-300 p-4 text-sm text-amber-900 dark:text-amber-100"><p className="font-bold">Payouts paused for reconciliation</p><p className="mt-1">{Number(data?.adjustmentOwedCents || 0) > 0 ? `${formatMoney(data.adjustmentOwedCents)} will be offset against future earnings after a payment adjustment. ` : ''}Funds stay recorded while the account is reviewed. No automatic debit is made to your bank account.</p></div>}
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryCard title={data?.testMode ? "Test Balance" : "Available"} value={formatMoney(availableCents)} subtitle="Settled funds" icon={Wallet} color="text-emerald-500" />
                <SummaryCard title="Pending Estimate" value={formatMoney(data?.pendingCents || 0)} subtitle="Awaiting settlement" icon={CalendarClock} color="text-amber-500" />
                <SummaryCard title="This Period" value={formatMoney((data?.periodAdRevenueCents || 0) + (data?.periodDonationRevenueCents || 0))} subtitle="Ads and creator support" icon={BadgeDollarSign} color="text-violet-500" />
                <SummaryCard title="Transferred" value={formatMoney(data?.paidOutCents || 0)} subtitle="Sent to payout provider" icon={BadgeDollarSign} color="text-blue-500" />
            </div>

            <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
                <div className={theme.components.panel + ' p-5'}>
                    <h3 className="mb-4 text-sm font-black uppercase tracking-wide text-slate-600 dark:text-slate-300">Earnings Over Time</h3>
                    <div className="h-[320px]"><LineChart datasets={chartData.earnings} /></div>
                </div>
                <div className={theme.components.panel + ' p-5'}>
                    <h3 className="mb-4 text-sm font-black uppercase tracking-wide text-slate-600 dark:text-slate-300">Revenue Mix</h3>
                    <div className="h-[320px]"><LineChart datasets={[...chartData.donations, ...chartData.ads]} /></div>
                </div>
            </div>

            {isOrgContext && (
                <div className={theme.components.panel + ' space-y-4 p-5'}>
                    <div>
                        <h2 className="text-xl font-black text-slate-900 dark:text-white">Organization Payout Policy</h2>
                        <p className="text-sm text-slate-500 dark:text-slate-400">Choose whether payouts go to the org Stripe account or are distributed to members.</p>
                    </div>

                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div className={theme.components.panel + ' p-3'}>
                            <div className="text-xs font-black uppercase tracking-widest text-slate-500 dark:text-slate-400 mb-2">Payout Mode</div>
                            <div className="flex gap-2">
                                <button
                                    type="button"
                                    onClick={() => setOrgPayoutMode('DIRECT_TO_ORG_STRIPE')}
                                    className={`rounded-lg px-3 py-2 text-xs font-bold border ${
                                        orgPayoutMode === 'DIRECT_TO_ORG_STRIPE'
                                            ? 'border-modtale-accent bg-modtale-accent text-white'
                                            : 'border-slate-200 bg-slate-100 text-slate-700 dark:border-white/10 dark:bg-white/5 dark:text-slate-300'
                                    }`}
                                >
                                    Direct to Org Stripe
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setOrgPayoutMode('DISTRIBUTE_TO_MEMBERS')}
                                    className={`rounded-lg px-3 py-2 text-xs font-bold border ${
                                        orgPayoutMode === 'DISTRIBUTE_TO_MEMBERS'
                                            ? 'border-modtale-accent bg-modtale-accent text-white'
                                            : 'border-slate-200 bg-slate-100 text-slate-700 dark:border-white/10 dark:bg-white/5 dark:text-slate-300'
                                    }`}
                                >
                                    Distribute to Members
                                </button>
                            </div>
                        </div>

                        <div className={theme.components.panel + ' p-3'}>
                            <div className="text-xs font-black uppercase tracking-widest text-slate-500 dark:text-slate-400">Distribution Total</div>
                            <div className={`mt-2 text-lg font-black ${orgPayoutMode === 'DISTRIBUTE_TO_MEMBERS' && orgShareTotal !== 100 ? 'text-red-600 dark:text-red-400' : 'text-slate-900 dark:text-white'}`}>{orgShareTotal}%</div>
                        </div>
                    </div>

                    {orgPayoutMode === 'DISTRIBUTE_TO_MEMBERS' && (
                        <div className="space-y-3">
                            {orgMembers.map((member) => (
                                <div key={member.userId} className={theme.components.panel + ' p-3'}>
                                    <div className="mb-2 flex items-start justify-between gap-3">
                                        <div>
                                            <div className="font-bold text-slate-900 dark:text-white">{member.username}</div>
                                            <div className="text-xs text-slate-500 dark:text-slate-400">Stripe: {member.stripeConnected ? (member.stripePayoutsEnabled ? 'Ready' : 'Connected, onboarding incomplete') : 'Not connected'}</div>
                                        </div>
                                        <div className="text-sm font-bold text-slate-600 dark:text-slate-300">{orgShares[member.userId] || 0}%</div>
                                    </div>
                                    <input
                                        type="number"
                                        min="0"
                                        max="100"
                                        step="1"
                                        value={orgShares[member.userId] || 0}
                                        onChange={(e) => setOrgShares(prev => ({ ...prev, [member.userId]: Number(e.target.value) }))}
                                        className={inputNoNativeUi + ' mt-2 w-24'}
                                    />
                                </div>
                            ))}
                        </div>
                    )}

                    <button onClick={saveOrgPayoutPolicy} disabled={savingOrgPolicy} className={theme.components.buttonPrimary}>
                        {savingOrgPolicy ? 'Saving...' : 'Save Organization Payout Policy'}
                    </button>
                </div>
            )}

            <div className={theme.components.panel + ' space-y-4 p-5'}>
                <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                    <div>
                        <h2 className="text-xl font-black text-slate-900 dark:text-white">Stripe Payouts</h2>
                        <p className="text-sm text-slate-500 dark:text-slate-400">Payouts run through Stripe Connect. Refresh status after onboarding.</p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        <button onClick={handleConnectStripe} disabled={!!busyAction || !data?.onboardingEnabled || (!data?.stripeConnected && !onboardingCountry)} className={theme.components.buttonPrimary}>Connect / Continue Stripe</button>
                        <button onClick={handleRefreshStripe} disabled={!!busyAction || !data?.stripeConnected} className={theme.components.buttonSecondary}><RefreshCw className="h-4 w-4" />Refresh Stripe Status</button>
                    </div>
                </div>

                {!data?.stripeConnected && <label className="block text-sm font-bold text-slate-700 dark:text-slate-200">Country of the payout account owner
                    <select value={onboardingCountry} disabled={!data?.onboardingEnabled || !!busyAction} onChange={event => setOnboardingCountry(event.target.value)} className={theme.components.inputField + ' mt-2'}><option value="">Choose country</option>{(data?.onboardingCountries || []).map((country: string) => <option key={country} value={country}>{new Intl.DisplayNames(undefined, { type: 'region' }).of(country) || country}</option>)}</select>
                    <span className="mt-2 block text-xs font-normal text-slate-500 dark:text-slate-400">Available countries depend on Stripe approval, identity verification, and local payout requirements. Use the legal account owner’s country.</span>
                </label>}
                <div className="grid grid-cols-1 gap-3 text-sm md:grid-cols-3">
                    <div className={theme.components.panel + ' p-3'}><div className="text-[10px] font-black uppercase tracking-widest text-slate-500 dark:text-slate-400">Connected</div><div className="mt-1 font-bold text-slate-900 dark:text-white">{data?.stripeConnected ? 'Yes' : 'No'}</div></div>
                    <div className={theme.components.panel + ' p-3'}><div className="text-[10px] font-black uppercase tracking-widest text-slate-500 dark:text-slate-400">Onboarding Complete</div><div className="mt-1 font-bold text-slate-900 dark:text-white">{data?.stripeOnboardingComplete ? 'Yes' : 'No'}</div></div>
                    <div className={theme.components.panel + ' p-3'}><div className="text-[10px] font-black uppercase tracking-widest text-slate-500 dark:text-slate-400">Payouts Enabled</div><div className="mt-1 font-bold text-slate-900 dark:text-white">{data?.stripePayoutsEnabled ? 'Yes' : 'No'}</div></div>
                </div>

                <div className="grid grid-cols-1 gap-3 md:grid-cols-[1fr_auto] md:items-end">
                    <div>
                        <label className="mb-2 block text-xs font-black uppercase tracking-widest text-slate-500 dark:text-slate-400">Payout Amount ({currency})</label>
                        <input
                            type="number"
                            min="0"
                            step="0.01"
                            value={payoutAmount}
                            onChange={(e) => setPayoutAmount(e.target.value)}
                            placeholder="Leave empty to payout full available balance"
                            className={inputNoNativeUi}
                        />
                    </div>
                    <button onClick={handleRequestPayout} disabled={!!busyAction || !data?.withdrawalsEnabled || data?.payoutHold || Number(availableCents) < Number(data?.minPayoutCents || 1000)} className={theme.components.buttonPrimary + ' h-[46px]'}>
                        <CreditCard className="h-4 w-4" /> Request Payout
                    </button>
                </div>
                <p className="text-xs text-slate-500 dark:text-slate-400">Minimum payout: {formatMoney(data?.minPayoutCents || 1000)}</p>
            </div>

            <section className={theme.components.panel + ' p-5'} aria-labelledby="payout-history-title">
                <h2 id="payout-history-title" className="text-xl font-black text-slate-900 dark:text-white">Transfer history</h2>
                <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Funds are reserved while a transfer is queued. Transfer completion means funds reached the payout provider, not necessarily your bank.</p>
                {(data?.payoutRequests || []).length === 0 ? <p className="mt-4 text-sm text-slate-500 dark:text-slate-400">No transfers requested yet.</p> : <div className="mt-4 space-y-3">{data.payoutRequests.map((request: any) => <div key={request.id} className="rounded-xl border border-slate-200 p-3 dark:border-white/10"><div className="flex flex-wrap items-center justify-between gap-2"><span className="font-bold text-slate-900 dark:text-white">{formatMoney(request.amountCents)}</span><span className="text-xs font-bold text-slate-600 dark:text-slate-300">{String(request.status).replaceAll('_', ' ')}</span></div><p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{new Date(request.createdAt).toLocaleString()}</p>{request.reviewReason && <p className="mt-2 text-sm text-amber-700 dark:text-amber-300">{request.reviewReason} The reserved balance has not been released for another withdrawal.</p>}</div>)}</div>}
            </section>
            {!isOrgContext && <SupportSubscriptions />}
        </div>
    );
};
