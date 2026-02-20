import React, { useEffect, useState } from 'react';
import { api } from '@/utils/api';
import type { Modjam, User } from '@/types';
import { Spinner } from '@/components/ui/Spinner';
import { Trophy, Plus, ArrowLeft, Sparkles, Wand2, CalendarDays } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
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

    const handleCreateDraft = () => {
        if (!metaData.title || !metaData.description) return;
        setStep(2);
    };

    const handleSaveJam = async () => {
        try {
            const res = await api.post('/modjams', metaData);
            setIsCreating(false);
            setJams([res.data, ...jams]);
            return true;
        } catch (e) {
            return false;
        }
    };

    if (isCreating) {
        if (step === 1) {
            return (
                <div className="max-w-xl mx-auto pt-20 px-6 animate-in fade-in zoom-in-95">
                    <button onClick={() => setIsCreating(false)} className="text-slate-500 font-bold mb-8 flex items-center gap-2 hover:text-slate-900 transition-colors">
                        <ArrowLeft className="w-4 h-4" /> Cancel
                    </button>
                    <div className="flex items-center gap-3 mb-2">
                        <div className="w-10 h-10 rounded-xl bg-modtale-accent/10 text-modtale-accent flex items-center justify-center">
                            <Wand2 className="w-5 h-5" />
                        </div>
                        <h1 className="text-3xl font-black">Let's build a jam.</h1>
                    </div>
                    <p className="text-slate-500 font-medium mb-8">Every great event starts with a solid foundation.</p>

                    <div className="space-y-6 bg-white dark:bg-modtale-card p-8 rounded-3xl border border-slate-200 dark:border-white/5 shadow-xl">
                        <div className="space-y-2">
                            <label className="text-[10px] font-black uppercase text-slate-500 tracking-widest ml-1">Event Title</label>
                            <input
                                value={metaData.title}
                                onChange={e => setMetaData({...metaData, title: e.target.value})}
                                className="w-full bg-slate-50 dark:bg-black/20 border-none rounded-2xl px-5 py-4 font-bold text-lg"
                                placeholder="Summer Hackathon 2026"
                            />
                        </div>
                        <div className="space-y-2">
                            <label className="text-[10px] font-black uppercase text-slate-500 tracking-widest ml-1">One-Sentence Summary</label>
                            <input
                                value={metaData.description}
                                onChange={e => setMetaData({...metaData, description: e.target.value})}
                                className="w-full bg-slate-50 dark:bg-black/20 border-none rounded-2xl px-5 py-4 text-slate-600 dark:text-slate-300"
                                placeholder="Create the best aesthetic world in 48 hours."
                            />
                        </div>
                        <button
                            onClick={handleCreateDraft}
                            disabled={!metaData.title || !metaData.description}
                            className="w-full h-16 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-2xl font-black text-lg shadow-lg shadow-modtale-accent/20 transition-all flex items-center justify-center gap-3 disabled:opacity-50"
                        >
                            Continue to Builder <Sparkles className="w-5 h-5" />
                        </button>
                    </div>
                </div>
            );
        }

        return (
            <JamBuilder
                jamData={null}
                metaData={metaData}
                setMetaData={setMetaData}
                handleSave={handleSaveJam}
                isLoading={loading}
                currentUser={currentUser}
                activeTab={activeTab}
                setActiveTab={setActiveTab}
            />
        );
    }

    return (
        <div className="max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 pt-12 pb-20">
            <div className="flex items-end justify-between mb-12">
                <div>
                    <h1 className="text-5xl font-black tracking-tight mb-2">Modjams</h1>
                    <p className="text-lg text-slate-500 font-medium">Join community events and show off your skills.</p>
                </div>
                {currentUser && (
                    <button onClick={() => { setIsCreating(true); setStep(1); }} className="h-14 px-8 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-xl font-black flex items-center gap-2 shadow-lg transition-all hover:-translate-y-1">
                        <Plus className="w-5 h-5" /> Host a Jam
                    </button>
                )}
            </div>
        </div>
    );
};