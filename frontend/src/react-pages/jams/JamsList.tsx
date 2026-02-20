import React, { useEffect, useState } from 'react';
import { api } from '@/utils/api';
import type { Modjam, User } from '@/types';
import { Spinner } from '@/components/ui/Spinner';
import { Trophy, Calendar, Users, Plus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { CreateJamModal } from './CreateJamModal';

export const JamsList: React.FC<{ currentUser: User | null }> = ({ currentUser }) => {
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
        return <div className="p-20 flex justify-center"><Spinner /></div>;
    }

    return (
        <div className="max-w-7xl mx-auto px-4 py-8">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
                <div className="flex items-center gap-3">
                    <Trophy className="w-8 h-8 text-modtale-accent" />
                    <h1 className="text-3xl font-black">Modjams</h1>
                </div>

                {currentUser && (
                    <button
                        onClick={() => setIsCreateModalOpen(true)}
                        className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 px-5 py-2.5 rounded-xl font-bold flex items-center gap-2 hover:scale-105 active:scale-95 transition-all shadow-sm"
                    >
                        <Plus className="w-4 h-4" /> Host a Jam
                    </button>
                )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {jams.map((jam) => {
                    const isParticipating = currentUser?.joinedModjamIds?.includes(jam.id);

                    return (
                        <Link
                            key={jam.id}
                            to={`/jam/${jam.slug}`}
                            className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden hover:ring-2 hover:ring-modtale-accent transition-all group flex flex-col"
                        >
                            <div className="h-40 bg-slate-100 dark:bg-slate-800 relative flex-shrink-0">
                                {jam.bannerUrl ? (
                                    <img src={jam.bannerUrl} alt={jam.title} className="w-full h-full object-cover" />
                                ) : (
                                    <div className="w-full h-full flex items-center justify-center text-slate-400">
                                        <Trophy className="w-12 h-12 opacity-20" />
                                    </div>
                                )}
                                <div className="absolute top-3 right-3 bg-black/60 backdrop-blur-md text-white px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wide">
                                    {jam.status}
                                </div>
                            </div>

                            <div className="p-5 flex flex-col flex-grow">
                                <h2 className="text-xl font-bold mb-2 group-hover:text-modtale-accent transition-colors line-clamp-1">{jam.title}</h2>
                                <p className="text-sm text-slate-600 dark:text-slate-400 mb-4 line-clamp-2 flex-grow">{jam.description}</p>

                                <div className="flex items-center justify-between text-sm text-slate-500 font-medium mt-auto">
                                    <div className="flex items-center gap-2">
                                        <Calendar className="w-4 h-4" />
                                        <span>{new Date(jam.startDate).toLocaleDateString()}</span>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <Users className="w-4 h-4" />
                                        <span>{jam.participantIds.length} Joined</span>
                                    </div>
                                </div>

                                {isParticipating && (
                                    <div className="mt-4 w-full bg-modtale-accent/10 text-modtale-accent text-center py-2 rounded-lg text-sm font-bold">
                                        You are participating
                                    </div>
                                )}
                            </div>
                        </Link>
                    )
                })}
            </div>

            {jams.length === 0 && (
                <div className="text-center py-20 text-slate-500">
                    <Trophy className="w-12 h-12 mx-auto mb-4 opacity-50" />
                    <p className="font-bold">No jams available at the moment.</p>
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