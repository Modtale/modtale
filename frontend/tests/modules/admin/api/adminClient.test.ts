import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/utils/api';
import { adminClient } from '@/modules/admin/api/adminClient';

vi.mock('@/utils/api', () => ({
    api: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        delete: vi.fn()
    }
}));

const mockedApi = vi.mocked(api);

describe('adminClient', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('passes file paths as query params when loading admin file content', async () => {
        mockedApi.get.mockResolvedValue({ data: 'contents' } as any);

        await adminClient.getFileContent('project-1', '1.0.0', 'mods/sky.txt');

        expect(mockedApi.get).toHaveBeenCalledWith('/admin/projects/project-1/versions/1.0.0/file', {
            params: { path: 'mods/sky.txt' }
        });
    });

    it('posts restore actions with a null body and status query param', async () => {
        mockedApi.post.mockResolvedValue({ data: { restored: true } } as any);

        await adminClient.restoreProject('project-1', 'PUBLISHED');

        expect(mockedApi.post).toHaveBeenCalledWith('/admin/projects/project-1/restore', null, {
            params: { status: 'PUBLISHED' }
        });
    });

    it('sends tier updates as form data', async () => {
        mockedApi.post.mockResolvedValue({ data: { ok: true } } as any);

        await adminClient.updateUserTier('user-1', 'ENTERPRISE');

        const [url, formData] = mockedApi.post.mock.calls[0];
        expect(url).toBe('/admin/users/user-1/tier');
        expect(formData).toBeInstanceOf(FormData);
        expect((formData as FormData).get('tier')).toBe('ENTERPRISE');
    });

    it('grants and revokes admin role through explicit role params', async () => {
        mockedApi.post.mockResolvedValue({ data: null } as any);
        mockedApi.delete.mockResolvedValue({ data: null } as any);

        await adminClient.grantAdmin('user-1');
        await adminClient.revokeAdmin('user-1');

        expect(mockedApi.post).toHaveBeenCalledWith('/admin/users/user-1/role', null, { params: { role: 'ADMIN' } });
        expect(mockedApi.delete).toHaveBeenCalledWith('/admin/users/user-1/role', { params: { role: 'ADMIN' } });
    });

    it('includes deletion reasons in admin delete requests', async () => {
        mockedApi.delete.mockResolvedValue({ data: null } as any);

        await adminClient.deleteProject('project-1', 'spam');

        expect(mockedApi.delete).toHaveBeenCalledWith('/admin/projects/project-1', { params: { reason: 'spam' } });
    });
});
