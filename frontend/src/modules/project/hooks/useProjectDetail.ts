import { useState, useEffect, useRef, useMemo } from 'react';
import { projectClient } from '../api/projectClient';
import { SiteRoutes } from '@/utils/routes';
import type { Project, User } from '@/types';

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
        projectClient.getProject(realId)
            .then(data => { if (isMounted) setProject(data); })
            .catch(() => { if (isMounted) setIsNotFound(true); })
            .finally(() => { if (isMounted) setLoading(false); });

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
                let authorData;
                if (authorIdToFetch) {
                    authorData = await projectClient.getUserProfile(authorIdToFetch);
                } else {
                    const lookupRes = await projectClient.lookupUser(project.author);
                    authorData = await projectClient.getUserProfile(lookupRes.id);
                }
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
    }, [project?.id, project?.author, project?.teamMembers]);

    const latestDependencies = useMemo(() => {
        if (!project?.versions?.length) return [];
        const sorted = [...project.versions].sort((a, b) => new Date(b.releaseDate).getTime() - new Date(a.releaseDate).getTime());
        return sorted[0].dependencies || [];
    }, [project?.versions]);

    useEffect(() => {
        if (!latestDependencies.length) return;
        const fetchMeta = async () => {
            const missing = latestDependencies.filter(d => d && d.projectId && !depMeta[d.projectId] && !fetchedDepMeta.current.has(d.projectId));
            if (!missing.length) return;

            missing.forEach(d => fetchedDepMeta.current.add(d.projectId));
            const newMeta = { ...depMeta };
            await Promise.all(missing.map(async (d) => {
                try {
                    const data = await projectClient.getDependencyMeta(d.projectId);
                    newMeta[d.projectId] = {
                        icon: data.icon,
                        title: data.title,
                        classification: data.classification,
                        slug: data.slug
                    };
                } catch (e) {
                    newMeta[d.projectId] = { icon: '', title: d.projectTitle || d.projectId };
                }
            }));
            setDepMeta(prev => ({...prev, ...newMeta}));
        };
        fetchMeta();
    }, [latestDependencies]);

    useEffect(() => {
        if (currentUser?.followingIds && project?.author) {
            setIsFollowing(currentUser.followingIds.includes(project.author));
        } else {
            setIsFollowing(false);
        }
    }, [currentUser, project?.author]);

    const handleFollowToggle = async () => {
        if (!currentUser || !project) return;
        const oldState = isFollowing;
        setIsFollowing(!oldState);
        try {
            if (oldState) await projectClient.unfollowUser(project.author);
            else await projectClient.followUser(project.author);
        } catch (e) {
            setIsFollowing(oldState);
        }
    };

    return { project, setProject, loading, isNotFound, authorProfile, orgMembers, contributors, depMeta, latestDependencies, isFollowing, handleFollowToggle };
};