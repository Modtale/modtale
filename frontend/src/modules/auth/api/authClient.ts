import { api } from '@/utils/api';

export type SignInMethod = 'email' | 'github' | 'gitlab' | 'discord' | 'google';

export type SignInResponsePayload = {
    status?: string;
    mfaRequired?: boolean;
    preAuthToken?: string | null;
    mfa_required?: boolean;
    pre_auth_token?: string | null;
};

export const LAST_SIGN_IN_METHOD_STORAGE_KEY = 'modtale.lastSignInMethod';
export const PENDING_SIGN_IN_METHOD_STORAGE_KEY = 'modtale.pendingSignInMethod';

export const SIGN_IN_METHOD_LABELS: Record<SignInMethod, string> = {
    email: 'Email',
    github: 'GitHub',
    gitlab: 'GitLab',
    discord: 'Discord',
    google: 'Google'
};

const isSignInMethod = (value: string | null): value is SignInMethod => (
    value === 'email' ||
    value === 'github' ||
    value === 'gitlab' ||
    value === 'discord' ||
    value === 'google'
);

const readStoredSignInMethod = (storage: Storage, key: string): SignInMethod | null => {
    const stored = storage.getItem(key);
    return isSignInMethod(stored) ? stored : null;
};

export const getLastSignInMethod = (): SignInMethod | null => {
    if (typeof window === 'undefined') return null;

    try {
        return readStoredSignInMethod(window.localStorage, LAST_SIGN_IN_METHOD_STORAGE_KEY);
    } catch {
        return null;
    }
};

export const stageSignInMethod = (method: SignInMethod) => {
    if (typeof window === 'undefined') return;

    try {
        window.sessionStorage.setItem(PENDING_SIGN_IN_METHOD_STORAGE_KEY, method);
    } catch {
        // Storage can be unavailable in private or restricted browsing modes.
    }
};

export const clearPendingSignInMethod = () => {
    if (typeof window === 'undefined') return;

    try {
        window.sessionStorage.removeItem(PENDING_SIGN_IN_METHOD_STORAGE_KEY);
    } catch {
        // Ignore storage failures; the hint is non-critical UI state.
    }
};

export const completeSignInMethod = (method?: SignInMethod | null): SignInMethod | null => {
    if (typeof window === 'undefined') return null;

    try {
        const completedMethod = method ?? readStoredSignInMethod(window.sessionStorage, PENDING_SIGN_IN_METHOD_STORAGE_KEY);
        if (!completedMethod) return null;

        window.localStorage.setItem(LAST_SIGN_IN_METHOD_STORAGE_KEY, completedMethod);
        window.sessionStorage.removeItem(PENDING_SIGN_IN_METHOD_STORAGE_KEY);
        return completedMethod;
    } catch {
        return null;
    }
};

export const normalizeSignInResponse = (payload: SignInResponsePayload | null | undefined) => ({
    status: payload?.status ?? null,
    mfaRequired: Boolean(payload?.mfaRequired ?? payload?.mfa_required),
    preAuthToken: payload?.preAuthToken ?? payload?.pre_auth_token ?? null
});

export const authClient = {
    signIn: async (data: any) => {
        return await api.post('/auth/signin', data);
    },
    register: async (data: any) => {
        return await api.post('/auth/register', data);
    },
    forgotPassword: async (data: any) => {
        return await api.post('/auth/forgot-password', data);
    },
    resetPassword: async (data: any) => {
        return await api.post('/auth/reset-password', data);
    },
    verifyEmail: async (token: string) => {
        return await api.post(`/auth/verify?token=${token}`);
    },
    validateMfaLogin: async (data: { pre_auth_token: string | null; code: string }) => {
        return await api.post('/auth/mfa/validate-login', data);
    },
    issueLauncherAuthCode: async (data: { redirectUri: string; state?: string | null }) => {
        return await api.post('/auth/launcher/issue', data);
    }
};
