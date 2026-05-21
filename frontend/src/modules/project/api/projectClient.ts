import { api } from '@/utils/api';
import type { ManifestInspectionResult, Project, User, ProjectRole } from '@/types';

export const projectClient = {
    getProject: async (id: string) => {
        const res = await api.get<Project>(`/projects/${id}`);
        return res.data;
    },
    getProjectGameVersions: async () => {
        const res = await api.get<string[]>('/meta/game-versions');
        return res.data;
    },
    trackView: async (id: string) => {
        await api.post(`/analytics/view/${id}`);
    },
    getUserProfile: async (id: string) => {
        const res = await api.get<User>(`/user/profile/${id}`);
        return res.data;
    },
    lookupUser: async (username: string) => {
        const res = await api.get<{ id: string }>(`/users/lookup/${username}`);
        return res.data;
    },
    getOrgMembers: async (orgId: string) => {
        const res = await api.get<User[]>(`/orgs/${orgId}/members`);
        return res.data;
    },
    getUsersBatch: async (userIds: string[]) => {
        const res = await api.post<User[]>('/users/batch', { userIds });
        return res.data;
    },
    getDependencyMeta: async (projectId: string) => {
        const res = await api.get(`/projects/${projectId}/meta`);
        return res.data;
    },
    followUser: async (targetId: string) => {
        await api.post(`/user/follow/${targetId}`);
    },
    unfollowUser: async (targetId: string) => {
        await api.post(`/user/unfollow/${targetId}`);
    },
    getComments: async (projectId: string) => {
        const res = await api.get<Project>(`/projects/${projectId}`);
        return res.data.comments || [];
    },
    postComment: async (projectId: string, content: string) => {
        await api.post(`/projects/${projectId}/comments`, { content });
    },
    updateComment: async (projectId: string, commentId: string, content: string) => {
        await api.put(`/projects/${projectId}/comments/${commentId}`, { content });
    },
    deleteComment: async (projectId: string, commentId: string) => {
        await api.delete(`/projects/${projectId}/comments/${commentId}`);
    },
    replyToComment: async (projectId: string, commentId: string, reply: string) => {
        await api.post(`/projects/${projectId}/comments/${commentId}/reply`, { reply });
    },
    voteComment: async (projectId: string, commentId: string, upvote: boolean, isReply: boolean = false) => {
        const endpoint = isReply
            ? `/projects/${projectId}/comments/${commentId}/reply/vote`
            : `/projects/${projectId}/comments/${commentId}/vote`;
        await api.post(`${endpoint}?upvote=${upvote}`);
    },
    submitReport: async (targetId: string, targetType: string, reason: string, description: string) => {
        const res = await api.post('/reports', { targetId, targetType, reason, description });
        return res.data;
    },
    getWikiData: async (slug: string) => {
        const res = await api.get(`/wiki/${slug}`);
        return res.data;
    },
    getWikiPage: async (projectSlug: string, pageSlug: string) => {
        const res = await api.get(`/wiki/${projectSlug}/${pageSlug}`);
        return res.data;
    },

    searchUsers: async (query: string) => {
        const res = await api.get<User[]>(`/users/search?query=${query}`);
        return res.data;
    },
    searchProjects: async (query: string) => {
        const res = await api.get(`/projects?search=${query}`);
        return res.data.content || [];
    },
    inspectManifest: async (projectId: string, file: File) => {
        const formData = new FormData();
        formData.append('file', file);
        const res = await api.post<ManifestInspectionResult>(`/projects/${projectId}/versions/dependency-suggestions`, formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
        });
        return res.data;
    },
    getMetaGameVersions: async () => {
        const res = await api.get<string[]>('/meta/game-versions');
        return Array.isArray(res.data) ? res.data : [];
    },
    getGitRepos: async (provider: 'github' | 'gitlab') => {
        const res = await api.get(`/user/repos/${provider}`);
        return res.data || [];
    },
    inviteUser: async (projectId: string, userId: string, roleId: string) => {
        await api.post(`/projects/${projectId}/invite`, { userId, roleId });
    },
    cancelInvite: async (projectId: string, userId: string) => {
        await api.delete(`/projects/${projectId}/invites/${userId}`);
    },
    removeContributor: async (projectId: string, userId: string) => {
        await api.delete(`/projects/${projectId}/contributors/${userId}`);
    },
    updateRole: async (projectId: string, userId: string, roleId: string) => {
        await api.put(`/projects/${projectId}/contributors/${userId}`, { roleId });
    },
    saveProjectRole: async (projectId: string, role: Partial<ProjectRole>) => {
        const res = role.id
            ? await api.put<Project>(`/projects/${projectId}/roles/${role.id}`, role)
            : await api.post<Project>(`/projects/${projectId}/roles`, role);
        return res.data;
    },
    deleteProjectRole: async (projectId: string, roleId: string) => {
        const res = await api.delete<Project>(`/projects/${projectId}/roles/${roleId}`);
        return res.data;
    },
    updateVersion: async (projectId: string, versionId: string, data: any) => {
        await api.put(`/projects/${projectId}/versions/${versionId}`, data);
    }
};
