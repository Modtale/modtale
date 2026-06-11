import React, { useEffect, useMemo, useState } from 'react';
import { Building2, Check, Coins, HandCoins, Play, Save, Square, TestTube2 } from 'lucide-react';
import { financeClient } from '@/modules/finance/api/financeClient';
import { LineChart } from '@/components/ui/charts/LineChart';
import { StatusModal } from '@/components/ui/StatusModal';
import { theme } from '@/styles/theme';

const inputNoNativeUi = `${theme.components.inputField} appearance-none [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none`;

const Card = ({ title, value, icon: Icon, color }: any) => (
    <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 shadow-sm hover:shadow-md transition-all relative overflow-hidden group backdrop-blur-md flex flex-col justify-between p-6">
        <div className="mb-2 flex items-center justify-between">
            <div className={`p-3 rounded-2xl ${color} bg-opacity-10 shadow-inner`}>
                <Icon className={`w-6 h-6 ${color}`} />
            </div>
            <div className="text-[10px] font-black uppercase tracking-wider text-slate-500 dark:text-slate-400">{title}</div>
        </div>
        <div className="text-3xl md:text-4xl font-black text-slate-900 dark:text-white tracking-tighter leading-none">{value}</div>
    </div>
);

interface FinanceAdminProps {
    isSuperAdmin: boolean;
}

export function FinanceAdmin({ isSuperAdmin }: FinanceAdminProps) {
    const defaultCreatives = [
        { placement: 'SIDEBAR_CARD', imageUrl: '' },
        { placement: 'WIDE_BANNER', imageUrl: '' },
        { placement: 'TALL_BANNER', imageUrl: '' }
    ];
    const [range, setRange] = useState('30d');
    const [loading, setLoading] = useState(true);
    const [data, setData] = useState<any>(null);
    const [status, setStatus] = useState<{ type: 'success' | 'error' | 'warning' | 'info'; title: string; msg: string } | null>(null);
    const [campaigns, setCampaigns] = useState<any[]>([]);
    const [testProjectId, setTestProjectId] = useState('');
    const [testPlacement, setTestPlacement] = useState<'SIDEBAR_CARD' | 'WIDE_BANNER' | 'TALL_BANNER'>('SIDEBAR_CARD');
    const [testAdResult, setTestAdResult] = useState<any>(null);

    const [settings, setSettings] = useState({
        defaultAdRevenuePerClickCents: 3,
        minPayoutCents: 1000
    });

    const [draftCampaign, setDraftCampaign] = useState({
        id: '',
        name: '',
        sponsorName: '',
        headline: '',
        body: '',
        callToAction: 'Learn more',
        targetUrl: '',
        imageUrl: '',
        creatives: defaultCreatives,
        affiliateCode: '',
        baseRevenuePerClickCents: 3,
        weight: 100,
        testCampaign: false,
        active: true
    });

    const currency = (data?.currency || 'usd').toUpperCase();

    const formatMoney = (cents: number) => new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: currency.length === 3 ? currency : 'USD'
    }).format((cents || 0) / 100);

    const loadCampaigns = async () => {
        if (!isSuperAdmin) return;
        try {
            const rows = await financeClient.getAdCampaigns();
            setCampaigns(rows || []);
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Campaign Load Failed', msg: e?.response?.data || 'Could not load ad campaigns.' });
        }
    };

    const load = async (selectedRange = range) => {
        setLoading(true);
        try {
            const overview = await financeClient.getAdminOverview(selectedRange);
            setData(overview);
            setSettings({
                defaultAdRevenuePerClickCents: Number(overview?.defaultAdRevenuePerClickCents || 3),
                minPayoutCents: Number(overview?.minPayoutCents || 1000)
            });
            await loadCampaigns();
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Load Failed', msg: e?.response?.data || 'Could not load finance admin data.' });
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load(range);
    }, [range]);

    const chartDatasets = useMemo(() => {
        const mapSeries = (series: any[]) => (series || []).map(point => ({ date: point.date, value: Number(point.count || 0) }));
        return {
            platform: [{ id: 'platform', label: 'Platform Revenue', color: '#2563eb', data: mapSeries(data?.platformRevenueChart) }],
            creator: [{ id: 'creator', label: 'Creator Revenue', color: '#16a34a', data: mapSeries(data?.creatorRevenueChart) }]
        };
    }, [data]);

    const saveSettings = async () => {
        try {
            const payload = {
                defaultAdRevenuePerClickCents: Math.max(0, Math.round(settings.defaultAdRevenuePerClickCents)),
                minPayoutCents: Math.max(100, Math.round(settings.minPayoutCents))
            };
            await financeClient.updateAdminSettings(payload);
            setStatus({ type: 'success', title: 'Settings Saved', msg: 'Platform finance settings updated.' });
            await load(range);
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Save Failed', msg: e?.response?.data || 'Could not save finance settings.' });
        }
    };

    const populateDraft = (campaign: any) => {
        const incomingCreatives = Array.isArray(campaign.creatives) ? campaign.creatives : [];
        const mergedCreatives = defaultCreatives.map((base) => {
            const found = incomingCreatives.find((c: any) => c?.placement === base.placement);
            return { placement: base.placement, imageUrl: found?.imageUrl || '' };
        });
        setDraftCampaign({
            id: campaign.id || '',
            name: campaign.name || '',
            sponsorName: campaign.sponsorName || '',
            headline: campaign.headline || '',
            body: campaign.body || '',
            callToAction: campaign.callToAction || 'Learn more',
            targetUrl: campaign.targetUrl || '',
            imageUrl: campaign.imageUrl || '',
            creatives: mergedCreatives,
            affiliateCode: campaign.affiliateCode || '',
            baseRevenuePerClickCents: Number(campaign.baseRevenuePerClickCents || 0),
            weight: Number(campaign.weight || 100),
            testCampaign: !!campaign.testCampaign,
            active: !!campaign.active
        });
    };

    const resetDraft = () => {
        setDraftCampaign({
            id: '',
            name: '',
            sponsorName: '',
            headline: '',
            body: '',
            callToAction: 'Learn more',
            targetUrl: '',
            imageUrl: '',
            creatives: defaultCreatives,
            affiliateCode: '',
            baseRevenuePerClickCents: 3,
            weight: 100,
            testCampaign: false,
            active: true
        });
    };

    const saveCampaign = async () => {
        try {
            const payload = {
                name: draftCampaign.name,
                sponsorName: draftCampaign.sponsorName,
                headline: draftCampaign.headline,
                body: draftCampaign.body,
                callToAction: draftCampaign.callToAction,
                targetUrl: draftCampaign.targetUrl,
                imageUrl: draftCampaign.imageUrl,
                creatives: (draftCampaign as any).creatives,
                affiliateCode: draftCampaign.affiliateCode,
                baseRevenuePerClickCents: Math.max(0, Math.round(draftCampaign.baseRevenuePerClickCents)),
                weight: Math.max(1, Math.round(draftCampaign.weight)),
                testCampaign: draftCampaign.testCampaign,
                active: draftCampaign.active,
                providerType: 'CUSTOM_AFFILIATE',
                privacyRespecting: true,
                nonIntrusive: true
            };

            if (draftCampaign.id) {
                await financeClient.updateAdCampaign(draftCampaign.id, payload);
                setStatus({ type: 'success', title: 'Campaign Updated', msg: 'Ad campaign updated successfully.' });
            } else {
                await financeClient.createAdCampaign(payload);
                setStatus({ type: 'success', title: 'Campaign Created', msg: 'Ad campaign started successfully.' });
            }
            resetDraft();
            await loadCampaigns();
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Campaign Save Failed', msg: e?.response?.data || 'Could not save campaign.' });
        }
    };

    const toggleCampaignState = async (campaignId: string, active: boolean) => {
        try {
            if (active) await financeClient.startAdCampaign(campaignId);
            else await financeClient.pauseAdCampaign(campaignId);
            await loadCampaigns();
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Campaign Update Failed', msg: e?.response?.data || 'Could not update campaign state.' });
        }
    };

    const runTestAd = async () => {
        if (!testProjectId.trim()) {
            setStatus({ type: 'warning', title: 'Project Required', msg: 'Enter a project id to run a test ad campaign.' });
            return;
        }

        try {
            const res = await financeClient.getTestAdSlot(testProjectId.trim(), testPlacement);
            setTestAdResult(res);
            setStatus({ type: 'info', title: 'Test Ad Loaded', msg: res?.enabled ? 'Test ad slot resolved.' : `No test ad returned (${res?.reason || 'unknown reason'}).` });
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Test Ad Failed', msg: e?.response?.data || 'Could not run test ad slot.' });
        }
    };

    if (loading) return <div className="py-16 text-center font-bold text-slate-500 dark:text-slate-400">Loading finance administration...</div>;

    return (
        <div className="space-y-6">
            {status && <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} />}

            <div className={theme.components.panel + ' p-5'}>
                <h1 className="text-3xl font-black tracking-tight text-slate-900 dark:text-white">Platform Finance</h1>
                <p className="mt-1 text-slate-500 dark:text-slate-400">Revenue attribution, creator payouts, and monetization controls.</p>
            </div>

            <div className="flex w-fit items-center gap-1 rounded-xl border border-slate-200 bg-slate-100 p-1 dark:border-white/10 dark:bg-white/5">
                {['30d', '90d', '1y'].map(option => (
                    <button
                        key={option}
                        onClick={() => setRange(option)}
                        className={`rounded-lg px-3 py-1.5 text-xs font-bold ${range === option ? 'bg-modtale-accent text-white' : 'text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white'}`}
                    >
                        {option}
                    </button>
                ))}
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                <Card title="Platform Revenue" value={formatMoney(data?.periodPlatformRevenueCents || 0)} icon={Coins} color="text-blue-500" />
                <Card title="Creator Revenue" value={formatMoney(data?.periodCreatorRevenueCents || 0)} icon={HandCoins} color="text-emerald-500" />
                <Card title="Payouts" value={formatMoney(data?.periodPayoutsCents || 0)} icon={Building2} color="text-purple-500" />
                <Card title="Creator Available" value={formatMoney(data?.totalCreatorAvailableCents || 0)} icon={Coins} color="text-amber-500" />
            </div>

            <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
                <div className={theme.components.panel + ' p-5'}>
                    <h3 className="mb-4 text-sm font-black uppercase tracking-wide text-slate-600 dark:text-slate-300">Platform Daily Revenue</h3>
                    <div className="h-[320px]"><LineChart datasets={chartDatasets.platform} /></div>
                </div>
                <div className={theme.components.panel + ' p-5'}>
                    <h3 className="mb-4 text-sm font-black uppercase tracking-wide text-slate-600 dark:text-slate-300">Creator Daily Revenue</h3>
                    <div className="h-[320px]"><LineChart datasets={chartDatasets.creator} /></div>
                </div>
            </div>

            <div className={theme.components.panel + ' space-y-4 p-5'}>
                <h3 className="text-lg font-black text-slate-900 dark:text-white">Platform Settings</h3>
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                    <label className={theme.components.panel + ' p-3'}>
                        <div className="text-xs font-black uppercase tracking-widest text-slate-500 dark:text-slate-400">Default Ad Revenue / Click (cents)</div>
                        <input type="number" min="0" step="1" value={settings.defaultAdRevenuePerClickCents} onChange={(e) => setSettings(prev => ({ ...prev, defaultAdRevenuePerClickCents: Number(e.target.value) }))} className={inputNoNativeUi + ' mt-2'} />
                    </label>

                    <label className={theme.components.panel + ' p-3'}>
                        <div className="text-xs font-black uppercase tracking-widest text-slate-500 dark:text-slate-400">Minimum Payout (cents)</div>
                        <input type="number" min="100" step="100" value={settings.minPayoutCents} onChange={(e) => setSettings(prev => ({ ...prev, minPayoutCents: Number(e.target.value) }))} className={inputNoNativeUi + ' mt-2'} />
                    </label>
                </div>

                <button onClick={saveSettings} className={theme.components.buttonPrimary}><Save className="h-4 w-4" /> Save Finance Settings</button>
            </div>

            {isSuperAdmin ? (
                <>
                    <div className={theme.components.panel + ' space-y-4 p-5'}>
                        <h3 className="text-lg font-black text-slate-900 dark:text-white">Start Ad Campaign</h3>
                        <p className="text-sm text-slate-500 dark:text-slate-400">Configure campaign content and delivery. Delivery weight controls how often this campaign is selected relative to other active campaigns.</p>

                        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                            <input value={draftCampaign.name} onChange={(e) => setDraftCampaign(prev => ({ ...prev, name: e.target.value }))} placeholder="Campaign name" className={theme.components.inputField} />
                            <input value={draftCampaign.sponsorName} onChange={(e) => setDraftCampaign(prev => ({ ...prev, sponsorName: e.target.value }))} placeholder="Sponsor" className={theme.components.inputField} />
                            <input value={draftCampaign.headline} onChange={(e) => setDraftCampaign(prev => ({ ...prev, headline: e.target.value }))} placeholder="Headline" className={theme.components.inputField} />
                            <input value={draftCampaign.callToAction} onChange={(e) => setDraftCampaign(prev => ({ ...prev, callToAction: e.target.value }))} placeholder="Call to action" className={theme.components.inputField} />
                            <input value={draftCampaign.targetUrl} onChange={(e) => setDraftCampaign(prev => ({ ...prev, targetUrl: e.target.value }))} placeholder="Target URL" className={theme.components.inputField} />
                            <input value={draftCampaign.affiliateCode} onChange={(e) => setDraftCampaign(prev => ({ ...prev, affiliateCode: e.target.value }))} placeholder="Affiliate code" className={theme.components.inputField} />
                            <input type="number" min="0" value={draftCampaign.baseRevenuePerClickCents} onChange={(e) => setDraftCampaign(prev => ({ ...prev, baseRevenuePerClickCents: Number(e.target.value) }))} placeholder="Revenue per click (cents)" className={inputNoNativeUi} />
                        </div>

                        <div className={theme.components.panel + ' p-3 space-y-2'}>
                            <div className="text-xs font-black uppercase tracking-widest text-slate-500 dark:text-slate-400">Creative Images By Placement</div>
                            {(draftCampaign as any).creatives.map((creative: any, idx: number) => (
                                <div key={creative.placement} className="grid grid-cols-1 md:grid-cols-[180px_1fr] gap-2 items-center">
                                    <div className="text-xs font-bold text-slate-600 dark:text-slate-300">
                                        {creative.placement === 'SIDEBAR_CARD' ? 'Sidebar Card' : creative.placement === 'WIDE_BANNER' ? 'Wide Banner' : 'Tall Banner'}
                                    </div>
                                    <input
                                        value={creative.imageUrl}
                                        onChange={(e) => setDraftCampaign(prev => {
                                            const next = [...(prev as any).creatives];
                                            next[idx] = { ...next[idx], imageUrl: e.target.value };
                                            return { ...prev, creatives: next };
                                        })}
                                        placeholder="Image URL"
                                        className={theme.components.inputField}
                                    />
                                </div>
                            ))}
                            <p className="text-xs text-slate-500 dark:text-slate-400">Use different assets for each placement type. Empty fields fall back to the campaign legacy image.</p>
                        </div>

                        <div className={theme.components.panel + ' p-3'}>
                            <div className="mb-1 flex items-center justify-between text-xs font-black uppercase tracking-wide text-slate-500 dark:text-slate-400">
                                <span>Delivery Weight</span>
                                <span className="text-slate-700 dark:text-slate-300">{draftCampaign.weight}</span>
                            </div>
                            <input
                                type="number"
                                min="1"
                                max="500"
                                step="1"
                                value={draftCampaign.weight}
                                onChange={(e) => setDraftCampaign(prev => ({ ...prev, weight: Number(e.target.value) }))}
                                className={inputNoNativeUi}
                            />
                            <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">Higher values make this campaign appear more often compared with others.</p>
                        </div>

                        <label className={theme.components.panel + ' flex items-center justify-between px-3 py-2.5'}>
                            <span className="text-sm font-bold text-slate-700 dark:text-slate-300">Test Campaign</span>
                            <button
                                type="button"
                                onClick={() => setDraftCampaign(prev => ({ ...prev, testCampaign: !prev.testCampaign }))}
                                className={`inline-flex items-center rounded-full border px-2 py-1 text-[10px] font-black uppercase tracking-wider transition-colors ${
                                    draftCampaign.testCampaign
                                        ? 'border-emerald-300 bg-emerald-50 text-emerald-700 dark:border-emerald-500/30 dark:bg-emerald-500/15 dark:text-emerald-300'
                                        : 'border-slate-300 bg-slate-100 text-slate-600 dark:border-white/20 dark:bg-white/5 dark:text-slate-400'
                                }`}
                            >
                                {draftCampaign.testCampaign ? <Check className="mr-1 h-3 w-3" /> : null}
                                {draftCampaign.testCampaign ? 'On' : 'Off'}
                            </button>
                        </label>

                        <textarea value={draftCampaign.body} onChange={(e) => setDraftCampaign(prev => ({ ...prev, body: e.target.value }))} placeholder="Ad body" rows={3} className={theme.components.inputField} />
                        <div className="flex gap-2">
                            <button onClick={saveCampaign} className={theme.components.buttonPrimary}><Play className="h-4 w-4" />{draftCampaign.id ? 'Update Campaign' : 'Start Campaign'}</button>
                            <button onClick={resetDraft} className={theme.components.buttonSecondary}>Clear</button>
                        </div>
                    </div>

                    <div className={theme.components.panel + ' space-y-4 p-5'}>
                        <h3 className="text-lg font-black text-slate-900 dark:text-white">Active Campaigns</h3>
                        <div className="space-y-2">
                            {campaigns.map((campaign: any) => (
                                <div key={campaign.id} className={theme.components.panel + ' flex flex-col gap-3 p-3 md:flex-row md:items-center md:justify-between'}>
                                    <div>
                                        <div className="font-black text-slate-900 dark:text-white">{campaign.name}</div>
                                        <div className="text-xs text-slate-500 dark:text-slate-400">{campaign.testCampaign ? 'Test Campaign' : 'Live Campaign'} · {campaign.active ? 'Running' : 'Paused'} · Weight {campaign.weight}</div>
                                    </div>
                                    <div className="flex gap-2">
                                        <button onClick={() => populateDraft(campaign)} className={theme.components.buttonSecondary + ' px-3 py-1.5 text-xs'}>Edit</button>
                                        {campaign.active ? (
                                            <button onClick={() => toggleCampaignState(campaign.id, false)} className="inline-flex items-center gap-1 rounded-lg bg-amber-600 px-3 py-1.5 text-xs font-bold text-white"><Square className="h-3 w-3" />Pause</button>
                                        ) : (
                                            <button onClick={() => toggleCampaignState(campaign.id, true)} className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-bold text-white"><Play className="h-3 w-3" />Start</button>
                                        )}
                                    </div>
                                </div>
                            ))}
                            {campaigns.length === 0 && <div className="text-sm text-slate-500 dark:text-slate-400">No campaigns found.</div>}
                        </div>
                    </div>

                    <div className={theme.components.panel + ' space-y-4 p-5'}>
                        <h3 className="text-lg font-black text-slate-900 dark:text-white">Run Test Campaign Slot</h3>
                        <div className="grid grid-cols-1 gap-3 md:grid-cols-[1fr_auto] md:items-end">
                            <div>
                                <label className="mb-2 block text-xs font-black uppercase tracking-widest text-slate-500 dark:text-slate-400">Project ID</label>
                                <input value={testProjectId} onChange={(e) => setTestProjectId(e.target.value)} placeholder="Target project id" className={theme.components.inputField} />
                                <div className="mt-2 flex gap-2">
                                    {[
                                        { id: 'SIDEBAR_CARD', label: 'Sidebar' },
                                        { id: 'WIDE_BANNER', label: 'Wide Banner' },
                                        { id: 'TALL_BANNER', label: 'Tall Banner' }
                                    ].map((item) => (
                                        <button
                                            key={item.id}
                                            type="button"
                                            onClick={() => setTestPlacement(item.id as any)}
                                            className={`rounded-lg px-3 py-1.5 text-xs font-bold border ${
                                                testPlacement === item.id
                                                    ? 'border-modtale-accent bg-modtale-accent text-white'
                                                    : 'border-slate-200 bg-slate-100 text-slate-700 dark:border-white/10 dark:bg-white/5 dark:text-slate-300'
                                            }`}
                                        >
                                            {item.label}
                                        </button>
                                    ))}
                                </div>
                            </div>
                            <button onClick={runTestAd} className={theme.components.buttonPrimary + ' h-[46px]'}><TestTube2 className="h-4 w-4" />Run Test Slot</button>
                        </div>
                        {testAdResult && <pre className="overflow-auto rounded-xl border border-slate-200 bg-slate-100 p-3 text-xs dark:border-white/10 dark:bg-white/5">{JSON.stringify(testAdResult, null, 2)}</pre>}
                    </div>
                </>
            ) : (
                <div className={theme.components.panel + ' p-5'}>
                    <h3 className="text-lg font-black text-slate-900 dark:text-white">Ad Campaign Control</h3>
                    <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">Only super admins can start or test ad campaigns.</p>
                </div>
            )}

            <div className={theme.components.panel + ' p-5'}>
                <h3 className="mb-4 text-lg font-black text-slate-900 dark:text-white">Top Creators ({range})</h3>
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="border-b border-slate-200 text-left text-slate-500 dark:border-white/10 dark:text-slate-400">
                                <th className="py-2 pr-4">Creator</th>
                                <th className="py-2 pr-4">Revenue</th>
                            </tr>
                        </thead>
                        <tbody>
                            {(data?.topCreators || []).map((creator: any) => (
                                <tr key={creator.creatorId} className="border-b border-slate-100 dark:border-white/5">
                                    <td className="py-2 pr-4 font-bold text-slate-900 dark:text-white">{creator.username}</td>
                                    <td className="py-2 pr-4 font-mono text-slate-700 dark:text-slate-300">{formatMoney(creator.revenueCents)}</td>
                                </tr>
                            ))}
                            {(data?.topCreators || []).length === 0 && (
                                <tr>
                                    <td className="py-4 text-slate-500 dark:text-slate-400" colSpan={2}>No creator revenue in this range.</td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
