import React, { useState, useEffect, useRef } from 'react';
import type { ProjectVersion } from '../../../types';
import { Download, X, ChevronDown, ChevronUp, Link as LinkIcon, List, AlertCircle, FileText, ChevronRight } from 'lucide-react';
import { formatTimeAgo, ChannelBadge } from '../../../utils/modHelpers';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import { api, BACKEND_URL } from '../../../utils/api';

const useScrollLock = (lock: boolean) => {
    useEffect(() => {
        if (lock) document.body.style.overflow = 'hidden';
        else document.body.style.overflow = 'unset';
        return () => { document.body.style.overflow = 'unset'; };
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

    const handleDownload = () => {
        dependencies.forEach((d, index) => {
            if (selected.has(d.modId)) {
                const url = `${BACKEND_URL}/api/projects/${d.modId}/versions/${d.versionNumber}/download`;
                setTimeout(() => {
                    const link = document.createElement('a');
                    link.href = url;
                    link.setAttribute('download', '');
                    document.body.appendChild(link);
                    link.click();
                    document.body.removeChild(link);
                }, index * 250);
            }
        });
        onConfirm();
    };

    const getIconUrl = (path?: string) => {
        if (!path) return '/assets/favicon.svg';
        return path.startsWith('http') ? path : `${BACKEND_URL}${path}`;
    };

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/80 backdrop-blur-md p-4 animate-in fade-in">
            <div className="bg-slate-900 border border-white/10 rounded-2xl max-w-lg w-full shadow-2xl overflow-hidden relative flex flex-col max-h-[85vh]">
                <div className="p-6 border-b border-white/10 flex justify-between items-center bg-black/20 shrink-0">
                    <h3 className="text-xl font-black text-white flex items-center gap-2">
                        <LinkIcon className="w-5 h-5 text-modtale-accent" /> Dependencies
                    </h3>
                    <button onClick={onClose}><X className="w-6 h-6 text-slate-400 hover:text-red-500" /></button>
                </div>

                <div className="p-6 space-y-4 overflow-y-auto bg-slate-900 custom-scrollbar">
                    <div className="flex items-center justify-between">
                        <div className="text-sm text-slate-400">
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
                                    className={`flex items-center justify-between p-3 rounded-xl border transition-all ${isSelected ? 'border-modtale-accent/30 bg-modtale-accent/5' : 'border-white/10 bg-white/5'}`}
                                >
                                    <div className="flex items-center gap-3 overflow-hidden">
                                        <input
                                            type="checkbox"
                                            checked={isSelected}
                                            onChange={() => toggleDep(dep.modId)}
                                            className="w-5 h-5 rounded text-modtale-accent focus:ring-modtale-accent border-slate-600 bg-slate-800 cursor-pointer flex-shrink-0"
                                        />
                                        <img
                                            src={getIconUrl(meta?.icon)}
                                            alt=""
                                            className="w-10 h-10 rounded-lg bg-slate-800 object-cover flex-shrink-0"
                                            onError={(e) => e.currentTarget.src='/assets/favicon.svg'}
                                        />
                                        <div className="cursor-pointer min-w-0" onClick={() => toggleDep(dep.modId)}>
                                            <div className="font-bold text-white text-sm truncate">
                                                {meta?.title || dep.modTitle || dep.modId}
                                            </div>
                                            <div className="text-xs text-slate-400 flex items-center gap-1.5">
                                                <span className="truncate max-w-[100px]">by {meta?.author || '...'}</span>
                                                <span className="w-1 h-1 rounded-full bg-slate-600"></span>
                                                <span className="font-mono opacity-80">v{dep.versionNumber}</span>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="flex-shrink-0 ml-2">
                                        {!dep.isOptional && <span className="text-[10px] font-bold uppercase bg-amber-500/10 text-amber-500 px-2 py-1 rounded-md border border-amber-500/20">Required</span>}
                                        {dep.isOptional && <span className="text-[10px] font-bold uppercase bg-white/10 text-slate-400 px-2 py-1 rounded-md border border-white/5">Optional</span>}
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    {missingRequired && (
                        <div className="flex items-start gap-2 text-xs text-amber-400 bg-amber-500/10 p-3 rounded-lg border border-amber-500/20">
                            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                            <p>Some <span className="font-bold">Required</span> dependencies are unchecked.</p>
                        </div>
                    )}
                </div>

                <div className="p-6 border-t border-white/10 bg-black/20 flex justify-end gap-3 shrink-0">
                    <button onClick={onClose} className="px-5 py-2.5 font-bold text-slate-400 hover:bg-white/10 rounded-xl transition-colors text-sm">
                        Cancel
                    </button>
                    <button
                        onClick={handleDownload}
                        className="px-6 py-2.5 font-bold rounded-xl shadow-lg shadow-modtale-accent/20 transition-colors flex items-center gap-2 bg-modtale-accent hover:bg-modtale-accentHover text-white text-sm"
                    >
                        <Download className="w-4 h-4" />
                        {selected.size > 0 ? `Download (${selected.size})` : "Continue without Downloading"}
                    </button>
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
                className="w-full flex items-center justify-between p-3.5 bg-slate-800 border border-white/10 rounded-xl font-bold text-white hover:border-modtale-accent transition-colors"
            >
                <span>{value ? value : placeholder}</span>
                <ChevronDown
                    className={`w-4 h-4 text-slate-400 transition-transform ${
                        isOpen ? 'rotate-180' : ''
                    }`}
                />
            </button>

            {isOpen && (
                <div className="absolute top-full left-0 right-0 mt-2 bg-slate-800 border border-white/10 rounded-xl shadow-xl z-50 overflow-hidden max-h-60 overflow-y-auto custom-scrollbar">
                    {options.length > 0 ? (
                        options.map((opt: string) => (
                            <button
                                key={opt}
                                onClick={() => {
                                    onChange(opt);
                                    setIsOpen(false);
                                }}
                                className={`w-full text-left px-4 py-3 hover:bg-white/10 text-sm font-bold ${
                                    value === opt
                                        ? 'text-modtale-accent bg-white/5'
                                        : 'text-slate-200'
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
            if (!hasRelease && !showExperimental) {
                onToggleExperimental();
            }
        }
    }, [selectedGameVer, versionsByGame, showExperimental, onToggleExperimental]);

    if (!show) return null;

    const gameVersions = Object.keys(versionsByGame).sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
    const currentVersions = versionsByGame[selectedGameVer] || [];

    const visibleVersions = currentVersions.filter((v: any) => showExperimental || (!v.channel || v.channel === 'RELEASE'));
    const sortedVersions = [...visibleVersions].sort((a: any, b: any) => new Date(b.releaseDate).getTime() - new Date(a.releaseDate).getTime());
    const latestVer = sortedVersions[0];

    const getVersionBadgeColor = (channel: string) => {
        switch(channel) {
            case 'BETA': return 'bg-blue-500/20 text-blue-200 border-blue-500/30';
            case 'ALPHA': return 'bg-orange-500/20 text-orange-200 border-orange-500/30';
            default: return 'bg-black/20 border-white/10';
        }
    };

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/80 backdrop-blur-md p-4 animate-in fade-in" onClick={onClose}>
            <div className="bg-slate-900 border border-white/10 rounded-2xl w-full max-w-lg shadow-2xl overflow-hidden relative flex flex-col max-h-[90vh]" onClick={e => e.stopPropagation()}>

                <div className="p-6 border-b border-white/10 flex justify-between items-center bg-black/20 shrink-0">
                    <div>
                        <h3 className="text-xl font-black text-white flex items-center gap-2"><Download className="w-5 h-5 text-modtale-accent" /> Download</h3>
                        <div className="mt-1 flex items-center gap-2 cursor-pointer group" onClick={onToggleExperimental}>
                            <div className={`w-8 h-4 rounded-full relative transition-colors ${showExperimental ? 'bg-modtale-accent' : 'bg-slate-700'}`}>
                                <div className={`absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform ${showExperimental ? 'translate-x-4' : ''}`} />
                            </div>
                            <span className="text-[10px] font-bold text-slate-500 uppercase group-hover:text-slate-300 transition-colors">Show Beta/Alpha</span>
                        </div>
                    </div>
                    <button onClick={onClose} className="p-2 rounded-full hover:bg-white/10 text-slate-500 transition-colors"><X className="w-5 h-5" /></button>
                </div>

                <div className="p-6 bg-slate-900 overflow-y-auto custom-scrollbar">
                    <div className="mb-6">
                        <label className="block text-xs font-bold text-slate-400 uppercase mb-2 tracking-wider">Game Version</label>
                        <CustomDropdown
                            options={gameVersions}
                            value={selectedGameVer}
                            onChange={setSelectedGameVer}
                            placeholder="Select Game Version"
                        />
                    </div>

                    {latestVer ? (
                        <>
                            <button
                                onClick={() => onDownload(latestVer.fileUrl, latestVer.versionNumber, latestVer.dependencies)}
                                className="w-full bg-modtale-accent hover:bg-modtale-accentHover text-white p-5 rounded-2xl shadow-lg shadow-modtale-accent/20 flex flex-col items-center justify-center gap-1.5 transition-all active:scale-95 mb-6 group relative overflow-hidden"
                            >
                                <div className="font-black text-xl flex items-center gap-2 group-hover:scale-105 transition-transform z-10"><Download className="w-6 h-6" /> Download Latest</div>
                                <div className={`text-xs font-bold font-mono px-3 py-1 rounded-full border flex items-center gap-2 z-10 ${getVersionBadgeColor(latestVer.channel || 'RELEASE')}`}>
                                    v{latestVer.versionNumber}
                                    {latestVer.channel !== 'RELEASE' && <span className="uppercase tracking-wider opacity-90">{latestVer.channel}</span>}
                                </div>
                            </button>

                            <div className="relative mb-6">
                                <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-white/10"></div></div>
                                <div className="relative flex justify-center"><span className="bg-slate-900 px-3 text-[10px] font-bold text-slate-500 uppercase tracking-widest">Other Versions</span></div>
                            </div>

                            <button
                                onClick={() => setIsListExpanded(!isListExpanded)}
                                className="w-full flex items-center justify-between p-3 rounded-xl border border-white/10 hover:bg-white/5 transition-colors group"
                            >
                                <span className="font-bold text-slate-300 text-sm">View all files for {selectedGameVer}</span>
                                <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${isListExpanded ? 'rotate-180' : ''}`} />
                            </button>

                            {isListExpanded && (
                                <div className="mt-2 space-y-2 animate-in fade-in slide-in-from-top-2 duration-200">
                                    {sortedVersions.map((ver: any) => (
                                        <div key={ver.id} className="flex items-center justify-between p-3 rounded-xl bg-slate-800/50 border border-white/5">
                                            <div className="flex items-center gap-3">
                                                <div className="w-10 h-10 rounded-lg bg-white/5 border border-white/10 flex items-center justify-center text-slate-400"><FileText className="w-5 h-5" /></div>
                                                <div>
                                                    <div className="font-bold text-white text-sm flex items-center gap-2">v{ver.versionNumber} <ChannelBadge channel={ver.channel} /></div>
                                                    <div className="text-xs text-slate-500">{formatTimeAgo(ver.releaseDate)}</div>
                                                </div>
                                            </div>
                                            <button onClick={() => onDownload(ver.fileUrl, ver.versionNumber, ver.dependencies)} className="p-2 rounded-lg bg-white/5 text-slate-300 hover:text-modtale-accent hover:bg-modtale-accent/10 transition-colors"><Download className="w-4 h-4" /></button>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </>
                    ) : (
                        <div className="text-center py-12 text-slate-500 flex flex-col items-center gap-2">
                            <AlertCircle className="w-8 h-8 opacity-50" />
                            <p className="font-medium">No compatible versions found.</p>
                            {!showExperimental && currentVersions.length > 0 && (
                                <button onClick={onToggleExperimental} className="text-xs text-modtale-accent font-bold hover:underline">
                                    Show Beta/Alpha versions
                                </button>
                            )}
                        </div>
                    )}
                </div>

                <div className="p-4 bg-black/20 border-t border-white/10 text-center shrink-0">
                    <button onClick={onViewHistory} className="text-xs text-slate-500 hover:text-modtale-accent font-bold uppercase tracking-wider flex items-center justify-center gap-1 transition-colors">
                        View Full Changelog <ChevronRight className="w-3 h-3" />
                    </button>
                </div>
            </div>
        </div>
    );
};

export const HistoryModal: React.FC<any> = ({ show, onClose, history, showExperimental, onToggleExperimental, onDownload }) => {
    useScrollLock(show);
    const [expandedChangelog, setExpandedChangelog] = useState<string | null>(null);

    if (!show) return null;

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/80 backdrop-blur-md p-4" onClick={onClose}>
            <div className="bg-slate-900 border border-white/10 rounded-2xl w-full max-w-3xl shadow-2xl overflow-hidden flex flex-col max-h-[85vh]" onClick={e => e.stopPropagation()}>

                <div className="p-6 border-b border-white/10 flex justify-between items-center bg-black/20 shrink-0">
                    <div>
                        <h3 className="text-xl font-black text-white flex items-center gap-2"><List className="w-5 h-5 text-modtale-accent" /> Changelog</h3>
                        <div className="mt-1 flex items-center gap-2 cursor-pointer group" onClick={onToggleExperimental}>
                            <div className={`w-8 h-4 rounded-full relative transition-colors ${showExperimental ? 'bg-modtale-accent' : 'bg-slate-700'}`}>
                                <div className={`absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform ${showExperimental ? 'translate-x-4' : ''}`} />
                            </div>
                            <span className="text-[10px] font-bold text-slate-500 uppercase group-hover:text-slate-300 transition-colors">Show Beta/Alpha</span>
                        </div>
                    </div>
                    <button onClick={onClose} className="p-2 rounded-full hover:bg-white/10 text-slate-500 transition-colors"><X className="w-5 h-5" /></button>
                </div>

                <div className="flex-1 overflow-y-auto p-6 bg-slate-900 custom-scrollbar">
                    <div className="space-y-6">
                        {history.map((ver: any) => {
                            const isLong = ver.changelog && ver.changelog.length > 300;
                            return (
                                <div key={ver.id} className="bg-slate-800/30 border border-white/5 rounded-xl p-5 shadow-sm hover:border-white/10 transition-colors">
                                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4 border-b border-white/5 pb-4">
                                        <div>
                                            <div className="flex items-center gap-3 mb-1">
                                                <span className="text-xl font-black text-white">v{ver.versionNumber}</span>
                                                <ChannelBadge channel={ver.channel} />
                                            </div>
                                            <div className="flex items-center gap-3 text-xs font-bold text-slate-500 uppercase tracking-wide">
                                                <span>{formatTimeAgo(ver.releaseDate)}</span>
                                                <span className="w-1 h-1 rounded-full bg-slate-600"></span>
                                                <span>{ver.gameVersions?.join(', ')}</span>
                                            </div>
                                        </div>
                                        <button
                                            onClick={() => onDownload(ver.fileUrl, ver.versionNumber, ver.dependencies)}
                                            className="px-4 py-2 bg-white/5 hover:bg-modtale-accent hover:text-white text-slate-300 rounded-lg font-bold text-sm transition-all flex items-center justify-center gap-2"
                                        >
                                            <Download className="w-4 h-4" /> Download
                                        </button>
                                    </div>

                                    {ver.changelog ? (
                                        <div className="mt-2">
                                            <div className={`prose prose-sm dark:prose-invert max-w-none text-slate-400 ${expandedChangelog === ver.id ? '' : 'line-clamp-3'}`}>
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
                                    ) : <p className="text-sm text-slate-600 italic">No changelog provided.</p>}
                                </div>
                            );
                        })}
                    </div>
                </div>
            </div>
        </div>
    );
};