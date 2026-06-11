import { useState, useEffect, useRef, useMemo } from 'react';
import { projectClient } from '../api/projectClient';
import { SiteRoutes } from '@/utils/routes';
import type { Project, User } from '@/types';

const consumeProjectBootstrap = async (realId: string) => {
    if (typeof window === 'undefined' || !window.__MODTALE_PROJECT_BOOTSTRAP) return null;

    const bootstrap = window.__MODTALE_PROJECT_BOOTSTRAP;
    window.__MODTALE_PROJECT_BOOTSTRAP = undefined;

    try {
        const data = await bootstrap;
        if (data && SiteRoutes.extractId(data.id) === realId) {
            return data as Project;
        }
    } catch {
        return null;
    }

    return null;
};

export const useProjectDetail = (rawId: string | undefined, initialData: Project | null, currentUser: User | null) => {
    const realId = rawId ? SiteRoutes.extractId(rawId) : '';

    const isInitialDataValid = initialData && SiteRoutes.extractId(initialData.id) === realId;

    const [project, setProject] = useState<Project | null>(isInitialDataValid ? initialData : null);
    const [loading, setLoading] = useState(!isInitialDataValid);
    const [isNotFound, setIsNotFound] = useState(false);
    const [authorProfile, setAuthorProfile] = useState<User | null>(null);
    const [orgMembers, setOrgMembers] = useState<User[]>([]);
    const [contributors, setContributors] = useState<User[]>([]);
    const [depMeta, setDepMeta] = useState<Record<string, { icon: string, title: string, classification?: string, slug?: string }>>({});
    const [isFollowing, setIsFollowing] = useState(false);

    const analyticsFired = useRef(false);
    const fetchedDepMeta = useRef<Set<string>>(new Set());

    useEffect(() => {
        if (!isInitialDataValid) {
            setProject(null);
            setLoading(true);
            setAuthorProfile(null);
            setOrgMembers([]);
            setContributors([]);
            analyticsFired.current = false;
        }
    }, [realId]);

    useEffect(() => {
        if (!realId) {
            setIsNotFound(true);
            setLoading(false);
            return;
        }
        if (project && SiteRoutes.extractId(project.id) === realId) {
            setLoading(false);
            return;
        }

        let isMounted = true;

        const loadProject = async () => {
            try {
                const bootstrapped = await consumeProjectBootstrap(realId);
                const data = bootstrapped || await projectClient.getProject(realId);
                if (isMounted) setProject(data);
            } catch {
                if (isMounted) setIsNotFound(true);
            } finally {
                if (isMounted) setLoading(false);
            }
        };

        loadProject();

        return () => { isMounted = false; };
    }, [realId, project?.id]);

    useEffect(() => {
        if (project?.id && !analyticsFired.current) {
            analyticsFired.current = true;
            projectClient.trackView(project.id).catch(() => {});
        }
    }, [project?.id]);

    useEffect(() => {
        if (!project) return;
        const fetchTeamData = async () => {
            try {
                const authorIdToFetch = (project as any).authorId;
                if (!authorIdToFetch) return;
                const authorData = await projectClient.getUserProfile(authorIdToFetch);
                setAuthorProfile(authorData);
                if (authorData.accountType === 'ORGANIZATION') {
                    const members = await projectClient.getOrgMembers(authorData.id);
                    setOrgMembers(members);
                }
                if (project.teamMembers?.length) {
                    const userIds = project.teamMembers.map(m => m.userId);
                    const contribs = await projectClient.getUsersBatch(userIds);
                    setContributors(contribs);
                }
            } catch (e) {}
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
            await Promise.all(missing.map(async (projectId) => {
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
