import React, { useState, useEffect, useCallback, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/cjs/styles/prism';
import { useDropzone } from 'react-dropzone';
import {
    Save, UploadCloud, Link as LinkIcon, Tag,
    GitMerge, Settings,
    ToggleLeft, ToggleRight, Trash2, FileText, LayoutTemplate,
    UserPlus, Scale, Check, Copy, Link2, Edit2, X, ChevronDown, RefreshCw, Loader2, CheckCircle2, Eye, Maximize2,
    AlertCircle, Clock, Archive, Globe, EyeOff, Image, MessageSquare, ExternalLink
} from 'lucide-react';
import { Link } from 'react-router-dom';

import { api, BACKEND_URL } from '../../../utils/api';
import { LICENSES, GLOBAL_TAGS } from '../../../data/categories';
import type { Classification } from '../../../data/categories';
import { Spinner } from '@/components/ui/Spinner';
import { StatusModal } from '@/components/ui/StatusModal';
import { ProjectLayout, SidebarSection } from '@/components/resources/ProjectLayout.tsx';
import { createSlug } from '../../../utils/slug';
import type { Mod, User } from '../../../types';
import { ModCard } from '../ModCard';
import { VersionFields, ThemedInput } from './FormShared';
import type { MetadataFormData, VersionFormData } from './FormShared';

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
    handleGalleryUpload: (file: File) => Promise<void>;
    handleGalleryDelete: (url: string) => Promise<void>;
    isLoading: boolean;
    classification: Classification | string;
    currentUser: User | null;
    activeTab: 'details' | 'files' | 'gallery' | 'settings';
    setActiveTab: (tab: 'details' | 'files' | 'gallery' | 'settings') => void;
    onShowStatus: (type: 'success' | 'error', title: string, msg: string) => void;
    readOnly?: boolean;
}

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
    const [isInviting, setIsInviting] = useState(false);
    const [showPublishConfirm, setShowPublishConfirm] = useState(false);
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

    const isPlugin = classification === 'PLUGIN';
    const isModpack = classification === 'MODPACK';
    const hasTags = metaData.tags.length > 0;
    const hasSummary = metaData.summary && metaData.summary.length >= 10;
    const hasVersion = (modData?.versions?.length || 0) > 0;

    const hasLicense = isModpack || (!!metaData.license && (!isCustomLicense || !!metaData.links.LICENSE));

    const publishRequirements = [
        { label: 'Short Summary (10+ chars)', met: hasSummary },
        { label: 'At least one Tag', met: hasTags },
        { label: 'At least one Version uploaded', met: hasVersion },
        { label: 'License selected', met: hasLicense },
        { label: 'All changes saved', met: !isDirty }
    ];

    if (metaData.repositoryUrl) {
        publishRequirements.push({ label: 'Valid Repository URL', met: repoValid });
    }

    const isPublishable = publishRequirements.every(r => r.met);
    const metCount = publishRequirements.filter(r => r.met).length;

    const markDirty = () => !readOnly && !isDirty && setIsDirty(true);
    const checkRepoUrl = useCallback((url: string) => {
        if (!url) { setRepoValid(true); return true; }
        const isValid = /^https:\/\/(github\.com|gitlab\.com)\/[\w.-]+\/[\w.-]+$/.test(url);
        setRepoValid(isValid);
        return isValid;
    }, []);

    const hasGithub = currentUser?.connectedAccounts?.some(a => a.provider === 'github') || false;
    const hasGitlab = currentUser?.connectedAccounts?.some(a => a.provider === 'gitlab') || false;
    const [provider, setProvider] = useState<'github' | 'gitlab'>(hasGithub ? 'github' : (hasGitlab ? 'gitlab' : 'github'));

    const fetchRepos = useCallback(() => {
        if (readOnly || manualRepo) return;
        if ((provider === 'github' && !hasGithub) || (provider === 'gitlab' && !hasGitlab)) return;

        setLoadingRepos(true);
        const endpoint = provider === 'gitlab' ? '/user/repos/gitlab' : '/user/repos/github';

        api.get(endpoint)
            .then(res => setRepos(res.data || []))
            .catch(e => console.error(e))
            .finally(() => setLoadingRepos(false));
    }, [provider, hasGithub, hasGitlab, readOnly, manualRepo]);

    useEffect(() => { checkRepoUrl(metaData.repositoryUrl || ''); }, [metaData.repositoryUrl, checkRepoUrl]);

    useEffect(() => {
        if (!manualRepo && !readOnly) {
            fetchRepos();
        }
    }, [classification, provider, manualRepo, readOnly]);

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

    const handleSlugChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        markDirty();
        const val = e.target.value;
        setMetaData({...metaData, slug: val});
        if (!val) { setSlugError(null); return; }
        const slugRegex = /^[a-z0-9](?:[a-z0-9-]{1,48}[a-z0-9])?$/;
        if (!slugRegex.test(val)) setSlugError("Must be 3-50 chars, lowercase alphanumeric, no start/end dash.");
        else setSlugError(null);
    };

    const getUrlPrefix = () => {
        if (classification === 'MODPACK') return 'modtale.net/modpack/';
        if (classification === 'SAVE') return 'modtale.net/world/';
        return 'modtale.net/mod/';
    };

    const getProjectLink = () => {
        if (!modData?.id) return '#';
        const slug = metaData.slug || modData.slug || modData.id;
        if (classification === 'MODPACK') return `/modpack/${slug}`;
        if (classification === 'SAVE') return `/world/${slug}`;
        return `/mod/${slug}`;
    };

    const onGalleryDrop = useCallback((acceptedFiles: File[]) => {
        if (acceptedFiles[0] && !readOnly) handleGalleryUpload(acceptedFiles[0]);
    }, [readOnly, handleGalleryUpload]);

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
        author: modData?.author || currentUser?.username || 'You',
        imageUrl: metaData.iconPreview || modData?.imageUrl || '',
        bannerUrl: bannerPreview || modData?.bannerUrl || '',
        classification: (typeof classification === 'string' ? classification : 'PLUGIN') as any,
        categories: metaData.tags.length > 0 ? metaData.tags : (modData?.categories || ['Misc']),
        updatedAt: new Date().toISOString(),
        downloadCount: modData?.downloadCount || 0,
        favoriteCount: modData?.favoriteCount || 0,
        rating: modData?.rating || 0,
        sizeBytes: modData?.sizeBytes || 0,
        modIds: modData?.modIds || [],
        childProjectIds: modData?.childProjectIds || [],
        versions: modData?.versions || [],
        reviews: [],
        galleryImages: []
    };

    const MarkdownComponents = {
        code({node, inline, className, children, ...props}: any) {
            const match = /language-(\w+)/.exec(className || '')
            return !inline && match ? (
                <SyntaxHighlighter
                    {...props}
                    style={vscDarkPlus}
                    language={match[1]}
                    PreTag="div"
                    className="rounded-lg text-sm"
                >
                    {String(children).replace(/\n$/, '')}
                </SyntaxHighlighter>
            ) : (
                <code className={`${className || ''} bg-slate-100 dark:bg-white/10 px-1 py-0.5 rounded text-sm`} {...props}>
                    {children}
                </code>
            )
        },
        p({node, children, ...props}: any) {
            return <p className="my-2 [li>&]:my-0" {...props}>{children}</p>
        },
        li({node, children, ...props}: any) {
            return <li className="my-1 [&>p]:my-0" {...props}>{children}</li>
        },
        ul({node, children, ...props}: any) {
            return <ul className="list-disc pl-6 my-3" {...props}>{children}</ul>
        },
        ol({node, children, ...props}: any) {
            return <ol className="list-decimal pl-6 my-3" {...props}>{children}</ol>
        }
    };

    return (
        <>
            {modData?.status === 'PENDING' && (
                <div className="bg-blue-500/10 border-b border-blue-500/20 px-8 py-3 flex items-center justify-between backdrop-blur-sm sticky top-0 z-50">
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
            )}

            {modData?.status === 'ARCHIVED' && (
                <div className="bg-slate-800 border-b border-slate-700 px-8 py-3 flex items-center justify-between backdrop-blur-sm sticky top-0 z-50">
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
            )}

            {showPublishConfirm && handlePublish && <StatusModal type="info" title="Submit?" message="Submit for verification?" onClose={() => setShowPublishConfirm(false)} actionLabel="Submit" onAction={() => { setShowPublishConfirm(false); handlePublish(); }} secondaryLabel="Cancel" />}

            {showCardPreview && (
                <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200">
                    <div className="relative w-full max-w-4xl">
                        <button
                            onClick={() => setShowCardPreview(false)}
                            className="absolute -top-10 -right-2 md:-right-10 text-white/50 hover:text-white transition-colors z-10"
                        >
                            <X className="w-8 h-8" />
                        </button>
                        <div
                            className="cursor-default pointer-events-none select-none shadow-2xl w-full flex items-center justify-center"
                            onClickCapture={(e) => { e.preventDefault(); e.stopPropagation(); }}
                        >
                            <div
                                className="w-full"
                                style={{
                                    zoom: '1.5',
                                    MozTransform: 'scale(1.5)',
                                    MozTransformOrigin: 'center center'
                                }}
                            >
                                <ModCard
                                    mod={previewMod}
                                    isFavorite={false}
                                    onToggleFavorite={() => {}}
                                    isLoggedIn={false}
                                />
                            </div>
                        </div>
                    </div>
                    <div className="absolute inset-0 -z-10" onClick={() => setShowCardPreview(false)} />
                </div>
            )}

            <ProjectLayout
                isEditing={!readOnly}
                bannerUrl={bannerPreview || modData?.bannerUrl}
                iconUrl={metaData.iconPreview || modData?.imageUrl}
                onBannerUpload={(f, p) => { markDirty(); setBannerFile(f); setBannerPreview(p); }}
                onIconUpload={(f, p) => { markDirty(); setMetaData(m => ({...m, iconFile: f, iconPreview: p})); }}
                headerContent={
                    <div>
                        <input value={metaData.title} disabled={readOnly} onChange={e => { markDirty(); setMetaData({...metaData, title: e.target.value}); }} className="text-4xl md:text-5xl font-black text-slate-900 dark:text-white bg-transparent border-b border-transparent outline-none pb-1 placeholder:text-slate-400 w-full hover:border-slate-300 dark:hover:border-white/20 focus:border-modtale-accent" placeholder="Project Title"/>
                        <input value={metaData.summary} disabled={readOnly} onChange={e => { markDirty(); setMetaData({...metaData, summary: e.target.value}); }} className="text-lg text-slate-600 dark:text-slate-300 font-medium bg-transparent border-b border-transparent outline-none pb-1 placeholder:text-slate-400 w-full mt-2 hover:border-slate-300 dark:hover:border-white/20 focus:border-modtale-accent" placeholder="Short summary..."/>
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
                                <button onClick={() => { handleSave(false); setIsDirty(false); }} disabled={isLoading} className="h-10 px-5 rounded-xl border border-slate-200 dark:border-white/10 bg-slate-100 dark:bg-white/5 font-bold flex items-center gap-2 hover:bg-slate-200">{isLoading ? <Spinner className="w-4 h-4"/> : <Save className="w-4 h-4" />} Save</button>
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
                                                    <span className={`text-xs font-bold ${req.met ? 'text-slate-900 dark:text-white' : 'text-slate-500'}`}>{req.label}</span>
                                                </div>
                                            ))}
                                        </div>
                                        <div className="absolute top-full right-8 -mt-1.5 border-8 border-transparent border-t-white dark:border-t-slate-900" />
                                    </div>

                                    <button
                                        onClick={() => setShowPublishConfirm(true)}
                                        disabled={isLoading || !isPublishable}
                                        className="h-10 bg-green-500 hover:bg-green-600 disabled:bg-slate-200 dark:disabled:bg-slate-800 disabled:text-slate-400 disabled:shadow-none text-white px-6 rounded-xl font-black flex items-center gap-2 shadow-lg transition-all"
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
                        {[{id: 'details', icon: LayoutTemplate, label: 'Details'}, {id: 'files', icon: UploadCloud, label: `Files (${modData?.versions?.length||0})`}, {id: 'gallery', icon: Image, label: `Gallery (${modData?.galleryImages?.length||0})`}, {id: 'settings', icon: Settings, label: 'Settings'}].map(t => (
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
                                    {!readOnly && <div className="flex bg-slate-100 dark:bg-slate-950/50 rounded-lg p-1 border border-slate-200 dark:border-white/10"><button onClick={() => setEditorMode('write')} className={`px-4 py-1.5 text-xs font-bold rounded-md ${editorMode === 'write' ? 'bg-modtale-accent text-white' : 'text-slate-500'}`}>Write</button><button onClick={() => setEditorMode('preview')} className={`px-4 py-1.5 text-xs font-bold rounded-md ${editorMode === 'preview' ? 'bg-modtale-accent text-white' : 'text-slate-500'}`}>Preview</button></div>}
                                </div>
                                {editorMode === 'write' && !readOnly ? (
                                    <textarea value={metaData.description} onChange={e => { markDirty(); setMetaData({...metaData, description: e.target.value}); }} className="flex-1 w-full h-full min-h-[400px] bg-transparent border-none outline-none text-slate-900 dark:text-slate-300 font-mono text-sm resize-none" placeholder="# Description..." />
                                ) : (
                                    <div className="prose dark:prose-invert prose-lg max-w-none min-h-[400px]">
                                        {metaData.description ? (
                                            <ReactMarkdown
                                                remarkPlugins={[remarkGfm]}
                                                rehypePlugins={[rehypeRaw, [rehypeSanitize, {
                                                    ...defaultSchema,
                                                    attributes: {
                                                        ...defaultSchema.attributes,
                                                        code: ['className']
                                                    }
                                                }]]}
                                                components={MarkdownComponents}
                                            >
                                                {metaData.description}
                                            </ReactMarkdown>
                                        ) : (
                                            <p className="text-slate-500 italic">No description.</p>
                                        )}
                                    </div>
                                )}
                            </div>
                        )}

                        {activeTab === 'files' && (
                            <div className="space-y-8">
                                {!readOnly && (
                                    <div className="bg-slate-50 dark:bg-slate-950/30 p-6 rounded-2xl border border-slate-200 dark:border-white/5">
                                        <VersionFields data={versionData} onChange={setVersionData} isModpack={classification === 'MODPACK'} projectType={typeof classification === 'string' ? classification : 'PLUGIN'} disabled={readOnly} />
                                        <div className="mt-6 flex justify-end"><button onClick={handleUploadVersion} disabled={isLoading || readOnly} className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-8 h-12 rounded-xl font-bold shadow-lg flex items-center gap-2 disabled:opacity-50">{isLoading ? <Spinner className="w-5 h-5"/> : <UploadCloud className="w-5 h-5" />} Upload Version</button></div>
                                    </div>
                                )}
                                {modData?.versions?.map(v => (
                                    <div key={v.id} className="bg-slate-50 dark:bg-slate-950/30 border border-slate-200 dark:border-white/5 rounded-xl p-4 flex justify-between items-center">
                                        <div>
                                            <div className="flex items-center gap-3"><span className="font-mono font-bold text-slate-900 dark:text-white text-lg">{v.versionNumber}</span><span className={`text-[10px] font-bold px-2 py-0.5 rounded border ${v.channel === 'RELEASE' ? 'text-green-500 border-green-500/30 bg-green-500/10' : 'text-orange-500 border-orange-500/30 bg-orange-500/10'}`}>{v.channel}</span></div>
                                            <div className="text-xs text-slate-500 mt-1">{v.gameVersions?.join(', ') || 'Unknown'} • {new Date(v.releaseDate).toLocaleDateString()}</div>
                                        </div>
                                        {!readOnly && handleDeleteVersion && <button onClick={() => handleDeleteVersion(v.id)} className="p-2 text-slate-500 hover:text-red-500"><Trash2 className="w-4 h-4" /></button>}
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
                                            {!readOnly && (
                                                <div className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                                                    <button onClick={() => handleGalleryDelete(img)} disabled={isLoading} className="p-2 bg-red-500 hover:bg-red-600 text-white rounded-lg shadow-lg transform scale-90 group-hover:scale-100 transition-transform">
                                                        <Trash2 className="w-5 h-5" />
                                                    </button>
                                                </div>
                                            )}
                                        </div>
                                    ))}
                                    {!readOnly && (
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

                        {activeTab === 'settings' && (
                            <div className="space-y-6">
                                <div className="bg-slate-50 dark:bg-slate-900/30 p-6 rounded-2xl border border-slate-200 dark:border-white/10">

                                    {(modData?.status === 'PUBLISHED' || modData?.status === 'UNLISTED') && (
                                        <div className="mb-6 pb-6 border-b border-slate-200 dark:border-white/5">
                                            <h3 className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-2 mb-4"><Eye className="w-4 h-4 text-slate-500" /> Project Visibility</h3>
                                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                                <button
                                                    onClick={handleRestore}
                                                    disabled={isLoading || modData.status === 'PUBLISHED'}
                                                    className={`p-4 rounded-xl border flex flex-col items-center gap-3 transition-all ${modData.status === 'PUBLISHED' ? 'bg-green-500/10 border-green-500 text-green-500' : 'bg-white dark:bg-black/20 border-slate-200 dark:border-white/10 hover:border-green-500 hover:text-green-500'}`}
                                                >
                                                    <Globe className="w-6 h-6" />
                                                    <div className="text-center">
                                                        <div className="font-bold text-sm">Published</div>
                                                        <div className="text-[10px] opacity-70">Visible to everyone</div>
                                                    </div>
                                                </button>

                                                <button
                                                    onClick={handleUnlist}
                                                    disabled={isLoading || modData.status === 'UNLISTED'}
                                                    className={`p-4 rounded-xl border flex flex-col items-center gap-3 transition-all ${modData.status === 'UNLISTED' ? 'bg-orange-500/10 border-orange-500 text-orange-500' : 'bg-white dark:bg-black/20 border-slate-200 dark:border-white/10 hover:border-orange-500 hover:text-orange-500'}`}
                                                >
                                                    <EyeOff className="w-6 h-6" />
                                                    <div className="text-center">
                                                        <div className="font-bold text-sm">Unlisted</div>
                                                        <div className="text-[10px] opacity-70">Hidden from search</div>
                                                    </div>
                                                </button>

                                                <button
                                                    onClick={handleArchive}
                                                    disabled={isLoading}
                                                    className="p-4 rounded-xl border flex flex-col items-center gap-3 transition-all bg-white dark:bg-black/20 border-slate-200 dark:border-white/10 hover:border-slate-500 hover:text-slate-500"
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
                                            <div className="flex items-center w-full bg-white dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl overflow-hidden focus-within:ring-2 focus-within:ring-modtale-accent transition-all">
                                                <div className="px-4 py-2 bg-slate-50 dark:bg-white/5 border-r border-slate-200 dark:border-white/10 text-slate-500 text-sm font-mono whitespace-nowrap select-none">{getUrlPrefix()}</div>
                                                <input disabled={readOnly} value={metaData.slug || ''} onChange={handleSlugChange} className={`flex-1 bg-transparent border-none px-4 py-2 text-sm font-mono text-slate-900 dark:text-white focus:outline-none placeholder:text-slate-400 ${slugError ? 'text-red-500' : ''}`} placeholder={createSlug(metaData.title, modData?.id || 'id')} />
                                            </div>
                                            {slugError && <p className="text-[10px] text-red-500 font-bold">{slugError}</p>}
                                        </div>
                                    </div>

                                    <div className="flex items-center justify-between mb-4">
                                        <div><h3 className="text-sm font-bold text-slate-900 dark:text-white">Allow Modpacks</h3><p className="text-xs text-slate-500">Allow inclusion in modpacks?</p></div>
                                        <button disabled={readOnly} onClick={() => { markDirty(); setModData(prev => prev ? {...prev, allowModpacks: !prev.allowModpacks} : null); }} className={`transition-colors ${readOnly ? 'opacity-50' : modData?.allowModpacks ? 'text-green-500' : 'text-slate-600'}`}>{modData?.allowModpacks ? <ToggleRight className="w-8 h-8" /> : <ToggleLeft className="w-8 h-8" />}</button>
                                    </div>

                                    <div className="flex items-center justify-between mb-4">
                                        <div><h3 className="text-sm font-bold text-slate-900 dark:text-white">Allow Reviews</h3><p className="text-xs text-slate-500">Enable community reviews?</p></div>
                                        <button disabled={readOnly} onClick={() => { markDirty(); setModData(prev => prev ? {...prev, allowReviews: !prev.allowReviews} : null); }} className={`transition-colors ${readOnly ? 'opacity-50' : modData?.allowReviews ? 'text-green-500' : 'text-slate-600'}`}>{modData?.allowReviews ? <ToggleRight className="w-8 h-8" /> : <ToggleLeft className="w-8 h-8" />}</button>
                                    </div>

                                    <div className="pt-4 border-t border-slate-200 dark:border-white/5">
                                        <h3 className="text-sm font-bold mb-2">Contributors</h3>
                                        <div className="flex gap-2 mb-4">
                                            <input disabled={readOnly} value={inviteUsername} onChange={e => setInviteUsername(e.target.value)} placeholder="Username" className="flex-1 bg-slate-100 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-lg px-3 py-2 text-sm" />
                                            <button disabled={readOnly || isInviting || !inviteUsername} className="bg-modtale-accent text-white px-4 py-2 rounded-lg font-bold text-xs flex items-center gap-2"><UserPlus className="w-3 h-3" /> Invite</button>
                                        </div>
                                        {modData?.contributors?.map(u => <div key={u} className="flex justify-between items-center p-2 bg-slate-100 dark:bg-black/20 rounded-lg border border-slate-200 dark:border-white/5"><span className="text-sm font-bold">{u}</span></div>)}
                                    </div>
                                </div>
                                {!readOnly && <button onClick={handleDelete} className="w-full bg-red-500/10 border border-red-500/20 text-red-500 hover:bg-red-500 hover:text-white p-4 rounded-xl font-bold flex justify-center gap-2 transition-all"><Trash2 className="w-4 h-4"/> Delete Project</button>}
                            </div>
                        )}
                    </>
                }
                sidebarContent={
                    <>
                        <SidebarSection title="Card Preview" icon={Eye}>
                            <div className="flex justify-center w-full overflow-hidden">
                                <div
                                    className="cursor-pointer transition-transform origin-top mt-2 relative group"
                                    onClick={() => setShowCardPreview(true)}
                                    style={{
                                        width: '115%',
                                        zoom: '0.75',
                                        MozTransform: 'scale(0.75)',
                                        MozTransformOrigin: 'top center',
                                    }}
                                >
                                    <div className="pointer-events-none select-none h-full w-full block relative" onClickCapture={(e) => { e.preventDefault(); e.stopPropagation(); }}>
                                        <ModCard
                                            mod={previewMod}
                                            isFavorite={false}
                                            onToggleFavorite={() => {}}
                                            isLoggedIn={false}
                                        />
                                        <div className="absolute inset-0 z-50 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center backdrop-blur-[1px] rounded-xl border-2 border-white/20">
                                            <div className="bg-black/50 px-3 py-1.5 rounded-full flex items-center gap-2 text-white border border-white/10 shadow-xl transform scale-125">
                                                <Maximize2 className="w-4 h-4" />
                                                <span className="text-xs font-bold uppercase tracking-widest">Expand</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <p className="text-center text-[10px] text-slate-500 mt-2">Click to expand</p>
                        </SidebarSection>

                        <SidebarSection title="Repository Source" icon={GitMerge}>
                            <div className="bg-slate-50 dark:bg-slate-950/50 border border-slate-200 dark:border-white/10 rounded-xl p-2">
                                <div className="flex bg-slate-200 dark:bg-black/40 rounded-lg p-1 mb-3">
                                    <button onClick={() => { setManualRepo(false); if (hasGithub) { if (provider !== 'github') setRepos([]); setProvider('github'); } }} disabled={readOnly || !hasGithub} className={`flex-1 py-1.5 text-[10px] font-bold rounded-md transition-all ${!manualRepo && provider === 'github' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-900 dark:hover:text-white disabled:opacity-30'}`}>GitHub</button>
                                    <button onClick={() => { setManualRepo(false); if (hasGitlab) { if (provider !== 'gitlab') setRepos([]); setProvider('gitlab'); } }} disabled={readOnly || !hasGitlab} className={`flex-1 py-1.5 text-[10px] font-bold rounded-md transition-all ${!manualRepo && provider === 'gitlab' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-900 dark:hover:text-white disabled:opacity-30'}`}>GitLab</button>
                                    <button onClick={() => setManualRepo(true)} disabled={readOnly} className={`flex-1 py-1.5 text-[10px] font-bold rounded-md transition-all ${manualRepo ? 'bg-modtale-accent text-white shadow-sm' : 'text-slate-500 hover:text-slate-900 dark:hover:text-white disabled:opacity-30'}`}>Link URL</button>
                                </div>

                                {manualRepo ? (
                                    <div className="relative">
                                        <input value={metaData.repositoryUrl} disabled={readOnly} onChange={e => { markDirty(); setMetaData({...metaData, repositoryUrl: e.target.value}); checkRepoUrl(e.target.value); }} className={`w-full bg-slate-100 dark:bg-slate-900 border rounded-lg px-3 py-2 text-xs text-slate-900 dark:text-white outline-none transition-all pr-8 ${!repoValid && metaData.repositoryUrl ? 'border-red-500' : 'border-slate-200 dark:border-white/10'}`} placeholder="https://github.com/..." />
                                        {metaData.repositoryUrl && <div className="absolute right-3 top-2.5">{repoValid ? <CheckCircle2 className="w-3.5 h-3.5 text-green-500" /> : <X className="w-3.5 h-3.5 text-red-500" />}</div>}
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
                                                        <button key={r.url} onClick={() => { markDirty(); setMetaData({...metaData, repositoryUrl: r.html_url || r.url}); checkRepoUrl(r.html_url || r.url); setRepoDropdownOpen(false); }} className="w-full text-left px-2 py-1.5 rounded hover:bg-slate-100 dark:hover:bg-white/10 text-xs text-slate-600 dark:text-slate-300 flex justify-between items-center group"><span className="font-mono">{r.name}</span>{metaData.repositoryUrl === (r.html_url || r.url) && <Check className="w-3 h-3 text-modtale-accent" />}</button>
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
                                <div className="bg-slate-50 dark:bg-slate-950/50 border border-slate-200 dark:border-white/10 rounded-xl p-2 max-h-80 overflow-y-auto">
                                    {LICENSES.map(lic => (
                                        <button key={lic.id} disabled={readOnly} onClick={() => { markDirty(); setIsCustomLicense(false); setMetaData({ ...metaData, license: lic.id }); }} className={`w-full text-left px-3 py-2 rounded-lg text-xs font-bold flex items-center justify-between ${!isCustomLicense && metaData.license === lic.id ? 'bg-modtale-accent text-white' : 'text-slate-500 hover:bg-slate-200 dark:hover:bg-white/10'}`}>
                                            <span>{lic.name}</span>{!isCustomLicense && metaData.license === lic.id && <Check className="w-3 h-3" />}
                                        </button>
                                    ))}

                                    <button
                                        disabled={readOnly}
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
                                                disabled={readOnly}
                                                className="w-full bg-white dark:bg-white/5 border border-slate-200 dark:border-white/10 rounded-lg px-3 py-2 text-xs"
                                            />
                                            <input
                                                value={metaData.links.LICENSE || ''}
                                                onChange={(e) => { markDirty(); setMetaData({...metaData, links: {...metaData.links, LICENSE: e.target.value}}); }}
                                                placeholder="License URL"
                                                disabled={readOnly}
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
                                    <button disabled={readOnly} key={tag} onClick={() => toggleTag(tag)} className={`px-2.5 py-1 rounded-lg text-[10px] font-bold border transition-all ${metaData.tags.includes(tag) ? 'bg-modtale-accent text-white border-modtale-accent' : 'bg-slate-100 dark:bg-slate-900/50 text-slate-500 dark:text-slate-400 border-slate-200 dark:border-white/10'}`}>
                                        {tag}
                                    </button>
                                ))}
                            </div>
                        </SidebarSection>

                        <SidebarSection title="External Links" icon={LinkIcon} defaultOpen={false}>
                            <div className="space-y-3">
                                {['WEBSITE', 'WIKI', 'ISSUE_TRACKER', 'DISCORD'].map(k => (
                                    <ThemedInput key={k} disabled={readOnly} label={k.replace('_', ' ')} value={metaData.links[k] || ''} onChange={(e:any) => { markDirty(); setMetaData({...metaData, links: {...metaData.links, [k]: e.target.value}}); }} placeholder="https://..." />
                                ))}
                            </div>
                        </SidebarSection>
                    </>
                }
            />
        </>
    );
};