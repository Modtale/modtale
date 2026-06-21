import React, { useState, useEffect, useRef } from 'react';
import { Package, Search, Trash2, EyeOff, Clock, AlertTriangle, ArrowRight, Hash, Terminal, Download, RotateCcw, Code, X, FileJson, Lock } from 'lucide-react';
import { adminClient } from '../api/adminClient';
import { API_BASE_URL, extractApiErrorMessage } from '@/utils/api';
import { isSuperAdminUser } from '../utils/access';
import type { Project, ScanIssue } from '@/types';
import { ModalPortal } from '@/components/ui/ModalPortal';

export function ProjectManagement({ setStatus }: { setStatus: (s: any) => void }) {
    const [query, setQuery] = useState('');
    const [idQuery, setIdQuery] = useState('');
    const [loading, setLoading] = useState(false);
    const [foundProject, setFoundProject] = useState<Project | null>(null);
    const [showVersions, setShowVersions] = useState(false);
    const [searchDeleted, setSearchDeleted] = useState(false);
    const [currentAdmin, setCurrentAdmin] = useState<any>(null);

    const [searchResults, setSearchResults] = useState<Project[]>([]);
    const [showResults, setShowResults] = useState(false);
    const searchTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    const [confirmAction, setConfirmAction] = useState<'DELETE' | 'UNLIST' | 'DELETE_VER' | 'RESTORE' | 'HARD_DELETE' | null>(null);
    const [restoreTargetStatus, setRestoreTargetStatus] = useState<string>('PUBLISHED');
    const [targetVersionId, setTargetVersionId] = useState<string | null>(null);
    const [confirmInput, setConfirmInput] = useState('');
    const [actionReason, setActionReason] = useState('');

    const [showRawModal, setShowRawModal] = useState(false);
    const [rawJsonStr, setRawJsonStr] = useState('');
    const [jsonError, setJsonError] = useState<string | null>(null);

    useEffect(() => {
        adminClient.getCurrentAdmin().then(res => setCurrentAdmin(res)).catch(() => {});
    }, []);

    const isSuperAdmin = isSuperAdminUser(currentAdmin);

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
                    const data = await adminClient.searchProjects({ query: val, deleted: searchDeleted });
                    setSearchResults(data);
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
            const data = await adminClient.getProjectById(idQuery.trim());
            setFoundProject(data);
            setQuery(data.title);
            setShowResults(false);
        } catch (e) {
            setStatus({
                type: 'error',
                title: 'Lookup Failed',
                msg: extractApiErrorMessage(e, `No project found with ID or slug "${idQuery.trim()}".`)
            });
        } finally {
            setLoading(false);
        }
    };

    const selectProject = async (mod: Project) => {
        setFoundProject(mod);
        setQuery(mod.title);
        setIdQuery(mod.id);
        setShowResults(false);
        setConfirmAction(null);
        setActionReason('');

        setLoading(true);
        try {
            const data = await adminClient.getProjectById(mod.id);
            setFoundProject(data);
        } catch (e) {
            setStatus({ type: 'error', title: 'Error', msg: extractApiErrorMessage(e, 'We could not load the full project details.') });
        } finally {
            setLoading(false);
        }
    };

    const openRawEdit = () => {
        setRawJsonStr(JSON.stringify(foundProject, null, 2));
        setJsonError(null);
        setShowRawModal(true);
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
                if (textareaRef.current) textareaRef.current.selectionStart = textareaRef.current.selectionEnd = start + 2;
            }, 0);
        }
    };

    const formatJson = () => {
        try {
            const parsed = JSON.parse(rawJsonStr);
            setRawJsonStr(JSON.stringify(parsed, null, 2));
            setJsonError(null);
        } catch (e) {}
    };

    const saveRawEdit = async () => {
        if (!foundProject) return;
        setLoading(true);
        try {
            const parsed = JSON.parse(rawJsonStr);
            await adminClient.updateProjectRaw(foundProject.id, parsed);
            setStatus({ type: 'success', title: 'Saved', msg: 'Raw project metadata updated successfully.' });
            setShowRawModal(false);
            setFoundProject(parsed);
        } catch (e: any) {
            setStatus({
                type: 'error',
                title: 'Error',
                msg: e instanceof SyntaxError
                    ? 'Invalid JSON format.'
                    : extractApiErrorMessage(e, 'We could not save the raw project data.')
            });
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
                await adminClient.deleteProject(foundProject.id, actionReason);
                setStatus({ type: 'success', title: 'Deleted', msg: 'Project soft-deleted. It will be permanently removed in 30 days.' });
                setFoundProject({ ...foundProject, status: 'DELETED' });
            } else if (confirmAction === 'HARD_DELETE') {
                if (confirmInput !== foundProject.id) return;
                await adminClient.hardDeleteProject(foundProject.id, actionReason);
                setStatus({ type: 'success', title: 'Deleted', msg: 'Project permanently deleted.' });
                setFoundProject(null);
                setQuery('');
                setIdQuery('');
            } else if (confirmAction === 'RESTORE') {
                await adminClient.restoreProject(foundProject.id, restoreTargetStatus);
                setStatus({ type: 'success', title: 'Restored', msg: `Project successfully restored to ${restoreTargetStatus}.` });
                setFoundProject({ ...foundProject, status: restoreTargetStatus as Project['status'] });
            } else if (confirmAction === 'UNLIST') {
                await adminClient.unlistProject(foundProject.id, actionReason);
                setStatus({ type: 'success', title: 'Unlisted', msg: 'Project is now unlisted.' });
                setFoundProject({ ...foundProject, status: 'UNLISTED' });
            } else if (confirmAction === 'DELETE_VER' && targetVersionId) {
                await adminClient.deleteVersion(foundProject.id, targetVersionId);
                setStatus({ type: 'success', title: 'Version Deleted', msg: 'Version removed successfully.' });
                const newVersions = (foundProject.versions || []).filter(v => v.id !== targetVersionId);
                setFoundProject({ ...foundProject, versions: newVersions });
            }
            setConfirmAction(null);
            setConfirmInput('');
            setActionReason('');
            setTargetVersionId(null);
        } catch (e: any) {
            const fallback = confirmAction === 'DELETE'
                ? 'We could not delete this project.'
                : confirmAction === 'HARD_DELETE'
                    ? 'We could not permanently delete this project.'
                    : confirmAction === 'RESTORE'
                        ? 'We could not restore this project.'
                        : confirmAction === 'UNLIST'
                            ? 'We could not unlist this project.'
                            : 'We could not delete this version.';
            setStatus({ type: 'error', title: 'Action Failed', msg: extractApiErrorMessage(e, fallback) });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-3xl p-8 shadow-sm backdrop-blur-md">
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
                            <input type="checkbox" className="hidden" checked={searchDeleted} onChange={e => setSearchDeleted(e.target.checked)} />
                            <span className={`text-xs font-bold ${searchDeleted ? 'text-red-500' : 'text-slate-500 group-hover/toggle:text-slate-700 dark:group-hover/toggle:text-slate-300'}`}>Search Deleted Projects</span>
                        </label>
                    </div>

                    {showResults && searchResults.length > 0 && (
                        <div className="absolute top-full left-0 right-0 mt-2 bg-white/95 dark:bg-slate-900/95 border border-slate-200 dark:border-white/10 rounded-2xl shadow-2xl backdrop-blur-xl overflow-hidden max-h-60 overflow-y-auto animate-in fade-in slide-in-from-top-2 duration-200 z-[60]">
                            {searchResults.map(mod => (
                                <button key={mod.id} onClick={() => selectProject(mod)} className="w-full text-left px-5 py-3 hover:bg-slate-50 dark:hover:bg-white/5 flex items-center gap-3 transition-colors border-b border-slate-100 dark:border-white/5 last:border-0">
                                    <div className="w-8 h-8 rounded-lg bg-slate-200 dark:bg-white/10 overflow-hidden shrink-0"><img src={mod.imageUrl || 'https://modtale.net/assets/favicon.svg'} alt="" className="w-full h-full object-cover" /></div>
                                    <div className="min-w-0 flex-1">
                                        <div className="flex items-center justify-between">
                                            <p className="text-sm font-bold text-slate-900 dark:text-white truncate">{mod.title}</p>
                                            {mod.status === 'DELETED' && <span className="text-[10px] bg-red-500/10 text-red-500 px-1.5 py-0.5 rounded font-black uppercase">Deleted</span>}
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
                    <input type="text" placeholder="Lookup exact ID/Slug..." className="w-full pl-14 pr-14 px-6 py-4 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-2xl focus:ring-2 focus:ring-modtale-accent outline-none dark:text-white font-bold transition-all placeholder:font-medium font-mono" value={idQuery} onChange={(e) => setIdQuery(e.target.value)} />
                    <button type="submit" disabled={loading || !idQuery.trim()} className="absolute right-2 top-2 bottom-2 aspect-square flex items-center justify-center bg-slate-200 dark:bg-white/10 hover:bg-modtale-accent hover:text-white text-slate-500 rounded-xl transition-all disabled:opacity-50 disabled:hover:bg-slate-200 dark:disabled:hover:bg-white/10"><ArrowRight className="w-5 h-5" /></button>
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
                            <button onClick={openRawEdit} disabled={!isSuperAdmin} className={`flex-1 py-3 border-2 rounded-xl font-bold flex items-center justify-center gap-2 transition-all min-w-[200px] ${!isSuperAdmin ? 'border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-white/5 text-slate-400 opacity-50 cursor-not-allowed' : 'border-indigo-500/20 hover:border-indigo-500 bg-indigo-500/5 hover:bg-indigo-500/10 text-indigo-500'}`}>
                                <Code className="w-4 h-4" /> Edit Raw JSON {!isSuperAdmin && <Lock className="w-4 h-4" />}
                            </button>

                            {foundProject.status === 'DELETED' ? (
                                <>
                                    <button onClick={() => setConfirmAction('RESTORE')} className="flex-1 py-3 border-2 border-emerald-500/20 hover:border-emerald-500 bg-emerald-500/5 hover:bg-emerald-500/10 text-emerald-500 rounded-xl font-bold flex items-center justify-center gap-2 transition-all min-w-[200px]">
                                        <RotateCcw className="w-4 h-4" /> Restore Project
                                    </button>
                                    <button onClick={() => setConfirmAction('HARD_DELETE')} className="flex-1 py-3 border-2 border-red-500/20 hover:border-red-500 bg-red-500/5 hover:bg-red-500/10 text-red-500 rounded-xl font-bold flex items-center justify-center gap-2 transition-all min-w-[200px]">
                                        <Trash2 className="w-4 h-4" /> Force Hard Delete
                                    </button>
                                </>
                            ) : (
                                <button onClick={() => setConfirmAction('DELETE')} className="flex-1 py-3 border-2 border-red-500/20 hover:border-red-500 bg-red-500/5 hover:bg-red-500/10 text-red-500 rounded-xl font-bold flex items-center justify-center gap-2 transition-all">
                                    <Trash2 className="w-4 h-4" /> Delete Project
                                </button>
                            )}
                            {foundProject.status !== 'UNLISTED' && foundProject.status !== 'DELETED' && (
                                <button onClick={() => setConfirmAction('UNLIST')} className="flex-1 py-3 border-2 border-amber-500/20 hover:border-amber-500 bg-amber-500/5 hover:bg-amber-500/10 text-amber-500 rounded-xl font-bold flex items-center justify-center gap-2 transition-all">
                                    <EyeOff className="w-4 h-4" /> Unlist Project
                                </button>
                            )}
                            <button onClick={() => setShowVersions(!showVersions)} className="flex-1 py-3 border-2 border-slate-200 dark:border-white/10 hover:border-modtale-accent bg-white dark:bg-white/5 text-slate-600 dark:text-slate-300 rounded-xl font-bold transition-all min-w-[200px]">
                                {showVersions ? 'Hide Versions' : `Manage Versions (${foundProject.versions ? foundProject.versions.length : 0})`}
                            </button>
                        </div>
                    </div>

                    {showVersions && foundProject.versions && (
                        <div className="space-y-2 mb-8 animate-in fade-in slide-in-from-top-2">
                            {foundProject.versions.map(ver => (
                                <div key={ver.id} className="flex items-center justify-between p-4 bg-white/50 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl backdrop-blur-sm">
                                    <div className="flex items-center gap-4">
                                        <div className="p-2 bg-slate-100 dark:bg-white/5 rounded-lg"><Package className="w-4 h-4 text-slate-400" /></div>
                                        <div>
                                            <p className="font-bold text-slate-900 dark:text-white">{ver.versionNumber}</p>
                                            <p className="text-xs text-slate-500 flex items-center gap-1"><Clock className="w-3 h-3" /> {ver.releaseDate}</p>
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <a href={`${API_BASE_URL}/projects/${foundProject.id}/versions/${ver.versionNumber}/download`} target="_blank" rel="noreferrer" className="p-2 hover:bg-slate-100 dark:hover:bg-white/10 text-slate-400 rounded-lg transition-colors" title="Download Jar"><Download className="w-4 h-4" /></a>
                                        <button onClick={() => { setTargetVersionId(ver.id); setConfirmAction('DELETE_VER'); }} className="p-2 hover:bg-red-500 hover:text-white text-slate-400 rounded-lg transition-colors" title="Delete Version"><Trash2 className="w-4 h-4" /></button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}

                    {showRawModal && (
                        <ModalPortal>
                        <div className="fixed inset-0 z-[300] bg-black/80 backdrop-blur-md flex items-center justify-center p-4 animate-in fade-in duration-200">
                            <div className="bg-slate-900 w-full max-w-5xl rounded-3xl shadow-2xl border border-white/10 flex flex-col overflow-hidden h-[85vh]">
                                <div className="px-6 py-4 border-b border-white/10 flex justify-between items-center bg-black/40">
                                    <div><h3 className="font-bold text-white flex items-center gap-2 text-lg"><FileJson className="w-5 h-5 text-indigo-400" /> JSON Editor</h3><p className="text-xs text-slate-400 mt-1 font-mono">Editing project: {foundProject.id}</p></div>
                                    <button onClick={() => setShowRawModal(false)} className="p-2 hover:bg-white/10 rounded-full transition-colors text-white/50 hover:text-white"><X className="w-5 h-5" /></button>
                                </div>
                                <div className="flex-1 p-6 overflow-hidden flex flex-col bg-[#0d1117]">
                                    <div className="flex items-center justify-between mb-3">
                                        <div className="flex items-center gap-2 text-xs font-bold bg-amber-500/10 text-amber-400 px-3 py-1.5 rounded-lg border border-amber-500/20"><AlertTriangle className="w-4 h-4" /> Bypasses standard validation.</div>
                                        <button onClick={formatJson} className="px-4 py-1.5 bg-white/5 hover:bg-white/10 text-slate-300 hover:text-white text-xs font-bold rounded-lg border border-white/10 transition-colors">Format JSON</button>
                                    </div>
                                    <div className="relative flex-1 rounded-xl border border-white/10 overflow-hidden bg-black/20 flex flex-col">
                                        <textarea ref={textareaRef} value={rawJsonStr} onChange={handleJsonChange} onKeyDown={handleJsonKeyDown} className={`flex-1 w-full p-4 bg-transparent text-slate-300 font-mono text-sm outline-none resize-none whitespace-pre overflow-auto transition-shadow ${jsonError ? 'shadow-[inset_0_0_0_2px_rgba(239,68,68,0.5)]' : 'focus:shadow-[inset_0_0_0_2px_rgba(99,102,241,0.5)]'}`} spellCheck={false} />
                                        {jsonError && <div className="absolute bottom-0 left-0 right-0 bg-red-500/90 text-white text-xs font-bold px-4 py-2 truncate shadow-lg backdrop-blur-sm">Parse Error: {jsonError}</div>}
                                    </div>
                                </div>
                                <div className="p-4 border-t border-white/10 bg-black/40 flex justify-end gap-3 px-6">
                                    <button onClick={() => setShowRawModal(false)} className="px-6 py-2.5 rounded-xl font-bold text-slate-300 hover:bg-white/5 transition-colors">Cancel</button>
                                    <button onClick={saveRawEdit} disabled={loading || !!jsonError} className="px-8 py-2.5 bg-indigo-500 hover:bg-indigo-600 text-white rounded-xl font-bold transition-colors disabled:opacity-50 shadow-lg shadow-indigo-500/20 flex items-center gap-2">{loading ? 'Saving...' : 'Save Changes'}</button>
                                </div>
                            </div>
                        </div>
                        </ModalPortal>
                    )}

                    {confirmAction && (
                        <ModalPortal>
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
                                        <label className="text-xs font-bold text-slate-500 uppercase tracking-wider block mb-2">Restore To Status</label>
                                        <select value={restoreTargetStatus} onChange={e => setRestoreTargetStatus(e.target.value)} className="themed-select w-full rounded-xl border border-slate-200 bg-slate-50 p-3 font-bold text-slate-900 outline-none transition-all focus:border-modtale-accent focus:ring-2 focus:ring-modtale-accent dark:border-white/10 dark:bg-black/20 dark:text-white">
                                            <option value="PUBLISHED">Published</option>
                                            <option value="DRAFT">Draft</option>
                                            <option value="PRIVATE">Private</option>
                                            <option value="UNLISTED">Unlisted</option>
                                            <option value="ARCHIVED">Archived</option>
                                        </select>
                                    </div>
                                )}
                                {(confirmAction === 'DELETE' || confirmAction === 'HARD_DELETE' || confirmAction === 'UNLIST') && (
                                    <div className="mb-6">
                                        <label className="text-xs font-bold text-slate-500 uppercase tracking-wider block mb-2">Reason for User Notification</label>
                                        <textarea value={actionReason} onChange={e => setActionReason(e.target.value)} placeholder="Explain why this action is being taken..." className="w-full p-3 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl text-sm outline-none resize-y min-h-[80px] font-medium text-slate-900 dark:text-white" />
                                    </div>
                                )}
                                {(confirmAction === 'DELETE' || confirmAction === 'HARD_DELETE') && (
                                    <div className="mb-6">
                                        <label className="text-xs font-bold text-slate-500 uppercase tracking-wider block mb-2">Type Project ID to confirm: <span className="text-slate-900 dark:text-white select-all">{foundProject.id}</span></label>
                                        <input type="text" value={confirmInput} onChange={e => setConfirmInput(e.target.value)} className="w-full p-3 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl font-mono text-sm" placeholder={foundProject.id} />
                                    </div>
                                )}
                                <div className="flex gap-3">
                                    <button onClick={() => { setConfirmAction(null); setConfirmInput(''); setTargetVersionId(null); setRestoreTargetStatus('PUBLISHED'); setActionReason(''); }} className="flex-1 py-3 bg-slate-100 dark:bg-white/5 font-bold rounded-xl text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-white/10 transition-colors">Cancel</button>
                                    <button onClick={handleAction} disabled={((confirmAction === 'DELETE' || confirmAction === 'HARD_DELETE') && confirmInput !== foundProject.id) || loading || ((confirmAction === 'DELETE' || confirmAction === 'HARD_DELETE' || confirmAction === 'UNLIST') && !actionReason)} className={`flex-1 py-3 font-bold rounded-xl disabled:opacity-50 transition-colors shadow-lg ${confirmAction === 'RESTORE' ? 'bg-emerald-500 hover:bg-emerald-600 text-white shadow-emerald-500/20' : 'bg-red-500 hover:bg-red-600 text-white shadow-red-500/20'}`}>{loading ? 'Processing...' : 'Confirm'}</button>
                                </div>
                            </div>
                        </div>
                        </ModalPortal>
                    )}
                </div>
            )}
        </div>
    );
}
