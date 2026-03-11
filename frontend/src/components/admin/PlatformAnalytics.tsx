import React, { useEffect, useState } from 'react';
import {
    Eye, Download, TrendingUp, TrendingDown,
    PackagePlus, Server
} from 'lucide-react';

import { api } from '../../utils/api.ts';
import { LineChart } from '../ui/charts/LineChart.tsx';
import { sliceData } from '../../utils/analytics.ts';

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

export const PlatformAnalytics: React.FC = () => {
    const [loading, setLoading] = useState(true);
    const [range, setRange] = useState('30d');
    const [data, setData] = useState<any>(null);
    const [hiddenSeries, setHiddenSeries] = useState<Record<string, boolean>>({});

    useEffect(() => {
        const fetchData = async () => {
            setLoading(true);
            try {
                const res = await api.get(`/admin/analytics/platform?range=${range}`);
                setData(res.data);
            } catch (e) {
                console.error("Failed to fetch platform analytics", e);
            } finally {
                setLoading(false);
            }
        };
        fetchData();
    }, [range]);

    if (loading) return (
        <div className="w-full space-y-8 animate-pulse">
            <div className="flex justify-between items-end mb-8">
                <div className="h-10 w-72 bg-slate-200 dark:bg-white/10 rounded-xl mb-3"></div>
                <div className="h-12 w-48 bg-slate-200 dark:bg-white/10 rounded-2xl"></div>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                {[...Array(4)].map((_, i) => (
                    <div key={i} className="h-40 bg-slate-200/50 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10"></div>
                ))}
            </div>
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {[...Array(3)].map((_, i) => (
                    <div key={i} className={`h-[500px] bg-slate-200/50 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 ${i === 2 ? 'lg:col-span-2' : ''}`}></div>
                ))}
            </div>
        </div>
    );

    if (!data) return null;

    const formatData = (chartData: any[]) => {
        return chartData?.map((d: any) => ({ date: d.date, value: d.count })) || [];
    };

    const downloadsData = formatData(data.downloadsChart);
    const viewsData = formatData(data.viewsChart);
    const newProjectsData = formatData(data.newProjectsChart);

    const chartDatasets = {
        downloads: [
            { id: 'downloads', label: 'Platform Downloads', color: '#3b82f6', data: sliceData(downloadsData), hidden: !!hiddenSeries['downloads'] }
        ],
        views: [
            { id: 'views', label: 'Platform Views', color: '#a855f7', data: sliceData(viewsData), hidden: !!hiddenSeries['views'] }
        ],
        newProjects: [
            { id: 'newProjects', label: 'New Projects', color: '#10b981', data: sliceData(newProjectsData), hidden: !!hiddenSeries['newProjects'] }
        ]
    };

    const ranges = ['7d', '30d', '90d'];
    const activeRangeIndex = ranges.indexOf(range);

    const apiPercentage = data.totalDownloads > 0 ? ((data.apiDownloads / data.totalDownloads) * 100).toFixed(1) : '0';

    return (
        <div className="relative animate-in fade-in duration-500">
            <div className="flex flex-col md:flex-row justify-between md:items-end gap-6 mb-8">
                <div className="relative flex bg-white/60 dark:bg-black/20 p-1 rounded-xl shadow-inner border border-slate-200 dark:border-white/10 shrink-0 w-fit ml-auto">
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
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                    <SummaryCard title="Total Downloads" value={data.totalDownloads.toLocaleString()} icon={Download} color="text-blue-500" />
                    <SummaryCard title="Total Views" value={data.totalViews.toLocaleString()} icon={Eye} color="text-purple-500" />
                    <SummaryCard title="New Projects" value={data.totalNewProjects.toLocaleString()} icon={PackagePlus} color="text-emerald-500" />
                    <SummaryCard title="API Downloads" value={apiPercentage} isPercent subValue={`${data.apiDownloads.toLocaleString()} requests`} icon={Server} color="text-orange-500" />
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 shadow-sm flex flex-col h-[500px] backdrop-blur-md">
                        <div className="flex items-center gap-4 mb-4 shrink-0 px-6 pt-6">
                            <div className="p-2.5 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-white/5 shadow-sm text-blue-500"><Download className="w-5 h-5" /></div>
                            <div>
                                <h3 className="font-bold text-lg text-slate-900 dark:text-white leading-tight">Downloads over Time</h3>
                                <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">Platform-wide daily downloads.</p>
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
                                <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">Platform-wide daily page views.</p>
                            </div>
                        </div>
                        <div className="flex-1 min-h-0 px-6 pb-6">
                            <LineChart datasets={chartDatasets.views} onToggle={(sid) => setHiddenSeries(p => ({ ...p, [sid]: !p[sid] }))} />
                        </div>
                    </div>

                    <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 shadow-sm flex flex-col h-[500px] backdrop-blur-md lg:col-span-2">
                        <div className="flex items-center gap-4 mb-4 shrink-0 px-6 pt-6">
                            <div className="p-2.5 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-white/5 shadow-sm text-emerald-500"><PackagePlus className="w-5 h-5" /></div>
                            <div>
                                <h3 className="font-bold text-lg text-slate-900 dark:text-white leading-tight">New Projects</h3>
                                <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">Daily new project creations.</p>
                            </div>
                        </div>
                        <div className="flex-1 min-h-0 px-6 pb-6">
                            <LineChart datasets={chartDatasets.newProjects} onToggle={(sid) => setHiddenSeries(p => ({ ...p, [sid]: !p[sid] }))} />
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};