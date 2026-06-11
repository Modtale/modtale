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
import { DependencyModal } from '@/modules/project/components/dialogs/DependencyModal';
import { DownloadModal } from '@/modules/project/components/dialogs/DownloadModal';
import { HistoryModal } from '@/modules/project/components/dialogs/HistoryModal';

export const InlineDependencyUI = ({ randomProject }: { randomProject?: Project }) => {
    const mockDeps = useMemo(() => [
        { projectId: 'hytale-core', projectTitle: 'Hytale Core Library', isOptional: false, isEmbedded: false, versionNumber: '1.2.0' },
        { projectId: 'mathlib', projectTitle: 'MathLib', isOptional: false, isEmbedded: false, versionNumber: '2.1.0' },
        ...(randomProject ? [{ projectId: randomProject.id, projectTitle: randomProject.title, isOptional: true, isEmbedded: false, versionNumber: randomProject.versions?.[0]?.versionNumber || '1.0.0' }] : [])
    ], [randomProject]);

    const initialMetaCache = useMemo(() => {
        const cache: Record<string, { title: string; author: string; icon: string }> = {
            'hytale-core': { title: 'Hytale Core Library', author: 'Modtale Team', icon: '/assets/favicon.svg' },
            'mathlib': { title: 'MathLib', author: 'Unknown', icon: '' }
        };
        if (randomProject) {
            cache[randomProject.id] = {
                title: randomProject.title,
                author: randomProject.author || 'Unknown',
                icon: randomProject.imageUrl || ''
            };
        }
        return cache;
    }, [randomProject]);

    return (
        <DependencyModal
            dependencies={mockDeps as any}
            onClose={() => {}}
            onDownloadBundle={() => {}}
            onDownloadProjectOnly={() => {}}
            isInline={true}
            initialMetaCache={initialMetaCache}
            initialSelected={['hytale-core']}
        />
    );
};

export const InlineDownloadUI = () => {
    const [view, setView] = useState<'download' | 'changelog'>('download');
    const [showExperimental, setShowExperimental] = useState(false);

    const orderedGameVersions = useMemo(() => ['0.6.0-pre.2', '0.6.0-pre.1.1', '0.6.0-pre.1', '0.5.4', '0.5.3', '0.5.2', '0.5.1', '0.5.0', '0.5.0-pre.9.2', '0.5.0-pre.9.1'], []);
    const preReleaseGameVersions = useMemo(() => ['0.6.0-pre.2', '0.6.0-pre.1.1', '0.6.0-pre.1', '0.5.0-pre.9.2', '0.5.0-pre.9.1'], []);

    const mockVersions = useMemo(() => {
        const now = new Date();
        const raw = [
            { id: 'v18', versionNumber: '3.2.0-beta.1', channel: 'BETA', gameVersion: '0.5.4', releaseDate: new Date(now.getTime() - 2 * 60 * 1000).toISOString(), changelog: 'Experimental beta testing the upcoming 3.2.0 update features.', fileUrl: '#', dependencies: [] },
            { id: 'v17', versionNumber: '3.2.0-alpha.1', channel: 'ALPHA', gameVersion: '0.5.4', releaseDate: new Date(now.getTime() - 5 * 60 * 1000).toISOString(), changelog: 'Early access alpha testing for the new major update.', fileUrl: '#', dependencies: [] },
            { id: 'v16', versionNumber: '3.1.1', channel: 'RELEASE', gameVersion: '0.5.4', releaseDate: new Date(now.getTime() - 10 * 60 * 1000).toISOString(), changelog: 'Stable release for Hytale 0.5.4. Re-balanced magic, polished textures, and updated default server configuration.', fileUrl: '#', dependencies: [] },
            { id: 'v15', versionNumber: '3.1.0', channel: 'RELEASE', gameVersion: '0.5.4', releaseDate: new Date(now.getTime() - 25 * 60 * 1000).toISOString(), changelog: 'Initial support for Hytale 0.5.4. Complete compatibility pass and UI refinements.', fileUrl: '#', dependencies: [] },
            { id: 'v9', versionNumber: '3.1.0-pre.2', channel: 'BETA', gameVersion: '0.6.0-pre.2', releaseDate: new Date(now.getTime() - 40 * 60 * 1000).toISOString(), changelog: 'Latest Hytale prerelease preview. Stabilized menus, polished world loading, and one more pass on particles.', fileUrl: '#', dependencies: [] },
            { id: 'v8', versionNumber: '3.1.0-pre.1.1', channel: 'ALPHA', gameVersion: '0.6.0-pre.1.1', releaseDate: new Date(now.getTime() - 90 * 60 * 1000).toISOString(), changelog: 'Hotfix preview for the next Hytale build. Focused on crash recovery and startup stability.', fileUrl: '#', dependencies: [] },
            { id: 'v7', versionNumber: '3.1.0-pre.1', channel: 'ALPHA', gameVersion: '0.6.0-pre.1', releaseDate: new Date(now.getTime() - 3 * 60 * 60 * 1000).toISOString(), changelog: 'Early Hytale prerelease with the new rendering pipeline. Expect rough edges.', fileUrl: '#', dependencies: [] },
            { id: 'v14', versionNumber: '3.0.5', channel: 'RELEASE', gameVersion: '0.5.4', releaseDate: new Date(now.getTime() - 6 * 60 * 60 * 1000).toISOString(), changelog: 'Hotfix release resolving UI scaling issues on ultra-wide monitors.', fileUrl: '#', dependencies: [] },
            { id: 'v13', versionNumber: '3.0.4-beta.2', channel: 'BETA', gameVersion: '0.5.4', releaseDate: new Date(now.getTime() - 10 * 60 * 1000).toISOString(), changelog: 'Testing multi-threaded particle calculations for high-density environments.', fileUrl: '#', dependencies: [] },
            { id: 'v6', versionNumber: '3.0.4', channel: 'RELEASE', gameVersion: '0.5.4', releaseDate: new Date(now.getTime() - 12 * 60 * 60 * 1000).toISOString(), changelog: 'Hytale release branch update. Minor localization fixes and a few stability improvements.', fileUrl: '#', dependencies: [] },
            { id: 'v5', versionNumber: '3.0.3', channel: 'RELEASE', gameVersion: '0.5.3', releaseDate: new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString(), changelog: 'Compatibility update for Hytale 0.5.3. Added new dynamic lighting and UI polish.', fileUrl: '#', dependencies: [] },
            { id: 'v12', versionNumber: '3.0.3-beta.1', channel: 'BETA', gameVersion: '0.5.3', releaseDate: new Date(now.getTime() - 28 * 60 * 60 * 1000).toISOString(), changelog: 'Early beta test of dynamic weather-influenced lighting values.', fileUrl: '#', dependencies: [] },
            { id: 'v11', versionNumber: '3.0.2', channel: 'RELEASE', gameVersion: '0.5.2', releaseDate: new Date(now.getTime() - 3 * 24 * 60 * 60 * 1000).toISOString(), changelog: 'Release build featuring stability improvements and durability fixes.', fileUrl: '#', dependencies: [] },
            { id: 'v4', versionNumber: '3.0.2-beta', channel: 'BETA', gameVersion: '0.5.2', releaseDate: new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString(), changelog: 'Testing new durability mechanics against Hytale 0.5.2. Expect bugs.', fileUrl: '#', dependencies: [] },
            { id: 'v3', versionNumber: '3.0.1', channel: 'RELEASE', gameVersion: '0.5.1', releaseDate: new Date(now.getTime() - 14 * 24 * 60 * 60 * 1000).toISOString(), changelog: 'Added new elemental wand effects and fixed visual bugs with particle effects.', fileUrl: '#', dependencies: [] },
            { id: 'v10', versionNumber: '3.0.0-pre.1', channel: 'ALPHA', gameVersion: '0.5.0', releaseDate: new Date(now.getTime() - 28 * 24 * 60 * 60 * 1000).toISOString(), changelog: 'Alpha testing for the overhauled skill tree system.', fileUrl: '#', dependencies: [] },
            { id: 'v2', versionNumber: '3.0.0', channel: 'RELEASE', gameVersion: '0.5.0', releaseDate: new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString(), changelog: 'Initial release of the expanded magic system for Hytale 0.5.0.', fileUrl: '#', dependencies: [] },
            { id: 'v1', versionNumber: '2.9.9', channel: 'RELEASE', gameVersion: '0.5.0-pre.9.2', releaseDate: new Date(now.getTime() - 60 * 24 * 60 * 60 * 1000).toISOString(), changelog: 'Final update for the old magic system before the Hytale prerelease branch changed over.', fileUrl: '#', dependencies: [] }
        ];
        return raw.map(v => ({ ...v, gameVersions: [v.gameVersion] }));
    }, []);

    const versionsByGame = useMemo(() => {
        const grouped: Record<string, any[]> = {};
        for (const v of mockVersions) {
            if (!grouped[v.gameVersion]) grouped[v.gameVersion] = [];
            grouped[v.gameVersion].push(v);
        }
        return grouped;
    }, [mockVersions]);

    const [measuredHeight, setMeasuredHeight] = useState<number | null>(null);
    const downloadRef = React.useCallback((node: HTMLDivElement | null) => {
        if (node && !measuredHeight) {
            const rect = node.getBoundingClientRect();
            if (rect.height > 0) {
                setMeasuredHeight(rect.height);
            }
        }
    }, [measuredHeight]);

    if (view === 'changelog') {
        return (
            <HistoryModal
                show={true}
                onClose={() => setView('download')}
                history={mockVersions}
                showExperimental={showExperimental}
                onToggleExperimental={() => setShowExperimental(!showExperimental)}
                onDownload={() => {}}
                isInline={true}
                inlineHeight={measuredHeight || undefined}
            />
        );
    }

    return (
        <DownloadModal
            show={true}
            onClose={() => {}}
            versionsByGame={versionsByGame}
            preReleaseGameVersions={preReleaseGameVersions}
            orderedGameVersions={orderedGameVersions}
            onDownload={() => {}}
            showExperimental={showExperimental}
            onToggleExperimental={() => setShowExperimental(!showExperimental)}
            onViewHistory={() => setView('changelog')}
            isInline={true}
            containerRef={downloadRef}
        />
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
            <h3 className="text-slate-500 dark:text-slate-400 text-[9px] font-black uppercase tracking-widest mb-0.5">{title}</h3>
            <div className="text-2xl sm:text-3xl font-black text-slate-900 dark:text-white tracking-tighter leading-none">
                {value}{isPercent && <span className="text-lg text-slate-400 ml-0.5">%</span>}
            </div>
                                            {subValue && <div className="text-[10px] text-slate-500 dark:text-slate-400 mt-1.5 font-medium">{subValue}</div>}
        </div>
    </div>
);



const InlineAnalyticsUI = ({ showConversionRate = true }: { showConversionRate?: boolean }) => {
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
            <div className={`grid grid-cols-2 ${showConversionRate ? 'md:grid-cols-3' : ''} gap-4`}>
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
                {showConversionRate && (
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
                )}
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
            .catch(() => {});
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
    return (
        <div className="relative w-full overflow-hidden">
            <div className="flex gap-4 overflow-x-auto pb-4 pt-2 scrollbar-none snap-x snap-mandatory w-full">
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
                    className="text-xs font-bold text-slate-500 hover:text-blue-600 dark:text-slate-400 dark:hover:text-blue-400 transition-colors flex items-center gap-0.5 shrink-0 pb-1.5"
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
                    className="text-xs font-bold text-slate-500 hover:text-emerald-600 dark:text-slate-400 dark:hover:text-emerald-400 transition-colors flex items-center gap-0.5 shrink-0 pb-1.5"
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

export const ProjectAnalyticsSection = ({ showConversionRate = true }: { showConversionRate?: boolean }) => {
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
                <InlineAnalyticsUI showConversionRate={showConversionRate} />
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
