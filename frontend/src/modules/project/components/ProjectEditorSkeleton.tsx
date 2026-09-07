import { ExternalLink, Save, Edit3, FileText, UploadCloud, Image as ImageIcon, Users, Settings, BookOpen, Eye, Scale, Tag, Link as LinkIcon } from 'lucide-react';
import type { Project } from '@/types';
import { theme } from '@/styles/theme';
import { ProjectLayout, SidebarSection } from './ProjectLayout';
import { ProjectCard } from './ProjectCard';
import { EditDetails } from '../tabs/EditDetails';
import { PROJECT_LOADING_DATA, PROJECT_LOADING_PROSE, loadingNoop } from './projectLoadingData';

/** Initial Details tab: uses the editor's real form and card in its real layout. */
export function ProjectEditorSkeleton({ project: summary }: { project?: Project | null }) {
    const project = { ...PROJECT_LOADING_DATA, ...summary };
    const tabs = [
        { id: 'details', icon: FileText, label: 'Details' },
        { id: 'files', icon: UploadCloud, label: `Files (${project.versions?.length || 0})` },
        { id: 'gallery', icon: ImageIcon, label: `Gallery (${project.galleryImages?.length || 0})` },
        { id: 'team', icon: Users, label: 'Team' }, { id: 'settings', icon: Settings, label: 'Settings' },
        ...(project.hmWikiEnabled ? [{ id: 'wiki', icon: BookOpen, label: 'Wiki Preview' }] : [])
    ];
    return <ProjectLayout loading isEditing onBack={loadingNoop}
        headerActions={<div className="flex items-center gap-3">
            <button className={`p-3 rounded-xl border ${theme.colors.border} ${theme.colors.bgSurface} ${theme.colors.textSecondary} hover:${theme.colors.textPrimary} transition-all shadow-sm`}><ExternalLink className="w-5 h-5" /></button>
            <button className={`px-6 h-10 rounded-xl font-bold flex items-center gap-2 transition-all shadow-lg ${theme.colors.bgSurface} ${theme.colors.textMuted} border ${theme.colors.border} cursor-not-allowed`}><Save className="w-4 h-4" /> Save</button>
        </div>}
        headerContent={<div>
            <div className="flex items-center gap-3 group rounded-2xl -ml-3 px-3 py-1.5 cursor-pointer hover:bg-black/5 dark:hover:bg-white/5 transition-colors">
                <h1 className={`text-4xl md:text-5xl font-black ${theme.colors.textPrimary} tracking-normal break-words`}>{project.title}</h1>
                <Edit3 className="w-5 h-5 text-slate-400 opacity-0 group-hover:opacity-100 transition-opacity" />
            </div>
            <input readOnly value={project.description} className={`text-lg ${theme.colors.textPrimary} font-medium bg-transparent border-b border-transparent outline-none w-full mt-2 hover:border-slate-300 dark:hover:border-white/20 focus:border-modtale-accent pb-1`} />
        </div>}
        tabs={<div className="flex items-center gap-1">{tabs.map(t => <button key={t.id} className={`px-6 py-3 text-sm font-bold border-b-2 transition-colors flex items-center gap-2 ${t.id === 'details' ? 'border-modtale-accent text-slate-900 dark:text-slate-300' : 'border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-slate-300'}`}><t.icon className="w-4 h-4" />{t.label}</button>)}</div>}
        sidebarContent={<>
            <SidebarSection title="Card Preview" icon={Eye}>
                <div className={`w-full max-w-[340px] mx-auto relative group overflow-hidden rounded-2xl border ${theme.colors.border} text-left transition-all hover:shadow-lg hover:shadow-modtale-accent/10 focus:outline-none focus:ring-2 focus:ring-modtale-accent focus:ring-offset-2 dark:focus:ring-offset-slate-950`}>
                    <div className="pointer-events-none select-none"><ProjectCard project={project} isFavorite={false} onToggleFavorite={loadingNoop} isLoggedIn={false} disableNavigation /></div>
                </div>
            </SidebarSection>
            {project.classification !== 'MODPACK' && <SidebarSection title="License" icon={Scale} defaultOpen={false}>{null}</SidebarSection>}
            <SidebarSection title="Tags" icon={Tag} defaultOpen={false}>{null}</SidebarSection>
            <SidebarSection title="External Links" icon={LinkIcon} defaultOpen={false}>{null}</SidebarSection>
        </>}
        mainContent={<EditDetails projectData={project} metaData={{ title: project.title, summary: project.description, description: PROJECT_LOADING_PROSE, tags: project.tags || [], links: {}, repositoryUrl: '', iconFile: null, iconPreview: null, customLicenseOpenSource: false }}
            setMetaData={loadingNoop} readOnly={false} hasProjectPermission={() => true} editorMode="write" setEditorMode={loadingNoop} markDirty={loadingNoop} />}
    />;
}
