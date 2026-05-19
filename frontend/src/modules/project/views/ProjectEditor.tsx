import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation, Link } from 'react-router-dom';
import { createPortal } from 'react-dom';
import { Save, UploadCloud, Eye, Image as ImageIcon, Users, BookOpen, Settings, FileText, ExternalLink, Send, Check, X } from 'lucide-react';

import type { User, Project, ProjectVersion } from '@/types';
import { theme } from '@/styles/theme';
import { SiteRoutes } from '@/utils/routes';
import { LICENSES } from '@/data/categories';
import { useHMWiki, WikiSidebar } from '@/modules/project/components/HMWiki';
import { SidebarSection, ProjectLayout } from '../components/ProjectLayout';

import { useProjectEditor } from '../hooks/useProjectEditor';
import { useProjectDetail } from '@/modules/project/hooks/useProjectDetail';
import { EditDetails } from '../tabs/EditDetails';
import { Files } from '../tabs/Files';
import { Gallery } from '../tabs/Gallery';
import { Team } from '../tabs/Team';
import { Settings as SettingsTab } from '../tabs/Settings';
import { WikiPreview } from '../tabs/WikiPreview';

import { Spinner } from '@/components/ui/Spinner';
import { ImageCropperModal } from '@/components/ui/ImageCropperModal';
import { ProjectCard } from '@/modules/project/components/ProjectCard';
import type { MetadataFormData, VersionFormData } from '../components/FormShared';

interface ProjectEditorViewProps {
    currentUser: User | null;
    onShowStatus: (type: 'success' | 'error' | 'warning' | 'info', title: string, msg: string) => void;
}

export const ProjectEditorView: React.FC<ProjectEditorViewProps> = ({ currentUser, onShowStatus }) => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const location = useLocation();

    const [activeTab, setActiveTab] = useState<string>('details');
    const [editorMode, setEditorMode] = useState<'write' | 'preview'>('write');

    const { project: projectData, setProject: setProjectData, loading, contributors } = useProjectDetail(id, null, currentUser);

    const [metaData, setMetaData] = useState<MetadataFormData>({
        title: '', summary: '', description: '', tags: [], links: {}, repositoryUrl: '', iconFile: null, iconPreview: null, slug: ''
    });
    const [versionData, setVersionData] = useState<VersionFormData>({
        projectIds: [], versionNumber: '', gameVersions: [], changelog: '', file: null, dependencies: [], modIds: [], channel: 'RELEASE'
    });

    const [bannerFile, setBannerFile] = useState<File | null>(null);
    const [bannerPreview, setBannerPreview] = useState<string | null>(null);

    const {
        repos, loadingRepos, manualRepo, setManualRepo, repoValid, isDirty, setIsDirty,
        slugError, setSlugError, userSearchResults, setUserSearchResults, provider,
        setProvider, markDirty, checkRepoUrl, fetchRepos, handleRoleUpdate, handleCancelInvite,
        handleSave, handleSubmit, isSaving, handleGalleryUpload, handleGalleryDelete
    } = useProjectEditor(projectData, currentUser, metaData, setMetaData, setProjectData, onShowStatus);

    const [wikiPreviewSlug, setWikiPreviewSlug] = useState<string | undefined>();
    const { data: wikiData, loading: wikiLoading, error: wikiError } = useHMWiki(projectData?.hmWikiSlug, wikiPreviewSlug, activeTab === 'wiki' && projectData?.hmWikiEnabled === true);

    const [idCopied, setIdCopied] = useState(false);
    const [showCardPreview, setShowCardPreview] = useState(false);
    const [showPublishConfirm, setShowPublishConfirm] = useState(false);
    const [showSlugPrompt, setShowSlugPrompt] = useState(false);
    const [editingVersion, setEditingVersion] = useState<ProjectVersion | null>(null);
    const [editVersionData, setEditVersionData] = useState<VersionFormData | null>(null);
    const [isSavingVersion, setIsSavingVersion] = useState(false);
    const [memberToRemove, setMemberToRemove] = useState<string | null>(null);
    const [galleryCropImage, setGalleryCropImage] = useState<string | null>(null);
    const [statusModal, setStatusModal] = useState<any>(null);

    useEffect(() => {
        if (projectData) {
            setMetaData({
                title: projectData.title || '',
                slug: projectData.slug || '',
                summary: projectData.description || '',
                description: projectData.about || '',
                tags: projectData.tags || [],
                links: projectData.links || {},
                repositoryUrl: projectData.repositoryUrl || '',
                iconFile: null,
                iconPreview: projectData.imageUrl || null,
                license: projectData.license
            });
            if (projectData.bannerUrl) setBannerPreview(projectData.bannerUrl);
        }
    }, [projectData]);

    if (loading || !projectData) return <div className="min-h-screen flex items-center justify-center"><Spinner /></div>;

    const readOnly = projectData.status === 'PENDING' || projectData.status === 'ARCHIVED';
    const isModpack = projectData.classification === 'MODPACK';
    const hasProjectPermission = (perm: string) => true;

    const availableTabs = [
        {id: 'details', icon: FileText, label: 'Details'},
        {id: 'files', icon: UploadCloud, label: `Files (${projectData?.versions?.length||0})`},
        {id: 'gallery', icon: ImageIcon, label: `Gallery (${projectData?.galleryImages?.length||0})`},
        {id: 'team', icon: Users, label: `Team`},
        {id: 'settings', icon: Settings, label: 'Settings'}
    ];

    if (projectData?.hmWikiEnabled) {
        availableTabs.push({id: 'wiki', icon: BookOpen, label: 'Wiki Preview'});
    }

    const previewProject: Project = { ...projectData, title: metaData.title, description: metaData.summary };

    const isCustomLicense = metaData.license && !LICENSES.some(l => l.id === metaData.license);
    const hasTitle = metaData.title && metaData.title.trim().length > 0;
    const hasTags = metaData.tags.length > 0;
    const hasSummary = metaData.summary && metaData.summary.length >= 10 && metaData.summary.length <= 250;
    const hasValidDescription = !metaData.description || metaData.description.length <= 50000;
    const hasVersion = (projectData?.versions?.length || 0) > 0;
    const hasLicense = isModpack || (!!metaData.license && (!isCustomLicense || !!metaData.links.LICENSE));
    const hasValidSlug = !metaData.slug || !slugError;

    const publishRequirements = [
        { label: 'Project Title', met: !!hasTitle },
        { label: 'Short Summary (10-250 chars)', met: !!hasSummary },
        { label: 'At least one Tag', met: hasTags }
    ];

    if (!isModpack) {
        publishRequirements.push({ label: 'At least one Version uploaded', met: hasVersion });
    }

    publishRequirements.push({ label: 'License selected', met: hasLicense });
    publishRequirements.push({ label: 'All changes saved', met: !isDirty });

    if (!hasValidDescription) {
        publishRequirements.push({ label: 'Description under 50k chars', met: hasValidDescription });
    }
    if (metaData.repositoryUrl) {
        publishRequirements.push({ label: 'Valid Repository URL', met: repoValid });
    }
    if (metaData.slug) {
        publishRequirements.push({ label: 'Valid URL Slug', met: hasValidSlug });
    }

    const isPublishable = publishRequirements.every(r => r.met);
    const metCount = publishRequirements.filter(r => r.met).length;

    return (
        <div className="relative">
            {galleryCropImage && createPortal(
                <ImageCropperModal
                    imageSrc={galleryCropImage}
                    aspect={16 / 9}
                    onCancel={() => setGalleryCropImage(null)}
                    onCropComplete={(file) => {
                        setGalleryCropImage(null);
                        handleGalleryUpload(file);
                    }}
                />,
                document.body)}

            <ProjectLayout
                isEditing={true}
                bannerUrl={bannerPreview}
                iconUrl={metaData.iconPreview}
                onBack={() => navigate(-1)}
                onBannerUpload={(f, p) => { markDirty(); setBannerFile(f); setBannerPreview(p); }}
                onIconUpload={(f, p) => { markDirty(); setMetaData(m => ({...m, iconFile: f, iconPreview: p})); }}
                headerActions={
                    <div className="flex items-center gap-3">
                        <Link to={SiteRoutes.project(projectData)} target="_blank" className={`p-3 rounded-xl border ${theme.colors.border} ${theme.colors.bgSurface} ${theme.colors.textSecondary} hover:${theme.colors.textPrimary} transition-all shadow-sm`} title="View Project">
                            <ExternalLink className="w-5 h-5" />
                        </Link>

                        {projectData.status === 'DRAFT' && (
                            <div className="relative group">
                                <div className="absolute bottom-full right-0 mb-3 w-64 bg-white dark:bg-slate-900 rounded-xl shadow-2xl p-4 border border-slate-200 dark:border-white/10 opacity-0 group-hover:opacity-100 transition-all pointer-events-none translate-y-2 group-hover:translate-y-0 z-50">
                                    <div className="flex items-center justify-between mb-3 border-b border-slate-100 dark:border-white/5 pb-2">
                                        <span className="text-xs font-black uppercase text-slate-500 tracking-widest">Requirements</span>
                                        <span className={`text-xs font-bold ${isPublishable ? 'text-green-500' : 'text-slate-400'}`}>
                                            {metCount}/{publishRequirements.length}
                                        </span>
                                    </div>
                                    <div className="space-y-2">
                                        {publishRequirements.map((req, i) => (
                                            <div key={i} className="flex items-center gap-2.5">
                                                <div className={`w-4 h-4 rounded-full flex items-center justify-center flex-shrink-0 ${req.met ? 'bg-green-500 text-white' : 'bg-slate-200 dark:bg-white/10 text-slate-400'}`}>
                                                    {req.met ? <Check className="w-2.5 h-2.5" strokeWidth={3} /> : <X className="w-2.5 h-2.5" strokeWidth={3} />}
                                                </div>
                                                <span className={`text-xs font-bold ${req.met ? 'text-slate-900 dark:text-white' : 'text-slate-500'}`}>{req.label}</span>
                                            </div>
                                        ))}
                                    </div>
                                    <div className="absolute top-full right-8 -mt-[1px] border-[8px] border-transparent border-t-slate-200 dark:border-t-white/10" />
                                    <div className="absolute top-full right-8 -mt-[3px] border-[8px] border-transparent border-t-white dark:border-t-slate-900" />
                                </div>

                                <button onClick={handleSubmit} disabled={isSaving || !isPublishable} className={`h-10 px-6 rounded-xl font-black flex items-center justify-center gap-2 transition-all shadow-lg active:scale-95 ${isPublishable ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-900 hover:opacity-90' : 'bg-slate-200 dark:bg-slate-800 text-slate-400 shadow-none cursor-not-allowed'}`}>
                                    {isSaving ? <Spinner className="w-4 h-4 !p-0" fullScreen={false} /> : <Send className="w-4 h-4" />}
                                    Submit
                                </button>
                            </div>
                        )}

                        <button onClick={handleSave} disabled={!isDirty || isSaving} className={`px-6 h-10 rounded-xl font-bold flex items-center gap-2 transition-all shadow-lg ${isDirty ? 'bg-modtale-accent text-white hover:bg-modtale-accentHover shadow-modtale-accent/20' : `${theme.colors.bgSurface} ${theme.colors.textMuted} border ${theme.colors.border} cursor-not-allowed`}`}>
                            {isSaving ? <Spinner className="w-4 h-4 !p-0" fullScreen={false} /> : <Save className="w-4 h-4" />}
                            Save
                        </button>
                    </div>
                }
                headerContent={
                    <div>
                        <input value={metaData.title} disabled={readOnly} onChange={e => { markDirty(); setMetaData({...metaData, title: e.target.value}); }} className={`text-4xl md:text-5xl font-black ${theme.colors.textPrimary} bg-transparent border-b border-transparent outline-none w-full hover:border-slate-300 dark:hover:border-white/20 focus:border-modtale-accent pb-1`} placeholder="Project Title"/>
                        <input value={metaData.summary} disabled={readOnly} onChange={e => { markDirty(); setMetaData({...metaData, summary: e.target.value}); }} className={`text-lg ${theme.colors.textSecondary} font-medium bg-transparent border-b border-transparent outline-none w-full mt-2 hover:border-slate-300 dark:hover:border-white/20 focus:border-modtale-accent pb-1`} placeholder="Short summary..."/>
                    </div>
                }
                tabs={
                    <div className="flex items-center gap-1">
                        {availableTabs.map(t => (
                            <button key={t.id} type="button" onClick={() => setActiveTab(t.id)} className={`px-6 py-3 text-sm font-bold border-b-2 transition-colors flex items-center gap-2 ${activeTab === t.id ? `border-modtale-accent text-slate-900 dark:text-slate-300` : `border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-slate-300`}`}>
                                <t.icon className="w-4 h-4" />
                                {t.label}
                            </button>
                        ))}
                    </div>
                }
                sidebarContent={
                    <>
                        {activeTab === 'wiki' && wikiData && !wikiLoading && !wikiError && (
                            <WikiSidebar tree={wikiData.mod.pages || []} projectUrl="#" currentSlug={wikiPreviewSlug} indexSlug={wikiData.mod.index?.slug} onNavigate={setWikiPreviewSlug} />
                        )}
                        <SidebarSection title="Card Preview" icon={Eye}>
                            <div className={`w-full max-w-[340px] mx-auto relative group cursor-pointer overflow-hidden rounded-2xl border ${theme.colors.border}`} onClick={() => setShowCardPreview(true)}>
                                <div className="pointer-events-none select-none">
                                    <ProjectCard project={previewProject} isFavorite={false} onToggleFavorite={() => {}} isLoggedIn={false} />
                                </div>
                            </div>
                        </SidebarSection>
                    </>
                }
                mainContent={
                    <>
                        {activeTab === 'details' && (
                            <EditDetails metaData={metaData} setMetaData={setMetaData} readOnly={readOnly} hasProjectPermission={hasProjectPermission} editorMode={editorMode} setEditorMode={setEditorMode} markDirty={markDirty} />
                        )}
                        {activeTab === 'files' && (
                            <Files projectData={projectData} versionData={versionData} setVersionData={setVersionData} readOnly={readOnly} hasProjectPermission={hasProjectPermission} classification={projectData.classification || 'PLUGIN'} handleUploadVersion={() => {}} handleEditVersion={() => {}} isLoading={false} />
                        )}
                        {activeTab === 'gallery' && (
                            <Gallery
                                projectData={projectData}
                                readOnly={readOnly}
                                hasProjectPermission={hasProjectPermission}
                                handleGalleryDelete={handleGalleryDelete}
                                handleGallerySelect={(f) => setGalleryCropImage(URL.createObjectURL(f))}
                                isLoading={isSaving}
                            />
                        )}
                        {activeTab === 'team' && (
                            <Team
                                projectData={projectData}
                                currentUser={currentUser}
                                canInvite={true}
                                canManageRoles={true}
                                canRemove={true}
                                inviteUsername=""
                                inviteUserId=""
                                setInviteUsername={() => {}}
                                setInviteUserId={() => {}}
                                inviteRoleId=""
                                setInviteRoleId={() => {}}
                                userSearchResults={[]}
                                setUserSearchResults={() => {}}
                                inviteRoleDropdownOpen={false}
                                setInviteRoleDropdownOpen={() => {}}
                                memberRoleDropdownOpen={null}
                                setMemberRoleDropdownOpen={() => {}}
                                setMemberToRemove={() => {}}
                                handleInvite={() => {}}
                                handleRoleUpdate={handleRoleUpdate}
                                handleCancelInvite={handleCancelInvite}
                                setEditingRole={() => {}}
                                setRoleModalOpen={() => {}}
                                handleDeleteRole={() => {}}
                                isInviting={false}
                                contributors={contributors}
                            />
                        )}
                        {activeTab === 'settings' && (
                            <SettingsTab
                                projectData={projectData}
                                metaData={metaData}
                                setMetaData={setMetaData}
                                setProjectData={setProjectData}
                                readOnly={readOnly}
                                hasProjectPermission={hasProjectPermission}
                                slugError={slugError}
                                handleSlugChange={() => {}}
                                getUrlPrefix={() => `https://modtale.net/${SiteRoutes.getProjectPrefix(projectData.classification)}/`}
                                markDirty={markDirty}
                                isLoading={false}
                            />
                        )}
                        {activeTab === 'wiki' && (
                            <WikiPreview wikiLoading={wikiLoading} wikiError={!!wikiError} wikiData={wikiData} wikiPreviewSlug={wikiPreviewSlug} projectData={projectData} />
                        )}
                    </>
                }
            />
        </div>
    );
};