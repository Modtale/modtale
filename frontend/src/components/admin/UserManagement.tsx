import React, { useState, useEffect, useRef } from 'react';
import { User as UserIcon, Search, Shield, Check, Zap, Trash2, Ban, Mail, Code, Lock, X, AlertTriangle, FileJson } from 'lucide-react';
import { api } from '../../utils/api';

export const UserManagement: React.FC<{ setStatus: (s: any) => void }> = ({ setStatus }) => {
    const [viewMode, setViewMode] = useState<'users' | 'bans'>('users');
    const [username, setUsername] = useState('');
    const [loading, setLoading] = useState(false);
    const [foundUser, setFoundUser] = useState<any>(null);
    const [currentAdmin, setCurrentAdmin] = useState<any>(null);

    const [searchResults, setSearchResults] = useState<any[]>([]);
    const [showResults, setShowResults] = useState(false);
    const searchTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [deleteConfirmUsername, setDeleteConfirmUsername] = useState('');

    const [bannedEmails, setBannedEmails] = useState<any[]>([]);
    const [banEmailInput, setBanEmailInput] = useState('');
    const [banReasonInput, setBanReasonInput] = useState('');

    const [showBanConfirm, setShowBanConfirm] = useState(false);
    const [banConfirmInput, setBanConfirmInput] = useState('');
    const [banUserReason, setBanUserReason] = useState('');

    const [showRawModal, setShowRawModal] = useState(false);
    const [rawJsonStr, setRawJsonStr] = useState('');
    const [jsonError, setJsonError] = useState<string | null>(null);

    useEffect(() => {
        api.get('/user/me').then(res => setCurrentAdmin(res.data)).catch(() => {});
    }, []);

    useEffect(() => {
        if (viewMode === 'bans') {
            fetchBannedEmails();
        }
    }, [viewMode]);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) {
                setShowResults(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const isSuperAdmin = currentAdmin?.id === '692620f7c2f3266e23ac0ded';
    const isTargetAdmin = foundUser?.roles?.includes('ADMIN');
    const canManageUser = isSuperAdmin || !isTargetAdmin;

    const fetchBannedEmails = async () => {
        setLoading(true);
        try {
            const res = await api.get('/admin/users/bans');
            setBannedEmails(res.data);
        } catch (e) {
            setStatus({ type: 'error', title: 'Error', msg: 'Failed to fetch banned emails' });
        } finally {
            setLoading(false);
        }
    };

    const handleBanEmail = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!banEmailInput || !banReasonInput) return;
        setLoading(true);
        try {
            await api.post('/admin/users/bans', { email: banEmailInput, reason: banReasonInput });
            setStatus({ type: 'success', title: 'Banned', msg: `Email ${banEmailInput} has been banned.` });
            setBanEmailInput('');
            setBanReasonInput('');
            fetchBannedEmails();
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Error', msg: e.response?.data || 'Failed to ban email.' });
        } finally {
            setLoading(false);
        }
    };

    const handleBanUserEmail = async () => {
        if (!foundUser || !foundUser.email) return;
        if (banConfirmInput !== foundUser.email) return;

        setLoading(true);
        try {
            await api.post('/admin/users/bans', { email: foundUser.email, reason: banUserReason || "Banned from user management" });

            await api.delete(`/admin/users/${foundUser.username}`);

            setStatus({ type: 'success', title: 'User Banned', msg: `Email ${foundUser.email} banned and account deleted.` });
            setFoundUser(null);
            setUsername('');
            setShowBanConfirm(false);
            setBanUserReason('');
            setBanConfirmInput('');
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Ban Failed', msg: e.response?.data || 'Could not ban user completely.' });
        } finally {
            setLoading(false);
        }
    };

    const handleUnbanEmail = async (email: string) => {
        setLoading(true);
        try {
            await api.delete('/admin/users/bans', { params: { email } });
            setStatus({ type: 'success', title: 'Unbanned', msg: 'Email unbanned successfully.' });
            fetchBannedEmails();
        } catch (e) {
            setStatus({ type: 'error', title: 'Error', msg: 'Failed to unban email.' });
        } finally {
            setLoading(false);
        }
    };

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
            const res = await api.get(`/admin/users/${name}`);
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

    const handleDeleteUser = async () => {
        if (!foundUser || deleteConfirmUsername !== foundUser.username) return;
        setLoading(true);
        try {
            await api.delete(`/admin/users/${foundUser.username}`);
            setStatus({ type: 'success', title: 'User Deleted', msg: `User ${foundUser.username} has been permanently deleted.` });
            setFoundUser(null);
            setUsername('');
            setShowDeleteConfirm(false);
            setDeleteConfirmUsername('');
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Delete Failed', msg: e.response?.data || 'Could not delete user.' });
        } finally {
            setLoading(false);
        }
    };

    const openRawEdit = async () => {
        setLoading(true);
        try {
            const res = await api.get(`/admin/users/${foundUser.username}/raw`);
            setRawJsonStr(JSON.stringify(res.data, null, 2));
            setJsonError(null);
            setShowRawModal(true);
        } catch (e) {
            setStatus({ type: 'error', title: 'Error', msg: 'Failed to fetch raw user data.' });
        } finally {
            setLoading(false);
        }
    };

    const handleJsonChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
        const val = e.target.value;
        setRawJsonStr(val);
        try {
            JSON.parse(val);
            setJsonError(null);
        } catch (err: any) {
            setJsonError(err.message);
        }
    };

    const handleJsonKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === 'Tab') {
            e.preventDefault();
            const target = e.currentTarget;
            const start = target.selectionStart;
            const end = target.selectionEnd;
            const val = target.value;

            setRawJsonStr(val.substring(0, start) + '  ' + val.substring(end));

            setTimeout(() => {
                if (textareaRef.current) {
                    textareaRef.current.selectionStart = textareaRef.current.selectionEnd = start + 2;
                }
            }, 0);
        }
    };

    const formatJson = () => {
        try {
            const parsed = JSON.parse(rawJsonStr);
            setRawJsonStr(JSON.stringify(parsed, null, 2));
            setJsonError(null);
        } catch (e) {
        }
    };

    const saveRawEdit = async () => {
        try {
            setLoading(true);
            const parsed = JSON.parse(rawJsonStr);
            await api.put(`/admin/users/${foundUser.username}/raw`, parsed);
            setStatus({ type: 'success', title: 'Saved', msg: 'Raw user metadata updated successfully.' });
            setShowRawModal(false);
            fetchUserProfile(foundUser.username);
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Error', msg: e instanceof SyntaxError ? 'Invalid JSON format.' : (e.response?.data || 'Server error saving raw data.') });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-[2rem] p-10 shadow-2xl shadow-black/5">
            <div className="flex gap-4 mb-8 border-b border-slate-200 dark:border-white/5 pb-4">
                <button
                    onClick={() => setViewMode('users')}
                    className={`px-4 py-2 rounded-xl font-bold transition-all ${viewMode === 'users' ? 'bg-modtale-accent text-white' : 'text-slate-500 hover:bg-slate-100 dark:hover:bg-white/5'}`}
                >
                    User Search
                </button>
                <button
                    onClick={() => setViewMode('bans')}
                    className={`px-4 py-2 rounded-xl font-bold transition-all ${viewMode === 'bans' ? 'bg-red-500 text-white' : 'text-slate-500 hover:bg-slate-100 dark:hover:bg-white/5'}`}
                >
                    Email Bans
                </button>
            </div>

            {viewMode === 'users' && (
                <>
                    {showBanConfirm && foundUser?.email && (
                        <div className="fixed inset-0 z-[200] bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-200">
                            <div className="bg-white dark:bg-slate-900 w-full max-w-md p-6 rounded-3xl shadow-2xl border border-slate-200 dark:border-white/10">
                                <h3 className="text-xl font-black text-slate-900 dark:text-white mb-2 text-red-500 flex items-center gap-2">
                                    <Ban className="w-5 h-5"/> Ban Email & Delete Account
                                </h3>
                                <p className="text-slate-500 mb-4 text-sm">
                                    This will ban <strong>{foundUser.email}</strong> and permanently delete the user <strong>{foundUser.username}</strong>.
                                </p>

                                <label className="block text-xs font-bold text-slate-400 uppercase mb-2">Reason</label>
                                <input
                                    type="text"
                                    value={banUserReason}
                                    onChange={e => setBanUserReason(e.target.value)}
                                    placeholder="e.g. Spam, Violation of TOS..."
                                    className="w-full p-3 mb-4 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl text-sm"
                                />

                                <label className="block text-xs font-bold text-slate-400 uppercase mb-2">Confirm Email</label>
                                <input
                                    type="email"
                                    value={banConfirmInput}
                                    onChange={e => setBanConfirmInput(e.target.value)}
                                    placeholder={foundUser.email}
                                    className="w-full p-3 mb-6 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl font-bold dark:text-white"
                                />

                                <div className="flex gap-3">
                                    <button
                                        onClick={() => setShowBanConfirm(false)}
                                        className="flex-1 py-3 bg-slate-100 dark:bg-white/5 font-bold rounded-xl text-slate-600 dark:text-slate-300"
                                    >Cancel</button>
                                    <button
                                        onClick={handleBanUserEmail}
                                        disabled={banConfirmInput !== foundUser.email || loading || !banUserReason}
                                        className="flex-1 py-3 bg-red-500 hover:bg-red-600 text-white font-bold rounded-xl disabled:opacity-50"
                                    >Confirm Ban</button>
                                </div>
                            </div>
                        </div>
                    )}

                    {showDeleteConfirm && (
                        <div className="fixed inset-0 z-[200] bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-200">
                            <div className="bg-white dark:bg-slate-900 w-full max-w-md p-6 rounded-3xl shadow-2xl border border-slate-200 dark:border-white/10">
                                <h3 className="text-xl font-black text-slate-900 dark:text-white mb-2 text-red-500">Danger Zone</h3>
                                <p className="text-slate-500 mb-6">You are about to delete <strong>{foundUser.username}</strong>. This is irreversible. Type the username to confirm.</p>

                                <input
                                    type="text"
                                    value={deleteConfirmUsername}
                                    onChange={e => setDeleteConfirmUsername(e.target.value)}
                                    placeholder={foundUser.username}
                                    className="w-full p-3 mb-4 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl font-bold dark:text-white"
                                />

                                <div className="flex gap-3">
                                    <button
                                        onClick={() => setShowDeleteConfirm(false)}
                                        className="flex-1 py-3 bg-slate-100 dark:bg-white/5 font-bold rounded-xl text-slate-600 dark:text-slate-300"
                                    >Cancel</button>
                                    <button
                                        onClick={handleDeleteUser}
                                        disabled={deleteConfirmUsername !== foundUser.username || loading}
                                        className="flex-1 py-3 bg-red-500 hover:bg-red-600 text-white font-bold rounded-xl disabled:opacity-50"
                                    >Delete User</button>
                                </div>
                            </div>
                        </div>
                    )}

                    {showRawModal && (
                        <div className="fixed inset-0 z-[300] bg-black/80 backdrop-blur-md flex items-center justify-center p-4 animate-in fade-in duration-200">
                            <div className="bg-slate-900 w-full max-w-5xl rounded-3xl shadow-2xl border border-white/10 flex flex-col overflow-hidden h-[85vh]">
                                <div className="px-6 py-4 border-b border-white/10 flex justify-between items-center bg-black/40">
                                    <div>
                                        <h3 className="font-bold text-white flex items-center gap-2 text-lg">
                                            <FileJson className="w-5 h-5 text-indigo-400" /> JSON Editor
                                        </h3>
                                        <p className="text-xs text-slate-400 mt-1 font-mono">Editing user: {foundUser.username}</p>
                                    </div>
                                    <button onClick={() => setShowRawModal(false)} className="p-2 hover:bg-white/10 rounded-full transition-colors text-white/50 hover:text-white">
                                        <X className="w-5 h-5" />
                                    </button>
                                </div>
                                <div className="flex-1 p-6 overflow-hidden flex flex-col bg-[#0d1117]">
                                    <div className="flex items-center justify-between mb-3">
                                        <div className="flex items-center gap-2 text-xs font-bold bg-amber-500/10 text-amber-400 px-3 py-1.5 rounded-lg border border-amber-500/20">
                                            <AlertTriangle className="w-4 h-4 shrink-0" /> Secure fields (passwords, MFA secrets) are intercepted and cannot be overwritten.
                                        </div>
                                        <button onClick={formatJson} className="px-4 py-1.5 bg-white/5 hover:bg-white/10 text-slate-300 hover:text-white text-xs font-bold rounded-lg border border-white/10 transition-colors">
                                            Format JSON
                                        </button>
                                    </div>
                                    <div className="relative flex-1 rounded-xl border border-white/10 overflow-hidden bg-black/20 flex flex-col">
                                        <textarea
                                            ref={textareaRef}
                                            value={rawJsonStr}
                                            onChange={handleJsonChange}
                                            onKeyDown={handleJsonKeyDown}
                                            className={`flex-1 w-full p-4 bg-transparent text-slate-300 font-mono text-sm outline-none resize-none whitespace-pre overflow-auto custom-scrollbar transition-shadow ${jsonError ? 'shadow-[inset_0_0_0_2px_rgba(239,68,68,0.5)]' : 'focus:shadow-[inset_0_0_0_2px_rgba(99,102,241,0.5)]'}`}
                                            spellCheck={false}
                                        />
                                        {jsonError && (
                                            <div className="absolute bottom-0 left-0 right-0 bg-red-500/90 text-white text-xs font-bold px-4 py-2 truncate shadow-lg backdrop-blur-sm">
                                                Parse Error: {jsonError}
                                            </div>
                                        )}
                                    </div>
                                </div>
                                <div className="p-4 border-t border-white/10 bg-black/40 flex justify-end gap-3 px-6">
                                    <button onClick={() => setShowRawModal(false)} className="px-6 py-2.5 rounded-xl font-bold text-slate-300 hover:bg-white/5 transition-colors">Cancel</button>
                                    <button onClick={saveRawEdit} disabled={loading || !!jsonError} className="px-8 py-2.5 bg-indigo-500 hover:bg-indigo-600 text-white rounded-xl font-bold transition-colors disabled:opacity-50 shadow-lg shadow-indigo-500/20 flex items-center gap-2">
                                        {loading ? 'Saving...' : 'Save Changes'}
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}

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
                                    {foundUser.email && (
                                        <div className="mt-2 text-sm text-slate-500 flex items-center gap-2">
                                            <Mail className="w-4 h-4" />
                                            {foundUser.email}
                                            {foundUser.emailVerified && (
                                                <span title="Verified">
                                                    <Check className="w-4 h-4 text-emerald-500" />
                                                </span>
                                            )}
                                        </div>
                                    )}
                                </div>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                                <button
                                    onClick={openRawEdit}
                                    disabled={loading || !isSuperAdmin}
                                    className={`relative p-8 rounded-3xl border-2 text-left transition-all duration-300 group overflow-hidden ${!isSuperAdmin ? 'opacity-50 cursor-not-allowed border-slate-200 dark:border-white/5' : 'border-indigo-500/20 hover:border-indigo-600 hover:bg-indigo-600/10 shadow-sm hover:shadow-xl'}`}
                                >
                                    <div className="relative z-10">
                                        <div className="flex justify-between items-start mb-4">
                                            <span className={`font-black text-xl flex items-center gap-3 ${!isSuperAdmin ? 'text-slate-900 dark:text-white' : 'text-indigo-600 dark:text-indigo-400'}`}>
                                                <Code className="w-6 h-6" /> Edit Raw JSON
                                            </span>
                                            {!isSuperAdmin && <Lock className="w-5 h-5 text-slate-400" />}
                                        </div>
                                        <p className="text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                                            {!isSuperAdmin ? 'Only the Super Admin can edit raw user metadata.' : 'Modify the underlying MongoDB document for this user. Advanced use only.'}
                                        </p>
                                    </div>
                                </button>

                                <button
                                    onClick={handleToggleAdmin}
                                    disabled={loading || !isSuperAdmin}
                                    className={`relative p-8 rounded-3xl border-2 text-left transition-all duration-300 group overflow-hidden ${!isSuperAdmin ? 'opacity-50 cursor-not-allowed border-slate-200 dark:border-white/5' : (foundUser.roles?.includes('ADMIN') ? 'border-red-500/30 bg-red-500/5 hover:bg-red-500/10' : 'border-slate-200 dark:border-white/5 hover:border-red-500 hover:bg-white dark:hover:bg-white/5 shadow-sm hover:shadow-xl')}`}
                                >
                                    <div className="relative z-10">
                                        <div className="flex justify-between items-start mb-4">
                                            <span className={`font-black text-xl flex items-center gap-3 ${foundUser.roles?.includes('ADMIN') && isSuperAdmin ? 'text-red-600 dark:text-red-400' : 'text-slate-900 dark:text-white group-hover:text-red-600 dark:group-hover:text-red-400'}`}>
                                                <Shield className="w-6 h-6" /> Admin Privileges
                                            </span>
                                            {!isSuperAdmin && <Lock className="w-5 h-5 text-slate-400" />}
                                            {isSuperAdmin && foundUser.roles?.includes('ADMIN') && <Check className="w-8 h-8 text-red-500 bg-red-100 dark:bg-red-900/30 p-1.5 rounded-full" />}
                                        </div>
                                        <p className="text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                                            {!isSuperAdmin
                                                ? 'Only the Super Admin can modify administrative roles.'
                                                : (foundUser.roles?.includes('ADMIN')
                                                    ? 'User currently has full administrative access. Click to revoke immediately.'
                                                    : 'Granting Admin access will allow this user to approve projects, manage users, and modify content.')}
                                        </p>
                                    </div>
                                </button>

                                <button
                                    onClick={() => handleUpdateTier(foundUser.tier === 'ENTERPRISE' ? 'USER' : 'ENTERPRISE')}
                                    disabled={loading || !isSuperAdmin}
                                    className={`relative p-8 rounded-3xl border-2 text-left transition-all duration-300 group overflow-hidden ${!isSuperAdmin ? 'opacity-50 cursor-not-allowed border-slate-200 dark:border-white/5' : (foundUser.tier === 'ENTERPRISE' ? 'border-purple-500/30 bg-purple-500/5 hover:bg-purple-500/10' : 'border-slate-200 dark:border-white/5 hover:border-purple-500 hover:bg-white dark:hover:bg-white/5 shadow-sm hover:shadow-xl')}`}
                                >
                                    <div className="relative z-10">
                                        <div className="flex justify-between items-start mb-4">
                                            <span className={`font-black text-xl flex items-center gap-3 ${foundUser.tier === 'ENTERPRISE' && isSuperAdmin ? 'text-purple-600 dark:text-purple-400' : 'text-slate-900 dark:text-white group-hover:text-purple-600 dark:group-hover:text-purple-400'}`}>
                                                <Zap className="w-6 h-6" /> Enterprise Tier
                                            </span>
                                            {!isSuperAdmin && <Lock className="w-5 h-5 text-slate-400" />}
                                            {isSuperAdmin && foundUser.tier === 'ENTERPRISE' && <Check className="w-8 h-8 text-purple-500 bg-purple-100 dark:bg-purple-900/30 p-1.5 rounded-full" />}
                                        </div>
                                        <p className="text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                                            {!isSuperAdmin
                                                ? 'Only the Super Admin can grant Enterprise API tiers.'
                                                : (foundUser.tier === 'ENTERPRISE'
                                                    ? 'User is on the Enterprise Tier. Click to downgrade to Standard User.'
                                                    : 'Granting Enterprise status allows higher API rate limits (1000 req/min) for CI/CD.')}
                                        </p>
                                    </div>
                                </button>

                                <button
                                    onClick={() => setShowDeleteConfirm(true)}
                                    disabled={loading || !canManageUser}
                                    className={`relative p-8 rounded-3xl border-2 text-left transition-all duration-300 group overflow-hidden ${!canManageUser ? 'opacity-50 cursor-not-allowed border-slate-200 dark:border-white/5' : 'border-slate-200 dark:border-white/5 hover:border-red-500 hover:bg-red-500/5'}`}
                                >
                                    <div className="relative z-10">
                                        <div className="flex justify-between items-start mb-4">
                                            <span className="font-black text-xl flex items-center gap-3 text-slate-900 dark:text-white group-hover:text-red-500">
                                                <Trash2 className="w-6 h-6" /> Delete Account
                                            </span>
                                            {!canManageUser && <Lock className="w-5 h-5 text-slate-400" />}
                                        </div>
                                        <p className="text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                                            Permanently delete this user, their projects, and all associated data. This action cannot be undone.
                                        </p>
                                    </div>
                                </button>

                                {foundUser.email && (
                                    <button
                                        onClick={() => setShowBanConfirm(true)}
                                        disabled={loading || !canManageUser}
                                        className={`relative p-8 rounded-3xl border-2 text-left transition-all duration-300 group overflow-hidden ${!canManageUser ? 'opacity-50 cursor-not-allowed border-slate-200 dark:border-white/5' : 'border-red-500/20 hover:border-red-600 hover:bg-red-600/10'}`}
                                    >
                                        <div className="relative z-10">
                                            <div className="flex justify-between items-start mb-4">
                                                <span className="font-black text-xl flex items-center gap-3 text-slate-900 dark:text-white group-hover:text-red-600">
                                                    <Ban className="w-6 h-6" /> Ban Email & Delete
                                                </span>
                                                {!canManageUser && <Lock className="w-5 h-5 text-slate-400" />}
                                            </div>
                                            <p className="text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                                                Ban <strong>{foundUser.email}</strong> from ever registering again and immediately delete this account.
                                            </p>
                                        </div>
                                    </button>
                                )}
                            </div>
                        </div>
                    )}
                </>
            )}

            {viewMode === 'bans' && (
                <div className="animate-in fade-in slide-in-from-right-4 duration-300">
                    <form onSubmit={handleBanEmail} className="mb-8 p-6 bg-slate-50 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/5">
                        <h3 className="font-bold text-slate-900 dark:text-white mb-4 flex items-center gap-2">
                            <Ban className="w-5 h-5 text-red-500" /> Ban New Email
                        </h3>
                        <div className="flex flex-col md:flex-row gap-4">
                            <div className="flex-1">
                                <input
                                    type="email"
                                    placeholder="email@example.com"
                                    value={banEmailInput}
                                    onChange={e => setBanEmailInput(e.target.value)}
                                    className="w-full px-4 py-3 rounded-xl bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-red-500 outline-none"
                                />
                            </div>
                            <div className="flex-[2]">
                                <input
                                    type="text"
                                    placeholder="Reason for ban..."
                                    value={banReasonInput}
                                    onChange={e => setBanReasonInput(e.target.value)}
                                    className="w-full px-4 py-3 rounded-xl bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-red-500 outline-none"
                                />
                            </div>
                            <button
                                type="submit"
                                disabled={loading || !banEmailInput || !banReasonInput}
                                className="px-6 py-3 bg-red-500 hover:bg-red-600 text-white font-bold rounded-xl shadow-lg shadow-red-500/20 disabled:opacity-50 transition-all"
                            >
                                Ban Email
                            </button>
                        </div>
                    </form>

                    <div className="space-y-3">
                        {bannedEmails.length === 0 ? (
                            <div className="text-center py-12 text-slate-500 font-medium">No banned emails found.</div>
                        ) : (
                            bannedEmails.map((ban: any) => (
                                <div key={ban.id} className="flex items-center justify-between p-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-xl hover:border-red-500/30 transition-all group">
                                    <div className="flex items-center gap-4">
                                        <div className="w-10 h-10 rounded-full bg-red-100 dark:bg-red-900/20 flex items-center justify-center text-red-500">
                                            <Mail className="w-5 h-5" />
                                        </div>
                                        <div>
                                            <p className="font-bold text-slate-900 dark:text-white">{ban.email}</p>
                                            <p className="text-xs text-slate-500">Reason: {ban.reason} • By: {ban.bannedBy}</p>
                                        </div>
                                    </div>
                                    <button
                                        onClick={() => handleUnbanEmail(ban.email)}
                                        className="px-4 py-2 text-sm font-bold text-slate-500 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/10 rounded-lg transition-colors"
                                    >
                                        Unban
                                    </button>
                                </div>
                            ))
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};