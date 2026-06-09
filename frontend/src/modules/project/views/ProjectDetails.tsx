import React, { Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useNavigate, useLocation, Link } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import { ChevronLeft, ChevronRight, Github, Globe } from 'lucide-react';
import { createPortal } from 'react-dom';

import type { User } from '@/types';
import { theme } from '@/styles/theme';
import { SiteRoutes } from '@/utils/routes';
import { generateProjectMeta } from '@/utils/meta';
import { generateBreadcrumbSchema, getBreadcrumbsForClassification } from '@/utils/schema';
import { DiscordIcon } from '@/utils/modHelpers';

import { useSSRData } from '@/context/SSRContext';
import { useMobile } from '@/context/MobileContext';

import { useProjectDetail } from '../hooks/useProjectDetail';
import { Sidebar } from '../components/Sidebar';
import { HeaderActions, HeaderContent } from '../components/Header';
import { ActionBar } from '../components/ActionBar';

import { ViewDetails } from '../tabs/ViewDetails';
import { useHMWiki } from '../hooks/useHMWiki';

import { ProjectLayout } from '../components/ProjectLayout';
import { Spinner } from '@/components/ui/Spinner';
import NotFound from '@/components/ui/error/NotFound';
import { StatusModal } from '@/components/ui/StatusModal';
import { api, extractApiErrorMessage } from '@/utils/api';
import { projectClient } from '../api/projectClient';
import { useScrollLock } from '@/hooks/useScrollLock';
import '../styles/downloadFx.css';

const ShareModal = lazy(() => import('../components/dialogs/ShareModal').then((module) => ({ default: module.ShareModal })));
const ReportModal = lazy(() => import('../components/dialogs/ReportModal').then((module) => ({ default: module.ReportModal })));
const PostDownloadModal = lazy(() => import('../components/dialogs/PostDownloadModal').then((module) => ({ default: module.PostDownloadModal })));
const HistoryModal = lazy(() => import('../components/dialogs/HistoryModal').then((module) => ({ default: module.HistoryModal })));
const DownloadModal = lazy(() => import('../components/dialogs/DownloadModal').then((module) => ({ default: module.DownloadModal })));
const DependencyModal = lazy(() => import('../components/dialogs/DependencyModal').then((module) => ({ default: module.DependencyModal })));
const Wiki = lazy(() => import('../tabs/Wiki').then((module) => ({ default: module.Wiki })));
const WikiSidebar = lazy(() => import('../components/HMWiki').then((module) => ({ default: module.WikiSidebar })));

interface ProjectDetailViewProps {
    currentUser: User | null;
    isLiked: (id: string) => boolean;
    onToggleFavorite: (id: string) => void;
    onDownload: (id: string) => void;
    downloadedSessionIds: Set<string>;
    onRefresh: () => Promise<void>;
}

type DownloadChannel = 'RELEASE' | 'BETA' | 'ALPHA';

const normalizeDownloadChannel = (channel?: string): DownloadChannel => (
    channel === 'BETA' || channel === 'ALPHA' ? channel : 'RELEASE'
);

export const ProjectDetails: React.FC<ProjectDetailViewProps> = ({
                                                                     currentUser, isLiked, onToggleFavorite, onDownload, downloadedSessionIds, onRefresh
                                                                 }) => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const location = useLocation();
    const { isMobile } = useMobile();
    const { initialData } = useSSRData();

    const { project, setProject, loading, isNotFound, authorProfile, orgMembers, contributors, depMeta, latestDependencies, isFollowing, handleFollowToggle } = useProjectDetail(id, initialData, currentUser);

    const [statusModal, setStatusModal] = useState<{ type: 'success' | 'error' | 'warning' | 'info'; title: string; message: string } | null>(null);
    const [isShareOpen, setIsShareOpen] = useState(false);
    const [isReportOpen, setIsReportOpen] = useState(false);

    const [isHistoryOpen, setIsHistoryOpen] = useState(false);
    const [isDownloadOpen, setIsDownloadOpen] = useState(false);
    const [showExperimental, setShowExperimental] = useState(false);

    const [showPostDownloadModal, setShowPostDownloadModal] = useState(false);
    const [lastDownloadWasBundle, setLastDownloadWasBundle] = useState(false);
    const [lastDownloadedFileName, setLastDownloadedFileName] = useState('');
    const [lastDownloadChannel, setLastDownloadChannel] = useState<DownloadChannel>('RELEASE');
    const [showDownloadFx, setShowDownloadFx] = useState(false);
    const [preReleaseGameVersions, setPreReleaseGameVersions] = useState<string[]>([]);
    const [orderedGameVersions, setOrderedGameVersions] = useState<string[]>([]);

    const [isDepModalOpen, setIsDepModalOpen] = useState(false);
    const [pendingDownload, setPendingDownload] = useState<{ versionNumber: string; gameVersion: string; dependencies: any[]; channel: DownloadChannel } | null>(null);
    const commentsRef = useRef<HTMLDivElement>(null);

    const browseBackTarget = useMemo(() => {
        if (typeof window === 'undefined') return SiteRoutes.browse();
        const stored = window.sessionStorage.getItem('modtale.lastBrowseUrl');
        if (!stored) return SiteRoutes.browse();

        const isBrowsePath = /^\/(mods|plugins|modpacks|worlds|art|data)(\?|$)/.test(stored);
        return isBrowsePath ? stored : SiteRoutes.browse();
    }, []);

    const isWikiRoute = location.pathname.includes('/wiki');
    const isGalleryRoute = /\/gallery\/?$/.test(location.pathname);
    const wikiMatch = location.pathname.match(/\/wiki\/?(.*)/);
    const wikiPageSlug = wikiMatch?.[1];

    const { data: wikiData, loading: wikiLoading, error: wikiError } = useHMWiki(project?.hmWikiSlug, wikiPageSlug, isWikiRoute && !!project?.hmWikiEnabled);
    const [displayWikiData, setDisplayWikiData] = useState(wikiData);
    const [displaySlug, setDisplaySlug] = useState(wikiPageSlug);
    const wikiContentRef = useRef<HTMLDivElement>(null);
    const [lockedHeight, setLockedHeight] = useState<number | undefined>(undefined);

    const prevPathnameRef = useRef(location.pathname);
    const scrollPosRef = useRef(0);
    const downloadFxTimeoutRef = useRef<number | null>(null);
    const [galleryIndex, setGalleryIndex] = useState(0);
    const galleryImages = project?.galleryImages || [];
    const projectUrl = project ? SiteRoutes.project(project) : '';

    const isStableBuild = useCallback((version: any) => {
        if (!version) return false;
        const channel = (version.channel || 'RELEASE').toUpperCase();
        if (channel === 'ALPHA' || channel === 'BETA') return false;
        return !String(version.versionNumber || '').includes('-');
    }, []);

    const hasStableBuilds = useMemo(() => {
        return (project?.versions || []).some((v: any) => isStableBuild(v));
    }, [project?.versions, isStableBuild]);

    const openGalleryIndexFromHash = () => {
        const hashIndex = Number((location.hash || '').replace('#', ''));
        if (Number.isFinite(hashIndex) && hashIndex > 0) {
            return Math.min(hashIndex - 1, Math.max(galleryImages.length - 1, 0));
        }
        return 0;
    };

    useEffect(() => {
        const handleScroll = () => { scrollPosRef.current = window.scrollY; };
        window.addEventListener('scroll', handleScroll, { passive: true });
        return () => window.removeEventListener('scroll', handleScroll);
    }, []);
    useScrollLock(isGalleryRoute);

    useEffect(() => {
        return () => {
            if (downloadFxTimeoutRef.current) window.clearTimeout(downloadFxTimeoutRef.current);
        };
    }, []);

    useEffect(() => {
        if (!isDownloadOpen) return;
        if (orderedGameVersions.length > 0 || preReleaseGameVersions.length > 0) return;

        let isCancelled = false;

        projectClient.getMetaGameVersionCatalog()
            .then((catalog) => {
                if (isCancelled) return;
                setPreReleaseGameVersions(catalog?.preReleaseVersions || []);
                setOrderedGameVersions(catalog?.orderedVersions || catalog?.allVersions || []);
            })
            .catch(() => {
                if (isCancelled) return;
                setPreReleaseGameVersions([]);
                setOrderedGameVersions([]);
            });

        return () => {
            isCancelled = true;
        };
    }, [isDownloadOpen, orderedGameVersions.length, preReleaseGameVersions.length]);

    useEffect(() => {
        if (!isDownloadOpen) return;
        if (hasStableBuilds) {
            setShowExperimental(false);
        } else {
            setShowExperimental(true);
        }
    }, [isDownloadOpen, hasStableBuilds]);

    useEffect(() => {
        if (!isHistoryOpen) return;
        if (hasStableBuilds) {
            setShowExperimental(false);
        } else {
            setShowExperimental(true);
        }
    }, [isHistoryOpen, hasStableBuilds]);

    useEffect(() => {
        if (prevPathnameRef.current.includes('/wiki') && isWikiRoute && prevPathnameRef.current !== location.pathname) {
            window.scrollTo(0, scrollPosRef.current);
        }
        prevPathnameRef.current = location.pathname;
    }, [location.pathname, isWikiRoute]);

    useEffect(() => {
        if (wikiData && !wikiLoading) {
            setDisplayWikiData(wikiData);
            setDisplaySlug(wikiPageSlug);
            setLockedHeight(undefined);
        }
    }, [wikiData, wikiLoading, wikiPageSlug]);

    useEffect(() => {
        if (!project) return;
        if (location.pathname.endsWith('/download')) {
            setIsDownloadOpen(true);
            setIsHistoryOpen(false);
        } else if (location.pathname.endsWith('/changelog')) {
            setIsHistoryOpen(true);
            setIsDownloadOpen(false);
        } else {
            setIsDownloadOpen(false);
            setIsHistoryOpen(false);
        }
    }, [location.pathname, project]);

    useEffect(() => {
        if (!isGalleryRoute) return;
        setGalleryIndex(openGalleryIndexFromHash());
    }, [isGalleryRoute, location.hash, project?.galleryImages]);

    useEffect(() => {
        if (!isGalleryRoute || galleryImages.length <= 1) return;

        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'ArrowLeft') {
                event.preventDefault();
                setGalleryIndex((prev) => (prev - 1 + galleryImages.length) % galleryImages.length);
            } else if (event.key === 'ArrowRight') {
                event.preventDefault();
                setGalleryIndex((prev) => (prev + 1) % galleryImages.length);
            } else if (event.key === 'Escape') {
                event.preventDefault();
                navigate(projectUrl);
            }
        };

        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, [isGalleryRoute, galleryImages.length, navigate, projectUrl]);

    useEffect(() => {
        if (project && id) {
            const currentRouteId = SiteRoutes.extractId(id);
            const loadedProjectId = SiteRoutes.extractId(project.id);

            if (loadedProjectId !== currentRouteId) {
                return;
            }

            const canonicalPath = SiteRoutes.project(project);
            const currentPrefixMatch = location.pathname.match(/^\/(project|mod|modpack|world)\/[^/]+/i);

            if (currentPrefixMatch) {
                const currentBase = currentPrefixMatch[0];
                if (currentBase !== canonicalPath) {
                    const newPath = location.pathname.replace(currentBase, canonicalPath);
                    navigate(
                        { pathname: newPath, search: location.search, hash: location.hash },
                        { replace: true }
                    );
                }
            }
        }
    }, [project, id, location.pathname, location.search, location.hash, navigate]);

    const getDependencyId = (dep: any) => {
        if (typeof dep === 'string') return dep;
        if (dep && typeof dep === 'object') {
            return dep.modId || dep.projectId || dep.id || '';
        }
        return '';
    };
    const showDownloadError = useCallback((error: unknown, fallback: string) => {
        setStatusModal({
            type: 'error',
            title: 'Download Unavailable',
            message: extractApiErrorMessage(error, fallback)
        });
    }, []);

    const sanitizeDownloadName = (input: string) => input.replace(/[^a-zA-Z0-9.-]/g, '_');

    const extractFileNameFromUrl = (rawUrl?: string) => {
        if (!rawUrl) return '';
        const withoutQuery = rawUrl.split('?')[0];
        const lastSegment = withoutQuery.substring(withoutQuery.lastIndexOf('/') + 1);
        const decoded = decodeURIComponent(lastSegment);
        return decoded.length > 37 && decoded.charAt(36) === '-' ? decoded.substring(37) : decoded;
    };

    const resolveDownloadedFileName = (projectData: any, versionNumber: string, gameVersion: string, isBundle: boolean) => {
        if (!projectData) return '';
        if (isBundle) return `${sanitizeDownloadName(projectData.title)}-UNZIP-ME.zip`;
        if (projectData.classification === 'MODPACK') return `${sanitizeDownloadName(projectData.title)}-${versionNumber}.zip`;

        const matchedVersion = (projectData.versions || []).find((v: any) => {
            if (v.versionNumber !== versionNumber) return false;
            if (!gameVersion) return true;
            const gameVersions = Array.isArray(v.gameVersions) ? v.gameVersions : [];
            return gameVersions.includes(gameVersion);
        });

        return extractFileNameFromUrl(matchedVersion?.fileUrl);
    };

    const finishVersionDownload = async (versionNumber: string, gameVersion: string, selectedDeps: string[], channel: DownloadChannel = 'RELEASE') => {
        if (!project) return;
        const currentProject = project;
        const isBundle = selectedDeps.length > 0;
        const depsQuery = isBundle ? `?deps=${selectedDeps.map(encodeURIComponent).join(',')}` : '';
        const params = new URLSearchParams();
        if (gameVersion) params.set('gameVersion', gameVersion);
        const queryPrefix = isBundle ? '&' : '?';
        const resolverQuery = params.toString() ? `${queryPrefix}${params.toString()}` : '';

        const endpoint = isBundle
            ? `/projects/${currentProject.id}/versions/${versionNumber}/download-bundle-url${depsQuery}${resolverQuery}`
            : `/projects/${currentProject.id}/versions/${versionNumber}/download-url${params.toString() ? `?${params.toString()}` : ''}`;

        const res = await api.get(endpoint);

        if (res.data && res.data.downloadUrl) {
            let downloadUrl = res.data.downloadUrl;
            if (!downloadUrl.startsWith('http')) {
                const baseUrl = (api.defaults.baseURL || '').replace(/\/$/, '');
                downloadUrl = baseUrl + downloadUrl;
            }
            setLastDownloadedFileName(resolveDownloadedFileName(currentProject, versionNumber, gameVersion, isBundle));

            window.open(downloadUrl, '_blank');
            setShowDownloadFx(true);
            if (downloadFxTimeoutRef.current) window.clearTimeout(downloadFxTimeoutRef.current);
            downloadFxTimeoutRef.current = window.setTimeout(() => setShowDownloadFx(false), 900);

            if (!downloadedSessionIds.has(currentProject.id)) {
                setProject(prev => prev ? { ...prev, downloadCount: (prev.downloadCount || 0) + 1 } : null);
                onDownload(currentProject.id);
            }

            setIsDownloadOpen(false);
            setIsHistoryOpen(false);
            setIsDepModalOpen(false);
            setPendingDownload(null);
            setLastDownloadWasBundle(isBundle || currentProject.classification === 'MODPACK');
            setLastDownloadChannel(channel);

            if (localStorage.getItem('hideInstallInstructions') !== 'true') {
                setShowPostDownloadModal(true);
            }

            if (location.pathname.endsWith('/download')) navigate(SiteRoutes.project(currentProject), { replace: true });
            return;
        }

        throw new Error('The server did not return a usable download link for this file.');
    };

    const handleDownloadClick = async (url: string, versionNumber: string, gameVersion: string, deps: any[], channel: string) => {
        try {
            const downloadChannel = normalizeDownloadChannel(channel);

            if (!versionNumber) {
                const baseUrl = (api.defaults.baseURL || '').replace(/\/$/, '');
                const targetUrl = baseUrl + '/files/download/' + encodeURI(url);
                setLastDownloadedFileName(extractFileNameFromUrl(url));
                setLastDownloadChannel(downloadChannel);
                window.open(targetUrl, '_blank');
                setShowDownloadFx(true);
                if (downloadFxTimeoutRef.current) window.clearTimeout(downloadFxTimeoutRef.current);
                downloadFxTimeoutRef.current = window.setTimeout(() => setShowDownloadFx(false), 900);

                if (project && !downloadedSessionIds.has(project.id)) {
                    setProject(prev => prev ? { ...prev, downloadCount: (prev.downloadCount || 0) + 1 } : null);
                    onDownload(project.id);
                }
                setIsDownloadOpen(false);
                setIsHistoryOpen(false);
                setLastDownloadWasBundle(false);

                if (localStorage.getItem('hideInstallInstructions') !== 'true') {
                    setShowPostDownloadModal(true);
                }
                if (location.pathname.endsWith('/download')) navigate(SiteRoutes.project(project), { replace: true });
                return;
            }

            const selectableDeps = (deps || []).filter(dep => getDependencyId(dep) && !dep?.isEmbedded);
            if (selectableDeps.length > 0) {
                setPendingDownload({ versionNumber, gameVersion, dependencies: selectableDeps, channel: downloadChannel });
                setIsDepModalOpen(true);
                return;
            }

            await finishVersionDownload(versionNumber, gameVersion, [], downloadChannel);
        } catch (e: unknown) {
            showDownloadError(e, 'We could not prepare this download.');
        }
    };

    const versionsByGame = useMemo(() => {
        return (project?.versions || []).reduce((acc: any, v: any) => {
            const keys = Array.isArray(v.gameVersions) && v.gameVersions.length > 0 ? v.gameVersions : ['Any'];
            keys.forEach((key: string) => {
                if (!acc[key]) acc[key] = [];
                acc[key].push(v);
            });
            return acc;
        }, {});
    }, [project?.versions]);

    const sortedHistory = useMemo(() => {
        return [...(project?.versions || [])].sort((a: any, b: any) => new Date(b.releaseDate).getTime() - new Date(a.releaseDate).getTime());
    }, [project?.versions]);

    const toggleExperimental = useCallback(() => {
        setShowExperimental((prev) => !prev);
    }, []);

    if (isNotFound) return <NotFound />;
    if (loading || !project) return <div className={`min-h-screen ${theme.colors.bgBase} flex items-center justify-center`}><Spinner /></div>;

    const canEdit = project.canEdit ?? Boolean(
        currentUser && (
            currentUser.id === project.authorId ||
            project.teamMembers?.some(m => m.userId === currentUser.id)
        )
    );

    const meta = generateProjectMeta(project);
    const breadcrumbSchema = generateBreadcrumbSchema([...getBreadcrumbsForClassification(project.classification || 'PLUGIN'), { name: project.title, url: projectUrl }]);

    const links = [
        project.repositoryUrl && { type: 'SOURCE', url: project.repositoryUrl, icon: Github, label: 'Source Code', colorClass: `${theme.colors.textSecondary} ${theme.colors.bgSurfaceHover} ${theme.colors.border}` },
        project.links?.DISCORD && { type: 'DISCORD', url: project.links.DISCORD, icon: DiscordIcon, label: 'Discord', colorClass: 'text-[#5865F2] hover:bg-[#5865F2]/20 border-[#5865F2]/20' },
        project.links?.WEBSITE && { type: 'WEBSITE', url: project.links.WEBSITE, icon: Globe, label: 'Website', colorClass: 'text-blue-500 dark:text-blue-400 hover:bg-blue-500/20 border-blue-500/20' }
    ].filter(Boolean) as any[];

    return (
        <>
            {showDownloadFx && typeof document !== 'undefined' && createPortal(
                <div className="download-screen-fx" aria-hidden="true">
                    <div className="download-screen-fx-glow" />
                    <div className="download-screen-fx-stage">
                        <div className="download-screen-fx-beam" />
                        <div className="download-screen-fx-arrow-shaft" />
                        <div className="download-screen-fx-arrow-head" />
                        <div className="download-screen-fx-packet download-screen-fx-packet-a" />
                        <div className="download-screen-fx-packet download-screen-fx-packet-b" />
                        <div className="download-screen-fx-packet download-screen-fx-packet-c" />
                        <div className="download-screen-fx-tray" />
                        <div className="download-screen-fx-impact" />
                        <div className="download-screen-fx-ring download-screen-fx-ring-a" />
                        <div className="download-screen-fx-ring download-screen-fx-ring-b" />
                    </div>
                </div>,
                document.body
            )}
            <Helmet>
                <title>{meta?.title}</title>
                <meta name="description" content={meta?.description} />
                <script type="application/ld+json">{JSON.stringify(breadcrumbSchema)}</script>
            </Helmet>

            {statusModal && <StatusModal {...statusModal} onClose={() => setStatusModal(null)} />}
            <Suspense fallback={null}>
                {isShareOpen && <ShareModal isOpen={isShareOpen} onClose={() => setIsShareOpen(false)} url={window.location.href} title={project.title} author={project.author} />}
                {isReportOpen && <ReportModal isOpen={isReportOpen} onClose={() => setIsReportOpen(false)} targetId={project.id} targetType="PROJECT" targetTitle={project.title} />}
                {showPostDownloadModal && <PostDownloadModal isOpen={showPostDownloadModal} onClose={() => setShowPostDownloadModal(false)} classification={project.classification!} title={project.title} channel={lastDownloadChannel} isBundle={lastDownloadWasBundle} fileName={lastDownloadedFileName} />}

                {isHistoryOpen && (
                    <HistoryModal
                        show={isHistoryOpen}
                        onClose={() => navigate(projectUrl)}
                        history={sortedHistory}
                        showExperimental={showExperimental}
                        onToggleExperimental={toggleExperimental}
                        onDownload={handleDownloadClick}
                        hasStableVersions={hasStableBuilds}
                    />
                )}
                {isDownloadOpen && (
                    <DownloadModal
                        show={isDownloadOpen}
                        onClose={() => navigate(projectUrl)}
                        versionsByGame={versionsByGame}
                        preReleaseGameVersions={preReleaseGameVersions}
                        orderedGameVersions={orderedGameVersions}
                        onDownload={handleDownloadClick}
                        showExperimental={showExperimental}
                        onToggleExperimental={toggleExperimental}
                        onViewHistory={() => navigate(projectUrl + '/changelog')}
                    />
                )}
                {isDepModalOpen && pendingDownload && (
                    <DependencyModal
                        dependencies={pendingDownload.dependencies}
                        onClose={() => {
                            setIsDepModalOpen(false);
                            setPendingDownload(null);
                        }}
                        onDownloadBundle={(selectedDeps) => {
                            finishVersionDownload(pendingDownload.versionNumber, pendingDownload.gameVersion, selectedDeps, pendingDownload.channel).catch(() => {
                                showDownloadError(new Error('The dependency bundle link could not be generated.'), 'We could not prepare that bundle download.');
                            });
                        }}
                        onDownloadProjectOnly={() => {
                            finishVersionDownload(pendingDownload.versionNumber, pendingDownload.gameVersion, [], pendingDownload.channel).catch(() => {
                                showDownloadError(new Error('The project-only download link could not be generated.'), 'We could not prepare that download.');
                            });
                        }}
                    />
                )}
            </Suspense>

            <ProjectLayout
                bannerUrl={project.bannerUrl}
                iconUrl={project.imageUrl}
                onBack={() => navigate(browseBackTarget)}
                headerActions={
                    <HeaderActions
                        project={project} currentUser={currentUser} isLiked={isLiked(project.id)} isFollowing={isFollowing} canEdit={Boolean(canEdit)} projectUrl={projectUrl}
                        onToggleFavorite={() => onToggleFavorite(project.id)} onShare={() => setIsShareOpen(true)} onReport={() => setIsReportOpen(true)} onFollowToggle={handleFollowToggle}
                    />
                }
                headerContent={<HeaderContent project={project} currentUser={currentUser} isLiked={isLiked(project.id)} isFollowing={isFollowing} onFollowToggle={handleFollowToggle} canEdit={Boolean(canEdit)} projectUrl={projectUrl} onToggleFavorite={() => {}} onShare={() => {}} onReport={() => {}} />}
                actionBar={<ActionBar project={project} projectUrl={projectUrl} latestDependencies={latestDependencies} depMeta={depMeta} links={links} isMobile={isMobile} commentsRef={commentsRef} />}
                sidebarContent={
                    isWikiRoute ? (
                        <>
                            <Suspense fallback={null}>
                                <WikiSidebar tree={displayWikiData?.mod?.pages || []} projectUrl={projectUrl} currentSlug={displaySlug} indexSlug={displayWikiData?.mod?.index?.slug} />
                            </Suspense>
                            <div className="mt-4">
                                <Link to={projectUrl} className={`block text-sm font-bold ${theme.colors.accent} hover:underline flex items-center gap-2`}>
                                    <ChevronLeft className="w-4 h-4" /> Back to Project
                                </Link>
                            </div>
                        </>
                    ) : (
                        <Sidebar project={project} navigate={navigate} dependencies={latestDependencies} depMeta={depMeta} contributors={contributors} orgMembers={orgMembers} author={authorProfile} />
                    )
                }
                mainContent={
                    isWikiRoute ? (
                        <Suspense fallback={<div className="flex justify-center p-12"><Spinner /></div>}>
                            <Wiki wikiLoading={wikiLoading} wikiError={wikiError} displayWikiData={displayWikiData} displaySlug={displaySlug} project={project} wikiContentRef={wikiContentRef} lockedHeight={lockedHeight} />
                        </Suspense>
                    ) : (
                        <ViewDetails project={project} currentUser={currentUser} canEdit={Boolean(canEdit)} commentsRef={commentsRef} setProject={setProject} setStatusModal={setStatusModal} onRefresh={onRefresh} />
                    )
                }
            />
            {isGalleryRoute && typeof document !== 'undefined' && createPortal(
                <div className={theme.components.modalOverlay} onClick={() => navigate(projectUrl)}>
                    <div
                        className={`${theme.components.modalContent} w-full max-w-6xl h-[90dvh]`}
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className={theme.components.modalHeader}>
                            <h2 className={`text-lg font-black ${theme.colors.textPrimary}`}>Gallery</h2>
                            <button
                                type="button"
                                onClick={() => navigate(projectUrl)}
                                className={`px-3 py-1.5 rounded-lg border ${theme.colors.border} ${theme.colors.textSecondary} hover:${theme.colors.textPrimary} ${theme.colors.bgSurfaceHover} transition-colors text-sm font-bold`}
                            >
                                Close
                            </button>
                        </div>
                        <div className={`${theme.components.modalBody} !p-0`}>
                            {galleryImages.length > 0 ? (
                                <div className="relative h-[72dvh] bg-black">
                                    <img
                                        src={galleryImages[galleryIndex]}
                                        alt={`${project.title} gallery image ${galleryIndex + 1}`}
                                        className="w-full h-full object-contain"
                                        loading="eager"
                                    />
                                    {galleryImages.length > 1 && (
                                        <>
                                            <button
                                                type="button"
                                                aria-label="Previous image"
                                                onClick={() => setGalleryIndex((prev) => (prev - 1 + galleryImages.length) % galleryImages.length)}
                                                className="absolute left-3 top-1/2 -translate-y-1/2 p-2 rounded-full bg-black/55 hover:bg-black/75 text-white transition-colors"
                                            >
                                                <ChevronLeft className="w-6 h-6" />
                                            </button>
                                            <button
                                                type="button"
                                                aria-label="Next image"
                                                onClick={() => setGalleryIndex((prev) => (prev + 1) % galleryImages.length)}
                                                className="absolute right-3 top-1/2 -translate-y-1/2 p-2 rounded-full bg-black/55 hover:bg-black/75 text-white transition-colors"
                                            >
                                                <ChevronRight className="w-6 h-6" />
                                            </button>
                                        </>
                                    )}
                                    <div className="absolute bottom-3 left-1/2 -translate-x-1/2 px-3 py-1 rounded-full bg-black/60 text-white text-sm font-semibold">
                                        {galleryIndex + 1} / {galleryImages.length}
                                    </div>
                                </div>
                            ) : (
                                <div className={`p-10 text-center ${theme.colors.textMuted}`}>No images in this gallery.</div>
                            )}
                        </div>
                    </div>
                </div>,
                document.body
            )}
        </>
    );
};
