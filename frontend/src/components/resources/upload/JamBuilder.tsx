import React, { useState } from 'react';
import {
    Settings, Plus, Trash2, LayoutGrid, FileText, Scale, Save, CheckCircle2, AlertCircle, Rocket, Check, X, UploadCloud,
    Info, Trophy, Sparkles
} from 'lucide-react';
import { JamLayout } from '@/components/jams/JamLayout';
import { JamDateInput } from '@/components/jams/JamCalendar';
import { SidebarSection } from '@/components/resources/ProjectLayout';
import { Spinner } from '@/components/ui/Spinner';
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
    const [editorMode, setEditorMode] = useState<'write' | 'preview'>('write');

    const publishChecklist = [
        { label: 'Title (min 5 chars)', met: (metaData.title?.trim().length || 0) >= 5 },
        { label: 'Description (min 20 chars)', met: (metaData.description?.trim().length || 0) >= 20 },
        { label: 'Start Date set in future', met: !!metaData.startDate && new Date(metaData.startDate) > new Date() },
        { label: 'Timeline follows order', met: !!metaData.endDate && !!metaData.votingEndDate && new Date(metaData.votingEndDate) > new Date(metaData.endDate) && new Date(metaData.endDate) > new Date(metaData.startDate) },
        { label: 'At least one category', met: (metaData.categories?.length || 0) > 0 },
        { label: 'All changes saved', met: !isDirty }
    ];

    const isReadyToPublish = publishChecklist.every(c => c.met);
    const metCount = publishChecklist.filter(c => c.met).length;

    const markDirty = () => { setIsDirty(true); };

    const updateField = (field: string, val: any) => {
        markDirty();
        setMetaData((prev: any) => ({ ...prev, [field]: val }));
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

    return (
        <JamLayout
            isEditing={true}
            bannerUrl={metaData.bannerUrl}
            iconUrl={metaData.imageUrl}
            onBannerUpload={(f, p) => updateField('bannerUrl', p)}
            onIconUpload={(f, p) => updateField('imageUrl', p)}
            onBack={onBack}
            headerContent={
                <input
                    value={metaData.title}
                    onChange={e => updateField('title', e.target.value)}
                    placeholder="Enter Jam Title"
                    className="text-4xl md:text-5xl font-black bg-transparent border-none outline-none w-full placeholder:text-slate-300 dark:placeholder:text-slate-700 focus:ring-0"
                />
            }
            headerActions={
                <>
                    {isDirty && <div className="text-[10px] font-bold text-amber-500 animate-pulse uppercase tracking-widest bg-amber-500/10 px-2 py-1 rounded">Unsaved Changes</div>}
                    <button
                        type="button"
                        onClick={() => { handleSave(); setIsDirty(false); }}
                        disabled={isLoading}
                        className="h-10 px-5 rounded-xl border border-slate-200 dark:border-white/10 bg-slate-100 dark:bg-white/5 font-bold flex items-center gap-2 hover:bg-slate-200 dark:hover:bg-white/10 transition-colors"
                    >
                        {isLoading ? <Spinner className="w-4 h-4"/> : <Save className="w-4 h-4" />} Save
                    </button>

                    <div className="relative group">
                        <div className="absolute bottom-full right-0 mb-3 w-64 bg-white dark:bg-slate-900 rounded-xl shadow-2xl p-4 border border-slate-200 dark:border-white/10 opacity-0 group-hover:opacity-100 transition-all pointer-events-none translate-y-2 group-hover:translate-y-0 z-50">
                            <div className="flex items-center justify-between mb-3 border-b border-slate-100 dark:border-white/5 pb-2">
                                <span className="text-xs font-black uppercase text-slate-500 tracking-widest">Requirements</span>
                                <span className={`text-xs font-bold ${isReadyToPublish ? 'text-green-500' : 'text-slate-400'}`}>
                                    {metCount}/{publishChecklist.length}
                                </span>
                            </div>
                            <div className="space-y-2">
                                {publishChecklist.map((req, i) => (
                                    <div key={i} className="flex items-center gap-2.5">
                                        <div className={`w-4 h-4 rounded-full flex items-center justify-center flex-shrink-0 ${req.met ? 'bg-green-500 text-white' : 'bg-slate-200 dark:bg-white/10 text-slate-400'}`}>
                                            {req.met ? <Check className="w-2.5 h-2.5" strokeWidth={3} /> : <X className="w-2.5 h-2.5" strokeWidth={3} />}
                                        </div>
                                        <span className={`text-xs font-bold ${req.met ? 'text-slate-900 dark:text-white' : 'text-slate-500'}`}>{req.label}</span>
                                    </div>
                                ))}
                            </div>
                            <div className="absolute top-full right-8 -mt-1.5 border-8 border-transparent border-t-white dark:border-t-slate-900" />
                        </div>
                        <button
                            type="button"
                            onClick={onPublish}
                            disabled={isLoading || !isReadyToPublish}
                            className="h-10 bg-green-500 hover:bg-green-600 disabled:bg-slate-200 dark:disabled:bg-slate-800 disabled:text-slate-400 disabled:shadow-none text-white px-6 rounded-xl font-black flex items-center justify-center gap-2 shadow-lg transition-all"
                        >
                            <UploadCloud className="w-5 h-5" /> Publish
                        </button>
                    </div>
                </>
            }
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
            mainContent={
                <>
                    {activeTab === 'details' && (
                        <div className="space-y-8 animate-in fade-in slide-in-from-bottom-2 h-full flex flex-col">
                            <div className="flex flex-col xl:flex-row gap-4">
                                <JamDateInput label="Starts" icon={Sparkles} value={metaData.startDate} onChange={v => updateField('startDate', v)} />
                                <JamDateInput label="Submissions Close" icon={LayoutGrid} value={metaData.endDate} minDate={metaData.startDate} onChange={v => updateField('endDate', v)} />
                                <JamDateInput label="Voting Ends" icon={Trophy} value={metaData.votingEndDate} minDate={metaData.endDate} onChange={v => updateField('votingEndDate', v)} />
                            </div>

                            <div className="flex-1 flex flex-col">
                                <div className="flex items-center justify-between mb-4 pb-2 border-b border-slate-200 dark:border-white/5">
                                    <h3 className="text-xs font-black uppercase text-slate-500 tracking-widest flex items-center gap-2"><FileText className="w-3 h-3"/> Event Summary</h3>
                                    <div className="flex bg-slate-100 dark:bg-slate-950/50 rounded-lg p-1 border border-slate-200 dark:border-white/10">
                                        <button type="button" onClick={() => setEditorMode('write')} className={`px-4 py-1.5 text-xs font-bold rounded-md ${editorMode === 'write' ? 'bg-modtale-accent text-white' : 'text-slate-500'}`}>Write</button>
                                        <button type="button" onClick={() => setEditorMode('preview')} className={`px-4 py-1.5 text-xs font-bold rounded-md ${editorMode === 'preview' ? 'bg-modtale-accent text-white' : 'text-slate-500'}`}>Preview</button>
                                    </div>
                                </div>
                                {editorMode === 'write' ? (
                                    <textarea
                                        value={metaData.description}
                                        onChange={e => updateField('description', e.target.value)}
                                        className="flex-1 w-full h-full min-h-[400px] bg-transparent border-none outline-none text-slate-900 dark:text-slate-300 font-mono text-sm resize-none"
                                        placeholder="# Event Description..."
                                    />
                                ) : (
                                    <div className="prose dark:prose-invert prose-lg max-w-none min-h-[400px]">
                                        {metaData.description ? (
                                            <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeRaw, [rehypeSanitize, {...defaultSchema, attributes: {...defaultSchema.attributes, code: ['className']}}]]} components={MarkdownComponents}>
                                                {metaData.description}
                                            </ReactMarkdown>
                                        ) : <p className="text-slate-500 italic">No description.</p>}
                                    </div>
                                )}
                            </div>
                        </div>
                    )}

                    {activeTab === 'categories' && (
                        <div className="space-y-8 animate-in fade-in slide-in-from-bottom-2">
                            <div className="flex items-center justify-between">
                                <div>
                                    <h2 className="text-2xl font-black">Scoring Categories</h2>
                                    <p className="text-sm text-slate-500 font-medium">Define criteria for judges and voters.</p>
                                </div>
                                <button type="button" onClick={() => updateField('categories', [...(metaData.categories || []), {name: '', description: '', maxScore: 5}])} className="h-10 px-4 bg-slate-100 dark:bg-white/5 rounded-xl text-sm font-bold flex items-center gap-2 hover:bg-slate-200 dark:hover:bg-white/10 transition-colors">
                                    <Plus className="w-4 h-4" /> Add
                                </button>
                            </div>

                            <div className="grid gap-4">
                                {(metaData.categories || []).map((cat: any, i: number) => (
                                    <div key={i} className="flex flex-col xl:flex-row gap-4 p-6 bg-slate-50 dark:bg-slate-950/30 rounded-2xl border border-slate-200 dark:border-white/5 group transition-colors hover:border-modtale-accent/50">
                                        <div className="flex-1 space-y-3">
                                            <input value={cat.name} onChange={e => { const c = [...metaData.categories]; c[i].name = e.target.value; updateField('categories', c); }} className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2.5 font-bold outline-none focus:ring-2 focus:ring-modtale-accent" placeholder="Criterion Name (e.g. Visuals)" />
                                            <input value={cat.description} onChange={e => { const c = [...metaData.categories]; c[i].description = e.target.value; updateField('categories', c); }} className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-modtale-accent" placeholder="Scoring details..." />
                                        </div>
                                        <div className="flex items-center gap-3">
                                            <div className="w-24">
                                                <label className="block text-[10px] font-black uppercase text-slate-500 mb-1">Max Score</label>
                                                <input type="number" value={cat.maxScore} onChange={e => { const c = [...metaData.categories]; c[i].maxScore = parseInt(e.target.value); updateField('categories', c); }} className="w-full h-11 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl font-bold text-center outline-none focus:ring-2 focus:ring-modtale-accent" />
                                            </div>
                                            <button type="button" onClick={() => updateField('categories', metaData.categories.filter((_:any, idx:number) => idx !== i))} className="p-3 bg-red-50 text-red-500 dark:bg-red-500/10 hover:bg-red-100 dark:hover:bg-red-500/20 rounded-xl transition-colors mt-5">
                                                <Trash2 className="w-4 h-4" />
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {activeTab === 'settings' && (
                        <div className="space-y-8 animate-in fade-in slide-in-from-bottom-2">
                            <h2 className="text-2xl font-black">Jam Settings</h2>
                            <div className="p-6 bg-slate-50 dark:bg-slate-950/30 rounded-2xl border border-slate-200 dark:border-white/5">
                                <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl cursor-pointer hover:border-modtale-accent transition-colors">
                                    <div className="flex flex-col">
                                        <span className="text-sm font-bold">Public Voting</span>
                                        <span className="text-xs text-slate-500 font-medium">Allow the community to score entries</span>
                                    </div>
                                    <input type="checkbox" checked={metaData.allowPublicVoting} onChange={e => updateField('allowPublicVoting', e.target.checked)} className="w-5 h-5 rounded text-modtale-accent focus:ring-modtale-accent" />
                                </label>
                            </div>
                        </div>
                    )}
                </>
            }
            sidebarContent={
                <SidebarSection title="Quick Summary" icon={Info}>
                    <div className="bg-slate-50 dark:bg-slate-950/30 rounded-xl border border-slate-200 dark:border-white/10 p-4 space-y-3">
                        <div className="flex justify-between text-xs">
                            <span className="font-bold text-slate-500">Categories</span>
                            <span className="font-black">{metaData.categories?.length || 0}</span>
                        </div>
                        <div className="flex justify-between text-xs">
                            <span className="font-bold text-slate-500">Description Length</span>
                            <span className={`font-black ${metaData.description?.length >= 20 ? 'text-green-500' : 'text-slate-900 dark:text-white'}`}>{metaData.description?.length || 0} chars</span>
                        </div>
                        <div className="flex justify-between text-xs">
                            <span className="font-bold text-slate-500">Public Voting</span>
                            <span className={`font-black ${metaData.allowPublicVoting ? 'text-green-500' : 'text-slate-500'}`}>{metaData.allowPublicVoting ? 'Yes' : 'No'}</span>
                        </div>
                    </div>
                </SidebarSection>
            }
        />
    );
};