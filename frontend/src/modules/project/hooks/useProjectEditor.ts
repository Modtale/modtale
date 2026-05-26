import { useState, useCallback } from 'react';
import { projectClient } from '@/modules/project/api/projectClient';
import { api } from '@/utils/api';
import type { Project, User } from '@/types';
import type { MetadataFormData } from '../components/FormShared';

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
            .catch(e => {
                const errorMsg = typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Failed to fetch repositories.';
                onShowStatus('error', 'Repository Error', errorMsg);
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
        } catch (err: any) {
            onShowStatus('error', 'Update Failed', err.response?.data || "Failed to update role.");
        }
    };

    const handleCancelInvite = async (userId: string) => {
        if (!projectData?.id) return;
        try {
            await projectClient.cancelInvite(projectData.id, userId);
            setProjectData(prev => prev ? ({ ...prev, teamInvites: (prev.teamInvites || []).filter(m => m.userId !== userId) }) : null);
        } catch (e: any) {
            onShowStatus('error', 'Error', e.response?.data || "Could not cancel invite.");
        }
    };

    const handleSave = async () => {
        if (!projectData?.id) return;
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
                license: metaData.license
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
                refreshed = await projectClient.getProject(projectData.id);
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
        } catch (e: any) {
            const errData = e.response?.data;
            if (typeof errData === 'string' && errData.toLowerCase().includes('slug')) {
                setSlugError(errData);
            }
            onShowStatus('error', 'Save Failed', errData || 'Failed to save project.');
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
            onShowStatus('error', 'Submit Failed', e.response?.data || 'Failed to submit project.');
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
            onShowStatus('error', 'Upload Failed', e.response?.data || 'Failed to upload image.');
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
            const errorMsg = (e.response?.data && typeof e.response.data === 'object')
                ? (e.response.data.message || "Failed to delete image.")
                : (e.response?.data || e.message || 'Failed to delete image.');
            onShowStatus('error', 'Delete Failed', errorMsg);
        }
    };

    return {
        repos, loadingRepos, manualRepo, setManualRepo, repoValid, isDirty, setIsDirty,
        slugError, setSlugError, userSearchResults, setUserSearchResults, provider,
        setProvider, markDirty, checkRepoUrl, fetchRepos, handleRoleUpdate, handleCancelInvite,
        handleSave, handleSubmit, isSaving, handleGalleryUpload, handleGalleryDelete
    };
};
