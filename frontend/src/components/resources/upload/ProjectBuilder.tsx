import React, { useState, useEffect, useRef, useCallback } from 'react';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import { BACKEND_URL, api } from '../../../utils/api';
import { GLOBAL_TAGS, LICENSES } from '../../../data/categories';
import type { Classification } from '../../../data/categories';
import { VersionFields } from './VersionFields';
import type { MetadataFormData, VersionFormData } from './FormShared';
import {
    Save, UploadCloud, CheckCircle2, Image as ImageIcon, Link as LinkIcon, Tag,
    ChevronDown, ChevronUp, RefreshCw, Check, Loader2, GitMerge, Settings,
    ToggleLeft, ToggleRight, Trash2, AlertCircle, FileText, Eye, LayoutTemplate,
    UserPlus, X, Plus, Globe, Scale, Box, Clock, Lock, Undo2, Archive, EyeOff, RotateCcw, Link2
} from 'lucide-react';
import { Spinner } from '@/components/ui/Spinner';
import { StatusModal } from '@/components/ui/StatusModal';
import { ModSidebar } from '@/components/resources/mod-detail/ModSidebar';
import { createSlug } from '../../../utils/slug';
import type { Mod, User } from '../../../types';
import { ImageCropperModal } from '@/components/ui/ImageCropperModal';

const ThemedInput = ({ label, disabled, ...props }: any) => (
    <div className="space-y-1.5">
        <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">{label}</label>
        <input {...props} disabled={disabled} className={`w-full bg-slate-50 dark:bg-slate-950/50 border border-slate-200 dark:border-white/10 rounded-xl px-4 py-2.5 text-slate-900 dark:text-white placeholder:text-slate-400 dark:placeholder:text-slate-600 outline-none transition-all text-sm ${disabled ? 'opacity-50 cursor-not-allowed' : 'focus:ring-2 focus:ring-modtale-accent'}`} />
    </div>
);

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

    handleSave: (silent?: boolean) => void;
    handlePublish?: () => void;
    handleDelete?: () => void;
    handleDeleteVersion?: (versionId: string) => void;
    handleUploadVersion: () => void;
    handleRevert?: () => void;
    handleArchive?: () => void;
    handleUnlist?: () => void;
    handleRestore?: () => void;

    isLoading: boolean;
    classification: Classification | string;
    currentUser: User | null;
    activeTab: 'details' | 'files' | 'settings';
    setActiveTab: (tab: 'details' | 'files' | 'settings') => void;
    onShowStatus: (type: 'success' | 'error', title: string, msg: string) => void;
    readOnly?: boolean;
}

export const ProjectBuilder: React.FC<ProjectBuilderProps> = ({
                                                                  modData, setModData, metaData, setMetaData, versionData, setVersionData,
                                                                  bannerPreview, setBannerPreview, setBannerFile,
                                                                  handleSave, handlePublish, handleDelete, handleDeleteVersion, handleUploadVersion, handleRevert, handleArchive, handleUnlist, handleRestore,
                                                                  isLoading, classification, currentUser, activeTab, setActiveTab, onShowStatus, readOnly
                                                              }) => {
    const [editorMode, setEditorMode] = useState<'write' | 'preview'>(readOnly ? 'preview' : 'write');
    const [inviteUsername, setInviteUsername] = useState('');
    const [isInviting, setIsInviting] = useState(false);

    const [showPublishConfirm, setShowPublishConfirm] = useState(false);
    const [showRevertConfirm, setShowRevertConfirm] = useState(false);
    const [showArchiveConfirm, setShowArchiveConfirm] = useState(false);
    const [showUnlistConfirm, setShowUnlistConfirm] = useState(false);
    const [showRestoreConfirm, setShowRestoreConfirm] = useState(false);
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [showDeleteVersionConfirm, setShowDeleteVersionConfirm] = useState<string | null>(null);

    const [expandedVersionId, setExpandedVersionId] = useState<string | null>(null);

    // Repository Logic
    const [repos, setRepos] = useState<any[]>([]);
    const [loadingRepos, setLoadingRepos] = useState(false);
    const [manualRepo, setManualRepo] = useState(false);
    const [repoSearch, setRepoSearch] = useState('');
    const [repoDropdownOpen, setRepoDropdownOpen] = useState(false);
    const repoDropdownRef = useRef<HTMLDivElement>(null);
    const [repoValid, setRepoValid] = useState(true); // Default true since optional

    const [cropperOpen, setCropperOpen] = useState(false);
    const [tempImage, setTempImage] = useState<string | null>(null);
    const [cropType, setCropType] = useState<'icon' | 'banner'>('icon');

    const [slugError, setSlugError] = useState<string | null>(null);

    const isPlugin = classification === 'PLUGIN';
    const isModpack = classification === 'MODPACK';

    const hasTags = metaData.tags.length > 0;
    const hasSummary = metaData.summary && metaData.summary.length >= 10;
    const hasVersion = (modData?.versions?.length || 0) > 0;
    const hasRepo = !metaData.repositoryUrl || repoValid;
    const hasLicense = isModpack || !!metaData.license;

    const isPublishable = hasTags && hasSummary && hasVersion && hasRepo && hasLicense;
    const [showReqs, setShowReqs] = useState(false);

    const hasGithub = currentUser?.connectedAccounts?.some(a => a.provider === 'github') || false;
    const hasGitlab = currentUser?.connectedAccounts?.some(a => a.provider === 'gitlab') || false;
    const [provider, setProvider] = useState<'github' | 'gitlab'>(hasGithub ? 'github' : (hasGitlab ? 'gitlab' : 'github'));

    const isDraft = modData?.status === 'DRAFT' || !modData?.status;
    const isPending = modData?.status === 'PENDING';
    const isArchived = modData?.status === 'ARCHIVED';
    const isUnlisted = modData?.status === 'UNLISTED';
    const canUpload = !readOnly && (!isDraft || (modData?.versions?.length || 0) === 0);

    const getUrlPrefix = () => {
        if (classification === 'MODPACK') return 'modtale.net/modpack/';
        if (classification === 'SAVE') return 'modtale.net/world/';
        return 'modtale.net/mod/';
    };

    const checkRepoUrl = useCallback((url: string) => {
        if (!url) {
            setRepoValid(true);
            return true;
        }
        const isValid = /^https:\/\/(github\.com|gitlab\.com)\/[\w.-]+\/[\w.-]+$/.test(url);
        setRepoValid(isValid);
        return isValid;
    }, []);

    const handleSlugChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const val = e.target.value;
        setMetaData({...metaData, slug: val});

        if (!val) {
            setSlugError(null);
            return;
        }

        const slugRegex = /^[a-z0-9](?:[a-z0-9-]{1,48}[a-z0-9])?$/;
        if (!slugRegex.test(val)) {
            setSlugError("Must be 3-50 chars, lowercase alphanumeric, no start/end dash.");
        } else {
            setSlugError(null);
        }
    };

    useEffect(() => {
        checkRepoUrl(metaData.repositoryUrl || '');
    }, [metaData.repositoryUrl, checkRepoUrl]);

    useEffect(() => {
        if (readOnly) {
            setEditorMode('preview');
        }
    }, [readOnly]);

    const fetchRepos = useCallback(() => {
        if (readOnly) return;
        if ((provider === 'github' && !hasGithub) || (provider === 'gitlab' && !hasGitlab)) return;
        setLoadingRepos(true);
        const endpoint = provider === 'gitlab' ? '/user/repos/gitlab' : '/user/repos/github';
        api.get(endpoint)
            .then(res => setRepos(res.data || []))
            .catch(e => console.error(e))
            .finally(() => setLoadingRepos(false));
    }, [provider, hasGithub, hasGitlab, readOnly]);

    useEffect(() => {
        if (classification === 'PLUGIN' && !manualRepo) {
            fetchRepos();
        }
    }, [classification, provider, fetchRepos, manualRepo]);

    useEffect(() => {
        const handleClick = (e: MouseEvent) => {
            if (repoDropdownRef.current && !repoDropdownRef.current.contains(e.target as Node)) {
                setRepoDropdownOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, []);

    const toggleTag = (tag: string) => {
        if (readOnly) return;
        setMetaData(prev => ({
            ...prev,
            tags: prev.tags.includes(tag) ? prev.tags.filter(t => t !== tag) : [...prev.tags, tag]
        }));
    };

    const handleInvite = async () => {
        if (readOnly || !modData || !inviteUsername) return;
        setIsInviting(true);
        try {
            await api.post(`/projects/${modData.id}/invite`, null, { params: { username: inviteUsername } });
            onShowStatus('success', 'Invite Sent', `Invited ${inviteUsername} to contribute.`);
            setInviteUsername('');
            setModData(prev => prev ? {...prev, pendingInvites: [...(prev.pendingInvites || []), inviteUsername]} : null);
        } catch (e: any) {
            onShowStatus('error', 'Invite Failed', e.response?.data || "Could not invite user.");
        } finally {
            setIsInviting(false);
        }
    };

    const handleRemoveContributor = async (username: string, isPending: boolean) => {
        if (readOnly || !modData) return;
        try {
            await api.delete(`/projects/${modData.id}/contributors/${username}`);
            if (isPending) {
                setModData(prev => prev ? {...prev, pendingInvites: prev.pendingInvites?.filter(u => u !== username)} : null);
            } else {
                setModData(prev => prev ? {...prev, contributors: prev.contributors?.filter(u => u !== username)} : null);
            }
            onShowStatus('success', 'Removed', `${username} has been removed.`);
        } catch (e: any) {
            onShowStatus('error', 'Error', "Failed to remove user.");
        }
    };

    const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>, type: 'icon' | 'banner') => {
        if (readOnly) return;
        if (e.target.files && e.target.files[0]) {
            const file = e.target.files[0];
            setTempImage(URL.createObjectURL(file));
            setCropType(type);
            setCropperOpen(true);
            e.target.value = '';
        }
    };

    const handleCropComplete = (croppedFile: File) => {
        const preview = URL.createObjectURL(croppedFile);
        if (cropType === 'icon') {
            setMetaData(p => ({ ...p, iconFile: croppedFile, iconPreview: preview }));
        } else {
            setBannerFile(croppedFile);
            setBannerPreview(preview);
        }
        setCropperOpen(false);
        setTempImage(null);
    };

    const bgStyle = bannerPreview
        ? { backgroundImage: `url(${bannerPreview})` }
        : modData?.bannerUrl
            ? { backgroundImage: `url(${modData.bannerUrl.startsWith('/api') ? BACKEND_URL + modData.bannerUrl : modData.bannerUrl})` }
            : { backgroundImage: 'linear-gradient(to bottom right, #1e293b, #0f172a)' };

    const hasBanner = !!(bannerPreview || modData?.bannerUrl);
    const iconSrc = metaData.iconPreview ? metaData.iconPreview : modData?.imageUrl ? (modData.imageUrl.startsWith('/api') ? `${BACKEND_URL}${modData.imageUrl}` : modData.imageUrl) : null;
    const filteredRepos = repos.filter(r => (r.name || '').toLowerCase().includes(repoSearch.toLowerCase()));

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 relative pb-20 overflow-x-hidden">

            {cropperOpen && tempImage && (
                <ImageCropperModal
                    imageSrc={tempImage}
                    aspect={cropType === 'banner' ? 3 : 1}
                    onCancel={() => { setCropperOpen(false); setTempImage(null); }}
                    onCropComplete={handleCropComplete}
                />
            )}

            {showPublishConfirm && handlePublish && (
                <StatusModal
                    type="info"
                    title="Submit for Verification?"
                    message="Your project will be submitted to the content verification team. Once approved, it will be visible to everyone."
                    onClose={() => setShowPublishConfirm(false)}
                    actionLabel="Submit Now"
                    onAction={() => { setShowPublishConfirm(false); handlePublish(); }}
                    secondaryLabel="Cancel"
                />
            )}
            {showRevertConfirm && handleRevert && (
                <StatusModal
                    type="warning"
                    title="Revert to Draft?"
                    message="Reverting to draft will remove this project from the verification queue. You will need to resubmit it later."
                    onClose={() => setShowRevertConfirm(false)}
                    actionLabel="Revert"
                    onAction={() => { setShowRevertConfirm(false); handleRevert(); }}
                    secondaryLabel="Cancel"
                />
            )}
            {showArchiveConfirm && handleArchive && (
                <StatusModal
                    type="warning"
                    title="Archive Project?"
                    message="Archiving will make the project read-only. It remains visible publicly, but cannot be modified until unarchived."
                    onClose={() => setShowArchiveConfirm(false)}
                    actionLabel="Archive"
                    onAction={() => { setShowArchiveConfirm(false); handleArchive(); }}
                    secondaryLabel="Cancel"
                />
            )}
            {showUnlistConfirm && handleUnlist && (
                <StatusModal
                    type="warning"
                    title="Unlist Project?"
                    message="Unlisting will hide the project from search results. Direct links will still work. Are you sure?"
                    onClose={() => setShowUnlistConfirm(false)}
                    actionLabel="Unlist"
                    onAction={() => { setShowUnlistConfirm(false); handleUnlist(); }}
                    secondaryLabel="Cancel"
                />
            )}
            {showRestoreConfirm && handleRestore && (
                <StatusModal
                    type="info"
                    title="Restore Project?"
                    message="This will make the project publicly visible and editable again."
                    onClose={() => setShowRestoreConfirm(false)}
                    actionLabel="Restore"
                    onAction={() => { setShowRestoreConfirm(false); handleRestore(); }}
                    secondaryLabel="Cancel"
                />
            )}
            {showDeleteConfirm && handleDelete && (
                <StatusModal
                    type="error"
                    title="Delete Project?"
                    message="This action is PERMANENT. All versions, reviews, and data will be wiped. This cannot be undone."
                    onClose={() => setShowDeleteConfirm(false)}
                    actionLabel="DELETE PERMANENTLY"
                    onAction={() => { setShowDeleteConfirm(false); handleDelete(); }}
                    secondaryLabel="Cancel"
                />
            )}
            {showDeleteVersionConfirm && handleDeleteVersion && (
                <StatusModal
                    type="error"
                    title="Delete Version?"
                    message="Are you sure you want to delete this version? This cannot be undone."
                    onClose={() => setShowDeleteVersionConfirm(null)}
                    actionLabel="Delete"
                    onAction={() => { if(showDeleteVersionConfirm) handleDeleteVersion(showDeleteVersionConfirm); setShowDeleteVersionConfirm(null); }}
                    secondaryLabel="Cancel"
                />
            )}

            <div className="fixed inset-0 w-full h-[60vh] z-0 overflow-hidden pointer-events-none">
                <div className="w-full h-full bg-cover bg-center opacity-60 scale-105 blur-sm transition-all duration-1000" style={bgStyle}></div>
                <div className="absolute inset-0 bg-gradient-to-b from-slate-50/50 dark:from-slate-950/50 via-slate-50/90 dark:via-slate-950/90 to-slate-50 dark:to-slate-950"></div>
            </div>

            <div className="relative w-full aspect-[3/1] bg-slate-800 overflow-hidden group">
                <div className={`w-full h-full bg-cover bg-center transition-opacity duration-300 ${hasBanner ? 'opacity-100' : 'opacity-0'}`} style={bgStyle}></div>
                <div className="absolute inset-0 bg-gradient-to-t from-slate-50/90 dark:from-slate-950/90 to-transparent" />

                {!readOnly && (
                    <label
                        className={`cursor-pointer transition-all duration-300 ${
                            hasBanner
                                ? "absolute top-6 right-6 z-30 bg-black/60 hover:bg-black/80 text-white px-4 py-2 rounded-xl text-xs font-bold border border-white/20 backdrop-blur-sm shadow-lg hover:scale-105"
                                : "absolute inset-0 z-30 flex flex-col items-center justify-center m-6 rounded-2xl border-2 border-dashed border-white/10 hover:border-white/30 bg-white/5 hover:bg-white/10 group/banner"
                        }`}
                    >
                        <input type="file" accept="image/png, image/jpeg, image/webp, image/gif" onChange={e => handleFileSelect(e, 'banner')} className="hidden" />
                        {hasBanner ? (
                            <div className="flex flex-col items-end">
                                <div className="flex items-center gap-2">
                                    <ImageIcon className="w-4 h-4" /> Change Banner
                                </div>
                                <span className="text-[10px] font-medium text-white/50">Rec: 1920x640</span>
                            </div>
                        ) : (
                            <>
                                <div className="w-16 h-16 rounded-full bg-white/5 flex items-center justify-center mb-4 group-hover/banner:scale-110 transition-transform">
                                    <Plus className="w-8 h-8 text-white/50 group-hover/banner:text-white transition-colors" />
                                </div>
                                <span className="text-lg font-bold text-white/80 group-hover/banner:text-white">Upload Project Banner</span>
                                <span className="text-xs font-medium text-white/40 mt-1 group-hover/banner:text-white/60">Recommended: 1920x640 (3:1)</span>
                            </>
                        )}
                    </label>
                )}
            </div>

            <div className="max-w-7xl min-[1600px]:max-w-[100rem] mx-auto px-4 relative z-50 -mt-32 transition-[max-width] duration-300">
                <div className={`bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl shadow-2xl min-h-[80vh] relative z-10`}>
                    <div className="relative md:p-12 md:pb-6 border-b border-slate-200 dark:border-white/5">
                        <div className="flex flex-col md:flex-row gap-8 items-start relative z-10">

                            <div className="flex-shrink-0 -mt-20 relative z-20">
                                <label className={`block w-48 h-48 rounded-[2rem] bg-slate-100 dark:bg-slate-900 shadow-2xl overflow-hidden border-[6px] border-white dark:border-slate-900 group relative ${!readOnly ? 'cursor-pointer' : ''}`}>
                                    <input type="file" disabled={readOnly} accept="image/png, image/jpeg, image/webp, image/gif" onChange={e => handleFileSelect(e, 'icon')} className="hidden" />
                                    {iconSrc ? (
                                        <img src={iconSrc} alt="Icon" className={`w-full h-full object-cover transition-transform ${!readOnly ? 'group-hover:scale-105' : ''}`} />
                                    ) : (
                                        <div className="w-full h-full flex flex-col items-center justify-center bg-slate-200 dark:bg-slate-800 text-slate-500 gap-2">
                                            <ImageIcon className="w-10 h-10 opacity-50" />
                                            <span className="text-[10px] font-bold uppercase tracking-widest opacity-50">512x512</span>
                                        </div>
                                    )}
                                    {!readOnly && (
                                        <div className="absolute inset-0 bg-black/50 flex flex-col items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200 backdrop-blur-[2px]">
                                            <ImageIcon className="w-8 h-8 text-white mb-2" />
                                            <span className="text-xs font-bold text-white">Change Icon</span>
                                            <span className="text-[10px] font-medium text-white/70">Rec: 512x512</span>
                                        </div>
                                    )}
                                </label>
                            </div>

                            <div className="flex-1 min-w-0 flex flex-col justify-end pt-2">

                                {isPending && (
                                    <div className="mb-6 bg-orange-500/10 border border-orange-500/20 px-4 py-3 rounded-xl flex flex-col sm:flex-row items-center justify-between gap-4">
                                        <div className="flex items-center gap-3 text-orange-600 dark:text-orange-400 font-bold text-sm">
                                            <Lock className="w-5 h-5 flex-shrink-0" />
                                            <span>Pending Verification - Read Only</span>
                                        </div>
                                        {handleRevert && (
                                            <button
                                                onClick={() => setShowRevertConfirm(true)}
                                                className="bg-orange-500 hover:bg-orange-600 text-white px-4 py-2 rounded-lg text-xs font-bold transition-all shadow-md active:scale-95 flex items-center gap-2 whitespace-nowrap"
                                            >
                                                <Undo2 className="w-4 h-4" />
                                                Revert to Draft
                                            </button>
                                        )}
                                    </div>
                                )}

                                {isArchived && (
                                    <div className="mb-6 bg-yellow-500/10 border border-yellow-500/20 px-4 py-3 rounded-xl flex flex-col sm:flex-row items-center justify-between gap-4">
                                        <div className="flex items-center gap-3 text-yellow-600 dark:text-yellow-400 font-bold text-sm">
                                            <Archive className="w-5 h-5 flex-shrink-0" />
                                            <span>Archived Project - Read Only</span>
                                        </div>
                                        {handleRestore && (
                                            <button
                                                onClick={() => setShowRestoreConfirm(true)}
                                                className="bg-yellow-500 hover:bg-yellow-600 text-white px-4 py-2 rounded-lg text-xs font-bold transition-all shadow-md active:scale-95 flex items-center gap-2 whitespace-nowrap"
                                            >
                                                <RotateCcw className="w-4 h-4" />
                                                Restore Project
                                            </button>
                                        )}
                                    </div>
                                )}

                                {isUnlisted && (
                                    <div className="mb-6 bg-blue-500/10 border border-blue-500/20 px-4 py-3 rounded-xl flex flex-col sm:flex-row items-center justify-between gap-4">
                                        <div className="flex items-center gap-3 text-blue-600 dark:text-blue-400 font-bold text-sm">
                                            <EyeOff className="w-5 h-5 flex-shrink-0" />
                                            <span>Unlisted - Hidden from Search</span>
                                        </div>
                                        {handleRestore && (
                                            <button
                                                onClick={() => setShowRestoreConfirm(true)}
                                                className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg text-xs font-bold transition-all shadow-md active:scale-95 flex items-center gap-2 whitespace-nowrap"
                                            >
                                                <Globe className="w-4 h-4" />
                                                Make Public
                                            </button>
                                        )}
                                    </div>
                                )}

                                <div className="flex flex-wrap items-end justify-between gap-4 mb-4">
                                    <div className="flex-1">
                                        <div className="flex items-center gap-3 mb-2">
                                            <input value={metaData.title} disabled={readOnly} onChange={e => setMetaData({...metaData, title: e.target.value})}
                                                   className={`text-4xl md:text-5xl font-black text-slate-900 dark:text-white tracking-tighter bg-transparent border-b border-transparent outline-none pb-1 placeholder:text-slate-400 dark:placeholder:text-slate-600 w-full md:w-auto ${!readOnly ? 'hover:border-slate-300 dark:hover:border-white/20 focus:border-modtale-accent' : 'cursor-not-allowed opacity-80'}`} placeholder="Project Title"/>
                                        </div>
                                        <input value={metaData.summary} disabled={readOnly} onChange={e => setMetaData({...metaData, summary: e.target.value})}
                                               className={`text-lg text-slate-600 dark:text-slate-300 font-medium bg-transparent border-b border-transparent outline-none pb-1 placeholder:text-slate-400 dark:placeholder:text-slate-500 w-full md:max-w-2xl ${!readOnly ? 'hover:border-slate-300 dark:hover:border-white/20 focus:border-modtale-accent' : 'cursor-not-allowed opacity-80'}`} placeholder="Short summary..."/>
                                    </div>

                                    {!readOnly && (
                                        <div className="flex items-center gap-3 relative">
                                            <button onClick={() => handleSave(false)} disabled={isLoading} className="h-10 min-w-[100px] px-5 rounded-xl border border-slate-200 dark:border-white/10 bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-200 dark:hover:bg-white/10 font-bold flex items-center justify-center gap-2 transition-all">
                                                {isLoading ? <Spinner className="w-4 h-4"/> : <><Save className="w-4 h-4" /> Save</>}
                                            </button>

                                            {handlePublish && (
                                                <div className="relative" onMouseEnter={() => setShowReqs(true)} onMouseLeave={() => setShowReqs(false)}>
                                                    <button
                                                        onClick={() => setShowPublishConfirm(true)}
                                                        disabled={isLoading || !isPublishable}
                                                        className="h-10 min-w-[120px] bg-green-500 hover:bg-green-600 disabled:bg-slate-200 dark:disabled:bg-slate-700 disabled:text-slate-400 dark:disabled:text-slate-500 text-white px-6 rounded-xl font-black flex items-center justify-center gap-2 shadow-lg shadow-green-500/20 transition-all active:scale-95"
                                                    >
                                                        <UploadCloud className="w-5 h-5" /> Submit for Verification
                                                    </button>

                                                    {showReqs && !isPublishable && (
                                                        <div className="absolute top-full right-0 mt-2 w-64 bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl p-4 z-50 animate-in fade-in slide-in-from-top-2">
                                                            <h4 className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-3">Requirements</h4>
                                                            <ul className="space-y-2 text-sm text-slate-700 dark:text-slate-300">
                                                                <li className={`flex items-center gap-2 ${hasSummary ? 'text-green-500' : 'text-red-400'}`}>
                                                                    {hasSummary ? <Check className="w-4 h-4" /> : <X className="w-4 h-4" />} Short Summary (10+ chars)
                                                                </li>
                                                                <li className={`flex items-center gap-2 ${hasTags ? 'text-green-500' : 'text-red-400'}`}>
                                                                    {hasTags ? <Check className="w-4 h-4" /> : <X className="w-4 h-4" />} At least 1 Tag
                                                                </li>
                                                                {isPlugin && metaData.repositoryUrl && !repoValid && (
                                                                    <li className="flex items-center gap-2 text-red-400">
                                                                        <X className="w-4 h-4" /> Valid Repository
                                                                    </li>
                                                                )}
                                                                {!isModpack && (
                                                                    <li className={`flex items-center gap-2 ${hasLicense ? 'text-green-500' : 'text-red-400'}`}>
                                                                        {hasLicense ? <Check className="w-4 h-4" /> : <X className="w-4 h-4" />} License Selected
                                                                    </li>
                                                                )}
                                                                <li className={`flex items-center gap-2 ${hasVersion ? 'text-green-500' : 'text-red-400'}`}>
                                                                    {hasVersion ? <Check className="w-4 h-4" /> : <X className="w-4 h-4" />}
                                                                    {isModpack ? "2+ Dependencies" : "Uploaded Version"}
                                                                </li>
                                                            </ul>
                                                        </div>
                                                    )}
                                                </div>
                                            )}
                                        </div>
                                    )}
                                </div>

                                <div className="flex items-center gap-1 mt-6 border-b border-slate-200 dark:border-white/5">
                                    <button onClick={() => setActiveTab('details')} className={`px-6 py-3 text-sm font-bold border-b-2 transition-colors flex items-center gap-2 ${activeTab === 'details' ? 'border-modtale-accent text-slate-900 dark:text-white' : 'border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'}`}>
                                        <LayoutTemplate className="w-4 h-4"/> Details & Metadata
                                    </button>
                                    <button onClick={() => setActiveTab('files')} className={`px-6 py-3 text-sm font-bold border-b-2 transition-colors flex items-center gap-2 ${activeTab === 'files' ? 'border-modtale-accent text-slate-900 dark:text-white' : 'border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'}`}>
                                        <UploadCloud className="w-4 h-4"/> Files & Versions <span className="bg-slate-200 dark:bg-white/10 text-xs px-1.5 py-0.5 rounded-md ml-1">{modData?.versions?.length || 0}</span>
                                    </button>
                                    <button onClick={() => setActiveTab('settings')} className={`px-6 py-3 text-sm font-bold border-b-2 transition-colors flex items-center gap-2 ${activeTab === 'settings' ? 'border-modtale-accent text-slate-900 dark:text-white' : 'border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'}`}>
                                        <Settings className="w-4 h-4"/> Settings
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="flex flex-col lg:grid lg:grid-cols-12 min-h-[500px]">
                        <div className="lg:col-span-8 p-6 md:p-12 md:border-r md:border-slate-200 md:dark:border-white/5">
                            {activeTab === 'details' && (
                                <div className="h-full flex flex-col">
                                    <div className="flex items-center justify-between mb-4 pb-2 border-b border-slate-200 dark:border-white/5">
                                        <h3 className="text-xs font-bold text-slate-500 uppercase tracking-widest flex items-center gap-2"><FileText className="w-3 h-3"/> About the Project</h3>
                                        {!readOnly && (
                                            <div className="flex bg-slate-100 dark:bg-slate-950/50 rounded-lg p-1 border border-slate-200 dark:border-white/10">
                                                <button onClick={() => setEditorMode('write')} className={`px-4 py-1.5 text-xs font-bold rounded-md transition-colors ${editorMode === 'write' ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'}`}>Write</button>
                                                <button onClick={() => setEditorMode('preview')} className={`px-4 py-1.5 text-xs font-bold rounded-md transition-colors flex items-center gap-1 ${editorMode === 'preview' ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'}`}><Eye className="w-3 h-3"/> Preview</button>
                                            </div>
                                        )}
                                    </div>

                                    {editorMode === 'write' && !readOnly ? (
                                        <textarea
                                            value={metaData.description}
                                            onChange={e => setMetaData({...metaData, description: e.target.value})}
                                            className="flex-1 w-full h-full min-h-[400px] bg-transparent border-none outline-none text-slate-900 dark:text-slate-300 placeholder:text-slate-400 dark:placeholder:text-slate-600 resize-none font-mono text-sm"
                                            placeholder="# Use Markdown to describe your project...\n\n* Features\n* Installation\n* Credits"
                                        />
                                    ) : (
                                        <div className="prose dark:prose-invert prose-lg max-w-none prose-a:text-modtale-accent prose-a:no-underline hover:prose-a:underline prose-img:rounded-2xl prose-headings:font-black prose-p:text-slate-600 dark:prose-p:text-slate-300 prose-p:leading-relaxed min-h-[400px]">
                                            {metaData.description ? <ReactMarkdown rehypePlugins={[rehypeRaw, rehypeSanitize]}>{metaData.description}</ReactMarkdown> : <p className="text-slate-500 italic">Nothing to preview yet.</p>}
                                        </div>
                                    )}
                                </div>
                            )}

                            {activeTab === 'files' && (
                                <div className="space-y-8">
                                    {!readOnly && (
                                        <div>
                                            <h3 className="text-xs font-bold text-slate-500 uppercase tracking-widest mb-3 flex items-center gap-2"><UploadCloud className="w-3 h-3"/> Upload New Version</h3>
                                            {canUpload ? (
                                                <div className="bg-slate-50 dark:bg-slate-950/30 p-6 rounded-2xl border border-slate-200 dark:border-white/5">
                                                    <VersionFields data={versionData} onChange={setVersionData} isModpack={classification === 'MODPACK'} projectType={typeof classification === 'string' ? classification : 'PLUGIN'} disabled={readOnly} />
                                                    <div className="mt-6 flex justify-end">
                                                        <button onClick={handleUploadVersion} disabled={isLoading || readOnly} className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 h-12 rounded-xl font-bold transition-colors shadow-lg shadow-modtale-accent/20 flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed">
                                                            {isLoading ? <Spinner className="w-5 h-5"/> : <UploadCloud className="w-5 h-5" />} Upload Version
                                                        </button>
                                                    </div>
                                                </div>
                                            ) : (
                                                <div className="bg-amber-500/5 border border-amber-500/10 rounded-2xl p-6 flex flex-col items-center justify-center text-center">
                                                    <AlertCircle className="w-8 h-8 text-amber-500 mb-3" />
                                                    <h4 className="text-amber-500 font-bold mb-1">Draft Limit Reached</h4>
                                                    <p className="text-sm text-slate-600 dark:text-slate-400 max-w-md">
                                                        Unpublished drafts are limited to one version file. To upload more versions, please <strong>Submit</strong> your project for verification.
                                                    </p>
                                                </div>
                                            )}
                                        </div>
                                    )}

                                    {modData?.versions && modData.versions.length > 0 && (
                                        <div>
                                            <h3 className="text-xs font-bold text-slate-500 uppercase tracking-widest mb-3 flex items-center gap-2"><CheckCircle2 className="w-3 h-3"/> Version History</h3>
                                            <div className="space-y-2">
                                                {modData.versions.map(v => (
                                                    <div key={v.id} className="bg-slate-50 dark:bg-slate-950/30 border border-slate-200 dark:border-white/5 rounded-xl group hover:border-slate-300 dark:hover:border-white/10 transition-colors overflow-hidden">
                                                        <div className="flex items-center justify-between p-4 cursor-pointer" onClick={() => setExpandedVersionId(expandedVersionId === v.id ? null : v.id)}>
                                                            <div>
                                                                <div className="flex items-center gap-3">
                                                                    <span className="font-mono font-bold text-slate-900 dark:text-white text-lg">{v.versionNumber}</span>
                                                                    {v.channel !== 'RELEASE' && <span className={`text-[10px] font-bold px-2 py-0.5 rounded border ${v.channel === 'BETA' ? 'text-blue-400 border-blue-400/30 bg-blue-400/10' : 'text-orange-400 border-orange-400/30 bg-orange-400/10'}`}>{v.channel}</span>}
                                                                </div>
                                                                <div className="text-xs text-slate-500 mt-1">
                                                                    {v.gameVersions?.join(', ') || 'Unknown Version'} • {new Date(v.releaseDate).toLocaleDateString()}
                                                                </div>
                                                            </div>
                                                            <div className="flex items-center gap-4">
                                                                <div className="text-slate-500 text-sm font-bold hidden sm:block">{v.downloadCount} downloads</div>
                                                                <div className="flex items-center gap-2">
                                                                    {!readOnly && handleDeleteVersion && (
                                                                        <button
                                                                            onClick={(e) => { e.stopPropagation(); setShowDeleteVersionConfirm(v.id); }}
                                                                            disabled={(!isDraft && modData.versions.length <= 1) || readOnly}
                                                                            className={`p-2 rounded-lg transition-colors ${
                                                                                (!isDraft && modData.versions.length <= 1) || readOnly
                                                                                    ? "text-slate-300 dark:text-slate-700 cursor-not-allowed"
                                                                                    : "text-slate-500 hover:text-red-500 hover:bg-red-500/10"
                                                                            }`}
                                                                            title={
                                                                                !isDraft && modData.versions.length <= 1
                                                                                    ? "Cannot delete the only version of a published project."
                                                                                    : "Delete Version"
                                                                            }
                                                                        >
                                                                            <Trash2 className="w-4 h-4" />
                                                                        </button>
                                                                    )}
                                                                    {expandedVersionId === v.id ? <ChevronUp className="w-5 h-5 text-slate-400" /> : <ChevronDown className="w-5 h-5 text-slate-400" />}
                                                                </div>
                                                            </div>
                                                        </div>

                                                        {expandedVersionId === v.id && (
                                                            <div className="px-6 pb-6 pt-2 border-t border-slate-200 dark:border-white/5 bg-slate-100/50 dark:bg-black/10">
                                                                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                                                    <div>
                                                                        <h4 className="text-xs font-bold text-slate-500 uppercase tracking-widest mb-2 flex items-center gap-2"><FileText className="w-3 h-3" /> Changelog</h4>
                                                                        <div className="text-sm text-slate-600 dark:text-slate-300 bg-white dark:bg-black/20 p-3 rounded-lg border border-slate-200 dark:border-white/5 min-h-[100px] max-h-[200px] overflow-y-auto custom-scrollbar">
                                                                            {v.changelog ? <ReactMarkdown rehypePlugins={[rehypeRaw, rehypeSanitize]} className="prose dark:prose-invert prose-sm max-w-none">{v.changelog}</ReactMarkdown> : <span className="italic text-slate-400">No changelog provided.</span>}
                                                                        </div>
                                                                    </div>
                                                                    <div>
                                                                        <h4 className="text-xs font-bold text-slate-500 uppercase tracking-widest mb-2 flex items-center gap-2">
                                                                            {isModpack ? <Box className="w-3 h-3" /> : <LinkIcon className="w-3 h-3" />}
                                                                            {isModpack ? "Included Mods" : "Dependencies"}
                                                                        </h4>
                                                                        <div className="space-y-2 max-h-[200px] overflow-y-auto custom-scrollbar bg-white dark:bg-black/20 p-3 rounded-lg border border-slate-200 dark:border-white/5">
                                                                            {v.dependencies && v.dependencies.length > 0 ? v.dependencies.map((dep, idx) => (
                                                                                <div key={idx} className="flex justify-between items-center text-xs p-2 rounded hover:bg-slate-50 dark:hover:bg-white/5">
                                                                                    <div className="font-bold text-slate-700 dark:text-slate-300 truncate max-w-[70%]">{dep.modTitle || dep.modId}</div>
                                                                                    <div className="flex items-center gap-2 text-slate-500">
                                                                                        {!isModpack && (
                                                                                            <span className={`text-[10px] uppercase font-bold px-1.5 py-0.5 rounded ${dep.isOptional ? 'bg-slate-100 text-slate-500 dark:bg-white/10' : 'bg-amber-100 text-amber-600 dark:bg-amber-900/30'}`}>
                                                                                                {dep.isOptional ? 'Optional' : 'Required'}
                                                                                            </span>
                                                                                        )}
                                                                                        <span className="font-mono bg-slate-200 dark:bg-white/10 px-1.5 rounded">{dep.versionNumber}</span>
                                                                                    </div>
                                                                                </div>
                                                                            )) : (
                                                                                <div className="text-center text-xs text-slate-400 italic py-4">No dependencies.</div>
                                                                            )}
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        )}
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}

                            {activeTab === 'settings' && (
                                <div className="space-y-6">
                                    <div className="bg-slate-50 dark:bg-slate-950/30 p-6 rounded-2xl border border-slate-200 dark:border-white/10">

                                        <div className="mb-6 pb-6 border-b border-slate-200 dark:border-white/5">
                                            <div className="flex flex-col gap-2">
                                                <div>
                                                    <h3 className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-2">
                                                        <Link2 className="w-4 h-4 text-slate-500" /> Project URL Slug
                                                    </h3>
                                                    <p className="text-xs text-slate-500 mt-1">Customize the URL for your project. Leave blank to use the default ID.</p>
                                                </div>
                                                <div className="flex items-center w-full bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl overflow-hidden focus-within:ring-2 focus-within:ring-modtale-accent transition-all">
                                                    <div className="px-4 py-2 bg-slate-50 dark:bg-white/5 border-r border-slate-200 dark:border-white/10 text-slate-500 text-sm font-mono whitespace-nowrap select-none">
                                                        {getUrlPrefix()}
                                                    </div>
                                                    <input
                                                        disabled={readOnly}
                                                        value={metaData.slug || ''}
                                                        onChange={handleSlugChange}
                                                        className={`flex-1 bg-transparent border-none px-4 py-2 text-sm font-mono text-slate-900 dark:text-white focus:outline-none placeholder:text-slate-400 ${slugError ? 'text-red-500' : ''}`}
                                                        placeholder={createSlug(metaData.title, modData?.id || 'id')}
                                                    />
                                                </div>
                                                {slugError && <p className="text-[10px] text-red-500 font-bold">{slugError}</p>}
                                            </div>
                                        </div>

                                        <div className="flex items-center justify-between mb-4">
                                            <div>
                                                <h3 className="text-sm font-bold text-slate-900 dark:text-white">Allow Modpack Inclusion</h3>
                                                <p className="text-xs text-slate-500 mt-1">Allow other creators to include this project in their modpacks.</p>
                                            </div>
                                            <button
                                                disabled={readOnly}
                                                onClick={() => setModData(prev => prev ? {...prev, allowModpacks: !prev.allowModpacks} : null)}
                                                className={`transition-colors ${readOnly ? 'opacity-50 cursor-not-allowed text-slate-400' : modData?.allowModpacks ? 'text-green-500' : 'text-slate-600'}`}
                                            >
                                                {modData?.allowModpacks ? <ToggleRight className="w-8 h-8" /> : <ToggleLeft className="w-8 h-8" />}
                                            </button>
                                        </div>

                                        <div className="pt-4 border-t border-slate-200 dark:border-white/5">
                                            <h3 className="text-sm font-bold text-slate-900 dark:text-white mb-2">Contributors</h3>
                                            <div className="flex gap-2 mb-4">
                                                <input
                                                    disabled={readOnly}
                                                    value={inviteUsername}
                                                    onChange={e => setInviteUsername(e.target.value)}
                                                    placeholder="Username"
                                                    className="flex-1 bg-slate-100 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-lg px-3 py-2 text-sm text-slate-900 dark:text-white focus:outline-none focus:border-modtale-accent disabled:opacity-50 disabled:cursor-not-allowed"
                                                />
                                                <button onClick={handleInvite} disabled={readOnly || isInviting || !inviteUsername} className="bg-modtale-accent text-white px-4 py-2 rounded-lg font-bold text-xs flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed">
                                                    {isInviting ? <Spinner className="w-3 h-3"/> : <UserPlus className="w-3 h-3" />} Invite
                                                </button>
                                            </div>
                                            <div className="space-y-2">
                                                {modData?.contributors?.map(u => (
                                                    <div key={u} className="flex justify-between items-center p-2 bg-slate-100 dark:bg-black/20 rounded-lg border border-slate-200 dark:border-white/5">
                                                        <span className="text-sm text-slate-700 dark:text-slate-300 font-bold">{u}</span>
                                                        <button disabled={readOnly} onClick={() => {if(handleDeleteVersion) handleRemoveContributor(u, false)}} className={`text-slate-500 ${!readOnly ? 'hover:text-red-500' : 'opacity-50 cursor-not-allowed'}`}><X className="w-4 h-4"/></button>
                                                    </div>
                                                ))}
                                                {modData?.pendingInvites?.map(u => (
                                                    <div key={u} className="flex justify-between items-center p-2 bg-slate-100 dark:bg-black/20 rounded-lg border border-slate-200 dark:border-white/5 border-dashed">
                                                        <span className="text-sm text-slate-500 font-bold italic">{u} (Pending)</span>
                                                        <button disabled={readOnly} onClick={() => {if(handleDeleteVersion) handleRemoveContributor(u, true)}} className={`text-slate-500 ${!readOnly ? 'hover:text-red-500' : 'opacity-50 cursor-not-allowed'}`}><X className="w-4 h-4"/></button>
                                                    </div>
                                                ))}
                                                {(!modData?.contributors?.length && !modData?.pendingInvites?.length) && <p className="text-xs text-slate-500 italic">No contributors.</p>}
                                            </div>
                                        </div>
                                    </div>

                                    {!readOnly && (
                                        <div className="bg-red-500/5 p-6 rounded-2xl border border-red-500/20 space-y-4">
                                            <h3 className="text-sm font-bold text-red-500 mb-2">Danger Zone</h3>

                                            <div className="flex items-center justify-between">
                                                <div>
                                                    <h4 className="text-xs font-bold text-slate-900 dark:text-white">Unlist Project</h4>
                                                    <p className="text-[10px] text-slate-500 dark:text-slate-400">Hide from search results but keep accessible via link.</p>
                                                </div>
                                                <button onClick={() => setShowUnlistConfirm(true)} className="bg-slate-100 dark:bg-white/10 hover:bg-slate-200 dark:hover:bg-white/20 text-slate-700 dark:text-slate-300 px-4 py-2 rounded-lg text-xs font-bold flex items-center gap-2 transition-colors">
                                                    <EyeOff className="w-3 h-3" /> Unlist
                                                </button>
                                            </div>

                                            <div className="flex items-center justify-between">
                                                <div>
                                                    <h4 className="text-xs font-bold text-slate-900 dark:text-white">Archive Project</h4>
                                                    <p className="text-[10px] text-slate-500 dark:text-slate-400">Make read-only and mark as archived. Reversible.</p>
                                                </div>
                                                <button onClick={() => setShowArchiveConfirm(true)} className="bg-amber-100 dark:bg-amber-900/30 hover:bg-amber-200 dark:hover:bg-amber-900/50 text-amber-800 dark:text-amber-200 px-4 py-2 rounded-lg text-xs font-bold flex items-center gap-2 transition-colors">
                                                    <Archive className="w-3 h-3" /> Archive
                                                </button>
                                            </div>

                                            <div className="h-px bg-red-500/20 my-2"></div>

                                            <div className="flex items-center justify-between">
                                                <div>
                                                    <h4 className="text-xs font-bold text-red-500">Delete Project</h4>
                                                    <p className="text-[10px] text-slate-500 dark:text-slate-400">Permanently remove this project and all its data.</p>
                                                </div>
                                                <button onClick={() => setShowDeleteConfirm(true)} className="bg-red-500 hover:bg-red-600 text-white px-4 py-2 rounded-lg text-xs font-bold flex items-center gap-2 transition-colors">
                                                    <Trash2 className="w-3 h-3" /> Delete
                                                </button>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>

                        <div className="lg:col-span-4 p-6 md:p-12 space-y-4 bg-transparent border-t md:border-t-0 md:border-l border-slate-200 dark:border-white/5">
                            {(classification === 'PLUGIN' || classification === 'MODPACK') && (
                                <ModSidebar title="Repository Source" icon={GitMerge} defaultOpen={true}>
                                    <div className="bg-slate-50 dark:bg-slate-950/50 border border-slate-200 dark:border-white/10 rounded-xl p-2">
                                        <div className="flex bg-slate-200 dark:bg-black/40 rounded-lg p-1 mb-3">
                                            <button
                                                onClick={() => {
                                                    setManualRepo(false);
                                                    if (hasGithub) {
                                                        if (provider !== 'github') setRepos([]);
                                                        setProvider('github');
                                                    }
                                                }}
                                                disabled={readOnly || !hasGithub}
                                                className={`flex-1 py-1.5 text-[10px] font-bold rounded-md transition-all ${!manualRepo && provider === 'github' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-900 dark:hover:text-white disabled:opacity-30'}`}
                                            >
                                                GitHub
                                            </button>
                                            <button
                                                onClick={() => {
                                                    setManualRepo(false);
                                                    if (hasGitlab) {
                                                        if (provider !== 'gitlab') setRepos([]);
                                                        setProvider('gitlab');
                                                    }
                                                }}
                                                disabled={readOnly || !hasGitlab}
                                                className={`flex-1 py-1.5 text-[10px] font-bold rounded-md transition-all ${!manualRepo && provider === 'gitlab' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-900 dark:hover:text-white disabled:opacity-30'}`}
                                            >
                                                GitLab
                                            </button>
                                            <button
                                                onClick={() => setManualRepo(true)}
                                                disabled={readOnly}
                                                className={`flex-1 py-1.5 text-[10px] font-bold rounded-md transition-all ${manualRepo ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 hover:text-slate-900 dark:hover:text-white disabled:opacity-30'}`}
                                            >
                                                Link URL
                                            </button>
                                        </div>

                                        {manualRepo ? (
                                            <div className="relative">
                                                <input
                                                    value={metaData.repositoryUrl}
                                                    disabled={readOnly}
                                                    onChange={e => {
                                                        const val = e.target.value;
                                                        setMetaData({...metaData, repositoryUrl: val});
                                                        checkRepoUrl(val);
                                                    }}
                                                    onKeyDown={e => e.key === 'Enter' && checkRepoUrl(metaData.repositoryUrl)}
                                                    className={`w-full bg-slate-100 dark:bg-slate-900 border rounded-lg px-3 py-2 text-xs text-slate-900 dark:text-white focus:outline-none transition-all pr-8 ${metaData.repositoryUrl ? (repoValid ? 'border-green-500 focus:border-green-500' : 'border-red-500 focus:border-red-500') : 'border-slate-200 dark:border-white/10 focus:border-modtale-accent'} ${readOnly ? 'opacity-50 cursor-not-allowed' : ''}`}
                                                    placeholder="https://github.com/username/repo"
                                                />
                                                {metaData.repositoryUrl && (
                                                    <div className="absolute right-3 top-2.5">
                                                        {repoValid ? (
                                                            <CheckCircle2 className="w-3.5 h-3.5 text-green-500" />
                                                        ) : (
                                                            <X className="w-3.5 h-3.5 text-red-500" />
                                                        )}
                                                    </div>
                                                )}
                                                {metaData.repositoryUrl && !repoValid && (
                                                    <p className="text-[10px] text-red-400 mt-1.5 ml-1">Must be a valid GitHub or GitLab URL.</p>
                                                )}
                                            </div>
                                        ) : (!hasGithub && !hasGitlab) ? (
                                            <div className="text-center py-4 text-xs text-slate-500">Link account in settings.</div>
                                        ) : (
                                            <div className="relative" ref={repoDropdownRef}>
                                                <button disabled={readOnly} onClick={() => setRepoDropdownOpen(!repoDropdownOpen)} className={`w-full flex items-center justify-between bg-slate-100 dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-lg px-3 py-2 text-xs text-slate-900 dark:text-white transition-colors ${!readOnly ? 'hover:border-modtale-accent' : 'opacity-50 cursor-not-allowed'}`}>
                                                    <span className="truncate">{metaData.repositoryUrl || "Select Repo..."}</span>
                                                    <ChevronDown className="w-3 h-3 text-slate-500" />
                                                </button>
                                                {repoDropdownOpen && !readOnly && (
                                                    <div className="absolute top-full mt-2 w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-xl shadow-xl z-50 overflow-hidden">
                                                        <div className="p-2 border-b border-slate-200 dark:border-white/5 flex gap-2">
                                                            <input autoFocus value={repoSearch} onChange={e => setRepoSearch(e.target.value)} className="flex-1 bg-slate-100 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-md px-2 py-1 text-xs text-slate-900 dark:text-white focus:outline-none" placeholder="Filter..." />
                                                            <button onClick={fetchRepos} className="p-1 bg-slate-100 dark:bg-white/5 rounded hover:bg-slate-200 dark:hover:bg-white/10"><RefreshCw className={`w-3 h-3 text-slate-400 ${loadingRepos ? 'animate-spin' : ''}`} /></button>
                                                        </div>
                                                        <div className="max-h-48 overflow-y-auto p-1 custom-scrollbar">
                                                            {loadingRepos ? <div className="p-4 text-center"><Loader2 className="w-4 h-4 animate-spin mx-auto text-modtale-accent" /></div> : filteredRepos.length > 0 ? filteredRepos.map(r => (
                                                                <button key={r.url} onClick={() => {
                                                                    const url = r.html_url || r.url;
                                                                    setMetaData({...metaData, repositoryUrl: url});
                                                                    checkRepoUrl(url);
                                                                    setRepoDropdownOpen(false);
                                                                }} className="w-full text-left px-2 py-1.5 rounded hover:bg-slate-100 dark:hover:bg-white/10 text-xs text-slate-600 dark:text-slate-300 flex justify-between items-center group">
                                                                    <span className="font-mono">{r.name}</span>
                                                                    {metaData.repositoryUrl === (r.html_url || r.url) && <Check className="w-3 h-3 text-modtale-accent" />}
                                                                </button>
                                                            )) : <div className="p-2 text-center text-[10px] text-slate-500">No repos found</div>}
                                                        </div>
                                                    </div>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                </ModSidebar>
                            )}

                            {!isModpack && (
                                <ModSidebar title="License" icon={Scale} defaultOpen={false}>
                                    <div className="bg-slate-50 dark:bg-slate-950/50 border border-slate-200 dark:border-white/10 rounded-xl p-2 max-h-60 overflow-y-auto custom-scrollbar">
                                        {LICENSES.map(lic => (
                                            <button
                                                key={lic.id}
                                                disabled={readOnly}
                                                onClick={() => setMetaData({ ...metaData, license: lic.id })}
                                                className={`w-full text-left px-3 py-2 rounded-lg text-xs font-bold transition-all flex items-center justify-between group ${metaData.license === lic.id ? 'bg-modtale-accent text-white' : readOnly ? 'text-slate-400 opacity-50 cursor-not-allowed' : 'text-slate-500 hover:bg-slate-200 dark:hover:bg-white/10 hover:text-slate-900 dark:hover:text-white'}`}
                                            >
                                                <span>{lic.name}</span>
                                                {metaData.license === lic.id && <Check className="w-3 h-3" />}
                                            </button>
                                        ))}
                                    </div>
                                </ModSidebar>
                            )}

                            <ModSidebar title="Tags" icon={Tag} defaultOpen={false}>
                                <div className="flex flex-wrap gap-2 mb-4">
                                    {GLOBAL_TAGS.map(tag => (
                                        <button disabled={readOnly} key={tag} onClick={() => toggleTag(tag)} className={`px-2.5 py-1 rounded-lg text-[10px] font-bold border transition-all ${metaData.tags.includes(tag) ? 'bg-modtale-accent text-white border-modtale-accent' : readOnly ? 'bg-slate-100 dark:bg-slate-900/50 text-slate-400 border-slate-200 dark:border-white/10 cursor-not-allowed opacity-50' : 'bg-slate-100 dark:bg-slate-900/50 text-slate-500 dark:text-slate-400 border-slate-200 dark:border-white/10 hover:border-slate-300 dark:hover:border-white/30 hover:text-slate-900 dark:hover:text-white'}`}>
                                            {tag}
                                        </button>
                                    ))}
                                </div>
                            </ModSidebar>

                            <ModSidebar title="External Links" icon={LinkIcon} defaultOpen={false}>
                                <div className="space-y-3">
                                    <ThemedInput disabled={readOnly} label="Website" value={metaData.links.WEBSITE || ''} onChange={(e:any) => setMetaData({...metaData, links: {...metaData.links, WEBSITE: e.target.value}})} placeholder="https://..." />
                                    <ThemedInput disabled={readOnly} label="Wiki" value={metaData.links.WIKI || ''} onChange={(e:any) => setMetaData({...metaData, links: {...metaData.links, WIKI: e.target.value}})} placeholder="https://..." />
                                    <ThemedInput disabled={readOnly} label="Issues" value={metaData.links.ISSUE_TRACKER || ''} onChange={(e:any) => setMetaData({...metaData, links: {...metaData.links, ISSUE_TRACKER: e.target.value}})} placeholder="https://..." />
                                    <ThemedInput disabled={readOnly} label="Discord" value={metaData.links.DISCORD || ''} onChange={(e:any) => setMetaData({...metaData, links: {...metaData.links, DISCORD: e.target.value}})} placeholder="Invite Link" />
                                </div>
                            </ModSidebar>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};