import React, { useState, useEffect } from 'react';
import { Download, List, X, ChevronDown, Check, Box, Link as LinkIcon, AlertCircle, Bell } from 'lucide-react';
import { OptimizedImage } from '@/components/ui/OptimizedImage';
import { BACKEND_URL } from '@/utils/api';
import type { Project } from '@/types';
import { theme } from '@/styles/theme';
import { GLASS_CARD, GLASS_HEADER } from '../styles';

export const InlineDependencyUI = ({ randomProject }: { randomProject?: Project }) => {
    const [isMounted, setIsMounted] = useState(false);
    useEffect(() => setIsMounted(true), []);

    const randomIconUrl = randomProject?.imageUrl
        ? (randomProject.imageUrl.startsWith('/api') ? `${BACKEND_URL}${randomProject.imageUrl}` : randomProject.imageUrl)
        : null;

    const randomVersion = randomProject?.versions?.[0]?.versionNumber || '1.0.0';

    return (
        <div className={`${theme.components.modalContent} w-full flex flex-col min-h-[420px] transform transition-transform hover:scale-[1.02] duration-500`}>
            <div className={theme.components.modalHeader}>
                <h3 className={`font-black ${theme.colors.textPrimary} flex items-center gap-2.5 text-lg`}>
                    <LinkIcon className={`w-5 h-5 ${theme.colors.accent}`} aria-hidden="true" /> Dependencies
                </h3>
            </div>
            <div className={`${theme.components.modalBody} overflow-hidden relative flex-1 flex flex-col`}>
                <div className="flex items-center justify-between p-4 rounded-2xl border border-blue-400/40 bg-blue-50/60 dark:bg-blue-500/10 shadow-sm transition-all hover:bg-blue-50 dark:hover:bg-blue-500/20">
                    <div className="flex items-center gap-3 sm:gap-4 min-w-0">
                        <div className="w-6 h-6 rounded-full bg-modtale-accent text-white flex items-center justify-center shrink-0 shadow-md">
                            <Check className="w-3.5 h-3.5" aria-hidden="true" />
                        </div>
                        <div className="w-10 h-10 sm:w-12 sm:h-12 rounded-xl bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 flex items-center justify-center shrink-0 overflow-hidden shadow-sm">
                            <img src="/assets/favicon.svg" alt="Hytale Core Library Icon" className="w-full h-full object-cover p-2" loading="lazy" />
                        </div>
                        <div className="min-w-0">
                            <div className="font-bold text-sm sm:text-base text-slate-900 dark:text-white truncate">Hytale Core Library</div>
                            <div className="text-[10px] sm:text-xs text-slate-500 dark:text-slate-400 font-mono mt-0.5">v1.2.0</div>
                        </div>
                    </div>
                    <span className="text-[9px] sm:text-[10px] font-bold uppercase bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-300 px-2 py-1 rounded-md border border-blue-200 dark:border-blue-500/30 shrink-0 ml-2">Required</span>
                </div>

                <div className={`flex items-center justify-between p-4 rounded-2xl ${theme.colors.dangerBorder} ${theme.colors.dangerBg} border shadow-sm transition-all hover:border-red-500 cursor-pointer`}>
                    <div className="flex items-center gap-3 sm:gap-4 min-w-0">
                        <div className="w-6 h-6 rounded-full border-2 border-slate-300 dark:border-slate-600 bg-white/50 dark:bg-slate-800/50 flex items-center justify-center shrink-0 shadow-sm" />
                        <div className="w-10 h-10 sm:w-12 sm:h-12 rounded-xl bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 flex items-center justify-center shrink-0 overflow-hidden shadow-sm">
                            <Box className="w-5 h-5 sm:w-6 h-6 text-slate-400" />
                        </div>
                        <div className="min-w-0">
                            <div className="font-bold text-sm sm:text-base text-slate-900 dark:text-white truncate">MathLib</div>
                            <div className="text-[10px] sm:text-xs text-slate-500 dark:text-slate-400 font-mono mt-0.5">v2.1.0</div>
                        </div>
                    </div>
                    <span className="text-[9px] sm:text-[10px] font-black uppercase bg-red-500 text-white px-2 py-1 rounded-md shadow-sm shrink-0 ml-2">Required</span>
                </div>

                {randomProject ? (
                    <div className={`flex items-center justify-between p-4 rounded-2xl ${theme.colors.border} ${theme.colors.bgBase} border shadow-sm`}>
                        <div className="flex items-center gap-3 sm:gap-4 min-w-0">
                            <div className="w-6 h-6 rounded-full border-2 border-slate-300 dark:border-slate-600 bg-white/50 dark:bg-slate-800/50 shrink-0" />
                            <div className="w-10 h-10 sm:w-12 sm:h-12 rounded-xl overflow-hidden shadow-sm border border-slate-200 dark:border-white/10 shrink-0">
                                {randomIconUrl ? (
                                    <OptimizedImage
                                        key={`dep-icon-${isMounted}`}
                                        src={randomIconUrl}
                                        alt={`${randomProject.title} Icon`}
                                        baseWidth={48}
                                        className="w-full h-full object-cover"
                                    />
                                ) : <Box className="w-5 h-5 sm:w-6 h-6 text-slate-400" />}
                            </div>
                            <div className="min-w-0">
                                <div className="font-bold text-sm sm:text-base text-slate-900 dark:text-white truncate">{randomProject.title}</div>
                                <div className="text-[10px] sm:text-xs text-slate-500 dark:text-slate-400 font-mono mt-0.5">v{randomVersion}</div>
                            </div>
                        </div>
                        <span className={`text-[9px] sm:text-[10px] font-bold uppercase ${theme.colors.bgSurfaceAlt} ${theme.colors.textSecondary} px-2 py-1 rounded-md border ${theme.colors.border} shrink-0 ml-2`}>Optional</span>
                    </div>
                ) : (
                    <div className="h-20 w-full animate-pulse bg-slate-100/50 dark:bg-slate-800/50 rounded-2xl border border-slate-200/50 dark:border-white/5" />
                )}

                <div className={`mt-auto flex items-start gap-3 text-sm ${theme.colors.dangerText} ${theme.colors.dangerBg} p-4 rounded-2xl border ${theme.colors.dangerBorder} shadow-sm`}>
                    <AlertCircle className="w-5 h-5 shrink-0" aria-hidden="true" />
                    <p className="font-medium text-xs sm:text-sm">Some <span className="font-black">Required</span> dependencies are currently unselected. The project may not function correctly without them.</p>
                </div>
            </div>
        </div>
    );
};

export const InlineDownloadUI = () => {
    const [showExperimental, setShowExperimental] = useState(false);
    const [view, setView] = useState<'download' | 'changelog'>('download');
    const [selectedVersion, setSelectedVersion] = useState('2026.03.11-dcad8778f');
    const [isDropdownOpen, setIsDropdownOpen] = useState(false);
    const versions = ['2026.03.11-dcad8778f', '2026.02.28-b8e9f1a2', '2026.01.15-a7c6d5e4'];

    const allVersions = [
        { id: 'v8', versionNumber: '3.0.1-alpha', channel: 'ALPHA', gameVersion: '2026.03.11-dcad8778f', date: '1 hour ago', changelog: 'Hotfix: resolved immediate crash on startup with specific GPU drivers.' },
        { id: 'v7', versionNumber: '3.0.0-alpha', channel: 'ALPHA', gameVersion: '2026.03.11-dcad8778f', date: '2 hours ago', changelog: 'Complete rewrite of the rendering engine. Highly unstable.' },
        { id: 'v6', versionNumber: '2.5.1', channel: 'RELEASE', gameVersion: '2026.03.11-dcad8778f', date: '12 hours ago', changelog: 'Minor localization fixes for French and German.' },
        { id: 'v5', versionNumber: '2.5.0', channel: 'RELEASE', gameVersion: '2026.03.11-dcad8778f', date: '1 day ago', changelog: 'Compatibility update for the latest game patch. Added new dynamic lighting.' },
        { id: 'v4', versionNumber: '2.4.5-beta', channel: 'BETA', gameVersion: '2026.02.28-b8e9f1a2', date: '1 week ago', changelog: 'Testing new durability mechanics. Expect bugs.' },
        { id: 'v3', versionNumber: '2.4.1', channel: 'RELEASE', gameVersion: '2026.02.28-b8e9f1a2', date: '2 weeks ago', changelog: 'Added 5 new elemental wands.\nFixed visual bugs with particle effects.' },
        { id: 'v2', versionNumber: '2.4.0', channel: 'RELEASE', gameVersion: '2026.01.15-a7c6d5e4', date: '1 month ago', changelog: 'Initial release of the expanded magic system. Includes 20 new spells and 3 new mob types.' },
        { id: 'v1', versionNumber: '2.3.9', channel: 'RELEASE', gameVersion: '2026.01.15-a7c6d5e4', date: '2 months ago', changelog: 'Final update for the old magic system before the rewrite.' }
    ];

    const currentVersions = allVersions.filter(v => v.gameVersion === selectedVersion);
    const visibleVersions = currentVersions.filter(v => showExperimental || v.channel === 'RELEASE');
    const latestVer = visibleVersions[0];

    const getVersionBadgeColor = (channel: string) => {
        switch(channel) {
            case 'BETA': return 'bg-purple-100/80 text-purple-800 border-purple-200/50 dark:bg-purple-500/20 dark:text-purple-200 dark:border-purple-500/30';
            case 'ALPHA': return 'bg-red-100/80 text-red-800 border-red-200/50 dark:bg-red-500/20 dark:text-red-200 dark:border-red-500/30';
            default: return 'bg-white/60 border-white/40 text-slate-800 dark:bg-white/10 dark:border-white/20 dark:text-white';
        }
    };

    const themeClass = latestVer?.channel === 'ALPHA'
        ? 'bg-red-600/90 hover:bg-red-500 shadow-[0_8px_24px_rgba(220,38,38,0.3),inset_0_1px_0_rgba(255,255,255,0.2)] border-red-500/50 text-white'
        : latestVer?.channel === 'BETA'
            ? 'bg-purple-600/90 hover:bg-purple-500 shadow-[0_8px_24px_rgba(147,51,234,0.3),inset_0_1px_0_rgba(255,255,255,0.2)] border-purple-500/50 text-white'
            : 'bg-modtale-accent/90 hover:bg-modtale-accent shadow-[0_8px_24px_rgba(59,130,246,0.3),inset_0_1px_0_rgba(255,255,255,0.2)] border-modtale-accent/50 text-white';

    if (view === 'changelog') {
        return (
            <div className={`${theme.components.modalContent} w-full overflow-hidden flex flex-col h-[380px] transform transition-transform hover:scale-[1.02] duration-500`}>
                <div className={`${theme.components.modalHeader} p-4 sm:p-5`}>
                    <div>
                        <h3 className="text-base sm:text-lg font-black text-slate-900 dark:text-white flex items-center gap-2"><List className="w-4 h-4 sm:w-5 sm:h-5 text-modtale-accent" aria-hidden="true" /> Changelog</h3>
                        <div className="mt-1 flex items-center gap-2 cursor-pointer group" onClick={() => setShowExperimental(!showExperimental)}>
                            <div className={`w-7 sm:w-8 h-3.5 sm:h-4 rounded-full relative transition-colors shadow-[inset_0_1px_4px_rgba(0,0,0,0.2)] ${showExperimental ? 'bg-modtale-accent' : 'bg-slate-300/80 dark:bg-slate-700/80'}`}>
                                <div className={`absolute top-[1px] sm:top-0.5 left-[1px] sm:left-0.5 w-3 h-3 bg-white rounded-full transition-transform shadow-sm border border-black/5 ${showExperimental ? 'translate-x-3 sm:translate-x-4' : ''}`} />
                            </div>
                            <span className="text-[9px] sm:text-[10px] font-bold text-slate-600 dark:text-slate-400 uppercase group-hover:text-slate-800 dark:group-hover:text-slate-200 transition-colors">Show Beta/Alpha</span>
                        </div>
                    </div>
                    <button onClick={() => setView('download')} aria-label="Close Changelog" className={`p-2 rounded-full ${theme.colors.bgSurfaceHover} ${theme.colors.textMuted} transition-colors`}><X className="w-4 h-4 sm:w-5 sm:h-5" /></button>
                </div>

                <div className={`${theme.components.modalBody} p-4 sm:p-5 overflow-y-auto custom-scrollbar flex-1 space-y-4 relative`}>
                    {visibleVersions.length > 0 ? (
                        visibleVersions.map(ver => (
                            <div key={ver.id} className={`rounded-xl p-3 sm:p-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 shadow-sm hover:border-slate-300 dark:hover:border-white/20 transition-colors`}>
                                <div className="flex items-start sm:items-center justify-between gap-2 sm:gap-4 mb-3 border-b border-slate-200 dark:border-white/10 pb-3 flex-col sm:flex-row">
                                    <div>
                                        <div className="flex items-center gap-2 mb-1">
                                            <span className="text-sm sm:text-base font-black text-slate-900 dark:text-white">v{ver.versionNumber}</span>
                                            {ver.channel !== 'RELEASE' && <span className={`text-[8px] sm:text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded border backdrop-blur-md shadow-sm ${getVersionBadgeColor(ver.channel)}`}>{ver.channel}</span>}
                                        </div>
                                        <div className="flex flex-wrap items-center gap-1.5 sm:gap-2 text-[9px] sm:text-[10px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wide">
                                            <span>{ver.date}</span>
                                            <span className="hidden sm:block w-1 h-1 rounded-full bg-slate-400/50 dark:bg-slate-600/50"></span>
                                            <span className="truncate max-w-[120px] sm:max-w-none">{ver.gameVersion}</span>
                                        </div>
                                    </div>
                                    <button aria-label={`Download version ${ver.versionNumber}`} className="p-2 sm:p-2.5 bg-slate-100 dark:bg-white/10 hover:bg-modtale-accent hover:text-white text-slate-700 dark:text-slate-300 rounded-lg transition-all shrink-0 shadow-sm border border-slate-200 dark:border-white/5 w-full sm:w-auto flex justify-center">
                                        <Download className="w-4 h-4" />
                                    </button>
                                </div>
                                <div className="text-[11px] sm:text-xs text-slate-700 dark:text-slate-300 whitespace-pre-wrap leading-relaxed">
                                    {ver.changelog}
                                </div>
                            </div>
                        ))
                    ) : (
                        <div className="flex flex-col items-center justify-center text-slate-500 h-full gap-2">
                            <AlertCircle className="w-8 h-8 opacity-50" aria-hidden="true" />
                            <p className="font-medium text-xs sm:text-sm">No compatible versions.</p>
                        </div>
                    )}
                </div>
            </div>
        );
    }

    return (
            <div className={`${theme.components.modalContent} w-full overflow-hidden relative flex flex-col h-[380px] transform transition-transform hover:scale-[1.02] duration-500`}>
            <div className={`${theme.components.modalHeader} p-4 sm:p-5`}>
                <div>
                    <h3 className="text-base sm:text-lg font-black text-slate-900 dark:text-white flex items-center gap-2">
                        <Download className="w-4 h-4 sm:w-5 sm:h-5 text-modtale-accent" aria-hidden="true" /> Download
                    </h3>
                    <div className="mt-1 flex items-center gap-2 group cursor-pointer" onClick={() => setShowExperimental(!showExperimental)}>
                        <div className={`w-7 sm:w-8 h-3.5 sm:h-4 rounded-full relative transition-colors shadow-[inset_0_1px_4px_rgba(0,0,0,0.2)] ${showExperimental ? 'bg-modtale-accent' : 'bg-slate-300/80 dark:bg-slate-700/80'}`}>
                            <div className={`absolute top-[1px] sm:top-0.5 left-[1px] sm:left-0.5 w-3 h-3 bg-white rounded-full transition-transform shadow-sm border border-black/5 ${showExperimental ? 'translate-x-3 sm:translate-x-4' : ''}`} />
                        </div>
                        <span className="text-[9px] sm:text-[10px] font-bold text-slate-600 dark:text-slate-400 uppercase group-hover:text-slate-800 dark:group-hover:text-slate-200 transition-colors">Show Beta/Alpha</span>
                    </div>
                </div>
            </div>

            <div className="p-4 sm:p-5 overflow-visible relative flex-1 flex flex-col justify-center">
                <div className="mb-5 relative z-20 shrink-0">
                    <label className="block text-[9px] sm:text-[10px] font-bold text-slate-600 dark:text-slate-400 uppercase mb-2 tracking-wider">Game Version</label>
                    <div className="relative">
                        <div
                            className={`w-full flex items-center justify-between p-3 rounded-xl font-bold text-slate-900 dark:text-white text-xs sm:text-sm cursor-pointer bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 shadow-sm hover:border-modtale-accent/40 dark:hover:border-modtale-accent/50 transition-all ${isDropdownOpen ? 'ring-2 ring-modtale-accent border-transparent' : ''}`}
                            onClick={() => setIsDropdownOpen(!isDropdownOpen)}
                        >
                            <span className="truncate pr-2">{selectedVersion}</span>
                            <ChevronDown className={`w-4 h-4 text-slate-500 shrink-0 transition-transform ${isDropdownOpen ? 'rotate-180' : ''}`} aria-hidden="true" />
                        </div>
                        {isDropdownOpen && (
                            <div className="absolute top-[calc(100%+8px)] left-0 w-full bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl overflow-hidden z-50 py-1">
                                {versions.map(v => (
                                    <div
                                        key={v}
                                        className={`px-4 py-2.5 text-xs sm:text-sm font-bold cursor-pointer transition-colors truncate ${selectedVersion === v ? 'text-modtale-accent bg-blue-50/50 dark:bg-blue-500/10' : 'text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'}`}
                                        onClick={() => {
                                            setSelectedVersion(v);
                                            setIsDropdownOpen(false);
                                        }}
                                    >
                                        {v}
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>

                {latestVer ? (
                    <button className={`w-full backdrop-blur-xl p-3 sm:p-4 rounded-2xl flex flex-col items-center justify-center gap-1 sm:gap-1.5 transition-all active:scale-95 mb-2 relative z-0 group overflow-hidden shrink-0 border ${themeClass}`}>
                        <div className="font-black text-base sm:text-lg flex items-center gap-2 group-hover:scale-105 transition-transform z-10"><Download className="w-4 h-4 sm:w-5 sm:h-5" aria-hidden="true" /> Download Latest</div>
                        <div className={`text-[9px] sm:text-[10px] font-bold font-mono px-2 sm:px-3 py-1 rounded-full border flex items-center gap-1.5 z-10 backdrop-blur-md shadow-sm ${getVersionBadgeColor(latestVer.channel)}`}>
                            v{latestVer.versionNumber} {latestVer.channel !== 'RELEASE' && <span className="uppercase opacity-80">{latestVer.channel}</span>}
                        </div>
                    </button>
                ) : (
                    <div className="flex-1 flex flex-col items-center justify-center text-slate-500">
                        <AlertCircle className="w-6 h-6 sm:w-8 sm:h-8 opacity-50 mb-2" aria-hidden="true" />
                        <p className="font-medium text-xs sm:text-sm text-center">No compatible versions.</p>
                        {!showExperimental && currentVersions.length > 0 && (
                            <button onClick={() => setShowExperimental(true)} className="mt-2 text-[10px] sm:text-[11px] font-bold text-modtale-accent hover:underline">
                                Show experimental
                            </button>
                        )}
                    </div>
                )}
            </div>

            <div className={`${theme.components.modalFooter} p-3 sm:p-4 shrink-0 z-10`}>
                <button onClick={() => setView('changelog')} className="text-[10px] sm:text-[11px] text-slate-500 hover:text-modtale-accent font-bold uppercase tracking-wider flex items-center justify-start gap-1 w-full transition-colors">
                    View Full Changelog <ChevronDown className="w-3 h-3 -rotate-90" aria-hidden="true" />
                </button>
            </div>
        </div>
    );
};

export const InlineNotificationUI = () => (
    <div className={`${GLASS_CARD} w-full flex flex-col h-[380px] transform transition-transform hover:scale-[1.02] duration-500`}>
        <div className={`p-4 sm:p-6 flex justify-between items-center ${GLASS_HEADER}`}>
            <h3 className="font-bold text-slate-900 dark:text-white flex items-center gap-2 sm:gap-2.5 text-base sm:text-lg">
                <Bell className="w-4 h-4 sm:w-5 sm:h-5 text-amber-500" aria-hidden="true" /> Notifications
            </h3>
            <span className="text-[10px] sm:text-xs text-amber-600 dark:text-amber-500 font-bold cursor-pointer hover:underline uppercase tracking-wider">Clear All</span>
        </div>
        <div className="divide-y divide-slate-200 dark:divide-white/5 relative flex-1 overflow-hidden">
            <div className="absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-white dark:from-slate-900 to-transparent z-10 pointer-events-none opacity-80" />

            <div className="p-4 sm:p-6 bg-blue-50/40 dark:bg-white/[0.02] flex items-start gap-3 sm:gap-4 hover:bg-blue-50/80 dark:hover:bg-white/[0.04] transition-colors">
                <div className="w-10 h-10 sm:w-12 sm:h-12 rounded-xl bg-white dark:bg-slate-800 text-blue-600 dark:text-blue-400 flex items-center justify-center shrink-0 border border-slate-200 dark:border-white/10 overflow-hidden shadow-sm">
                    <img src="https://cdn.modtale.net/images/d813b136-35aa-46c6-bb9e-359c20f7c146-cropped.png" alt="LevelingCore Update Icon" className="w-full h-full object-cover" loading="lazy" />
                </div>
                <div className="flex-1 min-w-0">
                    <div className="font-bold text-sm sm:text-base text-slate-900 dark:text-white mb-1 flex items-center truncate">
                        Update: LevelingCore <span className="inline-block w-2 h-2 bg-blue-500 rounded-full ml-2 shadow-[0_0_8px_rgba(59,130,246,0.6)] shrink-0" />
                    </div>
                    <div className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 font-medium truncate">Version 2.0 is now available.</div>
                    <div className="text-[10px] sm:text-xs text-slate-400 dark:text-slate-500 mt-1.5 sm:mt-2 font-mono uppercase tracking-wider">10 mins ago</div>
                </div>
            </div>

            <div className="p-4 sm:p-6 flex items-start gap-3 sm:gap-4 hover:bg-slate-50 dark:hover:bg-white/[0.02] transition-colors">
                <div className="w-10 h-10 sm:w-12 sm:h-12 rounded-xl bg-white dark:bg-slate-800 text-purple-600 dark:text-purple-400 flex items-center justify-center shrink-0 border border-slate-200 dark:border-white/10 overflow-hidden shadow-sm p-1">
                    <img src="https://cdn.modtale.net/avatars/AzureDoom/83c01443-6302-4aff-beb9-7d6f656f994c-cropped.png" alt="AzureDoom Profile Picture" className="w-full h-full object-cover rounded-lg" loading="lazy" />
                </div>
                <div className="flex-1 min-w-0">
                    <div className="font-bold text-sm sm:text-base text-slate-800 dark:text-slate-200 mb-1 truncate">
                        Developer Reply
                    </div>
                    <div className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 font-medium leading-relaxed truncate">AzureDoom replied to your comment.</div>
                    <div className="text-[10px] sm:text-xs text-slate-400 dark:text-slate-500 mt-1.5 sm:mt-2 font-mono uppercase tracking-wider">2 hours ago</div>
                </div>
            </div>
        </div>
    </div>
);
