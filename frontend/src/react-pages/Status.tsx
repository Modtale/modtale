import React, { useEffect, useState, useMemo } from 'react';
import { api } from '../utils/api';
import {
    CheckCircle, AlertTriangle, XCircle, Server, Database,
    HardDrive, Globe, RefreshCw, Activity, Zap, BarChart2, Info
} from 'lucide-react';
import { LineChart } from '../components/ui/charts/LineChart.tsx';
import { Spinner } from '../components/ui/Spinner';

interface ServiceStatus {
    id: string;
    name: string;
    status: 'operational' | 'degraded' | 'outage';
    latency: number;
}

interface HistoryPoint {
    time: number;
    api: number;
    db: number;
    storage: number;
}

interface StatusResponse {
    overall: string;
    services: ServiceStatus[];
    timestamp: number;
    history: HistoryPoint[];
}

interface UptimeHeatmapProps {
    serviceId: 'api' | 'db' | 'storage';
    data: HistoryPoint[];
    range: '24h' | '30d';
    label: string;
}

const UptimeHeatmap: React.FC<UptimeHeatmapProps> = ({ serviceId, data, range, label }) => {
    const [hoveredBar, setHoveredBar] = useState<{ index: number; label: string; status: string; percent: number } | null>(null);

    const buckets = useMemo(() => {
        const now = Date.now();
        const bucketCount = range === '24h' ? 48 : 30;
        const timeWindow = range === '24h' ? 24 * 60 * 60 * 1000 : 30 * 24 * 60 * 60 * 1000;
        const bucketDuration = timeWindow / bucketCount;
        const startTime = now - timeWindow;

        const results = [];

        for (let i = 0; i < bucketCount; i++) {
            const bucketStart = startTime + (i * bucketDuration);
            const bucketEnd = bucketStart + bucketDuration;

            const points = data.filter(d => d.time >= bucketStart && d.time < bucketEnd);

            let status: 'operational' | 'degraded' | 'down' | 'idle' = 'idle';
            let uptimePercent = 0;

            if (points.length > 0) {
                const upPoints = points.filter(p => p[serviceId] < 5000 && p[serviceId] > 0).length;
                uptimePercent = (upPoints / points.length) * 100;

                if (uptimePercent >= 99) status = 'operational';
                else if (uptimePercent >= 90) status = 'degraded';
                else status = 'down';
            }

            results.push({
                startTime: bucketStart,
                status,
                percent: uptimePercent
            });
        }
        return results;
    }, [data, range, serviceId]);

    const getColor = (status: string) => {
        switch (status) {
            case 'operational': return 'bg-emerald-500 dark:bg-emerald-500 hover:bg-emerald-400';
            case 'degraded': return 'bg-yellow-500 dark:bg-yellow-500 hover:bg-yellow-400';
            case 'down': return 'bg-red-500 dark:bg-red-500 hover:bg-red-400';
            default: return 'bg-slate-200 dark:bg-white/10 hover:bg-slate-300 dark:hover:bg-white/20';
        }
    };

    const formatTime = (ts: number) => {
        return range === '24h'
            ? new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            : new Date(ts).toLocaleDateString([], { month: 'short', day: 'numeric' });
    };

    return (
        <div className="w-full mb-6">
            <div className="flex justify-between items-end mb-2">
                <h4 className="text-xs font-bold uppercase text-slate-500 dark:text-slate-400 tracking-wider">{label}</h4>
                <span className="text-[10px] font-mono text-slate-400">
                    {hoveredBar ? (
                        <span className={
                            hoveredBar.status === 'down' ? 'text-red-500 font-bold' :
                                hoveredBar.status === 'degraded' ? 'text-yellow-500 font-bold' :
                                    hoveredBar.status === 'idle' ? 'text-slate-400' : 'text-emerald-500 font-bold'
                        }>
                            {hoveredBar.label}: {hoveredBar.status === 'idle' ? 'Sleeping' : `${hoveredBar.percent.toFixed(1)}%`}
                        </span>
                    ) : (
                        range === '24h' ? 'Uptime: 24h' : 'Uptime: 30d'
                    )}
                </span>
            </div>

            <div className="flex gap-1 h-8 md:h-10 w-full">
                {buckets.map((b, i) => (
                    <div
                        key={i}
                        className={`flex-1 rounded-sm transition-all duration-200 cursor-pointer ${getColor(b.status)}`}
                        onMouseEnter={() => setHoveredBar({ index: i, label: formatTime(b.startTime), status: b.status, percent: b.percent })}
                        onMouseLeave={() => setHoveredBar(null)}
                    />
                ))}
            </div>
        </div>
    );
};

const StatusIcon = ({ status }: { status: string }) => {
    if (status === 'operational') return <CheckCircle className="w-6 h-6 text-green-500" />;
    if (status === 'degraded') return <AlertTriangle className="w-6 h-6 text-yellow-500" />;
    return <XCircle className="w-6 h-6 text-red-500" />;
};

const ServiceIcon = ({ id }: { id: string }) => {
    if (id === 'api') return <Server className="w-5 h-5 text-blue-500" />;
    if (id === 'database') return <Database className="w-5 h-5 text-purple-500" />;
    if (id === 'storage') return <HardDrive className="w-5 h-5 text-orange-500" />;
    return <Globe className="w-5 h-5 text-slate-500" />;
};

export const Status: React.FC = () => {
    const [data, setData] = useState<StatusResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [lastUpdated, setLastUpdated] = useState<Date>(new Date());
    const [range, setRange] = useState<'24h' | '30d'>('24h');

    const fetchStatus = async () => {
        setIsRefreshing(true);
        const start = Date.now();
        try {
            const res = await api.get(`/status?range=${range}`);
            const apiLatency = Date.now() - start;

            const services = res.data.services.map((s: ServiceStatus) =>
                s.id === 'api' ? { ...s, latency: apiLatency } : s
            );

            setData({ ...res.data, services });
            setLastUpdated(new Date());
        } catch (e) {
            console.error(e);
            if (!data) {
                setData({ overall: 'outage', services: [], timestamp: Date.now(), history: [] });
            }
        } finally {
            setLoading(false);
            setIsRefreshing(false);
        }
    };

    useEffect(() => {
        fetchStatus();
        const interval = setInterval(fetchStatus, 60000);
        return () => clearInterval(interval);
    }, [range]);

    const displayHistory = useMemo(() => {
        let history: HistoryPoint[] = data?.history ? [...data.history] : [];
        const currentPoint: HistoryPoint = {
            time: data?.timestamp || Date.now(),
            api: data?.services?.find(s => s.id === 'api')?.latency || 0,
            db: data?.services?.find(s => s.id === 'database')?.latency || 0,
            storage: data?.services?.find(s => s.id === 'storage')?.latency || 0
        };

        if (history.length === 0) {
            history.push(currentPoint);
        } else if (currentPoint.time > history[history.length - 1].time) {
            history.push(currentPoint);
        }

        if (history.length === 1) {
            history.unshift({ ...history[0], time: history[0].time - 60000 });
        }
        return history;
    }, [data]);

    const perfDatasets = [
        {
            id: 'api', label: 'API', color: '#3b82f6',
            data: displayHistory.map(h => ({ date: new Date(h.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }), value: h.api }))
        },
        {
            id: 'db', label: 'Database', color: '#a855f7',
            data: displayHistory.map(h => ({ date: new Date(h.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }), value: h.db }))
        },
        {
            id: 'storage', label: 'Storage', color: '#f97316',
            data: displayHistory.map(h => ({ date: new Date(h.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }), value: h.storage }))
        }
    ];

    const overallColor = data?.overall === 'operational' ? 'bg-emerald-500' : (data?.overall === 'degraded' ? 'bg-yellow-500' : 'bg-red-500');
    const overallText = data?.overall === 'operational' ? 'All Systems Operational' : (data?.overall === 'degraded' ? 'Partial System Degraded' : 'Major System Outage');

    if (loading && !data) return <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark"><Spinner label="Status Check..." /></div>;

    return (
        <div className="max-w-5xl mx-auto px-4 py-16 min-h-screen">
            <div className="text-center mb-10">
                <h1 className="text-4xl font-black text-slate-900 dark:text-white mb-4 tracking-tight flex items-center justify-center gap-3">
                    <Activity className="w-10 h-10 text-modtale-accent" /> System Status
                </h1>
                <p className="text-slate-500 dark:text-slate-400">Live performance and reliability tracking.</p>
            </div>

            <div className="max-w-3xl mx-auto mb-10 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-xl p-4 flex gap-4">
                <Info className="w-6 h-6 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5" />
                <div className="text-sm">
                    <h3 className="font-bold text-blue-900 dark:text-blue-100 mb-1">Demo Environment: Scale-to-Zero Architecture</h3>
                    <p className="text-blue-800 dark:text-blue-200 leading-relaxed opacity-90">
                        Modtale is currently running in a cost-optimized <strong>Demo State</strong>.
                        The backend infrastructure is configured to auto-scale down to <strong>0 instances</strong> when idle.
                        <br className="mb-2 block"/>
                        You may observe "Sleeping" periods (gray bars) or brief "Cold Start" latency in the graphs below.
                        This is expected behavior for the demo. Once the site exits demo mode,
                        always-on instances will be provisioned for <strong>99.9%+ availability</strong> and significantly faster response times.
                    </p>
                </div>
            </div>

            <div className={`rounded-2xl p-1 shadow-lg mb-12 ${overallColor} transition-colors duration-500`}>
                <div className="bg-white dark:bg-modtale-card rounded-xl p-8 text-center border border-white/10">
                    <div className={`w-20 h-20 mx-auto rounded-full flex items-center justify-center mb-6 ${overallColor} bg-opacity-10`}>
                        {data?.overall === 'operational' ? <CheckCircle className={`w-10 h-10 ${overallColor.replace('bg-', 'text-')}`} /> : <AlertTriangle className={`w-10 h-10 ${overallColor.replace('bg-', 'text-')}`} />}
                    </div>
                    <h2 className="text-3xl font-black text-slate-900 dark:text-white mb-2">{overallText}</h2>
                    <p className="text-slate-500 text-sm font-bold opacity-75">Last updated: {lastUpdated.toLocaleTimeString()}</p>
                </div>
            </div>

            <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl p-8 shadow-sm mb-12">
                <div className="flex flex-col md:flex-row items-start md:items-center justify-between mb-8 gap-4">
                    <h3 className="text-2xl font-black text-slate-900 dark:text-white flex items-center gap-2">
                        <BarChart2 className="w-6 h-6 text-slate-400" /> Uptime History
                    </h3>
                    <div className="flex bg-slate-100 dark:bg-black/20 p-1 rounded-lg">
                        <button onClick={() => setRange('24h')} className={`px-4 py-1.5 rounded-md text-xs font-bold transition-all ${range === '24h' ? 'bg-white dark:bg-modtale-card text-modtale-accent shadow-sm' : 'text-slate-500'}`}>24 Hours</button>
                        <button onClick={() => setRange('30d')} className={`px-4 py-1.5 rounded-md text-xs font-bold transition-all ${range === '30d' ? 'bg-white dark:bg-modtale-card text-modtale-accent shadow-sm' : 'text-slate-500'}`}>30 Days</button>
                    </div>
                </div>

                <UptimeHeatmap label="API Gateway" serviceId="api" data={displayHistory} range={range} />
                <UptimeHeatmap label="Database (Atlas)" serviceId="db" data={displayHistory} range={range} />
                <UptimeHeatmap label="Storage (R2)" serviceId="storage" data={displayHistory} range={range} />

                <div className="flex flex-wrap justify-center gap-6 mt-6 border-t border-slate-100 dark:border-white/5 pt-6 text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                    <div className="flex items-center gap-2"><div className="w-2.5 h-2.5 rounded-full bg-emerald-500"></div> Operational</div>
                    <div className="flex items-center gap-2"><div className="w-2.5 h-2.5 rounded-full bg-yellow-500"></div> Partial Degraded</div>
                    <div className="flex items-center gap-2"><div className="w-2.5 h-2.5 rounded-full bg-red-500"></div> Outage</div>
                    <div className="flex items-center gap-2"><div className="w-2.5 h-2.5 rounded-full bg-slate-200 dark:bg-white/10"></div> Sleeping (Demo Mode)</div>
                </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-12">
                <div className="lg:col-span-2 bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl p-6 shadow-sm min-h-[400px]">
                    <h3 className="font-bold mb-6 flex items-center gap-2 text-slate-900 dark:text-white">
                        <Zap className="w-4 h-4 text-yellow-500" /> Response Times
                    </h3>
                    <div className="h-96 w-full">
                        <LineChart datasets={perfDatasets} yAxisFormatter={(v) => `${Math.round(v)}ms`} />
                    </div>
                </div>

                <div className="space-y-4">
                    {data?.services.map((service) => (
                        <div key={service.id} className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl p-5 shadow-sm">
                            <div className="flex items-center justify-between mb-2">
                                <div className="p-2 bg-slate-50 dark:bg-white/5 rounded-lg"><ServiceIcon id={service.id} /></div>
                                <StatusIcon status={service.status} />
                            </div>
                            <div className="text-sm font-bold text-slate-900 dark:text-white">{service.name}</div>
                            <div className="text-xs text-slate-500 mt-1 flex justify-between">
                                <span>Current Latency</span>
                                <span className="font-mono font-bold text-slate-700 dark:text-slate-300">{Math.round(service.latency)}ms</span>
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            <div className="flex justify-center">
                <button
                    onClick={fetchStatus}
                    disabled={isRefreshing}
                    className="flex items-center gap-2 px-8 py-3 bg-white dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-xl font-bold text-slate-600 dark:text-slate-300 hover:border-modtale-accent transition-all active:scale-95 disabled:opacity-50"
                >
                    <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin' : ''}`} />
                    Force Refresh
                </button>
            </div>
        </div>
    );
};