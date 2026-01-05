import React, { useState, useEffect, useRef } from 'react';
import { api, BACKEND_URL } from '../../utils/api.ts';
import type {User, Mod} from '../../types.ts';
import { Plus, Building2, Trash2, ChevronDown, Loader2, ArrowLeft, Settings, Upload, Image as ImageIcon, Eye, Shield, ShieldOff, ShieldCheck, UserPlus, Link as LinkIcon, EyeOff, Check, Github, Twitter } from 'lucide-react';
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

interface ManageOrganizationProps {
    user: User;
}

export const ManageOrganization: React.FC<ManageOrganizationProps> = ({ user }) => {
    const [orgs, setOrgs] = useState<User[]>([]);
    const [loading, setLoading] = useState(true);
    const [selectedOrg, setSelectedOrg] = useState<User | null>(null);
    const [members, setMembers] = useState<User[]>([]);
    const [orgProjects, setOrgProjects] = useState<Mod[]>([]);
    const [activeTab, setActiveTab] = useState<'MEMBERS' | 'PROJECTS' | 'SETTINGS'>('MEMBERS');

    const prevOrgIdRef = useRef<string | null>(null);

    const [isCreating, setIsCreating] = useState(false);
    const [newOrgName, setNewOrgName] = useState('');
    const [createError, setCreateError] = useState<string | null>(null);

    const [inviteUsername, setInviteUsername] = useState('');
    const [inviteRole, setInviteRole] = useState<'MEMBER' | 'ADMIN'>('MEMBER');
    const [manageError, setManageError] = useState<string | null>(null);
    const [userSearchResults, setUserSearchResults] = useState<User[]>([]);
    const [isSearchingUsers, setIsSearchingUsers] = useState(false);
    const [roleDropdownOpen, setRoleDropdownOpen] = useState(false);
    const roleDropdownRef = useRef<HTMLDivElement>(null);

    const [displayName, setDisplayName] = useState('');
    const [bio, setBio] = useState('');
    const [savingSettings, setSavingSettings] = useState(false);
    const [deleteModalOpen, setDeleteModalOpen] = useState(false);
    const [showUnlinkModal, setShowUnlinkModal] = useState<{provider: string, label: string} | null>(null);

    const [deleteProjectModal, setDeleteProjectModal] = useState<Mod | null>(null);
    const [transferModal, setTransferModal] = useState<Mod | null>(null);
    const [status, setStatus] = useState<{type: 'success'|'error', title: string, msg: string} | null>(null);

    const [cropperOpen, setCropperOpen] = useState(false);
    const [tempImage, setTempImage] = useState<string | null>(null);
    const [cropType, setCropType] = useState<'avatar' | 'banner'>('avatar');

    const avatarInputRef = useRef<HTMLInputElement>(null);
    const bannerInputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        fetchOrgs();
    }, []);

    useEffect(() => {
        if (selectedOrg) {
            if (prevOrgIdRef.current !== selectedOrg.id) {
                fetchMembers(selectedOrg.username);
                fetchProjects(selectedOrg.username);
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
        const handleClickOutside = (event: MouseEvent) => {
            if (roleDropdownRef.current && !roleDropdownRef.current.contains(event.target as Node)) {
                setRoleDropdownOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    useEffect(() => {
        if (!inviteUsername || inviteUsername.length < 2) {
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
    }, [inviteUsername]);

    const fetchOrgs = async () => {
        try {
            const res = await api.get('/user/orgs');
            setOrgs(res.data);
        } catch (e) { console.error(e); }
        finally { setLoading(false); }
    };

    const fetchMembers = async (username: string) => {
        try {
            const res = await api.get(`/orgs/${username}/members`);
            setMembers(res.data);
        } catch (e) { console.error(e); }
    };

    const fetchProjects = async (username: string) => {
        try {
            const res = await api.get(`/creators/${username}/projects?size=100`);
            setOrgProjects(res.data.content || []);
        } catch (e) { console.error(e); }
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
        if (!selectedOrg) return;
        setManageError(null);
        try {
            await api.post(`/orgs/${selectedOrg.id}/members`, { username: inviteUsername, role: inviteRole });
            await fetchMembers(selectedOrg.username);
            setInviteUsername('');
            setUserSearchResults([]);
        } catch (err: any) { setManageError(err.response?.data || "Failed to add member."); }
    };

    const handleRemoveMember = async (userId: string) => {
        if (!selectedOrg) return;
        if (!confirm("Remove this member?")) return;
        try {
            await api.delete(`/orgs/${selectedOrg.id}/members/${userId}`);
            await fetchMembers(selectedOrg.username);
            if (userId === user.id) {
                setSelectedOrg(null);
                fetchOrgs();
            }
        } catch (err: any) { setManageError(err.response?.data || "Failed to remove member."); }
    };

    const handleRoleUpdate = async (userId: string, currentRole: string) => {
        if (!selectedOrg) return;
        const newRole = currentRole === 'ADMIN' ? 'MEMBER' : 'ADMIN';
        const action = currentRole === 'ADMIN' ? 'Demote to Member' : 'Promote to Admin';

        if (!confirm(`Are you sure you want to ${action.toLowerCase()} this user?`)) return;

        try {
            await api.put(`/orgs/${selectedOrg.id}/members/${userId}`, { role: newRole });
            const updatedOrg = { ...selectedOrg };
            const memberIdx = updatedOrg.organizationMembers?.findIndex(m => m.userId === userId);
            if (memberIdx !== undefined && memberIdx > -1 && updatedOrg.organizationMembers) {
                updatedOrg.organizationMembers[memberIdx].role = newRole as "ADMIN" | "MEMBER";
                setSelectedOrg(updatedOrg);
            }
            await fetchMembers(selectedOrg.username);
        } catch (err: any) {
            setManageError(err.response?.data || "Failed to update role.");
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

        return (
            <div className={`flex items-center justify-between p-3 rounded-xl border transition-all h-full ${isLinked ? 'bg-white dark:bg-white/5 border-modtale-accent/30' : 'bg-slate-50 dark:bg-white/[0.02] border-slate-200 dark:border-white/5'}`}>
                <div className="flex items-center gap-3">
                    <div className={`p-2 rounded-lg ${isLinked ? 'text-modtale-accent bg-modtale-accent/10' : 'text-slate-400 bg-white dark:bg-white/5'}`}>
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
                                onClick={() => handleToggleOrgVisibility(provider)}
                                className={`p-1.5 rounded-lg transition-colors ${account.visible ? 'text-modtale-accent bg-modtale-accent/10 hover:bg-modtale-accent/20' : 'text-slate-400 hover:bg-slate-100 dark:hover:bg-white/10'}`}
                                title={account.visible ? "Publicly Visible" : "Hidden from profile"}
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
                            className="p-1.5 text-red-500 hover:bg-red-50 dark:hover:bg-red-950/30 rounded-lg transition-colors"
                            title="Unlink"
                        >
                            <Trash2 className="w-3.5 h-3.5" />
                        </button>
                    </div>
                ) : (
                    <button
                        onClick={() => handleOrgLink(provider)}
                        className="bg-white dark:bg-white/10 border border-slate-200 dark:border-white/10 text-slate-700 dark:text-slate-200 px-3 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-widest hover:border-modtale-accent hover:text-modtale-accent transition-all flex items-center gap-1.5 shadow-sm"
                    >
                        <Plus className="w-3 h-3" /> Link
                    </button>
                )}
            </div>
        );
    };

    if (loading) return <div className="flex justify-center py-20"><Loader2 className="w-8 h-8 animate-spin text-modtale-accent" /></div>;

    if (selectedOrg) {
        const myRole = selectedOrg.organizationMembers?.find(m => m.userId === user.id)?.role || 'MEMBER';
        const isAdmin = myRole === 'ADMIN';
        const isLastMember = members.length <= 1;

        return (
            <div className="space-y-6">
                {status && <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} />}

                {deleteModalOpen && (
                    <StatusModal
                        type="error"
                        title="Delete Organization"
                        message={`Are you sure you want to delete "${selectedOrg.username}"? This cannot be undone.`}
                        onClose={() => setDeleteModalOpen(false)}
                        actionLabel="Delete Forever"
                        onAction={handleDeleteOrg}
                        secondaryLabel="Cancel"
                    />
                )}

                {showUnlinkModal && (
                    <StatusModal
                        type="warning"
                        title={`Unlink ${showUnlinkModal.label}?`}
                        message={`Are you sure you want to unlink the ${showUnlinkModal.label} account from this organization?`}
                        actionLabel="Yes, Unlink"
                        onAction={handleOrgUnlink}
                        onClose={() => setShowUnlinkModal(null)}
                        secondaryLabel="Cancel"
                    />
                )}

                {deleteProjectModal && (
                    <StatusModal
                        type="error"
                        title="Delete Project?"
                        message={`Are you sure you want to delete "${deleteProjectModal.title}"? This cannot be undone.`}
                        actionLabel="Delete"
                        onAction={handleDeleteProject}
                        onClose={() => setDeleteProjectModal(null)}
                        secondaryLabel="Cancel"
                    />
                )}

                {transferModal && (
                    <TransferProjectModal
                        project={transferModal}
                        onClose={() => setTransferModal(null)}
                        onSuccess={(msg) => setStatus({ type: 'success', title: 'Request Sent', msg })}
                        onError={(msg) => setStatus({ type: 'error', title: 'Transfer Failed', msg })}
                    />
                )}

                {cropperOpen && tempImage && (
                    <ImageCropperModal
                        imageSrc={tempImage}
                        aspect={cropType === 'banner' ? 3 : 1}
                        onCancel={() => { setCropperOpen(false); setTempImage(null); }}
                        onCropComplete={handleCropComplete}
                    />
                )}

                <div className="flex items-center gap-4 mb-4">
                    <button onClick={() => setSelectedOrg(null)} className="p-2 hover:bg-slate-100 dark:hover:bg-white/5 rounded-lg transition-colors">
                        <ArrowLeft className="w-5 h-5" />
                    </button>
                    <div>
                        <h2 className="text-2xl font-black text-slate-900 dark:text-white flex items-center gap-3">
                            {selectedOrg.username}
                            <span className="text-xs bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-300 px-2 py-0.5 rounded uppercase tracking-wider font-bold">Organization</span>
                        </h2>
                        <p className="text-slate-500">Manage organization settings and members</p>
                    </div>
                </div>

                <div className="flex gap-4 border-b border-slate-200 dark:border-white/5">
                    {['MEMBERS', 'PROJECTS', 'SETTINGS'].map(tab => (
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

                        {isAdmin && (
                            <div className="bg-gradient-to-br from-white to-slate-50 dark:from-modtale-card dark:to-white/[0.02] border border-slate-200 dark:border-white/5 p-6 rounded-2xl shadow-sm">
                                <div className="flex items-center gap-3 mb-6">
                                    <div className="w-10 h-10 rounded-full bg-modtale-accent/10 flex items-center justify-center text-modtale-accent">
                                        <UserPlus className="w-5 h-5" />
                                    </div>
                                    <div>
                                        <h3 className="font-bold text-lg text-slate-900 dark:text-white">Invite New Member</h3>
                                        <p className="text-xs text-slate-500">Send an invitation to join this organization.</p>
                                    </div>
                                </div>

                                <form onSubmit={handleAddMember} className="flex flex-col md:flex-row gap-4 items-end relative">
                                    <div className="flex-1 w-full relative">
                                        <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1.5 ml-1">Username</label>
                                        <input
                                            type="text"
                                            placeholder="Enter username..."
                                            value={inviteUsername}
                                            onChange={e => setInviteUsername(e.target.value)}
                                            className="w-full bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 outline-none focus:ring-2 focus:ring-modtale-accent transition-all dark:text-white font-medium"
                                        />

                                        {userSearchResults.length > 0 && (
                                            <div className="absolute top-full left-0 right-0 mt-2 bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-50 overflow-hidden animate-in fade-in zoom-in-95">
                                                {userSearchResults.map(res => (
                                                    <button
                                                        key={res.id}
                                                        type="button"
                                                        onClick={() => { setInviteUsername(res.username); setUserSearchResults([]); }}
                                                        className="w-full flex items-center gap-3 p-3 hover:bg-slate-50 dark:hover:bg-white/5 transition-colors text-left"
                                                    >
                                                        <div className="w-8 h-8 rounded-full bg-slate-200 overflow-hidden"><img src={res.avatarUrl} className="w-full h-full object-cover" /></div>
                                                        <span className="font-bold text-sm text-slate-900 dark:text-white">{res.username}</span>
                                                    </button>
                                                ))}
                                            </div>
                                        )}
                                    </div>

                                    <div className="w-full md:w-48 relative" ref={roleDropdownRef}>
                                        <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1.5 ml-1">Role</label>
                                        <button
                                            type="button"
                                            onClick={() => setRoleDropdownOpen(!roleDropdownOpen)}
                                            className="w-full flex items-center justify-between bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 outline-none focus:ring-2 focus:ring-modtale-accent dark:text-white font-medium"
                                        >
                                            <div className="flex items-center gap-2">
                                                {inviteRole === 'ADMIN' ? <ShieldCheck className="w-4 h-4 text-amber-500"/> : <Shield className="w-4 h-4 text-blue-500"/>}
                                                <span>{inviteRole === 'ADMIN' ? 'Admin' : 'Member'}</span>
                                            </div>
                                            <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${roleDropdownOpen ? 'rotate-180' : ''}`} />
                                        </button>

                                        {roleDropdownOpen && (
                                            <div className="absolute top-full left-0 right-0 mt-2 bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-50 overflow-hidden animate-in fade-in zoom-in-95">
                                                <button
                                                    type="button"
                                                    onClick={() => { setInviteRole('MEMBER'); setRoleDropdownOpen(false); }}
                                                    className="w-full flex items-center gap-3 p-3 hover:bg-slate-50 dark:hover:bg-white/5 transition-colors text-left"
                                                >
                                                    <Shield className="w-5 h-5 text-blue-500" />
                                                    <div>
                                                        <div className="font-bold text-sm text-slate-900 dark:text-white">Member</div>
                                                        <div className="text-[10px] text-slate-500">Standard access to projects.</div>
                                                    </div>
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() => { setInviteRole('ADMIN'); setRoleDropdownOpen(false); }}
                                                    className="w-full flex items-center gap-3 p-3 hover:bg-slate-50 dark:hover:bg-white/5 transition-colors text-left"
                                                >
                                                    <ShieldCheck className="w-5 h-5 text-amber-500" />
                                                    <div>
                                                        <div className="font-bold text-sm text-slate-900 dark:text-white">Admin</div>
                                                        <div className="text-[10px] text-slate-500">Full access to settings & billing.</div>
                                                    </div>
                                                </button>
                                            </div>
                                        )}
                                    </div>

                                    <button
                                        type="submit"
                                        disabled={!inviteUsername}
                                        className="w-full md:w-auto bg-slate-900 dark:bg-white text-white dark:text-slate-900 font-bold px-8 py-3 rounded-xl hover:opacity-90 transition-opacity disabled:opacity-50 flex items-center justify-center gap-2"
                                    >
                                        <Plus className="w-4 h-4" /> Invite
                                    </button>
                                </form>
                            </div>
                        )}

                        <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 rounded-2xl overflow-hidden shadow-sm">
                            <div className="px-6 py-4 border-b border-slate-100 dark:border-white/5 flex justify-between items-center">
                                <h3 className="font-bold text-slate-900 dark:text-white">Active Members</h3>
                                <span className="text-xs font-bold bg-slate-100 dark:bg-white/10 px-2 py-1 rounded-lg text-slate-500">{members.length}</span>
                            </div>
                            <div className="divide-y divide-slate-100 dark:divide-white/5">
                                {members.map(member => {
                                    const role = selectedOrg.organizationMembers?.find(m => m.userId === member.id)?.role || 'MEMBER';
                                    const isMe = member.id === user.id;

                                    const preventSelfRemoval = isMe && isLastMember;

                                    return (
                                        <div key={member.id} className="p-4 flex items-center justify-between hover:bg-slate-50 dark:hover:bg-white/[0.02] transition-colors group">
                                            <div className="flex items-center gap-4">
                                                <div className="w-10 h-10 rounded-full overflow-hidden bg-slate-100 border border-slate-200 dark:border-white/10">
                                                    <img src={member.avatarUrl} alt="" className="w-full h-full object-cover" />
                                                </div>
                                                <div>
                                                    <div className="font-bold text-slate-900 dark:text-white text-sm">{member.username}</div>
                                                    <div className="flex items-center gap-2 mt-0.5">
                                                        <span className={`text-[10px] font-black uppercase tracking-wider px-1.5 py-0.5 rounded ${role === 'ADMIN' ? 'bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-400' : 'bg-slate-100 text-slate-500 dark:bg-white/10 dark:text-slate-400'}`}>{role}</span>
                                                        {isMe && <span className="text-[10px] text-slate-400 font-medium italic">(You)</span>}
                                                    </div>
                                                </div>
                                            </div>

                                            <div className="flex items-center gap-2 opacity-100 sm:opacity-0 group-hover:opacity-100 transition-opacity">
                                                {isAdmin && !isMe && (
                                                    <button
                                                        onClick={() => handleRoleUpdate(member.id, role)}
                                                        className={`p-2 rounded-lg transition-all ${role === 'ADMIN' ? 'text-amber-500 hover:bg-amber-50 dark:hover:bg-amber-900/20' : 'text-blue-500 hover:bg-blue-50 dark:hover:bg-blue-900/20'}`}
                                                        title={role === 'ADMIN' ? "Demote to Member" : "Promote to Admin"}
                                                    >
                                                        {role === 'ADMIN' ? <ShieldOff className="w-4 h-4" /> : <ShieldCheck className="w-4 h-4" />}
                                                    </button>
                                                )}

                                                {(isAdmin || isMe) && (
                                                    <div className="relative group/tooltip">
                                                        <button
                                                            onClick={() => !preventSelfRemoval && handleRemoveMember(member.id)}
                                                            disabled={preventSelfRemoval}
                                                            className={`p-2 rounded-lg transition-all ${preventSelfRemoval ? 'text-slate-300 cursor-not-allowed' : 'text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20'}`}
                                                            title={isMe ? "Leave Organization" : "Remove Member"}
                                                        >
                                                            <Trash2 className="w-4 h-4" />
                                                        </button>

                                                        {preventSelfRemoval && (
                                                            <div className="absolute bottom-full right-0 mb-2 w-48 bg-slate-900 text-white text-xs p-2 rounded shadow-lg opacity-0 group-hover/tooltip:opacity-100 pointer-events-none transition-opacity text-center z-10">
                                                                You cannot leave as the only member. Delete the organization instead.
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
                    </div>
                )}

                {activeTab === 'PROJECTS' && (
                    <div className="space-y-4 animate-in fade-in slide-in-from-bottom-2">
                        {orgProjects.length > 0 ? (
                            <div className="grid grid-cols-1 gap-4">
                                {orgProjects.map(project => (
                                    <ProjectListItem
                                        key={project.id}
                                        project={project}
                                        canManage={isAdmin}
                                        isOwner={false}
                                        showAuthor={false}
                                        onTransfer={setTransferModal}
                                        onDelete={setDeleteProjectModal}
                                    />
                                ))}
                            </div>
                        ) : (
                            <div className="text-center py-12 text-slate-400">No projects found.</div>
                        )}
                    </div>
                )}

                {activeTab === 'SETTINGS' && isAdmin && (
                    <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2">
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                            <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 p-6 rounded-2xl shadow-sm">
                                <h3 className="font-bold mb-4">Organization Icon</h3>
                                <div className="flex items-center gap-4">
                                    <img src={selectedOrg.avatarUrl} alt="" className="w-16 h-16 rounded-xl object-cover bg-slate-100" />
                                    <div>
                                        <input type="file" ref={avatarInputRef} onChange={e => handleFileSelect(e, 'avatar')} className="hidden" accept="image/*" />
                                        <button onClick={() => avatarInputRef.current?.click()} className="flex items-center gap-2 px-4 py-2 bg-slate-100 dark:bg-white/5 rounded-lg text-sm font-bold hover:bg-slate-200 dark:hover:bg-white/10 transition-colors"><Upload className="w-4 h-4" /> Upload</button>
                                    </div>
                                </div>
                            </div>
                            <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 p-6 rounded-2xl shadow-sm">
                                <h3 className="font-bold mb-4">Profile Banner</h3>
                                <div className="flex items-center gap-4">
                                    <div className="w-32 h-16 rounded-xl overflow-hidden bg-slate-100 relative">
                                        {selectedOrg.bannerUrl && <img src={selectedOrg.bannerUrl} alt="" className="w-full h-full object-cover" />}
                                    </div>
                                    <div>
                                        <input type="file" ref={bannerInputRef} onChange={e => handleFileSelect(e, 'banner')} className="hidden" accept="image/*" />
                                        <button onClick={() => bannerInputRef.current?.click()} className="flex items-center gap-2 px-4 py-2 bg-slate-100 dark:bg-white/5 rounded-lg text-sm font-bold hover:bg-slate-200 dark:hover:bg-white/10 transition-colors"><ImageIcon className="w-4 h-4" /> Upload</button>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 p-6 rounded-2xl shadow-sm">
                            <h3 className="font-bold text-lg mb-4 flex items-center gap-2"><Settings className="w-5 h-5 text-slate-400" /> General Settings</h3>
                            <form onSubmit={handleUpdateProfile} className="space-y-4">
                                <div>
                                    <label className="block text-xs font-bold uppercase text-slate-500 mb-1">Organization Name (Username)</label>
                                    <input type="text" value={displayName} onChange={e => setDisplayName(e.target.value)} className="w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-modtale-accent dark:text-white" />
                                    <p className="text-[10px] text-slate-400 mt-1">This is your unique organization identifier.</p>
                                </div>
                                <div>
                                    <label className="block text-xs font-bold uppercase text-slate-500 mb-1">Bio</label>
                                    <textarea value={bio} onChange={e => setBio(e.target.value)} rows={3} className="w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-modtale-accent dark:text-white" />
                                </div>
                                <div className="flex justify-end">
                                    <button type="submit" disabled={savingSettings} className="bg-modtale-accent text-white font-bold px-6 py-2.5 rounded-xl hover:bg-modtale-accentHover transition-colors disabled:opacity-50">{savingSettings ? 'Saving...' : 'Save Changes'}</button>
                                </div>
                            </form>
                        </div>

                        <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 p-6 rounded-2xl shadow-sm">
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

                        <div className="border border-red-200 dark:border-red-900/30 bg-red-50 dark:bg-red-900/10 p-6 rounded-2xl">
                            <h3 className="font-bold text-red-600 dark:text-red-400 mb-2">Danger Zone</h3>
                            <p className="text-sm text-red-500/80 mb-4">Deleting this organization will permanently remove it and unlist all associated projects.</p>
                            <button onClick={() => setDeleteModalOpen(true)} className="bg-white dark:bg-red-900/20 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-900/30 px-4 py-2 rounded-lg font-bold text-sm hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors">Delete Organization</button>
                        </div>
                    </div>
                )}
                {activeTab === 'SETTINGS' && !isAdmin && (
                    <div className="text-center py-10 text-slate-500">You do not have permission to edit this organization.</div>
                )}
            </div>
        );
    }

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <h1 className="text-2xl font-black text-slate-900 dark:text-white">Organizations</h1>
                <button onClick={() => setIsCreating(true)} className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-4 py-2 rounded-xl font-bold flex items-center gap-2 transition-colors shadow-lg shadow-modtale-accent/20"><Plus className="w-4 h-4" /> New Org</button>
            </div>
            {isCreating && (
                <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 p-6 rounded-2xl shadow-sm animate-in slide-in-from-top-2">
                    <h3 className="font-bold text-lg mb-4">Create Organization</h3>
                    {createError && <ErrorBanner message={createError} className="mb-4" />}
                    <form onSubmit={handleCreateOrg} className="flex gap-4">
                        <input type="text" placeholder="Organization Name" value={newOrgName} onChange={e => setNewOrgName(e.target.value)} className="flex-1 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2 outline-none focus:ring-2 focus:ring-modtale-accent transition-all dark:text-white" autoFocus />
                        <button type="submit" disabled={!newOrgName} className="bg-slate-900 dark:bg-white text-white dark:text-slate-900 font-bold px-6 py-2 rounded-xl">Create</button>
                        <button type="button" onClick={() => setIsCreating(false)} className="px-4 font-bold text-slate-500">Cancel</button>
                    </form>
                </div>
            )}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {orgs.map(org => (
                    <div key={org.id} onClick={() => setSelectedOrg(org)} className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 p-5 rounded-2xl shadow-sm hover:border-modtale-accent dark:hover:border-modtale-accent cursor-pointer transition-all group">
                        <div className="flex items-center justify-between mb-4">
                            <div className="w-12 h-12 bg-slate-100 dark:bg-white/5 rounded-xl flex items-center justify-center text-slate-400 group-hover:text-modtale-accent transition-colors">
                                {org.avatarUrl ? <img src={org.avatarUrl} className="w-full h-full object-cover rounded-xl" /> : <Building2 className="w-6 h-6" />}
                            </div>
                            <span className="text-xs font-bold uppercase tracking-wider bg-slate-100 dark:bg-white/5 text-slate-500 px-2 py-1 rounded">{org.organizationMembers?.find(m => m.userId === user.id)?.role || 'MEMBER'}</span>
                        </div>
                        <h3 className="text-lg font-bold text-slate-900 dark:text-white mb-1">{org.username}</h3>
                    </div>
                ))}
            </div>
        </div>
    );
};