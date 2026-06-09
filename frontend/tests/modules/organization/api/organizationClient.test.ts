import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/utils/api';
import { hasOrgPermission, organizationClient } from '@/modules/organization/api/organizationClient';

vi.mock('@/utils/api', () => ({
    api: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        delete: vi.fn()
    }
}));

const mockedApi = vi.mocked(api);

describe('organizationClient', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('unwraps organization projects from paged responses', async () => {
        mockedApi.get.mockResolvedValue({ data: { content: [{ id: 'project-1' }, { id: 'project-2' }] } } as any);

        await expect(organizationClient.getProjects('org-1')).resolves.toEqual([{ id: 'project-1' }, { id: 'project-2' }]);
        expect(mockedApi.get).toHaveBeenCalledWith('/creators/org-1/projects?size=100');
    });

    it('uploads organization images as multipart form data to the requested path', async () => {
        const file = new File(['banner'], 'banner.png', { type: 'image/png' });
        mockedApi.post.mockResolvedValue({ data: 'https://cdn.modtale.net/banner.png' } as any);

        await expect(organizationClient.uploadImage('org-1', 'banner', file)).resolves.toBe('https://cdn.modtale.net/banner.png');

        const [url, formData, config] = mockedApi.post.mock.calls[0];
        expect(url).toBe('/orgs/org-1/banner');
        expect(formData).toBeInstanceOf(FormData);
        expect((formData as FormData).get('file')).toBe(file);
        expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } });
    });

    it('searches users by query string in the endpoint path', async () => {
        mockedApi.get.mockResolvedValue({ data: [] } as any);

        await organizationClient.searchUsers('sky');

        expect(mockedApi.get).toHaveBeenCalledWith('/users/search?query=sky');
    });

    it('recognizes owner roles and explicit permissions in organization membership data', () => {
        const org = {
            organizationMembers: [
                { userId: 'owner', roleId: 'owner-role' },
                { userId: 'builder', roleId: 'builder-role' }
            ],
            organizationRoles: [
                { id: 'owner-role', isOwner: true, permissions: [] },
                { id: 'builder-role', isOwner: false, permissions: ['PROJECT_CREATE'] }
            ]
        } as any;

        expect(hasOrgPermission(org, 'owner', 'ANYTHING')).toBe(true);
        expect(hasOrgPermission(org, 'builder', 'PROJECT_CREATE')).toBe(true);
        expect(hasOrgPermission(org, 'builder', 'PROJECT_DELETE')).toBe(false);
        expect(hasOrgPermission(org, 'missing', 'PROJECT_CREATE')).toBe(false);
    });
});
