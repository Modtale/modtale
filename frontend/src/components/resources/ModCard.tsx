import React from 'react';
import type { Mod } from '../../types';
import { Download, Calendar, Heart, Star, Code, Paintbrush, Database, Layers, Layout, Box, HardDrive, Globe } from 'lucide-react';
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
}

const getClassificationIcon = (cls: string) => {
    switch(cls) {
        case 'PLUGIN': return <Code className="w-3 h-3" />;
        case 'ART': return <Paintbrush className="w-3 h-3" />;
        case 'DATA': return <Database className="w-3 h-3" />;
        case 'SAVE': return <Globe className="w-3 h-3" />;
        case 'MODPACK': return <Layers className="w-3 h-3" />;
        default: return <Layout className="w-3 h-3" />;
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

export const ModCard: React.FC<ModCardProps> = ({ mod, path, isFavorite, onToggleFavorite, isLoggedIn }) => {
    const title = mod.title || 'Untitled Project';
    const author = mod.author || 'Unknown';

    const canonicalPath = path || getProjectUrl(mod);

    const desc = mod.description ? mod.description : 'No description provided.';
    const classification = mod.classification || 'PLUGIN';
    const displayCategory = (mod.categories && mod.categories.length > 0) ? mod.categories[0] : (mod.category || 'Misc');

    const rating = mod.rating || 0;
    const downloads = (mod.downloadCount || 0).toLocaleString();
    const favorites = (mod.favoriteCount || 0).toLocaleString();

    const timeAgo = formatTimeAgo(mod.updatedAt || '');
    const modCount = (mod.modIds || mod.childProjectIds || []).length;
    const sizeMB = mod.sizeBytes ? (mod.sizeBytes / 1024 / 1024).toFixed(1) + ' MB' : null;
    const displayClassification = toTitleCase(classification);

    const resolveUrl = (url: string) => {
        if (!url) return '';
        if (url.startsWith('/api')) {
            return `${BACKEND_URL}${url}`;
        }
        return url;
    };

    const resolvedImage = mod.imageUrl ? resolveUrl(mod.imageUrl) : '/assets/favicon.svg';
    const resolvedBanner = mod.bannerUrl ? resolveUrl(mod.bannerUrl) : null;

    const handleMouseEnter = () => {
        prefetchProject(mod.id);
    };

    return (
        <Link
            to={canonicalPath}
            onMouseEnter={handleMouseEnter}
            className="group relative flex flex-col h-full bg-white dark:bg-slate-800 rounded-lg border border-slate-200 dark:border-slate-700 hover:border-modtale-accent dark:hover:border-modtale-accent transition-colors overflow-hidden"
        >
            <div className="relative h-24 w-full shrink-0 overflow-hidden bg-slate-100 dark:bg-slate-900 border-b border-slate-200/50 dark:border-white/5">
                {resolvedBanner ? (
                    <img
                        src={resolvedBanner}
                        alt=""
                        decoding="async"
                        className="w-full h-full object-cover"
                    />
                ) : (
                    <div className="w-full h-full bg-slate-200 dark:bg-slate-700" />
                )}

                <div className="absolute top-2 right-2 z-20">
                    <div className="bg-slate-900/80 text-white text-[10px] font-bold px-2 py-1 rounded flex items-center">
                        <span className="mr-1 text-modtale-accent">{getClassificationIcon(classification)}</span>
                        <span>{displayClassification}</span>
                    </div>
                </div>
            </div>

            <div className="flex px-4 relative z-10 flex-1">
                <div className="flex-shrink-0 -mt-8 mb-2 relative">
                    <div className="w-20 h-20 rounded-lg bg-slate-200 dark:bg-black/20 shadow-md border-4 border-white dark:border-slate-800 overflow-hidden relative">
                        <img
                            src={resolvedImage}
                            onError={(e) => e.currentTarget.src = '/assets/favicon.svg'}
                            alt={title}
                            width="80"
                            height="80"
                            decoding="async"
                            className="w-full h-full object-cover"
                        />
                        {classification === 'MODPACK' && modCount > 0 && (
                            <div className="absolute bottom-0 right-0 bg-slate-900/75 text-white text-[10px] font-bold px-1 py-0.5 rounded-tl flex items-center">
                                <Box className="w-2.5 h-2.5 mr-0.5" /> {modCount}
                            </div>
                        )}
                    </div>
                </div>

                <div className="flex-1 min-w-0 flex flex-col pt-1 pl-3">
                    <div className="flex justify-between items-start gap-2 mb-0.5">
                        <div className="min-w-0 flex-1">
                            <h3 className="text-lg font-bold text-slate-900 dark:text-slate-200 truncate group-hover:text-modtale-accent transition-colors" title={title}>
                                {title}
                            </h3>
                            <div className="flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400">
                                <span>by</span>
                                <Link
                                    to={`/creator/${author}`}
                                    onClick={(e) => e.stopPropagation()}
                                    className="text-slate-700 dark:text-slate-300 font-medium hover:text-modtale-accent hover:underline focus:outline-none relative z-20"
                                >
                                    {author}
                                </Link>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div className="px-4 pb-4 mt-2">
                <p className="text-slate-600 dark:text-slate-400 text-xs line-clamp-2 leading-relaxed h-10">
                    {desc}
                </p>
            </div>

            <div className="mt-auto bg-slate-50 dark:bg-white/[0.02] px-4 py-3 flex items-center justify-between text-xs font-medium text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-white/5">
                <div className="flex items-center gap-3">
                    {sizeMB ? (
                        <span className="flex items-center text-slate-700 dark:text-slate-300 bg-slate-200 dark:bg-white/10 px-2 py-1 rounded">
                            <HardDrive className="w-3 h-3 mr-1" /> {sizeMB}
                        </span>
                    ) : (
                        <span className="bg-slate-200 dark:bg-white/10 px-2 py-1 rounded text-slate-700 dark:text-slate-300">
                            {displayCategory}
                        </span>
                    )}

                    {rating > 0 && (
                        <span className="flex items-center text-slate-700 dark:text-slate-200" title={`Rated ${rating.toFixed(1)}/5`}>
                            <Star className="w-3 h-3 mr-1 text-amber-500 fill-current" />
                            {rating.toFixed(1)}
                        </span>
                    )}

                    <span className="flex items-center"><Download className="w-3 h-3 mr-1" /> {downloads}</span>

                    <button
                        disabled={!isLoggedIn}
                        onClick={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            if(isLoggedIn) onToggleFavorite(mod.id);
                        }}
                        className={`flex items-center transition-colors relative z-20 ${
                            !isLoggedIn
                                ? 'text-slate-300 dark:text-white/10 cursor-not-allowed'
                                : isFavorite
                                    ? 'text-red-500'
                                    : 'text-slate-400 hover:text-red-400'
                        }`}
                        title={!isLoggedIn ? "Log in to favorite" : ""}
                    >
                        <Heart className={`w-3 h-3 mr-1 ${isFavorite ? 'fill-current' : ''}`} />
                        {favorites}
                    </button>
                </div>
                <div className="flex items-center">
                    <Calendar className="w-3 h-3 mr-1" />
                    <span>{timeAgo ? `Updated ${timeAgo}` : 'Unknown'}</span>
                </div>
            </div>
        </Link>
    );
};