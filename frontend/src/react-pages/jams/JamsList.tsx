import React, { useEffect, useState } from 'react';
import { api } from '@/utils/api';
import type { Modjam, User } from '@/types';
import { Spinner } from '@/components/ui/Spinner';
import { Trophy, Plus, ArrowLeft, CalendarDays } from 'lucide-react';
import { Link } from 'react-router-dom';
import { JamBuilder } from '@/components/resources/upload/JamBuilder';

export const JamsList: React.FC<{ currentUser: User | null }> = ({ currentUser }) => {
    const [jams, setJams] = useState<Modjam[]>([]);
    const [loading, setLoading] = useState(true);
    const [isCreating, setIsCreating] = useState(false);
    const [step, setStep] = useState(0);

    const [metaData, setMetaData] = useState({
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
        try {
            const res = await api.post('/modjams', metaData);
            setJams(prev => {
                const filtered = prev.filter(j => j.id !== res.data.id);
                return [res.data, ...filtered];
            });
            return true;
        } catch (e) {
            return false;
        }
    };

    const handlePublish = async () => {
        try {
            await api.post(`/modjams/publish`, metaData);
            setIsCreating(false);
            setStep(0);
        } catch (e) {}
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
                                onChange={e => setMetaData({...metaData, title: e.target.value})}
                                className="w-full bg-slate-50 dark:bg-black/20 border-none rounded-2xl px-6 py-5 font-black text-xl shadow-inner outline-none focus:ring-2 focus:ring-modtale-accent transition-all"
                                placeholder="Summer Hackathon 2026"
                            />
                        </div>
                        <button
                            type="button"
                            onClick={() => setStep(2)}
                            disabled={!metaData.title || metaData.title.length < 5}
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
                isLoading={loading}
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

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 animate-in fade-in slide-in-from-bottom-4 duration-700">
                {jams.map((jam) => (
                    <Link key={jam.id} to={`/jam/${jam.slug}`} className="group relative bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-3xl overflow-hidden hover:border-modtale-accent transition-all shadow-sm hover:shadow-xl hover:-translate-y-1 flex flex-col h-full">
                        <div className="h-48 bg-slate-100 dark:bg-slate-800 relative">
                            {jam.bannerUrl ? (
                                <img src={jam.bannerUrl} alt="" className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                            ) : (
                                <div className="w-full h-full flex items-center justify-center text-slate-400">
                                    <Trophy className="w-12 h-12 opacity-20" />
                                </div>
                            )}
                            <div className="absolute top-4 right-4 bg-black/60 backdrop-blur-md text-white px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-widest">{jam.status}</div>
                        </div>
                        <div className="p-6 flex-1 flex flex-col">
                            <h2 className="text-2xl font-black mb-2 group-hover:text-modtale-accent transition-colors">{jam.title}</h2>
                            <p className="text-sm text-slate-500 font-medium mb-6 line-clamp-2">{jam.description}</p>
                            <div className="mt-auto flex items-center justify-between pt-4 border-t border-slate-100 dark:border-white/5">
                                <div className="flex items-center gap-1.5 text-slate-400 text-xs font-bold">
                                    <CalendarDays className="w-4 h-4" /> {new Date(jam.startDate).toLocaleDateString()}
                                </div>
                                <div className="text-modtale-accent font-black text-xs uppercase tracking-widest">View Event</div>
                            </div>
                        </div>
                    </Link>
                ))}
            </div>
        </div>
    );
};