import React, { useState, useEffect, useMemo, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Download, X, ChevronDown, FileText, AlertCircle, ChevronRight } from 'lucide-react';
import { DropdownSelect, type DropdownOption } from '@/components/ui/DropdownSelect';
import { theme } from '@/styles/theme';
import { formatTimeAgo, compareSemVer } from '@/utils/modHelpers';
import { useScrollLock } from '@/hooks/useScrollLock';

interface DownloadModalProps {
    show: boolean;
    onClose: () => void;
    versionsByGame: Record<string, any[]>;
    preReleaseGameVersions?: string[];
    orderedGameVersions?: string[];
    onDownload: (url: string, number: string, gameVersion: string, deps: any[], channel: string) => void;
    showExperimental: boolean;
    onToggleExperimental: () => void;
    onViewHistory: () => void;
}

export const DownloadModal: React.FC<DownloadModalProps> = ({
                                                                show, onClose, versionsByGame, preReleaseGameVersions = [], orderedGameVersions = [], onDownload, showExperimental, onToggleExperimental, onViewHistory
                                                            }) => {
    useScrollLock(show);
    const [selectedGameVer, setSelectedGameVer] = useState<string>('');
    const [isListExpanded, setIsListExpanded] = useState(false);
    const [showPreReleaseGameVersions, setShowPreReleaseGameVersions] = useState(false);
    const wasOpenRef = useRef(false);
    const preReleaseGameVersionSet = useMemo(() => new Set(preReleaseGameVersions), [preReleaseGameVersions]);

    const hasPreReleaseGameVersionEntries = useMemo(() => {
        return Object.entries(versionsByGame).some(([version, builds]) => preReleaseGameVersionSet.has(version) && Array.isArray(builds) && builds.length > 0);
    }, [versionsByGame, preReleaseGameVersionSet]);

    const hasReleaseGameVersionEntries = useMemo(() => {
        return Object.entries(versionsByGame).some(([version, builds]) => !preReleaseGameVersionSet.has(version) && Array.isArray(builds) && builds.length > 0);
    }, [versionsByGame, preReleaseGameVersionSet]);

    const forceShowPreReleaseGameVersions = hasPreReleaseGameVersionEntries && !hasReleaseGameVersionEntries;

    const gameVersions = useMemo(() => {
        const available = new Set(Object.keys(versionsByGame));
        const orderedAvailable = orderedGameVersions.filter(version => available.has(version));
        const orderedSet = new Set(orderedAvailable);
        const unordered = Object.keys(versionsByGame)
            .filter(version => !orderedSet.has(version))
            .sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
        const all = [...orderedAvailable, ...unordered];
        if (showPreReleaseGameVersions) return all;
        return all.filter(version => !preReleaseGameVersionSet.has(version));
    }, [versionsByGame, showPreReleaseGameVersions, preReleaseGameVersionSet, orderedGameVersions]);

    const gameVersionOptions = useMemo<DropdownOption[]>(
        () => gameVersions.map((version) => ({ value: version, label: version })),
        [gameVersions]
    );

    const preferredGameVersion = useMemo(() => gameVersions[0] || '', [gameVersions]);

    useEffect(() => {
        const isOpening = show && !wasOpenRef.current;
        const effectivePreferredGameVersion = preferredGameVersion || gameVersions[0] || '';

        if (isOpening && effectivePreferredGameVersion) {
            setSelectedGameVer(effectivePreferredGameVersion);
            setIsListExpanded(false);
        } else if (show && effectivePreferredGameVersion && !gameVersions.includes(selectedGameVer)) {
            setSelectedGameVer(effectivePreferredGameVersion);
        }

        wasOpenRef.current = show;
    }, [show, gameVersions, selectedGameVer, preferredGameVersion]);

    useEffect(() => {
        if (!show || !preferredGameVersion) return;
        setSelectedGameVer(preferredGameVersion);
        setIsListExpanded(false);
    }, [showPreReleaseGameVersions, showExperimental, show, preferredGameVersion]);

    useEffect(() => {
        if (forceShowPreReleaseGameVersions && !showPreReleaseGameVersions) {
            setShowPreReleaseGameVersions(true);
        }
    }, [forceShowPreReleaseGameVersions, showPreReleaseGameVersions]);

    if (!show) return null;

    const currentVersions = versionsByGame[selectedGameVer] || [];
    const hasReleaseForSelectedGameVersion = currentVersions.some((v: any) => !v.channel || v.channel === 'RELEASE');
    const hasExperimentalForSelectedGameVersion = currentVersions.some((v: any) => v.channel === 'ALPHA' || v.channel === 'BETA');
    const forceShowExperimental = hasExperimentalForSelectedGameVersion && !hasReleaseForSelectedGameVersion;
    const effectiveShowExperimental = showExperimental || forceShowExperimental;
    const visibleVersions = currentVersions.filter((v: any) => effectiveShowExperimental || (!v.channel || v.channel === 'RELEASE'));

    const sortedVersions = [...visibleVersions].sort((a: any, b: any) => {
        const dateDiff = new Date(b.releaseDate).getTime() - new Date(a.releaseDate).getTime();
        if (dateDiff !== 0) return dateDiff;
        return compareSemVer(b.versionNumber, a.versionNumber);
    });

    const latestVer = sortedVersions[0];

    const getVersionBadgeColor = (channel: string) => {
        switch(channel) {
            case 'BETA': return 'bg-purple-100 dark:bg-purple-500/20 text-purple-700 dark:text-purple-200 border-purple-200 dark:border-purple-500/30';
            case 'ALPHA': return 'bg-red-100 dark:bg-red-500/20 text-red-700 dark:text-red-100 border-red-200 dark:border-red-500/30';
            default: return `${theme.colors.bgSurfaceAlt} ${theme.colors.border} ${theme.colors.textPrimary}`;
        }
    };

    const themeClass = latestVer?.channel === 'ALPHA'
        ? 'bg-red-600 hover:bg-red-500 shadow-red-500/20 text-white'
        : latestVer?.channel === 'BETA'
            ? 'bg-purple-600 hover:bg-purple-500 shadow-purple-500/20 text-white'
            : 'bg-modtale-accent hover:bg-blue-500 shadow-blue-500/20 text-white';

    return (
        <div className={theme.components.modalOverlay} onClick={onClose}>
            <div className={`fixed top-[50%] left-[50%] translate-x-[-50%] translate-y-[-50%] w-full max-w-2xl max-h-[90dvh] flex flex-col z-[100] bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 shadow-2xl rounded-2xl overflow-hidden`} onClick={e => e.stopPropagation()}>
                <div className={`p-6 flex justify-between items-center shrink-0 border-b border-slate-100 dark:border-white/5 bg-slate-50 dark:bg-slate-800/50`}>
                    <div>
                        <h3 className={`text-xl font-black ${theme.colors.textPrimary} flex items-center gap-2`}><Download className={`w-5 h-5 ${theme.colors.accent}`} /> Download</h3>
                        {hasPreReleaseGameVersionEntries && hasReleaseGameVersionEntries && (
                            <div className="mt-1 flex items-center gap-2 cursor-pointer group" onClick={() => setShowPreReleaseGameVersions(!showPreReleaseGameVersions)}>
                                <div className={`w-8 h-4 rounded-full relative transition-colors shadow-inner ${showPreReleaseGameVersions ? 'bg-modtale-accent' : 'bg-slate-200 dark:bg-slate-800'}`}>
                                    <div className={`absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform shadow-sm ${showPreReleaseGameVersions ? 'translate-x-4' : ''}`} />
                                </div>
                                <span className={`text-[10px] font-bold ${theme.colors.textMuted} uppercase group-hover:${theme.colors.textPrimary} transition-colors`}>Show Pre-Release Game Versions</span>
                            </div>
                        )}
                        {hasExperimentalForSelectedGameVersion && hasReleaseForSelectedGameVersion && (
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

                <div className={`p-6 overflow-visible relative flex-1 flex flex-col justify-center`}>
                    <div className="mb-6">
                        <label className={`block text-xs font-bold ${theme.colors.textSecondary} uppercase mb-2 tracking-wider`}>Game Version</label>
                        <DropdownSelect
                            options={gameVersionOptions}
                            value={selectedGameVer}
                            onChange={setSelectedGameVer}
                            placeholder="Select Game Version"
                            emptyLabel="No versions found"
                            showSelectedCheck={false}
                            buttonClassName="w-full flex items-center justify-between gap-3 p-3 rounded-xl font-bold text-slate-900 dark:text-white text-xs sm:text-sm cursor-pointer bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 shadow-sm hover:border-modtale-accent/40 dark:hover:border-modtale-accent/50 transition-all duration-300"
                            menuClassName="left-0 right-0 bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-50 py-1 overflow-hidden"
                            optionClassName="w-full flex items-center justify-between text-left px-4 py-2.5 text-xs sm:text-sm font-bold cursor-pointer transition-colors truncate text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700"
                        />
                    </div>

                    {latestVer ? (
                        <>
                            <Link
                                to="#"
                                onClick={(e) => { e.preventDefault(); onDownload(latestVer.fileUrl, latestVer.versionNumber, selectedGameVer, latestVer.dependencies, latestVer.channel); }}
                                className={`w-full p-5 rounded-2xl shadow-lg flex flex-col items-center justify-center gap-1.5 transition-all active:scale-95 mb-6 group relative overflow-hidden ${themeClass}`}
                            >
                                <div className="font-black text-xl flex items-center gap-2 group-hover:scale-105 transition-transform z-10"><Download className="w-6 h-6" /> Download Latest</div>
                                <div className={`text-xs font-bold font-mono px-3 py-1 rounded-full border flex items-center gap-2 z-10 ${getVersionBadgeColor(latestVer.channel || 'RELEASE')}`}>
                                    v{latestVer.versionNumber}
                                    {latestVer.channel !== 'RELEASE' && <span className="uppercase tracking-wider opacity-90">{latestVer.channel}</span>}
                                </div>
                            </Link>

                            <div className="relative mb-6">
                                <div className="absolute inset-0 flex items-center"><div className={`w-full border-t ${theme.colors.borderFaint}`}></div></div>
                                <div className="relative flex justify-center"><span className={`bg-white dark:bg-slate-900 px-3 text-[10px] font-bold ${theme.colors.textMuted} uppercase tracking-widest`}>Other Versions</span></div>
                            </div>

                            <button
                                type="button"
                                onClick={() => setIsListExpanded(!isListExpanded)}
                                className={`w-full flex items-center justify-between p-3 rounded-xl border ${theme.colors.border} bg-white dark:bg-slate-900 ${theme.colors.bgSurfaceHover} transition-colors group shadow-sm`}
                            >
                                <span className={`font-bold ${theme.colors.textPrimary} text-sm`}>View all files for {selectedGameVer}</span>
                                <ChevronDown className={`w-4 h-4 ${theme.colors.textMuted} transition-transform ${isListExpanded ? 'rotate-180' : ''}`} />
                            </button>

                            {isListExpanded && (
                                <div className="mt-2 space-y-2 animate-in fade-in slide-in-from-top-2 duration-200">
                                    {sortedVersions.map((ver: any) => (
                                        <div key={ver.id} className={`flex items-center justify-between p-3 rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 shadow-sm hover:border-slate-300 dark:hover:border-white/20 transition-colors`}>
                                            <div className="flex items-center gap-3">
                                                <div className={`w-10 h-10 rounded-lg ${theme.colors.bgSurfaceAlt} border ${theme.colors.border} flex items-center justify-center ${theme.colors.textMuted}`}><FileText className="w-5 h-5" /></div>
                                                <div>
                                                    <div className={`font-bold ${theme.colors.textPrimary} text-sm flex items-center gap-2`}>
                                                        v{ver.versionNumber}
                                                        {ver.channel !== 'RELEASE' && <span className={`text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded border ${getVersionBadgeColor(ver.channel)}`}>{ver.channel}</span>}
                                                    </div>
                                                    <div className={`text-xs ${theme.colors.textMuted}`}>{formatTimeAgo(ver.releaseDate)}</div>
                                                </div>
                                            </div>
                                            <Link
                                                to="#"
                                                onClick={(e) => { e.preventDefault(); onDownload(ver.fileUrl, ver.versionNumber, selectedGameVer, ver.dependencies, ver.channel); }}
                                                className={`p-2 rounded-lg bg-slate-100 dark:bg-white/5 text-slate-500 dark:text-slate-400 hover:bg-modtale-accent hover:text-white transition-colors`}
                                            >
                                                <Download className="w-4 h-4" />
                                            </Link>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </>
                    ) : (
                        <div className={`text-center py-12 ${theme.colors.textMuted} flex flex-col items-center gap-2`}>
                            <AlertCircle className="w-8 h-8 opacity-50" />
                            <p className="font-medium">No compatible versions found.</p>
                            {!effectiveShowExperimental && currentVersions.length > 0 && hasExperimentalForSelectedGameVersion && hasReleaseForSelectedGameVersion && (
                                <button type="button" onClick={onToggleExperimental} className={`text-xs ${theme.colors.accent} font-bold hover:underline`}>
                                    Show Beta/Alpha versions
                                </button>
                            )}
                        </div>
                    )}
                </div>

                <div className={`p-4 border-t border-slate-100 dark:border-white/5 shrink-0 z-10 flex items-center justify-center bg-slate-50 dark:bg-[#0B1120]`}>
                    <button type="button" onClick={onViewHistory} className={`text-xs ${theme.colors.textMuted} hover:${theme.colors.accent} font-bold uppercase tracking-wider flex items-center justify-center gap-1 transition-colors w-full`}>
                        View Full Changelog <ChevronRight className="w-3 h-3" />
                    </button>
                </div>
            </div>
        </div>
    );
};
