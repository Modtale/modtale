import React, { useEffect, useState, useMemo } from 'react';
import { api, BACKEND_URL } from '@/utils/api';
import type { Modjam, User } from '@/types';
import { Spinner } from '@/components/ui/Spinner';
import { Trophy, Plus, ArrowLeft, Calendar, Users } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { JamBuilder } from '@/components/resources/upload/JamBuilder';

export const JamCard: React.FC<{ jam: Modjam }> = ({ jam }) => {
    const resolveUrl = (url?: string | null) => {
        if (!url) return '';
        if (url.startsWith('/api') || url.startsWith('/uploads')) {
            return `${BACKEND_URL}${url}`;
        }
        return url;
    };

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
        <Link to={`/jam/${jam.slug}`} className="group relative flex flex-col h-full bg-white/60 dark:bg-slate-900/40 backdrop-blur-xl rounded-[2rem] border border-slate-200 dark:border-white/10 hover:border-modtale-accent dark:hover:border-modtale-accent transition-all duration-300 hover:shadow-2xl hover:-translate-y-1 overflow-hidden shadow-lg">

            <div className="relative w-full aspect-[3/1] bg-slate-200/50 dark:bg-slate-800/50 overflow-hidden shrink-0 border-b border-slate-200/50 dark:border-white/5">
                {resolvedBanner ? (
                    <img
                        src={resolvedBanner}
                        alt=""
                        className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
                    />
                ) : (
                    <div className="w-full h-full flex items-center justify-center bg-slate-300/30 dark:bg-slate-700/30">
                        <Trophy className="w-8 h-8 opacity-20 text-slate-500" />
                    </div>
                )}
                <div className="absolute top-3 right-3 z-20">
                    <div className={`text-[10px] font-black uppercase tracking-widest px-2.5 py-1 rounded-lg flex items-center shadow-sm backdrop-blur-md border ${jam.status === 'ACTIVE' ? 'bg-modtale-accent/90 border-modtale-accent text-white' : 'bg-black/50 border-white/10 text-white'}`}>
                        <Trophy className="w-3 h-3 mr-1.5" />
                        <span>{jam.status}</span>
                    </div>
                </div>
            </div>

            <div className="flex flex-col flex-1 px-6 pb-6 pt-0 relative items-center text-center">
                <div className="w-20 h-20 shrink-0 -mt-10 mb-3 relative z-10 rounded-[1.25rem] bg-white/80 dark:bg-slate-800/80 backdrop-blur-md shadow-lg border-4 border-white dark:border-slate-900 overflow-hidden flex items-center justify-center transition-transform duration-500 group-hover:scale-105">
                    {resolvedIcon ? (
                        <img
                            src={resolvedIcon}
                            alt={jam.title}
                            className="w-full h-full object-cover"
                        />
                    ) : (
                        <Trophy className="w-8 h-8 text-slate-400" />
                    )}
                </div>

                <h3 className="text-xl font-black text-slate-900 dark:text-white group-hover:text-modtale-accent transition-colors line-clamp-1 w-full" title={jam.title}>
                    {jam.title}
                </h3>

                <div className="flex items-center justify-center gap-1 text-xs text-slate-500 dark:text-slate-400 mt-1 mb-4">
                    <span>Hosted by</span>
                    <span className="font-bold text-slate-700 dark:text-slate-300">{jam.hostName}</span>
                </div>

                <p className="text-sm text-slate-600 dark:text-slate-400 line-clamp-2 leading-relaxed mb-6">
                    {jam.description || 'No description provided.'}
                </p>

                <div className="mt-auto w-full flex items-center justify-between pt-4 border-t border-slate-200/50 dark:border-white/5">
                    <div className="flex items-center gap-2 text-xs font-bold text-slate-600 dark:text-slate-300 bg-white/50 dark:bg-white/5 px-2.5 py-1.5 rounded-lg border border-slate-200 dark:border-white/5">
                        <Users className="w-4 h-4 text-modtale-accent" /> {jam.participantIds?.length || 0}
                    </div>
                    <div className="flex items-center gap-2 text-xs font-bold text-slate-600 dark:text-slate-300 bg-white/50 dark:bg-white/5 px-2.5 py-1.5 rounded-lg border border-slate-200 dark:border-white/5">
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

    const DAY_WIDTH = 76;
    const TRACK_HEIGHT = 44;
    const TRACK_GAP = 12;

    const gradients = [
        'bg-gradient-to-r from-indigo-500/90 to-purple-600/90',
        'bg-gradient-to-r from-emerald-500/90 to-teal-600/90',
        'bg-gradient-to-r from-rose-500/90 to-pink-600/90',
        'bg-gradient-to-r from-blue-500/90 to-cyan-600/90',
        'bg-gradient-to-r from-amber-500/90 to-orange-600/90',
        'bg-gradient-to-r from-fuchsia-500/90 to-rose-600/90'
    ];

    useEffect(() => {
        api.get('/modjams').then(res => {
            setJams(res.data);
            setLoading(false);
        }).catch(() => setLoading(false));
    }, []);

    const { startDate, endDate, totalDays, days } = useMemo(() => {
        const start = new Date();
        start.setDate(start.getDate() - 10);
        start.setHours(0, 0, 0, 0);
        const total = 60;
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
            .filter(jam => jam.startDate && jam.endDate)
            .filter(jam => new Date(jam.endDate).getTime() >= startDate.getTime() && new Date(jam.startDate).getTime() <= endDate.getTime())
            .sort((a, b) => new Date(a.startDate).getTime() - new Date(b.startDate).getTime());

        sj.forEach(jam => {
            let placed = false;
            for (let i = 0; i < t.length; i++) {
                const track = t[i];
                const lastJam = track[track.length - 1];
                if (new Date(lastJam.endDate).getTime() < new Date(jam.startDate).getTime()) {
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
            let res;
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

                    <div className="space-y-6 bg-white dark:bg-modtale-card p-10 rounded-[2.5rem] border border-slate-200 dark:border-white/5 shadow-2xl">
                        <div className="space-y-3">
                            <label className="text-[10px] font-black uppercase text-slate-500 tracking-widest ml-2">Event Title</label>
                            <input
                                value={metaData.title}
                                onChange={e => setMetaData(prev => ({...prev, title: e.target.value}))}
                                className="w-full bg-slate-50 dark:bg-black/20 border-none rounded-2xl px-6 py-5 font-black text-xl shadow-inner outline-none focus:ring-2 focus:ring-modtale-accent transition-all"
                                placeholder="Summer Hackathon 2026"
                            />
                        </div>
                        <button
                            type="button"
                            onClick={() => setStep(2)}
                            disabled={!metaData.title || metaData.title.trim().length < 5}
                            className="w-full h-16 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-2xl font-black text-lg shadow-xl shadow-modtale-accent/20 transition-all flex items-center justify-center gap-3 disabled:opacity-50 hover:scale-[1.02] active:scale-95"
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

    return (
        <div className="max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 pt-16 pb-32">
            <div className="flex flex-col md:flex-row items-end justify-between gap-6 mb-12">
                <div className="animate-in fade-in slide-in-from-left-4 duration-500">
                    <h1 className="text-6xl font-black tracking-tighter mb-4">Modjams</h1>
                    <p className="text-xl text-slate-500 font-medium max-w-2xl leading-relaxed">The heartbeat of the community. Create, compete, and celebrate the best modding has to offer.</p>
                </div>
                {currentUser && (
                    <button type="button" onClick={() => { setIsCreating(true); setStep(1); }} className="h-16 px-10 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-[1.25rem] font-black text-lg shadow-xl shadow-modtale-accent/20 transition-all hover:-translate-y-1 active:scale-95 flex items-center gap-3 animate-in fade-in slide-in-from-right-4 duration-500 shrink-0">
                        <Plus className="w-6 h-6" /> Host a Jam
                    </button>
                )}
            </div>

            <div className="bg-white/60 dark:bg-slate-900/40 backdrop-blur-xl border border-slate-200 dark:border-white/10 rounded-[2.5rem] shadow-xl overflow-hidden flex flex-col animate-in fade-in slide-in-from-bottom-4 duration-700">
                <div className="p-6 md:p-8 border-b border-slate-200 dark:border-white/10 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50/50 dark:bg-black/20">
                    <h2 className="text-2xl font-black flex items-center gap-3 text-slate-900 dark:text-white">
                        <Calendar className="w-6 h-6 text-modtale-accent" />
                        Event Calendar
                    </h2>
                    <div className="text-sm font-bold text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 px-5 py-2.5 rounded-xl shadow-sm border border-slate-200 dark:border-white/5">
                        {monthLabel}
                    </div>
                </div>

                <div className="overflow-x-auto custom-scrollbar pb-8 pl-8 pt-8">
                    <div style={{ width: `${totalDays * DAY_WIDTH}px`, minHeight: `${Math.max(tracks.length * (TRACK_HEIGHT + TRACK_GAP) + 60, 300)}px` }} className="relative">
                        <div className="flex absolute top-0 left-0 right-0 h-10 border-b border-slate-200 dark:border-white/10">
                            {days.map(day => (
                                <div key={day.toISOString()} style={{ width: `${DAY_WIDTH}px` }} className="shrink-0 flex items-center text-[11px] font-black uppercase tracking-widest text-slate-400 border-l border-slate-200/50 dark:border-white/5 pl-2.5">
                                    {formatDay(day)}
                                </div>
                            ))}
                        </div>

                        <div className="absolute top-10 left-0 right-0 bottom-0 flex pointer-events-none">
                            {days.map(day => {
                                const isWeekend = day.getDay() === 0 || day.getDay() === 6;
                                return (
                                    <div key={day.toISOString()} style={{ width: `${DAY_WIDTH}px` }} className={`shrink-0 border-l border-slate-200/30 dark:border-white/[0.03] ${isWeekend ? 'bg-slate-50/50 dark:bg-white/[0.01]' : ''}`} />
                                );
                            })}
                        </div>

                        {isNowInWindow && (
                            <div className="absolute top-0 bottom-0 w-0.5 bg-modtale-accent z-20 shadow-[0_0_12px_rgba(var(--color-modtale-accent),0.8)]" style={{ left: `${nowLeftPx}px` }}>
                                <div className="absolute top-0 left-1/2 -translate-x-1/2 px-2 py-0.5 bg-modtale-accent text-white text-[9px] font-black uppercase tracking-widest rounded-b-md">Today</div>
                            </div>
                        )}

                        <div className="absolute top-16 left-0 right-0">
                            {sortedJams.map(jam => {
                                const startMs = new Date(jam.startDate).getTime();
                                const endMs = new Date(jam.endDate).getTime();
                                const windowStartMs = startDate.getTime();

                                const renderStart = Math.max(startMs, windowStartMs);
                                const renderEnd = Math.min(endMs, endDate.getTime());

                                const leftPx = ((renderStart - windowStartMs) / (1000 * 60 * 60 * 24)) * DAY_WIDTH;
                                const widthPx = ((renderEnd - renderStart) / (1000 * 60 * 60 * 24)) * DAY_WIDTH;

                                const trackIdx = jamTracks[jam.id] || 0;
                                const topPx = trackIdx * (TRACK_HEIGHT + TRACK_GAP);

                                const colorIdx = jam.id.charCodeAt(0) % gradients.length;
                                const gradient = gradients[colorIdx];

                                return (
                                    <Link
                                        key={jam.id}
                                        to={`/jam/${jam.slug}`}
                                        className={`absolute flex items-center px-4 rounded-[1.25rem] shadow-lg hover:shadow-xl hover:scale-[1.02] active:scale-95 transition-all cursor-pointer backdrop-blur-md border border-white/20 group z-10 ${gradient}`}
                                        style={{
                                            left: `${leftPx}px`,
                                            width: `${Math.max(widthPx, 140)}px`,
                                            top: `${topPx}px`,
                                            height: `${TRACK_HEIGHT}px`
                                        }}
                                        title={`${jam.title} (${jam.participantIds?.length || 0} joined)`}
                                    >
                                        <span className="text-white font-black text-sm truncate drop-shadow-md relative z-10">{jam.title}</span>
                                        <span className="ml-2 bg-black/20 text-white/90 px-2 py-0.5 rounded-md font-bold text-[10px] whitespace-nowrap relative z-10 group-hover:bg-black/30 group-hover:text-white transition-colors">
                                            {jam.participantIds?.length || 0} joined
                                        </span>
                                    </Link>
                                );
                            })}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};