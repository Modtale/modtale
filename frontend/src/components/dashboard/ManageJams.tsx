import React, { useState, useEffect } from 'react';
import { api } from '@/utils/api';
import type { Modjam, User } from '@/types';
import { Loader2, Plus, Trophy, Edit, Trash2, Users, ExternalLink, Calendar } from 'lucide-react';
import { Link } from 'react-router-dom';
import { StatusModal } from '@/components/ui/StatusModal';

interface JamListItemProps {
    jam: Modjam;
    onDelete: (jam: Modjam) => void;
}

const JamListItem: React.FC<JamListItemProps> = ({ jam, onDelete }) => {
    let statusBadge = null;
    switch (jam.status) {
        case 'DRAFT': statusBadge = <span className="text-[10px] bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-200 px-2 py-0.5 rounded font-bold uppercase">Draft</span>; break;
        case 'UPCOMING': statusBadge = <span className="text-[10px] bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-200 px-2 py-0.5 rounded font-bold uppercase">Upcoming</span>; break;
        case 'ACTIVE': statusBadge = <span className="text-[10px] bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-200 px-2 py-0.5 rounded font-bold uppercase">Active</span>; break;
        case 'VOTING': statusBadge = <span className="text-[10px] bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-200 px-2 py-0.5 rounded font-bold uppercase">Voting</span>; break;
        case 'AWAITING_WINNERS': statusBadge = <span className="text-[10px] bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-200 px-2 py-0.5 rounded font-bold uppercase">Awaiting Winners</span>; break;
        case 'COMPLETED': statusBadge = <span className="text-[10px] bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300 px-2 py-0.5 rounded font-bold uppercase">Completed</span>; break;
    }

    return (
        <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 p-4 rounded-2xl flex flex-col sm:flex-row items-center gap-4 group relative overflow-hidden">
            <div className="w-16 h-16 rounded-xl bg-slate-100 dark:bg-slate-800/50 overflow-hidden flex-shrink-0 flex items-center justify-center border border-slate-200/50 dark:border-white/5">
                {jam.imageUrl ? (
                    <img src={jam.imageUrl} alt="" className="w-full h-full object-cover" />
                ) : (
                    <Trophy className="w-8 h-8 text-slate-300 dark:text-slate-600" />
                )}
            </div>

            <div className="flex-1 text-center sm:text-left">
                <div className="flex items-center justify-center sm:justify-start gap-2 flex-wrap">
                    <Link to={`/jam/${jam.slug}/overview`} className="font-bold text-slate-900 dark:text-white text-lg hover:underline decoration-2 underline-offset-2 flex items-center gap-2">
                        {jam.title}
                        <ExternalLink className="w-3.5 h-3.5 opacity-50" />
                    </Link>
                    {statusBadge}
                </div>

                <div className="text-sm text-slate-500 flex items-center justify-center sm:justify-start gap-4 mt-1 flex-wrap">
                    <span className="flex items-center gap-1.5"><Users className="w-3.5 h-3.5" /> {jam.participantIds?.length || 0} Participants</span>
                    {jam.startDate && (
                        <span className="flex items-center gap-1.5">
                            <Calendar className="w-3.5 h-3.5" />
                            {new Date(jam.startDate).toLocaleDateString()} - {jam.endDate ? new Date(jam.endDate).toLocaleDateString() : 'TBD'}
                        </span>
                    )}
                </div>
            </div>

            <div className="flex items-center gap-2 opacity-100 sm:opacity-0 group-hover:opacity-100 transition-opacity">
                <Link to={`/jam/${jam.slug}/edit`} className="p-2 bg-slate-100 dark:bg-white/5 hover:bg-modtale-accent hover:text-white rounded-lg transition-colors" title="Edit">
                    <Edit className="w-4 h-4" />
                </Link>
                <button onClick={() => onDelete(jam)} className="p-2 bg-slate-100 dark:bg-white/5 hover:bg-red-500 hover:text-white rounded-lg transition-colors" title="Delete">
                    <Trash2 className="w-4 h-4" />
                </button>
            </div>
        </div>
    );
};

interface ManageJamsProps {
    user: User;
}

export const ManageJams: React.FC<ManageJamsProps> = ({ user }) => {
    const [jams, setJams] = useState<Modjam[]>([]);
    const [loading, setLoading] = useState(true);

    const [deleteModal, setDeleteModal] = useState<Modjam | null>(null);
    const [status, setStatus] = useState<{type: 'success'|'error', title: string, msg: string} | null>(null);

    useEffect(() => {
        api.get('/modjams/user/me')
            .then(res => setJams(res.data))
            .catch(console.error)
            .finally(() => setLoading(false));
    }, []);

    const handleDelete = async () => {
        if (!deleteModal) return;
        try {
            await api.delete(`/modjams/${deleteModal.id}`);
            setJams(prev => prev.filter(j => j.id !== deleteModal.id));
            setDeleteModal(null);
            setStatus({ type: 'success', title: 'Deleted', msg: "Jam deleted successfully." });
        } catch (e) {
            setStatus({ type: 'error', title: 'Delete Failed', msg: "Could not delete jam." });
        }
    };

    if (loading) return <div className="flex justify-center py-20"><Loader2 className="w-8 h-8 animate-spin text-modtale-accent" /></div>;

    return (
        <div className="space-y-8">
            <div className="flex justify-between items-center">
                <h1 className="text-2xl font-black text-slate-900 dark:text-white">Your Jams</h1>
                <Link to="/jams" className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-4 py-2 rounded-xl font-bold flex items-center gap-2 transition-colors shadow-lg shadow-modtale-accent/20">
                    <Plus className="w-4 h-4" /> Host a Jam
                </Link>
            </div>

            {status && <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} />}

            {deleteModal && (
                <StatusModal
                    type="warning"
                    title="Delete Jam?"
                    message={`Are you sure you want to delete "${deleteModal.title}"? This cannot be undone and will permanently delete all submissions.`}
                    actionLabel="Delete Jam"
                    onAction={handleDelete}
                    onClose={() => setDeleteModal(null)}
                    secondaryLabel="Cancel"
                />
            )}

            <div className="grid grid-cols-1 gap-4">
                {jams.map(jam => (
                    <JamListItem key={jam.id} jam={jam} onDelete={setDeleteModal} />
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