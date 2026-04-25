import React, { useState, useEffect, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import {
    Search, Upload, ChevronRight, Check,
    AlertCircle, Download, Link as LinkIcon, Bell,
    Box, ChevronDown, Github, Code, X, List, TrendingUp
} from 'lucide-react';
import { LineChart } from '../components/ui/charts/LineChart';
import { OptimizedImage } from '../components/ui/OptimizedImage';
import type { Mod, User } from '../types';
import { api, BACKEND_URL } from '../utils/api';
import { getProjectUrl } from '../utils/slug';

const GLASS_CARD = "bg-white/95 dark:bg-slate-900/95 backdrop-blur-xl border border-slate-200 dark:border-white/10 shadow-xl rounded-3xl overflow-hidden ring-1 ring-black/[0.02] dark:ring-white/[0.02]";
const GLASS_HEADER = "bg-slate-50 dark:bg-slate-800/95 border-b border-slate-200 dark:border-white/10";
const GLASS_ITEM = "bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 shadow-sm hover:border-blue-400 dark:hover:border-blue-500 transition-all duration-300";

const getInitialData = () => {
    if (typeof window !== 'undefined' && (window as any).INITIAL_DATA) {
        return (window as any).INITIAL_DATA;
    }
    return null;
};

const AnimatedCounter = ({ value }: { value: number }) => {
    const [count, setCount] = useState(0);

    useEffect(() => {
        let startTimestamp: number | null = null;
        const duration = 2000;

        const step = (timestamp: number) => {
            if (!startTimestamp) startTimestamp = timestamp;
            const progress = Math.min((timestamp - startTimestamp) / duration, 1);
            const ease = 1 - Math.pow(1 - progress, 4);
            setCount(Math.floor(ease * value));

            if (progress < 1) {
                window.requestAnimationFrame(step);
            } else {
                setCount(value);
            }
        };

        if (value > 0) {
            window.requestAnimationFrame(step);
        } else {
            setCount(0);
        }
    }, [value]);

    return <>{count.toLocaleString()}</>;
};

const FeaturedModCard = ({ mod, priority = false }: { mod: Mod, priority?: boolean }) => {
    const iconUrl = mod.imageUrl
        ? (mod.imageUrl.startsWith('/api') ? `${BACKEND_URL}${mod.imageUrl}` : mod.imageUrl)
        : '/assets/favicon.svg';

    const bannerUrl = mod.bannerUrl
        ? (mod.bannerUrl.startsWith('/api') ? `${BACKEND_URL}${mod.bannerUrl}` : mod.bannerUrl)
        : null;

    const projectUrl = getProjectUrl(mod);

    return (
        <article className="group relative flex flex-col w-full shrink-0 bg-white dark:bg-slate-900 border border-slate-300 dark:border-white/20 rounded-2xl overflow-hidden isolate hover:-translate-y-1.5 transition-all duration-500 shadow-lg hover:shadow-2xl dark:shadow-xl hover:ring-[3px] hover:ring-blue-600 dark:hover:ring-blue-500 hover:border-transparent">
            <Link
                to={projectUrl}
                className="absolute inset-0 z-30 focus:outline-none"
                aria-label={`Download ${mod.title} Hytale Mod`}
            />

            <div className={`w-full aspect-[3/1] relative border-b border-slate-100 dark:border-white/5 rounded-t-2xl overflow-hidden shrink-0 ${bannerUrl ? 'bg-transparent' : 'bg-slate-200 dark:bg-slate-800'}`}>
                {bannerUrl ? (
                    <OptimizedImage
                        src={bannerUrl}
                        alt={`${mod.title} Banner`}
                        baseWidth={400}
                        priority={priority}
                        className="w-full h-full opacity-80 group-hover:opacity-100 group-hover:scale-105 transition-all duration-700 bg-transparent"
                    />
                ) : (
                    <div className="absolute inset-0 bg-gradient-to-t from-white via-white/20 dark:from-slate-900 dark:via-slate-900/20 to-transparent pointer-events-none" />
                )}
            </div>

            <div className="px-6 pb-6 relative flex flex-col flex-1 bg-transparent">
                <div className="w-16 h-16 rounded-2xl absolute -top-8 group-hover:-translate-y-1 transition-transform duration-500 z-20 overflow-hidden border-4 border-white dark:border-slate-800 shadow-xl bg-transparent backdrop-blur-md">
                    <OptimizedImage
                        src={iconUrl}
                        alt={`${mod.title} Icon`}
                        baseWidth={64}
                        priority={priority}
                        className="w-full h-full bg-transparent"
                    />
                </div>

                <div className="mt-10 flex-1 relative z-20">
                    <h3 className="text-xl font-black text-slate-900 dark:text-white group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors truncate tracking-tight">
                        {mod.title}
                    </h3>
                    <div className="flex items-center gap-1 text-sm text-slate-500 dark:text-slate-400 font-medium truncate mt-1">
                        <span>By</span>
                        <Link
                            to={`/creator/${mod.author}`}
                            className="hover:text-blue-600 dark:hover:text-blue-400 hover:underline focus:outline-none relative z-40"
                            aria-label={`View profile for ${mod.author}`}
                            onClick={(e) => e.stopPropagation()}
                        >
                            {mod.author}
                        </Link>
                    </div>
                </div>

                <div className="mt-4 flex items-center gap-2 relative z-20 text-slate-500 dark:text-slate-400 uppercase tracking-widest font-bold">
                    <Download className="w-4 h-4 shrink-0" aria-hidden="true" />
                    <span className="text-[13px] leading-none translate-y-[1px]">{mod.downloadCount?.toLocaleString() || 0}</span>
                </div>
            </div>
        </article>
    );
};

const MarqueeColumn = ({ mods, duration }: { mods: Mod[], duration: string }) => (
    <div className="flex flex-col w-[260px] 2xl:w-[320px] shrink-0">
        <div className="flex flex-col gap-6 animate-marquee-up will-change-transform" style={{ '--marquee-duration': duration } as any}>
            {[...mods, ...mods].map((mod, index) => (
                <FeaturedModCard key={`${mod.id}-${index}`} mod={mod} priority={index < 2} />
            ))}
        </div>
    </div>
);

const InlineDependencyUI = ({ randomMod }: { randomMod?: Mod }) => {
    const randomIconUrl = randomMod?.imageUrl
        ? (randomMod.imageUrl.startsWith('/api') ? `${BACKEND_URL}${randomMod.imageUrl}` : randomMod.imageUrl)
        : null;

    const randomVersion = randomMod?.versions?.[0]?.versionNumber || '1.0.0';

    return (
        <div className={`${GLASS_CARD} w-full flex flex-col min-h-[420px] transform transition-transform hover:scale-[1.02] duration-500`}>
            <div className={`p-6 flex justify-between items-center ${GLASS_HEADER}`}>
                <h3 className="font-bold text-slate-900 dark:text-white flex items-center gap-2.5 text-lg">
                    <LinkIcon className="w-5 h-5 text-emerald-500" aria-hidden="true" /> Dependencies
                </h3>
            </div>
            <div className="p-6 space-y-4 overflow-hidden relative flex-1 flex flex-col">
                <div className={`flex items-center justify-between p-4 rounded-2xl border-emerald-500/20 bg-emerald-50/50 dark:bg-emerald-500/10 border shadow-sm transition-all hover:bg-emerald-50 dark:hover:bg-emerald-500/20`}>
                    <div className="flex items-center gap-4">
                        <div className="w-6 h-6 rounded-full bg-emerald-500 text-white flex items-center justify-center shrink-0 shadow-md">
                            <Check className="w-3.5 h-3.5" aria-hidden="true" />
                        </div>
                        <div className="w-12 h-12 rounded-xl bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 flex items-center justify-center shrink-0 overflow-hidden shadow-sm">
                            <img src="/assets/favicon.svg" alt="Hytale Core Library Icon" className="w-full h-full object-cover p-2" loading="lazy" />
                        </div>
                        <div>
                            <div className="font-bold text-slate-900 dark:text-white">Hytale Core Library</div>
                            <div className="text-xs text-slate-500 dark:text-slate-400 font-mono mt-1">v1.2.0</div>
                        </div>
                    </div>
                    <span className="text-[10px] font-bold uppercase bg-emerald-100 dark:bg-emerald-500/20 text-emerald-700 dark:text-emerald-300 px-2.5 py-1 rounded-md border border-emerald-200 dark:border-emerald-500/30">Required</span>
                </div>

                <div className={`flex items-center justify-between p-4 rounded-2xl border-rose-500/20 bg-rose-50/30 dark:bg-rose-500/5 border shadow-sm transition-all hover:bg-rose-50 dark:hover:bg-rose-500/10 cursor-pointer`}>
                    <div className="flex items-center gap-4">
                        <div className="w-6 h-6 rounded-full border-2 border-slate-300 dark:border-slate-600 bg-white/50 dark:bg-slate-800/50 flex items-center justify-center shrink-0 shadow-sm" />
                        <div className="w-12 h-12 rounded-xl bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 flex items-center justify-center shrink-0 overflow-hidden shadow-sm">
                            <Box className="w-6 h-6 text-slate-400" />
                        </div>
                        <div>
                            <div className="font-bold text-slate-900 dark:text-white">MathLib</div>
                            <div className="text-xs text-slate-500 dark:text-slate-400 font-mono mt-1">v2.1.0</div>
                        </div>
                    </div>
                    <span className="text-[10px] font-bold uppercase bg-rose-100 dark:bg-rose-500/20 text-rose-700 dark:text-rose-300 px-2.5 py-1 rounded-md border border-rose-200 dark:border-rose-500/30">Required</span>
                </div>

                {randomMod ? (
                    <div className={`flex items-center justify-between p-4 rounded-2xl ${GLASS_ITEM}`}>
                        <div className="flex items-center gap-4">
                            <div className="w-6 h-6 rounded-full border-2 border-slate-300 dark:border-slate-600 bg-white/50 dark:bg-slate-800/50 shrink-0" />
                            <div className="w-12 h-12 rounded-xl overflow-hidden shadow-sm border border-slate-200 dark:border-white/10 shrink-0">
                                {randomIconUrl ? (
                                    <OptimizedImage
                                        src={randomIconUrl}
                                        alt={`${randomMod.title} Icon`}
                                        baseWidth={48}
                                        className="w-full h-full"
                                    />
                                ) : <Box className="w-6 h-6 text-slate-400" />}
                            </div>
                            <div className="min-w-0">
                                <div className="font-bold text-slate-900 dark:text-white truncate">{randomMod.title}</div>
                                <div className="text-xs text-slate-500 dark:text-slate-400 font-mono mt-1">v{randomVersion}</div>
                            </div>
                        </div>
                        <span className="text-[10px] font-bold uppercase bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 px-2.5 py-1 rounded-md border border-slate-200 dark:border-white/10 shrink-0 ml-2">Optional</span>
                    </div>
                ) : (
                    <div className="h-20 w-full animate-pulse bg-slate-100/50 dark:bg-slate-800/50 rounded-2xl border border-slate-200/50 dark:border-white/5" />
                )}

                <div className="mt-auto flex items-start gap-3 text-sm text-amber-800 dark:text-amber-300 bg-amber-50 dark:bg-amber-500/10 p-4 rounded-2xl border border-amber-200 dark:border-amber-500/20 shadow-sm">
                    <AlertCircle className="w-5 h-5 shrink-0" aria-hidden="true" />
                    <p className="font-medium">Some <span className="font-black">Required</span> dependencies are currently unselected.</p>
                </div>
            </div>
        </div>
    );
};

const InlineDownloadUI = () => {
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
            <div className={`${GLASS_CARD} w-full overflow-hidden flex flex-col h-[380px] transform transition-transform hover:scale-[1.02] duration-500`}>
                <div className={`p-5 flex justify-between items-center shrink-0 ${GLASS_HEADER}`}>
                    <div>
                        <h3 className="text-lg font-black text-slate-900 dark:text-white flex items-center gap-2"><List className="w-5 h-5 text-modtale-accent" aria-hidden="true" /> Changelog</h3>
                        <div className="mt-1 flex items-center gap-2 cursor-pointer group" onClick={() => setShowExperimental(!showExperimental)}>
                            <div className={`w-8 h-4 rounded-full relative transition-colors shadow-[inset_0_1px_4px_rgba(0,0,0,0.2)] ${showExperimental ? 'bg-modtale-accent' : 'bg-slate-300/80 dark:bg-slate-700/80'}`}>
                                <div className={`absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform shadow-sm border border-black/5 ${showExperimental ? 'translate-x-4' : ''}`} />
                            </div>
                            <span className="text-[10px] font-bold text-slate-600 dark:text-slate-400 uppercase group-hover:text-slate-800 dark:group-hover:text-slate-200 transition-colors">Show Beta/Alpha</span>
                        </div>
                    </div>
                    <button onClick={() => setView('download')} aria-label="Close Changelog" className="p-2 rounded-full hover:bg-white/40 dark:hover:bg-white/10 text-slate-600 dark:text-slate-400 transition-colors backdrop-blur-md"><X className="w-5 h-5" /></button>
                </div>

                <div className="p-5 overflow-y-auto custom-scrollbar flex-1 space-y-4 relative">
                    {visibleVersions.length > 0 ? (
                        visibleVersions.map(ver => (
                            <div key={ver.id} className={`${GLASS_ITEM} rounded-xl p-4 hover:border-modtale-accent/40 transition-colors`}>
                                <div className="flex items-center justify-between gap-4 mb-3 border-b border-white/40 dark:border-white/10 pb-3">
                                    <div>
                                        <div className="flex items-center gap-2 mb-1">
                                            <span className="text-base font-black text-slate-900 dark:text-white">v{ver.versionNumber}</span>
                                            {ver.channel !== 'RELEASE' && <span className={`text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded border backdrop-blur-md shadow-sm ${getVersionBadgeColor(ver.channel)}`}>{ver.channel}</span>}
                                        </div>
                                        <div className="flex items-center gap-2 text-[10px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wide">
                                            <span>{ver.date}</span>
                                            <span className="w-1 h-1 rounded-full bg-slate-400/50 dark:bg-slate-600/50"></span>
                                            <span>{ver.gameVersion}</span>
                                        </div>
                                    </div>
                                    <button aria-label={`Download version ${ver.versionNumber}`} className="p-2 bg-white/50 dark:bg-white/10 hover:bg-modtale-accent hover:text-white text-slate-700 dark:text-slate-300 rounded-lg transition-all shrink-0 shadow-sm border border-white/30 dark:border-white/5">
                                        <Download className="w-4 h-4" />
                                    </button>
                                </div>
                                <div className="text-xs text-slate-700 dark:text-slate-300 whitespace-pre-wrap leading-relaxed">
                                    {ver.changelog}
                                </div>
                            </div>
                        ))
                    ) : (
                        <div className="flex flex-col items-center justify-center text-slate-500 h-full gap-2">
                            <AlertCircle className="w-8 h-8 opacity-50" aria-hidden="true" />
                            <p className="font-medium text-sm">No compatible versions for this game version.</p>
                        </div>
                    )}
                </div>
            </div>
        );
    }

    return (
        <div className={`${GLASS_CARD} w-full overflow-hidden relative flex flex-col h-[380px] transform transition-transform hover:scale-[1.02] duration-500`}>
            <div className={`p-5 flex justify-between items-center shrink-0 ${GLASS_HEADER}`}>
                <div>
                    <h3 className="text-lg font-black text-slate-900 dark:text-white flex items-center gap-2">
                        <Download className="w-5 h-5 text-modtale-accent" aria-hidden="true" /> Download
                    </h3>
                    <div className="mt-1 flex items-center gap-2 group cursor-pointer" onClick={() => setShowExperimental(!showExperimental)}>
                        <div className={`w-8 h-4 rounded-full relative transition-colors shadow-[inset_0_1px_4px_rgba(0,0,0,0.2)] ${showExperimental ? 'bg-modtale-accent' : 'bg-slate-300/80 dark:bg-slate-700/80'}`}>
                            <div className={`absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform shadow-sm border border-black/5 ${showExperimental ? 'translate-x-4' : ''}`} />
                        </div>
                        <span className="text-[10px] font-bold text-slate-600 dark:text-slate-400 uppercase group-hover:text-slate-800 dark:group-hover:text-slate-200 transition-colors">Show Beta/Alpha</span>
                    </div>
                </div>
            </div>

            <div className="p-5 overflow-visible relative flex-1 flex flex-col justify-center">
                <div className="mb-5 relative z-20 shrink-0">
                    <label className="block text-[10px] font-bold text-slate-600 dark:text-slate-400 uppercase mb-2 tracking-wider">Game Version</label>
                    <div className="relative">
                        <div
                            className={`w-full flex items-center justify-between p-3 rounded-xl font-bold text-slate-900 dark:text-white text-sm cursor-pointer ${GLASS_ITEM} ${isDropdownOpen ? 'ring-2 ring-modtale-accent border-transparent' : ''}`}
                            onClick={() => setIsDropdownOpen(!isDropdownOpen)}
                        >
                            <span>{selectedVersion}</span>
                            <ChevronDown className={`w-4 h-4 text-slate-500 transition-transform ${isDropdownOpen ? 'rotate-180' : ''}`} aria-hidden="true" />
                        </div>
                        {isDropdownOpen && (
                            <div className="absolute top-[calc(100%+8px)] left-0 w-full bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl overflow-hidden z-50 py-1">
                                {versions.map(v => (
                                    <div
                                        key={v}
                                        className={`px-4 py-2.5 text-sm font-bold cursor-pointer transition-colors ${selectedVersion === v ? 'text-modtale-accent bg-blue-50/50 dark:bg-blue-500/10' : 'text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'}`}
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
                    <button className={`w-full backdrop-blur-xl p-4 rounded-2xl flex flex-col items-center justify-center gap-1.5 transition-all active:scale-95 mb-2 relative z-0 group overflow-hidden shrink-0 border ${themeClass}`}>
                        <div className="font-black text-lg flex items-center gap-2 group-hover:scale-105 transition-transform z-10"><Download className="w-5 h-5" aria-hidden="true" /> Download Latest</div>
                        <div className={`text-[10px] font-bold font-mono px-3 py-1 rounded-full border flex items-center gap-1.5 z-10 backdrop-blur-md shadow-sm ${getVersionBadgeColor(latestVer.channel)}`}>
                            v{latestVer.versionNumber} {latestVer.channel !== 'RELEASE' && <span className="uppercase opacity-80">{latestVer.channel}</span>}
                        </div>
                    </button>
                ) : (
                    <div className="flex-1 flex flex-col items-center justify-center text-slate-500">
                        <AlertCircle className="w-8 h-8 opacity-50 mb-2" aria-hidden="true" />
                        <p className="font-medium text-sm">No compatible versions.</p>
                        {!showExperimental && currentVersions.length > 0 && (
                            <button onClick={() => setShowExperimental(true)} className="mt-2 text-[11px] font-bold text-modtale-accent hover:underline">
                                Show experimental versions
                            </button>
                        )}
                    </div>
                )}
            </div>

            <div className="p-4 bg-white/20 dark:bg-black/10 border-t border-white/40 dark:border-white/10 shrink-0 z-10 backdrop-blur-md">
                <button onClick={() => setView('changelog')} className="text-[11px] text-slate-500 dark:text-slate-500 hover:text-modtale-accent dark:hover:text-modtale-accent font-bold uppercase tracking-wider flex items-center justify-start gap-1 w-full transition-colors">
                    View Full Changelog <ChevronRight className="w-3 h-3" aria-hidden="true" />
                </button>
            </div>
        </div>
    );
};

const InlineNotificationUI = () => (
    <div className={`${GLASS_CARD} w-full flex flex-col h-[380px] transform transition-transform hover:scale-[1.02] duration-500`}>
        <div className={`p-6 flex justify-between items-center ${GLASS_HEADER}`}>
            <h3 className="font-bold text-slate-900 dark:text-white flex items-center gap-2.5 text-lg">
                <Bell className="w-5 h-5 text-amber-500" aria-hidden="true" /> Notifications
            </h3>
            <span className="text-xs text-amber-600 dark:text-amber-500 font-bold cursor-pointer hover:underline uppercase tracking-wider">Clear All</span>
        </div>
        <div className="divide-y divide-slate-100 dark:divide-white/5 relative flex-1 overflow-hidden">
            <div className="absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-white dark:from-slate-900 to-transparent z-10 pointer-events-none opacity-80" />

            <div className="p-6 bg-blue-50/40 dark:bg-white/[0.02] flex items-start gap-4 hover:bg-blue-50/80 dark:hover:bg-white/[0.04] transition-colors">
                <div className="w-12 h-12 rounded-xl bg-white dark:bg-slate-800 text-blue-600 dark:text-blue-400 flex items-center justify-center shrink-0 border border-slate-200 dark:border-white/10 overflow-hidden shadow-sm">
                    <img src="https://cdn.modtale.net/images/d813b136-35aa-46c6-bb9e-359c20f7c146-cropped.png" alt="LevelingCore Update Icon" className="w-full h-full object-cover" loading="lazy" />
                </div>
                <div className="flex-1 min-w-0">
                    <div className="font-bold text-base text-slate-900 dark:text-white mb-1 flex items-center">
                        Update: LevelingCore <span className="inline-block w-2 h-2 bg-blue-500 rounded-full ml-2 shadow-[0_0_8px_rgba(59,130,246,0.6)]" />
                    </div>
                    <div className="text-sm text-slate-600 dark:text-slate-300 font-medium">Version 2.0 is now available.</div>
                    <div className="text-xs text-slate-400 dark:text-slate-500 mt-2 font-mono uppercase tracking-wider">10 mins ago</div>
                </div>
            </div>

            <div className="p-6 flex items-start gap-4 hover:bg-slate-50 dark:hover:bg-white/[0.02] transition-colors">
                <div className="w-12 h-12 rounded-xl bg-white dark:bg-slate-800 text-purple-600 dark:text-purple-400 flex items-center justify-center shrink-0 border border-slate-200 dark:border-white/10 overflow-hidden shadow-sm p-1">
                    <img src="https://cdn.modtale.net/avatars/AzureDoom/83c01443-6302-4aff-beb9-7d6f656f994c-cropped.png" alt="AzureDoom Profile Picture" className="w-full h-full object-cover rounded-lg" loading="lazy" />
                </div>
                <div className="flex-1 min-w-0">
                    <div className="font-bold text-base text-slate-800 dark:text-slate-200 mb-1">
                        Developer Reply
                    </div>
                    <div className="text-sm text-slate-600 dark:text-slate-300 font-medium leading-relaxed">AzureDoom replied to your comment on LevelingCore.</div>
                    <div className="text-xs text-slate-400 dark:text-slate-500 mt-2 font-mono uppercase tracking-wider">2 hours ago</div>
                </div>
            </div>
        </div>
    </div>
);

export const Home: React.FC<{ user?: User | null }> = ({ user }) => {
    const ssrData = getInitialData();

    const [allMods, setAllMods] = useState<Mod[]>(ssrData?.homeMods || []);
    const [stats, setStats] = useState(ssrData?.stats || { totalProjects: 0, totalDownloads: 0, totalUsers: 0 });
    const [hiddenSeries, setHiddenSeries] = useState<Record<string, boolean>>({});

    useEffect(() => {
        if (!ssrData || !ssrData.homeMods || ssrData.homeMods.length === 0) {
            const fetchMods = async () => {
                try {
                    const [trending, popular, gems, relevance] = await Promise.all([
                        api.get('/projects', { params: { size: 20, sort: 'trending' } }),
                        api.get('/projects', { params: { size: 20, sort: 'popular' } }),
                        api.get('/projects', { params: { size: 20, category: 'hidden_gems', sort: 'favorites' } }),
                        api.get('/projects', { params: { size: 20, sort: 'relevance' } })
                    ]);

                    const combined = [
                        ...(trending.data?.content || []),
                        ...(popular.data?.content || []),
                        ...(gems.data?.content || []),
                        ...(relevance.data?.content || [])
                    ];

                    const unique = Array.from(new Map(combined.map(m => [m.id, m])).values());
                    const mixed = unique.sort(() => Math.random() - 0.5);

                    setAllMods(mixed);
                } catch (err) {
                    console.error("Failed to fetch mods", err);
                }
            };
            fetchMods();
        } else if (allMods.length > 0) {
            setAllMods(prev => [...prev].sort(() => Math.random() - 0.5));
        }

        if (!ssrData || !ssrData.stats || (ssrData.stats.totalProjects === 0 && ssrData.stats.totalDownloads === 0)) {
            const fetchStats = async () => {
                try {
                    const res = await api.get('/analytics/platform/stats');
                    setStats(res.data);
                } catch (err) {
                    console.error("Failed to fetch platform stats", err);
                }
            };
            fetchStats();
        }
    }, []);

    const chartDatasets = [
        {
            id: 'views', label: 'Project Views', color: '#8b5cf6', hidden: !!hiddenSeries['views'],
            data: [{ date: 'Mon', value: 120 }, { date: 'Tue', value: 240 }, { date: 'Wed', value: 180 }, { date: 'Thu', value: 450 }, { date: 'Fri', value: 390 }, { date: 'Sat', value: 680 }, { date: 'Sun', value: 850 }]
        },
        {
            id: 'downloads', label: 'Downloads', color: '#10b981', hidden: !!hiddenSeries['downloads'],
            data: [{ date: 'Mon', value: 50 }, { date: 'Tue', value: 80 }, { date: 'Wed', value: 110 }, { date: 'Thu', value: 320 }, { date: 'Fri', value: 210 }, { date: 'Sat', value: 450 }, { date: 'Sun', value: 600 }]
        }
    ];

    const toggleDataset = (id: string) => {
        setHiddenSeries(prev => ({ ...prev, [id]: !prev[id] }));
    };

    const validFeaturedMods = allMods.filter(mod => {
        const hasBanner = Boolean(mod.bannerUrl);
        const hasCustomIcon = Boolean(mod.imageUrl) && !mod.imageUrl.includes('favicon.svg') && !mod.imageUrl.includes('favicon.png');
        return hasBanner && hasCustomIcon;
    });

    const randomDisplayMod = useMemo(() => {
        if (allMods.length === 0) return undefined;
        const withIcons = allMods.filter(m => m.imageUrl && !m.imageUrl.includes('favicon'));
        return withIcons[Math.floor(Math.random() * withIcons.length)];
    }, [allMods]);

    const displayFeaturedMods = validFeaturedMods.slice(0, 16);
    const col1Mods = displayFeaturedMods.filter((_, i) => i % 2 === 0);
    const col2Mods = displayFeaturedMods.filter((_, i) => i % 2 === 1);

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-[#0B1120] text-slate-900 dark:text-slate-300 relative selection:bg-blue-500 selection:text-white overflow-x-hidden transition-colors duration-300">
            <Helmet>
                <title>Modtale - The Hytale Community Repository</title>
                <meta name="description" content="The community repository for Hytale. Discover, download, and share Hytale worlds, plugins, asset packs, worlds, and modpacks." />
                <style>{`
                    @keyframes marquee-up {
                        from { transform: translateY(0); }
                        to { transform: translateY(calc(-50% - 0.75rem)); }
                    }
                    .animate-marquee-up {
                        animation: marquee-up var(--marquee-duration, 40s) linear infinite;
                        will-change: transform;
                    }
                `}</style>
            </Helmet>

            <main className="relative z-10">
                <section className="relative w-full min-h-[85vh] 2xl:min-h-[90vh] flex flex-col items-center justify-center pt-16 lg:pt-16 2xl:pt-36 pb-16 lg:pb-20 border-b border-slate-200 dark:border-white/5 overflow-hidden">
                    <div className="absolute inset-0 bg-[repeating-linear-gradient(45deg,transparent,transparent_10px,rgba(59,130,246,0.05)_10px,rgba(59,130,246,0.05)_11px)] dark:bg-[repeating-linear-gradient(45deg,transparent,transparent_10px,rgba(255,255,255,0.03)_10px,rgba(255,255,255,0.03)_11px)] [mask-image:radial-gradient(ellipse_60%_60%_at_50%_50%,#000_70%,transparent_100%)] pointer-events-none" />

                    <div className="absolute top-1/4 -left-1/4 w-[800px] h-[800px] bg-blue-500/10 dark:bg-blue-600/15 rounded-full blur-[120px] mix-blend-multiply dark:mix-blend-screen pointer-events-none" />
                    <div className="absolute bottom-1/4 -right-1/4 w-[600px] h-[600px] bg-indigo-500/10 dark:bg-indigo-600/15 rounded-full blur-[120px] mix-blend-multiply dark:mix-blend-screen pointer-events-none" />

                    <div className="relative z-20 w-full max-w-[112rem] mx-auto px-6 sm:px-12 lg:px-16 2xl:px-28 grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-12 2xl:gap-20 items-stretch">

                        <div className="flex flex-col items-center lg:items-start text-center lg:text-left w-full max-w-2xl lg:max-w-xl 2xl:max-w-2xl animate-in fade-in duration-1000 py-4 lg:py-8 justify-center">
                            <img
                                src="/assets/logo_light.svg"
                                alt="Modtale Logo"
                                className="h-16 md:h-20 lg:h-24 mb-10 object-contain drop-shadow-[0_0_30px_rgba(59,130,246,0.2)] hidden dark:block"
                                fetchPriority="high"
                            />
                            <img
                                src="/assets/logo.svg"
                                alt="Modtale Logo"
                                className="h-16 md:h-20 lg:h-24 mb-10 object-contain drop-shadow-sm block dark:hidden"
                                fetchPriority="high"
                            />

                            <h1 className="text-4xl sm:text-5xl lg:text-6xl 2xl:text-[5.5rem] font-black text-slate-900 dark:text-white tracking-tighter leading-[1.05] mb-6 2xl:mb-8">
                                The Hytale <br className="hidden lg:block" />
                                <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-600 via-indigo-500 to-blue-500 dark:from-blue-400 dark:via-indigo-400 dark:to-blue-300">
                                    Community Repository.
                                </span>
                            </h1>

                            <p className="text-lg 2xl:text-xl text-slate-600 dark:text-slate-300 max-w-2xl lg:max-w-lg 2xl:max-w-xl mb-10 2xl:mb-12 font-medium leading-relaxed">
                                Discover, download, and seamlessly share Hytale mods, worlds, plugins, asset packs, and modpacks.
                            </p>

                            <nav aria-label="Primary Actions" className="flex flex-col sm:flex-row items-center gap-4 sm:gap-5 w-full sm:w-auto mb-10 2xl:mb-14">
                                <Link
                                    to="/mods"
                                    className="flex items-center justify-center px-10 h-16 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-2xl transition-all shadow-[0_8px_32px_rgba(37,99,235,0.25),inset_0_1px_0_rgba(255,255,255,0.2)] hover:shadow-[0_16px_48px_rgba(37,99,235,0.3),inset_0_1px_0_rgba(255,255,255,0.2)] hover:-translate-y-0.5 w-full sm:w-auto text-lg ring-1 ring-blue-500"
                                >
                                    <Search className="w-5 h-5 mr-3" aria-hidden="true" />
                                    Discover Projects
                                </Link>
                                <Link
                                    to="/upload"
                                    className="flex items-center justify-center px-10 h-16 bg-white dark:bg-slate-800/80 border border-slate-200 dark:border-white/10 text-slate-900 dark:text-white font-bold rounded-2xl hover:bg-slate-50 dark:hover:bg-slate-800 transition-all w-full sm:w-auto text-lg shadow-sm hover:shadow-md hover:-translate-y-0.5"
                                >
                                    <Upload className="w-5 h-5 mr-3 text-slate-400 dark:text-slate-500" aria-hidden="true" />
                                    Publish Work
                                </Link>
                            </nav>

                            <div className={`${GLASS_CARD} flex flex-wrap items-center justify-center lg:justify-start gap-6 sm:gap-10 2xl:gap-14 w-full sm:w-fit p-6 sm:p-8 shadow-sm lg:-ml-1.5`}>
                                <div className="flex flex-col items-center lg:items-start">
                                    <span className="text-3xl sm:text-4xl font-black text-slate-900 dark:text-white tracking-tight">
                                        <AnimatedCounter value={stats.totalProjects} />
                                    </span>
                                    <span className="text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest mt-2">Projects</span>
                                </div>
                                <div className="w-px h-12 bg-slate-200 dark:bg-white/10" aria-hidden="true" />
                                <div className="flex flex-col items-center lg:items-start">
                                    <span className="text-3xl sm:text-4xl font-black text-slate-900 dark:text-white tracking-tight">
                                        <AnimatedCounter value={stats.totalDownloads} />
                                    </span>
                                    <span className="text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest mt-2">Downloads</span>
                                </div>
                                <div className="w-px h-12 bg-slate-200 dark:bg-white/10 hidden sm:block" aria-hidden="true" />
                                <div className="flex flex-col items-center lg:items-start hidden sm:flex">
                                    <span className="text-3xl sm:text-4xl font-black text-slate-900 dark:text-white tracking-tight">
                                        <AnimatedCounter value={stats.totalUsers} />
                                    </span>
                                    <span className="text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest mt-2">Creators</span>
                                </div>
                            </div>
                        </div>

                        {displayFeaturedMods.length > 0 && (
                            <div className="relative hidden lg:block w-full lg:min-h-[600px] 2xl:min-h-[750px]">
                                <aside
                                    className="absolute -inset-x-4 2xl:-inset-x-8 inset-y-0 px-4 2xl:px-8 flex gap-6 2xl:gap-10 justify-end overflow-hidden animate-in fade-in slide-in-from-right-12 duration-1000 delay-300"
                                    style={{
                                        maskImage: 'linear-gradient(to bottom, transparent 0, black 120px, black calc(100% - 120px), transparent 100%)',
                                        WebkitMaskImage: 'linear-gradient(to bottom, transparent 0, black 120px, black calc(100% - 120px), transparent 100%)'
                                    }}
                                    aria-label="Trending Hytale Mods Showcase"
                                >
                                    <MarqueeColumn mods={col1Mods} duration="35s" />
                                    <MarqueeColumn mods={col2Mods} duration="45s" />
                                </aside>
                            </div>
                        )}

                        {displayFeaturedMods.length > 0 && (
                            <div className="w-full flex flex-col gap-6 lg:hidden animate-in fade-in slide-in-from-bottom-8 duration-1000 delay-300 mt-10">
                                {displayFeaturedMods.slice(0, 3).map((mod, index) => (
                                    <FeaturedModCard key={`mobile-${mod.id}-${index}`} mod={mod} priority={index === 0} />
                                ))}
                            </div>
                        )}
                    </div>
                </section>

                <div className="max-w-[112rem] mx-auto px-6 sm:px-12 lg:px-16 2xl:px-28 space-y-24 lg:space-y-32 2xl:space-y-40 py-24 lg:py-32 2xl:py-40 relative z-20">

                    <section className="flex flex-col lg:flex-row items-center gap-12 lg:gap-16 2xl:gap-24">
                        <div className="flex-1 space-y-6 2xl:space-y-8">
                            <span className="text-blue-600 dark:text-blue-400 font-bold tracking-widest uppercase text-sm mb-2 block bg-blue-50 dark:bg-blue-500/10 w-fit px-3 py-1 rounded-full border border-blue-100 dark:border-blue-500/20">Version Management</span>
                            <h2 className="text-3xl md:text-4xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">Install Hytale Mods with Confidence.</h2>
                            <p className="text-lg 2xl:text-xl text-slate-600 dark:text-slate-300 leading-relaxed font-medium">
                                Finding the right file shouldn't be a puzzle. Modtale automatically matches game servers and client projects to your game version and makes it easy to review changelogs before you hit download.
                            </p>
                            <Link to="/mods" className="inline-flex items-center font-bold text-blue-600 hover:text-blue-500 dark:text-blue-400 dark:hover:text-blue-300 transition-colors group text-lg">
                                Start browsing <ChevronRight className="w-5 h-5 ml-2 group-hover:translate-x-1.5 transition-transform" aria-hidden="true" />
                            </Link>
                        </div>
                        <div className="flex-1 w-full relative">
                            <div className="absolute -inset-10 bg-gradient-to-tr from-blue-400/20 to-transparent dark:from-blue-500/20 blur-3xl rounded-full z-0 pointer-events-none" />
                            <div className="relative z-10 w-full max-w-lg ml-auto">
                                <InlineDownloadUI />
                            </div>
                        </div>
                    </section>

                    <section className="flex flex-col lg:flex-row-reverse items-center gap-12 lg:gap-16 2xl:gap-24">
                        <div className="flex-1 space-y-6 2xl:space-y-8">
                            <span className="text-emerald-600 dark:text-emerald-400 font-bold tracking-widest uppercase text-sm mb-2 block bg-emerald-50 dark:bg-emerald-500/10 w-fit px-3 py-1 rounded-full border border-emerald-100 dark:border-emerald-500/20">Library Resolution</span>
                            <h2 className="text-3xl md:text-4xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">Automated Hytale Mod Dependencies.</h2>
                            <p className="text-lg 2xl:text-xl text-slate-600 dark:text-slate-300 leading-relaxed font-medium">
                                Forget hunting down core libraries or confusing modpacks. Modtale analyzes scripting requirements and allows you to seamlessly download required plugins and optional maps in one swift action.
                            </p>
                        </div>
                        <div className="flex-1 w-full relative">
                            <div className="absolute -inset-10 bg-gradient-to-tl from-emerald-400/20 to-transparent dark:from-emerald-500/20 blur-3xl rounded-full z-0 pointer-events-none" />
                            <div className="relative z-10 w-full max-w-lg mr-auto">
                                <InlineDependencyUI randomMod={randomDisplayMod} />
                            </div>
                        </div>
                    </section>

                    <section className="flex flex-col lg:flex-row items-center gap-12 lg:gap-16 2xl:gap-24">
                        <div className="flex-1 space-y-6 2xl:space-y-8">
                            <span className="text-purple-600 dark:text-purple-400 font-bold tracking-widest uppercase text-sm mb-2 block bg-purple-50 dark:bg-purple-500/10 w-fit px-3 py-1 rounded-full border border-purple-100 dark:border-purple-500/20">Creator Tools</span>
                            <h2 className="text-3xl md:text-4xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">Advanced Creator Analytics.</h2>
                            <p className="text-lg 2xl:text-xl text-slate-600 dark:text-slate-300 leading-relaxed font-medium">
                                Creators get access to powerful, privacy-respecting analytics. Track your daily modpack downloads, world page views, and week-over-week asset growth metrics instantly from your dashboard.
                            </p>
                            <Link to="/upload" className="inline-flex items-center font-bold text-purple-600 hover:text-purple-500 dark:text-purple-400 dark:hover:text-purple-300 transition-colors group text-lg">
                                Publish your project <ChevronRight className="w-5 h-5 ml-2 group-hover:translate-x-1.5 transition-transform" aria-hidden="true" />
                            </Link>
                        </div>
                        <div className="flex-1 w-full relative">
                            <div className="absolute -inset-10 bg-gradient-to-tr from-purple-400/20 to-transparent dark:from-purple-500/20 blur-3xl rounded-full z-0 pointer-events-none" />

                            <div className={`${GLASS_CARD} flex flex-col h-[500px] transform transition-transform hover:scale-[1.01] duration-500 ml-auto w-full max-w-xl relative z-10`}>
                                <div className="flex items-center gap-4 shrink-0 px-6 pt-6 mb-4">
                                    <div className="p-2.5 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-white/5 shadow-sm text-purple-500">
                                        <TrendingUp className="w-5 h-5" aria-hidden="true" />
                                    </div>
                                    <div>
                                        <h3 className="font-black text-lg text-slate-900 dark:text-white leading-tight">Project Growth</h3>
                                        <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">Platform-wide reach and momentum.</p>
                                    </div>
                                </div>
                                <div className="flex-1 min-h-0 px-6 pb-6">
                                    <LineChart
                                        datasets={chartDatasets}
                                        onToggle={toggleDataset}
                                    />
                                </div>
                            </div>
                        </div>
                    </section>

                    <section className="flex flex-col lg:flex-row-reverse items-center gap-12 lg:gap-16 2xl:gap-24">
                        <div className="flex-1 space-y-6 2xl:space-y-8">
                            <span className="text-amber-600 dark:text-amber-400 font-bold tracking-widest uppercase text-sm mb-2 block bg-amber-50 dark:bg-amber-500/10 w-fit px-3 py-1 rounded-full border border-amber-100 dark:border-amber-500/20">Community Hub</span>
                            <h2 className="text-3xl md:text-4xl 2xl:text-6xl font-black text-slate-900 dark:text-white tracking-tight leading-tight">Always in the loop.</h2>
                            <p className="text-lg 2xl:text-xl text-slate-600 dark:text-slate-300 leading-relaxed font-medium">
                                Modtale keeps the Hytale community connected. Receive real-time alerts when tracked texture packs drop new updates, or when plugin developers reply directly to your feedback.
                            </p>
                        </div>
                        <div className="flex-1 w-full relative">
                            <div className="absolute -inset-10 bg-gradient-to-tl from-amber-400/20 to-transparent dark:from-amber-500/20 blur-3xl rounded-full z-0 pointer-events-none" />
                            <div className="relative z-10 w-full max-w-lg mr-auto">
                                <InlineNotificationUI />
                            </div>
                        </div>
                    </section>

                </div>

                <section className="py-20 lg:py-32 border-t border-slate-200 dark:border-white/5 bg-slate-50/50 dark:bg-slate-900/20 backdrop-blur-xl relative z-20">
                    <div className="max-w-4xl mx-auto px-6 text-center">
                        <div className="w-20 h-20 bg-slate-200 dark:bg-slate-800 rounded-3xl mx-auto mb-8 flex items-center justify-center shadow-inner border border-slate-300/50 dark:border-white/5">
                            <Code className="w-10 h-10 text-slate-500 dark:text-slate-400" aria-hidden="true" />
                        </div>
                        <h2 className="text-4xl lg:text-5xl font-black text-slate-900 dark:text-white mb-8 tracking-tight">Built by the community, for the community.</h2>
                        <p className="text-xl text-slate-600 dark:text-slate-300 mb-12 font-medium max-w-3xl mx-auto leading-relaxed">
                            Modtale is 100% open-source. We believe a modding repository should exist purely to serve its ecosystem, free from corporate interests. Explore our source code or utilize our public API to build your own tools.
                        </p>
                        <nav aria-label="Footer Actions" className="flex flex-col sm:flex-row items-center justify-center gap-6">
                            <a href="https://github.com/Modtale/modtale" target="_blank" rel="noreferrer" className="inline-flex items-center justify-center px-8 h-16 text-lg font-bold rounded-2xl transition-all gap-3 w-full sm:w-auto text-slate-900 dark:text-white bg-white dark:bg-slate-800 border border-slate-200 dark:border-white/10 shadow-sm hover:shadow-md hover:-translate-y-0.5">
                                <Github className="w-6 h-6" aria-hidden="true" /> View Source Code
                            </a>
                            <Link to="/api-docs" className="inline-flex items-center justify-center px-8 h-16 text-lg font-bold rounded-2xl transition-all gap-3 w-full sm:w-auto text-blue-700 dark:text-blue-400 bg-blue-50 dark:bg-blue-500/10 border border-blue-200 dark:border-blue-500/20 hover:bg-blue-100 dark:hover:bg-blue-500/20 hover:-translate-y-0.5">
                                <Code className="w-6 h-6" aria-hidden="true" /> View API Docs
                            </Link>
                        </nav>
                    </div>
                </section>

            </main>
        </div>
    );
};