import React, { useState } from 'react';
import { Flag, ExternalLink, Check, X, ShieldAlert } from 'lucide-react';
import { api } from '../../utils/api';
import type { Report } from '../../types';

interface ReportQueueProps {
    reports: Report[];
    onRefresh: () => void;
}

export const ReportQueue: React.FC<ReportQueueProps> = ({ reports, onRefresh }) => {
    const [processing, setProcessing] = useState<string | null>(null);

    const handleResolve = async (id: string, action: 'RESOLVED' | 'DISMISSED') => {
        setProcessing(id);
        try {
            await api.post(`/admin/reports/${id}/resolve`, {
                status: action,
                note: action === 'RESOLVED' ? 'Action taken' : 'Dismissed by admin'
            });
            onRefresh();
        } catch (e) {
            console.error(e);
        } finally {
            setProcessing(null);
        }
    };

    if (reports.length === 0) {
        return (
            <div className="text-center py-32 bg-white dark:bg-slate-900 rounded-[2rem] border border-slate-200 dark:border-white/5 shadow-sm">
                <div className="w-20 h-20 bg-green-500/10 rounded-full flex items-center justify-center mx-auto mb-6">
                    <ShieldAlert className="w-10 h-10 text-green-500" />
                </div>
                <h3 className="text-2xl font-black text-slate-900 dark:text-white mb-2">No Reports</h3>
                <p className="text-slate-500 font-medium">Everything looks good!</p>
            </div>
        );
    }

    return (
        <div className="grid gap-4">
            {reports.map(report => (
                <div key={report.id} className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-3xl p-6 flex flex-col md:flex-row gap-6 relative overflow-hidden group">
                    <div className="absolute top-0 left-0 bottom-0 w-1 bg-red-500"></div>

                    <div className="flex-1">
                        <div className="flex items-center gap-3 mb-2">
                            <span className="px-2 py-0.5 rounded bg-red-500/10 text-red-500 text-[10px] font-black uppercase tracking-wider border border-red-500/20">
                                {report.reason.replace('_', ' ')}
                            </span>
                            <span className="text-xs text-slate-400 font-medium">{new Date(report.createdAt).toLocaleString()}</span>
                        </div>

                        <h3 className="text-lg font-bold text-slate-900 dark:text-white mb-1">
                            {report.projectTitle}
                        </h3>
                        <div className="text-xs font-mono text-slate-400 mb-4">{report.projectId}</div>

                        <p className="text-slate-600 dark:text-slate-300 bg-slate-50 dark:bg-white/5 p-4 rounded-xl text-sm italic mb-4 border border-slate-200 dark:border-white/5">
                            "{report.description}"
                        </p>

                        <div className="flex items-center gap-2 text-xs font-bold text-slate-500">
                            <span>Reported by: <span className="text-slate-700 dark:text-slate-300">{report.reporterUsername}</span></span>
                        </div>
                    </div>

                    <div className="flex flex-col gap-2 justify-center min-w-[180px]">
                        <a
                            href={`/mod/${report.projectId}`}
                            target="_blank"
                            rel="noreferrer"
                            className="w-full py-2 bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-300 rounded-lg text-xs font-bold flex items-center justify-center gap-2 hover:bg-slate-200 dark:hover:bg-white/10 transition-colors"
                        >
                            <ExternalLink className="w-3 h-3" /> View Project
                        </a>
                        <button
                            onClick={() => handleResolve(report.id, 'RESOLVED')}
                            disabled={!!processing}
                            className="w-full py-2 bg-red-500 text-white rounded-lg text-xs font-bold flex items-center justify-center gap-2 hover:bg-red-600 transition-colors shadow-lg shadow-red-500/20"
                        >
                            <Check className="w-3 h-3" /> Mark Resolved
                        </button>
                        <button
                            onClick={() => handleResolve(report.id, 'DISMISSED')}
                            disabled={!!processing}
                            className="w-full py-2 border border-slate-200 dark:border-white/10 text-slate-500 hover:text-slate-900 dark:hover:text-white rounded-lg text-xs font-bold flex items-center justify-center gap-2 hover:bg-slate-50 dark:hover:bg-white/5 transition-colors"
                        >
                            <X className="w-3 h-3" /> Dismiss
                        </button>
                    </div>
                </div>
            ))}
        </div>
    );
};