import React, { useState, useEffect, useMemo, useRef } from 'react';
import { Search, Loader2, X, Plus, AlertTriangle, FileText, CheckSquare, ShieldCheck, RefreshCw, Check, AlertCircle, ChevronRight, ChevronDown, ToggleRight, ToggleLeft } from 'lucide-react';
import { projectClient } from '@/modules/project/api/projectClient';
import { ScrollStyles } from './FormShared';
import { compareSemVer } from '@/utils/modHelpers';
import { theme } from '@/styles/theme';
import { BACKEND_URL } from '@/utils/api';
import type { Project, ProjectVersion, ProjectDependency } from '@/types';
import { useScrollLock } from '@/hooks/useScrollLock';
import { parseDependencyEntry, serializeDependencyEntry } from '../utils/dependencyEntries';

interface DependencyWizardProps {
    previousDeps: ProjectDependency[];
    targetGameVersion: string | undefined;
    onConfirm: (newDeps: string[]) => void;
    onClose: () => void;
}

const DependencyRow: React.FC<{ dep: ProjectDependency; targetGameVersion: string | undefined; onSelect: (id: string, version: string | null) => void; initialSelection?: string; }> = ({ dep, targetGameVersion, onSelect, initialSelection }) => {
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
                const project = await projectClient.getProject(dep.projectId);
                const sorted = (project.versions || []).sort((a, b) => compareSemVer(b.versionNumber, a.versionNumber));
                setVersions(sorted);
            } catch (e) {
            } finally {
                setLoading(false);
            }
        };
        fetchVersions();
    }, [dep.projectId]);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (isOpen && buttonRef.current && !buttonRef.current.contains(event.target as Node)) {
                const dropdownEl = document.getElementById(`dropdown-${dep.projectId}`);
                if (dropdownEl && !dropdownEl.contains(event.target as Node)) setIsOpen(false);
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
    }, [isOpen, dep.projectId]);

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

    const compatibleVersions = useMemo(() => targetGameVersion ? versions.filter(v => v.gameVersions?.includes(targetGameVersion)) : versions, [versions, targetGameVersion]);
    const incompatibleVersions = useMemo(() => targetGameVersion ? versions.filter(v => !v.gameVersions?.includes(targetGameVersion)) : [], [versions, targetGameVersion]);

    const handleSelect = (ver: string) => {
        setSelectedVer(ver);
        onSelect(dep.projectId, ver);
        setIsOpen(false);
    };

    return (
        <div className={`flex items-center justify-between p-4 border-b border-slate-200 dark:border-white/10 last:border-0 bg-white dark:bg-slate-800 transition-colors`}>
            <div className="flex-1 min-w-0 pr-4">
                <div className={`font-bold ${theme.colors.textPrimary} truncate`}>{dep.projectTitle || dep.projectId}</div>
                <div className={`text-xs ${theme.colors.textMuted} flex items-center gap-2`}>
                    <span>Previous: <span className={`font-mono ${theme.colors.bgSurfaceAlt} px-1 rounded`}>{dep.versionNumber}</span></span>
                    {dep.isOptional && <span className="text-[10px] uppercase bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 px-1.5 rounded">Optional</span>}
                </div>
            </div>

            <div className="w-48 shrink-0">
                {loading ? (
                    <div className={`flex items-center gap-2 text-xs ${theme.colors.textMuted} justify-end`}><Loader2 className="w-3 h-3 animate-spin" /> Loading...</div>
                ) : (
                    <>
                        <button
                            ref={buttonRef}
                            onClick={toggleOpen}
                            className={`w-full text-xs px-3 py-2 rounded-lg border flex justify-between items-center transition-all ${
                                selectedVer
                                    ? `${theme.colors.borderFaint} ${theme.colors.bgSurfaceAlt} ${theme.colors.textPrimary}`
                                    : 'border-amber-300 bg-amber-50 dark:border-amber-900/50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400 font-bold shadow-sm'
                            } hover:border-modtale-accent focus:ring-2 focus:ring-modtale-accent/20 outline-none`}
                        >
                            <span className="truncate mr-2">{selectedVer || "Select Version..."}</span>
                            <ChevronDown className={`w-3 h-3 opacity-50 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
                        </button>

                        {isOpen && (
                            <div id={`dropdown-${dep.projectId}`} style={dropdownStyle} className={`max-h-60 overflow-y-auto ${theme.colors.bgBase} border ${theme.colors.border} rounded-lg shadow-2xl custom-scrollbar`}>
                                {compatibleVersions.length > 0 ? (
                                    <>
                                        <div className={`px-3 py-2 text-[10px] font-bold ${theme.colors.textMuted} uppercase tracking-wider ${theme.colors.bgSurface} sticky top-0 z-10 backdrop-blur-sm`}>Compatible</div>
                                        {compatibleVersions.map(v => (
                                            <button
                                                key={v.id}
                                                onClick={() => handleSelect(v.versionNumber)}
                                                className={`w-full text-left px-3 py-2 text-xs ${theme.colors.bgSurfaceHover} flex justify-between items-center ${selectedVer === v.versionNumber ? `${theme.colors.accent} font-bold ${theme.colors.accentAlpha}` : theme.colors.textSecondary}`}
                                            >
                                                <span>{v.versionNumber}</span>
                                                {selectedVer === v.versionNumber && <Check className="w-3 h-3" />}
                                            </button>
                                        ))}
                                    </>
                                ) : (
                                    <div className={`px-3 py-4 text-center text-xs ${theme.colors.textMuted} italic`}>No compatible versions found.</div>
                                )}

                                {incompatibleVersions.length > 0 && (
                                    <>
                                        <div className={`px-3 py-2 text-[10px] font-bold ${theme.colors.dangerText} uppercase tracking-wider ${theme.colors.dangerBg} border-t ${theme.colors.borderFaint} mt-1 sticky top-0 z-10`}>Incompatible</div>
                                        {incompatibleVersions.map(v => (
                                            <button
                                                key={v.id}
                                                onClick={() => handleSelect(v.versionNumber)}
                                                className={`w-full text-left px-3 py-2 text-xs hover:${theme.colors.dangerBg} flex justify-between items-center opacity-75 ${selectedVer === v.versionNumber ? `${theme.colors.dangerText} font-bold` : theme.colors.textMuted}`}
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
    useScrollLock(true);
    const [selections, setSelections] = useState<Record<string, string | null>>({});

    const handleRowSelect = (id: string, ver: string | null) => setSelections(prev => ({ ...prev, [id]: ver }));

    const handleConfirm = () => {
        const result: string[] = [];
        previousDeps.forEach(dep => {
            const newVer = selections[dep.projectId];
            if (newVer) {
                result.push(serializeDependencyEntry({
                    projectId: dep.projectId,
                    versionNumber: newVer,
                    isOptional: Boolean(dep.isOptional),
                    isEmbedded: Boolean(dep.isEmbedded)
                }));
            }
        });
        onConfirm(result);
    };

    const validCount = Object.values(selections).filter(v => v !== null).length;

    return (
        <div className={theme.components.modalOverlay}>
            <div className={`fixed top-[50%] left-[50%] translate-x-[-50%] translate-y-[-50%] w-full max-w-2xl max-h-[85dvh] flex flex-col z-[100] bg-white/95 dark:bg-slate-900/95 backdrop-blur-xl border border-slate-200 dark:border-white/10 shadow-xl rounded-3xl overflow-hidden ring-1 ring-black/[0.02] dark:ring-white/[0.02]`} onClick={e => e.stopPropagation()}>
                <div className={`p-4 sm:p-5 flex justify-between items-center shrink-0 bg-slate-50 dark:bg-slate-800/95 border-b border-slate-200 dark:border-white/10`}>
                    <div>
                        <h3 className={`text-xl font-black ${theme.colors.textPrimary} flex items-center gap-2`}>
                            <RefreshCw className={`w-5 h-5 ${theme.colors.accent}`} /> Update Dependencies
                        </h3>
                        <p className={`text-sm ${theme.colors.textMuted} mt-1`}>Select versions for the <strong>{previousDeps.length}</strong> projects from the previous release.</p>
                    </div>
                    <button onClick={onClose} className={`p-2 rounded-full hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-500 transition-colors`}><X className="w-5 h-5" /></button>
                </div>

                <div className={`p-4 sm:p-5 overflow-y-auto custom-scrollbar flex-1`}>
                    {!targetGameVersion && (
                        <div className={`mb-4 p-3 ${theme.colors.warningBg} border ${theme.colors.warningBorder} rounded-lg text-xs ${theme.colors.warningText} flex items-start gap-2`}>
                            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                            <span><strong>Note:</strong> No Game Version selected. Showing all available versions. Select a Game Version first to filter compatible projects.</span>
                        </div>
                    )}
                    <div className={`border ${theme.colors.border} rounded-lg overflow-hidden ${theme.colors.bgSurface}`}>
                        {previousDeps.map(dep => (
                            <DependencyRow key={dep.projectId} dep={dep} targetGameVersion={targetGameVersion} onSelect={handleRowSelect} />
                        ))}
                    </div>
                </div>

                <div className={`p-4 sm:p-5 flex justify-between items-center shrink-0 bg-slate-50 dark:bg-slate-800/95 border-b border-slate-200 dark:border-white/10`}>
                    <div className={`text-sm ${theme.colors.textMuted}`}><strong>{validCount}</strong> selected</div>
                    <div className="flex gap-3">
                        <button onClick={onClose} className={`px-4 py-2 font-bold ${theme.colors.textMuted} ${theme.colors.bgSurfaceHover} rounded-lg`}>Skip</button>
                        <button onClick={handleConfirm} disabled={validCount === 0} className={`px-6 py-2 font-bold rounded-lg shadow-lg flex items-center gap-2 ${theme.components.buttonPrimary} disabled:opacity-50`}>
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
    previousDependencies?: ProjectDependency[];
    currentProjectId?: string;
    isModpack?: boolean;
    disabled?: boolean;
}

export const DependencySelector: React.FC<DependencySelectorProps> = ({ selectedDeps, onChange, targetGameVersion, label = "Dependencies", previousDependencies, currentProjectId, isModpack = false, disabled }) => {
    const [search, setSearch] = useState('');
    const [results, setResults] = useState<Project[]>([]);
    const [loading, setLoading] = useState(false);
    const [selectedModForVersion, setSelectedModForVersion] = useState<Project | null>(null);
    const [loadingProjectVersions, setLoadingProjectVersions] = useState(false);
    const [showIncompatible, setShowIncompatible] = useState(false);
    const [showAlphaBeta, setShowAlphaBeta] = useState(false);
    const [isOptional, setIsOptional] = useState(false);
    const [isEmbedded, setIsEmbedded] = useState(false);
    const [showWizard, setShowWizard] = useState(false);

    const [metaCache, setMetaCache] = useState<Record<string, { title: string; author: string; icon: string }>>({});

    useEffect(() => {
        const fetchMeta = async () => {
            const missingIds = selectedDeps.map(d => d.split(':')[0]).filter(id => !metaCache[id]);
            if (missingIds.length === 0) return;
            const newCache = { ...metaCache };
            await Promise.all(missingIds.map(async (id) => {
                try {
                    const data = await projectClient.getDependencyMeta(id);
                    newCache[id] = { title: data.title, author: data.author, icon: data.icon };
                } catch (e) { newCache[id] = { title: id, author: 'Unknown', icon: '' }; }
            }));
            setMetaCache(newCache);
        };
        fetchMeta();
    }, [selectedDeps.length, metaCache, selectedDeps]);

    useScrollLock(selectedModForVersion !== null);

    useEffect(() => {
        if (selectedModForVersion) {
            const compatible = (selectedModForVersion.versions || []).filter(v => !targetGameVersion || v.gameVersions?.includes(targetGameVersion));
            const hasRelease = compatible.some(v => !v.channel || v.channel === 'RELEASE');
            const hasAny = compatible.length > 0;
            setShowAlphaBeta(hasAny && !hasRelease);
        }
    }, [selectedModForVersion, targetGameVersion]);

    useEffect(() => {
        const timer = setTimeout(async () => {
            if (search.length < 2 || disabled) return;
            setLoading(true);
            try {
                const data = await projectClient.searchProjects(search);
                const filtered = data.filter((m: Project) => m.classification !== 'MODPACK' && m.classification !== 'SAVE' && m.id !== currentProjectId);
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
        const entry = serializeDependencyEntry({
            projectId: selectedModForVersion.id,
            versionNumber,
            isOptional: finalOptional,
            isEmbedded
        });
        onChange([...selectedDeps, entry]);
        setSelectedModForVersion(null);
        setIsOptional(false);
        setIsEmbedded(false);
        setSearch('');
        setResults([]);
    };

    const openVersionPicker = async (mod: Project) => {
        if (disabled) return;
        setLoadingProjectVersions(true);
        try {
            const fullProject = mod.versions ? mod : await projectClient.getProject(mod.id);
            setSelectedModForVersion({ ...fullProject, versions: fullProject.versions || [] });
        } catch (e) {
            setSelectedModForVersion({ ...mod, versions: mod.versions || [] });
        } finally {
            setLoadingProjectVersions(false);
        }
    };

    const removeDep = (index: number) => {
        if(disabled) return;
        const next = [...selectedDeps]; next.splice(index, 1); onChange(next);
    };

    const toggleOptionalExisting = (index: number) => {
        if (isModpack || disabled) return;
        const next = [...selectedDeps];
        const parsed = parseDependencyEntry(next[index]);
        next[index] = serializeDependencyEntry({
            ...parsed,
            isOptional: !parsed.isOptional
        });
        onChange(next);
    };

    const toggleEmbeddedExisting = (index: number) => {
        if (disabled) return;
        const next = [...selectedDeps];
        const parsed = parseDependencyEntry(next[index]);
        next[index] = serializeDependencyEntry({
            ...parsed,
            isEmbedded: !parsed.isEmbedded
        });
        onChange(next);
    };

    const getIconUrl = (path?: string) => { if (!path) return '/assets/favicon.svg'; return path.startsWith('http') ? path : `${BACKEND_URL}${path}`; };

    const availableVersions = useMemo(() => {
        if (!selectedModForVersion?.versions) return [];
        return [...selectedModForVersion.versions].sort((a, b) => compareSemVer(b.versionNumber, a.versionNumber));
    }, [selectedModForVersion]);

    const filteredVersions = availableVersions.filter(v => {
        const versionMatch = !targetGameVersion || v.gameVersions?.includes(targetGameVersion);
        const channelMatch = showAlphaBeta || !v.channel || v.channel === 'RELEASE';
        return (showIncompatible || versionMatch) && channelMatch;
    });

    return (
        <div className={`space-y-4 border ${theme.colors.border} rounded-2xl p-6 ${theme.colors.bgSurface} ${disabled ? 'opacity-70' : ''}`}>
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
                <div className={theme.components.modalOverlay}>
                    <div className={`fixed top-[50%] left-[50%] translate-x-[-50%] translate-y-[-50%] w-full max-w-md max-h-[85dvh] flex flex-col z-[100] bg-white/95 dark:bg-slate-900/95 backdrop-blur-xl border border-slate-200 dark:border-white/10 shadow-xl rounded-3xl overflow-hidden ring-1 ring-black/[0.02] dark:ring-white/[0.02]`} onClick={e => e.stopPropagation()}>
                        <div className={`p-4 sm:p-5 flex justify-between items-start shrink-0 bg-slate-50 dark:bg-slate-800/95 border-b border-slate-200 dark:border-white/10`}>
                            <h3 className={`text-lg font-black text-slate-900 dark:text-white flex items-center gap-2`}>Select Version</h3>
                            <button onClick={() => setSelectedModForVersion(null)} className={`p-2 rounded-full hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-500 transition-colors`}><X className="w-5 h-5" /></button>
                        </div>

                        <div className={`p-4 sm:p-5 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-white/10 flex flex-col gap-3 shrink-0`}>
                            <div className="flex items-center justify-between">
                                <span className={`text-xs font-bold ${theme.colors.textMuted} uppercase tracking-wider`}>Show Alpha/Beta</span>
                                <button onClick={() => setShowAlphaBeta(!showAlphaBeta)} className={`transition-colors ${showAlphaBeta ? theme.colors.accent : theme.colors.textMuted}`}>
                                    {showAlphaBeta ? <ToggleRight className="w-8 h-8" /> : <ToggleLeft className="w-8 h-8" />}
                                </button>
                            </div>
                            {!isModpack && (
                                <div className={`flex items-center gap-2 cursor-pointer transition-colors ${isOptional ? 'text-blue-500' : theme.colors.textMuted}`} onClick={() => setIsOptional(!isOptional)}>
                                    <div className={`w-4 h-4 rounded flex items-center justify-center border transition-all ${isOptional ? 'bg-blue-500 border-blue-500 text-white' : theme.colors.border}`}>
                                        {isOptional && <CheckSquare className="w-3 h-3" />}
                                    </div>
                                    <span className="text-xs font-bold">Optional Dependency</span>
                                </div>
                            )}
                            <div className={`flex items-center gap-2 cursor-pointer transition-colors ${isEmbedded ? 'text-emerald-600 dark:text-emerald-400' : theme.colors.textMuted}`} onClick={() => setIsEmbedded(!isEmbedded)}>
                                <div className={`w-4 h-4 rounded flex items-center justify-center border transition-all ${isEmbedded ? 'bg-emerald-500 border-emerald-500 text-white' : theme.colors.border}`}>
                                    {isEmbedded && <CheckSquare className="w-3 h-3" />}
                                </div>
                                <span className="text-xs font-bold">Embedded Dependency</span>
                            </div>
                        </div>

                        <div className="p-3 sm:p-4 overflow-y-auto custom-scrollbar flex-1 bg-slate-50/50 dark:bg-slate-900/50 space-y-2">
                            {loadingProjectVersions ? (
                                <div className={`p-4 text-center text-xs ${theme.colors.textMuted} flex items-center justify-center gap-2`}>
                                    <Loader2 className="w-4 h-4 animate-spin" /> Loading versions...
                                </div>
                            ) : filteredVersions.length > 0 ? filteredVersions.map(v => {
                                const isCompatible = !targetGameVersion || v.gameVersions?.includes(targetGameVersion);
                                return (
                                    <button key={v.id} onClick={() => confirmVersion(v.versionNumber)} className={`w-full text-left px-4 py-3 flex justify-between items-center rounded-xl transition-all duration-300 shadow-sm border ${!isCompatible ? "bg-red-50 dark:bg-red-900/10 border-red-200 dark:border-red-900/30" : "bg-white dark:bg-slate-800 border-slate-200 dark:border-white/10 hover:border-modtale-accent/40 dark:hover:border-modtale-accent/50"}`}>
                                        <div>
                                            <div className="flex items-center gap-2">
                                                <span className={`font-bold text-sm ${!isCompatible ? theme.colors.dangerText : theme.colors.textPrimary}`}>{v.versionNumber}</span>
                                                {v.channel !== 'RELEASE' && (
                                                    <span className={`text-[9px] font-bold px-1.5 rounded border ${v.channel === 'BETA' ? 'text-blue-500 border-blue-200 bg-blue-50 dark:bg-blue-900/20 dark:border-blue-800' : 'text-orange-500 border-orange-200 bg-orange-50 dark:bg-orange-900/20 dark:border-orange-800'}`}>
                                                        {v.channel}
                                                    </span>
                                                )}
                                            </div>
                                            <div className={`text-xs ${theme.colors.textMuted}`}>For {v.gameVersions?.join(', ')}</div>
                                        </div>
                                        {isCompatible ? <Plus className={`w-4 h-4 ${theme.colors.accent}`} /> : <AlertTriangle className="w-4 h-4 text-red-500" />}
                                    </button>
                                )
                            }) : (
                                <div className={`p-4 text-center text-xs ${theme.colors.textMuted} italic`}>No compatible versions found with current filters.</div>
                            )}
                        </div>
                        {targetGameVersion && (
                            <div className={`p-4 bg-slate-50 dark:bg-slate-800/95 border-t border-slate-200 dark:border-white/10 text-center shrink-0`}>
                                <button onClick={() => { setShowIncompatible(!showIncompatible); }} className={`text-xs font-bold ${theme.colors.accent} hover:underline`}>{showIncompatible ? 'Hide' : 'Show'} incompatible versions</button>
                            </div>
                        )}
                    </div>
                </div>
            )}

            <div className="flex justify-between items-center">
                <h3 className={`font-bold ${theme.colors.textPrimary} flex items-center gap-2 text-sm uppercase tracking-wide`}><Search className="w-4 h-4" /> {label}</h3>
            </div>

            {previousDependencies && previousDependencies.length > 0 && selectedDeps.length === 0 && !disabled && (
                <div className="bg-blue-50 dark:bg-blue-900/10 border border-blue-100 dark:border-blue-900/20 p-4 rounded-xl flex items-center justify-between animate-in fade-in">
                    <div className="flex items-center gap-3">
                        <RefreshCw className="w-5 h-5 text-blue-500" />
                        <div>
                            <h4 className="text-sm font-bold text-blue-900 dark:text-blue-100">Updates Available</h4>
                            <p className="text-xs text-blue-700 dark:text-blue-300">Found {previousDependencies.length} projects from the previous release.</p>
                        </div>
                    </div>
                    <button onClick={() => setShowWizard(true)} className="text-xs font-bold bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg transition-colors flex items-center gap-1 shadow-sm">
                        Import & Update <ChevronRight className="w-3 h-3" />
                    </button>
                </div>
            )}

            <div className="relative">
                <input type="text" disabled={disabled} value={search} onChange={e => setSearch(e.target.value)} className={`w-full ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl pl-10 pr-4 py-3 text-sm focus:ring-2 focus:ring-modtale-accent outline-none shadow-sm transition-all ${theme.colors.textPrimary} ${disabled ? `cursor-not-allowed ${theme.colors.bgSurfaceAlt}` : ''}`} placeholder="Search for projects..." />
                <Search className={`absolute left-3.5 top-3.5 w-4 h-4 ${theme.colors.textMuted}`} />
                {loading && <div className="absolute right-4 top-3.5"><Loader2 className={`w-4 h-4 animate-spin ${theme.colors.accent}`} /></div>}
            </div>

            {results.length > 0 && !disabled && (
                <div className={`max-h-56 overflow-y-auto custom-scrollbar ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl shadow-lg divide-y ${theme.colors.borderFaint}`}>
                    {results.map(mod => (
                        <button key={mod.id} onClick={(e) => { e.preventDefault(); void openVersionPicker(mod); }} className={`w-full text-left px-4 py-3 ${theme.colors.bgSurfaceHover} flex justify-between items-center text-sm transition-colors group`}>
                            <div className="flex items-center gap-3">
                                <img src={getIconUrl(mod.imageUrl)} className="w-8 h-8 rounded-md bg-slate-200 object-cover" alt="" onError={(e) => e.currentTarget.src='/assets/favicon.svg'} />
                                <div>
                                    <div className={`font-bold ${theme.colors.textPrimary} group-hover:${theme.colors.accent}`}>{mod.title}</div>
                                    <div className={`text-xs ${theme.colors.textMuted}`}>by {mod.author}</div>
                                </div>
                            </div>
                            <Plus className={`w-5 h-5 text-slate-300 group-hover:${theme.colors.accent}`} />
                        </button>
                    ))}
                </div>
            )}

            <div className="mt-6">
                <div className="flex justify-between items-center mb-3">
                    <h4 className={`text-xs font-bold uppercase ${theme.colors.textMuted}`}>Selected ({selectedDeps.length})</h4>
                </div>
                {selectedDeps.length === 0 ? (
                    <div className={`text-center p-8 border-2 border-dashed ${theme.colors.border} rounded-xl ${theme.colors.textMuted} text-sm italic`}>
                        No dependencies added.
                    </div>
                ) : (
                    <div className="grid grid-cols-1 gap-2">
                        {selectedDeps.map((entry, idx) => {
                            const { projectId: id, versionNumber: ver, isOptional: isOpt, isEmbedded: embedded } = parseDependencyEntry(entry);
                            const meta = metaCache[id];
                            return (
                                <div key={idx} className={`flex items-center justify-between ${theme.colors.bgBase} p-3 rounded-xl border ${theme.colors.border} text-sm shadow-sm group`}>
                                    <div className="flex items-center gap-3 overflow-hidden">
                                        <div className={`p-1 flex-shrink-0 rounded-lg ${isOpt ? `${theme.colors.bgSurfaceAlt} ${theme.colors.textMuted}` : 'bg-amber-100 text-amber-600 dark:bg-amber-900/20'}`}>
                                            {isOpt ? <FileText className="w-4 h-4" /> : <ShieldCheck className="w-4 h-4" />}
                                        </div>
                                        <img src={getIconUrl(meta?.icon)} alt="" className={`w-8 h-8 rounded ${theme.colors.bgSurfaceAlt} object-cover flex-shrink-0`} onError={(e) => e.currentTarget.src='/assets/favicon.svg'} />
                                        <div className="min-w-0">
                                            <div className={`font-bold ${theme.colors.textPrimary} truncate`}>{meta?.title || id}</div>
                                            <div className={`text-xs ${theme.colors.textMuted} flex items-center gap-1.5`}>
                                                <span className="truncate max-w-[100px]">by {meta?.author || '...'}</span>
                                                <span className="w-1 h-1 rounded-full bg-slate-300 dark:bg-white/20"></span>
                                                <span className={`font-mono ${theme.colors.bgSurfaceAlt} px-1.5 py-0.5 rounded`}>v{ver}</span>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-2 flex-shrink-0 ml-2">
                                        {!isModpack && (
                                            <button type="button" disabled={disabled} onClick={() => toggleOptionalExisting(idx)} className={`text-xs font-bold px-3 py-1.5 rounded-lg border transition-all ${isOpt ? `border-slate-200 text-slate-400 hover:text-slate-600 hover:bg-slate-50` : 'border-amber-200 bg-amber-50 text-amber-700 hover:bg-amber-100 dark:bg-amber-900/20 dark:border-amber-900/30 dark:text-amber-400'} ${disabled ? 'cursor-not-allowed opacity-70' : ''}`}>
                                                {isOpt ? 'Optional' : 'Required'}
                                            </button>
                                        )}
                                        <button type="button" disabled={disabled} onClick={() => toggleEmbeddedExisting(idx)} className={`text-xs font-bold px-3 py-1.5 rounded-lg border transition-all ${embedded ? 'border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-100 dark:bg-emerald-900/20 dark:border-emerald-900/30 dark:text-emerald-400' : `border-slate-200 ${theme.colors.textMuted} hover:${theme.colors.textPrimary} hover:bg-slate-50`} ${disabled ? 'cursor-not-allowed opacity-70' : ''}`}>
                                            {embedded ? 'Embedded' : 'Standalone'}
                                        </button>
                                        <button type="button" disabled={disabled} onClick={() => removeDep(idx)} className={`${theme.colors.textMuted} p-2 rounded-lg transition-colors ${disabled ? 'cursor-not-allowed opacity-50' : `hover:${theme.colors.dangerText} hover:${theme.colors.dangerBg}`}`}><X className="w-4 h-4" /></button>
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
