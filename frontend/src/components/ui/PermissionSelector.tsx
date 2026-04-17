import React from 'react';

export interface PermissionDef {
    id: string;
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
            { id: 'ORG_READ', label: 'Read Org' },
            { id: 'ORG_EDIT_METADATA', label: 'Edit Profile' },
            { id: 'ORG_EDIT_AVATAR', label: 'Edit Avatar' },
            { id: 'ORG_EDIT_BANNER', label: 'Edit Banner' },
            { id: 'ORG_DELETE', label: 'Delete Org' },
            { id: 'ORG_MEMBER_READ', label: 'Read Members' },
            { id: 'ORG_MEMBER_INVITE', label: 'Invite Members' },
            { id: 'ORG_MEMBER_REMOVE', label: 'Remove Members' },
            { id: 'ORG_MEMBER_EDIT_ROLE', label: 'Manage Roles' },
            { id: 'ORG_CONNECTION_MANAGE', label: 'Manage Connections' }
        ]
    },
    {
        group: 'Project Management',
        permissions: [
            { id: 'PROJECT_READ', label: 'Read Projects' },
            { id: 'PROJECT_CREATE', label: 'Create Projects' },
            { id: 'PROJECT_EDIT_METADATA', label: 'Edit Metadata' },
            { id: 'PROJECT_EDIT_ICON', label: 'Edit Icon' },
            { id: 'PROJECT_EDIT_BANNER', label: 'Edit Banner' },
            { id: 'PROJECT_DELETE', label: 'Delete Projects' }
        ]
    },
    {
        group: 'Versions & Releases',
        permissions: [
            { id: 'VERSION_READ', label: 'Read Versions' },
            { id: 'VERSION_CREATE', label: 'Upload Versions' },
            { id: 'VERSION_EDIT', label: 'Edit Versions' },
            { id: 'VERSION_DELETE', label: 'Delete Versions' },
            { id: 'VERSION_DOWNLOAD', label: 'Download Files' }
        ]
    },
    {
        group: 'Visibility & Publishing',
        permissions: [
            { id: 'PROJECT_STATUS_SUBMIT', label: 'Submit for Review' },
            { id: 'PROJECT_STATUS_REVERT', label: 'Revert to Draft' },
            { id: 'PROJECT_STATUS_ARCHIVE', label: 'Archive Project' },
            { id: 'PROJECT_STATUS_UNLIST', label: 'Unlist Project' },
            { id: 'PROJECT_STATUS_PUBLISH', label: 'Publish Projects' }
        ]
    },
    {
        group: 'Community & Media',
        permissions: [
            { id: 'PROJECT_GALLERY_ADD', label: 'Add Gallery Images' },
            { id: 'PROJECT_GALLERY_REMOVE', label: 'Remove Gallery Images' },
            { id: 'COMMENT_DELETE', label: 'Delete Comments' },
            { id: 'COMMENT_REPLY', label: 'Reply as Developer' }
        ]
    },
    {
        group: 'Team Management',
        permissions: [
            { id: 'PROJECT_TEAM_INVITE', label: 'Invite Contributors' },
            { id: 'PROJECT_TEAM_REMOVE', label: 'Remove Contributors' },
            { id: 'PROJECT_MEMBER_EDIT_ROLE', label: 'Manage Roles' },
            { id: 'PROJECT_TRANSFER_REQUEST', label: 'Request Transfer' },
            { id: 'PROJECT_TRANSFER_RESOLVE', label: 'Resolve Transfer' }
        ]
    }
];

export const PROJECT_PERMISSION_GROUPS = ALL_PERMISSION_GROUPS.filter(g => g.group !== 'Organization Settings');

export const TOTAL_PERMISSIONS = ALL_PERMISSION_GROUPS.reduce((acc, group) => acc + group.permissions.length, 0);

export const getPermissionLabel = (id: string) => {
    for (const group of ALL_PERMISSION_GROUPS) {
        const p = group.permissions.find(p => p.id === id);
        if (p) return p.label;
    }
    return id;
};

interface PermissionSelectorProps {
    groups: PermissionGroupDef[];
    selectedPermissions: string[];
    onChange: (permissions: string[]) => void;
    disabled?: boolean;
    variant?: 'card' | 'panel';
    className?: string;
}

export const PermissionSelector: React.FC<PermissionSelectorProps> = ({
                                                                          groups,
                                                                          selectedPermissions,
                                                                          onChange,
                                                                          disabled = false,
                                                                          variant = 'card',
                                                                          className = ''
                                                                      }) => {
    const togglePerm = (id: string) => {
        if (disabled) return;
        if (selectedPermissions.includes(id)) {
            onChange(selectedPermissions.filter(p => p !== id));
        } else {
            onChange([...selectedPermissions, id]);
        }
    };

    const toggleAllInGroup = (groupPermissions: PermissionDef[]) => {
        if (disabled) return;
        const groupIds = groupPermissions.map(p => p.id);
        const allSelected = groupIds.every(id => selectedPermissions.includes(id));

        if (allSelected) {
            onChange(selectedPermissions.filter(id => !groupIds.includes(id)));
        } else {
            onChange(Array.from(new Set([...selectedPermissions, ...groupIds])));
        }
    };

    const containerClasses = variant === 'panel'
        ? `columns-1 md:columns-2 lg:columns-3 gap-4 space-y-4 max-h-[420px] overflow-y-auto pr-2 pb-4 custom-scrollbar bg-slate-100/50 dark:bg-black/20 rounded-2xl p-4 ${className}`
        : `grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 ${className}`;

    const groupClasses = variant === 'panel'
        ? 'break-inside-avoid flex flex-col bg-white dark:bg-[#1a1f2e] rounded-xl border border-slate-200 dark:border-white/10 shadow-sm overflow-hidden'
        : 'bg-slate-50 dark:bg-white/5 rounded-xl border border-slate-200 dark:border-white/10 overflow-hidden self-start';

    const headerClasses = variant === 'panel'
        ? 'flex items-center justify-between bg-slate-50 dark:bg-white/5 px-3 py-2.5 border-b border-slate-200 dark:border-white/10'
        : 'flex items-center justify-between bg-slate-100 dark:bg-white/5 px-3 py-2';

    const headerTextClasses = variant === 'panel'
        ? 'text-xs font-bold text-slate-700 dark:text-slate-200 uppercase tracking-widest'
        : 'text-[10px] font-bold text-slate-500 uppercase tracking-widest';

    return (
        <div className={containerClasses}>
            {groups.map((group, idx) => (
                <div key={idx} className={groupClasses}>
                    <div className={headerClasses}>
                        <span className={headerTextClasses}>{group.group}</span>
                        {!disabled && (
                            <button
                                type="button"
                                onClick={() => toggleAllInGroup(group.permissions)}
                                className="text-[10px] text-modtale-accent hover:text-modtale-accentHover transition-colors font-bold uppercase tracking-wider"
                            >
                                Toggle
                            </button>
                        )}
                    </div>
                    <div className="p-2 space-y-0.5">
                        {group.permissions.map(perm => (
                            <label key={perm.id} className={`flex items-center gap-3 px-2.5 py-2 rounded-lg transition-colors group/label border border-transparent ${disabled ? 'opacity-50 cursor-not-allowed' : 'hover:bg-slate-50 dark:hover:bg-white/5 cursor-pointer hover:border-slate-100 dark:hover:border-white/5'}`}>
                                <input
                                    type="checkbox"
                                    checked={selectedPermissions.includes(perm.id)}
                                    onChange={() => togglePerm(perm.id)}
                                    disabled={disabled}
                                    className={`w-4 h-4 shrink-0 text-modtale-accent border-slate-300 dark:border-slate-600 rounded focus:ring-modtale-accent focus:ring-offset-0 bg-white dark:bg-black/40 transition-all ${disabled ? '' : 'cursor-pointer'}`}
                                />
                                <span className="text-sm font-medium text-slate-600 dark:text-slate-300 group-hover/label:text-slate-900 dark:group-hover/label:text-white transition-colors select-none">{perm.label}</span>
                            </label>
                        ))}
                    </div>
                </div>
            ))}
        </div>
    );
};