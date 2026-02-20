import React, { useState, useEffect } from 'react';
import {
    Trophy, Calendar, Users, Scale, Save, Trash2,
    Plus, Clock, Info, Check, Eye, ExternalLink, Settings, FileText
} from 'lucide-react';
import { ProjectLayout, SidebarSection } from '@/components/resources/ProjectLayout';
import { Spinner } from '@/components/ui/Spinner';
import type { Modjam, User } from '@/types';

interface JamBuilderProps {
    jamData: Modjam | null;
    metaData: any;
    setMetaData: React.Dispatch<React.SetStateAction<any>>;
    handleSave: (silent?: boolean) => Promise<boolean>;
    isLoading: boolean;
    currentUser: User | null;
    activeTab: 'details' | 'categories' | 'settings';
    setActiveTab: (tab: 'details' | 'categories' | 'settings') => void;
}

export const JamBuilder: React.FC<JamBuilderProps> = ({
                                                          jamData, metaData, setMetaData, handleSave, isLoading, activeTab, setActiveTab
                                                      }) => {
    const [isDirty, setIsDirty] = useState(false);

    const markDirty = () => setIsDirty(true);

    const handleAddCategory = () => {
        markDirty();
        const newCats = [...(metaData.categories || []), { name: '', description: '', maxScore: 5 }];
        setMetaData({ ...metaData, categories: newCats });
    };

    const updateCategory = (index: number, field: string, value: any) => {
        markDirty();
        const newCats = [...metaData.categories];
        newCats[index] = { ...newCats[index], [field]: value };
        setMetaData({ ...metaData, categories: newCats });
    };

    return (
        <ProjectLayout
            isEditing={true}
            bannerUrl={metaData.bannerUrl}
            iconUrl={metaData.bannerUrl || "https://modtale.net/assets/favicon.svg"}
            onBannerUpload={(f, p) => { markDirty(); setMetaData({...metaData, bannerUrl: p}); }}
            headerContent={
                <div className="space-y-2">
                    <input
                        value={metaData.title}
                        onChange={e => { markDirty(); setMetaData({...metaData, title: e.target.value}); }}
                        className="text-4xl md:text-5xl font-black text-slate-900 dark:text-white bg-transparent border-b border-transparent outline-none w-full hover:border-slate-300 focus:border-modtale-accent"
                        placeholder="Jam Title"
                    />
                    <textarea
                        value={metaData.description}
                        onChange={e => { markDirty(); setMetaData({...metaData, description: e.target.value}); }}
                        className="text-lg text-slate-600 dark:text-slate-300 font-medium bg-transparent border-none outline-none w-full mt-2 resize-none"
                        placeholder="What is this jam about?"
                    />
                </div>
            }
            headerActions={
                <div className="flex items-center gap-3">
                    {isDirty && <span className="text-[10px] font-black text-amber-500 uppercase tracking-widest animate-pulse">Unsaved</span>}
                    <button
                        onClick={() => { handleSave(false); setIsDirty(false); }}
                        className="h-12 px-6 bg-modtale-accent text-white rounded-xl font-black shadow-lg flex items-center gap-2 hover:scale-105 transition-all"
                    >
                        {isLoading ? <Spinner className="w-4 h-4" /> : <Save className="w-4 h-4" />} Save Jam
                    </button>
                </div>
            }
            tabs={
                <div className="flex items-center gap-1">
                    {[{id: 'details', icon: FileText, label: 'Details'}, {id: 'categories', icon: Scale, label: 'Judging'}, {id: 'settings', icon: Settings, label: 'Settings'}].map(t => (
                        <button key={t.id} onClick={() => setActiveTab(t.id as any)} className={`px-6 py-3 text-sm font-bold border-b-2 transition-colors flex items-center gap-2 ${activeTab === t.id ? 'border-modtale-accent text-slate-900 dark:text-white' : 'border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-white'}`}>
                            <t.icon className="w-4 h-4"/> {t.label}
                        </button>
                    ))}
                </div>
            }
            mainContent={
                <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4">
                    {activeTab === 'details' && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                            <div className="space-y-4">
                                <label className="block text-xs font-black uppercase text-slate-500 tracking-widest">Jam Schedule</label>
                                <div className="space-y-4 p-6 bg-slate-50 dark:bg-black/20 rounded-3xl border border-slate-200 dark:border-white/5">
                                    <div className="space-y-1">
                                        <span className="text-xs font-bold text-slate-400">Start Time</span>
                                        <input type="datetime-local" value={metaData.startDate} onChange={e => { markDirty(); setMetaData({...metaData, startDate: e.target.value}); }} className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2 text-sm font-bold" />
                                    </div>
                                    <div className="space-y-1">
                                        <span className="text-xs font-bold text-slate-400">End Time (Submissions Close)</span>
                                        <input type="datetime-local" value={metaData.endDate} onChange={e => { markDirty(); setMetaData({...metaData, endDate: e.target.value}); }} className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2 text-sm font-bold" />
                                    </div>
                                    <div className="space-y-1">
                                        <span className="text-xs font-bold text-slate-400">Voting End Time</span>
                                        <input type="datetime-local" value={metaData.votingEndDate} onChange={e => { markDirty(); setMetaData({...metaData, votingEndDate: e.target.value}); }} className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2 text-sm font-bold" />
                                    </div>
                                </div>
                            </div>
                            <div className="bg-modtale-accent/5 p-8 rounded-3xl border border-modtale-accent/20 flex flex-col items-center justify-center text-center">
                                <Trophy className="w-12 h-12 text-modtale-accent mb-4" />
                                <h3 className="font-black text-xl mb-2">Live Preview</h3>
                                <p className="text-sm text-slate-500 font-medium">As you edit dates and descriptions, your jam page updates in real-time.</p>
                            </div>
                        </div>
                    )}

                    {activeTab === 'categories' && (
                        <div className="space-y-6">
                            <div className="flex items-center justify-between">
                                <h3 className="text-xl font-black">Scoring Categories</h3>
                                <button onClick={handleAddCategory} className="px-4 py-2 bg-modtale-accent text-white rounded-xl text-sm font-bold flex items-center gap-2"><Plus className="w-4 h-4"/> Add Category</button>
                            </div>
                            <div className="grid gap-4">
                                {metaData.categories.map((cat: any, i: number) => (
                                    <div key={i} className="flex items-start gap-4 p-6 bg-slate-50 dark:bg-black/20 rounded-3xl border border-slate-200 dark:border-white/5">
                                        <div className="flex-1 space-y-4">
                                            <input value={cat.name} onChange={e => updateCategory(i, 'name', e.target.value)} className="w-full bg-white dark:bg-slate-900 border-none rounded-xl px-4 py-2 font-bold" placeholder="Category Name" />
                                            <input value={cat.description} onChange={e => updateCategory(i, 'description', e.target.value)} className="w-full bg-white dark:bg-slate-900 border-none rounded-xl px-4 py-2 text-sm" placeholder="Short description..." />
                                        </div>
                                        <div className="w-24">
                                            <span className="text-[10px] font-black uppercase text-slate-500 mb-1 block">Max Score</span>
                                            <input type="number" value={cat.maxScore} onChange={e => updateCategory(i, 'maxScore', e.target.value)} className="w-full bg-white dark:bg-slate-900 border-none rounded-xl px-4 py-2 font-bold text-center" />
                                        </div>
                                        <button onClick={() => { markDirty(); setMetaData({...metaData, categories: metaData.categories.filter((_:any,idx:number)=>idx!==i)}); }} className="p-2 text-slate-400 hover:text-red-500 mt-6"><Trash2 className="w-5 h-5"/></button>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            }
            sidebarContent={
                <div className="space-y-8">
                    <SidebarSection title="Quick Stats" icon={Info}>
                        <div className="p-4 bg-slate-50 dark:bg-black/20 rounded-2xl space-y-3">
                            <div className="flex justify-between text-xs font-bold">
                                <span className="text-slate-500">Categories</span>
                                <span>{metaData.categories.length}</span>
                            </div>
                            <div className="flex justify-between text-xs font-bold">
                                <span className="text-slate-500">Public Voting</span>
                                <span className={metaData.allowPublicVoting ? 'text-green-500' : 'text-slate-400'}>{metaData.allowPublicVoting ? 'Enabled' : 'Host Only'}</span>
                            </div>
                        </div>
                    </SidebarSection>
                </div>
            }
        />
    );
};