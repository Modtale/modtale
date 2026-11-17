import React, { useEffect, useState } from 'react';
import { api, BACKEND_URL } from '@/utils/api';
import type { Modjam, User } from '@/types';
import { Spinner } from '@/components/ui/Spinner';
import { Trophy, Plus, Calendar, Users, AlertCircle } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { JamCreateModal } from '@/modules/jam/components/JamCreateModal';
import { OptimizedImage } from '@/components/ui/OptimizedImage';
import { useSSRData } from '@/context/SSRContext';

const resolveUrl = (url?: string | null) => {
    if (!url) return '';
    if (url.startsWith('/api') || url.startsWith('/uploads')) {
        return `${BACKEND_URL}${url}`;
    }
    return url;
};

export const JamCard: React.FC<{ jam: Modjam }> = ({ jam }) => {
    const resolvedBanner = resolveUrl(jam.bannerUrl);
    const resolvedIcon = resolveUrl((jam as any).imageUrl || null);

    const formatJamDate = () => {
        if (!jam.startDate || !jam.endDate) return 'Unknown dates';
        const now = new Date();
        const start = new Date(jam.startDate);
        const end = new Date(jam.endDate);

        if (jam.status === 'UPCOMING' || now < start) {
            return `Starts ${start.toLocaleDateString()}`;
        } else if (jam.status === 'ACTIVE' || (now >= start && now <= end)) {
            return `Ends ${end.toLocaleDateString()}`;
        } else {
            return `Ended ${end.toLocaleDateString()}`;
        }
    };

    return (
        <Link to={`/jam/${jam.slug}`} className="group relative flex flex-col h-full bg-white dark:bg-slate-900 rounded-2xl border-2 border-slate-200/80 dark:border-white/10 hover:border-modtale-accent/50 dark:hover:border-modtale-accent/50 transition-all duration-300 hover:-translate-y-1 overflow-hidden shadow-lg hover:shadow-xl transform-gpu isolate">
            <div className="relative w-full aspect-[3/1] bg-slate-100 dark:bg-slate-800 shrink-0 overflow-hidden border-b border-slate-200 dark:border-white/5 rounded-t-xl z-0">
                {resolvedBanner ? (
                    <OptimizedImage
                        src={resolvedBanner}
                        alt={`${jam.title} banner`}
                        baseWidth={640}
                        className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
                    />
                ) : (
                    <div className="w-full h-full flex items-center justify-center bg-slate-200/50 dark:bg-slate-800/50">
                        <Trophy className="w-8 h-8 opacity-20 text-slate-500" />
                    </div>
                )}
                <div className="absolute top-3 right-3 z-20">
                    <div className={`text-[10px] font-black uppercase tracking-widest px-2.5 py-1 rounded-md flex items-center shadow-sm backdrop-blur-md border ${jam.status === 'ACTIVE' ? 'bg-modtale-accent/90 border-modtale-accent text-white' : 'bg-black/60 border-white/20 text-white'}`}>
                        {jam.status}
                    </div>
                </div>
            </div>

            <div className="flex flex-col flex-1 px-6 pb-6 relative items-center text-center">
                <div className="w-16 h-16 shrink-0 -mt-8 mb-4 relative z-10 rounded-2xl bg-white dark:bg-slate-900 shadow-xl border-4 border-white dark:border-slate-900 overflow-hidden flex items-center justify-center transition-colors duration-300 group-hover:border-modtale-accent/50">
                    {resolvedIcon ? (
                        <OptimizedImage
                            src={resolvedIcon}
                            alt={jam.title}
                            baseWidth={64}
                            initialQuality="standard"
                            className="w-full h-full object-cover"
                        />
                    ) : (
                        <Trophy className="w-6 h-6 text-slate-400" />
                    )}
                </div>

                <h3 className="text-xl font-black text-slate-900 dark:text-white group-hover:text-modtale-accent transition-colors line-clamp-2 w-full mb-1">
                    {jam.title}
                </h3>

                <div className="flex items-center justify-center gap-1 text-xs text-slate-500 dark:text-slate-400 mb-6">
                    <span>Hosted by</span>
                    <span className="font-bold text-slate-700 dark:text-slate-300">{jam.hostName}</span>
                </div>

                <div className="mt-auto w-full flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-2 pt-4 border-t border-slate-100 dark:border-white/5">
                    <div className="flex items-center gap-2 text-xs font-bold text-slate-600 dark:text-slate-300 bg-slate-50 dark:bg-white/5 px-3 py-2 rounded-lg border border-slate-200/50 dark:border-white/5">
                        <Users className="w-4 h-4 text-modtale-accent" />
                        <span>{jam.participantIds?.length || 0} Participants</span>
                    </div>
                    <div className="flex items-center gap-2 text-xs font-bold text-slate-600 dark:text-slate-300 bg-slate-50 dark:bg-white/5 px-3 py-2 rounded-lg border border-slate-200/50 dark:border-white/5">
                        <Calendar className="w-4 h-4 text-modtale-accent" />
                        <span>{formatJamDate()}</span>
                    </div>
                </div>
            </div>
        </Link>
    );
};

export const JamsList: React.FC<{ currentUser: User | null }> = ({ currentUser }) => {
    const navigate = useNavigate();
    const { initialData: ssrData } = useSSRData();
    const hasSsrJams = ssrData?.jamsDataReady === true && Array.isArray(ssrData?.jamsData);
    const [jams, setJams] = useState<Modjam[]>(() => hasSsrJams ? ssrData.jamsData : []);
    const [loading, setLoading] = useState(!hasSsrJams);
    const [isCreating, setIsCreating] = useState(false);
    const [loadError, setLoadError] = useState(false);

    useEffect(() => {
        if (hasSsrJams) return;
        const controller = new AbortController();
        setLoading(true);
        api.get('/modjams', { signal: controller.signal }).then(res => {
            if (controller.signal.aborted) return;
            setJams(Array.isArray(res.data) ? res.data : []);
            setLoadError(false);
        }).catch(() => { if (!controller.signal.aborted) setLoadError(true); })
            .finally(() => { if (!controller.signal.aborted) setLoading(false); });
        return () => controller.abort();
    }, [hasSsrJams]);

    if (loading) {
        return <div className="min-h-screen flex items-center justify-center"><Spinner className="w-8 h-8" fullScreen={false} /></div>;
    }

    const timelineJams = jams
        .filter(jam => jam.startDate && jam.endDate)
        .sort((a, b) => new Date(a.startDate || 0).getTime() - new Date(b.startDate || 0).getTime());

    const timelineStart = timelineJams.length
        ? new Date(Math.min(...timelineJams.map(jam => new Date(jam.startDate || 0).getTime())))
        : new Date();
    timelineStart.setHours(0, 0, 0, 0);

    const timelineEnd = timelineJams.length
        ? new Date(Math.max(...timelineJams.map(jam => new Date(jam.endDate || 0).getTime())))
        : new Date();
    timelineEnd.setHours(0, 0, 0, 0);

    const totalTimelineDays = Math.max(
        14,
        Math.ceil((timelineEnd.getTime() - timelineStart.getTime()) / (1000 * 60 * 60 * 24)) + 14
    );

    const dayWidth = 24;
    const timelineWidth = totalTimelineDays * dayWidth;

    const monthTicks: { label: string; left: number }[] = [];
    if (timelineJams.length > 0) {
        const cursor = new Date(timelineStart);
        cursor.setDate(1);
        while (cursor.getTime() <= timelineEnd.getTime() + 1000 * 60 * 60 * 24 * 30) {
            const daysFromStart = Math.floor((cursor.getTime() - timelineStart.getTime()) / (1000 * 60 * 60 * 24));
            if (daysFromStart >= 0 && daysFromStart <= totalTimelineDays) {
                monthTicks.push({
                    label: cursor.toLocaleDateString(undefined, { month: 'short', year: 'numeric' }),
                    left: daysFromStart * dayWidth
                });
            }
            cursor.setMonth(cursor.getMonth() + 1);
        }
    }

    return (
        <div className="max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 pt-8 md:pt-12 pb-28">
            {isCreating && <JamCreateModal onClose={() => setIsCreating(false)} onCreated={jam => { setIsCreating(false); navigate(`/jam/${jam.slug}/edit`); }} />}
            {loadError && <p role="alert" className="mb-6 flex items-center gap-2 rounded-xl border border-red-500/20 bg-red-500/10 p-4 text-sm text-red-600 dark:text-red-400"><AlertCircle className="size-4" /> We could not load the jams. Please refresh to try again.</p>}
            <div className="w-full animate-in fade-in slide-in-from-bottom-4 duration-700">
                <div className="flex flex-col sm:flex-row items-start sm:items-end justify-between gap-6 mb-8 border-b border-slate-200 dark:border-white/10 pb-6">
                    <div className="flex flex-col sm:flex-row sm:items-center gap-4">
                        <div className="flex items-center gap-3">
                            <Calendar className="w-8 h-8 text-modtale-accent" />
                            <h1 className="text-3xl sm:text-4xl font-black text-slate-900 dark:text-white tracking-tight">Jam Events</h1>
                        </div>
                    </div>
                    {currentUser && (
                        <button type="button" onClick={() => setIsCreating(true)} className="h-12 px-6 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-lg font-bold text-sm shadow-md shadow-modtale-accent/20 transition-all hover:-translate-y-0.5 active:scale-95 flex items-center gap-2 shrink-0">
                            <Plus className="w-5 h-5" /> Host a Jam
                        </button>
                    )}
                </div>

                <section className="rounded-2xl border border-slate-200 dark:border-white/10 bg-white/80 dark:bg-slate-900/70 shadow-sm">
                    {timelineJams.length > 0 ? (
                        <div
                            className="overflow-x-auto overflow-y-hidden px-4 sm:px-6 pb-6 pt-5 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
                        >
                            <div className="relative min-h-[8rem]" style={{ width: `${timelineWidth}px` }}>
                                <div className="sticky left-0 top-0 z-10 mb-4 h-8 bg-gradient-to-r from-white via-white/90 to-transparent dark:from-slate-900 dark:via-slate-900/90 dark:to-transparent" />
                                <div className="absolute inset-0 bg-[linear-gradient(to_right,rgba(148,163,184,0.2)_1px,transparent_1px)] dark:bg-[linear-gradient(to_right,rgba(100,116,139,0.2)_1px,transparent_1px)] bg-[size:24px_100%] pointer-events-none" />
                                <div className="relative h-12 mb-3 border-b border-slate-200 dark:border-white/10">
                                    {monthTicks.map(tick => (
                                        <div key={`${tick.label}-${tick.left}`} className="absolute top-0 h-full" style={{ left: `${tick.left}px` }}>
                                            <div className="h-4 border-l border-slate-300/80 dark:border-slate-500/70" />
                                            <div className="mt-1 text-[11px] font-bold text-slate-500 dark:text-slate-400 whitespace-nowrap">
                                                {tick.label}
                                            </div>
                                        </div>
                                    ))}
                                </div>

                                <div className="space-y-2.5">
                                    {timelineJams.map(jam => {
                                        const start = new Date(jam.startDate || 0);
                                        const end = new Date(jam.endDate || 0);
                                        const offsetDays = Math.max(0, Math.floor((start.getTime() - timelineStart.getTime()) / (1000 * 60 * 60 * 24)));
                                        const durationDays = Math.max(2, Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1);
                                        const barWidth = durationDays * dayWidth;
                                        const isActive = jam.status === 'ACTIVE';
                                        const isDone = ['VOTING', 'COMPLETED'].includes(jam.status);

                                        return (
                                            <Link
                                                key={jam.id}
                                                to={`/jam/${jam.slug}`}
                                                className={`group relative block h-12 rounded-xl border shadow-sm transition-all ${
                                                    isActive
                                                        ? 'bg-modtale-accent text-white border-modtale-accent'
                                                        : isDone
                                                            ? 'bg-slate-200/80 dark:bg-slate-700/70 border-slate-300 dark:border-slate-600 text-slate-700 dark:text-slate-100'
                                                            : 'bg-white dark:bg-slate-800 border-slate-300/90 dark:border-slate-600 text-slate-900 dark:text-white'
                                                }`}
                                                style={{ marginLeft: `${offsetDays * dayWidth}px`, width: `${barWidth}px` }}
                                            >
                                                <div className="h-full px-3 flex items-center justify-between gap-3">
                                                    <div className="min-w-0">
                                                        <p className="text-xs font-black uppercase tracking-wide truncate">{jam.title}</p>
                                                        <p className={`text-[11px] font-semibold truncate ${isActive ? 'text-white/90' : 'text-slate-500 dark:text-slate-300'}`}>
                                                            {start.toLocaleDateString()} - {end.toLocaleDateString()}
                                                        </p>
                                                    </div>
                                                    <div className={`text-[10px] font-black uppercase tracking-wider px-2 py-1 rounded-md border ${
                                                        isActive
                                                            ? 'bg-white/15 border-white/25 text-white'
                                                            : 'bg-slate-100 dark:bg-slate-900/80 border-slate-300 dark:border-slate-500'
                                                    }`}>
                                                        {jam.status}
                                                    </div>
                                                </div>
                                            </Link>
                                        );
                                    })}
                                </div>
                            </div>
                        </div>
                    ) : (
                        <div className="px-6 py-14 text-center text-slate-500 dark:text-slate-400">
                            <p className="text-sm font-bold">No jams to show yet.</p>
                        </div>
                    )}
                </section>
            </div>
        </div>
    );
};
