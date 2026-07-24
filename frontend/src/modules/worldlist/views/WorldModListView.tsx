import React, { useEffect, useMemo, useState } from 'react';
import { Helmet } from 'react-helmet-async';
import { Link, useParams } from 'react-router-dom';
import {
    Download,
    Loader2,
    MonitorDown,
    PackagePlus,
} from 'lucide-react';
import { Spinner } from '@/components/ui/Spinner';
import NotFound from '@/components/ui/error/NotFound';
import { SiteRoutes } from '@/utils/routes';
import { ProjectCard } from '@/modules/project/components/ProjectCard';
import {
    type WorldModList,
    type WorldModListItem,
    worldListClient,
    worldListDownloadUrl,
} from '@/modules/worldlist/api/worldListClient';
import { openLauncherListInstallOrFallback } from '@/modules/launcher/utils/launcherProtocol';
import type { Project } from '@/types';

const pageShell = 'mx-auto flex w-full max-w-[112rem] flex-col px-6 py-10 sm:px-12 md:px-16 lg:px-20 lg:py-14 xl:px-28';
const buttonBase = 'inline-flex h-10 items-center justify-center gap-2 rounded-md px-3 text-sm font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 focus:ring-offset-white disabled:pointer-events-none disabled:opacity-60 dark:focus:ring-offset-modtale-dark';
const primaryButton = `${buttonBase} bg-slate-950 text-white hover:bg-slate-800 dark:bg-white dark:text-slate-950 dark:hover:bg-slate-200`;
const secondaryButton = `${buttonBase} border border-slate-200 text-slate-800 hover:bg-slate-50 dark:border-white/10 dark:text-slate-100 dark:hover:bg-white/10`;
const noopToggleFavorite = () => {};
const PROJECT_CLASSIFICATIONS: Project['classification'][] = ['PLUGIN', 'DATA', 'ART', 'SAVE', 'MODPACK'];

const formatDate = (value?: string) => {
    if (!value) return 'Unknown';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return 'Unknown';
    return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' }).format(date);
};

const projectClassification = (value?: string): Project['classification'] => (
    PROJECT_CLASSIFICATIONS.includes(value as Project['classification'])
        ? value as Project['classification']
        : 'PLUGIN'
);

const projectDescription = (item: WorldModListItem) => {
    if (item.description?.trim()) return item.description.trim();
    return 'Shared in this world mod list.';
};

const worldListItemToProject = (item: WorldModListItem, list: WorldModList): Project => {
    const id = item.projectId || item.id || item.externalId || item.modId || item.title || 'world-list-item';

    return {
        id,
        slug: item.slug || undefined,
        title: item.title || 'Untitled mod',
        description: projectDescription(item),
        authorId: item.authorId || '',
        author: item.author || '',
        imageUrl: item.icon || '',
        bannerUrl: item.bannerUrl || undefined,
        classification: projectClassification(item.classification),
        downloadCount: item.downloadCount || 0,
        favoriteCount: item.favoriteCount || 0,
        updatedAt: item.updatedAt || list.createdAt,
        createdAt: list.createdAt,
    };
};

const projectPathForItem = (project: Project, item: WorldModListItem) => {
    if (!item.projectId && !item.slug) return undefined;
    return SiteRoutes.project(project);
};

const WorldListProjectCard = ({ item, list, priority }: { item: WorldModListItem; list: WorldModList; priority: boolean }) => {
    const project = worldListItemToProject(item, list);
    const path = projectPathForItem(project, item);

    return (
        <ProjectCard
            project={project}
            path={path}
            isFavorite={false}
            onToggleFavorite={noopToggleFavorite}
            isLoggedIn={false}
            priority={priority}
            viewStyle="list"
            isVisible={true}
            disableNavigation={!path}
            versionLabel={item.versionNumber ? `v${item.versionNumber}` : undefined}
        />
    );
};

export const WorldModListView: React.FC = () => {
    const { id = '' } = useParams();
    const [list, setList] = useState<WorldModList | null>(null);
    const [notFound, setNotFound] = useState(false);
    const [loading, setLoading] = useState(true);
    const [installing, setInstalling] = useState(false);

    useEffect(() => {
        let cancelled = false;

        const load = async () => {
            setLoading(true);
            setNotFound(false);

            try {
                const loaded = await worldListClient.get(id);
                if (!cancelled) setList(loaded);
            } catch {
                if (!cancelled) {
                    setNotFound(true);
                    setList(null);
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        };

        if (id) {
            load();
        } else {
            setLoading(false);
            setNotFound(true);
        }

        return () => {
            cancelled = true;
        };
    }, [id]);

    const shareUrl = useMemo(() => {
        if (list?.shareUrl) return list.shareUrl;
        if (typeof window !== 'undefined') return window.location.href;
        return '';
    }, [list?.shareUrl]);

    const downloadUrl = list ? worldListDownloadUrl(list.id) : '';

    const installWithLauncher = () => {
        if (!list) return;
        setInstalling(true);
        openLauncherListInstallOrFallback(
            { listId: list.id, shareUrl },
            () => {
                setInstalling(false);
                if (downloadUrl) window.location.assign(downloadUrl);
            }
        );
        window.setTimeout(() => setInstalling(false), 2600);
    };

    if (loading) {
        return (
            <main className={pageShell}>
                <div className="flex min-h-[360px] items-center justify-center">
                    <Spinner />
                </div>
            </main>
        );
    }

    if (notFound || !list) {
        return <NotFound />;
    }

    return (
        <main className={pageShell}>
            <Helmet>
                <title>{list.title} | Modtale Shared List</title>
                <meta name="description" content={`${list.title} is a shared Modtale list with ${list.modCount} mod${list.modCount === 1 ? '' : 's'}.`} />
            </Helmet>

            <section className="border-b border-slate-200 pb-8 dark:border-white/10">
                <div className="flex flex-col gap-5 md:flex-row md:items-start md:justify-between">
                    <div className="min-w-0">
                        <p className="text-2xl font-bold text-slate-950 dark:text-white sm:text-3xl">
                            Shared mod list
                        </p>
                        <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-slate-500 dark:text-slate-400">
                            {list.gameVersion && <span>Hytale {list.gameVersion}</span>}
                            {list.ownerUsername && <span>Shared by {list.ownerUsername}</span>}
                            <span>Expires {formatDate(list.expiresAt)}</span>
                            <span>{list.modCount} mod{list.modCount === 1 ? '' : 's'}</span>
                            <span>{list.viewCount} view{list.viewCount === 1 ? '' : 's'}</span>
                        </div>
                    </div>

                    <div className="flex flex-wrap gap-2 md:justify-end">
                        <a href={downloadUrl} className={secondaryButton}>
                            <Download className="h-4 w-4" aria-hidden="true" />
                            Download zip
                        </a>
                        <Link to={SiteRoutes.createModpackFromList(list.id)} className={secondaryButton}>
                            <PackagePlus className="h-4 w-4" aria-hidden="true" />
                            Make modpack
                        </Link>
                        <button type="button" className={primaryButton} onClick={installWithLauncher} disabled={installing}>
                            {installing ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <MonitorDown className="h-4 w-4" aria-hidden="true" />}
                            Install with launcher
                        </button>
                    </div>
                </div>
            </section>

            <section className="space-y-4 pt-6">
                {list.mods.map((item, index) => (
                    <WorldListProjectCard
                        key={item.id || `${item.title}-${item.versionNumber}`}
                        item={item}
                        list={list}
                        priority={index < 2}
                    />
                ))}
            </section>
        </main>
    );
};

export default WorldModListView;
