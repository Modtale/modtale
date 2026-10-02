import { describe, expect, it } from 'vitest';
import { buildHomeBootstrap, getHomeSectionReadiness, isProjectData, isProjectPage, isPublicProject, isPublicJamList } from '@/utils/publicSsr';

const project = { id: 'sky', title: 'Sky Tools', status: 'PUBLISHED' };
const page = { content: [project], totalPages: 1, totalElements: 1 };
const emptyPage = { content: [], totalPages: 0, totalElements: 0 };
const stats = { totalProjects: 2, totalDownloads: 30, totalUsers: 4 };


describe('homepage SSR readiness', () => {
    it('does not turn null failures into successful empty SSR', () => {
        const result = buildHomeBootstrap(null, null, null, null);
        expect(result.homeDataReady).toBe(false);
        expect(result.stats).toBeNull();
        expect(getHomeSectionReadiness(result)).toEqual({ marquee: false, trending: false, newest: false, stats: false });
    });
    it('keeps partial successes while retrying each missing section', () => {
        const result = buildHomeBootstrap(page, null, page, null);
        expect(result.homeDataReady).toBe(false);
        expect(result.homeMarqueeProjects).toEqual([project]);
        expect(result.homeNewestProjects).toEqual([project]);
        expect(result.homeTrendingProjects).toEqual([]);
        expect(result.homeProjects).toEqual([project]);
        expect(getHomeSectionReadiness(result)).toEqual({ marquee: true, trending: false, newest: true, stats: false });
    });
    it('accepts authoritative empty responses and zero stats without refetching', () => {
        const result = buildHomeBootstrap(emptyPage, emptyPage, emptyPage, { totalProjects: 0, totalDownloads: 0, totalUsers: 0 });
        expect(result.homeDataReady).toBe(true);
        expect(getHomeSectionReadiness(result)).toEqual({ marquee: true, trending: true, newest: true, stats: true });
    });
    it.each([{}, { content: null }, { content: {} }, { content: [null] }, { content: [{ id: 'sky' }] }])('rejects malformed list %j', malformed => {
        const result = buildHomeBootstrap(malformed, page, page, stats);
        expect(result.homeDataReady).toBe(false);
        expect(result.homeMarqueeProjects).toEqual([]);
    });
    it.each([{}, { ...stats, totalUsers: '4' }, { ...stats, totalUsers: NaN }, { ...stats, totalDownloads: -1 }])('rejects malformed stats %j', malformed => {
        expect(buildHomeBootstrap(page, page, page, malformed).homeSectionsReady.stats).toBe(false);
    });
    it('supports useful legacy project seeds without claiming other sections succeeded', () => {
        expect(getHomeSectionReadiness({ homeProjects: [project], stats })).toEqual({ marquee: false, trending: true, newest: false, stats: true });
    });
    it('does not trust malformed legacy array entries', () => {
        expect(getHomeSectionReadiness({ homeDataReady: true, homeMarqueeProjects: [null], homeTrendingProjects: [{}], homeNewestProjects: 'bad' })).toEqual({ marquee: false, trending: false, newest: false, stats: false });
    });
    it('does not allow a global ready bit to override missing payloads', () => {
        expect(getHomeSectionReadiness({ homeDataReady: true })).toEqual({ marquee: false, trending: false, newest: false, stats: false });
    });
});

describe('public project validation', () => {
    it('keeps archived content available and cacheable', () => {
        expect(isPublicProject({ ...project, status: 'ARCHIVED' })).toBe(true);
    });
    it('keeps unlisted details available but out of shared caches/listings', () => {
        const unlisted = { ...project, status: 'UNLISTED' };
        expect(isProjectData(unlisted)).toBe(true);
        expect(isPublicProject(unlisted)).toBe(false);
        expect(isProjectPage({ content: [unlisted] })).toBe(false);
    });
    it.each(['DRAFT', 'PRIVATE', 'PENDING', 'DELETED'])('rejects private status %s', status => {
        expect(isProjectData({ ...project, status })).toBe(false);
    });
});

describe('public jam listing validation', () => {
    const jam = {
        id: 'jam-1', slug: 'sky-jam', title: 'Sky Jam', description: 'Build together',
        hostId: 'ada', hostName: 'Ada', status: 'ACTIVE', participantIds: [],
        startDate: '2026-09-01T00:00:00Z', endDate: '2026-10-01T00:00:00Z', votingEndDate: '2026-10-08T00:00:00Z',
    };
    it('accepts complete public listings and successful empty results', () => {
        expect(isPublicJamList([jam])).toBe(true);
        expect(isPublicJamList([])).toBe(true);
        expect(isPublicJamList([{ ...jam, pendingJudgeInvites: [], pendingOrganizerInvites: [], pendingJudgeInviteUsers: {} }])).toBe(true);
    });
    it.each(['UPCOMING', 'ACTIVE', 'VOTING', 'AWAITING_WINNERS', 'COMPLETED'])('retains public phase %s', status => {
        expect(isPublicJamList([{ ...jam, status }])).toBe(true);
    });
    it.each([null, {}, [null], [{}], [{ ...jam, title: '' }], [{ ...jam, startDate: 'invalid' }], [{ ...jam, participantIds: 'bad' }], [{ ...jam, bannerUrl: {} }]])('rejects malformed listings %j', data => {
        expect(isPublicJamList(data)).toBe(false);
    });
    it.each(['DRAFT', 'PRIVATE', 'UNLISTED', 'DELETED'])('rejects private or unknown phase %s', status => {
        expect(isPublicJamList([{ ...jam, status }])).toBe(false);
    });
    it.each([
        { pendingJudgeInvites: ['private-person'] },
        { pendingOrganizerInvites: [{ userId: 'private-person' }] },
        { pendingJudgeInviteUsers: { 'private-person': 'private-name' } },
    ])('rejects private invitation data %j', privateData => {
        expect(isPublicJamList([{ ...jam, ...privateData }])).toBe(false);
    });
});
