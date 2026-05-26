import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation, Link } from 'react-router-dom';
import { createPortal } from 'react-dom';
import { Save, UploadCloud, Eye, Image as ImageIcon, Users, BookOpen, Settings, FileText, ExternalLink, Send, Check, X, Tag, Scale, Link as LinkIcon, Edit2, Edit3, XCircle } from 'lucide-react';

import type { User, Project, ProjectVersion } from '@/types';
import { theme } from '@/styles/theme';
import { SiteRoutes } from '@/utils/routes';
import { GLOBAL_TAGS, LICENSES } from '@/data/categories';
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
import { projectClient } from '../api/projectClient';
import { api } from '@/utils/api';

const MAX_UPLOAD_BYTES = 100 * 1024 * 1024;
const MAX_UPLOAD_ERROR_MESSAGE = 'File exceeds 100MB limit. Cloudflare only supports uploads up to 100MB.';
const isFileOverUploadLimit = (file: File) => file.size > MAX_UPLOAD_BYTES;

import { Spinner } from '@/components/ui/Spinner';
import { ImageCropperModal } from '@/components/ui/ImageCropperModal';
import { StatusModal } from '@/components/ui/StatusModal';
import { ProjectCard } from '@/modules/project/components/ProjectCard';
import { ThemedInput } from '../components/FormShared';
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
    } = useProjectEditor(
        projectData,
        currentUser,
        metaData,
        bannerFile,
        setMetaData,
        setBannerFile,
        setBannerPreview,
        setProjectData,
        onShowStatus
    );

    const [wikiPreviewSlug, setWikiPreviewSlug] = useState<string | undefined>();
    const { data: wikiData, loading: wikiLoading, error: wikiError } = useHMWiki(projectData?.hmWikiSlug, wikiPreviewSlug, activeTab === 'wiki' && projectData?.hmWikiEnabled === true);

    const [idCopied, setIdCopied] = useState(false);
    const [showCardPreview, setShowCardPreview] = useState(false);
    const [showPublishConfirm, setShowPublishConfirm] = useState(false);
    const [showSlugPrompt, setShowSlugPrompt] = useState(false);
    const [editingVersion, setEditingVersion] = useState<ProjectVersion | null>(null);
    const [editVersionData, setEditVersionData] = useState<VersionFormData | null>(null);
    const [isSavingVersion, setIsSavingVersion] = useState(false);
    const [isEditingTitle, setIsEditingTitle] = useState(false);
    const [memberToRemove, setMemberToRemove] = useState<string | null>(null);
    const [galleryCropImage, setGalleryCropImage] = useState<string | null>(null);
    const [statusModal, setStatusModal] = useState<any>(null);
    const [isStatusChanging, setIsStatusChanging] = useState(false);

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

    useEffect(() => {
        const handleBeforeUnload = (event: BeforeUnloadEvent) => {
            if (!isDirty) return;
            event.preventDefault();
            event.returnValue = '';
        };

        window.addEventListener('beforeunload', handleBeforeUnload);
        return () => window.removeEventListener('beforeunload', handleBeforeUnload);
    }, [isDirty]);

    if (loading || !projectData) return <div className="min-h-screen flex items-center justify-center"><Spinner /></div>;

    const readOnly = projectData.status === 'PENDING' || projectData.status === 'ARCHIVED';
    const isModpack = projectData.classification === 'MODPACK';
    const hasProjectPermission = (perm: string) => true;
    const toggleTag = (tag: string) => {
        if (readOnly) return;
        markDirty();
        setMetaData(prev => ({
            ...prev,
            tags: prev.tags.includes(tag)
                ? prev.tags.filter(t => t !== tag)
                : [...prev.tags, tag]
        }));
    };

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

    const previewTitle = metaData.title.trim() || projectData.title || '';
    const previewSummary = metaData.summary.trim() || projectData.description || '';
    const previewProject: Project = { ...projectData, title: previewTitle, description: previewSummary };

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

    const handleUploadVersion = async () => {
        if (!projectData?.id) return;
        if (!isModpack && !versionData.file) {
            onShowStatus('error', 'Upload Failed', 'A project file is required.');
            return;
        }
        if (!versionData.versionNumber || versionData.gameVersions.length === 0) {
            onShowStatus('error', 'Upload Failed', 'Version number and game versions are required.');
            return;
        }
        if (versionData.file && isFileOverUploadLimit(versionData.file)) {
            onShowStatus('error', 'Upload Failed', MAX_UPLOAD_ERROR_MESSAGE);
            return;
        }

        setIsSavingVersion(true);
        try {
            const formData = new FormData();
            formData.append('versionNumber', versionData.versionNumber);
            versionData.gameVersions.forEach(version => formData.append('gameVersions', version));
            if (versionData.file) formData.append('file', versionData.file);
            (versionData.projectIds || []).forEach(dep => formData.append('modIds', dep));
            if (versionData.changelog) formData.append('changelog', versionData.changelog);
            formData.append('channel', versionData.channel || 'RELEASE');

            await api.post(`/projects/${projectData.id}/versions`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });

            const refreshed = await projectClient.getProject(projectData.id);
            setProjectData(refreshed);
            setVersionData({
                projectIds: [],
                versionNumber: '',
                gameVersions: versionData.gameVersions,
                changelog: '',
                file: null,
                dependencies: [],
                modIds: [],
                channel: 'RELEASE'
            });
            onShowStatus('success', 'Uploaded', 'Version uploaded successfully.');
        } catch (e: any) {
            onShowStatus('error', 'Upload Failed', e.response?.data || 'Failed to upload version.');
        } finally {
            setIsSavingVersion(false);
        }
    };

    const runStatusTransition = async (nextStatus: 'PUBLISHED' | 'UNLISTED' | 'ARCHIVED') => {
        if (!projectData?.id || isStatusChanging) return;
        setIsStatusChanging(true);

        try {
            const endpoint = nextStatus === 'PUBLISHED' ? 'publish' : nextStatus === 'UNLISTED' ? 'unlist' : 'archive';
            await api.post(`/projects/${projectData.id}/${endpoint}`);
            setProjectData(prev => prev ? { ...prev, status: nextStatus } : null);
            onShowStatus('success', 'Status Updated', `Project status changed to ${nextStatus.toLowerCase()}.`);
        } catch (e: any) {
            onShowStatus('error', 'Status Update Failed', e.response?.data || 'Failed to update project status.');
        } finally {
            setIsStatusChanging(false);
            setStatusModal(null);
        }
    };

    const confirmStatusTransition = (nextStatus: 'PUBLISHED' | 'UNLISTED' | 'ARCHIVED') => {
        const config = {
            PUBLISHED: {
                type: 'info' as const,
                title: 'Publish Project?',
                message: 'This will make your project visible to everyone in discovery.',
                actionLabel: 'Publish'
            },
            UNLISTED: {
                type: 'info' as const,
                title: 'Unlist Project?',
                message: 'This will hide your project from search and browse, but direct links will still work.',
                actionLabel: 'Unlist'
            },
            ARCHIVED: {
                type: 'warning' as const,
                title: 'Archive Project?',
                message: 'This will move the project into read-only mode for everyone.',
                actionLabel: 'Archive'
            }
        }[nextStatus];

        setStatusModal({
            ...config,
            secondaryLabel: 'Cancel',
            onAction: () => runStatusTransition(nextStatus)
        });
    };

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
            {statusModal && createPortal(
                <StatusModal
                    type={statusModal.type}
                    title={statusModal.title}
                    message={statusModal.message}
                    actionLabel={statusModal.actionLabel}
                    secondaryLabel={statusModal.secondaryLabel}
                    onAction={statusModal.onAction}
                    onClose={() => !isStatusChanging && setStatusModal(null)}
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

                        {isDirty && (
                            <div className="flex items-center px-2 h-8 rounded border border-amber-300/60 dark:border-amber-400/30 bg-amber-50/80 dark:bg-amber-500/10 text-amber-700 dark:text-amber-300 animate-pulse">
                                <span className="text-[9px] font-semibold tracking-wide">Not Saved</span>
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
                        {isEditingTitle && !readOnly ? (
                            <div className="relative w-full max-w-full">
                                <input
                                    value={metaData.title}
                                    onChange={e => { markDirty(); setMetaData({...metaData, title: e.target.value}); }}
                                    className={`text-4xl md:text-5xl font-black ${theme.colors.textPrimary} bg-transparent border-b border-slate-300 dark:border-white/20 outline-none w-full focus:border-modtale-accent pb-1 pr-10`}
                                    placeholder="Project Title"
                                    autoFocus
                                />
                                <button
                                    onClick={() => { setMetaData({...metaData, title: projectData.title || ''}); setIsEditingTitle(false); }}
                                    className="absolute right-0 top-1/2 -translate-y-1/2 text-slate-400 hover:text-red-500 transition-colors"
                                    aria-label="Cancel title editing"
                                >
                                    <XCircle className="w-5 h-5" />
                                </button>
                            </div>
                        ) : (
                            <div
                                className={`flex items-center gap-3 group rounded-2xl -ml-3 px-3 py-1.5 ${readOnly ? '' : 'cursor-pointer hover:bg-black/5 dark:hover:bg-white/5'} transition-colors`}
                                onClick={() => { if (!readOnly) setIsEditingTitle(true); }}
                            >
                                <h1 className={`text-4xl md:text-5xl font-black ${theme.colors.textPrimary} tracking-tighter break-words`}>{metaData.title || 'Project Title'}</h1>
                                {!readOnly && <Edit3 className="w-5 h-5 text-slate-400 opacity-0 group-hover:opacity-100 transition-opacity" />}
                            </div>
                        )}
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
                        {!isModpack && (
                            <SidebarSection title="License" icon={Scale} defaultOpen={false}>
                                <div className={`bg-slate-50 dark:bg-slate-950/50 border ${theme.colors.border} rounded-xl p-2 max-h-80 overflow-y-auto custom-scrollbar`}>
                                    {LICENSES.map(lic => (
                                        <button
                                            key={lic.id}
                                            disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')}
                                            onClick={() => { markDirty(); setMetaData({ ...metaData, license: lic.id }); }}
                                            className={`w-full text-left px-3 py-2 rounded-lg text-xs font-bold flex items-center justify-between ${metaData.license === lic.id ? 'bg-modtale-accent text-white' : `${theme.colors.textSecondary} hover:bg-slate-200 dark:hover:bg-white/10`}`}
                                        >
                                            <span>{lic.name}</span>
                                            {metaData.license === lic.id && <Check className="w-3 h-3" />}
                                        </button>
                                    ))}
                                    <button
                                        disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')}
                                        onClick={() => { markDirty(); if (!metaData.license || LICENSES.some(l => l.id === metaData.license)) setMetaData({ ...metaData, license: '' }); }}
                                        className={`w-full text-left px-3 py-2 rounded-lg text-xs font-bold flex items-center justify-between border-t ${theme.colors.border} mt-1 pt-2 ${isCustomLicense ? 'bg-modtale-accent text-white' : `${theme.colors.textSecondary} hover:bg-slate-200 dark:hover:bg-white/10`}`}
                                    >
                                        <span>Custom License</span>
                                        {isCustomLicense ? <Check className="w-3 h-3" /> : <Edit2 className="w-3 h-3" />}
                                    </button>
                                    {isCustomLicense && (
                                        <div className="mt-2 p-2 bg-slate-100 dark:bg-black/20 rounded-lg space-y-2 animate-in slide-in-from-top-2">
                                            <input
                                                value={metaData.license || ''}
                                                onChange={(e) => { markDirty(); setMetaData({ ...metaData, license: e.target.value }); }}
                                                placeholder="License Name"
                                                disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')}
                                                className={`w-full ${theme.colors.bgSurfaceAlt} border ${theme.colors.border} rounded-lg px-3 py-2 text-xs`}
                                            />
                                            <input
                                                value={metaData.links.LICENSE || ''}
                                                onChange={(e) => { markDirty(); setMetaData({ ...metaData, links: { ...metaData.links, LICENSE: e.target.value } }); }}
                                                placeholder="License URL"
                                                disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')}
                                                className={`w-full ${theme.colors.bgSurfaceAlt} border rounded-lg px-3 py-2 text-xs font-mono transition-colors ${!metaData.links.LICENSE ? 'border-red-500 focus:border-red-500' : theme.colors.border}`}
                                            />
                                            {!metaData.links.LICENSE && (
                                                <p className="text-[10px] text-red-500 font-bold px-1">URL is required for custom licenses.</p>
                                            )}
                                        </div>
                                    )}
                                </div>
                            </SidebarSection>
                        )}
                        <SidebarSection title="Tags" icon={Tag} defaultOpen={false}>
                            <div className="flex flex-wrap gap-2">
                                {GLOBAL_TAGS.map(tag => (
                                    <button
                                        disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')}
                                        key={tag}
                                        onClick={() => toggleTag(tag)}
                                        className={`px-2.5 py-1 rounded-lg text-[10px] font-bold border transition-all ${metaData.tags.includes(tag) ? 'bg-modtale-accent text-white border-modtale-accent' : 'bg-slate-100 dark:bg-slate-900/50 text-slate-500 dark:text-slate-400 border-slate-200 dark:border-white/10'}`}
                                    >
                                        {tag}
                                    </button>
                                ))}
                            </div>
                        </SidebarSection>
                        <SidebarSection title="External Links" icon={LinkIcon} defaultOpen={false}>
                            <div className="space-y-3">
                                {['WEBSITE', 'WIKI', 'ISSUE_TRACKER', 'DISCORD'].map(k => (
                                    <ThemedInput
                                        key={k}
                                        disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')}
                                        label={k.replace('_', ' ')}
                                        value={metaData.links[k] || ''}
                                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                                            markDirty();
                                            setMetaData(prev => ({ ...prev, links: { ...prev.links, [k]: e.target.value } }));
                                        }}
                                        placeholder="https://..."
                                    />
                                ))}
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
                            <Files projectData={projectData} versionData={versionData} setVersionData={setVersionData} readOnly={readOnly} hasProjectPermission={hasProjectPermission} classification={projectData.classification || 'PLUGIN'} handleUploadVersion={handleUploadVersion} handleEditVersion={() => {}} isLoading={isSavingVersion} />
                        )}
                        {activeTab === 'gallery' && (
                <Gallery
                                projectData={projectData}
                                readOnly={readOnly}
                                hasProjectPermission={hasProjectPermission}
                                handleGalleryDelete={handleGalleryDelete}
                    handleGallerySelect={(f) => {
                        if (isFileOverUploadLimit(f)) {
                            onShowStatus('error', 'Upload Failed', MAX_UPLOAD_ERROR_MESSAGE);
                            return;
                        }
                        setGalleryCropImage(URL.createObjectURL(f));
                    }}
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
                                handleRestore={() => confirmStatusTransition('PUBLISHED')}
                                handleUnlist={() => confirmStatusTransition('UNLISTED')}
                                handleArchive={() => confirmStatusTransition('ARCHIVED')}
                                slugError={slugError}
                                handleSlugChange={() => {}}
                                getUrlPrefix={() => `https://modtale.net/${SiteRoutes.getProjectPrefix(projectData.classification)}/`}
                                markDirty={markDirty}
                                isLoading={isStatusChanging}
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
