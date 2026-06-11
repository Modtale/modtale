import React, { useState } from 'react';
import { createPortal } from 'react-dom';
import { Plus, Settings as SettingsIcon, Trash2, X, ShieldCheck, Palette } from 'lucide-react';
import { theme } from '@/styles/theme';
import { PermissionSelector } from '@/components/ui/PermissionSelector';
import { ALL_PERMISSION_GROUPS, Permission } from '@/modules/permissions/permissions';
import { StatusModal } from '@/components/ui/StatusModal';
import { organizationClient, hasOrgPermission } from '../api/organizationClient';
import { extractApiErrorMessage } from '@/utils/api';
import type { User, OrganizationRole } from '@/types';

interface RolesProps {
    org: User;
    currentUser: User;
    onUpdateOrg: (org: User) => void;
    showStatus: (type: 'success' | 'error', title: string, msg: string) => void;
}

export const Roles: React.FC<RolesProps> = ({ org, currentUser, onUpdateOrg, showStatus }) => {
    const [editingRole, setEditingRole] = useState<Partial<OrganizationRole> | null>(null);
    const [roleModalOpen, setRoleModalOpen] = useState(false);
    const [roleToDelete, setRoleToDelete] = useState<OrganizationRole | null>(null);

    const canManageRoles = hasOrgPermission(org, currentUser.id, Permission.ORG_MEMBER_EDIT_ROLE);

    const handleSaveRole = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!editingRole?.name || !editingRole?.color) return;

        try {
            let updatedOrg;
            if (editingRole.id) {
                updatedOrg = await organizationClient.updateRole(org.id, editingRole.id, editingRole);
            } else {
                updatedOrg = await organizationClient.createRole(org.id, editingRole);
            }
            onUpdateOrg(updatedOrg);
            setRoleModalOpen(false);
            setEditingRole(null);
            showStatus('success', 'Saved', 'Role saved successfully.');
        } catch (err: unknown) {
            showStatus('error', 'Role Save Failed', extractApiErrorMessage(err, 'We could not save that organization role.'));
        }
    };

    const handleDeleteRole = async () => {
        if (!roleToDelete) return;
        try {
            const updatedOrg = await organizationClient.deleteRole(org.id, roleToDelete.id);
            onUpdateOrg(updatedOrg);
            showStatus('success', 'Deleted', 'Role deleted successfully.');
        } catch (err: unknown) {
            showStatus('error', 'Role Delete Failed', extractApiErrorMessage(err, 'We could not delete that organization role.'));
        } finally {
            setRoleToDelete(null);
        }
    };

    return (
        <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2">
            {roleToDelete && createPortal(
                <StatusModal
                    type="warning"
                    title="Delete Role?"
                    message={`Are you sure you want to delete "${roleToDelete.name}"? Members currently assigned to this role will need to be reassigned before this action can succeed.`}
                    actionLabel="Delete Role"
                    secondaryLabel="Cancel"
                    onAction={handleDeleteRole}
                    onClose={() => setRoleToDelete(null)}
                />,
                document.body
            )}
            <div className={`flex justify-between items-center ${theme.colors.bgSurface} border ${theme.colors.border} p-6 rounded-2xl shadow-sm`}>
                <div>
                    <h3 className={`font-bold text-lg ${theme.colors.textPrimary}`}>Organization Roles</h3>
                    <p className={`text-xs ${theme.colors.textMuted} mt-1`}>Manage custom roles, colors, and granular permissions for your team.</p>
                </div>
                {canManageRoles && (
                    <button onClick={() => { setEditingRole({ permissions: [] }); setRoleModalOpen(true); }} className={theme.components.buttonPrimary}>
                        <Plus className="w-4 h-4" /> New Role
                    </button>
                )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {org.organizationRoles?.map(role => {
                    const memberCount = org.organizationMembers?.filter(m => m.roleId === role.id).length || 0;
                    return (
                        <div key={role.id} className={`${theme.colors.bgSurface} border ${theme.colors.border} rounded-2xl p-5 shadow-sm hover:border-slate-300 dark:hover:border-white/20 transition-all flex flex-col h-full group`}>
                            <div className="flex justify-between items-start mb-4">
                                <div className="flex items-center gap-3">
                                    <div className="w-8 h-8 rounded-full flex items-center justify-center border border-white/10 shadow-sm flex-shrink-0" style={{ backgroundColor: role.color }}>
                                        {role.isOwner ? <ShieldCheck className="w-4 h-4 text-white opacity-90" /> : <Palette className="w-4 h-4 text-white opacity-80" />}
                                    </div>
                                    <div>
                                        <h4 className={`font-bold ${theme.colors.textPrimary} leading-tight flex items-center gap-1.5`}>{role.name}</h4>
                                        <span className={`text-[10px] ${theme.colors.textMuted} font-bold uppercase`}>{memberCount} Member{memberCount !== 1 ? 's' : ''}</span>
                                    </div>
                                </div>
                                {role.isOwner ? (
                                    <span className={`text-[9px] uppercase font-bold text-slate-400 border ${theme.colors.border} px-1.5 py-0.5 rounded ${theme.colors.bgSurfaceAlt}`}>Immutable</span>
                                ) : canManageRoles && (
                                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                        <button onClick={() => { setEditingRole(role); setRoleModalOpen(true); }} className={`p-1.5 ${theme.colors.textMuted} hover:${theme.colors.accent} ${theme.colors.accentAlpha} rounded-lg transition-colors`}>
                                            <SettingsIcon className="w-4 h-4" />
                                        </button>
                                        <button onClick={() => setRoleToDelete(role)} className={`p-1.5 ${theme.colors.textMuted} hover:${theme.colors.dangerText} hover:${theme.colors.dangerBg} rounded-lg transition-colors`}>
                                            <Trash2 className="w-4 h-4" />
                                        </button>
                                    </div>
                                )}
                            </div>

                            <div className={`mt-auto pt-4 border-t ${theme.colors.borderFaint}`}>
                                <div className={`text-[10px] ${theme.colors.textMuted} font-bold uppercase mb-2`}>Key Permissions</div>
                                {role.isOwner ? (
                                    <span className={`text-[10px] ${theme.colors.dangerText} font-bold ${theme.colors.dangerBg} border ${theme.colors.dangerBorder} px-2 py-1 rounded inline-block`}>Full Access</span>
                                ) : (
                                    <div className="flex flex-wrap gap-1.5">
                                        {(role.permissions || []).slice(0, 3).map(p => (
                                            <span key={p} className={`text-[9px] px-1.5 py-0.5 ${theme.colors.bgSurfaceAlt} border ${theme.colors.borderFaint} rounded ${theme.colors.textSecondary} truncate max-w-[120px]`}>
                                                {p.replace(/_/g, ' ')}
                                            </span>
                                        ))}
                                        {(role.permissions || []).length > 3 && (
                                            <span className={`text-[9px] px-1.5 py-0.5 ${theme.colors.bgSurfaceAlt} border ${theme.colors.borderFaint} rounded text-slate-500 font-bold`}>
                                                +{(role.permissions || []).length - 3}
                                            </span>
                                        )}
                                        {(role.permissions || []).length === 0 && (
                                            <span className={`text-xs ${theme.colors.textMuted} italic`}>No specific permissions.</span>
                                        )}
                                    </div>
                                )}
                            </div>
                        </div>
                    );
                })}
            </div>

            {roleModalOpen && editingRole && createPortal(
                <div className={theme.components.modalOverlay}>
                    <div className={`${theme.components.modalContent} w-full max-w-3xl max-h-[85vh]`}>
                        <div className={theme.components.modalHeader}>
                            <div>
                                <h3 className={`text-xl font-black ${theme.colors.textPrimary}`}>{editingRole.id ? 'Edit Role' : 'Create Role'}</h3>
                                <p className={`text-xs ${theme.colors.textMuted}`}>Configure permissions for this role.</p>
                            </div>
                            <button onClick={() => setRoleModalOpen(false)} className={`p-2 ${theme.colors.bgSurfaceHover} rounded-xl transition-colors`}><X className="w-5 h-5" /></button>
                        </div>

                        <form onSubmit={handleSaveRole} className="flex flex-col flex-1 overflow-hidden">
                            <div className={theme.components.modalBody}>
                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <label className={`block text-[10px] font-bold ${theme.colors.textMuted} uppercase tracking-widest mb-1.5 ml-1`}>Role Name</label>
                                        <input
                                            type="text"
                                            value={editingRole.name || ''}
                                            onChange={e => setEditingRole({...editingRole, name: e.target.value})}
                                            className={theme.components.inputField}
                                            required
                                        />
                                    </div>
                                    <div>
                                        <label className={`block text-[10px] font-bold ${theme.colors.textMuted} uppercase tracking-widest mb-1.5 ml-1`}>Role Color</label>
                                        <div className="flex items-center gap-3">
                                            <input
                                                type="color"
                                                value={editingRole.color || '#3b82f6'}
                                                onChange={e => setEditingRole({...editingRole, color: e.target.value})}
                                                className={`w-12 h-12 p-1 ${theme.colors.bgSurfaceAlt} border ${theme.colors.border} rounded-xl cursor-pointer`}
                                            />
                                            <input
                                                type="text"
                                                value={editingRole.color || '#3b82f6'}
                                                onChange={e => setEditingRole({...editingRole, color: e.target.value})}
                                                className={`${theme.components.inputField} flex-1 font-mono`}
                                                pattern="^#[0-9A-Fa-f]{6}$"
                                            />
                                        </div>
                                    </div>
                                </div>

                                <div className="space-y-4">
                                    <h4 className={`font-bold ${theme.colors.textPrimary} text-sm border-b ${theme.colors.borderFaint} pb-2`}>Permissions</h4>
                                    <PermissionSelector
                                        groups={ALL_PERMISSION_GROUPS}
                                        selectedPermissions={editingRole.permissions || []}
                                        onChange={(perms) => setEditingRole({ ...editingRole, permissions: perms })}
                                        variant="card"
                                    />
                                </div>
                            </div>

                            <div className={theme.components.modalFooter}>
                                <button type="button" onClick={() => setRoleModalOpen(false)} className={theme.components.buttonSecondary}>Cancel</button>
                                <button type="submit" className={theme.components.buttonPrimary}>Save Role</button>
                            </div>
                        </form>
                    </div>
                </div>,
                document.body
            )}
        </div>
    );
};
