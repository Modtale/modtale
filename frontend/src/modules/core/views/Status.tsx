import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '@/utils/api';
import {
    Activity,
    AlertTriangle,
    ArrowUpRight,
    BarChart3,
    Bell,
    CheckCircle2,
    Clock3,
    Database,
    Globe2,
    Gauge,
    HardDrive,
    RefreshCw,
    Server,
    ShieldCheck,
    XCircle
} from 'lucide-react';
import { LineChart } from '@/components/ui/charts/LineChart';
import { Spinner } from '@/components/ui/Spinner';
import { DiscordBrandIcon } from '@/components/ui/icons/BrandIcons';

type StatusState = 'operational' | 'degraded' | 'outage';
type StatusRange = '24h' | '30d';
type HistoryKey = 'api' | 'db' | 'storage';

interface ServiceStatus {
    id: string;
    name: string;
    status: string;
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
    activeIncidents?: StatusIncident[];
    scheduledMaintenances?: StatusIncident[];
    incidentHistory?: StatusIncident[];
}

interface Bucket {
    startTime: number;
    status: StatusState | 'no-data';
    percent: number | null;
}

type IncidentKind = 'INCIDENT' | 'MAINTENANCE';
type IncidentState = 'SCHEDULED' | 'INVESTIGATING' | 'IDENTIFIED' | 'MONITORING' | 'RESOLVED' | 'CANCELED';

interface StatusIncidentUpdate {
    id: string;
    state: IncidentState | string;
    impact: string;
    message: string;
    createdAt: string;
    createdByUsername?: string;
}

interface StatusIncident {
    id: string;
    kind: IncidentKind | string;
    state: IncidentState | string;
    impact: string;
    title: string;
    affectedServices: string[];
    scheduledStart?: string;
    scheduledEnd?: string;
    startedAt?: string;
    resolvedAt?: string;
    createdAt: string;
    updatedAt: string;
    createdByUsername?: string;
    updates: StatusIncidentUpdate[];
}

const DISCORD_INVITE_URL = 'https://discord.gg/PcFaDVYqVe';
const HEALTHY_LATENCY_LIMIT_MS = 5000;

const SERVICE_META: Record<string, { historyKey: HistoryKey; label: string }> = {
    api: { historyKey: 'api', label: 'API Gateway' },
    database: { historyKey: 'db', label: 'Database' },
    storage: { historyKey: 'storage', label: 'Storage' },
};

const STATUS_COPY: Record<StatusState, { label: string; summary: string; badge: string; iconBg: string }> = {
    operational: {
        label: 'All systems operational',
        summary: 'Core Modtale services are responding normally.',
        badge: 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-500/20 dark:bg-emerald-500/10 dark:text-emerald-400',
        iconBg: 'bg-emerald-50 dark:bg-emerald-500/10',
    },
    degraded: {
        label: 'Partial system degradation',
        summary: 'One or more services are slower than expected or partially unavailable.',
        badge: 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-400',
        iconBg: 'bg-amber-50 dark:bg-amber-500/10',
    },
    outage: {
        label: 'Major system outage',
        summary: 'A critical Modtale service is currently unavailable.',
        badge: 'border-red-200 bg-red-50 text-red-700 dark:border-red-500/20 dark:bg-red-500/10 dark:text-red-400',
        iconBg: 'bg-red-50 dark:bg-red-500/10',
    },
};

const SURFACE_CARD_CLASS = 'rounded-3xl border border-slate-200 bg-white/90 shadow-xl shadow-slate-200/60 backdrop-blur-md dark:border-white/10 dark:bg-slate-900/90 dark:shadow-black/30';
const GLASS_CARD_CLASS = 'rounded-3xl border border-slate-200 bg-white/80 shadow-lg shadow-slate-200/50 backdrop-blur-md dark:border-white/10 dark:bg-white/5 dark:shadow-black/20';
const SOFT_CARD_CLASS = 'rounded-2xl border border-slate-200 bg-slate-50/80 shadow-sm dark:border-white/10 dark:bg-white/[0.04]';

const normalizeStatus = (status?: string): StatusState => {
    const normalized = status?.toLowerCase();
    if (normalized === 'operational' || normalized === 'degraded' || normalized === 'outage') {
        return normalized;
    }
    return 'degraded';
};

const formatIncidentState = (state?: string) => {
    const normalized = (state || '').toLowerCase().replace(/_/g, ' ');
    return normalized ? normalized.replace(/^\w/, (char) => char.toUpperCase()) : 'Unknown';
};

const isHealthyLatency = (value: number) => value > 0 && value < HEALTHY_LATENCY_LIMIT_MS;

const formatLatency = (value: number) => {
    if (!Number.isFinite(value) || value <= 0) {
        return 'n/a';
    }

    if (value >= 1000) {
        return `${(value / 1000).toFixed(1)}s`;
    }

    return `${Math.round(value)}ms`;
};

const formatPercent = (value: number | null) => {
    if (value === null || !Number.isFinite(value)) {
        return 'No data';
    }
    return `${value.toFixed(value >= 99.95 ? 2 : 1)}%`;
};

const average = (values: number[]) => {
    const cleanValues = values.filter((value) => Number.isFinite(value) && value > 0);
    if (cleanValues.length === 0) return null;
    return cleanValues.reduce((sum, value) => sum + value, 0) / cleanValues.length;
};

const percentile = (values: number[], target: number) => {
    const cleanValues = values.filter((value) => Number.isFinite(value) && value > 0).sort((a, b) => a - b);
    if (cleanValues.length === 0) return null;
    const index = Math.min(cleanValues.length - 1, Math.max(0, Math.ceil(cleanValues.length * target) - 1));
    return cleanValues[index];
};

const formatTime = (timestamp: number, range: StatusRange) => (
    range === '24h'
        ? new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        : new Date(timestamp).toLocaleDateString([], { month: 'short', day: 'numeric' })
);

const formatDateTime = (value?: string) => {
    if (!value) return null;
    return new Date(value).toLocaleString([], {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
};

const getServiceIcon = (id: string, className = 'w-5 h-5') => {
    if (id === 'api') return <Server className={`${className} text-blue-500`} />;
    if (id === 'database') return <Database className={`${className} text-violet-500`} />;
    if (id === 'storage') return <HardDrive className={`${className} text-orange-500`} />;
    return <Globe2 className={`${className} text-slate-500`} />;
};

const StatusMark = ({ status, className = 'w-5 h-5' }: { status: StatusState; className?: string }) => {
    if (status === 'operational') return <CheckCircle2 className={`${className} text-emerald-500`} />;
    if (status === 'degraded') return <AlertTriangle className={`${className} text-amber-500`} />;
    return <XCircle className={`${className} text-red-500`} />;
};

const StatusPill = ({ status }: { status: StatusState }) => (
    <span className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-bold ${STATUS_COPY[status].badge}`}>
        <StatusMark status={status} className="w-3.5 h-3.5" />
        {status === 'operational' ? 'Operational' : status === 'degraded' ? 'Degraded' : 'Outage'}
    </span>
);

const createBuckets = (data: HistoryPoint[], historyKey: HistoryKey, range: StatusRange): Bucket[] => {
    const now = Date.now();
    const bucketCount = range === '24h' ? 48 : 30;
    const timeWindow = range === '24h' ? 24 * 60 * 60 * 1000 : 30 * 24 * 60 * 60 * 1000;
    const bucketDuration = timeWindow / bucketCount;
    const startTime = now - timeWindow;

    return Array.from({ length: bucketCount }, (_, index) => {
        const bucketStart = startTime + (index * bucketDuration);
        const bucketEnd = bucketStart + bucketDuration;
        const points = data.filter((point) => point.time >= bucketStart && point.time < bucketEnd);

        if (points.length === 0) {
            return { startTime: bucketStart, status: 'no-data' as const, percent: null };
        }

        const healthyPoints = points.filter((point) => isHealthyLatency(point[historyKey])).length;
        const percent = (healthyPoints / points.length) * 100;
        let status: StatusState = 'outage';
        if (percent >= 99) status = 'operational';
        else if (percent >= 90) status = 'degraded';

        return { startTime: bucketStart, status, percent };
    });
};

const bucketColor = (status: Bucket['status']) => {
    if (status === 'operational') return 'bg-emerald-500 hover:bg-emerald-400';
    if (status === 'degraded') return 'bg-amber-500 hover:bg-amber-400';
    if (status === 'outage') return 'bg-red-500 hover:bg-red-400';
    return 'bg-slate-200 dark:bg-white/10';
};

const UptimeHeatmap = ({
    serviceId,
    data,
    range,
    label,
}: {
    serviceId: HistoryKey;
    data: HistoryPoint[];
    range: StatusRange;
    label: string;
}) => {
    const [hoveredBucket, setHoveredBucket] = useState<Bucket | null>(null);
    const buckets = useMemo(() => createBuckets(data, serviceId, range), [data, range, serviceId]);
    const averageUptime = useMemo(() => {
        const measuredBuckets = buckets.filter((bucket) => bucket.percent !== null);
        if (measuredBuckets.length === 0) return null;
        return measuredBuckets.reduce((sum, bucket) => sum + (bucket.percent || 0), 0) / measuredBuckets.length;
    }, [buckets]);

    const currentLabel = hoveredBucket
        ? `${formatTime(hoveredBucket.startTime, range)}: ${formatPercent(hoveredBucket.percent)}`
        : `${range === '24h' ? '24-hour' : '30-day'} availability ${formatPercent(averageUptime)}`;

    return (
        <section className="space-y-2">
            <div className="flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
                <h3 className="text-sm font-bold text-slate-900 dark:text-white">{label}</h3>
                <p className="text-xs font-semibold text-slate-500 dark:text-slate-400">{currentLabel}</p>
            </div>
            <div className="flex h-9 w-full gap-1 sm:h-10" aria-label={`${label} uptime history`}>
                {buckets.map((bucket, index) => (
                    <div
                        key={`${bucket.startTime}-${index}`}
                        title={`${formatTime(bucket.startTime, range)} ${formatPercent(bucket.percent)}`}
                        className={`min-w-0 flex-1 rounded-sm transition-colors ${bucketColor(bucket.status)}`}
                        onMouseEnter={() => setHoveredBucket(bucket)}
                        onMouseLeave={() => setHoveredBucket(null)}
                    />
                ))}
            </div>
        </section>
    );
};

const IncidentTimeline = ({ incident, compact = false, embedded = false }: { incident: StatusIncident; compact?: boolean; embedded?: boolean }) => {
    const incidentStatus = normalizeStatus(incident.impact);
    const updates = incident.updates || [];

    const content = (
        <>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div>
                    <div className="mb-2 flex flex-wrap items-center gap-2">
                        <StatusPill status={incidentStatus} />
                        <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-bold text-slate-600 dark:border-white/10 dark:bg-white/5 dark:text-slate-300">
                            {formatIncidentState(incident.state)}
                        </span>
                    </div>
                    <h3 className="text-lg font-black text-slate-950 dark:text-white">{incident.title}</h3>
                    <p className="mt-1 text-sm font-semibold text-slate-500 dark:text-slate-400">
                        {incident.kind === 'MAINTENANCE' ? 'Scheduled maintenance' : 'Incident'}
                        {incident.affectedServices?.length ? ` · ${incident.affectedServices.join(', ')}` : ''}
                    </p>
                </div>
                <div className="text-xs font-bold text-slate-500 dark:text-slate-400 sm:text-right">
                    {incident.scheduledStart && <div>Starts {formatDateTime(incident.scheduledStart)}</div>}
                    {incident.scheduledEnd && <div>Ends {formatDateTime(incident.scheduledEnd)}</div>}
                    {!incident.scheduledStart && <div>Opened {formatDateTime(incident.startedAt || incident.createdAt)}</div>}
                </div>
            </div>

            <div className="mt-4 space-y-3">
                {(compact ? updates.slice(0, 2) : updates).map((update) => (
                    <div key={update.id} className="border-l-2 border-slate-200 pl-4 dark:border-white/10">
                        <div className="flex flex-wrap items-center gap-2 text-xs font-bold text-slate-500 dark:text-slate-400">
                            <span>{formatIncidentState(update.state)}</span>
                            <span>·</span>
                            <span>{formatDateTime(update.createdAt)}</span>
                        </div>
                        <p className="mt-1 text-sm leading-6 text-slate-600 dark:text-slate-300">{update.message}</p>
                    </div>
                ))}
            </div>
        </>
    );

    if (embedded) {
        return (
            <div className="border-t border-slate-200 pt-5 first:border-t-0 first:pt-0 dark:border-white/10">
                {content}
            </div>
        );
    }

    return (
        <article className={`${GLASS_CARD_CLASS} p-5`}>
            {content}
        </article>
    );
};

export const Status: React.FC = () => {
    const [data, setData] = useState<StatusResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
    const [range, setRange] = useState<StatusRange>('24h');
    const [fetchError, setFetchError] = useState<string | null>(null);

    const fetchStatus = useCallback(async (requestedRange: StatusRange, silent = false) => {
        if (!silent) {
            setIsRefreshing(true);
        }

        try {
            const res = await api.get('/status', { params: { range: requestedRange } });
            setData(res.data);
            setLastUpdated(new Date());
            setFetchError(null);
        } catch (error) {
            console.error(error);
            setFetchError('Live status could not be reached. Showing the last known snapshot.');
            setData((previous) => previous ?? {
                overall: 'outage',
                services: [],
                timestamp: Date.now(),
                history: [],
                activeIncidents: [],
                scheduledMaintenances: [],
                incidentHistory: [],
            });
        } finally {
            setLoading(false);
            if (!silent) {
                setIsRefreshing(false);
            }
        }
    }, []);

    useEffect(() => {
        fetchStatus(range);
        const interval = window.setInterval(() => fetchStatus(range, true), 60000);
        return () => window.clearInterval(interval);
    }, [fetchStatus, range]);

    const history = useMemo(() => (
        [...(data?.history || [])].sort((a, b) => a.time - b.time)
    ), [data?.history]);

    const services = data?.services || [];
    const activeIncidents = data?.activeIncidents || [];
    const scheduledMaintenances = data?.scheduledMaintenances || [];
    const incidentHistory = data?.incidentHistory || [];
    const overallStatus = normalizeStatus(data?.overall);
    const impactedServices = services.filter((service) => normalizeStatus(service.status) !== 'operational');
    const allLatencyValues = history.flatMap((point) => [point.api, point.db, point.storage]);
    const p95Latency = percentile(allLatencyValues, 0.95);
    const averageLatency = average(allLatencyValues);
    const latestTimestamp = data?.timestamp ? new Date(data.timestamp) : lastUpdated;

    const availability = useMemo(() => {
        const measured = history.flatMap((point) => [point.api, point.db, point.storage]);
        if (measured.length === 0) {
            if (services.length === 0) return null;
            const healthyServices = services.filter((service) => normalizeStatus(service.status) === 'operational').length;
            return (healthyServices / services.length) * 100;
        }

        const healthy = measured.filter(isHealthyLatency).length;
        return (healthy / measured.length) * 100;
    }, [history, services]);

    const chartDatasets = useMemo(() => ([
        {
            id: 'api',
            label: 'API',
            color: '#2563eb',
            data: history.map((point) => ({ date: formatTime(point.time, range), value: point.api })),
        },
        {
            id: 'db',
            label: 'Database',
            color: '#7c3aed',
            data: history.map((point) => ({ date: formatTime(point.time, range), value: point.db })),
        },
        {
            id: 'storage',
            label: 'Storage',
            color: '#ea580c',
            data: history.map((point) => ({ date: formatTime(point.time, range), value: point.storage })),
        },
    ]), [history, range]);

    if (loading && !data) {
        return (
            <main className="min-h-screen bg-slate-50 dark:bg-slate-950">
                <div className="flex min-h-[70vh] items-center justify-center">
                    <Spinner label="Checking systems..." />
                </div>
            </main>
        );
    }

    return (
        <main className="min-h-screen bg-slate-50 dark:bg-slate-950">
            <div className="mx-auto max-w-[112rem] px-6 py-8 sm:px-12 sm:py-12 md:px-16 lg:px-20 xl:px-28">
                <section className="mb-8 grid gap-6 lg:grid-cols-[minmax(0,1.5fr)_minmax(320px,0.7fr)] lg:items-stretch">
                    <div className={`relative overflow-hidden ${SURFACE_CARD_CLASS} p-6 sm:p-8`}>
                        <div
                            className="pointer-events-none absolute inset-0 opacity-70"
                            style={{
                                backgroundImage: 'radial-gradient(circle at 1px 1px, rgba(148, 163, 184, 0.10) 1px, transparent 0)',
                                backgroundSize: '22px 22px',
                            }}
                        />
                        <div className="relative">
                            <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                                <div className="max-w-3xl">
                                    <div className="mb-4 flex flex-wrap items-center gap-3">
                                        <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-slate-200 bg-white shadow-sm dark:border-white/10 dark:bg-white/5">
                                            <Activity className="h-5 w-5 text-modtale-accent" />
                                        </div>
                                        <StatusPill status={overallStatus} />
                                    </div>
                                    <h1 className="text-3xl font-black text-slate-950 dark:text-white sm:text-5xl">
                                        {STATUS_COPY[overallStatus].label}
                                    </h1>
                                    <p className="mt-4 max-w-2xl text-base leading-7 text-slate-600 dark:text-slate-300">
                                        {STATUS_COPY[overallStatus].summary}
                                    </p>
                                </div>

                                <button
                                    type="button"
                                    onClick={() => fetchStatus(range)}
                                    disabled={isRefreshing}
                                    className="inline-flex h-11 shrink-0 items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-700 shadow-sm transition hover:border-modtale-accent hover:text-slate-950 disabled:cursor-not-allowed disabled:opacity-60 dark:border-white/10 dark:bg-white/5 dark:text-slate-200 dark:hover:text-white"
                                >
                                    <RefreshCw className={`h-4 w-4 ${isRefreshing ? 'animate-spin' : ''}`} />
                                    Refresh
                                </button>
                            </div>

                            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                                <div className={`${SOFT_CARD_CLASS} p-4`}>
                                    <div className="mb-3 flex items-center justify-between">
                                        <Gauge className="h-5 w-5 text-emerald-500" />
                                        <span className="text-xs font-bold text-slate-500 dark:text-slate-400">Health</span>
                                    </div>
                                    <div className="text-2xl font-black text-slate-950 dark:text-white">{formatPercent(availability)}</div>
                                    <div className="mt-1 text-xs font-semibold text-slate-500 dark:text-slate-400">Measured availability</div>
                                </div>

                                <div className={`${SOFT_CARD_CLASS} p-4`}>
                                    <div className="mb-3 flex items-center justify-between">
                                        <BarChart3 className="h-5 w-5 text-blue-500" />
                                        <span className="text-xs font-bold text-slate-500 dark:text-slate-400">P95</span>
                                    </div>
                                    <div className="text-2xl font-black text-slate-950 dark:text-white">{formatLatency(p95Latency || 0)}</div>
                                    <div className="mt-1 text-xs font-semibold text-slate-500 dark:text-slate-400">Response latency</div>
                                </div>

                                <div className={`${SOFT_CARD_CLASS} p-4`}>
                                    <div className="mb-3 flex items-center justify-between">
                                        <Clock3 className="h-5 w-5 text-violet-500" />
                                        <span className="text-xs font-bold text-slate-500 dark:text-slate-400">Check</span>
                                    </div>
                                    <div className="text-lg font-black text-slate-950 dark:text-white">
                                        {latestTimestamp ? latestTimestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'n/a'}
                                    </div>
                                    <div className="mt-1 text-xs font-semibold text-slate-500 dark:text-slate-400">
                                        {lastUpdated ? `Fetched ${lastUpdated.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}` : 'Waiting for data'}
                                    </div>
                                </div>

                                <div className={`${SOFT_CARD_CLASS} p-4`}>
                                    <div className="mb-3 flex items-center justify-between">
                                        <ShieldCheck className="h-5 w-5 text-orange-500" />
                                        <span className="text-xs font-bold text-slate-500 dark:text-slate-400">Scope</span>
                                    </div>
                                    <div className="text-2xl font-black text-slate-950 dark:text-white">{services.length || 3}</div>
                                    <div className="mt-1 text-xs font-semibold text-slate-500 dark:text-slate-400">Core services monitored</div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <aside className="space-y-4">
                        <div className={`${GLASS_CARD_CLASS} p-5`}>
                            <div className="mb-4 flex items-center justify-between gap-3">
                                <div>
                                    <h2 className="text-lg font-black text-slate-950 dark:text-white">Active Incidents</h2>
                                    <p className="text-sm text-slate-500 dark:text-slate-400">Current service impact</p>
                                </div>
                                <StatusMark status={overallStatus} className="h-6 w-6" />
                            </div>

                            {fetchError && (
                                <div className="mb-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm font-semibold text-amber-700 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-200">
                                    {fetchError}
                                </div>
                            )}

                            {activeIncidents.length > 0 ? (
                                <div className="space-y-2">
                                    {activeIncidents.slice(0, 3).map((incident) => (
                                        <div key={incident.id} className={`${SOFT_CARD_CLASS} p-3`}>
                                            <div className="mb-1 flex items-center justify-between gap-3">
                                                <span className="truncate text-sm font-bold text-slate-900 dark:text-white">{incident.title}</span>
                                                <StatusMark status={normalizeStatus(incident.impact)} className="h-4 w-4 shrink-0" />
                                            </div>
                                            <p className="text-xs font-semibold text-slate-500 dark:text-slate-400">{formatIncidentState(incident.state)}</p>
                                        </div>
                                    ))}
                                </div>
                            ) : impactedServices.length > 0 ? (
                                <div className="space-y-2">
                                    {impactedServices.map((service) => {
                                        const serviceStatus = normalizeStatus(service.status);
                                        return (
                                            <div key={service.id} className={`${SOFT_CARD_CLASS} flex items-center justify-between gap-3 p-3`}>
                                                <div className="flex min-w-0 items-center gap-3">
                                                    {getServiceIcon(service.id, 'h-4 w-4')}
                                                    <span className="truncate text-sm font-bold text-slate-900 dark:text-white">{service.name}</span>
                                                </div>
                                                <StatusPill status={serviceStatus} />
                                            </div>
                                        );
                                    })}
                                </div>
                            ) : (
                                <div className={`${SOFT_CARD_CLASS} p-4`}>
                                    <div className="flex items-start gap-3">
                                        <div className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${STATUS_COPY.operational.iconBg}`}>
                                            <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                                        </div>
                                        <div>
                                            <p className="font-bold text-slate-950 dark:text-white">No active incidents</p>
                                            <p className="mt-1 text-sm leading-6 text-slate-600 dark:text-slate-300">All monitored services are currently healthy.</p>
                                        </div>
                                    </div>
                                </div>
                            )}
                        </div>

                        <div className={`${GLASS_CARD_CLASS} p-5`}>
                            <div className="mb-4 flex items-center gap-3">
                                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#5865F2] text-white shadow-sm">
                                    <DiscordBrandIcon className="h-5 w-5" />
                                </div>
                                <div>
                                    <h2 className="text-lg font-black text-slate-950 dark:text-white">Discord Alerts</h2>
                                    <p className="text-sm text-slate-600 dark:text-slate-300">Status changes post to Discord automatically.</p>
                                </div>
                            </div>
                            <a
                                href={DISCORD_INVITE_URL}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-[#5865F2] px-4 text-sm font-bold text-white shadow-sm transition hover:bg-[#4752C4]"
                            >
                                <Bell className="h-4 w-4" />
                                Join Discord
                                <ArrowUpRight className="h-4 w-4" />
                            </a>
                        </div>
                    </aside>
                </section>

                {(activeIncidents.length > 0 || scheduledMaintenances.length > 0) && (
                    <section className={`mb-8 grid gap-6 ${
                        activeIncidents.length > 0 && scheduledMaintenances.length > 0
                            ? 'lg:grid-cols-[minmax(0,1.15fr)_minmax(320px,0.85fr)]'
                            : 'lg:grid-cols-1'
                    }`}>
                        {activeIncidents.length > 0 && (
                            <div className={`${GLASS_CARD_CLASS} p-5 sm:p-6`}>
                                <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                                    <div>
                                        <div className="mb-2 flex items-center gap-3">
                                            <AlertTriangle className="h-5 w-5 text-amber-500" />
                                            <h2 className="text-2xl font-black text-slate-950 dark:text-white">Current Downtime Updates</h2>
                                        </div>
                                        <p className="text-sm text-slate-500 dark:text-slate-400">Admin timeline updates as downtime moves from investigation to resolution.</p>
                                    </div>
                                    <StatusPill status={overallStatus} />
                                </div>
                                <div className="space-y-5">
                                    {activeIncidents.map((incident) => (
                                        <IncidentTimeline key={incident.id} incident={incident} embedded />
                                    ))}
                                </div>
                            </div>
                        )}

                        {scheduledMaintenances.length > 0 && (
                            <div className={`${GLASS_CARD_CLASS} p-5 sm:p-6`}>
                                <div className="mb-5 flex items-start gap-3">
                                    <Clock3 className="mt-1 h-5 w-5 text-violet-500" />
                                    <div>
                                        <h2 className="text-2xl font-black text-slate-950 dark:text-white">Scheduled Downtime</h2>
                                        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Planned maintenance windows.</p>
                                    </div>
                                </div>
                                <div className="space-y-5">
                                    {scheduledMaintenances.map((incident) => (
                                        <IncidentTimeline key={incident.id} incident={incident} compact embedded />
                                    ))}
                                </div>
                            </div>
                        )}
                    </section>
                )}

                <section className="mb-8 grid gap-4 lg:grid-cols-3">
                    {(services.length > 0 ? services : [
                        { id: 'api', name: 'API Gateway', status: overallStatus, latency: 0 },
                        { id: 'database', name: 'Database (Atlas)', status: overallStatus, latency: 0 },
                        { id: 'storage', name: 'Storage (R2)', status: overallStatus, latency: 0 },
                    ]).map((service) => {
                        const meta = SERVICE_META[service.id] || { historyKey: 'api' as HistoryKey, label: service.name };
                        const values = history.map((point) => point[meta.historyKey]);
                        const serviceAverage = average(values);
                        const serviceP95 = percentile(values, 0.95);
                        const serviceStatus = normalizeStatus(service.status);

                        return (
                            <article key={service.id} className={`${GLASS_CARD_CLASS} p-5 transition-colors hover:border-slate-300 dark:hover:border-white/20`}>
                                <div className="mb-5 flex items-start justify-between gap-4">
                                    <div className="flex items-center gap-3">
                                        <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-slate-200 bg-white shadow-sm dark:border-white/10 dark:bg-slate-800">
                                            {getServiceIcon(service.id)}
                                        </div>
                                        <div>
                                            <h2 className="font-black text-slate-950 dark:text-white">{meta.label}</h2>
                                            <p className="text-sm text-slate-500 dark:text-slate-400">Current {formatLatency(service.latency)}</p>
                                        </div>
                                    </div>
                                    <StatusPill status={serviceStatus} />
                                </div>

                                <div className="grid grid-cols-2 gap-3">
                                    <div className={`${SOFT_CARD_CLASS} p-3`}>
                                        <div className="text-xs font-bold text-slate-500 dark:text-slate-400">Average</div>
                                        <div className="mt-1 text-lg font-black text-slate-950 dark:text-white">{formatLatency(serviceAverage || 0)}</div>
                                    </div>
                                    <div className={`${SOFT_CARD_CLASS} p-3`}>
                                        <div className="text-xs font-bold text-slate-500 dark:text-slate-400">P95</div>
                                        <div className="mt-1 text-lg font-black text-slate-950 dark:text-white">{formatLatency(serviceP95 || 0)}</div>
                                    </div>
                                </div>
                            </article>
                        );
                    })}
                </section>

                <section className={`mb-8 ${GLASS_CARD_CLASS} p-5 sm:p-6`}>
                    <div className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                        <div>
                            <h2 className="text-2xl font-black text-slate-950 dark:text-white">Availability History</h2>
                            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Green means healthy checks, amber means degraded checks, red means failed checks.</p>
                        </div>
                        <div className="inline-flex rounded-xl border border-slate-200 bg-slate-100 p-1 dark:border-white/10 dark:bg-slate-950/40" role="group" aria-label="Status history range">
                            {(['24h', '30d'] as StatusRange[]).map((option) => (
                                <button
                                    key={option}
                                    type="button"
                                    onClick={() => setRange(option)}
                                    className={`h-9 rounded-lg px-4 text-sm font-bold transition ${
                                        range === option
                                            ? 'bg-white text-slate-950 shadow-sm dark:bg-modtale-card dark:text-white'
                                            : 'text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white'
                                    }`}
                                >
                                    {option === '24h' ? '24 Hours' : '30 Days'}
                                </button>
                            ))}
                        </div>
                    </div>

                    <div className="space-y-6">
                        <UptimeHeatmap label="API Gateway" serviceId="api" data={history} range={range} />
                        <UptimeHeatmap label="Database" serviceId="db" data={history} range={range} />
                        <UptimeHeatmap label="Storage" serviceId="storage" data={history} range={range} />
                    </div>

                    <div className="mt-6 flex flex-wrap gap-4 border-t border-slate-200 pt-5 text-xs font-bold text-slate-500 dark:border-white/10 dark:text-slate-400">
                        <span className="inline-flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full bg-emerald-500" /> Operational</span>
                        <span className="inline-flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full bg-amber-500" /> Degraded</span>
                        <span className="inline-flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full bg-red-500" /> Outage</span>
                        <span className="inline-flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full bg-slate-200 dark:bg-white/10" /> No data</span>
                    </div>
                </section>

                <section className={`mb-8 ${GLASS_CARD_CLASS} p-5 sm:p-6`}>
                    <div className="mb-6 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
                        <div>
                            <h2 className="text-2xl font-black text-slate-950 dark:text-white">Response Latency</h2>
                            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                                Average across selected range: {formatLatency(averageLatency || 0)}
                            </p>
                        </div>
                    </div>
                    <div className="h-[28rem] min-h-[22rem] w-full">
                        <LineChart datasets={chartDatasets} yAxisFormatter={(value) => formatLatency(value)} />
                    </div>
                </section>

                <section className={`${GLASS_CARD_CLASS} p-5 sm:p-6`}>
                    <div className="mb-5 flex items-start gap-3">
                        <CheckCircle2 className="mt-1 h-5 w-5 text-emerald-500" />
                        <div>
                            <h2 className="text-2xl font-black text-slate-950 dark:text-white">Past Incidents</h2>
                            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Recent resolved or canceled events.</p>
                        </div>
                    </div>
                    {incidentHistory.length ? (
                        <div className="space-y-5">
                            {incidentHistory.slice(0, 6).map((incident) => (
                                <IncidentTimeline key={incident.id} incident={incident} compact embedded />
                            ))}
                        </div>
                    ) : (
                        <div className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4 text-sm font-semibold text-slate-500 dark:border-white/10 dark:bg-white/[0.04] dark:text-slate-400">
                            No past incidents yet.
                        </div>
                    )}
                </section>
            </div>
        </main>
    );
};
