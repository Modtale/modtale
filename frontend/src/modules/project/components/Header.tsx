import React from 'react';
import { Link } from 'react-router-dom';
import { Heart, Share2, Flag, Edit, Calendar } from 'lucide-react';
import { getClassificationIcon, toTitleCase, formatTimeAgo } from '@/utils/modHelpers';
import { SiteRoutes } from '@/utils/routes';
import { theme } from '@/styles/theme';
import type { Project, User } from '@/types';

interface HeaderProps {
    project: Project;
    currentUser: User | null;
    isLiked: boolean;
    isFollowing: boolean;
    canEdit: boolean;
    projectUrl: string;
    onToggleFavorite: () => void;
    onShare: () => void;
    onReport: () => void;
    onFollowToggle: () => void;
}

export const HeaderActions: React.FC<HeaderProps> = ({ project, currentUser, isLiked, canEdit, projectUrl, onToggleFavorite, onShare, onReport }) => (
    <>
        <button disabled={!currentUser} aria-label="Favorite" onClick={onToggleFavorite} className={`p-3 rounded-xl border transition-all ${isLiked ? `${theme.colors.dangerBg} ${theme.colors.dangerText} ${theme.colors.dangerBorder}` : `${theme.colors.bgSurfaceAlt} ${theme.colors.textSecondary} hover:${theme.colors.textPrimary} ${theme.colors.border} hover:border-slate-300 dark:hover:border-white/20`}`}>
            <Heart className={`w-5 h-5 ${isLiked ? 'fill-current' : ''}`} aria-hidden="true" />
        </button>
        <button onClick={onShare} aria-label="Share" className={`p-3 rounded-xl border ${theme.colors.border} ${theme.colors.bgSurfaceAlt} ${theme.colors.textSecondary} hover:text-blue-500 hover:border-blue-500/30 transition-all`}>
            <Share2 className="w-5 h-5" aria-hidden="true" />
        </button>
        {(!currentUser || currentUser.id !== (project as any).authorId) && (
            <button onClick={onReport} aria-label="Report Project" className={`p-3 rounded-xl border ${theme.colors.border} ${theme.colors.bgSurfaceAlt} ${theme.colors.textSecondary} hover:${theme.colors.dangerText} hover:border-red-500/30 transition-all`}>
                <Flag className="w-5 h-5" aria-hidden="true" />
            </button>
        )}
        {canEdit && (
            <Link to={`${projectUrl}/edit`} aria-label="Edit Project" className={`p-3 rounded-xl border ${theme.colors.border} ${theme.colors.bgSurfaceAlt} ${theme.colors.textSecondary} hover:${theme.colors.textPrimary} transition-all block`}>
                <Edit className="w-5 h-5" aria-hidden="true" />
            </Link>
        )}
    </>
);

export const HeaderContent: React.FC<HeaderProps> = ({ project, currentUser, isFollowing, onFollowToggle }) => {
    const authorPath = project.authorId ? SiteRoutes.creator(project.authorId, project.author) : null;

    return (
    <>
        <div className="flex flex-wrap items-center gap-3 mb-3">
            <h1 className={`text-3xl md:text-5xl font-black ${theme.colors.textPrimary} tracking-tighter drop-shadow-sm leading-tight break-words`}>{project.title}</h1>
            <span className={`${theme.colors.bgSurfaceAlt} border ${theme.colors.border} px-3 py-1 rounded-full text-xs font-bold text-blue-700 dark:text-modtale-accent tracking-widest uppercase flex items-center gap-1.5 shadow-sm whitespace-nowrap`}>
                {getClassificationIcon(project.classification || 'PLUGIN', "w-3.5 h-3.5")}{toTitleCase(project.classification || 'PLUGIN')}
            </span>
        </div>
        <div className={`flex flex-wrap items-center gap-x-6 gap-y-2 text-sm font-medium ${theme.colors.textSecondary} mb-4`}>
            <div className="flex items-center gap-2">
                <span>by {authorPath ? <Link to={authorPath} className={`font-bold text-slate-800 dark:text-white hover:${theme.colors.accent} hover:underline decoration-2 underline-offset-4 transition-all`}>{project.author}</Link> : <span className="font-bold text-slate-800 dark:text-white">{project.author}</span>}</span>
                {currentUser && currentUser.id !== (project as any).authorId && (
                    <button onClick={onFollowToggle} className={`h-6 px-2.5 rounded-lg text-[10px] uppercase font-bold tracking-widest transition-all ${isFollowing ? `${theme.colors.bgSurfaceAlt} ${theme.colors.textSecondary} hover:bg-red-500/20 hover:${theme.colors.dangerText}` : `${theme.colors.accentBg} text-white hover:bg-modtale-accentHover shadow-lg shadow-modtale-accent/20`}`}>
                        {isFollowing ? 'Unfollow' : 'Follow'}
                    </button>
                )}
            </div>
            <span suppressHydrationWarning className={`hidden md:inline ${theme.colors.textMuted}`}>•</span>
            <span suppressHydrationWarning className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider opacity-80">
                <Calendar className="w-3 h-3" aria-hidden="true" /> Updated <span suppressHydrationWarning>{formatTimeAgo(project.updatedAt)}</span>
            </span>
        </div>
        {project.description && (
            <p className={`text-slate-700 dark:text-slate-300 text-base leading-relaxed max-w-4xl font-medium border-l-2 border-modtale-accent pl-4`}>
                {project.description}
            </p>
        )}
    </>
    );
};
