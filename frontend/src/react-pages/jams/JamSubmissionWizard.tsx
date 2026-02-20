import React, { useState } from 'react';
import { Check, Rocket, ArrowRight, Import, LayoutGrid, Sparkles } from 'lucide-react';
import type { Mod, Modjam } from '@/types';
import { api } from '@/utils/api';
import { Spinner } from '@/components/ui/Spinner';

export const JamSubmissionWizard: React.FC<{
    jam: Modjam,
    myProjects: Mod[],
    onSuccess: (sub: any) => void,
    onCancel: () => void
}> = ({ jam, myProjects, onSuccess, onCancel }) => {
    const [selectedProjectId, setSelectedProjectId] = useState<string>('');
    const [submitting, setSubmitting] = useState(false);

    const handleConfirm = async () => {
        if (!selectedProjectId) return;
        setSubmitting(true);
        try {
            const res = await api.post(`/modjams/${jam.id}/submit`, { projectId: selectedProjectId });
            onSuccess(res.data);
        } catch (e) {
            setSubmitting(false);
        }
    };

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in duration-300">
            <div className="bg-white dark:bg-modtale-card w-full max-w-2xl rounded-[2.5rem] shadow-2xl border border-slate-200 dark:border-white/10 overflow-hidden animate-in zoom-in-95">
                <div className="p-10 text-center">
                    <div className="w-20 h-20 rounded-[2rem] bg-modtale-accent/10 text-modtale-accent flex items-center justify-center mx-auto mb-6">
                        <Rocket className="w-10 h-10" />
                    </div>
                    <h2 className="text-3xl font-black mb-2">Submit to {jam.title}</h2>
                    <p className="text-slate-500 font-medium mb-8 px-8">Select one of your existing projects to enter into the jam. All your project's details, screenshots, and files will be automatically linked.</p>

                    <div className="grid gap-3 max-h-[300px] overflow-y-auto p-2 custom-scrollbar">
                        {myProjects.length > 0 ? myProjects.map(proj => (
                            <button
                                key={proj.id}
                                onClick={() => setSelectedProjectId(proj.id)}
                                className={`flex items-center gap-4 p-5 rounded-2xl border-2 transition-all text-left ${selectedProjectId === proj.id ? 'border-modtale-accent bg-modtale-accent/5 ring-4 ring-modtale-accent/10' : 'border-slate-100 dark:border-white/5 bg-slate-50 dark:bg-black/20 hover:border-slate-300'}`}
                            >
                                <div className="w-12 h-12 rounded-xl bg-slate-200 dark:bg-white/10 overflow-hidden shrink-0">
                                    {proj.imageUrl && <img src={proj.imageUrl} className="w-full h-full object-cover" alt="" />}
                                </div>
                                <div className="flex-1 min-w-0">
                                    <div className="font-black text-slate-900 dark:text-white truncate">{proj.title}</div>
                                    <div className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">{proj.classification}</div>
                                </div>
                                {selectedProjectId === proj.id && <Check className="w-5 h-5 text-modtale-accent" />}
                            </button>
                        )) : (
                            <div className="py-10 border-2 border-dashed border-slate-200 dark:border-white/5 rounded-3xl">
                                <LayoutGrid className="w-12 h-12 mx-auto text-slate-300 mb-2" />
                                <p className="text-sm font-bold text-slate-500">You don't have any projects yet.</p>
                            </div>
                        )}
                    </div>

                    <div className="flex items-center gap-4 mt-10">
                        <button onClick={onCancel} className="flex-1 h-14 rounded-2xl font-bold text-slate-500 hover:bg-slate-100 dark:hover:bg-white/5 transition-colors">Go Back</button>
                        <button
                            disabled={!selectedProjectId || submitting}
                            onClick={handleConfirm}
                            className="flex-[2] h-16 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-2xl font-black text-lg shadow-lg shadow-modtale-accent/20 transition-all flex items-center justify-center gap-2 disabled:opacity-50"
                        >
                            {submitting ? <Spinner className="w-5 h-5" /> : <>Finalize Submission <Sparkles className="w-5 h-5" /></>}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};