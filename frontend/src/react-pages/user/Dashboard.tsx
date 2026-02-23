import React from 'react';
import { Routes, Route, NavLink, Navigate } from 'react-router-dom';
import { User, Package, Users, Code, BarChart2, Bell, LayoutDashboard, Trophy } from 'lucide-react';
import type {User as UserType} from '../../types.ts';
import { ManageProjects } from '../../components/dashboard/manage-projects/ManageProjects.tsx';
import { ManageJams } from '../../components/dashboard/ManageJams.tsx';
import { DeveloperSettings } from '../../components/dashboard/DeveloperSettings.tsx';
import { Analytics } from '@/components/dashboard/Analytics.tsx';
import { NotificationSettings } from '../../components/dashboard/NotificationSettings.tsx';
import { ManageProfile } from '../../components/dashboard/ManageProfile.tsx';
import { ManageOrganization } from '../../components/dashboard/ManageOrganization.tsx';

interface DashboardProps {
    user: UserType | null;
    onRefreshUser?: () => void;
}

const Placeholder = ({ title, icon: Icon }: { title: string, icon: any }) => (
    <div className="flex flex-col items-center justify-center h-[50vh] text-slate-400">
        <div className="w-16 h-16 bg-slate-100 dark:bg-white/5 rounded-full flex items-center justify-center mb-4">
            <Icon className="w-8 h-8" />
        </div>
        <h3 className="text-xl font-bold text-slate-900 dark:text-white mb-2">{title}</h3>
        <p>This feature is coming soon.</p>
    </div>
);

export const Dashboard: React.FC<DashboardProps> = ({ user, onRefreshUser }) => {
    if (!user) return <Navigate to="/" replace />;

    const SidebarLink = ({ to, icon: Icon, label }: { to: string, icon: any, label: string }) => (
        <NavLink
            to={to}
            className={({ isActive }) => `flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-bold transition-all ${
                isActive
                    ? 'bg-modtale-accent text-white shadow-md shadow-modtale-accent/20'
                    : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-white/5 hover:text-slate-900 dark:hover:text-white'
            }`}
        >
            <Icon className="w-4 h-4" />
            {label}
        </NavLink>
    );

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark">
            <div className="max-w-[112rem] mx-auto px-8 sm:px-12 md:px-16 lg:px-28 py-8 transition-[max-width,padding] duration-300">
                <div className="flex flex-col lg:flex-row gap-8">
                    <aside className="w-full lg:w-64 flex-shrink-0">
                        <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 rounded-2xl p-4 shadow-sm sticky top-28">
                            <div className="flex items-center gap-3 px-4 py-4 mb-4 border-b border-slate-100 dark:border-white/5">
                                <img src={user.avatarUrl} alt="" className="w-10 h-10 rounded-full border border-slate-200 dark:border-white/10" />
                                <div className="overflow-hidden">
                                    <h2 className="font-black text-slate-900 dark:text-white truncate">{user.username}</h2>
                                    <p className="text-xs text-slate-500 uppercase font-bold tracking-wider">Creator</p>
                                </div>
                            </div>

                            <nav className="space-y-1">
                                <SidebarLink to="/dashboard/profile" icon={User} label="Manage Profile" />
                                <SidebarLink to="/dashboard/projects" icon={Package} label="Manage Projects" />
                                <SidebarLink to="/dashboard/jams" icon={Trophy} label="Manage Jams" />
                                <SidebarLink to="/dashboard/orgs" icon={Users} label="Organizations" />
                                <SidebarLink to="/dashboard/analytics" icon={BarChart2} label="Analytics" />
                                <SidebarLink to="/dashboard/notifications" icon={Bell} label="Notifications" />
                                <div className="h-px bg-slate-100 dark:bg-white/5 my-2 mx-4" />
                                <SidebarLink to="/dashboard/developer" icon={Code} label="Developer Settings" />
                            </nav>
                        </div>
                    </aside>

                    <div className="flex-1 min-w-0">
                        <Routes>
                            <Route path="/" element={<Navigate to="projects" replace />} />
                            <Route path="profile" element={<ManageProfile user={user} onUpdate={onRefreshUser || (() => {})} />} />
                            <Route path="projects" element={<ManageProjects user={user} />} />
                            <Route path="jams" element={<ManageJams user={user} />} />
                            <Route path="orgs" element={<ManageOrganization user={user} />} />
                            <Route path="analytics" element={<Analytics />} />
                            <Route path="analytics/project/:id" element={<Analytics />} />
                            <Route path="notifications" element={<NotificationSettings user={user} />} />
                            <Route path="developer" element={<div className="-mt-8 -mx-4"><DeveloperSettings /></div>} />
                        </Routes>
                    </div>
                </div>
            </div>
        </div>
    );
};