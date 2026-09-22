import { useRef } from 'react';
import { Link } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';
import type { Project } from '@/types';
import { MarkdownRenderer } from '@/components/ui/MarkdownRenderer';
import { MobileProvider, useMobile } from '@/context/MobileContext';
import { ProjectMetaSections } from './ProjectMetaSections';
import { ProjectLayout } from './ProjectLayout';
import { HeaderActions, HeaderContent } from './Header';
import { ActionBar } from './ActionBar';
import { Sidebar } from './Sidebar';
import { Wiki, WikiSidebar, WikiMobileNavigation } from './HMWiki';
import { PROJECT_LOADING_DATA, PROJECT_LOADING_PROSE, loadingNoop } from './projectLoadingData';

const wikiTree = [{ id: 'index', title: 'Getting started', slug: 'index' }, { id: 'configuration', title: 'Configuration', slug: 'configuration' }, { id: 'features', title: 'Features', slug: 'features' }];

/** Requires a router, but no project hooks, requests, or authenticated user. */
type ProjectPageSkeletonProps = { project?: Project | null; wiki?: boolean };
export function ProjectPageSkeleton(props: ProjectPageSkeletonProps) {
    return <MobileProvider><ProjectPageLoadingContent {...props} /></MobileProvider>;
}

function ProjectPageLoadingContent({ project: summary, wiki = false }: ProjectPageSkeletonProps) {
    const { isMobile } = useMobile();
    const project = { ...PROJECT_LOADING_DATA, ...summary };
    const commentsRef = useRef<HTMLDivElement>(null);
    const header = { project, currentUser: null, isLiked: false, isFollowing: false, canEdit: false,
        projectUrl: '/mod/loading-project', onToggleFavorite: loadingNoop, onShare: loadingNoop,
        onReport: loadingNoop, onFollowToggle: loadingNoop };
    const wikiNavigation = { tree: wikiTree, projectUrl: header.projectUrl, currentSlug: 'index', indexSlug: 'index' };
    return <ProjectLayout loading bannerUrl={project.bannerUrl} iconUrl={project.imageUrl} onBack={loadingNoop}
        headerContent={<HeaderContent {...header} />} headerActions={<HeaderActions {...header} />}
        actionBar={<ActionBar project={project} projectUrl={header.projectUrl} links={[]} commentsRef={commentsRef} />}
        sidebarContent={wiki ? <><WikiSidebar {...wikiNavigation} /><div className="mt-4">
            <Link to={header.projectUrl} className="block text-sm font-bold text-modtale-accent hover:underline flex items-center gap-2"><ChevronLeft className="w-4 h-4" /> Back to Project</Link>
        </div></> : <Sidebar project={project} showMetaSections={!isMobile} depMeta={{}} contributors={[]} orgMembers={[]} author={null} orderedGameVersions={['2026.01.17']} />}
        mainContent={wiki ? <><WikiMobileNavigation {...wikiNavigation} /><Wiki wikiLoading={false} wikiError={false}
            wikiData={{ mod: { name: 'Getting started', index: { slug: 'index' } }, content: { title: 'Getting started', content: PROJECT_LOADING_PROSE } }} mod={project} /></> :
            <><div className="prose dark:prose-invert prose-lg max-w-none prose-code:before:hidden prose-code:after:hidden"><MarkdownRenderer content={PROJECT_LOADING_PROSE} deferRich fastOnly /></div>
            {isMobile && <div className="mt-8 pt-8 border-t border-slate-200 dark:border-white/5"><ProjectMetaSections project={project} depMeta={{}} orderedGameVersions={['2026.01.17']} /></div>}</>}
    />;
}
