import React from 'react';
import { ChevronLeft, Save, AlertCircle, CheckCircle2, Rocket } from 'lucide-react';
import { Spinner } from '@/components/ui/Spinner';

interface JamLayoutProps {
    bannerUrl?: string;
    isSaving: boolean;
    isSaved: boolean;
    hasUnsavedChanges: boolean;
    onSave: () => void;
    onPublish: () => void;
    onBack: () => void;
    publishChecklist: { label: string; met: boolean }[];
    children: React.ReactNode;
    tabs: React.ReactNode;
}

export const JamLayout: React.FC<JamLayoutProps> = ({
                                                        bannerUrl, isSaving, isSaved, hasUnsavedChanges, onSave, onPublish, onBack, publishChecklist, children, tabs
                                                    }) => {
    const isReadyToPublish = publishChecklist.every(c => c.met) && !hasUnsavedChanges;

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 flex flex-col pb-40">
            <div className="relative h-64 bg-slate-900 overflow-hidden">
                {bannerUrl ? (
                    <img src={bannerUrl} className="w-full h-full object-cover opacity-40" alt="" />
                ) : (
                    <div className="w-full h-full bg-gradient-to-br from-modtale-accent/20 to-slate-900" />
                )}
                <div className="absolute inset-0 bg-gradient-to-t from-slate-50 dark:from-slate-950 to-transparent" />

                <div className="absolute top-8 left-0 right-0 max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28">
                    <button type="button" onClick={onBack} className="flex items-center gap-2 text-white/80 font-bold bg-black/40 hover:bg-black/60 backdrop-blur-md px-4 py-2 rounded-xl transition-all group">
                        <ChevronLeft className="w-4 h-4 group-hover:-translate-x-1 transition-transform" />
                        Back
                    </button>
                </div>
            </div>

            <div className="max-w-[112rem] w-full mx-auto px-4 sm:px-12 md:px-16 lg:px-28 -mt-24 relative z-10 flex-1">
                <div className="flex flex-col lg:grid lg:grid-cols-12 gap-8 items-start">
                    <div className="lg:col-span-8 w-full space-y-6">
                        <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-[2.5rem] shadow-2xl flex flex-col overflow-hidden">
                            <div className="px-8 md:px-12 pt-8 md:pt-10 border-b border-slate-100 dark:border-white/5">
                                {tabs}
                            </div>
                            <div className="p-8 md:p-12 min-h-[500px]">
                                {children}
                            </div>
                        </div>
                    </div>

                    <div className="lg:col-span-4 w-full sticky top-28 space-y-6">
                        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-[2rem] p-8 shadow-xl">
                            <h3 className="text-xs font-black uppercase text-slate-500 tracking-widest mb-6">Launch Checklist</h3>
                            <div className="space-y-4 mb-8">
                                {publishChecklist.map((req, i) => (
                                    <div key={i} className="flex items-start gap-3">
                                        <div className={`mt-0.5 shrink-0 ${req.met ? 'text-green-500' : 'text-slate-300 dark:text-slate-700'}`}>
                                            {req.met ? <CheckCircle2 className="w-4 h-4" /> : <AlertCircle className="w-4 h-4" />}
                                        </div>
                                        <span className={`text-sm font-bold ${req.met ? 'text-slate-900 dark:text-slate-200' : 'text-slate-400'}`}>{req.label}</span>
                                    </div>
                                ))}
                                <div className="flex items-start gap-3 pt-2 border-t border-slate-100 dark:border-white/5 mt-2">
                                    <div className={`mt-0.5 shrink-0 ${!hasUnsavedChanges ? 'text-green-500' : 'text-amber-500'}`}>
                                        {!hasUnsavedChanges ? <CheckCircle2 className="w-4 h-4" /> : <AlertCircle className="w-4 h-4" />}
                                    </div>
                                    <span className={`text-sm font-bold ${!hasUnsavedChanges ? 'text-slate-900 dark:text-slate-200' : 'text-amber-500'}`}>All changes saved</span>
                                </div>
                            </div>

                            <div className="space-y-3">
                                <button
                                    type="button"
                                    onClick={(e) => { e.preventDefault(); onSave(); }}
                                    disabled={isSaving}
                                    className={`w-full h-14 rounded-2xl font-black text-sm transition-all flex items-center justify-center gap-2 border-2 ${
                                        isSaved ? 'bg-green-500/10 border-green-500 text-green-500' :
                                            'bg-slate-900 dark:bg-white text-white dark:text-slate-900 border-transparent hover:scale-[1.02]'
                                    }`}
                                >
                                    {isSaving ? <Spinner className="w-4 h-4" fullScreen={false} /> : isSaved ? <CheckCircle2 className="w-4 h-4" /> : <Save className="w-4 h-4" />}
                                    {isSaved ? 'Changes Saved' : 'Save Draft'}
                                </button>

                                <button
                                    type="button"
                                    onClick={(e) => { e.preventDefault(); onPublish(); }}
                                    disabled={!isReadyToPublish || isSaving}
                                    className="w-full h-14 bg-modtale-accent hover:bg-modtale-accentHover disabled:bg-slate-100 dark:disabled:bg-slate-800 disabled:text-slate-400 text-white rounded-2xl font-black text-sm transition-all flex items-center justify-center gap-2 shadow-lg shadow-modtale-accent/20 enabled:hover:scale-[1.02]"
                                >
                                    <Rocket className="w-4 h-4" />
                                    Publish Jam
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};