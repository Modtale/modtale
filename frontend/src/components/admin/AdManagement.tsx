import React, { useState, useEffect, useRef } from 'react';
import { api } from '../../utils/api';
import { Plus, Trash2, Edit2, ExternalLink, BarChart2, Check, X, Upload, Image as ImageIcon } from 'lucide-react';
import type { AffiliateAd } from '../../types';

interface AdManagementProps {
    setStatus: (status: any) => void;
}

export const AdManagement: React.FC<AdManagementProps> = ({ setStatus }) => {
    const [ads, setAds] = useState<AffiliateAd[]>([]);
    const [loading, setLoading] = useState(false);
    const [showModal, setShowModal] = useState(false);
    const [editingAd, setEditingAd] = useState<AffiliateAd | null>(null);

    const fileInputRef = useRef<HTMLInputElement>(null);
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [previewUrl, setPreviewUrl] = useState<string>('');

    const [formData, setFormData] = useState({
        title: '',
        linkUrl: '',
        active: true,
        imageUrl: ''
    });

    useEffect(() => {
        fetchAds();
    }, []);

    useEffect(() => {
        if (selectedFile) {
            const url = URL.createObjectURL(selectedFile);
            setPreviewUrl(url);
            return () => URL.revokeObjectURL(url);
        }
    }, [selectedFile]);

    const fetchAds = async () => {
        setLoading(true);
        try {
            const res = await api.get<AffiliateAd[]>('/admin/ads');
            setAds(res.data);
        } catch (e) {
            setStatus({ type: 'error', title: 'Error', msg: 'Failed to load ads.' });
        } finally {
            setLoading(false);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        const data = new FormData();
        data.append('ad', new Blob([JSON.stringify({
            title: formData.title,
            linkUrl: formData.linkUrl,
            active: formData.active,
            imageUrl: formData.imageUrl
        })], { type: 'application/json' }));

        if (selectedFile) {
            data.append('image', selectedFile);
        }

        try {
            if (editingAd) {
                await api.put(`/admin/ads/${editingAd.id}`, data, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                });
                setStatus({ type: 'success', title: 'Updated', msg: 'Ad updated successfully.' });
            } else {
                await api.post('/admin/ads', data, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                });
                setStatus({ type: 'success', title: 'Created', msg: 'Ad created successfully.' });
            }
            setShowModal(false);
            resetForm();
            fetchAds();
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Error', msg: e.response?.data?.message || 'Failed to save ad.' });
        }
    };

    const handleDelete = async (id: string) => {
        if (!confirm('Are you sure you want to delete this ad?')) return;
        try {
            await api.delete(`/admin/ads/${id}`);
            setStatus({ type: 'success', title: 'Deleted', msg: 'Ad deleted successfully.' });
            fetchAds();
        } catch (e) {
            setStatus({ type: 'error', title: 'Error', msg: 'Failed to delete ad.' });
        }
    };

    const openEdit = (ad: AffiliateAd) => {
        setEditingAd(ad);
        setFormData({
            title: ad.title,
            linkUrl: ad.linkUrl,
            active: ad.active !== undefined ? ad.active : true,
            imageUrl: ad.imageUrl
        });
        setPreviewUrl(ad.imageUrl);
        setSelectedFile(null);
        setShowModal(true);
    };

    const openCreate = () => {
        resetForm();
        setShowModal(true);
    };

    const resetForm = () => {
        setEditingAd(null);
        setFormData({ title: '', linkUrl: '', active: true, imageUrl: '' });
        setSelectedFile(null);
        setPreviewUrl('');
    };

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files[0]) {
            setSelectedFile(e.target.files[0]);
            setFormData(prev => ({...prev, imageUrl: ''}));
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <div>
                    <h2 className="text-xl font-bold text-slate-900 dark:text-white">Affiliate Links</h2>
                    <p className="text-sm text-slate-500">Manage internal affiliate advertisements.</p>
                </div>
                <button
                    onClick={openCreate}
                    className="flex items-center gap-2 px-4 py-2 bg-modtale-accent text-white rounded-lg font-bold hover:bg-modtale-accentHover transition-colors"
                >
                    <Plus className="w-4 h-4" /> Create Ad
                </button>
            </div>

            {loading ? (
                <div className="text-center py-8 text-slate-500">Loading ads...</div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {ads.map((ad) => (
                        <div key={ad.id} className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 rounded-xl overflow-hidden group flex flex-col h-full">
                            <div className="relative aspect-video bg-slate-100 dark:bg-black/20">
                                {ad.imageUrl ? (
                                    <img src={ad.imageUrl} alt={ad.title} className="w-full h-full object-cover" />
                                ) : (
                                    <div className="w-full h-full flex items-center justify-center text-slate-400">
                                        <ImageIcon className="w-8 h-8" />
                                    </div>
                                )}
                                <div className={`absolute top-2 right-2 px-2 py-1 rounded text-xs font-black uppercase ${ad.active ? 'bg-green-500 text-white' : 'bg-slate-500 text-white'}`}>
                                    {ad.active ? 'Active' : 'Inactive'}
                                </div>
                            </div>
                            <div className="p-4 flex flex-col flex-1">
                                <h3 className="font-bold text-slate-900 dark:text-white truncate mb-1">{ad.title}</h3>
                                <a href={ad.linkUrl} target="_blank" rel="noreferrer" className="text-xs text-blue-400 hover:underline flex items-center gap-1 mb-4">
                                    {ad.linkUrl} <ExternalLink className="w-3 h-3" />
                                </a>

                                <div className="flex items-center gap-4 text-xs font-bold text-slate-500 mb-4 bg-slate-50 dark:bg-white/5 p-2 rounded-lg mt-auto">
                                    <div className="flex items-center gap-1"><BarChart2 className="w-3 h-3" /> {(ad as any).views || 0} Views</div>
                                    <div className="flex items-center gap-1"><ExternalLink className="w-3 h-3" /> {(ad as any).clicks || 0} Clicks</div>
                                </div>

                                <div className="flex gap-2">
                                    <button
                                        onClick={() => openEdit(ad)}
                                        className="flex-1 py-2 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-slate-600 dark:text-slate-300 rounded-lg text-sm font-bold transition-colors flex items-center justify-center gap-2"
                                    >
                                        <Edit2 className="w-3 h-3" /> Edit
                                    </button>
                                    <button
                                        onClick={() => handleDelete(ad.id)}
                                        className="py-2 px-3 bg-red-500/10 hover:bg-red-500/20 text-red-500 rounded-lg transition-colors"
                                    >
                                        <Trash2 className="w-4 h-4" />
                                    </button>
                                </div>
                            </div>
                        </div>
                    ))}
                    {ads.length === 0 && (
                        <div className="col-span-full text-center py-12 text-slate-500 bg-slate-50 dark:bg-white/5 rounded-xl border border-dashed border-slate-300 dark:border-white/10">
                            No affiliate ads created yet.
                        </div>
                    )}
                </div>
            )}

            {showModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4 animate-in fade-in duration-200">
                    <div className="bg-white dark:bg-slate-900 rounded-2xl w-full max-w-lg shadow-2xl border border-slate-200 dark:border-white/10">
                        <div className="p-6 border-b border-slate-100 dark:border-white/5 flex justify-between items-center">
                            <h3 className="text-lg font-bold text-slate-900 dark:text-white">{editingAd ? 'Edit Ad' : 'New Ad'}</h3>
                            <button onClick={() => setShowModal(false)} className="text-slate-500 hover:text-slate-900 dark:hover:text-white"><X className="w-5 h-5" /></button>
                        </div>
                        <form onSubmit={handleSubmit} className="p-6 space-y-4">
                            <div>
                                <label className="block text-sm font-bold text-slate-700 dark:text-slate-300 mb-1">Title</label>
                                <input
                                    type="text"
                                    required
                                    value={formData.title}
                                    onChange={e => setFormData({ ...formData, title: e.target.value })}
                                    className="w-full px-4 py-2 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent outline-none text-slate-900 dark:text-white"
                                    placeholder="Ad Campaign Name"
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-bold text-slate-700 dark:text-slate-300 mb-2">Ad Image</label>
                                <div
                                    onClick={() => fileInputRef.current?.click()}
                                    className="relative w-full aspect-video rounded-xl bg-slate-100 dark:bg-black/20 border-2 border-dashed border-slate-300 dark:border-white/10 hover:border-modtale-accent hover:bg-slate-50 dark:hover:bg-white/5 transition-all cursor-pointer flex flex-col items-center justify-center group overflow-hidden"
                                >
                                    {previewUrl ? (
                                        <>
                                            <img src={previewUrl} alt="Preview" className="w-full h-full object-cover" />
                                            <div className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center text-white font-bold">
                                                Change Image
                                            </div>
                                        </>
                                    ) : (
                                        <>
                                            <Upload className="w-8 h-8 text-slate-400 group-hover:text-modtale-accent mb-2 transition-colors" />
                                            <span className="text-sm text-slate-500 font-bold">Click to upload image</span>
                                        </>
                                    )}
                                </div>
                                <input
                                    ref={fileInputRef}
                                    type="file"
                                    accept="image/*"
                                    className="hidden"
                                    onChange={handleFileChange}
                                />
                                {(!previewUrl && formData.imageUrl) && (
                                    <div className="mt-2 text-xs text-slate-500">
                                        Current external URL: <span className="font-mono">{formData.imageUrl}</span>
                                    </div>
                                )}
                            </div>

                            <div>
                                <label className="block text-sm font-bold text-slate-700 dark:text-slate-300 mb-1">Target Link URL</label>
                                <input
                                    type="url"
                                    required
                                    value={formData.linkUrl}
                                    onChange={e => setFormData({ ...formData, linkUrl: e.target.value })}
                                    className="w-full px-4 py-2 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent outline-none text-slate-900 dark:text-white"
                                    placeholder="https://..."
                                />
                            </div>

                            <div className="flex items-center gap-3 pt-2">
                                <button
                                    type="button"
                                    onClick={() => setFormData({ ...formData, active: !formData.active })}
                                    className={`w-12 h-6 rounded-full transition-colors relative ${formData.active ? 'bg-green-500' : 'bg-slate-300 dark:bg-white/10'}`}
                                >
                                    <div className={`absolute top-1 w-4 h-4 rounded-full bg-white transition-transform ${formData.active ? 'left-7' : 'left-1'}`} />
                                </button>
                                <span className="text-sm font-bold text-slate-700 dark:text-slate-300">Active</span>
                            </div>

                            <div className="flex gap-3 pt-4">
                                <button
                                    type="button"
                                    onClick={() => setShowModal(false)}
                                    className="flex-1 py-3 rounded-xl font-bold bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-white/10"
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    className="flex-1 py-3 rounded-xl font-bold bg-modtale-accent text-white hover:bg-modtale-accentHover"
                                >
                                    {editingAd ? 'Save Changes' : 'Create Ad'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};