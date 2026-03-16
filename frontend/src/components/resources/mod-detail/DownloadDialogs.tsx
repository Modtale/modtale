import React, { useState, useEffect, useRef, useMemo } from 'react';
import type { ProjectVersion } from '../../../types';
import { Download, X, ChevronDown, ChevronUp, Link as LinkIcon, List, AlertCircle, FileText, ChevronRight, Check, Copy, Box } from 'lucide-react';
import { formatTimeAgo, compareSemVer } from '../../../utils/modHelpers';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import { api, API_BASE_URL } from '../../../utils/api';
import { Link } from 'react-router-dom';

let scrollLockCount = 0;

const useScrollLock = (lock: boolean) => {
    useEffect(() => {
        if (lock) {
            scrollLockCount++;
            document.body.style.overflow = 'hidden';
        }
        return () => {
            if (lock) {
                scrollLockCount--;
                if (scrollLockCount <= 0) {
                    scrollLockCount = 0;
                    document.body.style.overflow = '';
                }
            }
        };
    }, [lock]);
};

interface DependencyModalProps {
    dependencies: NonNullable<ProjectVersion['dependencies']>;
    onClose: () => void;
    onConfirm: () => void;
}

interface MetaCache {
    [key: string]: { title: string; author: string; icon: string };
}

export const PostDownloadModal: React.FC<{ isOpen: boolean; onClose: () => void; classification: string; title: string; channel?: string }> = ({ isOpen, onClose, classification, title, channel = 'RELEASE' }) => {
    useScrollLock(isOpen);
    const [os, setOs] = useState<'windows' | 'macos' | 'linux'>('windows');
    const [copied, setCopied] = useState(false);
    const [dontShow, setDontShow] = useState(false);

    useEffect(() => {
        if (isOpen) {
            const userAgent = window.navigator.userAgent.toLowerCase();
            if (userAgent.includes('mac')) setOs('macos');
            else if (userAgent.includes('linux')) setOs('linux');
            else setOs('windows');
        }
    }, [isOpen]);

    if (!isOpen) return null;

    const isWorld = classification === 'SAVE';
    const isModpack = classification === 'MODPACK';
    const folderName = isWorld ? 'Saves' : 'Mods';
    const typeName = isWorld ? 'World' : isModpack ? 'Modpack' : 'Mod';

    const paths = {
        windows: `C:\\Program Files\\Hypixel Studios\\Hytale Launcher\\UserData\\${folderName}`,
        macos: `/Applications/Hytale Launcher.app/Contents/MacOS/UserData/${folderName}`,
        linux: `~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/UserData/${folderName}`
    };

    const handleCopy = () => {
        navigator.clipboard.writeText(paths[os]);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    const handleClose = () => {
        if (dontShow) {
            localStorage.setItem('hideInstallInstructions', 'true');
        }
        onClose();
    };

    const theme = channel === 'ALPHA' ? {
        text: 'text-red-600 dark:text-red-400',
        bg: 'bg-red-600',
        bgAlpha: 'bg-red-500/10 dark:bg-red-500/20',
        border: 'border-red-500/20',
        iconGlow: 'bg-red-50 dark:bg-red-500/10 border-red-200 dark:border-red-500/20',
        checkHover: 'group-hover:border-red-500/30 dark:group-hover:border-red-500/30'
    } : channel === 'BETA' ? {
        text: 'text-purple-600 dark:text-purple-400',
        bg: 'bg-purple-600',
        bgAlpha: 'bg-purple-500/10 dark:bg-purple-500/20',
        border: 'border-purple-500/20',
        iconGlow: 'bg-purple-50 dark:bg-purple-500/10 border-purple-200 dark:border-purple-500/20',
        checkHover: 'group-hover:border-purple-500/30 dark:group-hover:border-purple-500/30'
    } : {
        text: 'text-modtale-accent',
        bg: 'bg-modtale-accent',
        bgAlpha: 'bg-blue-500/10 dark:bg-modtale-accent/20',
        border: 'border-blue-500/20 dark:border-modtale-accent/20',
        iconGlow: 'bg-emerald-50 dark:bg-emerald-500/10 border-emerald-200 dark:border-emerald-500/20 text-emerald-600 dark:text-emerald-500',
        checkHover: 'group-hover:border-slate-300 dark:group-hover:border-white/30'
    };

    return (
        <div className="fixed inset-0 z-[400] flex items-center justify-center bg-slate-900/50 dark:bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200" onClick={handleClose}>
            <div className="bg-white/95 dark:bg-slate-950/95 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl max-w-xl w-full shadow-2xl overflow-hidden flex flex-col ring-1 ring-black/5 dark:ring-black/50" onClick={e => e.stopPropagation()}>

                <div className="p-6 border-b border-slate-200 dark:border-white/5 flex justify-between items-center bg-slate-50/50 dark:bg-white/[0.02] shrink-0">
                    <div className="flex items-center gap-4">
                        <div className={`w-12 h-12 rounded-2xl border flex items-center justify-center shadow-inner ${theme.iconGlow}`}>
                            <Download className={`w-6 h-6 ${channel === 'RELEASE' ? 'text-emerald-500' : theme.text}`} />
                        </div>
                        <div>
                            <h3 className="text-xl font-black text-slate-900 dark:text-white tracking-tight">Download Started</h3>
                            <p className="text-xs text-slate-500 dark:text-slate-400 font-medium mt-1">Installation instructions for {title}</p>
                        </div>
                    </div>
                    <button onClick={handleClose} className="p-2 bg-slate-100 hover:bg-slate-200 dark:bg-white/5 dark:hover:bg-white/10 rounded-full transition-colors text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white"><X className="w-5 h-5" /></button>
                </div>

                <div className="bg-slate-50/30 dark:bg-slate-900/30 overflow-y-auto custom-scrollbar">

                    {channel === 'ALPHA' && (
                        <div className="mx-6 mt-6 p-4 rounded-xl bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 flex gap-3 text-red-700 dark:text-red-400">
                            <AlertCircle className="w-5 h-5 shrink-0" />
                            <div>
                                <p className="font-bold text-sm text-red-800 dark:text-red-300">Alpha Version</p>
                                <p className="text-xs text-red-600 dark:text-red-200 mt-0.5 leading-relaxed">This is an early testing version. It may contain severe bugs, lack features, or cause data corruption. Use with caution.</p>
                            </div>
                        </div>
                    )}

                    {channel === 'BETA' && (
                        <div className="mx-6 mt-6 p-4 rounded-xl bg-purple-50 dark:bg-purple-500/10 border border-purple-200 dark:border-purple-500/20 flex gap-3 text-purple-700 dark:text-purple-400">
                            <AlertCircle className="w-5 h-5 shrink-0" />
                            <div>
                                <p className="font-bold text-sm text-purple-800 dark:text-purple-300">Beta Version</p>
                                <p className="text-xs text-purple-600 dark:text-purple-200 mt-0.5 leading-relaxed">This version is in testing and may contain bugs. Please report any issues you find to the developer.</p>
                            </div>
                        </div>
                    )}

                    <div className="p-6">
                        <div className="flex bg-slate-200/50 dark:bg-black/40 rounded-xl p-1.5 border border-slate-200 dark:border-white/5 mb-6">
                            {(['windows', 'macos', 'linux'] as const).map(platform => (
                                <button
                                    key={platform}
                                    onClick={() => setOs(platform)}
                                    className={`flex-1 py-2 text-xs font-bold rounded-lg capitalize transition-all ${os === platform ? `${theme.bg} text-white shadow-md` : 'text-slate-600 dark:text-slate-500 hover:text-slate-900 dark:hover:text-slate-300 hover:bg-white dark:hover:bg-white/5'}`}
                                >
                                    {platform}
                                </button>
                            ))}
                        </div>

                        <div className="space-y-3 text-sm text-slate-700 dark:text-slate-300 font-medium">
                            {isWorld && (
                                <>
                                    <div className="flex gap-4 p-4 rounded-2xl bg-white dark:bg-white/[0.02] border border-slate-200 dark:border-white/5 shadow-sm">
                                        <div className={`w-8 h-8 rounded-full ${theme.bgAlpha} ${theme.text} font-black flex items-center justify-center shrink-0 shadow-inner`}>1</div>
                                        <div className="pt-1.5">
                                            Locate the downloaded zip file in your <code className={`bg-slate-100 dark:bg-black/50 border border-slate-200 dark:border-white/10 px-1.5 py-0.5 rounded-md font-mono text-xs ${theme.text} shadow-inner`}>Downloads</code> folder.
                                        </div>
                                    </div>
                                    <div className="flex gap-4 p-4 rounded-2xl bg-white dark:bg-white/[0.02] border border-slate-200 dark:border-white/5 shadow-sm">
                                        <div className={`w-8 h-8 rounded-full ${theme.bgAlpha} ${theme.text} font-black flex items-center justify-center shrink-0 shadow-inner`}>2</div>
                                        <div className="w-full min-w-0 pt-1.5">
                                            <p className="mb-3">Extract the world folder and move it into your Hytale Saves directory:</p>
                                            <div className="flex items-center gap-3 bg-slate-50 dark:bg-black/50 border border-slate-200 dark:border-white/10 rounded-xl p-2 pl-3">
                                                <code className="flex-1 font-mono text-[11px] text-slate-600 dark:text-slate-400 break-all select-all leading-relaxed">{paths[os]}</code>
                                                <button onClick={handleCopy} className="p-2 rounded-lg bg-white dark:bg-white/5 hover:bg-slate-100 dark:hover:bg-white/10 border border-slate-200 dark:border-transparent text-slate-500 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white transition-colors shadow-sm shrink-0 self-start" title="Copy Path">
                                                    {copied ? <Check className="w-4 h-4 text-emerald-500" /> : <Copy className="w-4 h-4" />}
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="flex gap-4 p-4 rounded-2xl bg-white dark:bg-white/[0.02] border border-slate-200 dark:border-white/5 shadow-sm">
                                        <div className={`w-8 h-8 rounded-full ${theme.bgAlpha} ${theme.text} font-black flex items-center justify-center shrink-0 shadow-inner`}>3</div>
                                        <div className="pt-1.5">
                                            Launch Hytale and select the world from the Singleplayer menu.
                                        </div>
                                    </div>
                                </>
                            )}

                            {isModpack && (
                                <>
                                    <div className="flex gap-4 p-4 rounded-2xl bg-white dark:bg-white/[0.02] border border-slate-200 dark:border-white/5 shadow-sm">
                                        <div className={`w-8 h-8 rounded-full ${theme.bgAlpha} ${theme.text} font-black flex items-center justify-center shrink-0 shadow-inner`}>1</div>
                                        <div className="pt-1.5">
                                            Locate the downloaded modpack in your <code className={`bg-slate-100 dark:bg-black/50 border border-slate-200 dark:border-white/10 px-1.5 py-0.5 rounded-md font-mono text-xs ${theme.text} shadow-inner`}>Downloads</code> folder.
                                        </div>
                                    </div>
                                    <div className="flex gap-4 p-4 rounded-2xl bg-white dark:bg-white/[0.02] border border-slate-200 dark:border-white/5 shadow-sm">
                                        <div className={`w-8 h-8 rounded-full ${theme.bgAlpha} ${theme.text} font-black flex items-center justify-center shrink-0 shadow-inner`}>2</div>
                                        <div className="w-full min-w-0 pt-1.5">
                                            <p className="mb-3">Unzip the downloaded file and place ALL contents into your Hytale Mods directory:</p>
                                            <div className="flex items-center gap-3 bg-slate-50 dark:bg-black/50 border border-slate-200 dark:border-white/10 rounded-xl p-2 pl-3">
                                                <code className="flex-1 font-mono text-[11px] text-slate-600 dark:text-slate-400 break-all select-all leading-relaxed">{paths[os]}</code>
                                                <button onClick={handleCopy} className="p-2 rounded-lg bg-white dark:bg-white/5 hover:bg-slate-100 dark:hover:bg-white/10 border border-slate-200 dark:border-transparent text-slate-500 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white transition-colors shadow-sm shrink-0 self-start" title="Copy Path">
                                                    {copied ? <Check className="w-4 h-4 text-emerald-500" /> : <Copy className="w-4 h-4" />}
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="flex gap-4 p-4 rounded-2xl bg-white dark:bg-white/[0.02] border border-slate-200 dark:border-white/5 shadow-sm">
                                        <div className={`w-8 h-8 rounded-full ${theme.bgAlpha} ${theme.text} font-black flex items-center justify-center shrink-0 shadow-inner`}>3</div>
                                        <div className="pt-1.5">
                                            Restart your Hytale Launcher. The modpack will be loaded automatically.
                                        </div>
                                    </div>
                                </>
                            )}

                            {!isWorld && !isModpack && (
                                <>
                                    <div className="flex gap-4 p-4 rounded-2xl bg-white dark:bg-white/[0.02] border border-slate-200 dark:border-white/5 shadow-sm">
                                        <div className={`w-8 h-8 rounded-full ${theme.bgAlpha} ${theme.text} font-black flex items-center justify-center shrink-0 shadow-inner`}>1</div>
                                        <div className="pt-1.5">
                                            Locate the downloaded file in your <code className={`bg-slate-100 dark:bg-black/50 border border-slate-200 dark:border-white/10 px-1.5 py-0.5 rounded-md font-mono text-xs ${theme.text} shadow-inner`}>Downloads</code> folder.
                                        </div>
                                    </div>
                                    <div className="flex gap-4 p-4 rounded-2xl bg-white dark:bg-white/[0.02] border border-slate-200 dark:border-white/5 shadow-sm">
                                        <div className={`w-8 h-8 rounded-full ${theme.bgAlpha} ${theme.text} font-black flex items-center justify-center shrink-0 shadow-inner`}>2</div>
                                        <div className="w-full min-w-0 pt-1.5">
                                            <p className="mb-3">Move the file to your Hytale Mods directory:</p>
                                            <div className="flex items-center gap-3 bg-slate-50 dark:bg-black/50 border border-slate-200 dark:border-white/10 rounded-xl p-2 pl-3">
                                                <code className="flex-1 font-mono text-[11px] text-slate-600 dark:text-slate-400 break-all select-all leading-relaxed">{paths[os]}</code>
                                                <button onClick={handleCopy} className="p-2 rounded-lg bg-white dark:bg-white/5 hover:bg-slate-100 dark:hover:bg-white/10 border border-slate-200 dark:border-transparent text-slate-500 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white transition-colors shadow-sm shrink-0 self-start" title="Copy Path">
                                                    {copied ? <Check className="w-4 h-4 text-emerald-500" /> : <Copy className="w-4 h-4" />}
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="flex gap-4 p-4 rounded-2xl bg-white dark:bg-white/[0.02] border border-slate-200 dark:border-white/5 shadow-sm">
                                        <div className={`w-8 h-8 rounded-full ${theme.bgAlpha} ${theme.text} font-black flex items-center justify-center shrink-0 shadow-inner`}>3</div>
                                        <div className="pt-1.5">
                                            Restart your Hytale Launcher to load the new mod.
                                        </div>
                                    </div>
                                </>
                            )}
                        </div>
                    </div>
                </div>

                <div className="p-5 border-t border-slate-200 dark:border-white/5 bg-slate-50/50 dark:bg-white/[0.02] flex justify-between items-center shrink-0">
                    <button
                        onClick={() => setDontShow(!dontShow)}
                        className="flex items-center gap-2.5 cursor-pointer group"
                    >
                        <div className={`w-5 h-5 rounded-md border flex items-center justify-center transition-colors shadow-inner ${dontShow ? `${theme.bg} ${theme.border}` : `bg-slate-100 dark:bg-black/30 border-slate-300 dark:border-white/10 ${theme.checkHover}`}`}>
                            {dontShow && <Check className="w-3.5 h-3.5 text-white" strokeWidth={3} />}
                        </div>
                        <span className="text-xs font-bold text-slate-600 dark:text-slate-400 group-hover:text-slate-900 dark:group-hover:text-slate-300 transition-colors select-none uppercase tracking-wider">Don't show again</span>
                    </button>
                    <button
                        onClick={handleClose}
                        className="px-8 py-2.5 rounded-xl font-black bg-slate-900 text-white dark:bg-white dark:text-slate-900 hover:bg-slate-800 dark:hover:bg-slate-200 transition-colors shadow-lg active:scale-95 text-sm"
                    >
                        Got it
                    </button>
                </div>
            </div>
        </div>
    );
};

export const DependencyModal: React.FC<DependencyModalProps> = ({ dependencies, onClose, onConfirm }) => {
    useScrollLock(true);
    const [selected, setSelected] = useState<Set<string>>(new Set(dependencies.map(d => d.modId)));
    const [metaCache, setMetaCache] = useState<MetaCache>({});

    useEffect(() => {
        const fetchMeta = async () => {
            const missingIds = dependencies.filter(d => !metaCache[d.modId]).map(d => d.modId);
            if (missingIds.length === 0) return;

            const newCache = { ...metaCache };
            await Promise.all(missingIds.map(async (id) => {
                try {
                    const res = await api.get(`/projects/${id}/meta`);
                    newCache[id] = {
                        title: res.data.title,
                        author: res.data.author,
                        icon: res.data.icon
                    };
                } catch (e) {
                    newCache[id] = { title: id, author: 'Unknown', icon: '' };
                }
            }));
            setMetaCache(newCache);
        };
        fetchMeta();
    }, [dependencies]);

    const missingRequired = dependencies.filter(d => !d.isOptional && !selected.has(d.modId)).length > 0;

    const toggleDep = (id: string) => {
        const next = new Set(selected);
        if (next.has(id)) next.delete(id); else next.add(id);
        setSelected(next);
    };

    const toggleAll = () => {
        if (selected.size === dependencies.length) {
            setSelected(new Set());
        } else {
            setSelected(new Set(dependencies.map(d => d.modId)));
        }
    };

    const handleDownload = async () => {
        for (let index = 0; index < dependencies.length; index++) {
            const d = dependencies[index];
            if (selected.has(d.modId)) {
                try {
                    await new Promise(resolve => setTimeout(resolve, index * 250));

                    const response = await api.get(`/projects/${d.modId}/versions/${d.versionNumber}/download-url`);
                    const { downloadUrl } = response.data;

                    const link = document.createElement('a');
                    link.href = `${API_BASE_URL}${downloadUrl}`;
                    link.setAttribute('download', '');
                    document.body.appendChild(link);
                    link.click();
                    document.body.removeChild(link);
                } catch (error) {
                    console.error(`Failed to download dependency ${d.modId}:`, error);
                }
            }
        }
        onConfirm();
    };

    const getIconUrl = (path?: string) => {
        if (!path) return '/assets/favicon.svg';
        return path.startsWith('http') ? path : `${API_BASE_URL}${path.startsWith('/') ? '' : '/'}${path}`;
    };

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-slate-900/50 dark:bg-black/80 backdrop-blur-md p-4 animate-in fade-in">
            <div className="bg-white/95 dark:bg-slate-900/95 backdrop-blur-xl border border-slate-200 dark:border-white/10 rounded-2xl max-w-lg w-full shadow-2xl overflow-hidden relative flex flex-col max-h-[85dvh]">
                <div className="p-6 border-b border-slate-200 dark:border-white/10 flex justify-between items-center bg-slate-50/50 dark:bg-black/20 shrink-0">
                    <h3 className="text-xl font-black text-slate-900 dark:text-white flex items-center gap-2">
                        <LinkIcon className="w-5 h-5 text-emerald-500" /> Dependencies
                    </h3>
                    <button onClick={onClose}><X className="w-6 h-6 text-slate-400 hover:text-red-500" /></button>
                </div>

                <div className="p-6 space-y-4 overflow-y-auto bg-slate-50/50 dark:bg-slate-900 custom-scrollbar">
                    <div className="flex items-center justify-between">
                        <div className="text-sm text-slate-600 dark:text-slate-400 font-medium">
                            Select dependencies to download automatically.
                        </div>
                        <button onClick={toggleAll} className="text-xs font-bold text-modtale-accent hover:underline">
                            {selected.size === dependencies.length ? 'Deselect All' : 'Select All'}
                        </button>
                    </div>

                    <div className="space-y-2">
                        {dependencies.map(dep => {
                            const meta = metaCache[dep.modId];
                            const isSelected = selected.has(dep.modId);

                            return (
                                <div
                                    key={dep.modId}
                                    className={`flex items-center justify-between p-4 rounded-2xl border shadow-sm transition-all cursor-pointer group ${
                                        isSelected
                                            ? 'border-emerald-500/20 bg-emerald-50/50 dark:bg-emerald-500/10 hover:bg-emerald-50 dark:hover:bg-emerald-500/20'
                                            : 'border-slate-200 dark:border-white/10 bg-white dark:bg-slate-800 hover:border-blue-400 dark:hover:border-blue-500'
                                    }`}
                                    onClick={() => toggleDep(dep.modId)}
                                >
                                    <div className="flex items-center gap-4 min-w-0">
                                        {isSelected ? (
                                            <div className="w-6 h-6 rounded-full bg-emerald-500 text-white flex items-center justify-center shrink-0 shadow-md">
                                                <Check className="w-3.5 h-3.5" aria-hidden="true" />
                                            </div>
                                        ) : (
                                            <div className="w-6 h-6 rounded-full border-2 border-slate-300 dark:border-slate-600 bg-white/50 dark:bg-slate-800/50 flex items-center justify-center shrink-0 shadow-sm transition-colors group-hover:border-blue-400" />
                                        )}

                                        <div className="w-12 h-12 rounded-xl bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 flex items-center justify-center shrink-0 overflow-hidden shadow-sm p-1">
                                            <img
                                                src={getIconUrl(meta?.icon)}
                                                alt=""
                                                className="w-full h-full object-cover rounded-lg"
                                                onError={(e) => e.currentTarget.src='/assets/favicon.svg'}
                                            />
                                        </div>

                                        <div className="min-w-0">
                                            <div className="font-bold text-slate-900 dark:text-white truncate">
                                                {meta?.title || dep.modTitle || dep.modId}
                                            </div>
                                            <div className="text-xs text-slate-500 dark:text-slate-400 font-mono mt-1 flex items-center gap-1.5">
                                                <span className="truncate max-w-[100px]">by {meta?.author || '...'}</span>
                                                <span className="w-1 h-1 rounded-full bg-slate-300 dark:bg-slate-600"></span>
                                                <span className="font-mono opacity-80">v{dep.versionNumber}</span>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="flex-shrink-0 ml-4">
                                        {!dep.isOptional ? (
                                            <span className="text-[10px] font-bold uppercase bg-emerald-100 dark:bg-emerald-500/20 text-emerald-700 dark:text-emerald-300 px-2.5 py-1 rounded-md border border-emerald-200 dark:border-emerald-500/30">Required</span>
                                        ) : (
                                            <span className="text-[10px] font-bold uppercase bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 px-2.5 py-1 rounded-md border border-slate-200 dark:border-white/10">Optional</span>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    {missingRequired && (
                        <div className="flex items-start gap-2 text-xs text-amber-700 dark:text-amber-400 bg-amber-50 dark:bg-amber-500/10 p-3 rounded-xl border border-amber-200 dark:border-amber-500/20 shadow-sm">
                            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                            <p>Some <span className="font-bold">Required</span> dependencies are unchecked.</p>
                        </div>
                    )}
                </div>

                <div className="p-6 border-t border-slate-200 dark:border-white/10 bg-slate-50/80 dark:bg-black/20 flex justify-end gap-3 shrink-0">
                    <button onClick={onClose} className="px-5 py-2.5 font-bold text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-white/10 rounded-xl transition-colors text-sm">
                        Cancel
                    </button>
                    <Link
                        to="#"
                        onClick={(e) => { e.preventDefault(); handleDownload(); }}
                        className="px-6 py-2.5 font-bold rounded-xl shadow-lg shadow-modtale-accent/20 transition-colors flex items-center gap-2 bg-modtale-accent hover:bg-blue-500 text-white text-sm"
                    >
                        <Download className="w-4 h-4" />
                        {selected.size > 0 ? `Download (${selected.size})` : "Continue without Downloading"}
                    </Link>
                </div>
            </div>
        </div>
    );
};

const CustomDropdown = ({ options, value, onChange, placeholder }: any) => {
    const [isOpen, setIsOpen] = useState(false);
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handleClick = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setIsOpen(false); };
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, []);

    return (
        <div className="relative" ref={ref}>
            <button
                onClick={() => setIsOpen(!isOpen)}
                className="w-full flex items-center justify-between p-3.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 shadow-sm rounded-xl font-bold text-slate-900 dark:text-white hover:border-modtale-accent transition-colors"
            >
                <span>{value ? value : placeholder}</span>
                <ChevronDown
                    className={`w-4 h-4 text-slate-400 transition-transform ${
                        isOpen ? 'rotate-180' : ''
                    }`}
                />
            </button>

            {isOpen && (
                <div className="absolute top-full left-0 right-0 mt-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-50 overflow-hidden max-h-60 overflow-y-auto custom-scrollbar">
                    {options.length > 0 ? (
                        options.map((opt: string) => (
                            <button
                                key={opt}
                                onClick={() => {
                                    onChange(opt);
                                    setIsOpen(false);
                                }}
                                className={`w-full text-left px-4 py-3 hover:bg-slate-50 dark:hover:bg-white/10 text-sm font-bold ${
                                    value === opt
                                        ? 'text-modtale-accent bg-blue-50 dark:bg-white/5'
                                        : 'text-slate-700 dark:text-slate-200'
                                }`}
                            >
                                {opt}
                            </button>
                        ))
                    ) : (
                        <div className="p-4 text-center text-slate-400 text-sm">
                            No versions found
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export const DownloadModal: React.FC<any> = ({ show, onClose, versionsByGame, onDownload, showExperimental, onToggleExperimental, onViewHistory }) => {
    useScrollLock(show);
    const [selectedGameVer, setSelectedGameVer] = useState<string>('');
    const [isListExpanded, setIsListExpanded] = useState(false);

    const hasExperimentalVersions = useMemo(() => {
        return Object.values(versionsByGame).flat().some((v: any) => v.channel && v.channel !== 'RELEASE');
    }, [versionsByGame]);

    useEffect(() => {
        if (show) {
            const keys = Object.keys(versionsByGame).sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
            if (keys.length > 0 && (!selectedGameVer || !keys.includes(selectedGameVer))) {
                setSelectedGameVer(keys[0]);
            }
        }
    }, [show, versionsByGame]);

    useEffect(() => {
        const currentVersions = versionsByGame[selectedGameVer] || [];
        if (currentVersions.length > 0) {
            const hasRelease = currentVersions.some((v: any) => !v.channel || v.channel === 'RELEASE');
            if (!hasRelease && !showExperimental && hasExperimentalVersions) {
                onToggleExperimental();
            }
        }
    }, [selectedGameVer, versionsByGame, showExperimental, onToggleExperimental, hasExperimentalVersions]);

    if (!show) return null;

    const gameVersions = Object.keys(versionsByGame).sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
    const currentVersions = versionsByGame[selectedGameVer] || [];

    const visibleVersions = currentVersions.filter((v: any) => showExperimental || (!v.channel || v.channel === 'RELEASE'));

    const sortedVersions = [...visibleVersions].sort((a: any, b: any) => {
        const dateDiff = new Date(b.releaseDate).getTime() - new Date(a.releaseDate).getTime();
        if (dateDiff !== 0) return dateDiff;
        return compareSemVer(b.versionNumber, a.versionNumber);
    });

    const latestVer = sortedVersions[0];

    const getVersionBadgeColor = (channel: string) => {
        switch(channel) {
            case 'BETA': return 'bg-purple-100 dark:bg-purple-500/20 text-purple-700 dark:text-purple-200 border-purple-200 dark:border-purple-500/30';
            case 'ALPHA': return 'bg-red-100 dark:bg-red-500/20 text-red-700 dark:text-red-100 border-red-200 dark:border-red-500/30';
            default: return 'bg-slate-100 dark:bg-black/20 border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-300';
        }
    };

    const themeClass = latestVer?.channel === 'ALPHA'
        ? 'bg-red-600 hover:bg-red-500 shadow-red-500/20 text-white'
        : latestVer?.channel === 'BETA'
            ? 'bg-purple-600 hover:bg-purple-500 shadow-purple-500/20 text-white'
            : 'bg-modtale-accent hover:bg-blue-500 shadow-blue-500/20 text-white';

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-slate-900/50 dark:bg-black/80 backdrop-blur-md p-4 animate-in fade-in" onClick={onClose}>
            <div className="bg-white/95 dark:bg-slate-900/95 backdrop-blur-xl border border-slate-200 dark:border-white/10 rounded-2xl w-full max-w-lg shadow-2xl overflow-hidden relative flex flex-col max-h-[90dvh]" onClick={e => e.stopPropagation()}>

                <div className="p-6 border-b border-slate-200 dark:border-white/10 flex justify-between items-center bg-slate-50/50 dark:bg-black/20 shrink-0">
                    <div>
                        <h3 className="text-xl font-black text-slate-900 dark:text-white flex items-center gap-2"><Download className="w-5 h-5 text-modtale-accent" /> Download</h3>
                        {hasExperimentalVersions && (
                            <div className="mt-1 flex items-center gap-2 cursor-pointer group" onClick={onToggleExperimental}>
                                <div className={`w-8 h-4 rounded-full relative transition-colors shadow-inner ${showExperimental ? 'bg-modtale-accent' : 'bg-slate-300 dark:bg-slate-700'}`}>
                                    <div className={`absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform shadow-sm ${showExperimental ? 'translate-x-4' : ''}`} />
                                </div>
                                <span className="text-[10px] font-bold text-slate-500 uppercase group-hover:text-slate-700 dark:group-hover:text-slate-300 transition-colors">Show Beta/Alpha</span>
                            </div>
                        )}
                    </div>
                    <button onClick={onClose} className="p-2 rounded-full hover:bg-slate-100 dark:hover:bg-white/10 text-slate-500 transition-colors"><X className="w-5 h-5" /></button>
                </div>

                <div className="p-6 bg-transparent overflow-y-auto custom-scrollbar">
                    <div className="mb-6">
                        <label className="block text-xs font-bold text-slate-500 dark:text-slate-400 uppercase mb-2 tracking-wider">Game Version</label>
                        <CustomDropdown
                            options={gameVersions}
                            value={selectedGameVer}
                            onChange={setSelectedGameVer}
                            placeholder="Select Game Version"
                        />
                    </div>

                    {latestVer ? (
                        <>
                            <Link
                                to="#"
                                onClick={(e) => { e.preventDefault(); onDownload(latestVer.fileUrl, latestVer.versionNumber, latestVer.dependencies, latestVer.channel); }}
                                className={`w-full p-5 rounded-2xl shadow-lg flex flex-col items-center justify-center gap-1.5 transition-all active:scale-95 mb-6 group relative overflow-hidden ${themeClass}`}
                            >
                                <div className="font-black text-xl flex items-center gap-2 group-hover:scale-105 transition-transform z-10"><Download className="w-6 h-6" /> Download Latest</div>
                                <div className={`text-xs font-bold font-mono px-3 py-1 rounded-full border flex items-center gap-2 z-10 ${getVersionBadgeColor(latestVer.channel || 'RELEASE')}`}>
                                    v{latestVer.versionNumber}
                                    {latestVer.channel !== 'RELEASE' && <span className="uppercase tracking-wider opacity-90">{latestVer.channel}</span>}
                                </div>
                            </Link>

                            <div className="relative mb-6">
                                <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-slate-200 dark:border-white/10"></div></div>
                                <div className="relative flex justify-center"><span className="bg-white dark:bg-slate-900 px-3 text-[10px] font-bold text-slate-500 uppercase tracking-widest">Other Versions</span></div>
                            </div>

                            <button
                                onClick={() => setIsListExpanded(!isListExpanded)}
                                className="w-full flex items-center justify-between p-3 rounded-xl border border-slate-200 dark:border-white/10 bg-white dark:bg-transparent hover:bg-slate-50 dark:hover:bg-white/5 transition-colors group shadow-sm"
                            >
                                <span className="font-bold text-slate-700 dark:text-slate-300 text-sm">View all files for {selectedGameVer}</span>
                                <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${isListExpanded ? 'rotate-180' : ''}`} />
                            </button>

                            {isListExpanded && (
                                <div className="mt-2 space-y-2 animate-in fade-in slide-in-from-top-2 duration-200">
                                    {sortedVersions.map((ver: any) => (
                                        <div key={ver.id} className="flex items-center justify-between p-3 rounded-xl bg-white dark:bg-slate-800/50 border border-slate-200 dark:border-white/5 shadow-sm">
                                            <div className="flex items-center gap-3">
                                                <div className="w-10 h-10 rounded-lg bg-slate-50 dark:bg-white/5 border border-slate-200 dark:border-white/10 flex items-center justify-center text-slate-400"><FileText className="w-5 h-5" /></div>
                                                <div>
                                                    <div className="font-bold text-slate-900 dark:text-white text-sm flex items-center gap-2">
                                                        v{ver.versionNumber}
                                                        {ver.channel !== 'RELEASE' && <span className={`text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded border ${getVersionBadgeColor(ver.channel)}`}>{ver.channel}</span>}
                                                    </div>
                                                    <div className="text-xs text-slate-500">{formatTimeAgo(ver.releaseDate)}</div>
                                                </div>
                                            </div>
                                            <Link
                                                to="#"
                                                onClick={(e) => { e.preventDefault(); onDownload(ver.fileUrl, ver.versionNumber, ver.dependencies, ver.channel); }}
                                                className="p-2 rounded-lg bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-300 hover:text-white dark:hover:text-white hover:bg-modtale-accent dark:hover:bg-modtale-accent/80 transition-colors"
                                            >
                                                <Download className="w-4 h-4" />
                                            </Link>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </>
                    ) : (
                        <div className="text-center py-12 text-slate-500 flex flex-col items-center gap-2">
                            <AlertCircle className="w-8 h-8 opacity-50" />
                            <p className="font-medium">No compatible versions found.</p>
                            {!showExperimental && currentVersions.length > 0 && hasExperimentalVersions && (
                                <button onClick={onToggleExperimental} className="text-xs text-modtale-accent font-bold hover:underline">
                                    Show Beta/Alpha versions
                                </button>
                            )}
                        </div>
                    )}
                </div>

                <div className="p-4 bg-slate-50/50 dark:bg-black/20 border-t border-slate-200 dark:border-white/10 text-center shrink-0">
                    <button onClick={onViewHistory} className="text-xs text-slate-500 hover:text-modtale-accent font-bold uppercase tracking-wider flex items-center justify-center gap-1 transition-colors w-full">
                        View Full Changelog <ChevronRight className="w-3 h-3" />
                    </button>
                </div>
            </div>
        </div>
    );
};

export const HistoryModal: React.FC<any> = ({
                                                show,
                                                onClose,
                                                history,
                                                showExperimental,
                                                onToggleExperimental,
                                                onDownload,
                                                hasExperimentalVersions
                                            }) => {
    useScrollLock(show);
    const [expandedChangelog, setExpandedChangelog] = useState<string | null>(null);

    const actualHasExperimental = useMemo(() => {
        if (hasExperimentalVersions !== undefined) return hasExperimentalVersions;
        return history.some((v: any) => v.channel && v.channel !== 'RELEASE');
    }, [history, hasExperimentalVersions]);

    const visibleHistory = useMemo(() => {
        return history.filter((v: any) => showExperimental || !v.channel || v.channel === 'RELEASE');
    }, [history, showExperimental]);

    if (!show) return null;

    const getVersionBadgeColor = (channel: string) => {
        switch(channel) {
            case 'BETA': return 'bg-purple-100 dark:bg-purple-500/20 text-purple-700 dark:text-purple-200 border-purple-200 dark:border-purple-500/30';
            case 'ALPHA': return 'bg-red-100 dark:bg-red-500/20 text-red-700 dark:text-red-100 border-red-200 dark:border-red-500/30';
            default: return 'bg-slate-100 dark:bg-black/20 border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-300';
        }
    };

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-slate-900/50 dark:bg-black/80 backdrop-blur-md p-4" onClick={onClose}>
            <div className="bg-white/95 dark:bg-slate-900/95 backdrop-blur-xl border border-slate-200 dark:border-white/10 rounded-2xl w-full max-w-3xl shadow-2xl overflow-hidden flex flex-col max-h-[85dvh]" onClick={e => e.stopPropagation()}>

                <div className="p-6 border-b border-slate-200 dark:border-white/10 flex justify-between items-center bg-slate-50/50 dark:bg-black/20 shrink-0">
                    <div>
                        <h3 className="text-xl font-black text-slate-900 dark:text-white flex items-center gap-2"><List className="w-5 h-5 text-modtale-accent" /> Changelog</h3>
                        {actualHasExperimental && (
                            <div className="mt-1 flex items-center gap-2 cursor-pointer group" onClick={onToggleExperimental}>
                                <div className={`w-8 h-4 rounded-full relative transition-colors shadow-inner ${showExperimental ? 'bg-modtale-accent' : 'bg-slate-300 dark:bg-slate-700'}`}>
                                    <div className={`absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform shadow-sm ${showExperimental ? 'translate-x-4' : ''}`} />
                                </div>
                                <span className="text-[10px] font-bold text-slate-500 uppercase group-hover:text-slate-700 dark:group-hover:text-slate-300 transition-colors">Show Beta/Alpha</span>
                            </div>
                        )}
                    </div>
                    <button onClick={onClose} className="p-2 rounded-full hover:bg-slate-100 dark:hover:bg-white/10 text-slate-500 transition-colors"><X className="w-5 h-5" /></button>
                </div>

                <div className="flex-1 overflow-y-auto p-6 bg-transparent custom-scrollbar">
                    <div className="space-y-6">
                        {visibleHistory.map((ver: any) => {
                            const isLong = ver.changelog && ver.changelog.length > 300;
                            return (
                                <div key={ver.id} className="bg-white dark:bg-slate-800/30 border border-slate-200 dark:border-white/5 rounded-xl p-5 shadow-sm hover:border-slate-300 dark:hover:border-white/10 transition-colors">
                                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4 border-b border-slate-100 dark:border-white/5 pb-4">
                                        <div>
                                            <div className="flex items-center gap-3 mb-1">
                                                <span className="text-xl font-black text-slate-900 dark:text-white">v{ver.versionNumber}</span>
                                                {ver.channel !== 'RELEASE' && <span className={`text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded border ${getVersionBadgeColor(ver.channel)}`}>{ver.channel}</span>}
                                            </div>
                                            <div className="flex items-center gap-3 text-xs font-bold text-slate-500 uppercase tracking-wide">
                                                <span>{formatTimeAgo(ver.releaseDate)}</span>
                                                <span className="w-1 h-1 rounded-full bg-slate-300 dark:bg-slate-600"></span>
                                                <span>{ver.gameVersions?.join(', ')}</span>
                                                <span className="w-1 h-1 rounded-full bg-slate-300 dark:bg-slate-600"></span>
                                                <span className="flex items-center gap-1.5"><Download className="w-3.5 h-3.5" /> {(ver.downloadCount || 0).toLocaleString()}</span>
                                            </div>
                                        </div>
                                        <Link
                                            to="#"
                                            onClick={(e) => { e.preventDefault(); onDownload(ver.fileUrl, ver.versionNumber, ver.dependencies, ver.channel); }}
                                            className="px-4 py-2 bg-slate-100 dark:bg-white/5 hover:bg-modtale-accent hover:text-white dark:hover:bg-modtale-accent text-slate-700 dark:text-slate-300 rounded-lg font-bold text-sm transition-all flex items-center justify-center gap-2"
                                        >
                                            <Download className="w-4 h-4" /> Download
                                        </Link>
                                    </div>

                                    {ver.changelog ? (
                                        <div className="mt-2">
                                            <div className={`prose prose-sm dark:prose-invert max-w-none text-slate-600 dark:text-slate-400 ${expandedChangelog === ver.id ? '' : 'line-clamp-3'}`}>
                                                <ReactMarkdown
                                                    rehypePlugins={[rehypeRaw, rehypeSanitize]}
                                                    components={{
                                                        li: ({children, ...props}: any) => <li className="my-0.5 [&>p]:my-0" {...props}>{children}</li>,
                                                        p: ({children, ...props}: any) => <p className="my-1" {...props}>{children}</p>
                                                    }}
                                                >{ver.changelog}</ReactMarkdown>
                                            </div>
                                            {isLong && (
                                                <button onClick={() => setExpandedChangelog(expandedChangelog === ver.id ? null : ver.id)} className="mt-3 text-xs font-bold text-modtale-accent hover:underline flex items-center gap-1">
                                                    {expandedChangelog === ver.id ? <><ChevronUp className="w-3 h-3"/> Show Less</> : <><ChevronDown className="w-3 h-3"/> Read More</>}
                                                </button>
                                            )}
                                        </div>
                                    ) : <p className="text-sm text-slate-500 dark:text-slate-600 italic">No changelog provided.</p>}
                                </div>
                            );
                        })}
                    </div>
                </div>
            </div>
        </div>
    );
};