import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/utils/api';
import { userClient } from '@/modules/user/api/userClient';

vi.mock('@/utils/api', () => ({
    api: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        delete: vi.fn()
    }
}));

const mockedApi = vi.mocked(api);

describe('userClient', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('uploads profile banners as multipart form data', async () => {
        const file = new File(['banner'], 'banner.png', { type: 'image/png' });
        mockedApi.post.mockResolvedValue({ data: 'ok' } as any);

        await userClient.uploadBanner(file);

        const [url, formData, config] = mockedApi.post.mock.calls[0];
        expect(url).toBe('/user/profile/banner');
        expect(formData).toBeInstanceOf(FormData);
        expect((formData as FormData).get('file')).toBe(file);
        expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } });
    });

    it('uploads profile avatars as multipart form data', async () => {
        const file = new File(['avatar'], 'avatar.png', { type: 'image/png' });
        mockedApi.post.mockResolvedValue({ data: 'ok' } as any);

        await userClient.uploadAvatar(file);

        const [url, formData, config] = mockedApi.post.mock.calls[0];
        expect(url).toBe('/user/profile/avatar');
        expect(formData).toBeInstanceOf(FormData);
        expect((formData as FormData).get('file')).toBe(file);
        expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } });
    });

    it('posts MFA verification codes in the expected payload shape', async () => {
        mockedApi.post.mockResolvedValue({ data: { verified: true } } as any);

        await userClient.verifyMfa('123456');

        expect(mockedApi.post).toHaveBeenCalledWith('/auth/mfa/verify', { code: '123456' });
    });

    it('routes connection toggles and notification updates to the correct endpoints', async () => {
        mockedApi.post.mockResolvedValue({ data: null } as any);
        mockedApi.put.mockResolvedValue({ data: null } as any);

        await userClient.toggleConnectionVisibility('github');
        await userClient.updateNotifications({ projectUpdates: false });

        expect(mockedApi.post).toHaveBeenCalledWith('/user/connections/github/toggle-visibility');
        expect(mockedApi.put).toHaveBeenCalledWith('/user/settings/notifications', { projectUpdates: false });
    });

    it('routes password removal to the auth endpoint', async () => {
        mockedApi.delete.mockResolvedValue({ data: null } as any);

        await userClient.removePassword();

        expect(mockedApi.delete).toHaveBeenCalledWith('/auth/password');
    });
});
