import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { api } from '../../../utils/api.ts';
import type {Mod, User} from '../../../types.ts';
import { Building2, Plus, Users, Loader2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { StatusModal } from '../../ui/StatusModal.tsx';
import { ProjectListItem } from '@/components/dashboard/manage-projects/ProjectListItem.tsx';
import { TransferProjectModal } from '@/components/dashboard/manage-projects/TransferProjectModal.tsx';

interface ManageProjectsProps {
    user: User;
}

export const ManageProjects: React.FC<ManageProjectsProps> = ({ user }) => {
    const [projects, setProjects] = useState<Mod[]>([]);
    const [orgProjects, setOrgProjects] = useState<Record<string, Mod[]>>({});
    const [contributedProjects, setContributedProjects] = useState<Mod[]>([]);
    const [myOrgs, setMyOrgs] = useState<User[]>([]);
    const [loading, setLoading] = useState(true);

    const [transferModal, setTransferModal] = useState<Mod | null>(null);
    const [deleteModal, setDeleteModal] = useState<Mod | null>(null);
    const [status, setStatus] = useState<{type: 'success'|'error', title: string, msg: string} | null>(null);

    useEffect(() => {
        const init = async () => {
            setLoading(true);
            try {
                const orgsRes = await api.get('/user/orgs');
                const orgsData = orgsRes.data || [];
                setMyOrgs(orgsData);

                const personalPromise = api.get(`/creators/${user.id}/projects?size=100`);
                const contribPromise = api.get('/projects/user/contributed').catch(() => ({ data: { content: [] } }));

                const orgPromises = orgsData.map((org: User) =>
                    api.get(`/creators/${org.id}/projects?size=100`)
                        .then(res => ({ id: org.id, projects: res.data.content || [] }))
                        .catch(() => ({ id: org.id, projects: [] }))
                );

                const [personalRes, contribRes, ...orgResults] = await Promise.all([
                    personalPromise,
                    contribPromise,
                    ...orgPromises
                ]);

                setProjects(personalRes.data.content || []);
                setContributedProjects(contribRes.data.content || []);

                const orgData: Record<string, Mod[]> = {};
                orgResults.forEach((result: any) => {
                    if (result.projects.length > 0) {
                        orgData[result.id] = result.projects;
                    }
                });
                setOrgProjects(orgData);

            } catch (e) {
                console.error("Failed to load projects", e);
            } finally {
                setLoading(false);
            }
        };
        init();
    }, [user.id]);

    const handleDelete = async () => {
        if (!deleteModal) return;
        try {
            await api.delete(`/projects/${deleteModal.id}`);

            setProjects(prev => prev.filter(p => p.id !== deleteModal.id));
            setContributedProjects(prev => prev.filter(p => p.id !== deleteModal.id));

            const newOrgProjects = { ...orgProjects };
            Object.keys(newOrgProjects).forEach(key => {
                newOrgProjects[key] = newOrgProjects[key].filter(p => p.id !== deleteModal.id);
            });
            setOrgProjects(newOrgProjects);

            setDeleteModal(null);
            setStatus({ type: 'success', title: 'Deleted', msg: "Project deleted successfully." });
        } catch (e) {
            setStatus({ type: 'error', title: 'Delete Failed', msg: "Could not delete project." });
        }
    };

    if (loading) return <div className="flex justify-center py-20"><Loader2 className="w-8 h-8 animate-spin text-modtale-accent" /></div>;

    return (
        <div className="space-y-8">
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-black text-slate-900 dark:text-white">Your Projects</h1>
                <Link to="/upload" className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-5 py-2.5 rounded-xl font-bold flex items-center gap-2 transition-colors shadow-lg shadow-modtale-accent/20">
                    <Plus className="w-4 h-4" /> New Project
                </Link>
            </div>

            {status && typeof document !== 'undefined' ? createPortal(
                <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} />,
                document.body
            ) : null}

            {transferModal && typeof document !== 'undefined' ? createPortal(
                <TransferProjectModal
                    project={transferModal}
                    myOrgs={myOrgs}
                    onClose={() => setTransferModal(null)}
                    onSuccess={(msg) => setStatus({ type: 'success', title: 'Request Sent', msg })}
                    onError={(msg) => setStatus({ type: 'error', title: 'Transfer Failed', msg })}
                />,
                document.body
            ) : null}

            {deleteModal && typeof document !== 'undefined' ? createPortal(
                <StatusModal
                    type="error"
                    title="Delete Project?"
                    message={`Are you sure you want to delete "${deleteModal.title}"? This cannot be undone.`}
                    actionLabel="Delete"
                    onAction={handleDelete}
                    onClose={() => setDeleteModal(null)}
                    secondaryLabel="Cancel"
                />,
                document.body
            ) : null}

            <div className="space-y-8">
                <div className="grid grid-cols-1 gap-4">
                    {projects.map(project => (
                        <div key={project.id} className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden backdrop-blur-md shadow-sm">
                            <ProjectListItem
                                project={project}
                                canManage={true}
                                isOwner={true}
                                showAuthor={true}
                                onTransfer={setTransferModal}
                                onDelete={setDeleteModal}
                            />
                        </div>
                    ))}
                </div>

                {Object.entries(orgProjects).map(([orgId, pList]) => {
                    const orgUser = myOrgs.find(o => o.id === orgId);
                    const orgDisplayName = orgUser?.displayName || orgUser?.username || orgId;
                    return (
                        <div key={orgId} className="border-t border-slate-200 dark:border-white/10 pt-8 mt-8">
                            <h2 className="text-xl font-black text-slate-900 dark:text-white mb-4 flex items-center gap-2">
                                <Building2 className="w-5 h-5 text-purple-500" />
                                {orgDisplayName}
                            </h2>
                            <div className="grid grid-cols-1 gap-4">
                                {pList.map(project => {
                                    const isOrgAdmin = myOrgs.some(o => o.id === orgId && o.organizationMembers?.some(m => m.userId === user.id && m.role === 'ADMIN'));
                                    return (
                                        <div key={project.id} className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden backdrop-blur-md shadow-sm">
                                            <ProjectListItem
                                                project={project}
                                                canManage={isOrgAdmin}
                                                showAuthor={false}
                                                onTransfer={setTransferModal}
                                                onDelete={setDeleteModal}
                                            />
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    );
                })}

                {contributedProjects.length > 0 && (
                    <div className="border-t border-slate-200 dark:border-white/10 pt-8 mt-8">
                        <h2 className="text-xl font-black text-slate-900 dark:text-white mb-4 flex items-center gap-2">
                            <Users className="w-5 h-5 text-blue-500" />
                            Contributed Projects
                        </h2>
                        <div className="grid grid-cols-1 gap-4">
                            {contributedProjects.map(project => {
                                const isOwner = project.authorId === user.id;
                                return (
                                    <div key={project.id} className="bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden backdrop-blur-md shadow-sm">
                                        <ProjectListItem
                                            project={project}
                                            canManage={isOwner}
                                            showAuthor={true}
                                            onTransfer={setTransferModal}
                                            onDelete={setDeleteModal}
                                        />
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                )}

                {projects.length === 0 && Object.keys(orgProjects).length === 0 && contributedProjects.length === 0 && (
                    <div className="text-center py-12 text-slate-400 bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl backdrop-blur-md shadow-sm">
                        <p>No projects found.</p>
                    </div>
                )}
            </div>
        </div>
    );
};