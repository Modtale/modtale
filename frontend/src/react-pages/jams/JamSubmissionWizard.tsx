import React, { useState } from 'react';
import { Check, Rocket, ArrowRight, Import, LayoutGrid, Sparkles, AlertCircle } from 'lucide-react';
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
    const [agreedToRules, setAgreedToRules] = useState(false);

    const hasRules = Boolean((jam as any).rules && (jam as any).rules.trim().length > 0);
    const hidesSubmissions = Boolean((jam as any).hideSubmissions);

    const validProjects = myProjects.filter(p => {
        if (hidesSubmissions) return ['DRAFT', 'PENDING', 'APPROVED_HIDDEN'].includes(p.status);
        return p.status === 'PUBLISHED';
    });

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
            let errorMsg = typeof e.response?.data === 'string'
                ? e.response.data
                : e.response?.data?.message || 'Failed to submit project.';

            errorMsg = errorMsg.replace(/^\d{3} [A-Z_]+ "(.*)"$/, '$1');
            onError(errorMsg);
        }
    };

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in duration-300">
            <div className="bg-white dark:bg-modtale-card w-full max-w-2xl rounded-[2.5rem] shadow-2xl border border-slate-200 dark:border-white/10 overflow-hidden animate-in zoom-in-95 flex flex-col max-h-[90vh]">
                <div className="p-10 text-center flex flex-col h-full overflow-hidden">
                    <h2 className="text-3xl font-black mb-2 shrink-0 text-slate-900 dark:text-white">Submit to {jam.title}</h2>
                    <p className="text-slate-500 font-medium mb-8 px-8 shrink-0">Select one of your existing projects to enter into the jam. All your project's details, screenshots, and files will be automatically linked.</p>

                    {hidesSubmissions && (
                        <div className="bg-amber-500/10 border border-amber-500/20 rounded-xl p-4 mb-6 flex items-start gap-3 text-left shrink-0">
                            <AlertCircle className="w-5 h-5 text-amber-500 shrink-0 mt-0.5" />
                            <div>
                                <h4 className="text-sm font-bold text-amber-700 dark:text-amber-400">Secret Submissions Active</h4>
                                <p className="text-xs text-amber-600/80 dark:text-amber-500/80 mt-1">This jam hides entries until voting starts. You can only submit <strong>Draft</strong> or <strong>Pending</strong> projects. Published projects cannot be entered.</p>
                            </div>
                        </div>
                    )}

                    <div className="grid gap-3 overflow-y-auto p-2 custom-scrollbar flex-1 min-h-[200px]">
                        {validProjects.length > 0 ? validProjects.map(proj => {
                            const resolvedImage = resolveUrl(proj.imageUrl);
                            return (
                                <button
                                    key={proj.id}
                                    onClick={() => setSelectedProjectId(proj.id)}
                                    className={`flex items-center gap-4 p-5 rounded-2xl border-2 transition-all text-left ${selectedProjectId === proj.id ? 'border-modtale-accent bg-modtale-accent/5 ring-4 ring-modtale-accent/10' : 'border-slate-100 dark:border-white/5 bg-slate-50 dark:bg-black/20 hover:border-slate-300 dark:hover:border-white/20'}`}
                                >
                                    <div className="w-12 h-12 rounded-xl bg-slate-200 dark:bg-white/10 overflow-hidden shrink-0 border border-slate-200 dark:border-white/5">
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
                                        <div className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">{proj.classification} • {proj.status}</div>
                                    </div>
                                    {selectedProjectId === proj.id && <Check className="w-5 h-5 text-modtale-accent" />}
                                </button>
                            );
                        }) : (
                            <div className="py-10 border-2 border-dashed border-slate-200 dark:border-white/5 rounded-3xl h-full flex flex-col items-center justify-center">
                                <LayoutGrid className="w-12 h-12 text-slate-300 mb-2 opacity-20" />
                                <p className="text-sm font-bold text-slate-500">
                                    {hidesSubmissions ? "You don't have any eligible draft projects." : "You don't have any published projects yet."}
                                </p>
                            </div>
                        )}
                    </div>

                    <div className="flex flex-col gap-5 mt-6 shrink-0 pt-6 border-t border-slate-100 dark:border-white/5">
                        {hasRules && (
                            <div className="flex items-center justify-center gap-3 cursor-pointer group px-4" onClick={(e) => { e.preventDefault(); setAgreedToRules(!agreedToRules); }}>
                                <div className={`w-6 h-6 rounded-lg border-2 flex items-center justify-center shrink-0 transition-all ${agreedToRules ? 'bg-modtale-accent border-modtale-accent text-white' : 'border-slate-300 dark:border-slate-600 group-hover:border-modtale-accent'}`}>
                                    {agreedToRules && <Check className="w-4 h-4" strokeWidth={3} />}
                                </div>
                                <span className="text-sm font-bold text-slate-600 dark:text-slate-400 text-left select-none">
                                    I have read and agree to the official <a href={`/jam/${jam.slug}/rules`} target="_blank" rel="noopener noreferrer" onClick={e => e.stopPropagation()} className="text-modtale-accent hover:underline">Jam Rules</a>.
                                </span>
                            </div>
                        )}

                        <div className="flex items-center gap-4 w-full">
                            <button onClick={onCancel} className="flex-1 h-14 rounded-2xl font-bold text-slate-500 hover:bg-slate-100 dark:hover:bg-white/5 transition-colors">Go Back</button>
                            <button
                                disabled={!selectedProjectId || (hasRules && !agreedToRules) || submitting}
                                onClick={handleConfirm}
                                className="flex-[2] h-16 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-2xl font-black text-lg shadow-lg shadow-modtale-accent/20 transition-all flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                {submitting ? <Spinner className="w-5 h-5" fullScreen={false} /> : <>Finalize Submission <Sparkles className="w-5 h-5" /></>}
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};