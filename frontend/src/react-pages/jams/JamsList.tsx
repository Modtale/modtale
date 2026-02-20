import React, { useEffect, useState } from 'react';
import { api } from '@/utils/api';
import type { Modjam, User } from '@/types';
import { Spinner } from '@/components/ui/Spinner';
import { Trophy, Plus, ArrowLeft, Sparkles, Wand2 } from 'lucide-react';
import { JamBuilder } from '@/components/resources/upload/JamBuilder';

export const JamsList: React.FC<{ currentUser: User | null }> = ({ currentUser }) => {
    const [jams, setJams] = useState<Modjam[]>([]);
    const [loading, setLoading] = useState(true);
    const [isCreating, setIsCreating] = useState(false);
    const [step, setStep] = useState(0);

    const [metaData, setMetaData] = useState({
        title: '',
        description: '',
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

    const handleSaveJam = async (silent = false) => {
        try {
            const res = await api.post('/modjams', metaData);
            setJams([res.data, ...jams.filter(j => j.id !== res.data.id)]);
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
                    <button onClick={() => setIsCreating(false)} className="text-slate-500 font-bold mb-10 flex items-center gap-2 hover:text-slate-900 transition-colors">
                        <ArrowLeft className="w-4 h-4" /> Cancel
                    </button>
                    <div className="flex items-center gap-4 mb-4">
                        <div className="w-12 h-12 rounded-[1.25rem] bg-modtale-accent/10 text-modtale-accent flex items-center justify-center">
                            <Wand2 className="w-6 h-6" />
                        </div>
                        <h1 className="text-4xl font-black tracking-tight">Let's build a jam.</h1>
                    </div>
                    <p className="text-slate-500 font-medium mb-10 text-lg">Define your theme and mission before we set the dates.</p>

                    <div className="space-y-8 bg-white dark:bg-modtale-card p-10 rounded-[2.5rem] border border-slate-200 dark:border-white/5 shadow-2xl">
                        <div className="space-y-3">
                            <label className="text-[10px] font-black uppercase text-slate-500 tracking-widest ml-2">Event Title</label>
                            <input
                                value={metaData.title}
                                onChange={e => setMetaData({...metaData, title: e.target.value})}
                                className="w-full bg-slate-50 dark:bg-black/20 border-none rounded-2xl px-6 py-5 font-black text-xl shadow-inner outline-none focus:ring-2 focus:ring-modtale-accent transition-all"
                                placeholder="The Big Mod Hackathon 2026"
                            />
                        </div>
                        <div className="space-y-3">
                            <label className="text-[10px] font-black uppercase text-slate-500 tracking-widest ml-2">Short Briefing</label>
                            <textarea
                                value={metaData.description}
                                onChange={e => setMetaData({...metaData, description: e.target.value})}
                                className="w-full bg-slate-50 dark:bg-black/20 border-none rounded-2xl px-6 py-5 text-slate-600 dark:text-slate-300 font-medium min-h-[120px] shadow-inner outline-none focus:ring-2 focus:ring-modtale-accent transition-all"
                                placeholder="Create something beautiful in just 48 hours..."
                            />
                        </div>
                        <button
                            onClick={() => setStep(2)}
                            disabled={!metaData.title || !metaData.description}
                            className="w-full h-16 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-2xl font-black text-lg shadow-xl shadow-modtale-accent/20 transition-all flex items-center justify-center gap-3 disabled:opacity-50 hover:scale-[1.02] active:scale-95"
                        >
                            Open Builder <Sparkles className="w-5 h-5" />
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
                handlePublish={handlePublish}
                isLoading={loading}
                activeTab={activeTab}
                setActiveTab={setActiveTab}
                onBack={() => setStep(1)}
            />
        );
    }

    return (
        <div className="max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 pt-16 pb-32">
            <div className="flex flex-col md:flex-row items-end justify-between gap-6 mb-16">
                <div>
                    <h1 className="text-6xl font-black tracking-tighter mb-4">Modjams</h1>
                    <p className="text-xl text-slate-500 font-medium max-w-2xl">The heartbeat of the community. Create, compete, and celebrate the best modding has to offer.</p>
                </div>
                {currentUser && (
                    <button onClick={() => { setIsCreating(true); setStep(1); }} className="h-16 px-10 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-[1.25rem] font-black text-lg shadow-xl shadow-modtale-accent/20 transition-all hover:-translate-y-1 active:scale-95 flex items-center gap-3">
                        <Plus className="w-6 h-6" /> Host a Jam
                    </button>
                )}
            </div>
        </div>
    );
};