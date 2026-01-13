import React, { useState, useRef, useEffect } from 'react';
import { Filter, Tag, ArrowDownUp, ChevronDown, Check, X, Search, Star, RotateCcw, Calendar as CalendarIcon, Download, ChevronLeft, ChevronRight } from 'lucide-react';
import { GLOBAL_TAGS } from '../../data/categories';

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
        <div className="p-3 bg-slate-50 dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-white/10 animate-in fade-in zoom-in-95 duration-200">
            <div className="flex justify-between items-center mb-3">
                <button onClick={() => changeMonth(-1)} className="p-1 hover:bg-slate-200 dark:hover:bg-white/10 rounded-full transition-colors"><ChevronLeft className="w-4 h-4 text-slate-500" /></button>
                <span className="text-sm font-bold text-slate-700 dark:text-slate-200">{viewDate.toLocaleString('default', { month: 'long', year: 'numeric' })}</span>
                <button onClick={() => changeMonth(1)} disabled={viewDate.getMonth() === today.getMonth() && viewDate.getFullYear() === today.getFullYear()} className="p-1 hover:bg-slate-200 dark:hover:bg-white/10 rounded-full transition-colors disabled:opacity-30 disabled:cursor-not-allowed"><ChevronRight className="w-4 h-4 text-slate-500" /></button>
            </div>
            <div className="grid grid-cols-7 gap-1 text-center mb-2">{['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'].map(d => (<div key={d} className="text-[10px] font-bold text-slate-400 uppercase">{d}</div>))}</div>
            <div className="grid grid-cols-7 gap-1">{days.map((d, i) => (d ? (<button key={i} disabled={isDisabled(d)} onClick={() => !isDisabled(d) && onSelect(new Date(year, month, d))} className={`w-7 h-7 flex items-center justify-center rounded-full text-xs font-medium transition-all ${isDisabled(d) ? 'text-slate-300 dark:text-slate-600 cursor-not-allowed opacity-40' : isSelected(d) ? 'bg-modtale-accent text-white shadow-md shadow-modtale-accent/30' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-white/10'}`}>{d}</button>) : <div key={i} />))}</div>
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
        { id: 'rating', label: 'Rating', mobileOnly: false },
        { id: 'newest', label: 'Newest', mobileOnly: true },
        { id: 'updated', label: 'Updated', mobileOnly: true }
    ];

    const visibleOptions = options.filter(opt => isMobile || !opt.mobileOnly);

    useEffect(() => { const handleClick = (e: MouseEvent) => { if (containerRef.current && !containerRef.current.contains(e.target as Node)) setIsOpen(false); }; document.addEventListener('mousedown', handleClick); return () => document.removeEventListener('mousedown', handleClick); }, []);
    const handleToggle = () => { if (!isOpen) onOpen(); setIsOpen(!isOpen); };
    const currentLabel = options.find(o => o.id === value)?.label || 'Sort';

    return (
        <div className="relative w-full md:w-auto" ref={containerRef}>
            <button onClick={handleToggle} className="w-full md:w-auto h-10 flex items-center gap-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-lg px-3 text-sm font-bold text-slate-700 dark:text-slate-300 hover:border-modtale-accent hover:shadow-sm transition-all whitespace-nowrap min-w-[120px] justify-between">
                <div className="flex items-center gap-2"><ArrowDownUp className="w-4 h-4 text-slate-400" /><span className="truncate">{currentLabel}</span></div>
                <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform flex-shrink-0 ${isOpen ? 'rotate-180' : ''}`} />
            </button>
            {isOpen && <div className="absolute right-0 md:right-auto md:left-0 top-full mt-2 w-48 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl py-2 z-[70]">{visibleOptions.map(opt => (<button key={opt.id} onClick={() => { onChange(opt.id); setIsOpen(false); }} className={`w-full text-left px-4 py-2.5 text-sm font-medium flex justify-between items-center transition-colors ${value === opt.id ? 'bg-modtale-accent/10 text-modtale-accent font-bold' : 'text-slate-700 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-white/5'}`}>{opt.label}{value === opt.id && <Check className="w-3 h-3" />}</button>))}</div>}
        </div>
    );
};

const FilterDropdown = ({ label, value, options, onChange }: { label: string, value: string, options: string[], onChange: (val: string) => void }) => {
    const [isOpen, setIsOpen] = useState(false);
    return (
        <div className="relative">
            <label className="text-xs font-bold text-slate-400 uppercase mb-1.5 block">{label}</label>
            <button onClick={() => setIsOpen(!isOpen)} className="w-full text-left px-3 py-2 rounded-lg text-sm font-bold bg-slate-50 dark:bg-white/5 border border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-300 flex justify-between items-center hover:border-modtale-accent transition-colors">{value}<ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${isOpen ? 'rotate-180' : ''}`} /></button>
            {isOpen && <div className="absolute top-full mt-1 left-0 w-full bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-lg shadow-xl z-50 max-h-48 overflow-y-auto py-1 custom-scrollbar">{options.map(opt => <button key={opt} onClick={() => { onChange(opt); setIsOpen(false); }} className={`w-full text-left px-3 py-2 text-xs font-bold transition-colors flex justify-between items-center ${value === opt ? 'bg-modtale-accent/10 text-modtale-accent' : 'text-slate-600 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-white/5'}`}>{opt}{value === opt && <Check className="w-3 h-3" />}</button>)}</div>}
        </div>
    );
};

interface HomeFiltersProps {
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
    minRating: number;
    setMinRating: (v: number) => void;
    minDownloads: number;
    setMinDownloads: (v: number) => void;
    filterDate: string | null;
    setFilterDate: (d: string | null) => void;
    setPage: (p: number) => void;
    showMiniSearch: boolean;
    isMobile: boolean;
}

export const HomeFilters: React.FC<HomeFiltersProps> = ({
                                                            pageTitle, totalItems, loading, sortBy, onSortChange,
                                                            selectedTags, onToggleTag, onClearTags, activeFilterCount, onResetFilters,
                                                            isFilterOpen, onToggleFilterMenu, searchTerm, onSearchChange,
                                                            selectedVersion, setSelectedVersion, minRating, setMinRating, minDownloads, setMinDownloads, filterDate, setFilterDate, setPage, showMiniSearch,
                                                            isMobile
                                                        }) => {
    const [isTagsOpen, setIsTagsOpen] = useState(false);
    const tagRef = useRef<HTMLDivElement>(null);
    const filterRef = useRef<HTMLDivElement>(null);
    const [customDl, setCustomDl] = useState('');
    const [showCalendar, setShowCalendar] = useState(false);
    const [selectedDateObj, setSelectedDateObj] = useState<Date | null>(null);

    useEffect(() => {
        const handleClick = (e: MouseEvent) => {
            if (tagRef.current && !tagRef.current.contains(e.target as Node)) setIsTagsOpen(false);
            if (filterRef.current && !filterRef.current.contains(e.target as Node)) {
                if(isFilterOpen) onToggleFilterMenu();
            }
        };
        document.addEventListener('mousedown', handleClick); return () => document.removeEventListener('mousedown', handleClick);
    }, [isFilterOpen, onToggleFilterMenu]);

    const handleSortOpen = () => { if (isFilterOpen) onToggleFilterMenu(); if (isTagsOpen) setIsTagsOpen(false); };
    const handleDateSelect = (date: Date) => { const isoDate = date.toISOString().split('T')[0]; setFilterDate(isoDate); setSelectedDateObj(date); setShowCalendar(false); setPage(0); };
    const handleDaysAgo = (days: number) => {
        if (days === 0) { setFilterDate(null); setSelectedDateObj(null); }
        else { const d = new Date(); d.setDate(d.getDate() - days); setFilterDate(d.toISOString().split('T')[0]); setSelectedDateObj(null); }
        setPage(0);
    };
    const isPresetActive = (days: number) => {
        if (days === 0) return !filterDate;
        if (!filterDate) return false;
        const target = new Date(); target.setDate(target.getDate() - days); const targetStr = target.toISOString().split('T')[0];
        return filterDate === targetStr && !selectedDateObj;
    };
    const resetAll = () => { onResetFilters(); setCustomDl(''); setFilterDate(null); setSelectedDateObj(null); setShowCalendar(false); };

    return (
        <div className="w-full">
            <div className={`md:hidden w-full transition-all duration-300 ease-in-out overflow-hidden ${showMiniSearch ? 'max-h-14 opacity-100 mb-3' : 'max-h-0 opacity-0 mb-0 pointer-events-none'}`}>
                <div className="relative group w-full">
                    <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400 dark:text-slate-300 group-focus-within:text-modtale-accent transition-colors" />
                    <input
                        type="text"
                        className="block w-full pl-9 pr-3 h-10 rounded-lg bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 text-sm shadow-sm focus:ring-2 focus:ring-modtale-accent focus:border-modtale-accent text-slate-900 dark:text-white"
                        placeholder="Search projects..."
                        value={searchTerm}
                        onChange={(e) => onSearchChange(e.target.value)}
                    />
                </div>
            </div>

            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
                <div className="hidden md:block">
                    <h2 className="text-2xl font-black text-slate-900 dark:text-white flex items-center gap-2">{pageTitle}</h2>
                    <span className="text-xs font-bold text-slate-400 uppercase tracking-wide mt-0.5 block">{loading ? 'Searching...' : `${totalItems.toLocaleString()} Results`}</span>
                </div>

                <div className="flex items-center gap-2 w-full md:w-auto">
                    <div className="relative flex-1 md:flex-none" ref={tagRef}>
                        <button onClick={() => { setIsTagsOpen(!isTagsOpen); if(isFilterOpen) onToggleFilterMenu(); }} className={`w-full md:w-auto h-10 flex items-center justify-between md:justify-start gap-2 border rounded-lg px-3 md:px-4 text-xs md:text-sm font-bold transition-all whitespace-nowrap ${selectedTags.length > 0 ? 'bg-modtale-accent text-white border-modtale-accent shadow-sm' : 'bg-white dark:bg-slate-800 border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-300 hover:border-modtale-accent'}`}>
                            <div className="flex items-center gap-2"><Tag className="w-3.5 h-3.5" /> <span>Tags</span></div>
                            {selectedTags.length > 0 && <span className="bg-white/20 px-1.5 rounded text-[10px]">{selectedTags.length}</span>}
                        </button>
                        {isTagsOpen && (
                            <div className="absolute top-12 left-0 md:left-auto md:right-0 w-72 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl shadow-2xl z-[70] p-4 animate-in fade-in slide-in-from-top-2">
                                <div className="flex justify-between items-center mb-3">
                                    <h3 className="font-bold text-sm text-slate-900 dark:text-white">Filter by Tag</h3>
                                    {selectedTags.length > 0 && <button onClick={onClearTags} className="text-xs text-red-500 hover:underline font-bold">Clear All</button>}
                                </div>
                                <div className="flex flex-wrap gap-2 max-h-60 overflow-y-auto pr-2 custom-scrollbar">
                                    <style>{` .custom-scrollbar::-webkit-scrollbar { width: 4px; } .custom-scrollbar::-webkit-scrollbar-track { background: transparent; } .custom-scrollbar::-webkit-scrollbar-thumb { background-color: rgba(156, 163, 175, 0.5); border-radius: 20px; } `}</style>
                                    {GLOBAL_TAGS.map(tag => (
                                        <button key={tag} onClick={() => onToggleTag(tag)} className={`px-3 py-1.5 rounded-lg text-xs font-bold border transition-colors flex items-center gap-1.5 ${selectedTags.includes(tag) ? 'bg-modtale-accent text-white border-modtale-accent' : 'bg-white dark:bg-white/5 border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-400 hover:border-modtale-accent'}`}>
                                            {tag} {selectedTags.includes(tag) && <Check className="w-3 h-3" />}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="relative flex-1 md:flex-none" ref={filterRef}>
                        <button onClick={onToggleFilterMenu} className={`w-full md:w-auto h-10 flex items-center justify-between md:justify-start gap-2 border rounded-lg px-3 md:px-4 text-xs md:text-sm font-bold transition-all whitespace-nowrap ${isFilterOpen || activeFilterCount > 0 ? 'bg-modtale-accent text-white border-modtale-accent shadow-sm' : 'bg-white dark:bg-slate-800 border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-300 hover:border-modtale-accent'}`}>
                            <div className="flex items-center gap-2"><Filter className="w-3.5 h-3.5" /> <span>Filters</span></div>
                            {activeFilterCount > 0 && <span className="bg-white/20 px-1.5 rounded text-[10px]">{activeFilterCount}</span>}
                        </button>

                        {isFilterOpen && (
                            <div className="absolute top-12 left-1/2 -translate-x-1/2 md:translate-x-0 md:left-0 w-72 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl shadow-2xl z-[70] animate-in fade-in slide-in-from-top-2">
                                <div className="p-4 border-b border-slate-100 dark:border-white/5">
                                    <h3 className="font-bold text-sm text-slate-900 dark:text-white">Refine Results</h3>
                                </div>
                                <div className="p-4 space-y-5">
                                    <FilterDropdown label="Game Version" value={selectedVersion} options={['Any', '1.0-SNAPSHOT']} onChange={(val) => {setSelectedVersion(val); setPage(0);}} />

                                    <div>
                                        <label className="text-xs font-bold text-slate-400 uppercase mb-1.5 block">Minimum Rating</label>
                                        <div className="flex gap-1">
                                            {[1, 2, 3, 4].map(r => (
                                                <button key={r} onClick={() => { setMinRating(r === minRating ? 0 : r); setPage(0); }} className={`flex-1 py-2 rounded-lg text-xs font-bold border transition-all flex justify-center items-center ${minRating === r ? 'bg-modtale-accent text-white border-modtale-accent' : 'bg-white dark:bg-white/5 border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-400 hover:border-modtale-accent'}`}>
                                                    {r}+ <Star className="w-3 h-3 ml-0.5 fill-current" />
                                                </button>
                                            ))}
                                        </div>
                                    </div>

                                    <div>
                                        <label className="text-xs font-bold text-slate-400 uppercase mb-1.5 block">Downloads</label>
                                        <div className="grid grid-cols-4 gap-1 mb-2">
                                            {[0, 1000, 5000, 10000].map(d => (
                                                <button key={d} onClick={() => { setMinDownloads(d); setCustomDl(''); setPage(0); }} className={`py-1.5 rounded-lg text-[10px] font-bold border transition-all ${minDownloads === d && customDl === '' ? 'bg-slate-800 dark:bg-white text-white dark:text-black border-transparent' : 'bg-transparent border-slate-200 dark:border-white/10 text-slate-500 hover:border-slate-400'}`}>
                                                    {d === 0 ? 'Any' : `${d/1000}k+`}
                                                </button>
                                            ))}
                                        </div>
                                        <div className="relative">
                                            <Download className="absolute left-3 top-2 w-3.5 h-3.5 text-slate-400" />
                                            <input type="number" placeholder="Custom min downloads..." value={customDl} onChange={e => { setCustomDl(e.target.value); setMinDownloads(Number(e.target.value)); setPage(0); }} className="w-full bg-slate-50 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-lg pl-9 pr-3 py-1.5 text-xs font-medium focus:ring-1 focus:ring-modtale-accent focus:border-modtale-accent outline-none [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none" />
                                        </div>
                                    </div>

                                    <div>
                                        <div className="flex justify-between items-center mb-1.5">
                                            <label className="text-xs font-bold text-slate-400 uppercase block">Last Updated</label>
                                        </div>
                                        {showCalendar ? ( <CalendarWidget selectedDate={selectedDateObj} onSelect={handleDateSelect} /> ) : (
                                            <>
                                                <div className="grid grid-cols-4 gap-1 mb-2">
                                                    {[0, 7, 30, 90].map(d => (
                                                        <button key={d} onClick={() => handleDaysAgo(d)} className={`py-1.5 rounded-lg text-[10px] font-bold border transition-all ${isPresetActive(d) ? 'bg-slate-800 dark:bg-white text-white dark:text-black border-transparent shadow-sm' : 'bg-transparent border-slate-200 dark:border-white/10 text-slate-500 hover:border-slate-400'}`}>
                                                            {d === 0 ? 'Any' : `${d}d`}
                                                        </button>
                                                    ))}
                                                </div>
                                                <button onClick={() => setShowCalendar(true)} className={`w-full flex items-center justify-between bg-slate-50 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors hover:border-modtale-accent ${filterDate && !isPresetActive(7) && !isPresetActive(30) && !isPresetActive(90) ? 'text-modtale-accent font-bold border-modtale-accent' : 'text-slate-500 dark:text-slate-400'}`}>
                                                    <span className="flex items-center gap-2"><CalendarIcon className="w-3.5 h-3.5" /> {filterDate ? `Since ${new Date(filterDate).toLocaleDateString()}` : 'Pick a Date'}</span>
                                                    <ChevronDown className="w-3.5 h-3.5" />
                                                </button>
                                            </>
                                        )}
                                    </div>

                                    <div className="pt-2 border-t border-slate-100 dark:border-white/5">
                                        <button onClick={resetAll} className="w-full py-2 bg-red-50 text-red-500 dark:bg-red-500/10 dark:text-red-400 font-bold rounded-lg text-xs hover:bg-red-100 dark:hover:bg-red-500/20 transition-colors flex items-center justify-center gap-2">
                                            <RotateCcw className="w-3 h-3" /> Reset Filters
                                        </button>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="flex-1 md:flex-none">
                        <SortDropdown value={sortBy} onChange={onSortChange} onOpen={handleSortOpen} isMobile={isMobile} />
                    </div>
                </div>
            </div>
        </div>
    );
};