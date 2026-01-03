import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import type {Mod, User} from '../../types.ts';
import { ArrowLeft, Upload, Image, X, ZoomIn } from 'lucide-react';
import { StatusModal } from '../../components/ui/StatusModal.tsx';
import { api, API_BASE_URL, BACKEND_URL } from '@/utils/api.ts';

interface GalleryProps {
    currentUser: User | null;
}

export const Gallery: React.FC<GalleryProps> = ({ currentUser }) => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const [mod, setMod] = useState<Mod | null>(null);
    const [uploading, setUploading] = useState(false);
    const [modal, setModal] = useState<{type: 'success'|'error', title: string, msg: string} | null>(null);
    const [viewingImage, setViewingImage] = useState<string | null>(null);

    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchMod = async () => {
            setLoading(true);
            try {
                const res = await api.get(`/projects/${id}`);
                setMod(res.data);
            } catch (err) {
                console.error("Mod not found");
            } finally {
                setLoading(false);
            }
        };
        fetchMod();
    }, [id]);

    useEffect(() => {
        if (viewingImage) {
            document.body.style.overflow = 'hidden';
        } else {
            document.body.style.overflow = 'unset';
        }
        return () => { document.body.style.overflow = 'unset'; };
    }, [viewingImage]);

    const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file || !mod) return;
        const formData = new FormData();
        formData.append('image', file);
        setUploading(true);
        try {
            await api.post(`/projects/${mod.id}/gallery`, formData);
            const res = await api.get(`/projects/${id}`);
            setMod(res.data);
            setModal({ type: 'success', title: 'Uploaded!', msg: 'Image added to gallery.' });
        } catch (err) {
            setModal({ type: 'error', title: 'Upload Failed', msg: 'Could not upload image.' });
        } finally {
            setUploading(false);
        }
    };

    const resolveUrl = (url: string) => url.startsWith('/api') ? `${BACKEND_URL}${url}` : url;

    if (loading) return <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark flex items-center justify-center text-slate-500 font-bold uppercase tracking-widest">Loading...</div>;
    if (!mod) return <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark flex items-center justify-center text-slate-500 font-bold">Project not found.</div>;

    const isOwner = currentUser?.username === mod.author;

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark pb-12">
            {modal && <StatusModal type={modal.type} title={modal.title} message={modal.msg} onClose={() => setModal(null)} />}
            {viewingImage && (
                <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/90 backdrop-blur-sm p-4 animate-in fade-in duration-200" onClick={() => setViewingImage(null)}>
                    <button onClick={() => setViewingImage(null)} className="absolute top-6 right-6 p-2 bg-black/50 rounded-full text-white hover:bg-white hover:text-black transition-colors z-[110]"><X className="w-8 h-8" /></button>
                    <div className="relative max-w-[90vw] max-h-[90vh] rounded-lg overflow-hidden shadow-2xl border border-white/10" onClick={(e) => e.stopPropagation()}>
                        <img src={resolveUrl(viewingImage)} alt="Full view" className="w-full h-full object-contain max-h-[85vh]" />
                    </div>
                </div>
            )}
            <div className="bg-white dark:bg-modtale-card border-b border-slate-200 dark:border-white/5 pt-8 pb-12 px-4">
                <div className="max-w-6xl mx-auto">
                    <button onClick={() => navigate(-1)} className="mb-6 flex items-center text-slate-500 hover:text-modtale-accent font-bold transition-colors">
                        <ArrowLeft className="w-4 h-4 mr-2" /> Back to Mod
                    </button>
                    <div className="flex justify-between items-center">
                        <h1 className="text-3xl font-black text-slate-900 dark:text-white">Gallery: <span className="text-modtale-accent">{mod.title}</span></h1>
                        {isOwner && (
                            <label className="bg-modtale-accent hover:bg-modtale-accentHover text-white px-6 py-2 rounded-lg font-bold cursor-pointer flex items-center gap-2 transition-colors shadow-md">
                                <Upload className="w-5 h-5" />
                                {uploading ? 'Uploading...' : 'Add Image'}
                                <input type="file" className="hidden" accept="image/*" onChange={handleFileUpload} disabled={uploading} />
                            </label>
                        )}
                    </div>
                </div>
            </div>
            <div className="max-w-6xl mx-auto px-4 mt-8">
                {mod.galleryImages && mod.galleryImages.length > 0 ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                        {mod.galleryImages.map((img, idx) => (
                            <div key={idx} className="aspect-video bg-black rounded-xl overflow-hidden border border-slate-200 dark:border-white/5 relative group shadow-lg">
                                <img src={resolveUrl(img)} alt={`Gallery ${idx}`} className="w-full h-full object-cover" />
                                <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-all duration-200 flex items-center justify-center">
                                    <button onClick={() => setViewingImage(img)} className="flex items-center gap-2 text-white font-bold border-2 border-white px-6 py-2 rounded-full hover:bg-white hover:text-black transition-colors transform scale-90 group-hover:scale-100">
                                        <ZoomIn className="w-5 h-5" /> View
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                ) : (
                    <div className="text-center py-20 border-2 border-dashed border-slate-300 dark:border-white/10 rounded-xl bg-white dark:bg-modtale-card shadow-inner">
                        <Image className="w-12 h-12 mx-auto text-slate-400 mb-3" />
                        <p className="text-slate-500 font-bold">No images in gallery yet.</p>
                    </div>
                )}
            </div>
        </div>
    );
};