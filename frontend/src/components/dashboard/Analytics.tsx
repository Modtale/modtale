import React, { useEffect, useState, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    BarChart2, Star, PieChart, ChevronDown, Check,
    User as UserIcon, Building2, ArrowLeft, Activity,
    Download, Eye, TrendingUp, TrendingDown, Layers
} from 'lucide-react';

import { api } from '../../utils/api.ts';
import { EmptyState } from '../ui/EmptyState.tsx';
import { LineChart } from '../ui/charts/LineChart.tsx';
import { BarChart } from '../ui/charts/BarChart.tsx';
import { COLORS, OVERALL_COLOR, BUFFER, sliceData, calculateWoW } from '../../utils/analytics.ts';
import type { Mod, User } from '../../types.ts';

const SummaryCard = ({ title, value, subValue, trend, icon: Icon, color, isPercent }: any) => (
    <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 shadow-sm hover:shadow-md transition-all relative overflow-hidden group backdrop-blur-md flex flex-col justify-between p-6">
        <div className={`absolute top-0 right-0 p-4 opacity-5 group-hover:opacity-10 transition-opacity ${color}`}>
            <Icon className="w-32 h-32 transform translate-x-8 -translate-y-8" />
        </div>
        <div className="relative z-10 flex items-start justify-between mb-2">
            <div className={`p-3 rounded-2xl ${color} bg-opacity-10 text-current shadow-inner`}>
                <Icon className="w-6 h-6" />
            </div>
            {trend !== undefined && (
                <div className={`flex items-center gap-1 text-[11px] font-black uppercase tracking-wider px-3 py-1 rounded-full border ${trend >= 0 ? 'bg-green-50 border-green-200 text-green-700 dark:bg-green-500/10 dark:border-green-500/30 dark:text-green-400' : 'bg-red-50 border-red-200 text-red-700 dark:bg-red-500/10 dark:border-red-500/30 dark:text-red-400'}`}>
                    {trend >= 0 ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                    {Math.abs(trend).toFixed(1)}%
                </div>
            )}
        </div>
        <div className="relative z-10 mt-4">
            <h3 className="text-slate-500 dark:text-slate-400 text-[10px] font-black uppercase tracking-widest mb-1">{title}</h3>
            <div className="text-3xl md:text-4xl font-black text-slate-900 dark:text-white tracking-tighter leading-none">
                {value}{isPercent && <span className="text-2xl text-slate-400 ml-1">%</span>}
            </div>
            {subValue && <div className="text-xs text-slate-500 dark:text-slate-400 mt-2 font-medium">{subValue}</div>}
        </div>
    </div>
);

export const Analytics: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const [loading, setLoading] = useState(true);
    const [range, setRange] = useState('30d');
    const [hasProjects, setHasProjects] = useState(true);
    const [hiddenSeries, setHiddenSeries] = useState<Record<string, boolean>>({});
    const [hiddenGrowth, setHiddenGrowth] = useState<Record<string, boolean>>({});

    const [currentUser, setCurrentUser] = useState<User | null>(null);
    const [myOrgs, setMyOrgs] = useState<User[]>([]);
    const [selectedContext, setSelectedContext] = useState<string>('');
    const [contextDropdownOpen, setContextDropdownOpen] = useState(false);
    const dropdownRef = useRef<HTMLDivElement>(null);

    const [meta, setMeta] = useState<{ title: string; subtitle: string }>({ title: '', subtitle: '' });
    const [summary, setSummary] = useState<any>(null);
    const [seriesData, setSeriesData] = useState<Record<string, any[]>>({});
    const [viewsData, setViewsData] = useState<Record<string, any[]>>({});
    const [items, setItems] = useState<string[]>([]);
    const [itemMeta, setItemMeta] = useState<Record<string, any>>({});
    const [fourthChart, setFourthChart] = useState<any>(null);
    const [tableConfig, setTableConfig] = useState<{ headers: string[], rowRenderer: (id: string, stats: any) => React.ReactNode } | null>(null);

    useEffect(() => {
        const init = async () => {
            try {
                const me = await api.get('/user/me');
                setCurrentUser(me.data);

                setSelectedContext(prev => prev || me.data.id);

                const orgs = await api.get('/user/orgs');
                const adminOrgs = orgs.data.filter((o: User) =>
                    o.organizationMembers?.some(m => m.userId === me.data.id && m.role === 'ADMIN')
                );
                setMyOrgs(adminOrgs);
            } catch (e) { console.error("Init failed", e); }
        };
        if (!id) init();

        const handleClickOutside = (event: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
                setContextDropdownOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [id]);

    useEffect(() => {
        const fetchData = async () => {
            if (!id && !selectedContext) return;
            setLoading(true);

            setSummary(null);
            setSeriesData({});
            setViewsData({});
            setItems([]);
            setFourthChart(null);
            setHasProjects(true);

            try {
                if (!id) {
                    try {
                        const p = await api.get(`/creators/${selectedContext}/projects?size=1`);
                        const hasP = p.data.totalElements > 0;
                        setHasProjects(hasP);
                        if (!hasP) {
                            setLoading(false);
                            return;
                        }
                    } catch (e) {
                        setHasProjects(false); setLoading(false); return;
                    }

                    const res = await api.get(`/user/analytics?range=${range}&userId=${selectedContext}`);
                    const data = res.data;

                    setMeta({
                        title: '',
                        subtitle: "Overall Performance & Reach"
                    });

                    setItems(Object.keys(data.projectMeta || {}).sort((a, b) => data.projectMeta[b].totalDownloads - data.projectMeta[a].totalDownloads));
                    setSeriesData(data.projectDownloads);
                    setViewsData(data.projectViews);
                    setItemMeta(data.projectMeta);

                    const conversionData = Object.keys(data.projectMeta).map(pid => {
                        const dl = (data.projectDownloads[pid] || []).slice(BUFFER).reduce((acc: number, d: any) => acc + d.count, 0);
                        const vw = (data.projectViews[pid] || []).slice(BUFFER).reduce((acc: number, d: any) => acc + d.count, 0);
                        return { id: pid, label: data.projectMeta[pid].title, value: vw > 0 ? (dl / vw) * 100 : 0 };
                    }).sort((a, b) => b.value - a.value);

                    setFourthChart({ title: "Conversion Rate (%)", icon: <PieChart className="w-5 h-5 text-orange-500" />, type: 'bar', data: conversionData, formatter: (v: number) => `${v.toFixed(1)}%` });

                    setSummary({
                        downloads: { value: data.periodDownloads, total: data.totalDownloads, trend: ((data.periodDownloads - data.previousPeriodDownloads) / (data.previousPeriodDownloads || 1)) * 100 },
                        views: { value: data.periodViews, total: data.totalViews, trend: ((data.periodViews - data.previousPeriodViews) / (data.previousPeriodViews || 1)) * 100 },
                        conversion: data.periodViews > 0 ? (data.periodDownloads / data.periodViews) * 100 : 0,
                        contentCount: { value: Object.keys(data.projectMeta).length, label: "Projects" }
                    });

                    setTableConfig({
                        headers: ["Project Name", "Period Downloads", "Total Downloads", "Rating", "Action"],
                        rowRenderer: (pid, sum) => (
                            <>
                                <td className="p-4 pl-6 font-bold text-slate-900 dark:text-white">{data.projectMeta[pid].title}</td>
                                <td className="p-4 text-slate-600 dark:text-slate-300 font-mono">+{sum.toLocaleString()}</td>
                                <td className="p-4 text-slate-600 dark:text-slate-300 font-mono">{data.projectMeta[pid].totalDownloads.toLocaleString()}</td>
                                <td className="p-4"><span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md border border-yellow-200 dark:border-yellow-500/30 bg-yellow-50 dark:bg-yellow-500/10 text-yellow-700 dark:text-yellow-500 font-bold text-xs"><Star className="w-3 h-3 fill-current" /> {data.projectMeta[pid].currentRating}</span></td>
                                <td className="p-4 text-right pr-6"><button onClick={() => navigate(`/dashboard/analytics/project/${pid}`)} className="text-slate-500 hover:text-modtale-accent font-bold text-xs border border-slate-200 dark:border-white/10 px-4 py-2 rounded-xl hover:border-modtale-accent transition-all bg-white/50 dark:bg-white/5 shadow-sm">Details</button></td>
                            </>
                        )
                    });

                } else {
                    const [analytics, info] = await Promise.all([
                        api.get(`/projects/${id}/analytics?range=${range}`),
                        api.get(`/projects/${id}`)
                    ]).then(r => [r[0].data, r[1].data as Mod]);

                    setMeta({ title: info.title, subtitle: "Project Performance & Reach" });

                    const vMap = new Map(info.versions.map((v: { id: any; }) => [v.id, v]));
                    setSeriesData(analytics.versionDownloads);
                    setViewsData({ 'overall': analytics.views });
                    setItems(Object.keys(analytics.versionDownloads).sort((a, b) => {
                        const sumA = analytics.versionDownloads[a].reduce((ac: number, x: any) => ac + x.count, 0);
                        const sumB = analytics.versionDownloads[b].reduce((ac: number, x: any) => ac + x.count, 0);
                        return sumB - sumA;
                    }));

                    const vMeta: Record<string, any> = {};
                    info.versions.forEach((v: { id: string | number; versionNumber: any; gameVersions: any[]; releaseDate: any; downloadCount: any; }) => vMeta[v.id] = {
                        label: v.versionNumber,
                        gameVer: v.gameVersions?.join(', ') || 'Unknown',
                        date: v.releaseDate,
                        total: v.downloadCount
                    });
                    setItemMeta(vMeta);

                    setFourthChart({ title: "Rating History", icon: <Star className="w-5 h-5 text-yellow-500" />, type: 'line', data: [{ id: 'avg_rating', label: 'Rating', color: '#f59e0b', data: analytics.ratingHistory }] });

                    setSummary({
                        downloads: { value: analytics.totalDownloads, total: info.downloadCount, trend: 0 },
                        views: { value: analytics.totalViews, total: analytics.totalViews, trend: 0 },
                        conversion: analytics.totalViews > 0 ? (analytics.totalDownloads / analytics.totalViews) * 100 : 0,
                        contentCount: { value: info.versions.length, label: "Versions" }
                    });

                    setTableConfig({
                        headers: ["Version", "Period Downloads", "Total Downloads", "Game Version", "Released"],
                        rowRenderer: (vid, sum) => (
                            <>
                                <td className="p-4 font-bold text-slate-900 dark:text-white flex items-center gap-2 pl-6">
                                    <span className={`w-2.5 h-2.5 rounded-full inline-block mr-2 shadow-sm`} style={{ backgroundColor: hiddenSeries[vid] ? '#cbd5e1' : COLORS[items.indexOf(vid) % COLORS.length] }} />
                                    {vMeta[vid]?.label || (vid === 'Unknown' ? 'Legacy' : vid.substring(0, 8))}
                                </td>
                                <td className="p-4 text-slate-600 dark:text-slate-300 font-mono">+{sum.toLocaleString()}</td>
                                <td className="p-4 text-slate-600 dark:text-slate-300 font-mono">{(vMeta[vid]?.total || 0).toLocaleString()}</td>
                                <td className="p-4 text-xs font-mono text-slate-500">{vMeta[vid]?.gameVer}</td>
                                <td className="p-4 text-right pr-6"><span className="text-xs font-bold bg-slate-200/50 dark:bg-white/10 px-2.5 py-1 rounded-md text-slate-500 border border-slate-200 dark:border-white/5">{vMeta[vid]?.date}</span></td>
                            </>
                        )
                    });
                }

                setHiddenSeries(prev => { const next = { ...prev, 'overall': false }; return next; });

            } catch (e) { console.error(e); } finally { setLoading(false); }
        };
        fetchData();
    }, [id, range, selectedContext]);

    const calculateOverall = (source: Record<string, any[]>) => {
        const firstKey = Object.keys(source)[0];
        if (!firstKey || !source[firstKey]) return [];
        return source[firstKey].map((_, idx) => {
            let sum = 0, date = source[firstKey][idx]?.date;
            Object.values(source).forEach(arr => { if (arr[idx]) sum += arr[idx].count; });
            return { date, value: sum };
        });
    };

    const ContextSwitcher = () => (
        <div className="relative" ref={dropdownRef}>
            <button
                onClick={() => setContextDropdownOpen(!contextDropdownOpen)}
                className="flex items-center gap-2 px-3 py-1.5 rounded-xl text-lg font-bold text-slate-700 dark:text-slate-200 hover:bg-slate-200/50 dark:hover:bg-white/10 transition-colors"
            >
                {selectedContext === currentUser?.id ? (
                    <><UserIcon className="w-5 h-5 text-blue-500" /> Personal</>
                ) : (
                    <><Building2 className="w-5 h-5 text-purple-500" /> {myOrgs.find(o => o.id === selectedContext)?.displayName || myOrgs.find(o => o.id === selectedContext)?.username || selectedContext}</>
                )}
                <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${contextDropdownOpen ? 'rotate-180' : ''}`} />
            </button>

            {contextDropdownOpen && (
                <div className="absolute left-0 top-full mt-2 w-64 bg-white/95 dark:bg-slate-900/95 backdrop-blur-xl border border-slate-200 dark:border-white/10 rounded-2xl shadow-2xl z-50 overflow-hidden animate-in fade-in zoom-in-95 duration-100">
                    <div className="p-2">
                        <button onClick={() => { setSelectedContext(currentUser?.id || ''); setContextDropdownOpen(false); }} className="w-full flex items-center gap-3 p-3 hover:bg-slate-100 dark:hover:bg-white/10 transition-colors text-left rounded-xl group">
                            <div className="w-8 h-8 rounded-lg bg-blue-100 dark:bg-blue-900/30 text-blue-600 flex items-center justify-center group-hover:bg-blue-200 dark:group-hover:bg-blue-900/50 transition-colors border border-blue-200 dark:border-blue-800/30"><UserIcon className="w-4 h-4"/></div>
                            <div className="flex-1">
                                <div className="font-bold text-sm text-slate-900 dark:text-white">Personal</div>
                                <div className="text-[10px] text-slate-500 uppercase">{currentUser?.username}</div>
                            </div>
                            {selectedContext === currentUser?.id && <Check className="w-4 h-4 text-modtale-accent" />}
                        </button>

                        {myOrgs.length > 0 && <div className="h-px bg-slate-200 dark:bg-white/10 my-2 mx-2" />}

                        {myOrgs.map(org => (
                            <button key={org.id} onClick={() => { setSelectedContext(org.id); setContextDropdownOpen(false); }} className="w-full flex items-center gap-3 p-3 hover:bg-slate-100 dark:hover:bg-white/10 transition-colors text-left rounded-xl group">
                                <div className="w-8 h-8 rounded-lg bg-purple-100 dark:bg-purple-900/30 text-purple-600 flex items-center justify-center group-hover:bg-purple-200 dark:group-hover:bg-purple-900/50 transition-colors border border-purple-200 dark:border-purple-800/30">
                                    {org.avatarUrl ? <img src={org.avatarUrl} className="w-full h-full rounded-lg object-cover" /> : <Building2 className="w-4 h-4"/>}
                                </div>
                                <div className="flex-1 min-w-0">
                                    <div className="font-bold text-sm text-slate-900 dark:text-white truncate">{org.displayName || org.username}</div>
                                    <div className="text-[10px] text-slate-500 uppercase">Organization</div>
                                </div>
                                {selectedContext === org.id && <Check className="w-4 h-4 text-modtale-accent" />}
                            </button>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );

    if (loading) return (
        <div className="w-full space-y-8 animate-pulse pt-2">
            <div className="flex justify-between items-end mb-8">
                <div>
                    <div className="h-10 w-72 bg-slate-200 dark:bg-white/10 rounded-xl mb-3"></div>
                    <div className="h-4 w-48 bg-slate-200 dark:bg-white/10 rounded-lg"></div>
                </div>
                <div className="h-12 w-48 bg-slate-200 dark:bg-white/10 rounded-2xl"></div>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                {[...Array(4)].map((_, i) => (
                    <div key={i} className="h-40 bg-slate-200/50 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10"></div>
                ))}
            </div>
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {[...Array(4)].map((_, i) => (
                    <div key={i} className="h-[500px] bg-slate-200/50 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10"></div>
                ))}
            </div>
        </div>
    );

    if (!id && !hasProjects) return (
        <div className="mt-8 animate-in fade-in duration-500">
            {!id && myOrgs.length > 0 && (
                <div className="flex items-center gap-3 mb-8">
                    <h1 className="text-2xl md:text-3xl font-black text-slate-900 dark:text-white">Analytics</h1>
                    <span className="text-slate-300 dark:text-slate-700 text-2xl font-light">/</span>
                    <ContextSwitcher />
                </div>
            )}
            <EmptyState icon={BarChart2} title="No Analytics Available" message="This account hasn't uploaded any content yet." actionLabel="Upload Content" onAction={() => navigate('/upload')} />
        </div>
    );

    const overallDownloads = calculateOverall(seriesData);
    const overallViews = id ? (viewsData['overall'] || []).map((v: any) => ({ date: v.date, value: v.count })) : calculateOverall(viewsData);

    const chartDatasets = {
        downloads: [
            { id: 'overall', label: 'Overall', color: OVERALL_COLOR, data: sliceData(overallDownloads), hidden: !!hiddenSeries['overall'] },
            ...items.map((key, i) => ({
                id: key, label: itemMeta[key]?.title || itemMeta[key]?.label || key, color: COLORS[i % COLORS.length],
                data: sliceData(seriesData[key]?.map((d: any) => ({ date: d.date, value: d.count })) || []),
                hidden: !!hiddenSeries[key] || (i >= 5 && hiddenSeries[key] === undefined)
            }))
        ],
        views: [
            { id: 'overall', label: 'Total Views', color: '#3b82f6', data: sliceData(overallViews), hidden: !!hiddenSeries['overall'] },
            ...items.map((key, i) => ({
                id: key,
                label: itemMeta[key]?.title || itemMeta[key]?.label || key,
                color: COLORS[i % COLORS.length],
                data: sliceData(viewsData[key]?.map((d: any) => ({ date: d.date, value: d.count })) || []),
                hidden: !!hiddenSeries[key] || (i >= 5 && hiddenSeries[key] === undefined)
            }))
        ],
        growth: [
            { id: 'overall', label: 'Overall Momentum', color: '#10b981', data: sliceData(calculateWoW(overallDownloads)), hidden: !!hiddenGrowth['overall'] },
            ...items.map((key, i) => ({
                id: key, label: itemMeta[key]?.title || itemMeta[key]?.label || key, color: COLORS[i % COLORS.length],
                data: sliceData(calculateWoW(seriesData[key]?.map((d: any) => ({ date: d.date, value: d.count })) || [])),
                hidden: !!hiddenGrowth[key] || true
            }))
        ],
        fourthMetric: { ...fourthChart, data: fourthChart?.type === 'bar' ? fourthChart.data.map((d:any) => ({...d, color: d.id === 'overall' ? OVERALL_COLOR : COLORS[items.indexOf(d.id) % COLORS.length], hidden: !!hiddenSeries[d.id] })) : fourthChart.data }
    };

    const ranges = ['7d', '30d', '90d'];
    const activeRangeIndex = ranges.indexOf(range);

    return (
        <div className="relative animate-in fade-in duration-500 pt-2">
            <div className="flex flex-col md:flex-row justify-between md:items-end gap-6 mb-8">
                <div>
                    <div className="flex items-center gap-3 mb-2">
                        <h1 className="text-2xl md:text-3xl font-black text-slate-900 dark:text-white">Analytics</h1>

                        {(!id && myOrgs.length > 0) && (
                            <>
                                <span className="text-slate-300 dark:text-slate-700 text-2xl font-light">/</span>
                                <ContextSwitcher />
                            </>
                        )}

                        {id && meta.title && (
                            <>
                                <span className="text-slate-300 dark:text-slate-700 text-2xl font-light">/</span>
                                <span className="text-lg md:text-xl font-bold text-slate-700 dark:text-slate-300 bg-slate-200/50 dark:bg-white/10 px-3 py-1.5 rounded-xl">{meta.title}</span>
                            </>
                        )}
                    </div>
                    <p className="text-sm text-slate-500 dark:text-slate-400 font-medium">{meta.subtitle}</p>
                </div>

                <div className="relative flex bg-white/60 dark:bg-black/20 p-1 rounded-xl shadow-inner border border-slate-200 dark:border-white/10 shrink-0 w-fit">
                    <div
                        className="absolute top-1 bottom-1 w-14 rounded-lg transition-transform duration-300 ease-out bg-modtale-accent shadow-sm shadow-modtale-accent/30 border border-transparent"
                        style={{ transform: `translateX(${activeRangeIndex * 100}%)` }}
                    />
                    {ranges.map(r => (
                        <button
                            key={r}
                            onClick={() => setRange(r)}
                            className={`relative z-10 w-14 py-2 text-xs font-bold transition-colors duration-300 ${
                                range === r ? 'text-white' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300'
                            }`}
                        >
                            {r}
                        </button>
                    ))}
                </div>
            </div>

            <div className="w-full space-y-6">
                {summary && (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                        <SummaryCard title="Downloads" value={summary.downloads.value.toLocaleString()} subValue={`Total: ${summary.downloads.total.toLocaleString()}`} trend={summary.downloads.trend} icon={Download} color="text-blue-500" />
                        <SummaryCard title="Views" value={summary.views.value.toLocaleString()} subValue={`Total: ${summary.views.total.toLocaleString()}`} trend={summary.views.trend} icon={Eye} color="text-purple-500" />
                        <SummaryCard title="Conversion Rate" value={summary.conversion.toFixed(1)} subValue="Downloads per View" icon={PieChart} color="text-emerald-500" isPercent />
                        <SummaryCard title={summary.contentCount.label} value={summary.contentCount.value.toLocaleString()} subValue="Active Items" icon={Layers} color="text-yellow-500" />
                    </div>
                )}

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 shadow-sm flex flex-col h-[500px] backdrop-blur-md">
                        <div className="flex items-center gap-4 mb-4 shrink-0 px-6 pt-6">
                            <div className="p-2.5 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-white/5 shadow-sm text-blue-500"><Download className="w-5 h-5" /></div>
                            <div>
                                <h3 className="font-bold text-lg text-slate-900 dark:text-white leading-tight">Downloads over Time</h3>
                                <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">Daily download activity.</p>
                            </div>
                        </div>
                        <div className="flex-1 min-h-0 px-6 pb-6">
                            <LineChart datasets={chartDatasets.downloads} onToggle={(sid) => setHiddenSeries(p => ({ ...p, [sid]: !p[sid] }))} />
                        </div>
                    </div>

                    <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 shadow-sm flex flex-col h-[500px] backdrop-blur-md">
                        <div className="flex items-center gap-4 mb-4 shrink-0 px-6 pt-6">
                            <div className="p-2.5 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-white/5 shadow-sm text-purple-500"><Eye className="w-5 h-5" /></div>
                            <div>
                                <h3 className="font-bold text-lg text-slate-900 dark:text-white leading-tight">Views over Time</h3>
                                <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">Daily page view activity.</p>
                            </div>
                        </div>
                        <div className="flex-1 min-h-0 px-6 pb-6">
                            <LineChart datasets={chartDatasets.views} onToggle={(sid) => setHiddenSeries(p => ({ ...p, [sid]: !p[sid] }))} />
                        </div>
                    </div>

                    <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 shadow-sm flex flex-col h-[500px] backdrop-blur-md">
                        <div className="flex items-center gap-4 mb-4 shrink-0 px-6 pt-6">
                            <div className="p-2.5 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-white/5 shadow-sm text-emerald-500"><TrendingUp className="w-5 h-5" /></div>
                            <div>
                                <h3 className="font-bold text-lg text-slate-900 dark:text-white leading-tight">Momentum (WoW %)</h3>
                                <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">Week over week growth.</p>
                            </div>
                        </div>
                        <div className="flex-1 min-h-0 px-6 pb-6">
                            <LineChart datasets={chartDatasets.growth} onToggle={(sid) => setHiddenGrowth(p => ({ ...p, [sid]: !p[sid] }))} yAxisFormatter={(val) => `${val > 0 ? '+' : ''}${Math.round(val)}%`} />
                        </div>
                    </div>

                    {chartDatasets.fourthMetric && (
                        <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 shadow-sm flex flex-col h-[500px] backdrop-blur-md">
                            <div className="flex items-center gap-4 mb-4 shrink-0 px-6 pt-6">
                                <div className="p-2.5 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-white/5 shadow-sm text-orange-500">{chartDatasets.fourthMetric.icon}</div>
                                <div>
                                    <h3 className="font-bold text-lg text-slate-900 dark:text-white leading-tight">{chartDatasets.fourthMetric.title}</h3>
                                    <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">Breakdown by item.</p>
                                </div>
                            </div>
                            <div className="flex-1 min-h-0 px-6 pb-6">
                                {chartDatasets.fourthMetric.type === 'line' ?
                                    <LineChart datasets={chartDatasets.fourthMetric.data} /> :
                                    <BarChart data={chartDatasets.fourthMetric.data} formatter={chartDatasets.fourthMetric.formatter} onToggle={(sid) => setHiddenSeries(p => ({ ...p, [sid]: !p[sid] }))} />
                                }
                            </div>
                        </div>
                    )}
                </div>

                <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 overflow-hidden shadow-sm backdrop-blur-md">
                    <div className="p-6 border-b border-slate-200 dark:border-white/10 bg-slate-50/50 dark:bg-white/[0.02]">
                        <h3 className="font-bold text-lg text-slate-900 dark:text-white flex items-center gap-3">
                            <div className="p-2 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-white/5 shadow-sm"><Layers className="w-5 h-5 text-blue-500" /></div>
                            {id ? "Version Breakdown" : "Project Breakdown"}
                        </h3>
                    </div>
                    <div className="overflow-x-auto">
                        <table className="w-full text-left text-sm">
                            <thead className="text-slate-500 dark:text-slate-400 font-bold uppercase tracking-widest text-[10px] border-b border-slate-200 dark:border-white/10 bg-slate-50/80 dark:bg-white/[0.02]">
                            <tr>
                                {(tableConfig?.headers || []).map((h, i) => (
                                    <th key={i} className={`px-4 py-4 ${i === 0 ? 'pl-8' : ''} ${i === (tableConfig?.headers.length || 0) - 1 ? 'text-right pr-8' : ''}`}>{h}</th>
                                ))}
                            </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-200 dark:divide-white/10">
                            {items.map(key => {
                                const sum = (seriesData[key] || []).slice(BUFFER).reduce((acc: number, d: any) => acc + d.count, 0);
                                return (
                                    <tr key={key} className="hover:bg-white/50 dark:hover:bg-white/[0.02] transition-colors">
                                        {tableConfig?.rowRenderer(key, sum)}
                                    </tr>
                                );
                            })}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    );
};