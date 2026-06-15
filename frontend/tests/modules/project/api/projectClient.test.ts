import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/utils/api';
import { projectClient } from '@/modules/project/api/projectClient';

vi.mock('@/utils/api', () => ({
    api: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        delete: vi.fn()
    }
}));

type MockApi = {
    get: ReturnType<typeof vi.fn>;
    post: ReturnType<typeof vi.fn>;
    put: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
};

const mockedApi = api as unknown as MockApi;

describe('projectClient', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('fetches project details from the lean project endpoint', async () => {
        mockedApi.get.mockResolvedValueOnce({ data: { id: 'project-1' } } as any);

        await expect(projectClient.getProject('project-1')).resolves.toEqual({ id: 'project-1' });

        expect(mockedApi.get).toHaveBeenCalledWith('/projects/project-1');
    });

    it('fetches full project details from the explicit details endpoint', async () => {
        mockedApi.get.mockResolvedValueOnce({ data: { id: 'project-1', versions: [{ id: 'v1' }] } } as any);

        await expect(projectClient.getProjectFull('project-1')).resolves.toEqual({ id: 'project-1', versions: [{ id: 'v1' }] });

        expect(mockedApi.get).toHaveBeenCalledWith('/projects/project-1/details');
    });

    it('fetches project versions from the version slice', async () => {
        mockedApi.get.mockResolvedValueOnce({ data: { versions: [{ id: 'version-1' }] } } as any);

        await expect(projectClient.getProjectVersions('project-1')).resolves.toEqual([{ id: 'version-1' }]);

        expect(mockedApi.get).toHaveBeenCalledWith('/projects/project-1/versions');
    });

    it('fetches gallery and team slices independently', async () => {
        mockedApi.get
            .mockResolvedValueOnce({ data: { galleryImages: ['one.png'], galleryImageCaptions: { 'one.png': 'Opening shot' } } } as any)
            .mockResolvedValueOnce({ data: { projectRoles: [{ id: 'role-1' }], teamMembers: [{ userId: 'u1' }], teamInvites: [{ userId: 'u2' }] } } as any);

        await expect(projectClient.getProjectGallery('project-1')).resolves.toEqual({
            galleryImages: ['one.png'],
            galleryImageCaptions: { 'one.png': 'Opening shot' }
        });
        await expect(projectClient.getProjectTeam('project-1')).resolves.toEqual({
            projectRoles: [{ id: 'role-1' }],
            teamMembers: [{ userId: 'u1' }],
            teamInvites: [{ userId: 'u2' }]
        });

        expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/projects/project-1/gallery');
        expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/projects/project-1/team');
    });

    it('fetches version changelogs separately', async () => {
        const changelogs = [{ id: 'version-1', versionNumber: '1.0.0', changelog: 'Changed things.' }];
        mockedApi.get.mockResolvedValueOnce({ data: changelogs } as any);

        await expect(projectClient.getProjectVersionChangelogs('project-1')).resolves.toEqual(changelogs);

        expect(mockedApi.get).toHaveBeenCalledWith('/projects/project-1/versions/changelogs');
    });

    it('returns project comments when present and falls back to an empty list', async () => {
        mockedApi.get.mockResolvedValueOnce({ data: { comments: [{ id: 'comment-1' }] } } as any);
        await expect(projectClient.getComments('project-1')).resolves.toEqual([{ id: 'comment-1' }]);

        mockedApi.get.mockResolvedValueOnce({ data: {} } as any);
        await expect(projectClient.getComments('project-1')).resolves.toEqual([]);

        expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/projects/project-1/comments');
        expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/projects/project-1/comments');
    });

    it('uses the correct vote endpoints for comments and replies', async () => {
        mockedApi.post.mockResolvedValue({ data: null } as any);

        await projectClient.voteComment('project-1', 'comment-1', true);
        await projectClient.voteComment('project-1', 'comment-1', false, true);

        expect(mockedApi.post).toHaveBeenNthCalledWith(1, '/projects/project-1/comments/comment-1/vote?upvote=true');
        expect(mockedApi.post).toHaveBeenNthCalledWith(2, '/projects/project-1/comments/comment-1/reply/vote?upvote=false');
    });

    it('unwraps paged project search responses and falls back to an empty array', async () => {
        mockedApi.get.mockResolvedValueOnce({ data: { content: [{ id: 'project-1' }] } } as any);
        await expect(projectClient.searchProjects('sky')).resolves.toEqual([{ id: 'project-1' }]);

        mockedApi.get.mockResolvedValueOnce({ data: {} } as any);
        await expect(projectClient.searchProjects('cloud')).resolves.toEqual([]);
    });

    it('returns an empty array when game version metadata is not an array', async () => {
        mockedApi.get.mockResolvedValueOnce({ data: ['1.0.0', '1.1.0'] } as any);
        await expect(projectClient.getMetaGameVersions()).resolves.toEqual(['1.0.0', '1.1.0']);

        mockedApi.get.mockResolvedValueOnce({ data: { versions: ['1.2.0'] } } as any);
        await expect(projectClient.getMetaGameVersions()).resolves.toEqual([]);
    });

    it('sends manifest inspection uploads as multipart form data', async () => {
        const file = new File(['{}'], 'manifest.json', { type: 'application/json' });
        mockedApi.post.mockResolvedValue({ data: { dependencies: [] } } as any);

        await projectClient.inspectManifest('project-1', file);

        const [url, formData, config] = mockedApi.post.mock.calls[0];
        expect(url).toBe('/projects/project-1/versions/dependency-suggestions');
        expect(formData).toBeInstanceOf(FormData);
        expect((formData as FormData).get('file')).toBe(file);
        expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } });
    });

    it('chooses between creating and updating project roles based on the role id', async () => {
        mockedApi.post.mockResolvedValueOnce({ data: { id: 'role-new' } } as any);
        mockedApi.put.mockResolvedValueOnce({ data: { id: 'role-1' } } as any);

        await expect(projectClient.saveProjectRole('project-1', { name: 'Writer' })).resolves.toEqual({ id: 'role-new' });
        await expect(projectClient.saveProjectRole('project-1', { id: 'role-1', name: 'Editor' })).resolves.toEqual({ id: 'role-1' });

        expect(mockedApi.post).toHaveBeenCalledWith('/projects/project-1/roles', { name: 'Writer' });
        expect(mockedApi.put).toHaveBeenCalledWith('/projects/project-1/roles/role-1', { id: 'role-1', name: 'Editor' });
    });
});
