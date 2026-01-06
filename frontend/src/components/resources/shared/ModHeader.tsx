import React, { useState, useRef, useEffect } from 'react';
import {
    Heart, Share2, Image, List,
    MessageSquare, Calendar, Globe, Bug, BookOpen,
    Github, Edit, Download, Link as LinkIcon, ChevronDown, ExternalLink
} from 'lucide-react';
import type { Mod, User } from '../../../types.ts';
import { getProjectUrl } from '../../../utils/slug.ts';
import { formatTimeAgo } from '../../../utils/modHelpers.tsx';

const DiscordIcon = ({ className }: { className?: string }) => (
    <svg className={className} fill="currentColor" viewBox="0 0 127.14 96.36">
        <path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c2.36-24.44-4.2-48.62-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z" />    </svg>
);

interface ModHeaderProps {
    mod: Mod;
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
    navigate: (path: string) => void;
    classificationIcon: React.ReactNode;
    displayClassification: string;
    onDownloadClick: () => void;
}

export const ModHeader: React.FC<ModHeaderProps> = ({
                                                        mod, isOwner, isLiked, isFollowing, currentUser, onToggleFavorite, onToggleFollow, onShare,
                                                        onOpenGallery, onOpenHistory, onScrollToReviews,
                                                        navigate, classificationIcon, displayClassification, onDownloadClick
                                                    }) => {
    const [showMobileLinks, setShowMobileLinks] = useState(false);
    const dropdownRef = useRef<HTMLDivElement>(null);

    const canEdit = isOwner || (currentUser && mod.contributors?.includes(currentUser.username));

    const handleEdit = () => {
        const url = getProjectUrl(mod);
        navigate(`${url}/edit`);
    };

    useEffect(() => {
        const handleClick = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
                setShowMobileLinks(false);
            }
        };
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, []);

    const links = [
        mod.repositoryUrl && { type: 'SOURCE', url: mod.repositoryUrl, icon: Github, label: 'Source Code' },
        mod.links?.DISCORD && { type: 'DISCORD', url: mod.links.DISCORD, icon: DiscordIcon, label: 'Discord' },
        mod.links?.WEBSITE && { type: 'WEBSITE', url: mod.links.WEBSITE, icon: Globe, label: 'Website' },
        mod.links?.WIKI && { type: 'WIKI', url: mod.links.WIKI, icon: BookOpen, label: 'Wiki' },
        mod.links?.ISSUE_TRACKER && { type: 'ISSUE', url: mod.links.ISSUE_TRACKER, icon: Bug, label: 'Issues' }
    ].filter(Boolean) as { type: string, url: string, icon: any, label: string }[];

    const getLinkColor = (type: string) => {
        if (type === 'DISCORD') return 'text-[#5865F2] hover:bg-[#5865F2]/20 border-[#5865F2]/20';
        if (type === 'WEBSITE') return 'text-blue-400 hover:bg-blue-500/20 border-blue-500/20';
        if (type === 'ISSUE') return 'text-red-400 hover:bg-red-500/20 border-red-500/20';
        if (type === 'SOURCE') return 'text-slate-300 hover:bg-slate-200 dark:hover:bg-white/10 border-slate-200 dark:border-white/10';
        return 'text-amber-500 hover:bg-amber-500/10 border-amber-500/20';
    };

    return (
        <div className="flex flex-col gap-6 w-full">
            <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="flex-1">
                    <div className="flex flex-wrap items-center gap-3 mb-3">
                        <h1 className="text-3xl md:text-5xl font-black text-slate-900 dark:text-white tracking-tighter drop-shadow-sm leading-tight break-words">{mod.title}</h1>
                        <span className="bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/10 px-3 py-1 rounded-full text-xs font-bold text-modtale-accent tracking-widest uppercase flex items-center gap-1.5 shadow-sm whitespace-nowrap">
                            {classificationIcon}{displayClassification}
                        </span>
                    </div>

                    <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm font-medium text-slate-500 dark:text-slate-400">
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
                        <span className="hidden md:inline text-slate-300 dark:text-slate-600">•</span>
                        <span className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider opacity-80">
                            <Calendar className="w-3 h-3" /> Updated {formatTimeAgo(mod.updatedAt)}
                        </span>
                    </div>
                </div>

                <div className="hidden md:flex items-center gap-2">
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
                <p className="text-slate-600 dark:text-slate-300 text-base leading-relaxed max-w-4xl font-medium border-l-2 border-modtale-accent pl-4">
                    {mod.description}
                </p>
            )}

            <div className="flex flex-col xl:flex-row items-start xl:items-center justify-between border-t border-slate-200 dark:border-white/5 pt-8 mt-2 gap-6 w-full">
                <div className="flex flex-col md:flex-row items-stretch md:items-center gap-4 w-full xl:w-auto">
                    <button
                        onClick={onDownloadClick}
                        className="flex-shrink-0 bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 py-3.5 rounded-xl font-black flex items-center justify-center gap-2 shadow-lg shadow-modtale-accent/20 transition-all active:scale-95 group"
                    >
                        <Download className="w-5 h-5 group-hover:animate-bounce" />
                        Download
                    </button>

                    <div className="hidden md:block w-px h-10 bg-slate-200 dark:bg-white/10 mx-2"></div>

                    <div className="grid grid-cols-2 md:flex md:flex-row gap-2 w-full md:w-auto">
                        {mod.galleryImages && mod.galleryImages.length > 0 && (
                            <button onClick={onOpenGallery} className="col-span-2 md:col-span-1 flex items-center justify-center gap-2 px-5 py-3 md:py-2.5 text-sm font-bold bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 rounded-xl text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200 dark:hover:bg-white/10 transition-colors whitespace-nowrap"><Image className="w-4 h-4" /> Gallery</button>
                        )}
                        <button onClick={onOpenHistory} className="flex items-center justify-center gap-2 px-5 py-3 md:py-2.5 text-sm font-bold bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 rounded-xl text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200 dark:hover:bg-white/10 transition-colors whitespace-nowrap"><List className="w-4 h-4" /> Changelog</button>
                        <button onClick={onScrollToReviews} className="flex items-center justify-center gap-2 px-5 py-3 md:py-2.5 text-sm font-bold bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 rounded-xl text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200 dark:hover:bg-white/10 transition-colors whitespace-nowrap"><MessageSquare className="w-4 h-4" /> Reviews</button>
                    </div>
                </div>

                <div className="w-full xl:w-auto flex justify-start md:justify-end">

                    <div className="hidden md:flex gap-2 flex-wrap justify-end">
                        {links.map((link, idx) => (
                            <a
                                key={idx}
                                href={link.url.startsWith('http') ? link.url : `https://${link.url}`}
                                target="_blank"
                                rel="noreferrer"
                                className={`p-2.5 rounded-xl border transition-all ${getLinkColor(link.type)}`}
                                title={link.label}
                            >
                                <link.icon className="w-5 h-5" />
                            </a>
                        ))}
                    </div>

                    {links.length > 0 && (
                        <div className="md:hidden relative w-full" ref={dropdownRef}>
                            <button
                                onClick={() => setShowMobileLinks(!showMobileLinks)}
                                className="w-full flex items-center justify-center gap-2 p-3 rounded-xl bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/10 font-bold text-slate-600 dark:text-slate-300"
                            >
                                <LinkIcon className="w-4 h-4" /> External Links <ChevronDown className={`w-4 h-4 transition-transform ${showMobileLinks ? 'rotate-180' : ''}`} />
                            </button>
                            {showMobileLinks && (
                                <div className="absolute top-full left-0 right-0 mt-2 bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-50 overflow-hidden animate-in fade-in slide-in-from-top-2 p-1">
                                    {links.map((link, idx) => (
                                        <a
                                            key={idx}
                                            href={link.url.startsWith('http') ? link.url : `https://${link.url}`}
                                            target="_blank"
                                            rel="noreferrer"
                                            className="flex items-center gap-3 p-3 rounded-lg hover:bg-white/5 transition-colors text-slate-300 hover:text-white"
                                        >
                                            <div className={`p-1.5 rounded-lg border bg-slate-950 ${getLinkColor(link.type)}`}>
                                                <link.icon className="w-4 h-4" />
                                            </div>
                                            <span className="text-sm font-bold">{link.label}</span>
                                            <ExternalLink className="w-3 h-3 ml-auto opacity-50" />
                                        </a>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};