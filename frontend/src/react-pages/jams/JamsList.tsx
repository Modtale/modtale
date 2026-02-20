import React, { useEffect, useState } from 'react';
import { api } from '@/utils/api';
import type { Modjam, User } from '@/types';
import { Spinner } from '@/components/ui/Spinner';
import { Trophy, Calendar, Users, Plus, ArrowRight } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { CreateJamModal } from './CreateJamModal';

export const JamsList: React.FC<{ currentUser: User | null }> = ({ currentUser }) => {
    const navigate = useNavigate();
    const [jams, setJams] = useState<Modjam[]>([]);
    const [loading, setLoading] = useState(true);
    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);

    useEffect(() => {
        const fetchJams = async () => {
            try {
                const res = await api.get('/modjams');
                setJams(res.data);
            } catch (err) {
                console.error(err);
            } finally {
                setLoading(false);
            }
        };
        fetchJams();
    }, []);

    const handleJamCreated = (newJam: Modjam) => {
        setJams(prev => [newJam, ...prev]);
    };

    if (loading) {
        return <div className="min-h-screen bg-slate-50 dark:bg-slate-950 flex items-center justify-center"><Spinner fullScreen={false} className="w-8 h-8" /></div>;
    }

    const containerClasses = "max-w-[112rem] px-4 sm:px-12 md:px-16 lg:px-28";

    return (
        <div className={`${containerClasses} mx-auto pt-12 pb-20 min-h-screen flex flex-col transition-[max-width,padding] duration-300`}>
            <div className="flex flex-col md:flex-row md:items-end justify-between gap-6 mb-12 animate-in fade-in slide-in-from-bottom-4 duration-500">
                <div>
                    <div className="flex items-center gap-3 mb-2">
                        <div className="w-12 h-12 rounded-2xl bg-modtale-accent/10 text-modtale-accent flex items-center justify-center">
                            <Trophy className="w-6 h-6" />
                        </div>
                        <h1 className="text-4xl md:text-5xl font-black text-slate-900 dark:text-white tracking-tight">Modjams</h1>
                    </div>
                    <p className="text-lg text-slate-500 dark:text-slate-400 font-medium">Compete, create, and vote on community events.</p>
                </div>

                {currentUser && (
                    <button
                        onClick={() => setIsCreateModalOpen(true)}
                        className="h-14 bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 rounded-xl font-black flex items-center justify-center gap-2 shadow-lg shadow-modtale-accent/20 transition-all hover:-translate-y-1 active:scale-95 flex-shrink-0"
                    >
                        <Plus className="w-5 h-5" /> Host a Jam
                    </button>
                )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 animate-in fade-in slide-in-from-bottom-8 duration-700">
                {jams.map((jam) => {
                    const isParticipating = currentUser?.joinedModjamIds?.includes(jam.id);

                    return (
                        <Link
                            key={jam.id}
                            to={`/jam/${jam.slug}`}
                            className="flex flex-col bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-3xl overflow-hidden hover:border-modtale-accent dark:hover:border-modtale-accent hover:shadow-xl hover:-translate-y-1 transition-all duration-300 group"
                        >
                            <div className="h-48 bg-slate-100 dark:bg-slate-800 relative flex-shrink-0 overflow-hidden">
                                {jam.bannerUrl ? (
                                    <img src={jam.bannerUrl} alt={jam.title} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                                ) : (
                                    <div className="w-full h-full flex items-center justify-center text-slate-400 bg-gradient-to-br from-slate-100 dark:from-slate-800 to-slate-200 dark:to-slate-900">
                                        <Trophy className="w-16 h-16 opacity-20 group-hover:scale-110 transition-transform duration-500" />
                                    </div>
                                )}
                                <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
                                <div className="absolute top-4 right-4 bg-white/90 dark:bg-black/60 backdrop-blur-md text-slate-900 dark:text-white px-3 py-1.5 rounded-full text-xs font-black uppercase tracking-widest shadow-lg">
                                    {jam.status}
                                </div>
                            </div>

                            <div className="p-6 flex flex-col flex-grow relative z-10 bg-white dark:bg-slate-900">
                                <h2 className="text-2xl font-black mb-3 text-slate-900 dark:text-white group-hover:text-modtale-accent transition-colors line-clamp-1">{jam.title}</h2>
                                <p className="text-sm text-slate-500 dark:text-slate-400 mb-6 line-clamp-2 flex-grow font-medium leading-relaxed">{jam.description}</p>

                                <div className="flex items-center justify-between mt-auto pt-4 border-t border-slate-100 dark:border-white/5">
                                    <div className="flex items-center gap-4 text-xs font-bold text-slate-600 dark:text-slate-400 uppercase tracking-widest">
                                        <div className="flex items-center gap-1.5">
                                            <Calendar className="w-4 h-4" />
                                            <span>{new Date(jam.startDate).toLocaleDateString()}</span>
                                        </div>
                                        <div className="flex items-center gap-1.5">
                                            <Users className="w-4 h-4" />
                                            <span>{jam.participantIds.length} Joined</span>
                                        </div>
                                    </div>
                                    <div className="w-8 h-8 rounded-full bg-slate-50 dark:bg-white/5 flex items-center justify-center text-slate-400 group-hover:bg-modtale-accent group-hover:text-white transition-colors">
                                        <ArrowRight className="w-4 h-4" />
                                    </div>
                                </div>

                                {isParticipating && (
                                    <div className="absolute top-0 right-6 -translate-y-1/2 bg-modtale-accent text-white px-4 py-1.5 rounded-full text-[10px] font-black uppercase tracking-widest shadow-lg border-2 border-white dark:border-slate-900">
                                        Participating
                                    </div>
                                )}
                            </div>
                        </Link>
                    )
                })}
            </div>

            {jams.length === 0 && (
                <div className="text-center py-32 text-slate-500 animate-in fade-in">
                    <Trophy className="w-16 h-16 mx-auto mb-6 opacity-20" />
                    <p className="text-xl font-black text-slate-700 dark:text-slate-300">No jams available right now.</p>
                    <p className="font-medium mt-2">Check back later or host your own!</p>
                </div>
            )}

            <CreateJamModal
                isOpen={isCreateModalOpen}
                onClose={() => setIsCreateModalOpen(false)}
                onSuccess={handleJamCreated}
            />
        </div>
    );
};