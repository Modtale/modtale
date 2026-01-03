import React, { useState, useEffect, useRef } from 'react';
import { api } from '../../utils/api.ts';
import { StatusModal } from '../../components/ui/StatusModal.tsx';
import { Shield, Search, User as UserIcon, Zap, Check, X, FileText, Clock, ExternalLink } from 'lucide-react';
import type { Mod } from '../../types';

interface AdminPanelProps {
    currentUser: any;
}

export const AdminPanel: React.FC<AdminPanelProps> = ({ currentUser }) => {
    const [activeTab, setActiveTab] = useState<'users' | 'verification'>('verification');
    const [status, setStatus] = useState<any>(null);

    const [username, setUsername] = useState('');
    const [loading, setLoading] = useState(false);
    const [foundUser, setFoundUser] = useState<any>(null);

    const [pendingProjects, setPendingProjects] = useState<Mod[]>([]);
    const [loadingQueue, setLoadingQueue] = useState(false);
    const [rejectReason, setRejectReason] = useState('');
    const [rejectingId, setRejectingId] = useState<string|null>(null);

    const isAdmin = currentUser?.username === 'Villagers654' || (currentUser?.roles && currentUser.roles.includes('ADMIN'));

    useEffect(() => {
        if (activeTab === 'verification' && isAdmin) {
            fetchQueue();
        }
    }, [activeTab, isAdmin]);

    const fetchQueue = async () => {
        setLoadingQueue(true);
        try {
            const res = await api.get('/admin/verification/queue');
            setPendingProjects(res.data);
        } catch (e) {
            console.error(e);
        } finally {
            setLoadingQueue(false);
        }
    };

    if (!currentUser || !isAdmin) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-slate-50 dark:bg-modtale-dark">
                <div className="text-center p-8 bg-white dark:bg-modtale-card rounded-xl border border-slate-200 dark:border-white/10 shadow-xl">
                    <Shield className="w-12 h-12 text-red-500 mx-auto mb-4" />
                    <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Access Denied</h1>
                    <p className="text-slate-500">You do not have permission to view this page.</p>
                </div>
            </div>
        );
    }

    const handleSearch = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setFoundUser(null);
        try {
            const res = await api.get(`/user/profile/${username}`);
            setFoundUser(res.data);
        } catch (e) {
            setStatus({ type: 'error', title: 'User Not Found', msg: `Could not find user "${username}"` });
        } finally {
            setLoading(false);
        }
    };

    const handleUpdateTier = async (newTier: 'USER' | 'ENTERPRISE') => {
        if (!foundUser) return;
        setLoading(true);
        try {
            const formData = new FormData();
            formData.append('tier', newTier);
            await api.post(`/admin/users/${foundUser.username}/tier`, formData);
            setStatus({ type: 'success', title: 'Tier Updated', msg: `Successfully changed ${foundUser.username} to ${newTier}.` });
            setFoundUser({ ...foundUser, tier: newTier });
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Update Failed', msg: e.response?.data?.message || 'Server error occurred.' });
        } finally {
            setLoading(false);
        }
    };

    const handleToggleAdmin = async () => {
        if (!foundUser) return;
        setLoading(true);
        try {
            await api.post(`/admin/users/${foundUser.username}/role`, null, { params: { role: 'ADMIN' } });
            setStatus({ type: 'success', title: 'Role Updated', msg: `Admin role granted to ${foundUser.username}.` });
            const roles = foundUser.roles || [];
            if (!roles.includes('ADMIN')) roles.push('ADMIN');
            setFoundUser({...foundUser, roles});
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Update Failed', msg: e.response?.data || 'Server error occurred.' });
        } finally {
            setLoading(false);
        }
    };

    const handleApprove = async (id: string) => {
        if (!confirm("Are you sure you want to approve this project?")) return;
        try {
            await api.post(`/projects/${id}/publish`);
            setStatus({ type: 'success', title: 'Approved', msg: 'Project published successfully.' });
            fetchQueue();
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Error', msg: e.response?.data || 'Failed to approve.' });
        }
    };

    const handleReject = async () => {
        if (!rejectingId) return;
        try {
            await api.post(`/admin/projects/${rejectingId}/reject`, { reason: rejectReason });
            setStatus({ type: 'info', title: 'Rejected', msg: 'Project returned to drafts.' });
            setRejectingId(null);
            setRejectReason('');
            fetchQueue();
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Error', msg: e.response?.data || 'Failed to reject.' });
        }
    };

    return (
        <div className="max-w-6xl mx-auto px-4 py-12 min-h-screen">
            {status && <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} />}

            {rejectingId && (
                <div className="fixed inset-0 z-[100] bg-black/50 flex items-center justify-center p-4 backdrop-blur-sm">
                    <div className="bg-white dark:bg-modtale-card w-full max-w-md p-6 rounded-2xl shadow-2xl border border-slate-200 dark:border-white/10">
                        <h3 className="text-xl font-bold mb-4 text-slate-900 dark:text-white">Reject Project</h3>
                        <textarea
                            value={rejectReason}
                            onChange={e => setRejectReason(e.target.value)}
                            placeholder="Reason for rejection (sent to user)..."
                            className="w-full h-32 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl p-3 mb-4 focus:ring-2 focus:ring-red-500 outline-none"
                        />
                        <div className="flex justify-end gap-3">
                            <button onClick={() => setRejectingId(null)} className="px-4 py-2 font-bold text-slate-500">Cancel</button>
                            <button onClick={handleReject} className="px-4 py-2 bg-red-500 text-white rounded-lg font-bold">Reject Project</button>
                        </div>
                    </div>
                </div>
            )}

            <div className="flex items-center gap-4 mb-8">
                <div className="bg-red-500/10 p-3 rounded-xl"><Shield className="w-8 h-8 text-red-500" /></div>
                <div>
                    <h1 className="text-3xl font-black text-slate-900 dark:text-white">Admin Console</h1>
                    <p className="text-slate-500">Manage users and content verification.</p>
                </div>
            </div>

            <div className="flex gap-2 mb-8 border-b border-slate-200 dark:border-white/10">
                <button
                    onClick={() => setActiveTab('verification')}
                    className={`px-6 py-3 font-bold text-sm border-b-2 transition-colors ${activeTab === 'verification' ? 'border-modtale-accent text-slate-900 dark:text-white' : 'border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-white'}`}
                >
                    Verification Queue {pendingProjects.length > 0 && <span className="ml-2 bg-red-500 text-white text-[10px] px-1.5 py-0.5 rounded-full">{pendingProjects.length}</span>}
                </button>
                <button
                    onClick={() => setActiveTab('users')}
                    className={`px-6 py-3 font-bold text-sm border-b-2 transition-colors ${activeTab === 'users' ? 'border-modtale-accent text-slate-900 dark:text-white' : 'border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-white'}`}
                >
                    User Management
                </button>
            </div>

            {activeTab === 'verification' && (
                <div className="space-y-4">
                    {loadingQueue ? (
                        <div className="text-center py-12 text-slate-500">Loading queue...</div>
                    ) : pendingProjects.length === 0 ? (
                        <div className="text-center py-12 bg-white dark:bg-modtale-card rounded-xl border border-slate-200 dark:border-white/5">
                            <Check className="w-12 h-12 text-green-500 mx-auto mb-4" />
                            <h3 className="text-lg font-bold text-slate-900 dark:text-white">All Caught Up!</h3>
                            <p className="text-slate-500">No projects pending verification.</p>
                        </div>
                    ) : (
                        pendingProjects.map(p => (
                            <div key={p.id} className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl p-6 flex flex-col md:flex-row gap-6">
                                <img src={p.imageUrl} className="w-24 h-24 rounded-xl object-cover bg-slate-100" />
                                <div className="flex-1">
                                    <div className="flex items-start justify-between mb-2">
                                        <div>
                                            <h3 className="text-xl font-black text-slate-900 dark:text-white flex items-center gap-2">
                                                {p.title}
                                                <span className="text-[10px] uppercase font-bold px-2 py-0.5 bg-slate-100 dark:bg-white/10 rounded text-slate-500">{p.classification}</span>
                                            </h3>
                                            <p className="text-sm text-slate-500 font-bold mb-1">by {p.author}</p>
                                        </div>
                                        <div className="text-xs text-slate-400 font-mono">Submitted: {p.updatedAt}</div>
                                    </div>
                                    <p className="text-slate-600 dark:text-slate-300 text-sm mb-4 line-clamp-2">{p.description}</p>

                                    <div className="flex flex-wrap gap-2 text-xs text-slate-500 mb-4">
                                        {p.tags?.map(t => <span key={t} className="px-2 py-1 bg-slate-100 dark:bg-white/5 rounded">{t}</span>)}
                                    </div>

                                    <div className="flex items-center gap-4">
                                        <a href={`/mod/${p.id}`} target="_blank" rel="noreferrer" className="text-sm font-bold text-modtale-accent flex items-center gap-1 hover:underline">
                                            <ExternalLink className="w-4 h-4" /> Preview Page
                                        </a>
                                        <div className="flex-1" />
                                        <button onClick={() => setRejectingId(p.id)} className="px-4 py-2 bg-red-500/10 text-red-600 hover:bg-red-500 hover:text-white rounded-lg font-bold text-xs transition-colors">
                                            Reject
                                        </button>
                                        <button onClick={() => handleApprove(p.id)} className="px-4 py-2 bg-green-500 hover:bg-green-600 text-white rounded-lg font-bold text-xs transition-colors shadow-lg shadow-green-500/20">
                                            Approve & Publish
                                        </button>
                                    </div>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            )}

            {activeTab === 'users' && (
                <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl p-8 shadow-lg">
                    <form onSubmit={handleSearch} className="flex gap-4 mb-8">
                        <div className="relative flex-1">
                            <UserIcon className="absolute left-3 top-3.5 w-5 h-5 text-slate-400" />
                            <input
                                type="text"
                                placeholder="Enter Username (exact match)..."
                                className="w-full pl-10 px-4 py-3 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl focus:ring-2 focus:ring-modtale-accent outline-none dark:text-white font-bold"
                                value={username}
                                onChange={e => setUsername(e.target.value)}
                            />
                        </div>
                        <button
                            type="submit"
                            disabled={loading || !username}
                            className="bg-slate-900 dark:bg-white text-white dark:text-black px-6 rounded-xl font-bold hover:opacity-90 transition-opacity disabled:opacity-50 flex items-center gap-2"
                        >
                            {loading ? 'Searching...' : <><Search className="w-4 h-4" /> Find User</>}
                        </button>
                    </form>

                    {foundUser && (
                        <div className="border border-slate-200 dark:border-white/10 rounded-xl p-6 bg-slate-50 dark:bg-white/5 animate-in fade-in slide-in-from-bottom-2">
                            <div className="flex items-center gap-4 mb-6">
                                <img src={foundUser.avatarUrl} alt={foundUser.username} className="w-16 h-16 rounded-xl border border-slate-200 dark:border-white/10" />
                                <div>
                                    <h3 className="text-xl font-black text-slate-900 dark:text-white">{foundUser.username}</h3>
                                    <div className="flex items-center gap-2 mt-1">
                                        <span className="text-xs font-bold text-slate-500 uppercase">Roles:</span>
                                        <div className="flex gap-1">
                                            {foundUser.roles?.map((r: string) => (
                                                <span key={r} className="px-2 py-0.5 bg-blue-500/10 text-blue-500 text-[10px] font-bold rounded border border-blue-500/20">{r}</span>
                                            ))}
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <button
                                    onClick={() => handleUpdateTier('ENTERPRISE')}
                                    disabled={loading || foundUser.tier === 'ENTERPRISE'}
                                    className={`p-4 rounded-xl border-2 text-left transition-all ${foundUser.tier === 'ENTERPRISE' ? 'border-purple-500 bg-purple-50 dark:bg-purple-900/10 cursor-default' : 'border-slate-200 dark:border-white/10 hover:border-purple-500 hover:bg-purple-50 dark:hover:bg-purple-900/10'}`}
                                >
                                    <div className="flex justify-between items-start mb-2">
                                        <span className="font-black text-purple-600 dark:text-purple-400 flex items-center gap-2"><Zap className="w-4 h-4" /> Enterprise Tier</span>
                                        {foundUser.tier === 'ENTERPRISE' && <Check className="w-5 h-5 text-purple-500" />}
                                    </div>
                                    <p className="text-xs text-slate-500 dark:text-slate-400">High volume limits (1000 req/min). For CI/CD & Apps.</p>
                                </button>

                                <button
                                    onClick={handleToggleAdmin}
                                    disabled={loading || (foundUser.roles && foundUser.roles.includes('ADMIN'))}
                                    className={`p-4 rounded-xl border-2 text-left transition-all ${foundUser.roles?.includes('ADMIN') ? 'border-red-500 bg-red-50 dark:bg-red-900/10 cursor-default' : 'border-slate-200 dark:border-white/10 hover:border-red-500 hover:bg-red-50 dark:hover:bg-red-900/10'}`}
                                >
                                    <div className="flex justify-between items-start mb-2">
                                        <span className="font-black text-red-600 dark:text-red-400 flex items-center gap-2"><Shield className="w-4 h-4" /> Grant Admin</span>
                                        {foundUser.roles?.includes('ADMIN') && <Check className="w-5 h-5 text-red-500" />}
                                    </div>
                                    <p className="text-xs text-slate-500 dark:text-slate-400">Full access to moderation tools and verification queue.</p>
                                </button>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};