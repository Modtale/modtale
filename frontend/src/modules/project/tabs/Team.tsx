import React from 'react';
import { Plus, Check, ChevronDown, Trash2, Shield, Settings } from 'lucide-react';
import { theme } from '@/styles/theme';
import { Spinner } from '@/components/ui/Spinner';
import type { Project, User } from '@/types';

interface TeamProps {
    projectData: Project | null;
    currentUser: User | null;
    canInvite: boolean;
    canManageRoles: boolean;
    canRemove: boolean;
    inviteUsername: string;
    inviteUserId: string;
    setInviteUsername: (val: string) => void;
    setInviteUserId: (val: string) => void;
    inviteRoleId: string;
    setInviteRoleId: (val: string) => void;
    userSearchResults: User[];
    setUserSearchResults: (data: User[]) => void;
    inviteRoleDropdownOpen: boolean;
    setInviteRoleDropdownOpen: (open: boolean) => void;
    memberRoleDropdownOpen: string | null;
    setMemberRoleDropdownOpen: (id: string | null) => void;
    setMemberToRemove: (id: string) => void;
    handleInvite: (e: React.FormEvent) => void;
    handleRoleUpdate: (userId: string, roleId: string) => void;
    handleCancelInvite: (userId: string) => void;
    setEditingRole: (role: any) => void;
    setRoleModalOpen: (open: boolean) => void;
    handleDeleteRole: (id: string) => void;
    isInviting: boolean;
    contributors: User[];
}

export const Team: React.FC<TeamProps> = ({
                                              projectData,
                                              currentUser,
                                              canInvite,
                                              canManageRoles,
                                              canRemove,
                                              inviteUsername,
                                              inviteUserId,
                                              setInviteUsername,
                                              setInviteUserId,
                                              inviteRoleId,
                                              setInviteRoleId,
                                              userSearchResults,
                                              setUserSearchResults,
                                              inviteRoleDropdownOpen,
                                              setInviteRoleDropdownOpen,
                                              memberRoleDropdownOpen,
                                              setMemberRoleDropdownOpen,
                                              setMemberToRemove,
                                              handleInvite,
                                              handleRoleUpdate,
                                              handleCancelInvite,
                                              setEditingRole,
                                              setRoleModalOpen,
                                              handleDeleteRole,
                                              isInviting,
                                              contributors
                                          }) => {
    return (
        <div className="space-y-8 animate-in fade-in slide-in-from-bottom-2">
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="lg:col-span-2 space-y-6">
                    {canInvite && (
                        <div className={`${theme.colors.bgSurface} p-6 rounded-2xl border ${theme.colors.border}`}>
                            <h3 className={`font-bold text-lg ${theme.colors.textPrimary} mb-4`}>Invite Contributor</h3>
                            {projectData?.projectRoles && projectData.projectRoles.length > 0 ? (
                                <form onSubmit={handleInvite} className="flex flex-col md:flex-row gap-4 items-end">
                                    <div className="flex-1 w-full relative modtale-dropdown-container">
                                        <label className={`block text-[10px] font-bold ${theme.colors.textMuted} uppercase tracking-widest mb-1.5 ml-1`}>Username</label>
                                        <input type="text" placeholder="Search username..." value={inviteUsername} onChange={e => { setInviteUsername(e.target.value); setInviteUserId(''); }} className={`w-full ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-modtale-accent transition-all ${theme.colors.textPrimary} font-medium shadow-inner`} />
                                        {userSearchResults.length > 0 && (
                                            <div className={`absolute top-full left-0 right-0 mt-2 ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl shadow-xl z-[100] overflow-hidden animate-in fade-in zoom-in-95`}>
                                                {userSearchResults.map(res => (
                                                    <button key={res.id} type="button" onClick={() => { setInviteUsername(res.username); setInviteUserId(res.id); setUserSearchResults([]); }} className={`w-full flex items-center gap-3 p-3 ${theme.colors.bgSurfaceHover} transition-colors text-left`}>
                                                        <div className="w-8 h-8 rounded-full bg-slate-200 overflow-hidden"><img src={res.avatarUrl} className="w-full h-full object-cover" /></div>
                                                        <span className={`font-bold text-sm ${theme.colors.textPrimary}`}>{res.username}</span>
                                                    </button>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                    <div className="w-full md:w-48 relative modtale-dropdown-container">
                                        <label className={`block text-[10px] font-bold ${theme.colors.textMuted} uppercase tracking-widest mb-1.5 ml-1`}>Role</label>
                                        <button type="button" onClick={() => setInviteRoleDropdownOpen(!inviteRoleDropdownOpen)} className={`w-full ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-modtale-accent ${theme.colors.textPrimary} font-medium shadow-inner flex justify-between items-center`}>
                                            {inviteRoleId ? <div className="flex items-center gap-2 truncate"><div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{backgroundColor: projectData.projectRoles.find(r => r.id === inviteRoleId)?.color}} /><span className="truncate">{projectData.projectRoles.find(r => r.id === inviteRoleId)?.name}</span></div> : <span className="text-slate-400">Select Role...</span>}
                                            <ChevronDown className={`w-4 h-4 text-slate-400 flex-shrink-0 transition-transform ${inviteRoleDropdownOpen ? 'rotate-180' : ''}`} />
                                        </button>
                                        {inviteRoleDropdownOpen && (
                                            <div className={`absolute top-full left-0 right-0 mt-2 ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl shadow-xl z-[100] overflow-hidden animate-in fade-in zoom-in-95`}>
                                                <div className="max-h-48 overflow-y-auto custom-scrollbar py-1">
                                                    {projectData.projectRoles.map(role => (
                                                        <button key={role.id} type="button" onClick={() => { setInviteRoleId(role.id); setInviteRoleDropdownOpen(false); }} className={`w-full flex items-center justify-between px-3 py-2 ${theme.colors.bgSurfaceHover} transition-colors text-left`}>
                                                            <div className="flex items-center gap-2 truncate"><div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{backgroundColor: role.color}} /><span className={`font-bold text-sm ${theme.colors.textPrimary} truncate`}>{role.name}</span></div>
                                                            {inviteRoleId === role.id && <Check className="w-4 h-4 text-modtale-accent flex-shrink-0" />}
                                                        </button>
                                                    ))}
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                    <button type="submit" disabled={!inviteUserId || !inviteRoleId || isInviting} className="w-full md:w-auto bg-slate-900 dark:bg-white text-white dark:text-slate-900 font-bold px-6 py-2.5 rounded-xl hover:opacity-90 transition-opacity disabled:opacity-50 flex items-center justify-center gap-2 shadow-lg h-11">{isInviting ? <Spinner className="w-4 h-4" /> : <><Plus className="w-4 h-4" /> Invite</>}</button>
                                </form>
                            ) : (
                                <div className={`text-sm ${theme.colors.warningText} ${theme.colors.warningBg} border ${theme.colors.warningBorder} p-4 rounded-xl flex items-center gap-2 font-medium`}>
                                    <Shield className="w-5 h-5" /> You must create at least one Role before inviting members.
                                </div>
                            )}
                        </div>
                    )}

                    <div className={`${theme.colors.bgSurface} border ${theme.colors.border} rounded-2xl overflow-hidden`}>
                        <div className={`px-6 py-4 border-b ${theme.colors.borderFaint} flex justify-between items-center`}>
                            <h3 className={`font-bold ${theme.colors.textPrimary}`}>Active Team</h3>
                            <span className={`text-xs font-bold ${theme.colors.bgSurfaceAlt} px-2 py-1 rounded-lg ${theme.colors.textSecondary}`}>{(projectData?.teamMembers?.length || 0) + 1}</span>
                        </div>
                        <div className={`divide-y ${theme.colors.borderFaint}`}>
                            <div className={`p-4 flex items-center justify-between ${theme.colors.bgSurfaceHover} transition-colors`}>
                                <div className="flex items-center gap-3">
                                    <div className={`w-10 h-10 rounded-full overflow-hidden ${theme.colors.bgSurfaceAlt} flex items-center justify-center font-bold text-slate-400 border ${theme.colors.borderFaint}`}>
                                        {projectData?.author?.charAt(0).toUpperCase()}
                                    </div>
                                    <div>
                                        <div className={`font-bold ${theme.colors.textPrimary} text-sm`}>{projectData?.author}</div>
                                        <div className="flex items-center gap-1.5 mt-0.5">
                                            <span className={`text-[9px] font-black uppercase tracking-wider px-1.5 py-0.5 rounded border ${theme.colors.bgSurfaceAlt} ${theme.colors.borderFaint} ${theme.colors.textSecondary}`}>Author</span>
                                            {projectData?.authorId === currentUser?.id && <span className={`text-[9px] ${theme.colors.textMuted} font-medium italic`}>(You)</span>}
                                        </div>
                                    </div>
                                </div>
                                <div className="flex items-center"><span className={`text-[10px] ${theme.colors.textMuted} font-bold uppercase tracking-wider`}>Owner</span></div>
                            </div>
                            {projectData?.teamMembers?.map(member => {
                                const role = projectData.projectRoles?.find(r => r.id === member.roleId);
                                const isMe = member.userId === currentUser?.id;
                                return (
                                    <div key={member.userId} className={`p-4 flex items-center justify-between ${theme.colors.bgSurfaceHover} transition-colors group`}>
                                        <div className="flex items-center gap-3">
                                            <div className={`w-10 h-10 rounded-full overflow-hidden ${theme.colors.bgSurfaceAlt} border ${theme.colors.borderFaint}`}>
                                                {member.avatarUrl ? <img src={member.avatarUrl} alt="" className="w-full h-full object-cover" /> : <div className={`w-full h-full ${theme.colors.bgSurfaceAlt} flex items-center justify-center font-bold text-slate-400`}>{member.username?.charAt(0).toUpperCase() || '?'}</div>}
                                            </div>
                                            <div>
                                                <div className={`font-bold ${theme.colors.textPrimary} text-sm`}>{member.username || 'Unknown User'}</div>
                                                <div className="flex items-center gap-2 mt-1">
                                                    {role ? <div className={`flex items-center gap-1.5 border ${theme.colors.borderFaint} ${theme.colors.bgSurfaceAlt} px-2 py-0.5 rounded-md`}><div className="w-2 h-2 rounded-full" style={{ backgroundColor: role.color }} /><span className={`text-[10px] font-bold uppercase tracking-wider ${theme.colors.textSecondary}`}>{role.name}</span></div> : <span className={`text-[10px] font-black uppercase tracking-wider px-1.5 py-0.5 rounded border ${theme.colors.bgSurfaceAlt} ${theme.colors.borderFaint} ${theme.colors.textSecondary}`}>Legacy Contributor</span>}
                                                    {isMe && <span className={`text-[10px] ${theme.colors.textMuted} font-medium italic`}>(You)</span>}
                                                </div>
                                            </div>
                                        </div>
                                        <div className="flex items-center gap-3">
                                            {canManageRoles && !isMe && (
                                                <div className="relative modtale-dropdown-container">
                                                    <button type="button" onClick={() => setMemberRoleDropdownOpen(memberRoleDropdownOpen === member.userId ? null : member.userId)} className={`flex items-center justify-between min-w-[120px] ${theme.colors.bgBase} border ${theme.colors.border} rounded-lg pl-3 pr-2 py-1.5 outline-none hover:border-modtale-accent transition-colors cursor-pointer shadow-sm`}>
                                                        <div className="flex items-center gap-2 truncate">
                                                            {role && <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: role.color }} />}
                                                            <span className={`text-xs font-bold ${theme.colors.textSecondary} truncate`}>{role ? role.name : 'Select Role'}</span>
                                                        </div>
                                                        <ChevronDown className={`w-3 h-3 text-slate-400 ml-2 flex-shrink-0 transition-transform ${memberRoleDropdownOpen === member.userId ? 'rotate-180' : ''}`} />
                                                    </button>
                                                    {memberRoleDropdownOpen === member.userId && (
                                                        <div className={`absolute right-0 top-full mt-2 w-48 ${theme.colors.bgBase} border ${theme.colors.border} rounded-xl shadow-xl z-[100] overflow-hidden animate-in fade-in zoom-in-95`}>
                                                            <div className="max-h-48 overflow-y-auto custom-scrollbar py-1">
                                                                {projectData.projectRoles?.map(r => (
                                                                    <button key={r.id} type="button" onClick={() => { handleRoleUpdate(member.userId, r.id); setMemberRoleDropdownOpen(null); }} className={`w-full flex items-center justify-between px-3 py-2 ${theme.colors.bgSurfaceHover} transition-colors text-left`}>
                                                                        <div className="flex items-center gap-2 truncate"><div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{backgroundColor: r.color}} /><span className={`font-bold text-xs ${theme.colors.textPrimary} truncate`}>{r.name}</span></div>
                                                                        {member.roleId === r.id && <Check className="w-3 h-3 text-modtale-accent flex-shrink-0" />}
                                                                    </button>
                                                                ))}
                                                            </div>
                                                        </div>
                                                    )}
                                                </div>
                                            )}
                                            {(canRemove || isMe) && (
                                                <div className="relative group/tooltip">
                                                    <button type="button" onClick={() => setMemberToRemove(member.userId)} className={`p-2 rounded-xl transition-all ${theme.colors.textMuted} hover:${theme.colors.dangerText} hover:${theme.colors.dangerBg}`} title={isMe ? "Leave Project" : "Remove Contributor"}><Trash2 className="w-4 h-4" /></button>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>

                    {(projectData?.teamInvites?.length || 0) > 0 && (
                        <div className={`${theme.colors.bgSurface} border ${theme.colors.border} rounded-2xl overflow-hidden`}>
                            <div className={`px-6 py-4 border-b ${theme.colors.borderFaint} flex justify-between items-center`}>
                                <h3 className={`font-bold ${theme.colors.textPrimary}`}>Pending Invites</h3>
                                <span className={`text-xs font-bold ${theme.colors.bgSurfaceAlt} px-2 py-1 rounded-lg ${theme.colors.textSecondary}`}>{projectData?.teamInvites?.length || 0}</span>
                            </div>
                            <div className={`divide-y ${theme.colors.borderFaint}`}>
                                {projectData?.teamInvites?.map(invite => {
                                    const role = projectData.projectRoles?.find(r => r.id === invite.roleId);
                                    return (
                                        <div key={invite.userId} className={`p-4 flex items-center justify-between ${theme.colors.bgSurfaceHover} transition-colors`}>
                                            <div className="flex items-center gap-4">
                                                <div className={`w-10 h-10 rounded-full overflow-hidden ${theme.colors.bgSurfaceAlt} border ${theme.colors.borderFaint} opacity-70 grayscale`}>
                                                    {invite.avatarUrl ? <img src={invite.avatarUrl} alt="" className="w-full h-full object-cover" /> : <div className={`w-full h-full ${theme.colors.bgSurfaceAlt} flex items-center justify-center font-bold text-slate-400`}>{invite.username?.charAt(0).toUpperCase() || '?'}</div>}
                                                </div>
                                                <div>
                                                    <div className={`font-bold ${theme.colors.textPrimary} text-sm`}>{invite.username || 'Unknown User'}</div>
                                                    <div className="flex items-center gap-2 mt-1">
                                                        <span className={`text-[9px] font-black uppercase tracking-wider px-1.5 py-0.5 rounded border ${theme.colors.warningBg} ${theme.colors.warningText} ${theme.colors.warningBorder}`}>Pending</span>
                                                        {role && <span className={`text-[10px] ${theme.colors.textMuted} font-medium`}>As {role.name}</span>}
                                                    </div>
                                                </div>
                                            </div>
                                            {canInvite && <button type="button" onClick={() => handleCancelInvite(invite.userId)} className={`text-xs font-bold ${theme.colors.dangerText} ${theme.colors.dangerBg} px-3 py-1.5 rounded-lg border ${theme.colors.dangerBorder} transition-colors`}>Cancel</button>}
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    )}
                </div>
                <div className="lg:col-span-1 space-y-6">
                    <div className={`${theme.colors.bgSurface} border ${theme.colors.border} p-6 rounded-2xl`}>
                        <div className="flex justify-between items-center mb-4">
                            <h3 className={`font-bold ${theme.colors.textPrimary}`}>Project Roles</h3>
                            {canManageRoles && <button type="button" onClick={() => { setEditingRole({ permissions: [] }); setRoleModalOpen(true); }} className={`${theme.colors.accent} hover:${theme.colors.accentHover} font-bold text-xs flex items-center gap-1 transition-colors`}><Plus className="w-3 h-3" /> New</button>}
                        </div>
                        <div className="space-y-3">
                            {projectData?.projectRoles?.map(role => {
                                const count = projectData.teamMembers?.filter(m => m.roleId === role.id).length || 0;
                                return (
                                    <div key={role.id} className={`${theme.colors.bgBase} border ${theme.colors.border} rounded-xl p-3 shadow-sm group`}>
                                        <div className="flex justify-between items-start">
                                            <div className="flex items-center gap-2">
                                                <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: role.color }} />
                                                <div><div className={`font-bold text-xs ${theme.colors.textPrimary} leading-tight`}>{role.name}</div><div className={`text-[9px] ${theme.colors.textMuted} uppercase tracking-wider font-bold mt-0.5`}>{count} Member{count !== 1 ? 's' : ''}</div></div>
                                            </div>
                                            {canManageRoles && (
                                                <div className="flex gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                                                    <button type="button" onClick={() => { setEditingRole(role); setRoleModalOpen(true); }} className={`p-1 ${theme.colors.textMuted} hover:${theme.colors.accent} ${theme.colors.accentAlpha} rounded transition-colors`}><Settings className="w-3.5 h-3.5" /></button>
                                                    <button type="button" onClick={() => handleDeleteRole(role.id)} className={`p-1 ${theme.colors.textMuted} hover:${theme.colors.dangerText} hover:${theme.colors.dangerBg} rounded transition-colors`}><Trash2 className="w-3.5 h-3.5" /></button>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                            {(!projectData?.projectRoles || projectData.projectRoles.length === 0) && <div className={`text-center py-6 text-xs ${theme.colors.textMuted} italic ${theme.colors.bgSurfaceHover} rounded-xl border ${theme.colors.border}`}>No custom roles defined.</div>}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};