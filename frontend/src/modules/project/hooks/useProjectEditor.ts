import { useState, useCallback } from 'react';
import { projectClient } from '@/modules/project/api/projectClient';
import { api, extractApiErrorMessage } from '@/utils/api';
import type { Project, User } from '@/types';
import type { MetadataFormData } from '../components/FormShared';
import { countGalleryCarouselMarkers, GALLERY_CAROUSEL_MARKER } from '../utils/galleryCarouselMarker';

export const useProjectEditor = (
    projectData: Project | null,
    currentUser: User | null,
    metaData: MetadataFormData,
    bannerFile: File | null,
    setMetaData: React.Dispatch<React.SetStateAction<MetadataFormData>>,
    setBannerFile: React.Dispatch<React.SetStateAction<File | null>>,
    setBannerPreview: React.Dispatch<React.SetStateAction<string | null>>,
    setProjectData: React.Dispatch<React.SetStateAction<Project | null>>,
    onShowStatus: any
) => {
    const [repos, setRepos] = useState<any[]>([]);
    const [loadingRepos, setLoadingRepos] = useState(false);
    const [manualRepo, setManualRepo] = useState(false);
    const [repoValid, setRepoValid] = useState(true);
    const [isDirty, setIsDirty] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [slugError, setSlugError] = useState<string | null>(null);
    const [userSearchResults, setUserSearchResults] = useState<User[]>([]);

    const hasGithub = currentUser?.connectedAccounts?.some(a => a.provider === 'github') || false;
    const hasGitlab = currentUser?.connectedAccounts?.some(a => a.provider === 'gitlab') || false;
    const [provider, setProvider] = useState<'github' | 'gitlab'>(hasGithub ? 'github' : (hasGitlab ? 'gitlab' : 'github'));

    const markDirty = useCallback(() => { if (!isDirty) setIsDirty(true); }, [isDirty]);

    const checkRepoUrl = useCallback((url: string) => {
        if (!url) { setRepoValid(true); return true; }
        const isValid = /^https:\/\/(github\.com|gitlab\.com|codeberg\.org)\/[\w.-]+\/[\w.-]+$/.test(url);
        setRepoValid(isValid);
        return isValid;
    }, []);

    const fetchRepos = useCallback(() => {
        if (manualRepo) return;
        if ((provider === 'github' && !hasGithub) || (provider === 'gitlab' && !hasGitlab)) return;
        setLoadingRepos(true);
        projectClient.getGitRepos(provider)
            .then(data => setRepos(data || []))
            .catch((e: unknown) => {
                onShowStatus('error', 'Repository Error', extractApiErrorMessage(e, 'We could not load repositories for that connected account.'));
            })
            .finally(() => setLoadingRepos(false));
    }, [provider, hasGithub, hasGitlab, manualRepo, onShowStatus]);

    const handleRoleUpdate = async (userId: string, newRoleId: string) => {
        if (!projectData?.id) return;
        try {
            await projectClient.updateRole(projectData.id, userId, newRoleId);
            setProjectData(prev => {
                if (!prev) return null;
                const members = [...(prev.teamMembers || [])];
                const idx = members.findIndex(m => m.userId === userId);
                if (idx > -1) members[idx] = { ...members[idx], roleId: newRoleId };
                return { ...prev, teamMembers: members };
            });
            onShowStatus('success', 'Updated', 'Member role updated.');
        } catch (err: unknown) {
            onShowStatus('error', 'Update Failed', extractApiErrorMessage(err, 'We could not update that team role.'));
        }
    };

    const handleCancelInvite = async (userId: string) => {
        if (!projectData?.id) return;
        try {
            await projectClient.cancelInvite(projectData.id, userId);
            setProjectData(prev => prev ? ({ ...prev, teamInvites: (prev.teamInvites || []).filter(m => m.userId !== userId) }) : null);
        } catch (e: unknown) {
            onShowStatus('error', 'Invite Cancel Failed', extractApiErrorMessage(e, 'We could not cancel that project invite.'));
        }
    };

    const handleSave = async () => {
        if (!projectData?.id) return;
        if (countGalleryCarouselMarkers(metaData.description) > 1) {
            onShowStatus('error', 'Gallery Carousel Marker', `Use ${GALLERY_CAROUSEL_MARKER} only once in the project description.`);
            return;
        }

        setIsSaving(true);
        setSlugError(null);
        try {
            const payload = {
                title: metaData.title,
                slug: metaData.slug,
                description: metaData.summary,
                about: metaData.description,
                tags: metaData.tags,
                links: metaData.links,
                repositoryUrl: metaData.repositoryUrl,
                license: metaData.license,
                allowModpacks: projectData.allowModpacks,
                allowComments: projectData.allowComments,
                hmWikiEnabled: projectData.hmWikiEnabled,
                hmWikiSlug: projectData.hmWikiSlug
            };

            await api.put(`/projects/${projectData.id}`, payload);

            if (metaData.iconFile) {
                const iconFormData = new FormData();
                iconFormData.append('file', metaData.iconFile);
                await api.put(`/projects/${projectData.id}/icon`, iconFormData, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                });
            }

            if (bannerFile) {
                const bannerFormData = new FormData();
                bannerFormData.append('file', bannerFile);
                await api.put(`/projects/${projectData.id}/banner`, bannerFormData, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                });
            }

            let refreshed: Project | null = null;
            try {
                refreshed = await projectClient.getProjectFull(projectData.id);
            } catch {
            }

            setProjectData(prev => {
                if (refreshed && prev) return { ...prev, ...refreshed };
                if (refreshed) return refreshed;
                if (!prev) return prev;
                return {
                    ...prev,
                    title: metaData.title,
                    slug: metaData.slug,
                    description: metaData.summary,
                    about: metaData.description,
                    tags: metaData.tags,
                    links: metaData.links,
                    repositoryUrl: metaData.repositoryUrl,
                    license: metaData.license
                };
            });
            setIsDirty(false);
            setMetaData(prev => ({
                ...prev,
                iconFile: null,
                iconPreview: refreshed?.imageUrl || prev.iconPreview
            }));
            setBannerFile(null);
            setBannerPreview(prev => refreshed?.bannerUrl ?? prev);

            onShowStatus('success', 'Saved', 'Project details saved successfully.');
        } catch (e: unknown) {
            const errorMessage = extractApiErrorMessage(e, 'We could not save this project.');
            if (errorMessage.toLowerCase().includes('slug')) {
                setSlugError(errorMessage.replace(/^we could not save this project\.\s*/i, ''));
            }
            onShowStatus('error', 'Save Failed', errorMessage);
        } finally {
            setIsSaving(false);
        }
    };

    const handleSubmit = async () => {
        if (!projectData?.id) return;
        setIsSaving(true);
        try {
            if (isDirty) await handleSave();
            await api.post(`/projects/${projectData.id}/submit`);
            setProjectData(prev => prev ? { ...prev, status: 'PENDING' as any } : null);
            onShowStatus('success', 'Submitted', 'Project submitted for review successfully.');
        } catch (e: any) {
            onShowStatus('error', 'Submit Failed', extractApiErrorMessage(e, 'Failed to submit project.'));
        } finally {
            setIsSaving(false);
        }
    };

    const handleGalleryUpload = async (file: File) => {
        if (!projectData?.id) return;
        const formData = new FormData();
        formData.append('file', file);
        try {
            const res = await api.post(`/projects/${projectData.id}/gallery`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
            setProjectData(res.data);
            onShowStatus('success', 'Uploaded', 'Image added to gallery.');
        } catch (e: any) {
            onShowStatus('error', 'Upload Failed', extractApiErrorMessage(e, 'Failed to upload image.'));
        }
    };

    const handleGalleryVideoAdd = async (videoUrl: string) => {
        if (!projectData?.id) return;
        try {
            const res = await api.post(`/projects/${projectData.id}/gallery/youtube`, {
                videoUrl
            });
            setProjectData(res.data);
            onShowStatus('success', 'Added', 'Video added to gallery.');
        } catch (e: any) {
            onShowStatus('error', 'Video Add Failed', extractApiErrorMessage(e, 'Failed to add video.'));
        }
    };

    const handleGalleryCaptionChange = async (imageUrl: string, caption: string) => {
        if (!projectData?.id) return;
        try {
            const res = await api.put(`/projects/${projectData.id}/gallery/caption`, {
                imageUrl,
                caption
            });
            setProjectData(res.data);
            onShowStatus('success', 'Saved', 'Gallery caption updated.');
        } catch (e: any) {
            onShowStatus('error', 'Caption Save Failed', extractApiErrorMessage(e, 'Failed to update gallery caption.'));
        }
    };

    const handleGalleryDelete = async (url: string) => {
        if (!projectData?.id) return;
        try {
            const res = await api.delete(`/projects/${projectData.id}/gallery`, {
                data: { imageUrl: url }
            });

            setProjectData(res.data);
            onShowStatus('success', 'Deleted', 'Image removed from gallery.');
        } catch (e: any) {
            onShowStatus('error', 'Delete Failed', extractApiErrorMessage(e, 'Failed to delete image.'));
        }
    };

    return {
        repos, loadingRepos, manualRepo, setManualRepo, repoValid, isDirty, setIsDirty,
        slugError, setSlugError, userSearchResults, setUserSearchResults, provider,
        setProvider, markDirty, checkRepoUrl, fetchRepos, handleRoleUpdate, handleCancelInvite,
        handleSave, handleSubmit, isSaving, handleGalleryUpload, handleGalleryVideoAdd, handleGalleryCaptionChange, handleGalleryDelete
    };
};
