import React, { useState, useEffect, useMemo } from 'react';
import { Shield, Users, LayoutDashboard, ShieldAlert, Package, Activity, FileText, CalendarClock } from 'lucide-react';
import { adminClient } from '../api/adminClient';
import { StatusModal } from '@/components/ui/StatusModal';
import { extractApiErrorMessage } from '@/utils/api';
import { VerificationQueue } from '../components/VerificationQueue';
import { UserManagement } from '../components/UserManagement.tsx';
import { Review } from './Review';
import { ReportQueue } from '../components/ReportQueue';
import { ProjectManagement } from '../components/ProjectManagement';
import { PlatformAnalytics } from '../components/PlatformAnalytics';
import { AuditLogs } from '../components/AuditLogs';
import { StatusIncidents } from '../components/StatusIncidents';
import { AdminPermission, hasAdminPermission, hasAnyAdminPermission, isAdminUser } from '../utils/access';
import type { Project } from '@/types';

interface AdminPanelProps {
    currentUser: any;
}

type AdminTab = 'users' | 'verification' | 'reports' | 'projects' | 'analytics' | 'logs' | 'status';

export function AdminPanel({ currentUser }: AdminPanelProps) {
    const [activeTab, setActiveTab] = useState<AdminTab>('verification');
    const [status, setStatus] = useState<any>(null);

    const [pendingProjects, setPendingProjects] = useState<Project[]>([]);
    const [loadingQueue, setLoadingQueue] = useState(false);
    const [queueError, setQueueError] = useState<string | null>(null);

    const [reviewingProject, setReviewingProject] = useState<any>(null);
    const [loadingReview, setLoadingReview] = useState(false);

    const [reports, setReports] = useState<any[]>([]);
    const [reportsError, setReportsError] = useState<string | null>(null);

    const isAdmin = isAdminUser(currentUser);
    const canReadReviewQueue = hasAdminPermission(currentUser, AdminPermission.PROJECT_REVIEW_READ);
    const canDecideReviews = hasAdminPermission(currentUser, AdminPermission.PROJECT_REVIEW_DECIDE);
    const canRescanVersions = hasAdminPermission(currentUser, AdminPermission.PROJECT_VERSION_RESCAN);
    const canReadReports = hasAdminPermission(currentUser, AdminPermission.REPORT_READ);
    const canResolveReports = hasAdminPermission(currentUser, AdminPermission.REPORT_RESOLVE);
    const canReadStatus = hasAnyAdminPermission(currentUser, [AdminPermission.STATUS_INCIDENT_READ, AdminPermission.STATUS_INCIDENT_MANAGE]);
    const canManageStatus = hasAdminPermission(currentUser, AdminPermission.STATUS_INCIDENT_MANAGE);
    const canReadAnalytics = hasAdminPermission(currentUser, AdminPermission.PLATFORM_ANALYTICS_READ);
    const canReadLogs = hasAdminPermission(currentUser, AdminPermission.AUDIT_LOG_READ);
    const canUseProjectManagement = hasAnyAdminPermission(currentUser, [
        AdminPermission.PROJECT_MANAGE_READ,
        AdminPermission.PROJECT_MODERATE,
        AdminPermission.PROJECT_DELETE,
        AdminPermission.PROJECT_RESTORE,
        AdminPermission.PROJECT_VERSION_DELETE,
        AdminPermission.PROJECT_RAW_EDIT
    ]);
    const canUseUserManagement = hasAnyAdminPermission(currentUser, [
        AdminPermission.USER_READ,
        AdminPermission.USER_DELETE,
        AdminPermission.EMAIL_BAN_READ,
        AdminPermission.EMAIL_BAN_MANAGE,
        AdminPermission.USER_TIER_MANAGE,
        AdminPermission.USER_PERMISSION_MANAGE,
        AdminPermission.USER_RAW_READ,
        AdminPermission.USER_RAW_EDIT
    ]);

    const tabAccess: Record<AdminTab, boolean> = useMemo(() => ({
        verification: canReadReviewQueue,
        reports: canReadReports,
        status: canReadStatus,
        analytics: canReadAnalytics,
        projects: canUseProjectManagement,
        users: canUseUserManagement,
        logs: canReadLogs
    }), [
        canReadAnalytics,
        canReadLogs,
        canReadReports,
        canReadReviewQueue,
        canReadStatus,
        canUseProjectManagement,
        canUseUserManagement
    ]);
    const firstAllowedTab = (Object.keys(tabAccess) as AdminTab[]).find(tab => tabAccess[tab]);

    useEffect(() => {
        if (canReadReviewQueue) {
            fetchQueue();
        } else {
            setPendingProjects([]);
            setQueueError(null);
        }

        if (canReadReports) {
            fetchReports();
        } else {
            setReports([]);
            setReportsError(null);
        }
    }, [canReadReviewQueue, canReadReports]);

    useEffect(() => {
        if (!canReadReviewQueue) return;
        const interval = setInterval(() => {
            fetchQueue();
        }, 30_000);
        return () => clearInterval(interval);
    }, [canReadReviewQueue]);

    useEffect(() => {
        if (isAdmin && firstAllowedTab && !tabAccess[activeTab]) {
            setActiveTab(firstAllowedTab);
        }
    }, [activeTab, firstAllowedTab, isAdmin, tabAccess]);

    const fetchQueue = async () => {
        if (!canReadReviewQueue) return;
        setLoadingQueue(true);
        try {
            const data = await adminClient.getVerificationQueue();
            setPendingProjects(data);
            setQueueError(null);
        } catch (e) {
            setQueueError(extractApiErrorMessage(e, 'We could not load the verification queue.'));
        } finally {
            setLoadingQueue(false);
        }
    };

    const fetchReports = async () => {
        if (!canReadReports) return;
        try {
            const data = await adminClient.getReportQueue('OPEN');
            setReports(data);
            setReportsError(null);
        } catch (e) {
            setReportsError(extractApiErrorMessage(e, 'We could not load the report queue.'));
        }
    };

    const fetchProjectDetails = async (id: string) => {
        setLoadingReview(true);
        try {
            const data = await adminClient.getReviewDetails(id);
            setReviewingProject(data);
        } catch (e) {
            setStatus({ type: 'error', title: 'Error', msg: extractApiErrorMessage(e, "We could not load this project's review details.") });
        } finally {
            setLoadingReview(false);
        }
    };

    const handleApprove = async () => {
        setStatus({ type: 'success', title: 'Approved', msg: 'Project published successfully.' });
        setReviewingProject(null);
        fetchQueue();
    };

    const handleReject = async (reason: string) => {
        setStatus({ type: 'info', title: 'Rejected', msg: 'Project returned to drafts.' });
        setReviewingProject(null);
        fetchQueue();
    };

    if (!currentUser || !isAdmin) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-slate-50 dark:bg-slate-950">
                <div className="text-center p-8 bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl rounded-3xl border border-slate-200 dark:border-white/10 shadow-2xl">
                    <Shield className="w-12 h-12 text-red-500 mx-auto mb-4" />
                    <h1 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Access Denied</h1>
                    <p className="text-slate-500">You do not have permission to view this page.</p>
                </div>
            </div>
        );
    }

    const SidebarButton = ({ tab, icon: Icon, label, badge }: { tab: AdminTab, icon: any, label: string, badge?: number }) => (
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
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 pb-20">
            {status && <StatusModal type={status.type} title={status.title} message={status.msg} onClose={() => setStatus(null)} />}

            {reviewingProject && (
                <Review
                    reviewingProject={reviewingProject}
                    onClose={() => setReviewingProject(null)}
                    onApprove={handleApprove}
                    onReject={handleReject}
                    setStatus={setStatus}
                    canDecide={canDecideReviews}
                    canRescan={canRescanVersions}
                />
            )}

            <div className="max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 py-8 transition-[max-width,padding] duration-300">
                <div className="flex flex-col lg:flex-row gap-8">
                    <aside className="w-full lg:w-64 flex-shrink-0">
                        <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl p-6 shadow-2xl sticky top-28">
                            <div className="flex items-center gap-3 px-4 py-4 mb-4 border-b border-slate-100 dark:border-white/5">
                                <img src={currentUser.avatarUrl} alt="" className="w-10 h-10 rounded-full border border-slate-200 dark:border-white/10" />
                                <div className="overflow-hidden">
                                    <h2 className="font-black text-slate-900 dark:text-white truncate">Admin Console</h2>
                                    <p className="text-xs text-slate-500 uppercase font-bold tracking-wider">{currentUser.username}</p>
                                </div>
                            </div>

                            <nav className="space-y-1">
                                {canReadReviewQueue && (
                                    <SidebarButton
                                        tab="verification"
                                        icon={LayoutDashboard}
                                        label="Verification Queue"
                                        badge={pendingProjects.length}
                                    />
                                )}
                                {canReadReports && (
                                    <SidebarButton
                                        tab="reports"
                                        icon={ShieldAlert}
                                        label="Reports"
                                        badge={reports.length}
                                    />
                                )}
                                {canReadStatus && (
                                    <SidebarButton
                                        tab="status"
                                        icon={CalendarClock}
                                        label="Status"
                                    />
                                )}
                                {canReadAnalytics && (
                                    <SidebarButton
                                        tab="analytics"
                                        icon={Activity}
                                        label="Platform Analytics"
                                    />
                                )}
                                {canUseProjectManagement && (
                                    <SidebarButton
                                        tab="projects"
                                        icon={Package}
                                        label="Project Management"
                                    />
                                )}
                                {canUseUserManagement && (
                                    <SidebarButton
                                        tab="users"
                                        icon={Users}
                                        label="User Management"
                                    />
                                )}
                                {canReadLogs && (
                                    <SidebarButton
                                        tab="logs"
                                        icon={FileText}
                                        label="Audit Logs"
                                    />
                                )}
                            </nav>
                        </div>
                    </aside>

                    <div className="flex-1 min-w-0">
                        <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl p-8 shadow-2xl">
                            {activeTab === 'verification' && canReadReviewQueue && (
                                <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                    <div className="mb-8">
                                        <h1 className="text-3xl font-black text-slate-900 dark:text-white tracking-tight">Verification Queue</h1>
                                        <p className="text-slate-500 dark:text-slate-400 font-medium mt-1">Review pending projects and updates.</p>
                                    </div>
                                    {queueError && (
                                        <div className="mb-6 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 dark:border-red-500/20 dark:bg-red-500/10 dark:text-red-300">
                                            {queueError}
                                        </div>
                                    )}
                                    <VerificationQueue
                                        pendingProjects={pendingProjects}
                                        loadingQueue={loadingQueue}
                                        loadingReview={loadingReview}
                                        reviewingId={reviewingProject?.mod?.id}
                                        onReview={fetchProjectDetails}
                                    />
                                </div>
                            )}

                            {activeTab === 'reports' && canReadReports && (
                                <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                    <div className="mb-8">
                                        <h1 className="text-3xl font-black text-slate-900 dark:text-white tracking-tight">Report Queue</h1>
                                        <p className="text-slate-500 dark:text-slate-400 font-medium mt-1">Handle content violations and user reports.</p>
                                    </div>
                                    {reportsError && (
                                        <div className="mb-6 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 dark:border-red-500/20 dark:bg-red-500/10 dark:text-red-300">
                                            {reportsError}
                                        </div>
                                    )}
                                    <ReportQueue reports={reports} onRefresh={fetchReports} canResolve={canResolveReports} />
                                </div>
                            )}

                            {activeTab === 'analytics' && canReadAnalytics && (
                                <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                    <PlatformAnalytics />
                                </div>
                            )}

                            {activeTab === 'status' && canReadStatus && (
                                <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                    <StatusIncidents setStatus={setStatus} canManage={canManageStatus} />
                                </div>
                            )}

                            {activeTab === 'projects' && canUseProjectManagement && (
                                <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                    <div className="mb-8">
                                        <h1 className="text-3xl font-black text-slate-900 dark:text-white tracking-tight">Project Management</h1>
                                        <p className="text-slate-500 dark:text-slate-400 font-medium mt-1">Manage, unlist, or delete any project.</p>
                                    </div>
                                    <ProjectManagement setStatus={setStatus} currentAdmin={currentUser} />
                                </div>
                            )}

                            {activeTab === 'users' && canUseUserManagement && (
                                <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                    <div className="mb-8">
                                        <h1 className="text-3xl font-black text-slate-900 dark:text-white tracking-tight">User Management</h1>
                                        <p className="text-slate-500 dark:text-slate-400 font-medium mt-1">Manage roles, tiers, and user statuses.</p>
                                    </div>
                                    <UserManagement setStatus={setStatus} currentAdmin={currentUser} />
                                </div>
                            )}

                            {activeTab === 'logs' && canReadLogs && (
                                <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
                                    <div className="mb-8">
                                        <h1 className="text-3xl font-black text-slate-900 dark:text-white tracking-tight">Audit Logs</h1>
                                        <p className="text-slate-500 dark:text-slate-400 font-medium mt-1">Review all administrative actions.</p>
                                    </div>
                                    <AuditLogs />
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
