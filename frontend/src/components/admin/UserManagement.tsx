import React, { useState, useEffect, useRef } from 'react';
import { User as UserIcon, Search, Shield, Check, Zap } from 'lucide-react';
import { api } from '../../utils/api';

export const UserManagement: React.FC<{ setStatus: (s: any) => void }> = ({ setStatus }) => {
    const [username, setUsername] = useState('');
    const [loading, setLoading] = useState(false);
    const [foundUser, setFoundUser] = useState<any>(null);

    const [searchResults, setSearchResults] = useState<any[]>([]);
    const [showResults, setShowResults] = useState(false);
    const searchTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
    const wrapperRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) {
                setShowResults(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const val = e.target.value;
        setUsername(val);

        if (searchTimeout.current) clearTimeout(searchTimeout.current);

        if (val.trim().length > 1) {
            searchTimeout.current = setTimeout(async () => {
                try {
                    const res = await api.get('/users/search', { params: { query: val } });
                    setSearchResults(res.data);
                    setShowResults(true);
                } catch (e) {
                    setSearchResults([]);
                }
            }, 300);
        } else {
            setSearchResults([]);
            setShowResults(false);
        }
    };

    const handleSelectUser = (user: any) => {
        setUsername(user.username);
        setFoundUser(user);
        setShowResults(false);
        fetchUserProfile(user.username);
    };

    const fetchUserProfile = async (name: string) => {
        setLoading(true);
        try {
            const res = await api.get(`/user/profile/${name}`);
            setFoundUser(res.data);
        } catch (e) {
            setStatus({ type: 'error', title: 'User Not Found', msg: `Could not load details for "${name}"` });
        } finally {
            setLoading(false);
        }
    };

    const handleSearch = async (e: React.FormEvent) => {
        e.preventDefault();
        setFoundUser(null);
        fetchUserProfile(username);
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
        const hasAdmin = foundUser.roles && foundUser.roles.includes('ADMIN');

        try {
            if (hasAdmin) {
                await api.delete(`/admin/users/${foundUser.username}/role`, { params: { role: 'ADMIN' } });
                const roles = foundUser.roles.filter((r: string) => r !== 'ADMIN');
                setFoundUser({ ...foundUser, roles });
                setStatus({ type: 'info', title: 'Role Updated', msg: `Admin role revoked from ${foundUser.username}.` });
            } else {
                await api.post(`/admin/users/${foundUser.username}/role`, null, { params: { role: 'ADMIN' } });
                const roles = foundUser.roles || [];
                roles.push('ADMIN');
                setFoundUser({ ...foundUser, roles });
                setStatus({ type: 'success', title: 'Role Updated', msg: `Admin role granted to ${foundUser.username}.` });
            }
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Update Failed', msg: e.response?.data || 'Server error occurred.' });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-[2rem] p-10 shadow-2xl shadow-black/5">
            <form onSubmit={handleSearch} className="flex gap-4 mb-10 relative z-50">
                <div className="relative flex-1 group" ref={wrapperRef}>
                    <UserIcon className="absolute left-5 top-4 w-5 h-5 text-slate-400 group-focus-within:text-modtale-accent transition-colors" />
                    <input
                        type="text"
                        placeholder="Enter Username to manage..."
                        className="w-full pl-14 px-6 py-4 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-2xl focus:ring-2 focus:ring-modtale-accent outline-none dark:text-white font-bold transition-all placeholder:font-medium"
                        value={username}
                        onChange={handleInputChange}
                        onFocus={() => { if(searchResults.length > 0) setShowResults(true); }}
                    />

                    {showResults && searchResults.length > 0 && (
                        <div className="absolute top-full left-0 right-0 mt-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-2xl shadow-xl overflow-hidden max-h-60 overflow-y-auto custom-scrollbar animate-in fade-in slide-in-from-top-2 duration-200">
                            {searchResults.map(user => (
                                <button
                                    key={user.id}
                                    type="button"
                                    onClick={() => handleSelectUser(user)}
                                    className="w-full text-left px-5 py-3 hover:bg-slate-50 dark:hover:bg-white/5 flex items-center gap-3 transition-colors border-b border-slate-100 dark:border-white/5 last:border-0"
                                >
                                    <div className="w-8 h-8 rounded-full bg-slate-200 dark:bg-white/10 overflow-hidden shrink-0">
                                        <img src={user.avatarUrl} alt="" className="w-full h-full object-cover" />
                                    </div>
                                    <p className="text-sm font-bold text-slate-900 dark:text-white">{user.username}</p>
                                </button>
                            ))}
                        </div>
                    )}
                </div>
                <button
                    type="submit"
                    disabled={loading || !username}
                    className="bg-modtale-accent text-white px-10 rounded-2xl font-black hover:bg-modtale-accentHover transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2 shadow-xl shadow-modtale-accent/20"
                >
                    {loading ? '...' : <><Search className="w-5 h-5" /> Search</>}
                </button>
            </form>

            {foundUser && (
                <div className="border border-slate-200 dark:border-white/10 rounded-3xl p-8 bg-slate-50/50 dark:bg-white/[0.02] animate-in fade-in slide-in-from-bottom-4 duration-500">
                    <div className="flex items-center gap-8 mb-10">
                        <div className="p-1 bg-white dark:bg-white/10 rounded-3xl shadow-lg">
                            <img src={foundUser.avatarUrl} alt={foundUser.username} className="w-24 h-24 rounded-2xl object-cover" />
                        </div>
                        <div>
                            <h3 className="text-3xl font-black text-slate-900 dark:text-white tracking-tight">{foundUser.username}</h3>
                            <div className="flex items-center gap-3 mt-3">
                                <span className="text-xs font-bold text-slate-400 uppercase tracking-widest">Active Roles</span>
                                <div className="flex gap-2">
                                    {foundUser.roles && foundUser.roles.length > 0 ? foundUser.roles.map((r: string) => (
                                        <span key={r} className="px-3 py-1 bg-blue-500/10 text-blue-600 dark:text-blue-400 text-[10px] font-black rounded-lg border border-blue-500/20">{r}</span>
                                    )) : <span className="text-xs text-slate-400 italic font-medium">No special roles</span>}
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <button
                            onClick={handleToggleAdmin}
                            disabled={loading}
                            className={`relative p-8 rounded-3xl border-2 text-left transition-all duration-300 group overflow-hidden ${foundUser.roles?.includes('ADMIN') ? 'border-red-500/30 bg-red-500/5 hover:bg-red-500/10' : 'border-slate-200 dark:border-white/5 hover:border-red-500 hover:bg-white dark:hover:bg-white/5 shadow-sm hover:shadow-xl'}`}
                        >
                            <div className="relative z-10">
                                <div className="flex justify-between items-start mb-4">
                                                <span className={`font-black text-xl flex items-center gap-3 ${foundUser.roles?.includes('ADMIN') ? 'text-red-600 dark:text-red-400' : 'text-slate-900 dark:text-white group-hover:text-red-600 dark:group-hover:text-red-400'}`}>
                                                    <Shield className="w-6 h-6" /> Admin Privileges
                                                </span>
                                    {foundUser.roles?.includes('ADMIN') && <Check className="w-8 h-8 text-red-500 bg-red-100 dark:bg-red-900/30 p-1.5 rounded-full" />}
                                </div>
                                <p className="text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                                    {foundUser.roles?.includes('ADMIN')
                                        ? 'User currently has full administrative access. Click to revoke immediately.'
                                        : 'Granting Admin access will allow this user to approve projects, manage users, and modify content.'}
                                </p>
                            </div>
                        </button>

                        <button
                            onClick={() => handleUpdateTier(foundUser.tier === 'ENTERPRISE' ? 'USER' : 'ENTERPRISE')}
                            disabled={loading}
                            className={`relative p-8 rounded-3xl border-2 text-left transition-all duration-300 group overflow-hidden ${foundUser.tier === 'ENTERPRISE' ? 'border-purple-500/30 bg-purple-500/5 hover:bg-purple-500/10' : 'border-slate-200 dark:border-white/5 hover:border-purple-500 hover:bg-white dark:hover:bg-white/5 shadow-sm hover:shadow-xl'}`}
                        >
                            <div className="relative z-10">
                                <div className="flex justify-between items-start mb-4">
                                                <span className={`font-black text-xl flex items-center gap-3 ${foundUser.tier === 'ENTERPRISE' ? 'text-purple-600 dark:text-purple-400' : 'text-slate-900 dark:text-white group-hover:text-purple-600 dark:group-hover:text-purple-400'}`}>
                                                    <Zap className="w-6 h-6" /> Enterprise Tier
                                                </span>
                                    {foundUser.tier === 'ENTERPRISE' && <Check className="w-8 h-8 text-purple-500 bg-purple-100 dark:bg-purple-900/30 p-1.5 rounded-full" />}
                                </div>
                                <p className="text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                                    {foundUser.tier === 'ENTERPRISE'
                                        ? 'User is on the Enterprise Tier. Click to downgrade to Standard User.'
                                        : 'Granting Enterprise status allows higher API rate limits (1000 req/min) for CI/CD.'}
                                </p>
                            </div>
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};