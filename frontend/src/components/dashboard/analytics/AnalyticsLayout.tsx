import React, { useState } from 'react';
import { ArrowLeft, Activity, Download, Eye, TrendingUp, TrendingDown, Layers, PieChart } from 'lucide-react';
import { LineChart } from '../../ui/charts/LineChart.tsx';
import { BarChart } from '../../ui/charts/BarChart.tsx';

export const SummaryCard = ({ title, value, subValue, trend, icon: Icon, color, isPercent }: any) => (
    <div className="bg-white dark:bg-modtale-card p-6 rounded-2xl border border-slate-200 dark:border-white/5 shadow-sm hover:shadow-md transition-all relative overflow-hidden group">
        <div className={`absolute top-0 right-0 p-4 opacity-5 group-hover:opacity-10 transition-opacity ${color}`}>
            <Icon className="w-24 h-24 transform translate-x-4 -translate-y-4" />
        </div>
        <div className="relative z-10">
            <div className="flex items-center justify-between mb-4">
                <div className={`p-3 rounded-xl ${color} bg-opacity-10 text-current`}>
                    <Icon className="w-6 h-6" />
                </div>
                {trend !== undefined && (
                    <div className={`flex items-center gap-1 text-xs font-bold px-2 py-1 rounded-full ${trend >= 0 ? 'bg-green-100 text-green-700 dark:bg-green-500/20 dark:text-green-400' : 'bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-400'}`}>
                        {trend >= 0 ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                        {Math.abs(trend).toFixed(1)}%
                    </div>
                )}
            </div>
            <h3 className="text-slate-500 dark:text-slate-400 text-xs font-bold uppercase tracking-wider mb-1">{title}</h3>
            <div className="text-3xl font-black text-slate-900 dark:text-white tracking-tight">{value}{isPercent && '%'}</div>
            {subValue && <div className="text-xs text-slate-400 mt-2 font-medium">{subValue}</div>}
        </div>
    </div>
);

interface DashboardProps {
    title: React.ReactNode;
    subtitle?: string;
    onBack?: () => void;
    range: string;
    setRange: (r: string) => void;
    summary: {
        downloads: { value: number; total: number; trend: number };
        views: { value: number; total: number; trend: number };
        conversion: number;
        contentCount: { value: number; label: string };
    };
    charts: {
        downloads: any[];
        views: any[];
        growth: any[];
        fourthMetric: { title: string; icon: any; data: any[]; type: 'line' | 'bar'; formatter?: (v: number) => string };
    };
    table: {
        title: string;
        headers: string[];
        rows: React.ReactNode[];
    };
    onToggleSeries: (id: string) => void;
    onToggleGrowthSeries: (id: string) => void;
    embedded?: boolean;
}

export const AnalyticsDashboard: React.FC<DashboardProps> = ({
                                                                 title, subtitle, onBack, range, setRange, summary, charts, table, onToggleSeries, onToggleGrowthSeries, embedded = false
                                                             }) => {
    const Controls = () => (
        <div className="flex bg-slate-100 dark:bg-black/20 p-1 rounded-lg shrink-0">
            {['7d', '30d', '90d'].map(r => (
                <button key={r} onClick={() => setRange(r)} className={`px-3 py-1.5 rounded-md text-xs font-bold transition-all ${range === r ? 'bg-white dark:bg-modtale-card text-modtale-accent shadow-sm' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'}`}>{r}</button>
            ))}
        </div>
    );

    return (
        <div className={`flex flex-col ${embedded ? 'w-full' : 'min-h-screen animate-in fade-in slide-in-from-bottom-4 duration-500'}`}>
            {embedded ? (
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
                    <div>
                        <h2 className="text-2xl font-black text-slate-900 dark:text-white">{title}</h2>
                        {subtitle && <p className="text-slate-500 text-sm">{subtitle}</p>}
                    </div>
                    <Controls />
                </div>
            ) : (
                <div className="sticky top-24 z-30 bg-white/80 dark:bg-modtale-card/90 backdrop-blur-md border-b border-slate-200 dark:border-white/5 shadow-sm">
                    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-20 flex items-center justify-between gap-4">
                        <div className="flex items-center gap-4">
                            {onBack && (
                                <button onClick={onBack} className="p-2 -ml-2 hover:bg-slate-100 dark:hover:bg-white/10 rounded-full transition-colors text-slate-500 dark:text-slate-400">
                                    <ArrowLeft className="w-5 h-5" />
                                </button>
                            )}
                            <div>
                                <h1 className="text-2xl font-black text-slate-900 dark:text-white flex items-center gap-2">
                                    <Activity className="w-6 h-6 text-modtale-accent" /> {title}
                                </h1>
                                {subtitle && <p className="text-xs font-bold text-slate-400 uppercase tracking-wider hidden sm:block">{subtitle}</p>}
                            </div>
                        </div>
                        <Controls />
                    </div>
                </div>
            )}

            <div className={`${embedded ? 'w-full space-y-6' : 'max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full space-y-8'}`}>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                    <SummaryCard title="Downloads" value={summary.downloads.value.toLocaleString()} subValue={`Total: ${summary.downloads.total.toLocaleString()}`} trend={summary.downloads.trend} icon={Download} color="text-blue-500" />
                    <SummaryCard title="Views" value={summary.views.value.toLocaleString()} subValue={`Total: ${summary.views.total.toLocaleString()}`} trend={summary.views.trend} icon={Eye} color="text-purple-500" />
                    <SummaryCard title="Conversion Rate" value={summary.conversion.toFixed(1)} subValue="Downloads per View" icon={PieChart} color="text-emerald-500" isPercent />
                    <SummaryCard title={summary.contentCount.label} value={summary.contentCount.value.toLocaleString()} subValue="Active Items" icon={Layers} color="text-yellow-500" />
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    <div className="bg-white dark:bg-modtale-card p-6 rounded-2xl border border-slate-200 dark:border-white/5 shadow-sm h-[500px] flex flex-col">
                        <h3 className="font-bold mb-4 flex items-center gap-2 text-slate-900 dark:text-white shrink-0"><Download className="w-4 h-4 text-blue-500" /> Downloads</h3>
                        <div className="flex-1 min-h-0">
                            <LineChart datasets={charts.downloads} onToggle={onToggleSeries} />
                        </div>
                    </div>
                    <div className="bg-white dark:bg-modtale-card p-6 rounded-2xl border border-slate-200 dark:border-white/5 shadow-sm h-[500px] flex flex-col">
                        <h3 className="font-bold mb-4 flex items-center gap-2 text-slate-900 dark:text-white shrink-0"><Eye className="w-4 h-4 text-purple-500" /> Views</h3>
                        <div className="flex-1 min-h-0">
                            <LineChart datasets={charts.views} onToggle={onToggleSeries} />
                        </div>
                    </div>
                    <div className="bg-white dark:bg-modtale-card p-6 rounded-2xl border border-slate-200 dark:border-white/5 shadow-sm h-[500px] flex flex-col">
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6 shrink-0">
                            <h3 className="font-bold text-slate-900 dark:text-white flex items-center gap-2"><TrendingUp className="w-4 h-4 text-emerald-500" /> Momentum (WoW %)</h3>
                        </div>
                        <div className="flex-1 min-h-0">
                            <LineChart datasets={charts.growth} onToggle={onToggleGrowthSeries} yAxisFormatter={(val) => `${val > 0 ? '+' : ''}${Math.round(val)}%`} />
                        </div>
                    </div>
                    <div className="bg-white dark:bg-modtale-card p-6 rounded-2xl border border-slate-200 dark:border-white/5 shadow-sm h-[500px] flex flex-col">
                        <h3 className="font-bold mb-4 flex items-center gap-2 text-slate-900 dark:text-white shrink-0">
                            {charts.fourthMetric.icon} {charts.fourthMetric.title}
                        </h3>
                        <div className="flex-1 min-h-0">
                            {charts.fourthMetric.type === 'line' ?
                                <LineChart datasets={charts.fourthMetric.data} /> :
                                <BarChart data={charts.fourthMetric.data} formatter={charts.fourthMetric.formatter} onToggle={onToggleSeries} />
                            }
                        </div>
                    </div>
                </div>

                <div className="bg-white dark:bg-modtale-card rounded-2xl border border-slate-200 dark:border-white/5 overflow-hidden shadow-sm">
                    <div className="p-6 border-b border-slate-200 dark:border-white/5">
                        <h3 className="font-bold text-lg text-slate-900 dark:text-white">{table.title}</h3>
                    </div>
                    <div className="overflow-x-auto">
                        <table className="w-full text-left text-sm">
                            <thead className="bg-slate-50 dark:bg-white/5 text-slate-500 dark:text-slate-400 font-bold uppercase text-xs">
                            <tr>
                                {table.headers.map((h, i) => (
                                    <th key={i} className={`p-4 ${i === 0 ? 'pl-6' : ''} ${i === table.headers.length - 1 ? 'text-right pr-6' : ''}`}>{h}</th>
                                ))}
                            </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-200 dark:divide-white/5">
                            {table.rows}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    );
};