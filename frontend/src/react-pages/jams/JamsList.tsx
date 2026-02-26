import React, { useEffect, useState, useMemo } from 'react';
import { api, BACKEND_URL } from '@/utils/api';
import type { Modjam, User } from '@/types';
import { Spinner } from '@/components/ui/Spinner';
import { Trophy, Plus, ArrowLeft, Calendar, Users, LayoutGrid } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { JamBuilder } from '@/components/jams/JamBuilder.tsx';

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
                    <img
                        src={resolvedBanner}
                        alt=""
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
                        <img
                            src={resolvedIcon}
                            alt={jam.title}
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

                <div className="mt-auto w-full flex items-center justify-between pt-4 border-t border-slate-100 dark:border-white/5">
                    <div className="flex items-center gap-2 text-xs font-bold text-slate-600 dark:text-slate-300 bg-slate-50 dark:bg-white/5 px-3 py-2 rounded-lg border border-slate-200/50 dark:border-white/5">
                        <LayoutGrid className="w-4 h-4 text-modtale-accent" />
                        <span>{jam.participantIds?.length || 0} Entries</span>
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

const formatDay = (date: Date) => {
    const d = date.getDate();
    const suffix = ['th', 'st', 'nd', 'rd'][(d % 10 > 3 || Math.floor(d % 100 / 10) === 1) ? 0 : d % 10];
    return `${d}${suffix}`;
};

export const JamsList: React.FC<{ currentUser: User | null }> = ({ currentUser }) => {
    const navigate = useNavigate();
    const [jams, setJams] = useState<Modjam[]>([]);
    const [loading, setLoading] = useState(true);
    const [isCreating, setIsCreating] = useState(false);
    const [step, setStep] = useState(0);
    const [isSavingJam, setIsSavingJam] = useState(false);

    const [metaData, setMetaData] = useState({
        id: '',
        slug: '',
        title: '',
        description: '',
        imageUrl: '',
        bannerUrl: '',
        startDate: '',
        endDate: '',
        votingEndDate: '',
        allowPublicVoting: true,
        allowConcurrentVoting: false,
        showResultsBeforeVotingEnds: true,
        categories: []
    });

    const [activeTab, setActiveTab] = useState<'details' | 'categories' | 'settings'>('details');

    const DAY_WIDTH = 80;
    const TRACK_HEIGHT = 60;
    const TRACK_GAP = 12;

    useEffect(() => {
        api.get('/modjams').then(res => {
            setJams(res.data);
            setLoading(false);
        }).catch(() => setLoading(false));
    }, []);

    const { startDate, endDate, totalDays, days } = useMemo(() => {
        const start = new Date();
        start.setDate(start.getDate() - 2); // 48 hours back
        start.setHours(0, 0, 0, 0);
        const total = 90;
        const end = new Date(start);
        end.setDate(end.getDate() + total);

        const dArr = Array.from({length: total}).map((_, i) => {
            const date = new Date(start);
            date.setDate(date.getDate() + i);
            return date;
        });

        return { startDate: start, endDate: end, totalDays: total, days: dArr };
    }, []);

    const { tracks, jamTracks, sortedJams } = useMemo(() => {
        const t: Modjam[][] = [];
        const jt: Record<string, number> = {};

        const sj = [...jams]
            .filter(jam => jam.startDate && (jam.votingEndDate || jam.endDate))
            .filter(jam => new Date(jam.votingEndDate || jam.endDate).getTime() >= startDate.getTime() && new Date(jam.startDate).getTime() <= endDate.getTime())
            .sort((a, b) => new Date(a.startDate).getTime() - new Date(b.startDate).getTime());

        sj.forEach(jam => {
            let placed = false;
            for (let i = 0; i < t.length; i++) {
                const track = t[i];
                const lastJam = track[track.length - 1];
                if (new Date(lastJam.votingEndDate || lastJam.endDate).getTime() + (1000 * 60 * 60 * 24 * 1) < new Date(jam.startDate).getTime()) {
                    track.push(jam);
                    jt[jam.id] = i;
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                t.push([jam]);
                jt[jam.id] = t.length - 1;
            }
        });

        return { tracks: t, jamTracks: jt, sortedJams: sj };
    }, [jams, startDate, endDate]);

    const handleSaveJam = async () => {
        setIsSavingJam(true);
        try {
            let res: any;
            if (metaData.id) {
                res = await api.put(`/modjams/${metaData.id}`, metaData);
            } else {
                res = await api.post('/modjams', metaData);
                setMetaData(prev => ({ ...prev, id: res.data.id, slug: res.data.slug }));
            }

            const currentId = res.data.id;
            let filesUploaded = false;

            if ((metaData as any).iconFile) {
                const fd = new FormData();
                fd.append('file', (metaData as any).iconFile);
                await api.put(`/modjams/${currentId}/icon`, fd, { headers: { 'Content-Type': 'multipart/form-data' }});
                filesUploaded = true;
            }

            if ((metaData as any).bannerFile) {
                const fd = new FormData();
                fd.append('file', (metaData as any).bannerFile);
                await api.put(`/modjams/${currentId}/banner`, fd, { headers: { 'Content-Type': 'multipart/form-data' }});
                filesUploaded = true;
            }

            if (filesUploaded) {
                const finalRes = await api.get(`/modjams/${res.data.slug}`);
                res = finalRes;
            }

            setJams(prev => {
                const filtered = prev.filter(j => j.id !== res.data.id);
                return [res.data, ...filtered];
            });
            setIsSavingJam(false);
            return res.data;
        } catch (e) {
            setIsSavingJam(false);
            return null;
        }
    };

    const handlePublish = async () => {
        let currentId = metaData.id;
        let currentSlug = metaData.slug;

        if (!currentId) {
            const savedJam = await handleSaveJam();
            if (!savedJam) return;
            currentId = savedJam.id;
            currentSlug = savedJam.slug;
        }

        setIsSavingJam(true);
        try {
            const updated = { ...metaData, status: 'PUBLISHED' };
            await api.put(`/modjams/${currentId}`, updated);

            setIsCreating(false);
            setStep(0);
            navigate(`/jam/${currentSlug}`);
        } catch (e) {} finally {
            setIsSavingJam(false);
        }
    };

    if (isCreating) {
        if (step === 1) {
            return (
                <div className="max-w-xl mx-auto pt-24 px-6 animate-in fade-in zoom-in-95 pb-32">
                    <button type="button" onClick={() => setIsCreating(false)} className="text-slate-500 font-bold mb-10 flex items-center gap-2 hover:text-slate-900 dark:hover:text-white transition-colors">
                        <ArrowLeft className="w-4 h-4" /> Cancel
                    </button>
                    <div className="mb-10">
                        <h1 className="text-4xl font-black tracking-tight mb-2">Host a Jam</h1>
                        <p className="text-slate-500 font-medium text-lg">Set the stage for your community event.</p>
                    </div>

                    <div className="space-y-6 bg-white dark:bg-modtale-card p-10 rounded-2xl border border-slate-200 dark:border-white/5 shadow-2xl">
                        <div className="space-y-3">
                            <label className="text-[10px] font-black uppercase text-slate-500 tracking-widest ml-2">Event Title</label>
                            <input
                                value={metaData.title}
                                onChange={e => setMetaData(prev => ({...prev, title: e.target.value}))}
                                className="w-full bg-slate-50 dark:bg-black/20 border-none rounded-xl px-6 py-5 font-black text-xl shadow-inner outline-none focus:ring-2 focus:ring-modtale-accent transition-all"
                                placeholder="Summer Hackathon 2026"
                            />
                        </div>
                        <button
                            type="button"
                            onClick={() => setStep(2)}
                            disabled={!metaData.title || metaData.title.trim().length < 5}
                            className="w-full h-14 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-xl font-black text-lg shadow-md shadow-modtale-accent/20 transition-all flex items-center justify-center gap-2 disabled:opacity-50 hover:scale-[1.02] active:scale-95"
                        >
                            Draft Event Details
                        </button>
                    </div>
                </div>
            );
        }

        return (
            <JamBuilder
                metaData={metaData}
                setMetaData={setMetaData}
                handleSave={handleSaveJam}
                onPublish={handlePublish}
                isLoading={isSavingJam}
                activeTab={activeTab}
                setActiveTab={setActiveTab}
                onBack={() => setStep(1)}
            />
        );
    }

    if (loading) {
        return <div className="min-h-screen flex items-center justify-center"><Spinner className="w-8 h-8" fullScreen={false} /></div>;
    }

    const startMonth = startDate.toLocaleString('default', { month: 'long', year: 'numeric' });
    const endMonth = endDate.toLocaleString('default', { month: 'long', year: 'numeric' });
    const monthLabel = startMonth === endMonth ? startMonth : `${startMonth} - ${endMonth}`;

    const now = new Date().getTime();
    const isNowInWindow = now >= startDate.getTime() && now <= endDate.getTime();
    const nowLeftPx = ((now - startDate.getTime()) / (1000 * 60 * 60 * 24)) * DAY_WIDTH;

    // A past jam is one that is VOTING or COMPLETED AND its end date is completely before the calendar's 48h lookback window
    const pastJams = jams.filter(jam => {
        if (!['VOTING', 'COMPLETED'].includes(jam.status)) return false;
        if (!jam.votingEndDate && !jam.endDate) return false;
        const jamEndMs = new Date(jam.votingEndDate || jam.endDate).getTime();
        return jamEndMs < startDate.getTime();
    });

    return (
        <div className="max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 pt-8 md:pt-16 pb-32">
            <div className="w-full animate-in fade-in slide-in-from-bottom-4 duration-700">
                <div className="flex flex-col sm:flex-row items-start sm:items-end justify-between gap-6 mb-8 border-b border-slate-200 dark:border-white/10 pb-6">
                    <div className="flex flex-col sm:flex-row sm:items-center gap-4">
                        <div className="flex items-center gap-3">
                            <Calendar className="w-8 h-8 text-modtale-accent" />
                            <h1 className="text-3xl sm:text-4xl font-black text-slate-900 dark:text-white tracking-tight">Event Calendar</h1>
                        </div>
                        <div className="text-xs font-bold text-slate-600 dark:text-slate-300 bg-slate-100 dark:bg-slate-800/50 px-3 py-1.5 rounded-md border border-slate-200 dark:border-white/5 sm:ml-2">
                            {monthLabel}
                        </div>
                    </div>
                    {currentUser && (
                        <button type="button" onClick={() => { setIsCreating(true); setStep(1); }} className="h-12 px-6 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-lg font-bold text-sm shadow-md shadow-modtale-accent/20 transition-all hover:-translate-y-0.5 active:scale-95 flex items-center gap-2 shrink-0">
                            <Plus className="w-5 h-5" /> Host a Jam
                        </button>
                    )}
                </div>

                <div className="overflow-x-auto custom-scrollbar pb-12 pt-2 relative w-full">
                    <div style={{ width: `${totalDays * DAY_WIDTH}px`, minHeight: `${Math.max(tracks.length * (TRACK_HEIGHT + TRACK_GAP) + 60, 240)}px` }} className="relative">
                        <div className="flex absolute top-0 left-0 right-0 h-10 border-b border-slate-200 dark:border-white/10 z-0">
                            {days.map((day, i) => {
                                const isFirstDayOfMonth = day.getDate() === 1;
                                return (
                                    <div key={day.toISOString()} style={{ width: `${DAY_WIDTH}px` }} className={`shrink-0 flex flex-col justify-end pb-1 text-left border-l border-slate-200/40 dark:border-white/5 pl-2 ${isFirstDayOfMonth ? 'border-dashed border-slate-400 dark:border-white/20 bg-slate-100/30 dark:bg-white/[0.02]' : ''}`}>
                                        {isFirstDayOfMonth || i === 0 ? (
                                            <span className="text-[9px] font-black uppercase tracking-widest text-modtale-accent leading-none mb-0.5">
                                                {day.toLocaleString('default', { month: 'short' })}
                                            </span>
                                        ) : null}
                                        <span className="text-[10px] font-bold text-slate-500 leading-none">
                                            {formatDay(day)}
                                        </span>
                                    </div>
                                );
                            })}
                        </div>

                        <div className="absolute top-10 left-0 right-0 bottom-0 flex pointer-events-none z-0">
                            {days.map(day => {
                                const isFirstDayOfMonth = day.getDate() === 1;
                                return (
                                    <div key={day.toISOString()} style={{ width: `${DAY_WIDTH}px` }} className={`shrink-0 border-l border-slate-200/40 dark:border-white/5 ${isFirstDayOfMonth ? 'border-dashed border-slate-400 dark:border-white/20 bg-slate-100/30 dark:bg-white/[0.01]' : ''}`} />
                                );
                            })}
                        </div>

                        {isNowInWindow && (
                            <div className="absolute top-0 bottom-0 w-px bg-modtale-accent z-20" style={{ left: `${nowLeftPx}px` }}>
                                <div className="absolute top-1 left-1/2 -translate-x-1/2 px-2 py-0.5 bg-modtale-accent text-white text-[8px] font-black uppercase tracking-widest rounded shadow-sm">Now</div>
                            </div>
                        )}

                        <div className="absolute top-14 left-0 right-0 z-10">
                            {sortedJams.map(jam => {
                                const startMs = new Date(jam.startDate).getTime();
                                const endMs = new Date(jam.votingEndDate || jam.endDate).getTime();
                                const windowStartMs = startDate.getTime();

                                const renderStart = Math.max(startMs, windowStartMs);
                                const renderEnd = Math.min(endMs, endDate.getTime());

                                const leftPx = ((renderStart - windowStartMs) / (1000 * 60 * 60 * 24)) * DAY_WIDTH;
                                const widthPx = ((renderEnd - renderStart) / (1000 * 60 * 60 * 24)) * DAY_WIDTH;

                                const trackIdx = jamTracks[jam.id] || 0;
                                const topPx = trackIdx * (TRACK_HEIGHT + TRACK_GAP);

                                const jamIcon = resolveUrl((jam as any).imageUrl || null);

                                return (
                                    <Link
                                        key={jam.id}
                                        to={`/jam/${jam.slug}`}
                                        className="absolute flex flex-col justify-center px-3 py-2 rounded-xl shadow-sm hover:shadow-md hover:-translate-y-0.5 transition-all cursor-pointer backdrop-blur-md border border-modtale-accent/20 hover:border-modtale-accent/40 group overflow-hidden bg-modtale-accent/5 dark:bg-modtale-accent/10"
                                        style={{
                                            left: `${leftPx}px`,
                                            width: `${Math.max(widthPx, 220)}px`,
                                            top: `${topPx}px`,
                                            height: `${TRACK_HEIGHT}px`
                                        }}
                                        title={`${jam.title} (${jam.participantIds?.length || 0} joined)`}
                                    >
                                        <div className="absolute left-0 top-0 bottom-0 w-1 bg-modtale-accent" />

                                        <div className="relative z-10 flex items-center w-full min-w-0 px-1">
                                            <div className="w-10 h-10 rounded-lg bg-white dark:bg-slate-900 flex items-center justify-center mr-3 shrink-0 shadow-sm overflow-hidden border border-slate-200 dark:border-white/10">
                                                {jamIcon ? <img src={jamIcon} className="w-full h-full object-cover" alt="" /> : <Trophy className="w-5 h-5 opacity-40 text-slate-500" />}
                                            </div>

                                            <div className="flex flex-col flex-1 min-w-0 pr-1">
                                                <span className="font-bold text-sm text-slate-900 dark:text-white truncate drop-shadow-sm leading-tight mb-0.5 group-hover:text-modtale-accent transition-colors">{jam.title}</span>
                                                <div className="flex items-center gap-2">
                                                    <span className="text-[10px] font-semibold text-slate-600 dark:text-slate-400 truncate">
                                                        {jam.participantIds?.length || 0} entries
                                                    </span>
                                                </div>
                                            </div>
                                        </div>
                                    </Link>
                                );
                            })}

                            {sortedJams.length === 0 && (
                                <div className="absolute top-4 left-10 right-10 text-center text-slate-500 py-16">
                                    <Calendar className="w-12 h-12 mx-auto mb-4 opacity-20" />
                                    <p className="text-base font-bold text-slate-500 dark:text-slate-400">No active jams scheduled.</p>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>

            <div className="mt-16 space-y-12 animate-in fade-in slide-in-from-bottom-8 duration-700">
                <section>
                    <div className="flex items-center gap-4 mb-8 border-b border-slate-200 dark:border-white/10 pb-4">
                        <LayoutGrid className="w-6 h-6 text-modtale-accent" />
                        <h2 className="text-2xl font-black text-slate-900 dark:text-white">Past Events</h2>
                    </div>

                    {pastJams.length > 0 ? (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                            {pastJams.map(jam => (
                                <JamCard key={jam.id} jam={jam} />
                            ))}
                        </div>
                    ) : (
                        <div className="py-24 text-center text-slate-500 border border-slate-200 dark:border-white/10 rounded-2xl bg-slate-50/50 dark:bg-slate-900/30">
                            <p className="text-sm font-bold">No past events yet.</p>
                        </div>
                    )}
                </section>
            </div>
        </div>
    );
};