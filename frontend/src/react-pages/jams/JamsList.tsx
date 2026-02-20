import React, { useEffect, useState } from 'react';
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
        <Link to={`/jam/${jam.slug}`} className="group relative flex flex-col h-full bg-white dark:bg-slate-800 rounded-lg border border-slate-200 dark:border-slate-700 hover:border-modtale-accent dark:hover:border-modtale-accent transition-colors overflow-hidden">
            <div className="relative h-24 w-full shrink-0 overflow-hidden bg-slate-100 dark:bg-slate-900 border-b border-slate-200/50 dark:border-white/5">
                {resolvedBanner ? (
                    <img
                        src={resolvedBanner}
                        alt=""
                        className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
                    />
                ) : (
                    <div className="w-full h-full flex items-center justify-center bg-slate-200 dark:bg-slate-700">
                        <Trophy className="w-8 h-8 opacity-20 text-slate-500" />
                    </div>
                )}

                <div className="absolute top-2 right-2 z-20">
                    <div className={`text-[10px] font-bold px-2 py-1 rounded flex items-center shadow-sm ${jam.status === 'ACTIVE' ? 'bg-modtale-accent text-white' : 'bg-slate-900/80 text-white'}`}>
                        <Trophy className="w-3 h-3 mr-1" />
                        <span>{jam.status}</span>
                    </div>
                </div>
            </div>

            <div className="flex px-4 relative flex-1">
                <div className="flex-shrink-0 -mt-8 mb-2 relative z-10">
                    <div className="w-20 h-20 rounded-lg bg-slate-200 dark:bg-slate-800 shadow-md border-4 border-white dark:border-slate-800 overflow-hidden relative flex items-center justify-center">
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
                </div>

                <div className="flex-1 min-w-0 flex flex-col pt-1 pl-3">
                    <div className="flex justify-between items-start gap-2 mb-0.5">
                        <div className="min-w-0 flex-1 relative">
                            <h3 className="text-lg font-bold text-slate-900 dark:text-slate-200 truncate group-hover:text-modtale-accent transition-colors" title={jam.title}>
                                {jam.title}
                            </h3>
                            <div className="flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400 relative z-20">
                                <span>Hosted by</span>
                                <span className="text-slate-700 dark:text-slate-300 font-medium">{jam.hostName}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div className="px-4 pb-4 mt-2">
                <p className="text-slate-600 dark:text-slate-400 text-xs line-clamp-2 leading-relaxed h-10">
                    {jam.description || 'No description provided.'}
                </p>
            </div>

            <div className="mt-auto bg-slate-50 dark:bg-white/[0.02] px-4 py-3 flex items-center justify-between text-xs font-medium text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-white/5">
                <div className="flex items-center gap-3">
                    <span className="flex items-center" title="Participants">
                        <Users className="w-3 h-3 mr-1" /> {jam.participantIds?.length || 0}
                    </span>
                </div>
                <div className="flex items-center">
                    <Calendar className="w-3 h-3 mr-1" />
                    <span>{formatJamDate()}</span>
                </div>
            </div>
        </Link>
    );
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
        categories: []
    });

    const [activeTab, setActiveTab] = useState<'details' | 'categories' | 'settings'>('details');

    useEffect(() => {
        api.get('/modjams').then(res => {
            setJams(res.data);
            setLoading(false);
        }).catch(() => setLoading(false));
    }, []);

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

    return (
        <div className="max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 pt-16 pb-32">
            <div className="flex flex-col md:flex-row items-end justify-between gap-6 mb-16">
                <div className="animate-in fade-in slide-in-from-left-4 duration-500">
                    <h1 className="text-6xl font-black tracking-tighter mb-4">Modjams</h1>
                    <p className="text-xl text-slate-500 font-medium max-w-2xl leading-relaxed">The heartbeat of the community. Create, compete, and celebrate the best modding has to offer.</p>
                </div>
                {currentUser && (
                    <button type="button" onClick={() => { setIsCreating(true); setStep(1); }} className="h-16 px-10 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-[1.25rem] font-black text-lg shadow-xl shadow-modtale-accent/20 transition-all hover:-translate-y-1 active:scale-95 flex items-center gap-3 animate-in fade-in slide-in-from-right-4 duration-500">
                        <Plus className="w-6 h-6" /> Host a Jam
                    </button>
                )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 animate-in fade-in slide-in-from-bottom-4 duration-700">
                {jams.map((jam) => (
                    <JamCard key={jam.id} jam={jam} />
                ))}
            </div>
        </div>
    );
};