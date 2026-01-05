import React, { useEffect, useState, useRef, useMemo } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import { Helmet } from 'react-helmet-async';
import type { Mod, User, ProjectVersion, Review, ModDependency } from '../../types';
import {
    MessageSquare, Send, Star, StarHalf, Copy, X, ChevronLeft, ChevronRight,
    Tag, Github, Scale, Link as LinkIcon, Box, Download, Gamepad2
} from 'lucide-react';
import { StatusModal } from '../../components/ui/StatusModal';
import { ShareModal } from '@/components/resources/mod-detail/ShareModal';
import { api, API_BASE_URL, BACKEND_URL } from '../../utils/api';
import { extractId, createSlug } from '../../utils/slug';
import { useSSRData } from '../../context/SSRContext';
import NotFound from '../../components/ui/error/NotFound';
import { ModHeader } from '@/components/resources/mod-detail/ModHeader';
import { Spinner } from '../../components/ui/Spinner';
import { compareSemVer, getClassificationIcon } from '../../utils/modHelpers';
import { DependencyModal, DownloadModal, HistoryModal } from '@/components/resources/mod-detail/DownloadDialogs';
import { ModSidebar } from '@/components/resources/mod-detail/ModSidebar.tsx';
import { generateProjectMeta } from '../../utils/meta'; // Import the new utility

const getLicenseInfo = (license: string) => {
    const l = license.toUpperCase().replace(/\s+/g, '');
    if (l.includes('MIT')) return { name: 'MIT', url: 'https://opensource.org/licenses/MIT' };
    if (l.includes('APACHE')) return { name: 'Apache 2.0', url: 'https://opensource.org/licenses/Apache-2.0' };
    if (l.includes('GPL')) return { name: 'GPL v3', url: 'https://www.gnu.org/licenses/gpl-3.0.en.html' };
    if (l.includes('CC0')) return { name: 'CC0', url: 'https://creativecommons.org/publicdomain/zero/1.0/' };
    if (l.includes('ARR') || l.includes('ALLRIGHTS')) return { name: 'All Rights Reserved', url: null };
    return { name: license, url: null };
};

const StarRating = ({ rating, size = "w-4 h-4" }: { rating: number, size?: string }) => {
    return (
        <div className="flex gap-0.5 text-yellow-500">
            {[1, 2, 3, 4, 5].map((i) => {
                if (rating >= i) return <Star key={i} className={`${size} fill-current`} />;
                if (rating >= i - 0.5) return <StarHalf key={i} className={`${size} fill-current`} />;
                return <Star key={i} className={`${size} text-slate-300 dark:text-slate-700`} />;
            })}
        </div>
    );
};

const ProjectSidebar: React.FC<{
    mod: Mod;
    latestDownloads: number;
    avgRating: string;
    totalReviews: number;
    dependencies?: ModDependency[];
    depMeta: Record<string, { icon: string, title: string }>;
    sourceUrl?: string;
    navigate: (path: string) => void;
}> = ({ mod, latestDownloads, avgRating, totalReviews, dependencies, depMeta, sourceUrl, navigate }) => {
    const licenseInfo = mod.license ? getLicenseInfo(mod.license) : null;
    const isModpack = mod.classification === 'MODPACK';

    const gameVersions = useMemo(() => {
        const set = new Set<string>();
        mod.versions.forEach(v => v.gameVersions?.forEach(gv => set.add(gv)));
        return Array.from(set).sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
    }, [mod.versions]);

    const getIconUrl = (path?: string) => {
        if (!path) return null;
        return path.startsWith('http') ? path : `${BACKEND_URL}${path}`;
    };

    return (
        <div className="flex flex-col gap-4 md:gap-6 md:block md:space-y-6">
            <div className="bg-slate-100 dark:bg-slate-900/50 md:bg-transparent rounded-xl md:rounded-none border md:border-none border-slate-200 dark:border-white/5 p-4 md:p-0 flex flex-row md:flex-col items-center justify-around md:justify-center gap-4">
                <div className="text-center">
                    <div className="text-3xl md:text-5xl font-black text-slate-900 dark:text-white tracking-tighter">{avgRating}</div>
                    <div className="flex justify-center my-1"><StarRating rating={Number(avgRating)} size="w-4 h-4 md:w-6 md:h-6" /></div>
                    <div className="text-[10px] md:text-xs font-bold text-slate-500 uppercase tracking-widest">{totalReviews} Reviews</div>
                </div>
                <div className="w-px h-10 bg-slate-300 dark:bg-white/10 md:hidden"></div>
                <div className="text-center md:mt-2">
                    <div className="md:hidden flex flex-col items-center">
                        <span className="text-xl font-black text-slate-900 dark:text-white">{mod.downloadCount.toLocaleString()}</span>
                        <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest flex items-center gap-1"><Download className="w-3 h-3" /> Downloads</span>
                    </div>
                    <div className="hidden md:block text-xs font-bold text-slate-600 dark:text-slate-400">{mod.downloadCount.toLocaleString()} Total Downloads</div>
                </div>
            </div>

            <div className="h-px bg-slate-200 dark:bg-white/5 w-full hidden md:block"></div>

            <div className="hidden md:block space-y-6">
                {gameVersions.length > 0 && (
                    <ModSidebar title="Supported Versions" icon={Gamepad2}>
                        <div className="flex flex-wrap gap-2">
                            {gameVersions.map(v => (
                                <span key={v} className="px-2.5 py-1 bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 rounded-md text-[10px] font-bold text-slate-600 dark:text-slate-300 uppercase tracking-wide">
                                    {v}
                                </span>
                            ))}
                        </div>
                    </ModSidebar>
                )}

                <ModSidebar title="Tags" icon={Tag}>
                    <div className="flex flex-wrap gap-2">
                        {mod.tags?.map((tag) => (
                            <span key={tag} className="px-3 py-1.5 bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/10 rounded-lg text-xs font-bold text-slate-600 dark:text-slate-300 hover:text-modtale-accent hover:border-modtale-accent hover:bg-modtale-accent/5 dark:hover:bg-modtale-accent/10 transition-all cursor-default shadow-sm dark:shadow-none">
                                {tag}
                            </span>
                        ))}
                        {(!mod.tags || mod.tags.length === 0) && <span className="text-xs text-slate-500 italic">No tags.</span>}
                    </div>
                </ModSidebar>

                {sourceUrl && (
                    <ModSidebar title="Source" icon={Github}>
                        <a href={sourceUrl} target="_blank" rel="noopener noreferrer" className="block w-full p-3 bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/5 rounded-xl hover:border-modtale-accent hover:shadow-md dark:hover:bg-white/5 transition-all group shadow-sm dark:shadow-none">
                            <div className="flex items-center justify-between">
                                <span className="text-sm font-bold text-slate-700 dark:text-slate-300 group-hover:text-modtale-accent dark:group-hover:text-white">Repository</span>
                                <LinkIcon className="w-4 h-4 text-slate-400 group-hover:text-modtale-accent" />
                            </div>
                            <div className="text-[10px] text-slate-500 mt-1 truncate">{sourceUrl.replace(/^https?:\/\//, '')}</div>
                        </a>
                    </ModSidebar>
                )}

                {licenseInfo && (
                    <ModSidebar title="License" icon={Scale}>
                        <div className="flex justify-between items-center text-xs">
                            {licenseInfo.url ? (
                                <a href={licenseInfo.url} target="_blank" rel="noopener noreferrer" className="text-slate-600 dark:text-slate-300 hover:text-modtale-accent hover:underline font-bold truncate block w-full text-center py-2 bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/5 rounded-lg shadow-sm dark:shadow-none">
                                    {licenseInfo.name}
                                </a>
                            ) : (
                                <span className="text-slate-600 dark:text-slate-300 font-bold truncate block w-full text-center py-2 bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/5 rounded-lg shadow-sm dark:shadow-none">
                                    {licenseInfo.name}
                                </span>
                            )}
                        </div>
                    </ModSidebar>
                )}

                {dependencies && dependencies.length > 0 && (
                    <ModSidebar title={isModpack ? "Included Mods" : "Dependencies"} icon={isModpack ? Box : LinkIcon}>
                        <div className="space-y-2">
                            {dependencies.map((dep, idx) => {
                                const meta = depMeta[dep.modId];
                                const iconUrl = getIconUrl(meta?.icon);
                                const title = meta?.title || dep.modTitle || dep.modId;
                                const slug = createSlug(title, dep.modId);

                                return (
                                    <button
                                        key={idx}
                                        onClick={() => navigate(mod.classification === 'MODPACK' ? `/modpack/${slug}` : `/mod/${slug}`)}
                                        className="w-full flex items-center gap-3 p-3 rounded-xl bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-white/5 hover:border-modtale-accent/50 hover:shadow-md dark:hover:bg-slate-900 transition-all group text-left shadow-sm dark:shadow-none"
                                    >
                                        <div className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-black/20 flex items-center justify-center text-slate-400 group-hover:text-modtale-accent transition-colors overflow-hidden">
                                            {iconUrl ? (
                                                <img src={iconUrl} alt="" className="w-full h-full object-cover" />
                                            ) : (
                                                <Box className="w-4 h-4" />
                                            )}
                                        </div>
                                        <div className="min-w-0">
                                            <div className="text-xs font-bold text-slate-700 dark:text-slate-300 group-hover:text-modtale-accent dark:group-hover:text-white truncate">
                                                {title}
                                            </div>
                                            <div className="text-[10px] text-slate-500 flex items-center gap-2">
                                                {!isModpack && (
                                                    <>
                                                        {dep.isOptional ? 'Optional' : <span className="text-amber-500 font-bold">Required</span>}
                                                        <span className="w-1 h-1 rounded-full bg-slate-300 dark:bg-white/20"></span>
                                                    </>
                                                )}
                                                <span className="font-mono opacity-75">v{dep.versionNumber}</span>
                                            </div>
                                        </div>
                                    </button>
                                );
                            })}
                        </div>
                    </ModSidebar>
                )}
            </div>
        </div>
    );
};

interface ReviewSectionProps {
    modId: string;
    reviews: Review[];
    currentUser: User | null;
    onReviewSubmitted: (newReviews: Review[]) => void;
    onError: (msg: string) => void;
    onSuccess: (msg: string) => void;
    innerRef?: React.RefObject<HTMLDivElement>;
}

const ReviewSection: React.FC<ReviewSectionProps> = ({ modId, reviews, currentUser, onReviewSubmitted, onError, onSuccess, innerRef }) => {
    const [text, setText] = useState('');
    const [rating, setRating] = useState(5);
    const [submitting, setSubmitting] = useState(false);

    const submit = async (e: React.FormEvent) => {
        e.preventDefault();
        setSubmitting(true);
        try {
            await api.post(`/projects/${modId}/reviews`, { comment: text, rating: rating, version: 'latest' });
            const res = await api.get(`/projects/${modId}`);
            onReviewSubmitted(res.data.reviews || []);
            setText('');
            onSuccess('Review posted!');
        } catch (err) { onError('Failed to post review.'); } finally { setSubmitting(false); }
    };

    return (
        <div ref={innerRef} className="mt-12 border-t border-slate-200 dark:border-white/5 pt-10 scroll-mt-32">
            <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-8 flex items-center gap-3">
                <MessageSquare className="w-6 h-6 text-modtale-accent" /> Community Reviews
            </h2>

            {currentUser ? (
                <form onSubmit={submit} className="mb-10 p-6 bg-slate-50 dark:bg-slate-950/30 rounded-2xl border border-slate-200 dark:border-white/5 shadow-sm dark:shadow-inner">
                    <h3 className="font-bold text-xs text-slate-500 dark:text-slate-400 uppercase tracking-widest mb-4">Leave a Review</h3>
                    <div className="flex gap-2 mb-4">
                        {[1, 2, 3, 4, 5].map(star => (
                            <button key={star} type="button" onClick={() => setRating(star)} className="hover:scale-110 transition-transform focus:outline-none">
                                <Star className={`w-8 h-8 ${star <= rating ? 'fill-yellow-500 text-yellow-500' : 'text-slate-300 dark:text-slate-700'}`} />
                            </button>
                        ))}
                    </div>
                    <textarea
                        value={text}
                        onChange={e => setText(e.target.value)}
                        className="w-full p-4 rounded-xl bg-white dark:bg-black/40 border border-slate-200 dark:border-white/10 text-slate-900 dark:text-white mb-4 focus:ring-2 focus:ring-modtale-accent outline-none font-medium text-sm min-h-[120px] placeholder:text-slate-400 dark:placeholder:text-slate-600"
                        placeholder="Share your experience with this project..."
                        required
                    />
                    <button type="submit" disabled={submitting} className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 py-3 rounded-xl font-bold flex items-center gap-2 disabled:opacity-50 transition-all shadow-lg shadow-modtale-accent/20 active:scale-95">
                        <Send className="w-4 h-4" /> Post Review
                    </button>
                </form>
            ) : (
                <div className="mb-10 p-8 bg-slate-50 dark:bg-slate-950/30 rounded-2xl text-center text-slate-500 font-bold border border-slate-200 dark:border-white/5">Log in to share your thoughts.</div>
            )}

            <div className="space-y-4">
                {reviews && reviews.length > 0 ? reviews.map((review) => {
                    const avatarUrl = review.userAvatarUrl
                        ? (review.userAvatarUrl.startsWith('/api') ? `${BACKEND_URL}${review.userAvatarUrl}` : review.userAvatarUrl)
                        : null;

                    return (
                        <div key={review.id} className="p-6 bg-white dark:bg-slate-950/20 rounded-2xl border border-slate-200 dark:border-white/5 shadow-sm dark:shadow-none">
                            <div className="flex justify-between items-start mb-3">
                                <div className="flex items-center gap-4">
                                    <div className="w-10 h-10 rounded-full bg-modtale-accent text-white flex items-center justify-center text-sm font-black shadow-lg overflow-hidden shrink-0 relative">
                                        {avatarUrl && (
                                            <img
                                                src={avatarUrl}
                                                alt={review.user}
                                                className="w-full h-full object-cover absolute inset-0 z-10"
                                                onError={(e) => e.currentTarget.style.display = 'none'}
                                            />
                                        )}
                                        <span className="relative z-0">{review.user.charAt(0)}</span>
                                    </div>
                                    <div>
                                        <span className="font-bold text-slate-900 dark:text-white text-base block leading-none mb-1">{review.user}</span>
                                        <StarRating rating={review.rating} size="w-3 h-3" />
                                    </div>
                                </div>
                                <span className="text-xs text-slate-500 dark:text-slate-600 font-mono">{review.version || 'Latest'}</span>
                            </div>
                            <p className="text-slate-700 dark:text-slate-300 leading-relaxed pl-14">{review.comment}</p>
                        </div>
                    );
                }) : <div className="text-center py-12 text-slate-500 dark:text-slate-600 italic">No reviews yet. Be the first to review!</div>}
            </div>
        </div>
    );
};

interface ModDetailProps {
    onToggleFavorite: (id: string) => void;
    isLiked: (id: string) => boolean;
    currentUser: User | null;
    onRefresh?: () => void;
    onDownload: (id: string) => void;
    downloadedSessionIds: Set<string>;
}

export const ModDetail: React.FC<ModDetailProps> = ({ onToggleFavorite, isLiked, currentUser, onRefresh, onDownload, downloadedSessionIds }) => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const location = useLocation();
    const realId = extractId(id);
    const { initialData } = useSSRData();
    const initialMod = (initialData && extractId(initialData.id) === realId) ? initialData : null;

    const [mod, setMod] = useState<Mod | null>(initialMod);
    const [loading, setLoading] = useState(!initialMod);
    const [isNotFound, setIsNotFound] = useState(false);

    const [showDownloadModal, setShowDownloadModal] = useState(false);
    const [showAllVersionsModal, setShowAllVersionsModal] = useState(false);
    const [statusModal, setStatusModal] = useState<any>(null);
    const [isShareOpen, setIsShareOpen] = useState(false);
    const [inviteModal, setInviteModal] = useState(false);
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [galleryIndex, setGalleryIndex] = useState<number | null>(null);

    const [showExperimental, setShowExperimental] = useState(false);
    const [showExperimentalPopup, setShowExperimentalPopup] = useState(false);
    const [isFollowing, setIsFollowing] = useState(false);
    const [pendingDownloadVer, setPendingDownloadVer] = useState<{url: string, ver: string, deps: any[]} | null>(null);
    const [depMeta, setDepMeta] = useState<Record<string, { icon: string, title: string }>>({});

    const reviewsRef = useRef<HTMLDivElement>(null);
    const currentUrl = typeof window !== 'undefined' ? window.location.href : `https://modtale.net${location.pathname}`;

    const projectMeta = useMemo(() => mod ? generateProjectMeta(mod) : null, [mod]);

    useEffect(() => {
        if (mod && extractId(mod.id) === realId) {
            setLoading(false);
            if(currentUser && currentUser.followingIds) setIsFollowing(currentUser.followingIds.includes(mod.author));
            return;
        }
        if (realId) {
            const fetchProject = async () => {
                setLoading(true);
                try {
                    const res = await api.get(`/projects/${realId}`);
                    setMod(res.data);
                    if (currentUser) {
                        try {
                            const userRes = await api.get(`/user/profile/${res.data.author}`);
                            if (currentUser.followingIds?.includes(userRes.data.id)) setIsFollowing(true);
                        } catch (ignore) {}
                    }
                    api.post(`/analytics/view/${realId}`).catch(() => {});
                } catch (err: any) { setIsNotFound(true); } finally { setLoading(false); }
            };
            fetchProject();
        }
    }, [realId, currentUser]);

    useEffect(() => {
        const params = new URLSearchParams(location.search);
        if (params.get('invite') === 'true' && currentUser && mod?.pendingInvites?.includes(currentUser.username)) setInviteModal(true);
    }, [location.search, currentUser, mod]);

    const allVersions = useMemo(() => mod?.versions || [], [mod]);
    const latestForGame = useMemo(() => {
        const versionsByGame: Record<string, ProjectVersion[]> = {};
        allVersions.forEach(v => {
            let gameVerList = v.gameVersions && v.gameVersions.length > 0 ? v.gameVersions : [(v as any).gameVersion || 'Unknown'];
            gameVerList.forEach(gv => {
                if (!versionsByGame[gv]) versionsByGame[gv] = [];
                versionsByGame[gv].push(v);
            });
        });

        const result: Record<string, ProjectVersion[]> = {};
        Object.keys(versionsByGame).forEach(gv => {
            const versions = versionsByGame[gv];
            const hasRelease = versions.some(v => !v.channel || v.channel === 'RELEASE');
            if (showExperimentalPopup || !hasRelease) {
                result[gv] = versions;
            } else {
                result[gv] = versions.filter(v => !v.channel || v.channel === 'RELEASE');
            }
        });
        return result;
    }, [allVersions, showExperimentalPopup]);

    const sortedHistory = useMemo(() => {
        const displayedVersions = allVersions.filter(v => showExperimental ? true : (!v.channel || v.channel === 'RELEASE'));
        return [...displayedVersions].sort((a, b) => compareSemVer(b.versionNumber, a.versionNumber));
    }, [allVersions, showExperimental]);

    const latestDependencies = useMemo(() => {
        if (allVersions.length === 0) return [];
        const latest = [...allVersions].sort((a, b) => new Date(b.releaseDate).getTime() - new Date(a.releaseDate).getTime())[0];
        return latest.dependencies || [];
    }, [allVersions]);

    useEffect(() => {
        if (!latestDependencies || latestDependencies.length === 0) return;

        const fetchMeta = async () => {
            const missing = latestDependencies.filter(d => !depMeta[d.modId]);
            if (missing.length === 0) return;

            const newMeta = { ...depMeta };
            await Promise.all(missing.map(async (d) => {
                try {
                    const res = await api.get(`/projects/${d.modId}/meta`);
                    newMeta[d.modId] = {
                        icon: res.data.icon,
                        title: res.data.title
                    };
                } catch (e) {
                    newMeta[d.modId] = { icon: '', title: d.modTitle || d.modId };
                }
            }));
            setDepMeta(newMeta);
        };
        fetchMeta();
    }, [latestDependencies]);

    const initiateDownload = (url: string, ver?: string, deps?: any[]) => {
        if (mod?.classification !== 'MODPACK' && deps && deps.length > 0) {
            setPendingDownloadVer({ url, ver: ver || '', deps });
            setShowDownloadModal(false);
        } else {
            executeDownload(url, ver);
        }
    };

    const executeDownload = (fileUrl: string, ver?: string) => {
        const targetUrl = mod?.classification === 'MODPACK'
            ? `${API_BASE_URL}/projects/${mod.id}/versions/${ver}/download`
            : (ver ? `${API_BASE_URL}/projects/${mod?.id}/versions/${ver}/download` : `${API_BASE_URL}/files/download/${encodeURI(fileUrl)}`);

        window.open(targetUrl, '_blank');
        if(mod && !downloadedSessionIds.has(mod.id)) { setMod(prev => prev ? { ...prev, downloadCount: prev.downloadCount + 1 } : null); onDownload(mod.id); }
        setPendingDownloadVer(null); setShowDownloadModal(false); setShowAllVersionsModal(false);
        setStatusModal({ type: 'success', title: 'Download Started', msg: 'Your download should begin shortly.' });
    };

    const handleShare = async () => {
        if (/Android|webOS|iPhone|iPad/i.test(navigator.userAgent) && navigator.share) {
            try { await navigator.share({ title: mod?.title, url: currentUrl }); } catch (e) { }
        } else { setIsShareOpen(true); }
    };

    const handleFollowToggle = async () => {
        if (!currentUser || !mod) return;
        const oldState = isFollowing;
        setIsFollowing(!oldState);
        try {
            await api.post(oldState ? `/user/unfollow/${mod.author}` : `/user/follow/${mod.author}`);
        } catch (e) { setIsFollowing(oldState); setStatusModal({ type: 'error', title: 'Error', msg: 'Failed to update status.' }); }
    };

    if (isNotFound) return <NotFound />;
    if (loading) return <div className="min-h-screen bg-slate-950 flex items-center justify-center"><Spinner fullScreen={false} className="w-8 h-8" /></div>;
    if (!mod) return null;

    const resolveUrl = (url: string) => url.startsWith('/api') ? `${BACKEND_URL}${url}` : url;
    const classification = mod.classification || 'PLUGIN';
    const displayClassification = classification === 'SAVE' ? 'World' : (classification === 'MODPACK' ? 'Modpack' : classification.charAt(0) + classification.slice(1).toLowerCase());
    const isOwner = currentUser && mod ? currentUser.username === mod.author : false;

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark pb-20">
            {projectMeta && (
                <Helmet>
                    <title>{projectMeta.title}</title>
                    <meta name="description" content={projectMeta.description} />
                    <meta property="og:title" content={projectMeta.title} />
                    <meta property="og:description" content={projectMeta.description} />
                    {mod.imageUrl && <meta property="og:image" content={resolveUrl(mod.imageUrl)} />}
                    <meta property="og:type" content="game.modification" />
                    <meta name="author" content={projectMeta.author} />
                </Helmet>
            )}

            {statusModal && <StatusModal type={statusModal.type} title={statusModal.title} message={statusModal.msg} onClose={() => setStatusModal(null)} />}
            <ShareModal isOpen={isShareOpen} onClose={() => setIsShareOpen(false)} url={currentUrl} title={mod.title} author={mod.author} />
            {showDeleteConfirm && <StatusModal type="warning" title="Delete Project?" message="Are you sure?" onClose={() => setShowDeleteConfirm(false)} actionLabel="Delete" onAction={async () => { try { await api.delete(`/projects/${mod.id}`); navigate('/'); } catch(e) { setStatusModal({type:'error', title:'Error', msg:'Failed'}); } }} secondaryLabel="Cancel" />}

            {pendingDownloadVer && <DependencyModal dependencies={pendingDownloadVer.deps} onClose={() => setPendingDownloadVer(null)} onConfirm={() => executeDownload(pendingDownloadVer.url, pendingDownloadVer.ver)} />}

            <DownloadModal show={showDownloadModal} onClose={() => setShowDownloadModal(false)} versionsByGame={latestForGame} onDownload={initiateDownload} showExperimental={showExperimentalPopup} onToggleExperimental={() => setShowExperimentalPopup(!showExperimentalPopup)} onViewHistory={() => { setShowDownloadModal(false); setShowAllVersionsModal(true); }} />

            <HistoryModal show={showAllVersionsModal} onClose={() => setShowAllVersionsModal(false)} history={sortedHistory} showExperimental={showExperimental} onToggleExperimental={() => setShowExperimental(!showExperimental)} onDownload={initiateDownload} />

            {galleryIndex !== null && mod.galleryImages && mod.galleryImages.length > 0 && (
                <div className="fixed inset-0 z-[200] bg-black/95 flex flex-col items-center justify-center backdrop-blur-sm" onClick={() => setGalleryIndex(null)}>
                    <button onClick={() => setGalleryIndex(null)} className="absolute top-6 right-6 p-2 text-white/70 hover:text-white z-[210]"><X className="w-8 h-8" /></button>
                    <div className="relative flex items-center justify-center w-full max-w-7xl min-[1600px]:max-w-[100rem] px-0 md:px-4 h-[70vh] transition-[max-width] duration-300">
                        <button onClick={(e) => {e.stopPropagation(); setGalleryIndex((galleryIndex - 1 + mod.galleryImages!.length) % mod.galleryImages!.length)}} className="absolute left-2 md:relative md:left-0 p-3 bg-black/50 md:bg-transparent text-white/70 hover:text-white rounded-full z-10"><ChevronLeft className="w-8 h-8 md:w-10 md:h-10" /></button>
                        <img src={resolveUrl(mod.galleryImages[galleryIndex])} className="max-h-full max-w-full object-contain shadow-2xl md:rounded-lg" onClick={e => e.stopPropagation()} alt="" />
                        <button onClick={(e) => {e.stopPropagation(); setGalleryIndex((galleryIndex + 1) % mod.galleryImages!.length)}} className="absolute right-2 md:relative md:right-0 p-3 bg-black/50 md:bg-transparent text-white/70 hover:text-white rounded-full z-10"><ChevronRight className="w-8 h-8 md:w-10 md:h-10" /></button>
                    </div>
                </div>
            )}

            <div className="relative w-full aspect-[3/1] bg-slate-800 overflow-hidden hidden md:block">
                {mod.bannerUrl ? (
                    <img src={resolveUrl(mod.bannerUrl)} alt="" className="w-full h-full object-cover opacity-80" />
                ) : (
                    <div className="w-full h-full bg-gradient-to-br from-indigo-900 via-slate-900 to-black"></div>
                )}
                <div className="absolute inset-0 bg-gradient-to-t from-slate-50 dark:from-modtale-dark via-transparent to-black/30" />

                <div className="max-w-7xl min-[1600px]:max-w-[100rem] mx-auto px-6 relative z-40 pt-6">
                    <button onClick={() => navigate('/home')} className="flex items-center text-white/90 font-bold transition-all bg-black/30 hover:bg-black/50 backdrop-blur-md border border-white/10 px-4 py-2 rounded-xl w-fit shadow-lg group">
                        <ChevronLeft className="w-4 h-4 mr-1 group-hover:-translate-x-1 transition-transform" /> Back
                    </button>
                </div>
            </div>

            <div className="max-w-7xl min-[1600px]:max-w-[100rem] mx-auto px-0 md:px-4 relative z-50 md:-mt-32 transition-[max-width] duration-300">
                <div className="bg-slate-50 dark:bg-slate-900 md:bg-white/90 md:dark:bg-slate-900/90 md:backdrop-blur-xl md:border md:border-slate-200 md:dark:border-white/10 md:rounded-3xl md:shadow-2xl min-h-[60vh]">

                    <div className="md:p-12 md:pb-0 relative">
                        <ModHeader
                            mod={mod}
                            dependencies={latestDependencies}
                            depMeta={depMeta}
                            isOwner={isOwner}
                            isLiked={isLiked(mod.id)}
                            isFollowing={isFollowing}
                            currentUser={currentUser}
                            onToggleFavorite={() => onToggleFavorite(mod.id)}
                            onToggleFollow={handleFollowToggle}
                            onShare={handleShare}
                            onOpenGallery={() => {if(mod.galleryImages?.length) setGalleryIndex(0)}}
                            onOpenHistory={() => setShowAllVersionsModal(true)}
                            onScrollToReviews={() => reviewsRef.current?.scrollIntoView({ behavior: 'smooth' })}
                            onDelete={() => setShowDeleteConfirm(true)}
                            onManageContributors={() => navigate(mod.classification === 'MODPACK' ? `/modpack/${createSlug(mod.title, mod.id)}/contributors` : `/mod/${createSlug(mod.title, mod.id)}/contributors`)}
                            navigate={navigate}
                            classificationIcon={getClassificationIcon(classification)}
                            displayClassification={displayClassification}
                            onDownloadClick={() => setShowDownloadModal(true)}
                        />
                    </div>

                    <div className="flex flex-col lg:grid lg:grid-cols-12 md:border-t md:border-slate-200 md:dark:border-white/5 md:mt-10">

                        <div className="lg:col-span-8 px-4 py-6 md:p-12 md:border-r md:border-slate-200 md:dark:border-white/5 order-2 lg:order-1 md:rounded-bl-3xl">

                            <div className="md:hidden mb-8 border-b border-slate-200 dark:border-white/5 pb-8">
                                <ProjectSidebar
                                    mod={mod}
                                    latestDownloads={allVersions[0]?.downloadCount || 0}
                                    avgRating={mod.rating?.toFixed(1) || "0.0"}
                                    totalReviews={mod.reviews?.length || 0}
                                    navigate={navigate}
                                    dependencies={latestDependencies}
                                    depMeta={depMeta}
                                    sourceUrl={(mod as any).sourceUrl || (mod as any).repoUrl}
                                />
                            </div>

                            <div className="prose dark:prose-invert prose-lg max-w-none prose-a:text-modtale-accent prose-a:no-underline hover:prose-a:underline prose-img:rounded-2xl prose-headings:font-black prose-p:text-slate-600 dark:prose-p:text-slate-300 prose-p:leading-relaxed">
                                {mod.about ? (
                                    <ReactMarkdown rehypePlugins={[rehypeRaw, rehypeSanitize]}>{mod.about}</ReactMarkdown>
                                ) : (
                                    <p className="text-slate-500 italic">No detailed description provided.</p>
                                )}
                            </div>

                            <ReviewSection modId={mod.id} reviews={mod.reviews || []} currentUser={currentUser} onReviewSubmitted={(r) => setMod(prev => prev ? {...prev, reviews: r} : null)} onError={(m) => setStatusModal({type:'error', title:'Error', msg:m})} onSuccess={(m) => setStatusModal({type:'success', title:'Success', msg:m})} innerRef={reviewsRef} />
                        </div>

                        <div className="hidden md:block lg:col-span-4 px-4 py-6 md:p-12 order-1 lg:order-2 bg-transparent md:rounded-br-3xl">
                            <ProjectSidebar
                                mod={mod}
                                latestDownloads={allVersions[0]?.downloadCount || 0}
                                avgRating={mod.rating?.toFixed(1) || "0.0"}
                                totalReviews={mod.reviews?.length || 0}
                                navigate={navigate}
                                dependencies={latestDependencies}
                                depMeta={depMeta}
                                sourceUrl={(mod as any).sourceUrl || (mod as any).repoUrl}
                            />

                            <div className="mt-8 pt-6 border-t border-slate-200 dark:border-white/5 flex items-center justify-between group">
                                <div>
                                    <div className="text-[10px] font-bold text-slate-500 uppercase tracking-widest mb-1">Project ID</div>
                                    <code className="text-xs font-mono text-slate-400 group-hover:text-slate-900 dark:group-hover:text-white transition-colors">{mod.id}</code>
                                </div>
                                <button onClick={() => {navigator.clipboard.writeText(mod.id); setStatusModal({type:'success', title:'Copied', msg:'ID copied'})}} className="p-2 text-slate-500 hover:text-modtale-accent transition-colors"><Copy className="w-4 h-4" /></button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};