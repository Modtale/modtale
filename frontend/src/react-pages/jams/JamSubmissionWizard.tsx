import React, { useState } from 'react';
import { Check, Rocket, ArrowRight, Import, LayoutGrid, Sparkles } from 'lucide-react';
import type { Mod, Modjam } from '@/types';
import { api, BACKEND_URL } from '@/utils/api';
import { Spinner } from '@/components/ui/Spinner';

export const JamSubmissionWizard: React.FC<{
    jam: Modjam,
    myProjects: Mod[],
    onSuccess: (sub: any) => void,
    onCancel: () => void,
    onError: (msg: string) => void
}> = ({ jam, myProjects, onSuccess, onCancel, onError }) => {
    const [selectedProjectId, setSelectedProjectId] = useState<string>('');
    const [submitting, setSubmitting] = useState(false);

    const resolveUrl = (url?: string | null) => {
        if (!url) return '';
        if (url.startsWith('/api') || url.startsWith('/uploads')) {
            return `${BACKEND_URL}${url}`;
        }
        return url;
    };

    const handleConfirm = async () => {
        if (!selectedProjectId) return;
        setSubmitting(true);
        try {
            const res = await api.post(`/modjams/${jam.id}/submit`, { projectId: selectedProjectId });
            onSuccess(res.data);
        } catch (e: any) {
            setSubmitting(false);
            onError(e.response?.data?.message || 'Failed to submit project.');
            onCancel();
        }
    };

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in duration-300">
            <div className="bg-white dark:bg-modtale-card w-full max-w-2xl rounded-[2.5rem] shadow-2xl border border-slate-200 dark:border-white/10 overflow-hidden animate-in zoom-in-95">
                <div className="p-10 text-center flex flex-col max-h-[90vh]">
                    <h2 className="text-3xl font-black mb-2 shrink-0">Submit to {jam.title}</h2>
                    <p className="text-slate-500 font-medium mb-8 px-8 shrink-0">Select one of your existing projects to enter into the jam. All your project's details, screenshots, and files will be automatically linked.</p>

                    <div className="grid gap-3 overflow-y-auto p-2 custom-scrollbar flex-1 min-h-[200px]">
                        {myProjects.length > 0 ? myProjects.map(proj => {
                            const resolvedImage = resolveUrl(proj.imageUrl);
                            return (
                                <button
                                    key={proj.id}
                                    onClick={() => setSelectedProjectId(proj.id)}
                                    className={`flex items-center gap-4 p-5 rounded-2xl border-2 transition-all text-left ${selectedProjectId === proj.id ? 'border-modtale-accent bg-modtale-accent/5 ring-4 ring-modtale-accent/10' : 'border-slate-100 dark:border-white/5 bg-slate-50 dark:bg-black/20 hover:border-slate-300'}`}
                                >
                                    <div className="w-12 h-12 rounded-xl bg-slate-200 dark:bg-white/10 overflow-hidden shrink-0">
                                        {resolvedImage ? (
                                            <img src={resolvedImage} className="w-full h-full object-cover" alt="" />
                                        ) : (
                                            <div className="w-full h-full flex items-center justify-center text-slate-400">
                                                <LayoutGrid className="w-6 h-6 opacity-20" />
                                            </div>
                                        )}
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <div className="font-black text-slate-900 dark:text-white truncate">{proj.title}</div>
                                        <div className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">{proj.classification}</div>
                                    </div>
                                    {selectedProjectId === proj.id && <Check className="w-5 h-5 text-modtale-accent" />}
                                </button>
                            );
                        }) : (
                            <div className="py-10 border-2 border-dashed border-slate-200 dark:border-white/5 rounded-3xl h-full flex flex-col items-center justify-center">
                                <LayoutGrid className="w-12 h-12 text-slate-300 mb-2 opacity-20" />
                                <p className="text-sm font-bold text-slate-500">You don't have any projects yet.</p>
                            </div>
                        )}
                    </div>

                    <div className="flex items-center gap-4 mt-8 shrink-0 pt-4 border-t border-slate-100 dark:border-white/5">
                        <button onClick={onCancel} className="flex-1 h-14 rounded-2xl font-bold text-slate-500 hover:bg-slate-100 dark:hover:bg-white/5 transition-colors">Go Back</button>
                        <button
                            disabled={!selectedProjectId || submitting}
                            onClick={handleConfirm}
                            className="flex-[2] h-16 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-2xl font-black text-lg shadow-lg shadow-modtale-accent/20 transition-all flex items-center justify-center gap-2 disabled:opacity-50"
                        >
                            {submitting ? <Spinner className="w-5 h-5" fullScreen={false} /> : <>Finalize Submission <Sparkles className="w-5 h-5" /></>}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};