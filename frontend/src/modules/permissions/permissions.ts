export enum Permission {
    PROJECT_READ = 'PROJECT_READ',
    PROJECT_CREATE = 'PROJECT_CREATE',
    PROJECT_EDIT_METADATA = 'PROJECT_EDIT_METADATA',
    PROJECT_EDIT_ICON = 'PROJECT_EDIT_ICON',
    PROJECT_EDIT_BANNER = 'PROJECT_EDIT_BANNER',
    PROJECT_DELETE = 'PROJECT_DELETE',
    PROJECT_TRANSFER_REQUEST = 'PROJECT_TRANSFER_REQUEST',
    PROJECT_TRANSFER_RESOLVE = 'PROJECT_TRANSFER_RESOLVE',
    PROJECT_FAVORITE = 'PROJECT_FAVORITE',
    PROJECT_STATUS_SUBMIT = 'PROJECT_STATUS_SUBMIT',
    PROJECT_STATUS_REVERT = 'PROJECT_STATUS_REVERT',
    PROJECT_STATUS_ARCHIVE = 'PROJECT_STATUS_ARCHIVE',
    PROJECT_STATUS_UNLIST = 'PROJECT_STATUS_UNLIST',
    PROJECT_STATUS_PUBLISH = 'PROJECT_STATUS_PUBLISH',
    PROJECT_GALLERY_ADD = 'PROJECT_GALLERY_ADD',
    PROJECT_GALLERY_REMOVE = 'PROJECT_GALLERY_REMOVE',
    PROJECT_TEAM_INVITE = 'PROJECT_TEAM_INVITE',
    PROJECT_TEAM_REMOVE = 'PROJECT_TEAM_REMOVE',
    PROJECT_MEMBER_EDIT_ROLE = 'PROJECT_MEMBER_EDIT_ROLE',
    VERSION_READ = 'VERSION_READ',
    VERSION_CREATE = 'VERSION_CREATE',
    VERSION_EDIT = 'VERSION_EDIT',
    VERSION_DELETE = 'VERSION_DELETE',
    VERSION_DOWNLOAD = 'VERSION_DOWNLOAD',
    COMMENT_READ = 'COMMENT_READ',
    COMMENT_CREATE = 'COMMENT_CREATE',
    COMMENT_EDIT = 'COMMENT_EDIT',
    COMMENT_DELETE = 'COMMENT_DELETE',
    COMMENT_REPLY = 'COMMENT_REPLY',
    ORG_READ = 'ORG_READ',
    ORG_CREATE = 'ORG_CREATE',
    ORG_EDIT_METADATA = 'ORG_EDIT_METADATA',
    ORG_EDIT_AVATAR = 'ORG_EDIT_AVATAR',
    ORG_EDIT_BANNER = 'ORG_EDIT_BANNER',
    ORG_DELETE = 'ORG_DELETE',
    ORG_MEMBER_READ = 'ORG_MEMBER_READ',
    ORG_MEMBER_INVITE = 'ORG_MEMBER_INVITE',
    ORG_MEMBER_REMOVE = 'ORG_MEMBER_REMOVE',
    ORG_MEMBER_EDIT_ROLE = 'ORG_MEMBER_EDIT_ROLE',
    ORG_INVITE_ACCEPT = 'ORG_INVITE_ACCEPT',
    ORG_INVITE_DECLINE = 'ORG_INVITE_DECLINE',
    ORG_CONNECTION_MANAGE = 'ORG_CONNECTION_MANAGE',
    PROFILE_READ = 'PROFILE_READ',
    PROFILE_EDIT_BASIC = 'PROFILE_EDIT_BASIC',
    PROFILE_EDIT_AVATAR = 'PROFILE_EDIT_AVATAR',
    PROFILE_EDIT_BANNER = 'PROFILE_EDIT_BANNER',
    PROFILE_DELETE = 'PROFILE_DELETE',
    PROFILE_FOLLOW = 'PROFILE_FOLLOW',
    PROFILE_UNFOLLOW = 'PROFILE_UNFOLLOW',
    PROFILE_CONNECTION_MANAGE = 'PROFILE_CONNECTION_MANAGE',
    PROFILE_NOTIFICATION_MANAGE = 'PROFILE_NOTIFICATION_MANAGE',
    NOTIFICATION_READ = 'NOTIFICATION_READ',
    NOTIFICATION_UPDATE = 'NOTIFICATION_UPDATE',
    NOTIFICATION_DELETE = 'NOTIFICATION_DELETE'
}

export interface PermissionDef {
    id: Permission;
    label: string;
}

export interface PermissionGroupDef {
    group: string;
    permissions: PermissionDef[];
}

export const ALL_PERMISSION_GROUPS: PermissionGroupDef[] = [
    {
        group: 'Organization Settings',
        permissions: [
            { id: Permission.ORG_READ, label: 'Read Org' },
            { id: Permission.ORG_EDIT_METADATA, label: 'Edit Profile' },
            { id: Permission.ORG_EDIT_AVATAR, label: 'Edit Avatar' },
            { id: Permission.ORG_EDIT_BANNER, label: 'Edit Banner' },
            { id: Permission.ORG_DELETE, label: 'Delete Org' },
            { id: Permission.ORG_MEMBER_READ, label: 'Read Members' },
            { id: Permission.ORG_MEMBER_INVITE, label: 'Invite Members' },
            { id: Permission.ORG_MEMBER_REMOVE, label: 'Remove Members' },
            { id: Permission.ORG_MEMBER_EDIT_ROLE, label: 'Manage Roles' },
            { id: Permission.ORG_CONNECTION_MANAGE, label: 'Manage Connections' }
        ]
    },
    {
        group: 'Project Management',
        permissions: [
            { id: Permission.PROJECT_READ, label: 'Read Projects' },
            { id: Permission.PROJECT_CREATE, label: 'Create Projects' },
            { id: Permission.PROJECT_EDIT_METADATA, label: 'Edit Metadata' },
            { id: Permission.PROJECT_EDIT_ICON, label: 'Edit Icon' },
            { id: Permission.PROJECT_EDIT_BANNER, label: 'Edit Banner' },
            { id: Permission.PROJECT_DELETE, label: 'Delete Projects' }
        ]
    },
    {
        group: 'Versions & Releases',
        permissions: [
            { id: Permission.VERSION_READ, label: 'Read Versions' },
            { id: Permission.VERSION_CREATE, label: 'Upload Versions' },
            { id: Permission.VERSION_EDIT, label: 'Edit Versions' },
            { id: Permission.VERSION_DELETE, label: 'Delete Versions' },
            { id: Permission.VERSION_DOWNLOAD, label: 'Download Files' }
        ]
    },
    {
        group: 'Visibility & Publishing',
        permissions: [
            { id: Permission.PROJECT_STATUS_SUBMIT, label: 'Submit for Review' },
            { id: Permission.PROJECT_STATUS_REVERT, label: 'Revert to Draft' },
            { id: Permission.PROJECT_STATUS_ARCHIVE, label: 'Archive Project' },
            { id: Permission.PROJECT_STATUS_UNLIST, label: 'Unlist Project' },
            { id: Permission.PROJECT_STATUS_PUBLISH, label: 'Publish Projects' }
        ]
    },
    {
        group: 'Community & Media',
        permissions: [
            { id: Permission.PROJECT_GALLERY_ADD, label: 'Add Gallery Images' },
            { id: Permission.PROJECT_GALLERY_REMOVE, label: 'Remove Gallery Images' },
            { id: Permission.COMMENT_DELETE, label: 'Delete Comments' },
            { id: Permission.COMMENT_REPLY, label: 'Reply as Developer' }
        ]
    },
    {
        group: 'Team Management',
        permissions: [
            { id: Permission.PROJECT_TEAM_INVITE, label: 'Invite Contributors' },
            { id: Permission.PROJECT_TEAM_REMOVE, label: 'Remove Contributors' },
            { id: Permission.PROJECT_MEMBER_EDIT_ROLE, label: 'Manage Roles' },
            { id: Permission.PROJECT_TRANSFER_REQUEST, label: 'Request Transfer' },
            { id: Permission.PROJECT_TRANSFER_RESOLVE, label: 'Resolve Transfer' }
        ]
    }
];

export const PROJECT_PERMISSION_GROUPS = ALL_PERMISSION_GROUPS.filter((group) => group.group !== 'Organization Settings');

export const TOTAL_PERMISSIONS = ALL_PERMISSION_GROUPS.reduce((acc, group) => acc + group.permissions.length, 0);

export const getPermissionLabel = (id: Permission): string => {
    for (const group of ALL_PERMISSION_GROUPS) {
        const permission = group.permissions.find((candidate) => candidate.id === id);
        if (permission) {
            return permission.label;
        }
    }
    return id;
};
