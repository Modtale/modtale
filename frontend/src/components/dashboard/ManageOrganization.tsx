import React, { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { api, BACKEND_URL } from '../../utils/api.ts';
import type {User, Mod, OrganizationRole} from '../../types.ts';
import { Plus, Building2, Trash2, ChevronDown, Loader2, ArrowLeft, Settings, Upload, Image as ImageIcon, Eye, Shield, ShieldOff, ShieldCheck, UserPlus, Link as LinkIcon, EyeOff, Check, Github, Twitter, Palette, X } from 'lucide-react';
import { ErrorBanner } from '../ui/error/ErrorBanner.tsx';
import { StatusModal } from '../ui/StatusModal.tsx';
import { ImageCropperModal } from '../ui/ImageCropperModal.tsx';
import { ProjectListItem } from '@/components/dashboard/manage-projects/ProjectListItem.tsx';
import { TransferProjectModal } from '@/components/dashboard/manage-projects/TransferProjectModal.tsx';

const GitLabIcon = ({ className }: { className?: string }) => (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
        <path d="M22.65 14.39L12 22.13L1.35 14.39L4.74 3.99C4.82 3.73 5.12 3.63 5.33 3.82L8.99 7.5L12 10.5L15.01 7.5L18.67 3.82C18.88 3.63 19.18 3.73 19.26 3.99L22.65 14.39Z" />
    </svg>
);

const BlueskyIcon = ({ className }: { className?: string }) => (
    <svg className={className} viewBox="0 0 568 501" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
        <path d="M123.121 33.664C188.241 83.564 263.357 167.332 284 200.793C304.643 167.332 379.759 83.564 444.879 33.664C497.868 -6.932 568 -22.108 568 46.54V218.456C568 243.66 550.05 266.304 525.669 271.936L429.574 294.116C408.665 298.944 397.697 323.76 411.39 340.948C447.869 386.724 513.799 432.892 531.867 447.668C564.128 474.056 544.721 526 502.981 526H463.317C433.09 526 404.931 513.292 386.324 490.308C363.393 461.98 322.99 401.7 284 345.244C245.01 401.7 204.607 461.98 181.676 490.308C163.069 513.292 134.91 526 104.683 526H65.019C23.279 526 3.872 474.056 36.133 447.668C54.201 432.892 120.131 386.724 156.61 340.948C170.303 323.76 159.335 298.944 138.426 294.116L42.331 271.936C17.95 266.304 0 243.66 0 218.456V46.54C0 -22.108 70.132 -6.932 123.121 33.664Z" transform="scale(0.85) translate(10, -20)"/>
    </svg>
);

const PERMISSION_GROUPS = [
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

interface ManageOrganizationProps {
    user: User;
}

export const ManageOrganization: React.FC<ManageOrganizationProps> = ({ user }) => {
    const [orgs, setOrgs] = useState<User[]>([]);
    const [loading, setLoading] = useState(true);
    const [selectedOrg, setSelectedOrg] = useState<User | null>(null);
    const [members, setMembers] = useState<User[]>([]);
    const [invites, setInvites] = useState<User[]>([]);
    const [orgProjects, setOrgProjects] = useState<Mod[]>([]);
    const [activeTab, setActiveTab] = useState<'MEMBERS' | 'ROLES' | 'PROJECTS' | 'SETTINGS'>('MEMBERS');

    const prevOrgIdRef = useRef<string | null>(null);

    const [isCreating, setIsCreating] = useState(false);
    const [newOrgName, setNewOrgName] = useState('');
    const [createError, setCreateError] = useState<string | null>(null);

    const [inviteUsername, setInviteUsername] = useState('');
    const [inviteUserId, setInviteUserId] = useState('');
    const [inviteRoleId, setInviteRoleId] = useState('');
    const [manageError, setManageError] = useState<string | null>(null);
    const [userSearchResults, setUserSearchResults] = useState<User[]>([]);
    const [isSearchingUsers, setIsSearchingUsers] = useState(false);

    const [inviteRoleDropdownOpen, setInviteRoleDropdownOpen] = useState(false);
    const [memberRoleDropdownOpen, setMemberRoleDropdownOpen] = useState<string | null>(null);

    const [editingRole, setEditingRole] = useState<Partial<OrganizationRole> | null>(null);
    const [roleModalOpen, setRoleModalOpen] = useState(false);

    const [displayName, setDisplayName] = useState('');
    const [bio, setBio] = useState('');
    const [savingSettings, setSavingSettings] = useState(false);
    const [deleteModalOpen, setDeleteModalOpen] = useState(false);
    const [showUnlinkModal, setShowUnlinkModal] = useState<{provider: string, label: string} | null>(null);

    const [deleteProjectModal, setDeleteProjectModal] = useState<Mod | null>(null);
    const [transferModal, setTransferModal] = useState<Mod | null>(null);
    const [status, setStatus] = useState<{type: 'success'|'error', title: string, msg: string} | null>(null);

    const [memberToRemove, setMemberToRemove] = useState<string | null>(null);

    const [cropperOpen, setCropperOpen] = useState(false);
    const [tempImage, setTempImage] = useState<string | null>(null);
    const [cropType, setCropType] = useState<'avatar' | 'banner'>('avatar');

    const avatarInputRef = useRef<HTMLInputElement>(null);
    const bannerInputRef = useRef<HTMLInputElement>(null);

    const isModalOpen = roleModalOpen || deleteModalOpen || showUnlinkModal !== null || deleteProjectModal !== null || memberToRemove !== null || status !== null || transferModal !== null || cropperOpen || isCreating;

    useEffect(() => {
        if (isModalOpen) {
            document.body.style.overflow = 'hidden';
        } else {
            document.body.style.overflow = '';
        }
        return () => {
            document.body.style.overflow = '';
        };
    }, [isModalOpen]);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            const target = e.target as HTMLElement;
            if (!target.closest('.modtale-dropdown-container')) {
                setInviteRoleDropdownOpen(false);
                setMemberRoleDropdownOpen(null);
                setUserSearchResults([]);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    useEffect(() => {
        fetchOrgs();
    }, []);

    useEffect(() => {
        if (selectedOrg) {
            if (prevOrgIdRef.current !== selectedOrg.id) {
                fetchMembers(selectedOrg.id);
                fetchInvites(selectedOrg.id);
                fetchProjects(selectedOrg.id);
                setDisplayName(selectedOrg.username);
                setBio(selectedOrg.bio || '');
                setActiveTab('MEMBERS');
                prevOrgIdRef.current = selectedOrg.id;
            } else {
                setDisplayName(selectedOrg.username);
                setBio(selectedOrg.bio || '');
            }
        } else {
            prevOrgIdRef.current = null;
        }
    }, [selectedOrg]);

    useEffect(() => {
        if (!inviteUsername || inviteUsername.length < 2 || inviteUserId) {
            setUserSearchResults([]);
            return;
        }
        const delayDebounceFn = setTimeout(async () => {
            setIsSearchingUsers(true);
            try {
                const res = await api.get(`/users/search?query=${inviteUsername}`);
                setUserSearchResults(res.data);
            } catch (e) { /* ignore */ }
            finally { setIsSearchingUsers(false); }
        }, 300);
        return () => clearTimeout(delayDebounceFn);
    }, [inviteUsername, inviteUserId]);

    const fetchOrgs = async () => {
        try {
            const res = await api.get('/user/orgs');
            setOrgs(res.data);
        } catch (e) { console.error(e); }
        finally { setLoading(false); }
    };

    const fetchMembers = async (orgId: string) => {
        try {
            const res = await api.get(`/orgs/${orgId}/members`);
            setMembers(res.data);
        } catch (e) { console.error(e); }
    };

    const fetchInvites = async (orgId: string) => {
        try {
            const res = await api.get(`/orgs/${orgId}/invites`);
            setInvites(res.data);
        } catch (e) { console.error(e); }
    };

    const fetchProjects = async (orgId: string) => {
        try {
            const res = await api.get(`/creators/${orgId}/projects?size=100`);
            setOrgProjects(res.data.content || []);
        } catch (e) { console.error(e); }
    };

    const hasOrgPermission = (perm: string) => {
        if (!selectedOrg) return false;
        const member = selectedOrg.organizationMembers?.find(m => m.userId === user.id);
        if (!member) return false;

        if (member.roleId && selectedOrg.organizationRoles) {
            const role = selectedOrg.organizationRoles.find(r => r.id === member.roleId);
            if (role) {
                if (role.isOwner) return true;
                if (role.permissions && role.permissions.includes(perm)) return true;
            }
        }
        return false;
    };

    const handleCreateOrg = async (e: React.FormEvent) => {
        e.preventDefault();
        setCreateError(null);
        try {
            const res = await api.post('/orgs', { name: newOrgName });
            setOrgs([...orgs, res.data]);
            setNewOrgName('');
            setIsCreating(false);
        } catch (err: any) {
            setCreateError(err.response?.data || "Failed to create organization.");
        }
    };

    const handleAddMember = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedOrg || !inviteUserId || !inviteRoleId) return;
        setManageError(null);
        try {
            await api.post(`/orgs/${selectedOrg.id}/members`, { userId: inviteUserId, roleId: inviteRoleId });
            await fetchInvites(selectedOrg.id);
            setInviteUsername('');
            setInviteUserId('');
            setUserSearchResults([]);
            setStatus({ type: 'success', title: 'Invited', msg: 'Member invitation sent successfully.' });
        } catch (err: any) { setManageError(err.response?.data || "Failed to add member."); }
    };

    const confirmRemoveMember = async () => {
        if (!selectedOrg || !memberToRemove) return;
        try {
            await api.delete(`/orgs/${selectedOrg.id}/members/${memberToRemove}`);
            await fetchMembers(selectedOrg.id);
            if (memberToRemove === user.id) {
                setSelectedOrg(null);
                fetchOrgs();
            }
        } catch (err: any) { setManageError(err.response?.data || "Failed to remove member."); }
        finally { setMemberToRemove(null); }
    };

    const handleCancelInvite = async (userId: string) => {
        if (!selectedOrg) return;
        try {
            await api.delete(`/orgs/${selectedOrg.id}/invites/${userId}`);
            await fetchInvites(selectedOrg.id);
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Cancel Failed', msg: e.response?.data || "Could not cancel invite." });
        }
    };

    const handleSaveRole = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedOrg || !editingRole?.name || !editingRole?.color) return;

        try {
            let updatedOrg;
            if (editingRole.id) {
                const res = await api.put(`/orgs/${selectedOrg.id}/roles/${editingRole.id}`, editingRole);
                updatedOrg = res.data;
            } else {
                const res = await api.post(`/orgs/${selectedOrg.id}/roles`, editingRole);
                updatedOrg = res.data;
            }
            setSelectedOrg(updatedOrg);
            setOrgs(prev => prev.map(o => o.id === selectedOrg.id ? updatedOrg : o));
            setRoleModalOpen(false);
            setEditingRole(null);
            setStatus({ type: 'success', title: 'Saved', msg: 'Role saved successfully.' });
        } catch (err: any) {
            setStatus({ type: 'error', title: 'Error', msg: err.response?.data || "Failed to save role." });
        }
    };

    const handleDeleteRole = async (roleId: string) => {
        if (!selectedOrg) return;
        try {
            const res = await api.delete(`/orgs/${selectedOrg.id}/roles/${roleId}`);
            setSelectedOrg(res.data);
            setOrgs(prev => prev.map(o => o.id === selectedOrg.id ? res.data : o));
            setStatus({ type: 'success', title: 'Deleted', msg: 'Role deleted successfully.' });
        } catch (err: any) {
            setStatus({ type: 'error', title: 'Delete Failed', msg: err.response?.data || "Cannot delete role." });
        }
    };

    const handleRoleUpdate = async (userId: string, newRoleId: string) => {
        if (!selectedOrg) return;
        try {
            await api.put(`/orgs/${selectedOrg.id}/members/${userId}`, { roleId: newRoleId });
            const updatedOrg = { ...selectedOrg };
            const memberIdx = updatedOrg.organizationMembers?.findIndex(m => m.userId === userId);
            if (memberIdx !== undefined && memberIdx > -1 && updatedOrg.organizationMembers) {
                updatedOrg.organizationMembers[memberIdx].roleId = newRoleId;
                setSelectedOrg(updatedOrg);
            }
            await fetchMembers(selectedOrg.id);
        } catch (err: any) {
            setStatus({ type: 'error', title: 'Update Failed', msg: err.response?.data || "Failed to update member role." });
        }
    };

    const handleUpdateProfile = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedOrg) return;
        setSavingSettings(true);
        try {
            const res = await api.put(`/orgs/${selectedOrg.id}`, { displayName, bio });
            setSelectedOrg(res.data);
            setOrgs(prev => prev.map(o => o.id === res.data.id ? res.data : o));
            setStatus({ type: 'success', title: 'Profile Updated', msg: 'Organization settings saved successfully.' });
        } catch (err: any) {
            setStatus({ type: 'error', title: 'Update Failed', msg: err.response?.data || "Failed to update profile." });
        } finally {
            setSavingSettings(false);
        }
    };

    const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>, type: 'avatar' | 'banner') => {
        if (e.target.files && e.target.files[0]) {
            const file = e.target.files[0];
            setTempImage(URL.createObjectURL(file));
            setCropType(type);
            setCropperOpen(true);
            e.target.value = '';
        }
    };

    const handleCropComplete = async (croppedFile: File) => {
        if (!selectedOrg) return;
        const formData = new FormData();
        formData.append('file', croppedFile);

        const uploadConfig = { headers: { 'Content-Type': 'multipart/form-data' } };

        try {
            const res = await api.post(`/orgs/${selectedOrg.id}/${cropType}`, formData, uploadConfig);
            if (cropType === 'avatar') {
                setSelectedOrg(prev => prev ? { ...prev, avatarUrl: res.data } : null);
                setOrgs(prev => prev.map(o => o.id === selectedOrg.id ? { ...o, avatarUrl: res.data } : o));
            } else {
                setSelectedOrg(prev => prev ? { ...prev, bannerUrl: res.data } : null);
                setOrgs(prev => prev.map(o => o.id === selectedOrg.id ? { ...o, bannerUrl: res.data } : o));
            }
        } catch (err: any) {
            setStatus({ type: 'error', title: 'Upload Failed', msg: err.response?.data || `Failed to upload ${cropType}.` });
        } finally {
            setCropperOpen(false);
            setTempImage(null);
        }
    };

    const handleDeleteOrg = async () => {
        if (!selectedOrg) return;
        try {
            await api.delete(`/orgs/${selectedOrg.id}`);
            setOrgs(prev => prev.filter(o => o.id !== selectedOrg.id));
            setSelectedOrg(null);
            setDeleteModalOpen(false);
        } catch (err: any) {
            setStatus({ type: 'error', title: 'Delete Failed', msg: err.response?.data || "Failed to delete organization." });
        }
    };

    const handleDeleteProject = async () => {
        if (!deleteProjectModal || !selectedOrg) return;
        try {
            await api.delete(`/projects/${deleteProjectModal.id}`);
            setOrgProjects(prev => prev.filter(p => p.id !== deleteProjectModal.id));
            setDeleteProjectModal(null);
            setStatus({ type: 'success', title: 'Deleted', msg: "Project deleted successfully." });
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Delete Failed', msg: e.response?.data || "Could not delete project." });
        }
    };

    const handleOrgLink = async (provider: string) => {
        if(!selectedOrg) return;
        try {
            await api.post(`/orgs/${selectedOrg.id}/link/prepare`);
            window.location.href = `${BACKEND_URL}/oauth2/authorization/${provider}`;
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Linking Failed', msg: e.response?.data || "Could not initiate account linking." });
        }
    };

    const handleOrgUnlink = async () => {
        if (!showUnlinkModal || !selectedOrg) return;
        try {
            await api.delete(`/orgs/${selectedOrg.id}/connections/${showUnlinkModal.provider}`);
            const updatedOrg = { ...selectedOrg };
            updatedOrg.connectedAccounts = updatedOrg.connectedAccounts?.filter(a => a.provider !== showUnlinkModal.provider);
            setSelectedOrg(updatedOrg);
            setOrgs(prev => prev.map(o => o.id === selectedOrg.id ? updatedOrg : o));
            setShowUnlinkModal(null);
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Unlink Failed', msg: e.response?.data || "Failed to unlink account." });
            setShowUnlinkModal(null);
        }
    };

    const handleToggleOrgVisibility = async (provider: string) => {
        if (!selectedOrg) return;
        try {
            await api.post(`/orgs/${selectedOrg.id}/connections/${provider}/toggle-visibility`);
            const updatedOrg = { ...selectedOrg };
            if(updatedOrg.connectedAccounts) {
                const acc = updatedOrg.connectedAccounts.find(a => a.provider === provider);
                if(acc) acc.visible = !acc.visible;
            }
            setSelectedOrg(updatedOrg);
            setOrgs(prev => prev.map(o => o.id === selectedOrg.id ? updatedOrg : o));
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Update Failed', msg: e.response?.data || "Failed to toggle visibility." });
        }
    };

    const AccountRow = ({ provider, icon: Icon, label }: { provider: string, icon: any, label: string }) => {
        if(!selectedOrg) return null;
        const accounts = selectedOrg.connectedAccounts || [];
        const account = accounts.find(a => a.provider === provider);
        const isLinked = !!account;
        const canBeVisible = true;
        const canManage = hasOrgPermission('ORG_CONNECTION_MANAGE');

        return (
            <div className={`flex items-center justify-between p-3 rounded-xl border transition-all h-full ${isLinked ? 'bg-white/60 dark:bg-white/5 border-modtale-accent/30 shadow-sm' : 'bg-white/40 dark:bg-white/[0.02] border-slate-200 dark:border-white/5'}`}>
                <div className="flex items-center gap-3">
                    <div className={`p-2 rounded-lg ${isLinked ? 'text-modtale-accent bg-modtale-accent/10' : 'text-slate-400 bg-slate-200/50 dark:bg-white/5'}`}>
                        <Icon className="w-4 h-4" />
                    </div>
                    <div>
                        <h4 className="font-bold text-[10px] text-slate-900 dark:text-white uppercase tracking-wider">{label}</h4>
                        {isLinked ? (
                            <p className="text-[10px] text-green-600 dark:text-green-400 font-bold flex items-center gap-1 mt-0.5">
                                <Check className="w-3 h-3" /> {account.username}
                            </p>
                        ) : (
                            <p className="text-[10px] text-slate-400 mt-0.5">Not connected</p>
                        )}
                    </div>
                </div>
                {isLinked ? (
                    <div className="flex gap-1.5">
                        {canBeVisible ? (
                            <button
                                onClick={() => canManage && handleToggleOrgVisibility(provider)}
                                disabled={!canManage}
                                className={`p-1.5 rounded-lg transition-colors ${!canManage ? 'opacity-50 cursor-not-allowed' : account.visible ? 'text-modtale-accent bg-modtale-accent/10 hover:bg-modtale-accent/20' : 'text-slate-400 hover:bg-slate-200/50 dark:hover:bg-white/10'}`}
                            >
                                {account.visible ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}
                            </button>
                        ) : (
                            <div className="p-1.5 text-slate-300 dark:text-white/10 cursor-not-allowed" title="Private connection">
                                <EyeOff className="w-3.5 h-3.5" />
                            </div>
                        )}
                        <button
                            onClick={() => setShowUnlinkModal({ provider, label })}
                            disabled={!canManage}
                            className={`p-1.5 rounded-lg transition-colors ${canManage ? 'text-red-500 hover:bg-red-50 dark:hover:bg-red-950/30' : 'text-slate-300 opacity-50 cursor-not-allowed'}`}
                        >
                            <Trash2 className="w-3.5 h-3.5" />
                        </button>
                    </div>
                ) : (
                    canManage && (
                        <button
                            onClick={() => handleOrgLink(provider)}
                            className="bg-white/60 dark:bg-white/10 border border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-200 px-3 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-widest hover:border-modtale-accent hover:text-modtale-accent transition-all flex items-center gap-1.5 shadow-sm"
                        >
                            <Plus className="w-3 h-3" /> Link
                        </button>
                    )
                )}
            </div>
        );
    };

    if (loading) return <div className="flex justify-center py-20"><Loader2 className="w-8 h-8 animate-spin text-modtale-accent" /></div>;

    if (selectedOrg) {
        const canManageRoles = hasOrgPermission('ORG_MEMBER_EDIT_ROLE');
        const canInvite = hasOrgPermission('ORG_MEMBER_INVITE');
        const canRemove = hasOrgPermission('ORG_MEMBER_REMOVE');
        const canEditProfile = hasOrgPermission('ORG_EDIT_METADATA');

        const myMember = selectedOrg.organizationMembers?.find(m => m.userId === user.id);
        const myRole = selectedOrg.organizationRoles?.find(r => r.id === myMember?.roleId);

        const nonOwnerRoles = selectedOrg.organizationRoles?.filter(r => !r.isOwner) || [];
        const hasRoles = nonOwnerRoles.length > 0;

        return (
            <div className="space-y-6 relative">
                {(inviteRoleDropdownOpen || memberRoleDropdownOpen) && (
                    <div className="fixed inset-0 z-[90]" onClick={() => { setInviteRoleDropdownOpen(false); setMemberRoleDropdownOpen(null); }} />
                )}

                {status && createPortal(
                    <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} />,
                    document.body
                )}

                {deleteModalOpen && createPortal(
                    <StatusModal
                        type="error"
                        title="Delete Organization"
                        message={`Are you sure you want to delete "${selectedOrg.username}"? This cannot be undone.`}
                        onClose={() => setDeleteModalOpen(false)}
                        actionLabel="Delete Forever"
                        onAction={handleDeleteOrg}
                        secondaryLabel="Cancel"
                    />,
                    document.body
                )}

                {showUnlinkModal && createPortal(
                    <StatusModal
                        type="warning"
                        title={`Unlink ${showUnlinkModal.label}?`}
                        message={`Are you sure you want to unlink the ${showUnlinkModal.label} account from this organization?`}
                        actionLabel="Yes, Unlink"
                        onAction={handleOrgUnlink}
                        onClose={() => setShowUnlinkModal(null)}
                        secondaryLabel="Cancel"
                    />,
                    document.body
                )}

                {deleteProjectModal && createPortal(
                    <StatusModal
                        type="error"
                        title="Delete Project?"
                        message={`Are you sure you want to delete "${deleteProjectModal.title}"? This cannot be undone.`}
                        actionLabel="Delete"
                        onAction={handleDeleteProject}
                        onClose={() => setDeleteProjectModal(null)}
                        secondaryLabel="Cancel"
                    />,
                    document.body
                )}

                {memberToRemove && createPortal(
                    <StatusModal
                        type="warning"
                        title="Remove Member?"
                        message="Are you sure you want to remove this member from the organization?"
                        actionLabel="Remove"
                        onAction={confirmRemoveMember}
                        secondaryLabel="Cancel"
                        onClose={() => setMemberToRemove(null)}
                    />,
                    document.body
                )}

                {roleModalOpen && editingRole && createPortal(
                    <div className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-in fade-in zoom-in-95 duration-200">
                        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-3xl w-full max-w-3xl shadow-2xl flex flex-col max-h-[85vh] overflow-hidden">
                            <div className="flex justify-between items-center p-6 border-b border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-white/5">
                                <div>
                                    <h3 className="text-xl font-black text-slate-900 dark:text-white">{editingRole.id ? 'Edit Role' : 'Create Role'}</h3>
                                    <p className="text-xs text-slate-500">Configure permissions for this role.</p>
                                </div>
                                <button onClick={() => setRoleModalOpen(false)} className="p-2 text-slate-400 hover:text-slate-700 dark:hover:text-white transition-colors bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl"><X className="w-5 h-5" /></button>
                            </div>

                            <form onSubmit={handleSaveRole} className="flex flex-col flex-1 overflow-hidden">
                                <div className="flex-1 overflow-y-auto custom-scrollbar p-6 space-y-6">
                                    <div className="grid grid-cols-2 gap-4">
                                        <div>
                                            <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1.5 ml-1">Role Name</label>
                                            <input
                                                type="text"
                                                value={editingRole.name || ''}
                                                onChange={e => setEditingRole({...editingRole, name: e.target.value})}
                                                className="w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 outline-none focus:ring-2 focus:ring-modtale-accent dark:text-white font-medium"
                                                required
                                            />
                                        </div>
                                        <div>
                                            <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1.5 ml-1">Role Color</label>
                                            <div className="flex items-center gap-3">
                                                <input
                                                    type="color"
                                                    value={editingRole.color || '#3b82f6'}
                                                    onChange={e => setEditingRole({...editingRole, color: e.target.value})}
                                                    className="w-12 h-12 p-1 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl cursor-pointer"
                                                />
                                                <input
                                                    type="text"
                                                    value={editingRole.color || '#3b82f6'}
                                                    onChange={e => setEditingRole({...editingRole, color: e.target.value})}
                                                    className="flex-1 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 outline-none focus:ring-2 focus:ring-modtale-accent dark:text-white font-mono text-sm"
                                                    pattern="^#[0-9A-Fa-f]{6}$"
                                                />
                                            </div>
                                        </div>
                                    </div>

                                    <div className="space-y-4">
                                        <h4 className="font-bold text-slate-900 dark:text-white text-sm border-b border-slate-200 dark:border-white/10 pb-2">Permissions</h4>
                                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                            {PERMISSION_GROUPS.map((group, idx) => (
                                                <div key={idx} className="bg-slate-50 dark:bg-white/5 rounded-xl border border-slate-200 dark:border-white/10 overflow-hidden self-start">
                                                    <div className="bg-slate-100 dark:bg-white/5 px-3 py-2 text-[10px] font-bold uppercase tracking-widest text-slate-500">{group.group}</div>
                                                    <div className="p-2 space-y-1">
                                                        {group.permissions.map(perm => (
                                                            <label key={perm.id} className="flex items-center gap-3 p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-white/5 cursor-pointer group/label border border-transparent hover:border-slate-200 dark:hover:border-white/5 transition-colors">
                                                                <input
                                                                    type="checkbox"
                                                                    checked={(editingRole.permissions || []).includes(perm.id)}
                                                                    onChange={(e) => {
                                                                        const cur = editingRole.permissions || [];
                                                                        setEditingRole({
                                                                            ...editingRole,
                                                                            permissions: e.target.checked ? [...cur, perm.id] : cur.filter(p => p !== perm.id)
                                                                        });
                                                                    }}
                                                                    className="w-4 h-4 text-modtale-accent border-slate-300 dark:border-slate-600 rounded focus:ring-modtale-accent focus:ring-offset-0 bg-white dark:bg-black/40 cursor-pointer"
                                                                />
                                                                <span className="text-[11px] font-medium text-slate-600 dark:text-slate-300 group-hover/label:text-slate-900 dark:group-hover/label:text-white transition-colors">{perm.label}</span>
                                                            </label>
                                                        ))}
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                </div>

                                <div className="flex justify-end gap-3 p-6 border-t border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-white/5">
                                    <button type="button" onClick={() => setRoleModalOpen(false)} className="px-5 py-2.5 font-bold text-slate-500 hover:text-slate-800 dark:hover:text-white transition-colors bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl">Cancel</button>
                                    <button type="submit" className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 py-2.5 rounded-xl font-bold shadow-lg transition-all active:scale-95 flex items-center gap-2">Save Role</button>
                                </div>
                            </form>
                        </div>
                    </div>,
                    document.body
                )}

                {transferModal && createPortal(
                    <TransferProjectModal
                        project={transferModal}
                        myOrgs={orgs}
                        onClose={() => setTransferModal(null)}
                        onSuccess={(msg) => setStatus({ type: 'success', title: 'Request Sent', msg })}
                        onError={(msg) => setStatus({ type: 'error', title: 'Transfer Failed', msg })}
                    />,
                    document.body
                )}

                {cropperOpen && tempImage && createPortal(
                    <ImageCropperModal
                        imageSrc={tempImage}
                        aspect={cropType === 'banner' ? 3 : 1}
                        onCancel={() => { setCropperOpen(false); setTempImage(null); }}
                        onCropComplete={handleCropComplete}
                    />,
                    document.body
                )}

                <div className="flex items-center gap-4 mb-4">
                    <button onClick={() => setSelectedOrg(null)} className="p-2 hover:bg-slate-200/50 dark:hover:bg-white/5 rounded-xl transition-colors">
                        <ArrowLeft className="w-5 h-5" />
                    </button>
                    <div>
                        <h2 className="text-2xl font-black text-slate-900 dark:text-white flex items-center gap-3">
                            {selectedOrg.username}
                            <span className="text-xs bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-300 px-2 py-0.5 rounded uppercase tracking-wider font-bold border border-purple-200 dark:border-purple-800/30">Organization</span>
                        </h2>
                        <p className="text-slate-500">Manage organization settings and members</p>
                    </div>
                </div>

                <div className="flex gap-4 border-b border-slate-200 dark:border-white/10">
                    {['MEMBERS', 'ROLES', 'PROJECTS', 'SETTINGS'].map(tab => (
                        <button
                            key={tab}
                            onClick={() => setActiveTab(tab as any)}
                            className={`pb-3 px-1 font-bold text-sm transition-colors border-b-2 ${activeTab === tab ? 'border-modtale-accent text-modtale-accent' : 'border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-white'}`}
                        >
                            {tab.charAt(0) + tab.slice(1).toLowerCase()}
                        </button>
                    ))}
                </div>

                {activeTab === 'MEMBERS' && (
                    <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2">
                        {manageError && <ErrorBanner message={manageError} />}

                        {canInvite && (
                            <div className="relative z-50 bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 p-6 rounded-2xl shadow-sm backdrop-blur-md">
                                <div className="flex items-center gap-3 mb-6">
                                    <div className="w-10 h-10 rounded-xl bg-modtale-accent/10 flex items-center justify-center text-modtale-accent">
                                        <UserPlus className="w-5 h-5" />
                                    </div>
                                    <div>
                                        <h3 className="font-bold text-lg text-slate-900 dark:text-white">Invite New Member</h3>
                                        <p className="text-xs text-slate-500">Send an invitation to join this organization.</p>
                                    </div>
                                </div>

                                {hasRoles ? (
                                    <form onSubmit={handleAddMember} className="flex flex-col md:flex-row gap-4 items-end">
                                        <div className="flex-1 w-full relative modtale-dropdown-container">
                                            <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1.5 ml-1">Username</label>
                                            <input
                                                type="text"
                                                placeholder="Search username..."
                                                value={inviteUsername}
                                                onChange={e => { setInviteUsername(e.target.value); setInviteUserId(''); }}
                                                className="w-full bg-white/60 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 outline-none focus:ring-2 focus:ring-modtale-accent transition-all dark:text-white font-medium shadow-inner"
                                            />

                                            {userSearchResults.length > 0 && (
                                                <div className="absolute top-full left-0 right-0 mt-2 bg-white/90 dark:bg-slate-900/90 backdrop-blur-xl border border-slate-200 dark:border-white/10 rounded-xl shadow-xl overflow-hidden animate-in fade-in zoom-in-95">
                                                    {userSearchResults.map(res => (
                                                        <button
                                                            key={res.id}
                                                            type="button"
                                                            onClick={() => { setInviteUsername(res.username); setInviteUserId(res.id); setUserSearchResults([]); }}
                                                            className="w-full flex items-center gap-3 p-3 hover:bg-slate-100 dark:hover:bg-white/10 transition-colors text-left"
                                                        >
                                                            <div className="w-8 h-8 rounded-full bg-slate-200 overflow-hidden"><img src={res.avatarUrl} className="w-full h-full object-cover" /></div>
                                                            <span className="font-bold text-sm text-slate-900 dark:text-white">{res.username}</span>
                                                        </button>
                                                    ))}
                                                </div>
                                            )}
                                        </div>

                                        <div className="w-full md:w-64 relative modtale-dropdown-container">
                                            <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1.5 ml-1">Role</label>
                                            <button
                                                type="button"
                                                onClick={() => setInviteRoleDropdownOpen(!inviteRoleDropdownOpen)}
                                                className="w-full bg-white/60 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 outline-none focus:ring-2 focus:ring-modtale-accent dark:text-white font-medium shadow-inner flex justify-between items-center"
                                            >
                                                {inviteRoleId ? (
                                                    <div className="flex items-center gap-2 truncate">
                                                        <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{backgroundColor: nonOwnerRoles.find(r => r.id === inviteRoleId)?.color}} />
                                                        <span className="truncate">{nonOwnerRoles.find(r => r.id === inviteRoleId)?.name}</span>
                                                    </div>
                                                ) : (
                                                    <span className="text-slate-400">Select Role...</span>
                                                )}
                                                <ChevronDown className={`w-4 h-4 text-slate-400 flex-shrink-0 transition-transform ${inviteRoleDropdownOpen ? 'rotate-180' : ''}`} />
                                            </button>

                                            {inviteRoleDropdownOpen && (
                                                <div className="absolute top-full left-0 right-0 mt-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl overflow-hidden animate-in fade-in zoom-in-95">
                                                    <div className="max-h-48 overflow-y-auto custom-scrollbar py-1">
                                                        {nonOwnerRoles.map(role => (
                                                            <button
                                                                key={role.id}
                                                                type="button"
                                                                onClick={() => { setInviteRoleId(role.id); setInviteRoleDropdownOpen(false); }}
                                                                className="w-full flex items-center justify-between px-3 py-2.5 hover:bg-slate-100 dark:hover:bg-white/10 transition-colors text-left"
                                                            >
                                                                <div className="flex items-center gap-2 truncate">
                                                                    <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{backgroundColor: role.color}} />
                                                                    <span className="font-bold text-sm text-slate-900 dark:text-white truncate">{role.name}</span>
                                                                </div>
                                                                {inviteRoleId === role.id && <Check className="w-4 h-4 text-modtale-accent flex-shrink-0" />}
                                                            </button>
                                                        ))}
                                                    </div>
                                                </div>
                                            )}
                                        </div>

                                        <button
                                            type="submit"
                                            disabled={!inviteUserId || !inviteRoleId}
                                            className="w-full md:w-auto bg-slate-900 dark:bg-white text-white dark:text-slate-900 font-bold px-8 py-3 rounded-xl hover:opacity-90 transition-opacity disabled:opacity-50 flex items-center justify-center gap-2 shadow-lg h-11"
                                        >
                                            <Plus className="w-4 h-4" /> Invite
                                        </button>
                                    </form>
                                ) : (
                                    <div className="text-sm text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/10 border border-amber-200 dark:border-amber-800/30 p-4 rounded-xl flex items-center gap-2 font-medium">
                                        <Shield className="w-5 h-5" /> You must create at least one Role before inviting members.
                                    </div>
                                )}
                            </div>
                        )}

                        <div className="relative z-10 bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden shadow-sm backdrop-blur-md">
                            <div className="px-6 py-5 border-b border-slate-200 dark:border-white/10 flex justify-between items-center">
                                <h3 className="font-bold text-slate-900 dark:text-white">Active Members</h3>
                                <span className="text-xs font-bold bg-slate-200/50 dark:bg-white/10 px-2 py-1 rounded-lg text-slate-600 dark:text-slate-400">{members.length}</span>
                            </div>
                            <div className="divide-y divide-slate-200 dark:divide-white/10">
                                {members.map(member => {
                                    const membership = selectedOrg.organizationMembers?.find(m => m.userId === member.id);
                                    const role = selectedOrg.organizationRoles?.find(r => r.id === membership?.roleId);
                                    const isMe = member.id === user.id;
                                    const isOwner = role?.isOwner;

                                    return (
                                        <div key={member.id} className="p-5 flex items-center justify-between hover:bg-white/50 dark:hover:bg-white/[0.02] transition-colors group">
                                            <div className="flex items-center gap-4">
                                                <div className="w-10 h-10 rounded-full overflow-hidden bg-slate-100 border border-slate-200 dark:border-white/10">
                                                    <img src={member.avatarUrl} alt="" className="w-full h-full object-cover" />
                                                </div>
                                                <div>
                                                    <div className="font-bold text-slate-900 dark:text-white text-sm">{member.username}</div>
                                                    <div className="flex items-center gap-2 mt-1">
                                                        {role ? (
                                                            <div className="flex items-center gap-1.5 border border-slate-200 dark:border-white/10 bg-white/60 dark:bg-black/20 px-2 py-0.5 rounded-md">
                                                                <div className="w-2 h-2 rounded-full" style={{ backgroundColor: role.color }} />
                                                                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-300">{role.name}</span>
                                                            </div>
                                                        ) : (
                                                            <span className="text-[10px] font-black uppercase tracking-wider px-1.5 py-0.5 rounded border bg-slate-100 border-slate-200 text-slate-600 dark:bg-white/5 dark:border-white/10 dark:text-slate-400">Legacy Member</span>
                                                        )}
                                                        {isMe && <span className="text-[10px] text-slate-400 font-medium italic">(You)</span>}
                                                    </div>
                                                </div>
                                            </div>

                                            <div className="flex items-center gap-3">
                                                {canManageRoles && !isMe && (
                                                    <div className="relative modtale-dropdown-container">
                                                        <button
                                                            onClick={() => setMemberRoleDropdownOpen(memberRoleDropdownOpen === member.id ? null : member.id)}
                                                            disabled={isOwner && !myRole?.isOwner}
                                                            className={`flex items-center justify-between min-w-[140px] bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-lg pl-3 pr-2 py-1.5 outline-none hover:border-modtale-accent transition-colors ${isOwner && !myRole?.isOwner ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer shadow-sm'}`}
                                                        >
                                                            <div className="flex items-center gap-2 truncate">
                                                                {role && <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: role.color }} />}
                                                                <span className="text-xs font-bold text-slate-600 dark:text-slate-300 truncate">
                                                                    {role ? role.name : 'Select Role'}
                                                                </span>
                                                            </div>
                                                            <ChevronDown className={`w-3 h-3 text-slate-400 ml-2 flex-shrink-0 transition-transform ${memberRoleDropdownOpen === member.id ? 'rotate-180' : ''}`} />
                                                        </button>

                                                        {memberRoleDropdownOpen === member.id && (
                                                            <div className="absolute right-0 top-full mt-2 w-48 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-[100] overflow-hidden animate-in fade-in zoom-in-95">
                                                                <div className="max-h-48 overflow-y-auto custom-scrollbar py-1">
                                                                    {selectedOrg.organizationRoles?.filter(r => !r.isOwner || isOwner || myRole?.isOwner).map(r => (
                                                                        <button
                                                                            key={r.id}
                                                                            onClick={() => {
                                                                                handleRoleUpdate(member.id, r.id);
                                                                                setMemberRoleDropdownOpen(null);
                                                                            }}
                                                                            className="w-full flex items-center justify-between px-3 py-2 hover:bg-slate-100 dark:hover:bg-white/10 transition-colors text-left"
                                                                        >
                                                                            <div className="flex items-center gap-2 truncate">
                                                                                <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{backgroundColor: r.color}} />
                                                                                <span className="font-bold text-xs text-slate-900 dark:text-white truncate">{r.name}</span>
                                                                            </div>
                                                                            {membership?.roleId === r.id && <Check className="w-3 h-3 text-modtale-accent flex-shrink-0" />}
                                                                        </button>
                                                                    ))}
                                                                </div>
                                                            </div>
                                                        )}
                                                    </div>
                                                )}

                                                {(canRemove || isMe) && (
                                                    <div className="relative group/tooltip">
                                                        <button
                                                            onClick={() => !isOwner && setMemberToRemove(member.id)}
                                                            disabled={isOwner}
                                                            className={`p-2 rounded-xl transition-all ${isOwner ? 'text-slate-300 cursor-not-allowed' : 'text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20'}`}
                                                            title={isMe ? "Leave Organization" : "Remove Member"}
                                                        >
                                                            <Trash2 className="w-4 h-4" />
                                                        </button>

                                                        {isOwner && (
                                                            <div className="absolute bottom-full right-0 mb-2 w-48 bg-slate-900 text-white text-xs p-2 rounded-xl shadow-lg opacity-0 group-hover/tooltip:opacity-100 pointer-events-none transition-opacity text-center z-10">
                                                                The Owner cannot be removed. Transfer ownership first.
                                                            </div>
                                                        )}
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>

                        {invites.length > 0 && (
                            <div className="relative z-0 bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden shadow-sm backdrop-blur-md">
                                <div className="px-6 py-5 border-b border-slate-200 dark:border-white/10 flex justify-between items-center">
                                    <h3 className="font-bold text-slate-900 dark:text-white">Pending Invites</h3>
                                    <span className="text-xs font-bold bg-slate-200/50 dark:bg-white/10 px-2 py-1 rounded-lg text-slate-600 dark:text-slate-400">{invites.length}</span>
                                </div>
                                <div className="divide-y divide-slate-200 dark:divide-white/10">
                                    {invites.map(inviteUser => {
                                        const inviteData = selectedOrg.pendingOrgInvites?.find(i => i.userId === inviteUser.id);
                                        const inviteRole = selectedOrg.organizationRoles?.find(r => r.id === inviteData?.roleId);

                                        return (
                                            <div key={inviteUser.id} className="p-5 flex items-center justify-between hover:bg-white/50 dark:hover:bg-white/[0.02] transition-colors">
                                                <div className="flex items-center gap-4">
                                                    <div className="w-10 h-10 rounded-full overflow-hidden bg-slate-100 border border-slate-200 dark:border-white/10 opacity-70 grayscale">
                                                        <img src={inviteUser.avatarUrl} alt="" className="w-full h-full object-cover" />
                                                    </div>
                                                    <div>
                                                        <div className="font-bold text-slate-900 dark:text-white text-sm">{inviteUser.username}</div>
                                                        <div className="flex items-center gap-2 mt-1">
                                                            <span className="text-[10px] font-black uppercase tracking-wider px-1.5 py-0.5 rounded border bg-amber-50 border-amber-200 text-amber-600 dark:bg-amber-900/20 dark:border-amber-700/30 dark:text-amber-400">Pending</span>
                                                            {inviteRole && (
                                                                <span className="text-[10px] text-slate-500 font-medium">As {inviteRole.name}</span>
                                                            )}
                                                        </div>
                                                    </div>
                                                </div>

                                                {canInvite && (
                                                    <button onClick={() => handleCancelInvite(inviteUser.id)} className="text-xs font-bold text-red-500 hover:text-red-600 bg-red-50 dark:bg-red-900/10 hover:bg-red-100 dark:hover:bg-red-900/30 px-3 py-1.5 rounded-lg border border-red-200 dark:border-red-900/30 transition-colors">
                                                        Cancel
                                                    </button>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {activeTab === 'ROLES' && (
                    <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2">
                        <div className="flex justify-between items-center bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 p-6 rounded-2xl shadow-sm backdrop-blur-md">
                            <div>
                                <h3 className="font-bold text-lg text-slate-900 dark:text-white">Organization Roles</h3>
                                <p className="text-xs text-slate-500 mt-1">Manage custom roles, colors, and granular permissions for your team.</p>
                            </div>
                            {canManageRoles && (
                                <button onClick={() => { setEditingRole({ permissions: [] }); setRoleModalOpen(true); }} className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-5 py-2.5 rounded-xl font-bold flex items-center gap-2 shadow-lg transition-all">
                                    <Plus className="w-4 h-4" /> New Role
                                </button>
                            )}
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {selectedOrg.organizationRoles?.map(role => {
                                const memberCount = selectedOrg.organizationMembers?.filter(m => m.roleId === role.id).length || 0;
                                return (
                                    <div key={role.id} className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl p-5 shadow-sm hover:border-slate-300 dark:hover:border-white/20 transition-all flex flex-col h-full group">
                                        <div className="flex justify-between items-start mb-4">
                                            <div className="flex items-center gap-3">
                                                <div className="w-8 h-8 rounded-full flex items-center justify-center border border-white/10 shadow-sm flex-shrink-0" style={{ backgroundColor: role.color }}>
                                                    {role.isOwner ? <ShieldCheck className="w-4 h-4 text-white opacity-90" /> : <Palette className="w-4 h-4 text-white opacity-80" />}
                                                </div>
                                                <div>
                                                    <h4 className="font-bold text-slate-900 dark:text-white leading-tight flex items-center gap-1.5">
                                                        {role.name}
                                                    </h4>
                                                    <span className="text-[10px] text-slate-500 font-bold uppercase">{memberCount} Member{memberCount !== 1 ? 's' : ''}</span>
                                                </div>
                                            </div>
                                            {role.isOwner ? (
                                                <span className="text-[9px] uppercase font-bold text-slate-400 border border-slate-200 dark:border-white/10 px-1.5 py-0.5 rounded bg-slate-50 dark:bg-white/5">Immutable</span>
                                            ) : canManageRoles && (
                                                <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                                    <button onClick={() => { setEditingRole(role); setRoleModalOpen(true); }} className="p-1.5 text-slate-400 hover:text-modtale-accent hover:bg-modtale-accent/10 rounded-lg transition-colors">
                                                        <Settings className="w-4 h-4" />
                                                    </button>
                                                    <button onClick={() => handleDeleteRole(role.id)} className="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-500/10 rounded-lg transition-colors">
                                                        <Trash2 className="w-4 h-4" />
                                                    </button>
                                                </div>
                                            )}
                                        </div>

                                        <div className="mt-auto pt-4 border-t border-slate-200 dark:border-white/5">
                                            <div className="text-[10px] text-slate-500 font-bold uppercase mb-2">Key Permissions</div>
                                            {role.isOwner ? (
                                                <span className="text-[10px] text-red-500 font-bold bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 px-2 py-1 rounded inline-block">Full Access</span>
                                            ) : (
                                                <div className="flex flex-wrap gap-1.5">
                                                    {(role.permissions || []).slice(0, 3).map(p => (
                                                        <span key={p} className="text-[9px] px-1.5 py-0.5 bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded text-slate-600 dark:text-slate-300 truncate max-w-[120px]">
                                                            {p.replace(/_/g, ' ')}
                                                        </span>
                                                    ))}
                                                    {(role.permissions || []).length > 3 && (
                                                        <span className="text-[9px] px-1.5 py-0.5 bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded text-slate-500 font-bold">
                                                            +{(role.permissions || []).length - 3}
                                                        </span>
                                                    )}
                                                    {(role.permissions || []).length === 0 && (
                                                        <span className="text-xs text-slate-400 italic">No specific permissions.</span>
                                                    )}
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                )}

                {activeTab === 'PROJECTS' && (
                    <div className="space-y-4 animate-in fade-in slide-in-from-bottom-2">
                        {orgProjects.length > 0 ? (
                            <div className="grid grid-cols-1 gap-4">
                                {orgProjects.map(project => (
                                    <div key={project.id} className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden backdrop-blur-md shadow-sm">
                                        <ProjectListItem
                                            project={project}
                                            canManage={hasOrgPermission('PROJECT_EDIT_METADATA')}
                                            isOwner={false}
                                            showAuthor={false}
                                            onTransfer={setTransferModal}
                                            onDelete={setDeleteProjectModal}
                                        />
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <div className="text-center py-12 text-slate-400 bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl backdrop-blur-md shadow-sm">
                                No projects found.
                            </div>
                        )}
                    </div>
                )}

                {activeTab === 'SETTINGS' && canEditProfile && (
                    <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2">
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                            <div className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 p-6 rounded-2xl shadow-sm backdrop-blur-md">
                                <h3 className="font-bold mb-4">Organization Icon</h3>
                                <div className="flex items-center gap-4">
                                    <img src={selectedOrg.avatarUrl} alt="" className="w-16 h-16 rounded-2xl object-cover bg-slate-100 border border-slate-200 dark:border-white/10 shadow-sm" />
                                    <div>
                                        <input type="file" ref={avatarInputRef} onChange={e => handleFileSelect(e, 'avatar')} className="hidden" accept="image/*" />
                                        <button onClick={() => avatarInputRef.current?.click()} className="flex items-center gap-2 px-4 py-2 bg-slate-200/50 dark:bg-white/5 rounded-xl text-sm font-bold hover:bg-slate-200 dark:hover:bg-white/10 transition-colors border border-slate-200 dark:border-white/5 shadow-sm">
                                            <Upload className="w-4 h-4" /> Upload
                                        </button>
                                    </div>
                                </div>
                            </div>
                            <div className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 p-6 rounded-2xl shadow-sm backdrop-blur-md">
                                <h3 className="font-bold mb-4">Profile Banner</h3>
                                <div className="flex items-center gap-4">
                                    <div className="w-32 h-16 rounded-xl overflow-hidden bg-slate-100 relative border border-slate-200 dark:border-white/10 shadow-sm">
                                        {selectedOrg.bannerUrl && <img src={selectedOrg.bannerUrl} alt="" className="w-full h-full object-cover" />}
                                    </div>
                                    <div>
                                        <input type="file" ref={bannerInputRef} onChange={e => handleFileSelect(e, 'banner')} className="hidden" accept="image/*" />
                                        <button onClick={() => bannerInputRef.current?.click()} className="flex items-center gap-2 px-4 py-2 bg-slate-200/50 dark:bg-white/5 rounded-xl text-sm font-bold hover:bg-slate-200 dark:hover:bg-white/10 transition-colors border border-slate-200 dark:border-white/5 shadow-sm">
                                            <ImageIcon className="w-4 h-4" /> Upload
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 p-6 rounded-2xl shadow-sm backdrop-blur-md">
                            <h3 className="font-bold text-lg mb-4 flex items-center gap-2"><Settings className="w-5 h-5 text-slate-400" /> General Settings</h3>
                            <form onSubmit={handleUpdateProfile} className="space-y-4">
                                <div>
                                    <label className="block text-xs font-bold uppercase text-slate-500 mb-1">Organization Name (Username)</label>
                                    <input type="text" value={displayName} onChange={e => setDisplayName(e.target.value)} className="w-full bg-white/60 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-modtale-accent dark:text-white shadow-inner" />
                                    <p className="text-[10px] text-slate-400 mt-1">This is your unique organization identifier.</p>
                                </div>
                                <div>
                                    <label className="block text-xs font-bold uppercase text-slate-500 mb-1">Bio</label>
                                    <textarea value={bio} onChange={e => setBio(e.target.value)} rows={3} className="w-full bg-white/60 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-modtale-accent dark:text-white shadow-inner" />
                                </div>
                                <div className="flex justify-end">
                                    <button type="submit" disabled={savingSettings} className="bg-modtale-accent text-white font-bold px-6 py-2.5 rounded-xl hover:bg-modtale-accentHover transition-colors disabled:opacity-50 shadow-lg shadow-modtale-accent/20">{savingSettings ? 'Saving...' : 'Save Changes'}</button>
                                </div>
                            </form>
                        </div>

                        <div className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 p-6 rounded-2xl shadow-sm backdrop-blur-md">
                            <div className="flex items-center gap-3 mb-4">
                                <LinkIcon className="w-5 h-5 text-modtale-accent" />
                                <div>
                                    <h3 className="font-bold text-lg text-slate-900 dark:text-white">Connected Accounts</h3>
                                    <p className="text-xs text-slate-500">Link organization accounts for imports and credibility.</p>
                                </div>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                                <AccountRow provider="github" icon={Github} label="GitHub" />
                                <AccountRow provider="gitlab" icon={GitLabIcon} label="GitLab" />
                                <AccountRow provider="twitter" icon={Twitter} label="X / Twitter" />
                                <AccountRow provider="bluesky" icon={BlueskyIcon} label="Bluesky" />
                            </div>
                        </div>

                        {hasOrgPermission('ORG_DELETE') && (
                            <div className="border border-red-200 dark:border-red-900/30 bg-red-50/40 dark:bg-red-900/10 p-6 rounded-2xl backdrop-blur-md shadow-sm">
                                <h3 className="font-bold text-red-600 dark:text-red-400 mb-2">Danger Zone</h3>
                                <p className="text-sm text-red-500/80 mb-4">Deleting this organization will permanently remove it and unlist all associated projects.</p>
                                <button onClick={() => setDeleteModalOpen(true)} className="bg-white dark:bg-red-900/20 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-900/30 px-5 py-2.5 rounded-xl font-bold text-sm hover:bg-red-50 dark:hover:bg-red-900/30 transition-colors shadow-sm">Delete Organization</button>
                            </div>
                        )}
                    </div>
                )}
                {activeTab === 'SETTINGS' && !canEditProfile && (
                    <div className="text-center py-10 text-slate-500">You do not have permission to edit this organization.</div>
                )}
            </div>
        );
    }

    return (
        <div className="space-y-6 relative">
            {isCreating && <div className="fixed inset-0 z-[100]" />}
            <div className="flex justify-between items-center mb-6 relative z-[101]">
                <h1 className="text-2xl font-black text-slate-900 dark:text-white">Organizations</h1>
                <button onClick={() => setIsCreating(true)} className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-5 py-2.5 rounded-xl font-bold flex items-center gap-2 transition-colors shadow-lg shadow-modtale-accent/20"><Plus className="w-4 h-4" /> New Org</button>
            </div>

            {isCreating && createPortal(
                <div className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-in fade-in zoom-in-95 duration-200">
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 p-6 rounded-3xl w-full max-w-lg shadow-2xl flex flex-col">
                        <div className="flex justify-between items-center mb-6">
                            <h3 className="font-black text-xl text-slate-900 dark:text-white">Create Organization</h3>
                            <button onClick={() => setIsCreating(false)} className="text-slate-400 hover:text-slate-700 dark:hover:text-white transition-colors"><X className="w-5 h-5" /></button>
                        </div>
                        {createError && <ErrorBanner message={createError} className="mb-4" />}
                        <form onSubmit={handleCreateOrg} className="flex flex-col gap-4">
                            <div className="space-y-1.5">
                                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-widest px-1">Organization Name</label>
                                <input type="text" placeholder="e.g. Modtale Team" value={newOrgName} onChange={e => setNewOrgName(e.target.value)} className="w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 outline-none focus:ring-2 focus:ring-modtale-accent transition-all dark:text-white shadow-inner" autoFocus />
                            </div>
                            <div className="flex justify-end gap-3 mt-2">
                                <button type="button" onClick={() => setIsCreating(false)} className="px-5 py-2.5 font-bold text-slate-500 hover:text-slate-800 dark:hover:text-white transition-colors bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl">Cancel</button>
                                <button type="submit" disabled={!newOrgName} className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 py-2.5 rounded-xl font-bold shadow-lg active:scale-95 transition-all disabled:opacity-50 disabled:cursor-not-allowed">Create</button>
                            </div>
                        </form>
                    </div>
                </div>,
                document.body
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 relative z-0">
                {orgs.map(org => {
                    const member = org.organizationMembers?.find(m => m.userId === user.id);
                    const role = org.organizationRoles?.find(r => r.id === member?.roleId);

                    return (
                        <div key={org.id} onClick={() => setSelectedOrg(org)} className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 p-5 rounded-2xl shadow-sm hover:border-modtale-accent dark:hover:border-modtale-accent cursor-pointer transition-all group backdrop-blur-md">
                            <div className="flex items-center justify-between mb-4">
                                <div className="w-12 h-12 bg-slate-200/50 dark:bg-white/5 rounded-xl flex items-center justify-center text-slate-400 group-hover:text-modtale-accent border border-slate-200 dark:border-white/10 transition-colors">
                                    {org.avatarUrl ? <img src={org.avatarUrl} className="w-full h-full object-cover rounded-xl" /> : <Building2 className="w-6 h-6" />}
                                </div>
                                {role ? (
                                    <div className="flex items-center gap-1.5 border border-slate-200 dark:border-white/10 bg-white/60 dark:bg-black/20 px-2 py-1 rounded-md">
                                        <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: role.color }} />
                                        <span className="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-300">{role.name}</span>
                                    </div>
                                ) : (
                                    <span className="text-[10px] font-bold uppercase tracking-wider bg-slate-200/50 dark:bg-white/10 border border-slate-200 dark:border-white/5 text-slate-500 px-2 py-1 rounded-md">Legacy Member</span>
                                )}
                            </div>
                            <h3 className="text-lg font-bold text-slate-900 dark:text-white mb-1">{org.username}</h3>
                        </div>
                    );
                })}
            </div>
        </div>
    );
};