import type { Modjam, Project } from '@/types';

export const isProjectData = (data: any): data is Project => Boolean(
    data && typeof data.id === 'string' && typeof data.title === 'string'
    && (!data.status || ['PUBLISHED', 'ARCHIVED', 'UNLISTED'].includes(data.status))
);

export const isPublicProject = (data: any): data is Project => isProjectData(data) && data.status !== 'UNLISTED';

export const isProjectPage = (data: any): boolean => Boolean(
    data && Array.isArray(data.content) && data.content.every(isPublicProject)
);

// Only complete public jam listings can suppress client recovery or enter a
// shared HTML cache. Drafts and invitation details are never public bootstrap.
export const isPublicJamList = (data: any): data is Modjam[] => Array.isArray(data) && data.every(jam =>
    jam && ['id', 'slug', 'title', 'hostId', 'hostName'].every(key => typeof jam[key] === 'string' && jam[key].trim().length > 0)
    && typeof jam.description === 'string'
    && ['startDate', 'endDate', 'votingEndDate'].every(key => typeof jam[key] === 'string' && Number.isFinite(Date.parse(jam[key])))
    && ['UPCOMING', 'ACTIVE', 'VOTING', 'AWAITING_WINNERS', 'COMPLETED'].includes(jam.status)
    && Array.isArray(jam.participantIds) && jam.participantIds.every((id: unknown) => typeof id === 'string')
    && ['imageUrl', 'bannerUrl'].every(key => jam[key] == null || typeof jam[key] === 'string')
    && ['pendingJudgeInvites', 'pendingOrganizerInvites'].every(key => jam[key] == null || (Array.isArray(jam[key]) && jam[key].length === 0))
    && (jam.pendingJudgeInviteUsers == null || (typeof jam.pendingJudgeInviteUsers === 'object' && !Array.isArray(jam.pendingJudgeInviteUsers) && Object.keys(jam.pendingJudgeInviteUsers).length === 0))
);

export const isPlatformStats = (data: any): boolean => Boolean(data &&
    ['totalProjects', 'totalDownloads', 'totalUsers'].every(key =>
        typeof data[key] === 'number' && Number.isFinite(data[key]) && data[key] >= 0
    )
);

export const buildHomeBootstrap = (marquee: any, trending: any, newest: any, stats: any) => {
    const homeSectionsReady = {
        marquee: isProjectPage(marquee),
        trending: isProjectPage(trending),
        newest: isProjectPage(newest),
        stats: isPlatformStats(stats),
    };
    const trendingProjects = homeSectionsReady.trending ? trending.content : [];
    const newestProjects = homeSectionsReady.newest ? newest.content : [];
    return {
        homeDataReady: Object.values(homeSectionsReady).every(Boolean),
        homeSectionsReady,
        homeProjects: homeSectionsReady.trending ? trendingProjects : newestProjects,
        homeMarqueeProjects: homeSectionsReady.marquee ? marquee.content : [],
        homeTrendingProjects: trendingProjects,
        homeNewestProjects: newestProjects,
        stats: homeSectionsReady.stats ? stats : null,
    };
};

// Older HTML may contain only homeProjects. Preserve its useful seed, but do
// not let one successful section suppress retries for another failed section.
export const getHomeSectionReadiness = (data: any) => {
    const ready = (section: string, items: any) => Array.isArray(items) && items.every(isProjectData) && (
        data?.homeSectionsReady ? data.homeSectionsReady[section] === true
            : data?.homeDataReady === true || items.length > 0
    );
    return {
        marquee: ready('marquee', data?.homeMarqueeProjects),
        trending: ready('trending', data?.homeTrendingProjects ?? data?.homeProjects),
        newest: ready('newest', data?.homeNewestProjects),
        stats: isPlatformStats(data?.stats) && (data?.homeSectionsReady ? data.homeSectionsReady.stats === true
            : data?.homeDataReady === true || [data?.homeProjects, data?.homeMarqueeProjects, data?.homeTrendingProjects, data?.homeNewestProjects].some(items => Array.isArray(items) && items.length > 0)),
    };
};
