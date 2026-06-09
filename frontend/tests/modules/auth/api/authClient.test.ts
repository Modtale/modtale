import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/utils/api';
import { authClient } from '@/modules/auth/api/authClient';

vi.mock('@/utils/api', () => ({
    api: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        delete: vi.fn()
    }
}));

const mockedApi = vi.mocked(api);

describe('authClient', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockedApi.post.mockResolvedValue({ data: { ok: true } } as any);
    });

    it('routes auth form submissions to their backend endpoints', async () => {
        await authClient.signIn({ email: 'ada@example.com' });
        await authClient.register({ username: 'ada' });
        await authClient.forgotPassword({ email: 'ada@example.com' });
        await authClient.resetPassword({ token: 'reset-token' });

        expect(mockedApi.post).toHaveBeenNthCalledWith(1, '/auth/signin', { email: 'ada@example.com' });
        expect(mockedApi.post).toHaveBeenNthCalledWith(2, '/auth/register', { username: 'ada' });
        expect(mockedApi.post).toHaveBeenNthCalledWith(3, '/auth/forgot-password', { email: 'ada@example.com' });
        expect(mockedApi.post).toHaveBeenNthCalledWith(4, '/auth/reset-password', { token: 'reset-token' });
    });

    it('appends verification tokens to the verify email endpoint', async () => {
        await authClient.verifyEmail('verify-token');

        expect(mockedApi.post).toHaveBeenCalledWith('/auth/verify?token=verify-token');
    });

    it('posts MFA login validation payloads unchanged', async () => {
        const payload = { pre_auth_token: 'pre-auth', code: '123456' };

        await authClient.validateMfaLogin(payload);

        expect(mockedApi.post).toHaveBeenCalledWith('/auth/mfa/validate-login', payload);
    });
});
