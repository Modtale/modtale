import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { api, BACKEND_URL } from '../../utils/api';
import type { Mod, User } from '../../types';
import type { MetadataFormData, VersionFormData } from '../../components/resources/upload/FormShared';
import { Spinner } from '../../components/ui/Spinner';
import { ProjectBuilder } from '../../components/resources/upload/ProjectBuilder.tsx';
import { ImageCropperModal } from '../../components/ui/ImageCropperModal';
import { StatusModal } from '../../components/ui/StatusModal';

interface EditModProps {
    currentUser: User | null;
}

export const EditMod: React.FC<EditModProps> = ({ currentUser }) => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const location = useLocation();

    const realId = React.useMemo(() => {
        if (!id) return '';
        const uuidMatch = id.match(/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/);
        return uuidMatch ? uuidMatch[0] : '';
    }, [id]);

    const [loading, setLoading] = useState(true);
    const [isLoading, setIsLoading] = useState(false);
    const [project, setProject] = useState<Mod | null>(null);

    const [activeTab, setActiveTab] = useState<'details' | 'files' | 'settings'>(() => {
        const params = new URLSearchParams(location.search);
        return params.get('tab') === 'files' ? 'files' : 'details';
    });

    const [statusModal, setStatusModal] = useState<{type: 'success' | 'error' | 'warning' | 'info', title: string, msg: string} | null>(null);

    const [cropperOpen, setCropperOpen] = useState(false);
    const [tempImage, setTempImage] = useState<string | null>(null);
    const [cropType, setCropType] = useState<'icon' | 'banner'>('icon');
    const [bannerFile, setBannerFile] = useState<File | null>(null);
    const [bannerPreview, setBannerPreview] = useState<string | null>(null);

    const [formData, setFormData] = useState<MetadataFormData>({
        title: '', summary: '', description: '', category: '', tags: [], links: {}, repositoryUrl: '', iconFile: null, iconPreview: null
    });
    const [versionData, setVersionData] = useState<VersionFormData>({
        versionNumber: '1.0.0', gameVersions: ['Release 1.1'], changelog: '', file: null, dependencies: [], modIds: [], channel: 'RELEASE'
    });

    useEffect(() => {
        const fetchProjectAndPerms = async () => {
            if (!currentUser) return;
            if (!realId) {
                setStatusModal({type: 'error', title: 'Error', msg: "Invalid Project ID format."});
                setLoading(false);
                return;
            }

            try {
                const [projectRes, orgsRes] = await Promise.all([
                    api.get(`/projects/${realId}`),
                    api.get('/user/orgs')
                ]);

                const data: Mod = projectRes.data;
                const myOrgs: User[] = orgsRes.data || [];

                const isOwner = data.author.toLowerCase() === currentUser.username.toLowerCase();

                const isContributor = data.contributors?.some(c => c.toLowerCase() === currentUser.username.toLowerCase());

                const isOrgAdmin = myOrgs.some(org =>
                    org.username.toLowerCase() === data.author.toLowerCase() &&
                    org.organizationMembers?.some(m => m.userId === currentUser.id && m.role === 'ADMIN')
                );

                if (!isOwner && !isContributor && !isOrgAdmin) {
                    navigate('/home');
                    return;
                }

                setProject(data);
                setFormData({
                    title: data.title,
                    summary: data.description,
                    description: data.about || '',
                    category: data.category || '',
                    tags: data.tags || [],
                    links: data.links || {},
                    repositoryUrl: data.repositoryUrl || '',
                    iconFile: null,
                    iconPreview: data.imageUrl ? (data.imageUrl.startsWith('/api') ? `${BACKEND_URL}${data.imageUrl}` : data.imageUrl) : null,
                    license: data.license
                });
                if (data.bannerUrl) {
                    setBannerPreview(data.bannerUrl.startsWith('/api') ? `${BACKEND_URL}${data.bannerUrl}` : data.bannerUrl);
                }

            } catch (e) {
                console.error(e);
                setStatusModal({type: 'error', title: 'Error', msg: "Failed to load project."});
                navigate('/home');
            } finally {
                setLoading(false);
            }
        };
        fetchProjectAndPerms();
    }, [realId, currentUser, navigate]);

    const handleSaveMetadata = async (silent = false): Promise<boolean> => {
        if (!project) return false;
        if (project.status === 'PENDING' || project.status === 'ARCHIVED') return false;

        if (!silent) setIsLoading(true);
        try {
            const body = {
                title: formData.title,
                description: formData.summary,
                about: formData.description,
                category: formData.category,
                tags: formData.tags,
                links: formData.links,
                repositoryUrl: formData.repositoryUrl,
                license: formData.license,
                allowModpacks: project.allowModpacks
            };
            await api.put(`/projects/${realId}`, body);

            if (formData.iconFile) {
                const f = new FormData(); f.append('file', formData.iconFile);
                await api.put(`/projects/${realId}/icon`, f);
            }
            if (bannerFile) {
                const f = new FormData(); f.append('file', bannerFile);
                await api.put(`/projects/${realId}/banner`, f);
            }

            const res = await api.get(`/projects/${realId}`);
            setProject(res.data);
            if (!silent) setStatusModal({type: 'success', title: 'Saved', msg: "Changes saved successfully."});
            return true;
        } catch (e: any) {
            console.error(e);
            if(!silent) setStatusModal({type: 'error', title: 'Error', msg: "Failed to save changes."});
            return false;
        }
        finally { if(!silent) setIsLoading(false); }
    };

    const handleUploadVersion = async () => {
        if(!project) return;
        if(project.status === 'PENDING' || project.status === 'ARCHIVED') return;

        if(!versionData.versionNumber) { setStatusModal({type: 'error', title: 'Error', msg: "Version number required"}); return; }
        if(project.classification !== 'MODPACK' && !versionData.file) { setStatusModal({type: 'error', title: 'Error', msg: "File required"}); return; }

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

            await api.post(`/projects/${realId}/versions`, fd);

            const res = await api.get(`/projects/${realId}`);
            setProject(res.data);
            setVersionData({...versionData, versionNumber: '', file: null, changelog: ''});
            setStatusModal({type: 'success', title: 'Success', msg: "Version uploaded successfully!"});
            setActiveTab('files');
        } catch(e: any) {
            setStatusModal({type: 'error', title: 'Upload Failed', msg: e.response?.data || "Upload failed"});
        } finally {
            setIsLoading(false);
        }
    };

    const handlePublish = async () => {
        if(!project) return;
        if(formData.summary.length < 10) { setStatusModal({type:'error', title:'Error', msg: "Short summary must be at least 10 characters."}); return; }
        if(!formData.tags.length) { setStatusModal({type:'error', title:'Error', msg: "At least one tag is required."}); return; }
        if(project.classification === 'PLUGIN' && !formData.repositoryUrl) { setStatusModal({type:'error', title:'Error', msg: "Repository URL is required for plugins."}); return; }
        if(project.classification !== 'MODPACK' && !formData.license) { setStatusModal({type:'error', title:'Error', msg: "License is required."}); return; }
        if(!project.versions?.length && project.classification !== 'MODPACK') { setStatusModal({type:'error', title:'Error', msg: "You must upload at least one version."}); setActiveTab('files'); return; }

        setIsLoading(true);
        try {
            if (!project.imageUrl && !formData.iconFile) {
                try {
                    const res = await fetch('https://modtale.net/assets/favicon.svg');
                    const blob = await res.blob();
                    const file = new File([blob], 'icon.svg', { type: 'image/svg+xml' });
                    const fd = new FormData();
                    fd.append('file', file);
                    await api.put(`/projects/${realId}/icon`, fd);
                } catch (err) {
                    console.error("Failed to set default icon", err);
                }
            }

            await handleSaveMetadata(true);
            await api.post(`/projects/${realId}/submit`);
            onShowStatus('success', 'Submitted', 'Project submitted for verification.');
            const res = await api.get(`/projects/${realId}`);
            setProject(res.data);
        } catch (e: any) {
            setStatusModal({type:'error', title:'Submit Failed', msg: e.response?.data || "Failed to submit."});
        } finally {
            setIsLoading(false);
        }
    };

    const handleRevert = async () => {
        if(!project) return;
        setIsLoading(true);
        try {
            await api.post(`/projects/${realId}/revert`);
            const res = await api.get(`/projects/${realId}`);
            setProject(res.data);
            onShowStatus('success', 'Reverted', 'Project reverted to draft.');
        } catch(e: any) {
            onShowStatus('error', 'Revert Failed', e.response?.data || "Failed to revert.");
        } finally {
            setIsLoading(false);
        }
    };

    const handleArchive = async () => {
        if(!project) return;
        setIsLoading(true);
        try {
            await api.post(`/projects/${realId}/archive`);
            const res = await api.get(`/projects/${realId}`);
            setProject(res.data);
            onShowStatus('success', 'Archived', 'Project is now archived.');
        } catch(e: any) {
            onShowStatus('error', 'Archive Failed', e.response?.data || "Failed to archive.");
        } finally {
            setIsLoading(false);
        }
    };

    const handleUnlist = async () => {
        if(!project) return;
        setIsLoading(true);
        try {
            await api.post(`/projects/${realId}/unlist`);
            const res = await api.get(`/projects/${realId}`);
            setProject(res.data);
            onShowStatus('success', 'Unlisted', 'Project is now unlisted.');
        } catch(e: any) {
            onShowStatus('error', 'Unlist Failed', e.response?.data || "Failed to unlist.");
        } finally {
            setIsLoading(false);
        }
    };

    const handleRestore = async () => {
        if(!project) return;
        setIsLoading(true);
        try {
            await api.post(`/projects/${realId}/publish`);
            const res = await api.get(`/projects/${realId}`);
            setProject(res.data);
            onShowStatus('success', 'Restored', 'Project is now published.');
        } catch(e: any) {
            onShowStatus('error', 'Restore Failed', e.response?.data || "Failed to restore.");
        } finally {
            setIsLoading(false);
        }
    };

    const handleDeleteVersion = async (versionId: string) => {
        setIsLoading(true);
        try {
            await api.delete(`/projects/${realId}/versions/${versionId}`);
            const res = await api.get(`/projects/${realId}`);
            setProject(res.data);
        } catch(e: any) {
            setStatusModal({type: 'error', title: 'Error', msg: "Failed to delete version."});
        } finally {
            setIsLoading(false);
        }
    };

    const handleDelete = async () => {
        setIsLoading(true);
        try {
            await api.delete(`/projects/${realId}`);
            navigate('/home');
        } catch(e: any) { setStatusModal({type: 'error', title: 'Error', msg: "Failed to delete project."}); setIsLoading(false); }
    };

    const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>, type: 'icon' | 'banner') => {
        if (project?.status === 'PENDING' || project?.status === 'ARCHIVED') return;

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
            setFormData(p => ({ ...p, iconFile: croppedFile, iconPreview: preview }));
        } else {
            setBannerFile(croppedFile);
            setBannerPreview(preview);
        }
        setCropperOpen(false);
        setTempImage(null);
    };

    const onShowStatus = (type: any, title: string, msg: string) => setStatusModal({type, title, msg});

    if (loading) return <div className="min-h-screen bg-slate-950 flex items-center justify-center"><Spinner fullScreen={false} className="w-8 h-8" /></div>;

    const isDraft = project?.status === 'DRAFT';
    const isPending = project?.status === 'PENDING';
    const isArchived = project?.status === 'ARCHIVED';

    return (
        <>
            {statusModal && <StatusModal type={statusModal.type} title={statusModal.title} message={statusModal.msg} onClose={() => setStatusModal(null)} />}

            {cropperOpen && tempImage && (
                <ImageCropperModal
                    imageSrc={tempImage}
                    aspect={cropType === 'banner' ? 3 : 1}
                    onCancel={() => { setCropperOpen(false); setTempImage(null); }}
                    onCropComplete={handleCropComplete}
                />
            )}

            <ProjectBuilder
                modData={project}
                setModData={setProject}
                metaData={formData}
                setMetaData={setFormData}
                versionData={versionData}
                setVersionData={setVersionData}
                bannerPreview={bannerPreview}
                handleFileSelect={handleFileSelect}
                handleSave={handleSaveMetadata}
                handlePublish={isDraft ? handlePublish : undefined}
                handleUploadVersion={handleUploadVersion}
                handleDelete={handleDelete}
                handleDeleteVersion={handleDeleteVersion}
                handleRevert={isPending ? handleRevert : undefined}
                handleArchive={handleArchive}
                handleUnlist={handleUnlist}
                handleRestore={handleRestore}
                isLoading={isLoading}
                classification={project?.classification || 'PLUGIN'}
                currentUser={currentUser}
                activeTab={activeTab}
                setActiveTab={setActiveTab}
                onShowStatus={onShowStatus}
                readOnly={isPending || isArchived}
            />
        </>
    );
};