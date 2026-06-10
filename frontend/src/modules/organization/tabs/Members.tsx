import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { UserPlus, ChevronDown, Check, Shield, Trash2 } from 'lucide-react';
import { theme } from '@/styles/theme';
import { DropdownSelect } from '@/components/ui/DropdownSelect';
import { Spinner } from '@/components/ui/Spinner';
import { StatusModal } from '@/components/ui/StatusModal';
import { organizationClient, hasOrgPermission } from '../api/organizationClient';
import { Permission } from '@/modules/permissions/permissions';
import { extractApiErrorMessage } from '@/utils/api';
import type { User } from '@/types';

interface MembersProps {
    org: User;
    currentUser: User;
    showStatus: (type: 'success' | 'error' | 'warning' | 'info', title: string, msg: string) => void;
    onMemberRemoved: (memberId: string) => void;
}

export function Members({ org, currentUser, showStatus, onMemberRemoved }: MembersProps) {
    const [members, setMembers] = useState<User[]>([]);
    const [invites, setInvites] = useState<User[]>([]);
    const [loading, setLoading] = useState(true);

    const [inviteUsername, setInviteUsername] = useState('');
    const [inviteUserId, setInviteUserId] = useState('');
    const [inviteRoleId, setInviteRoleId] = useState('');
    const [isInviting, setIsInviting] = useState(false);

    const [userSearchResults, setUserSearchResults] = useState<User[]>([]);
    const [memberRoleDropdownOpen, setMemberRoleDropdownOpen] = useState<string | null>(null);

    const [memberToRemove, setMemberToRemove] = useState<User | null>(null);

    const canInvite = hasOrgPermission(org, currentUser.id, Permission.ORG_MEMBER_INVITE);
    const canManageRoles = hasOrgPermission(org, currentUser.id, Permission.ORG_MEMBER_EDIT_ROLE);
    const canRemove = hasOrgPermission(org, currentUser.id, Permission.ORG_MEMBER_REMOVE);

    const nonOwnerRoles = org.organizationRoles?.filter(r => !r.isOwner) || [];
    const hasRoles = nonOwnerRoles.length > 0;

    useEffect(() => {
        const fetchRoster = async () => {
            try {
                const [m, i] = await Promise.all([
                    organizationClient.getMembers(org.id),
                    organizationClient.getInvites(org.id)
                ]);
                setMembers(m);
                setInvites(i);
            } catch (err: unknown) {
                showStatus('error', 'Member Load Failed', extractApiErrorMessage(err, 'We could not load this organization roster.'));
            } finally {
                setLoading(false);
            }
        };
        fetchRoster();
    }, [org.id]);

    useEffect(() => {
        if (!inviteUsername || inviteUsername.length < 2 || inviteUserId) {
            setUserSearchResults([]);
            return;
        }
        const delayDebounceFn = setTimeout(async () => {
            try {
                const res = await organizationClient.searchUsers(inviteUsername);
                setUserSearchResults(res);
            } catch (e) {}
        }, 300);
        return () => clearTimeout(delayDebounceFn);
    }, [inviteUsername, inviteUserId]);

    const handleInvite = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!inviteUserId || !inviteRoleId) return;
        setIsInviting(true);
        try {
            await organizationClient.addMember(org.id, inviteUserId, inviteRoleId);
            setInvites(await organizationClient.getInvites(org.id));
            setInviteUsername('');
            setInviteUserId('');
            setUserSearchResults([]);
            showStatus('success', 'Invited', 'Member invitation sent successfully.');
        } catch (err: unknown) {
            showStatus('error', 'Invitation Failed', extractApiErrorMessage(err, 'We could not send that organization invite.'));
        } finally {
            setIsInviting(false);
        }
    };

    const handleRoleUpdate = async (userId: string, newRoleId: string) => {
        try {
            await organizationClient.updateMemberRole(org.id, userId, newRoleId);
            setMembers(await organizationClient.getMembers(org.id));
            showStatus('success', 'Updated', 'Member role updated.');
        } catch (err: unknown) {
            showStatus('error', 'Role Update Failed', extractApiErrorMessage(err, 'We could not update that member role.'));
        }
    };

    const confirmRemoveMember = async () => {
        if (!memberToRemove) return;
        try {
            await organizationClient.removeMember(org.id, memberToRemove.id);
            if (memberToRemove.id === currentUser.id) {
                onMemberRemoved(memberToRemove.id);
            } else {
                setMembers(await organizationClient.getMembers(org.id));
                showStatus('success', 'Removed', 'Member has been removed.');
            }
        } catch (err: unknown) {
            showStatus('error', 'Member Removal Failed', extractApiErrorMessage(err, 'We could not remove that member from the organization.'));
        } finally {
            setMemberToRemove(null);
        }
    };

    const handleCancelInvite = async (userId: string) => {
        try {
            await organizationClient.cancelInvite(org.id, userId);
            setInvites(await organizationClient.getInvites(org.id));
        } catch (err: unknown) {
            showStatus('error', 'Invite Cancel Failed', extractApiErrorMessage(err, 'We could not cancel that pending invite.'));
        }
    };

    if (loading) return <div className="p-12 flex justify-center"><Spinner /></div>;

    return (
        <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2">
            {memberRoleDropdownOpen && (
                <div className="fixed inset-0 z-[90]" onClick={() => { setMemberRoleDropdownOpen(null); }} />
            )}

            {memberToRemove && createPortal(
                <StatusModal
                    type="warning"
                    title="Remove Member?"
                    message={`Are you sure you want to remove "${memberToRemove.username}" from the organization?`}
                    actionLabel="Remove"
                    onAction={confirmRemoveMember}
                    secondaryLabel="Cancel"
                    onClose={() => setMemberToRemove(null)}
                />,
                document.body
            )}

            {canInvite && (
                <div className={`relative z-20 ${theme.colors.bgSurfaceAlt} border ${theme.colors.border} p-6 rounded-2xl shadow-sm`}>
                    <div className="flex items-center gap-3 mb-6">
                        <div className="w-10 h-10 rounded-xl bg-modtale-accent/10 flex items-center justify-center text-modtale-accent">
                            <UserPlus className="w-5 h-5" />
                        </div>
                        <div>
                            <h3 className={`font-bold text-lg ${theme.colors.textPrimary}`}>Invite New Member</h3>
                            <p className={`text-xs ${theme.colors.textSecondary}`}>Send an invitation to join this organization.</p>
                        </div>
                    </div>

                    {hasRoles ? (
                        <form onSubmit={handleInvite} className="flex flex-col md:flex-row gap-4 items-end">
                            <div className="flex-1 w-full relative z-[100]">
                                <label className={`block text-[10px] font-bold ${theme.colors.textMuted} uppercase tracking-widest mb-1.5 ml-1`}>Username</label>
                                <input type="text" placeholder="Search username..." value={inviteUsername} onChange={e => { setInviteUsername(e.target.value); setInviteUserId(''); }} className={theme.components.inputField} />

                                {userSearchResults.length > 0 && (
                                    <div className={`absolute top-full left-0 right-0 mt-2 ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl shadow-xl overflow-hidden animate-in fade-in zoom-in-95`}>
                                        {userSearchResults.map(res => (
                                            <button key={res.id} type="button" onClick={() => { setInviteUsername(res.username); setInviteUserId(res.id); setUserSearchResults([]); }} className={`w-full flex items-center gap-3 p-3 ${theme.colors.bgSurfaceHover} transition-colors text-left`}>
                                                <div className="w-8 h-8 rounded-full bg-slate-200 overflow-hidden"><img src={res.avatarUrl} alt="" className="w-full h-full object-cover" /></div>
                                                <span className={`font-bold text-sm ${theme.colors.textPrimary}`}>{res.username}</span>
                                            </button>
                                        ))}
                                    </div>
                                )}
                            </div>

                            <div className="w-full md:w-64 relative z-[95]">
                                <label className={`block text-[10px] font-bold ${theme.colors.textMuted} uppercase tracking-widest mb-1.5 ml-1`}>Role</label>
                                <DropdownSelect
                                    value={inviteRoleId}
                                    onChange={setInviteRoleId}
                                    placeholder={<span className={theme.colors.textMuted}>Select Role...</span>}
                                    options={nonOwnerRoles.map(role => ({
                                        value: role.id,
                                        label: role.name,
                                        leftAdornment: <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: role.color }} />
                                    }))}
                                    containerClassName="relative"
                                    buttonClassName={`${theme.components.inputField} w-full flex justify-between items-center cursor-pointer`}
                                    menuClassName={`${theme.colors.bgBase} border ${theme.colors.border} rounded-xl shadow-xl overflow-hidden animate-in fade-in zoom-in-95 left-0 right-0`}
                                    optionClassName={`w-full flex items-center justify-between px-3 py-2.5 ${theme.colors.bgSurfaceHover} transition-colors text-left font-bold text-sm ${theme.colors.textPrimary}`}
                                />
                            </div>

                            <button type="submit" disabled={!inviteUserId || !inviteRoleId || isInviting} className="w-full md:w-auto bg-slate-900 dark:bg-white text-white dark:text-slate-900 font-bold px-8 py-3 rounded-xl hover:opacity-90 transition-opacity disabled:opacity-50 flex items-center justify-center gap-2 shadow-lg h-11">
                                {isInviting ? <Spinner className="w-4 h-4" /> : 'Invite'}
                            </button>
                        </form>
                    ) : (
                        <div className={`text-sm ${theme.colors.warningText} ${theme.colors.warningBg} border ${theme.colors.warningBorder} p-4 rounded-xl flex items-center gap-2 font-medium`}>
                            <Shield className="w-5 h-5" /> You must create at least one Role before inviting members.
                        </div>
                    )}
                </div>
            )}

            <div className={`relative z-10 ${theme.colors.bgSurface} border ${theme.colors.border} rounded-2xl overflow-hidden shadow-sm`}>
                <div className={`px-6 py-5 border-b ${theme.colors.border} flex justify-between items-center`}>
                    <h3 className={`font-bold ${theme.colors.textPrimary}`}>Active Members</h3>
                    <span className={`text-xs font-bold ${theme.colors.bgSurfaceAlt} px-2 py-1 rounded-lg ${theme.colors.textSecondary}`}>{members.length}</span>
                </div>
                <div className={`divide-y ${theme.colors.borderFaint}`}>
                    {members.map(member => {
                        const membership = org.organizationMembers?.find(m => m.userId === member.id);
                        const role = org.organizationRoles?.find(r => r.id === membership?.roleId);
                        const isMe = member.id === currentUser.id;
                        const isOwner = role?.isOwner;
                        const myMember = org.organizationMembers?.find(m => m.userId === currentUser.id);
                        const myRole = org.organizationRoles?.find(r => r.id === myMember?.roleId);

                        return (
                            <div key={member.id} className={`p-5 flex items-center justify-between ${theme.colors.bgSurfaceHover} transition-colors group`}>
                                <div className="flex items-center gap-4">
                                    <div className={`w-10 h-10 rounded-full overflow-hidden ${theme.colors.bgSurfaceAlt} border ${theme.colors.borderFaint}`}>
                                        {member.avatarUrl ? <img src={member.avatarUrl} alt="" className="w-full h-full object-cover" /> : <div className={`w-full h-full flex items-center justify-center font-bold text-slate-400`}>{member.username.charAt(0).toUpperCase()}</div>}
                                    </div>
                                    <div>
                                        <div className={`font-bold ${theme.colors.textPrimary} text-sm`}>{member.username}</div>
                                        <div className="flex items-center gap-2 mt-1">
                                            {role ? (
                                                <div className={`flex items-center gap-1.5 border ${theme.colors.borderFaint} ${theme.colors.bgSurfaceAlt} px-2 py-0.5 rounded-md`}>
                                                    <div className="w-2 h-2 rounded-full" style={{ backgroundColor: role.color }} />
                                                    <span className={`text-[10px] font-bold uppercase tracking-wider ${theme.colors.textSecondary}`}>{role.name}</span>
                                                </div>
                                            ) : (
                                                <span className={`text-[10px] font-black uppercase tracking-wider px-1.5 py-0.5 rounded border ${theme.colors.bgSurfaceAlt} ${theme.colors.borderFaint} ${theme.colors.textSecondary}`}>Legacy Member</span>
                                            )}
                                            {isMe && <span className={`text-[10px] ${theme.colors.textMuted} font-medium italic`}>(You)</span>}
                                        </div>
                                    </div>
                                </div>

                                <div className="flex items-center gap-3">
                                    {canManageRoles && !isMe && (
                                        <div className="relative modtale-dropdown-container">
                                            <button
                                                onClick={() => setMemberRoleDropdownOpen(memberRoleDropdownOpen === member.id ? null : member.id)}
                                                disabled={isOwner && !myRole?.isOwner}
                                                className={`flex items-center justify-between min-w-[140px] ${theme.colors.bgBase} border ${theme.colors.border} rounded-lg pl-3 pr-2 py-1.5 outline-none hover:border-modtale-accent transition-colors ${isOwner && !myRole?.isOwner ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer shadow-sm'}`}
                                            >
                                                <div className="flex items-center gap-2 truncate">
                                                    {role && <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: role.color }} />}
                                                    <span className={`text-xs font-bold ${theme.colors.textSecondary} truncate`}>{role ? role.name : 'Select Role'}</span>
                                                </div>
                                                <ChevronDown className={`w-3 h-3 ${theme.colors.textMuted} ml-2 flex-shrink-0 transition-transform ${memberRoleDropdownOpen === member.id ? 'rotate-180' : ''}`} />
                                            </button>

                                            {memberRoleDropdownOpen === member.id && (
                                                <div className={`absolute right-0 top-full mt-2 w-48 ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl shadow-xl z-[100] overflow-hidden animate-in fade-in zoom-in-95`}>
                                                    <div className="max-h-48 overflow-y-auto py-1">
                                                        {org.organizationRoles?.filter(r => !r.isOwner || isOwner || myRole?.isOwner).map(r => (
                                                            <button key={r.id} type="button" onClick={() => { handleRoleUpdate(member.id, r.id); setMemberRoleDropdownOpen(null); }} className={`w-full flex items-center justify-between px-3 py-2 ${theme.colors.bgSurfaceHover} transition-colors text-left`}>
                                                                <div className="flex items-center gap-2 truncate">
                                                                    <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{backgroundColor: r.color}} />
                                                                    <span className={`font-bold text-xs ${theme.colors.textPrimary} truncate`}>{r.name}</span>
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
                                        <button onClick={() => !isOwner && setMemberToRemove(member)} disabled={isOwner} className={`p-2 rounded-xl transition-all ${isOwner ? 'text-slate-300 cursor-not-allowed' : `${theme.colors.textMuted} hover:${theme.colors.dangerText} hover:${theme.colors.dangerBg}`}`} title={isMe ? "Leave Organization" : "Remove Member"}>
                                            <Trash2 className="w-4 h-4" />
                                        </button>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>

            {invites.length > 0 && (
                <div className={`relative z-0 ${theme.colors.bgSurface} border ${theme.colors.border} rounded-2xl overflow-hidden shadow-sm`}>
                    <div className={`px-6 py-5 border-b ${theme.colors.border} flex justify-between items-center`}>
                        <h3 className={`font-bold ${theme.colors.textPrimary}`}>Pending Invites</h3>
                        <span className={`text-xs font-bold ${theme.colors.bgSurfaceAlt} px-2 py-1 rounded-lg ${theme.colors.textSecondary}`}>{invites.length}</span>
                    </div>
                    <div className={`divide-y ${theme.colors.borderFaint}`}>
                        {invites.map(inviteUser => {
                            const inviteData = org.pendingOrgInvites?.find(i => i.userId === inviteUser.id);
                            const inviteRole = org.organizationRoles?.find(r => r.id === inviteData?.roleId);

                            return (
                                <div key={inviteUser.id} className={`p-5 flex items-center justify-between ${theme.colors.bgSurfaceHover} transition-colors`}>
                                    <div className="flex items-center gap-4">
                                        <div className={`w-10 h-10 rounded-full overflow-hidden ${theme.colors.bgSurfaceAlt} border ${theme.colors.borderFaint} opacity-70 grayscale`}>
                                            <img src={inviteUser.avatarUrl} alt="" className="w-full h-full object-cover" />
                                        </div>
                                        <div>
                                            <div className={`font-bold ${theme.colors.textPrimary} text-sm`}>{inviteUser.username}</div>
                                            <div className="flex items-center gap-2 mt-1">
                                                <span className={`text-[10px] font-black uppercase tracking-wider px-1.5 py-0.5 rounded border ${theme.colors.warningBg} ${theme.colors.warningText} ${theme.colors.warningBorder}`}>Pending</span>
                                                {inviteRole && <span className={`text-[10px] ${theme.colors.textMuted} font-medium`}>As {inviteRole.name}</span>}
                                            </div>
                                        </div>
                                    </div>
                                    {canInvite && (
                                        <button onClick={() => handleCancelInvite(inviteUser.id)} className={`text-xs font-bold ${theme.colors.dangerText} ${theme.colors.dangerBg} hover:opacity-80 px-3 py-1.5 rounded-lg border ${theme.colors.dangerBorder} transition-colors`}>
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
    );
}
