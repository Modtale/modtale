import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { createPortal } from 'react-dom';
import { UploadCloud, Eye, Image as ImageIcon, Users, BookOpen, Settings, FileText } from 'lucide-react';

import type { User, Project, ProjectVersion } from '@/types';
import { theme } from '@/styles/theme';
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

    const { repos, loadingRepos, manualRepo, setManualRepo, repoValid, isDirty, setIsDirty, slugError, setSlugError, userSearchResults, setUserSearchResults, provider, setProvider, markDirty, checkRepoUrl, fetchRepos, handleRoleUpdate, handleCancelInvite } = useProjectEditor(projectData, currentUser, metaData, setMetaData, setProjectData, onShowStatus);

    const [wikiPreviewSlug, setWikiPreviewSlug] = useState<string | undefined>();
    const { data: wikiData, loading: wikiLoading, error: wikiError } = useHMWiki(projectData?.hmWikiSlug, wikiPreviewSlug, activeTab === 'wiki' && projectData?.hmWikiEnabled === true);

    const [idCopied, setIdCopied] = useState(false);
    const [isCustomLicense, setIsCustomLicense] = useState(false);
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

    return (
        <div className="relative">
            {galleryCropImage && createPortal(<ImageCropperModal imageSrc={galleryCropImage} aspect={16 / 9} onCancel={() => setGalleryCropImage(null)} onCropComplete={() => {}} />, document.body)}

            <ProjectLayout
                isEditing={true}
                bannerUrl={bannerPreview}
                iconUrl={metaData.iconPreview}
                onBack={() => navigate(-1)}
                onBannerUpload={(f, p) => { markDirty(); setBannerFile(f); setBannerPreview(p); }}
                onIconUpload={(f, p) => { markDirty(); setMetaData(m => ({...m, iconFile: f, iconPreview: p})); }}
                headerContent={
                    <div>
                        <input value={metaData.title} disabled={readOnly} onChange={e => { markDirty(); setMetaData({...metaData, title: e.target.value}); }} className={`text-4xl md:text-5xl font-black ${theme.colors.textPrimary} bg-transparent border-b border-transparent outline-none w-full`} placeholder="Project Title"/>
                        <input value={metaData.summary} disabled={readOnly} onChange={e => { markDirty(); setMetaData({...metaData, summary: e.target.value}); }} className={`text-lg ${theme.colors.textSecondary} font-medium bg-transparent border-b border-transparent outline-none w-full mt-2`} placeholder="Short summary..."/>
                    </div>
                }
                tabs={
                    <div className="flex items-center gap-1">
                        {availableTabs.map(t => (
                            <button key={t.id} type="button" onClick={() => setActiveTab(t.id)} className={`px-6 py-3 text-sm font-bold border-b-2 transition-colors flex items-center gap-2 ${activeTab === t.id ? `border-modtale-accent ${theme.colors.textPrimary}` : `border-transparent ${theme.colors.textMuted}`}`}>{t.label}</button>
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
                            <Gallery projectData={projectData} readOnly={readOnly} hasProjectPermission={hasProjectPermission} handleGalleryDelete={async () => {}} getGalleryRootProps={() => ({})} getGalleryInputProps={() => ({})} isGalleryDragActive={false} isLoading={false} />
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
                            <SettingsTab projectData={projectData} metaData={metaData} setMetaData={setMetaData} setProjectData={setProjectData} readOnly={readOnly} hasProjectPermission={hasProjectPermission} slugError={slugError} handleSlugChange={() => {}} getUrlPrefix={() => ''} markDirty={markDirty} isLoading={false} />
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