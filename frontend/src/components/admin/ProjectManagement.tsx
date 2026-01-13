import React, { useState, useEffect, useRef } from 'react';
import { Package, Search, Trash2, EyeOff, Clock, AlertTriangle, ArrowRight, Hash } from 'lucide-react';
import { api } from '../../utils/api';
import type { Mod } from '../../types';

export const ProjectManagement: React.FC<{ setStatus: (s: any) => void }> = ({ setStatus }) => {
    const [query, setQuery] = useState('');
    const [idQuery, setIdQuery] = useState('');
    const [loading, setLoading] = useState(false);
    const [foundProject, setFoundProject] = useState<Mod | null>(null);
    const [showVersions, setShowVersions] = useState(false);

    const [searchResults, setSearchResults] = useState<Mod[]>([]);
    const [showResults, setShowResults] = useState(false);
    const searchTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
    const wrapperRef = useRef<HTMLDivElement>(null);

    const [confirmAction, setConfirmAction] = useState<'DELETE' | 'UNLIST' | 'DELETE_VER' | null>(null);
    const [targetVersionId, setTargetVersionId] = useState<string | null>(null);
    const [confirmInput, setConfirmInput] = useState('');

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
        setQuery(val);

        if (searchTimeout.current) clearTimeout(searchTimeout.current);

        if (val.trim().length > 1) {
            searchTimeout.current = setTimeout(async () => {
                try {
                    const res = await api.get('/admin/projects/search', { params: { query: val } });
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

    const handleIdLookup = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!idQuery.trim()) return;

        setLoading(true);
        setFoundProject(null);
        try {
            const res = await api.get(`/admin/projects/${idQuery.trim()}`);
            setFoundProject(res.data);
            setQuery(res.data.title);
            setShowResults(false);
        } catch (e) {
            setStatus({ type: 'error', title: 'Not Found', msg: `No project found with ID or Slug: ${idQuery}` });
        } finally {
            setLoading(false);
        }
    };

    const selectProject = (mod: Mod) => {
        setFoundProject(mod);
        setQuery(mod.title);
        setIdQuery(mod.id);
        setShowResults(false);
        setConfirmAction(null);
    };

    const handleAction = async () => {
        if (!foundProject) return;
        setLoading(true);
        try {
            if (confirmAction === 'DELETE') {
                if (confirmInput !== foundProject.id) return;
                await api.delete(`/admin/projects/${foundProject.id}`);
                setStatus({ type: 'success', title: 'Deleted', msg: 'Project permanently deleted.' });
                setFoundProject(null);
                setQuery('');
                setIdQuery('');
            } else if (confirmAction === 'UNLIST') {
                await api.post(`/admin/projects/${foundProject.id}/unlist`);
                setStatus({ type: 'success', title: 'Unlisted', msg: 'Project is now unlisted.' });
                setFoundProject({ ...foundProject, status: 'UNLISTED' });
            } else if (confirmAction === 'DELETE_VER' && targetVersionId) {
                await api.delete(`/admin/projects/${foundProject.id}/versions/${targetVersionId}`);
                setStatus({ type: 'success', title: 'Version Deleted', msg: 'Version removed successfully.' });
                const newVersions = foundProject.versions.filter(v => v.id !== targetVersionId);
                setFoundProject({ ...foundProject, versions: newVersions });
            }
            setConfirmAction(null);
            setConfirmInput('');
            setTargetVersionId(null);
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Action Failed', msg: e.response?.data || 'An error occurred.' });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-[2rem] p-10 shadow-2xl shadow-black/5">
            <div className="flex flex-col md:flex-row gap-4 mb-10 relative z-50">
                <div className="relative flex-1 group" ref={wrapperRef}>
                    <Package className="absolute left-5 top-4 w-5 h-5 text-slate-400 group-focus-within:text-modtale-accent transition-colors" />
                    <input
                        type="text"
                        placeholder="Search projects by title..."
                        className="w-full pl-14 px-6 py-4 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-2xl focus:ring-2 focus:ring-modtale-accent outline-none dark:text-white font-bold transition-all placeholder:font-medium"
                        value={query}
                        onChange={handleInputChange}
                        onFocus={() => { if(searchResults.length > 0) setShowResults(true); }}
                    />

                    {showResults && searchResults.length > 0 && (
                        <div className="absolute top-full left-0 right-0 mt-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-2xl shadow-xl overflow-hidden max-h-60 overflow-y-auto custom-scrollbar animate-in fade-in slide-in-from-top-2 duration-200">
                            {searchResults.map(mod => (
                                <button
                                    key={mod.id}
                                    onClick={() => selectProject(mod)}
                                    className="w-full text-left px-5 py-3 hover:bg-slate-50 dark:hover:bg-white/5 flex items-center gap-3 transition-colors border-b border-slate-100 dark:border-white/5 last:border-0"
                                >
                                    <div className="w-8 h-8 rounded-lg bg-slate-200 dark:bg-white/10 overflow-hidden shrink-0">
                                        <img src={mod.imageUrl} alt="" className="w-full h-full object-cover" />
                                    </div>
                                    <div className="min-w-0">
                                        <p className="text-sm font-bold text-slate-900 dark:text-white truncate">{mod.title}</p>
                                        <p className="text-[10px] text-slate-500 font-mono">{mod.id}</p>
                                    </div>
                                </button>
                            ))}
                        </div>
                    )}
                </div>

                <form onSubmit={handleIdLookup} className="relative flex-1 md:flex-none md:w-80 group">
                    <Hash className="absolute left-5 top-4 w-5 h-5 text-slate-400 group-focus-within:text-modtale-accent transition-colors" />
                    <input
                        type="text"
                        placeholder="Lookup exact ID/Slug..."
                        className="w-full pl-14 pr-14 px-6 py-4 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-2xl focus:ring-2 focus:ring-modtale-accent outline-none dark:text-white font-bold transition-all placeholder:font-medium font-mono"
                        value={idQuery}
                        onChange={(e) => setIdQuery(e.target.value)}
                    />
                    <button
                        type="submit"
                        disabled={loading || !idQuery.trim()}
                        className="absolute right-2 top-2 bottom-2 aspect-square flex items-center justify-center bg-slate-200 dark:bg-white/10 hover:bg-modtale-accent hover:text-white text-slate-500 rounded-xl transition-all disabled:opacity-50 disabled:hover:bg-slate-200 dark:disabled:hover:bg-white/10"
                    >
                        <ArrowRight className="w-5 h-5" />
                    </button>
                </form>
            </div>

            {foundProject && (
                <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                    <div className="border border-slate-200 dark:border-white/10 rounded-3xl p-8 bg-slate-50/50 dark:bg-white/[0.02] mb-8">
                        <div className="flex items-center gap-6 mb-6">
                            <img src={foundProject.imageUrl} className="w-20 h-20 rounded-2xl shadow-lg object-cover" alt="" />
                            <div>
                                <h3 className="text-2xl font-black text-slate-900 dark:text-white">{foundProject.title}</h3>
                                <p className="text-sm font-bold text-slate-500">by {foundProject.author} • <span className="font-mono">{foundProject.id}</span></p>
                                <span className={`inline-block mt-2 px-2 py-0.5 rounded text-[10px] font-black uppercase tracking-wider ${
                                    foundProject.status === 'PUBLISHED' ? 'bg-emerald-500/10 text-emerald-500' :
                                        foundProject.status === 'UNLISTED' ? 'bg-amber-500/10 text-amber-500' :
                                            'bg-slate-500/10 text-slate-500'
                                }`}>
                                    {foundProject.status}
                                </span>
                            </div>
                        </div>

                        <div className="flex gap-4">
                            <button
                                onClick={() => setConfirmAction('DELETE')}
                                className="flex-1 py-3 border-2 border-red-500/20 hover:border-red-500 bg-red-500/5 hover:bg-red-500/10 text-red-500 rounded-xl font-bold flex items-center justify-center gap-2 transition-all"
                            >
                                <Trash2 className="w-4 h-4" /> Delete Project
                            </button>
                            {foundProject.status !== 'UNLISTED' && (
                                <button
                                    onClick={() => setConfirmAction('UNLIST')}
                                    className="flex-1 py-3 border-2 border-amber-500/20 hover:border-amber-500 bg-amber-500/5 hover:bg-amber-500/10 text-amber-500 rounded-xl font-bold flex items-center justify-center gap-2 transition-all"
                                >
                                    <EyeOff className="w-4 h-4" /> Unlist Project
                                </button>
                            )}
                            <button
                                onClick={() => setShowVersions(!showVersions)}
                                className="flex-1 py-3 border-2 border-slate-200 dark:border-white/10 hover:border-modtale-accent bg-white dark:bg-white/5 text-slate-600 dark:text-slate-300 rounded-xl font-bold transition-all"
                            >
                                {showVersions ? 'Hide Versions' : `Manage Versions (${foundProject.versions.length})`}
                            </button>
                        </div>
                    </div>

                    {showVersions && (
                        <div className="space-y-2 mb-8 animate-in fade-in slide-in-from-top-2">
                            {foundProject.versions.map(ver => (
                                <div key={ver.id} className="flex items-center justify-between p-4 bg-white dark:bg-slate-950 border border-slate-200 dark:border-white/10 rounded-xl">
                                    <div className="flex items-center gap-4">
                                        <div className="p-2 bg-slate-100 dark:bg-white/5 rounded-lg">
                                            <Package className="w-4 h-4 text-slate-400" />
                                        </div>
                                        <div>
                                            <p className="font-bold text-slate-900 dark:text-white">{ver.versionNumber}</p>
                                            <p className="text-xs text-slate-500 flex items-center gap-1">
                                                <Clock className="w-3 h-3" /> {ver.releaseDate}
                                            </p>
                                        </div>
                                    </div>
                                    <button
                                        onClick={() => { setTargetVersionId(ver.id); setConfirmAction('DELETE_VER'); }}
                                        className="p-2 hover:bg-red-500 hover:text-white text-slate-400 rounded-lg transition-colors"
                                        title="Delete Version"
                                    >
                                        <Trash2 className="w-4 h-4" />
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}

                    {confirmAction && (
                        <div className="fixed inset-0 z-[200] bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-200">
                            <div className="bg-white dark:bg-slate-900 w-full max-w-md p-6 rounded-3xl shadow-2xl border border-slate-200 dark:border-white/10">
                                <div className="flex items-center gap-3 mb-4 text-red-500">
                                    <AlertTriangle className="w-6 h-6" />
                                    <h3 className="text-xl font-black">Confirm Action</h3>
                                </div>

                                <p className="text-slate-600 dark:text-slate-300 font-medium mb-6">
                                    {confirmAction === 'DELETE' && "Permanently delete this project? This cannot be undone."}
                                    {confirmAction === 'UNLIST' && "Hide this project from public listings? Direct links will still work."}
                                    {confirmAction === 'DELETE_VER' && "Delete this specific version? It will be removed from the project history."}
                                </p>

                                {confirmAction === 'DELETE' && (
                                    <div className="mb-6">
                                        <label className="text-xs font-bold text-slate-500 uppercase tracking-wider block mb-2">
                                            Type Project ID to confirm: <span className="text-slate-900 dark:text-white select-all">{foundProject.id}</span>
                                        </label>
                                        <input
                                            type="text"
                                            value={confirmInput}
                                            onChange={e => setConfirmInput(e.target.value)}
                                            className="w-full p-3 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl font-mono text-sm"
                                            placeholder={foundProject.id}
                                        />
                                    </div>
                                )}

                                <div className="flex gap-3">
                                    <button
                                        onClick={() => { setConfirmAction(null); setConfirmInput(''); setTargetVersionId(null); }}
                                        className="flex-1 py-3 bg-slate-100 dark:bg-white/5 font-bold rounded-xl text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-white/10 transition-colors"
                                    >Cancel</button>
                                    <button
                                        onClick={handleAction}
                                        disabled={confirmAction === 'DELETE' && confirmInput !== foundProject.id || loading}
                                        className="flex-1 py-3 bg-red-500 hover:bg-red-600 text-white font-bold rounded-xl disabled:opacity-50 transition-colors shadow-lg shadow-red-500/20"
                                    >
                                        {loading ? 'Processing...' : 'Confirm'}
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};