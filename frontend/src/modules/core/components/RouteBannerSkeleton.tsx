import { ArrowLeft, Download, Save } from 'lucide-react';
import { SkeletonSurface } from '@/components/ui/Skeleton';
import { ProjectCard, skeletonProject } from '@/modules/project/components/ProjectCard';

const container = 'max-w-[112rem] px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 mx-auto';

/** Geometry-only companion to ProfileLayout and ProjectLayout. Importing those
 * layouts here would eagerly load their cropper, dialogs, and editing controls. */
export function BannerRouteFrame({ profile = false, editing = false }: { profile?: boolean; editing?: boolean }) {
    const profileEditor = profile && editing;
    const inset = profileEditor ? 'w-full px-6' : container;
    const avatar = <div data-skeleton-media className={`${profile ? 'w-32 h-32 md:w-56 md:h-56' : 'w-32 h-32 lg:w-56 lg:h-56'} rounded-3xl border-4 md:border-8 border-white dark:border-slate-800 bg-white/40 dark:bg-slate-900/40 shadow-xl`} />;
    return <div className="min-h-screen bg-slate-50 dark:bg-[#0B1120] relative pb-20 overflow-x-hidden">
            {/* The banner sits behind the overlapping card. Keep its shimmer in
                that stacking layer rather than painting a media mask over it. */}
            <div data-skeleton-keep className="skeleton absolute top-0 inset-x-0 w-full aspect-[3/1] bg-slate-200 dark:bg-slate-800" />
            <div className="w-full aspect-[3/1] pointer-events-none" />
            {!profileEditor && <div className={`absolute top-6 inset-x-0 ${container}`}><span data-skeleton-keep className="flex items-center w-fit text-white/90 font-bold bg-black/30 backdrop-blur-md border border-white/10 p-2 md:px-4 md:py-2 rounded-full md:rounded-xl shadow-lg"><ArrowLeft className="size-5 md:size-4 md:mr-1" /><span className="hidden md:inline">Back</span></span></div>}
            <div className={`${inset} relative ${profileEditor ? '-mt-8 md:-mt-16' : '-mt-2 md:-mt-32'}`}>
                {profile ? <>
                    <div className={`bg-white dark:bg-slate-900 border border-slate-300 dark:border-white/20 rounded-3xl shadow-2xl p-6 md:px-10 md:pb-6 flex flex-col md:flex-row gap-4 md:gap-10 items-start ${profileEditor ? 'md:pt-8' : 'md:pt-10'}`}>
                        <div className={`hidden md:block shrink-0 ml-2 ${profileEditor ? 'md:-mt-12' : 'md:-mt-24'}`}>{avatar}</div>
                        <div className="flex-1 w-full min-w-0">
                            <div className="md:hidden -mt-16 mb-3">{avatar}</div>
                            <div className="mb-4 flex flex-col md:flex-row justify-between items-start gap-4"><div>
                                {profileEditor && <p data-skeleton-keep className="text-[10px] font-black uppercase tracking-widest mb-1">Username</p>}
                                <h1 className={`${profileEditor ? 'text-3xl md:text-4xl' : 'text-3xl md:text-5xl'} font-black tracking-tighter leading-tight pb-1`}>Creator name</h1>
                            </div><span className="hidden md:flex px-8 py-3 rounded-xl font-black bg-modtale-accent text-white">{editing ? 'Save Changes' : 'Sign in to follow'}</span></div>
                            {!profileEditor && <div className="md:hidden grid grid-cols-3 gap-3 mb-5">{['Downloads', 'Likes', 'Projects'].map(label => <div key={label} className="rounded-2xl p-3 text-center border border-slate-200 dark:border-white/5"><p className="text-lg font-black leading-none mb-1">123</p><p data-skeleton-keep className="text-[9px] font-bold uppercase tracking-widest">{label}</p></div>)}</div>}
                            {!profileEditor && <div className="md:hidden flex gap-3 mb-6 h-12"><span className="flex-1 h-12 rounded-xl bg-modtale-accent text-white text-center py-3 font-bold">Sign in to follow</span><div data-skeleton-media className="size-12" /></div>}
                            <div className="mb-4 md:mb-6">{profileEditor ? <><p data-skeleton-keep className="text-[10px] font-black uppercase tracking-widest mb-1">Bio</p><textarea readOnly tabIndex={-1} className="w-full h-24 rounded-xl border border-slate-200 dark:border-white/10" /></> : <p className="text-sm md:text-lg leading-snug md:leading-relaxed">Creating projects for the Hytale community.</p>}</div>
                            {!profileEditor && <div className="hidden md:flex gap-8 pt-6 border-t border-slate-200 dark:border-white/10">{['Downloads', 'Favorites', 'Followers', 'Projects'].map(label => <div key={label}><p className="text-2xl font-black">123</p><p data-skeleton-keep className="text-[10px] font-bold uppercase tracking-widest text-slate-400">{label}</p></div>)}</div>}
                        </div>
                    </div>
                    <div className={profileEditor ? 'mt-8 space-y-8 pb-12' : 'mt-6 md:mt-16'}>
                        {profileEditor ? <div className="bg-white/60 dark:bg-slate-900/40 border border-slate-200 dark:border-white/10 rounded-[2rem] p-6 md:p-8 shadow-sm backdrop-blur-xl">
                            <div className="flex items-center gap-4 mb-8 border-b border-slate-200 dark:border-white/10 pb-6"><div data-skeleton-media className="size-11 rounded-2xl" /><div><h3 data-skeleton-keep className="text-xl font-black tracking-tight">Security Settings</h3><p data-skeleton-keep className="text-xs text-slate-500 font-medium mt-1">Manage your credentials and account protection.</p></div></div>
                            <div className="space-y-10">{['Email Address', 'Two-Factor Authentication'].map(label => <div key={label}><h4 data-skeleton-keep className="font-bold text-sm mb-3">{label}</h4><div className="p-6 rounded-2xl bg-white dark:bg-white/[0.02] border border-slate-200 dark:border-white/10"><p className="font-bold">Account protection settings</p><p className="text-xs mt-2">Manage your credentials and account protection.</p></div></div>)}</div>
                        </div> : <><h2 data-skeleton-keep className="text-xl font-bold mb-6">Published Work</h2><div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 min-[1800px]:grid-cols-4 gap-4 md:gap-6 mt-4">{Array.from({ length: 12 }, (_, index) => <ProjectCard key={index} project={skeletonProject} isFavorite={false} isLoggedIn={false} onToggleFavorite={() => {}} disableNavigation />)}</div></>}
                    </div>
                </> : <div className="bg-white dark:bg-slate-900 border border-slate-300 dark:border-white/20 rounded-3xl shadow-2xl min-h-[80vh]">
                    <div className="relative md:p-12 md:pb-6 border-b border-slate-200 dark:border-white/10 p-4 pt-0">
                        <div className="lg:hidden -mt-16 mb-6">{avatar}</div>
                        <div className="flex flex-col lg:flex-row gap-8 items-start"><div className="hidden lg:block shrink-0 -mt-24 ml-2">{avatar}</div>
                            <div className="flex-1 min-w-0 pt-2 w-full"><div className="flex flex-col xl:flex-row justify-between gap-4"><div className="min-w-0 flex-1"><h1 className="text-4xl md:text-5xl font-black">Project title</h1><p className="text-lg font-medium mt-2">A project for the Hytale community.</p></div><span className="hidden lg:flex h-10 px-6 rounded-xl items-center gap-2 font-bold bg-modtale-accent text-white">{editing ? <Save className="size-4" /> : <Download className="size-4" />}{editing ? 'Save' : 'Download'}</span></div>
                                {editing ? <div className="mt-6 border-t border-slate-200 dark:border-white/10 pt-1 flex flex-wrap">{['Details', 'Gallery', 'Versions', 'Team', 'Settings'].map(label => <span key={label} data-skeleton-keep className="px-6 py-3 text-sm font-bold border-b-2 border-transparent">{label}</span>)}</div> : <div className="mt-8 pt-8 border-t border-slate-200 dark:border-white/10 flex gap-6"><span data-skeleton-keep className="font-bold">Description</span><span className="font-bold">123 Downloads</span></div>}
                            </div>
                        </div>
                    </div>
                    <div className="flex flex-col lg:grid lg:grid-cols-12 min-h-[500px]">
                        <div className="lg:col-span-8 xl:col-span-9 p-6 md:p-12 md:border-r border-slate-200 dark:border-white/5 order-2 lg:order-1 overflow-hidden">{editing ? <><h2 data-skeleton-keep className="text-lg font-bold mb-4">Description</h2><div className="border border-slate-200 dark:border-white/10 rounded-xl"><div data-skeleton-keep className="border-b border-slate-200 dark:border-white/10 px-4 py-3 font-bold">Write　 Preview</div><textarea readOnly tabIndex={-1} className="h-80 w-full bg-transparent" /></div></> : <div className="prose prose-lg dark:prose-invert max-w-none"><h2>About this project</h2><p>Discover new features and improvements for your Hytale experience. Explore this project and get started with the latest release.</p><h3>Getting started</h3><p>Download the project and follow the installation instructions to get started.</p></div>}</div>
                        <aside className="lg:col-span-4 xl:col-span-3 p-3 md:p-6 space-y-6 border-t lg:border-t-0 border-slate-200 dark:border-white/5 order-1 lg:order-2">{(editing ? ['Card Preview', 'License', 'Tags', 'External Links'] : ['Information', 'Categories', 'Links']).map((label, index) => <div key={label}><h3 data-skeleton-keep className="text-sm font-bold mb-4">{label}</h3>{editing && index === 0 ? <div data-skeleton-media className="aspect-video rounded-2xl" /> : <p className="text-sm leading-7">Project information</p>}</div>)}</aside>
                    </div>
                </div>}
            </div>
        </div>;
}

export function BannerRouteSkeleton(props: { profile?: boolean; editing?: boolean }) {
    return <SkeletonSurface label={props.profile ? 'Loading profile' : props.editing ? 'Loading project editor' : 'Loading project'}><BannerRouteFrame {...props} /></SkeletonSurface>;
}
