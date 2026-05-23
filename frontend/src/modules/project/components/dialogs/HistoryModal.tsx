import React, { memo, useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { List, X, Download, ChevronUp, ChevronDown } from 'lucide-react';
import { MarkdownRenderer } from '@/components/ui/MarkdownRenderer';
import { theme } from '@/styles/theme';
import { formatTimeAgo } from '@/utils/modHelpers';
import { useScrollLock } from '@/hooks/useScrollLock';

interface HistoryModalProps {
    show: boolean;
    onClose: () => void;
    history: any[];
    showExperimental: boolean;
    onToggleExperimental: () => void;
    onDownload: (url: string, number: string, deps: any[], channel: string) => void;
    hasExperimentalVersions?: boolean;
}

const HistoryVersionItem = memo(({
    ver,
    isExpanded,
    onToggleExpand,
    onDownload,
    badgeClass
}: {
    ver: any;
    isExpanded: boolean;
    onToggleExpand: (id: string) => void;
    onDownload: (e: React.MouseEvent, ver: any) => void;
    badgeClass: string;
}) => {
    const isLong = ver.changelog && ver.changelog.length > 300;
    return (
        <div
            className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 shadow-sm hover:border-slate-300 dark:hover:border-white/20 transition-all duration-300 rounded-xl p-5"
            style={{ contentVisibility: 'auto', containIntrinsicSize: '420px' }}
        >
            <div className={`flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4 border-b border-slate-100 dark:border-white/5 pb-4`}>
                <div>
                    <div className="flex items-center gap-3 mb-1">
                        <span className={`text-xl font-black ${theme.colors.textPrimary}`}>v{ver.versionNumber}</span>
                        {ver.channel !== 'RELEASE' && <span className={`text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded border ${badgeClass}`}>{ver.channel}</span>}
                    </div>
                    <div className={`flex items-center gap-3 text-xs font-bold ${theme.colors.textMuted} uppercase tracking-wide`}>
                        <span>{formatTimeAgo(ver.releaseDate)}</span>
                        <span className="w-1 h-1 rounded-full bg-slate-300 dark:bg-slate-600"></span>
                        <span>{ver.gameVersions?.join(', ')}</span>
                        <span className="w-1 h-1 rounded-full bg-slate-300 dark:bg-slate-600"></span>
                        <span className="flex items-center gap-1.5"><Download className="w-3.5 h-3.5" /> {(ver.downloadCount || 0).toLocaleString()}</span>
                    </div>
                </div>
                <Link
                    to="#"
                    onClick={(e) => onDownload(e, ver)}
                    className={`px-4 py-2 bg-slate-100 dark:bg-white/5 hover:bg-modtale-accent hover:text-white text-slate-500 dark:text-slate-400 rounded-lg font-bold text-sm transition-all flex items-center justify-center gap-2`}
                >
                    <Download className="w-4 h-4" /> Download
                </Link>
            </div>

            {ver.changelog ? (
                <div className="mt-2">
                    <div className={`prose prose-sm dark:prose-invert max-w-none ${theme.colors.textSecondary} ${isExpanded ? '' : 'line-clamp-3'}`}>
                        <MarkdownRenderer content={ver.changelog} />
                    </div>
                    {isLong && (
                        <button type="button" onClick={() => onToggleExpand(ver.id)} className={`mt-3 text-xs font-bold ${theme.colors.accent} hover:underline flex items-center gap-1`}>
                            {isExpanded ? <><ChevronUp className="w-3 h-3"/> Show Less</> : <><ChevronDown className="w-3 h-3"/> Read More</>}
                        </button>
                    )}
                </div>
            ) : <p className={`text-sm ${theme.colors.textMuted} italic`}>No changelog provided.</p>}
        </div>
    );
});

export const HistoryModal: React.FC<HistoryModalProps> = ({
    show, onClose, history, showExperimental, onToggleExperimental, onDownload, hasExperimentalVersions
}) => {
    useScrollLock(show);
    const [expandedChangelog, setExpandedChangelog] = useState<string | null>(null);

    const actualHasExperimental = useMemo(() => {
        if (hasExperimentalVersions !== undefined) return hasExperimentalVersions;
        return history.some((v: any) => v.channel === 'ALPHA' || v.channel === 'BETA');
    }, [history, hasExperimentalVersions]);

    const visibleHistory = useMemo(() => {
        return history.filter((v: any) => showExperimental || !v.channel || v.channel === 'RELEASE');
    }, [history, showExperimental]);

    const getVersionBadgeColor = useCallback((channel: string) => {
        switch(channel) {
            case 'BETA': return 'bg-purple-100 dark:bg-purple-500/20 text-purple-700 dark:text-purple-200 border-purple-200 dark:border-purple-500/30';
            case 'ALPHA': return 'bg-red-100 dark:bg-red-500/20 text-red-700 dark:text-red-100 border-red-200 dark:border-red-500/30';
            default: return `${theme.colors.bgSurfaceAlt} ${theme.colors.border} ${theme.colors.textPrimary}`;
        }
    }, []);

    const handleToggleExpand = useCallback((id: string) => {
        setExpandedChangelog((prev) => (prev === id ? null : id));
    }, []);

    const handleDownloadClick = useCallback((e: React.MouseEvent, ver: any) => {
        e.preventDefault();
        onDownload(ver.fileUrl, ver.versionNumber, ver.dependencies, ver.channel);
    }, [onDownload]);

    if (!show) return null;

    return (
        <div className={theme.components.modalOverlay} onClick={onClose}>
            <style>{`
                .history-modal-scrollbar {
                    scrollbar-width: thin;
                    scrollbar-color: rgba(148, 163, 184, 0.65) transparent;
                }
                .history-modal-scrollbar::-webkit-scrollbar {
                    width: 10px;
                }
                .history-modal-scrollbar::-webkit-scrollbar-track {
                    background: transparent;
                }
                .history-modal-scrollbar::-webkit-scrollbar-thumb {
                    background: linear-gradient(to bottom, rgba(148, 163, 184, 0.85), rgba(100, 116, 139, 0.8));
                    border: 2px solid transparent;
                    border-radius: 9999px;
                    background-clip: padding-box;
                }
                .history-modal-scrollbar::-webkit-scrollbar-thumb:hover {
                    background: linear-gradient(to bottom, rgba(100, 116, 139, 0.95), rgba(71, 85, 105, 0.92));
                    background-clip: padding-box;
                }
                .dark .history-modal-scrollbar {
                    scrollbar-color: rgba(100, 116, 139, 0.9) transparent;
                }
                .dark .history-modal-scrollbar::-webkit-scrollbar-thumb {
                    background: linear-gradient(to bottom, rgba(71, 85, 105, 0.95), rgba(51, 65, 85, 0.9));
                    background-clip: padding-box;
                }
                .dark .history-modal-scrollbar::-webkit-scrollbar-thumb:hover {
                    background: linear-gradient(to bottom, rgba(100, 116, 139, 0.95), rgba(71, 85, 105, 0.95));
                    background-clip: padding-box;
                }
            `}</style>
            <div className={`fixed top-[50%] left-[50%] translate-x-[-50%] translate-y-[-50%] w-full max-w-3xl max-h-[85dvh] flex flex-col z-[100] bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 shadow-2xl rounded-2xl overflow-hidden`} onClick={e => e.stopPropagation()}>
                <div className={`p-6 flex justify-between items-center shrink-0 border-b border-slate-100 dark:border-white/5 bg-slate-50 dark:bg-slate-800/50`}>
                    <div>
                        <h3 className={`text-xl font-black ${theme.colors.textPrimary} flex items-center gap-2`}><List className={`w-5 h-5 ${theme.colors.accent}`} /> Changelog</h3>
                        {actualHasExperimental && (
                            <div className="mt-1 flex items-center gap-2 cursor-pointer group" onClick={onToggleExperimental}>
                                <div className={`w-8 h-4 rounded-full relative transition-colors shadow-inner ${showExperimental ? 'bg-modtale-accent' : 'bg-slate-200 dark:bg-slate-800'}`}>
                                    <div className={`absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform shadow-sm ${showExperimental ? 'translate-x-4' : ''}`} />
                                </div>
                                <span className={`text-[10px] font-bold ${theme.colors.textMuted} uppercase group-hover:${theme.colors.textPrimary} transition-colors`}>Show Beta/Alpha</span>
                            </div>
                        )}
                    </div>
                    <button type="button" onClick={onClose} className={`p-2 rounded-full hover:bg-slate-100 dark:hover:bg-white/10 text-slate-500 transition-colors`}><X className="w-5 h-5" /></button>
                </div>

                <div className={`p-6 overflow-y-auto custom-scrollbar history-modal-scrollbar flex-1 relative`}>
                    <div className="space-y-6">
                        {visibleHistory.map((ver: any) => {
                            return (
                                <HistoryVersionItem
                                    key={ver.id}
                                    ver={ver}
                                    isExpanded={expandedChangelog === ver.id}
                                    onToggleExpand={handleToggleExpand}
                                    onDownload={handleDownloadClick}
                                    badgeClass={getVersionBadgeColor(ver.channel)}
                                />
                            );
                        })}
                    </div>
                </div>
            </div>
        </div>
    );
};
