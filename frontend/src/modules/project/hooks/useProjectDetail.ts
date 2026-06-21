import { useState, useEffect, useRef, useMemo } from 'react';
import { projectClient } from '../api/projectClient';
import { SiteRoutes } from '@/utils/routes';
import { consumePrefetchedProject } from '@/utils/prefetch';
import type { Project, User } from '@/types';
import { mergeProjectVersionChangelogs, projectNeedsChangelogHydration } from '../utils/changelogHydration';

const consumeProjectBootstrap = async (routeKey: string) => {
    const prefetched = await consumePrefetchedProject(routeKey);
    if (prefetched && SiteRoutes.matchesProjectRoute(prefetched, routeKey)) {
        return prefetched;
    }

    if (typeof window === 'undefined' || !window.__MODTALE_PROJECT_BOOTSTRAP) return null;

    const bootstrap = window.__MODTALE_PROJECT_BOOTSTRAP;
    window.__MODTALE_PROJECT_BOOTSTRAP = undefined;

    try {
        const data = await bootstrap;
        if (data && SiteRoutes.matchesProjectRoute(data, routeKey)) {
            return data as Project;
        }
    } catch {
        return null;
    }

    return null;
};

const mergeProjectSlices = (incoming: Project, previous: Project | null) => {
    if (!previous || previous.id !== incoming.id) return incoming;

    return {
        ...incoming,
        versions: incoming.versions ?? previous.versions,
        galleryImages: incoming.galleryImages ?? previous.galleryImages,
        galleryImageCaptions: incoming.galleryImageCaptions ?? previous.galleryImageCaptions,
        projectRoles: incoming.projectRoles ?? previous.projectRoles,
        teamMembers: incoming.teamMembers ?? previous.teamMembers,
        teamInvites: incoming.teamInvites ?? previous.teamInvites,
        comments: incoming.comments ?? previous.comments
    };
};

interface UseProjectDetailOptions {
    backgroundRefresh?: boolean;
    hydrateChangelogs?: boolean;
    full?: boolean;
}

export const useProjectDetail = (
    rawId: string | undefined,
    initialData: Project | null,
    currentUser: User | null,
    options: UseProjectDetailOptions = {}
) => {
    const { backgroundRefresh = false, hydrateChangelogs = false, full = false } = options;
    const routeKey = rawId?.trim() || '';
    const initialProjectUnavailable = Boolean((initialData as any)?.__projectUnavailable);

    const isInitialDataValid = !initialProjectUnavailable && initialData && SiteRoutes.matchesProjectRoute(initialData, routeKey);

    const [project, setProject] = useState<Project | null>(isInitialDataValid ? initialData : null);
    const [loading, setLoading] = useState(!isInitialDataValid && !initialProjectUnavailable);
    const [isNotFound, setIsNotFound] = useState(initialProjectUnavailable);
    const [authorProfile, setAuthorProfile] = useState<User | null>(null);
    const [orgMembers, setOrgMembers] = useState<User[]>([]);
    const [contributors, setContributors] = useState<User[]>([]);
    const [depMeta, setDepMeta] = useState<Record<string, { icon: string, title: string, classification?: string, slug?: string }>>({});
    const [isFollowing, setIsFollowing] = useState(false);

    const analyticsFired = useRef(false);
    const fetchedDepMeta = useRef<Set<string>>(new Set());
    const fetchedTeamDataKey = useRef('');
    const fetchedChangelogKey = useRef('');
    const fetchedVersionsKey = useRef('');
    const fetchingVersionsKey = useRef('');
    const fetchedGalleryKey = useRef('');
    const fetchedProjectTeamKey = useRef('');
    const fetchedCommentsKey = useRef('');

    useEffect(() => {
        if (initialProjectUnavailable) {
            setProject(null);
            setLoading(false);
            setIsNotFound(true);
            setAuthorProfile(null);
            setOrgMembers([]);
            setContributors([]);
            return;
        }

        if (!isInitialDataValid) {
            setProject(null);
            setLoading(true);
            setIsNotFound(false);
            setAuthorProfile(null);
            setOrgMembers([]);
            setContributors([]);
            analyticsFired.current = false;
            fetchedTeamDataKey.current = '';
            fetchedChangelogKey.current = '';
            fetchedVersionsKey.current = '';
            fetchingVersionsKey.current = '';
            fetchedGalleryKey.current = '';
            fetchedProjectTeamKey.current = '';
            fetchedCommentsKey.current = '';
        }
    }, [routeKey, isInitialDataValid, initialProjectUnavailable]);

    useEffect(() => {
        if (initialProjectUnavailable) {
            setIsNotFound(true);
            setLoading(false);
            return;
        }

        if (!routeKey) {
            setIsNotFound(true);
            setLoading(false);
            return;
        }

        const projectMatchesRoute = project && SiteRoutes.matchesProjectRoute(project, routeKey);
        if (projectMatchesRoute && !backgroundRefresh) {
            setLoading(false);
            return;
        }

        let isMounted = true;

        const loadProject = async () => {
            try {
                if (projectMatchesRoute && backgroundRefresh) {
                    setLoading(false);
                }

                const bootstrapped = full ? null : await consumeProjectBootstrap(routeKey);
                const data = bootstrapped || (full ? await projectClient.getProjectFull(routeKey) : await projectClient.getProject(routeKey));
                if (isMounted) {
                    if (data.versions !== undefined) {
                        fetchedVersionsKey.current = `${data.id}:${routeKey || data.id}`;
                    }
                    setProject((previous) => mergeProjectSlices(data, previous));
                }
            } catch {
                if (isMounted && !projectMatchesRoute) setIsNotFound(true);
            } finally {
                if (isMounted) setLoading(false);
            }
        };

        loadProject();

        return () => { isMounted = false; };
    }, [routeKey, project?.id, backgroundRefresh, full]);

    useEffect(() => {
        if (full || !project?.id) return;

        const sectionKey = `${project.id}:${routeKey || project.id}`;
        if (project.versions !== undefined && fetchedVersionsKey.current === sectionKey) return;
        if (fetchingVersionsKey.current === sectionKey) return;

        let isMounted = true;
        fetchingVersionsKey.current = sectionKey;

        projectClient.getProjectVersions(routeKey || project.id)
            .then((versions) => {
                if (!isMounted) return;
                fetchedVersionsKey.current = sectionKey;
                setProject((previous) => previous && previous.id === project.id ? { ...previous, versions } : previous);
            })
            .catch(() => {
                if (isMounted) fetchedVersionsKey.current = '';
            })
            .finally(() => {
                if (fetchingVersionsKey.current === sectionKey) {
                    fetchingVersionsKey.current = '';
                }
            });

        return () => {
            isMounted = false;
            if (fetchingVersionsKey.current === sectionKey) {
                fetchingVersionsKey.current = '';
            }
        };
    }, [full, project?.id, project?.versions, routeKey]);

    useEffect(() => {
        const hasLoadedGallerySlice = project?.galleryImages !== undefined
            && project?.galleryImageCaptions !== undefined;
        if (full || !project?.id || hasLoadedGallerySlice) return;

        const sectionKey = `${project.id}:${routeKey || project.id}`;
        if (fetchedGalleryKey.current === sectionKey) return;

        let isMounted = true;
        fetchedGalleryKey.current = sectionKey;

        projectClient.getProjectGallery(routeKey || project.id)
            .then((gallery) => {
                if (!isMounted) return;
                setProject((previous) => previous && previous.id === project.id ? {
                    ...previous,
                    galleryImages: gallery.galleryImages,
                    galleryImageCaptions: gallery.galleryImageCaptions
                } : previous);
            })
            .catch(() => {
                if (isMounted) fetchedGalleryKey.current = '';
            });

        return () => { isMounted = false; };
    }, [full, project?.galleryImageCaptions, project?.galleryImages, project?.id, routeKey]);

    useEffect(() => {
        const hasAnyTeamSection = project?.projectRoles !== undefined
            || project?.teamMembers !== undefined
            || project?.teamInvites !== undefined;
        if (full || !project?.id || hasAnyTeamSection) return;

        const sectionKey = `${project.id}:${routeKey || project.id}`;
        if (fetchedProjectTeamKey.current === sectionKey) return;

        let isMounted = true;
        fetchedProjectTeamKey.current = sectionKey;

        projectClient.getProjectTeam(routeKey || project.id)
            .then((team) => {
                if (!isMounted) return;
                setProject((previous) => previous && previous.id === project.id
                    ? {
                        ...previous,
                        projectRoles: team.projectRoles,
                        teamMembers: team.teamMembers,
                        teamInvites: team.teamInvites
                    }
                    : previous);
            })
            .catch(() => {
                if (isMounted) fetchedProjectTeamKey.current = '';
            });

        return () => { isMounted = false; };
    }, [full, project?.id, project?.projectRoles, project?.teamInvites, project?.teamMembers, routeKey]);

    useEffect(() => {
        if (full || !project?.id || project.comments !== undefined) return;

        const sectionKey = `${project.id}:${routeKey || project.id}`;
        if (fetchedCommentsKey.current === sectionKey) return;

        let isMounted = true;
        fetchedCommentsKey.current = sectionKey;

        projectClient.getComments(routeKey || project.id)
            .then((comments) => {
                if (!isMounted) return;
                setProject((previous) => previous && previous.id === project.id ? { ...previous, comments } : previous);
            })
            .catch(() => {
                if (isMounted) fetchedCommentsKey.current = '';
            });

        return () => { isMounted = false; };
    }, [full, project?.comments, project?.id, routeKey]);

    useEffect(() => {
        if (project?.id && !analyticsFired.current) {
            analyticsFired.current = true;
            projectClient.trackView(project.id).catch(() => {});
        }
    }, [project?.id]);

    const changelogHydrationKey = useMemo(() => {
        if (!project?.id || !project.versions?.length) return '';
        return `${project.id}:${project.versions.map(version => `${version.id}:${version.versionNumber}:${version.changelog == null ? 'missing' : 'present'}`).join('|')}`;
    }, [project?.id, project?.versions]);

    useEffect(() => {
        if (!hydrateChangelogs || !project?.id || !projectNeedsChangelogHydration(project)) return;
        if (!changelogHydrationKey || fetchedChangelogKey.current === changelogHydrationKey) return;

        let isMounted = true;
        fetchedChangelogKey.current = changelogHydrationKey;

        projectClient.getProjectVersionChangelogs(routeKey || project.id)
            .then((changelogs) => {
                if (!isMounted) return;
                setProject((previous) => {
                    if (!previous || previous.id !== project.id) return previous;
                    return mergeProjectVersionChangelogs(previous, changelogs);
                });
            })
            .catch(() => {
                if (isMounted) fetchedChangelogKey.current = '';
            });

        return () => {
            isMounted = false;
        };
    }, [hydrateChangelogs, project, routeKey, changelogHydrationKey]);

    useEffect(() => {
        if (!project) return;
        const fetchTeamData = async () => {
            try {
                const authorIdToFetch = (project as any).authorId;
                if (!authorIdToFetch) return;

                const contributorIds = project.teamMembers?.map(m => m.userId) || [];
                const teamDataKey = `${authorIdToFetch}:${[...contributorIds].sort().join(',')}`;
                if (fetchedTeamDataKey.current === teamDataKey) return;
                fetchedTeamDataKey.current = teamDataKey;

                const [authorData, contribs] = await Promise.all([
                    projectClient.getUserProfile(authorIdToFetch),
                    contributorIds.length ? projectClient.getUsersBatch(contributorIds) : Promise.resolve<User[]>([])
                ]);

                setAuthorProfile(authorData);
                setContributors(contribs);

                if (authorData.accountType === 'ORGANIZATION') {
                    const members = await projectClient.getOrgMembers(authorData.id);
                    setOrgMembers(members);
                }
            } catch (e) {
                fetchedTeamDataKey.current = '';
            }
        };
        fetchTeamData();
    }, [project?.id, project?.authorId, project?.teamMembers]);

    const latestDependencies = useMemo(() => {
        if (!project?.versions?.length) return [];
        const sorted = [...project.versions].sort((a, b) => new Date(b.releaseDate).getTime() - new Date(a.releaseDate).getTime());
        return sorted[0].dependencies || [];
    }, [project?.versions]);

    const latestIncompatibleProjectIds = useMemo(() => {
        if (!project?.versions?.length) return [];
        const sorted = [...project.versions].sort((a, b) => new Date(b.releaseDate).getTime() - new Date(a.releaseDate).getTime());
        return sorted[0].incompatibleProjectIds || [];
    }, [project?.versions]);

    useEffect(() => {
        const latestProjectIds = [...latestDependencies.map(dep => dep.projectId), ...latestIncompatibleProjectIds];
        if (!latestProjectIds.length) return;
        const fetchMeta = async () => {
            const missing = latestProjectIds.filter((projectId) => projectId && !depMeta[projectId] && !fetchedDepMeta.current.has(projectId));
            if (!missing.length) return;

            missing.forEach(projectId => fetchedDepMeta.current.add(projectId));
            const newMeta = { ...depMeta };
            try {
                const batchMeta = await projectClient.getDependencyMetaBatch(missing);
                Object.entries(batchMeta || {}).forEach(([projectId, data]) => {
                    const meta = data as { icon?: string; title?: string; classification?: string; slug?: string };
                    newMeta[projectId] = {
                        icon: meta.icon || '',
                        title: meta.title || projectId,
                        classification: meta.classification,
                        slug: meta.slug
                    };
                });
            } catch (e) {}

            const stillMissing = missing.filter(projectId => !newMeta[projectId]);
            await Promise.all(stillMissing.map(async (projectId) => {
                try {
                    const data = await projectClient.getDependencyMeta(projectId);
                    newMeta[projectId] = {
                        icon: data.icon,
                        title: data.title,
                        classification: data.classification,
                        slug: data.slug
                    };
                } catch (e) {
                    newMeta[projectId] = { icon: '', title: projectId };
                }
            }));
            setDepMeta(prev => ({...prev, ...newMeta}));
        };
        fetchMeta();
    }, [latestDependencies, latestIncompatibleProjectIds, depMeta]);

    useEffect(() => {
        const authorId = (project as any)?.authorId || authorProfile?.id;
        if (currentUser?.followingIds && authorId) {
            setIsFollowing(currentUser.followingIds.includes(authorId));
        } else {
            setIsFollowing(false);
        }
    }, [currentUser, project, authorProfile]);

    const handleFollowToggle = async () => {
        const authorId = (project as any)?.authorId || authorProfile?.id;
        if (!currentUser || !project || !authorId) return;
        const oldState = isFollowing;
        setIsFollowing(!oldState);
        try {
            if (oldState) await projectClient.unfollowUser(authorId);
            else await projectClient.followUser(authorId);
        } catch (e) {
            setIsFollowing(oldState);
        }
    };

    return { project, setProject, loading, isNotFound, authorProfile, orgMembers, contributors, depMeta, latestDependencies, latestIncompatibleProjectIds, isFollowing, handleFollowToggle };
};
