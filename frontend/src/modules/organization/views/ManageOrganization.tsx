import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { SiteRoutes } from '@/utils/routes';
import { createPortal } from 'react-dom';
import { Plus, Building2, ArrowLeft, X, ExternalLink } from 'lucide-react';
import { theme } from '@/styles/theme';
import { StatusModal } from '@/components/ui/StatusModal';
import { Spinner } from '@/components/ui/Spinner';
import { organizationClient } from '../api/organizationClient';
import { extractApiErrorMessage } from '@/utils/api';

import { Members } from '../tabs/Members';
import { Roles } from '../tabs/Roles';
import { Projects } from '../tabs/Projects';
import { Settings } from '../tabs/Settings';
import type { User } from '@/types';

interface ManageOrganizationProps {
    user: User;
}

export const ManageOrganization: React.FC<ManageOrganizationProps> = ({ user }) => {
    const [orgs, setOrgs] = useState<User[]>([]);
    const [loading, setLoading] = useState(true);
    const [selectedOrg, setSelectedOrg] = useState<User | null>(null);
    const [activeTab, setActiveTab] = useState<'MEMBERS' | 'ROLES' | 'PROJECTS' | 'SETTINGS'>('MEMBERS');

    const [isCreating, setIsCreating] = useState(false);
    const [newOrgName, setNewOrgName] = useState('');

    const [status, setStatus] = useState<{type: 'success' | 'error' | 'warning' | 'info', title: string, msg: string} | null>(null);

    useEffect(() => {
        const fetchOrgs = async () => {
            try {
                setOrgs(await organizationClient.getUserOrgs());
            } catch (e: unknown) {
                setStatus({
                    type: 'error',
                    title: 'Organizations Unavailable',
                    msg: extractApiErrorMessage(e, 'We could not load your organizations.')
                });
            } finally {
                setLoading(false);
            }
        };
        fetchOrgs();
    }, []);

    const handleCreateOrg = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const newOrg = await organizationClient.createOrg(newOrgName);
            setOrgs([...orgs, newOrg]);
            setNewOrgName('');
            setIsCreating(false);
            setSelectedOrg(newOrg);
            setActiveTab('MEMBERS');
        } catch (err: unknown) {
            setStatus({
                type: 'error',
                title: 'Organization Creation Failed',
                msg: extractApiErrorMessage(err, 'We could not create that organization.')
            });
        }
    };

    const showStatus = (type: 'success' | 'error' | 'warning' | 'info', title: string, msg: string) => {
        setStatus({ type, title, msg });
    };

    const handleUpdateOrg = (updatedOrg: User) => {
        setSelectedOrg(updatedOrg);
        setOrgs(prev => prev.map(o => o.id === updatedOrg.id ? updatedOrg : o));
    };

    const handleDeleteOrg = () => {
        if (!selectedOrg) return;
        setOrgs(prev => prev.filter(o => o.id !== selectedOrg.id));
        setSelectedOrg(null);
    };

    if (loading) return <div className="flex justify-center py-20"><Spinner /></div>;

    if (selectedOrg) {
        return (
            <div className="space-y-6 relative">
                {status && createPortal(
                    <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} />,
                    document.body
                )}

                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4">
                    <div className="flex items-center gap-4">
                        <button onClick={() => setSelectedOrg(null)} className={`p-2 ${theme.colors.bgSurfaceHover} rounded-xl transition-colors`}>
                            <ArrowLeft className="w-5 h-5" />
                        </button>
                        <div>
                            <h2 className={`text-2xl font-black ${theme.colors.textPrimary} flex items-center gap-3`}>
                                {selectedOrg.username}
                                <span className="text-xs bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-300 px-2 py-0.5 rounded uppercase tracking-wider font-bold border border-purple-200 dark:border-purple-800/30">Organization</span>
                            </h2>
                            <p className={theme.colors.textMuted}>Manage organization settings and members</p>
                        </div>
                    </div>
                    <Link to={SiteRoutes.creator(selectedOrg.id, selectedOrg.username)} className={`flex items-center gap-2 px-4 py-2 ${theme.colors.bgSurfaceAlt} border ${theme.colors.border} rounded-xl ${theme.colors.textSecondary} hover:${theme.colors.textPrimary} hover:${theme.colors.bgSurfaceHover} transition-colors text-sm font-bold shadow-sm`}>
                        <ExternalLink className="w-4 h-4" /> View Profile
                    </Link>
                </div>

                <div className={`flex gap-4 border-b ${theme.colors.border}`}>
                    {['MEMBERS', 'ROLES', 'PROJECTS', 'SETTINGS'].map(tab => (
                        <button
                            key={tab}
                            onClick={() => setActiveTab(tab as any)}
                            className={`pb-3 px-1 font-bold text-sm transition-colors border-b-2 ${activeTab === tab ? `border-modtale-accent ${theme.colors.textPrimary}` : `border-transparent ${theme.colors.textMuted} hover:${theme.colors.textPrimary}`}`}
                        >
                            {tab.charAt(0) + tab.slice(1).toLowerCase()}
                        </button>
                    ))}
                </div>

                {activeTab === 'MEMBERS' && <Members org={selectedOrg} currentUser={user} showStatus={showStatus} onMemberRemoved={(id) => { if(id === user.id) setSelectedOrg(null); }} />}
                {activeTab === 'ROLES' && <Roles org={selectedOrg} currentUser={user} onUpdateOrg={handleUpdateOrg} showStatus={showStatus} />}
                {activeTab === 'PROJECTS' && <Projects org={selectedOrg} currentUser={user} userOrgs={orgs} />}
                {activeTab === 'SETTINGS' && <Settings org={selectedOrg} currentUser={user} onUpdateOrg={handleUpdateOrg} onDeleteOrg={handleDeleteOrg} showStatus={showStatus} />}
            </div>
        );
    }

    return (
        <div className="space-y-6 relative">
            {status && createPortal(
                <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} />,
                document.body
            )}
            <div className="flex justify-between items-center mb-6">
                <h1 className={`text-2xl font-black ${theme.colors.textPrimary}`}>Organizations</h1>
                <button onClick={() => setIsCreating(true)} className={theme.components.buttonPrimary}>
                    <Plus className="w-4 h-4" /> New Org
                </button>
            </div>

            {isCreating && createPortal(
                <div className={theme.components.modalOverlay}>
                    <div className={`${theme.components.modalContent} w-full max-w-lg p-6`}>
                        <div className="flex justify-between items-center mb-6">
                            <h3 className={`font-black text-xl ${theme.colors.textPrimary}`}>Create Organization</h3>
                            <button onClick={() => setIsCreating(false)} className={`text-slate-400 hover:${theme.colors.textPrimary} transition-colors`}><X className="w-5 h-5" /></button>
                        </div>
                        <form onSubmit={handleCreateOrg} className="flex flex-col gap-4">
                            <div className="space-y-1.5">
                                <label className={`text-[10px] font-bold ${theme.colors.textMuted} uppercase tracking-widest px-1`}>Organization Name</label>
                                <input type="text" placeholder="e.g. Modtale Team" value={newOrgName} onChange={e => setNewOrgName(e.target.value)} className={theme.components.inputField} autoFocus />
                            </div>
                            <div className="flex justify-end gap-3 mt-2">
                                <button type="button" onClick={() => setIsCreating(false)} className={theme.components.buttonSecondary}>Cancel</button>
                                <button type="submit" disabled={!newOrgName} className={theme.components.buttonPrimary}>Create</button>
                            </div>
                        </form>
                    </div>
                </div>,
                document.body
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {orgs.map(org => {
                    const member = org.organizationMembers?.find(m => m.userId === user.id);
                    const role = org.organizationRoles?.find(r => r.id === member?.roleId);

                    return (
                        <div key={org.id} onClick={() => setSelectedOrg(org)} className={`${theme.colors.bgSurface} border ${theme.colors.border} p-5 rounded-2xl shadow-sm hover:border-modtale-accent dark:hover:border-modtale-accent cursor-pointer transition-all group`}>
                            <div className="flex items-center justify-between mb-4">
                                <div className={`w-12 h-12 ${theme.colors.bgSurfaceAlt} rounded-xl flex items-center justify-center ${theme.colors.textMuted} group-hover:${theme.colors.accent} border ${theme.colors.borderFaint} transition-colors`}>
                                    {org.avatarUrl ? <img src={org.avatarUrl} alt="" className="w-full h-full object-cover rounded-xl" /> : <Building2 className="w-6 h-6" />}
                                </div>
                                {role ? (
                                    <div className={`flex items-center gap-1.5 border ${theme.colors.borderFaint} ${theme.colors.bgSurfaceAlt} px-2 py-1 rounded-md`}>
                                        <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: role.color }} />
                                        <span className={`text-[10px] font-bold uppercase tracking-wider ${theme.colors.textSecondary}`}>{role.name}</span>
                                    </div>
                                ) : (
                                    <span className={`text-[10px] font-bold uppercase tracking-wider ${theme.colors.bgSurfaceAlt} border ${theme.colors.borderFaint} ${theme.colors.textMuted} px-2 py-1 rounded-md`}>Legacy Member</span>
                                )}
                            </div>
                            <h3 className={`text-lg font-bold ${theme.colors.textPrimary} mb-1`}>{org.username}</h3>
                        </div>
                    );
                })}
            </div>
        </div>
    );
};
