import { api } from '@/utils/api';

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
    }
};