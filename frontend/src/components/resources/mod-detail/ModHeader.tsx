import React, { useState } from 'react';
import {
    Heart, Share2, Image, List,
    MessageSquare, Calendar, Globe, Bug, BookOpen,
    Github, ChevronLeft, Edit, Download, Clock,
    Box, Link as LinkIcon, ChevronDown, ChevronUp
} from 'lucide-react';
import type { Mod, User, ModDependency } from '../../../types';
import { BACKEND_URL } from '../../../utils/api';
import { createSlug } from '../../../utils/slug';
import { formatTimeAgo } from '../../../utils/modHelpers';

const DiscordIcon = ({ className }: { className?: string }) => (
    <svg className={className} fill="currentColor" viewBox="0 0 127.14 96.36">
        <path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c2.36-24.44-4.2-48.62-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z" />
    </svg>
);

interface ModHeaderProps {
    mod: Mod;
    dependencies?: ModDependency[];
    isOwner: boolean;
    isLiked: boolean;
    isFollowing: boolean;
    currentUser: User | null;
    onToggleFavorite: () => void;
    onToggleFollow: () => void;
    onShare: () => void;
    onOpenGallery: () => void;
    onOpenHistory: () => void;
    onScrollToReviews: () => void;
    onDelete?: () => void;
    onManageContributors?: () => void;
    navigate: (path: string) => void;
    classificationIcon: React.ReactNode;
    displayClassification: string;
    onDownloadClick: () => void;
}

export const ModHeader: React.FC<ModHeaderProps> = ({
                                                        mod, dependencies, isOwner, isLiked, isFollowing, currentUser, onToggleFavorite, onToggleFollow, onShare,
                                                        onOpenGallery, onOpenHistory, onScrollToReviews,
                                                        navigate, classificationIcon, displayClassification, onDownloadClick
                                                    }) => {
    const [showMobileDeps, setShowMobileDeps] = useState(false);

    const canEdit = isOwner || (currentUser && mod.contributors?.includes(currentUser.username));

    const handleEdit = () => {
        const slug = createSlug(mod.title, mod.id);
        const basePath = mod.classification === 'MODPACK' ? `/modpack/${slug}` : `/mod/${slug}`;
        navigate(`${basePath}/edit`);
    };

    const LinkButton = ({ url, type, icon: Icon, label }: any) => {
        if (!url) return null;
        const color = type === 'DISCORD' ? 'text-[#5865F2] hover:bg-[#5865F2]/20 border-[#5865F2]/20' :
            type === 'WEBSITE' ? 'text-blue-400 hover:bg-blue-500/20 border-blue-500/20' :
                type === 'ISSUE' ? 'text-red-400 hover:bg-red-500/20 border-red-500/20' :
                    type === 'SOURCE' ? 'text-slate-300 hover:bg-slate-200 dark:hover:bg-white/10 border-slate-200 dark:border-white/10' :
                        'text-amber-500 hover:bg-amber-500/10 border-amber-500/20';
        return (
            <a href={url.startsWith('http') ? url : `https://${url}`} target="_blank" rel="noreferrer" className={`p-2.5 rounded-xl border transition-all ${color}`} title={label}>
                <Icon className="w-5 h-5" />
            </a>
        );
    };

    const MobileView = () => (
        <div className="md:hidden flex flex-col bg-slate-950 dark:bg-slate-900 border-b border-white/5">
            <div className="relative w-full aspect-[3/1] bg-slate-800 overflow-hidden">
                {mod.bannerUrl ? (
                    <img
                        src={mod.bannerUrl.startsWith('/api') ? `${BACKEND_URL}${mod.bannerUrl}` : mod.bannerUrl}
                        alt=""
                        className="w-full h-full object-cover opacity-90"
                    />
                ) : (
                    <div className="w-full h-full bg-gradient-to-br from-indigo-900 via-slate-900 to-black"></div>
                )}
                <div className="absolute inset-0 bg-gradient-to-t from-slate-950/80 to-transparent"></div>

                <div className="absolute top-4 left-4 z-10">
                    <button onClick={() => navigate('/home')} className="p-2 rounded-full bg-black/40 backdrop-blur text-white hover:bg-black/60 transition-colors border border-white/10">
                        <ChevronLeft className="w-5 h-5" />
                    </button>
                </div>
            </div>

            <div className="px-4 pb-6 relative">
                <div className="flex justify-between items-end -mt-10 mb-4">
                    <div className="w-24 h-24 rounded-2xl border-4 border-slate-950 shadow-2xl overflow-hidden bg-slate-800 relative z-10">
                        <img
                            src={mod.imageUrl?.startsWith('/api') ? `${BACKEND_URL}${mod.imageUrl}` : mod.imageUrl}
                            alt={mod.title}
                            className="w-full h-full object-cover"
                            onError={(e) => e.currentTarget.src = '/assets/favicon.svg'}
                        />
                    </div>

                    <div className="flex gap-2 mb-1">
                        <button disabled={!currentUser} onClick={onToggleFavorite} className={`p-2.5 rounded-xl border transition-all ${isLiked ? 'bg-red-500/10 text-red-500 border-red-500/20' : 'bg-white/5 text-slate-400 hover:text-white border-white/5'}`}>
                            <Heart className={`w-5 h-5 ${isLiked ? 'fill-current' : ''}`} />
                        </button>
                        <button onClick={onShare} className="p-2.5 rounded-xl border border-white/5 bg-white/5 text-slate-400 hover:text-blue-400">
                            <Share2 className="w-5 h-5" />
                        </button>
                        {canEdit && (
                            <button onClick={handleEdit} className="p-2.5 rounded-xl border border-white/5 bg-white/5 text-slate-400 hover:text-white">
                                <Edit className="w-5 h-5" />
                            </button>
                        )}
                    </div>
                </div>

                <div className="mb-6">
                    <h1 className="text-3xl font-black text-white tracking-tight leading-none mb-2">{mod.title}</h1>
                    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-slate-400">
                        <span className="flex items-center gap-1 font-bold text-slate-200">
                            {mod.author}
                            {currentUser && currentUser.username !== mod.author && (
                                <button onClick={onToggleFollow} className={`ml-2 px-2 py-0.5 rounded text-[10px] uppercase font-bold tracking-wide flex items-center gap-1 transition-colors ${isFollowing ? 'bg-white/10 text-slate-400' : 'bg-modtale-accent text-white'}`}>
                                    {isFollowing ? 'Following' : 'Follow'}
                                </button>
                            )}
                        </span>
                        <span className="bg-white/5 border border-white/10 px-2 py-0.5 rounded text-[10px] uppercase font-bold text-modtale-accent tracking-wider flex items-center gap-1">
                            {classificationIcon}{displayClassification}
                        </span>
                    </div>
                    {mod.description && <p className="text-slate-400 text-sm mt-3 leading-relaxed">{mod.description}</p>}
                </div>

                <button
                    onClick={onDownloadClick}
                    className="w-full bg-modtale-accent text-white px-5 py-3.5 rounded-xl font-black flex items-center justify-center gap-2 shadow-lg mb-6 active:scale-95 transition-all"
                >
                    <Download className="w-5 h-5" /> Download Latest
                </button>

                <div className="flex gap-3">
                    {mod.galleryImages && mod.galleryImages.length > 0 && (
                        <button onClick={onOpenGallery} className="flex flex-1 justify-center items-center gap-2 px-4 py-2.5 bg-white/5 border border-white/10 rounded-xl text-sm font-bold text-slate-300"><Image className="w-4 h-4" /> Gallery</button>
                    )}
                    <button onClick={onOpenHistory} className="flex flex-1 justify-center items-center gap-2 px-4 py-2.5 bg-white/5 border border-white/10 rounded-xl text-sm font-bold text-slate-300"><List className="w-4 h-4" /> Changelog</button>
                    <button onClick={onScrollToReviews} className="flex flex-1 justify-center items-center gap-2 px-4 py-2.5 bg-white/5 border border-white/10 rounded-xl text-sm font-bold text-slate-300"><MessageSquare className="w-4 h-4" /> Reviews</button>
                </div>

                {dependencies && dependencies.length > 0 && (
                    <div className="mt-6">
                        <button
                            onClick={() => setShowMobileDeps(!showMobileDeps)}
                            className="w-full flex items-center justify-between p-3 rounded-xl bg-white/5 border border-white/10 hover:bg-white/10 transition-colors group"
                        >
                            <span className="flex items-center gap-2 font-bold text-slate-300 text-sm">
                                {mod.classification === 'MODPACK' ? <Box className="w-4 h-4 text-modtale-accent" /> : <LinkIcon className="w-4 h-4 text-slate-400" />}
                                {mod.classification === 'MODPACK' ? 'Included Mods' : 'Dependencies'}
                                <span className="bg-white/10 text-xs px-2 py-0.5 rounded-full ml-1 text-slate-400">{dependencies.length}</span>
                            </span>
                            {showMobileDeps ? <ChevronUp className="w-4 h-4 text-slate-400" /> : <ChevronDown className="w-4 h-4 text-slate-400" />}
                        </button>

                        {showMobileDeps && (
                            <div className="mt-2 space-y-2 pl-2 border-l-2 border-white/10 ml-2 animate-in slide-in-from-top-2 duration-200">
                                {dependencies.map((dep, idx) => (
                                    <button
                                        key={idx}
                                        onClick={() => navigate(mod.classification === 'MODPACK' ? `/modpack/${createSlug(dep.modTitle || dep.modId, dep.modId)}` : `/mod/${createSlug(dep.modTitle || dep.modId, dep.modId)}`)}
                                        className="w-full text-left py-2 px-3 rounded-lg hover:bg-white/5 flex items-center justify-between group/item"
                                    >
                                        <span className="text-sm text-slate-400 group-hover/item:text-modtale-accent truncate max-w-[70%]">
                                            {dep.modTitle || dep.modId}
                                        </span>
                                        <span className="text-[10px] uppercase font-bold text-slate-600 tracking-wider">
                                            {dep.isOptional ? 'Optional' : 'Required'}
                                        </span>
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );

    const DesktopView = () => (
        <div className="hidden md:flex flex-row gap-8 items-start w-full">
            <div className="flex-shrink-0 -mt-20 pl-2 relative z-50">
                <div className="w-48 h-48 rounded-[2rem] bg-slate-100 dark:bg-slate-800 shadow-2xl overflow-hidden border-[6px] border-white dark:border-slate-800 group">
                    <img
                        src={mod.imageUrl?.startsWith('/api') ? `${BACKEND_URL}${mod.imageUrl}` : mod.imageUrl}
                        onError={(e) => e.currentTarget.src = '/assets/favicon.svg'}
                        alt={mod.title}
                        className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110"
                    />
                </div>
            </div>

            <div className="flex-1 min-w-0 flex flex-col justify-center pt-2">

                <div className="flex flex-wrap items-start justify-between gap-4 mb-3">
                    <div>
                        <div className="flex items-center gap-3 mb-2">
                            <h1 className="text-5xl font-black text-slate-900 dark:text-white tracking-tighter drop-shadow-sm leading-tight">{mod.title}</h1>
                            <span className="bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/10 px-3 py-1 rounded-full text-xs font-bold text-modtale-accent tracking-widest uppercase flex items-center gap-1.5 shadow-sm">
                                {classificationIcon}{displayClassification}
                            </span>
                        </div>

                        <div className="flex items-center gap-4 text-sm font-medium text-slate-500 dark:text-slate-400">
                            <div className="flex items-center gap-2">
                                <span>by <button onClick={() => navigate(`/creator/${mod.author}`)} className="font-bold text-slate-700 dark:text-white hover:text-modtale-accent hover:underline decoration-2 underline-offset-4 transition-all">{mod.author}</button></span>
                                {currentUser && currentUser.username !== mod.author && (
                                    <button
                                        onClick={onToggleFollow}
                                        className={`h-6 px-2.5 rounded-lg text-[10px] uppercase font-bold tracking-widest transition-all ${isFollowing ? 'bg-slate-200 dark:bg-white/10 text-slate-500 dark:text-slate-400 hover:bg-red-500/20 hover:text-red-400' : 'bg-modtale-accent text-white hover:bg-modtale-accentHover shadow-lg shadow-modtale-accent/20'}`}
                                    >
                                        {isFollowing ? 'Unfollow' : 'Follow'}
                                    </button>
                                )}
                            </div>
                            <span className="text-slate-400 dark:text-slate-600">•</span>
                            <span className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider opacity-80">
                                <Calendar className="w-3 h-3" /> Updated {formatTimeAgo(mod.updatedAt)}
                            </span>
                            {mod.createdAt && (
                                <>
                                    <span className="text-slate-400 dark:text-slate-600 hidden md:inline">•</span>
                                    <span className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider opacity-80">
                                        <Clock className="w-3 h-3" /> Created {formatTimeAgo(mod.createdAt)}
                                    </span>
                                </>
                            )}
                        </div>
                    </div>

                    <div className="flex items-center gap-2">
                        <button disabled={!currentUser} onClick={onToggleFavorite} className={`p-3 rounded-xl border transition-all ${isLiked ? 'bg-red-500/10 text-red-500 border-red-500/20' : 'bg-slate-100 dark:bg-white/5 text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white border-slate-200 dark:border-white/5 hover:border-slate-300 dark:hover:border-white/20'}`} title="Favorite">
                            <Heart className={`w-5 h-5 ${isLiked ? 'fill-current' : ''}`} />
                        </button>
                        <button onClick={onShare} className="p-3 rounded-xl border border-slate-200 dark:border-white/5 bg-slate-100 dark:bg-white/5 text-slate-500 dark:text-slate-400 hover:text-blue-400 hover:border-blue-400/30 transition-all" title="Share">
                            <Share2 className="w-5 h-5" />
                        </button>
                        {canEdit && (
                            <button onClick={handleEdit} className="p-3 rounded-xl border border-slate-200 dark:border-white/5 bg-slate-100 dark:bg-white/5 text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition-all" title="Edit Project">
                                <Edit className="w-5 h-5" />
                            </button>
                        )}
                    </div>
                </div>

                {mod.description && (
                    <p className="text-slate-600 dark:text-slate-300 text-base leading-relaxed mb-6 max-w-4xl font-medium border-l-2 border-modtale-accent pl-4">
                        {mod.description}
                    </p>
                )}

                <div className="flex flex-wrap items-center justify-between border-t border-slate-200 dark:border-white/5 pt-6 mt-2 gap-4">

                    <div className="flex items-center gap-4">
                        <button
                            onClick={onDownloadClick}
                            className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-5 py-2.5 rounded-xl font-black flex items-center gap-2 shadow-lg shadow-modtale-accent/20 transition-all active:scale-95 group"
                        >
                            <Download className="w-5 h-5 group-hover:animate-bounce" />
                            Download
                        </button>

                        <div className="w-px h-8 bg-slate-200 dark:bg-white/10 mx-1"></div>

                        {mod.galleryImages && mod.galleryImages.length > 0 && (
                            <button onClick={onOpenGallery} className="flex items-center gap-2 px-3 py-2 text-sm font-bold text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition-colors"><Image className="w-4 h-4" /> Gallery</button>
                        )}
                        <button onClick={onOpenHistory} className="flex items-center gap-2 px-3 py-2 text-sm font-bold text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition-colors"><List className="w-4 h-4" /> Changelog</button>
                        <button onClick={onScrollToReviews} className="flex items-center gap-2 px-3 py-2 text-sm font-bold text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition-colors"><MessageSquare className="w-4 h-4" /> Reviews</button>
                    </div>

                    <div className="flex gap-2">
                        {mod.repositoryUrl && <LinkButton url={mod.repositoryUrl} type="SOURCE" icon={Github} label="Source Code" />}
                        {mod.links?.DISCORD && <LinkButton url={mod.links.DISCORD} type="DISCORD" icon={DiscordIcon} label="Discord" />}
                        {mod.links?.WEBSITE && <LinkButton url={mod.links.WEBSITE} type="WEBSITE" icon={Globe} label="Website" />}
                        {mod.links?.WIKI && <LinkButton url={mod.links.WIKI} type="WIKI" icon={BookOpen} label="Wiki" />}
                        {mod.links?.ISSUE_TRACKER && <LinkButton url={mod.links.ISSUE_TRACKER} type="ISSUE" icon={Bug} label="Issues" />}
                    </div>
                </div>
            </div>
        </div>
    );

    return (
        <>
            <MobileView />
            <DesktopView />
        </>
    );
};