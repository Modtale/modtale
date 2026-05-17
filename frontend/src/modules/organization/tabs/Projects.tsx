import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { ManagedProjectCard } from '@/components/shared/ManagedProjectCard';
import { TransferProjectModal } from '@/components/shared/TransferProjectModal';
import { StatusModal } from '@/components/ui/StatusModal';
import { Spinner } from '@/components/ui/Spinner';
import { theme } from '@/styles/theme';
import { organizationClient, hasOrgPermission } from '../api/organizationClient';
import type { Project, User } from '@/types';

interface ProjectsProps {
    org: User;
    currentUser: User;
    userOrgs: User[];
}

export const Projects: React.FC<ProjectsProps> = ({ org, currentUser, userOrgs }) => {
    const [projects, setProjects] = useState<Project[]>([]);
    const [loading, setLoading] = useState(true);
    const [deleteModal, setDeleteModal] = useState<Project | null>(null);
    const [transferModal, setTransferModal] = useState<Project | null>(null);
    const [status, setStatus] = useState<{type: 'success'|'error', title: string, msg: string} | null>(null);

    const canManage = hasOrgPermission(org, currentUser.id, 'PROJECT_EDIT_METADATA');

    useEffect(() => {
        const fetchProjects = async () => {
            try {
                const data = await organizationClient.getProjects(org.id);
                setProjects(data);
            } catch (err) {
                console.error(err);
            } finally {
                setLoading(false);
            }
        };
        fetchProjects();
    }, [org.id]);

    const handleDeleteProject = async () => {
        if (!deleteModal) return;
        try {
            setProjects(prev => prev.filter(p => p.id !== deleteModal.id));
            setStatus({ type: 'success', title: 'Deleted', msg: "Project deleted successfully." });
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Delete Failed', msg: e.response?.data || "Could not delete project." });
        } finally {
            setDeleteModal(null);
        }
    };

    if (loading) return <div className="p-12 flex justify-center"><Spinner /></div>;

    return (
        <div className="space-y-4 animate-in fade-in slide-in-from-bottom-2">
            {status && createPortal(
                <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} />,
                document.body
            )}

            {deleteModal && createPortal(
                <StatusModal
                    type="error"
                    title="Delete Project?"
                    message={`Are you sure you want to delete "${deleteModal.title}"? This cannot be undone.`}
                    actionLabel="Delete"
                    onAction={handleDeleteProject}
                    onClose={() => setDeleteModal(null)}
                    secondaryLabel="Cancel"
                />,
                document.body
            )}

            {transferModal && createPortal(
                <TransferProjectModal
                    project={transferModal}
                    myOrgs={userOrgs}
                    onClose={() => setTransferModal(null)}
                    onSuccess={(msg) => setStatus({ type: 'success', title: 'Request Sent', msg })}
                    onError={(msg) => setStatus({ type: 'error', title: 'Transfer Failed', msg })}
                />,
                document.body
            )}

            {projects.length > 0 ? (
                <div className="grid grid-cols-1 gap-4">
                    {projects.map(project => (
                        <div key={project.id} className={`${theme.colors.bgSurface} border ${theme.colors.border} rounded-2xl overflow-hidden shadow-sm`}>
                            <ManagedProjectCard
                                project={project}
                                canManage={canManage}
                                showAuthor={false}
                                onTransfer={setTransferModal}
                                onDelete={setDeleteModal}
                            />
                        </div>
                    ))}
                </div>
            ) : (
                <div className={`text-center py-12 ${theme.colors.textMuted} ${theme.colors.bgSurface} border ${theme.colors.border} rounded-2xl shadow-sm`}>
                    No projects found.
                </div>
            )}
        </div>
    );
};