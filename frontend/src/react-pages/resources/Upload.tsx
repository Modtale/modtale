import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../utils/api';
import { PROJECT_TYPES } from '../../data/categories';
import type { Classification } from '../../data/categories';
import { ProjectBuilder } from '../../components/resources/upload/ProjectBuilder';
import type { MetadataFormData, VersionFormData } from '../../components/resources/upload/FormShared';
import { ArrowLeft, ArrowRight, AlertCircle, Building2, User as UserIcon, ChevronDown, Check, Plus, ImageIcon } from 'lucide-react';
import type { User, Mod } from '../../types';
import { Spinner } from '@/components/ui/Spinner';
import { StatusModal } from '@/components/ui/StatusModal';

interface UploadProps {
    onNavigate: (page: string) => void;
    onRefresh: () => void;
    currentUser: User | null;
}

export const Upload: React.FC<UploadProps> = ({ onNavigate, onRefresh, currentUser }) => {
    const navigate = useNavigate();

    const [step, setStep] = useState(0);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [draftId, setDraftId] = useState<string | null>(null);
    const [statusModal, setStatusModal] = useState<{type: 'success' | 'error' | 'warning', title: string, msg: string} | null>(null);
    const [versionToDelete, setVersionToDelete] = useState<string | null>(null);

    const [classification, setClassification] = useState<Classification | null>(null);
    const [title, setTitle] = useState('');
    const [summary, setSummary] = useState('');

    const [owner, setOwner] = useState<string>(currentUser?.username || '');
    const [myOrgs, setMyOrgs] = useState<User[]>([]);
    const [ownerDropdownOpen, setOwnerDropdownOpen] = useState(false);
    const ownerDropdownRef = useRef<HTMLDivElement>(null);

    const [modData, setModData] = useState<Mod | null>(null);
    const [activeTab, setActiveTab] = useState<'details' | 'files' | 'gallery' | 'settings'>('details');

    const [metaData, setMetaData] = useState<MetadataFormData>({
        title: '', summary: '', description: '', tags: [], links: {}, repositoryUrl: '', iconFile: null, iconPreview: null, license: '', slug: ''
    });
    const [bannerFile, setBannerFile] = useState<File | null>(null);
    const [bannerPreview, setBannerPreview] = useState<string | null>(null);

    const [versionData, setVersionData] = useState<VersionFormData>({
        versionNumber: '1.0.0', gameVersions: [], changelog: '', file: null, dependencies: [], modIds: [], channel: 'RELEASE'
    });

    useEffect(() => {
        if (currentUser) {
            if (!owner) {
                setOwner(currentUser.username);
            }

            api.get('/user/orgs').then(res => {
                const adminOrgs = res.data.filter((o: User) =>
                    o.organizationMembers?.some(m => m.userId === currentUser.id && m.role === 'ADMIN')
                );
                setMyOrgs(adminOrgs);
            }).catch(console.error);
        }

        const handleClickOutside = (event: MouseEvent) => {
            if (ownerDropdownRef.current && !ownerDropdownRef.current.contains(event.target as Node)) {
                setOwnerDropdownOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [currentUser]);

    const handleClassificationSelect = (typeId: string) => {
        if (!currentUser) {
            setStatusModal({
                type: 'error',
                title: 'Sign In Required',
                msg: "You must be logged in to create a project."
            });
            return;
        }
        if (!currentUser.emailVerified) {
            setStatusModal({
                type: 'error',
                title: 'Verification Required',
                msg: "You must verify your email address before creating projects. Please check your inbox."
            });
            return;
        }
        setClassification(typeId as Classification);
        setStep(1);
    };

    const handleCreateDraft = async () => {
        if (!currentUser?.emailVerified) {
            setStatusModal({
                type: 'error',
                title: 'Verification Required',
                msg: "You must verify your email address before creating projects."
            });
            return;
        }

        if (!title.trim() || !summary.trim() || !classification) {
            setError("Please fill in all fields."); return;
        }

        const effectiveOwner = owner || currentUser?.username;
        if (!effectiveOwner) {
            setError("Unable to determine project owner. Please refresh and try again.");
            return;
        }

        setIsLoading(true);
        try {
            const formData = new FormData();
            formData.append('title', title);
            formData.append('classification', classification);
            formData.append('description', summary);
            formData.append('owner', effectiveOwner);

            const uploadConfig = {
                headers: { 'Content-Type': 'multipart/form-data' }
            };

            const res = await api.post('/projects', formData, uploadConfig);
            setDraftId(res.data.id);
            setModData(res.data);

            setMetaData({
                title: res.data.title,
                summary: res.data.description,
                description: '',
                tags: [],
                links: {},
                repositoryUrl: '',
                iconFile: null, iconPreview: null,
                license: '',
                slug: res.data.slug || ''
            });
            setStep(2);
        } catch (e: any) {
            setError(e.response?.data || "Failed to create draft.");
        } finally {
            setIsLoading(false);
        }
    };

    const handleSaveMetadata = async (silent = false): Promise<boolean> => {
        if(!draftId) return false;
        if(!silent) setIsLoading(true);
        try {
            const body = {
                title: metaData.title,
                description: metaData.summary,
                about: metaData.description,
                tags: metaData.tags,
                links: metaData.links,
                repositoryUrl: metaData.repositoryUrl,
                license: metaData.license,
                allowModpacks: modData?.allowModpacks,
                slug: metaData.slug
            };
            await api.put(`/projects/${draftId}`, body);

            const uploadConfig = {
                headers: { 'Content-Type': 'multipart/form-data' }
            };

            if (metaData.iconFile) {
                const f = new FormData(); f.append('file', metaData.iconFile);
                await api.put(`/projects/${draftId}/icon`, f, uploadConfig);
            }
            if (bannerFile) {
                const f = new FormData(); f.append('file', bannerFile);
                await api.put(`/projects/${draftId}/banner`, f, uploadConfig);
            }

            const res = await api.get(`/projects/${draftId}`);
            setModData(res.data);
            if(!silent) setStatusModal({type: 'success', title: 'Saved', msg: 'Draft saved successfully.'});
            return true;
        } catch (e: any) {
            if(!silent) setStatusModal({type: 'error', title: 'Error', msg: e.response?.data?.message || 'Failed to save draft.'});
            return false;
        } finally {
            if(!silent) setIsLoading(false);
        }
    };

    const handleUploadVersion = async () => {
        if(!draftId) return;
        if(!versionData.versionNumber) { setStatusModal({type: 'error', title: 'Error', msg: "Version number required"}); return; }
        if(classification !== 'MODPACK' && !versionData.file) { setStatusModal({type: 'error', title: 'Error', msg: "File required"}); return; }

        setIsLoading(true);
        try {
            const saved = await handleSaveMetadata(true);
            if (!saved) return;

            const fd = new FormData();
            fd.append('versionNumber', versionData.versionNumber);
            fd.append('gameVersions', versionData.gameVersions[0]);
            fd.append('channel', versionData.channel || 'RELEASE');
            if(versionData.changelog) fd.append('changelog', versionData.changelog);
            if(versionData.file) fd.append('file', versionData.file);

            const depsToUse = versionData.modIds;
            if(depsToUse) depsToUse.forEach(d => fd.append('modIds', d));

            const uploadConfig = {
                headers: { 'Content-Type': 'multipart/form-data' }
            };

            await api.post(`/projects/${draftId}/versions`, fd, uploadConfig);

            const res = await api.get(`/projects/${draftId}`);
            setModData(res.data);
            setVersionData({...versionData, versionNumber: '', file: null, changelog: ''});
            setStatusModal({type: 'success', title: 'Success', msg: "Version uploaded successfully!"});
            setActiveTab('details');
        } catch(e: any) {
            setStatusModal({type: 'error', title: 'Upload Failed', msg: e.response?.data || "Could not upload version."});
        } finally {
            setIsLoading(false);
        }
    };

    const handleDeleteVersion = (versionId: string) => {
        setVersionToDelete(versionId);
    };

    const confirmDeleteVersion = async () => {
        if(!draftId || !versionToDelete) return;
        setIsLoading(true);
        try {
            await api.delete(`/projects/${draftId}/versions/${versionToDelete}`);
            const res = await api.get(`/projects/${draftId}`);
            setModData(res.data);
            setVersionToDelete(null);
        } catch(e: any) {
            setStatusModal({type: 'error', title: 'Error', msg: "Failed to delete version."});
        } finally {
            setIsLoading(false);
            setVersionToDelete(null);
        }
    };

    const handleGalleryUpload = async (file: File) => {
        if (!draftId) return;
        setIsLoading(true);
        try {
            const fd = new FormData();
            fd.append('file', file);
            await api.post(`/projects/${draftId}/gallery`, fd, { headers: { 'Content-Type': 'multipart/form-data' } });
            const res = await api.get(`/projects/${draftId}`);
            setModData(res.data);
            setStatusModal({type: 'success', title: 'Uploaded', msg: 'Image added to gallery.'});
        } catch (e: any) {
            setStatusModal({type: 'error', title: 'Error', msg: e.response?.data?.message || 'Upload failed.'});
        } finally { setIsLoading(false); }
    };

    const handleGalleryDelete = async (url: string) => {
        if (!draftId) return;
        setIsLoading(true);
        try {
            await api.delete(`/projects/${draftId}/gallery`, { params: { imageUrl: url } });
            const res = await api.get(`/projects/${draftId}`);
            setModData(res.data);
        } catch (e: any) {
            setStatusModal({type: 'error', title: 'Error', msg: 'Failed to delete image.'});
        } finally { setIsLoading(false); }
    };

    const handlePublish = async () => {
        if(!draftId) return;
        if(metaData.summary.length < 10) { setStatusModal({type:'error', title:'Error', msg: "Short summary must be at least 10 characters."}); return; }
        if(!metaData.tags.length) { setStatusModal({type:'error', title:'Error', msg: "At least one tag is required."}); return; }
        if(classification !== 'MODPACK' && !metaData.license) { setStatusModal({type:'error', title:'Error', msg: "License is required."}); return; }
        if(!modData?.versions.length && classification !== 'MODPACK') { setStatusModal({type:'error', title:'Error', msg: "You must upload at least one version."}); setActiveTab('files'); return; }

        setIsLoading(true);
        try {
            if (!modData?.imageUrl && !metaData.iconFile) {
                try {
                    const res = await fetch('https://modtale.net/assets/favicon.svg');
                    const blob = await res.blob();
                    const file = new File([blob], 'icon.svg', { type: 'image/svg+xml' });
                    const fd = new FormData();
                    fd.append('file', file);

                    const uploadConfig = {
                        headers: { 'Content-Type': 'multipart/form-data' }
                    };

                    await api.put(`/projects/${draftId}/icon`, fd, uploadConfig);
                } catch (err) {
                    console.error("Failed to set default icon", err);
                }
            }

            await handleSaveMetadata(true);
            await api.post(`/projects/${draftId}/submit`);
            onRefresh();
            setStatusModal({type:'success', title: 'Submitted', msg: 'Project submitted for verification.'});
            setTimeout(() => {
                navigate('/dashboard/projects');
            }, 1500);
        } catch (e: any) {
            setStatusModal({type:'error', title:'Submission Failed', msg: e.response?.data || "Failed to submit."});
        } finally {
            setIsLoading(false);
        }
    };

    const handleDelete = async () => {
        if(!draftId) return;
        setIsLoading(true);
        try {
            await api.delete(`/projects/${draftId}`);
            navigate('/');
        } catch(e: any) {
            setStatusModal({type: 'error', title: 'Error', msg: "Failed to delete project."});
            setIsLoading(false);
        }
    };

    const containerClasses = "max-w-[112rem] px-4 sm:px-12 md:px-16 lg:px-28";

    const ProjectTypeCard = ({ type, style, className = "" }: { type: any, style?: React.CSSProperties, className?: string }) => {
        const Icon = type.icon;
        return (
            <button
                onClick={() => handleClassificationSelect(type.id as string)}
                style={style}
                className={`relative p-8 rounded-3xl border-2 border-slate-300 dark:border-white/20 text-left transition-all duration-300 group overflow-hidden flex flex-col justify-center aspect-[4/3] bg-white/80 dark:bg-slate-900/80 backdrop-blur-xl shadow-xl hover:border-modtale-accent dark:hover:border-modtale-accent hover:ring-2 hover:ring-modtale-accent hover:shadow-2xl hover:-translate-y-1 ${className}`}
            >
                <div className="absolute inset-0 bg-gradient-to-br from-modtale-accent/0 to-modtale-accent/5 opacity-0 group-hover:opacity-100 transition-opacity duration-300" />

                <div className="w-20 h-20 rounded-2xl flex items-center justify-center mb-8 transition-colors bg-white/50 dark:bg-black/30 text-slate-500 dark:text-slate-400 group-hover:bg-modtale-accent group-hover:text-white transform duration-200 group-hover:scale-110 shadow-inner relative z-10 backdrop-blur-md border border-slate-200/50 dark:border-white/5">
                    <Icon className="w-10 h-10" />
                </div>

                <h3 className="font-black text-3xl mb-3 text-slate-900 dark:text-white group-hover:text-modtale-accent transition-colors relative z-10">
                    {type.label}
                </h3>
                <p className="text-sm md:text-base text-slate-500 dark:text-slate-400 leading-relaxed font-medium group-hover:text-slate-600 dark:group-hover:text-slate-300 transition-colors relative z-10">
                    {type.id === 'MODPACK' ? 'Bundle multiple mods into a pack.' : type.id === 'SAVE' ? 'Share worlds, schematics, or lobbies.' : type.id === 'PLUGIN' ? 'Server-side logic, tools, and scripts.' : 'Custom models, textures, and art.'}
                </p>
            </button>
        );
    };

    if (step === 0) {
        const filteredTypes = PROJECT_TYPES.filter(t => t.id !== 'All');
        const topRowCount = Math.ceil(filteredTypes.length / 2);
        const topRow = filteredTypes.slice(0, topRowCount);
        const bottomRow = filteredTypes.slice(topRowCount);

        const cardFlexStyle = { flex: `0 0 calc((100% - ${(topRowCount - 1) * 24}px) / ${topRowCount})` };

        return (
            <>
                {statusModal && <StatusModal type={statusModal.type} title={statusModal.title} message={statusModal.msg} onClose={() => setStatusModal(null)} />}
                <div className="min-h-screen bg-slate-50 dark:bg-slate-950 flex flex-col transition-colors duration-300">
                    <div className={`${containerClasses} mx-auto w-full flex flex-col transition-[max-width,padding] duration-300 flex-1 pt-12 md:pt-20 pb-16`}>

                        <button
                            onClick={() => navigate(-1)}
                            className="flex items-center text-sm font-bold text-slate-500 hover:text-slate-900 dark:hover:text-white transition-colors group w-fit mb-4 md:mb-8"
                        >
                            <ArrowLeft className="w-4 h-4 mr-2 group-hover:-translate-x-1 transition-transform" /> Cancel
                        </button>

                        <div className="text-center pb-12 md:pb-20">
                            <h1 className="text-4xl md:text-6xl font-black text-slate-900 dark:text-white tracking-tight mb-4">What are you creating?</h1>
                            <p className="text-lg md:text-xl text-slate-500 dark:text-slate-400">Select a project type to get started.</p>
                        </div>

                        <div className="hidden lg:flex flex-col gap-6 w-full animate-in fade-in slide-in-from-bottom-4 duration-700 pb-12">
                            <div className="flex w-full gap-6">
                                {topRow.map(type => (
                                    <ProjectTypeCard key={type.id} type={type} style={cardFlexStyle} />
                                ))}
                            </div>
                            {bottomRow.length > 0 && (
                                <div className="flex w-full justify-center gap-6">
                                    {bottomRow.map(type => (
                                        <ProjectTypeCard key={type.id} type={type} style={cardFlexStyle} />
                                    ))}
                                </div>
                            )}
                        </div>

                        <div className="grid lg:hidden grid-cols-1 sm:grid-cols-2 gap-6 w-full animate-in fade-in slide-in-from-bottom-4 duration-700 pb-12">
                            {filteredTypes.map(type => (
                                <ProjectTypeCard key={type.id} type={type} className="w-full" />
                            ))}
                        </div>

                    </div>
                </div>
            </>
        );
    }

    if (step === 1) {
        const selectedOwner = owner === currentUser?.username ? currentUser : myOrgs.find(o => o.username === owner);

        return (
            <>
                {statusModal && <StatusModal type={statusModal.type} title={statusModal.title} message={statusModal.msg} onClose={() => setStatusModal(null)} />}
                <div className="min-h-screen bg-slate-50 dark:bg-slate-950 flex flex-col transition-colors duration-300">
                    <div className={`${containerClasses} mx-auto w-full pt-12 md:pt-20 pb-16 flex-1`}>
                        <button onClick={() => setStep(0)} className="flex items-center text-sm font-bold text-slate-500 hover:text-slate-900 dark:hover:text-white transition-colors group w-fit mb-12 md:mb-16">
                            <ArrowLeft className="w-4 h-4 mr-2 group-hover:-translate-x-1 transition-transform" /> Back
                        </button>

                        <div className="w-full max-w-3xl">
                            <h1 className="text-3xl md:text-5xl font-black text-slate-900 dark:text-white mb-10">Let's give it a name.</h1>
                            {error && <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 text-red-500 rounded-xl flex items-center gap-2 text-sm font-bold"><AlertCircle className="w-5 h-5"/> {error}</div>}

                            <div className="space-y-6 bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl p-8 md:p-10 rounded-3xl border border-slate-200 dark:border-white/10 shadow-2xl animate-in fade-in zoom-in-95 duration-300">
                                {myOrgs.length > 0 && (
                                    <div className="relative z-50" ref={ownerDropdownRef}>
                                        <label className="block text-xs font-bold text-slate-500 uppercase mb-2 ml-1">Project Owner</label>
                                        <button
                                            onClick={() => setOwnerDropdownOpen(!ownerDropdownOpen)}
                                            className="w-full flex items-center justify-between bg-white/50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-5 py-4 text-left transition-all hover:bg-white dark:hover:bg-white/5 shadow-inner backdrop-blur-md"
                                        >
                                            <div className="flex items-center gap-4">
                                                {owner === currentUser?.username ? (
                                                    <div className="w-10 h-10 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center"><UserIcon className="w-5 h-5"/></div>
                                                ) : (
                                                    <div className="w-10 h-10 rounded-lg bg-purple-100 text-purple-600 flex items-center justify-center"><Building2 className="w-5 h-5"/></div>
                                                )}
                                                <div>
                                                    <div className="font-bold text-slate-900 dark:text-white text-base">{selectedOwner?.displayName || selectedOwner?.username}</div>
                                                    <div className="text-xs text-slate-500 font-bold uppercase tracking-wider mt-0.5">{owner === currentUser?.username ? 'Personal' : 'Organization'}</div>
                                                </div>
                                            </div>
                                            <ChevronDown className={`w-5 h-5 text-slate-400 transition-transform ${ownerDropdownOpen ? 'rotate-180' : ''}`} />
                                        </button>

                                        {ownerDropdownOpen && (
                                            <div className="absolute top-full left-0 right-0 mt-3 bg-white/90 dark:bg-slate-800/90 backdrop-blur-xl border border-slate-200 dark:border-white/10 rounded-2xl shadow-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-100 p-2">
                                                <button onClick={() => { setOwner(currentUser?.username || ''); setOwnerDropdownOpen(false); }} className="w-full flex items-center gap-4 p-3 hover:bg-slate-50 dark:hover:bg-white/5 transition-colors rounded-xl group">
                                                    <div className="w-10 h-10 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center group-hover:scale-105 transition-transform"><UserIcon className="w-5 h-5"/></div>
                                                    <div className="text-left flex-1">
                                                        <div className="font-bold text-slate-900 dark:text-white text-sm">{currentUser?.username}</div>
                                                        <div className="text-[10px] text-slate-500 font-bold uppercase tracking-wider">Personal Account</div>
                                                    </div>
                                                    {owner === currentUser?.username && <Check className="w-5 h-5 text-modtale-accent" />}
                                                </button>
                                                <div className="h-px bg-slate-100 dark:bg-white/5 mx-3 my-2" />
                                                {myOrgs.map(org => (
                                                    <button key={org.id} onClick={() => { setOwner(org.username); setOwnerDropdownOpen(false); }} className="w-full flex items-center gap-4 p-3 hover:bg-slate-50 dark:hover:bg-white/5 transition-colors rounded-xl group">
                                                        <div className="w-10 h-10 rounded-lg bg-purple-100 text-purple-600 flex items-center justify-center group-hover:scale-105 transition-transform"><Building2 className="w-5 h-5"/></div>
                                                        <div className="text-left flex-1">
                                                            <div className="font-bold text-slate-900 dark:text-white text-sm">{org.displayName || org.username}</div>
                                                            <div className="text-[10px] text-slate-500 font-bold uppercase tracking-wider">Organization</div>
                                                        </div>
                                                        {owner === org.username && <Check className="w-4 h-4 text-modtale-accent" />}
                                                    </button>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                )}

                                <div>
                                    <label className="block text-xs font-bold text-slate-500 uppercase mb-2 ml-1">Project Title</label>
                                    <input value={title} onChange={e => setTitle(e.target.value)} className="w-full bg-white/50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-5 py-4 font-black text-xl dark:text-white focus:ring-2 focus:ring-modtale-accent outline-none transition-all shadow-inner backdrop-blur-md" placeholder="My Awesome Project"/>
                                </div>
                                <div>
                                    <div className="flex justify-between items-end mb-2 ml-1 pr-1">
                                        <label className="block text-xs font-bold text-slate-500 uppercase">Short Summary</label>
                                        <p className="text-[10px] text-slate-400 font-bold">{summary.length}/250 (Min 10)</p>
                                    </div>
                                    <input value={summary} onChange={e => setSummary(e.target.value)} className="w-full bg-white/50 dark:bg-black/20 border border-slate-200 dark:border-white/10 rounded-xl px-5 py-4 dark:text-white focus:ring-2 focus:ring-modtale-accent outline-none transition-all shadow-inner backdrop-blur-md font-medium text-sm" placeholder="A brief description of what this does..."/>
                                </div>

                                <button onClick={handleCreateDraft} disabled={isLoading || !title || !summary} className="w-full h-16 mt-4 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-xl font-black text-lg flex items-center justify-center gap-3 transition-all disabled:opacity-50 disabled:cursor-not-allowed shadow-lg shadow-modtale-accent/20 active:scale-95">
                                    {isLoading ? <Spinner className="w-6 h-6 text-white" fullScreen={false} /> : <>Start Building <ArrowRight className="w-5 h-5"/></>}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </>
        );
    }

    return (
        <>
            {statusModal && <StatusModal type={statusModal.type} title={statusModal.title} message={statusModal.msg} onClose={() => setStatusModal(null)} />}
            {versionToDelete && (
                <StatusModal
                    type="warning"
                    title="Delete Version?"
                    message="Are you sure? This cannot be undone."
                    actionLabel="Delete"
                    onAction={confirmDeleteVersion}
                    secondaryLabel="Cancel"
                    onClose={() => setVersionToDelete(null)}
                />
            )}
            <ProjectBuilder
                modData={modData}
                setModData={setModData}
                metaData={metaData}
                setMetaData={setMetaData}
                versionData={versionData}
                setVersionData={setVersionData}
                bannerPreview={bannerPreview}
                setBannerPreview={setBannerPreview}
                setBannerFile={setBannerFile}
                handleSave={handleSaveMetadata}
                handlePublish={handlePublish}
                handleUploadVersion={handleUploadVersion}
                handleDeleteVersion={handleDeleteVersion}
                handleDelete={handleDelete}
                handleGalleryUpload={handleGalleryUpload}
                handleGalleryDelete={handleGalleryDelete}
                isLoading={isLoading}
                classification={classification || 'PLUGIN'}
                currentUser={currentUser}
                activeTab={activeTab}
                setActiveTab={setActiveTab}
                onShowStatus={(type, title, msg) => setStatusModal({type, title, msg})}
            />
        </>
    );
};