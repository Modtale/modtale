import React, { useState } from 'react';
import { ChevronLeft, ImageIcon, Plus, Trophy, Save, AlertCircle, CheckCircle2 } from 'lucide-react';
import { Spinner } from '@/components/ui/Spinner';

interface JamLayoutProps {
    bannerUrl?: string;
    isSaving: boolean;
    hasUnsavedChanges: boolean;
    onSave: () => void;
    onBack: () => void;
    publishChecklist: { label: string; met: boolean }[];
    children: React.ReactNode;
    sidebar: React.ReactNode;
}

export const JamLayout: React.FC<JamLayoutProps> = ({
                                                        bannerUrl, isSaving, hasUnsavedChanges, onSave, onBack, publishChecklist, children, sidebar
                                                    }) => {
    const isReadyToPublish = publishChecklist.every(c => c.met);
    const metCount = publishChecklist.filter(c => c.met).length;

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 flex flex-col pb-24">
            <div className="relative h-80 bg-slate-900 overflow-hidden">
                {bannerUrl ? (
                    <img src={bannerUrl} className="w-full h-full object-cover opacity-60" alt="" />
                ) : (
                    <div className="w-full h-full bg-gradient-to-br from-indigo-900 to-slate-900 opacity-50" />
                )}
                <div className="absolute inset-0 bg-gradient-to-t from-slate-50 dark:from-slate-950 to-transparent" />

                <div className="absolute top-8 left-0 right-0 max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28">
                    <button onClick={onBack} className="flex items-center gap-2 text-white/80 font-bold bg-black/20 hover:bg-black/40 backdrop-blur-md px-4 py-2 rounded-xl transition-all group">
                        <ChevronLeft className="w-4 h-4 group-hover:-translate-x-1 transition-transform" />
                        Back to Jams
                    </button>
                </div>
            </div>

            <div className="max-w-[112rem] w-full mx-auto px-4 sm:px-12 md:px-16 lg:px-28 -mt-32 relative z-10 flex-1">
                <div className="flex flex-col lg:grid lg:grid-cols-12 gap-8">
                    <div className="lg:col-span-8 space-y-6">
                        <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-[2.5rem] shadow-2xl overflow-hidden p-8 md:p-12">
                            {children}
                        </div>
                    </div>

                    <div className="lg:col-span-4 space-y-6">
                        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-[2rem] p-6 shadow-xl sticky top-28">
                            <div className="mb-6">
                                <h3 className="text-xs font-black uppercase text-slate-500 tracking-widest mb-4">Event Status</h3>
                                <div className="space-y-3">
                                    {publishChecklist.map((req, i) => (
                                        <div key={i} className="flex items-center gap-3">
                                            <div className={`w-5 h-5 rounded-full flex items-center justify-center shrink-0 ${req.met ? 'bg-green-500/20 text-green-500' : 'bg-slate-100 dark:bg-white/5 text-slate-400'}`}>
                                                {req.met ? <CheckCircle2 className="w-3.5 h-3.5" /> : <AlertCircle className="w-3.5 h-3.5" />}
                                            </div>
                                            <span className={`text-xs font-bold ${req.met ? 'text-slate-900 dark:text-slate-200' : 'text-slate-500'}`}>{req.label}</span>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            <div className="pt-6 border-t border-slate-100 dark:border-white/5">
                                <button
                                    onClick={onSave}
                                    disabled={isSaving}
                                    className={`w-full h-14 rounded-2xl font-black text-lg transition-all flex items-center justify-center gap-3 shadow-lg active:scale-95 ${
                                        isSaving ? 'bg-slate-100 dark:bg-slate-800' :
                                            isReadyToPublish ? 'bg-modtale-accent hover:bg-modtale-accentHover text-white shadow-modtale-accent/20' :
                                                'bg-slate-900 dark:bg-white text-white dark:text-slate-900'
                                    }`}
                                >
                                    {isSaving ? <Spinner className="w-5 h-5" fullScreen={false} /> : (
                                        <>
                                            <Save className="w-5 h-5" />
                                            {isReadyToPublish ? 'Publish Jam' : 'Save Changes'}
                                        </>
                                    )}
                                </button>
                                {hasUnsavedChanges && !isSaving && (
                                    <p className="text-center text-[10px] font-black text-amber-500 uppercase tracking-widest mt-3 animate-pulse">Unsaved Changes</p>
                                )}
                            </div>
                        </div>
                        {sidebar}
                    </div>
                </div>
            </div>
        </div>
    );
};