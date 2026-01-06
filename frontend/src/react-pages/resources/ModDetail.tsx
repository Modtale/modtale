import React, { useEffect, useState, useRef, useMemo } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import { Helmet } from 'react-helmet-async';
import type { Mod, User, ProjectVersion, Review, ModDependency } from '../../types';
import {
    MessageSquare, Send, Star, StarHalf, Copy, X,
    Tag, Scale, Link as LinkIcon, Box, Gamepad2, Heart, Share2, Edit, ChevronLeft, ChevronRight,
    Download
} from 'lucide-react';
import { StatusModal } from '../../components/ui/StatusModal';
import { ShareModal } from '@/components/resources/mod-detail/ShareModal';
import { api, API_BASE_URL, BACKEND_URL } from '../../utils/api';
import { extractId, createSlug, getProjectUrl } from '../../utils/slug';
import { useSSRData } from '../../context/SSRContext';
import NotFound from '../../components/ui/error/NotFound';
import { ModHeader } from '@/components/resources/shared/ModHeader.tsx';
import { Spinner } from '../../components/ui/Spinner';
import { getClassificationIcon, compareSemVer } from '../../utils/modHelpers';
import { DependencyModal, DownloadModal, HistoryModal } from '@/components/resources/mod-detail/DownloadDialogs';
import { ProjectLayout, SidebarSection } from '@/components/resources/shared/ProjectLayout';
import { generateProjectMeta } from '../../utils/meta';
import { getBreadcrumbsForClassification, generateBreadcrumbSchema } from '../../utils/schema';

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
    avgRating: string;
    totalReviews: number;
    dependencies?: ModDependency[];
    depMeta: Record<string, { icon: string, title: string }>;
    sourceUrl?: string;
    navigate: (path: string) => void;
}> = ({ mod, avgRating, totalReviews, dependencies, depMeta, navigate }) => {
    const licenseInfo = useMemo(() => {
        if (mod.links?.LICENSE) {
            return { name: mod.license || 'Custom License', url: mod.links.LICENSE };
        }
        return mod.license ? getLicenseInfo(mod.license) : null;
    }, [mod.license, mod.links]);

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
        <div className="flex flex-col gap-6">
            <div className="bg-slate-100 dark:bg-slate-900/50 rounded-2xl border border-slate-200 dark:border-white/5 grid grid-cols-2 divide-x divide-slate-200 dark:divide-white/5">
                <div className="p-6 flex flex-col items-center justify-center text-center">
                    <div className="text-4xl font-black text-slate-900 dark:text-white tracking-tighter leading-none mb-2">{avgRating}</div>
                    <StarRating rating={Number(avgRating)} size="w-4 h-4" />
                    <div className="text-[10px] font-bold text-slate-500 uppercase tracking-widest mt-2">{totalReviews} Reviews</div>
                </div>
                <div className="p-6 flex flex-col items-center justify-center text-center">
                    <div className="text-2xl md:text-3xl font-black text-slate-900 dark:text-white tracking-tighter leading-none mb-2">{mod.downloadCount.toLocaleString()}</div>
                    <div className="text-[10px] font-bold text-slate-500 uppercase tracking-widest flex items-center gap-1.5"><Download className="w-3 h-3" /> Downloads</div>
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

            <div className="pt-6 border-t border-slate-200 dark:border-white/5 flex items-center justify-between group">
                <div>
                    <div className="text-[10px] font-bold text-slate-500 uppercase tracking-widest mb-1">Project ID</div>
                    <code className="text-xs font-mono text-slate-400 group-hover:text-slate-900 dark:group-hover:text-white transition-colors">{mod.id}</code>
                </div>
                <button onClick={() => {navigator.clipboard.writeText(mod.id);}} className="p-2 text-slate-500 hover:text-modtale-accent transition-colors"><Copy className="w-4 h-4" /></button>
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
        <div ref={innerRef} className="mt-12 border-t border-slate-200 dark:border-white/5 pt-10">
            <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-8 flex items-center gap-3">
                <MessageSquare className="w-6 h-6 text-modtale-accent" /> Community Reviews
            </h2>

            {currentUser ? (
                <form onSubmit={submit} className="mb-10 p-6 bg-slate-50 dark:bg-slate-950/30 rounded-2xl border border-slate-200 dark:border-white/5">
                    <div className="flex gap-2 mb-4">
                        {[1, 2, 3, 4, 5].map(star => (
                            <button key={star} type="button" onClick={() => setRating(star)} className="hover:scale-110 transition-transform focus:outline-none">
                                <Star className={`w-8 h-8 ${star <= rating ? 'fill-yellow-500 text-yellow-500' : 'text-slate-300 dark:text-slate-700'}`} />
                            </button>
                        ))}
                    </div>
                    <textarea value={text} onChange={e => setText(e.target.value)} className="w-full p-4 rounded-xl bg-white dark:bg-black/40 border border-slate-200 dark:border-white/10 text-slate-900 dark:text-white mb-4 focus:ring-2 focus:ring-modtale-accent outline-none font-medium text-sm min-h-[120px]" placeholder="Share your experience..." required />
                    <button type="submit" disabled={submitting} className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 py-3 rounded-xl font-bold flex items-center gap-2 disabled:opacity-50"><Send className="w-4 h-4" /> Post Review</button>
                </form>
            ) : <div className="mb-10 p-8 bg-slate-50 dark:bg-slate-950/30 rounded-2xl text-center text-slate-500 font-bold border border-slate-200 dark:border-white/5">Log in to review.</div>}

            <div className="space-y-4">
                {reviews?.length > 0 ? reviews.map((review) => (
                    <div key={review.id} className="p-6 bg-white dark:bg-slate-950/20 rounded-2xl border border-slate-200 dark:border-white/5">
                        <div className="flex justify-between items-start mb-3">
                            <div className="flex items-center gap-4">
                                <div className="w-10 h-10 rounded-full bg-modtale-accent text-white flex items-center justify-center font-black">{review.user.charAt(0)}</div>
                                <div><span className="font-bold text-slate-900 dark:text-white block">{review.user}</span><StarRating rating={review.rating} size="w-3 h-3" /></div>
                            </div>
                        </div>
                        <p className="text-slate-700 dark:text-slate-300 pl-14">{review.comment}</p>
                    </div>
                )) : <div className="text-center py-12 text-slate-500 italic">No reviews yet.</div>}
            </div>
        </div>
    );
};

export const ModDetail: React.FC<{ onToggleFavorite: (id: string) => void; isLiked: (id: string) => boolean; currentUser: User | null; onDownload: (id: string) => void; downloadedSessionIds: Set<string>; }> = ({ onToggleFavorite, isLiked, currentUser, onDownload, downloadedSessionIds }) => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const location = useLocation();
    const realId = extractId(id);
    const { initialData } = useSSRData();
    const initialMod = (initialData && extractId(initialData.id) === realId) ? initialData : null;

    const [mod, setMod] = useState<Mod | null>(initialMod);
    const [loading, setLoading] = useState(!initialMod);
    const [isNotFound, setIsNotFound] = useState(false);

    // Modal States
    const [showDownloadModal, setShowDownloadModal] = useState(false);
    const [showAllVersionsModal, setShowAllVersionsModal] = useState(false);
    const [statusModal, setStatusModal] = useState<any>(null);
    const [isShareOpen, setIsShareOpen] = useState(false);
    const [galleryIndex, setGalleryIndex] = useState<number | null>(null);
    const [pendingDownloadVer, setPendingDownloadVer] = useState<{url: string, ver: string, deps: any[]} | null>(null);

    const [isFollowing, setIsFollowing] = useState(false);
    const [depMeta, setDepMeta] = useState<Record<string, { icon: string, title: string }>>({});
    const [showExperimental, setShowExperimental] = useState(false);
    const reviewsRef = useRef<HTMLDivElement>(null);
    const currentUrl = typeof window !== 'undefined' ? window.location.href : `https://modtale.net${location.pathname}`;
    const canEdit = currentUser && mod && (currentUser.username === mod.author || mod.contributors?.includes(currentUser.username));

    const projectMeta = useMemo(() => mod ? generateProjectMeta(mod) : null, [mod]);
    const breadcrumbSchema = useMemo(() => mod ? generateBreadcrumbSchema([...getBreadcrumbsForClassification(mod.classification || 'PLUGIN'), { name: mod.title, url: getProjectUrl(mod) }]) : null, [mod]);

    useEffect(() => {
        if (mod && extractId(mod.id) === realId) {
            setLoading(false);
            if(currentUser?.followingIds) setIsFollowing(currentUser.followingIds.includes(mod.author));
            return;
        }
        if (realId) {
            api.get(`/projects/${realId}`).then(res => {
                setMod(res.data);
                if (currentUser?.followingIds?.includes(res.data.author)) setIsFollowing(true);
            }).catch(() => setIsNotFound(true)).finally(() => setLoading(false));
            api.post(`/analytics/view/${realId}`).catch(() => {});
        }
    }, [realId, currentUser]);

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
                if (!v.channel || v.channel === 'RELEASE') result[gv].push(v);
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
            const missing = latestDependencies.filter(d => !depMeta[d.modId]);
            if (!missing.length) return;
            const newMeta = { ...depMeta };
            await Promise.all(missing.map(async (d) => {
                try {
                    const res = await api.get(`/projects/${d.modId}/meta`);
                    newMeta[d.modId] = { icon: res.data.icon, title: res.data.title };
                } catch (e) { newMeta[d.modId] = { icon: '', title: d.modTitle || d.modId }; }
            }));
            setDepMeta(newMeta);
        };
        fetchMeta();
    }, [latestDependencies]);

    const handleShare = () => {
        if (navigator.share) navigator.share({ title: mod?.title, url: currentUrl }).catch(() => {});
        else setIsShareOpen(true);
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

    const executeDownload = (fileUrl: string, ver?: string) => {
        const targetUrl = mod?.classification === 'MODPACK'
            ? `${API_BASE_URL}/projects/${mod.id}/versions/${ver}/download`
            : (ver ? `${API_BASE_URL}/projects/${mod?.id}/versions/${ver}/download` : `${API_BASE_URL}/files/download/${encodeURI(fileUrl)}`);

        window.open(targetUrl, '_blank');
        if(mod && !downloadedSessionIds.has(mod.id)) { setMod(prev => prev ? { ...prev, downloadCount: prev.downloadCount + 1 } : null); onDownload(mod.id); }
        setPendingDownloadVer(null); setShowDownloadModal(false); setShowAllVersionsModal(false);
        setStatusModal({ type: 'success', title: 'Download Started', msg: 'Your download should begin shortly.' });
    };

    if (isNotFound) return <NotFound />;
    if (loading) return <div className="min-h-screen bg-slate-950 flex items-center justify-center"><Spinner fullScreen={false} className="w-8 h-8" /></div>;
    if (!mod) return null;

    const resolveUrl = (url: string) => url.startsWith('/api') ? `${BACKEND_URL}${url}` : url;

    return (
        <>
            {projectMeta && <Helmet><title>{projectMeta.title}</title><meta name="description" content={projectMeta.description} /><script type="application/ld+json">{JSON.stringify(breadcrumbSchema)}</script></Helmet>}

            {/* Hoisted Modals */}
            {statusModal && <StatusModal type={statusModal.type} title={statusModal.title} message={statusModal.msg} onClose={() => setStatusModal(null)} />}
            <ShareModal isOpen={isShareOpen} onClose={() => setIsShareOpen(false)} url={currentUrl} title={mod.title} author={mod.author} />

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
                    <div className="relative w-full max-w-6xl max-h-[85vh] bg-slate-900 rounded-2xl shadow-2xl flex flex-col overflow-hidden border border-white/10" onClick={e => e.stopPropagation()}>

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
                onBack={() => navigate(-1)}
                headerActions={
                    <>
                        <button disabled={!currentUser} onClick={() => onToggleFavorite(mod.id)} className={`p-2.5 rounded-xl border transition-all ${isLiked(mod.id) ? 'bg-red-500/10 text-red-500 border-red-500/20' : 'bg-white/5 text-slate-400 hover:text-white border-white/5'}`}>
                            <Heart className={`w-5 h-5 ${isLiked(mod.id) ? 'fill-current' : ''}`} />
                        </button>
                        <button onClick={handleShare} className="p-2.5 rounded-xl border border-white/5 bg-white/5 text-slate-400 hover:text-blue-400">
                            <Share2 className="w-5 h-5" />
                        </button>
                        {canEdit && (
                            <button onClick={() => navigate(`${getProjectUrl(mod)}/edit`)} className="p-2.5 rounded-xl border border-white/5 bg-white/5 text-slate-400 hover:text-white">
                                <Edit className="w-5 h-5" />
                            </button>
                        )}
                    </>
                }
                headerContent={
                    <ModHeader
                        mod={mod}
                        isOwner={currentUser?.username === mod.author}
                        isLiked={isLiked(mod.id)}
                        isFollowing={isFollowing}
                        currentUser={currentUser}
                        onToggleFavorite={() => onToggleFavorite(mod.id)}
                        onToggleFollow={handleFollowToggle}
                        onShare={handleShare}
                        onOpenGallery={() => {if(mod.galleryImages?.length) setGalleryIndex(0)}}
                        onOpenHistory={() => setShowAllVersionsModal(true)}
                        onScrollToReviews={() => reviewsRef.current?.scrollIntoView({ behavior: 'smooth' })}
                        navigate={navigate}
                        classificationIcon={getClassificationIcon(mod.classification || 'PLUGIN')}
                        displayClassification={mod.classification === 'SAVE' ? 'World' : (mod.classification === 'MODPACK' ? 'Modpack' : 'Mod')}
                        onDownloadClick={() => setShowDownloadModal(true)}
                    />
                }
                mainContent={
                    <>
                        <div className="prose dark:prose-invert prose-lg max-w-none">
                            {mod.about ? <ReactMarkdown rehypePlugins={[rehypeRaw, rehypeSanitize]}>{mod.about}</ReactMarkdown> : <p className="text-slate-500 italic">No description.</p>}
                        </div>
                        <ReviewSection modId={mod.id} reviews={mod.reviews || []} currentUser={currentUser} onReviewSubmitted={(r) => setMod(prev => prev ? {...prev, reviews: r} : null)} onError={(m) => setStatusModal({type:'error', title:'Error', msg:m})} onSuccess={(m) => setStatusModal({type:'success', title:'Success', msg:m})} innerRef={reviewsRef} />
                    </>
                }
                sidebarContent={
                    <ProjectSidebar
                        mod={mod}
                        avgRating={mod.rating?.toFixed(1) || "0.0"}
                        totalReviews={mod.reviews?.length || 0}
                        navigate={navigate}
                        dependencies={latestDependencies}
                        depMeta={depMeta}
                        sourceUrl={(mod as any).sourceUrl || (mod as any).repoUrl}
                    />
                }
            />
        </>
    );
};