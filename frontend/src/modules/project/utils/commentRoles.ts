import type { Project, User } from '@/types';

export interface CommentRoleBadge {
    label: string;
    color?: string;
}

const FALLBACK_PROJECT_ROLE_COLOR = '#3b82f6';
const FALLBACK_ORG_ROLE_COLOR = '#8b5cf6';
const OWNER_ROLE_COLOR = '#f97316';

export const getCommentRoleBadge = (
    userId: string | undefined,
    project?: Project | null,
    authorProfile?: User | null
): CommentRoleBadge | null => {
    if (!userId || !project) return null;

    if (userId === project.authorId) {
        return {
            label: authorProfile?.accountType === 'ORGANIZATION' ? 'Organization' : 'Owner',
            color: OWNER_ROLE_COLOR
        };
    }

    const projectMembership = project.teamMembers?.find(member => member.userId === userId);
    if (projectMembership) {
        const projectRole = project.projectRoles?.find(role => role.id === projectMembership.roleId);
        return {
            label: projectRole?.name || 'Contributor',
            color: projectRole?.color || FALLBACK_PROJECT_ROLE_COLOR
        };
    }

    if (authorProfile?.accountType === 'ORGANIZATION') {
        const orgMembership = authorProfile.organizationMembers?.find(member => member.userId === userId);
        if (orgMembership) {
            const orgRole = authorProfile.organizationRoles?.find(role => role.id === orgMembership.roleId);
            return {
                label: orgRole?.name || 'Member',
                color: orgRole?.color || FALLBACK_ORG_ROLE_COLOR
            };
        }
    }

    return null;
};
