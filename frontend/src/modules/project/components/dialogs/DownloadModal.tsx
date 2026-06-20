import React, { useState, useEffect, useMemo, useRef } from 'react';
import { Download, X, ChevronDown, FileText, AlertCircle, ChevronRight, Check } from 'lucide-react';
import { theme } from '@/styles/theme';
import { buildVersionGroups, compareSemVer, formatTimeAgo, type VersionGroup } from '@/utils/modHelpers';
import { useScrollLock } from '@/hooks/useScrollLock';
import { getExternalDependencies } from '@/modules/project/utils/dependencyEntries';
import type { ProjectDependency } from '@/types';
import { ModalPortal } from '@/components/ui/ModalPortal';

interface VersionMultiSelectDropdownProps {
    selectedVersions: string[];
    versions: string[];
    groups: VersionGroup[];
    onChange: (versions: string[]) => void;
}

const VersionMultiSelectDropdown: React.FC<VersionMultiSelectDropdownProps> = ({
    selectedVersions,
    versions,
    groups,
    onChange
}) => {
    const [isOpen, setIsOpen] = useState(false);
    const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
    const containerRef = useRef<HTMLDivElement>(null);
    const selectedSet = useMemo(() => new Set(selectedVersions), [selectedVersions]);
    const selectedGroup = groups.find(group =>
        group.grouped &&
        group.versions.length === selectedVersions.length &&
        group.versions.every(version => selectedSet.has(version))
    );
    const displayValue = selectedVersions.length === 0
        ? 'Any'
        : selectedGroup
            ? selectedGroup.label
            : selectedVersions.length === 1
                ? selectedVersions[0]
                : `${selectedVersions.length} versions`;

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
                setIsOpen(false);
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const commitSelection = (nextVersions: string[]) => {
        const nextSet = new Set(nextVersions.filter(Boolean));
        onChange(versions.filter(version => nextSet.has(version)));
    };

    const toggleVersion = (version: string) => {
        commitSelection(selectedSet.has(version)
            ? selectedVersions.filter(selected => selected !== version)
            : [...selectedVersions, version]
        );
    };

    const toggleGroupSelection = (groupVersions: string[]) => {
        const hasEntireGroup = groupVersions.every(version => selectedSet.has(version));
        commitSelection(hasEntireGroup
            ? selectedVersions.filter(version => !groupVersions.includes(version))
            : [...selectedVersions, ...groupVersions]
        );
    };

    const toggleGroupExpanded = (label: string) => {
        setExpandedGroups(prev => {
            const next = new Set(prev);
            if (next.has(label)) next.delete(label);
            else next.add(label);
            return next;
        });
    };

    return (
        <div className="relative" ref={containerRef}>
            <button
                type="button"
                aria-label="Game version filter"
                onClick={(e) => {
                    e.stopPropagation();
                    setIsOpen(prev => !prev);
                }}
                className="w-full flex items-center justify-between gap-3 p-3 rounded-xl font-bold text-slate-900 dark:text-white text-xs sm:text-sm cursor-pointer bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 shadow-sm hover:border-modtale-accent/40 dark:hover:border-modtale-accent/50 transition-all duration-300"
            >
                <span className="min-w-0 truncate text-left">{displayValue}</span>
                <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform shrink-0 ${isOpen ? 'rotate-180' : ''}`} />
            </button>
            {isOpen && (
                <div className="absolute left-0 right-0 top-full mt-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-[120] py-1 overflow-hidden">
                    <button
                        type="button"
                        onClick={(e) => {
                            e.stopPropagation();
                            onChange([]);
                        }}
                        className={`w-full text-left px-4 py-2.5 text-xs sm:text-sm font-bold transition-colors flex justify-between items-center ${selectedVersions.length === 0 ? 'bg-modtale-accent text-white border-transparent shadow-sm' : 'text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'}`}
                    >
                        Any
                        {selectedVersions.length === 0 && <Check className="w-4 h-4" />}
                    </button>
                    <div className="max-h-56 overflow-y-auto py-1">
                        {groups.length > 0 ? groups.map(group => {
                            if (!group.grouped) {
                                const version = group.versions[0];
                                if (!version) return null;
                                const isSelected = selectedSet.has(version);
                                return (
                                    <button
                                        type="button"
                                        key={version}
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            toggleVersion(version);
                                        }}
                                        className={`w-full text-left px-4 py-2.5 text-xs sm:text-sm font-bold transition-colors flex justify-between items-center ${isSelected ? 'bg-modtale-accent text-white border-transparent shadow-sm' : 'text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'}`}
                                    >
                                        {version}
                                        {isSelected && <Check className="w-4 h-4" />}
                                    </button>
                                );
                            }

                            const selectedCount = group.versions.filter(version => selectedSet.has(version)).length;
                            const isSelected = selectedCount === group.versions.length;
                            const isPartiallySelected = selectedCount > 0 && !isSelected;
                            const isExpanded = expandedGroups.has(group.label);
                            return (
                                <div key={group.label}>
                                    <div className={`flex items-center transition-colors ${isSelected ? 'bg-modtale-accent text-white shadow-sm' : isPartiallySelected ? 'bg-modtale-accent/10 text-modtale-accent' : 'text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'}`}>
                                        <button
                                            type="button"
                                            aria-label={`Select all ${group.label} versions`}
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                toggleGroupSelection(group.versions);
                                            }}
                                            className="min-w-0 flex-1 text-left px-4 py-2.5 text-xs sm:text-sm font-bold flex items-center gap-2"
                                        >
                                            <span className="truncate">{group.label}</span>
                                        </button>
                                        <button
                                            type="button"
                                            aria-label={`${isExpanded ? 'Collapse' : 'Expand'} ${group.label} versions`}
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                toggleGroupExpanded(group.label);
                                            }}
                                            className="shrink-0 px-3 py-2.5"
                                        >
                                            <ChevronRight className={`w-3.5 h-3.5 transition-transform ${isExpanded ? 'rotate-90' : ''}`} />
                                        </button>
                                        {isSelected && <Check className="w-4 h-4 mr-4 shrink-0" />}
                                    </div>
                                    {isExpanded && group.versions.map(version => {
                                        const versionSelected = selectedSet.has(version);
                                        return (
                                            <button
                                                type="button"
                                                key={version}
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    toggleVersion(version);
                                                }}
                                                className={`w-full text-left pl-8 pr-4 py-2.5 text-xs sm:text-sm font-bold transition-colors flex justify-between items-center ${versionSelected ? 'bg-modtale-accent text-white border-transparent shadow-sm' : 'text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'}`}
                                            >
                                                {version}
                                                {versionSelected && <Check className="w-4 h-4" />}
                                            </button>
                                        );
                                    })}
                                </div>
                            );
                        }) : (
                            <div className="px-4 py-3 text-sm text-center text-slate-500 dark:text-slate-400">No versions found</div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};

interface DownloadModalProps {
    show: boolean;
    onClose: () => void;
    versionsByGame: Record<string, any[]>;
    preReleaseGameVersions?: string[];
    orderedGameVersions?: string[];
    onDownload: (url: string, number: string, gameVersion: string, deps: any[], channel: string) => void;
    showExperimental: boolean;
    onToggleExperimental: () => void;
    onViewHistory: () => void;
    isModpack?: boolean;
    isInline?: boolean;
    containerRef?: React.Ref<HTMLDivElement>;
}

export const DownloadModal: React.FC<DownloadModalProps> = ({
                                                                show, onClose, versionsByGame, preReleaseGameVersions = [], orderedGameVersions = [], onDownload, showExperimental, onToggleExperimental, onViewHistory, isModpack = false, isInline = false, containerRef
                                                            }) => {
    useScrollLock(show && !isInline);
    const [selectedGameVersions, setSelectedGameVersions] = useState<string[]>([]);
    const [isListExpanded, setIsListExpanded] = useState(false);
    const [showPreReleaseGameVersions, setShowPreReleaseGameVersions] = useState(false);
    const wasOpenRef = useRef(false);
    const preReleaseGameVersionSet = useMemo(() => new Set(preReleaseGameVersions), [preReleaseGameVersions]);

    const hasPreReleaseGameVersionEntries = useMemo(() => {
        return Object.entries(versionsByGame).some(([version, builds]) => preReleaseGameVersionSet.has(version) && Array.isArray(builds) && builds.length > 0);
    }, [versionsByGame, preReleaseGameVersionSet]);

    const hasReleaseGameVersionEntries = useMemo(() => {
        return Object.entries(versionsByGame).some(([version, builds]) => !preReleaseGameVersionSet.has(version) && Array.isArray(builds) && builds.length > 0);
    }, [versionsByGame, preReleaseGameVersionSet]);

    const forceShowPreReleaseGameVersions = hasPreReleaseGameVersionEntries && !hasReleaseGameVersionEntries;

    const allVersions = useMemo(() => {
        return Object.values(versionsByGame).flat();
    }, [versionsByGame]);

    const experimentalBuilds = useMemo(() => {
        return allVersions.filter(v => v.channel === 'ALPHA' || v.channel === 'BETA');
    }, [allVersions]);

    const onlyExperimentalArePreRelease = useMemo(() => {
        const hasExperimentalInPreRelease = Object.entries(versionsByGame).some(([gv, builds]) =>
            preReleaseGameVersionSet.has(gv) && Array.isArray(builds) && builds.some((v: any) => v.channel === 'ALPHA' || v.channel === 'BETA')
        );
        const hasExperimentalInRelease = Object.entries(versionsByGame).some(([gv, builds]) =>
            !preReleaseGameVersionSet.has(gv) && Array.isArray(builds) && builds.some((v: any) => v.channel === 'ALPHA' || v.channel === 'BETA')
        );
        return hasExperimentalInPreRelease && !hasExperimentalInRelease;
    }, [versionsByGame, preReleaseGameVersionSet]);

    const onlyPreReleaseAreExperimental = useMemo(() => {
        const latestReleaseVer = orderedGameVersions.find(v => !preReleaseGameVersionSet.has(v));
        const latestReleaseIdx = latestReleaseVer ? orderedGameVersions.indexOf(latestReleaseVer) : orderedGameVersions.length;
        const newerPreReleaseVersions = orderedGameVersions.slice(0, latestReleaseIdx).filter(v => preReleaseGameVersionSet.has(v));

        let hasPreReleaseBuilds = false;
        let hasReleaseInPreRelease = false;

        for (const [gv, builds] of Object.entries(versionsByGame)) {
            if (newerPreReleaseVersions.includes(gv) && Array.isArray(builds)) {
                if (builds.length > 0) {
                    hasPreReleaseBuilds = true;
                }
                if (builds.some((v: any) => !v.channel || v.channel === 'RELEASE')) {
                    hasReleaseInPreRelease = true;
                }
            }
        }
        return hasPreReleaseBuilds && !hasReleaseInPreRelease;
    }, [versionsByGame, orderedGameVersions, preReleaseGameVersionSet]);

    const effectiveShowPreReleaseGameVersions = showPreReleaseGameVersions || forceShowPreReleaseGameVersions || (showExperimental && onlyExperimentalArePreRelease);

    const gameVersions = useMemo(() => {
        const available = new Set(Object.keys(versionsByGame));
        const orderedAvailable = orderedGameVersions.filter(version => available.has(version));
        const orderedSet = new Set(orderedAvailable);
        const unordered = Object.keys(versionsByGame)
            .filter(version => !orderedSet.has(version))
            .sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
        const all = [...orderedAvailable, ...unordered];
        if (effectiveShowPreReleaseGameVersions) return all;
        return all.filter(version => !preReleaseGameVersionSet.has(version));
    }, [versionsByGame, effectiveShowPreReleaseGameVersions, preReleaseGameVersionSet, orderedGameVersions]);

    const gameVersionGroups = useMemo(() => buildVersionGroups(gameVersions), [gameVersions]);

    const preferredGameVersions = useMemo(() => gameVersionGroups[0]?.versions || [], [gameVersionGroups]);

    useEffect(() => {
        const isOpening = show && !wasOpenRef.current;

        if (isOpening && preferredGameVersions.length > 0) {
            setSelectedGameVersions(preferredGameVersions);
            setIsListExpanded(false);
        } else if (show) {
            setSelectedGameVersions((current) => {
                if (current.length === 0) return current;
                const validSelections = current.filter(version => gameVersions.includes(version));
                if (validSelections.length === current.length) return current;
                return validSelections.length > 0
                    ? validSelections
                    : preferredGameVersions;
            });
        }

        wasOpenRef.current = show;
    }, [show, gameVersions, preferredGameVersions]);

    const activeSelectedGameVersions = useMemo(() => {
        return selectedGameVersions.length > 0 ? selectedGameVersions : gameVersions;
    }, [selectedGameVersions, gameVersions]);

    const selectedVersionEntries = useMemo(() => {
        const entries = new Map<string, { version: any, gameVersion: string }>();
        for (const gameVersion of activeSelectedGameVersions) {
            const builds = versionsByGame[gameVersion] || [];
            for (const version of builds) {
                const key = version?.id || `${version?.versionNumber || 'unknown'}-${version?.fileUrl || ''}-${version?.releaseDate || ''}`;
                if (!entries.has(key)) {
                    entries.set(key, { version, gameVersion });
                }
            }
        }
        return Array.from(entries.values());
    }, [activeSelectedGameVersions, versionsByGame]);

    const hasReleaseForSelectedGameVersion = selectedVersionEntries.some(({ version }) => !version.channel || version.channel === 'RELEASE');
    const hasExperimentalForSelectedGameVersion = selectedVersionEntries.some(({ version }) => version.channel === 'ALPHA' || version.channel === 'BETA');
    const forceShowExperimental = hasExperimentalForSelectedGameVersion && !hasReleaseForSelectedGameVersion;

    const effectiveShowExperimental = showExperimental || forceShowExperimental || (effectiveShowPreReleaseGameVersions && onlyPreReleaseAreExperimental);

    const showAlphaBetaToggle = useMemo(() => {
        const isLockedOn = forceShowExperimental || (effectiveShowPreReleaseGameVersions && onlyPreReleaseAreExperimental);
        const isLockedOff = experimentalBuilds.length === 0;
        return !(isLockedOn || isLockedOff);
    }, [forceShowExperimental, effectiveShowPreReleaseGameVersions, onlyPreReleaseAreExperimental, experimentalBuilds]);

    const showPreReleaseToggle = useMemo(() => {
        const isLockedOn = forceShowPreReleaseGameVersions || (effectiveShowExperimental && onlyExperimentalArePreRelease);
        const isLockedOff = !hasPreReleaseGameVersionEntries;
        return !(isLockedOn || isLockedOff);
    }, [forceShowPreReleaseGameVersions, effectiveShowExperimental, onlyExperimentalArePreRelease, hasPreReleaseGameVersionEntries]);

    useEffect(() => {
        if (!show || preferredGameVersions.length === 0) return;
        setIsListExpanded(false);
    }, [effectiveShowPreReleaseGameVersions, effectiveShowExperimental, show, preferredGameVersions]);

    useEffect(() => {
        if (forceShowPreReleaseGameVersions && !showPreReleaseGameVersions) {
            setShowPreReleaseGameVersions(true);
        }
    }, [forceShowPreReleaseGameVersions, showPreReleaseGameVersions]);

    if (!show) return null;

    const visibleVersionEntries = selectedVersionEntries.filter(({ version }) => effectiveShowExperimental || (!version.channel || version.channel === 'RELEASE'));

    const sortedVersionEntries = [...visibleVersionEntries].sort((a, b) => {
        const dateDiff = new Date(b.version.releaseDate).getTime() - new Date(a.version.releaseDate).getTime();
        if (dateDiff !== 0) return dateDiff;
        return compareSemVer(b.version.versionNumber, a.version.versionNumber);
    });

    const latestEntry = sortedVersionEntries[0];
    const latestVer = latestEntry?.version;
    const latestGameVersion = latestEntry?.gameVersion || '';

    const getVersionBadgeColor = (channel: string) => {
        switch(channel) {
            case 'BETA': return 'bg-purple-100 dark:bg-purple-500/20 text-purple-700 dark:text-purple-200 border-purple-200 dark:border-purple-500/30';
            case 'ALPHA': return 'bg-red-100 dark:bg-red-500/20 text-red-700 dark:text-red-100 border-red-200 dark:border-red-500/30';
            default: return `${theme.colors.bgSurfaceAlt} ${theme.colors.border} ${theme.colors.textPrimary}`;
        }
    };

    const themeClass = latestVer?.channel === 'ALPHA'
        ? 'bg-red-600 hover:bg-red-500 shadow-red-500/20 text-white'
        : latestVer?.channel === 'BETA'
            ? 'bg-purple-600 hover:bg-purple-500 shadow-purple-500/20 text-white'
            : 'bg-modtale-accent hover:bg-blue-500 shadow-blue-500/20 text-white';

    const selectedSet = new Set(selectedGameVersions);
    const selectedGroup = gameVersionGroups.find(group =>
        group.grouped &&
        group.versions.length === selectedGameVersions.length &&
        group.versions.every(version => selectedSet.has(version))
    );
    const selectedGameVersionLabel = selectedGameVersions.length === 0
        ? 'any version'
        : selectedGroup
            ? selectedGroup.label
            : selectedGameVersions.length === 1
                ? selectedGameVersions[0]
                : `${selectedGameVersions.length} versions`;

    const shouldShowEntryGameVersion = activeSelectedGameVersions.length > 1;

    const otherCompatibleVersions = (ver: any, gameVersion: string) => {
        const versions = Array.isArray(ver?.gameVersions) ? ver.gameVersions : [];
        return versions.filter((gv: string) => gv !== gameVersion);
    };

    const externalDependenciesFor = (ver: any): ProjectDependency[] => (
        isModpack ? getExternalDependencies(ver?.dependencies) : []
    );

    const formatExternalDependencyNames = (dependencies: ProjectDependency[]) => {
        const names = dependencies.map(dep => dep.projectTitle || dep.externalId || dep.projectId).filter(Boolean);
        const visibleNames = names.slice(0, 3);
        const remainingCount = names.length - visibleNames.length;
        return `${visibleNames.join(', ')}${remainingCount > 0 ? `, +${remainingCount} more` : ''}`;
    };

    const renderExternalDependencyNotice = (ver: any) => {
        const externalDependencies = externalDependenciesFor(ver);
        if (externalDependencies.length === 0) return null;

        return (
            <div className="mb-6 rounded-xl border border-amber-200 bg-amber-50 p-3 text-amber-900 shadow-sm dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-100">
                <div className="flex items-start gap-2">
                    <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                    <div>
                        <p className="text-sm font-black">This modpack uses external mods</p>
                        <p className="mt-1 text-xs font-medium leading-relaxed">
                            {formatExternalDependencyNames(externalDependencies)} {externalDependencies.length === 1 ? 'comes' : 'come'} from outside Modtale. Check the linked source pages if the download or install flow asks for them separately.
                        </p>
                    </div>
                </div>
            </div>
        );
    };

    const content = (
        <div ref={containerRef} className={`${isInline ? 'w-full overflow-hidden relative flex flex-col transform transition-transform duration-500' : 'fixed top-[50%] left-[50%] translate-x-[-50%] translate-y-[-50%] w-full max-w-2xl max-h-[90dvh] flex flex-col z-[100]'} bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 shadow-2xl rounded-2xl overflow-hidden`} onClick={e => e.stopPropagation()}>
            <div className={`p-6 flex justify-between items-center shrink-0 border-b border-slate-100 dark:border-white/5 bg-slate-50 dark:bg-slate-800/50`}>
                <div>
                    <h3 className={`text-xl font-black ${theme.colors.textPrimary} flex items-center gap-2`}><Download className={`w-5 h-5 ${theme.colors.accent}`} /> Download</h3>
                    {showPreReleaseToggle && (
                        <div className="mt-1 flex items-center gap-2 cursor-pointer group" onClick={() => setShowPreReleaseGameVersions(!showPreReleaseGameVersions)}>
                            <div className={`w-8 h-4 rounded-full relative transition-colors shadow-inner ${effectiveShowPreReleaseGameVersions ? 'bg-modtale-accent' : 'bg-slate-200 dark:bg-slate-800'}`}>
                                <div className={`absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform shadow-sm ${effectiveShowPreReleaseGameVersions ? 'translate-x-4' : ''}`} />
                            </div>
                            <span className={`text-[10px] font-bold ${theme.colors.textMuted} uppercase group-hover:${theme.colors.textPrimary} transition-colors`}>Show Pre-Release Game Versions</span>
                        </div>
                    )}
                    {showAlphaBetaToggle && (
                        <div className="mt-1 flex items-center gap-2 cursor-pointer group" onClick={onToggleExperimental}>
                            <div className={`w-8 h-4 rounded-full relative transition-colors shadow-inner ${effectiveShowExperimental ? 'bg-modtale-accent' : 'bg-slate-200 dark:bg-slate-800'}`}>
                                <div className={`absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform shadow-sm ${effectiveShowExperimental ? 'translate-x-4' : ''}`} />
                            </div>
                            <span className={`text-[10px] font-bold ${theme.colors.textMuted} uppercase group-hover:${theme.colors.textPrimary} transition-colors`}>Show Beta/Alpha</span>
                        </div>
                    )}
                </div>
                {!isInline && (
                    <button type="button" onClick={onClose} className={`p-2 rounded-full hover:bg-slate-100 dark:hover:bg-white/10 text-slate-500 transition-colors`}><X className="w-5 h-5" /></button>
                )}
            </div>

            <div className={`p-6 overflow-visible relative flex-1 flex flex-col justify-start`}>
                <div className="mb-6">
                    <label className={`block text-xs font-bold ${theme.colors.textSecondary} uppercase mb-2 tracking-wider`}>Game Versions</label>
                    <VersionMultiSelectDropdown
                        selectedVersions={selectedGameVersions}
                        versions={gameVersions}
                        groups={gameVersionGroups}
                        onChange={setSelectedGameVersions}
                    />
                </div>

                {latestVer ? (
                    <>
                        <button
                            type="button"
                            onClick={() => onDownload(latestVer.fileUrl, latestVer.versionNumber, latestGameVersion, latestVer.dependencies, latestVer.channel)}
                            className={`w-full p-5 rounded-2xl shadow-lg flex flex-col items-center justify-center gap-1.5 transition-all active:scale-95 mb-6 group relative overflow-hidden ${themeClass}`}
                        >
                            <div className="font-black text-xl flex items-center gap-2 group-hover:scale-105 transition-transform z-10"><Download className="w-6 h-6" /> Download Latest</div>
                            <div className={`text-xs font-bold font-mono px-3 py-1 rounded-full border flex items-center gap-2 z-10 ${getVersionBadgeColor(latestVer.channel || 'RELEASE')}`}>
                                v{latestVer.versionNumber}
                                {latestVer.channel !== 'RELEASE' && <span className="uppercase tracking-wider opacity-90">{latestVer.channel}</span>}
                            </div>
                            {shouldShowEntryGameVersion && (
                                <div className="text-[11px] font-semibold text-blue-50/95 z-10">
                                    For {latestGameVersion}
                                </div>
                            )}
                            {otherCompatibleVersions(latestVer, latestGameVersion).length > 0 && (
                                <div className="text-[11px] font-semibold text-blue-50/95 z-10">
                                    Also supports: {otherCompatibleVersions(latestVer, latestGameVersion).join(', ')}
                                </div>
                            )}
                        </button>
                        {renderExternalDependencyNotice(latestVer)}

                        <div className="relative mb-6">
                            <div className="absolute inset-0 flex items-center"><div className={`w-full border-t ${theme.colors.borderFaint}`}></div></div>
                            <div className="relative flex justify-center"><span className={`bg-white dark:bg-slate-900 px-3 text-[10px] font-bold ${theme.colors.textMuted} uppercase tracking-widest`}>Other Versions</span></div>
                        </div>

                        <button
                            type="button"
                            onClick={() => setIsListExpanded(!isListExpanded)}
                            className={`w-full flex items-center justify-between p-3 rounded-xl border ${theme.colors.border} bg-white dark:bg-slate-900 ${theme.colors.bgSurfaceHover} transition-colors group shadow-sm`}
                        >
                            <span className={`font-bold ${theme.colors.textPrimary} text-sm`}>View all files for {selectedGameVersionLabel}</span>
                            <ChevronDown className={`w-4 h-4 ${theme.colors.textMuted} transition-transform ${isListExpanded ? 'rotate-180' : ''}`} />
                        </button>

                        {isListExpanded && (
                            <div className="mt-2 space-y-2 animate-in fade-in slide-in-from-top-2 duration-200">
                                {sortedVersionEntries.map(({ version: ver, gameVersion }) => (
                                    <div key={ver.id || `${ver.versionNumber}-${ver.fileUrl}-${gameVersion}`} className={`flex items-center justify-between p-3 rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 shadow-sm hover:border-slate-300 dark:hover:border-white/20 transition-colors`}>
                                        <div className="flex items-center gap-3">
                                            <div className={`w-10 h-10 rounded-lg ${theme.colors.bgSurfaceAlt} border ${theme.colors.border} flex items-center justify-center ${theme.colors.textMuted}`}><FileText className="w-5 h-5" /></div>
                                            <div>
                                                <div className={`font-bold ${theme.colors.textPrimary} text-sm flex items-center gap-2`}>
                                                    v{ver.versionNumber}
                                                    {ver.channel !== 'RELEASE' && <span className={`text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded border ${getVersionBadgeColor(ver.channel)}`}>{ver.channel}</span>}
                                                </div>
                                                <div className={`text-xs ${theme.colors.textMuted}`}>
                                                    {formatTimeAgo(ver.releaseDate)}{shouldShowEntryGameVersion ? ` · ${gameVersion}` : ''}
                                                </div>
                                                {otherCompatibleVersions(ver, gameVersion).length > 0 && (
                                                    <div className={`text-[11px] ${theme.colors.textSecondary}`}>
                                                        Also supports: {otherCompatibleVersions(ver, gameVersion).join(', ')}
                                                    </div>
                                                )}
                                                {externalDependenciesFor(ver).length > 0 && (
                                                    <div className="mt-1 inline-flex items-center gap-1 rounded-md border border-amber-200 bg-amber-50 px-2 py-0.5 text-[10px] font-black uppercase tracking-wide text-amber-700 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-200">
                                                        <AlertCircle className="h-3 w-3" aria-hidden="true" />
                                                        External mods
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                        <button
                                            type="button"
                                            onClick={() => onDownload(ver.fileUrl, ver.versionNumber, gameVersion, ver.dependencies, ver.channel)}
                                            className={`p-2 rounded-lg bg-slate-100 dark:bg-white/5 text-slate-500 dark:text-slate-400 hover:bg-modtale-accent hover:text-white transition-colors`}
                                            aria-label={`Download version ${ver.versionNumber}`}
                                        >
                                            <Download className="w-4 h-4" />
                                        </button>
                                    </div>
                                ))}
                            </div>
                        )}
                    </>
                ) : (
                    <div className="flex-1 flex flex-col items-center justify-center text-slate-500 min-h-[160px]">
                        <AlertCircle className="w-6 h-6 sm:w-8 sm:h-8 opacity-50 mb-2" aria-hidden="true" />
                        <p className="font-medium text-xs sm:text-sm text-center">No compatible versions.</p>
                        {!effectiveShowExperimental && selectedVersionEntries.length > 0 && (
                            <button onClick={onToggleExperimental} className="mt-2 text-[10px] sm:text-[11px] font-bold text-modtale-accent hover:underline">
                                Show experimental
                            </button>
                        )}
                    </div>
                )}
            </div>

            <div className={`p-4 border-t border-slate-100 dark:border-white/5 shrink-0 z-10 flex items-center justify-center bg-slate-50 dark:bg-[#0B1120]`}>
                <button type="button" onClick={onViewHistory} className={`text-xs ${theme.colors.textMuted} hover:${theme.colors.accent} font-bold uppercase tracking-wider flex items-center justify-center gap-1 transition-colors w-full`}>
                    View Full Changelog <ChevronRight className="w-3 h-3" />
                </button>
            </div>
        </div>
    );

    if (isInline) return content;

    return (
        <ModalPortal>
        <div className={theme.components.modalOverlay} onClick={onClose}>
            {content}
        </div>
        </ModalPortal>
    );
};
