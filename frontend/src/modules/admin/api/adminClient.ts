import { api } from '@/utils/api';

export const adminClient = {
    getProjectMeta: async (projectId: string) => (await api.get(`/projects/${projectId}/meta`)).data,
    getStructure: async (projectId: string, version: string) => (await api.get(`/admin/projects/${projectId}/versions/${version}/structure`)).data,
    getFileContent: async (projectId: string, version: string, path: string) => (await api.get(`/admin/projects/${projectId}/versions/${version}/file`, { params: { path } })).data,
    scanVersion: async (projectId: string, versionId: string) => (await api.post(`/admin/projects/${projectId}/versions/${versionId}/scan`)).data,
    publishProject: async (projectId: string) => (await api.post(`/admin/projects/${projectId}/publish`)).data,
    rejectProject: async (projectId: string, reason: string) => (await api.post(`/admin/projects/${projectId}/reject`, { reason })).data,
    approveVersion: async (projectId: string, versionId: string) => (await api.post(`/admin/projects/${projectId}/versions/${versionId}/approve`)).data,
    rejectVersion: async (projectId: string, versionId: string, reason: string) => (await api.post(`/admin/projects/${projectId}/versions/${versionId}/reject`, { reason })).data,

    getLogs: async (params: any) => (await api.get('/admin/logs', { params })).data,
    getPlatformAnalytics: async (range: string) => (await api.get(`/analytics/platform/full?range=${range}`)).data,

    searchProjects: async (params: any) => (await api.get('/admin/projects/search', { params })).data,
    getProjectById: async (id: string) => (await api.get(`/admin/projects/${id}`)).data,
    updateProjectRaw: async (id: string, data: any) => (await api.put(`/admin/projects/${id}/raw`, data)).data,
    deleteProject: async (id: string, reason: string) => (await api.delete(`/admin/projects/${id}`, { params: { reason } })).data,
    hardDeleteProject: async (id: string, reason: string) => (await api.delete(`/admin/projects/${id}/hard`, { params: { reason } })).data,
    restoreProject: async (id: string, status: string) => (await api.post(`/admin/projects/${id}/restore`, null, { params: { status } })).data,
    unlistProject: async (id: string, reason: string) => (await api.post(`/admin/projects/${id}/unlist`, { reason })).data,
    deleteVersion: async (projectId: string, versionId: string) => (await api.delete(`/admin/projects/${projectId}/versions/${versionId}`)).data,

    getReportQueue: async (status: string) => (await api.get(`/admin/reports/queue?status=${status}`)).data,
    resolveReport: async (id: string, data: any) => (await api.post(`/admin/reports/${id}/resolve`, data)).data,

    getBannedEmails: async () => (await api.get('/admin/users/bans')).data,
    banEmail: async (data: any) => (await api.post('/admin/users/bans', data)).data,
    unbanEmail: async (email: string) => (await api.delete('/admin/users/bans', { params: { email } })).data,
    searchUsers: async (query: string) => (await api.get('/users/search', { params: { query } })).data,
    getUserProfile: async (userId: string) => (await api.get(`/admin/users/${userId}`)).data,
    updateUserTier: async (userId: string, tier: string) => {
        const formData = new FormData();
        formData.append('tier', tier);
        return (await api.post(`/admin/users/${userId}/tier`, formData)).data;
    },
    grantAdmin: async (userId: string) => (await api.post(`/admin/users/${userId}/role`, null, { params: { role: 'ADMIN' } })).data,
    revokeAdmin: async (userId: string) => (await api.delete(`/admin/users/${userId}/role`, { params: { role: 'ADMIN' } })).data,
    deleteUser: async (userId: string, reason: string) => (await api.delete(`/admin/users/${userId}`, { params: { reason } })).data,
    getUserRaw: async (userId: string) => (await api.get(`/admin/users/${userId}/raw`)).data,
    updateUserRaw: async (userId: string, data: any) => (await api.put(`/admin/users/${userId}/raw`, data)).data,

    getVerificationQueue: async () => (await api.get('/admin/verification/queue')).data,
    getReviewDetails: async (id: string) => (await api.get(`/admin/projects/${id}/review-details`)).data,
    getCurrentAdmin: async () => (await api.get('/user/me')).data,

    getStatusIncidents: async () => (await api.get('/admin/status/incidents')).data,
    createStatusIncident: async (data: any) => (await api.post('/admin/status/incidents', data)).data,
    updateStatusIncident: async (id: string, data: any) => (await api.post(`/admin/status/incidents/${id}/updates`, data)).data,
};
