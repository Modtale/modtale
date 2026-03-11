import React from 'react';
import type { Mod } from '../../types';
import { Download, Calendar, Heart, Code, Paintbrush, Database, Layers, Layout, Box, Globe, ChevronRight } from 'lucide-react';
import { BACKEND_URL } from '../../utils/api';
import { Link } from 'react-router-dom';
import { getProjectUrl } from '../../utils/slug';
import { prefetchProject } from '../../utils/prefetch';

interface ModCardProps {
    mod: Mod;
    path?: string;
    isFavorite: boolean;
    onToggleFavorite: (modId: string) => void;
    isLoggedIn: boolean;
    onClick?: () => void;
    priority?: boolean;
    viewStyle?: 'grid' | 'list' | 'compact';
}

const getClassificationIcon = (cls: string) => {
    switch(cls) {
        case 'PLUGIN': return <Code className="w-4 h-4" />;
        case 'ART': return <Paintbrush className="w-4 h-4" />;
        case 'DATA': return <Database className="w-4 h-4" />;
        case 'SAVE': return <Globe className="w-4 h-4" />;
        case 'MODPACK': return <Layers className="w-4 h-4" />;
        default: return <Layout className="w-4 h-4" />;
    }
}

const toTitleCase = (str: string) => {
    if (!str) return '';
    if (str === 'SAVE') return 'World';
    return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
};

const formatTimeAgo = (dateString: string) => {
    if (!dateString) return null;
    const date = new Date(dateString);
    const now = new Date();
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    let interval = seconds / 31536000;
    if (interval > 1) return Math.floor(interval) + "y ago";
    interval = seconds / 2592000;
    if (interval > 1) return Math.floor(interval) + "mo ago";
    interval = seconds / 86400;
    if (interval > 1) return Math.floor(interval) + "d ago";
    interval = seconds / 3600;
    if (interval > 1) return Math.floor(interval) + "h ago";
    interval = seconds / 60;
    if (interval > 1) return Math.floor(interval) + "m ago";
    return "Just now";
};

export const ModCard: React.FC<ModCardProps> = React.memo(({ mod, path, isFavorite, onToggleFavorite, isLoggedIn, priority = false, onClick, viewStyle = 'grid' }) => {
    const title = mod.title || 'Untitled Project';
    const author = mod.author || 'Unknown';
    const canonicalPath = path || getProjectUrl(mod);
    const desc = mod.description ? mod.description : 'No description provided.';
    const classification = mod.classification || 'PLUGIN';

    const downloads = (mod.downloadCount || 0).toLocaleString();
    const favorites = (mod.favoriteCount || 0).toLocaleString();

    const timeAgo = formatTimeAgo(mod.updatedAt || '');
    const modCount = (mod.modIds || mod.childProjectIds || []).length;
    const displayClassification = toTitleCase(classification);

    const resolveUrl = (url: string) => {
        if (!url) return '';
        if (url.startsWith('/api')) return `${BACKEND_URL}${url}`;
        return url;
    };

    const resolvedImage = mod.imageUrl ? resolveUrl(mod.imageUrl) : '/assets/favicon.svg';
    const resolvedBanner = mod.bannerUrl ? resolveUrl(mod.bannerUrl) : null;

    const handleMouseEnter = () => {
        prefetchProject(mod.id);
    };

    if (viewStyle === 'compact') {
        return (
            <div
                onMouseEnter={handleMouseEnter}
                className="group relative flex items-center gap-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl p-3 hover:border-modtale-accent transition-all shadow-sm"
            >
                <Link to={canonicalPath} onClick={onClick} className="absolute inset-0 z-10" />
                <img src={resolvedImage} alt="" className="w-12 h-12 rounded-lg object-cover shrink-0" />
                <div className="flex-1 min-w-0">
                    <h3 className="text-sm font-bold text-slate-900 dark:text-white truncate leading-tight">{title}</h3>
                    <div className="flex items-center gap-1 text-xs font-medium text-slate-500 mt-1">
                        <span>by</span>
                        <Link to={`/creator/${author}`} onClick={(e) => e.stopPropagation()} className="relative z-20 font-bold hover:text-modtale-accent hover:underline truncate">
                            {author}
                        </Link>
                    </div>
                </div>
                <div className="hidden sm:flex flex-col items-end gap-1.5 shrink-0 text-[10px] font-bold text-slate-400 uppercase tracking-tight">
                    <div className="flex items-center gap-3">
                        <span className="flex items-center gap-1"><Download className="w-3 h-3" /> {downloads}</span>
                        <span className="flex items-center gap-1"><Heart className={`w-3 h-3 ${isFavorite ? 'text-red-500 fill-current' : ''}`} /> {favorites}</span>
                    </div>
                    <span className="flex items-center gap-1"><Calendar className="w-3 h-3" /> {timeAgo}</span>
                </div>
                <ChevronRight className="w-4 h-4 text-slate-300 group-hover:text-modtale-accent transition-colors shrink-0" />
            </div>
        );
    }

    if (viewStyle === 'list') {
        return (
            <div
                onMouseEnter={handleMouseEnter}
                className="group relative flex flex-col sm:flex-row gap-6 bg-white dark:bg-slate-900 border border-slate-300 dark:border-white/10 rounded-2xl overflow-hidden hover:border-modtale-accent/50 transition-all shadow-md p-4"
            >
                <Link to={canonicalPath} onClick={onClick} className="absolute inset-0 z-10" />
                <div className="w-full sm:w-32 h-32 rounded-xl overflow-hidden shrink-0 bg-slate-100 dark:bg-slate-800">
                    <img src={resolvedImage} alt={title} className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-500" />
                </div>
                <div className="flex-1 min-w-0 flex flex-col">
                    <div className="flex justify-between items-start gap-4">
                        <div>
                            <h3 className="text-lg font-black text-slate-900 dark:text-white group-hover:text-modtale-accent transition-colors truncate">{title}</h3>
                            <div className="flex items-center gap-1 text-xs font-bold text-slate-500">
                                <span>by</span>
                                <Link to={`/creator/${author}`} onClick={(e) => e.stopPropagation()} className="relative z-20 hover:text-modtale-accent hover:underline">
                                    {author}
                                </Link>
                            </div>
                        </div>
                        <div className="bg-slate-100 dark:bg-white/5 px-2 py-1 rounded-lg text-[10px] font-black uppercase tracking-wider flex items-center gap-1.5 shrink-0">
                            <span className="text-modtale-accent">{getClassificationIcon(classification)}</span>
                            {displayClassification}
                        </div>
                    </div>
                    <p className="mt-2 text-sm text-slate-600 dark:text-slate-400 line-clamp-2 flex-1">{desc}</p>
                    <div className="mt-4 flex items-center gap-6 text-[11px] font-bold text-slate-400 uppercase tracking-widest">
                        <span className="flex items-center gap-1.5"><Download className="w-4 h-4" /> {downloads}</span>
                        <span className="flex items-center gap-1.5"><Heart className={`w-4 h-4 ${isFavorite ? 'text-red-500 fill-current' : ''}`} /> {favorites}</span>
                        <span className="flex items-center gap-1.5"><Calendar className="w-4 h-4" /> {timeAgo}</span>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div
            onMouseEnter={handleMouseEnter}
            className="group relative flex flex-col h-full bg-white dark:bg-slate-900 border border-slate-300 dark:border-white/20 rounded-2xl overflow-hidden hover:border-modtale-accent/50 dark:hover:border-modtale-accent/40 hover:-translate-y-1.5 transition-all duration-500 shadow-lg hover:shadow-2xl dark:shadow-xl hover:shadow-modtale-accent/10 ring-1 ring-black/[0.02] dark:ring-white/[0.02]"
            role="article"
            aria-label={`Project: ${title} by ${author}`}
        >
            <Link
                to={canonicalPath}
                onClick={onClick ? (e) => { e.preventDefault(); onClick(); } : undefined}
                className="absolute inset-0 z-10 focus:outline-none"
                aria-hidden="true"
                tabIndex={-1}
            />

            <div className="w-full aspect-[3/1] relative bg-slate-800 border-b border-slate-100 dark:border-white/5 overflow-hidden shrink-0">
                {resolvedBanner ? (
                    <img
                        src={resolvedBanner}
                        alt=""
                        decoding={priority ? "sync" : "async"}
                        loading={priority ? "eager" : "lazy"}
                        className="w-full h-full object-cover opacity-80 group-hover:opacity-100 group-hover:scale-105 transition-all duration-700"
                    />
                ) : (
                    <>
                        <div className="w-full h-full bg-slate-200/30 dark:bg-slate-700/30" />
                        <div className="absolute inset-0 bg-gradient-to-t from-white via-white/20 dark:from-slate-900 dark:via-slate-900/20 to-transparent" />
                    </>
                )}

                <div className="absolute top-3 right-3 z-40">
                    <div className="bg-white/90 dark:bg-slate-900/95 backdrop-blur-md text-slate-800 dark:text-white text-[11px] font-bold px-2.5 py-1.5 rounded-lg flex items-center border border-slate-200 dark:border-white/10 shadow-sm relative">
                        <span className="mr-1.5 text-modtale-accent">{getClassificationIcon(classification)}</span>
                        <span>{displayClassification}</span>
                    </div>
                </div>
            </div>

            <div className="px-6 pb-6 relative flex flex-col flex-1 bg-transparent">
                <div className="w-20 h-20 rounded-2xl bg-transparent backdrop-blur-sm shadow-xl border-4 border-white dark:border-slate-800 overflow-hidden absolute -top-10 group-hover:-translate-y-1 transition-transform duration-500 ring-1 ring-black/5 dark:ring-white/10 z-20">
                    <img
                        src={resolvedImage}
                        onError={(e) => e.currentTarget.src = '/assets/favicon.svg'}
                        alt={title}
                        className="w-full h-full object-cover"
                    />
                    {classification === 'MODPACK' && modCount > 0 && (
                        <div className="absolute bottom-0 right-0 bg-slate-900/75 backdrop-blur-sm text-white text-[10px] font-bold px-1 py-0.5 rounded-tl-xl flex items-center">
                            <Box className="w-3 h-3 mr-0.5" /> {modCount}
                        </div>
                    )}
                </div>

                <div className="mt-12">
                    <h3 className="text-xl font-black text-slate-900 dark:text-white group-hover:text-modtale-accent transition-colors truncate tracking-tight" title={title}>
                        <Link
                            to={canonicalPath}
                            onClick={onClick ? (e) => { e.preventDefault(); onClick(); } : undefined}
                            className="relative z-10 focus:outline-none"
                        >
                            {title}
                        </Link>
                    </h3>

                    <div className="flex items-center gap-1 text-sm text-slate-500 dark:text-slate-400 font-medium truncate mt-1">
                        <span>By</span>
                        <Link
                            to={`/creator/${author}`}
                            onClick={(e) => e.stopPropagation()}
                            className="hover:text-modtale-accent hover:underline focus:outline-none relative z-20"
                        >
                            {author}
                        </Link>
                    </div>
                </div>

                <div className="mt-3 flex-1 relative z-0 pointer-events-none">
                    <p className="text-slate-600 dark:text-slate-400 text-sm line-clamp-2 leading-relaxed">
                        {desc}
                    </p>
                </div>

                <div className="mt-5 flex items-center justify-between text-[11px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest">
                    <div className="flex items-center gap-4">
                        <span className="flex items-center gap-1.5"><Download className="w-4 h-4" /> {downloads}</span>

                        <button
                            disabled={!isLoggedIn}
                            onClick={(e) => {
                                e.preventDefault();
                                e.stopPropagation();
                                if(isLoggedIn) onToggleFavorite(mod.id);
                            }}
                            className={`flex items-center gap-1.5 transition-colors relative z-20 ${
                                !isLoggedIn
                                    ? 'text-slate-300 dark:text-white/10 cursor-not-allowed'
                                    : isFavorite
                                        ? 'text-red-500'
                                        : 'text-slate-400 hover:text-red-400'
                            }`}
                        >
                            <Heart className={`w-4 h-4 ${isFavorite ? 'fill-current' : ''}`} />
                            {favorites}
                        </button>
                    </div>
                    <div className="flex items-center gap-1.5">
                        <Calendar className="w-4 h-4" />
                        <span suppressHydrationWarning>{timeAgo || 'Unknown'}</span>
                    </div>
                </div>
            </div>
        </div>
    );
}, (prevProps, nextProps) => {
    return (
        prevProps.mod.id === nextProps.mod.id &&
        prevProps.mod.updatedAt === nextProps.mod.updatedAt &&
        prevProps.mod.favoriteCount === nextProps.mod.favoriteCount &&
        prevProps.isFavorite === nextProps.isFavorite &&
        prevProps.isLoggedIn === nextProps.isLoggedIn &&
        prevProps.priority === nextProps.priority &&
        prevProps.viewStyle === nextProps.viewStyle
    );
});

ModCard.displayName = 'ModCard';