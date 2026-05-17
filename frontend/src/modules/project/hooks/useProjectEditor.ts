import { useState, useEffect, useRef, useCallback } from 'react';
import { projectClient } from '@/modules/project/api/projectClient';
import type { Project, User } from '@/types';
import type { MetadataFormData } from '../components/FormShared';

export const useProjectEditor = (projectData: Project | null, currentUser: User | null, metaData: MetadataFormData, setMetaData: React.Dispatch<React.SetStateAction<MetadataFormData>>, setProjectData: React.Dispatch<React.SetStateAction<Project | null>>, onShowStatus: any) => {
    const [repos, setRepos] = useState<any[]>([]);
    const [loadingRepos, setLoadingRepos] = useState(false);
    const [manualRepo, setManualRepo] = useState(false);
    const [repoValid, setRepoValid] = useState(true);
    const [isDirty, setIsDirty] = useState(false);
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
        } catch (err: any) { onShowStatus('error', 'Update Failed', err.response?.data || "Failed to update role."); }
    };

    const handleCancelInvite = async (userId: string) => {
        if (!projectData?.id) return;
        try {
            await projectClient.cancelInvite(projectData.id, userId);
            setProjectData(prev => prev ? ({ ...prev, teamInvites: (prev.teamInvites || []).filter(m => m.userId !== userId) }) : null);
        } catch (e: any) { onShowStatus('error', 'Error', e.response?.data || "Could not cancel invite."); }
    };

    return { repos, loadingRepos, manualRepo, setManualRepo, repoValid, isDirty, setIsDirty, slugError, setSlugError, userSearchResults, setUserSearchResults, provider, setProvider, markDirty, checkRepoUrl, fetchRepos, handleRoleUpdate, handleCancelInvite };
};