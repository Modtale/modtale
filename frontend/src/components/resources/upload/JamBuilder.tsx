import React, { useState } from 'react';
import { Settings, Plus, Trash2, LayoutGrid, List, Sparkles, Trophy, FileText, Scale, Globe } from 'lucide-react';
import { JamLayout } from '@/components/jams/JamLayout';
import { JamDateInput } from '@/components/jams/JamCalendar';

export const JamBuilder: React.FC<any> = ({
                                              metaData, setMetaData, handleSave, isLoading, activeTab, setActiveTab, onBack, onPublish
                                          }) => {
    const [isDirty, setIsDirty] = useState(false);
    const [isSaved, setIsSaved] = useState(false);

    const publishChecklist = [
        { label: 'Title (min 5 chars)', met: (metaData.title?.trim().length || 0) >= 5 },
        { label: 'Description (min 20 chars)', met: (metaData.description?.trim().length || 0) >= 20 },
        { label: 'Start Date set in future', met: !!metaData.startDate && new Date(metaData.startDate) > new Date() },
        { label: 'Submission & Voting dates', met: !!metaData.endDate && !!metaData.votingEndDate && new Date(metaData.votingEndDate) > new Date(metaData.endDate) && new Date(metaData.endDate) > new Date(metaData.startDate) },
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

    return (
        <JamLayout
            isSaving={isLoading}
            isSaved={isSaved}
            hasUnsavedChanges={isDirty}
            onSave={performSave}
            onPublish={onPublish}
            onBack={onBack}
            bannerUrl={metaData.bannerUrl}
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
        >
            {activeTab === 'details' && (
                <div className="space-y-12 animate-in fade-in slide-in-from-bottom-2">
                    <div className="space-y-8">
                        <input
                            value={metaData.title}
                            onChange={e => updateField('title', e.target.value)}
                            placeholder="Enter a bold title..."
                            className="text-5xl font-black bg-transparent border-none outline-none w-full placeholder:text-slate-100 dark:placeholder:text-slate-800 focus:ring-0"
                        />

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
                    </div>

                    <div className="space-y-4">
                        <div className="flex items-center justify-between border-b border-slate-100 dark:border-white/5 pb-2">
                            <h3 className="text-xs font-black uppercase text-slate-500 tracking-widest flex items-center gap-2">
                                <List className="w-3 h-3" /> Event Summary
                            </h3>
                        </div>
                        <textarea
                            value={metaData.description}
                            onChange={e => updateField('description', e.target.value)}
                            placeholder="Rules, theme, goals, and glory..."
                            className="w-full min-h-[400px] bg-transparent border-none outline-none text-lg text-slate-700 dark:text-slate-300 font-medium resize-none leading-relaxed focus:ring-0"
                        />
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
                            className="h-12 px-6 bg-slate-100 dark:bg-white/5 rounded-2xl text-sm font-black flex items-center gap-2 hover:bg-slate-200 transition-all"
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
                    </div>
                </div>
            )}

            {activeTab === 'settings' && (
                <div className="space-y-12 animate-in fade-in slide-in-from-bottom-2">
                    <div>
                        <h2 className="text-3xl font-black">Jam Settings</h2>
                        <p className="text-sm text-slate-500 font-medium mt-1">Configure visibility and participation rules.</p>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <div className="p-8 bg-slate-50 dark:bg-black/20 rounded-[2rem] border border-slate-200 dark:border-white/5 space-y-6">
                            <div className="flex items-center gap-4 mb-2">
                                <div className="w-10 h-10 rounded-xl bg-blue-500/10 text-blue-500 flex items-center justify-center">
                                    <Globe className="w-5 h-5" />
                                </div>
                                <h3 className="font-black text-lg">Voting Policy</h3>
                            </div>

                            <label className="flex items-center justify-between p-4 bg-white dark:bg-slate-900 rounded-2xl cursor-pointer hover:border-modtale-accent border border-transparent transition-all shadow-sm">
                                <div className="flex flex-col">
                                    <span className="text-sm font-bold">Public Participation</span>
                                    <span className="text-xs text-slate-500 font-medium">Allow anyone to score entries</span>
                                </div>
                                <input
                                    type="checkbox"
                                    checked={metaData.allowPublicVoting}
                                    onChange={e => updateField('allowPublicVoting', e.target.checked)}
                                    className="w-6 h-6 rounded-lg border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                                />
                            </label>

                            <p className="text-xs text-slate-500 font-medium leading-relaxed px-2">
                                If public voting is disabled, only the host and designated judges will be able to score submissions.
                            </p>
                        </div>

                        <div className="p-8 bg-slate-50 dark:bg-black/20 rounded-[2rem] border border-slate-200 dark:border-white/5 flex flex-col items-center justify-center text-center">
                            <div className="w-16 h-16 rounded-3xl bg-modtale-accent/10 text-modtale-accent flex items-center justify-center mb-6">
                                <Sparkles className="w-8 h-8" />
                            </div>
                            <h3 className="font-black text-xl mb-2">Almost Ready</h3>
                            <p className="text-sm text-slate-500 font-medium max-w-xs">
                                Review your checklist to the right. Once all requirements are green, you can push your jam live.
                            </p>
                        </div>
                    </div>
                </div>
            )}
        </JamLayout>
    );
};