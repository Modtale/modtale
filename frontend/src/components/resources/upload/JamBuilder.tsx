import React, { useState } from 'react';
import { Settings, Plus, Trash2, LayoutGrid, List, Sparkles, Trophy, FileText, Scale, Globe } from 'lucide-react';
import { JamLayout } from '@/components/jams/JamLayout';
import { JamDateInput } from '@/components/jams/JamCalendar';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/cjs/styles/prism';

export const JamBuilder: React.FC<any> = ({
                                              metaData, setMetaData, handleSave, isLoading, activeTab, setActiveTab, onBack, onPublish
                                          }) => {
    const [isDirty, setIsDirty] = useState(false);
    const [isSaved, setIsSaved] = useState(false);
    const [editorMode, setEditorMode] = useState<'write' | 'preview'>('write');

    const publishChecklist = [
        { label: 'Title (min 5 chars)', met: (metaData.title || '').trim().length >= 5 },
        { label: 'Description (min 10 chars)', met: (metaData.description || '').trim().length >= 10 },
        { label: 'Start Date set', met: !!metaData.startDate },
        { label: 'Timeline follows order', met: !!metaData.endDate && !!metaData.votingEndDate && new Date(metaData.votingEndDate) > new Date(metaData.endDate) && new Date(metaData.endDate) > new Date(metaData.startDate) },
        { label: 'Scoring criteria set', met: (metaData.categories?.length || 0) > 0 }
    ];

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
            isSaving={isLoading}
            isSaved={isSaved}
            hasUnsavedChanges={isDirty}
            onSave={performSave}
            onPublish={onPublish}
            onBack={onBack}
            isPublished={isPublished}
            bannerUrl={metaData.bannerUrl}
            iconUrl={metaData.imageUrl}
            onBannerUpload={(f, p) => {
                markDirty();
                setMetaData((prev: any) => ({ ...prev, bannerUrl: p, bannerFile: f }));
            }}
            onIconUpload={(f, p) => {
                markDirty();
                setMetaData((prev: any) => ({ ...prev, imageUrl: p, iconFile: f }));
            }}
            isEditing={true}
            publishChecklist={publishChecklist}
            tabs={
                <div className="flex items-center gap-1">
                    {[
                        {id: 'details', icon: FileText, label: 'Details'},
                        {id: 'categories', icon: Scale, label: `Judging (${metaData.categories?.length || 0})`},
                        {id: 'settings', icon: Settings, label: 'Settings'}
                    ].map(t => (
                        <button
                            key={t.id}
                            type="button"
                            onClick={() => setActiveTab(t.id as any)}
                            className={`px-6 py-4 text-sm font-black border-b-4 transition-all flex items-center gap-2 ${activeTab === t.id ? 'border-modtale-accent text-slate-900 dark:text-white translate-y-[2px]' : 'border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-white'}`}
                        >
                            <t.icon className="w-4 h-4"/> {t.label}
                        </button>
                    ))}
                </div>
            }
            headerContent={
                <div className="flex items-center">
                    <input
                        value={metaData.title}
                        onChange={e => updateField('title', e.target.value)}
                        placeholder="Enter Jam Title"
                        className="text-4xl md:text-5xl font-black bg-transparent border-none outline-none w-full placeholder:text-slate-300 dark:placeholder:text-slate-700 focus:ring-0"
                    />
                </div>
            }
            mainContent={
                <>
                    {activeTab === 'details' && (
                        <div className="space-y-12 animate-in fade-in slide-in-from-bottom-2 h-full flex flex-col">
                            <div className="flex flex-col md:flex-row gap-4">
                                <JamDateInput
                                    label="Starts"
                                    icon={Sparkles}
                                    value={metaData.startDate}
                                    onChange={v => updateField('startDate', v)}
                                />
                                <JamDateInput
                                    label="Submissions Close"
                                    icon={LayoutGrid}
                                    value={metaData.endDate}
                                    minDate={metaData.startDate}
                                    onChange={v => updateField('endDate', v)}
                                />
                                <JamDateInput
                                    label="Voting Ends"
                                    icon={Trophy}
                                    value={metaData.votingEndDate}
                                    minDate={metaData.endDate}
                                    onChange={v => updateField('votingEndDate', v)}
                                />
                            </div>

                            <div className="flex-1 flex flex-col">
                                <div className="flex items-center justify-between mb-4 pb-2 border-b border-slate-200 dark:border-white/5">
                                    <h3 className="text-xs font-black uppercase text-slate-500 tracking-widest flex items-center gap-2">
                                        <List className="w-3 h-3" /> Event Summary
                                    </h3>
                                    <div className="flex bg-slate-100 dark:bg-slate-950/50 rounded-lg p-1 border border-slate-200 dark:border-white/10">
                                        <button type="button" onClick={() => setEditorMode('write')} className={`px-4 py-1.5 text-xs font-bold rounded-md ${editorMode === 'write' ? 'bg-modtale-accent text-white' : 'text-slate-500'}`}>Write</button>
                                        <button type="button" onClick={() => setEditorMode('preview')} className={`px-4 py-1.5 text-xs font-bold rounded-md ${editorMode === 'preview' ? 'bg-modtale-accent text-white' : 'text-slate-500'}`}>Preview</button>
                                    </div>
                                </div>
                                {editorMode === 'write' ? (
                                    <textarea
                                        value={metaData.description}
                                        onChange={e => updateField('description', e.target.value)}
                                        placeholder="# Welcome to the Jam!&#10;&#10;Describe the rules, theme, goals, and glory..."
                                        className="w-full min-h-[400px] bg-transparent border-none outline-none text-slate-700 dark:text-slate-300 font-mono text-sm resize-none focus:ring-0"
                                    />
                                ) : (
                                    <div className="prose dark:prose-invert prose-lg max-w-none min-h-[400px]">
                                        {metaData.description ? (
                                            <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeRaw, [rehypeSanitize, {...defaultSchema, attributes: {...defaultSchema.attributes, code: ['className']}}]]} components={MarkdownComponents}>
                                                {metaData.description}
                                            </ReactMarkdown>
                                        ) : <p className="text-slate-500 italic">No description provided.</p>}
                                    </div>
                                )}
                            </div>
                        </div>
                    )}

                    {activeTab === 'categories' && (
                        <div className="space-y-8 animate-in fade-in slide-in-from-bottom-2">
                            <div className="flex items-center justify-between">
                                <div>
                                    <h2 className="text-3xl font-black">Scoring</h2>
                                    <p className="text-sm text-slate-500 font-medium mt-1">Define criteria for judges and voters.</p>
                                </div>
                                <button
                                    type="button"
                                    onClick={() => updateField('categories', [...(metaData.categories || []), {name: '', description: '', maxScore: 5}])}
                                    className="h-12 px-6 bg-slate-100 dark:bg-white/5 rounded-2xl text-sm font-black flex items-center gap-2 hover:bg-slate-200 dark:hover:bg-white/10 transition-all"
                                >
                                    <Plus className="w-4 h-4" /> Add Criterion
                                </button>
                            </div>

                            <div className="grid gap-4">
                                {(metaData.categories || []).map((cat: any, i: number) => (
                                    <div key={i} className="flex flex-col sm:flex-row gap-6 p-8 bg-slate-50 dark:bg-black/20 rounded-[2rem] border border-slate-200 dark:border-white/5 group transition-all hover:border-modtale-accent/30">
                                        <div className="flex-1 space-y-4">
                                            <input
                                                value={cat.name}
                                                onChange={e => {
                                                    const c = [...metaData.categories];
                                                    c[i].name = e.target.value;
                                                    updateField('categories', c);
                                                }}
                                                className="w-full bg-white dark:bg-slate-900 border-none rounded-xl px-4 py-3 font-bold shadow-sm outline-none focus:ring-2 focus:ring-modtale-accent"
                                                placeholder="Criterion Name (e.g. Creativity)"
                                            />
                                            <input
                                                value={cat.description}
                                                onChange={e => {
                                                    const c = [...metaData.categories];
                                                    c[i].description = e.target.value;
                                                    updateField('categories', c);
                                                }}
                                                className="w-full bg-white dark:bg-slate-900 border-none rounded-xl px-4 py-3 text-sm shadow-sm outline-none focus:ring-2 focus:ring-modtale-accent"
                                                placeholder="Brief scoring guide..."
                                            />
                                        </div>
                                        <div className="flex items-center gap-4">
                                            <div className="w-24">
                                                <label className="block text-[10px] font-black uppercase text-slate-400 mb-1 ml-1">Max Score</label>
                                                <input
                                                    type="number"
                                                    value={cat.maxScore}
                                                    onChange={e => {
                                                        const c = [...metaData.categories];
                                                        c[i].maxScore = parseInt(e.target.value);
                                                        updateField('categories', c);
                                                    }}
                                                    className="w-full h-12 bg-white dark:bg-slate-900 border-none rounded-xl font-black text-center shadow-sm outline-none focus:ring-2 focus:ring-modtale-accent"
                                                />
                                            </div>
                                            <button
                                                type="button"
                                                onClick={() => updateField('categories', metaData.categories.filter((_:any, idx:number) => idx !== i))}
                                                className="p-3 bg-red-500/10 text-red-500 rounded-xl hover:bg-red-500 hover:text-white transition-all mt-5"
                                            >
                                                <Trash2 className="w-5 h-5" />
                                            </button>
                                        </div>
                                    </div>
                                ))}
                                {(!metaData.categories || metaData.categories.length === 0) && (
                                    <div className="text-center py-20 border-2 border-dashed border-slate-200 dark:border-white/5 rounded-[2rem]">
                                        <Scale className="w-12 h-12 mx-auto mb-4 opacity-20" />
                                        <p className="text-slate-500 font-bold">No scoring criteria added yet.</p>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}

                    {activeTab === 'settings' && (
                        <div className="space-y-8 animate-in fade-in slide-in-from-bottom-2">
                            <h2 className="text-3xl font-black">Jam Settings</h2>
                            <div className="p-8 bg-slate-50 dark:bg-black/20 rounded-[2rem] border border-slate-200 dark:border-white/5 space-y-6">
                                <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-900 rounded-2xl cursor-pointer hover:border-modtale-accent border border-transparent transition-all shadow-sm">
                                    <div className="flex flex-col">
                                        <span className="text-sm font-bold">Public Participation</span>
                                        <span className="text-xs text-slate-500 font-medium">Allow anyone to score entries</span>
                                    </div>
                                    <input
                                        type="checkbox"
                                        checked={metaData.allowPublicVoting !== false}
                                        onChange={e => updateField('allowPublicVoting', e.target.checked)}
                                        className="w-6 h-6 rounded-lg border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                    />
                                </label>

                                <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-900 rounded-2xl cursor-pointer hover:border-modtale-accent border border-transparent transition-all shadow-sm">
                                    <div className="flex flex-col">
                                        <span className="text-sm font-bold">Concurrent Voting</span>
                                        <span className="text-xs text-slate-500 font-medium">Allow users to vote while submissions are still open</span>
                                    </div>
                                    <input
                                        type="checkbox"
                                        checked={metaData.allowConcurrentVoting || false}
                                        onChange={e => updateField('allowConcurrentVoting', e.target.checked)}
                                        className="w-6 h-6 rounded-lg border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                    />
                                </label>

                                <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-900 rounded-2xl cursor-pointer hover:border-modtale-accent border border-transparent transition-all shadow-sm">
                                    <div className="flex flex-col">
                                        <span className="text-sm font-bold">Public Results</span>
                                        <span className="text-xs text-slate-500 font-medium">Show live scores and rankings before the jam finishes</span>
                                    </div>
                                    <input
                                        type="checkbox"
                                        checked={metaData.showResultsBeforeVotingEnds || false}
                                        onChange={e => updateField('showResultsBeforeVotingEnds', e.target.checked)}
                                        className="w-6 h-6 rounded-lg border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                    />
                                </label>
                            </div>
                        </div>
                    )}
                </>
            }
        />
    );
};