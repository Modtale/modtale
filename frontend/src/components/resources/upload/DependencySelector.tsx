import React, { useState, useEffect, useMemo, useRef } from 'react';
import { Search, Loader2, X, Plus, AlertTriangle, FileText, CheckSquare, Square, ShieldCheck, RefreshCw, Check, AlertCircle, ChevronRight, ChevronDown, ToggleRight, ToggleLeft } from 'lucide-react';
import { api, BACKEND_URL } from '../../../utils/api';
import type {Mod, ProjectVersion, ModDependency} from '../../../types';
import { ScrollStyles } from './FormShared';

interface DependencyWizardProps {
    previousDeps: ModDependency[];
    targetGameVersion: string | undefined;
    onConfirm: (newDeps: string[]) => void;
    onClose: () => void;
}

const DependencyRow: React.FC<{
    dep: ModDependency;
    targetGameVersion: string | undefined;
    onSelect: (id: string, version: string | null) => void;
    initialSelection?: string;
}> = ({ dep, targetGameVersion, onSelect, initialSelection }) => {
    const [versions, setVersions] = useState<ProjectVersion[]>([]);
    const [loading, setLoading] = useState(false);
    const [selectedVer, setSelectedVer] = useState<string>(initialSelection || '');
    const [isOpen, setIsOpen] = useState(false);

    const buttonRef = useRef<HTMLButtonElement>(null);
    const [dropdownStyle, setDropdownStyle] = useState<React.CSSProperties>({});

    useEffect(() => {
        const fetchVersions = async () => {
            setLoading(true);
            try {
                const res = await api.get(`/projects/${dep.modId}`);
                const mod = res.data as Mod;
                setVersions(mod.versions || []);
            } catch (e) {
                console.error(`Failed to fetch versions for ${dep.modId}`);
            } finally {
                setLoading(false);
            }
        };
        fetchVersions();
    }, [dep.modId]);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (isOpen && buttonRef.current && !buttonRef.current.contains(event.target as Node)) {
                const dropdownEl = document.getElementById(`dropdown-${dep.modId}`);
                if (dropdownEl && !dropdownEl.contains(event.target as Node)) {
                    setIsOpen(false);
                }
            }
        };

        const handleScroll = () => { if (isOpen) setIsOpen(false); };

        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside);
            document.addEventListener('scroll', handleScroll, true);
            window.addEventListener('resize', handleScroll);
        }

        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
            document.removeEventListener('scroll', handleScroll, true);
            window.removeEventListener('resize', handleScroll);
        };
    }, [isOpen, dep.modId]);

    const toggleOpen = () => {
        if (!isOpen && buttonRef.current) {
            const rect = buttonRef.current.getBoundingClientRect();
            setDropdownStyle({
                position: 'fixed',
                top: `${rect.bottom + 4}px`,
                left: `${rect.right - 256}px`,
                width: '16rem',
                zIndex: 9999
            });
        }
        setIsOpen(!isOpen);
    };

    const compatibleVersions = useMemo(() => {
        if (!targetGameVersion) return versions;
        return versions.filter(v => v.gameVersions?.includes(targetGameVersion));
    }, [versions, targetGameVersion]);

    const incompatibleVersions = useMemo(() => {
        if (!targetGameVersion) return [];
        return versions.filter(v => !v.gameVersions?.includes(targetGameVersion));
    }, [versions, targetGameVersion]);

    const handleSelect = (ver: string) => {
        setSelectedVer(ver);
        onSelect(dep.modId, ver);
        setIsOpen(false);
    };

    return (
        <div className="flex items-center justify-between p-3 border-b border-slate-100 dark:border-white/5 last:border-0 hover:bg-slate-50 dark:hover:bg-white/[0.02]">
            <div className="flex-1 min-w-0 pr-4">
                <div className="font-bold text-slate-700 dark:text-slate-200 truncate">{dep.modTitle || dep.modId}</div>
                <div className="text-xs text-slate-500 flex items-center gap-2">
                    <span>Previous: <span className="font-mono bg-slate-100 dark:bg-white/10 px-1 rounded">{dep.versionNumber}</span></span>
                    {dep.isOptional && <span className="text-[10px] uppercase bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 px-1.5 rounded">Optional</span>}
                </div>
            </div>

            <div className="w-48 shrink-0">
                {loading ? (
                    <div className="flex items-center gap-2 text-xs text-slate-400 justify-end"><Loader2 className="w-3 h-3 animate-spin" /> Loading...</div>
                ) : (
                    <>
                        <button
                            ref={buttonRef}
                            onClick={toggleOpen}
                            className={`w-full text-xs px-3 py-2 rounded-lg border flex justify-between items-center transition-all ${
                                selectedVer
                                    ? 'border-slate-200 dark:border-white/10 bg-white dark:bg-white/5 text-slate-900 dark:text-white'
                                    : 'border-amber-300 bg-amber-50 dark:border-amber-900/50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400 font-bold shadow-sm'
                            } hover:border-modtale-accent focus:ring-2 focus:ring-modtale-accent/20 outline-none`}
                        >
                            <span className="truncate mr-2">{selectedVer || "Select Version..."}</span>
                            <ChevronDown className={`w-3 h-3 opacity-50 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
                        </button>

                        {isOpen && (
                            <div
                                id={`dropdown-${dep.modId}`}
                                style={dropdownStyle}
                                className="max-h-60 overflow-y-auto bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-lg shadow-2xl custom-scrollbar"
                            >
                                {compatibleVersions.length > 0 ? (
                                    <>
                                        <div className="px-3 py-2 text-[10px] font-bold text-slate-400 uppercase tracking-wider bg-slate-50 dark:bg-white/5 sticky top-0 z-10 backdrop-blur-sm">Compatible</div>
                                        {compatibleVersions.map(v => (
                                            <button
                                                key={v.id}
                                                onClick={() => handleSelect(v.versionNumber)}
                                                className={`w-full text-left px-3 py-2 text-xs hover:bg-slate-50 dark:hover:bg-white/10 flex justify-between items-center ${selectedVer === v.versionNumber ? 'text-modtale-accent font-bold bg-modtale-accent/5' : 'text-slate-700 dark:text-slate-300'}`}
                                            >
                                                <span>{v.versionNumber}</span>
                                                {selectedVer === v.versionNumber && <Check className="w-3 h-3" />}
                                            </button>
                                        ))}
                                    </>
                                ) : (
                                    <div className="px-3 py-4 text-center text-xs text-slate-400 italic">No compatible versions found.</div>
                                )}

                                {incompatibleVersions.length > 0 && (
                                    <>
                                        <div className="px-3 py-2 text-[10px] font-bold text-red-400 uppercase tracking-wider bg-red-50 dark:bg-red-900/10 border-t border-slate-100 dark:border-white/5 mt-1 sticky top-0 z-10">Incompatible</div>
                                        {incompatibleVersions.map(v => (
                                            <button
                                                key={v.id}
                                                onClick={() => handleSelect(v.versionNumber)}
                                                className={`w-full text-left px-3 py-2 text-xs hover:bg-red-50 dark:hover:bg-red-900/20 flex justify-between items-center opacity-75 ${selectedVer === v.versionNumber ? 'text-red-600 dark:text-red-400 font-bold' : 'text-slate-500'}`}
                                            >
                                                <span>{v.versionNumber}</span>
                                                {selectedVer === v.versionNumber && <Check className="w-3 h-3" />}
                                            </button>
                                        ))}
                                    </>
                                )}
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
};

const DependencyUpdateWizard: React.FC<DependencyWizardProps> = ({ previousDeps, targetGameVersion, onConfirm, onClose }) => {
    useEffect(() => {
        document.body.style.overflow = 'hidden';
        return () => { document.body.style.overflow = 'unset'; };
    }, []);

    const [selections, setSelections] = useState<Record<string, string | null>>({});

    const handleRowSelect = (id: string, ver: string | null) => {
        setSelections(prev => ({ ...prev, [id]: ver }));
    };

    const handleConfirm = () => {
        const result: string[] = [];
        previousDeps.forEach(dep => {
            const newVer = selections[dep.modId];
            if (newVer) {
                let entry = `${dep.modId}:${newVer}`;
                if (dep.isOptional) entry += `:optional`;
                result.push(entry);
            }
        });
        onConfirm(result);
    };

    const validCount = Object.values(selections).filter(v => v !== null).length;

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in">
            <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl w-full max-w-2xl shadow-2xl flex flex-col max-h-[85vh]">
                <div className="p-6 border-b border-slate-200 dark:border-white/10 flex justify-between items-center bg-slate-50 dark:bg-modtale-dark">
                    <div>
                        <h3 className="text-xl font-black text-slate-900 dark:text-white flex items-center gap-2">
                            <RefreshCw className="w-5 h-5 text-modtale-accent" /> Update Dependencies
                        </h3>
                        <p className="text-sm text-slate-500 mt-1">
                            Select versions for the <strong>{previousDeps.length}</strong> mods from the previous release.
                        </p>
                    </div>
                    <button onClick={onClose}><X className="w-6 h-6 text-slate-400 hover:text-red-500" /></button>
                </div>

                <div className="flex-1 overflow-y-auto p-4 custom-scrollbar bg-white dark:bg-modtale-card">
                    {!targetGameVersion && (
                        <div className="mb-4 p-3 bg-amber-50 dark:bg-amber-900/20 border border-amber-100 dark:border-amber-900/30 rounded-lg text-xs text-amber-800 dark:text-amber-200 flex items-start gap-2">
                            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                            <span><strong>Note:</strong> No Game Version selected. Showing all available versions. Select a Game Version first to filter compatible mods.</span>
                        </div>
                    )}
                    <div className="border border-slate-200 dark:border-white/10 rounded-lg overflow-hidden bg-white dark:bg-black/20">
                        {previousDeps.map(dep => (
                            <DependencyRow key={dep.modId} dep={dep} targetGameVersion={targetGameVersion} onSelect={handleRowSelect} />
                        ))}
                    </div>
                </div>

                <div className="p-6 border-t border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-modtale-dark flex justify-between items-center">
                    <div className="text-sm text-slate-500"><strong>{validCount}</strong> selected</div>
                    <div className="flex gap-3">
                        <button onClick={onClose} className="px-4 py-2 font-bold text-slate-500 hover:bg-slate-200 dark:hover:bg-white/10 rounded-lg">Skip</button>
                        <button onClick={handleConfirm} disabled={validCount === 0} className="px-6 py-2 font-bold rounded-lg shadow-lg flex items-center gap-2 bg-modtale-accent hover:bg-modtale-accentHover text-white disabled:opacity-50">
                            <Check className="w-4 h-4" /> Import Selected
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};

interface DependencySelectorProps {
    selectedDeps: string[];
    onChange: (deps: string[]) => void;
    targetGameVersion?: string;
    label?: string;
    previousDependencies?: ModDependency[];
    currentProjectId?: string;
    isModpack?: boolean;
    disabled?: boolean;
}

interface MetaCache {
    [key: string]: { title: string; author: string; icon: string };
}

export const DependencySelector: React.FC<DependencySelectorProps> = ({ selectedDeps, onChange, targetGameVersion, label = "Dependencies", previousDependencies, currentProjectId, isModpack = false, disabled }) => {
    const [search, setSearch] = useState('');
    const [results, setResults] = useState<Mod[]>([]);
    const [loading, setLoading] = useState(false);
    const [selectedModForVersion, setSelectedModForVersion] = useState<Mod | null>(null);
    const [showIncompatible, setShowIncompatible] = useState(false);
    const [showAlphaBeta, setShowAlphaBeta] = useState(false);
    const [isOptional, setIsOptional] = useState(false);
    const [showWizard, setShowWizard] = useState(false);

    const [metaCache, setMetaCache] = useState<MetaCache>({});

    useEffect(() => {
        const fetchMeta = async () => {
            const missingIds = selectedDeps.map(d => d.split(':')[0]).filter(id => !metaCache[id]);
            if (missingIds.length === 0) return;
            const newCache = { ...metaCache };
            await Promise.all(missingIds.map(async (id) => {
                try {
                    const res = await api.get(`/projects/${id}/meta`);
                    newCache[id] = { title: res.data.title, author: res.data.author, icon: res.data.icon };
                } catch (e) { newCache[id] = { title: id, author: 'Unknown', icon: '' }; }
            }));
            setMetaCache(newCache);
        };
        fetchMeta();
    }, [selectedDeps.length]);

    useEffect(() => {
        if (selectedModForVersion) {
            document.body.style.overflow = 'hidden';
        } else {
            document.body.style.overflow = 'unset';
        }
        return () => { document.body.style.overflow = 'unset'; };
    }, [selectedModForVersion]);

    useEffect(() => {
        if (selectedModForVersion) {
            const compatible = selectedModForVersion.versions.filter(v => !targetGameVersion || v.gameVersions?.includes(targetGameVersion));
            const hasRelease = compatible.some(v => !v.channel || v.channel === 'RELEASE');
            const hasAny = compatible.length > 0;

            if (hasAny && !hasRelease) {
                setShowAlphaBeta(true);
            } else {
                setShowAlphaBeta(false);
            }
        }
    }, [selectedModForVersion, targetGameVersion]);

    useEffect(() => {
        const timer = setTimeout(async () => {
            if (search.length < 2 || disabled) return;
            setLoading(true);
            try {
                const res = await api.get(`/projects?search=${search}`);
                const filtered = (res.data.content || []).filter((m: Mod) =>
                    m.classification !== 'MODPACK' &&
                    m.classification !== 'SAVE' &&
                    m.id !== currentProjectId
                );
                setResults(filtered);
            } catch (e) { setResults([]); } finally { setLoading(false); }
        }, 300);
        return () => clearTimeout(timer);
    }, [search, currentProjectId, disabled]);

    const confirmVersion = (versionNumber: string) => {
        if (!selectedModForVersion || disabled) return;
        if (selectedDeps.some(d => d.startsWith(`${selectedModForVersion.id}:`))) { alert("Project already added."); return; }
        setMetaCache(prev => ({ ...prev, [selectedModForVersion.id]: { title: selectedModForVersion.title, author: selectedModForVersion.author, icon: selectedModForVersion.imageUrl } }));

        const finalOptional = isModpack ? false : isOptional;

        const entry = `${selectedModForVersion.id}:${versionNumber}${finalOptional ? ':optional' : ''}`;
        onChange([...selectedDeps, entry]);
        setSelectedModForVersion(null);
        setIsOptional(false);
        setSearch('');
        setResults([]);
    };

    const removeDep = (index: number) => {
        if(disabled) return;
        const next = [...selectedDeps]; next.splice(index, 1); onChange(next);
    };

    const toggleOptionalExisting = (index: number) => {
        if (isModpack || disabled) return;
        const next = [...selectedDeps];
        const parts = next[index].split(':');
        const iscurrentlyOptional = parts.length === 3 && parts[2] === 'optional';
        if (iscurrentlyOptional) next[index] = `${parts[0]}:${parts[1]}`;
        else next[index] = `${parts[0]}:${parts[1]}:optional`;
        onChange(next);
    };

    const getIconUrl = (path?: string) => { if (!path) return '/assets/favicon.svg'; return path.startsWith('http') ? path : `${BACKEND_URL}${path}`; };

    const availableVersions = selectedModForVersion?.versions || [];

    const isRelease = (v: any) => !v.channel || v.channel === 'RELEASE';

    const filteredVersions = availableVersions.filter(v => {
        const versionMatch = !targetGameVersion || v.gameVersions?.includes(targetGameVersion);
        const channelMatch = showAlphaBeta || isRelease(v);
        return (showIncompatible || versionMatch) && channelMatch;
    });

    return (
        <div className={`space-y-4 border border-slate-200 dark:border-white/10 rounded-2xl p-6 bg-slate-50 dark:bg-black/20 ${disabled ? 'opacity-70' : ''}`}>
            <ScrollStyles />

            {showWizard && previousDependencies && !disabled && (
                <DependencyUpdateWizard
                    previousDeps={previousDependencies}
                    targetGameVersion={targetGameVersion}
                    onConfirm={(newDeps) => {
                        const currentIds = new Set(selectedDeps.map(d => d.split(':')[0]));
                        const toAdd = newDeps.filter(d => !currentIds.has(d.split(':')[0]));
                        onChange([...selectedDeps, ...toAdd]);
                        setShowWizard(false);
                    }}
                    onClose={() => setShowWizard(false)}
                />
            )}

            {selectedModForVersion && !disabled && (
                <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm p-4 animate-in fade-in">
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl w-full max-w-md shadow-2xl overflow-hidden flex flex-col max-h-[85vh]">
                        <div className="p-4 border-b border-slate-100 dark:border-white/5 flex justify-between items-center bg-slate-50 dark:bg-black/20">
                            <h3 className="font-bold text-slate-900 dark:text-white">Select Version</h3>
                            <button onClick={() => setSelectedModForVersion(null)}><X className="w-5 h-5 text-slate-400 hover:text-slate-900 dark:hover:text-white" /></button>
                        </div>

                        <div className="p-3 bg-slate-50 dark:bg-black/20 border-b border-slate-200 dark:border-white/5 flex flex-col gap-2">
                            <div className="flex items-center justify-between">
                                <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Show Alpha/Beta</span>
                                <button
                                    onClick={() => setShowAlphaBeta(!showAlphaBeta)}
                                    className={`transition-colors ${showAlphaBeta ? 'text-modtale-accent' : 'text-slate-400'}`}
                                >
                                    {showAlphaBeta ? <ToggleRight className="w-8 h-8" /> : <ToggleLeft className="w-8 h-8" />}
                                </button>
                            </div>

                            {!isModpack && (
                                <div className={`flex items-center gap-2 cursor-pointer transition-colors ${isOptional ? 'text-blue-500' : 'text-slate-500'}`} onClick={() => setIsOptional(!isOptional)}>
                                    <div className={`w-4 h-4 rounded flex items-center justify-center border transition-all ${isOptional ? 'bg-blue-500 border-blue-500 text-white' : 'border-slate-300 dark:border-white/20'}`}>
                                        {isOptional && <CheckSquare className="w-3 h-3" />}
                                    </div>
                                    <span className="text-xs font-bold">Optional Dependency</span>
                                </div>
                            )}
                        </div>

                        <div className="p-2 overflow-y-auto custom-scrollbar flex-1">
                            {filteredVersions.length > 0 ? filteredVersions.map(v => {
                                const isCompatible = !targetGameVersion || v.gameVersions?.includes(targetGameVersion);
                                return (
                                    <button key={v.id} onClick={() => confirmVersion(v.versionNumber)} className={`w-full text-left px-4 py-3 flex justify-between items-center border-b border-slate-100 dark:border-white/5 last:border-0 transition-colors ${!isCompatible ? 'bg-red-50 dark:bg-red-900/10' : 'hover:bg-slate-50 dark:hover:bg-white/5'}`}>
                                        <div>
                                            <div className="flex items-center gap-2">
                                                <span className={`font-bold text-sm ${!isCompatible ? 'text-red-700 dark:text-red-400' : 'text-slate-800 dark:text-slate-200'}`}>{v.versionNumber}</span>
                                                {v.channel !== 'RELEASE' && (
                                                    <span className={`text-[9px] font-bold px-1.5 rounded border ${v.channel === 'BETA' ? 'text-blue-500 border-blue-200 bg-blue-50 dark:bg-blue-900/20 dark:border-blue-800' : 'text-orange-500 border-orange-200 bg-orange-50 dark:bg-orange-900/20 dark:border-orange-800'}`}>
                                                        {v.channel}
                                                    </span>
                                                )}
                                            </div>
                                            <div className="text-xs text-slate-500">For {v.gameVersions?.join(', ')}</div>
                                        </div>
                                        {isCompatible ? <Plus className="w-4 h-4 text-modtale-accent" /> : <AlertTriangle className="w-4 h-4 text-red-500" />}
                                    </button>
                                )
                            }) : (
                                <div className="p-4 text-center text-xs text-slate-400 italic">
                                    No versions found with current filters.
                                </div>
                            )}
                        </div>
                        {targetGameVersion && (
                            <div className="p-3 bg-slate-50 dark:bg-black/20 border-t border-slate-200 dark:border-white/5 text-center shrink-0">
                                <button onClick={() => setShowIncompatible(!showIncompatible)} className="text-xs font-bold text-modtale-accent hover:underline">{showIncompatible ? 'Hide' : 'Show'} incompatible versions</button>
                            </div>
                        )}
                    </div>
                </div>
            )}

            <div className="flex justify-between items-center">
                <h3 className="font-bold text-slate-900 dark:text-white flex items-center gap-2 text-sm uppercase tracking-wide"><Search className="w-4 h-4" /> {label}</h3>
            </div>

            {previousDependencies && previousDependencies.length > 0 && selectedDeps.length === 0 && !disabled && (
                <div className="bg-blue-50 dark:bg-blue-900/10 border border-blue-100 dark:border-blue-900/20 p-4 rounded-xl flex items-center justify-between animate-in fade-in">
                    <div className="flex items-center gap-3">
                        <RefreshCw className="w-5 h-5 text-blue-500" />
                        <div>
                            <h4 className="text-sm font-bold text-blue-900 dark:text-blue-100">Updates Available</h4>
                            <p className="text-xs text-blue-700 dark:text-blue-300">Found {previousDependencies.length} mods from the previous release.</p>
                        </div>
                    </div>
                    <button onClick={() => setShowWizard(true)} className="text-xs font-bold bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg transition-colors flex items-center gap-1 shadow-sm">
                        Import & Update <ChevronRight className="w-3 h-3" />
                    </button>
                </div>
            )}

            <div className="relative">
                <input type="text" disabled={disabled} value={search} onChange={e => setSearch(e.target.value)} className={`w-full bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl pl-10 pr-4 py-3 text-sm focus:ring-2 focus:ring-modtale-accent outline-none shadow-sm transition-all dark:text-white ${disabled ? 'cursor-not-allowed bg-slate-50 dark:bg-black/10' : ''}`} placeholder="Search for projects..." />
                <Search className="absolute left-3.5 top-3.5 w-4 h-4 text-slate-400" />
                {loading && <div className="absolute right-4 top-3.5"><Loader2 className="w-4 h-4 animate-spin text-modtale-accent" /></div>}
            </div>

            {results.length > 0 && !disabled && (
                <div className="max-h-56 overflow-y-auto custom-scrollbar bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl shadow-lg divide-y divide-slate-100 dark:divide-white/5">
                    {results.map(mod => (
                        <button key={mod.id} onClick={() => setSelectedModForVersion(mod)} className="w-full text-left px-4 py-3 hover:bg-slate-50 dark:hover:bg-white/5 flex justify-between items-center text-sm transition-colors group">
                            <div className="flex items-center gap-3">
                                <img src={getIconUrl(mod.imageUrl)} className="w-8 h-8 rounded-md bg-slate-200 object-cover" alt="" onError={(e) => e.currentTarget.src='/assets/favicon.svg'} />
                                <div>
                                    <div className="font-bold text-slate-800 dark:text-slate-200 group-hover:text-modtale-accent">{mod.title}</div>
                                    <div className="text-xs text-slate-500">by {mod.author}</div>
                                </div>
                            </div>
                            <Plus className="w-5 h-5 text-slate-300 group-hover:text-modtale-accent" />
                        </button>
                    ))}
                </div>
            )}

            <div className="mt-6">
                <div className="flex justify-between items-center mb-3">
                    <h4 className="text-xs font-bold uppercase text-slate-500">Selected ({selectedDeps.length})</h4>
                </div>
                {selectedDeps.length === 0 ? (
                    <div className="text-center p-8 border-2 border-dashed border-slate-200 dark:border-white/10 rounded-xl text-slate-400 text-sm italic">
                        No dependencies added.
                    </div>
                ) : (
                    <div className="grid grid-cols-1 gap-2">
                        {selectedDeps.map((entry, idx) => {
                            const [id, ver, opt] = entry.split(':');
                            const isOpt = opt === 'optional';
                            const meta = metaCache[id];
                            return (
                                <div key={idx} className="flex items-center justify-between bg-white dark:bg-modtale-card p-3 rounded-xl border border-slate-200 dark:border-white/10 text-sm shadow-sm group">
                                    <div className="flex items-center gap-3 overflow-hidden">
                                        <div className={`p-1 flex-shrink-0 rounded-lg ${isOpt ? 'bg-slate-100 text-slate-500 dark:bg-white/5' : 'bg-amber-100 text-amber-600 dark:bg-amber-900/20'}`}>
                                            {isOpt ? <FileText className="w-4 h-4" /> : <ShieldCheck className="w-4 h-4" />}
                                        </div>
                                        <img src={getIconUrl(meta?.icon)} alt="" className="w-8 h-8 rounded bg-slate-200 dark:bg-white/10 object-cover flex-shrink-0" onError={(e) => e.currentTarget.src='/assets/favicon.svg'} />
                                        <div className="min-w-0">
                                            <div className="font-bold text-slate-700 dark:text-slate-200 truncate">{meta?.title || id}</div>
                                            <div className="text-xs text-slate-500 flex items-center gap-1.5">
                                                <span className="truncate max-w-[100px]">by {meta?.author || '...'}</span>
                                                <span className="w-1 h-1 rounded-full bg-slate-300 dark:bg-white/20"></span>
                                                <span className="font-mono bg-slate-100 dark:bg-white/5 px-1.5 py-0.5 rounded">v{ver}</span>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-2 flex-shrink-0 ml-2">
                                        {!isModpack && (
                                            <button disabled={disabled} onClick={() => toggleOptionalExisting(idx)} className={`text-xs font-bold px-3 py-1.5 rounded-lg border transition-all ${isOpt ? 'border-slate-200 text-slate-400 hover:text-slate-600 hover:bg-slate-50' : 'border-amber-200 bg-amber-50 text-amber-700 hover:bg-amber-100 dark:bg-amber-900/20 dark:border-amber-900/30 dark:text-amber-400'} ${disabled ? 'cursor-not-allowed opacity-70' : ''}`}>
                                                {isOpt ? 'Optional' : 'Required'}
                                            </button>
                                        )}
                                        <button disabled={disabled} onClick={() => removeDep(idx)} className={`text-slate-400 p-2 rounded-lg transition-colors ${disabled ? 'cursor-not-allowed opacity-50' : 'hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20'}`}><X className="w-4 h-4" /></button>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
};