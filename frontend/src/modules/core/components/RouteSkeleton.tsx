import type { ReactNode } from 'react';
import { ArrowLeft, BarChart2, Bell, Code, Package, Plus, Users, User } from 'lucide-react';
import { SkeletonSurface } from '@/components/ui/Skeleton';
import { PROJECT_TYPES } from '@/data/categories';
import { ManagedProjectCard } from '@/components/shared/ManagedProjectCard';
import { skeletonProject } from '@/modules/user/skeletons/fixtures';
import { AuthRouteSkeleton, DocsRouteSkeleton } from './RoutePanelSkeletons';
import { BannerRouteFrame, BannerRouteSkeleton } from './RouteBannerSkeleton';

// Module-loading frames only. Keep view modules, their hooks, editors, and charts
// behind App's lazy imports. Data-loading skeletons remain owned by those views.
const container = 'max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28';
const panel = 'bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl shadow-2xl';
const card = 'bg-white/40 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden backdrop-blur-md shadow-sm';

function Title({ children, description }: { children: ReactNode; description?: string }) {
    return <div><h1 data-skeleton-keep className="text-2xl font-black text-slate-900 dark:text-white">{children}</h1>
        {description && <p data-skeleton-keep className="text-sm text-slate-500">{description}</p>}</div>;
}

const dashboardLinks = [
    { id: 'profile', label: 'Manage Profile', icon: User },
    { id: 'projects', label: 'Manage Projects', icon: Package },
    { id: 'orgs', label: 'Organizations', icon: Users },
    { id: 'analytics', label: 'Analytics', icon: BarChart2 },
    { id: 'notifications', label: 'Notifications', icon: Bell },
    { id: 'developer', label: 'Developer Settings', icon: Code },
];

function DashboardBody({ section }: { section: string }) {
    if (section === 'profile') return <BannerRouteFrame profile editing />;
    if (section === 'analytics') return <div className="pt-2 space-y-6">
        <div className="flex flex-col md:flex-row md:items-end justify-between gap-6 mb-8"><Title description="Track your project performance.">Analytics</Title>
            <div data-skeleton-keep className="flex w-fit p-1 rounded-xl bg-white/60 dark:bg-black/20 border border-slate-200 dark:border-white/10">{['7D', '30D', '90D'].map(day => <span key={day} className="w-14 py-2 text-center text-xs font-bold">{day}</span>)}</div></div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">{['Downloads', 'Favorites', 'Views', 'Conversion'].map(label => <div key={label} className={`${card} !rounded-3xl p-6`}>
            <div data-skeleton-media className="size-12 rounded-2xl mb-6" /><p data-skeleton-keep className="text-[10px] font-black uppercase tracking-widest text-slate-500">{label}</p><p className="text-3xl md:text-4xl font-black leading-none">12,345</p>
        </div>)}</div>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">{['Downloads over Time', 'Project Performance'].map(label => <div key={label} className={`${card} !rounded-3xl h-[500px] p-6`}>
            <h3 data-skeleton-keep className="font-bold text-lg mb-4">{label}</h3><div className="h-[380px] border-l border-b border-slate-200 dark:border-white/10" />
        </div>)}</div>
    </div>;
    if (section === 'notifications') return <div className="space-y-6">
        <Title description="Control what alerts you receive.">Notification Preferences</Title>
        <div className={card}>{['Favorite Project Updates', 'New Creator Uploads', 'New Comments', 'New Followers', 'Dependency Updates'].map(label => <div key={label} className="flex flex-col sm:flex-row sm:items-center justify-between p-6 border-b border-slate-200 dark:border-white/10 last:border-0 gap-4">
            <div><h3 data-skeleton-keep className="font-bold">{label}</h3><p className="text-sm mt-1">Choose which notifications you receive.</p></div>
            <div data-skeleton-keep className="flex p-1 rounded-xl border border-slate-200 dark:border-white/10">{['Off', 'On'].map(label => <span key={label} className="w-16 py-2 text-xs text-center font-bold">{label}</span>)}</div>
        </div>)}</div><div className="flex justify-end"><span data-skeleton-keep className="px-8 py-3 rounded-xl bg-modtale-accent text-white font-bold">Save Changes</span></div>
    </div>;
    if (section === 'developer') return <div className="space-y-8">
        <Title description="Manage highly granular, context-aware API keys.">Developer Settings</Title>
        <div className={`${card} p-6`}><h2 data-skeleton-keep className="text-xl font-bold mb-2">API Keys</h2><p className="text-sm mb-6">Manage API keys for your applications and integrations.</p>
            <div className="flex flex-col sm:flex-row gap-4"><div data-skeleton-media className="h-12 flex-1 rounded-xl" /><span data-skeleton-keep className="px-6 py-3 rounded-xl bg-modtale-accent text-white font-bold">Create API Key</span></div>
        </div>
    </div>;
    if (section === 'orgs') return <div className="space-y-8"><Title>Organizations</Title><div className="grid grid-cols-1 gap-4">{[0, 1, 2].map(index => <div key={index} className={`${card} p-6 flex items-center gap-4`}><div data-skeleton-media className="size-16 shrink-0 rounded-xl" /><div><h2 className="text-xl font-black">Organization name</h2><p className="text-sm">Organization members</p></div></div>)}</div></div>;
    return <div className="space-y-8">
        <div className="flex justify-between items-center mb-6"><Title>Your Projects</Title><span data-skeleton-keep className="bg-modtale-accent text-white px-5 py-2.5 rounded-xl font-bold flex items-center gap-2 shadow-lg shadow-modtale-accent/20"><Plus className="size-4" />New Project</span></div>
        <div className="grid grid-cols-1 gap-4">{[0, 1, 2].map(index => <div key={index} className={card}><ManagedProjectCard project={skeletonProject} canManage isOwner showAuthor onTransfer={() => {}} onDelete={() => {}} /></div>)}</div>
    </div>;
}

function DashboardRouteSkeleton({ section, admin = false }: { section: string; admin?: boolean }) {
    // Admin permissions are not available until authentication completes. Mask
    // navigation labels instead of presenting permission-dependent controls.
    const links = admin ? ['Verification Queue', 'Reports', 'Status', 'Analytics', 'Projects', 'Users', 'Audit Logs'].map(label => ({ id: label, label, icon: Package })) : dashboardLinks;
    return <SkeletonSurface label={admin ? 'Loading admin console' : 'Loading dashboard'}>
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 pb-20"><div className={`${container} py-8`}><div className="flex flex-col lg:flex-row gap-8">
            <aside className="w-full lg:w-64 flex-shrink-0"><div className={`${panel} p-6 sticky top-28`}>
                <div className="flex items-center gap-3 px-4 py-4 mb-4 border-b border-slate-100 dark:border-white/5">
                    <div data-skeleton-media className="size-10 shrink-0 rounded-full border border-slate-200 dark:border-white/10" /><div className="overflow-hidden"><h2 className="font-black truncate">{admin ? 'Admin Console' : 'Creator name'}</h2><p data-skeleton-keep={!admin || undefined} className="text-xs text-slate-500 uppercase font-bold tracking-wider">Creator</p></div>
                </div>
                <nav className="space-y-1">{links.map(({ id, label, icon: Icon }) => <div key={id}>
                    {id === 'developer' && <div className="h-px bg-slate-100 dark:bg-white/5 my-2 mx-4" />}
                    <div data-skeleton-keep={!admin || undefined} className={`flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-bold ${id === section ? 'bg-modtale-accent text-white shadow-md shadow-modtale-accent/20' : 'text-slate-600 dark:text-slate-400'}`}><Icon className="size-4 shrink-0" />{label}</div>
                </div>)}</nav>
            </div></aside>
            <div className="flex-1 min-w-0"><div className={`${panel} p-8`}>
                {admin ? <div className="space-y-6"><Title>Verification Queue</Title>{[0, 1, 2].map(index => <div key={index} className={`${card} p-6 flex gap-4`}><div data-skeleton-media className="size-16 shrink-0" /><div><h2 className="font-black text-lg">Project title</h2><p className="text-sm">Version awaiting review</p></div></div>)}</div> : <DashboardBody section={section} />}
            </div></div>
        </div></div></div>
    </SkeletonSurface>;
}

function UploadRouteSkeleton() {
    const types = PROJECT_TYPES.filter(type => type.id !== 'All');
    const columns = Math.ceil(types.length / 2);
    const typeCard = (type: typeof types[number]) => <div key={type.id} style={{ flex: `0 0 calc((100% - ${(columns - 1) * 24}px) / ${columns})` }} className="p-8 rounded-3xl border-2 border-slate-300 dark:border-white/20 text-left flex flex-col justify-center aspect-[4/3] bg-white/80 dark:bg-slate-900/80 backdrop-blur-xl shadow-xl">
        <div className="size-16 rounded-2xl flex items-center justify-center mb-6 bg-white/50 dark:bg-black/30 border border-slate-200/50 dark:border-white/5"><type.icon className="size-8" /></div>
        <h3 className="font-black text-2xl mb-2">{type.label}</h3><p className="text-xs md:text-sm leading-relaxed font-medium text-slate-500">{type.id === 'MODPACK' ? 'Bundle multiple mods into a pack.' : type.id === 'SAVE' ? 'Share worlds, schematics, or lobbies.' : type.id === 'PLUGIN' ? 'Server-side logic, tools, and scripts.' : 'Custom models, textures, and art.'}</p>
    </div>;
    return <SkeletonSurface label="Loading project creation"><div className="min-h-screen bg-slate-50 dark:bg-slate-950"><div className={`${container} w-full pt-12 md:pt-20 pb-16`}>
        <div data-skeleton-keep className="flex items-center text-sm font-bold text-slate-500 w-fit mb-12 md:mb-16"><ArrowLeft className="size-4 mr-2" />Cancel</div>
        <div className="text-center pb-8 md:pb-16"><div data-skeleton-keep className="mb-12"><h1 className="text-4xl md:text-6xl font-black tracking-tight mb-4 leading-none">What are you creating?</h1><p className="text-lg md:text-xl text-slate-500 font-medium">Select a project type to get started.</p></div>
            <div data-skeleton-keep className="hidden lg:flex flex-col gap-6 w-full max-w-6xl mx-auto"><div className="flex justify-center gap-6">{types.slice(0, columns).map(typeCard)}</div><div className="flex justify-center gap-6">{types.slice(columns).map(typeCard)}</div></div>
            <div data-skeleton-keep className="grid lg:hidden grid-cols-1 sm:grid-cols-2 gap-6 w-full max-w-2xl mx-auto">{types.map(typeCard)}</div>
        </div>
    </div></div></SkeletonSurface>;
}

/** Pure props make every module fallback available to screenshot review without
 * suspending a production route or mounting a page's data-fetching hooks. */
export function RouteSkeleton({ pathname, search = '' }: { pathname: string; search?: string }) {
    const path = pathname.toLowerCase().replace(/\/+$/, '') || '/';
    if (path === '/dashboard' || path.startsWith('/dashboard/')) return <DashboardRouteSkeleton section={path.split('/')[2] || 'projects'} />;
    if (path === '/admin') return <DashboardRouteSkeleton admin section="verification" />;
    if (path === '/upload') return <UploadRouteSkeleton />;
    if (/^\/creator\/[^/]+$/.test(path)) return <BannerRouteSkeleton profile />;
    if (/^\/(project|mod|modpack|world)\/[^/]+\/edit$/.test(path)) return <BannerRouteSkeleton editing />;
    if (/^\/(project|mod|modpack|world)\/[^/]+(?:\/.*)?$/.test(path)) return <BannerRouteSkeleton />;
    if (path === '/verify' || path === '/reset-password' || path === '/mfa') return <AuthRouteSkeleton path={path} hasToken={Boolean(new URLSearchParams(search).get('token'))} />;
    if (path === '/terms' || path === '/privacy' || path === '/api-docs') return <DocsRouteSkeleton path={path} />;
    // Swagger redirects without rendering a page; eager browse/404 routes never
    // suspend on a route module. Do not flash an unrelated page frame for them.
    return null;
}
