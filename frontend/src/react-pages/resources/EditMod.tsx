import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { api, BACKEND_URL } from '../../utils/api';
import type { Mod, User } from '../../types';
import type { MetadataFormData, VersionFormData } from '../../components/resources/upload/FormShared';
import { Spinner } from '../../components/ui/Spinner';
import { ProjectBuilder } from '../../components/resources/upload/ProjectBuilder';
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
        return uuidMatch ? uuidMatch[0] : id;
    }, [id]);

    const [loading, setLoading] = useState(true);
    const [isLoading, setIsLoading] = useState(false);
    const [project, setProject] = useState<Mod | null>(null);

    const [activeTab, setActiveTab] = useState<'details' | 'files' | 'settings'>(() => {
        const params = new URLSearchParams(location.search);
        return params.get('tab') === 'files' ? 'files' : 'details';
    });

    const [statusModal, setStatusModal] = useState<{type: 'success' | 'error' | 'warning' | 'info', title: string, msg: string} | null>(null);

    const [bannerFile, setBannerFile] = useState<File | null>(null);
    const [bannerPreview, setBannerPreview] = useState<string | null>(null);

    const [formData, setFormData] = useState<MetadataFormData>({
        title: '', summary: '', description: '', category: '', tags: [], links: {}, repositoryUrl: '', iconFile: null, iconPreview: null, slug: ''
    });
    const [versionData, setVersionData] = useState<VersionFormData>({
        versionNumber: '1.0.0', gameVersions: ['Release 1.1'], changelog: '', file: null, dependencies: [], modIds: [], channel: 'RELEASE'
    });

    useEffect(() => {
        const fetchProjectAndPerms = async () => {
            if (!realId) {
                setStatusModal({type: 'error', title: 'Error', msg: "Invalid Project Identifier."});
                setLoading(false);
                return;
            }

            if (!currentUser) {
                setStatusModal({type: 'error', title: 'Unauthorized', msg: "You must be signed in to edit this project."});
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
                    setStatusModal({type: 'error', title: 'Unauthorized', msg: "You do not have permission to edit this project."});
                    setLoading(false);
                    return;
                }

                setProject(data);
                setFormData({
                    title: data.title,
                    slug: data.slug || '',
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
                navigate('/');
            } finally {
                setLoading(false);
            }
        };
        fetchProjectAndPerms();
    }, [realId, currentUser, navigate]);

    const handleSaveMetadata = async (silent = false): Promise<boolean> => {
        const currentProject = project;
        if (!currentProject) return false;
        if (currentProject.status === 'PENDING' || currentProject.status === 'ARCHIVED') return false;

        if (!silent) setIsLoading(true);
        try {
            const body = {
                title: formData.title,
                slug: formData.slug,
                description: formData.summary,
                about: formData.description,
                category: formData.category,
                tags: formData.tags,
                links: formData.links,
                repositoryUrl: formData.repositoryUrl,
                license: formData.license,
                allowModpacks: currentProject.allowModpacks
            };

            await api.put(`/projects/${currentProject.id}`, body);

            const uploadConfig = {
                headers: { 'Content-Type': 'multipart/form-data' }
            };

            if (formData.iconFile) {
                const f = new FormData(); f.append('file', formData.iconFile);
                await api.put(`/projects/${currentProject.id}/icon`, f, uploadConfig);
            }
            if (bannerFile) {
                const f = new FormData(); f.append('file', bannerFile);
                await api.put(`/projects/${currentProject.id}/banner`, f, uploadConfig);
            }

            const res = await api.get(`/projects/${currentProject.id}`);
            setProject(res.data);
            if (!silent) setStatusModal({type: 'success', title: 'Saved', msg: "Changes saved successfully."});
            return true;
        } catch (e: any) {
            console.error(e);
            if(!silent) setStatusModal({type: 'error', title: 'Error', msg: e.response?.data?.message || "Failed to save changes."});
            return false;
        }
        finally { if(!silent) setIsLoading(false); }
    };

    const handleUploadVersion = async () => {
        const currentProject = project;
        if(!currentProject) return;
        if(currentProject.status === 'PENDING' || currentProject.status === 'ARCHIVED') return;

        if(!versionData.versionNumber) { setStatusModal({type: 'error', title: 'Error', msg: "Version number required"}); return; }
        if(currentProject.classification !== 'MODPACK' && !versionData.file) { setStatusModal({type: 'error', title: 'Error', msg: "File required"}); return; }

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

            await api.post(`/projects/${currentProject.id}/versions`, fd, uploadConfig);

            const res = await api.get(`/projects/${currentProject.id}`);
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
        const currentProject = project;
        if(!currentProject) return;
        if(formData.summary.length < 10) { setStatusModal({type:'error', title:'Error', msg: "Short summary must be at least 10 characters."}); return; }
        if(!formData.tags.length) { setStatusModal({type:'error', title:'Error', msg: "At least one tag is required."}); return; }
        if(currentProject.classification !== 'MODPACK' && !formData.license) { setStatusModal({type:'error', title:'Error', msg: "License is required."}); return; }
        if(!currentProject.versions?.length && currentProject.classification !== 'MODPACK') { setStatusModal({type:'error', title:'Error', msg: "You must upload at least one version."}); setActiveTab('files'); return; }

        setIsLoading(true);
        try {
            if (!currentProject.imageUrl && !formData.iconFile) {
                try {
                    const res = await fetch('https://modtale.net/assets/favicon.svg');
                    const blob = await res.blob();
                    const file = new File([blob], 'icon.svg', { type: 'image/svg+xml' });
                    const fd = new FormData();
                    fd.append('file', file);

                    const uploadConfig = {
                        headers: { 'Content-Type': 'multipart/form-data' }
                    };

                    await api.put(`/projects/${currentProject.id}/icon`, fd, uploadConfig);
                } catch (err) {
                    console.error("Failed to set default icon", err);
                }
            }

            await handleSaveMetadata(true);
            await api.post(`/projects/${currentProject.id}/submit`);
            onShowStatus('success', 'Submitted', 'Project submitted for verification.');
            const res = await api.get(`/projects/${currentProject.id}`);
            setProject(res.data);
        } catch (e: any) {
            setStatusModal({type:'error', title:'Submit Failed', msg: e.response?.data || "Failed to submit."});
        } finally {
            setIsLoading(false);
        }
    };

    const handleRevert = async () => {
        const currentProject = project;
        if(!currentProject) return;
        setIsLoading(true);
        try {
            await api.post(`/projects/${currentProject.id}/revert`);
            const res = await api.get(`/projects/${currentProject.id}`);
            setProject(res.data);
            onShowStatus('success', 'Reverted', 'Project reverted to draft.');
        } catch(e: any) {
            onShowStatus('error', 'Revert Failed', e.response?.data || "Failed to revert.");
        } finally {
            setIsLoading(false);
        }
    };

    const handleArchive = async () => {
        const currentProject = project;
        if(!currentProject) return;
        setIsLoading(true);
        try {
            await api.post(`/projects/${currentProject.id}/archive`);
            const res = await api.get(`/projects/${currentProject.id}`);
            setProject(res.data);
            onShowStatus('success', 'Archived', 'Project is now archived.');
        } catch(e: any) {
            onShowStatus('error', 'Archive Failed', e.response?.data || "Failed to archive.");
        } finally {
            setIsLoading(false);
        }
    };

    const handleUnlist = async () => {
        const currentProject = project;
        if(!currentProject) return;
        setIsLoading(true);
        try {
            await api.post(`/projects/${currentProject.id}/unlist`);
            const res = await api.get(`/projects/${currentProject.id}`);
            setProject(res.data);
            onShowStatus('success', 'Unlisted', 'Project is now unlisted.');
        } catch(e: any) {
            onShowStatus('error', 'Unlist Failed', e.response?.data || "Failed to unlist.");
        } finally {
            setIsLoading(false);
        }
    };

    const handleRestore = async () => {
        const currentProject = project;
        if(!currentProject) return;
        setIsLoading(true);
        try {
            await api.post(`/projects/${currentProject.id}/publish`);
            const res = await api.get(`/projects/${currentProject.id}`);
            setProject(res.data);
            onShowStatus('success', 'Restored', 'Project is now published.');
        } catch(e: any) {
            onShowStatus('error', 'Restore Failed', e.response?.data || "Failed to restore.");
        } finally {
            setIsLoading(false);
        }
    };

    const handleDeleteVersion = async (versionId: string) => {
        const currentProject = project;
        if(!currentProject) return;
        setIsLoading(true);
        try {
            await api.delete(`/projects/${currentProject.id}/versions/${versionId}`);
            const res = await api.get(`/projects/${currentProject.id}`);
            setProject(res.data);
        } catch(e: any) {
            setStatusModal({type: 'error', title: 'Error', msg: "Failed to delete version."});
        } finally {
            setIsLoading(false);
        }
    };

    const handleDelete = async () => {
        const currentProject = project;
        if(!currentProject) return;
        setIsLoading(true);
        try {
            await api.delete(`/projects/${currentProject.id}`);
            navigate('/');
        } catch(e: any) { setStatusModal({type: 'error', title: 'Error', msg: "Failed to delete project."}); setIsLoading(false); }
    };

    const onShowStatus = (type: any, title: string, msg: string) => setStatusModal({type, title, msg});

    if (loading) return <div className="min-h-screen bg-slate-950 flex items-center justify-center"><Spinner fullScreen={false} className="w-8 h-8" /></div>;

    const isDraft = project?.status === 'DRAFT';
    const isPending = project?.status === 'PENDING';
    const isArchived = project?.status === 'ARCHIVED';

    return (
        <>
            {statusModal && <StatusModal type={statusModal.type} title={statusModal.title} message={statusModal.msg} onClose={() => setStatusModal(null)} />}

            <ProjectBuilder
                modData={project}
                setModData={setProject}
                metaData={formData}
                setMetaData={setFormData}
                versionData={versionData}
                setVersionData={setVersionData}
                bannerPreview={bannerPreview}
                setBannerPreview={setBannerPreview}
                setBannerFile={setBannerFile}
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