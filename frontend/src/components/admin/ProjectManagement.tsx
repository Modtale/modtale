import React, { useState, useEffect, useRef } from 'react';
import { Package, Search, Trash2, EyeOff, Clock, AlertTriangle, ArrowRight, Hash, Terminal, Download, RotateCcw, Code, X } from 'lucide-react';
import { api, API_BASE_URL } from '../../utils/api';
import type { Mod, ScanIssue } from '../../types';

export const ProjectManagement: React.FC<{ setStatus: (s: any) => void }> = ({ setStatus }) => {
    const [query, setQuery] = useState('');
    const [idQuery, setIdQuery] = useState('');
    const [loading, setLoading] = useState(false);
    const [foundProject, setFoundProject] = useState<Mod | null>(null);
    const [showVersions, setShowVersions] = useState(false);
    const [searchDeleted, setSearchDeleted] = useState(false);

    const [searchResults, setSearchResults] = useState<Mod[]>([]);
    const [showResults, setShowResults] = useState(false);
    const searchTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
    const wrapperRef = useRef<HTMLDivElement>(null);

    const [confirmAction, setConfirmAction] = useState<'DELETE' | 'UNLIST' | 'DELETE_VER' | 'RESTORE' | 'HARD_DELETE' | null>(null);
    const [restoreTargetStatus, setRestoreTargetStatus] = useState<string>('PUBLISHED');
    const [targetVersionId, setTargetVersionId] = useState<string | null>(null);
    const [confirmInput, setConfirmInput] = useState('');

    const [inspectorData, setInspectorData] = useState<{ version: string, structure: string[], issues: ScanIssue[], initialFile?: string, initialLine?: number } | null>(null);
    const [loadingInspector, setLoadingInspector] = useState(false);

    const [showRawModal, setShowRawModal] = useState(false);
    const [rawJsonStr, setRawJsonStr] = useState('');

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) {
                setShowResults(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    useEffect(() => {
        setSearchResults([]);
        setShowResults(false);
        if (query.length > 1) {
            const e = { target: { value: query } } as React.ChangeEvent<HTMLInputElement>;
            handleInputChange(e);
        }
    }, [searchDeleted]);

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const val = e.target.value;
        setQuery(val);

        if (searchTimeout.current) clearTimeout(searchTimeout.current);

        if (val.trim().length > 1) {
            searchTimeout.current = setTimeout(async () => {
                try {
                    const res = await api.get('/admin/projects/search', { params: { query: val, deleted: searchDeleted } });
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

    const openRawEdit = () => {
        setRawJsonStr(JSON.stringify(foundProject, null, 2));
        setShowRawModal(true);
    };

    const saveRawEdit = async () => {
        if (!foundProject) return;
        setLoading(true);
        try {
            const parsed = JSON.parse(rawJsonStr);
            await api.put(`/admin/projects/${foundProject.id}/raw`, parsed);
            setStatus({ type: 'success', title: 'Saved', msg: 'Raw project metadata updated successfully.' });
            setShowRawModal(false);
            setFoundProject(parsed);
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Error', msg: e instanceof SyntaxError ? 'Invalid JSON format.' : (e.response?.data || 'Server error saving raw data.') });
        } finally {
            setLoading(false);
        }
    };

    const handleAction = async () => {
        if (!foundProject) return;
        setLoading(true);
        try {
            if (confirmAction === 'DELETE') {
                if (confirmInput !== foundProject.id) return;
                await api.delete(`/admin/projects/${foundProject.id}`);
                setStatus({ type: 'success', title: 'Deleted', msg: 'Project soft-deleted. It will be permanently removed in 30 days.' });
                setFoundProject({ ...foundProject, status: 'DELETED' });
            } else if (confirmAction === 'HARD_DELETE') {
                if (confirmInput !== foundProject.id) return;
                await api.delete(`/admin/projects/${foundProject.id}/hard`);
                setStatus({ type: 'success', title: 'Deleted', msg: 'Project permanently deleted.' });
                setFoundProject(null);
                setQuery('');
                setIdQuery('');
            } else if (confirmAction === 'RESTORE') {
                await api.post(`/admin/projects/${foundProject.id}/restore`, null, { params: { status: restoreTargetStatus } });
                setStatus({ type: 'success', title: 'Restored', msg: `Project successfully restored to ${restoreTargetStatus}.` });
                setFoundProject({
                    ...foundProject,
                    status: restoreTargetStatus as Mod['status']
                });            } else if (confirmAction === 'UNLIST') {
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

    const openInspector = async (version: string, issues: ScanIssue[] = []) => {
        if (!foundProject) return;
        setLoadingInspector(true);
        try {
            const res = await api.get(`/admin/projects/${foundProject.id}/versions/${version}/structure`);
            setInspectorData({ version, structure: res.data, issues });
        } catch (e) {
            setStatus({ type: 'error', title: 'Error', msg: 'Failed to inspect JAR structure.' });
        } finally {
            setLoadingInspector(false);
        }
    };

    return (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-[2rem] p-10 shadow-2xl shadow-black/5">
            <div className="flex flex-col md:flex-row gap-4 mb-10 relative z-50 items-start">
                <div className="relative flex-1 group w-full" ref={wrapperRef}>
                    <div className="relative">
                        <Package className="absolute left-5 top-4 w-5 h-5 text-slate-400 group-focus-within:text-modtale-accent transition-colors" />
                        <input
                            type="text"
                            placeholder={searchDeleted ? "Search deleted projects..." : "Search active projects..."}
                            className={`w-full pl-14 px-6 py-4 bg-slate-50 dark:bg-black/20 border ${searchDeleted ? 'border-red-500/30 focus:ring-red-500' : 'border-slate-200 dark:border-white/10 focus:ring-modtale-accent'} rounded-2xl focus:ring-2 outline-none dark:text-white font-bold transition-all placeholder:font-medium`}
                            value={query}
                            onChange={handleInputChange}
                            onFocus={() => { if(searchResults.length > 0) setShowResults(true); }}
                        />
                    </div>

                    <div className="mt-2 flex items-center gap-2 px-2">
                        <label className="flex items-center gap-2 cursor-pointer select-none group/toggle">
                            <div className={`w-10 h-6 rounded-full p-1 transition-colors ${searchDeleted ? 'bg-red-500' : 'bg-slate-200 dark:bg-white/10'}`}>
                                <div className={`w-4 h-4 bg-white rounded-full shadow-sm transition-transform ${searchDeleted ? 'translate-x-4' : ''}`} />
                            </div>
                            <input
                                type="checkbox"
                                className="hidden"
                                checked={searchDeleted}
                                onChange={e => setSearchDeleted(e.target.checked)}
                            />
                            <span className={`text-xs font-bold ${searchDeleted ? 'text-red-500' : 'text-slate-500 group-hover/toggle:text-slate-700 dark:group-hover/toggle:text-slate-300'}`}>
                                Search Deleted Projects
                            </span>
                        </label>
                    </div>

                    {showResults && searchResults.length > 0 && (
                        <div className="absolute top-full left-0 right-0 mt-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-2xl shadow-xl overflow-hidden max-h-60 overflow-y-auto custom-scrollbar animate-in fade-in slide-in-from-top-2 duration-200 z-[60]">
                            {searchResults.map(mod => (
                                <button
                                    key={mod.id}
                                    onClick={() => selectProject(mod)}
                                    className="w-full text-left px-5 py-3 hover:bg-slate-50 dark:hover:bg-white/5 flex items-center gap-3 transition-colors border-b border-slate-100 dark:border-white/5 last:border-0"
                                >
                                    <div className="w-8 h-8 rounded-lg bg-slate-200 dark:bg-white/10 overflow-hidden shrink-0">
                                        <img src={mod.imageUrl || 'https://modtale.net/assets/favicon.svg'} alt="" className="w-full h-full object-cover" />
                                    </div>
                                    <div className="min-w-0 flex-1">
                                        <div className="flex items-center justify-between">
                                            <p className="text-sm font-bold text-slate-900 dark:text-white truncate">{mod.title}</p>
                                            {mod.status === 'DELETED' && (
                                                <span className="text-[10px] bg-red-500/10 text-red-500 px-1.5 py-0.5 rounded font-black uppercase">Deleted</span>
                                            )}
                                        </div>
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
                            <img src={foundProject.imageUrl || 'https://modtale.net/assets/favicon.svg'} className="w-20 h-20 rounded-2xl shadow-lg object-cover" alt="" />
                            <div>
                                <h3 className="text-2xl font-black text-slate-900 dark:text-white">{foundProject.title}</h3>
                                <p className="text-sm font-bold text-slate-500">by {foundProject.author} • <span className="font-mono">{foundProject.id}</span></p>
                                <span className={`inline-block mt-2 px-2 py-0.5 rounded text-[10px] font-black uppercase tracking-wider ${
                                    foundProject.status === 'PUBLISHED' ? 'bg-emerald-500/10 text-emerald-500' :
                                        foundProject.status === 'UNLISTED' ? 'bg-amber-500/10 text-amber-500' :
                                            foundProject.status === 'DELETED' ? 'bg-red-500/10 text-red-500' :
                                                'bg-slate-500/10 text-slate-500'
                                }`}>
                                    {foundProject.status}
                                </span>
                            </div>
                        </div>

                        <div className="flex flex-wrap gap-4">
                            <button
                                onClick={openRawEdit}
                                className="flex-1 py-3 border-2 border-indigo-500/20 hover:border-indigo-500 bg-indigo-500/5 hover:bg-indigo-500/10 text-indigo-500 rounded-xl font-bold flex items-center justify-center gap-2 transition-all min-w-[200px]"
                            >
                                <Code className="w-4 h-4" /> Edit Raw JSON
                            </button>

                            {foundProject.status === 'DELETED' ? (
                                <>
                                    <button
                                        onClick={() => setConfirmAction('RESTORE')}
                                        className="flex-1 py-3 border-2 border-emerald-500/20 hover:border-emerald-500 bg-emerald-500/5 hover:bg-emerald-500/10 text-emerald-500 rounded-xl font-bold flex items-center justify-center gap-2 transition-all min-w-[200px]"
                                    >
                                        <RotateCcw className="w-4 h-4" /> Restore Project
                                    </button>
                                    <button
                                        onClick={() => setConfirmAction('HARD_DELETE')}
                                        className="flex-1 py-3 border-2 border-red-500/20 hover:border-red-500 bg-red-500/5 hover:bg-red-500/10 text-red-500 rounded-xl font-bold flex items-center justify-center gap-2 transition-all min-w-[200px]"
                                    >
                                        <Trash2 className="w-4 h-4" /> Force Hard Delete
                                    </button>
                                </>
                            ) : (
                                <button
                                    onClick={() => setConfirmAction('DELETE')}
                                    className="flex-1 py-3 border-2 border-red-500/20 hover:border-red-500 bg-red-500/5 hover:bg-red-500/10 text-red-500 rounded-xl font-bold flex items-center justify-center gap-2 transition-all"
                                >
                                    <Trash2 className="w-4 h-4" /> Delete Project
                                </button>
                            )}
                            {foundProject.status !== 'UNLISTED' && foundProject.status !== 'DELETED' && (
                                <button
                                    onClick={() => setConfirmAction('UNLIST')}
                                    className="flex-1 py-3 border-2 border-amber-500/20 hover:border-amber-500 bg-amber-500/5 hover:bg-amber-500/10 text-amber-500 rounded-xl font-bold flex items-center justify-center gap-2 transition-all"
                                >
                                    <EyeOff className="w-4 h-4" /> Unlist Project
                                </button>
                            )}
                            <button
                                onClick={() => setShowVersions(!showVersions)}
                                className="flex-1 py-3 border-2 border-slate-200 dark:border-white/10 hover:border-modtale-accent bg-white dark:bg-white/5 text-slate-600 dark:text-slate-300 rounded-xl font-bold transition-all min-w-[200px]"
                            >
                                {showVersions ? 'Hide Versions' : `Manage Versions (${foundProject.versions ? foundProject.versions.length : 0})`}
                            </button>
                        </div>
                    </div>

                    {showVersions && foundProject.versions && (
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
                                    <div className="flex items-center gap-2">
                                        <button
                                            onClick={() => openInspector(ver.versionNumber, ver.scanResult?.issues || [])}
                                            disabled={loadingInspector}
                                            className="px-3 py-2 bg-indigo-500/10 hover:bg-indigo-500 hover:text-white text-indigo-500 rounded-lg text-xs font-bold transition-colors border border-indigo-500/20 flex items-center gap-2"
                                        >
                                            <Terminal className="w-3.5 h-3.5" /> Inspect
                                        </button>
                                        <a
                                            href={`${API_BASE_URL}/projects/${foundProject.id}/versions/${ver.versionNumber}/download`}
                                            target="_blank"
                                            rel="noreferrer"
                                            className="p-2 hover:bg-slate-100 dark:hover:bg-white/10 text-slate-400 rounded-lg transition-colors"
                                            title="Download Jar"
                                        >
                                            <Download className="w-4 h-4" />
                                        </a>
                                        <button
                                            onClick={() => { setTargetVersionId(ver.id); setConfirmAction('DELETE_VER'); }}
                                            className="p-2 hover:bg-red-500 hover:text-white text-slate-400 rounded-lg transition-colors"
                                            title="Delete Version"
                                        >
                                            <Trash2 className="w-4 h-4" />
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}

                    {showRawModal && (
                        <div className="fixed inset-0 z-[300] bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-200">
                            <div className="bg-slate-900 w-full max-w-4xl rounded-2xl shadow-2xl border border-white/10 flex flex-col overflow-hidden max-h-[90vh]">
                                <div className="p-4 border-b border-white/10 flex justify-between items-center bg-black/20">
                                    <h3 className="font-bold text-white flex items-center gap-2">
                                        <Code className="w-5 h-5 text-indigo-500" /> Edit Raw Project Metadata
                                    </h3>
                                    <button onClick={() => setShowRawModal(false)} className="p-2 hover:bg-white/10 rounded-full transition-colors text-white/50 hover:text-white">
                                        <X className="w-4 h-4" />
                                    </button>
                                </div>
                                <div className="p-4 flex-1 overflow-hidden flex flex-col">
                                    <div className="mb-2 text-amber-400 text-xs font-bold flex items-center gap-2 bg-amber-500/10 p-2 rounded-lg border border-amber-500/20">
                                        <AlertTriangle className="w-4 h-4" /> Warning: Directly modifying raw JSON bypasses standard validation rules. Malformed data will break the project.
                                    </div>
                                    <textarea
                                        value={rawJsonStr}
                                        onChange={(e) => setRawJsonStr(e.target.value)}
                                        className="flex-1 w-full p-4 bg-[#0d1117] text-slate-300 font-mono text-sm rounded-xl border border-white/10 focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 outline-none resize-none whitespace-pre overflow-auto custom-scrollbar"
                                        spellCheck={false}
                                    />
                                </div>
                                <div className="p-4 border-t border-white/10 bg-black/20 flex justify-end gap-3">
                                    <button onClick={() => setShowRawModal(false)} className="px-6 py-2 rounded-lg font-bold text-slate-300 hover:bg-white/5 transition-colors">Cancel</button>
                                    <button onClick={saveRawEdit} disabled={loading} className="px-6 py-2 bg-indigo-500 hover:bg-indigo-600 text-white rounded-lg font-bold transition-colors disabled:opacity-50">
                                        {loading ? 'Saving...' : 'Save JSON'}
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}

                    {confirmAction && (
                        <div className="fixed inset-0 z-[200] bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-200">
                            <div className="bg-white dark:bg-slate-900 w-full max-w-md p-6 rounded-3xl shadow-2xl border border-slate-200 dark:border-white/10">
                                <div className={`flex items-center gap-3 mb-4 ${confirmAction === 'RESTORE' ? 'text-emerald-500' : 'text-red-500'}`}>
                                    {confirmAction === 'RESTORE' ? <RotateCcw className="w-6 h-6" /> : <AlertTriangle className="w-6 h-6" />}
                                    <h3 className="text-xl font-black">{confirmAction === 'RESTORE' ? 'Confirm Restoration' : 'Confirm Action'}</h3>
                                </div>

                                <p className="text-slate-600 dark:text-slate-300 font-medium mb-6">
                                    {confirmAction === 'DELETE' && "Permanently delete this project? It will be recoverable for 30 days."}
                                    {confirmAction === 'UNLIST' && "Hide this project from public listings? Direct links will still work."}
                                    {confirmAction === 'DELETE_VER' && "Delete this specific version? It will be removed from the project history."}
                                    {confirmAction === 'RESTORE' && "Restore this project to an active state? Please select the target status."}
                                    {confirmAction === 'HARD_DELETE' && "WARNING: This action cannot be undone. All project data, images, versions, and analytics will be permanently destroyed immediately."}
                                </p>

                                {confirmAction === 'RESTORE' && (
                                    <div className="mb-6">
                                        <label className="text-xs font-bold text-slate-500 uppercase tracking-wider block mb-2">
                                            Restore To Status
                                        </label>
                                        <select
                                            value={restoreTargetStatus}
                                            onChange={e => setRestoreTargetStatus(e.target.value)}
                                            className="w-full p-3 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl font-bold outline-none"
                                        >
                                            <option value="PUBLISHED">Published</option>
                                            <option value="DRAFT">Draft</option>
                                            <option value="UNLISTED">Unlisted</option>
                                            <option value="ARCHIVED">Archived</option>
                                        </select>
                                    </div>
                                )}

                                {(confirmAction === 'DELETE' || confirmAction === 'HARD_DELETE') && (
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
                                        onClick={() => { setConfirmAction(null); setConfirmInput(''); setTargetVersionId(null); setRestoreTargetStatus('PUBLISHED'); }}
                                        className="flex-1 py-3 bg-slate-100 dark:bg-white/5 font-bold rounded-xl text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-white/10 transition-colors"
                                    >Cancel</button>
                                    <button
                                        onClick={handleAction}
                                        disabled={((confirmAction === 'DELETE' || confirmAction === 'HARD_DELETE') && confirmInput !== foundProject.id) || loading}
                                        className={`flex-1 py-3 font-bold rounded-xl disabled:opacity-50 transition-colors shadow-lg ${
                                            confirmAction === 'RESTORE'
                                                ? 'bg-emerald-500 hover:bg-emerald-600 text-white shadow-emerald-500/20'
                                                : 'bg-red-500 hover:bg-red-600 text-white shadow-red-500/20'
                                        }`}
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