import { LoadingChartFrame } from '@/modules/admin/components/LoadingChartFrame';
import { SkeletonSurface } from '@/components/ui/Skeleton';
import React, { useEffect, useState } from 'react';
import { Eye, Download, TrendingUp, TrendingDown, PackagePlus, UserPlus, Activity } from 'lucide-react';
import { adminClient } from '../api/adminClient';
import { LineChart } from '@/components/ui/charts/LineChart';
import { useChartVisibility } from '@/components/ui/charts/chartVisibility';
import { extractApiErrorMessage } from '@/utils/api';
import { sliceData, calculateWoW, calculateRollingAverage } from '@/utils/analytics';

const SummaryCard = ({ title, value, subValue, trend, icon: Icon, color, isPercent }: any) => (
    <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 shadow-sm hover:shadow-md transition-all relative overflow-hidden group backdrop-blur-md flex flex-col justify-between p-6">
        <div className={`absolute top-0 right-0 p-4 opacity-5 group-hover:opacity-10 transition-opacity ${color}`}>
            <Icon data-skeleton-keep className={`w-32 h-32 transform translate-x-8 -translate-y-8 ${color}`} />
        </div>
        <div className="relative z-10 flex items-start justify-between mb-2">
            <div data-skeleton-keep className={`p-3 rounded-2xl ${color} bg-opacity-10 text-current shadow-inner`}>
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
            <h3 data-skeleton-keep className="text-slate-500 dark:text-slate-400 text-[10px] font-black uppercase tracking-widest mb-1">{title}</h3>
            <div className="text-3xl md:text-4xl font-black text-slate-900 dark:text-white tracking-normal leading-none">
                {value}{isPercent && <span className="text-2xl text-slate-400 ml-1">%</span>}
            </div>
            {subValue && <div className="text-xs text-slate-500 dark:text-slate-400 mt-2 font-medium">{subValue}</div>}
        </div>
    </div>
);

export function PlatformAnalytics() {
    const [loading, setLoading] = useState(true);
    const [range, setRange] = useState('30d');
    const [loadedData, setData] = useState<any>(null);
    const { isHidden, toggleHandler } = useChartVisibility();
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    useEffect(() => {
        const fetchData = async () => {
            setLoading(true);
        try {
            const res = await adminClient.getPlatformAnalytics(range);
            setData(res);
            setErrorMessage(null);
        } catch (e) {
            setErrorMessage(extractApiErrorMessage(e, 'We could not load platform analytics.'));
        } finally {
            setLoading(false);
        }
        };
        fetchData();
    }, [range]);

    // Render the same cards, axes and legends while the API is pending.
    const data = loadedData || (loading ? {
        totalDownloads: 1234, previousTotalDownloads: 1000,
        totalViews: 5678, previousTotalViews: 5000,
        totalNewUsers: 123, previousTotalNewUsers: 100,
        totalNewProjects: 123, previousTotalNewProjects: 100,
        ...Object.fromEntries(['downloadsChart', 'apiDownloadsChart', 'viewsChart', 'newProjectsChart', 'newUsersChart', 'newOrgsChart'].map(key => [key,
            Array.from({ length: 65 }, (_, i) => ({ date: new Date(Date.UTC(2026, 0, i + 1)).toISOString().slice(0, 10), count: 0 }))
        ])),
    } : null);
    const Surface = loading ? SkeletonSurface : React.Fragment;

    if (!data) {
        if (!errorMessage) return null;
        return (
            <div className="rounded-3xl border border-red-200 bg-red-50 px-6 py-5 text-sm font-medium text-red-700 dark:border-red-500/20 dark:bg-red-500/10 dark:text-red-300">
                {errorMessage}
            </div>
        );
    }

    const calcTrend = (current: number, previous: number) => {
        if (!previous) {
            if (!current) return 0;
            return current > 0 ? 100 : -100;
        }
        return ((current - previous) / Math.abs(previous)) * 100;
    };

    const formatData = (chartData: any[]) => {
        return chartData?.map((d: any) => ({ date: d.date, value: d.count })) || [];
    };

    const downloadsData = formatData(data.downloadsChart);
    const apiDownloadsData = formatData(data.apiDownloadsChart);
    const viewsData = formatData(data.viewsChart);
    const downloadsAvg7 = calculateRollingAverage(downloadsData, 7);
    const downloadsAvg30 = calculateRollingAverage(downloadsData, 30);
    const viewsAvg7 = calculateRollingAverage(viewsData, 7);
    const viewsAvg30 = calculateRollingAverage(viewsData, 30);
    const newProjectsData = formatData(data.newProjectsChart);
    const newUsersData = formatData(data.newUsersChart);
    const newOrgsData = formatData(data.newOrgsChart);

    const chartDatasets = {
        downloads: [
            { id: 'downloads', label: 'Platform Downloads', color: '#3b82f6', data: sliceData(downloadsData), hidden: isHidden('downloads', 'downloads') },
            { id: 'apiDownloads', label: 'API Downloads', color: '#f97316', data: sliceData(apiDownloadsData), hidden: isHidden('downloads', 'apiDownloads') },
            { id: 'downloadsAvg7', label: 'Downloads 7d Avg', color: '#14b8a6', data: sliceData(downloadsAvg7), hidden: isHidden('downloads', 'downloadsAvg7', true) },
            { id: 'downloadsAvg30', label: 'Downloads 30d Avg', color: '#a855f7', data: sliceData(downloadsAvg30), hidden: isHidden('downloads', 'downloadsAvg30', true) }
        ],
        views: [
            { id: 'views', label: 'Platform Views', color: '#a855f7', data: sliceData(viewsData), hidden: isHidden('views', 'views') },
            { id: 'viewsAvg7', label: 'Views 7d Avg', color: '#14b8a6', data: sliceData(viewsAvg7), hidden: isHidden('views', 'viewsAvg7', true) },
            { id: 'viewsAvg30', label: 'Views 30d Avg', color: '#f97316', data: sliceData(viewsAvg30), hidden: isHidden('views', 'viewsAvg30', true) }
        ],
        newProjects: [
            { id: 'newProjects', label: 'Net New Projects', color: '#10b981', data: sliceData(newProjectsData), hidden: isHidden('newCreations', 'newProjects') },
            { id: 'newUsers', label: 'Net New Users', color: '#f59e0b', data: sliceData(newUsersData), hidden: isHidden('newCreations', 'newUsers') },
            { id: 'newOrgs', label: 'Net New Organizations', color: '#8b5cf6', data: sliceData(newOrgsData), hidden: isHidden('newCreations', 'newOrgs') }
        ],
        growth: [
            { id: 'downloadsGrowth', label: 'Downloads Momentum', color: '#3b82f6', data: sliceData(calculateWoW(downloadsData)), hidden: isHidden('momentum', 'downloadsGrowth') },
            { id: 'viewsGrowth', label: 'Views Momentum', color: '#a855f7', data: sliceData(calculateWoW(viewsData)), hidden: isHidden('momentum', 'viewsGrowth') },
            { id: 'usersGrowth', label: 'Net Users Momentum', color: '#f59e0b', data: sliceData(calculateWoW(newUsersData)), hidden: isHidden('momentum', 'usersGrowth') }
        ]
    };

    const ranges = ['7d', '30d', '90d'];
    const activeRangeIndex = ranges.indexOf(range);

    return (
        <Surface>
        <div className="relative animate-in fade-in duration-500">
            {errorMessage && (
                <div className="mb-6 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 dark:border-red-500/20 dark:bg-red-500/10 dark:text-red-300">
                    {errorMessage}
                </div>
            )}
            <div className="flex flex-col md:flex-row justify-between md:items-end gap-6 mb-8">
                <div>
                    <h1 data-skeleton-keep className="text-3xl font-black text-slate-900 dark:text-white tracking-normal">Platform Analytics</h1>
                    <p data-skeleton-keep className="text-slate-500 dark:text-slate-400 font-medium mt-1">Monitor platform-wide statistics and growth.</p>
                </div>
                <div data-skeleton-keep className="relative flex bg-white/60 dark:bg-black/20 p-1 rounded-xl shadow-inner border border-slate-200 dark:border-white/10 shrink-0 w-fit">
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
                    <SummaryCard
                        title="Downloads"
                        value={data.totalDownloads.toLocaleString()}
                        trend={calcTrend(data.totalDownloads, data.previousTotalDownloads)}
                        icon={Download} color="text-blue-500"
                    />
                    <SummaryCard
                        title="Views"
                        value={data.totalViews.toLocaleString()}
                        trend={calcTrend(data.totalViews, data.previousTotalViews)}
                        icon={Eye} color="text-purple-500"
                    />
                    <SummaryCard
                        title="Net New Users"
                        value={data.totalNewUsers.toLocaleString()}
                        trend={calcTrend(data.totalNewUsers, data.previousTotalNewUsers)}
                        icon={UserPlus} color="text-orange-500"
                    />
                    <SummaryCard
                        title="Net New Projects"
                        value={data.totalNewProjects.toLocaleString()}
                        trend={calcTrend(data.totalNewProjects, data.previousTotalNewProjects)}
                        icon={PackagePlus} color="text-emerald-500"
                    />
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 shadow-sm flex flex-col h-[500px] backdrop-blur-md">
                        <div data-skeleton-keep className="flex items-center gap-4 mb-4 shrink-0 px-6 pt-6">
                            <div className="p-2.5 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-white/5 shadow-sm text-blue-500"><Download className="w-5 h-5" /></div>
                            <div>
                                <h3 data-skeleton-keep className="font-bold text-lg text-slate-900 dark:text-white leading-tight">Downloads over Time</h3>
                                <p data-skeleton-keep className="text-xs text-slate-500 dark:text-slate-400 font-medium">Platform vs API daily downloads.</p>
                            </div>
                        </div>
                        <LoadingChartFrame pending={loading} className="flex-1 min-h-0 px-6 pb-6">
                            <LineChart datasets={chartDatasets.downloads} onToggle={toggleHandler('downloads')} />
                        </LoadingChartFrame>
                    </div>

                    <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 shadow-sm flex flex-col h-[500px] backdrop-blur-md">
                        <div data-skeleton-keep className="flex items-center gap-4 mb-4 shrink-0 px-6 pt-6">
                            <div className="p-2.5 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-white/5 shadow-sm text-purple-500"><Eye className="w-5 h-5" /></div>
                            <div>
                                <h3 data-skeleton-keep className="font-bold text-lg text-slate-900 dark:text-white leading-tight">Views over Time</h3>
                                <p data-skeleton-keep className="text-xs text-slate-500 dark:text-slate-400 font-medium">Platform-wide daily page views.</p>
                            </div>
                        </div>
                        <LoadingChartFrame pending={loading} className="flex-1 min-h-0 px-6 pb-6">
                            <LineChart datasets={chartDatasets.views} onToggle={toggleHandler('views')} />
                        </LoadingChartFrame>
                    </div>

                    <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 shadow-sm flex flex-col h-[500px] backdrop-blur-md">
                        <div data-skeleton-keep className="flex items-center gap-4 mb-4 shrink-0 px-6 pt-6">
                            <div className="p-2.5 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-white/5 shadow-sm text-emerald-500"><PackagePlus className="w-5 h-5" /></div>
                            <div>
                                <h3 data-skeleton-keep className="font-bold text-lg text-slate-900 dark:text-white leading-tight">New Creations</h3>
                                <p data-skeleton-keep className="text-xs text-slate-500 dark:text-slate-400 font-medium">Daily net users, orgs, and projects.</p>
                            </div>
                        </div>
                        <LoadingChartFrame pending={loading} className="flex-1 min-h-0 px-6 pb-6">
                            <LineChart datasets={chartDatasets.newProjects} onToggle={toggleHandler('newCreations')} />
                        </LoadingChartFrame>
                    </div>

                    <div className="bg-white/40 dark:bg-white/5 rounded-3xl border border-slate-200 dark:border-white/10 shadow-sm flex flex-col h-[500px] backdrop-blur-md">
                        <div data-skeleton-keep className="flex items-center gap-4 mb-4 shrink-0 px-6 pt-6">
                            <div className="p-2.5 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-white/5 shadow-sm text-modtale-accent"><Activity className="w-5 h-5" /></div>
                            <div>
                                <h3 data-skeleton-keep className="font-bold text-lg text-slate-900 dark:text-white leading-tight">Momentum (WoW %)</h3>
                                <p data-skeleton-keep className="text-xs text-slate-500 dark:text-slate-400 font-medium">Platform week-over-week growth.</p>
                            </div>
                        </div>
                        <LoadingChartFrame pending={loading} className="flex-1 min-h-0 px-6 pb-6">
                            <LineChart datasets={chartDatasets.growth} onToggle={toggleHandler('momentum')} yAxisFormatter={(val) => `${val > 0 ? '+' : ''}${Math.round(val)}%`} />
                        </LoadingChartFrame>
                    </div>
                </div>
            </div>
        </div>
        </Surface>
    );
}
