import React, { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, BadgeDollarSign, Building2, CalendarClock, ChevronDown, CreditCard, RefreshCw, Wallet } from 'lucide-react';
import { financeClient } from '@/modules/finance/api/financeClient';
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
    const isOrgContext = data?.ownerAccountType === 'ORGANIZATION';

    const formatMoney = (cents: number) => new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: currency.length === 3 ? currency : 'USD'
    }).format((cents || 0) / 100);

    const loadContexts = async () => {
        const available = await financeClient.getFinanceContexts();
        setContexts(Array.isArray(available) ? available : []);
        if (!selectedOwnerId && Array.isArray(available) && available.length > 0) setSelectedOwnerId(available[0].id);
    };

    const load = async (selectedRange = range, ownerId = selectedOwnerId) => {
        if (!ownerId) return;
        setLoading(true);
        try {
            const overview = await financeClient.getCreatorOverview(selectedRange, ownerId);
            setData(overview);

            if (overview?.ownerAccountType === 'ORGANIZATION') {
                const policy = await financeClient.getOrgPayoutPolicy(ownerId);
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
            setStatus({ type: 'error', title: 'Load Failed', msg: e?.response?.data || 'Could not load finance data.' });
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadContexts().catch(() => setStatus({ type: 'error', title: 'Load Failed', msg: 'Could not load finance contexts.' }));
    }, []);

    useEffect(() => {
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
        try {
            const res = await financeClient.createStripeOnboardingLink('/dashboard/finance', selectedOwnerId || undefined);
            if (res?.onboardingUrl) window.open(res.onboardingUrl, '_blank', 'noopener,noreferrer');
            setStatus({ type: 'info', title: 'Stripe Onboarding', msg: 'Stripe opened in a new tab. Complete it, then click "Refresh Stripe Status".' });
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Stripe Error', msg: e?.response?.data || 'Could not start Stripe onboarding.' });
        }
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
        try {
            const parsed = payoutAmount.trim() ? Math.round(Number(payoutAmount) * 100) : undefined;
            const res = await financeClient.requestPayout(parsed, selectedOwnerId || undefined);
            setStatus({ type: 'success', title: 'Payout Requested', msg: `Requested ${formatMoney(res.amountCents || 0)} payout.` });
            setPayoutAmount('');
            await load(range, selectedOwnerId);
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Payout Failed', msg: e?.response?.data || 'Could not request payout.' });
        }
    };

    const saveOrgPayoutPolicy = async () => {
        if (!isOrgContext) return;
        const shares = Object.entries(orgShares)
            .map(([userId, percent]) => ({ userId, percent: Math.max(0, Math.round(percent || 0)) }))
            .filter(item => item.percent > 0);

        const total = shares.reduce((sum, item) => sum + item.percent, 0);
        if (orgPayoutMode === 'DISTRIBUTE_TO_MEMBERS' && total < 100) {
            setStatus({ type: 'warning', title: 'Invalid Distribution', msg: 'Distributed payout shares must total at least 100%.' });
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

    const selectedContext = contexts.find(ctx => ctx.id === selectedOwnerId);
    const orgShareTotal = Object.values(orgShares).reduce((sum, n) => sum + Math.max(0, Math.round(Number(n || 0))), 0);
    const ranges = ['30d', '90d', '1y'];
    const activeRangeIndex = ranges.indexOf(range);

    return (
        <div className="space-y-6">
            {status && <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} />}

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

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryCard title="Available" value={formatMoney(data?.availableCents || 0)} subtitle="Ready to payout" icon={Wallet} color="text-emerald-500" />
                <SummaryCard title="Pending" value={formatMoney(data?.pendingCents || 0)} subtitle="Awaiting settlement" icon={CalendarClock} color="text-amber-500" />
                <SummaryCard title="Expiring Soon" value={formatMoney(data?.expiringSoonCents || 0)} subtitle="Within 30 days" icon={AlertTriangle} color="text-red-500" />
                <SummaryCard title="Paid Out" value={formatMoney(data?.paidOutCents || 0)} subtitle="Lifetime payouts" icon={BadgeDollarSign} color="text-blue-500" />
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
                        <button onClick={handleConnectStripe} className={theme.components.buttonPrimary}>Connect / Continue Stripe</button>
                        <button onClick={handleRefreshStripe} className={theme.components.buttonSecondary}><RefreshCw className="h-4 w-4" />Refresh Stripe Status</button>
                    </div>
                </div>

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
                    <button onClick={handleRequestPayout} className={theme.components.buttonPrimary + ' h-[46px]'}>
                        <CreditCard className="h-4 w-4" /> Request Payout
                    </button>
                </div>
                <p className="text-xs text-slate-500 dark:text-slate-400">Minimum payout: {formatMoney(data?.minPayoutCents || 1000)}</p>
            </div>

        </div>
    );
};
