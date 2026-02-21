import React, { useState, useEffect } from 'react';
import { api } from '@/utils/api';
import type { Modjam, User } from '@/types';
import { Loader2, Plus, Trophy } from 'lucide-react';
import { Link } from 'react-router-dom';
import { JamCard } from '@/react-pages/jams/JamsList';

interface ManageJamsProps {
    user: User;
}

export const ManageJams: React.FC<ManageJamsProps> = ({ user }) => {
    const [jams, setJams] = useState<Modjam[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        api.get('/modjams/user/me')
            .then(res => setJams(res.data))
            .catch(console.error)
            .finally(() => setLoading(false));
    }, []);

    if (loading) return <div className="flex justify-center py-20"><Loader2 className="w-8 h-8 animate-spin text-modtale-accent" /></div>;

    return (
        <div className="space-y-8">
            <div className="flex justify-between items-center">
                <h1 className="text-2xl font-black text-slate-900 dark:text-white">Your Jams</h1>
                <Link to="/jams" className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-4 py-2 rounded-xl font-bold flex items-center gap-2 transition-colors shadow-lg shadow-modtale-accent/20">
                    <Plus className="w-4 h-4" /> Host a Jam
                </Link>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {jams.map(jam => (
                    <JamCard key={jam.id} jam={jam} />
                ))}
            </div>

            {jams.length === 0 && (
                <div className="text-center py-12 text-slate-400 border-2 border-dashed border-slate-200 dark:border-white/5 rounded-3xl">
                    <Trophy className="w-12 h-12 mx-auto mb-4 opacity-20" />
                    <p className="font-bold text-lg text-slate-900 dark:text-white">You haven't hosted any jams yet.</p>
                    <p className="text-sm font-medium mt-1">Host your first jam to see it here!</p>
                </div>
            )}
        </div>
    );
};