import React, { useEffect, useState, useRef, useMemo } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/cjs/styles/prism';
import { Helmet } from 'react-helmet-async';
import type { Mod, User, ProjectVersion, Comment, ModDependency } from '../../types';
import {
    MessageSquare, Send, Copy, X, Check,
    Tag, Scale, Link as LinkIcon, Box, Gamepad2, Heart, Share2, Edit, ChevronLeft, ChevronRight,
    Download, Image, List, Globe, Bug, BookOpen, Github, ExternalLink, Calendar, ChevronDown, Hash,
    Code, Paintbrush, Database, Layers, Layout, Flag, CornerDownRight, Crown, Trash, Users
} from 'lucide-react';
import { StatusModal } from '../../components/ui/StatusModal';
import { ShareModal } from '@/components/resources/mod-detail/ShareModal';
import { api, API_BASE_URL, BACKEND_URL } from '../../utils/api';
import { extractId, createSlug, getProjectUrl } from '../../utils/slug';
import { useSSRData } from '../../context/SSRContext';
import NotFound from '../../components/ui/error/NotFound';
import { Spinner } from '../../components/ui/Spinner';
import { compareSemVer, formatTimeAgo } from '../../utils/modHelpers';
import { DependencyModal, DownloadModal, HistoryModal } from '@/components/resources/mod-detail/DownloadDialogs';
import { ProjectLayout, SidebarSection } from '@/components/resources/ProjectLayout.tsx';
import { generateProjectMeta } from '../../utils/meta';
import { getBreadcrumbsForClassification, generateBreadcrumbSchema } from '../../utils/schema';
import { ReportModal } from '@/components/resources/mod-detail/ReportModal';
import { useMobile } from '../../context/MobileContext';

const DiscordIcon = ({ className }: { className?: string }) => (
    <svg className={className} fill="currentColor" viewBox="0 0 127.14 96.36">
        <path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c2.36-24.44-4.2-48.62-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z" />    </svg>
);

const getLicenseInfo = (license: string) => {
    const l = license.toUpperCase().replace(/[^A-Z0-9]/g, '');
    if (l.includes('MIT')) return { name: 'MIT', url: 'https://opensource.org/licenses/MIT' };
    if (l.includes('APACHE')) return { name: 'Apache 2.0', url: 'https://opensource.org/licenses/Apache-2.0' };
    if (l.includes('LGPL')) return { name: 'LGPL v3', url: 'https://www.gnu.org/licenses/lgpl-3.0.en.html' };
    if (l.includes('AGPL')) return { name: 'AGPL v3', url: 'https://www.gnu.org/licenses/agpl-3.0.en.html' };
    if (l.includes('GPL')) return { name: 'GPL v3', url: 'https://www.gnu.org/licenses/gpl-3.0.en.html' };
    if (l.includes('MPL')) return { name: 'MPL 2.0', url: 'https://opensource.org/licenses/MPL-2.0' };
    if (l.includes('BSD')) return { name: 'BSD 3-Clause', url: 'https://opensource.org/licenses/BSD-3-Clause' };
    if (l.includes('CC0')) return { name: 'CC0', url: 'https://creativecommons.org/publicdomain/zero/1.0/' };
    if (l.includes('CCBYNCND')) return { name: 'CC BY-NC-ND 4.0', url: 'https://creativecommons.org/licenses/by-nc-nd/4.0/' };
    if (l.includes('CCBYNCSA')) return { name: 'CC BY-NC-SA 4.0', url: 'https://creativecommons.org/licenses/by-nc-sa/4.0/' };
    if (l.includes('CCBYNC')) return { name: 'CC BY-NC 4.0', url: 'https://creativecommons.org/licenses/by-nc/4.0/' };
    if (l.includes('CCBYSA')) return { name: 'CC BY-SA 4.0', url: 'https://creativecommons.org/licenses/by-sa/4.0/' };
    if (l.includes('CCBY')) return { name: 'CC BY 4.0', url: 'https://creativecommons.org/licenses/by/4.0/' };
    if (l.includes('UNLICENSE')) return { name: 'The Unlicense', url: 'https://unlicense.org/' };
    if (l.includes('ARR') || l.includes('ALLRIGHTS')) return { name: 'All Rights Reserved', url: null };
    return { name: license, url: null };
};

const getClassificationIcon = (cls: string) => {
    switch (cls) {
        case 'PLUGIN': return <Code className="w-3.5 h-3.5" />;
        case 'ART': return <Paintbrush className="w-3.5 h-3.5" />;
        case 'DATA': return <Database className="w-3.5 h-3.5" />;
        case 'SAVE': return <Globe className="w-3.5 h-3.5" />;
        case 'MODPACK': return <Layers className="w-3.5 h-3.5" />;
        default: return <Layout className="w-3.5 h-3.5" />;
    }
};

const toTitleCase = (str: string) => {
    if (!str) return '';
    if (str === 'SAVE') return 'World';
    return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
};

const ProjectSidebar: React.FC<{
    mod: Mod;
    dependencies?: ModDependency[];
    depMeta: Record<string, { icon: string, title: string }>;
    sourceUrl?: string;
    navigate: (path: string) => void;
    contributors: User[];
    orgMembers: User[];
    author: User | null;
}> = ({ mod, dependencies, depMeta, navigate, contributors, orgMembers, author }) => {
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

    const handleCopyId = () => {
        navigator.clipboard.writeText(mod.id);
        setCopiedId(true);
        setTimeout(() => setCopiedId(false), 2000);
    };

    const isModpack = mod.classification === 'MODPACK';
    const getIconUrl = (path?: string) => path ? (path.startsWith('http') ? path : `${BACKEND_URL}${path}`) : null;

    return (
        <div className="flex flex-col gap-8">
            <div className="grid grid-cols-2 gap-2 py-2">
                <div className="flex flex-col items-center justify-start">
                    <div className="text-3xl font-black text-slate-900 dark:text-white tracking-tighter leading-none mb-1">{mod.favoriteCount.toLocaleString()}</div>
                    <div className="flex items-center justify-center mt-1 h-5 w-full gap-1.5 text-slate-400">
                        <Heart className="w-3.5 h-3.5" />
                        <span className="text-[10px] font-bold uppercase tracking-widest pt-0.5">Favorites</span>
                    </div>
                </div>

                <div className="flex flex-col items-center justify-start border-l border-slate-200 dark:border-white/5">
                    <div className="text-3xl font-black text-slate-900 dark:text-white tracking-tighter leading-none mb-1">{mod.downloadCount.toLocaleString()}</div>
                    <div className="flex items-center gap-1.5 mt-1 h-5">
                        <Download className="w-3.5 h-3.5 text-slate-400" />
                        <span className="text-[10px] font-bold text-slate-400 uppercase tracking-widest pt-0.5">Downloads</span>
                    </div>
                </div>
            </div>

            {gameVersions.length > 0 && (
                <SidebarSection title="Supported Versions" icon={Gamepad2}>
                    <div className="flex flex-wrap gap-2">
                        {gameVersions.map(v => (
                            <span key={v} className="px-2.5 py-1 bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 rounded-md text-[10px] font-bold text-slate-600 dark:text-slate-300 uppercase tracking-wide">
                                {v}
                            </span>
                        ))}
                    </div>
                </SidebarSection>
            )}

            <SidebarSection title="Tags" icon={Tag}>
                <div className="flex flex-wrap gap-2">
                    {mod.tags?.map((tag) => (
                        <span key={tag} className="px-3 py-1.5 bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/10 rounded-lg text-xs font-bold text-slate-600 dark:text-slate-300 hover:text-modtale-accent hover:border-modtale-accent transition-all cursor-default">
                            {tag}
                        </span>
                    ))}
                    {(!mod.tags || mod.tags.length === 0) && <span className="text-xs text-slate-500 italic">No tags.</span>}
                </div>
            </SidebarSection>

            {author && author.accountType === 'ORGANIZATION' && orgMembers.length > 0 && (
                <SidebarSection title="Team Members" icon={Users}>
                    <div className="flex flex-col gap-2">
                        {orgMembers.map(member => (
                            <a
                                key={member.id}
                                href={`/creator/${member.username}`}
                                className="flex items-center gap-3 p-2 rounded-xl hover:bg-slate-100 dark:hover:bg-white/5 transition-colors group"
                            >
                                <div className="w-8 h-8 rounded-lg overflow-hidden bg-slate-100 border border-slate-200 dark:border-white/10">
                                    <img src={member.avatarUrl} alt={member.username} className="w-full h-full object-cover" />
                                </div>
                                <div>
                                    <div className="text-xs font-bold text-slate-700 dark:text-slate-300 group-hover:text-modtale-accent">{member.username}</div>
                                    <div className="text-[10px] text-slate-500 uppercase font-bold tracking-wider">
                                        {author.organizationMembers?.find(m => m.userId === member.id)?.role || 'Member'}
                                    </div>
                                </div>
                            </a>
                        ))}
                    </div>
                </SidebarSection>
            )}

            {contributors.length > 0 && (
                <SidebarSection title="Contributors" icon={Users}>
                    <div className="flex flex-col gap-2">
                        {contributors.map(contributor => (
                            <a
                                key={contributor.id}
                                href={`/creator/${contributor.username}`}
                                className="flex items-center gap-3 p-2 rounded-xl hover:bg-slate-100 dark:hover:bg-white/5 transition-colors group"
                            >
                                <div className="w-8 h-8 rounded-lg overflow-hidden bg-slate-100 border border-slate-200 dark:border-white/10">
                                    <img src={contributor.avatarUrl} alt={contributor.username} className="w-full h-full object-cover" />
                                </div>
                                <div className="text-xs font-bold text-slate-700 dark:text-slate-300 group-hover:text-modtale-accent">{contributor.username}</div>
                            </a>
                        ))}
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
                                    <div className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-black/20 flex items-center justify-center text-slate-400 group-hover:text-modtale-accent transition-colors overflow-hidden">
                                        {iconUrl ? <img src={iconUrl} alt="" className="w-full h-full object-cover" /> : <Box className="w-4 h-4" />}
                                    </div>
                                    <div className="min-w-0">
                                        <div className="text-xs font-bold text-slate-700 dark:text-slate-300 group-hover:text-modtale-accent truncate">{title}</div>
                                        <div className="text-[10px] text-slate-500 flex items-center gap-2">
                                            {!isModpack && <span className={dep.isOptional ? '' : 'text-amber-500 font-bold'}>{dep.isOptional ? 'Optional' : 'Required'}</span>}
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
                            <a href={licenseInfo.url} target="_blank" rel="noopener noreferrer" className="block text-center p-2 rounded-lg bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/5 text-slate-600 dark:text-slate-300 hover:text-modtale-accent hover:border-modtale-accent transition-all truncate">
                                {licenseInfo.name}
                            </a>
                        ) : (
                            <div className="text-center p-2 rounded-lg bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/5 text-slate-600 dark:text-slate-300 truncate">
                                {licenseInfo.name}
                            </div>
                        )}
                    </div>
                </SidebarSection>
            )}

            <SidebarSection title="Project ID" icon={Hash}>
                <div className="flex items-center justify-between group bg-white dark:bg-slate-900/50 p-3 rounded-xl border border-slate-200 dark:border-white/5">
                    <code className="text-xs font-mono text-slate-600 dark:text-slate-300">{mod.id}</code>
                    <button onClick={handleCopyId} className="text-slate-400 hover:text-modtale-accent transition-colors">
                        {copiedId ? <Check className="w-4 h-4 text-green-500" /> : <Copy className="w-4 h-4" />}
                    </button>
                </div>
            </SidebarSection>
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
}

const CommentSection: React.FC<CommentSectionProps> = ({ modId, comments, currentUser, isCreator, onCommentSubmitted, onError, onSuccess, innerRef, commentsDisabled }) => {
    const [text, setText] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [editingCommentId, setEditingCommentId] = useState<string | null>(null);
    const [replyingCommentId, setReplyingCommentId] = useState<string | null>(null);
    const [replyText, setReplyText] = useState('');

    const startEditing = (comment: Comment) => {
        setText(comment.content);
        setEditingCommentId(comment.id);
        if(innerRef?.current) innerRef.current.scrollIntoView({behavior:'smooth'});
    };

    const cancelEdit = () => {
        setEditingCommentId(null);
        setText('');
    };

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

    return (
        <div ref={innerRef} className="mt-12 border-t border-slate-200 dark:border-white/5 pt-10">
            <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-8 flex items-center gap-3">
                <MessageSquare className="w-6 h-6 text-modtale-accent" /> Comments <span className="text-sm font-medium text-slate-400">({comments.length})</span>
            </h2>

            {commentsDisabled && (
                <div className="mb-6 p-4 bg-orange-500/10 border border-orange-500/20 text-orange-500 rounded-xl flex items-center gap-2 text-sm font-bold">
                    <Flag className="w-4 h-4"/> Comments are currently disabled. Only you can see them.
                </div>
            )}

            {currentUser ? (
                <form onSubmit={submit} className="mb-10 p-6 bg-slate-50 dark:bg-slate-950/30 rounded-2xl border border-slate-200 dark:border-white/5">
                    <div className="flex justify-between mb-4">
                        <h3 className="font-bold text-slate-900 dark:text-white">{editingCommentId ? 'Edit your comment' : 'Leave a comment'}</h3>
                        {editingCommentId && <button type="button" onClick={cancelEdit} className="text-xs text-slate-500 hover:text-red-500 font-bold">Cancel</button>}
                    </div>
                    <textarea value={text} onChange={e => setText(e.target.value)} className="w-full p-4 rounded-xl bg-white dark:bg-black/40 border border-slate-200 dark:border-white/10 text-slate-900 dark:text-white mb-4 focus:ring-2 focus:ring-modtale-accent outline-none font-medium text-sm min-h-[100px]" placeholder="Ask a question or share your thoughts..." required />
                    <button type="submit" disabled={submitting} className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 py-3 rounded-xl font-bold flex items-center gap-2 disabled:opacity-50">
                        <Send className="w-4 h-4" /> {editingCommentId ? 'Update Comment' : 'Post Comment'}
                    </button>
                </form>
            ) : <div className="mb-10 p-8 bg-slate-50 dark:bg-slate-950/30 rounded-2xl text-center text-slate-500 font-bold border border-slate-200 dark:border-white/5">Log in to post a comment.</div>}

            <div className="space-y-4">
                {comments?.length > 0 ? comments.map((comment) => (
                    <div key={comment.id} className="p-6 bg-white dark:bg-slate-950/20 rounded-2xl border border-slate-200 dark:border-white/5 group">
                        <div className="flex justify-between items-start mb-3">
                            <div className="flex items-center gap-4">
                                <div className="w-10 h-10 rounded-full bg-slate-200 dark:bg-white/5 text-slate-500 flex items-center justify-center font-black overflow-hidden shrink-0">
                                    {comment.userAvatarUrl ? (
                                        <img src={comment.userAvatarUrl} alt={comment.user} className="w-full h-full object-cover" />
                                    ) : (
                                        comment.user.charAt(0)
                                    )}
                                </div>
                                <div><span className="font-bold text-slate-900 dark:text-white block">{comment.user}</span></div>
                            </div>
                            <div className="flex flex-col items-end gap-1">
                                <div className="text-xs font-bold text-slate-400 uppercase tracking-wider">
                                    {new Date(comment.date).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })}
                                </div>
                                <div className="flex items-center gap-3 opacity-0 group-hover:opacity-100 transition-opacity">
                                    {(currentUser && currentUser.username === comment.user) && (
                                        <button onClick={() => startEditing(comment)} className="text-xs text-slate-500 hover:text-modtale-accent font-bold flex items-center gap-1">
                                            <Edit className="w-3 h-3"/> Edit
                                        </button>
                                    )}
                                    {(isCreator || (currentUser && currentUser.username === comment.user)) && (
                                        <button onClick={() => deleteComment(comment.id)} className="text-xs text-slate-500 hover:text-red-500 font-bold flex items-center gap-1">
                                            <Trash className="w-3 h-3"/> Delete
                                        </button>
                                    )}
                                    {isCreator && (
                                        <button onClick={() => { setReplyingCommentId(comment.id); setReplyText(comment.developerReply?.content || ''); }} className="text-xs text-modtale-accent font-bold hover:underline">
                                            {comment.developerReply ? 'Edit Reply' : 'Reply'}
                                        </button>
                                    )}
                                </div>
                            </div>
                        </div>
                        <p className="text-slate-700 dark:text-slate-300 pl-14 whitespace-pre-wrap">{comment.content}</p>

                        {replyingCommentId === comment.id ? (
                            <form onSubmit={submitReply} className="mt-4 ml-14 bg-slate-50 dark:bg-slate-900/50 p-4 rounded-xl border border-slate-200 dark:border-white/10">
                                <textarea
                                    value={replyText}
                                    onChange={e => setReplyText(e.target.value)}
                                    className="w-full bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-lg p-2 text-sm mb-2 focus:outline-none focus:border-modtale-accent"
                                    placeholder="Write a reply..."
                                    rows={3}
                                />
                                <div className="flex justify-end gap-2">
                                    <button type="button" onClick={() => setReplyingCommentId(null)} className="text-xs font-bold px-3 py-1.5 text-slate-500 hover:text-slate-900 dark:hover:text-white">Cancel</button>
                                    <button type="submit" disabled={submitting} className="text-xs font-bold bg-modtale-accent text-white px-3 py-1.5 rounded-lg flex items-center gap-1">
                                        <CornerDownRight className="w-3 h-3"/> Post Reply
                                    </button>
                                </div>
                            </form>
                        ) : comment.developerReply && (
                            <div className="mt-6 ml-14 relative">
                                <div className="absolute -left-4 top-0 bottom-0 w-1 bg-gradient-to-b from-modtale-accent to-transparent rounded-full opacity-50"></div>
                                <div className="bg-slate-50 dark:bg-white/5 p-4 rounded-xl border border-slate-200 dark:border-white/5">
                                    <div className="flex items-center justify-between mb-2">
                                        <div className="flex items-center gap-3">
                                            <div className="w-6 h-6 rounded-full bg-modtale-accent text-white flex items-center justify-center text-xs font-bold overflow-hidden">
                                                {comment.developerReply.userAvatarUrl ? (
                                                    <img src={comment.developerReply.userAvatarUrl} alt="" className="w-full h-full object-cover"/>
                                                ) : (
                                                    <Crown className="w-3 h-3" />
                                                )}
                                            </div>
                                            <div className="flex flex-col">
                                                <span className="text-xs font-bold text-slate-900 dark:text-white flex items-center gap-1">
                                                    {comment.developerReply.user} <Crown className="w-3 h-3 text-modtale-accent" />
                                                </span>
                                                <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">
                                                    Developer Response • {new Date(comment.developerReply.date).toLocaleDateString()}
                                                </span>
                                            </div>
                                        </div>
                                    </div>
                                    <p className="text-sm text-slate-600 dark:text-slate-300 leading-relaxed">{comment.developerReply.content}</p>
                                </div>
                            </div>
                        )}
                    </div>
                )) : <div className="text-center py-12 text-slate-500 italic">No comments yet.</div>}
            </div>
        </div>
    );
};

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
    const realId = extractId(id);
    const { isMobile } = useMobile();
    const { initialData } = useSSRData();
    const initialMod = (initialData && extractId(initialData.id) === realId) ? initialData : null;

    const [mod, setMod] = useState<Mod | null>(initialMod);
    const [loading, setLoading] = useState(!initialMod);
    const [isNotFound, setIsNotFound] = useState(false);

    const [showDownloadModal, setShowDownloadModal] = useState(false);
    const [showAllVersionsModal, setShowAllVersionsModal] = useState(false);
    const [statusModal, setStatusModal] = useState<any>(null);
    const [isShareOpen, setIsShareOpen] = useState(false);
    const [galleryIndex, setGalleryIndex] = useState<number | null>(null);
    const [pendingDownloadVer, setPendingDownloadVer] = useState<{url: string, ver: string, deps: any[]} | null>(null);

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
    const [showReportModal, setShowReportModal] = useState(false);

    const commentsRef = useRef<HTMLDivElement>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);
    const depsDropdownRef = useRef<HTMLDivElement>(null);
    const currentUrl = typeof window !== 'undefined' ? window.location.href : `https://modtale.net${location.pathname}`;

    const canEdit = mod?.canEdit ?? (currentUser && mod && (currentUser.username === mod.author || mod.contributors?.includes(currentUser.username)));

    const analyticsFired = useRef(false);
    const fetchedDepMeta = useRef<Set<string>>(new Set());

    const projectMeta = useMemo(() => mod ? generateProjectMeta(mod) : null, [mod]);
    const breadcrumbSchema = useMemo(() => mod ? generateBreadcrumbSchema([...getBreadcrumbsForClassification(mod.classification || 'PLUGIN'), { name: mod.title, url: getProjectUrl(mod) }]) : null, [mod]);
    const canonicalUrl = useMemo(() => mod ? `https://modtale.net${getProjectUrl(mod)}` : null, [mod]);

    const ogImageUrl = useMemo(() => mod ? `${API_BASE_URL}/og/project/${mod.id}.png` : '', [mod]);

    useEffect(() => {
        if (mod && mod.id && !analyticsFired.current) {
            analyticsFired.current = true;
            api.post(`/analytics/view/${mod.id}`).catch(() => {});
        }
    }, [mod?.id]);

    useEffect(() => {
        if (mod && extractId(mod.id) === realId) {
            setLoading(false);
            if(currentUser?.followingIds) setIsFollowing(currentUser.followingIds.includes(mod.author));
            return;
        }

        if (realId) {
            setLoading(true);
            api.get(`/projects/${realId}`).then(res => {
                setMod(res.data);
                if (currentUser?.followingIds?.includes(res.data.author)) setIsFollowing(true);

                const vers = res.data.versions || [];
                if (vers.length > 0 && !vers.some((v: any) => !v.channel || v.channel === 'RELEASE')) {
                    setShowExperimental(true);
                }

            }).catch(() => setIsNotFound(true)).finally(() => setLoading(false));
        }
    }, [realId, currentUser]);

    useEffect(() => {
        const fetchTeam = async () => {
            if (!mod) return;

            try {
                const authorRes = await api.get(`/user/profile/${mod.author}`);
                const author = authorRes.data;
                setAuthorProfile(author);

                if (author.accountType === 'ORGANIZATION') {
                    const membersRes = await api.get(`/orgs/${mod.author}/members`);
                    setOrgMembers(membersRes.data);
                }
            } catch (e) {
                console.error("Failed to fetch author profile/members", e);
            }

            if (mod.contributors && mod.contributors.length > 0) {
                try {
                    const res = await api.post('/users/batch', { usernames: mod.contributors });
                    setContributors(res.data);
                } catch (e) {
                    console.error("Failed to fetch contributors", e);
                }
            }
        };

        if (mod) fetchTeam();
    }, [mod?.id, mod?.author]);

    useEffect(() => {
        if (mod && !loading) {
            const canonicalPath = getProjectUrl(mod);
            const currentPath = location.pathname;

            if (currentPath.replace(/\/$/, "") !== canonicalPath.replace(/\/$/, "")) {
                console.log(`[SEO] CSR Redirecting: ${currentPath} -> ${canonicalPath}`);
                navigate(canonicalPath, { replace: true });
            }
        }
    }, [mod, loading, location, navigate]);

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

    const allVersions = useMemo(() => mod?.versions || [], [mod]);

    const sortedHistory = useMemo(() => {
        const displayedVersions = allVersions.filter(v => showExperimental ? true : (!v.channel || v.channel === 'RELEASE'));
        return [...displayedVersions].sort((a, b) => compareSemVer(b.versionNumber, a.versionNumber));
    }, [allVersions, showExperimental]);

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

    const initiateDownload = (url: string, ver?: string, deps?: any[]) => {
        if (mod?.classification !== 'MODPACK' && deps && deps.length > 0) {
            setPendingDownloadVer({ url, ver: ver || '', deps });
            setShowDownloadModal(false);
        } else {
            executeDownload(url, ver);
        }
    };

    const executeDownload = async (fileUrl: string, ver?: string) => {
        try {
            if (!ver) {
                const targetUrl = `${API_BASE_URL}/files/download/${encodeURI(fileUrl)}`;
                window.open(targetUrl, '_blank');
                if(mod && !downloadedSessionIds.has(mod.id)) {
                    setMod(prev => prev ? { ...prev, downloadCount: prev.downloadCount + 1 } : null);
                    onDownload(mod.id);
                }
                setPendingDownloadVer(null); setShowDownloadModal(false); setShowAllVersionsModal(false);
                setStatusModal({ type: 'success', title: 'Download Started', msg: 'Your download should begin shortly.' });
                return;
            }

            const response = await api.get(`/projects/${mod?.id}/versions/${ver}/download-url`);
            const { downloadUrl } = response.data;

            window.open(`${API_BASE_URL}${downloadUrl}`, '_blank');
            if(mod && !downloadedSessionIds.has(mod.id)) {
                setMod(prev => prev ? { ...prev, downloadCount: prev.downloadCount + 1 } : null);
                onDownload(mod.id);
            }
            setPendingDownloadVer(null); setShowDownloadModal(false); setShowAllVersionsModal(false);
            setStatusModal({ type: 'success', title: 'Download Started', msg: 'Your download should begin shortly.' });
        } catch (error) {
            console.error('Failed to initiate download:', error);
            setStatusModal({ type: 'error', title: 'Download Failed', msg: 'Unable to generate download link. Please try again.' });
        }
    };

    const memoizedDescription = useMemo(() => {
        if (!mod?.about) return <p className="text-slate-500 italic">No description.</p>;

        return (
            <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                rehypePlugins={[rehypeRaw, [rehypeSanitize, {
                    ...defaultSchema,
                    attributes: {
                        ...defaultSchema.attributes,
                        code: ['className']
                    }
                }]]}
                components={{
                    code({node, inline, className, children, ...props}: any) {
                        const match = /language-(\w+)/.exec(className || '')
                        return !inline && match ? (
                            <SyntaxHighlighter
                                {...props}
                                style={vscDarkPlus}
                                language={match[1]}
                                PreTag="div"
                                className="rounded-lg text-sm"
                            >
                                {String(children).replace(/\n$/, '')}
                            </SyntaxHighlighter>
                        ) : (
                            <code className={`${className || ''} bg-slate-100 dark:bg-white/10 px-1 py-0.5 rounded text-sm`} {...props}>
                                {children}
                            </code>
                        )
                    },
                    p({node, children, ...props}: any) {
                        return <p className="my-2 [li>&]:my-0" {...props}>{children}</p>
                    },
                    li({node, children, ...props}: any) {
                        return <li className="my-1 [&>p]:my-0" {...props}>{children}</li>
                    },
                    ul({node, children, ...props}: any) {
                        return <ul className="list-disc pl-6 my-3" {...props}>{children}</ul>
                    },
                    ol({node, children, ...props}: any) {
                        return <ol className="list-decimal pl-6 my-3" {...props}>{children}</ol>
                    }
                }}
            >
                {mod.about}
            </ReactMarkdown>
        );
    }, [mod?.about]);

    if (isNotFound) return <NotFound />;
    if (loading) return <div className="min-h-screen bg-slate-950 flex items-center justify-center"><Spinner fullScreen={false} className="w-8 h-8" /></div>;
    if (!mod) return null;

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
        if (type === 'WEBSITE') return 'text-blue-400 hover:bg-blue-500/20 border-blue-500/20';
        if (type === 'ISSUE') return 'text-red-400 hover:bg-red-500/20 border-red-500/20';
        if (type === 'SOURCE') return 'text-slate-300 hover:bg-slate-200 dark:hover:bg-white/10 border-slate-200 dark:border-white/10';
        return 'text-amber-500 hover:bg-amber-500/10 border-amber-500/20';
    };

    const displayClassification = toTitleCase(mod.classification || 'PLUGIN');

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
                </Helmet>
            )}

            {statusModal && <StatusModal type={statusModal.type} title={statusModal.title} message={statusModal.msg} onClose={() => setStatusModal(null)} />}
            <ShareModal isOpen={isShareOpen} onClose={() => setIsShareOpen(false)} url={currentUrl} title={mod.title} author={mod.author} />

            <ReportModal
                isOpen={showReportModal}
                onClose={() => setShowReportModal(false)}
                projectId={mod.id}
                projectTitle={mod.title}
            />

            {pendingDownloadVer && (
                <DependencyModal
                    dependencies={pendingDownloadVer.deps}
                    onClose={() => setPendingDownloadVer(null)}
                    onConfirm={() => executeDownload(pendingDownloadVer.url, pendingDownloadVer.ver)}
                />
            )}

            <DownloadModal
                show={showDownloadModal}
                onClose={() => setShowDownloadModal(false)}
                versionsByGame={latestForGame}
                onDownload={initiateDownload}
                showExperimental={showExperimental}
                onToggleExperimental={() => setShowExperimental(!showExperimental)}
                onViewHistory={() => { setShowDownloadModal(false); setShowAllVersionsModal(true); }}
            />

            <HistoryModal
                show={showAllVersionsModal}
                onClose={() => setShowAllVersionsModal(false)}
                history={sortedHistory}
                showExperimental={showExperimental}
                onToggleExperimental={() => setShowExperimental(!showExperimental)}
                onDownload={initiateDownload}
            />

            {galleryIndex !== null && mod.galleryImages && (
                <div className="fixed inset-0 z-[300] bg-black/80 backdrop-blur-md flex items-center justify-center p-4 animate-in fade-in duration-200" onClick={() => setGalleryIndex(null)}>
                    <div className="relative w-full max-w-6xl max-h-[85dvh] bg-slate-900 rounded-2xl shadow-2xl flex flex-col overflow-hidden border border-white/10" onClick={e => e.stopPropagation()}>

                        <div className="p-4 flex justify-between items-center bg-black/20 border-b border-white/10 z-10 shrink-0">
                            <span className="text-sm font-bold text-white/70">Image {galleryIndex + 1} of {mod.galleryImages.length}</span>
                            <button onClick={() => setGalleryIndex(null)} className="p-2 bg-white/5 hover:bg-white/10 text-white rounded-full transition-colors"><X className="w-5 h-5" /></button>
                        </div>

                        <div className="flex-1 relative flex items-center justify-center bg-black/40 overflow-hidden group">
                            {mod.galleryImages.length > 1 && (
                                <>
                                    <button
                                        onClick={(e) => {e.stopPropagation(); setGalleryIndex((galleryIndex - 1 + mod.galleryImages!.length) % mod.galleryImages!.length)}}
                                        className="absolute left-4 p-3 bg-black/50 hover:bg-modtale-accent text-white rounded-full transition-all z-20 opacity-0 group-hover:opacity-100 -translate-x-4 group-hover:translate-x-0"
                                    >
                                        <ChevronLeft className="w-6 h-6" />
                                    </button>
                                    <button
                                        onClick={(e) => {e.stopPropagation(); setGalleryIndex((galleryIndex + 1) % mod.galleryImages!.length)}}
                                        className="absolute right-4 p-3 bg-black/50 hover:bg-modtale-accent text-white rounded-full transition-all z-20 opacity-0 group-hover:opacity-100 translate-x-4 group-hover:translate-x-0"
                                    >
                                        <ChevronRight className="w-6 h-6" />
                                    </button>
                                </>
                            )}
                            <img src={resolveUrl(mod.galleryImages[galleryIndex])} className="max-w-full max-h-full object-contain shadow-lg" alt="" />
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
                        <button disabled={!currentUser} onClick={() => onToggleFavorite(mod.id)} className={`p-3 rounded-xl border transition-all ${isLiked(mod.id) ? 'bg-red-500/10 text-red-500 border-red-500/20' : 'bg-slate-100 dark:bg-white/5 text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white border-slate-200 dark:border-white/5 hover:border-slate-300 dark:hover:border-white/20'}`} title="Favorite">
                            <Heart className={`w-5 h-5 ${isLiked(mod.id) ? 'fill-current' : ''}`} />
                        </button>
                        <button onClick={handleShare} className="p-3 rounded-xl border border-slate-200 dark:border-white/5 bg-slate-100 dark:bg-white/5 text-slate-500 dark:text-slate-400 hover:text-blue-400 hover:border-blue-400/30 transition-all" title="Share">
                            <Share2 className="w-5 h-5" />
                        </button>
                        <button onClick={() => setShowReportModal(true)} className="p-3 rounded-xl border border-slate-200 dark:border-white/5 bg-slate-100 dark:bg-white/5 text-slate-500 dark:text-slate-400 hover:text-red-500 hover:border-red-500/30 transition-all" title="Report Project">
                            <Flag className="w-5 h-5" />
                        </button>
                        {Boolean(canEdit) && (
                            <button onClick={() => navigate(`${getProjectUrl(mod)}/edit`)} className="p-3 rounded-xl border border-slate-200 dark:border-white/5 bg-slate-100 dark:bg-white/5 text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition-all" title="Edit Project">
                                <Edit className="w-5 h-5" />
                            </button>
                        )}
                    </>
                }
                headerContent={
                    <>
                        <div className="flex flex-wrap items-center gap-3 mb-3">
                            <h1 className="text-3xl md:text-5xl font-black text-slate-900 dark:text-white tracking-tighter drop-shadow-sm leading-tight break-words">{mod.title}</h1>
                            <span className="bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/10 px-3 py-1 rounded-full text-xs font-bold text-modtale-accent tracking-widest uppercase flex items-center gap-1.5 shadow-sm whitespace-nowrap">
                                {getClassificationIcon(mod.classification || 'PLUGIN')}{displayClassification}
                            </span>
                        </div>

                        <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm font-medium text-slate-500 dark:text-slate-400 mb-4">
                            <div className="flex items-center gap-2">
                                <span>by <button onClick={() => navigate(`/creator/${mod.author}`)} className="font-bold text-slate-700 dark:text-white hover:text-modtale-accent hover:underline decoration-2 underline-offset-4 transition-all">{mod.author}</button></span>
                                {currentUser && currentUser.username !== mod.author && (
                                    <button
                                        onClick={handleFollowToggle}
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

                        {mod.description && (
                            <p className="text-slate-600 dark:text-slate-300 text-base leading-relaxed max-w-4xl font-medium border-l-2 border-modtale-accent pl-4">
                                {mod.description}
                            </p>
                        )}
                    </>
                }
                actionBar={
                    <div className="flex flex-col xl:flex-row items-start xl:items-center justify-between gap-6 w-full">
                        <div className="flex flex-col md:flex-row items-stretch md:items-center gap-4 w-full xl:w-auto">
                            <button
                                onClick={() => setShowDownloadModal(true)}
                                className="flex-shrink-0 bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 py-3.5 rounded-xl font-black flex items-center justify-center gap-2 shadow-lg shadow-modtale-accent/20 transition-all active:scale-95 group"
                            >
                                <Download className="w-5 h-5 group-hover:animate-bounce" />
                                Download
                            </button>

                            <div className="hidden md:block w-px h-10 bg-slate-200 dark:bg-white/10 mx-2"></div>

                            <div className="grid grid-cols-2 md:flex md:flex-row gap-2 w-full md:w-auto">
                                {mod.galleryImages && mod.galleryImages.length > 0 && (
                                    <button onClick={() => {if(mod.galleryImages?.length) setGalleryIndex(0)}} className="col-span-2 md:col-span-1 flex items-center justify-center gap-2 px-5 py-3 md:py-2.5 text-sm font-bold bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 rounded-xl text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200 dark:hover:bg-white/10 transition-colors whitespace-nowrap"><Image className="w-4 h-4" /> Gallery</button>
                                )}
                                <button onClick={() => setShowAllVersionsModal(true)} className="flex items-center justify-center gap-2 px-5 py-3 md:py-2.5 text-sm font-bold bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 rounded-xl text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200 dark:hover:bg-white/10 transition-colors whitespace-nowrap"><List className="w-4 h-4" /> Changelog</button>
                                <button onClick={() => commentsRef.current?.scrollIntoView({ behavior: 'smooth' })} className="flex items-center justify-center gap-2 px-5 py-3 md:py-2.5 text-sm font-bold bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 rounded-xl text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200 dark:hover:bg-white/10 transition-colors whitespace-nowrap"><MessageSquare className="w-4 h-4" /> Comments</button>
                            </div>
                        </div>

                        <div className="w-full xl:w-auto flex flex-col md:flex-row justify-start md:justify-end gap-3">
                            {isMobile && mod.classification === 'MODPACK' && latestDependencies.length > 0 && (
                                <div className="relative w-full" ref={depsDropdownRef}>
                                    <button
                                        onClick={() => { setShowMobileDeps(!showMobileDeps); setShowMobileLinks(false); }}
                                        className="w-full flex items-center justify-center gap-2 p-3 rounded-xl bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/10 font-bold text-slate-600 dark:text-slate-300"
                                    >
                                        <Box className="w-4 h-4" /> Included Mods <ChevronDown className={`w-4 h-4 transition-transform ${showMobileDeps ? 'rotate-180' : ''}`} />
                                    </button>
                                    {showMobileDeps && (
                                        <div className="absolute top-full left-0 right-0 mt-2 bg-slate-900 border border-white/10 rounded-xl shadow-xl z-50 overflow-hidden animate-in fade-in slide-in-from-top-2 p-1 max-h-[300px] overflow-y-auto">
                                            {latestDependencies.map((dep, idx) => {
                                                const meta = depMeta[dep.modId];
                                                const title = meta?.title || dep.modTitle || dep.modId;
                                                const path = `/mod/${createSlug(title, dep.modId)}`;
                                                return (
                                                    <button
                                                        key={idx}
                                                        onClick={() => { navigate(path); setShowMobileDeps(false); }}
                                                        className="w-full flex items-center gap-3 p-3 rounded-lg hover:bg-white/5 transition-colors text-slate-300 hover:text-white text-left"
                                                    >
                                                        <div className="w-8 h-8 rounded-lg bg-slate-950 flex items-center justify-center border border-white/5 shrink-0 overflow-hidden">
                                                            {meta?.icon ? <img src={resolveUrl(meta.icon)} className="w-full h-full object-cover" alt="" /> : <Box className="w-4 h-4 text-slate-600" />}
                                                        </div>
                                                        <div className="min-w-0 flex-1">
                                                            <div className="text-sm font-bold truncate">{title}</div>
                                                            <div className="text-[10px] text-slate-500 font-mono">v{dep.versionNumber}</div>
                                                        </div>
                                                        <ExternalLink className="w-3 h-3 opacity-50 shrink-0" />
                                                    </button>
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
                                    >
                                        <link.icon className="w-5 h-5" />
                                    </a>
                                ))}
                            </div>

                            {links.length > 0 && (
                                <div className="md:hidden relative w-full" ref={dropdownRef}>
                                    <button
                                        onClick={() => { setShowMobileLinks(!showMobileLinks); setShowMobileDeps(false); }}
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
                }
                sidebarContent={
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
                }
                mainContent={
                    <>
                        <div className="prose dark:prose-invert prose-lg max-none">
                            {memoizedDescription}
                        </div>
                        <CommentSection
                            modId={mod.id}
                            comments={mod.comments || []}
                            currentUser={currentUser}
                            isCreator={Boolean(canEdit)}
                            commentsDisabled={mod.allowComments === false}
                            onCommentSubmitted={(c) => { setMod(prev => prev ? {...prev, comments: c} : null); if(onRefresh) onRefresh(); }}
                            onError={(m) => setStatusModal({type:'error', title:'Error', msg:m})}
                            onSuccess={(m) => setStatusModal({type:'success', title:'Success', msg:m})}
                            innerRef={commentsRef}
                        />
                    </>
                }
            />
        </>
    );
};