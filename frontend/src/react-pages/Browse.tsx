import React, { useState, useRef, useEffect, useCallback, useMemo, useLayoutEffect } from 'react';
import { Helmet } from 'react-helmet-async';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { Filter, Tag, ArrowDownUp, ChevronDown, Check, X, Search, Heart, RotateCcw, Calendar as CalendarIcon, Download, ChevronLeft, ChevronRight, Clock, CornerDownLeft, PackageSearch, LayoutGrid, List, AlignJustify } from 'lucide-react';

import type { Mod, Modpack, World } from '../types';
import { ModCard } from '../components/resources/ModCard';
import { api, BACKEND_URL } from '../utils/api';
import { captureError } from '../utils/errorTracking';
import { PROJECT_TYPES, BROWSE_VIEWS, GLOBAL_TAGS } from '../data/categories';
import type { Classification } from '../data/categories';
import { getProjectUrl } from '../utils/slug';
import { EmptyState } from '../components/ui/EmptyState';
import { getCategorySEO } from '../data/seo-constants';
import { generateItemListSchema, generateBreadcrumbSchema, getBreadcrumbsForClassification } from '../utils/schema';
import { useMobile } from '../context/MobileContext';
import { compareSemVer } from '../utils/modHelpers';

const CalendarWidget = ({ selectedDate, onSelect }: { selectedDate: Date | null, onSelect: (date: Date) => void }) => {
    const [viewDate, setViewDate] = useState(selectedDate || new Date());
    const getDaysInMonth = (year: number, month: number) => new Date(year, month + 1, 0).getDate();
    const getFirstDay = (year: number, month: number) => new Date(year, month, 1).getDay();
    const year = viewDate.getFullYear();
    const month = viewDate.getMonth();
    const daysInMonth = getDaysInMonth(year, month);
    const startDay = getFirstDay(year, month);
    const today = new Date();
    today.setHours(0,0,0,0);
    const days = [];
    for (let i = 0; i < startDay; i++) days.push(null);
    for (let i = 1; i <= daysInMonth; i++) days.push(i);
    const changeMonth = (delta: number) => { setViewDate(new Date(year, month + delta, 1)); };
    const isSelected = (d: number) => { return selectedDate && selectedDate.getDate() === d && selectedDate.getMonth() === month && selectedDate.getFullYear() === year; };
    const isDisabled = (d: number) => { const checkDate = new Date(year, month, d); return checkDate > today; };

    return (
        <div className="p-3 bg-slate-50 dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-white/10 animate-in fade-in zoom-in-95 duration-200 shadow-sm">
            <div className="flex justify-between items-center mb-3">
                <button onClick={() => changeMonth(-1)} aria-label="Previous month" className="p-1 hover:bg-slate-200 dark:hover:bg-white/[0.05] rounded-full transition-colors flex items-center justify-center"><ChevronLeft className="w-4 h-4 text-slate-500" /></button>
                <span className="text-sm font-bold text-slate-700 dark:text-slate-200">{viewDate.toLocaleString('default', { month: 'long', year: 'numeric' })}</span>
                <button onClick={() => changeMonth(1)} disabled={viewDate.getMonth() === today.getMonth() && viewDate.getFullYear() === today.getFullYear()} aria-label="Next month" className="p-1 hover:bg-slate-200 dark:hover:bg-white/[0.05] rounded-full transition-colors disabled:opacity-30 disabled:cursor-not-allowed flex items-center justify-center"><ChevronRight className="w-4 h-4 text-slate-500" /></button>
            </div>
            <div className="grid grid-cols-7 gap-1 text-center mb-2">{['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'].map(d => (<div key={d} className="text-[10px] font-bold text-slate-400 uppercase">{d}</div>))}</div>
            <div className="grid grid-cols-7 gap-1">{days.map((d, i) => (d ? (<button key={i} disabled={isDisabled(d)} onClick={() => !isDisabled(d) && onSelect(new Date(year, month, d))} aria-label={new Date(year, month, d).toDateString()} className={`w-7 h-7 flex items-center justify-center rounded-lg text-xs font-medium transition-all ${isDisabled(d) ? 'text-slate-300 dark:text-slate-600 cursor-not-allowed opacity-40' : isSelected(d) ? 'bg-modtale-accent text-white shadow-md shadow-modtale-accent/30' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-white/[0.05]'}`}>{d}</button>) : <div key={i} />))}</div>
        </div>
    );
};

const SortDropdown = ({ value, onChange, onOpen, isMobile }: { value: string, onChange: (val: any) => void, onOpen: () => void, isMobile: boolean }) => {
    const [isOpen, setIsOpen] = useState(false);
    const containerRef = useRef<HTMLDivElement>(null);

    const options = [
        { id: 'relevance', label: 'Relevance', mobileOnly: false },
        { id: 'popular', label: 'Popular', mobileOnly: true },
        { id: 'trending', label: 'Trending', mobileOnly: true },
        { id: 'downloads', label: 'Downloads', mobileOnly: false },
        { id: 'favorites', label: 'Favorites', mobileOnly: false },
        { id: 'newest', label: 'Newest', mobileOnly: true },
        { id: 'updated', label: 'Updated', mobileOnly: true }
    ];

    const visibleOptions = options.filter(opt => isMobile || !opt.mobileOnly);

    useEffect(() => {
        const handleClick = (e: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) setIsOpen(false);
        };
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, []);

    const handleToggle = () => { if (!isOpen) onOpen(); setIsOpen(!isOpen); };
    const currentLabel = options.find(o => o.id === value)?.label || 'Sort';

    return (
        <div className="relative w-full md:w-auto" ref={containerRef}>
            <button
                onClick={handleToggle}
                aria-label="Sort options"
                className={`w-full md:w-auto h-10 flex items-center justify-between gap-2 border rounded-xl px-4 text-xs md:text-sm font-bold transition-all whitespace-nowrap min-w-[130px] shadow-sm ${
                    isOpen
                        ? 'bg-modtale-accent text-white border-transparent'
                        : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-white/[0.02]'
                }`}
            >
                <span className="truncate">{currentLabel}</span>
                <ChevronDown className={`w-4 h-4 transition-transform flex-shrink-0 ${isOpen ? 'rotate-180 text-white' : 'text-slate-400'}`} />
            </button>

            {isOpen && (
                <div className="absolute right-0 md:right-auto md:left-0 top-full mt-2 w-48 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-2xl shadow-xl py-2 z-[70] animate-in fade-in zoom-in-95 duration-200 overflow-hidden">
                    <div className="px-4 py-2 mb-1 border-b border-slate-100 dark:border-white/5">
                        <span className="text-[10px] font-black uppercase text-slate-400 dark:text-slate-500 tracking-widest">Sort By</span>
                    </div>
                    {visibleOptions.map(opt => (
                        <button
                            key={opt.id}
                            onClick={() => { onChange(opt.id); setIsOpen(false); }}
                            className={`w-full text-left px-4 py-2.5 text-sm font-bold flex justify-between items-center transition-colors ${
                                value === opt.id
                                    ? 'bg-modtale-accent text-white'
                                    : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/5'
                            }`}
                        >
                            {opt.label}
                            {value === opt.id && <Check className="w-3.5 h-3.5" />}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};

const FilterDropdown = ({ label, value, options, onChange }: { label: string, value: string, options: string[], onChange: (val: string) => void }) => {
    const [isOpen, setIsOpen] = useState(false);
    return (
        <div className="relative">
            <style>{` .custom-scrollbar::-webkit-scrollbar { width: 4px; } .custom-scrollbar::-webkit-scrollbar-track { background: transparent; } .custom-scrollbar::-webkit-scrollbar-thumb { background-color: rgba(156, 163, 175, 0.3); border-radius: 20px; } `}</style>
            <label className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase mb-1.5 block">{label}</label>
            <button onClick={(e) => { e.stopPropagation(); setIsOpen(!isOpen); }} className="w-full text-left px-3 py-2 rounded-xl text-sm font-bold bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-300 flex justify-between items-center hover:bg-slate-100 dark:hover:bg-white/[0.05] transition-colors shadow-sm">{value}<ChevronDown className={`w-4 h-4 text-slate-400 transition-transform pointer-events-none ${isOpen ? 'rotate-180' : ''}`} /></button>
            {isOpen && <div className="absolute top-full mt-1 left-0 w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-[250] max-h-48 overflow-y-auto py-1 custom-scrollbar">{options.map(opt => <button key={opt} onClick={(e) => { e.stopPropagation(); onChange(opt); setIsOpen(false); }} className={`w-full text-left px-3 py-2 text-xs font-bold transition-colors flex justify-between items-center ${value === opt ? 'bg-modtale-accent text-white' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/5'}`}>{opt}{value === opt && <Check className="w-3 h-3" />}</button>)}</div>}
        </div>
    );
};

interface HomeFiltersProps {
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
    viewStyle: 'grid' | 'list' | 'compact';
    onViewStyleChange: (style: 'grid' | 'list' | 'compact') => void;
}

export const BrowseFilters: React.FC<HomeFiltersProps> = React.memo(({
                                                                         categoryPills,
                                                                         pageTitle, totalItems, loading, sortBy, onSortChange,
                                                                         selectedTags, onToggleTag, onClearTags, activeFilterCount, onResetFilters,
                                                                         isFilterOpen, onToggleFilterMenu, searchTerm, onSearchChange,
                                                                         selectedVersion, setSelectedVersion, minFavorites, setMinFavorites, minDownloads, setMinDownloads, filterDate, setFilterDate, setPage,
                                                                         isMobile, viewStyle, onViewStyleChange
                                                                     }) => {
    const [isTagsOpen, setIsTagsOpen] = useState(false);
    const tagRef = useRef<HTMLDivElement>(null);
    const filterRef = useRef<HTMLDivElement>(null);
    const [customDl, setCustomDl] = useState('');
    const [customFav, setCustomFav] = useState('');
    const [showCalendar, setShowCalendar] = useState(false);
    const [selectedDateObj, setSelectedDateObj] = useState<Date | null>(null);
    const [gameVersionOptions, setGameVersionOptions] = useState<string[]>(['Any']);

    useEffect(() => {
        const handleClick = (e: MouseEvent) => {
            if (tagRef.current && !tagRef.current.contains(e.target as Node)) setIsTagsOpen(false);
            if (filterRef.current && !filterRef.current.contains(e.target as Node)) {
                if(isFilterOpen) onToggleFilterMenu();
            }
        };
        document.addEventListener('mousedown', handleClick);
        return () => {
            document.removeEventListener('mousedown', handleClick);
        };
    }, [isFilterOpen, onToggleFilterMenu]);

    useEffect(() => {
        api.get('/meta/game-versions').then(res => {
            const versions = Array.isArray(res.data) ? res.data : [];
            const sorted = versions.sort((a: string, b: string) => compareSemVer(b, a));
            setGameVersionOptions(['Any', ...sorted]);
        }).catch(err => console.error("Failed to load game versions for filters", err));
    }, []);

    const handleSortOpen = () => { if (isFilterOpen) onToggleFilterMenu(); if (isTagsOpen) setIsTagsOpen(false); };

    const getDateStringDaysAgo = (days: number) => {
        const d = new Date();
        d.setDate(d.getDate() - days);
        return d.toISOString().split('T')[0];
    };

    const handleDateSelect = (date: Date) => {
        const isoDate = date.toISOString().split('T')[0];
        setFilterDate(isoDate);
        setSelectedDateObj(date);
        setShowCalendar(false);
    };

    const handleDaysAgo = (days: number) => {
        if (days === 0) { setFilterDate(null); setSelectedDateObj(null); }
        else { setFilterDate(getDateStringDaysAgo(days)); setSelectedDateObj(null); }
    };

    const isDownloadSort = sortBy === 'downloads';

    const isPresetActive = (days: number) => {
        if (isDownloadSort) return false;
        if (days === 0) return !filterDate;
        if (!filterDate) return false;
        const targetStr = getDateStringDaysAgo(days);
        return filterDate === targetStr && !selectedDateObj;
    };

    const isDownloadPresetActive = (days: number) => {
        if (!isDownloadSort) return false;
        if (days === 0) return !filterDate;
        if (!filterDate) return false;
        const targetStr = getDateStringDaysAgo(days);
        return filterDate === targetStr;
    }

    const resetAll = () => { onResetFilters(); setCustomDl(''); setCustomFav(''); setFilterDate(null); setSelectedDateObj(null); setShowCalendar(false); };

    const handleDownloadTimeframe = (days: number) => {
        if (days === 0) setFilterDate(null);
        else setFilterDate(getDateStringDaysAgo(days));
    }

    const displayFilterCount = [
        selectedVersion && selectedVersion !== 'Any',
        minFavorites > 0,
        minDownloads > 0,
        (filterDate !== null && !isDownloadSort)
    ].filter(Boolean).length;

    return (
        <div className="w-full flex flex-col gap-3 relative z-40">
            <div className="md:hidden w-full">
                <div className="relative group w-full h-10">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-500 z-10 pointer-events-none group-focus-within:text-modtale-accent transition-colors" />
                    <input
                        type="text"
                        className="block w-full pl-9 pr-3 h-full rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 text-sm shadow-sm focus:ring-2 focus:ring-modtale-accent outline-none text-slate-900 dark:text-white relative z-0"
                        placeholder="Search projects..."
                        value={searchTerm}
                        onChange={(e) => onSearchChange(e.target.value)}
                        aria-label="Mobile search"
                    />
                </div>
            </div>

            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 w-full">
                <div className="flex items-center gap-4 w-full md:w-auto min-w-0 flex-1">
                    {categoryPills ? categoryPills : (
                        <div className="hidden md:block shrink-0">
                            <h2 className="text-xl md:text-2xl font-black text-slate-900 dark:text-white flex items-center gap-2 drop-shadow-sm">{pageTitle}</h2>
                        </div>
                    )}
                    <div className="hidden lg:block shrink-0 border-l border-slate-200 dark:border-white/10 pl-4">
                        <span className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wide block drop-shadow-sm">
                            {loading ? 'Searching...' : `${totalItems.toLocaleString()} Results`}
                        </span>
                    </div>
                </div>

                <div className="flex flex-wrap items-center justify-start md:justify-end gap-2 w-full md:w-auto shrink-0">
                    <div className="hidden md:flex bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl p-1 h-10 items-center shadow-sm">
                        <button
                            onClick={() => onViewStyleChange('grid')}
                            className={`p-1.5 rounded-lg transition-colors ${viewStyle === 'grid' ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 hover:bg-slate-50 dark:hover:bg-white/5'}`}
                            title="Grid View"
                        >
                            <LayoutGrid className="w-4 h-4" />
                        </button>
                        <button
                            onClick={() => onViewStyleChange('list')}
                            className={`p-1.5 rounded-lg transition-colors ${viewStyle === 'list' ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 hover:bg-slate-50 dark:hover:bg-white/5'}`}
                            title="List View"
                        >
                            <List className="w-4 h-4" />
                        </button>
                        <button
                            onClick={() => onViewStyleChange('compact')}
                            className={`p-1.5 rounded-lg transition-colors ${viewStyle === 'compact' ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 hover:bg-slate-50 dark:hover:bg-white/5'}`}
                            title="Compact View"
                        >
                            <AlignJustify className="w-4 h-4" />
                        </button>
                    </div>

                    <div className="relative flex-1 md:flex-none shrink-0 h-10" ref={tagRef}>
                        <button onClick={() => { setIsTagsOpen(!isTagsOpen); if(isFilterOpen) onToggleFilterMenu(); }} className={`w-full md:w-auto h-full flex items-center justify-between md:justify-start gap-2 border rounded-xl px-3 md:px-4 text-xs md:text-sm font-bold transition-all whitespace-nowrap ${selectedTags.length > 0 ? 'bg-modtale-accent text-white border-transparent shadow-md' : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-white/[0.02] shadow-sm'}`}>
                            <div className="flex items-center gap-2 pointer-events-none"><Tag className="w-3.5 h-3.5" /> <span>Tags</span></div>
                            {selectedTags.length > 0 && <span className="bg-white/20 px-1.5 rounded text-[10px] pointer-events-none">{selectedTags.length}</span>}
                        </button>
                        {isTagsOpen && (
                            <div className="absolute right-0 md:left-0 top-full mt-2 w-[calc(100vw-2rem)] md:w-72 max-h-[70vh] overflow-y-auto bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-2xl shadow-xl z-[200] p-4 animate-in fade-in slide-in-from-top-2">
                                <div className="flex justify-between items-center mb-3">
                                    <h3 className="font-bold text-sm text-slate-900 dark:text-white">Filter by Tag</h3>
                                    {selectedTags.length > 0 && <button onClick={onClearTags} className="text-xs text-red-500 hover:underline font-bold">Clear All</button>}
                                </div>
                                <div className="flex flex-wrap gap-2 max-h-60 overflow-y-auto pr-2 custom-scrollbar">
                                    <style>{` .custom-scrollbar::-webkit-scrollbar { width: 4px; } .custom-scrollbar::-webkit-scrollbar-track { background: transparent; } .custom-scrollbar::-webkit-scrollbar-thumb { background-color: rgba(156, 163, 175, 0.5); border-radius: 20px; } `}</style>
                                    {GLOBAL_TAGS.map(tag => (
                                        <button key={tag} onClick={() => onToggleTag(tag)} className={`px-3 py-1.5 rounded-xl text-xs font-bold border transition-colors flex items-center gap-1.5 ${selectedTags.includes(tag) ? 'bg-modtale-accent text-white border-transparent shadow-sm' : 'bg-slate-50 dark:bg-slate-950/50 border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/10 shadow-sm'}`}>
                                            {tag} {selectedTags.includes(tag) && <Check className="w-3 h-3" />}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="relative flex-1 md:flex-none shrink-0 h-10" ref={filterRef}>
                        <button onClick={onToggleFilterMenu} className={`w-full md:w-auto h-full flex items-center justify-between md:justify-start gap-2 border rounded-xl px-3 md:px-4 text-xs md:text-sm font-bold transition-all ${isFilterOpen || displayFilterCount > 0 ? 'bg-modtale-accent text-white border-transparent shadow-md' : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-white/[0.02] shadow-sm'}`}>
                            <div className="flex items-center gap-2 pointer-events-none"><Filter className="w-3.5 h-3.5" /> <span>Filters</span></div>
                            {displayFilterCount > 0 && <span className="bg-white/20 px-1.5 rounded text-[10px] pointer-events-none">{displayFilterCount}</span>}
                        </button>

                        {isFilterOpen && (
                            <div className="absolute right-0 md:right-0 md:translate-x-0 top-full mt-2 w-72 max-h-[70vh] overflow-y-auto bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-2xl shadow-xl z-[200] animate-in fade-in slide-in-from-top-2">
                                <div className="p-4 border-b border-slate-200 dark:border-white/10">
                                    <h3 className="font-bold text-sm text-slate-900 dark:text-white">Refine Results</h3>
                                </div>
                                <div className="p-4 space-y-5">
                                    <FilterDropdown label="Game Version" value={selectedVersion} options={gameVersionOptions} onChange={(val) => {setSelectedVersion(val);}} />

                                    <div>
                                        <label className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase mb-1.5 block">Minimum Favorites</label>
                                        <div className="grid grid-cols-4 gap-1 mb-2">
                                            {[0, 10, 50, 100].map(f => (
                                                <button key={f} onClick={() => { setMinFavorites(f); setCustomFav(''); }} className={`py-1.5 rounded-xl text-[10px] font-bold border transition-colors ${minFavorites === f && customFav === '' ? 'bg-modtale-accent text-white border-transparent shadow-sm' : 'bg-transparent border-slate-200 dark:border-white/10 text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-white/5'}`}>
                                                    {f === 0 ? 'Any' : `${f}+`}
                                                </button>
                                            ))}
                                        </div>
                                        <div className="relative group">
                                            <Heart className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400 z-10 pointer-events-none" />
                                            <input
                                                type="number"
                                                placeholder="Custom min favorites..."
                                                value={customFav}
                                                onChange={e => {
                                                    setCustomFav(e.target.value);
                                                    setMinFavorites(Number(e.target.value));
                                                }}
                                                aria-label="Custom minimum favorites"
                                                className="w-full bg-slate-50 dark:bg-slate-950/50 border border-slate-200 dark:border-white/10 rounded-xl pl-9 pr-3 py-2 text-xs font-medium focus:ring-1 focus:ring-modtale-accent outline-none shadow-sm [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none relative z-0"
                                            />
                                        </div>
                                    </div>

                                    <div>
                                        <label className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase mb-1.5 block">Downloads</label>
                                        <div className="grid grid-cols-4 gap-1 mb-2">
                                            {[0, 1000, 5000, 10000].map(d => (
                                                <button key={d} onClick={() => { setMinDownloads(d); setCustomDl(''); }} className={`py-1.5 rounded-xl text-[10px] font-bold border transition-colors ${minDownloads === d && customDl === '' ? 'bg-modtale-accent text-white border-transparent shadow-sm' : 'bg-transparent border-slate-200 dark:border-white/10 text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-white/5'}`}>
                                                    {d === 0 ? 'Any' : `${d/1000}k+`}
                                                </button>
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
                                        {showCalendar ? ( <CalendarWidget selectedDate={selectedDateObj} onSelect={handleDateSelect} /> ) : (
                                            <>
                                                <div className="grid grid-cols-4 gap-1 mb-2">
                                                    {[0, 7, 30, 90].map(d => (
                                                        <button key={d} onClick={() => handleDaysAgo(d)} className={`py-1.5 rounded-xl text-[10px] font-bold border transition-colors ${isPresetActive(d) ? 'bg-modtale-accent text-white border-transparent shadow-sm' : 'bg-transparent border-slate-200 dark:border-white/10 text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-white/5'}`}>
                                                            {d === 0 ? 'Any' : `${d}d`}
                                                        </button>
                                                    ))}
                                                </div>
                                                <button onClick={() => setShowCalendar(true)} className={`w-full flex items-center justify-between bg-slate-50 dark:bg-slate-950/50 border border-slate-200 dark:border-white/10 rounded-xl px-3 py-2 text-xs font-medium transition-colors hover:bg-slate-100 dark:hover:bg-white/10 shadow-sm ${filterDate && !isPresetActive(7) && !isPresetActive(30) && !isPresetActive(90) && !isDownloadSort ? 'text-modtale-accent font-bold border-modtale-accent' : 'text-slate-500 dark:text-slate-300'}`}>
                                                    <span className="flex items-center gap-2 pointer-events-none"><CalendarIcon className="w-3.5 h-3.5" /> {filterDate && !isDownloadSort ? `Since ${new Date(filterDate).toLocaleDateString()}` : 'Pick a Date'}</span>
                                                    <ChevronDown className="w-3.5 h-3.5 pointer-events-none" />
                                                </button>
                                            </>
                                        )}
                                    </div>

                                    <div className="pt-2 border-t border-slate-200 dark:border-white/10">
                                        <button onClick={resetAll} className="w-full py-2 bg-red-50 text-red-600 dark:bg-red-500/10 dark:text-red-400 font-bold rounded-xl text-xs hover:bg-red-100 dark:hover:bg-red-500/20 transition-colors flex items-center justify-center gap-2 border border-transparent dark:border-red-500/20">
                                            <RotateCcw className="w-3 h-3 pointer-events-none" /> Reset Filters
                                        </button>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>

                    {isDownloadSort && (
                        <div className="hidden md:flex bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl p-1 h-10 items-center animate-in fade-in slide-in-from-right-4 duration-200 shrink-0 shadow-sm">
                            {[
                                { label: '7d', val: 7 },
                                { label: '30d', val: 30 },
                                { label: '90d', val: 90 },
                                { label: 'All', val: 0 }
                            ].map((opt) => (
                                <button
                                    key={opt.label}
                                    onClick={() => handleDownloadTimeframe(opt.val)}
                                    className={`px-3 py-1 text-xs font-bold rounded-lg transition-colors flex items-center justify-center h-full ${isDownloadPresetActive(opt.val) ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-white/[0.02]'}`}
                                >
                                    {opt.label}
                                </button>
                            ))}
                        </div>
                    )}

                    <div className="flex-1 md:flex-none shrink-0 h-10">
                        <SortDropdown value={sortBy} onChange={(val) => onSortChange(val)} onOpen={handleSortOpen} isMobile={isMobile} />
                    </div>
                </div>
            </div>

            {isDownloadSort && (
                <div className="md:hidden mt-3 flex justify-center h-10">
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl p-1 flex w-full max-w-xs justify-between shadow-sm">
                        {[
                            { label: '7 Days', val: 7 },
                            { label: '30 Days', val: 30 },
                            { label: '3 Months', val: 90 },
                            { label: 'All Time', val: 0 }
                        ].map((opt) => (
                            <button
                                key={opt.label}
                                onClick={() => handleDownloadTimeframe(opt.val)}
                                className={`flex-1 py-1.5 text-xs font-bold rounded-lg transition-colors flex items-center justify-center ${isDownloadPresetActive(opt.val) ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-white/[0.02]'}`}
                            >
                                {opt.label}
                            </button>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
});

BrowseFilters.displayName = 'BrowseFilters';

type SortOption = 'relevance' | 'downloads' | 'favorites' | 'newest' | 'updated' | 'trending' | 'gems' | 'popular';

const getRouteForClassification = (cls: Classification | 'All') => {
    switch(cls) {
        case 'PLUGIN': return '/plugins';
        case 'MODPACK': return '/modpacks';
        case 'SAVE': return '/worlds';
        case 'ART': return '/art';
        case 'DATA': return '/data';
        default: return '/mods';
    }
};

const getResolvedImageUrl = (url?: string) => {
    if (!url) return null;
    return url.startsWith('/api') ? `${BACKEND_URL}${url}` : url;
};

const CategoryPillNav: React.FC<{ selectedClassification: Classification | 'All', onClassificationChange: (cls: Classification | 'All') => void }> = ({ selectedClassification, onClassificationChange }) => {
    const tabsRef = useRef<(HTMLElement | null)[]>([]);
    const navContainerRef = useRef<HTMLDivElement>(null);
    const [pillStyle, setPillStyle] = useState({ left: 0, width: 0, opacity: 0 });
    const [showLeftFade, setShowLeftFade] = useState(false);
    const [showRightFade, setShowRightFade] = useState(false);

    const calculatePillPosition = useCallback(() => {
        const index = PROJECT_TYPES.findIndex(t => t.id === selectedClassification);
        if (index === -1) return;
        const el = tabsRef.current[index];
        if (el && el.offsetWidth > 0) {
            const wrapper = el.offsetParent as HTMLElement;
            const offsetLeft = el.offsetLeft + (wrapper?.offsetLeft || 0);
            setPillStyle({ left: offsetLeft, width: el.offsetWidth, opacity: 1 });
        }
    }, [selectedClassification]);

    const checkScroll = useCallback(() => {
        if (!navContainerRef.current) return;
        const { scrollLeft, scrollWidth, clientWidth } = navContainerRef.current;
        setShowLeftFade(scrollLeft > 0);
        setShowRightFade(scrollLeft + clientWidth < scrollWidth - 1);
    }, []);

    useLayoutEffect(() => {
        calculatePillPosition();
        checkScroll();
        const timer = setTimeout(() => { calculatePillPosition(); checkScroll(); }, 100);
        window.addEventListener('resize', calculatePillPosition);
        window.addEventListener('resize', checkScroll);

        const nav = navContainerRef.current;
        if(nav) nav.addEventListener('scroll', checkScroll);

        return () => {
            clearTimeout(timer);
            window.removeEventListener('resize', calculatePillPosition);
            window.removeEventListener('resize', checkScroll);
            if(nav) nav.removeEventListener('scroll', checkScroll);
        };
    }, [calculatePillPosition, checkScroll]);

    return (
        <div className="relative group flex-shrink-0 max-w-full flex items-center">
            <div className={`absolute left-0 top-0 bottom-0 w-8 bg-gradient-to-r from-slate-50/80 via-slate-50/80 to-transparent dark:from-[#0B1120]/80 dark:via-[#0B1120]/80 pointer-events-none z-30 transition-opacity duration-300 ${showLeftFade ? 'opacity-100' : 'opacity-0'}`} />
            <div className={`absolute right-0 top-0 bottom-0 w-12 bg-gradient-to-l from-slate-50/80 via-slate-50/80 to-transparent dark:from-[#0B1120]/80 dark:via-[#0B1120]/80 pointer-events-none z-30 transition-opacity duration-300 ${showRightFade ? 'opacity-100' : 'opacity-0'}`} />

            <div ref={navContainerRef} className="relative inline-flex h-11 bg-white dark:bg-slate-900 p-1 rounded-2xl border border-slate-200 dark:border-white/10 max-w-full overflow-x-auto snap-x scrollbar-hide z-10 shadow-sm" style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}>
                <style>{` .scrollbar-hide::-webkit-scrollbar { display: none; } `}</style>
                <div className="absolute top-1 bottom-1 bg-modtale-accent shadow-md rounded-xl transition-all duration-300 ease-out z-0" style={{ left: pillStyle.left, width: pillStyle.width, opacity: pillStyle.opacity }} />
                <div className="flex relative z-10 h-full">
                    {PROJECT_TYPES.map((type, index) => {
                        const Icon = type.icon;
                        const isSelected = selectedClassification === type.id;
                        return (
                            <Link
                                key={type.id}
                                to={getRouteForClassification(type.id)}
                                onClick={() => onClassificationChange(type.id as any)}
                                ref={el => tabsRef.current[index] = el}
                                className={`px-3 md:px-4 h-full rounded-xl text-xs md:text-sm font-bold flex items-center justify-center gap-2 transition-colors duration-200 whitespace-nowrap snap-center ${isSelected ? 'text-white' : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200'}`}
                            >
                                <Icon className={`w-3.5 h-3.5 md:w-4 md:h-4 pointer-events-none`} />
                                <span className="inline pointer-events-none">{type.label.replace(' Assets', '').replace('Server ', '')}</span>
                            </Link>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};

interface BrowseProps {
    onModClick: (mod: Mod) => void;
    onModpackClick: (modpack: Modpack) => void;
    onWorldClick: (world: World) => void;
    onAuthorClick: (authorId: string) => void;
    likedModIds: string[];
    onToggleFavoriteMod: (modId: string) => void;
    onToggleFavoriteModpack: (modpackId: string) => void;
    isLoggedIn: boolean;
    initialClassification?: Classification;
}

export const Browse: React.FC<BrowseProps> = ({
                                                  onModClick, onModpackClick, onWorldClick, onAuthorClick,
                                                  likedModIds, onToggleFavoriteMod, onToggleFavoriteModpack, isLoggedIn,
                                                  initialClassification
                                              }) => {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const { isMobile } = useMobile();

    const page = parseInt(searchParams.get('page') || '0');
    const sortBy = (searchParams.get('sort') as SortOption) || 'relevance';
    const activeViewId = searchParams.get('view') || 'all';
    const selectedVersion = searchParams.get('version') || 'Any';
    const minDownloads = parseInt(searchParams.get('minDl') || '0');
    const minFavorites = parseInt(searchParams.get('minFav') || '0');
    const filterDate = searchParams.get('date');
    const rawTags = searchParams.get('tags');
    const selectedTags = useMemo(() => rawTags ? rawTags.split(',').filter(Boolean) : [], [rawTags]);
    const urlSearchTerm = searchParams.get('q') || '';

    const [viewStyle, setViewStyle] = useState<'grid' | 'list' | 'compact'>(() => {
        return (localStorage.getItem('modtale_view_style') as any) || (searchParams.get('style') as any) || 'grid';
    });

    const [searchTerm, setSearchTerm] = useState(urlSearchTerm);

    const [selectedClassification, setSelectedClassification] = useState<Classification | 'All'>(initialClassification || 'All');
    const [totalPages, setTotalPages] = useState(0);
    const [totalItems, setTotalItems] = useState(0);
    const [loading, setLoading] = useState(false);
    const [items, setItems] = useState<(Mod | Modpack | World)[]>([]);
    const [jumpPage, setJumpPage] = useState('');
    const [isTopFilterOpen, setIsTopFilterOpen] = useState(false);
    const [itemsPerPage, setItemsPerPage] = useState(12);

    const abortControllerRef = useRef<AbortController | null>(null);
    const cardsSectionRef = useRef<HTMLDivElement>(null);

    const itemListSchema = useMemo(() => generateItemListSchema(items), [items]);

    const breadcrumbSchema = useMemo(() => {
        const crumbs = getBreadcrumbsForClassification(selectedClassification);
        return generateBreadcrumbSchema(crumbs);
    }, [selectedClassification]);

    const lcpBannerUrl = useMemo(() => {
        if (items.length > 0 && items[0].bannerUrl) {
            return getResolvedImageUrl(items[0].bannerUrl);
        }
        return null;
    }, [items]);

    useEffect(() => {
        if (urlSearchTerm !== searchTerm) {
            setSearchTerm(urlSearchTerm);
        }
    }, [urlSearchTerm]);

    useEffect(() => {
        const handler = setTimeout(() => {
            if (searchTerm !== urlSearchTerm) {
                setSearchParams(prev => {
                    const next = new URLSearchParams(prev);
                    if (searchTerm) {
                        next.set('q', searchTerm);
                    } else {
                        next.delete('q');
                    }
                    next.set('page', '0');
                    return next;
                }, { replace: true });
            }
        }, 500);
        return () => clearTimeout(handler);
    }, [searchTerm, urlSearchTerm, setSearchParams]);

    useEffect(() => {
        if (initialClassification) {
            setSelectedClassification(initialClassification);
        } else {
            setSelectedClassification('All');
        }
    }, [initialClassification]);

    useEffect(() => {
        const handleResize = () => {
            const width = window.innerWidth;
            let cols = 1;
            let rows = 12;

            if (viewStyle === 'grid') {
                cols = width >= 1800 ? 3 : (width >= 768 ? 2 : 1);
                rows = 6;
            } else if (viewStyle === 'compact') {
                cols = width >= 1280 ? 3 : (width >= 768 ? 2 : 1);
                rows = 20;
            } else {
                cols = 1;
                rows = 12;
            }

            const targetSize = cols * rows;

            setItemsPerPage(prev => {
                if (prev !== targetSize) {
                    setSearchParams(prevParams => {
                        const next = new URLSearchParams(prevParams);
                        next.set('page', '0');
                        return next;
                    }, { replace: true });
                    return targetSize;
                }
                return prev;
            });
        };

        handleResize();
        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, [setSearchParams, viewStyle]);

    useEffect(() => {
        if (cardsSectionRef.current && page === 0) {
            const offset = cardsSectionRef.current.offsetTop - 120;
            if (window.scrollY > offset) {
                window.scrollTo({ top: offset, behavior: 'smooth' });
            }
        }
    }, [selectedClassification, selectedTags, urlSearchTerm, selectedVersion, minFavorites, minDownloads, filterDate, activeViewId, sortBy]);

    const fetchData = useCallback(async () => {
        if (abortControllerRef.current) {
            abortControllerRef.current.abort();
        }

        const controller = new AbortController();
        abortControllerRef.current = controller;

        setLoading(true);
        try {
            let categoryParam: string | undefined = undefined;
            if (activeViewId === 'favorites') categoryParam = 'Favorites';
            else if (activeViewId === 'hidden_gems') categoryParam = 'hidden_gems';
            else if (activeViewId === 'popular') categoryParam = 'popular';
            else if (activeViewId === 'trending') categoryParam = 'trending';

            const res = await api.get('/projects', {
                params: {
                    page: page,
                    size: itemsPerPage,
                    classification: selectedClassification !== 'All' ? selectedClassification : undefined,
                    tags: selectedTags.join(','),
                    search: urlSearchTerm,
                    sort: sortBy,
                    gameVersion: selectedVersion !== 'Any' ? selectedVersion : undefined,
                    minRating: undefined,
                    minDownloads: minDownloads > 0 ? minDownloads : undefined,
                    minFavorites: minFavorites > 0 ? minFavorites : undefined,
                    dateRange: filterDate || 'all',
                    category: categoryParam,
                },
                signal: controller.signal
            });

            setItems(res.data?.content || []);
            setTotalPages(res.data?.totalPages || 0);
            setTotalItems(res.data?.totalElements || 0);

        } catch (err: any) {
            if (err.name !== 'Canceled') {
                captureError(err);
                setItems([]);
                setTotalPages(0);
            }
        } finally {
            if (!controller.signal.aborted) {
                setLoading(false);
            }
        }
    }, [page, itemsPerPage, selectedClassification, selectedTags, urlSearchTerm, sortBy, selectedVersion, minDownloads, minFavorites, filterDate, activeViewId]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const updateParams = useCallback((updates: Record<string, string | null>) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            Object.entries(updates).forEach(([key, value]) => {
                if (value === null) {
                    next.delete(key);
                } else {
                    next.set(key, value);
                }
            });
            if (!updates.hasOwnProperty('page')) {
                next.set('page', '0');
            }
            return next;
        });
    }, [setSearchParams]);

    const handleClassificationChange = useCallback((cls: Classification | 'All') => {
        if (cls !== selectedClassification) {
            const route = getRouteForClassification(cls);
            navigate(route);
        }
    }, [selectedClassification, navigate]);

    const handleSortChange = useCallback((newSort: any) => {
        const sortOption = newSort as SortOption;
        const updates: Record<string, string | null> = { sort: sortOption };

        if (activeViewId === 'hidden_gems' || activeViewId === 'favorites') {
        } else {
            if (sortOption === 'popular') updates.view = 'popular';
            else if (sortOption === 'trending') updates.view = 'trending';
            else if (sortOption === 'newest') updates.view = 'new';
            else if (sortOption === 'updated') updates.view = 'updated';
            else {
                if (['popular', 'trending', 'new', 'updated'].includes(activeViewId)) {
                    updates.view = 'all';
                }
            }
        }
        updateParams(updates);
    }, [activeViewId, updateParams]);

    const handleScrollTop = useCallback(() => {
        window.scrollTo({
            top: cardsSectionRef.current?.offsetTop ? cardsSectionRef.current.offsetTop - 120 : 0,
            behavior: 'smooth'
        });
    }, []);

    const handlePageChange = useCallback((p: number) => {
        if (p >= 0 && p < totalPages) {
            setSearchParams(prev => {
                const next = new URLSearchParams(prev);
                next.set('page', p.toString());
                return next;
            });
            handleScrollTop();
        }
    }, [totalPages, setSearchParams, handleScrollTop]);

    const handleJump = (e: React.FormEvent) => {
        e.preventDefault();
        const p = parseInt(jumpPage);
        if (!isNaN(p) && p >= 1 && p <= totalPages) {
            handlePageChange(p - 1);
            setJumpPage('');
        }
    };

    const handleToggleLocal = useCallback((id: string, isModpack: boolean) => {
        if (!isLoggedIn) return;
        onToggleFavoriteMod(id);
        setItems(prev => prev.map(i => i.id === id ? { ...i, favoriteCount: (likedModIds.includes(id) ? Math.max(0, i.favoriteCount - 1) : i.favoriteCount + 1)} : i));
    }, [isLoggedIn, onToggleFavoriteMod, likedModIds]);

    const handleViewStyleChange = useCallback((style: 'grid' | 'list' | 'compact') => {
        setViewStyle(style);
        localStorage.setItem('modtale_view_style', style);
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('style', style);
            next.set('page', '0');
            return next;
        });
    }, [setSearchParams]);

    const getProjectPath = (item: Mod | Modpack | World) => {
        return getProjectUrl(item);
    };

    const getPageTitle = useCallback(() => {
        if (selectedTags.length > 0) return `Tagged: ${selectedTags[0]}${selectedTags.length > 1 ? ` (+${selectedTags.length - 1})` : ''}`;
        if (activeViewId === 'all') return selectedClassification === 'All' ? 'All Projects' : `All ${PROJECT_TYPES.find(t=>t.id===selectedClassification)?.label}`;
        const view = BROWSE_VIEWS.find(v => v.id === activeViewId);
        if (view) return view.label;
        return 'Projects';
    }, [selectedTags, activeViewId, selectedClassification]);

    const getPageNumbers = () => { const total = totalPages; const current = page + 1; const delta = 2; const range = []; const rangeWithDots: (number | string)[] = []; let l; range.push(1); for (let i = current - delta; i <= current + delta; i++) { if (i < total && i > 1) { range.push(i); } } range.push(total); const uniqueRange = [...new Set(range)].sort((a, b) => a - b); for (const i of uniqueRange) { if (l) { if (i - l === 2) { rangeWithDots.push(l + 1); } else if (i - l !== 1) { rangeWithDots.push('...'); } } rangeWithDots.push(i); l = i; } return rangeWithDots; };

    const resetFilters = useCallback(() => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.delete('version');
            next.delete('minDl');
            next.delete('minFav');
            next.delete('date');
            next.delete('tags');
            next.set('page', '0');
            return next;
        });
        setIsTopFilterOpen(false);
    }, [setSearchParams]);

    const createViewUrl = (viewId: string) => {
        const search = new URLSearchParams(searchParams);
        search.set('view', viewId);
        if (viewId === 'hidden_gems') search.set('sort', 'favorites');
        else if (viewId === 'popular') search.set('sort', 'popular');
        else if (viewId === 'trending') search.set('sort', 'trending');
        else if (viewId === 'new') search.set('sort', 'newest');
        else if (viewId === 'updated') search.set('sort', 'updated');
        else search.set('sort', 'relevance');
        search.delete('page');
        return `?${search.toString()}`;
    };

    const createPageUrl = (p: number) => {
        const search = new URLSearchParams(searchParams);
        search.set('page', p.toString());
        return `?${search.toString()}`;
    };

    const activeFilterCount = (selectedVersion !== 'Any' ? 1 : 0) + (minDownloads > 0 ? 1 : 0) + (minFavorites > 0 ? 1 : 0) + (filterDate ? 1 : 0);
    const seoContent = getCategorySEO(selectedClassification);

    const categoryPills = <CategoryPillNav selectedClassification={selectedClassification} onClassificationChange={handleClassificationChange} />;

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-[#0B1120] text-slate-900 dark:text-slate-300 relative transition-colors duration-300">
            <Helmet>
                <title>{seoContent.title}</title>
                <meta name="description" content={seoContent.description} />
                <meta name="keywords" content={seoContent.keywords} />
                {lcpBannerUrl && <link rel="preload" as="image" href={lcpBannerUrl} fetchPriority="high" />}
                {itemListSchema && <script type="application/ld+json">{JSON.stringify(itemListSchema)}</script>}
                {breadcrumbSchema && <script type="application/ld+json">{JSON.stringify(breadcrumbSchema)}</script>}
            </Helmet>

            <main className="max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 pt-2 pb-8 transition-[max-width] duration-300">
                <div className="flex flex-col md:flex-row gap-8">
                    <div className="hidden md:block w-60 flex-shrink-0 z-30 sticky top-24 pt-3 h-fit space-y-4">
                        <div className="mb-6">
                            <div className="relative group h-11">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-500 pointer-events-none z-10 group-focus-within:text-modtale-accent transition-colors" />
                                <input type="text" className="block w-full pl-9 pr-3 h-full rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 text-sm shadow-sm focus:ring-2 focus:ring-modtale-accent outline-none text-slate-900 dark:text-white transition-all relative z-0" aria-label="Quick search" placeholder="Search projects..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} />
                            </div>
                        </div>
                        <div className="p-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-2xl shadow-sm animate-in fade-in slide-in-from-left-4 duration-700">
                            <h2 className="text-xs font-black uppercase text-slate-500 dark:text-slate-400 mb-3 tracking-widest px-2 drop-shadow-sm">Browse</h2>
                            <div className="space-y-1.5">
                                {BROWSE_VIEWS.map(v => (
                                    <Link
                                        key={v.id}
                                        to={createViewUrl(v.id)}
                                        className={`block w-full text-left px-4 py-3 rounded-xl text-sm font-bold transition-all ${activeViewId === v.id ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-white/5'}`}
                                    >
                                        <span className="pointer-events-none">{v.label}</span>
                                    </Link>
                                ))}
                            </div>
                        </div>
                    </div>

                    <div className="flex-1 min-h-[500px]" ref={cardsSectionRef}>
                        <div className="sticky top-24 z-50 mb-4 bg-slate-50/80 dark:bg-[#0B1120]/80 backdrop-blur-xl -mx-4 sm:mx-0 px-4 sm:px-0 pt-3 pb-3 border-b border-slate-200 dark:border-white/10 transition-all">
                            <BrowseFilters
                                categoryPills={categoryPills}
                                pageTitle={getPageTitle()}
                                totalItems={totalItems}
                                loading={loading}
                                sortBy={sortBy}
                                onSortChange={handleSortChange}
                                selectedTags={selectedTags}
                                onToggleTag={useCallback((tag: string) => {
                                    const newTags = selectedTags.includes(tag)
                                        ? selectedTags.filter(t => t !== tag)
                                        : [...selectedTags, tag];
                                    updateParams({ tags: newTags.length > 0 ? newTags.join(',') : null });
                                }, [selectedTags, updateParams])}
                                onClearTags={useCallback(() => updateParams({ tags: null }), [updateParams])}
                                activeFilterCount={activeFilterCount}
                                onResetFilters={resetFilters}
                                isFilterOpen={isTopFilterOpen}
                                onToggleFilterMenu={useCallback(() => setIsTopFilterOpen(prev => !prev), [])}
                                searchTerm={searchTerm}
                                onSearchChange={setSearchTerm}
                                selectedVersion={selectedVersion}
                                setSelectedVersion={useCallback((v: string) => updateParams({ version: v !== 'Any' ? v : null }), [updateParams])}
                                minFavorites={minFavorites}
                                setMinFavorites={useCallback((v: number) => updateParams({ minFav: v > 0 ? v.toString() : null }), [updateParams])}
                                minDownloads={minDownloads}
                                setMinDownloads={useCallback((v: number) => updateParams({ minDl: v > 0 ? v.toString() : null }), [updateParams])}
                                filterDate={filterDate}
                                setFilterDate={useCallback((v: string | null) => updateParams({ date: v }), [updateParams])}
                                setPage={handlePageChange}
                                isMobile={isMobile}
                                viewStyle={viewStyle}
                                onViewStyleChange={handleViewStyleChange}
                            />
                        </div>

                        {loading ? (
                            <div className={viewStyle === 'grid' ? "grid grid-cols-1 md:grid-cols-2 min-[1800px]:grid-cols-3 gap-4 md:gap-5 mt-4" : viewStyle === 'compact' ? "grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3 mt-4" : "space-y-4 mt-4"}>
                                {[...Array(itemsPerPage)].map((_, i) => (
                                    <div
                                        key={i}
                                        className={`${viewStyle === 'grid' ? 'h-[154px]' : viewStyle === 'list' ? 'h-32' : 'h-16'} bg-white/40 dark:bg-white/5 backdrop-blur-md rounded-2xl animate-pulse border border-slate-200 dark:border-white/10 relative overflow-hidden`}
                                    >
                                        <div className="absolute inset-0 -translate-x-full animate-[shimmer_1.5s_infinite] bg-gradient-to-r from-transparent via-white/20 to-transparent"></div>
                                    </div>
                                ))}
                            </div>
                        ) : items.length > 0 ? (
                            <div className={viewStyle === 'grid' ? "grid grid-cols-1 md:grid-cols-2 min-[1800px]:grid-cols-3 gap-4 md:gap-6 mt-4" : viewStyle === 'compact' ? "grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3 mt-4" : "space-y-4 mt-4"}>
                                {items.map((item, index) => {
                                    const isPriority = index < 6;
                                    return (
                                        <div
                                            key={item.id}
                                            className={isPriority ? "" : "animate-in fade-in zoom-in-95 duration-500 fill-mode-backwards"}
                                            style={isPriority ? {} : { animationDelay: `${(index - 6) * 50}ms` }}
                                        >
                                            <ModCard
                                                mod={item}
                                                path={getProjectPath(item)}
                                                isFavorite={likedModIds.includes(item.id)}
                                                onToggleFavorite={() => handleToggleLocal(item.id, item.classification === 'MODPACK')}
                                                isLoggedIn={isLoggedIn}
                                                onClick={() => { if(item.classification === 'MODPACK') onModpackClick(item as Modpack); else if (item.classification === 'SAVE') onWorldClick(item as World); else onModClick(item as Mod); }}
                                                priority={isPriority}
                                                viewStyle={viewStyle}
                                            />
                                        </div>
                                    );
                                })}
                            </div>
                        ) : (
                            <div className="mt-8 animate-in fade-in zoom-in-95 duration-500">
                                <EmptyState
                                    icon={PackageSearch}
                                    title="No matches found"
                                    message="Try adjusting your search terms or filters to find what you're looking for."
                                />
                            </div>
                        )}

                        {totalPages > 1 && (
                            <div className="mt-12 flex flex-col md:flex-row justify-center items-center gap-12 pb-12 animate-in fade-in slide-in-from-bottom-4 duration-700 delay-300">
                                <div className="flex items-center gap-2">
                                    {page === 0 ? (
                                        <button disabled className="text-slate-400 opacity-20 cursor-not-allowed p-2"><ChevronLeft className="w-5 h-5" /></button>
                                    ) : (
                                        <Link to={createPageUrl(page - 1)} onClick={handleScrollTop} className="text-slate-500 hover:text-modtale-accent transition-all p-2"><ChevronLeft className="w-5 h-5" /></Link>
                                    )}

                                    <div className="hidden sm:flex items-center gap-1">
                                        {getPageNumbers().map((p, idx) => (
                                            typeof p === 'number' ? (
                                                <Link
                                                    key={p}
                                                    to={createPageUrl(p - 1)}
                                                    onClick={handleScrollTop}
                                                    className={`w-9 h-9 flex items-center justify-center text-sm font-bold transition-all ${page === p - 1 ? 'text-modtale-accent' : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'}`}
                                                >
                                                    {p}
                                                </Link>
                                            ) : (
                                                <span key={`dots-${idx}`} className="px-2 text-slate-400">...</span>
                                            )
                                        ))}
                                    </div>

                                    {page === totalPages - 1 ? (
                                        <button disabled className="text-slate-400 opacity-20 cursor-not-allowed p-2"><ChevronRight className="w-5 h-5" /></button>
                                    ) : (
                                        <Link to={createPageUrl(page + 1)} onClick={handleScrollTop} className="text-slate-500 hover:text-modtale-accent transition-all p-2"><ChevronRight className="w-5 h-5" /></Link>
                                    )}
                                </div>

                                <form onSubmit={handleJump} className="flex items-center gap-4 group">
                                    <span className="text-[10px] font-black uppercase text-slate-400 tracking-widest pointer-events-none group-focus-within:text-modtale-accent transition-colors">Go to page</span>
                                    <div className="relative">
                                        <input
                                            type="number"
                                            min={1}
                                            max={totalPages}
                                            value={jumpPage}
                                            onChange={(e) => setJumpPage(e.target.value)}
                                            className="w-12 h-8 bg-transparent border-b border-slate-300 dark:border-white/10 text-center text-sm font-bold text-slate-900 dark:text-white focus:border-modtale-accent focus:outline-none transition-all [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
                                            placeholder="..."
                                        />
                                        <button
                                            type="submit"
                                            disabled={!jumpPage}
                                            className="absolute -right-8 top-1/2 -translate-y-1/2 text-slate-400 hover:text-modtale-accent disabled:opacity-0 transition-all active:scale-90"
                                        >
                                            <CornerDownLeft className="w-3.5 h-3.5" />
                                        </button>
                                    </div>
                                </form>
                            </div>
                        )}
                    </div>
                </div>
            </main>
        </div>
    );
};