import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import {
    Search, Upload, ChevronRight, Check,
    AlertCircle, Download, Link as LinkIcon, Bell,
    Zap, BarChart3, Box, FileText, ChevronDown,
    Github, Code, X, List
} from 'lucide-react';
import { ModCard } from '../components/resources/ModCard';
import { LineChart } from '../components/ui/charts/LineChart';
import type { Mod } from '../types';
import { api, BACKEND_URL } from '../utils/api';
import { createSlug } from '../utils/slug';

const MiniModCard = ({ mod }: { mod: Mod }) => {
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
            className="flex flex-col rounded-2xl bg-slate-900/90 border border-white/10 w-72 shrink-0 backdrop-blur-md group transition-transform hover:scale-105 hover:border-white/20 overflow-hidden shadow-xl"
        >
            <div className="h-14 w-full bg-slate-800 relative border-b border-white/5">
                {bannerUrl && <img src={bannerUrl} alt="" className="w-full h-full object-cover opacity-60 group-hover:opacity-100 transition-opacity" loading="lazy" />}
            </div>
            <div className="px-4 pb-4 relative">
                <img
                    src={iconUrl}
                    alt=""
                    className="w-12 h-12 rounded-xl object-cover bg-slate-900 shadow-lg absolute -top-6 border-2 border-slate-900 group-hover:scale-110 transition-transform"
                    onError={(e) => e.currentTarget.src = '/assets/favicon.svg'}
                />
                <div className="mt-8">
                    <div className="text-sm font-bold text-white truncate group-hover:text-modtale-accent transition-colors">{mod.title}</div>
                    <div className="text-[10px] font-medium text-slate-400 truncate mt-0.5">by {mod.author}</div>
                    <div className="flex items-center gap-1.5 mt-2.5 text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                        <Download className="w-3.5 h-3.5 text-slate-600"/>
                        {mod.downloadCount?.toLocaleString() || 0}
                    </div>
                </div>
            </div>
        </Link>
    );
};

const VerticalScrollingMarquee = ({ mods, direction, speed }: { mods: Mod[], direction: 'up' | 'down', speed: number }) => {
    if (!mods || mods.length === 0) return null;

    const multiplied = [...mods, ...mods, ...mods, ...mods];

    return (
        <div className="flex flex-col h-full w-auto select-none px-4 p-4 -m-4">
            <div
                className={`flex flex-col gap-6 h-max w-72 ${direction === 'up' ? 'animate-marquee-up' : 'animate-marquee-down'} group-hover:[animation-play-state:paused]`}
                style={{ animationDuration: `${speed}s` }}
            >
                {multiplied.map((mod, i) => (
                    <MiniModCard key={`${mod.id}-${i}`} mod={mod} />
                ))}
            </div>
        </div>
    );
};

const InlineDependencyUI = () => (
    <div className="bg-slate-900/80 border border-white/10 rounded-2xl w-full shadow-2xl overflow-hidden flex flex-col h-[350px] backdrop-blur-md transform transition-transform hover:scale-[1.02] duration-500">
        <div className="p-5 border-b border-white/10 flex justify-between items-center bg-white/[0.02]">
            <h3 className="font-bold text-white flex items-center gap-2">
                <LinkIcon className="w-4 h-4 text-modtale-accent" /> Dependencies
            </h3>
        </div>
        <div className="p-5 space-y-3 overflow-hidden relative flex-1">
            <div className="absolute inset-x-0 bottom-0 h-32 bg-gradient-to-t from-slate-900 to-transparent z-10 pointer-events-none" />

            <div className="flex items-center justify-between p-3 rounded-xl border border-modtale-accent/30 bg-modtale-accent/5">
                <div className="flex items-center gap-3">
                    <div className="w-5 h-5 rounded bg-modtale-accent text-white flex items-center justify-center shrink-0">
                        <Check className="w-3.5 h-3.5" />
                    </div>
                    <div className="w-10 h-10 rounded-lg bg-slate-800 border border-white/5 flex items-center justify-center shrink-0">
                        <Box className="w-5 h-5 text-slate-500" />
                    </div>
                    <div>
                        <div className="font-bold text-white text-sm">Hytale Core Library</div>
                        <div className="text-xs text-slate-400 font-mono mt-0.5">v1.2.0</div>
                    </div>
                </div>
                <span className="text-[10px] font-bold uppercase bg-amber-500/10 text-amber-500 px-2 py-1 rounded-md border border-amber-500/20">Required</span>
            </div>

            <div className="flex items-center justify-between p-3 rounded-xl border border-white/10 bg-white/5">
                <div className="flex items-center gap-3">
                    <div className="w-5 h-5 rounded border border-slate-600 bg-slate-800 shrink-0" />
                    <div className="w-10 h-10 rounded-lg bg-slate-800 border border-white/5 flex items-center justify-center shrink-0">
                        <Box className="w-5 h-5 text-slate-500" />
                    </div>
                    <div>
                        <div className="font-bold text-white text-sm">Dynamic Weather</div>
                        <div className="text-xs text-slate-400 font-mono mt-0.5">v3.0.1</div>
                    </div>
                </div>
                <span className="text-[10px] font-bold uppercase bg-white/10 text-slate-400 px-2 py-1 rounded-md border border-white/5">Optional</span>
            </div>

            <div className="flex items-start gap-2 text-xs text-amber-400 bg-amber-500/10 p-3 rounded-lg border border-amber-500/20">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                <p>Some <span className="font-bold">Required</span> dependencies are missing.</p>
            </div>
        </div>
    </div>
);

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
            case 'BETA': return 'bg-blue-500/20 text-blue-200 border-blue-500/30';
            case 'ALPHA': return 'bg-orange-500/20 text-orange-200 border-orange-500/30';
            default: return 'bg-white/10 border-white/20 text-white';
        }
    };

    if (view === 'changelog') {
        return (
            <div className="bg-slate-900/90 border border-white/10 rounded-2xl w-full shadow-2xl overflow-hidden flex flex-col h-[350px] backdrop-blur-md transform transition-transform hover:scale-[1.02] duration-500">
                <div className="p-5 border-b border-white/10 flex justify-between items-center bg-black/20 shrink-0">
                    <div>
                        <h3 className="text-lg font-black text-white flex items-center gap-2"><List className="w-5 h-5 text-modtale-accent" /> Changelog</h3>
                        <div className="mt-1 flex items-center gap-2 cursor-pointer group" onClick={() => setShowExperimental(!showExperimental)}>
                            <div className={`w-8 h-4 rounded-full relative transition-colors ${showExperimental ? 'bg-modtale-accent' : 'bg-slate-700'}`}>
                                <div className={`absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform ${showExperimental ? 'translate-x-4' : ''}`} />
                            </div>
                            <span className="text-[10px] font-bold text-slate-500 uppercase group-hover:text-slate-300 transition-colors">Show Beta/Alpha</span>
                        </div>
                    </div>
                    <button onClick={() => setView('download')} className="p-2 rounded-full hover:bg-white/10 text-slate-500 transition-colors"><X className="w-5 h-5" /></button>
                </div>

                <div className="p-5 overflow-y-auto custom-scrollbar flex-1 space-y-4 relative">
                    {visibleVersions.map(ver => (
                        <div key={ver.id} className="bg-slate-800/50 border border-white/5 rounded-xl p-4 shadow-sm hover:border-white/10 transition-colors">
                            <div className="flex items-center justify-between gap-4 mb-3 border-b border-white/5 pb-3">
                                <div>
                                    <div className="flex items-center gap-2 mb-1">
                                        <span className="text-base font-black text-white">v{ver.versionNumber}</span>
                                        {ver.channel !== 'RELEASE' && <span className={`text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded border ${getVersionBadgeColor(ver.channel)}`}>{ver.channel}</span>}
                                    </div>
                                    <div className="flex items-center gap-2 text-[10px] font-bold text-slate-500 uppercase tracking-wide">
                                        <span>{ver.date}</span>
                                        <span className="w-1 h-1 rounded-full bg-slate-600"></span>
                                        <span>1.0.4</span>
                                    </div>
                                </div>
                                <button className="p-2 bg-white/5 hover:bg-modtale-accent hover:text-white text-slate-300 rounded-lg transition-all shrink-0">
                                    <Download className="w-4 h-4" />
                                </button>
                            </div>
                            <div className="text-xs text-slate-400 whitespace-pre-wrap leading-relaxed">
                                {ver.changelog}
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        );
    }

    return (
        <div className="bg-slate-900/90 border border-white/10 rounded-2xl w-full shadow-2xl overflow-hidden relative flex flex-col h-[350px] backdrop-blur-md transform transition-transform hover:scale-[1.02] duration-500">
            <div className="p-5 border-b border-white/10 flex justify-between items-center bg-black/20 shrink-0">
                <div>
                    <h3 className="text-lg font-black text-white flex items-center gap-2">
                        <Download className="w-5 h-5 text-modtale-accent" /> Download
                    </h3>
                    <div className="mt-1 flex items-center gap-2 group cursor-pointer" onClick={() => setShowExperimental(!showExperimental)}>
                        <div className={`w-8 h-4 rounded-full relative transition-colors ${showExperimental ? 'bg-modtale-accent' : 'bg-slate-700'}`}>
                            <div className={`absolute top-0.5 left-0.5 w-3 h-3 bg-white rounded-full transition-transform ${showExperimental ? 'translate-x-4' : ''}`} />
                        </div>
                        <span className="text-[10px] font-bold text-slate-500 uppercase group-hover:text-slate-300 transition-colors">Show Beta/Alpha</span>
                    </div>
                </div>
                <button className="p-2 rounded-full hover:bg-white/10 text-slate-500 transition-colors"><X className="w-5 h-5" /></button>
            </div>

            <div className="p-5 overflow-hidden relative flex-1 flex flex-col justify-center">
                <div className="absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-slate-900 to-transparent z-10 pointer-events-none" />

                <div className="mb-5 relative z-0 shrink-0">
                    <label className="block text-[10px] font-bold text-slate-400 uppercase mb-2 tracking-wider">Game Version</label>
                    <div className="w-full flex items-center justify-between p-3 bg-slate-800 border border-white/10 rounded-xl font-bold text-white text-sm cursor-pointer">
                        <span>1.0.4</span>
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

            <div className="p-4 bg-black/20 border-t border-white/10 text-center shrink-0 z-20">
                <button onClick={() => setView('changelog')} className="text-xs text-slate-400 hover:text-modtale-accent font-bold uppercase tracking-wider flex items-center justify-center gap-1 w-full transition-colors">
                    View Full Changelog <ChevronRight className="w-3 h-3" />
                </button>
            </div>
        </div>
    );
};

const InlineNotificationUI = () => (
    <div className="bg-slate-900/80 border border-white/10 rounded-2xl w-full shadow-2xl overflow-hidden flex flex-col h-[350px] backdrop-blur-md transform transition-transform hover:scale-[1.02] duration-500">
        <div className="p-5 border-b border-white/10 flex justify-between items-center bg-white/[0.02]">
            <h3 className="font-bold text-white flex items-center gap-2">
                <Bell className="w-4 h-4 text-modtale-accent" /> Notifications
            </h3>
            <span className="text-xs text-modtale-accent font-bold">Clear All</span>
        </div>
        <div className="divide-y divide-white/5 relative flex-1 overflow-hidden">
            <div className="absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-slate-900 to-transparent z-10 pointer-events-none" />

            <div className="p-5 bg-white/[0.03] flex items-start gap-4">
                <div className="w-10 h-10 rounded-lg bg-blue-500/20 text-blue-400 flex items-center justify-center shrink-0 border border-blue-500/30">
                    <Zap className="w-5 h-5" />
                </div>
                <div className="flex-1 min-w-0">
                    <div className="font-bold text-sm text-white mb-1 flex items-center">
                        Project Updated <span className="inline-block w-1.5 h-1.5 bg-modtale-accent rounded-full ml-2" />
                    </div>
                    <div className="text-xs text-slate-400">Magic Wands Expanded v2.0 is out now!</div>
                    <div className="text-[10px] text-slate-500 mt-2 font-mono">10 mins ago</div>
                </div>
            </div>

            <div className="p-5 flex items-start gap-4">
                <div className="w-10 h-10 rounded-lg bg-purple-500/20 text-purple-400 flex items-center justify-center shrink-0 font-black border border-purple-500/30">
                    P
                </div>
                <div className="flex-1 min-w-0">
                    <div className="font-bold text-sm text-slate-200 mb-1">
                        New Comment
                    </div>
                    <div className="text-xs text-slate-400">PlayerOne replied to your comment on Zone 3 Overhaul.</div>
                    <div className="text-[10px] text-slate-500 mt-2 font-mono">2 hours ago</div>
                </div>
            </div>
        </div>
    </div>
);

export const Home: React.FC = () => {
    const [allMods, setAllMods] = useState<Mod[]>([]);

    useEffect(() => {
        const fetchMods = async () => {
            try {
                const res = await api.get('/projects', { params: { size: 30, sort: 'popular' } });
                setAllMods(res.data?.content || []);
            } catch (err) {
                console.error("Failed to fetch mods", err);
            }
        };
        fetchMods();
    }, []);

    const row1 = allMods.filter((_, i) => i % 2 === 0);
    const row2 = allMods.filter((_, i) => i % 2 === 1);

    const chartData = [
        {
            id: 'growth', label: 'Project Views', color: '#3b82f6',
            data: [{ date: 'Mon', value: 120 }, { date: 'Tue', value: 240 }, { date: 'Wed', value: 180 }, { date: 'Thu', value: 450 }, { date: 'Fri', value: 390 }, { date: 'Sat', value: 680 }, { date: 'Sun', value: 850 }]
        }
    ];

    return (
        <div className="min-h-screen bg-[#0B1120] text-slate-300 relative selection:bg-modtale-accent selection:text-white overflow-x-hidden">
            <Helmet>
                <title>Modtale - The Hytale Community Repository</title>
                <meta name="description" content="Discover, download, and share Hytale mods, plugins, modpacks, and server resources." />
                <style>{`
                    @keyframes marquee-up { 0% { transform: translateY(0); } 100% { transform: translateY(-50%); } }
                    @keyframes marquee-down { 0% { transform: translateY(-50%); } 100% { transform: translateY(0); } }
                    .animate-marquee-up { animation: marquee-up linear infinite; }
                    .animate-marquee-down { animation: marquee-down linear infinite; }
                `}</style>
            </Helmet>

            <div className="absolute inset-0 z-0 pointer-events-none opacity-50">
                <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full h-[600px] bg-modtale-accent/10 rounded-full blur-[150px] mix-blend-screen" />
                <div className="absolute top-1/3 -right-40 w-[500px] h-[500px] bg-purple-500/10 rounded-full blur-[120px] mix-blend-screen" />
            </div>

            <main className="relative z-10">

                <section className="relative pt-32 pb-16 lg:pt-48 lg:pb-32 max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 flex flex-col lg:flex-row items-center justify-between gap-16 lg:gap-24 overflow-hidden lg:overflow-visible">

                    <div className="flex-1 flex flex-col items-center text-center lg:items-start lg:text-left z-20">
                        <img
                            src="/assets/logo_light.svg"
                            alt="Modtale Logo"
                            className="h-10 md:h-12 mb-10 animate-in fade-in duration-1000 object-contain drop-shadow-[0_0_20px_rgba(59,130,246,0.3)]"
                        />

                        <h1 className="text-5xl md:text-7xl xl:text-8xl font-black text-white tracking-tighter leading-[1.05] mb-10 animate-in slide-in-from-bottom-8 duration-700 delay-100">
                            The Hytale <br className="hidden lg:block" />
                            <span className="text-transparent bg-clip-text bg-gradient-to-r from-modtale-accent to-blue-300">
                                Community Repository.
                            </span>
                        </h1>

                        <div className="flex flex-col sm:flex-row items-center gap-4 w-full sm:w-auto animate-in fade-in duration-700 delay-300">
                            <Link
                                to="/mods"
                                className="flex items-center justify-center px-8 h-14 bg-modtale-accent text-white font-bold rounded-2xl hover:bg-modtale-accentHover transition-all shadow-[0_0_30px_rgba(59,130,246,0.3)] w-full sm:w-auto text-base backdrop-blur-md"
                            >
                                <Search className="w-5 h-5 mr-2" />
                                Discover Projects
                            </Link>
                            <Link
                                to="/upload"
                                className="flex items-center justify-center px-8 h-14 bg-white/5 border border-white/10 text-white font-bold rounded-2xl hover:bg-white/10 transition-all w-full sm:w-auto text-base backdrop-blur-md"
                            >
                                <Upload className="w-5 h-5 mr-2 opacity-70" />
                                Publish Work
                            </Link>
                        </div>
                    </div>

                    <div className="flex-1 w-full relative z-10 hidden lg:block h-[650px] overflow-hidden">
                        <div className="absolute top-0 inset-x-0 h-40 bg-gradient-to-b from-[#0B1120] via-[#0B1120]/80 to-transparent pointer-events-none z-20" />
                        <div className="absolute bottom-0 inset-x-0 h-40 bg-gradient-to-t from-[#0B1120] via-[#0B1120]/80 to-transparent pointer-events-none z-20" />

                        <div className="absolute inset-0 flex justify-end gap-4 opacity-100 transform translate-x-0">
                            <VerticalScrollingMarquee mods={row1} direction="up" speed={60} />
                            <VerticalScrollingMarquee mods={row2} direction="down" speed={70} />
                        </div>
                    </div>
                </section>

                <div className="max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 space-y-32 py-20">

                    <div className="flex flex-col lg:flex-row items-center gap-12 lg:gap-24">
                        <div className="flex-1 space-y-6">
                            <h2 className="text-3xl md:text-5xl font-black text-white tracking-tight">Install with confidence.</h2>
                            <p className="text-lg text-slate-400 leading-relaxed font-medium">
                                Finding the right file shouldn't be a puzzle. Modtale automatically matches projects to your game version and provides detailed, easy-to-read changelogs before you commit to a download.
                            </p>
                            <Link to="/mods" className="inline-flex items-center font-bold text-modtale-accent hover:text-white transition-colors group">
                                Start browsing <ChevronRight className="w-4 h-4 ml-1 group-hover:translate-x-1 transition-transform" />
                            </Link>
                        </div>
                        <div className="flex-1 w-full relative">
                            <div className="absolute -inset-4 bg-gradient-to-tr from-modtale-accent/20 to-transparent blur-2xl rounded-full z-0 opacity-50 pointer-events-none" />
                            <div className="relative z-10 w-full max-w-md ml-auto">
                                <InlineDownloadUI />
                            </div>
                        </div>
                    </div>

                    <div className="flex flex-col lg:flex-row-reverse items-center gap-12 lg:gap-24">
                        <div className="flex-1 space-y-6">
                            <h2 className="text-3xl md:text-5xl font-black text-white tracking-tight">Automated dependencies.</h2>
                            <p className="text-lg text-slate-400 leading-relaxed font-medium">
                                Forget hunting down core libraries. Modtale reads a project's dependency tree and allows you to seamlessly download required and optional additions in one swift action.
                            </p>
                        </div>
                        <div className="flex-1 w-full relative">
                            <div className="absolute -inset-4 bg-gradient-to-tl from-emerald-500/20 to-transparent blur-2xl rounded-full z-0 opacity-50 pointer-events-none" />
                            <div className="relative z-10 w-full max-w-md mr-auto">
                                <InlineDependencyUI />
                            </div>
                        </div>
                    </div>

                    <div className="flex flex-col lg:flex-row items-center gap-12 lg:gap-24">
                        <div className="flex-1 space-y-6">
                            <h2 className="text-3xl md:text-5xl font-black text-white tracking-tight">Measure your impact.</h2>
                            <p className="text-lg text-slate-400 leading-relaxed font-medium">
                                Creators get access to powerful, privacy-respecting analytics. Track your daily downloads, page views, and week-over-week growth metrics instantly from your dashboard.
                            </p>
                            <Link to="/upload" className="inline-flex items-center font-bold text-modtale-accent hover:text-white transition-colors group">
                                Publish your project <ChevronRight className="w-4 h-4 ml-1 group-hover:translate-x-1 transition-transform" />
                            </Link>
                        </div>
                        <div className="flex-1 w-full relative">
                            <div className="absolute -inset-4 bg-gradient-to-tr from-purple-500/20 to-transparent blur-2xl rounded-full z-0 opacity-50 pointer-events-none" />
                            <div className="relative z-10 w-full max-w-lg ml-auto bg-slate-900/80 border border-white/10 rounded-2xl p-6 shadow-2xl h-[350px] backdrop-blur-md transform transition-transform hover:scale-[1.02] duration-500">
                                <div className="flex items-center gap-2 mb-6">
                                    <BarChart3 className="w-5 h-5 text-purple-400" />
                                    <span className="font-bold text-white">Project Growth</span>
                                </div>
                                <div className="h-64 pointer-events-none">
                                    <LineChart datasets={chartData} />
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="flex flex-col lg:flex-row-reverse items-center gap-12 lg:gap-24">
                        <div className="flex-1 space-y-6">
                            <h2 className="text-3xl md:text-5xl font-black text-white tracking-tight">Always in the loop.</h2>
                            <p className="text-lg text-slate-400 leading-relaxed font-medium">
                                Modtale keeps the community connected. Receive real-time alerts when tracked projects drop new updates, or when creators reply directly to your feedback.
                            </p>
                        </div>
                        <div className="flex-1 w-full relative">
                            <div className="absolute -inset-4 bg-gradient-to-tl from-amber-500/20 to-transparent blur-2xl rounded-full z-0 opacity-50 pointer-events-none" />
                            <div className="relative z-10 w-full max-w-md mr-auto">
                                <InlineNotificationUI />
                            </div>
                        </div>
                    </div>

                </div>

                <section className="py-24 border-t border-white/5 bg-white/[0.01]">
                    <div className="max-w-4xl mx-auto px-4 text-center">
                        <Code className="w-12 h-12 text-slate-600 mx-auto mb-6" />
                        <h2 className="text-3xl font-black text-white mb-6 tracking-tight">Built by the community, for the community.</h2>
                        <p className="text-lg text-slate-400 mb-10 font-medium max-w-2xl mx-auto leading-relaxed">
                            Modtale is 100% open-source. We believe a modding repository should exist purely to serve its ecosystem.
                        </p>
                        <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
                            <a href="https://github.com/Modtale/modtale" target="_blank" rel="noreferrer" className="inline-flex items-center justify-center px-6 py-3 bg-white/5 border border-white/10 text-white font-bold rounded-xl hover:bg-white/10 transition-colors gap-2 w-full sm:w-auto">
                                <Github className="w-5 h-5" /> View Source Code
                            </a>
                            <Link to="/api-docs" className="inline-flex items-center justify-center px-6 py-3 bg-modtale-accent/10 border border-modtale-accent/20 text-modtale-accent font-bold rounded-xl hover:bg-modtale-accent/20 transition-colors gap-2 w-full sm:w-auto">
                                <Code className="w-5 h-5" /> View API Docs
                            </Link>
                        </div>
                    </div>
                </section>

            </main>
        </div>
    );
};