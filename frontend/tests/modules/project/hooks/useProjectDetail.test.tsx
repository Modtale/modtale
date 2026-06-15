import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { useProjectDetail } from '@/modules/project/hooks/useProjectDetail';
import { projectClient } from '@/modules/project/api/projectClient';
import type { Project, User } from '@/types';

vi.mock('@/modules/project/api/projectClient', () => ({
    projectClient: {
        getProject: vi.fn(),
        getProjectVersions: vi.fn(),
        getProjectVersionChangelogs: vi.fn(),
        getProjectGallery: vi.fn(),
        getProjectTeam: vi.fn(),
        trackView: vi.fn(),
        getUserProfile: vi.fn(),
        getOrgMembers: vi.fn(),
        getUsersBatch: vi.fn(),
        getDependencyMeta: vi.fn(),
        getDependencyMetaBatch: vi.fn(),
        getComments: vi.fn(),
        followUser: vi.fn(),
        unfollowUser: vi.fn()
    }
}));

const mockedProjectClient = vi.mocked(projectClient);

const settle = async (times = 8) => {
    for (let i = 0; i < times; i += 1) {
        await act(async () => {
            await Promise.resolve();
        });
    }
};

type HookSnapshot = ReturnType<typeof useProjectDetail>;

const Probe = ({
    rawId,
    initialData,
    currentUser,
    onRender
}: {
    rawId: string | undefined;
    initialData: Project | null;
    currentUser: User | null;
    onRender: (snapshot: HookSnapshot) => void;
}) => {
    const snapshot = useProjectDetail(rawId, initialData, currentUser);
    onRender(snapshot);

    return (
        <div
            id="probe"
            data-loading={String(snapshot.loading)}
            data-not-found={String(snapshot.isNotFound)}
            data-project-id={snapshot.project?.id ?? ''}
            data-author-id={snapshot.authorProfile?.id ?? ''}
            data-org-members={String(snapshot.orgMembers.length)}
            data-contributors={String(snapshot.contributors.length)}
            data-following={String(snapshot.isFollowing)}
            data-dependency-title={snapshot.depMeta['dep-1']?.title ?? ''}
            data-incompatible-title={snapshot.depMeta['bad-1']?.title ?? ''}
        />
    );
};

describe('useProjectDetail', () => {
    let container: HTMLDivElement;
    let root: Root;
    let latestSnapshot: HookSnapshot;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        latestSnapshot = undefined as unknown as HookSnapshot;

        vi.clearAllMocks();
        mockedProjectClient.getProjectVersions.mockResolvedValue([]);
        mockedProjectClient.getProjectGallery.mockResolvedValue([]);
        mockedProjectClient.getProjectTeam.mockResolvedValue({
            projectRoles: [],
            teamMembers: [],
            teamInvites: []
        });
        mockedProjectClient.getComments.mockResolvedValue([]);
        mockedProjectClient.trackView.mockResolvedValue(undefined);
        mockedProjectClient.getOrgMembers.mockResolvedValue([]);
        mockedProjectClient.getUsersBatch.mockResolvedValue([]);
        mockedProjectClient.getDependencyMeta.mockResolvedValue({ icon: '', title: '', classification: 'MOD', slug: 'dep' } as any);
        mockedProjectClient.getDependencyMetaBatch.mockResolvedValue({});
        mockedProjectClient.followUser.mockResolvedValue(undefined);
        mockedProjectClient.unfollowUser.mockResolvedValue(undefined);
    });

    afterEach(async () => {
        window.__MODTALE_PROJECT_BOOTSTRAP = undefined;
        window.__MODTALE_PROJECT_BOOTSTRAP_URL = undefined;
        await act(async () => {
            root.unmount();
        });
        container.remove();
    });

    it('uses bootstrapped project data and skips the duplicate detail fetch', async () => {
        const projectId = '123e4567-e89b-12d3-a456-426614174000';
        const project = {
            id: projectId,
            authorId: 'author-1',
            author: 'Ada',
            versions: []
        } as any satisfies Project;

        window.__MODTALE_PROJECT_BOOTSTRAP = Promise.resolve(project);
        mockedProjectClient.getUserProfile.mockResolvedValue({
            id: 'author-1',
            username: 'Ada',
            avatarUrl: '',
            likedProjectIds: [],
            accountType: 'USER'
        } as User);

        await act(async () => {
            root.render(
                <Probe
                    rawId={`sky-tools~${projectId}`}
                    initialData={null}
                    currentUser={null}
                    onRender={snapshot => {
                        latestSnapshot = snapshot;
                    }}
                />
            );
        });
        await settle();

        expect(mockedProjectClient.getProject).not.toHaveBeenCalled();
        expect(mockedProjectClient.trackView).toHaveBeenCalledWith(projectId);
        expect(latestSnapshot.project?.id).toBe(projectId);
        expect(latestSnapshot.loading).toBe(false);
        expect(window.__MODTALE_PROJECT_BOOTSTRAP).toBeUndefined();
    });

    it('accepts canonical slug routes when bootstrapped data already matches the slug', async () => {
        const project = {
            id: '123e4567-e89b-12d3-a456-426614174000',
            slug: 'levelingcore',
            authorId: 'author-1',
            author: 'Ada',
            versions: []
        } as any satisfies Project;

        window.__MODTALE_PROJECT_BOOTSTRAP = Promise.resolve(project);
        mockedProjectClient.getUserProfile.mockResolvedValue({
            id: 'author-1',
            username: 'Ada',
            avatarUrl: '',
            likedProjectIds: [],
            accountType: 'USER'
        } as User);

        await act(async () => {
            root.render(
                <Probe
                    rawId="levelingcore"
                    initialData={null}
                    currentUser={null}
                    onRender={snapshot => {
                        latestSnapshot = snapshot;
                    }}
                />
            );
        });
        await settle();

        expect(mockedProjectClient.getProject).not.toHaveBeenCalled();
        expect(mockedProjectClient.trackView).toHaveBeenCalledWith(project.id);
        expect(latestSnapshot.project?.id).toBe(project.id);
        expect(latestSnapshot.loading).toBe(false);
        expect(window.__MODTALE_PROJECT_BOOTSTRAP).toBeUndefined();
    });

    it('loads related author, contributor, dependency, and analytics data from the fetched project', async () => {
        const projectId = '123e4567-e89b-12d3-a456-426614174000';
        const rawId = `sky-tools-${projectId}`;
        const project = {
            id: projectId,
            authorId: 'org-1',
            author: 'SkyOrg',
            teamMembers: [{ userId: 'contrib-1', roleId: 'dev' }],
            versions: [
                { id: 'v1', versionNumber: '1.0.0', releaseDate: '2024-01-01T00:00:00Z', dependencies: [] },
                {
                    id: 'v2',
                    versionNumber: '1.1.0',
                    releaseDate: '2024-05-01T00:00:00Z',
                    dependencies: [{ projectId: 'dep-1', projectTitle: 'Dependency One', versionNumber: '2.0.0' }],
                    incompatibleProjectIds: ['bad-1']
                }
            ]
        } as any satisfies Project;
        const currentUser = {
            id: 'viewer-1',
            username: 'Viewer',
            avatarUrl: '',
            likedProjectIds: [],
            followingIds: ['org-1']
        } satisfies User;

        mockedProjectClient.getProject.mockResolvedValue(project);
        mockedProjectClient.getUserProfile.mockResolvedValue({
            id: 'org-1',
            username: 'SkyOrg',
            avatarUrl: '',
            likedProjectIds: [],
            accountType: 'ORGANIZATION'
        } as User);
        mockedProjectClient.getOrgMembers.mockResolvedValue([{ id: 'member-1', username: 'Builder', avatarUrl: '', likedProjectIds: [] } as User]);
        mockedProjectClient.getUsersBatch.mockResolvedValue([{ id: 'contrib-1', username: 'Contributor', avatarUrl: '', likedProjectIds: [] } as User]);
        mockedProjectClient.getDependencyMetaBatch.mockResolvedValue({
            'dep-1': { icon: '/dep.png', title: 'Dependency One', classification: 'MOD', slug: 'dependency-one' },
            'bad-1': { icon: '/bad.png', title: 'Bad Mod', classification: 'MOD', slug: 'bad-mod' }
        } as any);
        mockedProjectClient.getDependencyMeta.mockImplementation(async (projectId: string) => {
            if (projectId === 'bad-1') {
                return { icon: '/bad.png', title: 'Bad Mod', classification: 'MOD', slug: 'bad-mod' } as any;
            }
            return { icon: '/dep.png', title: 'Dependency One', classification: 'MOD', slug: 'dependency-one' } as any;
        });

        await act(async () => {
            root.render(
                <Probe
                    rawId={rawId}
                    initialData={null}
                    currentUser={currentUser}
                    onRender={snapshot => {
                        latestSnapshot = snapshot;
                    }}
                />
            );
        });
        await settle();

        const probe = container.querySelector('#probe') as HTMLDivElement;
        expect(probe.dataset.loading).toBe('false');
        expect(probe.dataset.notFound).toBe('false');
        expect(probe.dataset.projectId).toBe(projectId);
        expect(probe.dataset.authorId).toBe('org-1');
        expect(probe.dataset.orgMembers).toBe('1');
        expect(probe.dataset.contributors).toBe('1');
        expect(probe.dataset.following).toBe('true');
        expect(probe.dataset.dependencyTitle).toBe('Dependency One');
        expect(probe.dataset.incompatibleTitle).toBe('Bad Mod');

        expect(mockedProjectClient.getProject).toHaveBeenCalledWith(rawId);
        expect(mockedProjectClient.trackView).toHaveBeenCalledTimes(1);
        expect(mockedProjectClient.trackView).toHaveBeenCalledWith(projectId);
        expect(mockedProjectClient.getUserProfile).toHaveBeenCalledWith('org-1');
        expect(mockedProjectClient.getOrgMembers).toHaveBeenCalledWith('org-1');
        expect(mockedProjectClient.getUsersBatch).toHaveBeenCalledWith(['contrib-1']);
        expect(mockedProjectClient.getDependencyMetaBatch).toHaveBeenCalledWith(['dep-1', 'bad-1']);
        expect(mockedProjectClient.getDependencyMeta).not.toHaveBeenCalled();
        expect(latestSnapshot.latestDependencies).toEqual([
            { projectId: 'dep-1', projectTitle: 'Dependency One', versionNumber: '2.0.0' }
        ]);
        expect(latestSnapshot.latestIncompatibleProjectIds).toEqual(['bad-1']);
    });

    it('marks the project as not found when the primary fetch fails', async () => {
        mockedProjectClient.getProject.mockRejectedValue(new Error('missing'));

        await act(async () => {
            root.render(
                <Probe
                    rawId="missing-project"
                    initialData={null}
                    currentUser={null}
                    onRender={snapshot => {
                        latestSnapshot = snapshot;
                    }}
                />
            );
        });
        await settle();

        const probe = container.querySelector('#probe') as HTMLDivElement;
        expect(probe.dataset.loading).toBe('false');
        expect(probe.dataset.notFound).toBe('true');
        expect(mockedProjectClient.trackView).not.toHaveBeenCalled();
        expect(latestSnapshot.project).toBeNull();
    });

    it('optimistically follows the author and keeps the new state when the request succeeds', async () => {
        const project = {
            id: 'project-1',
            authorId: 'author-1',
            author: 'Ada',
            versions: []
        } as any satisfies Project;
        const currentUser = {
            id: 'viewer-1',
            username: 'Viewer',
            avatarUrl: '',
            likedProjectIds: [],
            followingIds: []
        } satisfies User;

        mockedProjectClient.getUserProfile.mockResolvedValue({
            id: 'author-1',
            username: 'Ada',
            avatarUrl: '',
            likedProjectIds: [],
            accountType: 'USER'
        } as User);

        await act(async () => {
            root.render(
                <Probe
                    rawId="project-1"
                    initialData={project}
                    currentUser={currentUser}
                    onRender={snapshot => {
                        latestSnapshot = snapshot;
                    }}
                />
            );
        });
        await settle();

        await act(async () => {
            await latestSnapshot.handleFollowToggle();
        });
        await settle();

        expect(mockedProjectClient.followUser).toHaveBeenCalledWith('author-1');
        expect(latestSnapshot.isFollowing).toBe(true);
    });

    it('reverts the optimistic unfollow state when the request fails', async () => {
        const project = {
            id: 'project-1',
            authorId: 'author-1',
            author: 'Ada',
            versions: []
        } as any satisfies Project;
        const currentUser = {
            id: 'viewer-1',
            username: 'Viewer',
            avatarUrl: '',
            likedProjectIds: [],
            followingIds: ['author-1']
        } satisfies User;

        mockedProjectClient.getUserProfile.mockResolvedValue({
            id: 'author-1',
            username: 'Ada',
            avatarUrl: '',
            likedProjectIds: [],
            accountType: 'USER'
        } as User);
        mockedProjectClient.unfollowUser.mockRejectedValue(new Error('network'));

        await act(async () => {
            root.render(
                <Probe
                    rawId="project-1"
                    initialData={project}
                    currentUser={currentUser}
                    onRender={snapshot => {
                        latestSnapshot = snapshot;
                    }}
                />
            );
        });
        await settle();

        expect(latestSnapshot.isFollowing).toBe(true);

        await act(async () => {
            await latestSnapshot.handleFollowToggle();
        });
        await settle();

        expect(mockedProjectClient.unfollowUser).toHaveBeenCalledWith('author-1');
        expect(latestSnapshot.isFollowing).toBe(true);
    });
});
