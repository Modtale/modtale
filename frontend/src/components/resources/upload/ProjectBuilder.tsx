import React, { useState, useEffect, useCallback, useRef } from 'react';
import { createPortal } from 'react-dom';
import { useDropzone } from 'react-dropzone';
import {
    Save, UploadCloud, Link as LinkIcon, Tag,
    GitMerge, Settings, Plus,
    ToggleLeft, ToggleRight, Trash2, FileText, LayoutTemplate,
    UserPlus, Scale, Check, Copy, Link2, Edit2, X, ChevronDown, RefreshCw, Loader2, CheckCircle2, Eye, Maximize2,
    AlertCircle, Clock, Archive, Globe, EyeOff, Image as ImageIcon, MessageSquare, ExternalLink, Sparkles, Trophy, Users, Palette, ShieldCheck, Shield, BookOpen
} from 'lucide-react';
import { Link } from 'react-router-dom';

import { api, BACKEND_URL } from '../../../utils/api';
import { LICENSES, GLOBAL_TAGS } from '../../../data/categories';
import type { Classification } from '../../../data/categories';
import { Spinner } from '@/components/ui/Spinner';
import { StatusModal } from '@/components/ui/StatusModal';
import { ImageCropperModal } from '@/components/ui/ImageCropperModal';
import { ProjectLayout, SidebarSection } from '@/components/resources/ProjectLayout.tsx';
import { createSlug } from '../../../utils/slug';
import type { Mod, User, ProjectVersion, ProjectRole, ProjectMember } from '../../../types';
import { ModCard } from '../ModCard';
import { VersionFields, ThemedInput } from './FormShared';
import type { MetadataFormData, VersionFormData } from './FormShared';
import { MarkdownRenderer } from '@/components/ui/MarkdownRenderer';
import { useHMWiki, WikiSidebar, WikiContent } from '../HMWiki';
import { PermissionSelector, PROJECT_PERMISSION_GROUPS } from '../../ui/PermissionSelector.tsx';

interface ProjectBuilderProps {
    modData: Mod | null;
    setModData: React.Dispatch<React.SetStateAction<Mod | null>>;
    metaData: MetadataFormData;
    setMetaData: React.Dispatch<React.SetStateAction<MetadataFormData>>;
    versionData: VersionFormData;
    setVersionData: React.Dispatch<React.SetStateAction<VersionFormData>>;
    bannerPreview: string | null;
    setBannerPreview: (url: string | null) => void;
    setBannerFile: (file: File | null) => void;
    handleSave: (silent?: boolean) => Promise<boolean>;
    handlePublish?: () => void;
    handleDelete?: () => void;
    handleDeleteVersion?: (versionId: string) => void;
    handleUploadVersion: () => void;
    handleRevert?: () => void;
    handleArchive?: () => void;
    handleUnlist?: () => void;
    handleRestore?: () => void;
    handleGalleryUpload: (file: File) => Promise<void>;
    handleGalleryDelete: (url: string) => Promise<void>;
    isLoading: boolean;
    classification: Classification | string;
    currentUser: User | null;
    activeTab: 'details' | 'files' | 'gallery' | 'team' | 'settings' | 'wiki' | string;
    setActiveTab: (tab: any) => void;
    onShowStatus: (type: 'success' | 'error' | 'warning' | 'info', title: string, msg: string) => void;
    readOnly?: boolean;
}

const JamSelectDropdown = ({ value, options, onChange }: { value: string, options: any[], onChange: (v: string) => void }) => {
    const [isOpen, setIsOpen] = useState(false);
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (ref.current && !ref.current.contains(e.target as Node)) setIsOpen(false);
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const selected = options.find(o => o.id === value);

    return (
        <div className="relative w-full" ref={ref}>
            <button
                type="button"
                onClick={() => setIsOpen(!isOpen)}
                className="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 text-sm font-bold text-slate-900 dark:text-white outline-none focus:ring-2 focus:ring-modtale-accent flex justify-between items-center transition-all shadow-sm hover:border-modtale-accent/50"
            >
                <span className="truncate">{selected ? selected.title : '-- Do not enter a jam --'}</span>
                <ChevronDown className={`w-4 h-4 text-slate-500 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
            </button>
            {isOpen && (
                <div className="absolute top-full mt-2 left-0 right-0 bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-50 max-h-48 overflow-y-auto custom-scrollbar p-1">
                    <button
                        type="button"
                        onClick={() => { onChange(''); setIsOpen(false); }}
                        className={`w-full text-left px-3 py-2 text-sm rounded-lg transition-colors ${!value ? 'bg-modtale-accent/10 text-modtale-accent font-black' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-white/5 font-medium'}`}
                    >
                        -- Do not enter a jam --
                    </button>
                    {options.map((opt) => (
                        <button
                            key={opt.id}
                            type="button"
                            onClick={() => { onChange(opt.id); setIsOpen(false); }}
                            className={`w-full text-left px-3 py-2 text-sm rounded-lg transition-colors ${value === opt.id ? 'bg-modtale-accent/10 text-modtale-accent font-black' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-white/5 font-medium'}`}
                        >
                            {opt.title}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};

export const ProjectBuilder: React.FC<ProjectBuilderProps> = ({
                                                                  modData, setModData, metaData, setMetaData, versionData, setVersionData,
                                                                  bannerPreview, setBannerPreview, setBannerFile,
                                                                  handleSave, handlePublish, handleDelete, handleDeleteVersion, handleUploadVersion,
                                                                  handleRevert, handleArchive, handleUnlist, handleRestore, handleGalleryUpload, handleGalleryDelete,
                                                                  isLoading, classification, activeTab, setActiveTab, readOnly, currentUser,
                                                                  onShowStatus
                                                              }) => {
    const [editorMode, setEditorMode] = useState<'write' | 'preview'>(readOnly ? 'preview' : 'write');
    const [inviteUsername, setInviteUsername] = useState('');
    const [inviteUserId, setInviteUserId] = useState('');
    const [inviteRoleId, setInviteRoleId] = useState('');
    const [userSearchResults, setUserSearchResults] = useState<User[]>([]);
    const [isSearchingUsers, setIsSearchingUsers] = useState(false);

    const [inviteRoleDropdownOpen, setInviteRoleDropdownOpen] = useState(false);
    const [memberRoleDropdownOpen, setMemberRoleDropdownOpen] = useState<string | null>(null);

    const [editingRole, setEditingRole] = useState<Partial<ProjectRole> | null>(null);
    const [roleModalOpen, setRoleModalOpen] = useState(false);

    const [isInviting, setIsInviting] = useState(false);
    const [showPublishConfirm, setShowPublishConfirm] = useState(false);
    const [showSlugPrompt, setShowSlugPrompt] = useState(false);
    const [showCardPreview, setShowCardPreview] = useState(false);

    const [repos, setRepos] = useState<any[]>([]);
    const [loadingRepos, setLoadingRepos] = useState(false);
    const [repoSearch, setRepoSearch] = useState('');
    const [repoDropdownOpen, setRepoDropdownOpen] = useState(false);
    const [manualRepo, setManualRepo] = useState(false);
    const [repoValid, setRepoValid] = useState(true);
    const repoDropdownRef = useRef<HTMLDivElement>(null);

    const [isDirty, setIsDirty] = useState(false);
    const [idCopied, setIdCopied] = useState(false);
    const [slugError, setSlugError] = useState<string | null>(null);

    const [isCustomLicense, setIsCustomLicense] = useState(false);

    const [editingVersion, setEditingVersion] = useState<ProjectVersion | null>(null);
    const [editVersionData, setEditVersionData] = useState<VersionFormData | null>(null);
    const [isSavingVersion, setIsSavingVersion] = useState(false);
    const [memberToRemove, setMemberToRemove] = useState<string | null>(null);
    const [galleryCropImage, setGalleryCropImage] = useState<string | null>(null);

    const [wikiPreviewSlug, setWikiPreviewSlug] = useState<string | undefined>();
    const { data: wikiData, loading: wikiLoading, error: wikiError } = useHMWiki(modData?.hmWikiSlug, wikiPreviewSlug, activeTab === 'wiki' && modData?.hmWikiEnabled === true);

    const [jamMeta, setJamMeta] = useState<Record<string, { title: string, slug: string, isWinner: boolean, imageUrl?: string }>>({});
    const fetchedJamMeta = useRef<Set<string>>(new Set());

    // Jam Submission State
    const [activeJams, setActiveJams] = useState<any[]>([]);
    const [selectedPublishJamId, setSelectedPublishJamId] = useState<string>('');
    const [agreedToPublishJamRules, setAgreedToPublishJamRules] = useState(false);
    const [isPublishingJam, setIsPublishingJam] = useState(false);
    const [jamSubmitError, setJamSubmitError] = useState<string | null>(null);

    const isPlugin = classification === 'PLUGIN';
    const isModpack = classification === 'MODPACK';
    const hasTitle = metaData.title && metaData.title.trim().length > 0;
    const hasTags = metaData.tags.length > 0;
    const hasSummary = metaData.summary && metaData.summary.length >= 10 && metaData.summary.length <= 250;
    const hasValidDescription = !metaData.description || metaData.description.length <= 50000;
    const hasVersion = (modData?.versions?.length || 0) > 0;

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

    const isModalOpen = roleModalOpen || showSlugPrompt || showPublishConfirm || showCardPreview || editingVersion !== null || memberToRemove !== null || galleryCropImage !== null;

    useEffect(() => {
        if (isModalOpen) {
            document.body.style.overflow = 'hidden';
        } else {
            document.body.style.overflow = '';
        }
        return () => {
            document.body.style.overflow = '';
        };
    }, [isModalOpen]);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            const target = e.target as HTMLElement;
            if (!target.closest('.modtale-dropdown-container')) {
                setInviteRoleDropdownOpen(false);
                setMemberRoleDropdownOpen(null);
                setUserSearchResults([]);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const markDirty = () => !readOnly && !isDirty && setIsDirty(true);
    const checkRepoUrl = useCallback((url: string) => {
        if (!url) { setRepoValid(true); return true; }
        const isValid = /^https:\/\/(github\.com|gitlab\.com|codeberg\.org)\/[\w.-]+\/[\w.-]+$/.test(url);
        setRepoValid(isValid);
        return isValid;
    }, []);

    const hasGithub = currentUser?.connectedAccounts?.some(a => a.provider === 'github') || false;
    const hasGitlab = currentUser?.connectedAccounts?.some(a => a.provider === 'gitlab') || false;
    const [provider, setProvider] = useState<'github' | 'gitlab'>(hasGithub ? 'github' : (hasGitlab ? 'gitlab' : 'github'));

    useEffect(() => {
        let isMounted = true;
        if (modData?.id && modData.id !== 'new-project' && !readOnly) {
            api.get(`/projects/${modData.id}`).then(res => {
                if (isMounted && res.data) {
                    setModData(prev => {
                        if (!prev) return res.data;
                        const merged = { ...prev };
                        if (res.data.versions && prev.versions?.length !== res.data.versions.length) {
                            merged.versions = res.data.versions;
                        }
                        if (res.data.teamMembers) merged.teamMembers = res.data.teamMembers;
                        if (res.data.teamInvites) merged.teamInvites = res.data.teamInvites;
                        if (res.data.projectRoles) merged.projectRoles = res.data.projectRoles;
                        return merged;
                    });
                }
            }).catch(() => {});
        }
        return () => { isMounted = false; };
    }, [modData?.id, readOnly, setModData]);

    useEffect(() => {
        if (!readOnly && currentUser) {
            api.get('/modjams').then(res => {
                const active = (res.data || []).filter((j: any) => j.status === 'ACTIVE');
                setActiveJams(active);
            }).catch(() => {});
        }
    }, [readOnly, currentUser]);

    const fetchRepos = useCallback(() => {
        if (readOnly || manualRepo) return;
        if ((provider === 'github' && !hasGithub) || (provider === 'gitlab' && !hasGitlab)) return;

        setLoadingRepos(true);
        const endpoint = provider === 'gitlab' ? '/user/repos/gitlab' : '/user/repos/github';

        api.get(endpoint)
            .then(res => setRepos(res.data || []))
            .catch(e => {
                console.error(e);
                const errorMsg = typeof e.response?.data === 'string'
                    ? e.response.data
                    : e.response?.data?.message || 'Failed to fetch repositories.';
                onShowStatus('error', 'Repository Error', errorMsg);
            })
            .finally(() => setLoadingRepos(false));
    }, [provider, hasGithub, hasGitlab, readOnly, manualRepo]);

    useEffect(() => { checkRepoUrl(metaData.repositoryUrl || ''); }, [metaData.repositoryUrl, checkRepoUrl]);

    useEffect(() => {
        if (!manualRepo && !readOnly) {
            fetchRepos();
        }
    }, [classification, provider, manualRepo, readOnly, fetchRepos]);

    useEffect(() => {
        if (metaData.license && !LICENSES.some(l => l.id === metaData.license)) {
            setIsCustomLicense(true);
        }
    }, [metaData.license]);

    useEffect(() => {
        const handleClick = (e: MouseEvent) => {
            if (repoDropdownRef.current && !repoDropdownRef.current.contains(e.target as Node)) {
                setRepoDropdownOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, []);

    useEffect(() => {
        if (!inviteUsername || inviteUsername.length < 2 || inviteUserId) {
            setUserSearchResults([]);
            return;
        }
        const delayDebounceFn = setTimeout(async () => {
            setIsSearchingUsers(true);
            try {
                const res = await api.get(`/users/search?query=${inviteUsername}`);
                setUserSearchResults(res.data);
            } catch (e) { /* ignore */ }
            finally { setIsSearchingUsers(false); }
        }, 300);
        return () => clearTimeout(delayDebounceFn);
    }, [inviteUsername, inviteUserId]);

    useEffect(() => {
        if (!modData?.modjamIds?.length) return;

        const missing = modData.modjamIds.filter(id => !jamMeta[id] && !fetchedJamMeta.current.has(id));
        if (!missing.length) return;

        missing.forEach(id => fetchedJamMeta.current.add(id));

        const fetchJams = async () => {
            try {
                const jamsRes = await api.get('/modjams');
                const allJams = jamsRes.data;
                const newMeta: Record<string, { title: string, slug: string, isWinner: boolean, imageUrl?: string }> = {};

                await Promise.all(missing.map(async (id) => {
                    const jam = allJams.find((j: any) => j.id === id);
                    if (jam) {
                        let isWinner = false;
                        try {
                            if (modData?.id) {
                                const subsRes = await api.get(`/modjams/${jam.slug}/submissions`);
                                const mySub = subsRes.data.find((s: any) => s.projectId === modData.id);
                                if (mySub && mySub.rank === 1) {
                                    isWinner = true;
                                }
                            }
                        } catch (e) {}
                        newMeta[id] = { title: jam.title, slug: jam.slug, isWinner, imageUrl: jam.imageUrl };
                    } else {
                        newMeta[id] = { title: `Jam ${id.substring(0,8)}`, slug: id, isWinner: false, imageUrl: undefined };
                    }
                }));

                setJamMeta(prev => ({ ...prev, ...newMeta }));
            } catch (e) {
                const newMeta: Record<string, { title: string, slug: string, isWinner: boolean, imageUrl?: string }> = {};
                missing.forEach(id => {
                    newMeta[id] = { title: `Jam ${id.substring(0,8)}`, slug: id, isWinner: false, imageUrl: undefined };
                });
                setJamMeta(prev => ({ ...prev, ...newMeta }));
            }
        };
        fetchJams();
    }, [modData?.modjamIds, modData?.id]);

    const toggleTag = (tag: string) => {
        if (readOnly) return;
        markDirty();
        setMetaData(prev => ({...prev, tags: prev.tags.includes(tag) ? prev.tags.filter(t => t !== tag) : [...prev.tags, tag]}));
    };

    const handleCopyId = () => {
        if (modData?.id) {
            navigator.clipboard.writeText(modData.id);
            setIdCopied(true);
            setTimeout(() => setIdCopied(false), 2000);
        }
    };

    const hasProjectPermission = (perm: string) => {
        if (!modData || !currentUser) return false;
        if (modData.isOwner) return true;
        if (modData.authorId === currentUser.id) return true;

        const member = modData.teamMembers?.find(m => m.userId === currentUser.id);
        if (member && member.roleId && modData.projectRoles) {
            const role = modData.projectRoles.find(r => r.id === member.roleId);
            if (role && role.permissions && role.permissions.includes(perm)) return true;
        }
        return false;
    };

    const canManageRoles = hasProjectPermission('PROJECT_MEMBER_EDIT_ROLE');
    const canInvite = hasProjectPermission('PROJECT_TEAM_INVITE');
    const canRemove = hasProjectPermission('PROJECT_TEAM_REMOVE');

    const handleInvite = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!modData?.id || !inviteUserId || !inviteRoleId) return;
        setIsInviting(true);
        try {
            await api.post(`/projects/${modData.id}/invite`, { userId: inviteUserId, roleId: inviteRoleId });

            const selectedRole = modData.projectRoles?.find(r => r.id === inviteRoleId);
            const userMatch = userSearchResults.find(u => u.id === inviteUserId);

            if (userMatch) {
                setModData(prev => prev ? ({
                    ...prev,
                    teamInvites: [...(prev.teamInvites || []), {
                        userId: inviteUserId,
                        roleId: inviteRoleId,
                        username: userMatch.username,
                        avatarUrl: userMatch.avatarUrl
                    }]
                }) : null);
            }

            setInviteUsername('');
            setInviteUserId('');
            setUserSearchResults([]);
            onShowStatus('success', 'Invited', `Invitation sent successfully.`);
        } catch (e: any) {
            let errorMsg = typeof e.response?.data === 'string'
                ? e.response.data
                : e.response?.data?.message || 'Failed to invite user.';
            errorMsg = errorMsg.replace(/^\d{3} [A-Z_]+ "(.*)"$/, '$1');
            onShowStatus('error', 'Error', errorMsg);
        } finally {
            setIsInviting(false);
        }
    };

    const handleCancelInvite = async (userId: string) => {
        if (!modData?.id) return;
        try {
            await api.delete(`/projects/${modData.id}/invites/${userId}`);
            setModData(prev => prev ? ({
                ...prev,
                teamInvites: (prev.teamInvites || []).filter(m => m.userId !== userId)
            }) : null);
        } catch (e: any) {
            onShowStatus('error', 'Error', e.response?.data || "Could not cancel invite.");
        }
    };

    const confirmRemoveContributor = async () => {
        if (!modData?.id || !memberToRemove) return;
        try {
            await api.delete(`/projects/${modData.id}/contributors/${memberToRemove}`);
            setModData(prev => {
                if (!prev) return null;
                return {
                    ...prev,
                    teamMembers: (prev.teamMembers || []).filter(m => m.userId !== memberToRemove),
                    teamInvites: (prev.teamInvites || []).filter(m => m.userId !== memberToRemove)
                };
            });
            onShowStatus('success', 'Removed', `Member removed from project.`);
        } catch (e: any) {
            let errorMsg = typeof e.response?.data === 'string'
                ? e.response.data
                : e.response?.data?.message || e.message || 'Failed to remove contributor.';
            errorMsg = errorMsg.replace(/^\d{3} [A-Z_]+ "(.*)"$/, '$1');
            onShowStatus('error', 'Error', errorMsg);
        } finally {
            setMemberToRemove(null);
        }
    };

    const handleSaveRole = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!modData?.id || !editingRole?.name || !editingRole?.color) return;

        try {
            let updatedMod;
            if (editingRole.id) {
                const res = await api.put(`/projects/${modData.id}/roles/${editingRole.id}`, editingRole);
                updatedMod = res.data;
            } else {
                const res = await api.post(`/projects/${modData.id}/roles`, editingRole);
                updatedMod = res.data;
            }

            setModData(prev => {
                if (!prev) return updatedMod;
                return { ...prev, projectRoles: updatedMod.projectRoles };
            });

            setRoleModalOpen(false);
            setEditingRole(null);
            onShowStatus('success', 'Saved', 'Role saved successfully.');
        } catch (err: any) {
            onShowStatus('error', 'Error', err.response?.data || "Failed to save role.");
        }
    };

    const handleDeleteRole = async (roleId: string) => {
        if (!modData?.id) return;
        try {
            const res = await api.delete(`/projects/${modData.id}/roles/${roleId}`);
            setModData(prev => {
                if (!prev) return res.data;
                return { ...prev, projectRoles: res.data.projectRoles };
            });
            onShowStatus('success', 'Deleted', 'Role deleted successfully.');
        } catch (err: any) {
            onShowStatus('error', 'Delete Failed', err.response?.data || "Cannot delete role.");
        }
    };

    const handleRoleUpdate = async (userId: string, newRoleId: string) => {
        if (!modData?.id) return;
        try {
            await api.put(`/projects/${modData.id}/contributors/${userId}`, { roleId: newRoleId });

            setModData(prev => {
                if (!prev) return null;
                const members = [...(prev.teamMembers || [])];
                const idx = members.findIndex(m => m.userId === userId);
                if (idx > -1) {
                    members[idx] = { ...members[idx], roleId: newRoleId };
                }
                return { ...prev, teamMembers: members };
            });

            onShowStatus('success', 'Updated', 'Member role updated.');
        } catch (err: any) {
            onShowStatus('error', 'Update Failed', err.response?.data || "Failed to update role.");
        }
    };

    const validateSlugFormat = (val: string) => {
        if (!val) return null;
        const slugRegex = /^[a-z0-9](?:[a-z0-9-]{1,48}[a-z0-9])?$/;
        if (!slugRegex.test(val)) return "Must be 3-50 chars, lowercase alphanumeric, no start/end dash.";
        return null;
    }

    const handleSlugChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        markDirty();
        const val = e.target.value;
        setMetaData({...metaData, slug: val});
        setSlugError(validateSlugFormat(val));
    };

    const getUrlPrefix = () => {
        if (classification === 'MODPACK') return 'modtale.net/modpack/';
        if (classification === 'SAVE') return 'modtale.net/world/';
        return 'modtale.net/mod/';
    }

    const getProjectLink = () => {
        if (!modData?.id) return '#';
        const slug = metaData.slug || modData.slug || modData.id;
        if (classification === 'MODPACK') return `/modpack/${slug}`;
        if (classification === 'SAVE') return `/world/${slug}`;
        return `/mod/${slug}`;
    };

    const onGalleryDrop = useCallback((acceptedFiles: File[]) => {
        if (acceptedFiles[0] && !readOnly) {
            setGalleryCropImage(URL.createObjectURL(acceptedFiles[0]));
        }
    }, [readOnly]);

    const { getRootProps: getGalleryRootProps, getInputProps: getGalleryInputProps, isDragActive: isGalleryDragActive } = useDropzone({
        onDrop: onGalleryDrop,
        maxFiles: 1,
        accept: { 'image/*': ['.png', '.jpg', '.jpeg', '.webp'] },
        disabled: readOnly
    });

    const filteredRepos = repos.filter(r => (r.name || '').toLowerCase().includes(repoSearch.toLowerCase()));

    const previewMod: Mod = {
        ...(modData || {} as Mod),
        id: modData?.id || 'new-project',
        title: metaData.title || 'Untitled Project',
        description: metaData.summary || 'No summary provided.',
        authorId: modData?.authorId || currentUser?.id || '',
        author: modData?.author || currentUser?.username || 'You',
        imageUrl: metaData.iconPreview || modData?.imageUrl || '',
        bannerUrl: bannerPreview || modData?.bannerUrl || '',
        classification: (typeof classification === 'string' ? classification : 'PLUGIN') as any,
        updatedAt: new Date().toISOString(),
        downloadCount: modData?.downloadCount || 0,
        favoriteCount: modData?.favoriteCount || 0,
        sizeBytes: modData?.sizeBytes || 0,
        modIds: modData?.modIds || [],
        modjamIds: modData?.modjamIds?.filter(id => !((modData as any).hiddenModjamIds || []).includes(id)) || [],
        childProjectIds: modData?.childProjectIds || [],
        versions: modData?.versions || [],
        comments: [],
        galleryImages: [],
        teamMembers: modData?.teamMembers || [],
        teamInvites: modData?.teamInvites || []
    };

    const handleSubmitClick = () => {
        if (!metaData.slug && !modData?.slug) {
            setShowSlugPrompt(true);
        } else {
            setShowPublishConfirm(true);
        }
    };

    const handleSaveClick = async () => {
        try {
            const success = await handleSave(false);
            if (success) {
                setIsDirty(false);
            }
        } catch (e: any) {
            let errorMsg = typeof e.response?.data === 'string'
                ? e.response.data
                : e.response?.data?.message || 'Failed to save project.';
            errorMsg = errorMsg.replace(/^\d{3} [A-Z_]+ "(.*)"$/, '$1');
            onShowStatus('error', 'Save Failed', errorMsg);
        }
    };

    const handleConfirmSlug = async (useCurrent: boolean) => {
        setSlugError(null);
        if (!useCurrent && metaData.slug) {
            try {
                const success = await handleSave(true);
                if (success) {
                    setShowSlugPrompt(false);
                    setIsDirty(false);
                    setShowPublishConfirm(true);
                } else {
                    setSlugError("Unable to save slug. It may be taken.");
                }
            } catch (e: any) {
                let errorMsg = typeof e.response?.data === 'string'
                    ? e.response.data
                    : e.response?.data?.message || 'Failed to save slug.';
                errorMsg = errorMsg.replace(/^\d{3} [A-Z_]+ "(.*)"$/, '$1');
                setSlugError(errorMsg);
            }
        } else {
            setShowSlugPrompt(false);
            setShowPublishConfirm(true);
        }
    };

    const executePublish = async () => {
        setJamSubmitError(null);
        if (selectedPublishJamId && modData?.id) {
            setIsPublishingJam(true);
            try {
                await api.post(`/modjams/${selectedPublishJamId}/submit`, { projectId: modData.id });
            } catch (e: any) {
                let errorMsg = typeof e.response?.data === 'string'
                    ? e.response.data
                    : (e.response?.data?.message || 'Failed to submit to jam');
                errorMsg = errorMsg.replace(/^\d{3} [A-Z_]+ "(.*)"$/, '$1');

                setJamSubmitError(errorMsg);
                setIsPublishingJam(false);
                return;
            }
        }
        setIsPublishingJam(false);
        setShowPublishConfirm(false);
        if (handlePublish) handlePublish();
    };

    const handleEditVersion = (version: ProjectVersion) => {
        const formattedDependencies = version.dependencies?.map(d =>
            `${d.modId}:${d.versionNumber}${d.isOptional ? ':optional' : ''}`
        ) || [];

        setEditVersionData({
            versionNumber: version.versionNumber,
            gameVersions: version.gameVersions || [],
            changelog: version.changelog || '',
            file: null,
            channel: version.channel || 'RELEASE',
            modIds: formattedDependencies,
            dependencies: []
        });
        setEditingVersion(version);
    };

    const saveVersionUpdates = async () => {
        if (!modData || !editingVersion || !editVersionData) return;
        setIsSavingVersion(true);
        try {
            await api.put(`/projects/${modData.id}/versions/${editingVersion.id}`, {
                gameVersions: editVersionData.gameVersions,
                changelog: editVersionData.changelog,
                channel: editVersionData.channel,
                modIds: editVersionData.modIds
            });

            const res = await api.get(`/projects/${modData.id}`);
            setModData(res.data);
            setEditingVersion(null);
            setEditVersionData(null);
            onShowStatus('success', 'Updated', 'Version metadata updated successfully.');
        } catch (e: any) {
            let errorMsg = typeof e.response?.data === 'string'
                ? e.response.data
                : e.response?.data?.message || 'Failed to update version.';
            errorMsg = errorMsg.replace(/^\d{3} [A-Z_]+ "(.*)"$/, '$1');
            onShowStatus('error', 'Update Failed', errorMsg);
        } finally {
            setIsSavingVersion(false);
        }
    };

    const availableTabs = [
        {id: 'details', icon: FileText, label: 'Details'},
        {id: 'files', icon: UploadCloud, label: `Files (${modData?.versions?.length||0})`},
        {id: 'gallery', icon: ImageIcon, label: `Gallery (${modData?.galleryImages?.length||0})`},
        {id: 'team', icon: Users, label: `Team (${(modData?.teamMembers?.length||0) + 1})`},
        {id: 'settings', icon: Settings, label: 'Settings'}
    ];

    if (modData?.hmWikiEnabled) {
        availableTabs.push({id: 'wiki', icon: BookOpen, label: 'Wiki Preview'});
    }

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
                document.body
            )}

            {modData?.status === 'PENDING' && (
                <div className="bg-blue-500/10 border-b border-blue-500/20 backdrop-blur-sm sticky top-0 z-50">
                    <div className="max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 py-3 flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded-full bg-blue-500/20 flex items-center justify-center">
                                <Clock className="w-5 h-5 text-blue-500" />
                            </div>
                            <div>
                                <div className="font-bold text-blue-500 leading-tight">Pending Verification</div>
                                <div className="text-[10px] text-blue-500/80 font-bold uppercase tracking-wide">Project is read-only</div>
                            </div>
                        </div>
                        {handleRevert && (
                            <button onClick={handleRevert} disabled={isLoading} className="bg-blue-500 hover:bg-blue-600 text-white w-40 h-9 rounded-lg text-xs font-bold shadow-lg transition-all flex items-center justify-center gap-2">
                                {isLoading ? <Spinner className="w-3 h-3 text-white"/> : <Edit2 className="w-3 h-3"/>}
                                Revert to Draft
                            </button>
                        )}
                    </div>
                </div>
            )}

            {modData?.status === 'ARCHIVED' && (
                <div className="bg-slate-800 border-b border-slate-700 backdrop-blur-sm sticky top-0 z-50">
                    <div className="max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 py-3 flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded-full bg-slate-700 flex items-center justify-center">
                                <Archive className="w-5 h-5 text-slate-400" />
                            </div>
                            <div>
                                <div className="font-bold text-slate-300 leading-tight">Archived</div>
                                <div className="text-[10px] text-slate-500 font-bold uppercase tracking-wide">Project is read-only</div>
                            </div>
                        </div>
                        {handleRestore && (
                            <button onClick={handleRestore} disabled={isLoading} className="bg-slate-700 hover:bg-slate-600 text-white w-40 h-9 rounded-lg text-xs font-bold shadow-lg transition-all flex items-center justify-center gap-2">
                                {isLoading ? <Spinner className="w-3 h-3 text-white"/> : <RefreshCw className="w-3 h-3"/>}
                                Restore Project
                            </button>
                        )}
                    </div>
                </div>
            )}

            {memberToRemove && createPortal(
                <StatusModal
                    type="warning"
                    title="Remove Member?"
                    message="Are you sure you want to remove this member from the project team?"
                    actionLabel="Remove"
                    onAction={confirmRemoveContributor}
                    secondaryLabel="Cancel"
                    onClose={() => setMemberToRemove(null)}
                />,
                document.body
            )}

            {roleModalOpen && editingRole && createPortal(
                <div className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-in fade-in zoom-in-95 duration-200">
                    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-3xl w-full max-w-3xl shadow-2xl flex flex-col max-h-[85vh] overflow-hidden">
                        <div className="flex justify-between items-center p-6 border-b border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-white/5">
                            <div>
                                <h3 className="text-xl font-black text-slate-900 dark:text-white">{editingRole.id ? 'Edit Role' : 'Create Role'}</h3>
                                <p className="text-xs text-slate-500">Configure permissions for this project role.</p>
                            </div>
                            <button onClick={() => setRoleModalOpen(false)} className="p-2 text-slate-400 hover:text-slate-700 dark:hover:text-white transition-colors bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl"><X className="w-5 h-5" /></button>
                        </div>

                        <form onSubmit={handleSaveRole} className="flex flex-col flex-1 overflow-hidden">
                            <div className="flex-1 overflow-y-auto custom-scrollbar p-6 space-y-6">
                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1.5 ml-1">Role Name</label>
                                        <input
                                            type="text"
                                            value={editingRole.name || ''}
                                            onChange={e => setEditingRole({...editingRole, name: e.target.value})}
                                            className="w-full bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 outline-none focus:ring-2 focus:ring-modtale-accent dark:text-white font-medium"
                                            required
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1.5 ml-1">Role Color</label>
                                        <div className="flex items-center gap-3">
                                            <input
                                                type="color"
                                                value={editingRole.color || '#3b82f6'}
                                                onChange={e => setEditingRole({...editingRole, color: e.target.value})}
                                                className="w-12 h-12 p-1 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl cursor-pointer"
                                            />
                                            <input
                                                type="text"
                                                value={editingRole.color || '#3b82f6'}
                                                onChange={e => setEditingRole({...editingRole, color: e.target.value})}
                                                className="flex-1 bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-3 outline-none focus:ring-2 focus:ring-modtale-accent dark:text-white font-mono text-sm"
                                                pattern="^#[0-9A-Fa-f]{6}$"
                                            />
                                        </div>
                                    </div>
                                </div>

                                <div className="space-y-4">
                                    <h4 className="font-bold text-slate-900 dark:text-white text-sm border-b border-slate-200 dark:border-white/10 pb-2">Permissions</h4>
                                    <PermissionSelector
                                        groups={PROJECT_PERMISSION_GROUPS}
                                        selectedPermissions={editingRole.permissions || []}
                                        onChange={(perms) => setEditingRole({ ...editingRole, permissions: perms })}
                                        variant="card"
                                    />
                                </div>
                            </div>

                            <div className="flex justify-end gap-3 p-6 border-t border-slate-200 dark:border-white/10 bg-slate-50 dark:bg-white/5">
                                <button type="button" onClick={() => setRoleModalOpen(false)} className="px-5 py-2.5 font-bold text-slate-500 hover:text-slate-800 dark:hover:text-white transition-colors bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl">Cancel</button>
                                <button type="submit" className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 py-2.5 rounded-xl font-bold shadow-lg transition-all active:scale-95 flex items-center gap-2">Save Role</button>
                            </div>
                        </form>
                    </div>
                </div>,
                document.body
            )}

            {editingVersion && editVersionData && createPortal(
                <div className="fixed inset-0 z-[300] bg-black/60 backdrop-blur-md flex items-center justify-center p-4 animate-in fade-in duration-200">
                    <div className="bg-white dark:bg-slate-900 w-full max-w-2xl rounded-3xl shadow-2xl overflow-hidden border border-slate-200 dark:border-white/10 animate-in zoom-in-95 duration-200 max-h-[90vh] flex flex-col">
                        <div className="p-6 border-b border-slate-200 dark:border-white/5 flex justify-between items-center bg-slate-50 dark:bg-white/5">
                            <div>
                                <h2 className="text-xl font-black text-slate-900 dark:text-white">Edit Version Metadata</h2>
                                <p className="text-xs text-slate-500 font-mono mt-1">Editing {editingVersion.versionNumber}</p>
                            </div>
                            <button onClick={() => setEditingVersion(null)} className="text-slate-400 hover:text-slate-600 dark:hover:text-white transition-colors">
                                <X className="w-6 h-6" />
                            </button>
                        </div>

                        <div className="p-6 overflow-y-auto custom-scrollbar">
                            <VersionFields
                                data={editVersionData}
                                onChange={setEditVersionData}
                                isModpack={classification === 'MODPACK'}
                                projectType={typeof classification === 'string' ? classification : 'PLUGIN'}
                                disabled={isSavingVersion}
                                hideFilePicker={true}
                            />
                        </div>

                        <div className="p-6 border-t border-slate-200 dark:border-white/5 bg-slate-50 dark:bg-white/5 flex justify-end gap-3">
                            <button
                                onClick={() => setEditingVersion(null)}
                                className="px-6 py-3 rounded-xl font-bold text-sm bg-slate-200 dark:bg-white/10 text-slate-600 dark:text-slate-300 hover:bg-slate-300 dark:hover:bg-white/20 transition-all"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={saveVersionUpdates}
                                disabled={isSavingVersion}
                                className="px-6 py-3 rounded-xl font-bold text-sm bg-modtale-accent text-white hover:bg-modtale-accentHover shadow-lg transition-all flex items-center gap-2"
                            >
                                {isSavingVersion ? <Spinner className="w-4 h-4 text-white" /> : <Save className="w-4 h-4" />}
                                Save Changes
                            </button>
                        </div>
                    </div>
                </div>,
                document.body
            )}

            {showSlugPrompt && createPortal(
                <div className="fixed inset-0 z-[300] bg-black/60 backdrop-blur-md flex items-center justify-center p-4 animate-in fade-in duration-200">
                    <div className="bg-white dark:bg-slate-900 w-full max-w-xl rounded-3xl shadow-2xl overflow-hidden border border-slate-200 dark:border-white/10 animate-in zoom-in-95 duration-200">
                        <div className="p-8">
                            <div className="flex items-center justify-between mb-6">
                                <button onClick={() => setShowSlugPrompt(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-white transition-colors">
                                    <X className="w-6 h-6" />
                                </button>
                            </div>

                            <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Claim your custom URL</h2>
                            <p className="text-slate-500 dark:text-slate-400 text-sm font-medium leading-relaxed mb-8">
                                Custom slugs make your project easier to share.
                            </p>

                            <div className="space-y-4">
                                <div className="flex flex-col gap-2">
                                    <label className="text-[10px] font-black uppercase text-slate-400 tracking-widest px-1">Project URL Slug</label>
                                    <div className={`flex items-center w-full bg-slate-50 dark:bg-black/20 border rounded-2xl overflow-hidden focus-within:ring-2 focus-within:ring-modtale-accent transition-all ${slugError ? 'border-red-500' : 'border-slate-200 dark:border-white/10'}`}>
                                        <div className="px-4 py-3 bg-slate-100 dark:bg-white/5 border-r border-slate-200 dark:border-white/10 text-slate-400 text-xs font-mono whitespace-nowrap select-none">{getUrlPrefix()}</div>
                                        <input
                                            value={metaData.slug || ''}
                                            onChange={handleSlugChange}
                                            className={`flex-1 bg-transparent border-none px-4 py-3 text-sm font-mono text-slate-900 dark:text-white focus:outline-none placeholder:text-slate-400 ${slugError ? 'text-red-500' : ''}`}
                                            placeholder={createSlug(metaData.title, modData?.id || 'id')}
                                        />
                                    </div>
                                    {slugError && <p className="text-[10px] text-red-500 font-bold px-1">{slugError}</p>}
                                </div>
                            </div>

                            <div className="flex flex-col sm:flex-row items-center gap-3 mt-10">
                                <button
                                    onClick={() => handleConfirmSlug(false)}
                                    disabled={!!slugError || !metaData.slug}
                                    className="w-full sm:flex-1 h-14 bg-modtale-accent hover:bg-modtale-accentHover disabled:bg-slate-200 dark:disabled:bg-slate-800 disabled:text-slate-400 text-white rounded-2xl font-black text-base shadow-lg shadow-modtale-accent/20 transition-all active:scale-95 flex items-center justify-center gap-2"
                                >
                                    <Save className="w-5 h-5" /> Save & Continue
                                </button>
                                <button
                                    onClick={() => handleConfirmSlug(true)}
                                    className="w-full sm:w-auto px-8 h-14 bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-300 rounded-2xl font-bold text-sm hover:bg-slate-200 dark:hover:bg-white/10 transition-all"
                                >
                                    Use Default
                                </button>
                            </div>
                        </div>
                    </div>
                </div>,
                document.body
            )}

            {showPublishConfirm && handlePublish && (
                <div className="fixed inset-0 z-[400] flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in duration-200">
                    <div className="bg-white dark:bg-slate-900 w-full max-w-lg rounded-3xl shadow-2xl overflow-hidden border border-slate-200 dark:border-white/10 flex flex-col">
                        <div className="p-8 pb-6">
                            <h2 className="text-2xl font-black text-slate-900 dark:text-white mb-2">Ready to publish?</h2>
                            <p className="text-slate-500 text-sm font-medium mb-6">Your project will be submitted for verification. Once approved, it will be live on Modtale.</p>

                            {activeJams.length > 0 && (
                                <div className="bg-slate-50 dark:bg-slate-800/50 rounded-2xl border border-slate-200 dark:border-white/10 p-5 mb-2">
                                    <h3 className="text-sm font-bold text-slate-900 dark:text-white mb-3 flex items-center gap-2">
                                        <Trophy className="w-4 h-4 text-amber-500" /> Enter a Modjam (Optional)
                                    </h3>

                                    <JamSelectDropdown
                                        value={selectedPublishJamId}
                                        options={activeJams}
                                        onChange={(v) => { setSelectedPublishJamId(v); setAgreedToPublishJamRules(false); setJamSubmitError(null); }}
                                    />

                                    {(() => {
                                        const j = activeJams.find(jam => jam.id === selectedPublishJamId);
                                        if (!j) return null;
                                        const hides = Boolean(j.hideSubmissions);
                                        const rules = Boolean(j.rules?.trim().length > 0);

                                        return (
                                            <div className="mt-4 space-y-4 animate-in fade-in slide-in-from-top-2">
                                                {hides && (
                                                    <div className="bg-amber-500/10 border border-amber-500/20 rounded-xl p-3 flex items-start gap-3">
                                                        <AlertCircle className="w-5 h-5 text-amber-500 shrink-0 mt-0.5" />
                                                        <div>
                                                            <h4 className="text-sm font-bold text-amber-700 dark:text-amber-400">Secret Submissions Active</h4>
                                                            <p className="text-xs text-amber-600/80 dark:text-amber-500/80 mt-1">
                                                                This jam hides entries until voting starts. Your project will remain private until <strong className="text-amber-700 dark:text-amber-400">{new Date(j.votingEndDate).toLocaleDateString()}</strong>.
                                                            </p>
                                                        </div>
                                                    </div>
                                                )}

                                                {rules && (
                                                    <div className="flex items-center gap-3 cursor-pointer group" onClick={(e) => { e.preventDefault(); setAgreedToPublishJamRules(!agreedToPublishJamRules); }}>
                                                        <div className={`w-5 h-5 rounded border flex items-center justify-center shrink-0 transition-all ${agreedToPublishJamRules ? 'bg-modtale-accent border-modtale-accent text-white' : 'bg-white dark:bg-slate-900 border-slate-300 dark:border-slate-600 group-hover:border-modtale-accent'}`}>
                                                            {agreedToPublishJamRules && <Check className="w-3.5 h-3.5" strokeWidth={3} />}
                                                        </div>
                                                        <span className="text-xs font-bold text-slate-600 dark:text-slate-400 select-none">
                                                            I agree to the <a href={`/jam/${j.slug}/rules`} target="_blank" rel="noopener noreferrer" onClick={e => e.stopPropagation()} className="text-modtale-accent hover:underline">Jam Rules</a>.
                                                        </span>
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })()}
                                </div>
                            )}

                            {jamSubmitError && (
                                <div className="bg-red-500/10 border border-red-500/20 rounded-xl p-4 mt-4 flex items-start gap-3 animate-in fade-in zoom-in-95">
                                    <AlertCircle className="w-5 h-5 text-red-500 shrink-0 mt-0.5" />
                                    <div>
                                        <h4 className="text-sm font-bold text-red-700 dark:text-red-400">Jam Submission Failed</h4>
                                        <p className="text-xs text-red-600/80 dark:text-red-500/80 mt-1">{jamSubmitError}</p>
                                        <p className="text-[10px] text-red-600/60 dark:text-red-500/60 mt-2 font-bold uppercase tracking-widest">Fix the issue, or choose "-- Do not enter a jam --"</p>
                                    </div>
                                </div>
                            )}
                        </div>

                        <div className="p-6 border-t border-slate-200 dark:border-white/5 bg-slate-50 dark:bg-slate-950/50 flex justify-end gap-3">
                            <button
                                onClick={() => setShowPublishConfirm(false)}
                                disabled={isPublishingJam}
                                className="px-6 py-3 rounded-xl font-bold text-sm bg-slate-200 dark:bg-white/10 text-slate-600 dark:text-slate-300 hover:bg-slate-300 dark:hover:bg-white/20 transition-all"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={executePublish}
                                disabled={isPublishingJam || (() => {
                                    const j = activeJams.find(jam => jam.id === selectedPublishJamId);
                                    return j && Boolean(j.rules?.trim().length > 0) && !agreedToPublishJamRules;
                                })()}
                                className="px-8 py-3 rounded-xl font-black text-sm bg-modtale-accent hover:bg-modtale-accentHover text-white shadow-lg shadow-modtale-accent/20 transition-all flex items-center gap-2 disabled:opacity-50"
                            >
                                {isPublishingJam ? <Spinner className="w-4 h-4" fullScreen={false} /> : <UploadCloud className="w-4 h-4" />}
                                {selectedPublishJamId ? 'Submit to Jam & Publish' : 'Publish Project'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {showCardPreview && createPortal(
                <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200" onClick={() => setShowCardPreview(false)}>
                    <div className="relative w-full max-w-[380px] flex flex-col items-center" onClick={e => e.stopPropagation()}>
                        <div
                            className="cursor-default pointer-events-none select-none shadow-2xl w-full rounded-2xl overflow-hidden scale-110 sm:scale-125 md:scale-150 transform-gpu origin-center bg-white dark:bg-modtale-card"
                            onClickCapture={(e) => { e.preventDefault(); e.stopPropagation(); }}
                        >
                            <ModCard
                                mod={previewMod}
                                isFavorite={false}
                                onToggleFavorite={() => {}}
                                isLoggedIn={false}
                            />
                        </div>
                    </div>
                </div>,
                document.body
            )}

            <ProjectLayout
                isEditing={!readOnly}
                bannerUrl={bannerPreview || modData?.bannerUrl}
                iconUrl={metaData.iconPreview || modData?.imageUrl}
                onBannerUpload={(f, p) => { markDirty(); setBannerFile(f); setBannerPreview(p); }}
                onIconUpload={(f, p) => { markDirty(); setMetaData(m => ({...m, iconFile: f, iconPreview: p})); }}
                headerContent={
                    <div>
                        <input value={metaData.title} disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')} onChange={e => { markDirty(); setMetaData({...metaData, title: e.target.value}); }} className="text-4xl md:text-5xl font-black text-slate-900 dark:text-white bg-transparent border-b border-transparent outline-none pb-1 placeholder:text-slate-400 w-full hover:border-slate-300 dark:hover:border-white/20 focus:border-modtale-accent" placeholder="Project Title"/>
                        <input value={metaData.summary} disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')} onChange={e => { markDirty(); setMetaData({...metaData, summary: e.target.value}); }} className="text-lg text-slate-600 dark:text-slate-300 font-medium bg-transparent border-b border-transparent outline-none pb-1 placeholder:text-slate-400 w-full mt-2 hover:border-slate-300 dark:hover:border-white/20 focus:border-modtale-accent" placeholder="Short summary..."/>
                    </div>
                }
                headerActions={
                    <div className="flex flex-col items-end gap-2">
                        {isDirty && <div className="text-[10px] font-bold text-amber-500 animate-pulse uppercase tracking-widest bg-amber-500/10 px-2 py-1 rounded">Unsaved Changes</div>}
                        <div className="flex items-center gap-3">
                            <Link to={getProjectLink()} target="_blank" className="h-10 px-3 rounded-xl border border-slate-200 dark:border-white/10 bg-slate-100 dark:bg-white/5 text-slate-500 hover:text-slate-900 dark:hover:text-white flex items-center justify-center transition-colors" title="View Page">
                                <ExternalLink className="w-5 h-5" />
                            </Link>

                            {!readOnly && (
                                <button onClick={handleSaveClick} disabled={isLoading} className="h-10 px-5 rounded-xl border border-slate-200 dark:border-white/10 bg-slate-100 dark:bg-white/5 font-bold flex items-center gap-2 hover:bg-slate-200">{isLoading ? <Spinner className="w-4 h-4"/> : <Save className="w-4 h-4" />} Save</button>
                            )}

                            {!readOnly && handlePublish && (
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
                                                    <span className={`text-xs font-bold ${req.met ? 'text-slate-900 dark:text-white' : 'text-slate-50'}`}>{req.label}</span>
                                                </div>
                                            ))}
                                        </div>
                                        <div className="absolute top-full right-8 -mt-1.5 border-8 border-transparent border-t-white dark:border-t-slate-900" />
                                    </div>

                                    <button
                                        onClick={handleSubmitClick}
                                        disabled={isLoading || !isPublishable}
                                        className="h-10 bg-green-500 hover:bg-green-600 disabled:bg-slate-200 dark:disabled:bg-slate-800 disabled:text-slate-400 disabled:shadow-none text-white px-6 rounded-xl font-black flex items-center justify-center gap-2 shadow-lg transition-all"
                                    >
                                        <UploadCloud className="w-5 h-5" /> Submit
                                    </button>
                                </div>
                            )}
                        </div>
                    </div>
                }
                tabs={
                    <div className="flex items-center gap-1">
                        {availableTabs.map(t => (
                            <button key={t.id} onClick={() => setActiveTab(t.id as any)} className={`px-6 py-3 text-sm font-bold border-b-2 transition-colors flex items-center gap-2 ${activeTab === t.id ? 'border-modtale-accent text-slate-900 dark:text-white' : 'border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-white'}`}>
                                <t.icon className="w-4 h-4"/> {t.label}
                            </button>
                        ))}
                    </div>
                }
                mainContent={
                    <>
                        {activeTab === 'details' && (
                            <div className="h-full flex flex-col">
                                <div className="flex items-center justify-between mb-4 pb-2 border-b border-slate-200 dark:border-white/5">
                                    <h3 className="text-xs font-bold text-slate-500 uppercase tracking-widest flex items-center gap-2"><FileText className="w-3 h-3"/> Description</h3>
                                    {!readOnly && hasProjectPermission('PROJECT_EDIT_METADATA') && <div className="flex bg-slate-100 dark:bg-slate-950/50 rounded-lg p-1 border border-slate-200 dark:border-white/10"><button onClick={() => setEditorMode('write')} className={`px-4 py-1.5 text-xs font-bold rounded-md ${editorMode === 'write' ? 'bg-modtale-accent text-white' : 'text-slate-500'}`}>Write</button><button onClick={() => setEditorMode('preview')} className={`px-4 py-1.5 text-xs font-bold rounded-md ${editorMode === 'preview' ? 'bg-modtale-accent text-white' : 'text-slate-500'}`}>Preview</button></div>}
                                </div>
                                {editorMode === 'write' && !readOnly && hasProjectPermission('PROJECT_EDIT_METADATA') ? (
                                    <textarea value={metaData.description} onChange={e => { markDirty(); setMetaData({...metaData, description: e.target.value}); }} className="flex-1 w-full h-full min-h-[400px] bg-transparent border-none outline-none text-slate-900 dark:text-slate-300 font-mono text-sm resize-none" placeholder="# Description..." />
                                ) : (
                                    <div className="prose dark:prose-invert prose-lg max-w-none min-h-[400px] prose-code:before:hidden prose-code:after:hidden">
                                        {metaData.description ? (
                                            <MarkdownRenderer content={metaData.description} />
                                        ) : (
                                            <p className="text-slate-500 italic">No description.</p>
                                        )}
                                    </div>
                                )}
                            </div>
                        )}

                        {activeTab === 'wiki' && (
                            <div className="h-full flex flex-col">
                                <div className="flex items-center justify-between mb-4 pb-2 border-b border-slate-200 dark:border-white/5">
                                    <h3 className="text-xs font-bold text-slate-500 uppercase tracking-widest flex items-center gap-2"><BookOpen className="w-3 h-3"/> Wiki Preview</h3>
                                </div>
                                <WikiContent wikiLoading={wikiLoading} wikiError={wikiError} wikiData={wikiData} wikiPageSlug={wikiPreviewSlug} mod={modData} />
                            </div>
                        )}

                        {activeTab === 'files' && (
                            <div className="space-y-8">
                                {!readOnly && hasProjectPermission('VERSION_CREATE') && (
                                    <div className="bg-slate-50 dark:bg-slate-950/30 p-6 rounded-2xl border border-slate-200 dark:border-white/5">
                                        <VersionFields data={versionData} onChange={setVersionData} isModpack={classification === 'MODPACK'} projectType={typeof classification === 'string' ? classification : 'PLUGIN'} disabled={readOnly} />
                                        <div className="mt-6 flex justify-end"><button onClick={handleUploadVersion} disabled={isLoading || readOnly} className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 h-12 rounded-xl font-bold shadow-lg flex items-center gap-2 disabled:opacity-50">{isLoading ? <Spinner className="w-5 h-5"/> : <UploadCloud className="w-5 h-5" />} Upload Version</button></div>
                                    </div>
                                )}
                                {modData?.versions?.map(v => (
                                    <div key={v.id} className="bg-slate-50 dark:bg-slate-950/30 border border-slate-200 dark:border-white/5 rounded-xl p-4 flex justify-between items-center group">
                                        <div>
                                            <div className="flex items-center gap-3"><span className="font-mono font-bold text-slate-900 dark:text-white text-lg">{v.versionNumber}</span><span className={`text-[10px] font-bold px-2 py-0.5 rounded border ${v.channel === 'RELEASE' ? 'text-green-500 border-green-500/30 bg-green-500/10' : 'text-orange-500 border-orange-500/30 bg-orange-500/10'}`}>{v.channel}</span></div>
                                            <div className="text-xs text-slate-500 mt-1">{v.gameVersions?.join(', ') || 'Unknown'} • {new Date(v.releaseDate).toLocaleDateString()}</div>
                                        </div>
                                        {!readOnly && (
                                            <div className="flex items-center gap-2">
                                                {hasProjectPermission('VERSION_EDIT') && (
                                                    <button
                                                        onClick={() => handleEditVersion(v)}
                                                        className="p-2 text-slate-500 hover:text-modtale-accent hover:bg-modtale-accent/10 rounded-lg transition-colors"
                                                        title="Edit Version Metadata"
                                                    >
                                                        <Edit2 className="w-4 h-4" />
                                                    </button>
                                                )}
                                                {handleDeleteVersion && hasProjectPermission('VERSION_DELETE') && (
                                                    <button onClick={() => handleDeleteVersion(v.id)} className="p-2 text-slate-500 hover:text-red-500 hover:bg-red-500/10 rounded-lg transition-colors">
                                                        <Trash2 className="w-4 h-4" />
                                                    </button>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                ))}
                            </div>
                        )}

                        {activeTab === 'gallery' && (
                            <div className="space-y-6">
                                <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                                    {modData?.galleryImages?.map((img, idx) => (
                                        <div key={idx} className="relative group aspect-video bg-black/20 rounded-xl overflow-hidden border border-slate-200 dark:border-white/5">
                                            <img src={img.startsWith('/api') ? `${BACKEND_URL}${img}` : img} alt="" className="w-full h-full object-cover" />
                                            {!readOnly && hasProjectPermission('PROJECT_GALLERY_REMOVE') && (
                                                <div className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                                                    <button onClick={() => handleGalleryDelete(img)} disabled={isLoading} className="p-2 bg-red-500 hover:bg-red-600 text-white rounded-lg shadow-lg transform scale-90 group-hover:scale-100 transition-transform">
                                                        <Trash2 className="w-5 h-5" />
                                                    </button>
                                                </div>
                                            )}
                                        </div>
                                    ))}
                                    {!readOnly && hasProjectPermission('PROJECT_GALLERY_ADD') && (
                                        <div
                                            {...getGalleryRootProps()}
                                            className={`aspect-video rounded-xl border-2 border-dashed flex flex-col items-center justify-center cursor-pointer transition-all ${isGalleryDragActive ? 'border-modtale-accent bg-modtale-accent/5' : 'border-slate-300 dark:border-white/10 bg-slate-100 dark:bg-white/5 hover:border-modtale-accent hover:bg-slate-200 dark:hover:bg-white/10'}`}
                                        >
                                            <input {...getGalleryInputProps()} />
                                            {isLoading ? <Spinner className="w-6 h-6 text-modtale-accent" fullScreen={false} /> : (
                                                <>
                                                    <UploadCloud className="w-8 h-8 text-slate-400 mb-2" />
                                                    <span className="text-xs font-bold text-slate-500 uppercase">Upload Image</span>
                                                </>
                                            )}
                                        </div>
                                    )}
                                </div>
                                {modData?.galleryImages?.length === 0 && readOnly && (
                                    <div className="text-center py-12 text-slate-500 italic">No images in gallery.</div>
                                )}
                            </div>
                        )}

                        {activeTab === 'team' && (
                            <div className="space-y-8 animate-in fade-in slide-in-from-bottom-2">
                                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                                    <div className="lg:col-span-2 space-y-6">
                                        {canInvite && (
                                            <div className="relative z-20 bg-slate-50 dark:bg-white/5 border border-slate-200 dark:border-white/10 p-6 rounded-2xl shadow-sm">
                                                <h3 className="font-bold text-lg text-slate-900 dark:text-white mb-4">Invite Contributor</h3>
                                                {modData?.projectRoles && modData.projectRoles.length > 0 ? (
                                                    <form onSubmit={handleInvite} className="flex flex-col md:flex-row gap-4 items-end">
                                                        <div className="flex-1 w-full relative modtale-dropdown-container">
                                                            <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1.5 ml-1">Username</label>
                                                            <input
                                                                type="text"
                                                                placeholder="Search username..."
                                                                value={inviteUsername}
                                                                onChange={e => { setInviteUsername(e.target.value); setInviteUserId(''); }}
                                                                className="w-full bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-modtale-accent transition-all dark:text-white font-medium shadow-inner"
                                                            />

                                                            {userSearchResults.length > 0 && (
                                                                <div className="absolute top-full left-0 right-0 mt-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-[100] overflow-hidden animate-in fade-in zoom-in-95">
                                                                    {userSearchResults.map(res => (
                                                                        <button
                                                                            key={res.id}
                                                                            type="button"
                                                                            onClick={() => { setInviteUsername(res.username); setInviteUserId(res.id); setUserSearchResults([]); }}
                                                                            className="w-full flex items-center gap-3 p-3 hover:bg-slate-50 dark:hover:bg-white/5 transition-colors text-left"
                                                                        >
                                                                            <div className="w-8 h-8 rounded-full bg-slate-200 overflow-hidden"><img src={res.avatarUrl} className="w-full h-full object-cover" /></div>
                                                                            <span className="font-bold text-sm text-slate-900 dark:text-white">{res.username}</span>
                                                                        </button>
                                                                    ))}
                                                                </div>
                                                            )}
                                                        </div>

                                                        <div className="w-full md:w-48 relative modtale-dropdown-container">
                                                            <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1.5 ml-1">Role</label>
                                                            <button
                                                                type="button"
                                                                onClick={() => setInviteRoleDropdownOpen(!inviteRoleDropdownOpen)}
                                                                className="w-full bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2.5 outline-none focus:ring-2 focus:ring-modtale-accent dark:text-white font-medium shadow-inner flex justify-between items-center"
                                                            >
                                                                {inviteRoleId ? (
                                                                    <div className="flex items-center gap-2 truncate">
                                                                        <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{backgroundColor: modData.projectRoles.find(r => r.id === inviteRoleId)?.color}} />
                                                                        <span className="truncate">{modData.projectRoles.find(r => r.id === inviteRoleId)?.name}</span>
                                                                    </div>
                                                                ) : (
                                                                    <span className="text-slate-400">Select Role...</span>
                                                                )}
                                                                <ChevronDown className={`w-4 h-4 text-slate-400 flex-shrink-0 transition-transform ${inviteRoleDropdownOpen ? 'rotate-180' : ''}`} />
                                                            </button>

                                                            {inviteRoleDropdownOpen && (
                                                                <div className="absolute top-full left-0 right-0 mt-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-[100] overflow-hidden animate-in fade-in zoom-in-95">
                                                                    <div className="max-h-48 overflow-y-auto custom-scrollbar py-1">
                                                                        {modData.projectRoles.map(role => (
                                                                            <button
                                                                                key={role.id}
                                                                                type="button"
                                                                                onClick={() => { setInviteRoleId(role.id); setInviteRoleDropdownOpen(false); }}
                                                                                className="w-full flex items-center justify-between px-3 py-2 hover:bg-slate-50 dark:hover:bg-white/5 transition-colors text-left"
                                                                            >
                                                                                <div className="flex items-center gap-2 truncate">
                                                                                    <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{backgroundColor: role.color}} />
                                                                                    <span className="font-bold text-sm text-slate-900 dark:text-white truncate">{role.name}</span>
                                                                                </div>
                                                                                {inviteRoleId === role.id && <Check className="w-4 h-4 text-modtale-accent flex-shrink-0" />}
                                                                            </button>
                                                                        ))}
                                                                    </div>
                                                                </div>
                                                            )}
                                                        </div>

                                                        <button
                                                            type="submit"
                                                            disabled={!inviteUserId || !inviteRoleId || isInviting}
                                                            className="w-full md:w-auto bg-slate-900 dark:bg-white text-white dark:text-slate-900 font-bold px-6 py-2.5 rounded-xl hover:opacity-90 transition-opacity disabled:opacity-50 flex items-center justify-center gap-2 shadow-lg h-11"
                                                        >
                                                            {isInviting ? <Spinner className="w-4 h-4" /> : <><Plus className="w-4 h-4" /> Invite</>}
                                                        </button>
                                                    </form>
                                                ) : (
                                                    <div className="text-sm text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/10 border border-amber-200 dark:border-amber-800/30 p-4 rounded-xl flex items-center gap-2 font-medium">
                                                        <Shield className="w-5 h-5" /> You must create at least one Role before inviting members.
                                                    </div>
                                                )}
                                            </div>
                                        )}

                                        <div className="relative z-10 bg-slate-50 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden shadow-sm">
                                            <div className="px-6 py-4 border-b border-slate-200 dark:border-white/10 flex justify-between items-center bg-white/50 dark:bg-black/10">
                                                <h3 className="font-bold text-slate-900 dark:text-white">Active Team</h3>
                                                <span className="text-xs font-bold bg-slate-200/50 dark:bg-white/10 px-2 py-1 rounded-lg text-slate-600 dark:text-slate-400">{(modData?.teamMembers?.length || 0) + 1}</span>
                                            </div>
                                            <div className="divide-y divide-slate-200 dark:divide-white/10">
                                                <div className="p-4 flex items-center justify-between hover:bg-white/50 dark:hover:bg-white/[0.02] transition-colors">
                                                    <div className="flex items-center gap-3">
                                                        <div className="w-10 h-10 rounded-full overflow-hidden bg-slate-200 dark:bg-white/10 flex items-center justify-center font-bold text-slate-400 border border-slate-200 dark:border-white/5">
                                                            {modData?.author?.charAt(0).toUpperCase()}
                                                        </div>
                                                        <div>
                                                            <div className="font-bold text-slate-900 dark:text-white text-sm">{modData?.author}</div>
                                                            <div className="flex items-center gap-1.5 mt-0.5">
                                                                <span className="text-[9px] font-black uppercase tracking-wider px-1.5 py-0.5 rounded border bg-slate-100 border-slate-200 text-slate-600 dark:bg-white/5 dark:border-white/10 dark:text-slate-400">Author</span>
                                                                {modData?.authorId === currentUser?.id && <span className="text-[9px] text-slate-400 font-medium italic">(You)</span>}
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div className="flex items-center">
                                                        <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">Owner</span>
                                                    </div>
                                                </div>

                                                {modData?.teamMembers?.map(member => {
                                                    const role = modData.projectRoles?.find(r => r.id === member.roleId);
                                                    const isMe = member.userId === currentUser?.id;

                                                    return (
                                                        <div key={member.userId} className="p-4 flex items-center justify-between hover:bg-white/50 dark:hover:bg-white/[0.02] transition-colors group">
                                                            <div className="flex items-center gap-3">
                                                                <div className="w-10 h-10 rounded-full overflow-hidden bg-slate-100 border border-slate-200 dark:border-white/10">
                                                                    {member.avatarUrl ? <img src={member.avatarUrl} alt="" className="w-full h-full object-cover" /> : <div className="w-full h-full bg-slate-200 dark:bg-white/10 flex items-center justify-center font-bold text-slate-400">{member.username?.charAt(0).toUpperCase() || '?'}</div>}
                                                                </div>
                                                                <div>
                                                                    <div className="font-bold text-slate-900 dark:text-white text-sm">{member.username || 'Unknown User'}</div>
                                                                    <div className="flex items-center gap-2 mt-1">
                                                                        {role ? (
                                                                            <div className="flex items-center gap-1.5 border border-slate-200 dark:border-white/10 bg-white/60 dark:bg-black/20 px-2 py-0.5 rounded-md">
                                                                                <div className="w-2 h-2 rounded-full" style={{ backgroundColor: role.color }} />
                                                                                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-300">{role.name}</span>
                                                                            </div>
                                                                        ) : (
                                                                            <span className="text-[10px] font-black uppercase tracking-wider px-1.5 py-0.5 rounded border bg-slate-100 border-slate-200 text-slate-600 dark:bg-white/5 dark:border-white/10 dark:text-slate-400">Legacy Contributor</span>
                                                                        )}
                                                                        {isMe && <span className="text-[10px] text-slate-400 font-medium italic">(You)</span>}
                                                                    </div>
                                                                </div>
                                                            </div>

                                                            <div className="flex items-center gap-3">
                                                                {canManageRoles && !isMe && (
                                                                    <div className="relative modtale-dropdown-container">
                                                                        <button
                                                                            onClick={() => setMemberRoleDropdownOpen(memberRoleDropdownOpen === member.userId ? null : member.userId)}
                                                                            className={`flex items-center justify-between min-w-[120px] bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-lg pl-3 pr-2 py-1.5 outline-none hover:border-modtale-accent transition-colors cursor-pointer shadow-sm`}
                                                                        >
                                                                            <div className="flex items-center gap-2 truncate">
                                                                                {role && <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: role.color }} />}
                                                                                <span className="text-xs font-bold text-slate-600 dark:text-slate-300 truncate">
                                                                                    {role ? role.name : 'Select Role'}
                                                                                </span>
                                                                            </div>
                                                                            <ChevronDown className={`w-3 h-3 text-slate-400 ml-2 flex-shrink-0 transition-transform ${memberRoleDropdownOpen === member.userId ? 'rotate-180' : ''}`} />
                                                                        </button>

                                                                        {memberRoleDropdownOpen === member.userId && (
                                                                            <div className="absolute right-0 top-full mt-2 w-48 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-[100] overflow-hidden animate-in fade-in zoom-in-95">
                                                                                <div className="max-h-48 overflow-y-auto custom-scrollbar py-1">
                                                                                    {modData.projectRoles?.map(r => (
                                                                                        <button
                                                                                            key={r.id}
                                                                                            onClick={() => {
                                                                                                handleRoleUpdate(member.userId, r.id);
                                                                                                setMemberRoleDropdownOpen(null);
                                                                                            }}
                                                                                            className="w-full flex items-center justify-between px-3 py-2 hover:bg-slate-50 dark:hover:bg-white/5 transition-colors text-left"
                                                                                        >
                                                                                            <div className="flex items-center gap-2 truncate">
                                                                                                <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{backgroundColor: r.color}} />
                                                                                                <span className="font-bold text-xs text-slate-900 dark:text-white truncate">{r.name}</span>
                                                                                            </div>
                                                                                            {member.roleId === r.id && <Check className="w-3 h-3 text-modtale-accent flex-shrink-0" />}
                                                                                        </button>
                                                                                    ))}
                                                                                </div>
                                                                            </div>
                                                                        )}
                                                                    </div>
                                                                )}

                                                                {(canRemove || isMe) && (
                                                                    <div className="relative group/tooltip">
                                                                        <button
                                                                            onClick={() => setMemberToRemove(member.userId)}
                                                                            className={`p-2 rounded-xl transition-all text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20`}
                                                                            title={isMe ? "Leave Project" : "Remove Contributor"}
                                                                        >
                                                                            <Trash2 className="w-4 h-4" />
                                                                        </button>
                                                                    </div>
                                                                )}
                                                            </div>
                                                        </div>
                                                    );
                                                })}
                                            </div>
                                        </div>

                                        {(modData?.teamInvites?.length || 0) > 0 && (
                                            <div className="relative z-0 bg-slate-50 dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-2xl overflow-hidden shadow-sm">
                                                <div className="px-6 py-4 border-b border-slate-200 dark:border-white/10 flex justify-between items-center bg-white/50 dark:bg-black/10">
                                                    <h3 className="font-bold text-slate-900 dark:text-white">Pending Invites</h3>
                                                    <span className="text-xs font-bold bg-slate-200/50 dark:bg-white/10 px-2 py-1 rounded-lg text-slate-600 dark:text-slate-400">{modData?.teamInvites?.length || 0}</span>
                                                </div>
                                                <div className="divide-y divide-slate-200 dark:divide-white/10">
                                                    {modData?.teamInvites?.map(invite => {
                                                        const role = modData.projectRoles?.find(r => r.id === invite.roleId);

                                                        return (
                                                            <div key={invite.userId} className="p-4 flex items-center justify-between hover:bg-white/50 dark:hover:bg-white/[0.02] transition-colors">
                                                                <div className="flex items-center gap-4">
                                                                    <div className="w-10 h-10 rounded-full overflow-hidden bg-slate-100 border border-slate-200 dark:border-white/10 opacity-70 grayscale">
                                                                        {invite.avatarUrl ? <img src={invite.avatarUrl} alt="" className="w-full h-full object-cover" /> : <div className="w-full h-full bg-slate-200 dark:bg-white/10 flex items-center justify-center font-bold text-slate-400">{invite.username?.charAt(0).toUpperCase() || '?'}</div>}
                                                                    </div>
                                                                    <div>
                                                                        <div className="font-bold text-slate-900 dark:text-white text-sm">{invite.username || 'Unknown User'}</div>
                                                                        <div className="flex items-center gap-2 mt-1">
                                                                            <span className="text-[9px] font-black uppercase tracking-wider px-1.5 py-0.5 rounded border bg-amber-50 border-amber-200 text-amber-600 dark:bg-amber-900/20 dark:border-amber-700/30 dark:text-amber-400">Pending</span>
                                                                            {role && (
                                                                                <span className="text-[10px] text-slate-500 font-medium">As {role.name}</span>
                                                                            )}
                                                                        </div>
                                                                    </div>
                                                                </div>

                                                                {canInvite && (
                                                                    <button onClick={() => handleCancelInvite(invite.userId)} className="text-xs font-bold text-red-500 hover:text-red-600 bg-red-50 dark:bg-red-900/10 hover:bg-red-100 dark:hover:bg-red-900/30 px-3 py-1.5 rounded-lg border border-red-200 dark:border-red-900/30 transition-colors">
                                                                        Cancel
                                                                    </button>
                                                                )}
                                                            </div>
                                                        );
                                                    })}
                                                </div>
                                            </div>
                                        )}
                                    </div>

                                    <div className="lg:col-span-1 space-y-6">
                                        <div className="bg-slate-50 dark:bg-white/5 border border-slate-200 dark:border-white/10 p-5 rounded-2xl shadow-sm">
                                            <div className="flex justify-between items-center mb-4">
                                                <h3 className="font-bold text-slate-900 dark:text-white">Project Roles</h3>
                                                {canManageRoles && (
                                                    <button onClick={() => { setEditingRole({ permissions: [] }); setRoleModalOpen(true); }} className="text-modtale-accent hover:text-modtale-accentHover font-bold text-xs flex items-center gap-1 transition-colors">
                                                        <Plus className="w-3 h-3" /> New
                                                    </button>
                                                )}
                                            </div>

                                            <div className="space-y-3">
                                                {modData?.projectRoles?.map(role => {
                                                    const count = modData.teamMembers?.filter(m => m.roleId === role.id).length || 0;
                                                    return (
                                                        <div key={role.id} className="bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl p-3 shadow-sm group">
                                                            <div className="flex justify-between items-start">
                                                                <div className="flex items-center gap-2">
                                                                    <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: role.color }} />
                                                                    <div>
                                                                        <div className="font-bold text-xs text-slate-900 dark:text-white leading-tight">{role.name}</div>
                                                                        <div className="text-[9px] text-slate-500 uppercase tracking-wider font-bold mt-0.5">{count} Member{count !== 1 ? 's' : ''}</div>
                                                                    </div>
                                                                </div>
                                                                {canManageRoles && (
                                                                    <div className="flex gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                                                                        <button onClick={() => { setEditingRole(role); setRoleModalOpen(true); }} className="p-1 text-slate-400 hover:text-modtale-accent hover:bg-modtale-accent/10 rounded transition-colors">
                                                                            <Settings className="w-3.5 h-3.5" />
                                                                        </button>
                                                                        <button onClick={() => handleDeleteRole(role.id)} className="p-1 text-slate-400 hover:text-red-500 hover:bg-red-500/10 rounded transition-colors">
                                                                            <Trash2 className="w-3.5 h-3.5" />
                                                                        </button>
                                                                    </div>
                                                                )}
                                                            </div>
                                                        </div>
                                                    );
                                                })}
                                                {(!modData?.projectRoles || modData.projectRoles.length === 0) && (
                                                    <div className="text-center py-6 text-xs text-slate-500 italic bg-white/50 dark:bg-black/10 rounded-xl border border-slate-200 dark:border-white/5">
                                                        No custom roles defined.
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}

                        {activeTab === 'settings' && (
                            <div className="space-y-6">
                                <div className="bg-slate-50 dark:bg-slate-900/30 p-6 rounded-2xl border border-slate-200 dark:border-white/10">

                                    {(modData?.status === 'PUBLISHED' || modData?.status === 'UNLISTED') && (
                                        <div className="mb-6 pb-6 border-b border-slate-200 dark:border-white/5">
                                            <h3 className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-2 mb-4"><Eye className="w-4 h-4 text-slate-500" /> Project Visibility</h3>
                                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                                <button
                                                    onClick={handleRestore}
                                                    disabled={isLoading || modData.status === 'PUBLISHED' || !hasProjectPermission('PROJECT_STATUS_PUBLISH')}
                                                    className={`p-4 rounded-xl border flex flex-col items-center gap-3 transition-all ${modData.status === 'PUBLISHED' ? 'bg-green-500/10 border-green-500 text-green-500' : 'bg-white dark:bg-black/20 border-slate-200 dark:border-white/10 hover:border-green-500 hover:text-green-500 disabled:opacity-50 disabled:hover:border-slate-200 dark:disabled:hover:border-white/10 disabled:hover:text-inherit'}`}
                                                >
                                                    <Globe className="w-6 h-6" />
                                                    <div className="text-center">
                                                        <div className="font-bold text-sm">Published</div>
                                                        <div className="text-[10px] opacity-70">Visible to everyone</div>
                                                    </div>
                                                </button>

                                                <button
                                                    onClick={handleUnlist}
                                                    disabled={isLoading || modData.status === 'UNLISTED' || !hasProjectPermission('PROJECT_STATUS_UNLIST')}
                                                    className={`p-4 rounded-xl border flex flex-col items-center gap-3 transition-all ${modData.status === 'UNLISTED' ? 'bg-orange-500/10 border-orange-500 text-orange-500' : 'bg-white dark:bg-black/20 border-slate-200 dark:border-white/10 hover:border-orange-500 hover:text-orange-500 disabled:opacity-50 disabled:hover:border-slate-200 dark:disabled:hover:border-white/10 disabled:hover:text-inherit'}`}
                                                >
                                                    <EyeOff className="w-6 h-6" />
                                                    <div className="text-center">
                                                        <div className="font-bold text-sm">Unlisted</div>
                                                        <div className="text-[10px] opacity-70">Hidden from search</div>
                                                    </div>
                                                </button>

                                                <button
                                                    onClick={handleArchive}
                                                    disabled={isLoading || !hasProjectPermission('PROJECT_STATUS_ARCHIVE')}
                                                    className="p-4 rounded-xl border flex flex-col items-center gap-3 transition-all bg-white dark:bg-black/20 border-slate-200 dark:border-white/10 hover:border-slate-500 hover:text-slate-500 disabled:opacity-50 disabled:hover:border-slate-200 dark:disabled:hover:border-white/10 disabled:hover:text-inherit"
                                                >
                                                    <Archive className="w-6 h-6" />
                                                    <div className="text-center">
                                                        <div className="font-bold text-sm">Archived</div>
                                                        <div className="text-[10px] opacity-70">Read-only state</div>
                                                    </div>
                                                </button>
                                            </div>
                                        </div>
                                    )}

                                    {modData?.id && (
                                        <div className="mb-6 pb-6 border-b border-slate-200 dark:border-white/5">
                                            <div className="flex flex-col gap-2">
                                                <div><h3 className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-2"><Tag className="w-4 h-4 text-slate-500" /> Project ID</h3><p className="text-xs text-slate-500">Unique identifier.</p></div>
                                                <div className="flex items-center gap-2">
                                                    <code className="bg-slate-100 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-lg px-3 py-2 text-xs font-mono select-all">{modData.id}</code>
                                                    <button onClick={handleCopyId} className="p-2 bg-slate-100 dark:bg-white/5 rounded-lg text-slate-500 hover:text-slate-900">{idCopied ? <Check className="w-4 h-4 text-green-500" /> : <Copy className="w-4 h-4" />}</button>
                                                </div>
                                            </div>
                                        </div>
                                    )}

                                    <div className="mb-6 pb-6 border-b border-slate-200 dark:border-white/5">
                                        <div className="flex flex-col gap-2">
                                            <div><h3 className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-2"><Link2 className="w-4 h-4 text-slate-500" /> Project Slug</h3><p className="text-xs text-slate-500">Customize the URL.</p></div>
                                            <div className={`flex items-center w-full bg-white dark:bg-black/20 border rounded-xl overflow-hidden focus-within:ring-2 focus-within:ring-modtale-accent transition-all ${slugError ? 'border-red-500' : 'border-slate-200 dark:border-white/10'}`}>
                                                <div className="px-4 py-2 bg-slate-50 dark:bg-white/5 border-r border-slate-200 dark:border-white/10 text-slate-500 text-sm font-mono whitespace-nowrap select-none">{getUrlPrefix()}</div>
                                                <input disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')} value={metaData.slug || ''} onChange={handleSlugChange} className={`flex-1 bg-transparent border-none px-4 py-2 text-sm font-mono text-slate-900 dark:text-white focus:outline-none placeholder:text-slate-400 ${slugError ? 'text-red-500' : ''}`} placeholder={createSlug(metaData.title, modData?.id || 'id')} />
                                            </div>
                                            {slugError && <p className="text-[10px] text-red-500 font-bold">{slugError}</p>}
                                        </div>
                                    </div>

                                    <div className="flex items-center justify-between mb-4">
                                        <div><h3 className="text-sm font-bold text-slate-900 dark:text-white">Allow Modpacks</h3><p className="text-xs text-slate-500">Allow inclusion in modpacks?</p></div>
                                        <button disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')} onClick={() => { markDirty(); setModData(prev => prev ? {...prev, allowModpacks: !prev.allowModpacks} : null); }} className={`transition-colors ${readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA') ? 'opacity-50' : modData?.allowModpacks ? 'text-green-500' : 'text-slate-600'}`}>{modData?.allowModpacks ? <ToggleRight className="w-8 h-8" /> : <ToggleLeft className="w-8 h-8" />}</button>
                                    </div>

                                    <div className="flex items-center justify-between mb-6 pb-6 border-b border-slate-200 dark:border-white/5">
                                        <div><h3 className="text-sm font-bold text-slate-900 dark:text-white">Allow Comments</h3><p className="text-xs text-slate-500">Enable community comments?</p></div>
                                        <button disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')} onClick={() => { markDirty(); setModData(prev => prev ? {...prev, allowComments: !prev.allowComments} : null); }} className={`transition-colors ${readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA') ? 'opacity-50' : modData?.allowComments ? 'text-green-500' : 'text-slate-600'}`}>{modData?.allowComments ? <ToggleRight className="w-8 h-8" /> : <ToggleLeft className="w-8 h-8" />}</button>
                                    </div>

                                    <div className="flex items-center justify-between mb-4">
                                        <div>
                                            <h3 className="text-sm font-bold text-slate-900 dark:text-white">HytaleModding Wiki</h3>
                                            <p className="text-xs text-slate-500 mt-0.5">Embed your <a href="https://wiki.hytalemodding.dev" target="_blank" rel="noopener noreferrer" className="text-modtale-accent hover:underline font-bold">HytaleModding Wiki</a> directly on your project page.</p>
                                        </div>
                                        <button disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')} onClick={() => { markDirty(); setModData(prev => prev ? {...prev, hmWikiEnabled: !prev.hmWikiEnabled} : null); }} className={`transition-colors ${readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA') ? 'opacity-50' : modData?.hmWikiEnabled ? 'text-green-500' : 'text-slate-600'}`}>{modData?.hmWikiEnabled ? <ToggleRight className="w-8 h-8" /> : <ToggleLeft className="w-8 h-8" />}</button>
                                    </div>

                                    {modData?.hmWikiEnabled && (
                                        <div className="mb-6 p-4 bg-slate-100 dark:bg-black/20 rounded-xl border border-slate-200 dark:border-white/10 animate-in slide-in-from-top-2">
                                            <label className="text-[10px] font-black uppercase text-slate-400 tracking-widest px-1 mb-2 block">Wiki Project Slug / ID</label>
                                            <input
                                                value={modData.hmWikiSlug || ''}
                                                onChange={e => {
                                                    markDirty();
                                                    const newSlug = e.target.value;
                                                    setModData(prev => prev ? {...prev, hmWikiSlug: newSlug} : null);
                                                    setMetaData(prev => {
                                                        const currentWiki = prev.links.WIKI || '';
                                                        if (!currentWiki || /^https?:\/\/wiki\.hytalemodding\.dev\/mods?\//i.test(currentWiki)) {
                                                            return {
                                                                ...prev,
                                                                links: {
                                                                    ...prev.links,
                                                                    WIKI: newSlug ? `https://wiki.hytalemodding.dev/mod/${newSlug}` : ''
                                                                }
                                                            };
                                                        }
                                                        return prev;
                                                    });
                                                }}
                                                disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')}
                                                placeholder="e.g., my-awesome-mod"
                                                className="w-full bg-white dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-lg px-3 py-2 text-sm font-mono focus:border-modtale-accent focus:ring-1 focus:ring-modtale-accent outline-none transition-all"
                                            />
                                            <div className="mt-3 flex items-start gap-2 bg-blue-500/10 text-blue-600 dark:text-blue-400 p-3 rounded-lg border border-blue-500/20 text-xs leading-relaxed">
                                                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                                                <div>
                                                    Don't have a wiki yet? <a href="https://wiki.hytalemodding.dev" target="_blank" rel="noopener noreferrer" className="font-bold underline hover:text-blue-500 transition-colors">Create one on HytaleModding first</a>, then link the URL slug here to display it on Modtale!
                                                </div>
                                            </div>
                                        </div>
                                    )}

                                    {modData?.modjamIds && modData.modjamIds.length > 0 && (
                                        <div className="mb-6 pb-6 border-b border-slate-200 dark:border-white/5">
                                            <h3 className="text-sm font-bold text-slate-900 dark:text-white mb-1"><Trophy className="w-4 h-4 inline mr-2 text-slate-500" /> Displayed Modjams</h3>
                                            <p className="text-xs text-slate-500 mb-4">Choose which jams are featured on your project page.</p>
                                            <div className="space-y-2">
                                                {modData.modjamIds.map(jamId => {
                                                    const meta = jamMeta[jamId];
                                                    const isHidden = ((modData as any).hiddenModjamIds || []).includes(jamId);
                                                    return (
                                                        <div key={jamId} className="flex items-center justify-between p-3 bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl">
                                                            <div className="flex items-center gap-3">
                                                                <div className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-slate-800 flex items-center justify-center overflow-hidden shrink-0 border border-slate-200 dark:border-white/5">
                                                                    {meta?.imageUrl ? <img src={meta.imageUrl.startsWith('http') ? meta.imageUrl : `${BACKEND_URL}${meta.imageUrl}`} className="w-full h-full object-cover" alt="" /> : <Trophy className="w-4 h-4 text-slate-400" />}
                                                                </div>
                                                                <div>
                                                                    <div className="text-sm font-bold text-slate-900 dark:text-white">{meta?.title || 'Loading...'}</div>
                                                                    <div className="text-[10px] text-slate-500 uppercase tracking-widest">{meta?.isWinner ? 'Winner' : 'Submission'}</div>
                                                                </div>
                                                            </div>
                                                            <button
                                                                disabled={readOnly}
                                                                onClick={() => {
                                                                    markDirty();
                                                                    setModData(prev => {
                                                                        if (!prev) return null;
                                                                        const hidden = (prev as any).hiddenModjamIds || [];
                                                                        return {
                                                                            ...prev,
                                                                            hiddenModjamIds: hidden.includes(jamId)
                                                                                ? hidden.filter((id: string) => id !== jamId)
                                                                                : [...hidden, jamId]
                                                                        };
                                                                    });
                                                                }}
                                                                className={`transition-colors ${readOnly ? 'opacity-50' : !isHidden ? 'text-green-500' : 'text-slate-600'}`}
                                                            >
                                                                {!isHidden ? <ToggleRight className="w-8 h-8" /> : <ToggleLeft className="w-8 h-8" />}
                                                            </button>
                                                        </div>
                                                    );
                                                })}
                                            </div>
                                        </div>
                                    )}
                                </div>
                                {!readOnly && hasProjectPermission('PROJECT_DELETE') && <button onClick={handleDelete} className="w-full bg-red-500/10 border border-red-500/20 text-red-500 hover:bg-red-500 hover:text-white p-4 rounded-xl font-bold flex justify-center gap-2 transition-all"><Trash2 className="w-4 h-4"/> Delete Project</button>}
                            </div>
                        )}
                    </>
                }
                sidebarContent={
                    <>
                        {activeTab === 'wiki' && wikiData && !wikiLoading && !wikiError && (
                            <WikiSidebar tree={wikiData.mod.pages || []} projectUrl="#" currentSlug={wikiPreviewSlug} indexSlug={wikiData.mod.index?.slug} onNavigate={setWikiPreviewSlug} />
                        )}
                        <SidebarSection title="Card Preview" icon={Eye}>
                            <div className="w-full max-w-[340px] mx-auto relative group cursor-pointer overflow-hidden rounded-2xl border border-slate-200 dark:border-white/10 shadow-sm" onClick={() => setShowCardPreview(true)}>
                                <div className="pointer-events-none select-none">
                                    <ModCard
                                        mod={previewMod}
                                        isFavorite={false}
                                        onToggleFavorite={() => {}}
                                        isLoggedIn={false}
                                    />
                                </div>
                                <div className="absolute inset-0 z-50 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center backdrop-blur-[2px]">
                                    <div className="bg-black/60 px-4 py-2 rounded-full flex items-center gap-2 text-white border border-white/20 shadow-xl transform scale-90 group-hover:scale-100 transition-transform">
                                        <Maximize2 className="w-4 h-4" />
                                        <span className="text-xs font-bold uppercase tracking-widest">Expand</span>
                                    </div>
                                </div>
                            </div>
                        </SidebarSection>
                        <SidebarSection title="Repository Source" icon={GitMerge}>
                            <div className="bg-slate-50 dark:bg-slate-950/50 border border-slate-200 dark:border-white/10 rounded-xl p-2">
                                <div className="flex bg-slate-200 dark:bg-black/40 rounded-lg p-1 mb-3">
                                    <button onClick={() => { setManualRepo(false); if (hasGithub) { if (provider !== 'github') setRepos([]); setProvider('github'); } }} disabled={readOnly || !hasGithub || !hasProjectPermission('PROJECT_EDIT_METADATA')} className={`flex-1 py-1.5 text-[10px] font-bold rounded-md transition-all ${!manualRepo && provider === 'github' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-900 dark:hover:text-white disabled:opacity-30'}`}>GitHub</button>
                                    <button onClick={() => { setManualRepo(false); if (hasGitlab) { if (provider !== 'gitlab') setRepos([]); setProvider('gitlab'); } }} disabled={readOnly || !hasGitlab || !hasProjectPermission('PROJECT_EDIT_METADATA')} className={`flex-1 py-1.5 text-[10px] font-bold rounded-md transition-all ${!manualRepo && provider === 'gitlab' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-900 dark:hover:text-white disabled:opacity-30'}`}>GitLab</button>
                                    <button onClick={() => setManualRepo(true)} disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')} className={`flex-1 py-1.5 text-[10px] font-bold rounded-md transition-all ${manualRepo ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 hover:text-slate-900 dark:hover:text-white disabled:opacity-30'}`}>Link URL</button>
                                </div>
                                {manualRepo ? (
                                    <div className="relative">
                                        <input value={metaData.repositoryUrl} disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')} onChange={e => { markDirty(); setMetaData({...metaData, repositoryUrl: e.target.value}); checkRepoUrl(e.target.value); }} className={`w-full bg-slate-100 dark:bg-slate-900 border rounded-lg px-3 py-2 text-xs text-slate-900 dark:text-white outline-none transition-all pr-8 ${!repoValid && metaData.repositoryUrl ? 'border-red-500' : 'border-slate-200 dark:border-white/10'}`} placeholder="https://github.com/..." />
                                        {metaData.repositoryUrl && <div className="absolute right-3 top-2.5">{repoValid ? <CheckCircle2 className="w-3.5 h-3.5 text-green-500" /> : <X className="w-3.5 h-3.5 text-red-500" />}</div>}
                                    </div>
                                ) : (!hasGithub && !hasGitlab) ? (
                                    <div className="text-center py-4 text-xs text-slate-500">Link account in settings.</div>
                                ) : (
                                    <div className="relative modtale-dropdown-container">
                                        <button disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')} onClick={() => setRepoDropdownOpen(!repoDropdownOpen)} className={`w-full flex items-center justify-between bg-slate-100 dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-lg px-3 py-2 text-xs text-slate-900 dark:text-white transition-colors ${(!readOnly && hasProjectPermission('PROJECT_EDIT_METADATA')) ? 'hover:border-modtale-accent' : 'opacity-50 cursor-not-allowed'}`}>
                                            <span className="truncate">{metaData.repositoryUrl || "Select Repo..."}</span>
                                            <ChevronDown className="w-3 h-3 text-slate-500" />
                                        </button>
                                        {repoDropdownOpen && !readOnly && hasProjectPermission('PROJECT_EDIT_METADATA') && (
                                            <div className="absolute top-full mt-2 w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-[60] overflow-hidden">
                                                <div className="p-2 border-b border-slate-200 dark:border-white/5 flex gap-2">
                                                    <input autoFocus value={repoSearch} onChange={e => setRepoSearch(e.target.value)} className="flex-1 bg-slate-100 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-md px-2 py-1 text-xs text-slate-900 dark:text-white focus:outline-none" placeholder="Filter..." />
                                                    <button type="button" onClick={fetchRepos} className="p-1 bg-slate-100 dark:bg-white/5 rounded hover:bg-slate-200 dark:hover:bg-white/10"><RefreshCw className={`w-3 h-3 text-slate-400 ${loadingRepos ? 'animate-spin' : ''}`} /></button>
                                                </div>
                                                <div className="max-h-48 overflow-y-auto p-1 custom-scrollbar">
                                                    {loadingRepos ? <div className="p-4 text-center"><Loader2 className="w-4 h-4 animate-spin mx-auto text-modtale-accent" /></div> : filteredRepos.length > 0 ? filteredRepos.map(r => (
                                                        <button key={r.url} type="button" onClick={() => { markDirty(); setMetaData({...metaData, repositoryUrl: r.html_url || r.url}); checkRepoUrl(r.html_url || r.url); setRepoDropdownOpen(false); }} className="w-full text-left px-2 py-1.5 rounded hover:bg-slate-100 dark:hover:bg-white/10 text-xs text-slate-600 dark:text-slate-300 flex justify-between items-center group"><span className="font-mono">{r.name}</span>{metaData.repositoryUrl === (r.html_url || r.url) && <Check className="w-3 h-3 text-modtale-accent" />}</button>
                                                    )) : <div className="p-2 text-center text-[10px] text-slate-500">No repos found</div>}
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                )}
                            </div>
                        </SidebarSection>
                        {!isModpack && (
                            <SidebarSection title="License" icon={Scale} defaultOpen={false}>
                                <div className="bg-slate-50 dark:bg-slate-950/50 border border-slate-200 dark:border-white/10 rounded-xl p-2 max-h-80 overflow-y-auto custom-scrollbar">
                                    {LICENSES.map(lic => (
                                        <button key={lic.id} disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')} onClick={() => { markDirty(); setIsCustomLicense(false); setMetaData({ ...metaData, license: lic.id }); }} className={`w-full text-left px-3 py-2 rounded-lg text-xs font-bold flex items-center justify-between ${!isCustomLicense && metaData.license === lic.id ? 'bg-modtale-accent text-white' : 'text-slate-500 hover:bg-slate-200 dark:hover:bg-white/10'}`}>
                                            <span>{lic.name}</span>{!isCustomLicense && metaData.license === lic.id && <Check className="w-3 h-3" />}
                                        </button>
                                    ))}

                                    <button
                                        disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')}
                                        onClick={() => { markDirty(); setIsCustomLicense(true); if(!metaData.license || LICENSES.some(l => l.id === metaData.license)) setMetaData({...metaData, license: ''}); }}
                                        className={`w-full text-left px-3 py-2 rounded-lg text-xs font-bold flex items-center justify-between border-t border-slate-200 dark:border-white/10 mt-1 pt-2 ${isCustomLicense ? 'bg-modtale-accent text-white' : 'text-slate-500 hover:bg-slate-200 dark:hover:bg-white/10'}`}
                                    >
                                        <span>Custom License</span>
                                        {isCustomLicense ? <Check className="w-3 h-3" /> : <Edit2 className="w-3 h-3" />}
                                    </button>

                                    {isCustomLicense && (
                                        <div className="mt-2 p-2 bg-slate-100 dark:bg-black/20 rounded-lg space-y-2 animate-in slide-in-from-top-2">
                                            <input
                                                value={metaData.license}
                                                onChange={(e) => { markDirty(); setMetaData({...metaData, license: e.target.value}); }}
                                                placeholder="License Name"
                                                disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')}
                                                className="w-full bg-white dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-lg px-3 py-2 text-xs"
                                            />
                                            <input
                                                value={metaData.links.LICENSE || ''}
                                                onChange={(e) => { markDirty(); setMetaData({...metaData, links: {...metaData.links, LICENSE: e.target.value}}); }}
                                                placeholder="License URL"
                                                disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')}
                                                className={`w-full bg-white dark:bg-white/5 border rounded-lg px-3 py-2 text-xs font-mono transition-colors ${!metaData.links.LICENSE ? 'border-red-500 focus:border-red-500' : 'border-slate-200 dark:border-white/10'}`}
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
                                    <button disabled={readOnly || !hasProjectPermission('PROJECT_EDIT_METADATA')} key={tag} onClick={() => toggleTag(tag)} className={`px-2.5 py-1 rounded-lg text-[10px] font-bold border transition-all ${metaData.tags.includes(tag) ? 'bg-modtale-accent text-white border-modtale-accent' : 'bg-slate-100 dark:bg-slate-900/50 text-slate-500 dark:text-slate-400 border-slate-200 dark:border-white/10'}`}>
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
                                        value={metaData.links[k as keyof typeof metaData.links] || ''}
                                        onChange={(e:any) => {
                                            markDirty();
                                            const newVal = e.target.value;
                                            setMetaData(prev => ({...prev, links: {...prev.links, [k]: newVal}}));

                                            if (k === 'WIKI') {
                                                const match = newVal.match(/^https?:\/\/wiki\.hytalemodding\.dev\/mods?\/([a-zA-Z0-9-]+)\/?/i);
                                                if (match) {
                                                    setModData(prev => prev ? { ...prev, hmWikiEnabled: true, hmWikiSlug: match[1] } : null);
                                                }
                                            }
                                        }}
                                        placeholder="https://..."
                                    />
                                ))}
                            </div>
                        </SidebarSection>
                    </>
                }
            />
        </div>
    );
};