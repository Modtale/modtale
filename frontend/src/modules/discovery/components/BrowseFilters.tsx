import React, { useState, useEffect, useRef, useMemo } from 'react';
import { createPortal } from 'react-dom';
import {
    Filter,
    Tag,
    ChevronDown,
    ChevronRight,
    Check,
    X,
    Search,
    Heart,
    RotateCcw,
    Calendar as CalendarIcon,
    Download,
    LayoutGrid,
    List,
    AlignJustify
} from 'lucide-react';
import { GLOBAL_TAGS } from '@/data/categories';
import { projectClient } from '@/modules/project/api/projectClient';
import { compareSemVer } from '@/utils/modHelpers';
import { DropdownSelect } from '@/components/ui/DropdownSelect';
import { CalendarWidget } from '@/components/ui/CalendarWidget';
import { SortDropdown } from './SortDropdown';
import { BROWSE_ITEMS_PER_PAGE_OPTIONS, type BrowseViewStyle } from '../preferences';

const parseSelectedVersions = (value: string) => {
    if (!value || value === 'Any') return [];
    return value.split(',').map(v => v.trim()).filter(v => v && v !== 'Any');
};

const getVersionRangeLabel = (version: string) => {
    const [baseVersion] = version.split('-');
    const parts = baseVersion.split('.');
    if (parts.length < 2 || !parts[0] || !parts[1]) return null;
    if (!/^\d+$/.test(parts[0]) || !/^\d+$/.test(parts[1])) return null;
    return `${parts[0]}.${parts[1]}.x`;
};

const buildVersionGroups = (versions: string[]) => {
    const groups = new Map<string, string[]>();
    for (const version of versions) {
        const label = getVersionRangeLabel(version);
        const key = label || version;
        groups.set(key, [...(groups.get(key) || []), version]);
    }
    return Array.from(groups, ([label, groupVersions]) => ({
        label,
        versions: groupVersions,
        grouped: groupVersions.length > 1
    }));
};

const VersionFilterDropdown = ({
    selectedVersions,
    versions,
    groups,
    onChange
}: {
    selectedVersions: string[],
    versions: string[],
    groups: { label: string, versions: string[], grouped: boolean }[],
    onChange: (val: string) => void
}) => {
    const [isOpen, setIsOpen] = useState(false);
    const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
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

    const commitSelection = (nextVersions: string[]) => {
        const nextSet = new Set(nextVersions.filter(Boolean));
        const ordered = versions.filter(version => nextSet.has(version));
        onChange(ordered.length > 0 ? ordered.join(',') : 'Any');
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
        <div className="relative">
            <button type="button" aria-label="Game version filter" onClick={(e) => { e.stopPropagation(); setIsOpen(!isOpen); }} className="w-full text-left px-3 py-2 rounded-xl text-sm font-bold bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-300 flex justify-between items-center hover:bg-slate-100 dark:hover:bg-white/[0.05] transition-colors shadow-sm">
                <span className="truncate">{displayValue}</span>
                <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform pointer-events-none ${isOpen ? 'rotate-180' : ''}`} />
            </button>
            {isOpen && (
                <div className="absolute top-full mt-1 left-0 w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-[250] overflow-hidden py-1">
                    <button type="button" onClick={(e) => { e.stopPropagation(); onChange('Any'); }} className={`w-full text-left px-3 py-2 text-xs font-bold transition-colors flex justify-between items-center ${selectedVersions.length === 0 ? 'bg-modtale-accent text-white border-transparent shadow-sm' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/5'}`}>
                        Any
                        {selectedVersions.length === 0 && <Check className="w-3 h-3" />}
                    </button>
                    <div className="max-h-48 overflow-y-auto py-1">
                        {groups.map(group => {
                            if (!group.grouped) {
                                const version = group.versions[0];
                                if (!version) return null;
                                const isSelected = selectedSet.has(version);
                                return (
                                    <button type="button" key={version} onClick={(e) => { e.stopPropagation(); toggleVersion(version); }} className={`w-full text-left px-3 py-2 text-xs font-bold transition-colors flex justify-between items-center ${isSelected ? 'bg-modtale-accent text-white border-transparent shadow-sm' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/5'}`}>
                                        {version}
                                        {isSelected && <Check className="w-3 h-3" />}
                                    </button>
                                );
                            }

                            const selectedCount = group.versions.filter(version => selectedSet.has(version)).length;
                            const isSelected = selectedCount === group.versions.length;
                            const isPartiallySelected = selectedCount > 0 && !isSelected;
                            const isExpanded = expandedGroups.has(group.label);
                            return (
                                <div key={group.label}>
                                    <div className={`flex items-center transition-colors ${isSelected ? 'bg-modtale-accent text-white shadow-sm' : isPartiallySelected ? 'bg-modtale-accent/10 text-modtale-accent' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/5'}`}>
                                        <button type="button" aria-label={`Select all ${group.label} versions`} onClick={(e) => { e.stopPropagation(); toggleGroupSelection(group.versions); }} className="min-w-0 flex-1 text-left px-3 py-2 text-xs font-bold flex items-center gap-2">
                                            <span className="truncate">{group.label}</span>
                                            {isPartiallySelected && <span className="shrink-0 text-[10px] opacity-80">{selectedCount}/{group.versions.length}</span>}
                                        </button>
                                        <button type="button" aria-label={`${isExpanded ? 'Collapse' : 'Expand'} ${group.label} versions`} onClick={(e) => { e.stopPropagation(); toggleGroupExpanded(group.label); }} className="shrink-0 px-3 py-2">
                                            <ChevronRight className={`w-3.5 h-3.5 transition-transform ${isExpanded ? 'rotate-90' : ''}`} />
                                        </button>
                                        {isSelected && <Check className="w-3 h-3 mr-3 shrink-0" />}
                                    </div>
                                    {isExpanded && group.versions.map(version => {
                                        const versionSelected = selectedSet.has(version);
                                        return (
                                            <button type="button" key={version} onClick={(e) => { e.stopPropagation(); toggleVersion(version); }} className={`w-full text-left pl-7 pr-3 py-2 text-xs font-bold transition-colors flex justify-between items-center ${versionSelected ? 'bg-modtale-accent text-white border-transparent shadow-sm' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/5'}`}>
                                                {version}
                                                {versionSelected && <Check className="w-3 h-3" />}
                                            </button>
                                        );
                                    })}
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}
        </div>
    );
};

export interface BrowseFiltersProps {
    categoryPills?: React.ReactNode;
    pageTitle: string;
    totalItems: number;
    loading: boolean;
    sortBy: string;
    onSortChange: (s: any) => void;
    selectedTags: string[];
    onToggleTag: (tag: string) => void;
    onClearTags: () => void;
    activeFilterCount: number;
    onResetFilters: () => void;
    isFilterOpen: boolean;
    onToggleFilterMenu: () => void;
    searchTerm: string;
    onSearchChange: (val: string) => void;
    selectedVersion: string;
    setSelectedVersion: (v: string) => void;
    minFavorites: number;
    setMinFavorites: (v: number) => void;
    minDownloads: number;
    setMinDownloads: (v: number) => void;
    filterDate: string | null;
    setFilterDate: (d: string | null) => void;
    setPage: (p: number) => void;
    isMobile: boolean;
    viewStyle: BrowseViewStyle;
    onViewStyleChange: (style: BrowseViewStyle) => void;
    itemsPerPage: number;
    onItemsPerPageChange: (size: number) => void;
    isScrolled: boolean;
}

export const BrowseFilters: React.FC<BrowseFiltersProps> = React.memo(({
                                                                           categoryPills, pageTitle, totalItems, loading, sortBy, onSortChange,
                                                                           selectedTags, onToggleTag, onClearTags, onResetFilters,
                                                                           isFilterOpen, onToggleFilterMenu, searchTerm, onSearchChange,
                                                                           selectedVersion, setSelectedVersion, minFavorites, setMinFavorites, minDownloads, setMinDownloads, filterDate, setFilterDate,
                                                                           isMobile, viewStyle, onViewStyleChange, itemsPerPage, onItemsPerPageChange, isScrolled
                                                                       }) => {
    const [isTagsOpen, setIsTagsOpen] = useState(false);
    const tagRef = useRef<HTMLDivElement>(null);
    const filterRef = useRef<HTMLDivElement>(null);
    const [customDl, setCustomDl] = useState('');
    const [customFav, setCustomFav] = useState('');
    const [showCalendar, setShowCalendar] = useState(false);
    const [selectedDateObj, setSelectedDateObj] = useState<Date | null>(null);
    const [allGameVersions, setAllGameVersions] = useState<string[]>([]);
    const [preReleaseVersionSet, setPreReleaseVersionSet] = useState<Set<string>>(new Set());
    const [showPreReleases, setShowPreReleases] = useState(false);
    const [hasLoadedGameVersions, setHasLoadedGameVersions] = useState(false);
    const hasLoadedGameVersionsRef = useRef(false);
    const selectedVersions = useMemo(() => parseSelectedVersions(selectedVersion), [selectedVersion]);

    useEffect(() => {
        if (isMobile && isFilterOpen) document.body.style.overflow = 'hidden';
        else document.body.style.overflow = '';
        return () => { document.body.style.overflow = ''; };
    }, [isMobile, isFilterOpen]);

    useEffect(() => {
        const handleClick = (e: MouseEvent) => {
            if (tagRef.current && !tagRef.current.contains(e.target as Node)) setIsTagsOpen(false);
            if (filterRef.current && !filterRef.current.contains(e.target as Node)) {
                const mobileModal = document.getElementById('mobile-filter-modal');
                if (isMobile && isFilterOpen && mobileModal && mobileModal.contains(e.target as Node)) return;
                if (isFilterOpen) onToggleFilterMenu();
            }
        };
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, [isFilterOpen, onToggleFilterMenu, isMobile]);

    useEffect(() => {
        if (hasLoadedGameVersionsRef.current) return;
        if (!isFilterOpen && selectedVersion === 'Any') return;

        hasLoadedGameVersionsRef.current = true;

        projectClient.getMetaGameVersionCatalog().then((catalog) => {
            const ordered = catalog?.orderedVersions || catalog?.allVersions || [];
            const pre = new Set<string>();
            if (catalog?.versions?.length) {
                for (const entry of catalog.versions) {
                    if (entry.preRelease) pre.add(entry.version);
                }
            } else {
                for (const version of (catalog?.preReleaseVersions || [])) pre.add(version);
                for (const version of (catalog?.releaseVersions || [])) pre.delete(version);
            }
            setAllGameVersions(ordered);
            setPreReleaseVersionSet(pre);
            setHasLoadedGameVersions(true);
        }).catch(async () => {
            try {
                const versions = await projectClient.getProjectGameVersions();
                const sorted = versions.sort((a: string, b: string) => compareSemVer(b, a));
                setAllGameVersions(sorted);
                setPreReleaseVersionSet(new Set());
                setHasLoadedGameVersions(true);
            } catch {
                setAllGameVersions([]);
                setPreReleaseVersionSet(new Set());
                setHasLoadedGameVersions(true);
            }
        });
    }, [isFilterOpen, selectedVersion]);

    useEffect(() => {
        if (selectedVersions.some(version => preReleaseVersionSet.has(version))) {
            setShowPreReleases(true);
        }
    }, [selectedVersions, preReleaseVersionSet]);

    const visibleGameVersions = useMemo(() => {
        const preReleaseVersions = allGameVersions.filter(v => preReleaseVersionSet.has(v));
        const releaseVersions = allGameVersions.filter(v => !preReleaseVersionSet.has(v));
        return showPreReleases
            ? [...preReleaseVersions, ...releaseVersions]
            : releaseVersions;
    }, [allGameVersions, preReleaseVersionSet, showPreReleases]);

    const gameVersionGroups = useMemo(() => buildVersionGroups(visibleGameVersions), [visibleGameVersions]);

    useEffect(() => {
        if (!hasLoadedGameVersions || selectedVersions.length === 0) return;
        const validVersionSet = new Set(allGameVersions);
        const validSelectedVersions = selectedVersions.filter(version => validVersionSet.has(version));
        if (validSelectedVersions.length !== selectedVersions.length) {
            setSelectedVersion(validSelectedVersions.length > 0 ? validSelectedVersions.join(',') : 'Any');
        }
    }, [hasLoadedGameVersions, selectedVersions, allGameVersions, setSelectedVersion]);

    const getDateStringDaysAgo = (days: number) => {
        const d = new Date();
        d.setDate(d.getDate() - days);
        return d.toISOString().split('T')[0];
    };

    const handleDateSelect = (date: Date) => {
        setFilterDate(date.toISOString().split('T')[0]);
        setSelectedDateObj(date);
        setShowCalendar(false);
    };

    const handleDaysAgo = (days: number) => {
        if (days === 0) { setFilterDate(null); setSelectedDateObj(null); }
        else { setFilterDate(getDateStringDaysAgo(days)); setSelectedDateObj(null); }
    };

    const isDownloadSort = sortBy === 'downloads';
    const isPresetActive = (days: number) => {
        if (isDownloadSort || !filterDate) return days === 0;
        return filterDate === getDateStringDaysAgo(days) && !selectedDateObj;
    };

    const isDownloadPresetActive = (days: number) => {
        if (!isDownloadSort || !filterDate) return days === 0;
        return filterDate === getDateStringDaysAgo(days);
    };

    const resetAll = () => {
        onResetFilters();
        setShowPreReleases(false);
        setCustomDl('');
        setCustomFav('');
        setSelectedDateObj(null);
        setShowCalendar(false);
    };
    const displayFilterCount = [selectedVersions.length > 0, minFavorites > 0, minDownloads > 0, (filterDate !== null && !isDownloadSort)].filter(Boolean).length;
    const resultCountLabel = totalItems === 1 ? '1 result' : `${totalItems.toLocaleString()} results`;

    const filterMenuBody = (
        <div className="space-y-5">
            <div className="space-y-2">
                <div className="flex items-center justify-between mb-1.5">
                    <label className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase block">Game Version</label>
                    {preReleaseVersionSet.size > 0 && (
                        <button
                            type="button"
                            onClick={(e) => {
                                e.stopPropagation();
                                setShowPreReleases(prev => !prev);
                            }}
                            className="inline-flex items-center gap-1 text-[10px] font-semibold text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 transition-colors"
                        >
                            <span className={`w-3 h-3 rounded-[3px] border flex items-center justify-center ${showPreReleases ? 'border-modtale-accent bg-modtale-accent/15 text-modtale-accent' : 'border-slate-300 dark:border-white/20 text-transparent'}`}>
                                <Check className="w-2.5 h-2.5" />
                            </span>
                            <span>Pre Releases</span>
                        </button>
                    )}
                </div>
                <VersionFilterDropdown selectedVersions={selectedVersions} versions={visibleGameVersions} groups={gameVersionGroups} onChange={setSelectedVersion} />
            </div>
            <div>
                <label className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase mb-1.5 block">Minimum Favorites</label>
                <div className="grid grid-cols-4 gap-1 mb-2">
                    {[0, 10, 50, 100].map(f => (
                        <button key={f} type="button" onClick={() => { setMinFavorites(f); setCustomFav(''); }} className={`py-1.5 rounded-xl text-[10px] font-bold border transition-colors ${minFavorites === f && customFav === '' ? 'bg-modtale-accent text-white border-transparent shadow-sm' : 'bg-transparent border-slate-200 dark:border-white/10 text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-white/5'}`}>{f === 0 ? 'Any' : `${f}+`}</button>
                    ))}
                </div>
                <div className="relative group">
                    <Heart className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400 z-10 pointer-events-none" />
                    <input type="number" placeholder="Custom min favorites..." value={customFav} onChange={e => { setCustomFav(e.target.value); setMinFavorites(Number(e.target.value)); }} aria-label="Custom minimum favorites" className="w-full bg-slate-50 dark:bg-slate-950/50 border border-slate-200 dark:border-white/10 rounded-xl pl-9 pr-3 py-2 text-xs font-medium focus:ring-1 focus:ring-modtale-accent outline-none shadow-sm [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none relative z-0" />
                </div>
            </div>
            <div>
                <label className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase mb-1.5 block">Downloads</label>
                <div className="grid grid-cols-4 gap-1 mb-2">
                    {[0, 1000, 5000, 10000].map(d => (
                        <button key={d} type="button" onClick={() => { setMinDownloads(d); setCustomDl(''); }} className={`py-1.5 rounded-xl text-[10px] font-bold border transition-colors ${minDownloads === d && customDl === '' ? 'bg-modtale-accent text-white border-transparent shadow-sm' : 'bg-transparent border-slate-200 dark:border-white/10 text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-white/5'}`}>{d === 0 ? 'Any' : `${d/1000}k+`}</button>
                    ))}
                </div>
                <div className="relative group">
                    <Download className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400 z-10 pointer-events-none" />
                    <input type="number" placeholder="Custom min downloads..." value={customDl} onChange={e => { setCustomDl(e.target.value); setMinDownloads(Number(e.target.value)); }} aria-label="Custom minimum downloads" className="w-full bg-slate-50 dark:bg-slate-950/50 border border-slate-200 dark:border-white/10 rounded-xl pl-9 pr-3 py-2 text-xs font-medium focus:ring-1 focus:ring-modtale-accent outline-none shadow-sm [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none relative z-0" />
                </div>
            </div>
            <div>
                <div className="flex justify-between items-center mb-1.5">
                    <label className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase block">Last Updated</label>
                </div>
                {showCalendar ? <CalendarWidget selectedDate={selectedDateObj} onSelect={handleDateSelect} /> : (
                    <>
                        <div className="grid grid-cols-4 gap-1 mb-2">
                            {[0, 7, 30, 90].map(d => (
                                <button key={d} type="button" onClick={() => handleDaysAgo(d)} className={`py-1.5 rounded-xl text-[10px] font-bold border transition-colors ${isPresetActive(d) ? 'bg-modtale-accent text-white border-transparent shadow-sm' : 'bg-transparent border-slate-200 dark:border-white/10 text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-white/5'}`}>{d === 0 ? 'Any' : `${d}d`}</button>
                            ))}
                        </div>
                        <button type="button" onClick={() => setShowCalendar(true)} className={`w-full flex items-center justify-between bg-slate-50 dark:bg-slate-950/50 border border-slate-200 dark:border-white/10 rounded-xl px-3 py-2 text-xs font-medium transition-colors hover:bg-slate-100 dark:hover:bg-white/10 shadow-sm ${filterDate && !isPresetActive(7) && !isPresetActive(30) && !isPresetActive(90) && !isDownloadSort ? 'text-modtale-accent font-bold border-modtale-accent' : 'text-slate-500 dark:text-slate-300'}`}>
                            <span className="flex items-center gap-2 pointer-events-none"><CalendarIcon className="w-3.5 h-3.5" /> {filterDate && !isDownloadSort ? `Since ${new Date(filterDate).toLocaleDateString()}` : 'Pick a Date'}</span>
                            <ChevronDown className="w-3.5 h-3.5 pointer-events-none" />
                        </button>
                    </>
                )}
            </div>
            <div className="pt-2 border-t border-slate-200 dark:border-white/10">
                <button type="button" onClick={resetAll} className="w-full py-2 bg-red-50 text-red-600 dark:bg-red-500/10 dark:text-red-400 font-bold rounded-xl text-xs hover:bg-red-100 dark:hover:bg-red-500/20 transition-colors flex items-center justify-center gap-2 border border-transparent dark:border-red-500/20"><RotateCcw className="w-3 h-3" /> Reset Filters</button>
            </div>
        </div>
    );

    return (
        <div className="w-full flex flex-col relative z-40 min-w-0">
            <div className="lg:hidden flex items-center w-full transition-all duration-300 gap-2">
                <div className="relative group flex-1 h-10 min-w-0 transition-all duration-300">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-500 z-10 pointer-events-none group-focus-within:text-modtale-accent transition-colors" />
                    <input type="text" className="block w-full pl-9 pr-9 h-full rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 text-sm shadow-sm focus:ring-2 focus:ring-modtale-accent outline-none text-slate-900 dark:text-white relative z-0 transition-all duration-300" placeholder="Search projects..." value={searchTerm} onChange={(e) => onSearchChange(e.target.value)} />
                    {searchTerm && (
                        <button type="button" aria-label="Clear search" onClick={() => onSearchChange('')} className="absolute right-3 top-1/2 -translate-y-1/2 p-1 z-20"><X className="w-4 h-4 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors" /></button>
                    )}
                </div>
                <div className={`flex bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl items-center shadow-sm shrink-0 transition-all duration-300 overflow-hidden ${isScrolled && isMobile ? 'max-w-0 opacity-0 border-transparent p-0 m-0 h-10' : 'max-w-[200px] opacity-100 p-1 h-10'}`}>
                    {(['grid', 'list', 'compact'] as const).map(style => (
                        <button key={style} type="button" aria-label={`${style} view`} onClick={() => onViewStyleChange(style)} className={`p-1.5 rounded-lg transition-colors ${viewStyle === style ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 hover:bg-slate-50 dark:hover:bg-white/5'}`}>
                            {style === 'grid' && <LayoutGrid className="w-4 h-4" />}
                            {style === 'list' && <List className="w-4 h-4" />}
                            {style === 'compact' && <AlignJustify className="w-4 h-4" />}
                        </button>
                    ))}
                </div>
            </div>
            <div className={`w-full flex flex-col transition-all duration-300 overflow-visible ${isScrolled && isMobile ? 'max-h-0 opacity-0 pointer-events-none mt-0' : 'max-h-[500px] opacity-100 mt-3 lg:mt-0 gap-3'}`}>
                <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3 lg:gap-4 w-full min-w-0">
                    <div className="flex items-center gap-4 min-w-0 flex-1">
                        {categoryPills ? <div className="min-w-0 max-w-full">{categoryPills}</div> : <div className="hidden lg:block shrink-0 min-w-0"><h1 className="text-xl lg:text-2xl font-black text-slate-900 dark:text-white flex items-center gap-2 drop-shadow-sm truncate">{pageTitle}</h1></div>}
                        <div className="hidden 2xl:block shrink-0 border-l border-slate-200 dark:border-white/10 pl-4">
                            <span className="text-xs font-bold text-slate-500 dark:text-slate-400 tracking-wide block drop-shadow-sm whitespace-nowrap">{loading ? 'Searching...' : resultCountLabel}</span>
                        </div>
                    </div>
                    <div className="flex flex-wrap lg:flex-nowrap items-center justify-start lg:justify-end gap-2 w-full lg:w-auto shrink-0 mt-1 lg:mt-0">
                        <div className="hidden lg:flex bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl p-1 h-10 items-center shadow-sm shrink-0">
                            {(['grid', 'list', 'compact'] as const).map(style => (
                                <button key={style} type="button" aria-label={`${style} view`} onClick={() => onViewStyleChange(style)} className={`p-1.5 rounded-lg transition-colors ${viewStyle === style ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 hover:bg-slate-50 dark:hover:bg-white/5'}`}>
                                    {style === 'grid' && <LayoutGrid className="w-4 h-4" />}
                                    {style === 'list' && <List className="w-4 h-4" />}
                                    {style === 'compact' && <AlignJustify className="w-4 h-4" />}
                                </button>
                            ))}
                        </div>
                        <DropdownSelect
                            value={String(itemsPerPage)}
                            onChange={(value) => onItemsPerPageChange(Number(value))}
                            onOpen={() => setIsTagsOpen(false)}
                            options={BROWSE_ITEMS_PER_PAGE_OPTIONS.map(size => ({
                                value: String(size),
                                label: String(size)
                            }))}
                            placeholder="12"
                            containerClassName="relative flex-none h-10 w-16"
                            buttonLabel={itemsPerPage}
                            buttonAriaLabel={`${itemsPerPage} results per page`}
                            buttonTitle="Results per page"
                            showSelectedCheck={false}
                            buttonClassName="w-full h-full flex items-center justify-center gap-1 border rounded-xl px-2 text-xs font-black transition-all whitespace-nowrap bg-white dark:bg-slate-900 border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-white/[0.02] shadow-sm"
                            menuAlign="right"
                            menuClassName="w-16 max-w-[calc(100vw-2rem)] bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl py-1 z-[70] animate-in fade-in zoom-in-95 duration-200 overflow-hidden"
                            optionClassName="w-full px-2 py-2 text-sm font-bold flex justify-center items-center transition-colors text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/5"
                        />
                        <div className="relative flex-1 lg:flex-none h-10" ref={tagRef}>
                            <button type="button" onClick={(e) => { e.preventDefault(); e.stopPropagation(); setIsTagsOpen(!isTagsOpen); if(isFilterOpen) onToggleFilterMenu(); }} className={`w-full lg:w-auto h-full flex items-center justify-center lg:justify-start gap-1.5 border rounded-xl px-3 text-xs font-bold transition-all whitespace-nowrap ${selectedTags.length > 0 ? 'bg-modtale-accent text-white border-transparent shadow-md' : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-white/[0.02] shadow-sm'}`}>
                                <div className="flex items-center gap-1.5 pointer-events-none"><Tag className="w-3.5 h-3.5" /> <span>Tags</span></div>
                                {selectedTags.length > 0 && <span className="bg-white/20 px-1.5 rounded text-[10px] pointer-events-none">{selectedTags.length}</span>}
                            </button>
                            {isTagsOpen && (
                                <div className="absolute left-0 lg:left-auto top-full mt-2 w-[280px] sm:w-[320px] lg:w-72 max-w-[calc(100vw-2rem)] max-h-[70vh] overflow-y-auto bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-2xl shadow-xl z-[200] p-4 animate-in fade-in slide-in-from-top-2">
                                    <div className="flex justify-between items-center mb-3">
                                        <span className="font-bold text-sm text-slate-900 dark:text-white">Filter by Tag</span>
                                        {selectedTags.length > 0 && <button type="button" onClick={onClearTags} className="text-xs text-red-500 hover:underline font-bold">Clear All</button>}
                                    </div>
                                    <div className="flex flex-wrap gap-2 max-h-60 overflow-y-auto pr-2">
                                        {GLOBAL_TAGS.map(tag => (
                                            <button key={tag} type="button" onClick={() => onToggleTag(tag)} className={`px-3 py-1.5 rounded-xl text-xs font-bold border transition-colors flex items-center gap-1.5 ${selectedTags.includes(tag) ? 'bg-modtale-accent text-white border-transparent shadow-sm' : 'bg-slate-50 dark:bg-slate-950/50 border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/10 shadow-sm'}`}>{tag} {selectedTags.includes(tag) && <Check className="w-3 h-3" />}</button>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                        <div className="relative flex-1 lg:flex-none h-10" ref={filterRef}>
                            <button type="button" onClick={(e) => { e.preventDefault(); e.stopPropagation(); onToggleFilterMenu(); }} className={`w-full h-full flex items-center justify-center lg:justify-start gap-1.5 border rounded-xl px-3 text-xs font-bold transition-all whitespace-nowrap ${isFilterOpen || displayFilterCount > 0 ? 'bg-modtale-accent text-white border-transparent shadow-md' : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-white/[0.02] shadow-sm'}`}>
                                <div className="flex items-center gap-1.5 pointer-events-none"><Filter className="w-3.5 h-3.5" /> <span>Filters</span></div>
                                {displayFilterCount > 0 && <span className="bg-white/20 px-1.5 rounded text-[10px] pointer-events-none">{displayFilterCount}</span>}
                            </button>
                            {isFilterOpen && (
                                <>
                                    {isMobile ? (
                                        typeof document !== 'undefined' ? createPortal(
                                            <div className="fixed inset-0 z-[9999] bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-200" onClick={(e) => { e.stopPropagation(); onToggleFilterMenu(); }}>
                                                <div id="mobile-filter-modal" className="w-full max-w-sm max-h-[85vh] bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-3xl shadow-2xl flex flex-col animate-in zoom-in-95 duration-200 overflow-hidden" onClick={(e) => e.stopPropagation()}>
                                                    <div className="p-4 border-b border-slate-200 dark:border-white/10 flex justify-between items-center bg-slate-50 dark:bg-slate-950/50">
                                                        <span className="font-bold text-sm text-slate-900 dark:text-white">Refine Results</span>
                                                        <button type="button" onClick={onToggleFilterMenu} className="p-1.5 rounded-full hover:bg-slate-200/10 dark:hover:bg-white/10 transition-colors"><X className="w-5 h-5 text-slate-500" /></button>
                                                    </div>
                                                    <div className="p-4 space-y-5 overflow-y-auto flex-1">{filterMenuBody}</div>
                                                </div>
                                            </div>,
                                            document.body
                                        ) : null
                                    ) : (
                                        <div className="absolute right-0 top-full mt-2 w-[280px] lg:w-72 max-h-[70vh] overflow-y-auto bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-2xl shadow-xl z-[200] p-4 animate-in fade-in slide-in-from-top-2">{filterMenuBody}</div>
                                    )}
                                </>
                            )}
                        </div>
                        {isDownloadSort && (
                            <div className="hidden lg:flex bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl p-1 h-10 items-center shadow-sm shrink-0">
                                {[
                                    { label: '7d', val: 7 },
                                    { label: '30d', val: 30 },
                                    { label: '90d', val: 90 },
                                    { label: 'All', val: 0 }
                                ].map((opt) => (
                                    <button key={opt.label} type="button" onClick={() => { if (opt.val === 0) setFilterDate(null); else { const d = new Date(); d.setDate(d.getDate() - opt.val); setFilterDate(d.toISOString().split('T')[0]); } }} className={`px-3 py-1 text-xs font-bold rounded-lg transition-colors flex items-center justify-center h-full ${isDownloadPresetActive(opt.val) ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-white/[0.02]'}`}>{opt.label}</button>
                                ))}
                            </div>
                        )}
                        <style>{`
                            .sort-override-wrapper > div > button:first-child {
                                background-color: rgb(255 255 255) !important;
                                border-color: rgb(226 232 240) !important;
                                color: rgb(51 65 85) !important;
                            }
                            .dark .sort-override-wrapper > div > button:first-child {
                                background-color: rgb(15 23 42) !important;
                                border-color: rgba(255,255,255,0.1) !important;
                                color: rgb(203 213 225) !important;
                            }
                            .sort-override-wrapper > div > button:first-child:hover {
                                background-color: rgb(248 250 252) !important;
                            }
                            .dark .sort-override-wrapper > div > button:first-child:hover {
                                background-color: rgba(255,255,255,0.02) !important;
                            }
                        `}</style>
                        <div className="sort-override-wrapper flex-1 lg:flex-none h-10">
                            <SortDropdown value={sortBy} onChange={(val) => onSortChange(val)} onOpen={() => setIsTagsOpen(false)} isMobile={isMobile} />
                        </div>
                    </div>
                </div>
                {isDownloadSort && (
                    <div className="lg:hidden flex justify-center h-10 w-full mt-3">
                        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl p-1 flex w-full justify-between shadow-sm">
                            {[
                                { label: '7 Days', val: 7 },
                                { label: '30 Days', val: 30 },
                                { label: '3 Months', val: 90 },
                                { label: 'All Time', val: 0 }
                            ].map((opt) => (
                                <button key={opt.label} type="button" onClick={() => { if (opt.val === 0) setFilterDate(null); else { const d = new Date(); d.setDate(d.getDate() - opt.val); setFilterDate(d.toISOString().split('T')[0]); } }} className={`flex-1 py-1.5 text-xs font-bold rounded-lg transition-colors flex items-center justify-center ${isDownloadPresetActive(opt.val) ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-white/[0.02]'}`}>{opt.label}</button>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
});

BrowseFilters.displayName = 'BrowseFilters';
