import React, { useState, useEffect, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import {
    Search, Upload, ChevronRight, Check,
    AlertCircle, Download, Link as LinkIcon, Bell,
    Zap, BarChart3, Box, ChevronDown, Github, Code, X, List
} from 'lucide-react';
import { LineChart } from '../components/ui/charts/LineChart';
import type { Mod } from '../types';
import { api, BACKEND_URL } from '../utils/api';
import { createSlug } from '../utils/slug';

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
        }
    }, [value]);

    return <>{count.toLocaleString()}</>;
};

const FeaturedModCard = ({ mod }: { mod: Mod }) => {
    const iconUrl = mod.imageUrl
        ? (mod.imageUrl.startsWith('/api') ? `${BACKEND_URL}${mod.imageUrl}` : mod.imageUrl)
        : '/assets/favicon.svg';

    const bannerUrl = mod.bannerUrl
        ? (mod.bannerUrl.startsWith('/api') ? `${BACKEND_URL}${mod.bannerUrl}` : mod.bannerUrl)
        : null;

    const projectUrl = `/${mod.classification === 'MODPACK' ? 'modpack' : mod.classification === 'SAVE' ? 'world' : 'mod'}/${createSlug(mod.title, mod.id)}`;

    return (
        <Link
            to={projectUrl}
            className="flex flex-col w-full shrink-0 bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden group hover:border-modtale-accent/50 dark:hover:border-white/30 hover:-translate-y-1 transition-all shadow-lg hover:shadow-2xl dark:shadow-xl backdrop-blur-md"
        >
            <div className="w-full aspect-[3/1] relative bg-slate-800 border-b border-white/5">
                {bannerUrl && (
                    <img
                        src={bannerUrl}
                        alt=""
                        className="w-full h-full object-cover opacity-70 group-hover:opacity-100 transition-opacity duration-500"
                        loading="lazy"
                    />
                )}
                <div className="absolute inset-0 bg-gradient-to-t from-white via-white/80 dark:from-[#0B1120] dark:via-[#0B1120]/80 to-transparent" />
            </div>
            <div className="px-5 pb-5 relative flex flex-col flex-1 bg-white dark:bg-transparent">
                <img
                    src={iconUrl}
                    alt=""
                    className="w-14 h-14 rounded-xl border-4 border-white dark:border-slate-900 bg-slate-100 dark:bg-slate-900 shadow-lg absolute -top-8 object-cover group-hover:scale-105 transition-transform"
                    onError={(e) => e.currentTarget.src = '/assets/favicon.svg'}
                />
                <div className="mt-8 flex-1">
                    <h3 className="text-lg font-black text-slate-900 dark:text-white group-hover:text-modtale-accent transition-colors truncate">{mod.title}</h3>
                    <p className="text-xs text-slate-500 dark:text-slate-400 font-medium truncate mt-0.5">By {mod.author}</p>
                </div>
                <div className="mt-4 flex items-center gap-2 text-[10px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest">
                    <Download className="w-3.5 h-3.5 text-slate-400 dark:text-slate-500" /> {mod.downloadCount?.toLocaleString() || 0}
                </div>
            </div>
        </Link>
    );
};

const InlineDependencyUI = ({ randomMod }: { randomMod?: Mod }) => {
    const randomIconUrl = randomMod?.imageUrl
        ? (randomMod.imageUrl.startsWith('/api') ? `${BACKEND_URL}${randomMod.imageUrl}` : randomMod.imageUrl)
        : null;

    const randomVersion = randomMod?.versions?.[0]?.versionNumber || '1.0.0';

    return (
        <div className="bg-white dark:bg-slate-900/80 border border-slate-200 dark:border-white/10 rounded-2xl w-full shadow-2xl overflow-hidden flex flex-col h-[350px] backdrop-blur-md transform transition-transform hover:scale-[1.02] duration-500">
            <div className="p-5 border-b border-slate-200 dark:border-white/10 flex justify-between items-center bg-slate-50 dark:bg-white/[0.02]">
                <h3 className="font-bold text-slate-900 dark:text-white flex items-center gap-2">
                    <LinkIcon className="w-4 h-4 text-modtale-accent" /> Dependencies
                </h3>
            </div>
            <div className="p-5 space-y-3 overflow-hidden relative flex-1">
                <div className="flex items-center justify-between p-3 rounded-xl border border-modtale-accent/30 bg-modtale-accent/5">
                    <div className="flex items-center gap-3">
                        <div className="w-5 h-5 rounded bg-modtale-accent text-white flex items-center justify-center shrink-0">
                            <Check className="w-3.5 h-3.5" />
                        </div>
                        <div className="w-10 h-10 rounded-lg bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-white/5 flex items-center justify-center shrink-0 overflow-hidden">
                            <img src="/assets/favicon.svg" alt="" className="w-full h-full object-cover" />
                        </div>
                        <div>
                            <div className="font-bold text-slate-900 dark:text-white text-sm">Hytale Core Library</div>
                            <div className="text-xs text-slate-500 dark:text-slate-400 font-mono mt-0.5">v1.2.0</div>
                        </div>
                    </div>
                    <span className="text-[10px] font-bold uppercase bg-amber-100 dark:bg-amber-500/10 text-amber-600 dark:text-amber-500 px-2 py-1 rounded-md border border-amber-200 dark:border-amber-500/20">Required</span>
                </div>

                {randomMod ? (
                    <div className="flex items-center justify-between p-3 rounded-xl border border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-white/5">
                        <div className="flex items-center gap-3">
                            <div className="w-5 h-5 rounded border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 shrink-0" />
                            <div className="w-10 h-10 rounded-lg bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-white/5 flex items-center justify-center shrink-0 overflow-hidden">
                                {randomIconUrl ? <img src={randomIconUrl} alt="" className="w-full h-full object-cover" /> : <Box className="w-5 h-5 text-slate-500" />}
                            </div>
                            <div className="min-w-0">
                                <div className="font-bold text-slate-900 dark:text-white text-sm truncate">{randomMod.title}</div>
                                <div className="text-xs text-slate-500 dark:text-slate-400 font-mono mt-0.5">v{randomVersion}</div>
                            </div>
                        </div>
                        <span className="text-[10px] font-bold uppercase bg-slate-200 dark:bg-white/10 text-slate-600 dark:text-slate-400 px-2 py-1 rounded-md border border-slate-300 dark:border-white/5 shrink-0 ml-2">Optional</span>
                    </div>
                ) : (
                    <div className="h-16 w-full animate-pulse bg-slate-100 dark:bg-white/5 rounded-xl" />
                )}

                <div className="flex items-start gap-2 text-xs text-amber-700 dark:text-amber-400 bg-amber-50 dark:bg-amber-500/10 p-3 rounded-lg border border-amber-200 dark:border-amber-500/20">
                    <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                    <p>Some <span className="font-bold">Required</span> dependencies are missing.</p>
                </div>
            </div>
        </div>
    );
};

const InlineDownloadUI = () => {
    const [showExperimental, setShowExperimental] = useState(false);
    const [view, setView] = useState<'download' | 'changelog'>('download');

    const allVersions = [
        { id: 'v3', versionNumber: '2.5.0-beta', channel: 'BETA', date: '2 days ago', changelog: 'Testing new durability mechanics. Expect bugs.' },
        { id: 'v2', versionNumber: '2.4.1', channel: 'RELEASE', date: '5 days ago', changelog: 'Added 5 new elemental wands.\nFixed visual bugs with particle effects.' },
        { id: 'v1', versionNumber: '2.4.0', channel: 'RELEASE', date: '2 weeks ago', changelog: 'Initial release of the expanded magic system.' },
    ];

    const visibleVersions = allVersions.filter(v => showExperimental || v.channel === 'RELEASE');
    const latestVer = visibleVersions[0];

    const getVersionBadgeColor = (channel: string) => {
        switch(channel) {
            case 'BETA': return 'bg-blue-100 text-blue-700 border-blue-200 dark:bg-blue-500/20 dark:text-blue-200 dark:border-blue-500/30';
            case 'ALPHA': return 'bg-orange-100 text-orange-700 border-orange-200 dark:bg-orange-500/20 dark:text-orange-200 dark:border-orange-500/30';
            default: return 'bg-slate-100 border-slate-200 text-slate-700 dark:bg-white/10 dark:border-white/20 dark:text-white';
        }
    };

    if (view === 'changelog') {
        return (
            <div className="bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-white/10 rounded-2xl w-full shadow-2xl overflow-hidden flex flex-col h-[350px] backdrop-blur-md transform transition-transform hover:scale-[1.02] duration-500">
                <div className="p-5 border-b border-slate-200 dark:border-white/10 flex justify-between items-center bg-slate-50 dark:bg-black/20 shrink-0">
                    <div>
                        <h3 className="text-lg font-black text-slate-900 dark:text-white flex items-center gap-2"><List className="w-5 h-5 text-modtale-accent" /> Changelog</h3>
                        <div className="mt-1 flex items-center gap-2 cursor-pointer group" onClick={() => setShowExperimental(!showExperimental)}>
                            <div className={`w-8 h-4 rounded-full relative transition-colors ${showExperimental ? 'bg-modtale-accent' : 'bg-slate-300 dark:bg-slate-700'}`}>
                                <div className={`absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform ${showExperimental ? 'translate-x-4' : ''}`} />
                            </div>
                            <span className="text-[10px] font-bold text-slate-500 uppercase group-hover:text-slate-700 dark:group-hover:text-slate-300 transition-colors">Show Beta/Alpha</span>
                        </div>
                    </div>
                    <button onClick={() => setView('download')} className="p-2 rounded-full hover:bg-slate-200 dark:hover:bg-white/10 text-slate-500 transition-colors"><X className="w-5 h-5" /></button>
                </div>

                <div className="p-5 overflow-y-auto custom-scrollbar flex-1 space-y-4 relative">
                    {visibleVersions.map(ver => (
                        <div key={ver.id} className="bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-white/5 rounded-xl p-4 shadow-sm hover:border-modtale-accent/30 dark:hover:border-white/10 transition-colors">
                            <div className="flex items-center justify-between gap-4 mb-3 border-b border-slate-200 dark:border-white/5 pb-3">
                                <div>
                                    <div className="flex items-center gap-2 mb-1">
                                        <span className="text-base font-black text-slate-900 dark:text-white">v{ver.versionNumber}</span>
                                        {ver.channel !== 'RELEASE' && <span className={`text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded border ${getVersionBadgeColor(ver.channel)}`}>{ver.channel}</span>}
                                    </div>
                                    <div className="flex items-center gap-2 text-[10px] font-bold text-slate-500 uppercase tracking-wide">
                                        <span>{ver.date}</span>
                                        <span className="w-1 h-1 rounded-full bg-slate-300 dark:bg-slate-600"></span>
                                        <span>2026.01.13-dcad8778f</span>
                                    </div>
                                </div>
                                <button className="p-2 bg-slate-200 dark:bg-white/5 hover:bg-modtale-accent hover:text-white text-slate-600 dark:text-slate-300 rounded-lg transition-all shrink-0">
                                    <Download className="w-4 h-4" />
                                </button>
                            </div>
                            <div className="text-xs text-slate-600 dark:text-slate-400 whitespace-pre-wrap leading-relaxed">
                                {ver.changelog}
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        );
    }

    return (
        <div className="bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-white/10 rounded-2xl w-full shadow-2xl overflow-hidden relative flex flex-col h-[350px] backdrop-blur-md transform transition-transform hover:scale-[1.02] duration-500">
            <div className="p-5 border-b border-slate-200 dark:border-white/10 flex justify-between items-center bg-slate-50 dark:bg-black/20 shrink-0">
                <div>
                    <h3 className="text-lg font-black text-slate-900 dark:text-white flex items-center gap-2">
                        <Download className="w-5 h-5 text-modtale-accent" /> Download
                    </h3>
                    <div className="mt-1 flex items-center gap-2 group cursor-pointer" onClick={() => setShowExperimental(!showExperimental)}>
                        <div className={`w-8 h-4 rounded-full relative transition-colors ${showExperimental ? 'bg-modtale-accent' : 'bg-slate-300 dark:bg-slate-700'}`}>
                            <div className={`absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform ${showExperimental ? 'translate-x-4' : ''}`} />
                        </div>
                        <span className="text-[10px] font-bold text-slate-500 uppercase group-hover:text-slate-700 dark:group-hover:text-slate-300 transition-colors">Show Beta/Alpha</span>
                    </div>
                </div>
                <button className="p-2 rounded-full hover:bg-slate-200 dark:hover:bg-white/10 text-slate-500 transition-colors"><X className="w-5 h-5" /></button>
            </div>

            <div className="p-5 overflow-hidden relative flex-1 flex flex-col justify-center">
                <div className="mb-5 relative z-0 shrink-0">
                    <label className="block text-[10px] font-bold text-slate-500 dark:text-slate-400 uppercase mb-2 tracking-wider">Game Version</label>
                    <div className="w-full flex items-center justify-between p-3 bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-white/10 rounded-xl font-bold text-slate-900 dark:text-white text-sm cursor-pointer">
                        <span>2026.01.13-dcad8778f</span>
                        <ChevronDown className="w-4 h-4 text-slate-400" />
                    </div>
                </div>

                {latestVer ? (
                    <button className="w-full bg-modtale-accent hover:bg-modtale-accentHover text-white p-4 rounded-2xl shadow-lg shadow-modtale-accent/20 flex flex-col items-center justify-center gap-1.5 transition-all active:scale-95 mb-2 relative z-0 group overflow-hidden shrink-0">
                        <div className="font-black text-lg flex items-center gap-2 group-hover:scale-105 transition-transform"><Download className="w-5 h-5" /> Download Latest</div>
                        <div className={`text-[10px] font-bold font-mono px-3 py-1 rounded-full border flex items-center gap-1.5 z-10 ${getVersionBadgeColor(latestVer.channel)}`}>
                            v{latestVer.versionNumber} {latestVer.channel !== 'RELEASE' && <span className="uppercase opacity-80">{latestVer.channel}</span>}
                        </div>
                    </button>
                ) : (
                    <div className="flex-1 flex flex-col items-center justify-center text-slate-500">
                        <AlertCircle className="w-8 h-8 opacity-50 mb-2" />
                        <p className="font-medium text-sm">No compatible versions.</p>
                    </div>
                )}
            </div>

            <div className="p-4 bg-slate-50 dark:bg-black/20 border-t border-slate-200 dark:border-white/10 text-center shrink-0 z-20">
                <button onClick={() => setView('changelog')} className="text-xs text-slate-600 dark:text-slate-400 hover:text-modtale-accent dark:hover:text-modtale-accent font-bold uppercase tracking-wider flex items-center justify-center gap-1 w-full transition-colors">
                    View Full Changelog <ChevronRight className="w-3 h-3" />
                </button>
            </div>
        </div>
    );
};

const InlineNotificationUI = () => (
    <div className="bg-white dark:bg-slate-900/80 border border-slate-200 dark:border-white/10 rounded-2xl w-full shadow-2xl overflow-hidden flex flex-col h-[350px] backdrop-blur-md transform transition-transform hover:scale-[1.02] duration-500">
        <div className="p-5 border-b border-slate-200 dark:border-white/10 flex justify-between items-center bg-slate-50 dark:bg-white/[0.02]">
            <h3 className="font-bold text-slate-900 dark:text-white flex items-center gap-2">
                <Bell className="w-4 h-4 text-modtale-accent" /> Notifications
            </h3>
            <span className="text-xs text-modtale-accent font-bold cursor-pointer hover:underline">Clear All</span>
        </div>
        <div className="divide-y divide-slate-100 dark:divide-white/5 relative flex-1 overflow-hidden">
            <div className="absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-white dark:from-slate-900 to-transparent z-10 pointer-events-none" />

            <div className="p-5 bg-blue-50/50 dark:bg-white/[0.03] flex items-start gap-4">
                <div className="w-10 h-10 rounded-lg bg-blue-100 dark:bg-blue-500/20 text-blue-600 dark:text-blue-400 flex items-center justify-center shrink-0 border border-blue-200 dark:border-blue-500/30 overflow-hidden">
                    <img src="https://cdn.modtale.net/images/d813b136-35aa-46c6-bb9e-359c20f7c146-cropped.png" alt="" className="w-full h-full object-cover" />
                </div>
                <div className="flex-1 min-w-0">
                    <div className="font-bold text-sm text-slate-900 dark:text-white mb-1 flex items-center">
                        Update: LevelingCore <span className="inline-block w-1.5 h-1.5 bg-modtale-accent rounded-full ml-2" />
                    </div>
                    <div className="text-xs text-slate-600 dark:text-slate-400">Version 2.0 is now available.</div>
                    <div className="text-[10px] text-slate-500 mt-2 font-mono">10 mins ago</div>
                </div>
            </div>

            <div className="p-5 flex items-start gap-4">
                <div className="w-10 h-10 rounded-lg bg-purple-100 dark:bg-purple-500/20 text-purple-600 dark:text-purple-400 flex items-center justify-center shrink-0 font-black border border-purple-200 dark:border-purple-500/30 overflow-hidden">
                    <img src="https://cdn.modtale.net/avatars/AzureDoom/83c01443-6302-4aff-beb9-7d6f656f994c-cropped.png" alt="" className="w-full h-full object-cover" />
                </div>
                <div className="flex-1 min-w-0">
                    <div className="font-bold text-sm text-slate-800 dark:text-slate-200 mb-1">
                        Developer Reply
                    </div>
                    <div className="text-xs text-slate-600 dark:text-slate-400">AzureDoom replied to your comment on LevelingCore.</div>
                    <div className="text-[10px] text-slate-500 mt-2 font-mono">2 hours ago</div>
                </div>
            </div>
        </div>
    </div>
);

export const Home: React.FC = () => {
    const [allMods, setAllMods] = useState<Mod[]>([]);
    const [stats, setStats] = useState({ totalProjects: 0, totalDownloads: 0, totalUsers: 0 });
    const [chartConfig, setChartConfig] = useState({ viewsHidden: false, downloadsHidden: false });

    useEffect(() => {
        const fetchMods = async () => {
            try {
                const res = await api.get('/projects', { params: { size: 50, sort: 'trending' } });
                setAllMods(res.data?.content || []);
            } catch (err) {
                console.error("Failed to fetch mods", err);
            }
        };

        const fetchStats = async () => {
            try {
                const res = await api.get('/analytics/stats');
                setStats(res.data);
            } catch (err) {
                console.error("Failed to fetch platform stats", err);
            }
        };

        fetchMods();
        fetchStats();
    }, []);

    const chartData = [
        {
            id: 'views', label: 'Project Views', color: '#3b82f6', hidden: chartConfig.viewsHidden,
            data: [{ date: 'Mon', value: 120 }, { date: 'Tue', value: 240 }, { date: 'Wed', value: 180 }, { date: 'Thu', value: 450 }, { date: 'Fri', value: 390 }, { date: 'Sat', value: 680 }, { date: 'Sun', value: 850 }]
        },
        {
            id: 'downloads', label: 'Downloads', color: '#10b981', hidden: chartConfig.downloadsHidden,
            data: [{ date: 'Mon', value: 50 }, { date: 'Tue', value: 80 }, { date: 'Wed', value: 110 }, { date: 'Thu', value: 320 }, { date: 'Fri', value: 210 }, { date: 'Sat', value: 450 }, { date: 'Sun', value: 600 }]
        }
    ];

    const toggleDataset = (id: string) => {
        setChartConfig(prev => ({
            ...prev,
            viewsHidden: id === 'views' ? !prev.viewsHidden : prev.viewsHidden,
            downloadsHidden: id === 'downloads' ? !prev.downloadsHidden : prev.downloadsHidden
        }));
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

    const displayFeaturedMods = validFeaturedMods.slice(0, 10);
    const col1Mods = displayFeaturedMods.filter((_, i) => i % 2 === 0);
    const col2Mods = displayFeaturedMods.filter((_, i) => i % 2 === 1);

    const scrollingCol1 = [...col1Mods, ...col1Mods, ...col1Mods, ...col1Mods];
    const scrollingCol2 = [...col2Mods, ...col2Mods, ...col2Mods, ...col2Mods];

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-[#0B1120] text-slate-900 dark:text-slate-300 relative selection:bg-modtale-accent selection:text-white overflow-x-hidden transition-colors duration-300">
            <Helmet>
                <title>Modtale - The Hytale Community Repository</title>
                <meta name="description" content="The community repository for Hytale. Discover, download, and share Hytale mods, worlds, plugins, asset packs, and modpacks." />
                <style>{`
                    @keyframes scroll-vertical {
                        0% { transform: translateY(0); }
                        100% { transform: translateY(-50%); }
                    }
                    .animate-scroll-vertical {
                        animation: scroll-vertical linear infinite;
                    }
                `}</style>
            </Helmet>

            <main className="relative z-10">
                <section className="relative w-full min-h-[90vh] flex flex-col items-center justify-center pt-24 lg:pt-32 pb-16 border-b border-slate-200 dark:border-white/5">
                    <div className="absolute inset-0 bg-[radial-gradient(#cbd5e1_1px,transparent_1px)] dark:bg-[radial-gradient(rgba(255,255,255,0.08)_1px,transparent_1px)] [background-size:28px_28px] opacity-70 [mask-image:radial-gradient(ellipse_at_center,black_10%,transparent_80%)] pointer-events-none transition-colors duration-300" />
                    <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[600px] bg-modtale-accent/10 dark:bg-modtale-accent/15 rounded-full blur-[150px] mix-blend-multiply dark:mix-blend-screen pointer-events-none transition-colors duration-300" />

                    <div className="relative z-20 w-full max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-16 items-center overflow-hidden">

                        <div className="flex flex-col items-center lg:items-start text-center lg:text-left w-full max-w-4xl animate-in fade-in duration-1000">
                            <img
                                src="/assets/logo_light.svg"
                                alt="Modtale Logo"
                                className="h-16 md:h-20 lg:h-24 mb-8 object-contain drop-shadow-[0_0_20px_rgba(59,130,246,0.3)] hidden dark:block ml-1 lg:ml-2"
                            />
                            <img
                                src="/assets/logo.svg"
                                alt="Modtale Logo"
                                className="h-16 md:h-20 lg:h-24 mb-8 object-contain drop-shadow-sm block dark:hidden ml-1 lg:ml-2"
                            />

                            <h1 className="text-5xl md:text-7xl xl:text-8xl font-black text-slate-900 dark:text-white tracking-tighter leading-[1.05] mb-6">
                                The Hytale <br className="hidden md:block" />
                                <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-600 to-modtale-accent dark:from-modtale-accent dark:to-blue-300">
                                    Community Repository.
                                </span>
                            </h1>

                            <p className="text-lg text-slate-600 dark:text-slate-400 max-w-2xl lg:max-w-xl mb-10 font-medium leading-relaxed">
                                The community repository for Hytale. Discover, download, and share Hytale mods, worlds, plugins, asset packs, and modpacks.
                            </p>

                            <div className="flex flex-col sm:flex-row items-center gap-4 w-full sm:w-auto mb-10">
                                <Link
                                    to="/mods"
                                    className="flex items-center justify-center px-10 h-14 bg-modtale-accent text-white font-bold rounded-2xl hover:bg-modtale-accentHover transition-all shadow-[0_0_30px_rgba(59,130,246,0.3)] w-full sm:w-auto text-base backdrop-blur-md"
                                >
                                    <Search className="w-5 h-5 mr-2" />
                                    Discover Projects
                                </Link>
                                <Link
                                    to="/upload"
                                    className="flex items-center justify-center px-10 h-14 bg-white dark:bg-white/5 border border-slate-200 dark:border-white/10 text-slate-900 dark:text-white font-bold rounded-2xl hover:bg-slate-100 dark:hover:bg-white/10 transition-all w-full sm:w-auto text-base backdrop-blur-md shadow-sm"
                                >
                                    <Upload className="w-5 h-5 mr-2 opacity-70" />
                                    Publish Work
                                </Link>
                            </div>

                            <div className="flex items-center justify-center lg:justify-start gap-8 sm:gap-12 w-full pt-8 border-t border-slate-200 dark:border-white/10">
                                <div className="flex flex-col items-center lg:items-start">
                                    <div className="text-3xl sm:text-4xl font-black text-slate-900 dark:text-white">
                                        <AnimatedCounter value={stats.totalProjects} />
                                    </div>
                                    <div className="text-[10px] font-bold text-slate-500 uppercase tracking-widest mt-1">Projects</div>
                                </div>
                                <div className="w-px h-10 bg-slate-200 dark:bg-white/10" />
                                <div className="flex flex-col items-center lg:items-start">
                                    <div className="text-3xl sm:text-4xl font-black text-slate-900 dark:text-white">
                                        <AnimatedCounter value={stats.totalDownloads} />
                                    </div>
                                    <div className="text-[10px] font-bold text-slate-500 uppercase tracking-widest mt-1">Downloads</div>
                                </div>
                                <div className="w-px h-10 bg-slate-200 dark:bg-white/10 hidden sm:block" />
                                <div className="flex flex-col items-center lg:items-start hidden sm:flex">
                                    <div className="text-3xl sm:text-4xl font-black text-slate-900 dark:text-white">
                                        <AnimatedCounter value={stats.totalUsers} />
                                    </div>
                                    <div className="text-[10px] font-bold text-slate-500 uppercase tracking-widest mt-1">Creators</div>
                                </div>
                            </div>
                        </div>

                        {displayFeaturedMods.length > 0 && (
                            <div
                                className="relative h-[500px] lg:h-[700px] hidden md:flex gap-4 lg:gap-6 justify-end overflow-hidden scroll-container animate-in fade-in slide-in-from-right-8 duration-1000 delay-300 w-full"
                                style={{
                                    maskImage: 'linear-gradient(to bottom, transparent, black 10%, black 90%, transparent)',
                                    WebkitMaskImage: 'linear-gradient(to bottom, transparent, black 10%, black 90%, transparent)'
                                }}
                            >
                                <div className="flex flex-col gap-4 lg:gap-6 w-full max-w-[300px] animate-scroll-vertical" style={{ animationDuration: '40s' }}>
                                    {scrollingCol1.map((mod, index) => (
                                        <FeaturedModCard key={`c1-${mod.id}-${index}`} mod={mod} />
                                    ))}
                                </div>

                                <div className="flex flex-col gap-4 lg:gap-6 w-full max-w-[300px] animate-scroll-vertical" style={{ animationDuration: '48s' }}>
                                    {scrollingCol2.map((mod, index) => (
                                        <FeaturedModCard key={`c2-${mod.id}-${index}`} mod={mod} />
                                    ))}
                                </div>
                            </div>
                        )}

                        {displayFeaturedMods.length > 0 && (
                            <div className="w-full flex flex-col gap-6 md:hidden animate-in fade-in slide-in-from-bottom-8 duration-1000 delay-300 mt-8">
                                {displayFeaturedMods.slice(0, 3).map((mod, index) => (
                                    <FeaturedModCard key={`mobile-${mod.id}-${index}`} mod={mod} />
                                ))}
                            </div>
                        )}
                    </div>
                </section>

                <div className="max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 space-y-32 py-32">

                    <div className="flex flex-col lg:flex-row items-center gap-12 lg:gap-24">
                        <div className="flex-1 space-y-6">
                            <h2 className="text-3xl md:text-5xl font-black text-slate-900 dark:text-white tracking-tight">Install with confidence.</h2>
                            <p className="text-lg text-slate-600 dark:text-slate-400 leading-relaxed font-medium">
                                Finding the right file shouldn't be a puzzle. Modtale automatically matches projects to your game version makes it easy to find changelogs before you commit to a download.
                            </p>
                            <Link to="/mods" className="inline-flex items-center font-bold text-modtale-accent hover:text-modtale-accentHover dark:hover:text-white transition-colors group">
                                Start browsing <ChevronRight className="w-4 h-4 ml-1 group-hover:translate-x-1 transition-transform" />
                            </Link>
                        </div>
                        <div className="flex-1 w-full relative">
                            <div className="absolute -inset-4 bg-gradient-to-tr from-blue-200 dark:from-modtale-accent/20 to-transparent blur-2xl rounded-full z-0 opacity-50 pointer-events-none" />
                            <div className="relative z-10 w-full max-w-md ml-auto">
                                <InlineDownloadUI />
                            </div>
                        </div>
                    </div>

                    <div className="flex flex-col lg:flex-row-reverse items-center gap-12 lg:gap-24">
                        <div className="flex-1 space-y-6">
                            <h2 className="text-3xl md:text-5xl font-black text-slate-900 dark:text-white tracking-tight">Automated dependencies.</h2>
                            <p className="text-lg text-slate-600 dark:text-slate-400 leading-relaxed font-medium">
                                Forget hunting down core libraries. Modtale allows you to seamlessly download required and optional additions in one swift action.
                            </p>
                        </div>
                        <div className="flex-1 w-full relative">
                            <div className="absolute -inset-4 bg-gradient-to-tl from-emerald-200 dark:from-emerald-500/20 to-transparent blur-2xl rounded-full z-0 opacity-50 pointer-events-none" />
                            <div className="relative z-10 w-full max-w-md mr-auto">
                                <InlineDependencyUI randomMod={randomDisplayMod} />
                            </div>
                        </div>
                    </div>

                    <div className="flex flex-col lg:flex-row items-center gap-12 lg:gap-24">
                        <div className="flex-1 space-y-6">
                            <h2 className="text-3xl md:text-5xl font-black text-slate-900 dark:text-white tracking-tight">Measure your impact.</h2>
                            <p className="text-lg text-slate-600 dark:text-slate-400 leading-relaxed font-medium">
                                Creators get access to powerful, privacy-respecting analytics. Track your daily downloads, page views, and week-over-week growth metrics instantly from your dashboard.
                            </p>
                            <Link to="/upload" className="inline-flex items-center font-bold text-modtale-accent hover:text-modtale-accentHover dark:hover:text-white transition-colors group">
                                Publish your project <ChevronRight className="w-4 h-4 ml-1 group-hover:translate-x-1 transition-transform" />
                            </Link>
                        </div>
                        <div className="flex-1 w-full relative">
                            <div className="absolute -inset-4 bg-gradient-to-tr from-purple-200 dark:from-purple-500/20 to-transparent blur-2xl rounded-full z-0 opacity-50 pointer-events-none" />
                            <div className="relative z-10 w-full max-w-lg ml-auto bg-white dark:bg-slate-900/80 border border-slate-200 dark:border-white/10 rounded-2xl p-6 shadow-2xl h-[350px] backdrop-blur-md transform transition-transform hover:scale-[1.02] duration-500">
                                <div className="flex items-center gap-2 mb-6">
                                    <BarChart3 className="w-5 h-5 text-purple-600 dark:text-purple-400" />
                                    <span className="font-bold text-slate-900 dark:text-white">Project Growth</span>
                                </div>
                                <div className="h-64 pointer-events-none">
                                    <LineChart datasets={chartData} onToggle={toggleDataset} />
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="flex flex-col lg:flex-row-reverse items-center gap-12 lg:gap-24">
                        <div className="flex-1 space-y-6">
                            <h2 className="text-3xl md:text-5xl font-black text-slate-900 dark:text-white tracking-tight">Always in the loop.</h2>
                            <p className="text-lg text-slate-600 dark:text-slate-400 leading-relaxed font-medium">
                                Modtale keeps the community connected. Receive real-time alerts when tracked projects drop new updates, or when creators reply directly to your feedback.
                            </p>
                        </div>
                        <div className="flex-1 w-full relative">
                            <div className="absolute -inset-4 bg-gradient-to-tl from-amber-200 dark:from-amber-500/20 to-transparent blur-2xl rounded-full z-0 opacity-50 pointer-events-none" />
                            <div className="relative z-10 w-full max-w-md mr-auto">
                                <InlineNotificationUI />
                            </div>
                        </div>
                    </div>

                </div>

                <section className="py-24 border-t border-slate-200 dark:border-white/5 bg-slate-100/50 dark:bg-white/[0.01]">
                    <div className="max-w-4xl mx-auto px-4 text-center">
                        <Code className="w-12 h-12 text-slate-400 dark:text-slate-600 mx-auto mb-6" />
                        <h2 className="text-3xl font-black text-slate-900 dark:text-white mb-6 tracking-tight">Built by the community, for the community.</h2>
                        <p className="text-lg text-slate-600 dark:text-slate-400 mb-10 font-medium max-w-2xl mx-auto leading-relaxed">
                            Modtale is 100% open-source. We believe a modding repository should exist purely to serve its ecosystem.
                        </p>
                        <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
                            <a href="https://github.com/Modtale/modtale" target="_blank" rel="noreferrer" className="inline-flex items-center justify-center px-6 py-3 bg-white dark:bg-white/5 border border-slate-200 dark:border-white/10 text-slate-900 dark:text-white font-bold rounded-xl hover:bg-slate-50 dark:hover:bg-white/10 transition-colors gap-2 w-full sm:w-auto shadow-sm">
                                <Github className="w-5 h-5" /> View Source Code
                            </a>
                            <Link to="/api-docs" className="inline-flex items-center justify-center px-6 py-3 bg-blue-50 dark:bg-modtale-accent/10 border border-blue-200 dark:border-modtale-accent/20 text-blue-700 dark:text-modtale-accent font-bold rounded-xl hover:bg-blue-100 dark:hover:bg-modtale-accent/20 transition-colors gap-2 w-full sm:w-auto">
                                <Code className="w-5 h-5" /> View API Docs
                            </Link>
                        </div>
                    </div>
                </section>

            </main>
        </div>
    );
};