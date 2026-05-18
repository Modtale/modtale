import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { MessageSquare, Send, Edit, Trash, Flag, CornerDownRight, Crown, ArrowBigUp, ArrowBigDown } from 'lucide-react';
import { projectClient } from '../api/projectClient';
import { MarkdownRenderer } from '@/components/ui/MarkdownRenderer';
import { OptimizedImage } from '@/components/ui/OptimizedImage';
import { formatTimeAgo } from '@/utils/modHelpers';
import { BACKEND_URL } from '@/utils/api';
import { SiteRoutes } from '@/utils/routes';
import type { Comment, User } from '@/types';

interface VoteWidgetProps {
    score: number;
    userVote: 'up' | 'down' | null;
    onVote: (up: boolean) => void;
}

const VoteWidget: React.FC<VoteWidgetProps> = ({ score, userVote, onVote }) => (
    <div className="flex flex-col items-center shrink-0 mt-1">
        <button
            type="button"
            onClick={() => onVote(true)}
            className={`p-1.5 rounded-lg hover:bg-slate-200 dark:hover:bg-white/10 transition-colors ${userVote === 'up' ? 'text-modtale-accent' : 'text-slate-400'}`}
        >
            <ArrowBigUp className={`w-6 h-6 ${userVote === 'up' ? 'fill-current' : ''}`} />
        </button>
        <span className={`text-sm font-black min-w-[1.5rem] text-center ${userVote === 'up' ? 'text-modtale-accent' : userVote === 'down' ? 'text-orange-500' : 'text-slate-500'}`}>
            {score > 0 ? `+${score}` : score}
        </span>
        <button
            type="button"
            onClick={() => onVote(false)}
            className={`p-1.5 rounded-lg hover:bg-slate-200 dark:hover:bg-white/10 transition-colors ${userVote === 'down' ? 'text-orange-500' : 'text-slate-400'}`}
        >
            <ArrowBigDown className={`w-6 h-6 ${userVote === 'down' ? 'fill-current' : ''}`} />
        </button>
    </div>
);

interface CommentSectionProps {
    projectId: string;
    comments: Comment[];
    currentUser: User | null;
    isCreator: boolean;
    commentsDisabled?: boolean;
    onCommentsUpdated: (comments: Comment[]) => void;
    onError: (msg: string) => void;
    onSuccess: (msg: string) => void;
    onReport: (commentId: string) => void;
    innerRef?: React.RefObject<HTMLDivElement | null>;
}

export const CommentSection: React.FC<CommentSectionProps> = React.memo(({
                                                                             projectId, comments, currentUser, isCreator, commentsDisabled,
                                                                             onCommentsUpdated, onError, onSuccess, onReport, innerRef
                                                                         }) => {
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
            projectClient.getUsersBatch(Array.from(userIds)).then(data => {
                const profiles: Record<string, {username: string, avatarUrl: string}> = {};
                data.forEach((u: User) => {
                    profiles[u.id] = { username: u.username, avatarUrl: u.avatarUrl };
                });
                setUserProfiles(prev => ({...prev, ...profiles}));
            }).catch(() => {});
        }
    }, [comments, userProfiles]);

    const refreshComments = async () => {
        const updated = await projectClient.getComments(projectId);
        onCommentsUpdated(updated);
    };

    const handleVote = async (commentId: string, isReply: boolean, upvote: boolean) => {
        if (!currentUser) {
            onError("Log in to vote on comments.");
            return;
        }
        try {
            await projectClient.voteComment(projectId, commentId, upvote, isReply);
            await refreshComments();
        } catch {
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
        if (innerRef?.current) {
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
                await projectClient.updateComment(projectId, editingCommentId, text);
                onSuccess('Comment updated!');
            } else {
                await projectClient.postComment(projectId, text);
                onSuccess('Comment posted!');
            }
            await refreshComments();
            setEditingCommentId(null);
            setText('');
        } catch (err: any) {
            onError(err.response?.data || 'Failed to post comment.');
        } finally {
            setSubmitting(false);
        }
    };

    const deleteComment = async (commentId: string) => {
        if(!window.confirm("Are you sure you want to delete this comment?")) return;
        setSubmitting(true);
        try {
            await projectClient.deleteComment(projectId, commentId);
            await refreshComments();
            onSuccess('Comment deleted.');
        } catch (err: any) {
            onError(err.response?.data || 'Failed to delete.');
        } finally {
            setSubmitting(false);
        }
    };

    const submitReply = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!replyingCommentId) return;
        setSubmitting(true);
        try {
            await projectClient.replyToComment(projectId, replyingCommentId, replyText);
            await refreshComments();
            setReplyingCommentId(null);
            setReplyText('');
            onSuccess('Reply posted!');
        } catch (err: any) {
            onError(err.response?.data || 'Failed to post reply.');
        } finally {
            setSubmitting(false);
        }
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

                    const profileLink = SiteRoutes.creator(authorUsername);
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
                                        <button aria-label="Reply to comment" type="button" onClick={() => { setReplyingCommentId(comment.id); setReplyText(''); }} className="text-xs font-bold text-slate-500 hover:text-slate-900 dark:hover:text-white flex items-center gap-1.5 transition-colors">
                                            <MessageSquare className="w-4 h-4" aria-hidden="true"/> Reply
                                        </button>
                                    )}
                                    {isCreator && comment.developerReply && (
                                        <button aria-label="Edit reply" type="button" onClick={() => { setReplyingCommentId(comment.id); setReplyText(comment.developerReply?.content || ''); }} className="text-xs font-bold text-slate-500 hover:text-slate-900 dark:hover:text-white flex items-center gap-1.5 transition-colors">
                                            <MessageSquare className="w-4 h-4" aria-hidden="true"/> Edit Reply
                                        </button>
                                    )}
                                    {currentUser && !isCommentOwner && (
                                        <button aria-label="Report comment" type="button" onClick={() => onReport(comment.id)} className="text-xs font-bold text-slate-500 hover:text-red-500 flex items-center gap-1.5 transition-colors">
                                            <Flag className="w-4 h-4" aria-hidden="true"/> Report
                                        </button>
                                    )}
                                    {isCommentOwner && (
                                        <button aria-label="Edit comment" type="button" onClick={() => startEditing(comment)} className="text-xs font-bold text-slate-500 hover:text-modtale-accent flex items-center gap-1.5 transition-colors">
                                            <Edit className="w-4 h-4" aria-hidden="true"/> Edit
                                        </button>
                                    )}
                                    {(isCreator || isCommentOwner) && (
                                        <button aria-label="Delete comment" type="button" onClick={() => deleteComment(comment.id)} className="text-xs font-bold text-slate-500 hover:text-red-500 flex items-center gap-1.5 transition-colors">
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

                                    const replyProfileLink = SiteRoutes.creator(replyUsername);

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
                                                        <button aria-label="Report comment" type="button" onClick={() => onReport(comment.id)} className="text-xs font-bold text-slate-500 hover:text-red-500 flex items-center gap-1.5 transition-colors">
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