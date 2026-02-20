import React, { useState } from 'react';
import {
    Settings,
    Plus,
    Trash2,
    LayoutGrid,
    List,
    Sparkles,
    Trophy,
    ArrowRight,
    FileText,
    Scale,
    ChevronLeft
} from 'lucide-react';
import { JamLayout } from '@/components/jams/JamLayout';
import { JamDateInput } from '@/components/jams/JamCalendar';
import { SidebarSection } from '@/components/resources/ProjectLayout';

export const JamBuilder: React.FC<any> = ({
                                              metaData, setMetaData, handleSave, isLoading, activeTab, setActiveTab, onBack, onPublish
                                          }) => {
    const [isDirty, setIsDirty] = useState(false);
    const [isSaved, setIsSaved] = useState(false);

    const publishChecklist = [
        { label: 'Title (min 5 chars)', met: (metaData.title?.trim().length || 0) >= 5 },
        { label: 'Description (min 20 chars)', met: (metaData.description?.trim().length || 0) >= 20 },
        { label: 'Start Date set in future', met: !!metaData.startDate && new Date(metaData.startDate) > new Date() },
        { label: 'Submission & Voting dates', met: !!metaData.endDate && !!metaData.votingEndDate && metaData.votingEndDate > metaData.endDate && metaData.endDate > metaData.startDate },
        { label: 'Scoring categories set', met: (metaData.categories?.length || 0) > 0 }
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
        setMetaData({ ...metaData, [field]: val });
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
            sidebar={
                <SidebarSection title="Configuration" icon={Settings}>
                    <div className="space-y-4 pt-2">
                        <label className="flex items-center justify-between p-4 bg-slate-50 dark:bg-black/20 rounded-2xl cursor-pointer hover:bg-slate-100 dark:hover:bg-white/5 transition-colors">
                            <div className="flex flex-col">
                                <span className="text-xs font-bold">Public Voting</span>
                                <span className="text-[10px] text-slate-500 font-medium">Community can score</span>
                            </div>
                            <input
                                type="checkbox"
                                checked={metaData.allowPublicVoting}
                                onChange={e => updateField('allowPublicVoting', e.target.checked)}
                                className="w-5 h-5 rounded-lg border-slate-300 text-modtale-accent focus:ring-modtale-accent transition-all"
                            />
                        </label>
                    </div>
                </SidebarSection>
            }
        >
            {activeTab === 'details' && (
                <div className="space-y-12 animate-in fade-in slide-in-from-bottom-2">
                    <div className="space-y-8">
                        <input
                            value={metaData.title}
                            onChange={e => updateField('title', e.target.value)}
                            placeholder="Enter a bold title..."
                            className="text-5xl font-black bg-transparent border-none outline-none w-full placeholder:text-slate-100 dark:placeholder:text-slate-800"
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
                            className="w-full min-h-[300px] bg-transparent border-none outline-none text-lg text-slate-700 dark:text-slate-300 font-medium resize-none leading-relaxed"
                        />
                    </div>

                    <div className="pt-10 flex justify-end">
                        <button
                            type="button"
                            onClick={() => setActiveTab('categories')}
                            className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 px-8 py-4 rounded-2xl font-black flex items-center gap-2 hover:scale-105 transition-all shadow-xl"
                        >
                            Next: Setup Judging <ArrowRight className="w-5 h-5" />
                        </button>
                    </div>
                </div>
            )}

            {activeTab === 'categories' && (
                <div className="space-y-8 animate-in fade-in slide-in-from-bottom-2">
                    <div className="flex items-center justify-between">
                        <div>
                            <h2 className="text-3xl font-black">Scoring</h2>
                            <p className="text-slate-500 font-medium mt-1">Define criteria for judges and voters.</p>
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
                                        className="w-full bg-white dark:bg-slate-900 border-none rounded-xl px-4 py-3 font-bold shadow-sm"
                                        placeholder="Criterion Name (e.g. Creativity)"
                                    />
                                    <input
                                        value={cat.description}
                                        onChange={e => {
                                            const c = [...metaData.categories];
                                            c[i].description = e.target.value;
                                            updateField('categories', c);
                                        }}
                                        className="w-full bg-white dark:bg-slate-900 border-none rounded-xl px-4 py-3 text-sm shadow-sm"
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
                                            className="w-full h-12 bg-white dark:bg-slate-900 border-none rounded-xl font-black text-center shadow-sm"
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

                    <div className="pt-10 flex justify-between">
                        <button
                            type="button"
                            onClick={() => setActiveTab('details')}
                            className="text-slate-500 font-black flex items-center gap-2 hover:text-slate-900 transition-all"
                        >
                            <ChevronLeft className="w-5 h-5" /> Back to Details
                        </button>
                        <button
                            type="button"
                            onClick={() => setActiveTab('settings')}
                            className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 px-8 py-4 rounded-2xl font-black flex items-center gap-2 hover:scale-105 transition-all shadow-xl"
                        >
                            Next: Final Settings <ArrowRight className="w-5 h-5" />
                        </button>
                    </div>
                </div>
            )}

            {activeTab === 'settings' && (
                <div className="space-y-8 animate-in fade-in slide-in-from-bottom-2">
                    <h2 className="text-3xl font-black">Final Configuration</h2>
                    <div className="p-8 bg-slate-50 dark:bg-black/20 rounded-[2rem] border border-slate-200 dark:border-white/5">
                        <p className="text-slate-500 font-medium mb-6 text-center italic">Review your details and judging criteria in the sidebar before publishing.</p>
                        <button
                            type="button"
                            onClick={() => setActiveTab('categories')}
                            className="text-slate-500 font-black flex items-center gap-2 hover:text-slate-900 transition-all mx-auto"
                        >
                            <ChevronLeft className="w-5 h-5" /> Back to Judging
                        </button>
                    </div>
                </div>
            )}
        </JamLayout>
    );
};