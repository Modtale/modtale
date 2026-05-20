import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useLocation, Link } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import { ChevronLeft, Github, Globe } from 'lucide-react';

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
import { Wiki } from '../tabs/Wiki';
import { useHMWiki, WikiSidebar } from '../components/HMWiki';

import { ProjectLayout } from '../components/ProjectLayout';
import { Spinner } from '@/components/ui/Spinner';
import NotFound from '@/components/ui/error/NotFound';
import { StatusModal } from '@/components/ui/StatusModal';
import { ShareModal } from '../components/dialogs/ShareModal';
import { ReportModal } from '../components/dialogs/ReportModal';
import { PostDownloadModal } from '../components/dialogs/PostDownloadModal';
import { HistoryModal } from '../components/dialogs/HistoryModal';
import { DownloadModal } from '../components/dialogs/DownloadModal';
import { api } from '@/utils/api';

interface ProjectDetailViewProps {
    currentUser: User | null;
    isLiked: (id: string) => boolean;
    onToggleFavorite: (id: string) => void;
    onDownload: (id: string) => void;
    downloadedSessionIds: Set<string>;
    onRefresh: () => Promise<void>;
}

export const ProjectDetails: React.FC<ProjectDetailViewProps> = ({
                                                                     currentUser, isLiked, onToggleFavorite, onDownload, downloadedSessionIds, onRefresh
                                                                 }) => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const location = useLocation();
    const { isMobile } = useMobile();
    const { initialData } = useSSRData();

    const { project, setProject, loading, isNotFound, authorProfile, orgMembers, contributors, depMeta, latestDependencies, isFollowing, handleFollowToggle } = useProjectDetail(id, initialData, currentUser);

    const [statusModal, setStatusModal] = useState<any>(null);
    const [isShareOpen, setIsShareOpen] = useState(false);
    const [isReportOpen, setIsReportOpen] = useState(false);

    const [isHistoryOpen, setIsHistoryOpen] = useState(false);
    const [isDownloadOpen, setIsDownloadOpen] = useState(false);
    const [showExperimental, setShowExperimental] = useState(false);

    const [showPostDownloadModal, setShowPostDownloadModal] = useState(false);
    const [lastDownloadWasBundle, setLastDownloadWasBundle] = useState(false);
    const commentsRef = useRef<HTMLDivElement>(null);

    const isWikiRoute = location.pathname.includes('/wiki');
    const wikiMatch = location.pathname.match(/\/wiki\/?(.*)/);
    const wikiPageSlug = wikiMatch?.[1];

    const { data: wikiData, loading: wikiLoading, error: wikiError } = useHMWiki(project?.hmWikiSlug, wikiPageSlug, isWikiRoute && !!project?.hmWikiEnabled);
    const [displayWikiData, setDisplayWikiData] = useState(wikiData);
    const [displaySlug, setDisplaySlug] = useState(wikiPageSlug);
    const wikiContentRef = useRef<HTMLDivElement>(null);
    const [lockedHeight, setLockedHeight] = useState<number | undefined>(undefined);

    const prevPathnameRef = useRef(location.pathname);
    const scrollPosRef = useRef(0);

    useEffect(() => {
        const handleScroll = () => { scrollPosRef.current = window.scrollY; };
        window.addEventListener('scroll', handleScroll, { passive: true });
        return () => window.removeEventListener('scroll', handleScroll);
    }, []);

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

    if (isNotFound) return <NotFound />;
    if (loading || !project) return <div className={`min-h-screen ${theme.colors.bgBase} flex items-center justify-center`}><Spinner /></div>;

    const canEdit = project.canEdit ?? (currentUser && (currentUser.username === project.author || project.teamMembers?.some(m => m.userId === currentUser.id)));
    const projectUrl = SiteRoutes.project(project);

    const handleDownloadClick = async (url: string, versionNumber: string, deps: any[], channel: string) => {
        try {
            const isBundle = project.classification === 'MODPACK';
            const endpoint = isBundle
                ? '/projects/' + project.id + '/versions/' + versionNumber + '/download-bundle-url'
                : '/projects/' + project.id + '/versions/' + versionNumber + '/download-url';
            
            const res = await api.get(endpoint);
            if (res.data?.downloadUrl) {
                onDownload(project.id);
                setLastDownloadWasBundle(isBundle);
                setIsDownloadOpen(false);
                setIsHistoryOpen(false);
                navigate(SiteRoutes.project(project), { replace: true });
                setShowPostDownloadModal(true);
                window.location.href = res.data.downloadUrl;
            }
        } catch (e) {
            setStatusModal({ type: 'error', title: 'Download Failed', msg: 'Could not generate download link. Please try again later.' });
        }
    };

    const versionsByGame = (project.versions || []).reduce((acc: any, v: any) => {
        const key = v.gameVersions?.[0] || 'Any';
        if (!acc[key]) acc[key] = [];
        acc[key].push(v);
        return acc;
    }, {});

    const sortedHistory = [...(project.versions || [])].sort((a: any, b: any) => new Date(b.releaseDate).getTime() - new Date(a.releaseDate).getTime());

    const meta = generateProjectMeta(project);
    const breadcrumbSchema = generateBreadcrumbSchema([...getBreadcrumbsForClassification(project.classification || 'PLUGIN'), { name: project.title, url: projectUrl }]);

    const links = [
        project.repositoryUrl && { type: 'SOURCE', url: project.repositoryUrl, icon: Github, label: 'Source Code', colorClass: `${theme.colors.textSecondary} ${theme.colors.bgSurfaceHover} ${theme.colors.border}` },
        project.links?.DISCORD && { type: 'DISCORD', url: project.links.DISCORD, icon: DiscordIcon, label: 'Discord', colorClass: 'text-[#5865F2] hover:bg-[#5865F2]/20 border-[#5865F2]/20' },
        project.links?.WEBSITE && { type: 'WEBSITE', url: project.links.WEBSITE, icon: Globe, label: 'Website', colorClass: 'text-blue-500 dark:text-blue-400 hover:bg-blue-500/20 border-blue-500/20' }
    ].filter(Boolean) as any[];

    return (
        <>
            <Helmet>
                <title>{meta?.title}</title>
                <meta name="description" content={meta?.description} />
                <script type="application/ld+json">{JSON.stringify(breadcrumbSchema)}</script>
            </Helmet>

            {statusModal && <StatusModal {...statusModal} onClose={() => setStatusModal(null)} />}
            <ShareModal isOpen={isShareOpen} onClose={() => setIsShareOpen(false)} url={window.location.href} title={project.title} author={project.author} />
            <ReportModal isOpen={isReportOpen} onClose={() => setIsReportOpen(false)} targetId={project.id} targetType="PROJECT" targetTitle={project.title} />
            <PostDownloadModal isOpen={showPostDownloadModal} onClose={() => setShowPostDownloadModal(false)} classification={project.classification!} title={project.title} isBundle={lastDownloadWasBundle} />
            
            <HistoryModal 
                show={isHistoryOpen} 
                onClose={() => navigate(projectUrl)} 
                history={sortedHistory} 
                showExperimental={showExperimental} 
                onToggleExperimental={() => setShowExperimental(!showExperimental)} 
                onDownload={handleDownloadClick} 
            />
            <DownloadModal 
                show={isDownloadOpen} 
                onClose={() => navigate(projectUrl)} 
                versionsByGame={versionsByGame} 
                onDownload={handleDownloadClick} 
                showExperimental={showExperimental} 
                onToggleExperimental={() => setShowExperimental(!showExperimental)} 
                onViewHistory={() => navigate(projectUrl + '/changelog')} 
            />

            <ProjectLayout
                bannerUrl={project.bannerUrl}
                iconUrl={project.imageUrl}
                onBack={() => navigate(SiteRoutes.home())}
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
                            <WikiSidebar tree={displayWikiData?.mod?.pages || []} projectUrl={projectUrl} currentSlug={displaySlug} indexSlug={displayWikiData?.mod?.index?.slug} />
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
                        <Wiki wikiLoading={wikiLoading} wikiError={wikiError} displayWikiData={displayWikiData} displaySlug={displaySlug} project={project} wikiContentRef={wikiContentRef} lockedHeight={lockedHeight} />
                    ) : (
                        <ViewDetails project={project} currentUser={currentUser} canEdit={Boolean(canEdit)} commentsRef={commentsRef} setProject={setProject} setStatusModal={setStatusModal} onRefresh={onRefresh} />
                    )
                }
            />
        </>
    );
};