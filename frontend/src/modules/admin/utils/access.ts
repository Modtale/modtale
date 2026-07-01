export enum AdminPermission {
    PROJECT_REVIEW_READ = 'PROJECT_REVIEW_READ',
    PROJECT_REVIEW_DECIDE = 'PROJECT_REVIEW_DECIDE',
    PROJECT_MANAGE_READ = 'PROJECT_MANAGE_READ',
    PROJECT_MODERATE = 'PROJECT_MODERATE',
    PROJECT_DELETE = 'PROJECT_DELETE',
    PROJECT_RESTORE = 'PROJECT_RESTORE',
    PROJECT_VERSION_DELETE = 'PROJECT_VERSION_DELETE',
    PROJECT_VERSION_RESCAN = 'PROJECT_VERSION_RESCAN',
    PROJECT_RAW_EDIT = 'PROJECT_RAW_EDIT',
    REPORT_READ = 'REPORT_READ',
    REPORT_RESOLVE = 'REPORT_RESOLVE',
    USER_READ = 'USER_READ',
    USER_DELETE = 'USER_DELETE',
    EMAIL_BAN_READ = 'EMAIL_BAN_READ',
    EMAIL_BAN_MANAGE = 'EMAIL_BAN_MANAGE',
    USER_TIER_MANAGE = 'USER_TIER_MANAGE',
    USER_PERMISSION_MANAGE = 'USER_PERMISSION_MANAGE',
    USER_RAW_READ = 'USER_RAW_READ',
    USER_RAW_EDIT = 'USER_RAW_EDIT',
    AUDIT_LOG_READ = 'AUDIT_LOG_READ',
    PLATFORM_ANALYTICS_READ = 'PLATFORM_ANALYTICS_READ',
    STATUS_INCIDENT_READ = 'STATUS_INCIDENT_READ',
    STATUS_INCIDENT_MANAGE = 'STATUS_INCIDENT_MANAGE'
}

export const ALL_ADMIN_PERMISSIONS = Object.values(AdminPermission);

export const ADMIN_PERMISSION_GROUPS = [
    {
        group: 'Review',
        permissions: [
            { id: AdminPermission.PROJECT_REVIEW_READ, label: 'Read Review Queue' },
            { id: AdminPermission.PROJECT_REVIEW_DECIDE, label: 'Approve or Reject' },
            { id: AdminPermission.PROJECT_VERSION_RESCAN, label: 'Rescan Versions' }
        ]
    },
    {
        group: 'Reports',
        permissions: [
            { id: AdminPermission.REPORT_READ, label: 'Read Reports' },
            { id: AdminPermission.REPORT_RESOLVE, label: 'Resolve Reports' }
        ]
    },
    {
        group: 'Projects',
        permissions: [
            { id: AdminPermission.PROJECT_MANAGE_READ, label: 'Read Project Admin Data' },
            { id: AdminPermission.PROJECT_MODERATE, label: 'Moderate Projects' },
            { id: AdminPermission.PROJECT_DELETE, label: 'Delete Projects' },
            { id: AdminPermission.PROJECT_RESTORE, label: 'Restore Projects' },
            { id: AdminPermission.PROJECT_VERSION_DELETE, label: 'Delete Versions' },
            { id: AdminPermission.PROJECT_RAW_EDIT, label: 'Edit Raw Project JSON' }
        ]
    },
    {
        group: 'Users',
        permissions: [
            { id: AdminPermission.USER_READ, label: 'Read User Admin Data' },
            { id: AdminPermission.USER_DELETE, label: 'Delete Users' },
            { id: AdminPermission.EMAIL_BAN_READ, label: 'Read Email Bans' },
            { id: AdminPermission.EMAIL_BAN_MANAGE, label: 'Manage Email Bans' },
            { id: AdminPermission.USER_TIER_MANAGE, label: 'Manage API Tiers' },
            { id: AdminPermission.USER_PERMISSION_MANAGE, label: 'Manage Admin Permissions' },
            { id: AdminPermission.USER_RAW_READ, label: 'Read Raw User JSON' },
            { id: AdminPermission.USER_RAW_EDIT, label: 'Edit Raw User JSON' }
        ]
    },
    {
        group: 'Platform',
        permissions: [
            { id: AdminPermission.PLATFORM_ANALYTICS_READ, label: 'Read Platform Analytics' },
            { id: AdminPermission.AUDIT_LOG_READ, label: 'Read Audit Logs' },
            { id: AdminPermission.STATUS_INCIDENT_READ, label: 'Read Status Incidents' },
            { id: AdminPermission.STATUS_INCIDENT_MANAGE, label: 'Manage Status Incidents' }
        ]
    }
] as const;

type MaybeUser = {
    id?: string | null;
    roles?: string[] | null;
    adminPermissions?: string[] | null;
} | null | undefined;

const isKnownAdminPermission = (value: string): value is AdminPermission =>
    (ALL_ADMIN_PERMISSIONS as string[]).includes(value);

export const getEffectiveAdminPermissions = (user: MaybeUser): Set<AdminPermission> => {
    const permissions = new Set<AdminPermission>();
    if (!user) return permissions;

    user.adminPermissions
        ?.filter(isKnownAdminPermission)
        .forEach(permission => permissions.add(permission));

    return permissions;
};

export const hasAdminPermission = (user: MaybeUser, permission: AdminPermission): boolean =>
    getEffectiveAdminPermissions(user).has(permission);

export const hasAnyAdminPermission = (user: MaybeUser, permissions?: AdminPermission[]): boolean => {
    const effective = getEffectiveAdminPermissions(user);
    if (!permissions || permissions.length === 0) return effective.size > 0;
    return permissions.some(permission => effective.has(permission));
};

export const isAdminUser = (user: MaybeUser): boolean => hasAnyAdminPermission(user);
