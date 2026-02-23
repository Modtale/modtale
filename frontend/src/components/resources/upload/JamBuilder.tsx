import React, { useState } from 'react';
import { Settings, Plus, Trash2, List, Sparkles, Trophy, FileText, Scale, Save, CheckCircle2, Rocket, AlertCircle, LayoutGrid, Edit3 } from 'lucide-react';
import { JamLayout } from '@/components/jams/JamLayout';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/cjs/styles/prism';
import { Spinner } from '@/components/ui/Spinner';

const DateInput: React.FC<{ label: string, icon: any, value: string, minDate?: string, onChange: (v: string) => void }> = ({ label, icon: Icon, value, minDate, onChange }) => (
    <div className="flex flex-col bg-white/40 dark:bg-slate-900/40 backdrop-blur-2xl border border-white/60 dark:border-white/10 rounded-[1.25rem] md:rounded-[1.5rem] px-5 py-3 md:py-3.5 shadow-xl shadow-black/5 dark:shadow-none relative overflow-hidden group focus-within:border-modtale-accent focus-within:ring-1 focus-within:ring-modtale-accent transition-all min-w-[200px]">
        <div className="flex items-center gap-2 mb-1.5 text-slate-500">
            <Icon className="w-4 h-4 text-modtale-accent" />
            <span className="text-[10px] font-black uppercase tracking-widest">{label}</span>
        </div>
        <input
            type="datetime-local"
            value={value ? new Date(new Date(value).getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16) : ''}
            min={minDate ? new Date(new Date(minDate).getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16) : undefined}
            onChange={(e) => {
                if (e.target.value) {
                    const d = new Date(e.target.value);
                    onChange(d.toISOString());
                } else {
                    onChange('');
                }
            }}
            className="w-full bg-transparent border-none p-0 text-sm md:text-base font-black text-slate-900 dark:text-white outline-none focus:ring-0 color-scheme-dark"
        />
    </div>
);

export const JamBuilder: React.FC<any> = ({
                                              metaData, setMetaData, handleSave, isLoading, activeTab, setActiveTab, onBack, onPublish
                                          }) => {
    const [isDirty, setIsDirty] = useState(false);
    const [isSaved, setIsSaved] = useState(false);
    const [editorMode, setEditorMode] = useState<'write' | 'preview'>('write');
    const [isEditingTitle, setIsEditingTitle] = useState(false);

    const publishChecklist = [
        { label: 'Title (min 5 chars)', met: (metaData.title || '').trim().length >= 5 },
        { label: 'Description (min 10 chars)', met: (metaData.description || '').trim().length >= 10 },
        { label: 'Start Date set', met: !!metaData.startDate },
        { label: 'Timeline follows order', met: !!metaData.endDate && !!metaData.votingEndDate && new Date(metaData.votingEndDate) > new Date(metaData.endDate) && new Date(metaData.endDate) > new Date(metaData.startDate) },
        { label: 'Scoring criteria set', met: (metaData.categories?.length || 0) > 0 }
    ];

    const isReadyToPublish = publishChecklist.every(c => c.met) && !isDirty;

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

    const isPublished = metaData.status && metaData.status !== 'DRAFT';

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
                    <span className="w-2.5 h-2.5 rounded-full bg-modtale-accent animate-pulse" /> Editing Draft
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

                    <button
                        type="button"
                        onClick={(e) => { e.preventDefault(); onPublish(); }}
                        disabled={!isReadyToPublish || isLoading}
                        className="h-12 md:h-14 px-6 md:px-8 bg-modtale-accent hover:bg-modtale-accentHover disabled:bg-slate-300 dark:disabled:bg-slate-800 disabled:text-slate-500 text-white rounded-[1rem] md:rounded-[1.25rem] font-black text-sm transition-all flex items-center justify-center gap-2 shadow-xl shadow-modtale-accent/20 enabled:active:scale-95"
                    >
                        <Rocket className="w-4 h-4" />
                        <span className="hidden sm:inline">{isPublished ? 'Save & Close' : 'Publish Jam'}</span>
                    </button>
                </div>
            }
            tabsAndTimers={
                <div className="flex flex-col xl:flex-row xl:items-end justify-between gap-4 border-b-2 border-slate-200/50 dark:border-white/5 pb-3 xl:pb-0">
                    <div className="flex items-center gap-6 md:gap-8 h-full">
                        {[
                            {id: 'details', icon: FileText, label: 'Overview'},
                            {id: 'categories', icon: Scale, label: `Judging (${metaData.categories?.length || 0})`},
                            {id: 'settings', icon: Settings, label: 'Settings'}
                        ].map(t => (
                            <button
                                key={t.id}
                                type="button"
                                onClick={() => setActiveTab(t.id as any)}
                                className={`pb-3 text-sm font-black uppercase tracking-widest transition-all flex items-center gap-2 ${activeTab === t.id ? 'border-modtale-accent text-modtale-accent border-b-4 -mb-[2px]' : 'border-transparent text-slate-400 hover:text-slate-600 dark:hover:text-slate-200'}`}
                            >
                                <t.icon className="w-4 h-4"/> {t.label}
                            </button>
                        ))}
                    </div>
                    <div className="flex flex-wrap items-center gap-3 pb-3 xl:pb-2">
                        <DateInput label="Starts" icon={Sparkles} value={metaData.startDate} onChange={v => updateField('startDate', v)} />
                        <DateInput label="Submissions Close" icon={LayoutGrid} value={metaData.endDate} minDate={metaData.startDate} onChange={v => updateField('endDate', v)} />
                        <DateInput label="Voting Ends" icon={Trophy} value={metaData.votingEndDate} minDate={metaData.endDate} onChange={v => updateField('votingEndDate', v)} />
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
                                    placeholder="# Welcome to the Jam!&#10;&#10;Describe the rules, theme, goals, and glory..."
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
                                                className="w-10 h-10 bg-red-500/10 text-red-500 rounded-xl hover:bg-red-500 hover:text-white transition-all flex items-center justify-center mt-3.5"
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

                    {activeTab === 'settings' && (
                        <div className="space-y-8">
                            <div className="bg-white/40 dark:bg-slate-900/40 backdrop-blur-xl border border-white/40 dark:border-white/10 rounded-[2rem] p-6 md:p-8 shadow-sm">
                                <h3 className="text-sm font-black uppercase text-slate-500 tracking-widest mb-6">Launch Checklist</h3>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    {publishChecklist.map((req, i) => (
                                        <div key={i} className="flex items-start gap-3 bg-white/50 dark:bg-slate-800/50 p-4 rounded-2xl border border-white/50 dark:border-white/5">
                                            <div className={`mt-0.5 shrink-0 transition-colors ${req.met ? 'text-green-500' : 'text-slate-400 dark:text-slate-600'}`}>
                                                {req.met ? <CheckCircle2 className="w-5 h-5" /> : <AlertCircle className="w-5 h-5" />}
                                            </div>
                                            <span className={`text-sm font-bold transition-colors ${req.met ? 'text-slate-900 dark:text-slate-200' : 'text-slate-500'}`}>{req.label}</span>
                                        </div>
                                    ))}
                                </div>
                                {isDirty && (
                                    <div className="flex items-center gap-3 mt-6 p-4 bg-amber-500/10 border border-amber-500/20 rounded-2xl">
                                        <AlertCircle className="w-5 h-5 text-amber-500 shrink-0" />
                                        <span className="text-sm font-bold text-amber-600 dark:text-amber-400">You have unsaved changes. Save before publishing.</span>
                                    </div>
                                )}
                            </div>

                            <div className="space-y-6">
                                <h3 className="text-sm font-black uppercase text-slate-500 tracking-widest border-b border-slate-200/50 dark:border-white/5 pb-4 flex items-center gap-2">
                                    <Settings className="w-4 h-4" /> Configuration
                                </h3>
                                <div className="p-6 md:p-8 bg-white/60 dark:bg-slate-900/60 backdrop-blur-xl rounded-[2rem] border border-white/40 dark:border-white/10 space-y-4 shadow-sm">
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
                        </div>
                    )}
                </div>
            }
        />
    );
};