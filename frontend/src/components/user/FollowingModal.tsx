import React, { useState, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { X, User as UserIcon, Loader2, ExternalLink } from 'lucide-react';
import { api } from '../../utils/api';
import type {User} from '../../types';
import { Link } from 'react-router-dom';

interface FollowingModalProps {
    username: string;
    onClose: () => void;
}

export const FollowingModal: React.FC<FollowingModalProps> = ({ username, onClose }) => {
    const [users, setUsers] = useState<User[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchFollowing = async () => {
            try {
                const res = await api.get(`/users/${username}/following`);
                setUsers(res.data);
            } catch (e) {
                console.error("Failed to load following list", e);
            } finally {
                setLoading(false);
            }
        };
        fetchFollowing();
    }, [username]);

    return createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200">
            <div className="absolute inset-0" onClick={onClose}></div>

            <div className="relative bg-white dark:bg-modtale-card w-full max-w-md rounded-2xl border border-slate-200 dark:border-white/10 shadow-2xl overflow-hidden flex flex-col max-h-[80vh]">

                <div className="p-4 border-b border-slate-100 dark:border-white/5 flex justify-between items-center bg-slate-50/50 dark:bg-white/[0.02]">
                    <h3 className="font-black text-lg text-slate-900 dark:text-white flex items-center gap-2">
                        <UserIcon className="w-5 h-5 text-modtale-accent" /> Following
                    </h3>
                    <button onClick={onClose} className="p-1 rounded-lg text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-white/10 transition-colors">
                        <X className="w-5 h-5" />
                    </button>
                </div>

                <div className="flex-1 overflow-y-auto p-2 custom-scrollbar">
                    {loading ? (
                        <div className="p-8 text-center text-slate-400 flex flex-col items-center gap-2">
                            <Loader2 className="w-6 h-6 animate-spin text-modtale-accent" />
                            <span className="text-xs font-bold">Loading...</span>
                        </div>
                    ) : users.length === 0 ? (
                        <div className="p-8 text-center">
                            <div className="w-12 h-12 bg-slate-100 dark:bg-white/5 rounded-full flex items-center justify-center mx-auto mb-3">
                                <UserIcon className="w-6 h-6 text-slate-300 dark:text-slate-600" />
                            </div>
                            <p className="text-slate-900 dark:text-white font-bold">Not following anyone</p>
                            <p className="text-xs text-slate-500 mt-1">Follow creators to see them here.</p>
                        </div>
                    ) : (
                        <div className="space-y-1">
                            {users.map(u => (
                                <Link
                                    key={u.id}
                                    to={`/creator/${u.username}`}
                                    onClick={onClose}
                                    className="flex items-center justify-between p-3 rounded-xl hover:bg-slate-50 dark:hover:bg-white/5 transition-colors group"
                                >
                                    <div className="flex items-center gap-3">
                                        <img
                                            src={u.avatarUrl || '/assets/default-avatar.png'}
                                            alt={u.username}
                                            className="w-10 h-10 rounded-full bg-slate-200 dark:bg-black/20 object-cover border border-slate-200 dark:border-white/10"
                                        />
                                        <div>
                                            <div className="font-bold text-sm text-slate-900 dark:text-white group-hover:text-modtale-accent transition-colors">{u.username}</div>
                                            {u.roles && u.roles.length > 0 && u.roles[0] !== 'USER' && (
                                                <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                                                    {u.roles[0]}
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                    <ExternalLink className="w-4 h-4 text-slate-300 group-hover:text-slate-500 dark:text-slate-600 dark:group-hover:text-slate-400" />
                                </Link>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </div>,
        document.body
    );
};