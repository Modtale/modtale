import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useLocation, Link } from 'react-router-dom';
import { createPortal } from 'react-dom';
import { Save, UploadCloud, Eye, Image as ImageIcon, Users, BookOpen, Settings, FileText, ExternalLink, Send, Check, X, Tag, Scale, Link as LinkIcon, Edit2, Edit3, XCircle, Undo2, AlertTriangle, Info } from 'lucide-react';

import type { ProjectDependency, User, Project, ProjectVersion } from '@/types';
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
import { api, extractApiErrorMessage } from '@/utils/api';
import { Spinner } from '@/components/ui/Spinner';
import { ImageCropperModal } from '@/components/ui/ImageCropperModal';
import { StatusModal } from '@/components/ui/StatusModal';
import { PermissionSelector } from '@/components/ui/PermissionSelector';
import { ProjectCard } from '@/modules/project/components/ProjectCard';
import { ThemedInput } from '../components/FormShared';
import type { MetadataFormData, VersionFormData } from '../components/FormShared';
import type { ProjectRole } from '@/types';
import { Permission, PROJECT_PERMISSION_GROUPS } from '@/modules/permissions/permissions';
import { VersionFields } from '../components/VersionFields';
import { worldListClient } from '@/modules/worldlist/api/worldListClient';
import { skippedWorldListItems, worldListToProjectDependencies } from '@/modules/worldlist/utils/modpackSeed';

const MAX_UPLOAD_BYTES = 100 * 1024 * 1024;
const MAX_UPLOAD_ERROR_MESSAGE = 'File exceeds 100MB limit. Cloudflare only supports uploads up to 100MB.';

const appendDependenciesToFormData = (formData: FormData, dependencies: ProjectDependency[] = []) => {
    dependencies.forEach((dependency, index) => {
        if (dependency.id) formData.append(`dependencies[${index}].id`, dependency.id);
        formData.append(`dependencies[${index}].projectId`, dependency.projectId);
        formData.append(`dependencies[${index}].projectTitle`, dependency.projectTitle || '');
        formData.append(`dependencies[${index}].versionNumber`, dependency.versionNumber);
        formData.append(`dependencies[${index}].dependencyType`, dependency.dependencyType || 'REQUIRED');
        formData.append(`dependencies[${index}].source`, dependency.source || 'MODTALE');
        if (dependency.externalId) formData.append(`dependencies[${index}].externalId`, dependency.externalId);
        if (dependency.externalUrl) formData.append(`dependencies[${index}].externalUrl`, dependency.externalUrl);
        if (dependency.externalFileUrl) formData.append(`dependencies[${index}].externalFileUrl`, dependency.externalFileUrl);
        if (dependency.externalFileName) formData.append(`dependencies[${index}].externalFileName`, dependency.externalFileName);
        if (dependency.hytaleProjectConfirmed) formData.append(`dependencies[${index}].hytaleProjectConfirmed`, 'true');
    });
};
const isFileOverUploadLimit = (file: File) => file.size > MAX_UPLOAD_BYTES;
const CARD_PREVIEW_BASE_WIDTH = 340;
const CARD_PREVIEW_FALLBACK_HEIGHT = 390;
const CARD_PREVIEW_MAX_SCALE = 2.15;
const CARD_PREVIEW_VIEWPORT_PADDING = 48;
const CARD_PREVIEW_VERTICAL_RESERVE = 128;

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

    const { project: projectData, setProject: setProjectData, loading, contributors } = useProjectDetail(id, null, currentUser, { hydrateChangelogs: true, full: true });

    const [metaData, setMetaData] = useState<MetadataFormData>({
        title: '', summary: '', description: '', tags: [], links: {}, repositoryUrl: '', iconFile: null, iconPreview: null, slug: '', customLicenseOpenSource: false
    });
    const [versionData, setVersionData] = useState<VersionFormData>({
        dependencies: [], incompatibleProjectIds: [], versionNumber: '', gameVersions: [], changelog: '', file: null, channel: 'RELEASE', replaceExisting: false
    });

    const [bannerFile, setBannerFile] = useState<File | null>(null);
    const [bannerPreview, setBannerPreview] = useState<string | null>(null);

    const {
        repos, loadingRepos, manualRepo, setManualRepo, repoValid, isDirty, setIsDirty,
        slugError, setSlugError, userSearchResults, setUserSearchResults, provider,
        setProvider, markDirty, checkRepoUrl, fetchRepos, handleRoleUpdate, handleCancelInvite,
        handleSave, handleSubmit, isSaving, handleGalleryUpload, handleGalleryVideoAdd, handleGalleryCaptionChange, handleGalleryDelete
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
    const { data: wikiData, loading: wikiLoading, error: wikiError } = useHMWiki(projectData?.id, wikiPreviewSlug, activeTab === 'wiki' && projectData?.hmWikiEnabled === true);

    const [idCopied, setIdCopied] = useState(false);
    const [showCardPreview, setShowCardPreview] = useState(false);
    const cardPreviewRef = useRef<HTMLDivElement | null>(null);
    const [cardPreviewSize, setCardPreviewSize] = useState({
        width: CARD_PREVIEW_BASE_WIDTH,
        height: CARD_PREVIEW_FALLBACK_HEIGHT
    });
    const [cardPreviewScale, setCardPreviewScale] = useState(1);
    const [showPublishConfirm, setShowPublishConfirm] = useState(false);
    const [showSlugPrompt, setShowSlugPrompt] = useState(false);
    const [editingVersion, setEditingVersion] = useState<ProjectVersion | null>(null);
    const [editVersionData, setEditVersionData] = useState<VersionFormData | null>(null);
    const [isSavingVersion, setIsSavingVersion] = useState(false);
    const [isEditingTitle, setIsEditingTitle] = useState(false);
    const [memberToRemove, setMemberToRemove] = useState<string | null>(null);
    const [inviteUsername, setInviteUsername] = useState('');
    const [inviteUserId, setInviteUserId] = useState('');
    const [inviteRoleId, setInviteRoleId] = useState('');
    const [inviteRoleDropdownOpen, setInviteRoleDropdownOpen] = useState(false);
    const [memberRoleDropdownOpen, setMemberRoleDropdownOpen] = useState<string | null>(null);
    const [isInviting, setIsInviting] = useState(false);
    const [editingRole, setEditingRole] = useState<Partial<ProjectRole> | null>(null);
    const [roleModalOpen, setRoleModalOpen] = useState(false);
    const [galleryCropImage, setGalleryCropImage] = useState<string | null>(null);
    const [galleryCropFile, setGalleryCropFile] = useState<File | null>(null);
    const [statusModal, setStatusModal] = useState<any>(null);
    const [isStatusChanging, setIsStatusChanging] = useState(false);
    const seededListRef = useRef('');

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
                license: projectData.license,
                customLicenseOpenSource: projectData.customLicenseOpenSource || false
            });
            if (projectData.bannerUrl) setBannerPreview(projectData.bannerUrl);
        }
    }, [projectData]);

    useEffect(() => {
        if (!projectData || projectData.classification !== 'MODPACK') {
            return;
        }

        const seedListId = new URLSearchParams(location.search).get('seedList') || '';
        if (!seedListId || seededListRef.current === seedListId) {
            return;
        }

        seededListRef.current = seedListId;
        setActiveTab('files');

        worldListClient.get(seedListId)
            .then(list => {
                const seededDependencies = worldListToProjectDependencies(list);
                const skippedCount = skippedWorldListItems(list).length;
                setVersionData(current => {
                    const existingKeys = new Set((current.dependencies || []).map(dep => `${dep.source || 'MODTALE'}:${dep.projectId}`));
                    const mergedDependencies = [
                        ...(current.dependencies || []),
                        ...seededDependencies.filter(dep => !existingKeys.has(`${dep.source || 'MODTALE'}:${dep.projectId}`))
                    ];
                    return {
                        ...current,
                        dependencies: mergedDependencies,
                        versionNumber: current.versionNumber || '1.0.0',
                        gameVersions: current.gameVersions?.length
                            ? current.gameVersions
                            : list.gameVersion
                                ? [list.gameVersion]
                                : current.gameVersions
                    };
                });
                onShowStatus(
                    skippedCount > 0 ? 'warning' : 'success',
                    skippedCount > 0 ? 'Modpack Seeded' : 'Modpack Ready',
                    skippedCount > 0
                        ? `Added ${seededDependencies.length} mod${seededDependencies.length === 1 ? '' : 's'} from the shared list. ${skippedCount} local-only item${skippedCount === 1 ? '' : 's'} could not be added automatically.`
                        : `Added ${seededDependencies.length} mod${seededDependencies.length === 1 ? '' : 's'} from the shared list.`
                );
            })
            .catch(error => {
                onShowStatus('error', 'List Unavailable', extractApiErrorMessage(error, 'We could not load that shared mod list.'));
            });
    }, [location.search, onShowStatus, projectData]);

    useEffect(() => {
        const handleBeforeUnload = (event: BeforeUnloadEvent) => {
            if (!isDirty) return;
            event.preventDefault();
            event.returnValue = '';
        };

        window.addEventListener('beforeunload', handleBeforeUnload);
        return () => window.removeEventListener('beforeunload', handleBeforeUnload);
    }, [isDirty]);

    useEffect(() => {
        if (!inviteUsername || inviteUsername.length < 2 || inviteUserId) {
            setUserSearchResults([]);
            return;
        }

        const delayDebounceFn = window.setTimeout(async () => {
            try {
                const res = await projectClient.searchUsers(inviteUsername);
                setUserSearchResults(res);
            } catch {
            }
        }, 300);

        return () => window.clearTimeout(delayDebounceFn);
    }, [inviteUsername, inviteUserId, setUserSearchResults]);

    useEffect(() => {
        if (!showCardPreview) return;

        const originalOverflow = document.body.style.overflow;

        const updateCardPreviewScale = () => {
            const card = cardPreviewRef.current;
            const width = card?.offsetWidth || CARD_PREVIEW_BASE_WIDTH;
            const height = card?.offsetHeight || CARD_PREVIEW_FALLBACK_HEIGHT;
            const availableWidth = Math.max(240, window.innerWidth - CARD_PREVIEW_VIEWPORT_PADDING);
            const availableHeight = Math.max(240, window.innerHeight - CARD_PREVIEW_VERTICAL_RESERVE);
            const nextScale = Math.min(CARD_PREVIEW_MAX_SCALE, availableWidth / width, availableHeight / height);
            const boundedScale = Number.isFinite(nextScale) && nextScale > 0 ? Math.max(0.5, nextScale) : 1;

            setCardPreviewSize((current) => (
                current.width === width && current.height === height ? current : { width, height }
            ));
            setCardPreviewScale((current) => (
                Math.abs(current - boundedScale) < 0.01 ? current : boundedScale
            ));
        };
        const handleCardPreviewKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                setShowCardPreview(false);
            }
        };

        document.body.style.overflow = 'hidden';
        const animationFrame = window.requestAnimationFrame(updateCardPreviewScale);
        window.addEventListener('resize', updateCardPreviewScale);
        window.addEventListener('keydown', handleCardPreviewKeyDown);

        let resizeObserver: ResizeObserver | null = null;
        if ('ResizeObserver' in window && cardPreviewRef.current) {
            resizeObserver = new ResizeObserver(updateCardPreviewScale);
            resizeObserver.observe(cardPreviewRef.current);
        }

        return () => {
            document.body.style.overflow = originalOverflow;
            window.cancelAnimationFrame(animationFrame);
            window.removeEventListener('resize', updateCardPreviewScale);
            window.removeEventListener('keydown', handleCardPreviewKeyDown);
            resizeObserver?.disconnect();
        };
    }, [showCardPreview]);

    if (loading || !projectData) return <div className="min-h-screen flex items-center justify-center"><Spinner /></div>;

    const readOnly = projectData.status === 'PENDING' || projectData.status === 'ARCHIVED';
    const isModpack = projectData.classification === 'MODPACK';
    const hasProjectPermission = (_perm: Permission) => true;
    const canInvite = !readOnly && hasProjectPermission(Permission.PROJECT_TEAM_INVITE);
    const canManageRoles = !readOnly && hasProjectPermission(Permission.PROJECT_MEMBER_EDIT_ROLE);
    const canRemove = !readOnly && hasProjectPermission(Permission.PROJECT_TEAM_REMOVE);
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
    const handleSlugChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (readOnly) return;
        markDirty();
        setSlugError(null);
        setMetaData(prev => ({
            ...prev,
            slug: e.target.value
        }));
    };
    const handleSubmitClick = () => {
        if (!metaData.slug?.trim()) {
            setShowSlugPrompt(true);
            return;
        }
        handleSubmit();
    };
    const handleSetCustomSlug = () => {
        setShowSlugPrompt(false);
        setActiveTab('settings');
        window.setTimeout(() => {
            const slugInput = document.getElementById('project-custom-slug-input') as HTMLInputElement | null;
            slugInput?.focus();
        }, 0);
    };
    const handleInvite = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!projectData.id || !inviteUserId || !inviteRoleId) return;

        setIsInviting(true);
        try {
            await projectClient.inviteUser(projectData.id, inviteUserId, inviteRoleId);
            const refreshed = await projectClient.getProjectFull(projectData.id);
            setProjectData(refreshed);
            setInviteUsername('');
            setInviteUserId('');
            setInviteRoleId('');
            setInviteRoleDropdownOpen(false);
            setUserSearchResults([]);
            onShowStatus('success', 'Invited', 'Contributor invitation sent successfully.');
        } catch (err: unknown) {
            onShowStatus('error', 'Invitation Failed', extractApiErrorMessage(err, 'We could not send that project invite.'));
        } finally {
            setIsInviting(false);
        }
    };

    const confirmRemoveMember = async () => {
        if (!projectData.id || !memberToRemove) return;

        try {
            await projectClient.removeContributor(projectData.id, memberToRemove);
            const refreshed = await projectClient.getProjectFull(projectData.id);
            setProjectData(refreshed);
            onShowStatus('success', memberToRemove === currentUser?.id ? 'Left Project' : 'Removed', memberToRemove === currentUser?.id ? 'You have left the project.' : 'Contributor removed successfully.');
        } catch (err: unknown) {
            onShowStatus('error', 'Removal Failed', extractApiErrorMessage(err, 'We could not remove that contributor.'));
        } finally {
            setMemberToRemove(null);
            setStatusModal(null);
        }
    };

    const handleSaveRole = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!projectData.id || !editingRole?.name || !editingRole?.color) return;

        try {
            const updatedProject = await projectClient.saveProjectRole(projectData.id, editingRole);
            setProjectData(updatedProject);
            setRoleModalOpen(false);
            setEditingRole(null);
            onShowStatus('success', 'Saved', 'Project role saved successfully.');
        } catch (err: unknown) {
            onShowStatus('error', 'Role Save Failed', extractApiErrorMessage(err, 'We could not save that project role.'));
        }
    };

    const runDeleteRole = async (roleId: string) => {
        if (!projectData.id) return;

        try {
            const updatedProject = await projectClient.deleteProjectRole(projectData.id, roleId);
            setProjectData(updatedProject);
            onShowStatus('success', 'Deleted', 'Project role deleted successfully.');
        } catch (err: unknown) {
            onShowStatus('error', 'Role Delete Failed', extractApiErrorMessage(err, 'We could not delete that project role.'));
        } finally {
            setStatusModal(null);
        }
    };

    const handleDeleteRole = (roleId: string) => {
        const role = projectData.projectRoles?.find(r => r.id === roleId);
        setStatusModal({
            type: 'warning',
            title: 'Delete Role?',
            message: `Are you sure you want to delete "${role?.name || 'this role'}"? Contributors currently assigned to it will need to be reassigned before this action can succeed.`,
            actionLabel: 'Delete Role',
            secondaryLabel: 'Cancel',
            onClose: () => setStatusModal(null),
            onAction: () => runDeleteRole(roleId)
        });
    };

    const handleRequestRemoveMember = (userId: string) => {
        const target = contributors.find(contributor => contributor.id === userId);
        const targetLabel = userId === currentUser?.id ? 'leave this project' : `remove "${target?.username || 'this contributor'}"`;
        setMemberToRemove(userId);
        setStatusModal({
            type: 'warning',
            title: userId === currentUser?.id ? 'Leave Project?' : 'Remove Contributor?',
            message: `Are you sure you want to ${targetLabel}?`,
            actionLabel: userId === currentUser?.id ? 'Leave Project' : 'Remove',
            secondaryLabel: 'Cancel',
            onClose: () => {
                setMemberToRemove(null);
                setStatusModal(null);
            },
            onAction: confirmRemoveMember
        });
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

    const isCustomLicense = typeof metaData.license === 'string' && !LICENSES.some(l => l.id === metaData.license);
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

    const handleSelectStandardLicense = (licenseId: string) => {
        markDirty();
        const linksWithoutCustomLicense = { ...metaData.links };
        delete linksWithoutCustomLicense.LICENSE;
        setMetaData({ ...metaData, license: licenseId, links: linksWithoutCustomLicense, customLicenseOpenSource: false });
    };

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
            appendDependenciesToFormData(formData, versionData.dependencies || []);
            (versionData.incompatibleProjectIds || []).forEach(projectId => formData.append('incompatibleProjectIds', projectId));
            if (versionData.changelog) formData.append('changelog', versionData.changelog);
            formData.append('channel', versionData.channel || 'RELEASE');
            formData.append('replaceExisting', versionData.replaceExisting ? 'true' : 'false');

            await api.post(`/projects/${projectData.id}/versions`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });

            const refreshed = await projectClient.getProjectFull(projectData.id);
            setProjectData(refreshed);
            setVersionData({
                dependencies: [],
                incompatibleProjectIds: [],
                versionNumber: '',
                gameVersions: versionData.gameVersions,
                changelog: '',
                file: null,
                channel: versionData.channel || 'RELEASE',
                replaceExisting: false
            });
            onShowStatus('success', 'Uploaded', 'Version uploaded successfully.');
        } catch (e: any) {
            onShowStatus('error', 'Upload Failed', extractApiErrorMessage(e, 'Failed to upload version.'));
        } finally {
            setIsSavingVersion(false);
        }
    };

    const handleStartEditVersion = (version: ProjectVersion) => {
        setEditingVersion(version);
        setEditVersionData({
            dependencies: version.dependencies || [],
            incompatibleProjectIds: version.incompatibleProjectIds || [],
            versionNumber: version.versionNumber || '',
            gameVersions: version.gameVersions || (version.gameVersion ? [version.gameVersion] : []),
            changelog: version.changelog || '',
            file: null,
            channel: version.channel || 'RELEASE',
            replaceExisting: false
        });
    };

    const handleSaveEditedVersion = async () => {
        if (!projectData?.id || !editingVersion || !editVersionData) return;
        if (!editVersionData.gameVersions || editVersionData.gameVersions.length === 0) {
            onShowStatus('error', 'Update Failed', 'At least one game version is required.');
            return;
        }
        setIsSavingVersion(true);
        try {
            await projectClient.updateVersion(projectData.id, editingVersion.id, {
                dependencies: editVersionData.dependencies || [],
                incompatibleProjectIds: editVersionData.incompatibleProjectIds || [],
                gameVersions: editVersionData.gameVersions,
                changelog: editVersionData.changelog || '',
                channel: editVersionData.channel || 'RELEASE'
            });
            const refreshed = await projectClient.getProjectFull(projectData.id);
            setProjectData(refreshed);
            setEditingVersion(null);
            setEditVersionData(null);
            onShowStatus('success', 'Version Updated', 'Version metadata updated successfully.');
        } catch (e: any) {
            onShowStatus('error', 'Update Failed', e.response?.data || 'Failed to update version.');
        } finally {
            setIsSavingVersion(false);
        }
    };

    const runDeleteVersion = async (versionId: string) => {
        if (!projectData?.id) return;
        setIsSavingVersion(true);
        try {
            await projectClient.deleteVersion(projectData.id, versionId);
            const refreshed = await projectClient.getProjectFull(projectData.id);
            setProjectData(refreshed);
            onShowStatus('success', 'Version Deleted', 'Version deleted successfully.');
        } catch (e: any) {
            onShowStatus('error', 'Delete Failed', e.response?.data || 'Failed to delete version.');
        } finally {
            setIsSavingVersion(false);
            setStatusModal(null);
        }
    };

    const handleDeleteVersion = (versionId: string) => {
        const target = projectData?.versions?.find(v => v.id === versionId);
        setStatusModal({
            type: 'warning',
            title: 'Delete Version?',
            message: `This will permanently delete version ${target?.versionNumber || ''}. This action cannot be undone.`,
            actionLabel: 'Delete Version',
            secondaryLabel: 'Cancel',
            onAction: () => runDeleteVersion(versionId)
        });
    };

    const runStatusTransition = async (nextStatus: 'PUBLISHED' | 'PRIVATE' | 'UNLISTED' | 'ARCHIVED') => {
        if (!projectData?.id || isStatusChanging) return;
        setIsStatusChanging(true);

        try {
            const endpoint = nextStatus === 'PUBLISHED'
                ? 'publish'
                : nextStatus === 'PRIVATE'
                    ? 'private'
                    : nextStatus === 'UNLISTED'
                        ? 'unlist'
                        : 'archive';
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

    const confirmStatusTransition = (nextStatus: 'PUBLISHED' | 'PRIVATE' | 'UNLISTED' | 'ARCHIVED') => {
        const config = {
            PUBLISHED: {
                type: 'info' as const,
                title: 'Publish Project?',
                message: 'This will make your project visible to everyone in discovery.',
                actionLabel: 'Publish'
            },
            PRIVATE: {
                type: 'info' as const,
                title: 'Make Project Private?',
                message: 'This will hide your project from public discovery while keeping it fully editable.',
                actionLabel: 'Make Private'
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

    const handleRevertToDraft = async () => {
        if (!projectData?.id || isStatusChanging) return;
        setIsStatusChanging(true);

        try {
            await api.post(`/projects/${projectData.id}/revert`);
            setProjectData(prev => prev ? { ...prev, status: 'DRAFT' } : null);
            onShowStatus('success', 'Reverted', 'Project returned to draft. Editing is now enabled.');
        } catch (e: any) {
            onShowStatus('error', 'Revert Failed', e.response?.data || 'Failed to revert project to draft.');
        } finally {
            setIsStatusChanging(false);
            setStatusModal(null);
        }
    };

    const scaledCardPreviewWidth = cardPreviewSize.width * cardPreviewScale;
    const scaledCardPreviewHeight = cardPreviewSize.height * cardPreviewScale;

    return (
        <div className="relative">
            {galleryCropImage && createPortal(
                <ImageCropperModal
                    imageSrc={galleryCropImage}
                    sourceFile={galleryCropFile}
                    aspect={16 / 9}
                    onCancel={() => { setGalleryCropImage(null); setGalleryCropFile(null); }}
                    onCropComplete={(file) => {
                        setGalleryCropImage(null);
                        setGalleryCropFile(null);
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
                    onClose={() => {
                        if (isStatusChanging) return;
                        if (typeof statusModal.onClose === 'function') {
                            statusModal.onClose();
                            return;
                        }
                        setStatusModal(null);
                    }}
                />,
                document.body)}
            {roleModalOpen && editingRole && createPortal(
                <div className={theme.components.modalOverlay}>
                    <div className={`${theme.components.modalContent} w-full max-w-3xl max-h-[85vh]`}>
                        <div className={theme.components.modalHeader}>
                            <div>
                                <h3 className={`text-xl font-black ${theme.colors.textPrimary}`}>{editingRole.id ? 'Edit Role' : 'Create Role'}</h3>
                                <p className={`text-xs ${theme.colors.textMuted}`}>Configure permissions for this project role.</p>
                            </div>
                            <button onClick={() => { setRoleModalOpen(false); setEditingRole(null); }} className={`p-2 ${theme.colors.bgSurfaceHover} rounded-xl transition-colors`}><X className="w-5 h-5" /></button>
                        </div>

                        <form onSubmit={handleSaveRole} className="flex flex-col flex-1 overflow-hidden">
                            <div className={theme.components.modalBody}>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <div>
                                        <label className={`block text-[10px] font-bold ${theme.colors.textMuted} uppercase tracking-widest mb-1.5 ml-1`}>Role Name</label>
                                        <input
                                            type="text"
                                            value={editingRole.name || ''}
                                            onChange={e => setEditingRole({ ...editingRole, name: e.target.value })}
                                            className={theme.components.inputField}
                                            required
                                        />
                                    </div>
                                    <div>
                                        <label className={`block text-[10px] font-bold ${theme.colors.textMuted} uppercase tracking-widest mb-1.5 ml-1`}>Role Color</label>
                                        <div className="flex items-center gap-3">
                                            <input
                                                type="color"
                                                value={editingRole.color || '#3b82f6'}
                                                onChange={e => setEditingRole({ ...editingRole, color: e.target.value })}
                                                className={`w-12 h-12 p-1 ${theme.colors.bgSurfaceAlt} border ${theme.colors.border} rounded-xl cursor-pointer`}
                                            />
                                            <input
                                                type="text"
                                                value={editingRole.color || '#3b82f6'}
                                                onChange={e => setEditingRole({ ...editingRole, color: e.target.value })}
                                                className={`${theme.components.inputField} flex-1 font-mono`}
                                                pattern="^#[0-9A-Fa-f]{6}$"
                                            />
                                        </div>
                                    </div>
                                </div>

                                <div className="space-y-4">
                                    <h4 className={`font-bold ${theme.colors.textPrimary} text-sm border-b ${theme.colors.borderFaint} pb-2`}>Permissions</h4>
                                    <PermissionSelector
                                        groups={PROJECT_PERMISSION_GROUPS}
                                        selectedPermissions={editingRole.permissions || []}
                                        onChange={(perms) => setEditingRole({ ...editingRole, permissions: perms })}
                                        variant="card"
                                    />
                                </div>
                            </div>

                            <div className={theme.components.modalFooter}>
                                <button type="button" onClick={() => { setRoleModalOpen(false); setEditingRole(null); }} className={theme.components.buttonSecondary}>Cancel</button>
                                <button type="submit" className={theme.components.buttonPrimary}>Save Role</button>
                            </div>
                        </form>
                    </div>
                </div>,
                document.body)}
            {showSlugPrompt && createPortal(
                <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200">
                    <div className={`bg-white dark:bg-modtale-card border ${theme.colors.border} rounded-xl w-full max-w-md shadow-2xl overflow-hidden relative z-[110]`}>
                        <button onClick={() => setShowSlugPrompt(false)} className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 dark:hover:text-white">
                            <X className="w-5 h-5" />
                        </button>
                        <div className="p-6 text-center bg-blue-500/10">
                            <div className="mx-auto w-16 h-16 rounded-full flex items-center justify-center mb-4 bg-blue-500 text-white">
                                <Info className="w-8 h-8" />
                            </div>
                            <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Choose a Custom Slug?</h2>
                            <p className="text-slate-600 dark:text-slate-300">Your project can use a cleaner URL if you set a custom slug before submitting. You can still submit without one.</p>
                        </div>
                        <div className="p-4 flex justify-center gap-3">
                            <button
                                type="button"
                                onClick={handleSetCustomSlug}
                                className="px-6 py-3 rounded-lg font-bold text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/10 transition-colors"
                            >
                                Set Custom Slug
                            </button>
                            <button
                                type="button"
                                onClick={() => { setShowSlugPrompt(false); handleSubmit(); }}
                                className="flex items-center gap-2 px-8 py-3 rounded-lg font-bold text-white transition-transform active:scale-95 bg-blue-600 hover:bg-blue-700"
                            >
                                Submit Anyway
                            </button>
                        </div>
                    </div>
                </div>,
                document.body)}
            {editingVersion && editVersionData && createPortal(
                <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200">
                    <div className={`bg-white dark:bg-modtale-card border ${theme.colors.border} rounded-xl w-full max-w-3xl shadow-2xl overflow-hidden relative z-[110] max-h-[90vh] overflow-y-auto`}>
                        <button onClick={() => { if (!isSavingVersion) { setEditingVersion(null); setEditVersionData(null); } }} className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 dark:hover:text-white">
                            <X className="w-5 h-5" />
                        </button>
                        <div className="p-6 border-b border-slate-200 dark:border-white/10">
                            <h2 className={`text-2xl font-black ${theme.colors.textPrimary}`}>Edit Version</h2>
                            <p className={`text-sm ${theme.colors.textMuted} mt-1`}>{editingVersion.versionNumber}</p>
                        </div>
                        <div className="p-6">
                            <VersionFields
                                data={editVersionData}
                                onChange={setEditVersionData}
                                isModpack={isModpack}
                                projectType={projectData.classification || 'PLUGIN'}
                                hideFilePicker={true}
                                currentProjectId={projectData.id}
                            />
                        </div>
                        <div className="p-4 border-t border-slate-200 dark:border-white/10 flex justify-end gap-3">
                            <button type="button" disabled={isSavingVersion} onClick={() => { setEditingVersion(null); setEditVersionData(null); }} className={`px-5 py-2.5 rounded-lg font-bold ${theme.colors.textSecondary} hover:bg-slate-100 dark:hover:bg-white/10 transition-colors`}>
                                Cancel
                            </button>
                            <button type="button" disabled={isSavingVersion} onClick={handleSaveEditedVersion} className={theme.components.buttonPrimary}>
                                {isSavingVersion ? <Spinner className="w-4 h-4 !p-0" fullScreen={false} /> : 'Save Version'}
                            </button>
                        </div>
                    </div>
                </div>,
                document.body)}
            {showCardPreview && createPortal(
                <div
                    className="fixed inset-0 z-[220] flex items-center justify-center bg-slate-950/85 backdrop-blur-md p-4 sm:p-8 animate-in fade-in duration-200"
                    role="dialog"
                    aria-modal="true"
                    aria-label="Project card preview"
                    onClick={() => setShowCardPreview(false)}
                >
                    <button
                        type="button"
                        onClick={() => setShowCardPreview(false)}
                        className="absolute right-4 top-4 sm:right-6 sm:top-6 z-10 flex h-11 w-11 items-center justify-center rounded-full border border-white/15 bg-white/10 text-white shadow-2xl backdrop-blur transition-colors hover:bg-white/20 focus:outline-none focus:ring-2 focus:ring-white/70"
                        aria-label="Close card preview"
                    >
                        <X className="h-5 w-5" />
                    </button>
                    <div className="flex h-full w-full flex-col items-center justify-center gap-5">
                        <div className="text-center text-white">
                            <h3 className="text-lg sm:text-xl font-black">Project Card Preview</h3>
                        </div>
                        <div
                            data-testid="project-card-preview-frame"
                            className="max-w-full"
                            onClick={(event) => event.stopPropagation()}
                            style={{
                                width: scaledCardPreviewWidth,
                                height: scaledCardPreviewHeight
                            }}
                        >
                            <div
                                ref={cardPreviewRef}
                                className="pointer-events-none select-none origin-top-left"
                                style={{
                                    width: CARD_PREVIEW_BASE_WIDTH,
                                    transform: `scale(${cardPreviewScale})`,
                                    transition: 'transform 160ms ease'
                                }}
                            >
                                <ProjectCard project={previewProject} isFavorite={false} onToggleFavorite={() => {}} isLoggedIn={false} disableNavigation />
                            </div>
                        </div>
                    </div>
                </div>,
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

                        {(projectData.status === 'DRAFT' || projectData.status === 'PRIVATE') && (
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

                                <button onClick={handleSubmitClick} disabled={isSaving || !isPublishable} className={`h-10 px-6 rounded-xl font-black flex items-center justify-center gap-2 transition-all shadow-lg active:scale-95 ${isPublishable ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-900 hover:opacity-90' : 'bg-slate-200 dark:bg-slate-800 text-slate-400 shadow-none cursor-not-allowed'}`}>
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
                                <h1 className={`text-4xl md:text-5xl font-black ${theme.colors.textPrimary} tracking-normal break-words`}>{metaData.title || 'Project Title'}</h1>
                                {!readOnly && <Edit3 className="w-5 h-5 text-slate-400 opacity-0 group-hover:opacity-100 transition-opacity" />}
                            </div>
                        )}
                        <input value={metaData.summary} disabled={readOnly} onChange={e => { markDirty(); setMetaData({...metaData, summary: e.target.value}); }} className={`text-lg ${theme.colors.textPrimary} font-medium bg-transparent border-b border-transparent outline-none w-full mt-2 hover:border-slate-300 dark:hover:border-white/20 focus:border-modtale-accent pb-1`} placeholder="Short summary..."/>
                        {projectData.status === 'PENDING' && (
                            <div className="mt-4 rounded-2xl border border-amber-300/70 dark:border-amber-400/30 bg-amber-50/90 dark:bg-amber-500/10 p-4">
                                <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
                                    <div className="flex items-start gap-3">
                                        <AlertTriangle className="w-5 h-5 text-amber-600 dark:text-amber-300 mt-0.5 flex-shrink-0" />
                                        <div>
                                            <p className="text-sm font-bold text-amber-900 dark:text-amber-200">This project is pending review.</p>
                                            <p className="text-xs font-medium text-amber-800/90 dark:text-amber-200/80 mt-1">Need to fix something before approval? Revert to draft to unlock editing and resubmit when ready.</p>
                                        </div>
                                    </div>
                                    <button
                                        type="button"
                                        disabled={isStatusChanging}
                                        onClick={() => setStatusModal({
                                            type: 'warning',
                                            title: 'Revert to Draft?',
                                            message: 'This will move your pending submission back to draft so you can make edits.',
                                            actionLabel: 'Revert to Draft',
                                            secondaryLabel: 'Cancel',
                                            onAction: handleRevertToDraft
                                        })}
                                        className="h-10 px-4 rounded-xl font-bold text-sm flex items-center justify-center gap-2 bg-amber-600 text-white hover:bg-amber-700 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
                                    >
                                        <Undo2 className="w-4 h-4" />
                                        Revert to Draft
                                    </button>
                                </div>
                            </div>
                        )}
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
                            <WikiSidebar tree={wikiData.mod.pages || []} projectUrl="#" currentSlug={wikiPreviewSlug} indexSlug={wikiData.mod.index?.slug} onNavigate={setWikiPreviewSlug} pageCache={wikiData.pageCache} />
                        )}
                        <SidebarSection title="Card Preview" icon={Eye}>
                            <div
                                role="button"
                                tabIndex={0}
                                onClick={() => setShowCardPreview(true)}
                                onKeyDown={(event) => {
                                    if (event.key === 'Enter' || event.key === ' ') {
                                        event.preventDefault();
                                        setShowCardPreview(true);
                                    }
                                }}
                                className={`w-full max-w-[340px] mx-auto relative group overflow-hidden rounded-2xl border ${theme.colors.border} text-left transition-all hover:shadow-lg hover:shadow-modtale-accent/10 focus:outline-none focus:ring-2 focus:ring-modtale-accent focus:ring-offset-2 dark:focus:ring-offset-slate-950`}
                                aria-label="Expand project card preview"
                            >
                                <div className="pointer-events-none select-none">
                                    <ProjectCard project={previewProject} isFavorite={false} onToggleFavorite={() => {}} isLoggedIn={false} disableNavigation />
                                </div>
                                <div className="pointer-events-none absolute inset-x-0 bottom-0 flex items-center justify-between bg-gradient-to-t from-slate-950/80 via-slate-950/20 to-transparent px-4 py-3 text-white opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100">
                                    <span className="text-[11px] font-bold uppercase tracking-[0.2em]">Click to expand</span>
                                    <ExternalLink className="w-4 h-4" />
                                </div>
                            </div>
                        </SidebarSection>
                        {!isModpack && (
                            <SidebarSection title="License" icon={Scale} defaultOpen={false}>
                                <div className={`bg-slate-50 dark:bg-slate-950/50 border ${theme.colors.border} rounded-xl p-2 max-h-80 overflow-y-auto`}>
                                    {LICENSES.map(lic => (
                                        <button
                                            key={lic.id}
                                            disabled={readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA)}
                                            onClick={() => handleSelectStandardLicense(lic.id)}
                                            className={`w-full text-left px-3 py-2 rounded-lg text-xs font-bold flex items-center justify-between ${metaData.license === lic.id ? 'bg-modtale-accent text-white' : `${theme.colors.textSecondary} hover:bg-slate-200 dark:hover:bg-white/10`}`}
                                        >
                                            <span>{lic.name}</span>
                                            {metaData.license === lic.id && <Check className="w-3 h-3" />}
                                        </button>
                                    ))}
                                    <button
                                        disabled={readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA)}
                                        onClick={() => { markDirty(); if (!isCustomLicense) setMetaData({ ...metaData, license: '', customLicenseOpenSource: false }); }}
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
                                                disabled={readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA)}
                                                className={`w-full ${theme.colors.bgSurfaceAlt} border ${theme.colors.border} rounded-lg px-3 py-2 text-xs`}
                                            />
                                            <input
                                                value={metaData.links.LICENSE || ''}
                                                onChange={(e) => { markDirty(); setMetaData({ ...metaData, links: { ...metaData.links, LICENSE: e.target.value } }); }}
                                                placeholder="License URL"
                                                disabled={readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA)}
                                                className={`w-full ${theme.colors.bgSurfaceAlt} border rounded-lg px-3 py-2 text-xs font-mono transition-colors ${!metaData.links.LICENSE ? 'border-red-500 focus:border-red-500' : theme.colors.border}`}
                                            />
                                            {!metaData.links.LICENSE && (
                                                <p className="text-[10px] text-red-500 font-bold px-1">URL is required for custom licenses.</p>
                                            )}
                                            <button
                                                type="button"
                                                aria-pressed={metaData.customLicenseOpenSource}
                                                disabled={readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA)}
                                                onClick={() => { markDirty(); setMetaData({ ...metaData, customLicenseOpenSource: !metaData.customLicenseOpenSource }); }}
                                                className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg border text-xs font-bold transition-colors ${metaData.customLicenseOpenSource ? 'bg-emerald-500/10 border-emerald-500/40 text-emerald-700 dark:text-emerald-300' : `${theme.colors.bgSurfaceAlt} ${theme.colors.border} ${theme.colors.textSecondary} hover:bg-slate-200 dark:hover:bg-white/10`}`}
                                            >
                                                <span className={`w-4 h-4 rounded border flex items-center justify-center shrink-0 ${metaData.customLicenseOpenSource ? 'bg-emerald-500 border-emerald-500 text-white' : theme.colors.border}`}>
                                                    {metaData.customLicenseOpenSource && <Check className="w-3 h-3" />}
                                                </span>
                                                <span>Open Source</span>
                                            </button>
                                        </div>
                                    )}
                                </div>
                            </SidebarSection>
                        )}
                        <SidebarSection title="Tags" icon={Tag} defaultOpen={false}>
                            <div className="flex flex-wrap gap-2">
                                {GLOBAL_TAGS.map(tag => (
                                    <button
                                        disabled={readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA)}
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
                                        disabled={readOnly || !hasProjectPermission(Permission.PROJECT_EDIT_METADATA)}
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
                            <EditDetails metaData={metaData} projectData={projectData} setMetaData={setMetaData} readOnly={readOnly} hasProjectPermission={hasProjectPermission} editorMode={editorMode} setEditorMode={setEditorMode} markDirty={markDirty} />
                        )}
                        {activeTab === 'files' && (
                            <Files
                                projectData={projectData}
                                versionData={versionData}
                                setVersionData={setVersionData}
                                readOnly={readOnly}
                                hasProjectPermission={hasProjectPermission}
                                classification={projectData.classification || 'PLUGIN'}
                                handleUploadVersion={handleUploadVersion}
                                handleEditVersion={handleStartEditVersion}
                                handleDeleteVersion={handleDeleteVersion}
                                isLoading={isSavingVersion}
                            />
                        )}
                        {activeTab === 'gallery' && (
                <Gallery
                                projectData={projectData}
                                readOnly={readOnly}
                                hasProjectPermission={hasProjectPermission}
                                handleGalleryDelete={handleGalleryDelete}
                                handleGalleryCaptionChange={handleGalleryCaptionChange}
                                handleGalleryVideoAdd={handleGalleryVideoAdd}
                    handleGallerySelect={(f) => {
                        if (isFileOverUploadLimit(f)) {
                            onShowStatus('error', 'Upload Failed', MAX_UPLOAD_ERROR_MESSAGE);
                            return;
                        }
                        setGalleryCropImage(URL.createObjectURL(f));
                        setGalleryCropFile(f);
                    }}
                                isLoading={isSaving}
                            />
                        )}
                        {activeTab === 'team' && (
                            <Team
                                projectData={projectData}
                                currentUser={currentUser}
                                canInvite={canInvite}
                                canManageRoles={canManageRoles}
                                canRemove={canRemove}
                                inviteUsername={inviteUsername}
                                inviteUserId={inviteUserId}
                                setInviteUsername={setInviteUsername}
                                setInviteUserId={setInviteUserId}
                                inviteRoleId={inviteRoleId}
                                setInviteRoleId={setInviteRoleId}
                                userSearchResults={userSearchResults}
                                setUserSearchResults={setUserSearchResults}
                                inviteRoleDropdownOpen={inviteRoleDropdownOpen}
                                setInviteRoleDropdownOpen={setInviteRoleDropdownOpen}
                                memberRoleDropdownOpen={memberRoleDropdownOpen}
                                setMemberRoleDropdownOpen={setMemberRoleDropdownOpen}
                                setMemberToRemove={handleRequestRemoveMember}
                                handleInvite={handleInvite}
                                handleRoleUpdate={handleRoleUpdate}
                                handleCancelInvite={handleCancelInvite}
                                setEditingRole={setEditingRole}
                                setRoleModalOpen={setRoleModalOpen}
                                handleDeleteRole={handleDeleteRole}
                                isInviting={isInviting}
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
                                handlePrivate={() => confirmStatusTransition('PRIVATE')}
                                handleUnlist={() => confirmStatusTransition('UNLISTED')}
                                handleArchive={() => confirmStatusTransition('ARCHIVED')}
                                slugError={slugError}
                                handleSlugChange={handleSlugChange}
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
