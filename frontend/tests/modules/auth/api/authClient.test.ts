import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/utils/api';
import {
    authClient,
    completeSignInMethod,
    getLastSignInMethod,
    LAST_SIGN_IN_METHOD_STORAGE_KEY,
    normalizeSignInResponse,
    PENDING_SIGN_IN_METHOD_STORAGE_KEY,
    stageSignInMethod
} from '@/modules/auth/api/authClient';

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
        window.localStorage.clear();
        window.sessionStorage.clear();
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

    it('normalizes signin payloads from both camelCase and snake_case responses', () => {
        expect(normalizeSignInResponse({
            status: 'mfa_required',
            mfaRequired: true,
            preAuthToken: 'camel-token'
        })).toEqual({
            status: 'mfa_required',
            mfaRequired: true,
            preAuthToken: 'camel-token'
        });

        expect(normalizeSignInResponse({
            status: 'mfa_required',
            mfa_required: true,
            pre_auth_token: 'snake-token'
        })).toEqual({
            status: 'mfa_required',
            mfaRequired: true,
            preAuthToken: 'snake-token'
        });
    });

    it('stores the completed sign-in method for the next modal session', () => {
        stageSignInMethod('github');

        expect(window.sessionStorage.getItem(PENDING_SIGN_IN_METHOD_STORAGE_KEY)).toBe('github');
        expect(completeSignInMethod()).toBe('github');
        expect(getLastSignInMethod()).toBe('github');
        expect(window.localStorage.getItem(LAST_SIGN_IN_METHOD_STORAGE_KEY)).toBe('github');
        expect(window.sessionStorage.getItem(PENDING_SIGN_IN_METHOD_STORAGE_KEY)).toBeNull();
    });

    it('ignores unknown stored sign-in method values', () => {
        window.localStorage.setItem(LAST_SIGN_IN_METHOD_STORAGE_KEY, 'fax-machine');

        expect(getLastSignInMethod()).toBeNull();
    });
});
