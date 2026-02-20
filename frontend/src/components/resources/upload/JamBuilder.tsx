import React, { useState, useEffect } from 'react';
import { Trophy, Settings, Plus, Trash2, Info, LayoutGrid, List, Sparkles } from 'lucide-react';
import { JamLayout } from '@/components/jams/JamLayout';
import { JamDateInput } from '@/components/jams/JamCalendar';
import { SidebarSection } from '@/components/resources/ProjectLayout';
import { api } from '@/utils/api';
import type { Modjam, User } from '@/types';

export const JamBuilder: React.FC<any> = ({
                                              metaData, setMetaData, handleSave, isLoading, activeTab, setActiveTab, onBack, onPublish
                                          }) => {
    const [isDirty, setIsDirty] = useState(false);
    const [isSaved, setIsSaved] = useState(false);

    const publishChecklist = [
        { label: 'Event Title (min 5 chars)', met: metaData.title?.length >= 5 },
        { label: 'Description (min 20 chars)', met: metaData.description?.length >= 20 },
        { label: 'Start Date (Future Only)', met: !!metaData.startDate && new Date(metaData.startDate) > new Date() },
        { label: 'Submissions Close Date', met: !!metaData.endDate && metaData.endDate > metaData.startDate },
        { label: 'Voting Period configured', met: !!metaData.votingEndDate && metaData.votingEndDate > metaData.endDate },
        { label: 'At least one category', met: metaData.categories?.length > 0 }
    ];

    const markDirty = () => { setIsDirty(true); setIsSaved(false); };

    const performSave = async () => {
        const success = await handleSave(true);
        if (success) {
            setIsDirty(false);
            setIsSaved(true);
            setTimeout(() => setIsSaved(false), 3000);
        }
    };

    const updateField = (field: string, val: any) => { markDirty(); setMetaData({ ...metaData, [field]: val }); };

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
            sidebar={
                <SidebarSection title="Configuration" icon={Settings}>
                    <div className="space-y-4 pt-2">
                        <label className="flex items-center justify-between p-4 bg-slate-50 dark:bg-black/20 rounded-2xl cursor-pointer hover:bg-slate-100 transition-colors">
                            <div className="flex flex-col">
                                <span className="text-xs font-bold">Public Voting</span>
                                <span className="text-[10px] text-slate-500 font-medium">Allow anyone to score</span>
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
                            placeholder="A Title to Remember..."
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
                                label="Voting Concludes"
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
                                <List className="w-3 h-3" /> Event Briefing
                            </h3>
                            <span className="text-[10px] font-bold text-slate-400">{metaData.description?.length || 0} characters</span>
                        </div>
                        <textarea
                            value={metaData.description}
                            onChange={e => updateField('description', e.target.value)}
                            placeholder="Rules, theme, goals, and glory..."
                            className="w-full min-h-[400px] bg-transparent border-none outline-none text-lg text-slate-700 dark:text-slate-300 font-medium resize-none leading-relaxed"
                        />
                    </div>
                </div>
            )}

            {activeTab === 'categories' && (
                <div className="space-y-8 animate-in fade-in slide-in-from-bottom-2">
                    <div className="flex items-center justify-between">
                        <div>
                            <h2 className="text-3xl font-black">Scoring</h2>
                            <p className="text-slate-500 font-medium mt-1">Submission criteria for judges and voters.</p>
                        </div>
                        <button onClick={() => updateField('categories', [...metaData.categories, {name: '', description: '', maxScore: 5}])} className="h-12 px-6 bg-slate-100 dark:bg-white/5 rounded-2xl text-sm font-black flex items-center gap-2 hover:bg-slate-200 transition-all">
                            <Plus className="w-4 h-4" /> Add Criterion
                        </button>
                    </div>

                    <div className="grid gap-4">
                        {metaData.categories.map((cat: any, i: number) => (
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
                                        placeholder="Name (e.g. Creativity)"
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
        </JamLayout>
    );
};