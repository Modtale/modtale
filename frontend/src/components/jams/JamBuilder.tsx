import React, { useState, useEffect, useRef } from 'react';
import { Settings, Plus, Trash2, List, Trophy, FileText, Scale, Save, CheckCircle2, AlertCircle, LayoutGrid, Edit3, Clock, Check, X, Shield, Calendar, Play, ChevronDown, Loader2, BookOpen, Wand2 } from 'lucide-react';
import { JamLayout } from '@/components/jams/JamLayout.tsx';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/cjs/styles/prism';
import { Spinner } from '@/components/ui/Spinner.tsx';
import { api, BACKEND_URL } from '@/utils/api';

const DateInput: React.FC<{ label: string, icon: any, value: string, minDate?: string, onChange: (v: string) => void }> = ({ label, icon: Icon, value, minDate, onChange }) => {
    const formatForInput = (isoString?: string) => {
        if (!isoString) return '';
        const d = new Date(isoString);
        if (isNaN(d.getTime())) return '';
        const pad = (n: number) => n.toString().padStart(2, '0');
        const Y = d.getFullYear();
        const M = pad(d.getMonth() + 1);
        const D = pad(d.getDate());
        const H = pad(d.getHours());
        const m = pad(d.getMinutes());
        return `${Y}-${M}-${D}T${H}:${m}`;
    };

    const handleInput = (val: string) => {
        if (!val) { onChange(''); return; }
        const d = new Date(val);
        onChange(d.toISOString());
    };

    return (
        <div className="flex flex-col bg-white/40 dark:bg-slate-900/40 backdrop-blur-2xl border border-white/60 dark:border-white/10 rounded-[1.25rem] md:rounded-[1.5rem] px-5 py-3 md:py-3.5 shadow-xl shadow-black/5 dark:shadow-none relative overflow-hidden group focus-within:border-modtale-accent focus-within:ring-1 focus-within:ring-modtale-accent transition-all min-w-[200px]">
            <div className="flex items-center gap-2 mb-1.5 text-slate-500">
                <Icon className="w-4 h-4 text-modtale-accent" />
                <span className="text-[10px] font-black uppercase tracking-widest">{label}</span>
            </div>
            <input
                type="datetime-local"
                value={formatForInput(value)}
                min={formatForInput(minDate)}
                onChange={(e) => handleInput(e.target.value)}
                className="w-full bg-transparent border-none p-0 text-sm md:text-base font-black text-slate-900 dark:text-white outline-none focus:ring-0 color-scheme-dark"
            />
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
        <div className="relative w-full" ref={ref}>
            <button
                type="button"
                onClick={() => setIsOpen(!isOpen)}
                className="w-full bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-xl px-4 py-2 font-bold shadow-sm outline-none focus:ring-2 focus:ring-modtale-accent flex justify-between items-center text-sm transition-all"
            >
                <span className="truncate">{selected.length > 0 ? `${selected.length} selected` : placeholder}</span>
                <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${isOpen && direction === 'down' ? 'rotate-180' : ''} ${!isOpen && direction === 'up' ? 'rotate-180' : ''}`} />
            </button>
            {isOpen && (
                <div className={`${popupClass} left-0 right-0 bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl shadow-2xl z-50 max-h-48 overflow-y-auto custom-scrollbar p-1`}>
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

    useEffect(() => {
        api.get('/meta/game-versions').then(res => {
            const versions = Array.isArray(res.data) ? res.data : (res.data.content || []);
            setGameVersionOptions(versions.map((v: string) => ({ label: v, value: v })));
        }).catch(() => {});
    }, []);

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
        const success = await handleSave();
        if (success) {
            setIsDirty(false);
            setIsSaved(true);
            setTimeout(() => setIsSaved(false), 3000);
        }
    };

    const updateField = (field: string, val: any) => {
        markDirty();
        setMetaData((prev: any) => ({ ...prev, [field]: val }));
    };

    const generateRulesText = () => {
        let text = "### Official Modjam Rules\n\nPlease read and agree to the following rules before submitting your project.\n\n";

        if (metaData.oneEntryPerPerson) text += "- **One Entry Limit:** Participants are restricted to a single project submission.\n";

        const res = metaData.restrictions;
        if (res?.requireNewProject) text += "- **Fresh Projects Only:** All submissions must be created *after* the jam start date. Old projects are not allowed.\n";
        if (res?.requireSourceRepo) text += "- **Open Source (Repo):** A public source code repository must be linked to your project.\n";
        if (res?.requireOsiLicense) text += "- **Open Source (License):** You must use an OSI-approved open source license.\n";
        if (res?.requireUniqueSubmission) text += "- **Unique Submission:** Your project cannot be entered into multiple active jams simultaneously.\n";
        if (res?.requireNewbie) text += "- **Newbies Only:** This jam is restricted to first-time jam participants.\n";
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
                            <div className="absolute bottom-full right-0 mb-3 w-64 bg-white dark:bg-slate-900 rounded-2xl shadow-2xl p-5 border border-slate-200 dark:border-white/10 opacity-0 group-hover:opacity-100 transition-all pointer-events-none translate-y-2 group-hover:translate-y-0 z-50">
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
                    <div className="flex items-center gap-6 md:gap-8 h-full overflow-x-auto [&::-webkit-scrollbar]:hidden [-ms-overflow-style:none] [scrollbar-width:none]">
                        {[
                            {id: 'details', icon: FileText, label: 'Overview'},
                            {id: 'schedule', icon: Calendar, label: 'Schedule'},
                            {id: 'rules', icon: BookOpen, label: 'Rules'},
                            {id: 'categories', icon: Scale, label: `Judging (${metaData.categories?.length || 0})`},
                            {id: 'restrictions', icon: Shield, label: 'Restrictions'},
                            {id: 'settings', icon: Settings, label: 'Settings'}
                        ].map(t => (
                            <button
                                key={t.id}
                                type="button"
                                onClick={() => setActiveTab(t.id as any)}
                                className={`pb-3 text-sm font-black uppercase tracking-widest transition-all flex items-center gap-2 whitespace-nowrap ${activeTab === t.id ? 'border-modtale-accent text-modtale-accent border-b-4 -mb-[2px]' : 'border-transparent text-slate-400 hover:text-slate-600 dark:hover:text-slate-200'}`}
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
                        <div className="space-y-6">
                            <h3 className="text-sm font-black uppercase text-slate-500 tracking-widest border-b border-slate-200/50 dark:border-white/5 pb-4 flex items-center gap-2">
                                <Calendar className="w-4 h-4" /> Timeline Configuration
                            </h3>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                                <div className="p-6 md:p-8 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl rounded-[2rem] border border-white/40 dark:border-white/10 shadow-sm flex flex-col items-center text-center focus-within:ring-2 focus-within:ring-modtale-accent transition-all">
                                    <div className="w-14 h-14 bg-blue-500/10 text-blue-500 rounded-full flex items-center justify-center mb-4">
                                        <Play className="w-6 h-6 ml-1" />
                                    </div>
                                    <h4 className="font-black text-xl mb-1 text-slate-900 dark:text-white">Jam Starts</h4>
                                    <p className="text-xs text-slate-500 mb-6 font-medium">When users can begin submitting projects to the jam.</p>
                                    <div className="w-full bg-slate-50 dark:bg-slate-800/50 rounded-xl p-3 border border-slate-200 dark:border-white/5">
                                        <input
                                            type="datetime-local"
                                            value={metaData.startDate ? new Date(new Date(metaData.startDate).getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16) : ''}
                                            onChange={e => updateField('startDate', e.target.value ? new Date(e.target.value).toISOString() : '')}
                                            className="w-full bg-transparent border-none p-0 text-sm font-black text-center text-slate-900 dark:text-white outline-none focus:ring-0 color-scheme-dark"
                                        />
                                    </div>
                                </div>

                                <div className="p-6 md:p-8 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl rounded-[2rem] border border-white/40 dark:border-white/10 shadow-sm flex flex-col items-center text-center focus-within:ring-2 focus-within:ring-modtale-accent transition-all">
                                    <div className="w-14 h-14 bg-orange-500/10 text-orange-500 rounded-full flex items-center justify-center mb-4">
                                        <LayoutGrid className="w-6 h-6" />
                                    </div>
                                    <h4 className="font-black text-xl mb-1 text-slate-900 dark:text-white">Submissions Close</h4>
                                    <p className="text-xs text-slate-500 mb-6 font-medium">When the deadline passes and entries are locked.</p>
                                    <div className="w-full bg-slate-50 dark:bg-slate-800/50 rounded-xl p-3 border border-slate-200 dark:border-white/5">
                                        <input
                                            type="datetime-local"
                                            min={metaData.startDate ? new Date(new Date(metaData.startDate).getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16) : undefined}
                                            value={metaData.endDate ? new Date(new Date(metaData.endDate).getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16) : ''}
                                            onChange={e => updateField('endDate', e.target.value ? new Date(e.target.value).toISOString() : '')}
                                            className="w-full bg-transparent border-none p-0 text-sm font-black text-center text-slate-900 dark:text-white outline-none focus:ring-0 color-scheme-dark"
                                        />
                                    </div>
                                </div>

                                <div className="p-6 md:p-8 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl rounded-[2rem] border border-white/40 dark:border-white/10 shadow-sm flex flex-col items-center text-center focus-within:ring-2 focus-within:ring-modtale-accent transition-all">
                                    <div className="w-14 h-14 bg-purple-500/10 text-purple-500 rounded-full flex items-center justify-center mb-4">
                                        <Trophy className="w-6 h-6" />
                                    </div>
                                    <h4 className="font-black text-xl mb-1 text-slate-900 dark:text-white">Voting Ends</h4>
                                    <p className="text-xs text-slate-500 mb-6 font-medium">When judging closes and results can be finalized.</p>
                                    <div className="w-full bg-slate-50 dark:bg-slate-800/50 rounded-xl p-3 border border-slate-200 dark:border-white/5">
                                        <input
                                            type="datetime-local"
                                            min={metaData.endDate ? new Date(new Date(metaData.endDate).getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16) : undefined}
                                            value={metaData.votingEndDate ? new Date(new Date(metaData.votingEndDate).getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16) : ''}
                                            onChange={e => updateField('votingEndDate', e.target.value ? new Date(e.target.value).toISOString() : '')}
                                            className="w-full bg-transparent border-none p-0 text-sm font-black text-center text-slate-900 dark:text-white outline-none focus:ring-0 color-scheme-dark"
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

                                    <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-800/50 rounded-2xl cursor-pointer hover:border-modtale-accent border border-slate-200 dark:border-white/5 transition-all shadow-sm md:col-span-2">
                                        <div className="flex flex-col">
                                            <span className="text-sm font-bold text-slate-900 dark:text-white">Newbie Only</span>
                                            <span className="text-xs text-slate-500 font-medium">Only users with no prior jam participations</span>
                                        </div>
                                        <input
                                            type="checkbox"
                                            checked={metaData.restrictions?.requireNewbie || false}
                                            onChange={e => updateField('restrictions', {...metaData.restrictions, requireNewbie: e.target.checked})}
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

                                    <div className="bg-white dark:bg-slate-800/50 rounded-2xl border border-slate-200 dark:border-white/5 p-4 shadow-sm flex flex-col justify-center z-20">
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