import { api } from '@/utils/api';

export const userClient = {
    updateProfile: async (data: { bio?: string; username?: string }) => (await api.put('/user/profile', data)).data,
    updateCredentials: async (data: any) => (await api.put('/auth/credentials', data)).data,
    changePassword: async (data: any) => (await api.post('/auth/change-password', data)).data,
    removePassword: async () => (await api.delete('/auth/password')).data,
    uploadBanner: async (file: File) => {
        const formData = new FormData();
        formData.append('file', file);
        return (await api.post('/user/profile/banner', formData, { headers: { 'Content-Type': 'multipart/form-data' } })).data;
    },
    uploadAvatar: async (file: File) => {
        const formData = new FormData();
        formData.append('file', file);
        return (await api.post('/user/profile/avatar', formData, { headers: { 'Content-Type': 'multipart/form-data' } })).data;
    },
    deleteAccount: async () => (await api.delete('/user/me')).data,
    unlinkConnection: async (provider: string) => (await api.delete(`/user/connections/${provider}`)).data,
    toggleConnectionVisibility: async (provider: string) => (await api.post(`/user/connections/${provider}/toggle-visibility`)).data,
    resendVerification: async () => (await api.post('/auth/resend-verification')).data,
    startMfaSetup: async () => (await api.get('/auth/mfa/setup')).data,
    verifyMfa: async (code: string) => (await api.post('/auth/mfa/verify', { code })).data,
    updateNotifications: async (data: any) => (await api.put('/user/settings/notifications', data)).data,
};
