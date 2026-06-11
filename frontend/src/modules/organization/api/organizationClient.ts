import { api } from '@/utils/api';
import type { User, OrganizationRole, Project } from '@/types';
import type { Permission } from '@/modules/permissions/permissions';

export const organizationClient = {
    getUserOrgs: async () => (await api.get<User[]>('/user/orgs')).data,
    getMembers: async (orgId: string) => (await api.get<User[]>(`/orgs/${orgId}/members`)).data,
    getInvites: async (orgId: string) => (await api.get<User[]>(`/orgs/${orgId}/invites`)).data,
    getProjects: async (orgId: string) => (await api.get(`/creators/${orgId}/projects?size=100`)).data.content as Project[],

    createOrg: async (name: string) => (await api.post<User>('/orgs', { name })).data,
    deleteOrg: async (orgId: string) => (await api.delete(`/orgs/${orgId}`)).data,
    updateProfile: async (orgId: string, data: { displayName: string, bio: string }) => (await api.put<User>(`/orgs/${orgId}`, data)).data,

    addMember: async (orgId: string, userId: string, roleId: string) => (await api.post(`/orgs/${orgId}/members`, { userId, roleId })).data,
    removeMember: async (orgId: string, userId: string) => (await api.delete(`/orgs/${orgId}/members/${userId}`)).data,
    updateMemberRole: async (orgId: string, userId: string, roleId: string) => (await api.put(`/orgs/${orgId}/members/${userId}`, { roleId })).data,
    cancelInvite: async (orgId: string, userId: string) => (await api.delete(`/orgs/${orgId}/invites/${userId}`)).data,

    createRole: async (orgId: string, role: Partial<OrganizationRole>) => (await api.post<User>(`/orgs/${orgId}/roles`, role)).data,
    updateRole: async (orgId: string, roleId: string, role: Partial<OrganizationRole>) => (await api.put<User>(`/orgs/${orgId}/roles/${roleId}`, role)).data,
    deleteRole: async (orgId: string, roleId: string) => (await api.delete<User>(`/orgs/${orgId}/roles/${roleId}`)).data,

    uploadImage: async (orgId: string, type: 'avatar' | 'banner', file: File) => {
        const formData = new FormData();
        formData.append('file', file);
        const { data } = await api.post<string | { url: string }>(
            `/orgs/${orgId}/${type}`,
            formData,
            { headers: { 'Content-Type': 'multipart/form-data' } }
        );

        return typeof data === 'string' ? data : data.url;
    },

    toggleVisibility: async (orgId: string, provider: string) => (await api.post(`/orgs/${orgId}/connections/${provider}/toggle-visibility`)).data,
    unlinkAccount: async (orgId: string, provider: string) => (await api.delete(`/orgs/${orgId}/connections/${provider}`)).data,
    searchUsers: async (query: string) => (await api.get<User[]>(`/users/search?query=${query}`)).data
};

export const hasOrgPermission = (org: User | null, userId: string, perm: Permission): boolean => {
    if (!org || !org.organizationMembers || !org.organizationRoles) return false;
    const member = org.organizationMembers.find(m => m.userId === userId);
    if (!member || !member.roleId) return false;

    const role = org.organizationRoles.find(r => r.id === member.roleId);
    if (!role) return false;

    if (role.isOwner) return true;
    return role.permissions?.includes(perm) || false;
};
