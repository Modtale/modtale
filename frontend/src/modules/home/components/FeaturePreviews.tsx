import React, { useState, useEffect, useMemo, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Download, List, X, ChevronDown, ChevronRight, Check, Box, Link as LinkIcon, AlertCircle, Bell, Search, ArrowUpRight, MessageSquare, Send, Save, PieChart, TrendingUp, Eye, ArrowBigUp, ArrowBigDown, Settings } from 'lucide-react';
import { OptimizedImage } from '@/components/ui/OptimizedImage';
import { api, BACKEND_URL } from '@/utils/api';
import { SiteRoutes } from '@/utils/routes';
import { ProjectCard } from '@/modules/project/components/ProjectCard';
import type { Project, User } from '@/types';
import { theme } from '@/styles/theme';
import { GLASS_CARD, GLASS_HEADER } from '../styles';
import { getClassificationIcon, toTitleCase, formatTimeAgo } from '@/utils/modHelpers';
import { LineChart } from '@/components/ui/charts/LineChart';
import { FeaturedModCard } from './HeroMarquee';
import { getCommentRoleBadge } from '@/modules/project/utils/commentRoles';

export const InlineDependencyUI = ({ randomProject }: { randomProject?: Project }) => {
    const [isMounted, setIsMounted] = useState(false);
    useEffect(() => setIsMounted(true), []);

    const randomIconUrl = randomProject?.imageUrl
        ? (randomProject.imageUrl.startsWith('/api') ? `${BACKEND_URL}${randomProject.imageUrl}` : randomProject.imageUrl)
        : null;

    const randomVersion = randomProject?.versions?.[0]?.versionNumber || '1.0.0';

    return (
        <div className={`${theme.components.modalContent} w-full flex flex-col min-h-[420px] transform transition-transform duration-500`}>
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
    const [showPreReleaseGameVersions, setShowPreReleaseGameVersions] = useState(false);
    const [view, setView] = useState<'download' | 'changelog'>('download');
    const [selectedVersion, setSelectedVersion] = useState('0.5.4');
    const [isDropdownOpen, setIsDropdownOpen] = useState(false);
    const orderedGameVersions = ['0.6.0-pre.2', '0.6.0-pre.1.1', '0.6.0-pre.1', '0.5.4', '0.5.3', '0.5.2', '0.5.1', '0.5.0', '0.5.0-pre.9.2', '0.5.0-pre.9.1'];
    const preReleaseGameVersions = ['0.6.0-pre.2', '0.6.0-pre.1.1', '0.6.0-pre.1', '0.5.0-pre.9.2', '0.5.0-pre.9.1'];
    const preReleaseGameVersionSet = useMemo(() => new Set(preReleaseGameVersions), []);
    const gameVersions = useMemo(() => {
        if (showPreReleaseGameVersions) return orderedGameVersions;
        return orderedGameVersions.filter(version => !preReleaseGameVersionSet.has(version));
    }, [orderedGameVersions, preReleaseGameVersionSet, showPreReleaseGameVersions]);
    const preferredGameVersion = useMemo(() => gameVersions[0] || '', [gameVersions]);

    const allVersions = [
        { id: 'v9', versionNumber: '3.1.0-pre.2', channel: 'BETA', gameVersion: '0.6.0-pre.2', date: '30 minutes ago', changelog: 'Latest Hytale prerelease preview. Stabilized menus, polished world loading, and one more pass on particles.' },
        { id: 'v8', versionNumber: '3.1.0-pre.1.1', channel: 'ALPHA', gameVersion: '0.6.0-pre.1.1', date: '1 hour ago', changelog: 'Hotfix preview for the next Hytale build. Focused on crash recovery and startup stability.' },
        { id: 'v7', versionNumber: '3.1.0-pre.1', channel: 'ALPHA', gameVersion: '0.6.0-pre.1', date: '2 hours ago', changelog: 'Early Hytale prerelease with the new rendering pipeline. Expect rough edges.' },
        { id: 'v6', versionNumber: '3.0.4', channel: 'RELEASE', gameVersion: '0.5.4', date: '12 hours ago', changelog: 'Hytale release branch update. Minor localization fixes and a few stability improvements.' },
        { id: 'v5', versionNumber: '3.0.3', channel: 'RELEASE', gameVersion: '0.5.3', date: '1 day ago', changelog: 'Compatibility update for Hytale 0.5.3. Added new dynamic lighting and UI polish.' },
        { id: 'v4', versionNumber: '3.0.2-beta', channel: 'BETA', gameVersion: '0.5.2', date: '1 week ago', changelog: 'Testing new durability mechanics against Hytale 0.5.2. Expect bugs.' },
        { id: 'v3', versionNumber: '3.0.1', channel: 'RELEASE', gameVersion: '0.5.1', date: '2 weeks ago', changelog: 'Added new elemental wand effects and fixed visual bugs with particle effects.' },
        { id: 'v2', versionNumber: '3.0.0', channel: 'RELEASE', gameVersion: '0.5.0', date: '1 month ago', changelog: 'Initial release of the expanded magic system for Hytale 0.5.0.' },
        { id: 'v1', versionNumber: '2.9.9', channel: 'RELEASE', gameVersion: '0.5.0-pre.9.2', date: '2 months ago', changelog: 'Final update for the old magic system before the Hytale prerelease branch changed over.' }
    ];

    useEffect(() => {
        if (!preferredGameVersion) return;
        setSelectedVersion(preferredGameVersion);
        setIsDropdownOpen(false);
    }, [preferredGameVersion, showPreReleaseGameVersions, showExperimental]);

    const currentVersions = allVersions.filter(v => v.gameVersion === selectedVersion);
    const visibleVersions = currentVersions.filter(v => showExperimental || v.channel === 'RELEASE');
    const latestVer = visibleVersions[0];
    const hasPreReleaseGameVersionEntries = preReleaseGameVersions.some(version => allVersions.some(v => v.gameVersion === version));
    const hasReleaseGameVersionEntries = orderedGameVersions.some(version => !preReleaseGameVersionSet.has(version) && allVersions.some(v => v.gameVersion === version));
    const forceShowPreReleaseGameVersions = hasPreReleaseGameVersionEntries && !hasReleaseGameVersionEntries;

    useEffect(() => {
        if (forceShowPreReleaseGameVersions && !showPreReleaseGameVersions) {
            setShowPreReleaseGameVersions(true);
        }
    }, [forceShowPreReleaseGameVersions, showPreReleaseGameVersions]);

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
            <div className={`${theme.components.modalContent} w-full overflow-hidden flex flex-col h-[380px] transform transition-transform duration-500`}>
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

                <div className={`${theme.components.modalBody} p-4 sm:p-5 overflow-y-auto flex-1 space-y-4 relative`}>
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
            <div className={`${theme.components.modalContent} w-full overflow-hidden relative flex flex-col h-[380px] transform transition-transform duration-500`}>
            <div className={`${theme.components.modalHeader} p-4 sm:p-5`}>
                <div>
                    <h3 className="text-base sm:text-lg font-black text-slate-900 dark:text-white flex items-center gap-2">
                        <Download className="w-4 h-4 sm:w-5 sm:h-5 text-modtale-accent" aria-hidden="true" /> Download
                    </h3>
                    {hasPreReleaseGameVersionEntries && hasReleaseGameVersionEntries && (
                        <div className="mt-1 flex items-center gap-2 cursor-pointer group" onClick={() => setShowPreReleaseGameVersions(!showPreReleaseGameVersions)}>
                            <div className={`w-7 sm:w-8 h-3.5 sm:h-4 rounded-full relative transition-colors shadow-[inset_0_1px_4px_rgba(0,0,0,0.2)] ${showPreReleaseGameVersions ? 'bg-modtale-accent' : 'bg-slate-300/80 dark:bg-slate-700/80'}`}>
                                <div className={`absolute top-[1px] sm:top-0.5 left-[1px] sm:left-0.5 w-3 h-3 bg-white rounded-full transition-transform shadow-sm border border-black/5 ${showPreReleaseGameVersions ? 'translate-x-3 sm:translate-x-4' : ''}`} />
                            </div>
                            <span className="text-[9px] sm:text-[10px] font-bold text-slate-600 dark:text-slate-400 uppercase group-hover:text-slate-800 dark:group-hover:text-slate-200 transition-colors">Show Pre-Release Game Versions</span>
                        </div>
                    )}
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
                                {gameVersions.map(v => (
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
                        <div className="font-black text-base sm:text-lg flex items-center gap-2 transition-transform z-10"><Download className="w-4 h-4 sm:w-5 sm:h-5" aria-hidden="true" /> Download Latest</div>
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
    <div className={`${GLASS_CARD} w-full flex flex-col h-[380px] transform transition-transform duration-500`}>
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

const resolveAssetUrl = (asset?: string | null) => {
    if (!asset) return null;
    return asset.startsWith('/api') ? `${BACKEND_URL}${asset}` : asset;
};

const PreviewPanel = ({
    icon: Icon,
    title,
    accentClass,
    children,
    className = '',
}: {
    icon: React.ComponentType<{ className?: string }>;
    title: string;
    accentClass: string;
    children: React.ReactNode;
    className?: string;
}) => (
    <div className={`${GLASS_CARD} ${className} flex flex-col`}>
        <div className={`${theme.components.modalHeader} p-4 sm:p-5`}>
            <h3 className={`font-bold ${theme.colors.textPrimary} flex items-center gap-2.5 text-base sm:text-lg`}>
                <Icon className={`w-4 h-4 sm:w-5 sm:h-5 ${accentClass}`} aria-hidden="true" />
                {title}
            </h3>
        </div>
        <div className="p-4 sm:p-5 flex-1">
            {children}
        </div>
    </div>
);

const PreviewSummaryCard = ({ title, value, subValue, trend, icon: Icon, color, isPercent }: any) => (
    <div className="bg-white/40 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/10 shadow-sm hover:shadow-md transition-all relative overflow-hidden group backdrop-blur-md flex flex-col justify-between p-4 sm:p-5">
        <div className={`absolute top-0 right-0 p-3 opacity-5 group-hover:opacity-10 transition-opacity ${color}`}>
            <Icon className="w-24 h-24 transform translate-x-4 -translate-y-4" />
        </div>
        <div className="relative z-10 flex items-start justify-between">
            <div className={`p-2.5 rounded-xl ${color} bg-opacity-10 text-current shadow-inner`}>
                <Icon className="w-4 h-4" />
            </div>
            {trend !== undefined && (
                <div className="flex items-center gap-0.5 text-[9px] font-black uppercase tracking-wider px-2 py-0.5 rounded-full border bg-green-50 border-green-200 text-green-700 dark:bg-green-500/10 dark:border-green-500/30 dark:text-green-400">
                    <TrendingUp className="w-3 h-3" />
                    {trend}%
                </div>
            )}
        </div>
        <div className="relative z-10 mt-3">
            <h3 className="text-slate-550 dark:text-slate-400 text-[9px] font-black uppercase tracking-widest mb-0.5">{title}</h3>
            <div className="text-2xl sm:text-3xl font-black text-slate-900 dark:text-white tracking-tighter leading-none">
                {value}{isPercent && <span className="text-lg text-slate-450 ml-0.5">%</span>}
            </div>
                                            {subValue && <div className="text-[10px] text-slate-500 dark:text-slate-400 mt-1.5 font-medium">{subValue}</div>}
        </div>
    </div>
);



const InlineAnalyticsUI = () => {
    const [hiddenDatasets, setHiddenDatasets] = useState<Set<string>>(new Set());
    const mockChartData = [
        { date: 'Jun 4', value: 1200 },
        { date: 'Jun 5', value: 1350 },
        { date: 'Jun 6', value: 1600 },
        { date: 'Jun 7', value: 1550 },
        { date: 'Jun 8', value: 1900 },
        { date: 'Jun 9', value: 2300 },
        { date: 'Jun 10', value: 2842 }
    ];
    const mockViewsData = [
        { date: 'Jun 4', value: 800 },
        { date: 'Jun 5', value: 950 },
        { date: 'Jun 6', value: 1100 },
        { date: 'Jun 7', value: 1050 },
        { date: 'Jun 8', value: 1300 },
        { date: 'Jun 9', value: 1700 },
        { date: 'Jun 10', value: 2000 }
    ];

    const handleToggle = (id: string) => {
        setHiddenDatasets(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const mockChartDatasets = [
        {
            id: 'downloads',
            label: 'Downloads',
            color: '#3b82f6',
            data: mockChartData,
            hidden: hiddenDatasets.has('downloads')
        },
        {
            id: 'views',
            label: 'Views',
            color: '#8b5cf6',
            data: mockViewsData,
            hidden: hiddenDatasets.has('views')
        }
    ];

    return (
        <div className={`${GLASS_CARD} w-full flex flex-col min-h-[460px] p-5 sm:p-6 transform transition-transform duration-500`}>
            <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                <PreviewSummaryCard
                    title="Downloads"
                    value="148,294"
                    subValue="Total: 984,201"
                    trend="12.4"
                    icon={Download}
                    color="text-blue-500"
                    className="p-0.5"
                />
                <PreviewSummaryCard
                    title="Views"
                    value="321,567"
                    subValue="Unique Visitors"
                    icon={Eye}
                    color="text-purple-500"
                    trend="8.1"
                    className="p-0.5"
                />
                <div className="hidden md:block">
                    <PreviewSummaryCard
                        title="Conversion Rate"
                        value="43.2"
                        subValue="Downloads per View"
                        icon={PieChart}
                        color="text-emerald-500"
                        isPercent={true}
                        className="p-0.5"
                    />
                </div>
            </div>
            <div className="bg-white/40 dark:bg-white/5 rounded-2xl border border-slate-200 dark:border-white/10 shadow-sm flex flex-col backdrop-blur-md p-4 sm:p-5 mt-4">
                <div className="h-[380px] w-full">
                    <LineChart datasets={mockChartDatasets} onToggle={handleToggle} />
                </div>
            </div>
        </div>
    );
};

const InlineCommentThreadUI = ({ project, currentUser }: { project?: Project; currentUser?: User | null }) => {
    const resolveAvatar = (url?: string | null) => {
        if (!url || url === 'null') return null;
        if (url.startsWith('http')) return url;
        return `${BACKEND_URL}${url.startsWith('/') ? '' : '/'}${url}`;
    };
    const userAvatar = resolveAvatar(currentUser?.avatarUrl);
    const userInitial = currentUser?.username?.charAt(0)?.toUpperCase() ?? 'Y';

    const [randomCommenter, setRandomCommenter] = useState<{ name: string; avatar: string } | null>(null);
    const [authorProfile, setAuthorProfile] = useState<User | null>(null);

    useEffect(() => {
        if (!project?.authorId) return;
        api.get(`/user/profile/${project.authorId}`)
            .then(res => {
                const u = res.data;
                setAuthorProfile(u);
                if (!u?.username) return;
                const raw: string = u.avatarUrl ?? '';
                const resolvedAvatar = raw.startsWith('http')
                    ? raw
                    : raw ? `${BACKEND_URL}${raw.startsWith('/') ? '' : '/'}${raw}` : '';
                setRandomCommenter({
                    name: u.displayName || u.username,
                    avatar: resolvedAvatar,
                });
            })
            .catch(() => { /* silently fail */ });
    }, [project?.authorId]);

    const commenterName = randomCommenter?.name ?? project?.author ?? '…';
    const commenterAvatar = randomCommenter?.avatar;
    const previewReplyUserId = project?.teamMembers?.[0]?.userId ?? project?.authorId;
    const previewReplyRoleBadge = getCommentRoleBadge(previewReplyUserId, project, authorProfile);

    return (
    <PreviewPanel icon={MessageSquare} title="Comments" accentClass="text-violet-600 dark:text-violet-400" className="min-h-[360px]">
        <div className="space-y-4">
            <div className="flex gap-3">
                <div className="bg-slate-50 dark:bg-black/20 rounded-xl border border-slate-200 dark:border-white/5 overflow-hidden shadow-sm flex-1 p-4 flex flex-col gap-3">
                    <div className="text-[11px] font-bold uppercase tracking-widest text-modtale-accent">Leave a comment</div>
                    <div className="flex items-start gap-3">
                        <div className="w-9 h-9 rounded-full overflow-hidden shrink-0 shadow-sm border border-slate-200 dark:border-white/5 bg-indigo-100 dark:bg-indigo-900/30 flex items-center justify-center font-bold text-indigo-600 dark:text-indigo-400 text-sm">
                            {userAvatar
                                ? <img src={userAvatar} alt={currentUser?.username ?? ''} className="w-full h-full object-cover" />
                                : userInitial
                            }
                        </div>
                        <div className="flex-1 text-sm text-slate-400 dark:text-slate-500 pt-1.5">
                            What are your thoughts on {project?.title || 'this project'}?
                        </div>
                    </div>
                    <div className="flex justify-end pt-1">
                        <button type="button" className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-4 py-1.5 rounded-lg font-bold flex items-center gap-1.5 text-xs shadow-md">
                            <Send className="w-3.5 h-3.5" aria-hidden="true" />
                            Post Comment
                        </button>
                    </div>
                </div>
            </div>

            <div className="p-4 bg-slate-50 dark:bg-white/[0.02] rounded-xl border border-slate-200 dark:border-white/5 shadow-sm group relative flex gap-3">
                <div className="flex flex-col items-center shrink-0 mt-1">
                    <button type="button" className="p-1.5 rounded-lg hover:bg-slate-200 dark:hover:bg-white/10 transition-colors text-modtale-accent">
                        <ArrowBigUp className="w-6 h-6 fill-current" />
                    </button>
                    <span className="text-sm font-black min-w-[1.5rem] text-center text-modtale-accent">+12</span>
                    <button type="button" className="p-1.5 rounded-lg hover:bg-slate-200 dark:hover:bg-white/10 transition-colors text-slate-400">
                        <ArrowBigDown className="w-6 h-6" />
                    </button>
                </div>

                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-3 mb-2">
                        <div className="w-10 h-10 rounded-full overflow-hidden shadow-sm border border-slate-200 dark:border-white/5 shrink-0 bg-slate-200 dark:bg-slate-700">
                            {commenterAvatar
                                ? <img src={commenterAvatar} alt={commenterName} className="w-full h-full object-cover" loading="lazy" />
                                : <span className="w-full h-full flex items-center justify-center text-sm font-bold text-slate-500">{commenterName.charAt(0)}</span>
                            }
                        </div>
                        <div className="flex flex-col">
                            <span className="font-bold text-sm text-slate-900 dark:text-white">{commenterName}</span>
                            <span className="text-xs font-medium text-slate-500 dark:text-slate-400">2 hours ago</span>
                        </div>
                    </div>

                    <p className="text-sm text-slate-700 dark:text-slate-300 leading-relaxed">
                        The latest release fixed the dedicated server crash and made setup much smoother.
                    </p>

                    <div className="mt-3 flex gap-3 relative">
                        <div className="absolute -left-[1.75rem] top-0 bottom-4 w-px bg-slate-200 dark:bg-white/10" />
                        <div className="absolute -left-[1.75rem] top-4 w-4 h-px bg-slate-200 dark:bg-white/10" />

                        <div className="flex flex-col items-center shrink-0 mt-1">
                            <button type="button" className="p-1 rounded-lg hover:bg-slate-200 dark:hover:bg-white/10 transition-colors text-slate-400">
                                <ArrowBigUp className="w-5 h-5" />
                            </button>
                            <span className="text-xs font-black min-w-[1.25rem] text-center text-slate-500">+5</span>
                            <button type="button" className="p-1 rounded-lg hover:bg-slate-200 dark:hover:bg-white/10 transition-colors text-slate-400">
                                <ArrowBigDown className="w-5 h-5" />
                            </button>
                        </div>

                        <div className="flex-1 min-w-0 bg-modtale-accent/5 dark:bg-modtale-accent/[0.02] rounded-2xl p-4 border border-modtale-accent/10 dark:border-modtale-accent/20">
                            <div className="flex items-center gap-3 mb-2">
                        <div className="w-8 h-8 rounded-full overflow-hidden shadow-sm border border-slate-200 dark:border-white/5 shrink-0">
                                    <img src="https://cdn.modtale.net/avatars/AzureDoom/83c01443-6302-4aff-beb9-7d6f656f994c-cropped.png" alt="AzureDoom" className="w-full h-full object-cover" loading="lazy" />
                                </div>
                                <div className="flex flex-col">
                                    <span className="font-bold text-sm text-slate-900 dark:text-white flex items-center gap-1.5">
                                        AzureDoom
                                    </span>
                                    {previewReplyRoleBadge && (
                                        <span
                                            className="w-fit text-[9px] px-1.5 py-0.5 rounded font-black uppercase tracking-widest border"
                                            style={{
                                                color: previewReplyRoleBadge.color,
                                                backgroundColor: `${previewReplyRoleBadge.color}1A`,
                                                borderColor: `${previewReplyRoleBadge.color}33`
                                            }}
                                        >
                                            {previewReplyRoleBadge.label}
                                        </span>
                                    )}
                                    <span className="text-xs font-medium text-slate-500 dark:text-slate-400">1 hour ago</span>
                                </div>
                            </div>
                            <p className="text-sm text-slate-700 dark:text-slate-200 leading-relaxed">
                                Thanks. We tightened the dependency check for older installs in this build too.
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </PreviewPanel>
    );
};

const NotificationSettingRow = ({ label, enabled }: { label: string; enabled: boolean }) => (
    <div className="flex items-center justify-between gap-3 p-4 border-b border-slate-200 dark:border-white/10 last:border-0">
        <div className="min-w-0">
            <div className="text-sm font-bold text-slate-900 dark:text-white truncate">{label}</div>
            <div className="text-xs text-slate-500 dark:text-slate-400">Quick toggle from dashboard settings.</div>
        </div>
        <div className="relative flex bg-white/60 dark:bg-black/20 border border-slate-200 dark:border-white/10 p-1 rounded-xl shadow-inner shrink-0 w-fit">
            <div
                className={`absolute top-1 bottom-1 w-14 rounded-lg transition-all duration-300 ease-out shadow-sm ${
                    enabled ? 'bg-modtale-accent shadow-modtale-accent/30' : 'bg-white dark:bg-slate-700 border border-slate-200 dark:border-white/10'
                }`}
                style={{ transform: `translateX(${enabled ? 100 : 0}%)` }}
            />
            <span className={`relative z-10 w-14 py-1.5 text-[11px] font-bold text-center ${enabled ? 'text-slate-500 dark:text-slate-400' : 'text-slate-900 dark:text-white'}`}>Off</span>
            <span className={`relative z-10 w-14 py-1.5 text-[11px] font-bold text-center ${enabled ? 'text-white' : 'text-slate-500 dark:text-slate-400'}`}>On</span>
        </div>
    </div>
);

const InlineNotificationSettingsUI = () => (
    <PreviewPanel icon={Bell} title="Notification Settings" accentClass="text-amber-600 dark:text-amber-400" className="min-h-[360px]">
        <div className="flex flex-col h-full">
            <div className="rounded-2xl border border-slate-200 dark:border-white/10 bg-white/50 dark:bg-white/5 overflow-hidden shadow-sm">
                <NotificationSettingRow label="Favorite Project Updates" enabled={true} />
                <NotificationSettingRow label="New Creator Uploads" enabled={true} />
                <NotificationSettingRow label="Dependency Updates" enabled={false} />
            </div>
            <div className="mt-auto pt-4 flex justify-end">
                <button type="button" className="bg-modtale-accent text-white px-5 py-2.5 rounded-xl font-bold shadow-lg shadow-modtale-accent/20 flex items-center gap-2 text-sm">
                    <Save className="w-4 h-4" aria-hidden="true" />
                    Save Changes
                </button>
            </div>
        </div>
    </PreviewPanel>
);

const CompactFeaturedModCard = ({ project }: { project: Project }) => {
    const iconUrl = project.imageUrl
        ? (project.imageUrl.startsWith('/api') ? `${BACKEND_URL}${project.imageUrl}` : project.imageUrl)
        : '/assets/favicon.svg';

    const bannerUrl = project.bannerUrl
        ? (project.bannerUrl.startsWith('/api') ? `${BACKEND_URL}${project.bannerUrl}` : project.bannerUrl)
        : null;

    const projectUrl = SiteRoutes.project(project);

    return (
        <article className="group relative flex flex-col w-full shrink-0 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl overflow-hidden isolate hover:-translate-y-1 transition-all duration-300 shadow-sm hover:shadow-md hover:ring-2 hover:ring-blue-500 dark:hover:ring-blue-400 hover:border-transparent h-full">
            <Link
                to={projectUrl}
                className="absolute inset-0 z-30 focus:outline-none"
                aria-label={`Download ${project.title} Hytale Mod`}
            />

            <div className={`w-full aspect-[2.8/1] relative border-b border-slate-100 dark:border-white/5 overflow-hidden shrink-0 ${bannerUrl ? 'bg-transparent' : 'bg-slate-200 dark:bg-slate-800'}`}>
                {bannerUrl ? (
                    <img
                        src={bannerUrl}
                        alt={`${project.title} Banner`}
                        loading="lazy"
                        className="w-full h-full opacity-80 group-hover:opacity-100 group-hover:scale-105 transition-all duration-500 bg-transparent object-cover"
                    />
                ) : (
                    <div className="absolute inset-0 bg-gradient-to-t from-white via-white/20 dark:from-slate-900 dark:via-slate-900/20 to-transparent pointer-events-none" />
                )}
            </div>

            <div className="px-3 pb-3 relative flex flex-col flex-1 bg-transparent">
                <div className="w-10 h-10 rounded-lg absolute -top-5 left-3 group-hover:-translate-y-0.5 transition-transform duration-300 z-20 overflow-hidden border-2 border-white dark:border-slate-800 shadow-md bg-white dark:bg-slate-950">
                    <img
                        src={iconUrl}
                        alt={`${project.title} Icon`}
                        loading="lazy"
                        className="w-full h-full bg-transparent object-cover"
                    />
                </div>

                <div className="mt-6 flex-1 relative z-20 pointer-events-none">
                    <h3 className="text-xs sm:text-sm font-black text-slate-900 dark:text-white group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors truncate tracking-tight">
                        {project.title}
                    </h3>
                    <div className="flex items-center gap-1 text-[10px] text-slate-500 dark:text-slate-400 font-medium truncate mt-0.5">
                        <span>By</span>
                        <span className="font-bold">{project.author}</span>
                    </div>
                </div>

                <div className="mt-2 flex items-center gap-1.5 relative z-20 pointer-events-none text-slate-400 dark:text-slate-500 uppercase tracking-widest font-bold text-[9px]">
                    <Download className="w-3 h-3 shrink-0" aria-hidden="true" />
                    <span className="leading-none translate-y-[0.5px]">{project.downloadCount?.toLocaleString() || 0}</span>
                </div>
            </div>
        </article>
    );
};

const ScrollContainer = ({ children }: { children: React.ReactNode }) => {
    const containerRef = useRef<HTMLDivElement>(null);
    const [showLeftFade, setShowLeftFade] = useState(false);
    const [showRightFade, setShowRightFade] = useState(false);

    const handleScroll = () => {
        const el = containerRef.current;
        if (!el) return;
        
        const { scrollLeft, scrollWidth, clientWidth } = el;
        setShowLeftFade(scrollLeft > 1);
        setShowRightFade(Math.ceil(scrollLeft + clientWidth) < scrollWidth - 1);
    };

    useEffect(() => {
        const el = containerRef.current;
        if (!el) return;
        
        handleScroll();
        
        const resizeObserver = new ResizeObserver(() => handleScroll());
        resizeObserver.observe(el);
        
        return () => resizeObserver.disconnect();
    }, []);

    return (
        <div className="relative w-full overflow-hidden">
            <div className={`absolute left-0 top-0 bottom-0 w-8 bg-gradient-to-r from-[#0b1220]/92 via-[#0b1220]/76 to-transparent pointer-events-none z-30 transition-opacity duration-300 ${showLeftFade ? 'opacity-100' : 'opacity-0'}`} />
            
            <div className={`absolute right-0 top-0 bottom-0 w-12 bg-gradient-to-l from-[#0b1220]/92 via-[#0b1220]/76 to-transparent pointer-events-none z-30 transition-opacity duration-300 ${showRightFade ? 'opacity-100' : 'opacity-0'}`} />
            
            <div 
                ref={containerRef}
                onScroll={handleScroll}
                className="flex gap-4 overflow-x-auto pb-4 pt-2 scrollbar-none snap-x snap-mandatory w-full"
            >
                {children}
            </div>
        </div>
    );
};

export const TrendingProjectsSection = ({
    projects,
    likedProjectIds = [],
    onToggleFavorite = () => {},
    isLoggedIn = false
}: {
    projects: Project[];
    likedProjectIds?: string[];
    onToggleFavorite?: (projectId: string) => void;
    isLoggedIn?: boolean;
}) => {
    if (projects.length === 0) return null;
    return (
        <section className="space-y-6">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-end pb-0 gap-4 relative">
                <div className="space-y-1 flex-1">
                    <h2 className="text-2xl sm:text-3xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">
                        Trending
                    </h2>
                </div>
                <Link
                    to={`${SiteRoutes.browse()}?view=trending`}
                    className="text-xs font-bold text-slate-550 hover:text-blue-600 dark:text-slate-400 dark:hover:text-blue-400 transition-colors flex items-center gap-0.5 shrink-0 pb-1.5"
                >
                    Browse All
                    <ArrowUpRight className="w-3.5 h-3.5" />
                </Link>
            </div>
            
            <ScrollContainer>
                {projects.map((project) => (
                    <div key={project.id} className="w-[280px] sm:w-[320px] shrink-0 snap-start">
                        <ProjectCard
                            project={project}
                            isFavorite={likedProjectIds.includes(project.id)}
                            onToggleFavorite={onToggleFavorite}
                            isLoggedIn={isLoggedIn}
                            viewStyle="grid"
                        />
                    </div>
                ))}
            </ScrollContainer>
        </section>
    );
};

export const NewReleasesSection = ({
    projects,
    likedProjectIds = [],
    onToggleFavorite = () => {},
    isLoggedIn = false
}: {
    projects: Project[];
    likedProjectIds?: string[];
    onToggleFavorite?: (projectId: string) => void;
    isLoggedIn?: boolean;
}) => {
    if (projects.length === 0) return null;
    return (
        <section className="space-y-6">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-end pb-0 gap-4 relative">
                <div className="space-y-1 flex-1">
                    <h2 className="text-2xl sm:text-3xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">
                        New Releases
                    </h2>
                </div>
                <Link
                    to={`${SiteRoutes.browse()}?sort=newest`}
                    className="text-xs font-bold text-slate-550 hover:text-emerald-600 dark:text-slate-400 dark:hover:text-emerald-400 transition-colors flex items-center gap-0.5 shrink-0 pb-1.5"
                >
                    Browse All
                    <ArrowUpRight className="w-3.5 h-3.5" />
                </Link>
            </div>
            
            <ScrollContainer>
                {projects.map((project) => (
                    <div key={project.id} className="w-[280px] sm:w-[320px] shrink-0 snap-start">
                        <ProjectCard
                            project={project}
                            isFavorite={likedProjectIds.includes(project.id)}
                            onToggleFavorite={onToggleFavorite}
                            isLoggedIn={isLoggedIn}
                            viewStyle="grid"
                        />
                    </div>
                ))}
            </ScrollContainer>
        </section>
    );
};

export const DirectDownloadsSection = () => {
    return (
        <div className="flex flex-col lg:flex-row items-center gap-12 lg:gap-16 2xl:gap-24">
            <div className="flex-1 space-y-5 flex flex-col items-center text-center lg:items-start lg:text-left">
                <h2 className="text-4xl sm:text-5xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">
                    Direct Downloads
                </h2>
                <p className="text-lg sm:text-xl font-semibold text-transparent bg-clip-text bg-gradient-to-r from-purple-500 to-pink-500 dark:from-purple-400 dark:to-pink-400">
                    Versioned builds & changelogs.
                </p>
                <p className="text-lg sm:text-xl text-slate-500 dark:text-slate-400 font-medium leading-relaxed max-w-xl">
                    Finding the right file shouldn't be a puzzle. Modtale makes it easy to find projects for your game version and review changelogs before you hit download.
                </p>
            </div>
            <div className="flex-1 w-full max-w-xl relative overflow-visible">
                <div className="absolute inset-0 bg-gradient-to-tr from-purple-500/5 via-transparent to-pink-500/5 dark:from-purple-500/10 dark:via-transparent dark:to-pink-500/10 rounded-3xl blur-2xl pointer-events-none" />
                <InlineDownloadUI />
            </div>
        </div>
    );
};

export const SmartDependenciesSection = ({ randomProject }: { randomProject?: Project }) => {
    return (
        <div className="flex flex-col lg:flex-row-reverse items-center gap-12 lg:gap-16 2xl:gap-24">
            <div className="flex-1 space-y-5 flex flex-col items-center text-center lg:items-end lg:text-right">
                <h2 className="text-4xl sm:text-5xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">
                    Smart Dependencies
                </h2>
                <p className="text-lg sm:text-xl font-semibold text-transparent bg-clip-text bg-gradient-to-r from-emerald-500 to-teal-500 dark:from-emerald-400 dark:to-teal-400">
                    Automated library resolution.
                </p>
                <p className="text-lg sm:text-xl text-slate-500 dark:text-slate-400 font-medium leading-relaxed max-w-xl">
                    Forget hunting down core libraries or confusing projectpacks. Modtale allows you to seamlessly download all required projects in one swift action.
                </p>
            </div>
            <div className="flex-1 w-full max-w-xl relative overflow-visible">
                <div className="absolute inset-0 bg-gradient-to-tr from-emerald-500/5 via-transparent to-teal-500/5 dark:from-emerald-500/10 dark:via-transparent dark:to-teal-500/10 rounded-3xl blur-2xl pointer-events-none" />
                <InlineDependencyUI randomProject={randomProject} />
            </div>
        </div>
    );
};

export const ProjectAnalyticsSection = () => {
    return (
        <div className="flex flex-col lg:flex-row items-center gap-12 lg:gap-16 2xl:gap-24">
            <div className="flex-1 space-y-5 flex flex-col items-center text-center lg:items-start lg:text-left">
                <h2 className="text-4xl sm:text-5xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">
                    Project Analytics
                </h2>
                <p className="text-lg sm:text-xl font-semibold text-transparent bg-clip-text bg-gradient-to-r from-blue-500 to-indigo-500 dark:from-blue-400 dark:to-indigo-400">
                    Track growth, downloads, and views over time.
                </p>
                <p className="text-lg sm:text-xl text-slate-500 dark:text-slate-400 font-medium leading-relaxed max-w-xl">
                    Keep tabs on how your Hytale uploads perform in real-time. Review clean graphs, growth percentages, and historical metrics in your developer hub.
                </p>
            </div>
            <div className="flex-1 w-full max-w-xl relative overflow-visible">
                <div className="absolute inset-0 bg-gradient-to-tr from-blue-500/5 via-transparent to-indigo-500/5 dark:from-blue-500/10 dark:via-transparent dark:to-indigo-500/10 rounded-3xl blur-2xl pointer-events-none" />
                <InlineAnalyticsUI />
            </div>
        </div>
    );
};

export const CommunityThreadsSection = ({ project, currentUser }: { project?: Project; currentUser?: User | null }) => {
    return (
        <div className="flex flex-col lg:flex-row-reverse items-center gap-12 lg:gap-16 2xl:gap-24">
            <div className="flex-1 space-y-5 flex flex-col items-center text-center lg:items-end lg:text-right">
                <h2 className="text-4xl sm:text-5xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">
                    Comment Threads
                </h2>
                <p className="text-lg sm:text-xl font-semibold text-transparent bg-clip-text bg-gradient-to-r from-violet-500 to-purple-500 dark:from-violet-400 dark:to-purple-400">
                    Engage and share feedback.
                </p>
                <p className="text-lg sm:text-xl text-slate-500 dark:text-slate-400 font-medium leading-relaxed max-w-xl">
                    Provide direct feedback, report immediate bugs, or collaborate with the creator team through nested, upvotable community comment sections.
                </p>
            </div>
            <div className="flex-1 w-full max-w-2xl relative overflow-visible">
                <div className="absolute inset-0 bg-gradient-to-tr from-indigo-500/5 via-transparent to-purple-500/5 dark:from-indigo-500/10 dark:via-transparent dark:to-purple-500/10 rounded-3xl blur-2xl pointer-events-none" />
                <InlineCommentThreadUI project={project} currentUser={currentUser} />
            </div>
        </div>
    );
};

export const RealTimeAlertsSection = () => {
    return (
        <div className="flex flex-col lg:flex-row items-center gap-12 lg:gap-16 2xl:gap-24">
            <div className="flex-1 space-y-5 flex flex-col items-center text-center lg:items-start lg:text-left">
                <h2 className="text-4xl sm:text-5xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">
                    Push Notifications
                </h2>
                <p className="text-lg sm:text-xl font-semibold text-transparent bg-clip-text bg-gradient-to-r from-amber-500 to-orange-500 dark:from-amber-400 dark:to-orange-400">
                    Real-time updates on activity.
                </p>
                <p className="text-lg sm:text-xl text-slate-500 dark:text-slate-400 font-medium leading-relaxed max-w-xl">
                    Never miss a crucial patch or follow-up reply. Real-time platform notifications let you track your favorite projects and active discussions instantly.
                </p>
            </div>
            <div className="flex-1 w-full max-w-xl relative overflow-visible">
                <div className="absolute inset-0 bg-gradient-to-tr from-amber-500/5 via-transparent to-orange-500/5 dark:from-amber-500/10 dark:via-transparent dark:to-orange-500/10 rounded-3xl blur-2xl pointer-events-none" />
                <InlineNotificationUI />
            </div>
        </div>
    );
};

export const AccountPreferencesSection = () => {
    return (
        <div className="flex flex-col lg:flex-row-reverse items-center gap-12 lg:gap-16 2xl:gap-24">
            <div className="flex-1 space-y-5 flex flex-col items-center text-center lg:items-end lg:text-right">
                <h2 className="text-4xl sm:text-5xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">
                    Notification Control
                </h2>
                <p className="text-lg sm:text-xl font-semibold text-transparent bg-clip-text bg-gradient-to-r from-slate-500 to-slate-400 dark:from-slate-400 dark:to-slate-500">
                    Tailored settings for alerts.
                </p>
                <p className="text-lg sm:text-xl text-slate-500 dark:text-slate-400 font-medium leading-relaxed max-w-xl">
                    Adjust toggles to choose exactly which events trigger notifications.
                </p>
            </div>
            <div className="flex-1 w-full max-w-xl relative overflow-visible">
                <div className="absolute inset-0 bg-gradient-to-tr from-slate-500/5 via-transparent to-slate-400/5 dark:from-slate-500/10 dark:via-transparent dark:to-slate-400/10 rounded-3xl blur-2xl pointer-events-none" />
                <InlineNotificationSettingsUI />
            </div>
        </div>
    );
};
