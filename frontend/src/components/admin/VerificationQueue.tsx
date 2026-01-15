import React from 'react';
import type { Mod } from '../../types';
import { CheckCircle, Clock, Shield, AlertCircle } from 'lucide-react';

interface VerificationQueueProps {
    pendingProjects: Mod[];
    loadingQueue: boolean;
    loadingReview: boolean;
    reviewingId?: string;
    onReview: (id: string) => void;
}

export const VerificationQueue: React.FC<VerificationQueueProps> = ({
                                                                        pendingProjects, loadingQueue, loadingReview, reviewingId, onReview
                                                                    }) => {
    if (loadingQueue) {
        return <div className="text-center py-24 text-slate-400 font-bold animate-pulse">Loading queue...</div>;
    }

    if (!pendingProjects || pendingProjects.length === 0) {
        return (
            <div className="text-center py-32 bg-white dark:bg-slate-900 rounded-[2rem] border border-slate-200 dark:border-white/5 shadow-sm">
                <div className="w-20 h-20 bg-emerald-500/10 rounded-full flex items-center justify-center mx-auto mb-6">
                    <CheckCircle className="w-10 h-10 text-emerald-500" />
                </div>
                <h3 className="text-2xl font-black text-slate-900 dark:text-white mb-2">All Caught Up!</h3>
                <p className="text-slate-500 font-medium">No projects or versions pending verification.</p>
            </div>
        );
    }

    // Filter to find the actual pending item (Project or Version)
    const renderQueueItem = (mod: Mod) => {
        const isProjectPending = mod.status === 'PENDING';

        // Find versions that are pending
        const pendingVersions = mod.versions.filter(v => v.reviewStatus === 'PENDING');
        // If project is pending, we usually look at the latest version anyway.
        // If project is published, we specifically look at the pending updates.

        const targetVersion = pendingVersions[0] || mod.versions[0];
        const scan = targetVersion?.scanResult;
        const hasIssues = scan && scan.status !== 'CLEAN';

        return (
            <div key={mod.id} className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-3xl p-6 flex flex-col md:flex-row gap-8 hover:shadow-xl transition-all duration-300 group hover:border-modtale-accent/20">
                <div className="w-full md:w-32 h-32 rounded-2xl overflow-hidden bg-slate-100 dark:bg-white/5 relative shrink-0 shadow-inner">
                    <img src={mod.imageUrl} className="w-full h-full object-cover" alt="" onError={(e) => e.currentTarget.src = '/assets/favicon.svg'} />
                    {isProjectPending && (
                        <div className="absolute top-0 left-0 right-0 bg-orange-500 text-white text-[10px] font-bold text-center py-1 uppercase">New Project</div>
                    )}
                    {!isProjectPending && pendingVersions.length > 0 && (
                        <div className="absolute top-0 left-0 right-0 bg-blue-500 text-white text-[10px] font-bold text-center py-1 uppercase">Update</div>
                    )}
                </div>
                <div className="flex-1 min-w-0 py-1 flex flex-col justify-center">
                    <div className="flex items-start justify-between mb-3">
                        <div>
                            <h3 className="text-2xl font-black text-slate-900 dark:text-white flex items-center gap-3 truncate tracking-tight">
                                {mod.title}
                                <span className="text-[10px] uppercase font-bold px-2.5 py-1 bg-modtale-accent/10 text-modtale-accent rounded-lg tracking-wider">{mod.classification}</span>
                            </h3>
                            <p className="text-sm text-slate-500 font-bold mb-1">by <span className="text-slate-700 dark:text-slate-300">{mod.author}</span></p>
                        </div>
                        <div className="flex items-center gap-2 text-xs font-bold text-slate-400 bg-slate-100 dark:bg-white/5 px-3 py-1.5 rounded-lg uppercase tracking-wider">
                            <Clock className="w-3 h-3" />
                            {mod.updatedAt}
                        </div>
                    </div>

                    {isProjectPending ? (
                        <p className="text-slate-600 dark:text-slate-400 text-sm mb-6 line-clamp-2 leading-relaxed font-medium">{mod.description}</p>
                    ) : (
                        <div className="mb-6">
                            <p className="text-slate-600 dark:text-slate-400 text-sm font-medium mb-1">
                                Pending Version: <span className="text-slate-900 dark:text-white font-bold">{targetVersion?.versionNumber}</span>
                            </p>
                            <p className="text-slate-500 text-xs italic line-clamp-1">{targetVersion?.changelog || 'No changelog provided'}</p>
                        </div>
                    )}

                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-4">
                            <button
                                onClick={() => onReview(mod.id)}
                                className="px-6 py-3 bg-slate-900 dark:bg-white text-white dark:text-black rounded-xl font-black text-sm flex items-center gap-2 hover:bg-slate-800 dark:hover:bg-slate-200 transition-all shadow-lg shadow-black/10 dark:shadow-white/5 hover:scale-105 active:scale-95"
                            >
                                {loadingReview && reviewingId === mod.id ? 'Loading...' : <><Shield className="w-4 h-4" /> Verify {isProjectPending ? 'Project' : 'Update'}</>}
                            </button>
                        </div>
                        {hasIssues && (
                            <div className="flex items-center gap-2 text-red-500 bg-red-500/10 px-3 py-1.5 rounded-lg">
                                <AlertCircle className="w-4 h-4" />
                                <span className="text-xs font-bold uppercase tracking-wide">Scanner Flags</span>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        );
    };

    return (
        <div className="grid gap-4">
            {pendingProjects.map(renderQueueItem)}
        </div>
    );
};