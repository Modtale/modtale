import React, { useState, useEffect } from 'react';
import { Flag, ExternalLink, Check, X, ShieldAlert, MessageSquare, User, Filter } from 'lucide-react';
import { api } from '../../utils/api';
import type { Report } from '../../types';

interface ReportQueueProps {
    reports: Report[];
    onRefresh: () => void;
}

export const ReportQueue: React.FC<ReportQueueProps> = ({ reports: initialReports, onRefresh }) => {
    const [reports, setReports] = useState<Report[]>(initialReports);
    const [processing, setProcessing] = useState<string | null>(null);
    const [responses, setResponses] = useState<Record<string, string>>({});
    const [statusFilter, setStatusFilter] = useState<'OPEN' | 'RESOLVED' | 'DISMISSED'>('OPEN');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (statusFilter === 'OPEN') {
            setReports(initialReports);
        } else {
            fetchFilteredReports();
        }
    }, [statusFilter, initialReports]);

    const fetchFilteredReports = async () => {
        setLoading(true);
        try {
            const res = await api.get(`/admin/reports/queue?status=${statusFilter}`);
            setReports(res.data);
        } catch (e) {
            console.error(e);
        } finally {
            setLoading(false);
        }
    };

    const handleResolve = async (id: string, action: 'RESOLVED' | 'DISMISSED') => {
        setProcessing(id);
        try {
            await api.post(`/admin/reports/${id}/resolve`, {
                status: action,
                note: responses[id] || (action === 'RESOLVED' ? 'Action taken' : 'Dismissed by admin')
            });
            setResponses(prev => {
                const next = { ...prev };
                delete next[id];
                return next;
            });
            if (statusFilter === 'OPEN') {
                onRefresh(); // Refresh parent if we're looking at open queue
            } else {
                fetchFilteredReports(); // Otherwise refresh current view
            }
        } catch (e) {
            console.error(e);
        } finally {
            setProcessing(null);
        }
    };

    const getTargetLink = (report: Report) => {
        if (report.targetType === 'USER') return `/creator/${report.targetSummary}`;
        if (report.targetType === 'PROJECT') return `/mod/${report.targetId}`;
        return '#';
    };

    const getIcon = (type: string) => {
        switch (type) {
            case 'USER': return <User className="w-4 h-4" />;
            case 'COMMENT': return <MessageSquare className="w-4 h-4" />;
            default: return <Flag className="w-4 h-4" />;
        }
    };

    return (
        <div className="grid gap-4">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-white dark:bg-slate-900 p-4 rounded-2xl border border-slate-200 dark:border-white/5">
                <div className="flex items-center gap-2">
                    <Filter className="w-4 h-4 text-slate-400" />
                    <span className="text-sm font-bold text-slate-700 dark:text-slate-300">Filter Status:</span>
                </div>
                <div className="flex bg-slate-100 dark:bg-black/20 p-1 rounded-xl">
                    {(['OPEN', 'RESOLVED', 'DISMISSED'] as const).map(status => (
                        <button
                            key={status}
                            onClick={() => setStatusFilter(status)}
                            className={`px-4 py-1.5 text-xs font-bold rounded-lg transition-colors ${
                                statusFilter === status
                                    ? 'bg-white dark:bg-slate-800 text-modtale-accent shadow-sm'
                                    : 'text-slate-500 hover:text-slate-900 dark:hover:text-white'
                            }`}
                        >
                            {status}
                        </button>
                    ))}
                </div>
            </div>

            {loading ? (
                <div className="text-center py-12">
                    <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-modtale-accent mx-auto"></div>
                </div>
            ) : reports.length === 0 ? (
                <div className="text-center py-20 bg-white dark:bg-slate-900 rounded-[2rem] border border-slate-200 dark:border-white/5 shadow-sm">
                    <div className="w-20 h-20 bg-green-500/10 rounded-full flex items-center justify-center mx-auto mb-6">
                        <ShieldAlert className="w-10 h-10 text-green-500" />
                    </div>
                    <h3 className="text-2xl font-black text-slate-900 dark:text-white mb-2">No Reports Found</h3>
                    <p className="text-slate-500 font-medium">Nothing to show for '{statusFilter}' status.</p>
                </div>
            ) : (
                reports.map(report => (
                    <div key={report.id} className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/5 rounded-3xl p-6 flex flex-col md:flex-row gap-6 relative overflow-hidden group">
                        <div className={`absolute top-0 left-0 bottom-0 w-1 ${
                            report.status === 'OPEN' ? 'bg-red-500' :
                                report.status === 'RESOLVED' ? 'bg-green-500' : 'bg-slate-500'
                        }`}></div>

                        <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-3 mb-2 flex-wrap">
                                <span className={`px-2 py-0.5 rounded text-[10px] font-black uppercase tracking-wider border ${
                                    report.status === 'OPEN' ? 'bg-red-500/10 text-red-500 border-red-500/20' :
                                        report.status === 'RESOLVED' ? 'bg-green-500/10 text-green-500 border-green-500/20' :
                                            'bg-slate-500/10 text-slate-500 border-slate-500/20'
                                }`}>
                                    {report.reason.replace('_', ' ')}
                                </span>
                                <span className="px-2 py-0.5 rounded bg-slate-100 dark:bg-white/10 text-slate-500 dark:text-slate-300 text-[10px] font-black uppercase tracking-wider flex items-center gap-1">
                                    {getIcon(report.targetType)} {report.targetType}
                                </span>
                                <span className="text-[10px] bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 font-mono px-2 py-0.5 rounded border border-indigo-200 dark:border-indigo-500/20">
                                    ID: {report.id}
                                </span>
                                <span className="text-xs text-slate-400 font-medium ml-auto">{new Date(report.createdAt).toLocaleString()}</span>
                            </div>

                            <h3 className="text-lg font-bold text-slate-900 dark:text-white mb-1">
                                {report.targetSummary || 'Unknown Target'}
                            </h3>
                            <div className="text-xs font-mono text-slate-400 mb-4">Target ID: {report.targetId}</div>

                            <p className="text-slate-600 dark:text-slate-300 bg-slate-50 dark:bg-white/5 p-4 rounded-xl text-sm italic mb-4 border border-slate-200 dark:border-white/5 break-words">
                                "{report.description}"
                            </p>

                            <div className="flex items-center gap-2 text-xs font-bold text-slate-500 mb-4">
                                <span>Reported by: <span className="text-slate-700 dark:text-slate-300">{report.reporterUsername}</span></span>
                            </div>

                            {report.status === 'OPEN' && (
                                <textarea
                                    value={responses[report.id] || ''}
                                    onChange={(e) => setResponses(prev => ({ ...prev, [report.id]: e.target.value }))}
                                    placeholder="Add an optional response to the reporter..."
                                    className="w-full p-3 bg-slate-50 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-xl text-sm text-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 resize-y min-h-[80px]"
                                />
                            )}

                            {report.status !== 'OPEN' && (
                                <div className="mt-4 p-3 bg-slate-50 dark:bg-black/20 rounded-xl border border-slate-200 dark:border-white/5 text-sm">
                                    <div className="font-bold text-slate-700 dark:text-slate-300 mb-1">
                                        Action taken by <span className="text-modtale-accent">{report.resolvedBy || 'System'}</span>
                                    </div>
                                    <div className="text-slate-600 dark:text-slate-400 italic">
                                        "{report.resolutionNote || 'No note provided.'}"
                                    </div>
                                </div>
                            )}
                        </div>

                        <div className="flex flex-col gap-2 justify-center min-w-[180px]">
                            {report.targetType !== 'COMMENT' && (
                                <a
                                    href={getTargetLink(report)}
                                    target="_blank"
                                    rel="noreferrer"
                                    className="w-full py-2 bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-300 rounded-lg text-xs font-bold flex items-center justify-center gap-2 hover:bg-slate-200 dark:hover:bg-white/10 transition-colors"
                                >
                                    <ExternalLink className="w-3 h-3" /> View Target
                                </a>
                            )}

                            {report.status === 'OPEN' && (
                                <>
                                    <button
                                        onClick={() => handleResolve(report.id, 'RESOLVED')}
                                        disabled={!!processing}
                                        className="w-full py-2 bg-red-500 text-white rounded-lg text-xs font-bold flex items-center justify-center gap-2 hover:bg-red-600 transition-colors shadow-lg shadow-red-500/20 disabled:opacity-50"
                                    >
                                        <Check className="w-3 h-3" /> Mark Resolved
                                    </button>
                                    <button
                                        onClick={() => handleResolve(report.id, 'DISMISSED')}
                                        disabled={!!processing}
                                        className="w-full py-2 border border-slate-200 dark:border-white/10 text-slate-500 hover:text-slate-900 dark:hover:text-white rounded-lg text-xs font-bold flex items-center justify-center gap-2 hover:bg-slate-50 dark:hover:bg-white/5 transition-colors disabled:opacity-50"
                                    >
                                        <X className="w-3 h-3" /> Dismiss
                                    </button>
                                </>
                            )}
                        </div>
                    </div>
                ))
            )}
        </div>
    );
};