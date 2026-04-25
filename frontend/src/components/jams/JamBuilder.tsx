import React, { useState, useEffect, useRef } from 'react';
import { Settings, Plus, Trash2, List, Trophy, FileText, Scale, Save, CheckCircle2, AlertCircle, LayoutGrid, Edit3, Clock, Check, X, Shield, Calendar, Play, ChevronDown, Loader2, BookOpen, Wand2, ChevronLeft, ChevronRight, Users, UserPlus, User as UserIcon, Link2 } from 'lucide-react';
import { JamLayout } from '@/components/jams/JamLayout.tsx';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/cjs/styles/prism';
import { Spinner } from '@/components/ui/Spinner.tsx';
import { api, BACKEND_URL } from '@/utils/api';

const CalendarWidget = ({ viewDate, setViewDate, selectedDate, onSelect, minDate }: { viewDate: Date, setViewDate: (d: Date) => void, selectedDate: Date, onSelect: (d: Date) => void, minDate: Date }) => {
    const getDaysInMonth = (year: number, month: number) => new Date(year, month + 1, 0).getDate();
    const getFirstDay = (year: number, month: number) => new Date(year, month, 1).getDay();
    const year = viewDate.getFullYear();
    const month = viewDate.getMonth();
    const daysInMonth = getDaysInMonth(year, month);
    const startDay = getFirstDay(year, month);

    const days = [];
    for (let i = 0; i < startDay; i++) days.push(null);
    for (let i = 1; i <= daysInMonth; i++) days.push(i);

    const changeMonth = (delta: number) => { setViewDate(new Date(year, month + delta, 1)); };

    const isSelected = (d: number) => { return selectedDate.getDate() === d && selectedDate.getMonth() === month && selectedDate.getFullYear() === year; };

    const isDisabled = (d: number) => {
        const checkDate = new Date(year, month, d, 23, 59, 59, 999);
        return checkDate.getTime() < minDate.getTime();
    };

    return (
        <div className="p-3 bg-slate-50 dark:bg-slate-800/50 rounded-xl border border-slate-200 dark:border-white/5">
            <div className="flex justify-between items-center mb-3">
                <button type="button" onClick={() => changeMonth(-1)} className="p-1 hover:bg-slate-200 dark:hover:bg-white/10 rounded-full transition-colors"><ChevronLeft className="w-4 h-4 text-slate-500" /></button>
                <span className="text-sm font-bold text-slate-700 dark:text-slate-200">{viewDate.toLocaleString('default', { month: 'long', year: 'numeric' })}</span>
                <button type="button" onClick={() => changeMonth(1)} className="p-1 hover:bg-slate-200 dark:hover:bg-white/10 rounded-full transition-colors"><ChevronRight className="w-4 h-4 text-slate-500" /></button>
            </div>
            <div className="grid grid-cols-7 gap-1 text-center mb-2">{['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'].map(d => (<div key={d} className="text-[10px] font-bold text-slate-400 uppercase">{d}</div>))}</div>
            <div className="grid grid-cols-7 gap-1">
                {days.map((d, i) => (
                    d ? (
                        <button
                            key={i}
                            type="button"
                            disabled={isDisabled(d)}
                            onClick={() => !isDisabled(d) && onSelect(new Date(year, month, d))}
                            className={`w-8 h-8 mx-auto flex items-center justify-center rounded-full text-xs font-medium transition-all ${isDisabled(d) ? 'text-slate-300 dark:text-slate-600 cursor-not-allowed opacity-40' : isSelected(d) ? 'bg-modtale-accent text-white shadow-md shadow-modtale-accent/30 scale-110' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-white/10'}`}
                        >
                            {d}
                        </button>
                    ) : <div key={i} />
                ))}
            </div>
        </div>
    );
};

const TimeDropdown = ({ value, options, onChange }: { value: string | number, options: {label: string, value: string | number}[], onChange: (val: any) => void }) => {
    const [isOpen, setIsOpen] = useState(false);
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (ref.current && !ref.current.contains(e.target as Node)) setIsOpen(false);
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const selectedLabel = options.find(o => o.value === value)?.label || value;

    return (
        <div className="relative" ref={ref}>
            <button
                type="button"
                onClick={() => setIsOpen(!isOpen)}
                className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-lg px-3 py-1.5 text-sm font-bold text-slate-700 dark:text-white outline-none focus:border-modtale-accent text-center min-w-[3.5rem] flex justify-center items-center hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors shadow-sm"
            >
                {selectedLabel}
            </button>
            {isOpen && (
                <div className="absolute bottom-full mb-2 left-1/2 -translate-x-1/2 bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-[150] max-h-48 overflow-y-auto custom-scrollbar p-1 min-w-[4rem]">
                    {options.map((opt) => (
                        <button
                            key={opt.value}
                            type="button"
                            onClick={() => { onChange(opt.value); setIsOpen(false); }}
                            className={`w-full text-center px-2 py-2 text-sm rounded-lg hover:bg-slate-50 dark:hover:bg-white/5 transition-colors ${value === opt.value ? 'bg-modtale-accent/10 text-modtale-accent font-bold' : 'text-slate-700 dark:text-slate-300 font-medium'}`}
                        >
                            {opt.label}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};

const CustomDateTimePicker: React.FC<{ label: string, icon: any, value: string, minDate?: string, onChange: (v: string) => void }> = ({ label, icon: Icon, value, minDate, onChange }) => {
    const [isOpen, setIsOpen] = useState(false);
    const containerRef = useRef<HTMLDivElement>(null);

    const isPassed = value && new Date(value).getTime() <= Date.now();

    const twentyFourHoursFromNow = new Date(Date.now() + 24 * 60 * 60 * 1000);
    const effectiveMinDate = minDate ? new Date(Math.max(twentyFourHoursFromNow.getTime(), new Date(minDate).getTime())) : twentyFourHoursFromNow;

    const initialDate = value ? new Date(value) : effectiveMinDate;
    const [tempDate, setTempDate] = useState<Date>(initialDate);
    const [viewDate, setViewDate] = useState<Date>(initialDate);

    useEffect(() => {
        if (isOpen) {
            const start = value ? new Date(value) : effectiveMinDate;
            setTempDate(start);
            setViewDate(start);
        }
    }, [isOpen, value, effectiveMinDate]);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) setIsOpen(false);
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const handleDateSelect = (d: Date) => {
        const newDate = new Date(tempDate);
        newDate.setFullYear(d.getFullYear(), d.getMonth(), d.getDate());
        setTempDate(newDate);
    };

    const currentHour24 = tempDate.getHours();
    const displayHour = currentHour24 % 12 || 12;
    const currentMinute = tempDate.getMinutes();
    const currentPeriod = currentHour24 >= 12 ? 'PM' : 'AM';

    const handleHourChange = (h12: number) => {
        let h24 = h12;
        if (currentPeriod === 'AM' && h12 === 12) h24 = 0;
        if (currentPeriod === 'PM' && h12 !== 12) h24 += 12;
        const newDate = new Date(tempDate);
        newDate.setHours(h24);
        setTempDate(newDate);
    };

    const handleMinuteChange = (m: number) => {
        const newDate = new Date(tempDate);
        newDate.setMinutes(m);
        setTempDate(newDate);
    };

    const handlePeriodChange = (p: 'AM' | 'PM') => {
        if (p === currentPeriod) return;
        let h24 = displayHour;
        if (p === 'AM' && displayHour === 12) h24 = 0;
        if (p === 'PM' && displayHour !== 12) h24 += 12;
        const newDate = new Date(tempDate);
        newDate.setHours(h24);
        setTempDate(newDate);
    };

    const handleApply = () => {
        if (tempDate.getTime() < effectiveMinDate.getTime()) {
            alert("Please select a valid time (At least 24 hours from now, and sequentially after previous phases).");
            return;
        }
        onChange(tempDate.toISOString());
        setIsOpen(false);
    };

    const formatDisplay = (iso?: string) => {
        if (!iso) return 'Not set';
        const d = new Date(iso);
        if (isNaN(d.getTime())) return 'Invalid Date';
        return d.toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit', timeZoneName: 'short' });
    };

    const hourOptions = Array.from({length: 12}, (_, i) => ({ value: i + 1, label: (i + 1).toString() }));
    const minuteOptions = Array.from({length: 12}, (_, i) => ({ value: i * 5, label: (i * 5).toString().padStart(2, '0') }));
    const periodOptions = [{ value: 'AM', label: 'AM' }, { value: 'PM', label: 'PM' }];

    return (
        <div className={`relative w-full ${isOpen ? 'z-[100]' : 'z-10'}`} ref={containerRef}>
            <button
                type="button"
                onClick={() => !isPassed && setIsOpen(!isOpen)}
                className={`w-full flex flex-col bg-white/40 dark:bg-slate-900/40 backdrop-blur-2xl border border-white/60 dark:border-white/10 rounded-[1.25rem] md:rounded-[1.5rem] px-5 py-3 md:py-3.5 shadow-xl shadow-black/5 dark:shadow-none relative overflow-hidden group transition-all text-left ${isPassed ? 'opacity-60 cursor-not-allowed grayscale' : 'hover:border-modtale-accent focus-within:ring-1 focus-within:ring-modtale-accent'}`}
            >
                <div className="flex items-center justify-between w-full mb-1.5">
                    <div className="flex items-center gap-2 text-slate-500">
                        <Icon className="w-4 h-4 text-modtale-accent" />
                        <span className="text-[10px] font-black uppercase tracking-widest">{label}</span>
                    </div>
                    {isPassed && <span className="text-[9px] bg-slate-200 dark:bg-white/10 px-1.5 py-0.5 rounded font-bold uppercase text-slate-500">Locked</span>}
                </div>
                <span className="text-sm md:text-base font-black text-slate-900 dark:text-white truncate">
                    {formatDisplay(value)}
                </span>
            </button>

            {isOpen && !isPassed && (
                <div className="absolute top-full mt-2 left-1/2 -translate-x-1/2 w-[300px] md:w-[320px] max-w-[90vw] bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl shadow-[0_10px_50px_rgba(0,0,0,0.25)] z-[9999] p-4 animate-in fade-in zoom-in-95 duration-200 origin-top">
                    <CalendarWidget viewDate={viewDate} setViewDate={setViewDate} selectedDate={tempDate} onSelect={handleDateSelect} minDate={effectiveMinDate} />

                    <div className="mt-4 flex items-center justify-between gap-2 bg-slate-50 dark:bg-slate-800/50 p-3 rounded-xl border border-slate-200 dark:border-white/5">
                        <div className="flex items-center gap-2 pl-1">
                            <Clock className="w-4 h-4 text-slate-400" />
                            <span className="text-xs font-bold text-slate-600 dark:text-slate-300 uppercase tracking-wide">Time</span>
                        </div>
                        <div className="flex items-center gap-1.5">
                            <TimeDropdown value={displayHour} options={hourOptions} onChange={handleHourChange} />
                            <span className="font-black text-slate-400 pb-0.5">:</span>
                            <TimeDropdown value={currentMinute} options={minuteOptions} onChange={handleMinuteChange} />
                            <div className="w-1" />
                            <TimeDropdown value={currentPeriod} options={periodOptions} onChange={handlePeriodChange} />
                        </div>
                    </div>

                    <div className="mt-4 pt-4 border-t border-slate-100 dark:border-white/5 flex justify-end gap-2">
                        <button type="button" onClick={() => setIsOpen(false)} className="px-4 py-2 rounded-xl text-xs font-bold text-slate-500 hover:bg-slate-100 dark:hover:bg-white/5 transition-colors">Cancel</button>
                        <button type="button" onClick={handleApply} className="px-5 py-2 rounded-xl text-xs font-bold bg-modtale-accent text-white hover:bg-modtale-accentHover shadow-md transition-all">Apply</button>
                    </div>
                </div>
            )}
        </div>
    );
};

const MultiSelectDropdown: React.FC<{ options: {label: string, value: string}[], selected: string[], onChange: (val: string[]) => void, placeholder: string, direction?: 'up' | 'down' }> = ({ options, selected, onChange, placeholder, direction = 'down' }) => {
    const [isOpen, setIsOpen] = useState(false);
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (ref.current && !ref.current.contains(e.target as Node)) setIsOpen(false);
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const popupClass = direction === 'up' ? 'absolute bottom-full mb-2' : 'absolute top-full mt-2';

    return (
        <div className={`relative w-full ${isOpen ? 'z-[100]' : 'z-10'}`} ref={ref}>
            <button
                type="button"
                onClick={() => setIsOpen(!isOpen)}
                className="w-full bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-xl px-4 py-2 font-bold shadow-sm outline-none focus:ring-2 focus:ring-modtale-accent flex justify-between items-center text-sm transition-all"
            >
                <span className="truncate">{selected.length > 0 ? `${selected.length} selected` : placeholder}</span>
                <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${isOpen && direction === 'down' ? 'rotate-180' : ''} ${!isOpen && direction === 'up' ? 'rotate-180' : ''}`} />
            </button>
            {isOpen && (
                <div className={`${popupClass} left-0 right-0 bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl shadow-[0_10px_50px_rgba(0,0,0,0.25)] z-[9999] max-h-48 overflow-y-auto custom-scrollbar p-1`}>
                    {options.map((opt) => (
                        <button
                            key={opt.value}
                            type="button"
                            onClick={() => {
                                if (selected.includes(opt.value)) onChange(selected.filter(v => v !== opt.value));
                                else onChange([...selected, opt.value]);
                            }}
                            className="w-full flex items-center justify-between px-3 py-2 text-sm rounded-lg hover:bg-slate-50 dark:hover:bg-white/5 transition-colors"
                        >
                            <span className="text-slate-700 dark:text-slate-300 font-medium">{opt.label}</span>
                            {selected.includes(opt.value) && <Check className="w-4 h-4 text-modtale-accent" />}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};

const JamDependencySelector: React.FC<{ selectedId: string | undefined, onChange: (id: string | undefined) => void }> = ({ selectedId, onChange }) => {
    const [search, setSearch] = useState('');
    const [results, setResults] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    const [selectedMeta, setSelectedMeta] = useState<any>(null);

    useEffect(() => {
        if (selectedId && !selectedMeta) {
            api.get(`/projects/${selectedId}/meta`).then(res => setSelectedMeta(res.data)).catch(() => {});
        }
    }, [selectedId, selectedMeta]);

    useEffect(() => {
        const timer = setTimeout(async () => {
            if (search.length < 2) { setResults([]); return; }
            setLoading(true);
            try {
                const res = await api.get(`/projects?search=${search}`);
                setResults((res.data.content || []).filter((m: any) => m.classification !== 'MODPACK' && m.classification !== 'SAVE'));
            } catch (e) { setResults([]); } finally { setLoading(false); }
        }, 300);
        return () => clearTimeout(timer);
    }, [search]);

    const getIconUrl = (path?: string) => { if (!path) return '/assets/favicon.svg'; return path.startsWith('http') ? path : `${BACKEND_URL}${path}`; };

    if (selectedId) {
        return (
            <div className="flex items-center justify-between bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl p-3 shadow-sm">
                <div className="flex items-center gap-3">
                    <img src={getIconUrl(selectedMeta?.icon)} className="w-8 h-8 rounded-lg bg-slate-200 dark:bg-slate-800 object-cover" alt="" onError={(e) => e.currentTarget.src='/assets/favicon.svg'} />
                    <div>
                        <div className="font-bold text-sm text-slate-900 dark:text-white">{selectedMeta?.title || selectedId}</div>
                        <div className="text-xs text-slate-500">by {selectedMeta?.author || '...'}</div>
                    </div>
                </div>
                <button type="button" onClick={() => { onChange(undefined); setSelectedMeta(null); }} className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors">
                    <X className="w-4 h-4" />
                </button>
            </div>
        );
    }

    return (
        <div className="relative">
            <input type="text" value={search} onChange={e => setSearch(e.target.value)} placeholder="Search for a project..." className="w-full bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-xl px-4 py-2.5 font-bold shadow-sm outline-none focus:ring-2 focus:ring-modtale-accent text-sm transition-all" />
            {loading && <Loader2 className="absolute right-3 top-2.5 w-5 h-5 animate-spin text-modtale-accent" />}
            {results.length > 0 && (
                <div className="absolute top-full left-0 right-0 mt-2 bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-50 max-h-48 overflow-y-auto custom-scrollbar p-1">
                    {results.map(mod => (
                        <button key={mod.id} type="button" onClick={() => { onChange(mod.id); setSelectedMeta({ title: mod.title, author: mod.author, icon: mod.imageUrl }); setSearch(''); setResults([]); }} className="w-full flex items-center gap-3 px-3 py-2.5 hover:bg-slate-50 dark:hover:bg-white/5 rounded-lg transition-colors text-left group">
                            <img src={getIconUrl(mod.imageUrl)} className="w-8 h-8 rounded bg-slate-200 dark:bg-slate-800 object-cover" alt="" onError={(e) => e.currentTarget.src='/assets/favicon.svg'} />
                            <div>
                                <div className="font-bold text-sm text-slate-900 dark:text-white group-hover:text-modtale-accent transition-colors">{mod.title}</div>
                                <div className="text-xs text-slate-500">by {mod.author}</div>
                            </div>
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};

export const JamBuilder: React.FC<any> = ({
                                              metaData, setMetaData, handleSave, isLoading, activeTab, setActiveTab, onBack, onPublish
                                          }) => {
    const [isDirty, setIsDirty] = useState(false);
    const [isSaved, setIsSaved] = useState(false);
    const [editorMode, setEditorMode] = useState<'write' | 'preview'>('write');
    const [rulesEditorMode, setRulesEditorMode] = useState<'generate' | 'write' | 'preview'>('generate');
    const [isEditingTitle, setIsEditingTitle] = useState(false);
    const [gameVersionOptions, setGameVersionOptions] = useState<{label: string, value: string}[]>([]);

    const [slugError, setSlugError] = useState<string | null>(null);

    // User search states
    const [inviteUsername, setInviteUsername] = useState('');
    const [inviteStatus, setInviteStatus] = useState('');
    const [isInviting, setIsInviting] = useState(false);
    const [searchResults, setSearchResults] = useState<any[]>([]);
    const [showResults, setShowResults] = useState(false);
    const searchTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
    const searchWrapperRef = useRef<HTMLDivElement>(null);
    const [judgeProfiles, setJudgeProfiles] = useState<Record<string, User>>({});

    useEffect(() => {
        api.get('/meta/game-versions').then(res => {
            const versions = Array.isArray(res.data) ? res.data : (res.data.content || []);
            setGameVersionOptions(versions.map((v: string) => ({ label: v, value: v })));
        }).catch(() => {});
    }, []);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (searchWrapperRef.current && !searchWrapperRef.current.contains(e.target as Node)) {
                setShowResults(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    useEffect(() => {
        if (activeTab === 'judges' && metaData.judgeIds && metaData.judgeIds.length > 0) {
            const missingIds = metaData.judgeIds.filter((id: string) => !judgeProfiles[id]);
            if (missingIds.length > 0) {
                api.post('/users/batch/ids', { ids: missingIds })
                    .then(res => {
                        const newProfiles = { ...judgeProfiles };
                        res.data.forEach((u: any) => {
                            newProfiles[u.id] = u;
                        });
                        setJudgeProfiles(newProfiles);
                    })
                    .catch(console.error);
            }
        }
    }, [activeTab, metaData.judgeIds, judgeProfiles]);

    const [genState, setGenState] = useState({
        allowNSFW: false,
        allowPremade: true,
        allowAI: false,
        requireDiscord: false,
        requireFeedback: true,
        strictTheme: true,
        strictStability: true,
        requireLocalization: false,
        mediaConsent: true,
        collaborationCredit: true,
        updateLock: true
    });

    const publishChecklist = [
        { label: 'Title (min 5 chars)', met: (metaData.title || '').trim().length >= 5 },
        { label: 'Valid URL Slug', met: !!metaData.slug && !slugError },
        { label: 'Description (min 10 chars)', met: (metaData.description || '').trim().length >= 10 },
        { label: 'Start Date set', met: !!metaData.startDate },
        { label: 'Timeline follows order', met: !!metaData.endDate && !!metaData.votingEndDate && new Date(metaData.votingEndDate) > new Date(metaData.endDate) && new Date(metaData.endDate) > new Date(metaData.startDate) },
        { label: 'Scoring criteria set', met: (metaData.categories?.length || 0) > 0 },
        { label: 'All changes saved', met: !isDirty }
    ];

    const isReadyToPublish = publishChecklist.every(c => c.met);
    const metCount = publishChecklist.filter(r => r.met).length;
    const isPublished = metaData.status && metaData.status !== 'DRAFT';

    const markDirty = () => {
        setIsDirty(true);
        setIsSaved(false);
    };

    const performSave = async () => {
        try {
            const success = await handleSave();
            if (success) {
                setIsDirty(false);
                setIsSaved(true);
                setTimeout(() => setIsSaved(false), 3000);
            }
        } catch (e: any) {
            let errorMsg = typeof e.response?.data === 'string'
                ? e.response.data
                : e.response?.data?.message || 'Failed to save jam.';
            errorMsg = errorMsg.replace(/^\d{3} [A-Z_]+ "(.*)"$/, '$1');
            alert(errorMsg);
        }
    };

    const updateField = (field: string, val: any) => {
        markDirty();
        setMetaData((prev: any) => ({ ...prev, [field]: val }));
    };

    const validateSlugFormat = (val: string) => {
        if (!val) return "Slug is required.";
        const slugRegex = /^[a-z0-9](?:[a-z0-9-]{1,48}[a-z0-9])?$/;
        if (!slugRegex.test(val)) return "Must be 3-50 chars, lowercase alphanumeric, no start/end dash.";
        return null;
    };

    const handleSlugChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        markDirty();
        const val = e.target.value;
        setMetaData((prev: any) => ({...prev, slug: val}));
        setSlugError(validateSlugFormat(val));
    };

    const handleInputSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const val = e.target.value;
        setInviteUsername(val);
        if (searchTimeout.current) clearTimeout(searchTimeout.current);
        if (val.trim().length > 1) {
            searchTimeout.current = setTimeout(async () => {
                try {
                    const res = await api.get('/users/search', { params: { query: val } });
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

    const handleSelectUser = (user: any) => {
        setInviteUsername(user.username);
        setShowResults(false);
    };

    const handleInviteJudge = async () => {
        if (!metaData.id) {
            setInviteStatus("Please save the jam draft first before inviting judges.");
            return;
        }
        setIsInviting(true);
        try {
            const res = await api.post(`/modjams/${metaData.id}/judges/invite`, { username: inviteUsername });
            setMetaData((prev: any) => ({
                ...prev,
                pendingJudgeInvites: res.data.pendingJudgeInvites,
                judgeIds: res.data.judgeIds
            }));
            setInviteUsername('');
            setInviteStatus("Invited successfully!");
            setTimeout(() => setInviteStatus(''), 3000);
        } catch (e: any) {
            let errorMsg = typeof e.response?.data === 'string'
                ? e.response.data
                : e.response?.data?.message || 'Failed to invite user.';
            errorMsg = errorMsg.replace(/^\d{3} [A-Z_]+ "(.*)"$/, '$1');
            setInviteStatus(errorMsg);
        } finally {
            setIsInviting(false);
        }
    };

    const handleRemoveJudge = async (username: string) => {
        if (!metaData.id) return;
        try {
            const res = await api.delete(`/modjams/${metaData.id}/judges/${username}`);
            setMetaData((prev: any) => ({
                ...prev,
                pendingJudgeInvites: res.data.pendingJudgeInvites,
                judgeIds: res.data.judgeIds
            }));
        } catch (e: any) {
            let errorMsg = typeof e.response?.data === 'string'
                ? e.response.data
                : e.response?.data?.message || 'Failed to remove judge.';
            errorMsg = errorMsg.replace(/^\d{3} [A-Z_]+ "(.*)"$/, '$1');
            alert(errorMsg);
        }
    };

    const generateRulesText = () => {
        let text = "### Official Modjam Rules\n\nPlease read and agree to the following rules before submitting your project.\n\n";

        if (metaData.hideSubmissions) text += "- **Secret Submissions:** All project entries will remain completely hidden from the public until the voting phase commences.\n";
        if (metaData.oneEntryPerPerson) text += "- **One Entry Limit:** Participants are restricted to a single project submission.\n";

        const res = metaData.restrictions;
        if (res?.requireNewbie) text += "- **First-Time Jammers Only:** This jam is restricted to users who have never participated in a Modjam before.\n";
        if (res?.requirePriorJams) text += "- **Veteran Jammers Only:** You must have participated in at least one previous Modjam.\n";
        if (res?.requireNoPriorProjects) text += "- **First-Time Modders Only:** You must not have any previously published projects on the platform.\n";
        if (res?.requirePriorProjects) text += "- **Veteran Modders Only:** You must have at least one previously published project on the platform.\n";
        if (res?.requireNewProject) text += "- **Fresh Projects Only:** All submissions must be created *after* the jam start date. Old projects are not allowed.\n";
        if (res?.requireSourceRepo) text += "- **Open Source (Repo):** A public source code repository must be linked to your project.\n";
        if (res?.requireOsiLicense) text += "- **Open Source (License):** You must use an OSI-approved open source license.\n";
        if (res?.requireUniqueSubmission) text += "- **Unique Submission:** Your project cannot be entered into multiple active jams simultaneously.\n";
        if (res?.minContributors || res?.maxContributors) {
            text += `- **Team Size:** Teams must be between ${res.minContributors || 1} and ${res.maxContributors || 'unlimited'} members.\n`;
        }
        if (res?.allowedGameVersions && res.allowedGameVersions.length > 0) {
            text += `- **Game Version Lock:** Submissions must support the following game version(s): ${res.allowedGameVersions.join(', ')}.\n`;
        }
        if (res?.requiredClassUsage) {
            text += `- **Required Implementation:** Submissions must explicitly utilize the \`${res.requiredClassUsage}\` class or package in their compiled code.\n`;
        }

        text += "\n#### Community & Content Guidelines\n";

        if (genState.strictTheme) text += "- **Thematic Relevance:** Entries must clearly relate to the jam's theme. The host reserves the right to disqualify off-topic projects.\n";
        if (genState.strictStability) text += "- **Stability:** Projects containing game-breaking bugs, malicious intent, or severe instability will be disqualified.\n";
        if (genState.updateLock) text += "- **Update Lock:** You agree not to upload new files or radically alter your project page after the submission deadline has passed (bug fixes may be allowed at host discretion).\n";
        if (genState.collaborationCredit) text += "- **Credit Required:** You must clearly credit all external libraries, assets, and collaborators in your project description.\n";
        if (genState.requireLocalization) text += "- **Localization:** Projects are expected to support localization (i18n) and provide language files where applicable.\n";

        text += `- **NSFW Content:** ${genState.allowNSFW ? 'Allowed (Must be properly tagged as mature).' : 'Strictly prohibited. Submissions containing NSFW material will be disqualified.'}\n`;
        text += `- **Premade Assets:** ${genState.allowPremade ? 'You may use pre-existing assets, provided you have the legal right to use them and declare them in your description.' : 'Not allowed. All assets, code, and resources must be created entirely within the jam timeframe.'}\n`;
        text += `- **AI Generation:** ${genState.allowAI ? 'AI-generated assets or code are allowed.' : 'The use of AI-generated code, art, or audio is strictly forbidden.'}\n`;

        text += "\n#### Participation Expectations\n";

        if (genState.requireDiscord) text += "- **Community:** You must be a member of the official Jam Discord server to participate.\n";
        if (genState.requireFeedback) text += "- **Feedback Loop:** To be eligible for winning, you are expected to actively participate by playing, voting, and leaving constructive comments on other participants' entries.\n";
        if (genState.mediaConsent) text += "- **Media Consent:** By entering, you grant the host permission to feature, showcase, or stream your project publicly.\n";

        updateField('rules', text);
        setRulesEditorMode('write');
    };

    const MarkdownComponents = {
        code({node, inline, className, children, ...props}: any) {
            const match = /language-(\w+)/.exec(className || '');
            return !inline && match ? (
                <SyntaxHighlighter {...props} style={vscDarkPlus} language={match[1]} PreTag="div" className="rounded-lg text-sm">
                    {String(children).replace(/\n$/, '')}
                </SyntaxHighlighter>
            ) : (
                <code className={`${className || ''} bg-slate-100 dark:bg-white/10 px-1 py-0.5 rounded text-sm`} {...props}>
                    {children}
                </code>
            );
        },
        p({node, children, ...props}: any) { return <p className="my-2 [li>&]:my-0" {...props}>{children}</p>; },
        li({node, children, ...props}: any) { return <li className="my-1 [&>p]:my-0" {...props}>{children}</li>; },
        ul({node, children, ...props}: any) { return <ul className="list-disc pl-6 my-3" {...props}>{children}</ul>; },
        ol({node, children, ...props}: any) { return <ol className="list-decimal pl-6 my-3" {...props}>{children}</ol>; }
    };

    const classificationOptions = [
        { label: 'Plugin', value: 'PLUGIN' },
        { label: 'Modpack', value: 'MODPACK' },
        { label: 'Data Pack', value: 'DATA' },
        { label: 'World / Save', value: 'SAVE' },
        { label: 'Art Assets', value: 'ART' }
    ];

    const licenseOptions = [
        { label: 'MIT', value: 'MIT' },
        { label: 'Apache 2.0', value: 'APACHE' },
        { label: 'LGPL v3', value: 'LGPL' },
        { label: 'AGPL v3', value: 'AGPL' },
        { label: 'GPL v3', value: 'GPL' },
        { label: 'MPL 2.0', value: 'MPL' },
        { label: 'BSD 3-Clause', value: 'BSD' },
        { label: 'CC0', value: 'CC0' },
        { label: 'The Unlicense', value: 'UNLICENSE' }
    ];

    return (
        <JamLayout
            isEditing={true}
            onBack={onBack}
            bannerUrl={metaData.bannerUrl}
            iconUrl={metaData.imageUrl}
            onBannerUpload={(f, p) => { markDirty(); setMetaData((prev: any) => ({ ...prev, bannerUrl: p, bannerFile: f })); }}
            onIconUpload={(f, p) => { markDirty(); setMetaData((prev: any) => ({ ...prev, imageUrl: p, iconFile: f })); }}
            titleContent={
                <div className="flex items-center gap-3 w-full group relative">
                    <div className="relative flex items-center">
                        <input
                            value={metaData.title}
                            onFocus={() => setIsEditingTitle(true)}
                            onBlur={() => setIsEditingTitle(false)}
                            onChange={e => updateField('title', e.target.value)}
                            placeholder="Enter Jam Title"
                            className="text-3xl md:text-5xl lg:text-6xl font-black bg-transparent border-none outline-none w-full placeholder:text-slate-300 dark:placeholder:text-slate-700 focus:ring-0 text-slate-900 dark:text-white drop-shadow-xl p-0 min-w-[50px]"
                            style={{ width: `${Math.max(metaData.title?.length || 10, 1)}ch` }}
                        />
                        {!isEditingTitle && (
                            <Edit3 className="w-6 h-6 md:w-8 md:h-8 text-slate-400 dark:text-white/50 shrink-0 ml-4 pointer-events-none" />
                        )}
                    </div>
                </div>
            }
            hostContent={
                <div className="text-slate-600 dark:text-slate-300 font-bold flex items-center gap-2 bg-white/50 dark:bg-black/30 backdrop-blur-md px-3 py-1.5 rounded-xl border border-white/20 dark:border-white/10 shadow-sm w-fit text-sm">
                    <span className="w-2.5 h-2.5 rounded-full bg-modtale-accent animate-pulse" /> {isPublished ? 'Live Event' : 'Editing Draft'}
                </div>
            }
            actionContent={
                <div className="flex items-center gap-3">
                    <button
                        type="button"
                        onClick={(e) => { e.preventDefault(); performSave(); }}
                        disabled={isLoading || !isDirty}
                        className={`h-12 md:h-14 px-5 md:px-6 rounded-[1rem] md:rounded-[1.25rem] font-black text-sm transition-all flex items-center justify-center gap-2 backdrop-blur-xl border shadow-lg ${
                            isSaved ? 'bg-green-500/20 border-green-500/30 text-green-700 dark:text-green-400' :
                                !isDirty ? 'bg-white/40 dark:bg-slate-800/40 border-white/20 dark:border-white/5 text-slate-500 cursor-not-allowed shadow-none' :
                                    'bg-white/80 dark:bg-slate-800/80 border-white/60 dark:border-white/20 text-slate-900 dark:text-white hover:border-modtale-accent hover:text-modtale-accent active:scale-95'
                        }`}
                    >
                        {isLoading ? <Spinner className="w-4 h-4" fullScreen={false} /> : isSaved ? <CheckCircle2 className="w-4 h-4" /> : <Save className="w-4 h-4" />}
                        <span className="hidden sm:inline">{isLoading ? 'Saving...' : isSaved ? 'Saved!' : (isPublished ? 'Save Changes' : 'Save Draft')}</span>
                    </button>

                    {!isPublished && (
                        <div className="relative group">
                            <div className="absolute bottom-full right-0 mb-3 w-64 bg-white dark:bg-slate-900 rounded-2xl shadow-2xl p-5 border border-slate-200 dark:border-white/10 opacity-0 group-hover:opacity-100 transition-all pointer-events-none translate-y-2 group-hover:translate-y-0 z-[100]">
                                <div className="flex items-center justify-between mb-4 border-b border-slate-100 dark:border-white/5 pb-3">
                                    <span className="text-[10px] font-black uppercase text-slate-500 tracking-widest">Requirements</span>
                                    <span className={`text-xs font-black ${isReadyToPublish ? 'text-green-500' : 'text-slate-400'}`}>
                                        {metCount}/{publishChecklist.length}
                                    </span>
                                </div>
                                <div className="space-y-3">
                                    {publishChecklist.map((req, i) => (
                                        <div key={i} className="flex items-center gap-3">
                                            <div className={`w-4 h-4 rounded-full flex items-center justify-center flex-shrink-0 ${req.met ? 'bg-green-500 text-white' : 'bg-slate-100 dark:bg-white/5 text-slate-400'}`}>
                                                {req.met ? <Check className="w-2.5 h-2.5" strokeWidth={4} /> : <X className="w-2.5 h-2.5" strokeWidth={4} />}
                                            </div>
                                            <span className={`text-[11px] font-bold ${req.met ? 'text-slate-900 dark:text-slate-200' : 'text-slate-400 dark:text-slate-600'}`}>{req.label}</span>
                                        </div>
                                    ))}
                                </div>
                                <div className="absolute top-full right-10 -mt-1.5 border-8 border-transparent border-t-white dark:border-t-slate-900" />
                            </div>

                            <button
                                type="button"
                                onClick={(e) => { e.preventDefault(); onPublish(); }}
                                disabled={!isReadyToPublish || isLoading}
                                className="h-12 md:h-14 px-6 md:px-8 bg-modtale-accent hover:bg-modtale-accentHover disabled:bg-slate-300 dark:disabled:bg-slate-800 disabled:text-slate-500 text-white rounded-[1rem] md:rounded-[1.25rem] font-black text-sm transition-all flex items-center justify-center gap-2 shadow-xl shadow-modtale-accent/20 enabled:active:scale-95"
                            >
                                <span className="hidden sm:inline">Publish Jam</span>
                            </button>
                        </div>
                    )}
                </div>
            }
            tabsAndTimers={
                <div className="flex flex-col xl:flex-row xl:items-end justify-between gap-4 border-b-2 border-slate-200/50 dark:border-white/5 pb-3 xl:pb-0">
                    <div className="flex items-center gap-6 md:gap-8 h-full w-full overflow-x-auto overflow-y-hidden [&::-webkit-scrollbar]:hidden [-ms-overflow-style:none] [scrollbar-width:none]">
                        {[
                            {id: 'details', icon: FileText, label: 'Overview'},
                            {id: 'schedule', icon: Calendar, label: 'Schedule'},
                            {id: 'rules', icon: BookOpen, label: 'Rules'},
                            {id: 'categories', icon: Scale, label: `Judging (${metaData.categories?.length || 0})`},
                            {id: 'judges', icon: Users, label: 'Judges'},
                            {id: 'restrictions', icon: Shield, label: 'Restrictions'},
                            {id: 'settings', icon: Settings, label: 'Settings'}
                        ].map(t => (
                            <button
                                key={t.id}
                                type="button"
                                onClick={() => setActiveTab(t.id as any)}
                                className={`pb-3 text-sm font-black uppercase tracking-widest transition-all whitespace-nowrap flex items-center gap-2 ${activeTab === t.id ? 'border-modtale-accent text-modtale-accent border-b-4 -mb-[2px]' : 'border-transparent text-slate-400 hover:text-slate-600 dark:hover:text-slate-200'}`}
                            >
                                <t.icon className="w-4 h-4"/> {t.label}
                            </button>
                        ))}
                    </div>
                </div>
            }
            mainContent={
                <div className="animate-in fade-in slide-in-from-bottom-2">
                    {activeTab === 'details' && (
                        <div className="space-y-6">
                            <div className="flex items-center justify-between border-b border-slate-200/50 dark:border-white/5 pb-4">
                                <h3 className="text-sm font-black uppercase text-slate-500 tracking-widest flex items-center gap-2">
                                    <List className="w-4 h-4" /> Event Description
                                </h3>
                                <div className="flex bg-white/50 dark:bg-black/20 rounded-xl p-1 border border-slate-200/50 dark:border-white/5 shadow-sm">
                                    <button type="button" onClick={() => setEditorMode('write')} className={`px-4 py-1.5 text-xs font-bold rounded-lg transition-colors ${editorMode === 'write' ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 hover:text-slate-900 dark:hover:text-white'}`}>Write</button>
                                    <button type="button" onClick={() => setEditorMode('preview')} className={`px-4 py-1.5 text-xs font-bold rounded-lg transition-colors ${editorMode === 'preview' ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 hover:text-slate-900 dark:hover:text-white'}`}>Preview</button>
                                </div>
                            </div>
                            {editorMode === 'write' ? (
                                <textarea
                                    value={metaData.description}
                                    onChange={e => updateField('description', e.target.value)}
                                    placeholder="# Welcome to the Jam!&#10;&#10;Describe the theme, goals, and glory..."
                                    className="w-full min-h-[500px] bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 rounded-[2rem] p-6 md:p-8 text-slate-700 dark:text-slate-300 font-mono text-sm md:text-base resize-none focus:ring-2 focus:ring-modtale-accent shadow-sm outline-none transition-all custom-scrollbar"
                                />
                            ) : (
                                <div className="prose dark:prose-invert prose-lg max-w-none min-h-[500px] bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 rounded-[2rem] p-6 md:p-8 shadow-sm">
                                    {metaData.description ? (
                                        <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeRaw, [rehypeSanitize, {...defaultSchema, attributes: {...defaultSchema.attributes, code: ['className']}}]]} components={MarkdownComponents}>
                                            {metaData.description}
                                        </ReactMarkdown>
                                    ) : <p className="text-slate-500 italic">No description provided.</p>}
                                </div>
                            )}
                        </div>
                    )}

                    {activeTab === 'rules' && (
                        <div className="space-y-6">
                            <div className="flex items-center justify-between border-b border-slate-200/50 dark:border-white/5 pb-4">
                                <h3 className="text-sm font-black uppercase text-slate-500 tracking-widest flex items-center gap-2">
                                    <BookOpen className="w-4 h-4" /> Jam Rules
                                </h3>
                                <div className="flex bg-white/50 dark:bg-black/20 rounded-xl p-1 border border-slate-200/50 dark:border-white/5 shadow-sm">
                                    <button type="button" onClick={() => setRulesEditorMode('generate')} className={`px-4 py-1.5 text-xs font-bold rounded-lg transition-colors ${rulesEditorMode === 'generate' ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 hover:text-slate-900 dark:hover:text-white'}`}>Generator</button>
                                    <button type="button" onClick={() => setRulesEditorMode('write')} className={`px-4 py-1.5 text-xs font-bold rounded-lg transition-colors ${rulesEditorMode === 'write' ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 hover:text-slate-900 dark:hover:text-white'}`}>Write</button>
                                    <button type="button" onClick={() => setRulesEditorMode('preview')} className={`px-4 py-1.5 text-xs font-bold rounded-lg transition-colors ${rulesEditorMode === 'preview' ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 hover:text-slate-900 dark:hover:text-white'}`}>Preview</button>
                                </div>
                            </div>

                            {rulesEditorMode === 'generate' && (
                                <div className="p-6 md:p-8 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl rounded-[2rem] border border-white/40 dark:border-white/10 space-y-6 shadow-sm">
                                    <div className="bg-blue-50 dark:bg-blue-900/10 border border-blue-200 dark:border-blue-900/30 p-4 rounded-xl flex items-start gap-3">
                                        <Wand2 className="w-5 h-5 text-blue-500 mt-0.5 shrink-0" />
                                        <p className="text-sm text-blue-800 dark:text-blue-300 font-medium">
                                            The rules generator automatically incorporates your active <strong>Restrictions</strong> and <strong>Settings</strong>. Use the toggles below to append community guidelines, then hit Generate to formulate the Markdown text!
                                        </p>
                                    </div>

                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                        <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                            <div className="flex flex-col">
                                                <span className="text-sm font-bold text-slate-900 dark:text-white">Strict Theme Requirement</span>
                                                <span className="text-xs text-slate-500 font-medium">Allow disqualifying off-topic entries</span>
                                            </div>
                                            <input
                                                type="checkbox"
                                                checked={genState.strictTheme}
                                                onChange={e => setGenState({...genState, strictTheme: e.target.checked})}
                                                className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                            />
                                        </label>

                                        <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                            <div className="flex flex-col">
                                                <span className="text-sm font-bold text-slate-900 dark:text-white">Feedback Loop</span>
                                                <span className="text-xs text-slate-500 font-medium">Require participants to vote/comment</span>
                                            </div>
                                            <input
                                                type="checkbox"
                                                checked={genState.requireFeedback}
                                                onChange={e => setGenState({...genState, requireFeedback: e.target.checked})}
                                                className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                            />
                                        </label>

                                        <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                            <div className="flex flex-col">
                                                <span className="text-sm font-bold text-slate-900 dark:text-white">Bug & Stability Clause</span>
                                                <span className="text-xs text-slate-500 font-medium">Disqualify game-breaking entries</span>
                                            </div>
                                            <input
                                                type="checkbox"
                                                checked={genState.strictStability}
                                                onChange={e => setGenState({...genState, strictStability: e.target.checked})}
                                                className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                            />
                                        </label>

                                        <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                            <div className="flex flex-col">
                                                <span className="text-sm font-bold text-slate-900 dark:text-white">Update Lock</span>
                                                <span className="text-xs text-slate-500 font-medium">Prohibit updates after deadline</span>
                                            </div>
                                            <input
                                                type="checkbox"
                                                checked={genState.updateLock}
                                                onChange={e => setGenState({...genState, updateLock: e.target.checked})}
                                                className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                            />
                                        </label>

                                        <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                            <div className="flex flex-col">
                                                <span className="text-sm font-bold text-slate-900 dark:text-white">Collaboration Credit</span>
                                                <span className="text-xs text-slate-500 font-medium">Require listing third-party assets</span>
                                            </div>
                                            <input
                                                type="checkbox"
                                                checked={genState.collaborationCredit}
                                                onChange={e => setGenState({...genState, collaborationCredit: e.target.checked})}
                                                className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                            />
                                        </label>

                                        <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                            <div className="flex flex-col">
                                                <span className="text-sm font-bold text-slate-900 dark:text-white">Require Localization</span>
                                                <span className="text-xs text-slate-500 font-medium">Mandate translation support</span>
                                            </div>
                                            <input
                                                type="checkbox"
                                                checked={genState.requireLocalization}
                                                onChange={e => setGenState({...genState, requireLocalization: e.target.checked})}
                                                className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                            />
                                        </label>

                                        <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                            <div className="flex flex-col">
                                                <span className="text-sm font-bold text-slate-900 dark:text-white">Allow Premade Assets</span>
                                                <span className="text-xs text-slate-500 font-medium">Permit using existing code/art</span>
                                            </div>
                                            <input
                                                type="checkbox"
                                                checked={genState.allowPremade}
                                                onChange={e => setGenState({...genState, allowPremade: e.target.checked})}
                                                className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                            />
                                        </label>

                                        <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                            <div className="flex flex-col">
                                                <span className="text-sm font-bold text-slate-900 dark:text-white">Allow AI Generation</span>
                                                <span className="text-xs text-slate-500 font-medium">Permit AI generated assets/code</span>
                                            </div>
                                            <input
                                                type="checkbox"
                                                checked={genState.allowAI}
                                                onChange={e => setGenState({...genState, allowAI: e.target.checked})}
                                                className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                            />
                                        </label>

                                        <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                            <div className="flex flex-col">
                                                <span className="text-sm font-bold text-slate-900 dark:text-white">Media Consent</span>
                                                <span className="text-xs text-slate-500 font-medium">Require permission to feature entries</span>
                                            </div>
                                            <input
                                                type="checkbox"
                                                checked={genState.mediaConsent}
                                                onChange={e => setGenState({...genState, mediaConsent: e.target.checked})}
                                                className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                            />
                                        </label>

                                        <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                            <div className="flex flex-col">
                                                <span className="text-sm font-bold text-slate-900 dark:text-white">Require Discord</span>
                                                <span className="text-xs text-slate-500 font-medium">Mandate joining a community server</span>
                                            </div>
                                            <input
                                                type="checkbox"
                                                checked={genState.requireDiscord}
                                                onChange={e => setGenState({...genState, requireDiscord: e.target.checked})}
                                                className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                            />
                                        </label>
                                    </div>

                                    <div className="pt-4 border-t border-slate-200/50 dark:border-white/5 flex justify-end">
                                        <button
                                            type="button"
                                            onClick={generateRulesText}
                                            className="px-6 py-3 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-xl font-black shadow-lg shadow-modtale-accent/20 flex items-center gap-2 transition-all active:scale-95"
                                        >
                                            <Wand2 className="w-4 h-4" /> Generate Rules Text
                                        </button>
                                    </div>
                                </div>
                            )}

                            {rulesEditorMode === 'write' && (
                                <textarea
                                    value={metaData.rules || ''}
                                    onChange={e => updateField('rules', e.target.value)}
                                    placeholder="### Jam Rules&#10;&#10;Users will have to agree to these before submitting..."
                                    className="w-full min-h-[500px] bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 rounded-[2rem] p-6 md:p-8 text-slate-700 dark:text-slate-300 font-mono text-sm md:text-base resize-none focus:ring-2 focus:ring-modtale-accent shadow-sm outline-none transition-all custom-scrollbar"
                                />
                            )}

                            {rulesEditorMode === 'preview' && (
                                <div className="prose dark:prose-invert prose-lg max-w-none min-h-[500px] bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl border border-white/40 dark:border-white/10 rounded-[2rem] p-6 md:p-8 shadow-sm">
                                    {metaData.rules ? (
                                        <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeRaw, [rehypeSanitize, {...defaultSchema, attributes: {...defaultSchema.attributes, code: ['className']}}]]} components={MarkdownComponents}>
                                            {metaData.rules}
                                        </ReactMarkdown>
                                    ) : <p className="text-slate-500 italic">No rules generated yet.</p>}
                                </div>
                            )}
                        </div>
                    )}

                    {activeTab === 'schedule' && (
                        <div className="space-y-6 pb-48">
                            <h3 className="text-sm font-black uppercase text-slate-500 tracking-widest border-b border-slate-200/50 dark:border-white/5 pb-4 flex items-center gap-2">
                                <Calendar className="w-4 h-4" /> Timeline Configuration
                            </h3>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                                <div className="p-6 md:p-8 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl rounded-[2rem] border border-white/40 dark:border-white/10 shadow-sm flex flex-col items-center text-center transition-all">
                                    <div className="w-14 h-14 bg-blue-500/10 text-blue-500 rounded-full flex items-center justify-center mb-4">
                                        <Play className="w-6 h-6 ml-1" />
                                    </div>
                                    <h4 className="font-black text-xl mb-1 text-slate-900 dark:text-white">Jam Starts</h4>
                                    <p className="text-xs text-slate-500 mb-6 font-medium">When users can begin submitting projects to the jam.</p>
                                    <div className="w-full">
                                        <CustomDateTimePicker
                                            label="Jam Starts"
                                            icon={Play}
                                            value={metaData.startDate}
                                            minDate={undefined}
                                            onChange={(v: string) => updateField('startDate', v)}
                                        />
                                    </div>
                                </div>

                                <div className="p-6 md:p-8 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl rounded-[2rem] border border-white/40 dark:border-white/10 shadow-sm flex flex-col items-center text-center transition-all">
                                    <div className="w-14 h-14 bg-orange-500/10 text-orange-500 rounded-full flex items-center justify-center mb-4">
                                        <LayoutGrid className="w-6 h-6" />
                                    </div>
                                    <h4 className="font-black text-xl mb-1 text-slate-900 dark:text-white">Submissions Close</h4>
                                    <p className="text-xs text-slate-500 mb-6 font-medium">When the deadline passes and entries are locked.</p>
                                    <div className="w-full">
                                        <CustomDateTimePicker
                                            label="Submissions Close"
                                            icon={LayoutGrid}
                                            value={metaData.endDate}
                                            minDate={metaData.startDate}
                                            onChange={(v: string) => updateField('endDate', v)}
                                        />
                                    </div>
                                </div>

                                <div className="p-6 md:p-8 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl rounded-[2rem] border border-white/40 dark:border-white/10 shadow-sm flex flex-col items-center text-center transition-all">
                                    <div className="w-14 h-14 bg-purple-500/10 text-purple-500 rounded-full flex items-center justify-center mb-4">
                                        <Trophy className="w-6 h-6" />
                                    </div>
                                    <h4 className="font-black text-xl mb-1 text-slate-900 dark:text-white">Voting Ends</h4>
                                    <p className="text-xs text-slate-500 mb-6 font-medium">When judging closes and results can be finalized.</p>
                                    <div className="w-full">
                                        <CustomDateTimePicker
                                            label="Voting Ends"
                                            icon={Trophy}
                                            value={metaData.votingEndDate}
                                            minDate={metaData.endDate}
                                            onChange={(v: string) => updateField('votingEndDate', v)}
                                        />
                                    </div>
                                </div>
                            </div>
                            <div className="bg-blue-50 dark:bg-blue-900/10 border border-blue-200 dark:border-blue-900/30 p-4 rounded-xl flex items-start gap-3">
                                <Clock className="w-5 h-5 text-blue-500 mt-0.5 shrink-0" />
                                <p className="text-sm text-blue-800 dark:text-blue-300 font-medium">
                                    Timezone configuration is automatic! All times are selected and displayed in your local timezone (<strong>{Intl.DateTimeFormat().resolvedOptions().timeZone}</strong>), but are securely stored as UTC on the backend so participants worldwide will see the correct local times for them.
                                </p>
                            </div>
                        </div>
                    )}

                    {activeTab === 'categories' && (
                        <div className="space-y-6">
                            <div className="flex items-center justify-between border-b border-slate-200/50 dark:border-white/5 pb-4">
                                <div>
                                    <h3 className="text-sm font-black uppercase text-slate-500 tracking-widest flex items-center gap-2">
                                        <Scale className="w-4 h-4" /> Scoring Categories
                                    </h3>
                                </div>
                                <button
                                    type="button"
                                    onClick={() => updateField('categories', [...(metaData.categories || []), {name: '', description: '', maxScore: 5}])}
                                    className="px-4 py-2 bg-modtale-accent/10 text-modtale-accent rounded-xl text-xs font-black flex items-center gap-1.5 hover:bg-modtale-accent hover:text-white transition-all"
                                >
                                    <Plus className="w-3.5 h-3.5" /> Add Category
                                </button>
                            </div>

                            <div className="grid gap-4">
                                {(metaData.categories || []).map((cat: any, i: number) => (
                                    <div key={i} className="flex flex-col sm:flex-row gap-4 p-5 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl rounded-[1.5rem] border border-white/40 dark:border-white/10 group transition-all hover:border-modtale-accent/50 shadow-sm">
                                        <div className="flex-1 space-y-3">
                                            <input
                                                value={cat.name}
                                                onChange={e => {
                                                    const c = [...metaData.categories];
                                                    c[i].name = e.target.value;
                                                    updateField('categories', c);
                                                }}
                                                className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-xl px-4 py-2.5 font-bold shadow-sm outline-none focus:ring-2 focus:ring-modtale-accent"
                                                placeholder="Criterion Name (e.g. Creativity)"
                                            />
                                            <input
                                                value={cat.description}
                                                onChange={e => {
                                                    const c = [...metaData.categories];
                                                    c[i].description = e.target.value;
                                                    updateField('categories', c);
                                                }}
                                                className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-xl px-4 py-2.5 text-sm shadow-sm outline-none focus:ring-2 focus:ring-modtale-accent text-slate-500"
                                                placeholder="Brief scoring guide..."
                                            />
                                        </div>
                                        <div className="flex items-center gap-3">
                                            <div className="w-20">
                                                <label className="block text-[9px] font-black uppercase text-slate-400 mb-1 ml-1 text-center">Max</label>
                                                <input
                                                    type="number"
                                                    value={cat.maxScore}
                                                    onChange={e => {
                                                        const c = [...metaData.categories];
                                                        c[i].maxScore = parseInt(e.target.value);
                                                        updateField('categories', c);
                                                    }}
                                                    className="w-full h-10 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-xl font-black text-center shadow-sm outline-none focus:ring-2 focus:ring-modtale-accent"
                                                />
                                            </div>
                                            <button
                                                type="button"
                                                onClick={() => updateField('categories', metaData.categories.filter((_:any, idx:number) => idx !== i))}
                                                className="w-10 h-10 bg-red-500/10 text-red-500 rounded-xl hover:bg-red-50 hover:text-white transition-all flex items-center justify-center mt-3.5"
                                            >
                                                <Trash2 className="w-4 h-4" />
                                            </button>
                                        </div>
                                    </div>
                                ))}
                                {(!metaData.categories || metaData.categories.length === 0) && (
                                    <div className="text-center py-16 border-2 border-dashed border-slate-200 dark:border-white/5 rounded-[2rem]">
                                        <Scale className="w-10 h-10 mx-auto mb-3 opacity-20 text-slate-500" />
                                        <p className="text-sm text-slate-500 font-bold">No scoring criteria added yet.</p>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}

                    {activeTab === 'judges' && (
                        <div className="space-y-6">
                            <h3 className="text-sm font-black uppercase text-slate-500 tracking-widest border-b border-slate-200/50 dark:border-white/5 pb-4 flex items-center gap-2">
                                <Users className="w-4 h-4" /> Panel of Judges
                            </h3>

                            <div className="p-6 md:p-8 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl rounded-[2rem] border border-white/40 dark:border-white/10 shadow-sm">
                                <div className="flex flex-col md:flex-row gap-6 mb-8">
                                    <div className="flex-1 space-y-2">
                                        <h4 className="font-black text-lg text-slate-900 dark:text-white">Invite a Judge</h4>
                                        <p className="text-sm text-slate-500 font-medium">Judges have their scores tracked separately, giving organizers clear insight into expert opinions when picking winners.</p>
                                    </div>
                                    <div className="flex-1 relative z-50" ref={searchWrapperRef}>
                                        <div className="flex gap-2">
                                            <div className="relative flex-1 group">
                                                <UserIcon className="absolute left-4 top-3 w-4 h-4 text-slate-400 group-focus-within:text-modtale-accent transition-colors" />
                                                <input
                                                    type="text"
                                                    value={inviteUsername}
                                                    onChange={handleInputSearchChange}
                                                    onFocus={() => { if(searchResults.length > 0) setShowResults(true); }}
                                                    placeholder="Search username..."
                                                    className="w-full pl-11 pr-4 py-2.5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl font-bold shadow-sm outline-none focus:ring-2 focus:ring-modtale-accent transition-all"
                                                />
                                            </div>
                                            <button
                                                onClick={handleInviteJudge}
                                                disabled={isInviting || !inviteUsername.trim()}
                                                className="px-6 py-2.5 bg-modtale-accent hover:bg-modtale-accentHover text-white font-black rounded-xl shadow-lg shadow-modtale-accent/20 transition-all active:scale-95 disabled:opacity-50 flex items-center gap-2 shrink-0"
                                            >
                                                {isInviting ? <Spinner className="w-4 h-4" fullScreen={false} /> : <UserPlus className="w-4 h-4" />} Invite
                                            </button>
                                        </div>

                                        {showResults && searchResults.length > 0 && (
                                            <div className="absolute top-full left-0 right-[100px] mt-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-2xl shadow-xl overflow-hidden max-h-60 overflow-y-auto custom-scrollbar animate-in fade-in slide-in-from-top-2 duration-200">
                                                {searchResults.map(user => (
                                                    <button
                                                        key={user.id}
                                                        type="button"
                                                        onClick={() => handleSelectUser(user)}
                                                        className="w-full text-left px-4 py-3 hover:bg-slate-50 dark:hover:bg-white/5 flex items-center gap-3 transition-colors border-b border-slate-100 dark:border-white/5 last:border-0"
                                                    >
                                                        <div className="w-6 h-6 rounded-full bg-slate-200 dark:bg-white/10 overflow-hidden shrink-0">
                                                            <img src={user.avatarUrl} alt="" className="w-full h-full object-cover" />
                                                        </div>
                                                        <p className="text-sm font-bold text-slate-900 dark:text-white">{user.username}</p>
                                                    </button>
                                                ))}
                                            </div>
                                        )}

                                        {inviteStatus && (
                                            <p className={`text-xs font-bold mt-2 ml-1 ${inviteStatus.includes('success') ? 'text-green-500' : 'text-red-500'}`}>
                                                {inviteStatus}
                                            </p>
                                        )}
                                    </div>
                                </div>

                                <div className="space-y-3">
                                    {((metaData.judgeIds || []).length === 0 && (metaData.pendingJudgeInvites || []).length === 0) ? (
                                        <div className="text-center py-10 border-2 border-dashed border-slate-200 dark:border-white/5 rounded-2xl text-slate-400">
                                            <p className="font-bold text-sm">No judges invited yet.</p>
                                        </div>
                                    ) : (
                                        <>
                                            {(metaData.pendingJudgeInvites || []).map((username: string) => (
                                                <div key={username} className="flex items-center justify-between p-4 bg-slate-50 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-xl">
                                                    <div className="flex items-center gap-3">
                                                        <div className="w-8 h-8 rounded-full bg-slate-200 dark:bg-slate-700 flex items-center justify-center"><Users className="w-4 h-4 text-slate-400" /></div>
                                                        <div>
                                                            <div className="font-bold text-slate-900 dark:text-white">{username}</div>
                                                            <div className="text-[10px] font-black uppercase tracking-widest text-orange-500">Pending Invite</div>
                                                        </div>
                                                    </div>
                                                    <button onClick={() => handleRemoveJudge(username)} className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-500/10 rounded-lg transition-colors">
                                                        <Trash2 className="w-4 h-4" />
                                                    </button>
                                                </div>
                                            ))}
                                            {(metaData.judgeIds || []).map((id: string) => {
                                                const profile = judgeProfiles[id];
                                                return (
                                                    <div key={id} className="flex items-center justify-between p-4 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl shadow-sm">
                                                        <div className="flex items-center gap-3">
                                                            <div className="w-8 h-8 rounded-full bg-slate-200 dark:bg-white/10 overflow-hidden shrink-0 flex items-center justify-center border border-slate-200 dark:border-white/5">
                                                                {profile?.avatarUrl ? <img src={profile.avatarUrl} className="w-full h-full object-cover" alt="" /> : <CheckCircle2 className="w-4 h-4 text-modtale-accent" />}
                                                            </div>
                                                            <div>
                                                                <div className="font-bold text-slate-900 dark:text-white">{profile?.username || `ID: ${id.substring(0, 8)}`}</div>
                                                                <div className="text-[10px] font-black uppercase tracking-widest text-green-500">Active Judge</div>
                                                            </div>
                                                        </div>
                                                        {profile?.username && (
                                                            <button onClick={() => handleRemoveJudge(profile.username)} className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-500/10 rounded-lg transition-colors">
                                                                <Trash2 className="w-4 h-4" />
                                                            </button>
                                                        )}
                                                    </div>
                                                );
                                            })}
                                        </>
                                    )}
                                </div>
                            </div>
                        </div>
                    )}

                    {activeTab === 'restrictions' && (
                        <div className="space-y-6">
                            <h3 className="text-sm font-black uppercase text-slate-500 tracking-widest border-b border-slate-200/50 dark:border-white/5 pb-4 flex items-center gap-2">
                                <Shield className="w-4 h-4" /> Submission Restrictions
                            </h3>
                            <div className="p-6 md:p-8 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl rounded-[2rem] border border-white/40 dark:border-white/10 space-y-4 shadow-sm">

                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                        <div className="flex flex-col">
                                            <span className="text-sm font-bold text-slate-900 dark:text-white">Require New Project</span>
                                            <span className="text-xs text-slate-500 font-medium">Project must be created after jam starts</span>
                                        </div>
                                        <input
                                            type="checkbox"
                                            checked={metaData.restrictions?.requireNewProject || false}
                                            onChange={e => updateField('restrictions', {...metaData.restrictions, requireNewProject: e.target.checked})}
                                            className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                        />
                                    </label>

                                    <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                        <div className="flex flex-col">
                                            <span className="text-sm font-bold text-slate-900 dark:text-white">Require Source Repo</span>
                                            <span className="text-xs text-slate-500 font-medium">Must have a public repository linked</span>
                                        </div>
                                        <input
                                            type="checkbox"
                                            checked={metaData.restrictions?.requireSourceRepo || false}
                                            onChange={e => updateField('restrictions', {...metaData.restrictions, requireSourceRepo: e.target.checked})}
                                            className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                        />
                                    </label>

                                    <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                        <div className="flex flex-col">
                                            <span className="text-sm font-bold text-slate-900 dark:text-white">Require Open Source</span>
                                            <span className="text-xs text-slate-500 font-medium">Must use an OSI-approved open source license</span>
                                        </div>
                                        <input
                                            type="checkbox"
                                            checked={metaData.restrictions?.requireOsiLicense || false}
                                            onChange={e => updateField('restrictions', {...metaData.restrictions, requireOsiLicense: e.target.checked})}
                                            className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                        />
                                    </label>

                                    <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                        <div className="flex flex-col">
                                            <span className="text-sm font-bold text-slate-900 dark:text-white">Unique Submission</span>
                                            <span className="text-xs text-slate-500 font-medium">Cannot be submitted to other active jams</span>
                                        </div>
                                        <input
                                            type="checkbox"
                                            checked={metaData.restrictions?.requireUniqueSubmission || false}
                                            onChange={e => updateField('restrictions', {...metaData.restrictions, requireUniqueSubmission: e.target.checked})}
                                            className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                        />
                                    </label>

                                    <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                        <div className="flex flex-col">
                                            <span className="text-sm font-bold text-slate-900 dark:text-white">First-Time Jammer</span>
                                            <span className="text-xs text-slate-500 font-medium">Has never participated in a jam before</span>
                                        </div>
                                        <input
                                            type="checkbox"
                                            checked={metaData.restrictions?.requireNewbie || false}
                                            onChange={e => updateField('restrictions', {...metaData.restrictions, requireNewbie: e.target.checked, requirePriorJams: e.target.checked ? false : metaData.restrictions?.requirePriorJams})}
                                            className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                        />
                                    </label>

                                    <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                        <div className="flex flex-col">
                                            <span className="text-sm font-bold text-slate-900 dark:text-white">Veteran Jammer</span>
                                            <span className="text-xs text-slate-500 font-medium">Has participated in a previous jam</span>
                                        </div>
                                        <input
                                            type="checkbox"
                                            checked={metaData.restrictions?.requirePriorJams || false}
                                            onChange={e => updateField('restrictions', {...metaData.restrictions, requirePriorJams: e.target.checked, requireNewbie: e.target.checked ? false : metaData.restrictions?.requireNewbie})}
                                            className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                        />
                                    </label>

                                    <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                        <div className="flex flex-col">
                                            <span className="text-sm font-bold text-slate-900 dark:text-white">First-Time Modder</span>
                                            <span className="text-xs text-slate-500 font-medium">Has no previously published projects</span>
                                        </div>
                                        <input
                                            type="checkbox"
                                            checked={metaData.restrictions?.requireNoPriorProjects || false}
                                            onChange={e => updateField('restrictions', {...metaData.restrictions, requireNoPriorProjects: e.target.checked, requirePriorProjects: e.target.checked ? false : metaData.restrictions?.requirePriorProjects})}
                                            className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                        />
                                    </label>

                                    <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                        <div className="flex flex-col">
                                            <span className="text-sm font-bold text-slate-900 dark:text-white">Veteran Modder</span>
                                            <span className="text-xs text-slate-500 font-medium">Has at least one published project</span>
                                        </div>
                                        <input
                                            type="checkbox"
                                            checked={metaData.restrictions?.requirePriorProjects || false}
                                            onChange={e => updateField('restrictions', {...metaData.restrictions, requirePriorProjects: e.target.checked, requireNoPriorProjects: e.target.checked ? false : metaData.restrictions?.requireNoPriorProjects})}
                                            className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                        />
                                    </label>

                                    <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm md:col-span-2">
                                        <div className="flex flex-col">
                                            <span className="text-sm font-bold text-slate-900 dark:text-white">Hide Submissions</span>
                                            <span className="text-xs text-slate-500 font-medium">Keep project submissions secret until the voting phase begins</span>
                                        </div>
                                        <input
                                            type="checkbox"
                                            checked={metaData.hideSubmissions || false}
                                            onChange={e => updateField('hideSubmissions', e.target.checked)}
                                            className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                        />
                                    </label>
                                </div>

                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4 relative">
                                    <div className="bg-white dark:bg-slate-800/50 rounded-2xl border border-slate-200 dark:border-white/5 p-4 shadow-sm flex flex-col justify-center">
                                        <label className="text-sm font-bold text-slate-900 dark:text-white block mb-2">Contributors</label>
                                        <div className="flex items-center gap-4">
                                            <div className="flex-1">
                                                <label className="text-[10px] font-black uppercase text-slate-400 mb-1 ml-1">Min</label>
                                                <input
                                                    type="number" min="1"
                                                    value={metaData.restrictions?.minContributors || ''}
                                                    onChange={e => updateField('restrictions', {...metaData.restrictions, minContributors: parseInt(e.target.value) || undefined})}
                                                    className="w-full bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-xl px-4 py-2 font-bold shadow-sm outline-none focus:ring-2 focus:ring-modtale-accent"
                                                    placeholder="No min"
                                                />
                                            </div>
                                            <div className="flex-1">
                                                <label className="text-[10px] font-black uppercase text-slate-400 mb-1 ml-1">Max</label>
                                                <input
                                                    type="number" min="1"
                                                    value={metaData.restrictions?.maxContributors || ''}
                                                    onChange={e => updateField('restrictions', {...metaData.restrictions, maxContributors: parseInt(e.target.value) || undefined})}
                                                    className="w-full bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-xl px-4 py-2 font-bold shadow-sm outline-none focus:ring-2 focus:ring-modtale-accent"
                                                    placeholder="No max"
                                                />
                                            </div>
                                        </div>
                                    </div>

                                    <div className="bg-white dark:bg-slate-800/50 rounded-2xl border border-slate-200 dark:border-white/5 p-4 shadow-sm flex flex-col justify-center z-[20]">
                                        <label className="text-sm font-bold text-slate-900 dark:text-white block mb-1">Required Dependency</label>
                                        <span className="text-xs text-slate-500 font-medium block mb-2">Search to force a required library</span>
                                        <JamDependencySelector
                                            selectedId={metaData.restrictions?.requiredDependencyId}
                                            onChange={(id) => updateField('restrictions', {...metaData.restrictions, requiredDependencyId: id})}
                                        />
                                    </div>

                                    <div className="bg-white dark:bg-slate-800/50 rounded-2xl border border-slate-200 dark:border-white/5 p-4 shadow-sm flex flex-col justify-center">
                                        <label className="text-sm font-bold text-slate-900 dark:text-white block mb-1">Required Class / Package Usage</label>
                                        <span className="text-xs text-slate-500 font-medium block mb-2">Ensure jar utilizes an internal API</span>
                                        <input
                                            type="text"
                                            value={metaData.restrictions?.requiredClassUsage || ''}
                                            onChange={e => updateField('restrictions', {...metaData.restrictions, requiredClassUsage: e.target.value})}
                                            className="w-full bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-xl px-4 py-2 font-bold shadow-sm outline-none focus:ring-2 focus:ring-modtale-accent font-mono text-xs"
                                            placeholder="e.g., com.fox2code.hypertale.*"
                                        />
                                    </div>

                                    <div className="bg-white dark:bg-slate-800/50 rounded-2xl border border-slate-200 dark:border-white/5 p-4 shadow-sm flex flex-col justify-center z-[15]">
                                        <label className="text-sm font-bold text-slate-900 dark:text-white block mb-1">Game Version Lock</label>
                                        <span className="text-xs text-slate-500 font-medium block mb-2">Select allowed game versions</span>
                                        <MultiSelectDropdown
                                            options={gameVersionOptions}
                                            selected={metaData.restrictions?.allowedGameVersions || []}
                                            onChange={val => updateField('restrictions', {...metaData.restrictions, allowedGameVersions: val})}
                                            placeholder="Any Version"
                                            direction="up"
                                        />
                                    </div>

                                    <div className="bg-white dark:bg-slate-800/50 rounded-2xl border border-slate-200 dark:border-white/5 p-4 shadow-sm flex flex-col justify-center z-[10]">
                                        <label className="text-sm font-bold text-slate-900 dark:text-white block mb-1">Allowed Classifications</label>
                                        <span className="text-xs text-slate-500 font-medium block mb-2">Limit submissions to specific types</span>
                                        <MultiSelectDropdown
                                            options={classificationOptions}
                                            selected={metaData.restrictions?.allowedClassifications || []}
                                            onChange={val => updateField('restrictions', {...metaData.restrictions, allowedClassifications: val})}
                                            placeholder="Any Type"
                                            direction="up"
                                        />
                                    </div>

                                    <div className="bg-white dark:bg-slate-800/50 rounded-2xl border border-slate-200 dark:border-white/5 p-4 shadow-sm flex flex-col justify-center z-[5]">
                                        <label className="text-sm font-bold text-slate-900 dark:text-white block mb-1">Allowed Specific Licenses</label>
                                        <span className="text-xs text-slate-500 font-medium block mb-2">Restrict submissions to explicit licenses</span>
                                        <MultiSelectDropdown
                                            options={licenseOptions}
                                            selected={metaData.restrictions?.allowedLicenses || []}
                                            onChange={val => updateField('restrictions', {...metaData.restrictions, allowedLicenses: val})}
                                            placeholder="Any License"
                                            direction="up"
                                        />
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {activeTab === 'settings' && (
                        <div className="space-y-6">
                            <h3 className="text-sm font-black uppercase text-slate-500 tracking-widest border-b border-slate-200/50 dark:border-white/5 pb-4 flex items-center gap-2">
                                <Settings className="w-4 h-4" /> Configuration
                            </h3>
                            <div className="p-6 md:p-8 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl rounded-[2rem] border border-white/40 dark:border-white/10 space-y-4 shadow-sm">
                                <div className="mb-6 pb-6 border-b border-slate-200 dark:border-white/5">
                                    <div className="flex flex-col gap-2">
                                        <div><h3 className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-2"><Link2 className="w-4 h-4 text-slate-500" /> Jam Slug</h3><p className="text-xs text-slate-500">Customize the URL.</p></div>
                                        <div className={`flex items-center w-full bg-white dark:bg-black/20 border rounded-xl overflow-hidden focus-within:ring-2 focus-within:ring-modtale-accent transition-all ${slugError ? 'border-red-500' : 'border-slate-200 dark:border-white/10'}`}>
                                            <div className="px-4 py-3 bg-slate-50 dark:bg-white/5 border-r border-slate-200 dark:border-white/10 text-slate-500 text-sm font-mono whitespace-nowrap select-none">modtale.net/jam/</div>
                                            <input value={metaData.slug || ''} onChange={handleSlugChange} className={`flex-1 bg-transparent border-none px-4 py-3 text-sm font-mono text-slate-900 dark:text-white focus:outline-none placeholder:text-slate-400 ${slugError ? 'text-red-500' : ''}`} placeholder="jam-slug" />
                                        </div>
                                        {slugError && <p className="text-[10px] text-red-500 font-bold">{slugError}</p>}
                                    </div>
                                </div>

                                <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                    <div className="flex flex-col">
                                        <span className="text-sm font-bold text-slate-900 dark:text-white">One Entry per Person</span>
                                        <span className="text-xs text-slate-500 font-medium">Prevent users from submitting multiple projects to this jam</span>
                                    </div>
                                    <input
                                        type="checkbox"
                                        checked={metaData.oneEntryPerPerson !== false}
                                        onChange={e => updateField('oneEntryPerPerson', e.target.checked)}
                                        className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                    />
                                </label>

                                <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                    <div className="flex flex-col">
                                        <span className="text-sm font-bold text-slate-900 dark:text-white">Public Participation</span>
                                        <span className="text-xs text-slate-500 font-medium">Allow anyone to score entries</span>
                                    </div>
                                    <input
                                        type="checkbox"
                                        checked={metaData.allowPublicVoting !== false}
                                        onChange={e => updateField('allowPublicVoting', e.target.checked)}
                                        className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                    />
                                </label>

                                <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                    <div className="flex flex-col">
                                        <span className="text-sm font-bold text-slate-900 dark:text-white">Concurrent Voting</span>
                                        <span className="text-xs text-slate-500 font-medium">Allow users to vote while submissions are still open</span>
                                    </div>
                                    <input
                                        type="checkbox"
                                        checked={metaData.allowConcurrentVoting || false}
                                        onChange={e => updateField('allowConcurrentVoting', e.target.checked)}
                                        className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                    />
                                </label>

                                <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm">
                                    <div className="flex flex-col">
                                        <span className="text-sm font-bold text-slate-900 dark:text-white">Public Results</span>
                                        <span className="text-xs text-slate-500 font-medium">Show live scores and rankings before the jam finishes</span>
                                    </div>
                                    <input
                                        type="checkbox"
                                        checked={metaData.showResultsBeforeVotingEnds !== false}
                                        onChange={e => updateField('showResultsBeforeVotingEnds', e.target.checked)}
                                        className="w-5 h-5 rounded-md border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                    />
                                </label>
                            </div>
                        </div>
                    )}
                </div>
            }
        />
    );
};