import React, { useState, useEffect } from 'react';
import { api } from '../utils/api.ts';
import { StatusModal } from '../components/ui/StatusModal.tsx';
import { Shield, Users, LayoutDashboard, ShieldAlert, Package } from 'lucide-react';
import type { Mod } from '../types.ts';
import { VerificationQueue } from '../components/admin/VerificationQueue';
import { UserManagement } from '../components/admin/UserManagement';
import { ReviewInterface } from '../components/admin/ReviewInterface';
import { ReportQueue } from '../components/admin/ReportQueue';
import { ProjectManagement } from '../components/admin/ProjectManagement';

interface AdminPanelProps {
    currentUser: any;
}

export const AdminPanel: React.FC<AdminPanelProps> = ({ currentUser }) => {
    const [activeTab, setActiveTab] = useState<'users' | 'verification' | 'reports' | 'projects'>('verification');
    const [status, setStatus] = useState<any>(null);

    const [pendingProjects, setPendingProjects] = useState<Mod[]>([]);
    const [loadingQueue, setLoadingQueue] = useState(false);

    const [reviewingProject, setReviewingProject] = useState<any>(null);
    const [loadingReview, setLoadingReview] = useState(false);

    const [reports, setReports] = useState<any[]>([]);

    const isAdmin = currentUser?.roles?.includes('ADMIN') || currentUser?.username === 'Villagers654';
    const isSuperAdmin = currentUser?.username === 'Villagers654';

    useEffect(() => {
        if (isAdmin) {
            fetchQueue();
            if (activeTab === 'reports') fetchReports();
        }
    }, [isAdmin, activeTab]);

    const fetchQueue = async () => {
        setLoadingQueue(true);
        try {
            const res = await api.get('/admin/verification/queue');
            setPendingProjects(res.data);
        } catch (e) {
            console.error(e);
        } finally {
            setLoadingQueue(false);
        }
    };

    const fetchReports = async () => {
        try {
            const res = await api.get('/admin/reports/queue');
            setReports(res.data);
        } catch (e) {
            console.error(e);
        }
    };

    const fetchProjectDetails = async (id: string) => {
        setLoadingReview(true);
        try {
            const res = await api.get(`/admin/projects/${id}/review-details`);
            setReviewingProject(res.data);
        } catch (e) {
            setStatus({ type: 'error', title: 'Error', msg: 'Could not load project details' });
        } finally {
            setLoadingReview(false);
        }
    };

    const handleApprove = async () => {
        if (!reviewingProject) return;
        try {
            await api.post(`/projects/${reviewingProject.mod.id}/publish`);
            setStatus({ type: 'success', title: 'Approved', msg: 'Project published successfully.' });
            setReviewingProject(null);
            fetchQueue();
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Error', msg: e.response?.data || 'Failed to approve.' });
        }
    };

    const handleReject = async (reason: string) => {
        if (!reviewingProject) return;
        try {
            await api.post(`/admin/projects/${reviewingProject.mod.id}/reject`, { reason });
            setStatus({ type: 'info', title: 'Rejected', msg: 'Project returned to drafts.' });
            setReviewingProject(null);
            fetchQueue();
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Error', msg: e.response?.data || 'Failed to reject.' });
        }
    };

    if (!currentUser || !isAdmin) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-slate-50 dark:bg-modtale-dark">
                <div className="text-center p-8 bg-white dark:bg-slate-900 rounded-3xl border border-slate-200 dark:border-white/5 shadow-xl">
                    <Shield className="w-12 h-12 text-red-500 mx-auto mb-4" />
                    <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Access Denied</h1>
                    <p className="text-slate-500">You do not have permission to view this page.</p>
                </div>
            </div>
        );
    }

    const SidebarButton = ({ tab, icon: Icon, label, badge }: { tab: 'users' | 'verification' | 'reports' | 'projects', icon: any, label: string, badge?: number }) => (
        <button
            onClick={() => setActiveTab(tab)}
            className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-bold transition-all ${
                activeTab === tab
                    ? 'bg-modtale-accent text-white shadow-md shadow-modtale-accent/20'
                    : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-white/5 hover:text-slate-900 dark:hover:text-white'
            }`}
        >
            <Icon className="w-4 h-4" />
            <span className="flex-1 text-left">{label}</span>
            {badge !== undefined && badge > 0 && (
                <span className={`text-[10px] px-2 py-0.5 rounded-full font-black ${
                    activeTab === tab ? 'bg-white text-modtale-accent' : 'bg-slate-200 dark:bg-white/10 text-slate-600 dark:text-slate-300'
                }`}>
                    {badge}
                </span>
            )}
        </button>
    );

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark">
            {status && <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} />}

            {reviewingProject && (
                <ReviewInterface
                    reviewingProject={reviewingProject}
                    onClose={() => setReviewingProject(null)}
                    onApprove={handleApprove}
                    onReject={handleReject}
                    setStatus={setStatus}
                />
            )}

            <div className="max-w-[112rem] mx-auto px-8 sm:px-12 md:px-16 lg:px-28 py-8 transition-[max-width,padding] duration-300">
                <div className="flex flex-col lg:flex-row gap-8">
                    <aside className="w-full lg:w-64 flex-shrink-0">
                        <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 rounded-2xl p-4 shadow-sm sticky top-28">
                            <div className="flex items-center gap-3 px-4 py-4 mb-4 border-b border-slate-100 dark:border-white/5">
                                <img src={currentUser.avatarUrl} alt="" className="w-10 h-10 rounded-full border border-slate-200 dark:border-white/10" />
                                <div className="overflow-hidden">
                                    <h2 className="font-black text-slate-900 dark:text-white truncate">Admin Console</h2>
                                    <p className="text-xs text-slate-500 uppercase font-bold tracking-wider">{currentUser.username}</p>
                                </div>
                            </div>

                            <nav className="space-y-1">
                                <SidebarButton
                                    tab="verification"
                                    icon={LayoutDashboard}
                                    label="Verification Queue"
                                    badge={pendingProjects.length}
                                />
                                <SidebarButton
                                    tab="reports"
                                    icon={ShieldAlert}
                                    label="Reports"
                                    badge={reports.length}
                                />
                                {isSuperAdmin && (
                                    <>
                                        <SidebarButton
                                            tab="projects"
                                            icon={Package}
                                            label="Project Management"
                                        />
                                        <SidebarButton
                                            tab="users"
                                            icon={Users}
                                            label="User Management"
                                        />
                                    </>
                                )}
                            </nav>
                        </div>
                    </aside>

                    <div className="flex-1 min-w-0">
                        {activeTab === 'verification' && (
                            <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                <div className="mb-8">
                                    <h1 className="text-3xl font-black text-slate-900 dark:text-white tracking-tight">Verification Queue</h1>
                                    <p className="text-slate-500 dark:text-slate-400 font-medium mt-1">Review pending projects and updates.</p>
                                </div>
                                <VerificationQueue
                                    pendingProjects={pendingProjects}
                                    loadingQueue={loadingQueue}
                                    loadingReview={loadingReview}
                                    reviewingId={reviewingProject?.mod?.id}
                                    onReview={fetchProjectDetails}
                                />
                            </div>
                        )}

                        {activeTab === 'reports' && (
                            <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                <div className="mb-8">
                                    <h1 className="text-3xl font-black text-slate-900 dark:text-white tracking-tight">Report Queue</h1>
                                    <p className="text-slate-500 dark:text-slate-400 font-medium mt-1">Handle content violations and user reports.</p>
                                </div>
                                <ReportQueue reports={reports} onRefresh={fetchReports} />
                            </div>
                        )}

                        {activeTab === 'projects' && isSuperAdmin && (
                            <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                <div className="mb-8">
                                    <h1 className="text-3xl font-black text-slate-900 dark:text-white tracking-tight">Project Management</h1>
                                    <p className="text-slate-500 dark:text-slate-400 font-medium mt-1">Manage, unlist, or delete any project.</p>
                                </div>
                                <ProjectManagement setStatus={setStatus} />
                            </div>
                        )}

                        {activeTab === 'users' && isSuperAdmin && (
                            <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                <div className="mb-8">
                                    <h1 className="text-3xl font-black text-slate-900 dark:text-white tracking-tight">User Management</h1>
                                    <p className="text-slate-500 dark:text-slate-400 font-medium mt-1">Manage roles, tiers, and user statuses.</p>
                                </div>
                                <UserManagement setStatus={setStatus} />
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};