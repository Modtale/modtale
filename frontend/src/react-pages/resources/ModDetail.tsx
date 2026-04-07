import React, { useEffect, useState, useRef, useMemo, useCallback } from 'react';
import { useParams, useNavigate, useLocation, Link } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import type { Mod, User, ProjectVersion, Comment, ModDependency } from '../../types';
import {
    MessageSquare, Send, Copy, X, Check,
    Tag, Scale, Link as LinkIcon, Box, Gamepad2, Heart, Share2, Edit, ChevronLeft, ChevronRight,
    Download, Image, List, Globe, Bug, BookOpen, ExternalLink, Calendar, ChevronDown, Hash,
    CornerDownRight, Crown, Trash, Users, Flag, AlertTriangle, Archive, Github,
    ArrowBigUp, ArrowBigDown
} from 'lucide-react';
import { StatusModal } from '../../components/ui/StatusModal';
import { ShareModal } from '@/components/resources/mod-detail/ShareModal';
import { OptimizedImage } from '../../components/ui/OptimizedImage';
import { api, API_BASE_URL, BACKEND_URL } from '../../utils/api';
import { extractId, createSlug, getProjectUrl } from '../../utils/slug';
import { useSSRData } from '../../context/SSRContext';
import NotFound from '../../components/ui/error/NotFound';
import { Spinner } from '@/components/ui/Spinner';
import { compareSemVer, formatTimeAgo, DiscordIcon, getLicenseInfo, getClassificationIcon, toTitleCase } from '../../utils/modHelpers';
import { DependencyModal, DownloadModal, HistoryModal, PostDownloadModal } from '@/components/resources/mod-detail/DownloadDialogs';
import { ProjectLayout, SidebarSection } from '@/components/resources/ProjectLayout';
import { generateProjectMeta } from '../../utils/meta';
import { getBreadcrumbsForClassification, generateBreadcrumbSchema } from '../../utils/schema';
import { useMobile } from '../../context/MobileContext';
import { ReportModal } from '@/components/resources/mod-detail/ReportModal';
import { MarkdownRenderer } from '@/components/ui/MarkdownRenderer';
import { useHMWiki, WikiSidebar, WikiContent } from '../../components/resources/HMWiki';

const ProjectSidebar: React.FC<{
    mod: Mod;
    dependencies?: ModDependency[];
    depMeta: Record<string, { icon: string, title: string }>;
    sourceUrl?: string;
    navigate: (path: string) => void;
    contributors: User[];
    orgMembers: User[];
    author: User | null;
}> = React.memo(({ mod, dependencies, depMeta, navigate, contributors, orgMembers, author }) => {
    const [copiedId, setCopiedId] = useState(false);

    const gameVersions = useMemo(() => {
        const set = new Set<string>();
        mod.versions.forEach(v => v.gameVersions?.forEach(gv => set.add(gv)));
        return Array.from(set).sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
    }, [mod.versions]);

    const licenseInfo = useMemo(() => {
        if (mod.links?.LICENSE) {
            return { name: mod.license || 'Custom License', url: mod.links.LICENSE };
        }
        return mod.license ? getLicenseInfo(mod.license) : null;
    }, [mod.license, mod.links]);

    const handleCopyId = useCallback(() => {
        navigator.clipboard.writeText(mod.id);
        setCopiedId(true);
        setTimeout(() => setCopiedId(false), 2000);
    }, [mod.id]);

    const isModpack = mod.classification === 'MODPACK';
    const getIconUrl = (path?: string) => path ? (path.startsWith('http') ? path : `${BACKEND_URL}${path}`) : null;

    return (
        <div className="flex flex-col gap-8">
            <div className="grid grid-cols-2 gap-2 py-2">
                <div className="flex flex-col items-center justify-start">
                    <div suppressHydrationWarning className="text-3xl font-black text-slate-900 dark:text-white tracking-tighter leading-none mb-1">{mod.favoriteCount.toLocaleString()}</div>
                    <div className="flex items-center justify-center mt-1 h-5 w-full gap-1.5 text-slate-500 dark:text-slate-400">
                        <Heart className="w-3.5 h-3.5" aria-hidden="true" />
                        <span className="text-[10px] font-bold uppercase tracking-widest pt-0.5">Favorites</span>
                    </div>
                </div>

                <div className="flex flex-col items-center justify-start border-l border-slate-200 dark:border-white/5">
                    <div suppressHydrationWarning className="text-3xl font-black text-slate-900 dark:text-white tracking-tighter leading-none mb-1">{mod.downloadCount.toLocaleString()}</div>
                    <div className="flex items-center gap-1.5 mt-1 h-5">
                        <Download className="w-3.5 h-3.5 text-slate-500 dark:text-slate-400" aria-hidden="true" />
                        <span className="text-[10px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest pt-0.5">Downloads</span>
                    </div>
                </div>
            </div>

            {gameVersions.length > 0 && (
                <SidebarSection title="Supported Versions" icon={Gamepad2}>
                    <div className="flex flex-wrap gap-2">
                        {gameVersions.map(v => (
                            <span key={v} className="px-2.5 py-1 bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 rounded-md text-[10px] font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide">
                                {v}
                            </span>
                        ))}
                    </div>
                </SidebarSection>
            )}

            <SidebarSection title="Tags" icon={Tag}>
                <div className="flex flex-wrap gap-2">
                    {mod.tags?.map((tag) => (
                        <span key={tag} className="px-3 py-1.5 bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/10 rounded-lg text-xs font-bold text-slate-700 dark:text-slate-300 hover:text-modtale-accent hover:border-modtale-accent transition-all cursor-default">
                            {tag}
                        </span>
                    ))}
                    {(!mod.tags || mod.tags.length === 0) && <span className="text-xs text-slate-500 italic">No tags.</span>}
                </div>
            </SidebarSection>

            {author && author.accountType === 'ORGANIZATION' && orgMembers.length > 0 && (
                <SidebarSection title="Organization Members" icon={Users}>
                    <div className="flex flex-col gap-2">
                        {orgMembers.map(member => {
                            const orgMembership = author.organizationMembers?.find(m => m.userId === member.id);
                            const role = author.organizationRoles?.find(r => r.id === orgMembership?.roleId);
                            const roleName = role?.name || 'Member';

                            return (
                                <Link
                                    key={member.id}
                                    to={`/creator/${member.username}`}
                                    className="flex items-center gap-3 p-2 rounded-xl hover:bg-slate-100 dark:hover:bg-white/5 transition-colors group"
                                >
                                    <OptimizedImage
                                        src={member.avatarUrl}
                                        alt={`${member.username} Avatar`}
                                        baseWidth={32}
                                        className="w-8 h-8 rounded-lg border border-slate-200 dark:border-white/5 shrink-0"
                                    />
                                    <div className="min-w-0">
                                        <div className="text-xs font-bold text-slate-800 dark:text-slate-200 group-hover:text-modtale-accent truncate">{member.username}</div>
                                        <div className="flex items-center gap-1.5 mt-0.5">
                                            <span className="text-[10px] text-slate-500 uppercase font-bold tracking-wider truncate">{roleName}</span>
                                        </div>
                                    </div>
                                </Link>
                            );
                        })}
                    </div>
                </SidebarSection>
            )}

            {contributors.length > 0 && (
                <SidebarSection title={author?.accountType === 'ORGANIZATION' ? "Project Contributors" : "Team Members"} icon={Users}>
                    <div className="flex flex-col gap-2">
                        {contributors.map(contributor => {
                            const teamMembership = mod.teamMembers?.find(m => m.userId === contributor.id);
                            const role = mod.projectRoles?.find(r => r.id === teamMembership?.roleId);

                            return (
                                <Link
                                    key={contributor.id}
                                    to={`/creator/${contributor.username}`}
                                    className="flex items-center gap-3 p-2 rounded-xl hover:bg-slate-100 dark:hover:bg-white/5 transition-colors group"
                                >
                                    <OptimizedImage
                                        src={contributor.avatarUrl}
                                        alt={`${contributor.username} Avatar`}
                                        baseWidth={32}
                                        className="w-8 h-8 rounded-lg border border-slate-200 dark:border-white/5 shrink-0"
                                    />
                                    <div className="min-w-0">
                                        <div className="text-xs font-bold text-slate-800 dark:text-slate-200 group-hover:text-modtale-accent truncate">{contributor.username}</div>
                                        {role ? (
                                            <div className="flex items-center gap-1.5 mt-0.5">
                                                <div className="w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ backgroundColor: role.color }} />
                                                <span className="text-[10px] text-slate-500 uppercase font-bold tracking-wider truncate">{role.name}</span>
                                            </div>
                                        ) : (
                                            <div className="flex items-center gap-1.5 mt-0.5">
                                                <span className="text-[10px] text-slate-500 uppercase font-bold tracking-wider truncate">Contributor</span>
                                            </div>
                                        )}
                                    </div>
                                </Link>
                            );
                        })}
                    </div>
                </SidebarSection>
            )}

            {dependencies && dependencies.length > 0 && (
                <SidebarSection title={isModpack ? "Included Mods" : "Dependencies"} icon={isModpack ? Box : LinkIcon}>
                    <div className="space-y-2">
                        {dependencies.map((dep, idx) => {
                            const meta = depMeta[dep.modId];
                            const iconUrl = getIconUrl(meta?.icon);
                            const title = meta?.title || dep.modTitle || dep.modId;
                            const slug = createSlug(title, dep.modId);
                            const path = mod.classification === 'MODPACK' ? `/modpack/${slug}` : `/mod/${slug}`;

                            return (
                                <button
                                    key={idx}
                                    onClick={() => navigate(path)}
                                    className="w-full flex items-center gap-3 p-3 rounded-xl bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/5 hover:border-modtale-accent/50 hover:shadow-md transition-all group text-left"
                                >
                                    <div className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-black/20 flex items-center justify-center text-slate-400 group-hover:text-modtale-accent transition-colors overflow-hidden shrink-0">
                                        {iconUrl ? (
                                            <OptimizedImage
                                                src={iconUrl}
                                                alt={`${title} Icon`}
                                                baseWidth={32}
                                                className="w-full h-full"
                                            />
                                        ) : <Box className="w-4 h-4" aria-hidden="true" />}
                                    </div>
                                    <div className="min-w-0">
                                        <div className="text-xs font-bold text-slate-800 dark:text-slate-200 group-hover:text-modtale-accent truncate">{title}</div>
                                        <div className="text-[10px] text-slate-600 dark:text-slate-400 flex items-center gap-2">
                                            {!isModpack && <span className={dep.isOptional ? '' : 'text-amber-600 dark:text-amber-500 font-bold'}>{dep.isOptional ? 'Optional' : 'Required'}</span>}
                                            <span className="font-mono opacity-75">v{dep.versionNumber}</span>
                                        </div>
                                    </div>
                                </button>
                            );
                        })}
                    </div>
                </SidebarSection>
            )}

            {licenseInfo && (
                <SidebarSection title="License" icon={Scale}>
                    <div className="text-xs font-bold">
                        {licenseInfo.url ? (
                            <a href={licenseInfo.url} target="_blank" rel="noopener noreferrer" className="block text-center p-2 rounded-lg bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/5 text-slate-700 dark:text-slate-300 hover:text-modtale-accent hover:border-modtale-accent transition-all truncate">
                                {licenseInfo.name}
                            </a>
                        ) : (
                            <div className="text-center p-2 rounded-lg bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/5 text-slate-700 dark:text-slate-300 truncate">
                                {licenseInfo.name}
                            </div>
                        )}
                    </div>
                </SidebarSection>
            )}

            <SidebarSection title="Project ID" icon={Hash}>
                <div className="flex items-center justify-between group bg-white dark:bg-slate-900/50 p-3 rounded-xl border border-slate-200 dark:border-white/5">
                    <code className="text-xs font-mono text-slate-700 dark:text-slate-300">{mod.id}</code>
                    <button onClick={handleCopyId} aria-label="Copy Project ID" className="text-slate-500 hover:text-modtale-accent transition-colors">
                        {copiedId ? <Check className="w-4 h-4 text-green-600 dark:text-green-500" /> : <Copy className="w-4 h-4" />}
                    </button>
                </div>
            </SidebarSection>
        </div>
    );
});
ProjectSidebar.displayName = 'ProjectSidebar';

interface VoteWidgetProps {
    score: number;
    userVote: 'up' | 'down' | null;
    onVote: (up: boolean) => void;
    vertical?: boolean;
}

const VoteWidget: React.FC<VoteWidgetProps> = ({ score, userVote, onVote, vertical = true }) => {
    return (
        <div className={`flex ${vertical ? 'flex-col items-center' : 'items-center gap-2'} shrink-0 mt-1`}>
            <button
                onClick={() => onVote(true)}
                className={`p-1.5 rounded-lg hover:bg-slate-200 dark:hover:bg-white/10 transition-colors ${userVote === 'up' ? 'text-modtale-accent' : 'text-slate-400'}`}
                aria-label="Helpful"
            >
                <ArrowBigUp className={`w-6 h-6 ${userVote === 'up' ? 'fill-current' : ''}`} />
            </button>
            <span className={`text-sm font-black min-w-[1.5rem] text-center ${userVote === 'up' ? 'text-modtale-accent' : userVote === 'down' ? 'text-orange-500' : 'text-slate-500'}`}>
                {score > 0 ? `+${score}` : score}
            </span>
            <button
                onClick={() => onVote(false)}
                className={`p-1.5 rounded-lg hover:bg-slate-200 dark:hover:bg-white/10 transition-colors ${userVote === 'down' ? 'text-orange-500' : 'text-slate-400'}`}
                aria-label="Not Helpful"
            >
                <ArrowBigDown className={`w-6 h-6 ${userVote === 'down' ? 'fill-current' : ''}`} />
            </button>
        </div>
    );
};

interface CommentSectionProps {
    modId: string;
    comments: Comment[];
    currentUser: User | null;
    isCreator: boolean;
    onCommentSubmitted: (newComments: Comment[]) => void;
    onError: (msg: string) => void;
    onSuccess: (msg: string) => void;
    innerRef?: React.RefObject<HTMLDivElement | null>;
    commentsDisabled?: boolean;
    onReport: (commentId: string) => void;
}

const CommentSection: React.FC<CommentSectionProps> = React.memo(({ modId, comments, currentUser, isCreator, onCommentSubmitted, onError, onSuccess, innerRef, commentsDisabled, onReport }) => {
    const [text, setText] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [editingCommentId, setEditingCommentId] = useState<string | null>(null);
    const [replyingCommentId, setReplyingCommentId] = useState<string | null>(null);
    const [replyText, setReplyText] = useState('');
    const [userProfiles, setUserProfiles] = useState<Record<string, {username: string, avatarUrl: string}>>({});

    useEffect(() => {
        const userIds = new Set<string>();
        comments.forEach(c => {
            const anyC = c as any;
            const aId = anyC.authorId || anyC.userId;
            if (aId && !userProfiles[aId]) userIds.add(aId);

            if (anyC.developerReply) {
                const rId = anyC.developerReply.authorId || anyC.developerReply.userId;
                if (rId && !userProfiles[rId]) userIds.add(rId);
            }
        });

        if (userIds.size > 0) {
            api.post('/users/batch', { userIds: Array.from(userIds) })
                .then(res => {
                    const profiles: Record<string, {username: string, avatarUrl: string}> = {};
                    res.data.forEach((u: User) => {
                        profiles[u.id] = { username: u.username, avatarUrl: u.avatarUrl };
                    });
                    setUserProfiles(prev => ({...prev, ...profiles}));
                })
                .catch(() => {});
        }
    }, [comments]);

    const handleVote = async (commentId: string, isReply: boolean, upvote: boolean) => {
        if (!currentUser) {
            onError("Log in to vote on comments.");
            return;
        }
        try {
            const endpoint = isReply ? `/projects/${modId}/comments/${commentId}/reply/vote` : `/projects/${modId}/comments/${commentId}/vote`;
            await api.post(`${endpoint}?upvote=${upvote}`);
            const res = await api.get(`/projects/${modId}`);
            onCommentSubmitted(res.data.comments || []);
        } catch (err: any) {
            onError("Failed to register vote.");
        }
    };

    const resolveAvatar = (url?: string | null) => {
        if (!url || url === 'null') return null;
        if (url.startsWith('http')) return url;
        return `${BACKEND_URL}${url.startsWith('/') ? '' : '/'}${url}`;
    };

    const startEditing = useCallback((comment: Comment) => {
        setText(comment.content);
        setEditingCommentId(comment.id);
        if(innerRef?.current) {
            const y = innerRef.current.getBoundingClientRect().top + window.scrollY - 100;
            window.scrollTo({top: y, behavior: 'smooth'});
        }
    }, [innerRef]);

    const cancelEdit = useCallback(() => {
        setEditingCommentId(null);
        setText('');
    }, []);

    const submit = async (e: React.FormEvent) => {
        e.preventDefault();
        setSubmitting(true);
        try {
            if (editingCommentId) {
                await api.put(`/projects/${modId}/comments/${editingCommentId}`, { content: text });
                onSuccess('Comment updated!');
            } else {
                await api.post(`/projects/${modId}/comments`, { content: text });
                onSuccess('Comment posted!');
            }
            const res = await api.get(`/projects/${modId}`);
            onCommentSubmitted(res.data.comments || []);
            setEditingCommentId(null);
            setText('');
        } catch (err: any) { onError(err.response?.data || 'Failed to post comment.'); } finally { setSubmitting(false); }
    };

    const deleteComment = async (commentId: string) => {
        if(!window.confirm("Are you sure you want to delete this comment?")) return;
        setSubmitting(true);
        try {
            await api.delete(`/projects/${modId}/comments/${commentId}`);
            const res = await api.get(`/projects/${modId}`);
            onCommentSubmitted(res.data.comments || []);
            onSuccess('Comment deleted.');
        } catch (err: any) { onError(err.response?.data || 'Failed to delete.'); } finally { setSubmitting(false); }
    };

    const submitReply = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!replyingCommentId) return;
        setSubmitting(true);
        try {
            await api.post(`/projects/${modId}/comments/${replyingCommentId}/reply`, { reply: replyText });
            const res = await api.get(`/projects/${modId}`);
            onCommentSubmitted(res.data.comments || []);
            setReplyingCommentId(null);
            setReplyText('');
            onSuccess('Reply posted!');
        } catch (err: any) { onError(err.response?.data || 'Failed to post reply.'); } finally { setSubmitting(false); }
    };

    if (commentsDisabled && !isCreator) return null;

    const currentUserAvatar = resolveAvatar(currentUser?.avatarUrl);

    return (
        <div ref={innerRef} id="comments" className="mt-12 pt-10 scroll-mt-24 border-t border-slate-200 dark:border-white/5">
            <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-8 flex items-center gap-3">
                <MessageSquare className="w-6 h-6 text-modtale-accent" aria-hidden="true" /> {comments.length} Comments
            </h2>

            {commentsDisabled && (
                <div className="mb-6 p-4 bg-orange-500/10 border border-orange-500/20 text-orange-600 dark:text-orange-500 rounded-xl flex items-center gap-2 text-sm font-bold">
                    <Flag className="w-4 h-4" aria-hidden="true"/> Comments are currently disabled. Only you can see them.
                </div>
            )}

            {currentUser ? (
                <div className="mb-12 flex gap-4">
                    <form onSubmit={submit} className="flex-1 bg-slate-50 dark:bg-black/20 rounded-xl border border-slate-200 dark:border-white/5 overflow-hidden shadow-sm focus-within:border-modtale-accent focus-within:ring-1 focus-within:ring-modtale-accent transition-all p-4 sm:p-5 flex flex-col gap-3 sm:gap-4">
                        <div className="flex justify-between items-center">
                            <h3 className="font-bold text-modtale-accent m-0 text-[11px] uppercase tracking-widest">{editingCommentId ? 'Edit your comment' : 'Leave a comment'}</h3>
                            {editingCommentId && <button type="button" onClick={cancelEdit} className="text-[11px] text-slate-500 hover:text-red-500 font-bold uppercase tracking-widest transition-colors">Cancel</button>}
                        </div>
                        <div className="flex items-start gap-3">
                            <div className="w-9 h-9 rounded-full bg-slate-200 dark:bg-slate-800 flex items-center justify-center font-bold text-slate-500 overflow-hidden shrink-0 shadow-sm border border-slate-200 dark:border-white/5 mt-0.5">
                                {currentUserAvatar ? (
                                    <OptimizedImage src={currentUserAvatar} alt={`${currentUser?.username} Avatar`} baseWidth={36} className="w-full h-full object-cover" />
                                ) : (
                                    currentUser?.username?.charAt(0).toUpperCase() || '?'
                                )}
                            </div>
                            <textarea
                                aria-label="Comment content"
                                value={text}
                                onChange={e => setText(e.target.value)}
                                className="w-full bg-transparent border-none text-slate-900 dark:text-white outline-none font-medium text-sm min-h-[60px] resize-y placeholder-slate-400 dark:placeholder-slate-500 pt-1.5"
                                placeholder="What are your thoughts?"
                                required
                            />
                        </div>
                        <div className="flex justify-end pt-1">
                            <button type="submit" disabled={submitting} className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-5 py-1.5 rounded-lg font-bold flex items-center gap-1.5 disabled:opacity-50 transition-colors text-xs shadow-md">
                                <Send className="w-3.5 h-3.5" aria-hidden="true" /> {editingCommentId ? 'Update' : 'Post Comment'}
                            </button>
                        </div>
                    </form>
                </div>
            ) : <div className="mb-8 p-6 bg-slate-50 dark:bg-black/20 rounded-xl text-center text-slate-500 font-bold border border-slate-200 dark:border-white/5 shadow-sm">Log in to join the conversation.</div>}

            <div className="space-y-4">
                {comments?.length > 0 ? comments.map((comment) => {
                    const anyC = comment as any;
                    const authorId = anyC.authorId || anyC.userId;
                    const profile = userProfiles[authorId];
                    const authorUsername = profile?.username || anyC.author?.username || 'Unknown';

                    const rawAvatar = authorId ? userProfiles[authorId]?.avatarUrl : null;
                    const authorAvatar = resolveAvatar(rawAvatar);

                    const score = (anyC.upvotes?.length || 0) - (anyC.downvotes?.length || 0);
                    const userVote = currentUser && anyC.upvotes?.includes(currentUser.id) ? 'up' : (currentUser && anyC.downvotes?.includes(currentUser.id) ? 'down' : null);

                    const profileLink = `/creator/${authorUsername}`;
                    const isCommentOwner = currentUser && (currentUser.id === authorId || currentUser.username === authorUsername);

                    return (
                        <div key={comment.id} className="p-4 sm:p-5 bg-slate-50 dark:bg-white/[0.02] rounded-xl border border-slate-200 dark:border-white/5 shadow-sm group relative flex gap-3 sm:gap-4">
                            <VoteWidget score={score} userVote={userVote} onVote={(up) => handleVote(comment.id, false, up)} />

                            <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-3 mb-2">
                                    <Link to={profileLink} className="shrink-0">
                                        <div className="w-10 h-10 rounded-full bg-slate-200 dark:bg-slate-800 flex items-center justify-center font-bold text-slate-500 overflow-hidden hover:ring-2 hover:ring-modtale-accent transition-all shadow-sm border border-slate-200 dark:border-white/5">
                                            {authorAvatar ? (
                                                <OptimizedImage src={authorAvatar} alt={`${authorUsername} Avatar`} baseWidth={40} className="w-full h-full object-cover" />
                                            ) : (
                                                authorUsername.charAt(0).toUpperCase()
                                            )}
                                        </div>
                                    </Link>
                                    <div className="flex flex-col">
                                        <Link to={profileLink} className="font-bold text-sm sm:text-base text-slate-900 dark:text-white hover:text-modtale-accent transition-colors">
                                            {authorUsername}
                                        </Link>
                                        <span suppressHydrationWarning className="text-xs font-medium text-slate-500 dark:text-slate-400">
                                            {formatTimeAgo(comment.date)}
                                        </span>
                                    </div>
                                </div>

                                <div className="prose dark:prose-invert max-w-none text-slate-700 dark:text-slate-300 whitespace-pre-wrap leading-relaxed prose-code:before:hidden prose-code:after:hidden break-words">
                                    <MarkdownRenderer content={comment.content} />
                                </div>

                                <div className="mt-3 flex items-center gap-4 opacity-0 group-hover:opacity-100 transition-opacity focus-within:opacity-100">
                                    {isCreator && !comment.developerReply && (
                                        <button aria-label="Reply to comment" onClick={() => { setReplyingCommentId(comment.id); setReplyText(''); }} className="text-xs font-bold text-slate-500 hover:text-slate-900 dark:hover:text-white flex items-center gap-1.5 transition-colors">
                                            <MessageSquare className="w-4 h-4" aria-hidden="true"/> Reply
                                        </button>
                                    )}
                                    {isCreator && comment.developerReply && (
                                        <button aria-label="Edit reply" onClick={() => { setReplyingCommentId(comment.id); setReplyText(comment.developerReply?.content || ''); }} className="text-xs font-bold text-slate-500 hover:text-slate-900 dark:hover:text-white flex items-center gap-1.5 transition-colors">
                                            <MessageSquare className="w-4 h-4" aria-hidden="true"/> Edit Reply
                                        </button>
                                    )}
                                    {currentUser && !isCommentOwner && (
                                        <button aria-label="Report comment" onClick={() => onReport(comment.id)} className="text-xs font-bold text-slate-500 hover:text-red-500 flex items-center gap-1.5 transition-colors">
                                            <Flag className="w-4 h-4" aria-hidden="true"/> Report
                                        </button>
                                    )}
                                    {isCommentOwner && (
                                        <button aria-label="Edit comment" onClick={() => startEditing(comment)} className="text-xs font-bold text-slate-500 hover:text-modtale-accent flex items-center gap-1.5 transition-colors">
                                            <Edit className="w-4 h-4" aria-hidden="true"/> Edit
                                        </button>
                                    )}
                                    {(isCreator || isCommentOwner) && (
                                        <button aria-label="Delete comment" onClick={() => deleteComment(comment.id)} className="text-xs font-bold text-slate-500 hover:text-red-500 flex items-center gap-1.5 transition-colors">
                                            <Trash className="w-4 h-4" aria-hidden="true"/> Delete
                                        </button>
                                    )}
                                </div>

                                {replyingCommentId === comment.id && (
                                    <form onSubmit={submitReply} className="mt-4 pl-4 border-l-2 border-slate-200 dark:border-white/10 relative">
                                        <textarea
                                            aria-label="Developer reply content"
                                            value={replyText}
                                            onChange={e => setReplyText(e.target.value)}
                                            className="w-full bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/10 rounded-xl p-4 text-base focus:outline-none focus:ring-2 focus:ring-modtale-accent text-slate-900 dark:text-white transition-all shadow-inner resize-y"
                                            placeholder="Write a reply..."
                                            rows={3}
                                        />
                                        <div className="mt-2 flex justify-end gap-2">
                                            <button type="button" onClick={() => setReplyingCommentId(null)} className="text-sm font-bold px-4 py-2 text-slate-500 hover:text-slate-900 dark:hover:text-white transition-colors">Cancel</button>
                                            <button type="submit" disabled={submitting} className="text-sm font-bold bg-modtale-accent text-white px-5 py-2 rounded-lg flex items-center gap-2 hover:bg-modtale-accentHover transition-colors shadow-sm">
                                                <CornerDownRight className="w-4 h-4" aria-hidden="true"/> Post Reply
                                            </button>
                                        </div>
                                    </form>
                                )}

                                {comment.developerReply && !replyingCommentId && (() => {
                                    const devReply = comment.developerReply as any;
                                    const replyId = devReply.authorId || devReply.userId;

                                    const replyProfile = userProfiles[replyId];
                                    const replyUsername = replyProfile?.username || devReply.author?.username || 'Developer';

                                    const rawReplyAvatar = replyId ? replyProfile?.avatarUrl : null;
                                    const replyAvatar = resolveAvatar(rawReplyAvatar);

                                    const replyProfileLink = `/creator/${replyUsername}`;

                                    const replyScore = (devReply.upvotes?.length || 0) - (devReply.downvotes?.length || 0);
                                    const replyUserVote = currentUser && devReply.upvotes?.includes(currentUser.id) ? 'up' : (currentUser && devReply.downvotes?.includes(currentUser.id) ? 'down' : null);

                                    return (
                                        <div className="mt-3 flex gap-3 relative">
                                            <div className="absolute -left-[1.75rem] sm:-left-[2.25rem] top-0 bottom-4 w-px bg-slate-200 dark:bg-white/10"></div>
                                            <div className="absolute -left-[1.75rem] sm:-left-[2.25rem] top-4 w-4 h-px bg-slate-200 dark:bg-white/10"></div>

                                            <VoteWidget score={replyScore} userVote={replyUserVote} onVote={(up) => handleVote(comment.id, true, up)} />

                                            <div className="flex-1 min-w-0 bg-modtale-accent/5 dark:bg-modtale-accent/[0.02] rounded-2xl p-4 border border-modtale-accent/10 dark:border-modtale-accent/20">
                                                <div className="flex items-center gap-3 mb-2">
                                                    <Link to={replyProfileLink} className="shrink-0">
                                                        <div className="w-8 h-8 rounded-full bg-slate-200 dark:bg-slate-800 flex items-center justify-center font-black overflow-hidden hover:ring-2 hover:ring-modtale-accent transition-all shadow-sm border border-slate-200 dark:border-white/5">
                                                            {replyAvatar ? (
                                                                <OptimizedImage src={replyAvatar} alt={`${replyUsername} Avatar`} baseWidth={32} className="w-full h-full object-cover" />
                                                            ) : (
                                                                <Crown className="w-4 h-4 text-modtale-accent" aria-hidden="true" />
                                                            )}
                                                        </div>
                                                    </Link>
                                                    <div className="flex flex-col">
                                                        <Link to={replyProfileLink} className="font-bold text-sm text-slate-900 dark:text-white hover:text-modtale-accent transition-colors flex items-center gap-1.5">
                                                            {replyUsername}
                                                            <span className="bg-modtale-accent/10 text-modtale-accent text-[9px] px-1.5 py-0.5 rounded font-black uppercase tracking-widest flex items-center gap-1"><Crown className="w-2.5 h-2.5"/> Creator</span>
                                                        </Link>
                                                        <span suppressHydrationWarning className="text-xs font-medium text-slate-500 dark:text-slate-400">
                                                            {formatTimeAgo(comment.developerReply.date)}
                                                        </span>
                                                    </div>
                                                </div>

                                                <div className="prose dark:prose-invert max-w-none text-slate-700 dark:text-slate-300 leading-relaxed prose-code:before:hidden prose-code:after:hidden break-words">
                                                    <MarkdownRenderer content={comment.developerReply.content} />
                                                </div>

                                                <div className="mt-3 flex items-center gap-4 opacity-0 group-hover:opacity-100 transition-opacity focus-within:opacity-100">
                                                    {currentUser && currentUser.id !== replyId && (
                                                        <button aria-label="Report comment" onClick={() => onReport(comment.id)} className="text-xs font-bold text-slate-500 hover:text-red-500 flex items-center gap-1.5 transition-colors">
                                                            <Flag className="w-4 h-4" aria-hidden="true"/> Report
                                                        </button>
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    );
                                })()}
                            </div>
                        </div>
                    );
                }) : <div className="text-center py-16 text-slate-500 font-medium bg-white dark:bg-white/[0.02] rounded-2xl border border-slate-200 dark:border-white/5 border-dashed">No comments yet. Be the first to share your thoughts!</div>}
            </div>
        </div>
    );
});
CommentSection.displayName = 'CommentSection';

export const ModDetail: React.FC<{
    onToggleFavorite: (id: string) => void;
    isLiked: (id: string) => boolean;
    currentUser: User | null;
    onDownload: (id: string) => void;
    downloadedSessionIds: Set<string>;
    onRefresh: () => Promise<void>;
}> = ({
          onToggleFavorite,
          isLiked,
          currentUser,
          onDownload,
          downloadedSessionIds,
          onRefresh
      }) => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const location = useLocation();
    const realId = id ? extractId(id) : '';
    const { isMobile } = useMobile();
    const { initialData } = useSSRData();
    const initialMod = (initialData?.id && extractId(initialData.id) === realId) ? (initialData as Mod) : null;

    const [mod, setMod] = useState<Mod | null>(initialMod);
    const [loading, setLoading] = useState(!initialMod);
    const [isNotFound, setIsNotFound] = useState(false);

    const [showDownloadModal, setShowDownloadModal] = useState(location.pathname.endsWith('/download'));
    const [showAllVersionsModal, setShowAllVersionsModal] = useState(location.pathname.endsWith('/changelog'));
    const [statusModal, setStatusModal] = useState<any>(null);
    const [isShareOpen, setIsShareOpen] = useState(false);
    const [pendingDownloadVer, setPendingDownloadVer] = useState<{url: string, ver: string, deps: any[]} | null>(null);
    const [showPostDownloadModal, setShowPostDownloadModal] = useState(false);

    const isWikiRoute = location.pathname.includes('/wiki');
    const wikiMatch = location.pathname.match(/\/wiki\/?(.*)/);
    const wikiPageSlug = wikiMatch && wikiMatch[1] ? wikiMatch[1] : undefined;

    const { data: wikiData, loading: wikiLoading, error: wikiError } = useHMWiki(mod?.hmWikiSlug, wikiPageSlug, isWikiRoute && mod?.hmWikiEnabled === true);

    const isGalleryRoute = location.pathname.endsWith('/gallery');
    const parsedHash = parseInt(location.hash.replace('#', ''));
    const galleryIndex = isGalleryRoute && !isNaN(parsedHash) && parsedHash > 0 && mod?.galleryImages && parsedHash <= mod.galleryImages.length ? parsedHash - 1 : null;

    const [isFollowing, setIsFollowing] = useState(false);
    const [depMeta, setDepMeta] = useState<Record<string, { icon: string, title: string }>>({});

    const [contributors, setContributors] = useState<User[]>([]);
    const [orgMembers, setOrgMembers] = useState<User[]>([]);
    const [authorProfile, setAuthorProfile] = useState<User | null>(null);

    const [showExperimental, setShowExperimental] = useState(() => {
        if (initialMod?.versions?.length) {
            return !initialMod.versions.some((v: any) => !v.channel || v.channel === 'RELEASE');
        }
        return false;
    });

    const [showMobileLinks, setShowMobileLinks] = useState(false);
    const [showMobileDeps, setShowMobileDeps] = useState(false);

    const [reportTarget, setReportTarget] = useState<{id: string, type: 'PROJECT' | 'COMMENT' | 'USER', title?: string} | null>(null);

    const commentsRef = useRef<HTMLDivElement>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);
    const depsDropdownRef = useRef<HTMLDivElement>(null);
    const currentUrl = typeof window !== 'undefined' ? window.location.href : `https://modtale.net${location.pathname}`;

    const projectUrl = getProjectUrl(mod);

    const wikiTopRef = useRef<HTMLDivElement>(null);
    const prevWikiSlugRef = useRef(wikiPageSlug);
    const prevPathnameRef = useRef(location.pathname);

    useEffect(() => {
        if (isWikiRoute && !wikiLoading && wikiTopRef.current) {
            const wasWikiNavigation = prevPathnameRef.current.includes('/wiki') && location.pathname.includes('/wiki');
            const slugChanged = prevWikiSlugRef.current !== wikiPageSlug;

            if (wasWikiNavigation && slugChanged) {
                setTimeout(() => {
                    if (wikiTopRef.current) {
                        const y = wikiTopRef.current.getBoundingClientRect().top + window.scrollY - 120;
                        window.scrollTo({ top: y, behavior: 'smooth' });
                    }
                }, 50);
            }
            prevWikiSlugRef.current = wikiPageSlug;
        }
        prevPathnameRef.current = location.pathname;
    }, [wikiPageSlug, wikiLoading, isWikiRoute, location.pathname]);

    useEffect(() => {
        if (isWikiRoute && !wikiPageSlug && wikiData?.mod) {
            const defaultSlug = wikiData.mod.index?.slug || (wikiData.mod.pages?.length > 0 ? wikiData.mod.pages[0].slug : null);
            if (defaultSlug) {
                navigate(`${projectUrl}/wiki/${defaultSlug}`, { replace: true });
            }
        }
    }, [isWikiRoute, wikiPageSlug, wikiData?.mod, navigate, projectUrl]);

    const handleCommentSubmitted = useCallback((c: Comment[]) => {
        setMod(prev => prev ? {...prev, comments: c} : null);
        if(onRefresh) onRefresh();
    }, [onRefresh]);

    const handleError = useCallback((m: string) => setStatusModal({type:'error', title:'Error', msg:m}), []);
    const handleSuccess = useCallback((m: string) => setStatusModal({type:'success', title:'Success', msg:m}), []);
    const handleReport = useCallback((commentId: string) => setReportTarget({id: commentId, type: 'COMMENT'}), []);

    useEffect(() => {
        if (mod && extractId(mod.id) === realId) {
            setLoading(false);
            return;
        }

        if (!realId) {
            setIsNotFound(true);
            setLoading(false);
            return;
        }

        let isMounted = true;
        setLoading(true);

        api.get(`/projects/${realId}`)
            .then(res => {
                if (isMounted) {
                    setMod(res.data);
                    const vers = res.data.versions || [];
                    if (vers.length > 0 && !vers.some((v: any) => !v.channel || v.channel === 'RELEASE')) {
                        setShowExperimental(true);
                    }
                }
            })
            .catch(() => {
                if (isMounted) setIsNotFound(true);
            })
            .finally(() => {
                if (isMounted) setLoading(false);
            });

        return () => { isMounted = false; };
    }, [realId]);

    useEffect(() => {
        if (location.pathname.endsWith('/gallery') && mod?.galleryImages?.length) {
            const hashNum = parseInt(location.hash.replace('#', ''));
            if (isNaN(hashNum) || hashNum < 1 || hashNum > mod.galleryImages.length) {
                navigate(`${getProjectUrl(mod)}/gallery#1`, { replace: true });
            }
        }
    }, [location.pathname, location.hash, mod, navigate]);

    useEffect(() => {
        if (currentUser?.followingIds && mod?.author) {
            setIsFollowing(currentUser.followingIds.includes(mod.author));
        } else {
            setIsFollowing(false);
        }
    }, [currentUser, mod?.author]);

    const canEdit = mod?.canEdit ?? (currentUser && mod && (currentUser.username === mod.author || mod.teamMembers?.some(m => m.userId === currentUser.id)));
    const analyticsFired = useRef(false);
    const fetchedDepMeta = useRef<Set<string>>(new Set());

    const projectMeta = mod ? generateProjectMeta(mod) : null;
    const breadcrumbSchema = mod ? generateBreadcrumbSchema([...getBreadcrumbsForClassification(mod.classification || 'PLUGIN'), { name: mod.title, url: getProjectUrl(mod) }]) : null;
    const canonicalUrl = mod ? `https://modtale.net${getProjectUrl(mod)}` : null;
    const ogImageUrl = mod ? `${API_BASE_URL}/og/project/${mod.id}.png` : '';

    useEffect(() => {
        if (mod && mod.id && !analyticsFired.current) {
            analyticsFired.current = true;
            api.post(`/analytics/view/${mod.id}`).catch(() => {});
        }
    }, [mod?.id]);

    useEffect(() => {
        const fetchTeam = async () => {
            if (!mod) return;

            try {
                const authorIdToFetch = (mod as any).authorId;
                let authorData;

                if (authorIdToFetch) {
                    const authorRes = await api.get(`/user/profile/${authorIdToFetch}`);
                    authorData = authorRes.data;
                } else {
                    const lookupRes = await api.get(`/users/lookup/${mod.author}`);
                    const authorRes = await api.get(`/user/profile/${lookupRes.data.id}`);
                    authorData = authorRes.data;
                }

                setAuthorProfile(authorData);

                if (authorData.accountType === 'ORGANIZATION') {
                    const membersRes = await api.get(`/orgs/${authorData.id}/members`);
                    setOrgMembers(membersRes.data);
                }
            } catch (e) {
                console.error("Failed to fetch author profile", e);
            }

            if (mod.teamMembers && mod.teamMembers.length > 0) {
                try {
                    const userIds = mod.teamMembers.map(m => m.userId);
                    const res = await api.post('/users/batch', { userIds });
                    setContributors(res.data);
                } catch (e) {
                    console.error("Failed to fetch contributors", e);
                }
            }
        };

        if (mod) fetchTeam();
    }, [mod?.id, mod?.author, mod?.teamMembers]);

    useEffect(() => {
        if (mod && !loading) {
            const canonicalPath = getProjectUrl(mod);
            const currentPath = location.pathname;

            if (currentPath.replace(/\/$/, "") !== canonicalPath.replace(/\/$/, "")) {
                if (!currentPath.endsWith('/download') && !currentPath.endsWith('/changelog') && !currentPath.endsWith('/gallery') && !currentPath.includes('/wiki')) {
                    navigate(canonicalPath, { replace: true });
                }
            }

            if (location.hash === '#comments' && commentsRef.current) {
                const y = commentsRef.current.getBoundingClientRect().top + window.scrollY - 100;
                window.scrollTo({top: y, behavior: 'smooth'});
            }
        }
    }, [mod, loading, location.pathname, location.hash, navigate]);

    useEffect(() => {
        if (location.pathname.endsWith('/download')) setShowDownloadModal(true);
        if (location.pathname.endsWith('/changelog')) setShowAllVersionsModal(true);
    }, [location.pathname]);

    useEffect(() => {
        const handleClick = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
                setShowMobileLinks(false);
            }
            if (depsDropdownRef.current && !depsDropdownRef.current.contains(e.target as Node)) {
                setShowMobileDeps(false);
            }
        };
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, []);

    const allVersions = mod?.versions || [];

    const sortedHistory = useMemo(() => {
        const displayedVersions = allVersions.filter(v => showExperimental ? true : (!v.channel || v.channel === 'RELEASE'));
        return [...displayedVersions].sort((a, b) => compareSemVer(b.versionNumber, a.versionNumber));
    }, [allVersions, showExperimental]);

    const hasExperimentalVersions = useMemo(() => {
        return allVersions.some(v => v.channel && v.channel !== 'RELEASE');
    }, [allVersions]);

    const latestForGame = useMemo(() => {
        const result: Record<string, ProjectVersion[]> = {};
        allVersions.forEach(v => {
            const gvList = v.gameVersions?.length ? v.gameVersions : ['Unknown'];
            gvList.forEach(gv => {
                if (!result[gv]) result[gv] = [];
                result[gv].push(v);
            });
        });
        return result;
    }, [allVersions]);

    const latestDependencies = useMemo(() => {
        if (!allVersions.length) return [];
        return [...allVersions].sort((a, b) => new Date(b.releaseDate).getTime() - new Date(a.releaseDate).getTime())[0].dependencies || [];
    }, [allVersions]);

    useEffect(() => {
        if (!latestDependencies.length) return;
        const fetchMeta = async () => {
            const missing = latestDependencies.filter(d =>
                !depMeta[d.modId] && !fetchedDepMeta.current.has(d.modId)
            );

            if (!missing.length) return;

            missing.forEach(d => fetchedDepMeta.current.add(d.modId));

            const newMeta = { ...depMeta };
            await Promise.all(missing.map(async (d) => {
                try {
                    const res = await api.get(`/projects/${d.modId}/meta`);
                    newMeta[d.modId] = { icon: res.data.icon, title: res.data.title };
                } catch (e) {
                    newMeta[d.modId] = { icon: '', title: d.modTitle || d.modId };
                }
            }));

            setDepMeta(prev => ({...prev, ...newMeta}));
        };
        fetchMeta();
    }, [latestDependencies]);

    const handleShare = async () => {
        if (isMobile && navigator.share) {
            try {
                await navigator.share({ title: mod?.title, url: currentUrl });
            } catch (e) {
            }
        } else {
            setIsShareOpen(true);
        }
    };

    const handleFollowToggle = async () => {
        if (!currentUser || !mod) return;
        const oldState = isFollowing;
        setIsFollowing(!oldState);
        try { await api.post(oldState ? `/user/unfollow/${mod.author}` : `/user/follow/${mod.author}`); } catch (e) { setIsFollowing(oldState); }
    };

    const initiateDownload = useCallback((url: string, ver?: string, deps?: any[]) => {
        if (mod?.classification !== 'MODPACK' && deps && deps.length > 0) {
            setPendingDownloadVer({ url, ver: ver || '', deps });
            setShowDownloadModal(false);
        } else {
            executeDownload(url, ver, false);
        }
    }, [mod?.classification]);

    const executeDownload = async (fileUrl: string, ver?: string, asBundle: boolean = false) => {
        try {
            if (!ver) {
                const targetUrl = `${API_BASE_URL}/files/download/${encodeURI(fileUrl)}`;
                window.open(targetUrl, '_blank');
                if(mod && !downloadedSessionIds.has(mod.id)) {
                    setMod(prev => prev ? { ...prev, downloadCount: prev.downloadCount + 1 } : null);
                    onDownload(mod.id);
                }
                setPendingDownloadVer(null); setShowDownloadModal(false); setShowAllVersionsModal(false);

                if (localStorage.getItem('hideInstallInstructions') !== 'true') {
                    setShowPostDownloadModal(true);
                }

                if (location.pathname.endsWith('/download')) navigate(getProjectUrl(mod!), { replace: true });
                return;
            }

            const endpoint = asBundle
                ? `/projects/${mod?.id}/versions/${ver}/download-bundle-url`
                : `/projects/${mod?.id}/versions/${ver}/download-url`;

            const response = await api.get(endpoint);
            const { downloadUrl } = response.data;

            window.open(`${API_BASE_URL}${downloadUrl}`, '_blank');
            if(mod && !downloadedSessionIds.has(mod.id)) {
                setMod(prev => prev ? { ...prev, downloadCount: prev.downloadCount + 1 } : null);
                onDownload(mod.id);
            }
            setPendingDownloadVer(null); setShowDownloadModal(false); setShowAllVersionsModal(false);

            if (localStorage.getItem('hideInstallInstructions') !== 'true') {
                setShowPostDownloadModal(true);
            }

            if (location.pathname.endsWith('/download')) navigate(getProjectUrl(mod!), { replace: true });
        } catch (error) {
            setStatusModal({ type: 'error', title: 'Download Failed', msg: 'Unable to generate download link. Please try again.' });
        }
    };

    if (isNotFound) return <NotFound />;
    if (loading) return <div className="min-h-screen bg-slate-950 flex items-center justify-center"><Spinner fullScreen={false} className="w-8 h-8" /></div>;
    if (!mod) return null;

    if (isWikiRoute && (!mod.hmWikiEnabled || !mod.hmWikiSlug)) return <NotFound />;
    if (isGalleryRoute && (!mod.galleryImages || mod.galleryImages.length === 0)) return <NotFound />;

    const resolveUrl = (url: string) => url.startsWith('/api') ? `${BACKEND_URL}${url}` : url;

    const links = [
        mod.repositoryUrl && { type: 'SOURCE', url: mod.repositoryUrl, icon: Github, label: 'Source Code' },
        mod.links?.DISCORD && { type: 'DISCORD', url: mod.links.DISCORD, icon: DiscordIcon, label: 'Discord' },
        mod.links?.WEBSITE && { type: 'WEBSITE', url: mod.links.WEBSITE, icon: Globe, label: 'Website' },
        mod.links?.WIKI && { type: 'WIKI', url: mod.links.WIKI, icon: BookOpen, label: 'Wiki' },
        mod.links?.ISSUE_TRACKER && { type: 'ISSUE', url: mod.links.ISSUE_TRACKER, icon: Bug, label: 'Issues' }
    ].filter(Boolean) as { type: string, url: string, icon: any, label: string }[];

    const getLinkColor = (type: string) => {
        if (type === 'DISCORD') return 'text-[#5865F2] hover:bg-[#5865F2]/20 border-[#5865F2]/20';
        if (type === 'WEBSITE') return 'text-blue-500 dark:text-blue-400 hover:bg-blue-500/20 border-blue-500/20';
        if (type === 'ISSUE') return 'text-red-500 dark:text-red-400 hover:bg-red-500/20 border-red-500/20';
        if (type === 'SOURCE') return 'text-slate-700 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-white/10 border-slate-200 dark:border-white/10';
        return 'text-amber-600 dark:text-amber-500 hover:bg-amber-500/10 border-amber-500/20';
    };

    const displayClassification = toTitleCase(mod.classification || 'PLUGIN');

    const isUnlisted = mod.status === 'UNLISTED';
    const isArchived = mod.status === 'ARCHIVED';

    return (
        <>
            {projectMeta && (
                <Helmet>
                    <title>{projectMeta.title}</title>
                    <meta name="description" content={projectMeta.description} />
                    {canonicalUrl && <link rel="canonical" href={canonicalUrl} />}
                    {breadcrumbSchema && <script type="application/ld+json">{JSON.stringify(breadcrumbSchema)}</script>}

                    <meta property="og:title" content={mod.title} />
                    <meta property="og:site_name" content="Modtale" />
                    <meta property="og:image" content={ogImageUrl} />
                    <meta property="og:type" content="website" />
                    <meta property="og:url" content={canonicalUrl || currentUrl} />
                    <meta name="theme-color" content="#3b82f6" />

                    <meta name="twitter:card" content="summary_large_image" />
                    <meta name="twitter:title" content={mod.title} />
                    <meta name="twitter:image" content={ogImageUrl} />

                    {(isUnlisted || isArchived) && <meta name="robots" content="noindex, nofollow" />}
                </Helmet>
            )}

            {isUnlisted && (
                <div className="sticky top-16 lg:top-20 z-[100] w-full bg-amber-500/90 dark:bg-amber-500/20 backdrop-blur-md border-b border-amber-500/30 text-white dark:text-amber-500 px-4 py-3 flex items-center justify-center gap-2 text-sm font-bold shadow-md">
                    <AlertTriangle className="w-4 h-4" />
                    This project is unlisted. Only people with the link can view it.
                </div>
            )}

            {isArchived && (
                <div className="sticky top-16 lg:top-20 z-[100] w-full bg-slate-600/90 dark:bg-slate-500/20 backdrop-blur-md border-b border-slate-500/30 text-white dark:text-slate-400 px-4 py-3 flex items-center justify-center gap-2 text-sm font-bold shadow-md">
                    <Archive className="w-4 h-4" />
                    This project is archived. It is read-only and no longer actively maintained.
                </div>
            )}

            {statusModal && <StatusModal type={statusModal.type} title={statusModal.title} message={statusModal.msg} onClose={() => setStatusModal(null)} />}
            <ShareModal isOpen={isShareOpen} onClose={() => setIsShareOpen(false)} url={currentUrl} title={mod.title} author={mod.author} />

            <PostDownloadModal
                isOpen={showPostDownloadModal}
                onClose={() => setShowPostDownloadModal(false)}
                classification={mod.classification}
                title={mod.title}
            />

            <ReportModal
                isOpen={!!reportTarget}
                onClose={() => setReportTarget(null)}
                targetId={reportTarget?.id || mod?.id}
                targetType={reportTarget?.type || 'PROJECT'}
                targetTitle={reportTarget?.type === 'PROJECT' ? (reportTarget.title || mod?.title) : undefined}
            />

            {pendingDownloadVer && (
                <DependencyModal
                    dependencies={pendingDownloadVer.deps}
                    onClose={() => setPendingDownloadVer(null)}
                    onConfirm={() => executeDownload(pendingDownloadVer.url, pendingDownloadVer.ver, true)}
                />
            )}

            <DownloadModal
                show={showDownloadModal}
                onClose={() => {
                    setShowDownloadModal(false);
                    if (location.pathname.endsWith('/download')) navigate(projectUrl, { replace: true });
                }}
                versionsByGame={latestForGame}
                onDownload={initiateDownload}
                showExperimental={showExperimental}
                onToggleExperimental={() => setShowExperimental(!showExperimental)}
                onViewHistory={() => {
                    setShowDownloadModal(false);
                    setShowAllVersionsModal(true);
                    if (location.pathname.endsWith('/download')) navigate(`${projectUrl}/changelog`, { replace: true });
                }}
            />

            <HistoryModal
                show={showAllVersionsModal}
                onClose={() => {
                    setShowAllVersionsModal(false);
                    if (location.pathname.endsWith('/changelog')) navigate(projectUrl, { replace: true });
                }}
                history={sortedHistory}
                showExperimental={showExperimental}
                onToggleExperimental={() => setShowExperimental(!showExperimental)}
                onDownload={initiateDownload}
                hasExperimentalVersions={hasExperimentalVersions}
            />

            {galleryIndex !== null && mod.galleryImages && mod.galleryImages[galleryIndex] && (
                <div className="fixed inset-0 z-[300] bg-black/80 backdrop-blur-md flex items-center justify-center p-4 animate-in fade-in duration-200" onClick={() => navigate(projectUrl, { replace: true })}>
                    <div className="relative w-full max-w-6xl max-h-[85dvh] bg-slate-900 rounded-2xl shadow-2xl flex flex-col overflow-hidden border border-white/10" onClick={e => e.stopPropagation()}>

                        <div className="p-4 flex justify-between items-center bg-black/20 border-b border-white/10 z-10 shrink-0">
                            <span className="text-sm font-bold text-white/70">Image {galleryIndex + 1} of {mod.galleryImages.length}</span>
                            <button aria-label="Close gallery" onClick={() => navigate(projectUrl, { replace: true })} className="p-2 bg-white/5 hover:bg-white/10 text-white rounded-full transition-colors"><X className="w-5 h-5" aria-hidden="true" /></button>
                        </div>

                        <div className="flex-1 relative flex items-center justify-center bg-black/40 overflow-hidden group">
                            {mod.galleryImages.length > 1 && (
                                <>
                                    <button
                                        aria-label="Previous image"
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            const prevIdx = (galleryIndex - 1 + mod.galleryImages!.length) % mod.galleryImages!.length;
                                            navigate(`${projectUrl}/gallery#${prevIdx + 1}`, { replace: true });
                                        }}
                                        className="absolute left-4 p-3 bg-black/50 hover:bg-modtale-accent text-white rounded-full transition-all z-20 opacity-0 group-hover:opacity-100 -translate-x-4 group-hover:translate-x-0"
                                    >
                                        <ChevronLeft className="w-6 h-6" aria-hidden="true" />
                                    </button>
                                    <button
                                        aria-label="Next image"
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            const nextIdx = (galleryIndex + 1) % mod.galleryImages!.length;
                                            navigate(`${projectUrl}/gallery#${nextIdx + 1}`, { replace: true });
                                        }}
                                        className="absolute right-4 p-3 bg-black/50 hover:bg-modtale-accent text-white rounded-full transition-all z-20 opacity-0 group-hover:opacity-100 translate-x-4 group-hover:translate-x-0"
                                    >
                                        <ChevronRight className="w-6 h-6" aria-hidden="true" />
                                    </button>
                                </>
                            )}
                            <OptimizedImage
                                src={resolveUrl(mod.galleryImages[galleryIndex])}
                                alt={`${mod.title} Gallery Image ${galleryIndex + 1}`}
                                baseWidth={1200}
                                className="w-full h-full [&>img]:!object-contain shadow-lg"
                            />
                        </div>
                    </div>
                </div>
            )}

            <ProjectLayout
                bannerUrl={mod.bannerUrl}
                iconUrl={mod.imageUrl}
                onBack={() => {
                    if (window.history.state && window.history.state.idx > 0) {
                        navigate(-1);
                    } else {
                        navigate('/');
                    }
                }}
                headerActions={
                    <>
                        <button disabled={!currentUser} aria-label="Favorite" onClick={() => onToggleFavorite(mod.id)} className={`p-3 rounded-xl border transition-all ${isLiked(mod.id) ? 'bg-red-500/10 text-red-600 dark:text-red-500 border-red-500/20' : 'bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white border-slate-200 dark:border-white/5 hover:border-slate-300 dark:hover:border-white/20'}`} title="Favorite">
                            <Heart className={`w-5 h-5 ${isLiked(mod.id) ? 'fill-current' : ''}`} aria-hidden="true" />
                        </button>
                        <button onClick={handleShare} aria-label="Share" className="p-3 rounded-xl border border-slate-200 dark:border-white/5 bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-400 hover:text-blue-500 hover:border-blue-500/30 transition-all" title="Share">
                            <Share2 className="w-5 h-5" aria-hidden="true" />
                        </button>
                        {(!currentUser || currentUser.id !== mod.authorId) && (
                            <button onClick={() => setReportTarget({id: mod.id, type: 'PROJECT', title: mod.title})} aria-label="Report Project" className="p-3 rounded-xl border border-slate-200 dark:border-white/5 bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-400 hover:text-red-600 dark:hover:text-red-500 hover:border-red-500/30 transition-all" title="Report Project">
                                <Flag className="w-5 h-5" aria-hidden="true" />
                            </button>
                        )}
                        {Boolean(canEdit) && (
                            <Link to={`${projectUrl}/edit`} aria-label="Edit Project" className="p-3 rounded-xl border border-slate-200 dark:border-white/5 bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition-all block" title="Edit Project">
                                <Edit className="w-5 h-5" aria-hidden="true" />
                            </Link>
                        )}
                    </>
                }
                headerContent={
                    <>
                        <div className="flex flex-wrap items-center gap-3 mb-3">
                            <h1 className="text-3xl md:text-5xl font-black text-slate-900 dark:text-white tracking-tighter drop-shadow-sm leading-tight break-words">{mod.title}</h1>
                            <span className="bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/10 px-3 py-1 rounded-full text-xs font-bold text-blue-700 dark:text-modtale-accent tracking-widest uppercase flex items-center gap-1.5 shadow-sm whitespace-nowrap">
                                {getClassificationIcon(mod.classification || 'PLUGIN', "w-3.5 h-3.5")}{displayClassification}
                            </span>
                        </div>

                        <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm font-medium text-slate-600 dark:text-slate-400 mb-4">
                            <div className="flex items-center gap-2">
                                <span>by <Link to={`/creator/${mod.author}`} className="font-bold text-slate-800 dark:text-white hover:text-modtale-accent hover:underline decoration-2 underline-offset-4 transition-all">{mod.author}</Link></span>
                                {currentUser && currentUser.id !== mod.authorId && (
                                    <button
                                        onClick={handleFollowToggle}
                                        className={`h-6 px-2.5 rounded-lg text-[10px] uppercase font-bold tracking-widest transition-all ${isFollowing ? 'bg-slate-200 dark:bg-white/10 text-slate-600 dark:text-slate-400 hover:bg-red-500/20 hover:text-red-600 dark:hover:text-red-500' : 'bg-modtale-accent text-white hover:bg-modtale-accentHover shadow-lg shadow-modtale-accent/20'}`}
                                    >
                                        {isFollowing ? 'Unfollow' : 'Follow'}
                                    </button>
                                )}
                            </div>
                            <span suppressHydrationWarning className="hidden md:inline text-slate-400 dark:text-slate-600">•</span>
                            <span suppressHydrationWarning className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider opacity-80">
                                <Calendar className="w-3 h-3" aria-hidden="true" /> Updated <span suppressHydrationWarning>{formatTimeAgo(mod.updatedAt)}</span>
                            </span>
                        </div>

                        {mod.description && (
                            <p className="text-slate-700 dark:text-slate-300 text-base leading-relaxed max-w-4xl font-medium border-l-2 border-modtale-accent pl-4">
                                {mod.description}
                            </p>
                        )}
                    </>
                }
                actionBar={
                    <div className="flex flex-col xl:flex-row items-start xl:items-center justify-between gap-2 xl:gap-6 w-full">
                        <div className="flex flex-col md:flex-row items-stretch md:items-center gap-2 w-full xl:w-auto">
                            {(!mod.versions || mod.versions.length === 0) ? (
                                <button
                                    disabled
                                    className="flex-shrink-0 bg-modtale-accent hover:bg-modtale-accentHover disabled:bg-slate-200 dark:disabled:bg-slate-800 disabled:text-slate-500 text-white px-8 py-3.5 rounded-xl font-black flex items-center justify-center gap-2 shadow-lg shadow-modtale-accent/20 transition-all active:scale-95 group cursor-not-allowed"
                                >
                                    <Download className="w-5 h-5 group-hover:animate-bounce" aria-hidden="true" />
                                    Download
                                </button>
                            ) : (
                                <Link
                                    to={`${projectUrl}/download`}
                                    className="flex-shrink-0 bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 py-3.5 rounded-xl font-black flex items-center justify-center gap-2 shadow-lg shadow-modtale-accent/20 transition-all active:scale-95 group"
                                >
                                    <Download className="w-5 h-5 group-hover:animate-bounce" aria-hidden="true" />
                                    Download
                                </Link>
                            )}

                            <div className="hidden md:block w-px h-10 bg-slate-200 dark:bg-white/10 mx-2"></div>

                            <div className="grid grid-cols-2 md:flex md:flex-row gap-2 w-full md:w-auto">
                                {mod.hmWikiEnabled && mod.hmWikiSlug && (
                                    <Link to={`${projectUrl}/wiki`} className="flex items-center justify-center gap-2 px-5 py-3 md:py-2.5 text-sm font-bold bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 rounded-xl text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200 dark:hover:bg-white/10 transition-colors whitespace-nowrap">
                                        <BookOpen className="w-4 h-4" aria-hidden="true" /> Wiki
                                    </Link>
                                )}
                                {mod.galleryImages && mod.galleryImages.length > 0 && (
                                    <Link to={`${projectUrl}/gallery#1`} className="col-span-2 md:col-span-1 flex items-center justify-center gap-2 px-5 py-3 md:py-2.5 text-sm font-bold bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 rounded-xl text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200 dark:hover:bg-white/10 transition-colors whitespace-nowrap"><Image className="w-4 h-4" aria-hidden="true" /> Gallery</Link>
                                )}
                                <Link to={`${projectUrl}/changelog`} className="flex items-center justify-center gap-2 px-5 py-3 md:py-2.5 text-sm font-bold bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 rounded-xl text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200 dark:hover:bg-white/10 transition-colors whitespace-nowrap"><List className="w-4 h-4" aria-hidden="true" /> Changelog</Link>
                                <a
                                    href={`${projectUrl}#comments`}
                                    onClick={(e) => {
                                        if (location.pathname === projectUrl) {
                                            e.preventDefault();
                                            if (commentsRef.current) {
                                                const y = commentsRef.current.getBoundingClientRect().top + window.scrollY - 100;
                                                window.scrollTo({top: y, behavior: 'smooth'});
                                            }
                                        }
                                    }}
                                    className="flex items-center justify-center gap-2 px-5 py-3 md:py-2.5 text-sm font-bold bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 rounded-xl text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200 dark:hover:bg-white/10 transition-colors whitespace-nowrap"
                                >
                                    <MessageSquare className="w-4 h-4" aria-hidden="true" /> Comments
                                </a>
                            </div>
                        </div>

                        <div className="w-full xl:w-auto flex flex-col md:flex-row justify-start md:justify-end gap-2">
                            {isMobile && mod.classification === 'MODPACK' && latestDependencies.length > 0 && (
                                <div className="relative w-full" ref={depsDropdownRef}>
                                    <button
                                        onClick={() => { setShowMobileDeps(!showMobileDeps); setShowMobileLinks(false); }}
                                        className="w-full flex items-center justify-center gap-2 p-3 rounded-xl bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/10 font-bold text-slate-700 dark:text-slate-300"
                                    >
                                        <Box className="w-4 h-4" aria-hidden="true" /> Included Mods <ChevronDown className={`w-4 h-4 transition-transform ${showMobileDeps ? 'rotate-180' : ''}`} aria-hidden="true" />
                                    </button>
                                    {showMobileDeps && (
                                        <div className="absolute top-full left-0 right-0 mt-1.5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-50 overflow-hidden animate-in fade-in slide-in-from-top-2 p-1 max-h-[300px] overflow-y-auto">
                                            {latestDependencies.map((dep, idx) => {
                                                const meta = depMeta[dep.modId];
                                                const title = meta?.title || dep.modTitle || dep.modId;
                                                const path = `/mod/${createSlug(title, dep.modId)}`;
                                                return (
                                                    <Link
                                                        key={idx}
                                                        to={path}
                                                        onClick={() => setShowMobileDeps(false)}
                                                        className="w-full flex items-center gap-3 p-2.5 hover:bg-slate-100 dark:hover:bg-white/5 transition-colors text-slate-700 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white text-left"
                                                    >
                                                        <div className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-slate-950 flex items-center justify-center border border-slate-200 dark:border-white/5 shrink-0 overflow-hidden">
                                                            {meta?.icon ? (
                                                                <OptimizedImage
                                                                    src={resolveUrl(meta.icon)}
                                                                    alt={`${title} Icon`}
                                                                    baseWidth={32}
                                                                    className="w-full h-full"
                                                                />
                                                            ) : <Box className="w-4 h-4 text-slate-500" aria-hidden="true" />}
                                                        </div>
                                                        <div className="min-w-0 flex-1">
                                                            <div className="text-sm font-bold truncate">{title}</div>
                                                            <div className="text-[10px] text-slate-500 dark:text-slate-400 font-mono">v{dep.versionNumber}</div>
                                                        </div>
                                                        <ExternalLink className="w-3 h-3 opacity-50 shrink-0" aria-hidden="true" />
                                                    </Link>
                                                );
                                            })}
                                        </div>
                                    )}
                                </div>
                            )}

                            <div className="hidden md:flex gap-2 flex-wrap justify-end">
                                {links.map((link, idx) => (
                                    <a
                                        key={idx}
                                        href={link.url.startsWith('http') ? link.url : `https://${link.url}`}
                                        target="_blank"
                                        rel="noreferrer"
                                        className={`p-2.5 rounded-xl border transition-all ${getLinkColor(link.type)}`}
                                        title={link.label}
                                        aria-label={link.label}
                                    >
                                        <link.icon className="w-5 h-5" aria-hidden="true" />
                                    </a>
                                ))}
                            </div>

                            {links.length > 0 && (
                                <div className="md:hidden relative w-full" ref={dropdownRef}>
                                    <button
                                        onClick={() => { setShowMobileLinks(!showMobileLinks); setShowMobileDeps(false); }}
                                        className="w-full flex items-center justify-center gap-2 p-3 rounded-xl bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/10 font-bold text-slate-700 dark:text-slate-300"
                                    >
                                        <LinkIcon className="w-4 h-4" aria-hidden="true" /> External Links <ChevronDown className={`w-4 h-4 transition-transform ${showMobileLinks ? 'rotate-180' : ''}`} aria-hidden="true" />
                                    </button>
                                    {showMobileLinks && (
                                        <div className="absolute top-full left-0 right-0 mt-1.5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-50 overflow-hidden animate-in fade-in slide-in-from-top-2 p-1">
                                            {links.map((link, idx) => (
                                                <a
                                                    key={idx}
                                                    href={link.url.startsWith('http') ? link.url : `https://${link.url}`}
                                                    target="_blank"
                                                    rel="noreferrer"
                                                    className="flex items-center gap-3 p-2.5 hover:bg-slate-100 dark:hover:bg-white/5 transition-colors text-slate-700 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white"
                                                >
                                                    <div className={`p-1.5 rounded-lg border bg-slate-50 dark:bg-slate-950 ${getLinkColor(link.type)}`}>
                                                        <link.icon className="w-4 h-4" aria-hidden="true" />
                                                    </div>
                                                    <span className="text-sm font-bold">{link.label}</span>
                                                    <ExternalLink className="w-3 h-3 ml-auto opacity-50" aria-hidden="true" />
                                                </a>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    </div>
                }
                sidebarContent={
                    isWikiRoute ? (
                        wikiLoading ? (
                            <div className="flex justify-center p-4"><Spinner /></div>
                        ) : wikiError || !wikiData ? (
                            <SidebarSection title="Wiki" icon={BookOpen}>
                                <div className="text-sm text-slate-500">Navigation unavailable.</div>
                            </SidebarSection>
                        ) : (
                            <>
                                <WikiSidebar tree={wikiData.mod.pages || []} projectUrl={projectUrl} currentSlug={wikiPageSlug} indexSlug={wikiData.mod.index?.slug} />
                                <SidebarSection title="Project Info" icon={Box}>
                                    <Link to={projectUrl} className="block text-sm font-bold text-modtale-accent hover:underline flex items-center gap-2">
                                        <ChevronLeft className="w-4 h-4" /> Back to Project
                                    </Link>
                                </SidebarSection>
                            </>
                        )
                    ) : (
                        <ProjectSidebar
                            mod={mod}
                            navigate={navigate}
                            dependencies={latestDependencies}
                            depMeta={depMeta}
                            sourceUrl={(mod as any).sourceUrl || (mod as any).repoUrl}
                            contributors={contributors}
                            orgMembers={orgMembers}
                            author={authorProfile}
                        />
                    )
                }
                mainContent={
                    isWikiRoute ? (
                        <div ref={wikiTopRef} className="scroll-mt-24">
                            <WikiContent wikiLoading={wikiLoading} wikiError={wikiError} wikiData={wikiData} wikiPageSlug={wikiPageSlug} mod={mod} />
                        </div>
                    ) : (
                        <>
                            <div className="prose dark:prose-invert prose-lg max-w-none prose-code:before:hidden prose-code:after:hidden">
                                <MarkdownRenderer content={mod?.about || "*No description.*"} />
                            </div>
                            <CommentSection
                                modId={mod.id}
                                comments={mod.comments || []}
                                currentUser={currentUser}
                                isCreator={Boolean(canEdit)}
                                commentsDisabled={mod.allowComments === false}
                                onCommentSubmitted={handleCommentSubmitted}
                                onError={handleError}
                                onSuccess={handleSuccess}
                                innerRef={commentsRef}
                                onReport={handleReport}
                            />
                        </>
                    )
                }
            />
        </>
    );
};