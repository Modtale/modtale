import React, { useState } from 'react';
import { LayoutGrid, Trophy, List, Settings, Plus, Trash2, Info, Image as ImageIcon } from 'lucide-react';
import { JamLayout } from '@/components/jams/JamLayout';
import { JamCalendar } from '@/components/jams/JamCalendar';
import { SidebarSection } from '@/components/resources/ProjectLayout';
import type { Modjam, User } from '@/types';

export const JamBuilder: React.FC<any> = ({
                                              metaData, setMetaData, handleSave, isLoading, activeTab, setActiveTab, onBack
                                          }) => {
    const [isDirty, setIsDirty] = useState(false);

    const publishChecklist = [
        { label: 'Event Title', met: metaData.title.length > 5 },
        { label: 'Description', met: metaData.description.length > 20 },
        { label: 'Start Date set', met: !!metaData.startDate },
        { label: 'Timeline follows order', met: (!!metaData.startDate && !!metaData.endDate && metaData.endDate > metaData.startDate) },
        { label: 'Banner Image', met: !!metaData.bannerUrl }
    ];

    const markDirty = () => setIsDirty(true);

    return (
        <JamLayout
            isSaving={isLoading}
            hasUnsavedChanges={isDirty}
            onSave={handleSave}
            onBack={onBack}
            bannerUrl={metaData.bannerUrl}
            publishChecklist={publishChecklist}
            sidebar={
                <SidebarSection title="Quick Config" icon={Settings}>
                    <div className="space-y-4 pt-2">
                        <label className="flex items-center justify-between p-3 bg-slate-50 dark:bg-black/20 rounded-xl cursor-pointer">
                            <span className="text-xs font-bold">Public Voting</span>
                            <input
                                type="checkbox"
                                checked={metaData.allowPublicVoting}
                                onChange={e => { markDirty(); setMetaData({...metaData, allowPublicVoting: e.target.checked}); }}
                                className="w-5 h-5 rounded border-slate-300 dark:border-slate-700 text-modtale-accent focus:ring-modtale-accent"
                            />
                        </label>
                    </div>
                </SidebarSection>
            }
        >
            {activeTab === 'details' && (
                <div className="space-y-10 animate-in fade-in slide-in-from-bottom-2">
                    <div className="space-y-6">
                        <input
                            value={metaData.title}
                            onChange={e => { markDirty(); setMetaData({...metaData, title: e.target.value}); }}
                            placeholder="Enter a bold title..."
                            className="text-5xl font-black bg-transparent border-none outline-none w-full placeholder:text-slate-200 dark:placeholder:text-slate-800"
                        />
                        <JamCalendar
                            startDate={metaData.startDate}
                            endDate={metaData.endDate}
                            votingEndDate={metaData.votingEndDate}
                            onChange={(f, v) => { markDirty(); setMetaData({...metaData, [f]: v}); }}
                        />
                    </div>

                    <div className="space-y-4">
                        <h3 className="text-xs font-black uppercase text-slate-500 tracking-widest flex items-center gap-2">
                            <List className="w-3 h-3" /> Event Summary
                        </h3>
                        <textarea
                            value={metaData.description}
                            onChange={e => { markDirty(); setMetaData({...metaData, description: e.target.value}); }}
                            placeholder="Describe the rules, theme, and prizes..."
                            className="w-full min-h-[300px] bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/5 rounded-3xl p-6 outline-none focus:border-modtale-accent transition-all"
                        />
                    </div>
                </div>
            )}

            {activeTab === 'categories' && (
                <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2">
                    <div className="flex items-center justify-between">
                        <h2 className="text-2xl font-black">Judging</h2>
                        <button onClick={() => { markDirty(); setMetaData({...metaData, categories: [...metaData.categories, {name: '', description: '', maxScore: 5}]}) }} className="px-4 py-2 bg-slate-100 dark:bg-white/5 rounded-xl text-sm font-bold flex items-center gap-2">
                            <Plus className="w-4 h-4" /> Add Criterion
                        </button>
                    </div>
                </div>
            )}
        </JamLayout>
    );
};