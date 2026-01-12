import React from 'react';
import { Clock, Shield, Check } from 'lucide-react';
import type { Mod } from '../../types';

interface VerificationQueueProps {
    pendingProjects: Mod[];
    loadingQueue: boolean;
    loadingReview: boolean;
    reviewingId: string | undefined;
    onReview: (id: string) => void;
}

export const VerificationQueue: React.FC<VerificationQueueProps> = ({ pendingProjects, loadingQueue, loadingReview, reviewingId, onReview }) => {
    if (loadingQueue) {
        return <div className="text-center py-24 text-slate-400 font-bold animate-pulse">Loading queue...</div>;
    }

    if (pendingProjects.length === 0) {
        return (
            <div className="text-center py-32 bg-white dark:bg-slate-900 rounded-[2rem] border border-slate-200 dark:border-white/5 shadow-sm">
                <div className="w-20 h-20 bg-emerald-500/10 rounded-full flex items-center justify-center mx-auto mb-6">
                    <Check className="w-10 h-10 text-emerald-500" />
                </div>
                <h3 className="text-2xl font-black text-slate-900 dark:text-white mb-2">All Caught Up!</h3>
                <p className="text-slate-500 font-medium">No projects pending verification.</p>
            </div>
        );
    }

    return (
        <div className="grid gap-4">
            {pendingProjects.map(p => (
                <div key={p.id} className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-3xl p-6 flex flex-col md:flex-row gap-8 hover:shadow-xl transition-all duration-300 group hover:border-modtale-accent/20">
                    <div className="w-full md:w-32 h-32 rounded-2xl overflow-hidden bg-slate-100 dark:bg-white/5 relative shrink-0 shadow-inner">
                        <img src={p.imageUrl} className="w-full h-full object-cover" alt="" onError={(e) => e.currentTarget.src = '/assets/favicon.svg'} />
                    </div>
                    <div className="flex-1 min-w-0 py-1 flex flex-col justify-center">
                        <div className="flex items-start justify-between mb-3">
                            <div>
                                <h3 className="text-2xl font-black text-slate-900 dark:text-white flex items-center gap-3 truncate tracking-tight">
                                    {p.title}
                                    <span className="text-[10px] uppercase font-bold px-2.5 py-1 bg-modtale-accent/10 text-modtale-accent rounded-lg tracking-wider">{p.classification}</span>
                                </h3>
                                <p className="text-sm text-slate-500 font-bold mb-1">by <span className="text-slate-700 dark:text-slate-300">{p.author}</span></p>
                            </div>
                            <div className="flex items-center gap-2 text-xs font-bold text-slate-400 bg-slate-100 dark:bg-white/5 px-3 py-1.5 rounded-lg uppercase tracking-wider">
                                <Clock className="w-3 h-3" />
                                {p.updatedAt}
                            </div>
                        </div>
                        <p className="text-slate-600 dark:text-slate-400 text-sm mb-6 line-clamp-2 leading-relaxed font-medium">{p.description}</p>

                        <div className="flex items-center gap-3">
                            <button
                                onClick={() => onReview(p.id)}
                                className="px-6 py-3 bg-slate-900 dark:bg-white text-white dark:text-black rounded-xl font-black text-sm flex items-center gap-2 hover:bg-slate-800 dark:hover:bg-slate-200 transition-all shadow-lg shadow-black/10 dark:shadow-white/5 hover:scale-105 active:scale-95"
                            >
                                {loadingReview && reviewingId === p.id ? 'Loading...' : <><Shield className="w-4 h-4" /> Verify Project</>}
                            </button>
                        </div>
                    </div>
                </div>
            ))}
        </div>
    );
};