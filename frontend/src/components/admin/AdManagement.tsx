import React, { useState, useEffect, useRef } from 'react';
import { api } from '../../utils/api';
import { Plus, Trash2, Edit2, ExternalLink, BarChart2, X, Upload, RectangleHorizontal, Columns, Layout } from 'lucide-react';
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
    const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
    const [previewUrls, setPreviewUrls] = useState<string[]>([]);
    const [deletedCreativeIds, setDeletedCreativeIds] = useState<string[]>([]);

    const [formData, setFormData] = useState({
        title: '',
        linkUrl: '',
        trackingParam: '', // New field
        active: true
    });

    useEffect(() => {
        fetchAds();
    }, []);

    useEffect(() => {
        return () => {
            previewUrls.forEach(url => URL.revokeObjectURL(url));
        };
    }, [previewUrls]);

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
            trackingParam: formData.trackingParam,
            active: formData.active
        })], { type: 'application/json' }));

        selectedFiles.forEach(file => {
            data.append('images', file);
        });

        if (deletedCreativeIds.length > 0) {
            data.append('deleteIds', deletedCreativeIds.join(','));
        }

        try {
            if (editingAd) {
                await api.put(`/admin/ads/${editingAd.id}`, data, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                });
                setStatus({ type: 'success', title: 'Updated', msg: 'Ad campaign updated.' });
            } else {
                await api.post('/admin/ads', data, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                });
                setStatus({ type: 'success', title: 'Created', msg: 'Ad campaign created.' });
            }
            setShowModal(false);
            resetForm();
            fetchAds();
        } catch (e: any) {
            setStatus({ type: 'error', title: 'Error', msg: e.response?.data?.message || 'Failed to save ad.' });
        }
    };

    const handleDelete = async (id: string) => {
        if (!confirm('Are you sure you want to delete this campaign?')) return;
        try {
            await api.delete(`/admin/ads/${id}`);
            setStatus({ type: 'success', title: 'Deleted', msg: 'Campaign deleted.' });
            fetchAds();
        } catch (e) {
            setStatus({ type: 'error', title: 'Error', msg: 'Failed to delete.' });
        }
    };

    const openEdit = (ad: AffiliateAd) => {
        setEditingAd(ad);
        setFormData({
            title: ad.title,
            linkUrl: ad.linkUrl,
            trackingParam: ad.trackingParam || '',
            active: ad.active
        });
        setSelectedFiles([]);
        setPreviewUrls([]);
        setDeletedCreativeIds([]);
        setShowModal(true);
    };

    const openCreate = () => {
        resetForm();
        setShowModal(true);
    };

    const resetForm = () => {
        setEditingAd(null);
        setFormData({ title: '', linkUrl: '', trackingParam: '', active: true });
        setSelectedFiles([]);
        setPreviewUrls([]);
        setDeletedCreativeIds([]);
        if (fileInputRef.current) fileInputRef.current.value = '';
    };

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files.length > 0) {
            const files = Array.from(e.target.files);
            setSelectedFiles(prev => [...prev, ...files]);

            const newPreviews = files.map(f => URL.createObjectURL(f));
            setPreviewUrls(prev => [...prev, ...newPreviews]);
        }
    };

    const getIconForType = (type: string) => {
        switch(type) {
            case 'BANNER': return <RectangleHorizontal className="w-4 h-4 text-purple-500" />;
            case 'SIDEBAR': return <Columns className="w-4 h-4 text-blue-500" />;
            default: return <Layout className="w-4 h-4 text-green-500" />;
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <div>
                    <h2 className="text-xl font-bold text-slate-900 dark:text-white">Ad Campaigns</h2>
                    <p className="text-sm text-slate-500">Manage multi-format affiliate advertisements.</p>
                </div>
                <button onClick={openCreate} className="flex items-center gap-2 px-4 py-2 bg-modtale-accent text-white rounded-lg font-bold hover:bg-modtale-accentHover transition-colors"><Plus className="w-4 h-4" /> Create Campaign</button>
            </div>

            {loading ? <div className="text-center py-8 text-slate-500">Loading...</div> : (
                <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6">
                    {ads.map((ad) => (
                        <div key={ad.id} className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 rounded-xl overflow-hidden flex flex-col h-full">
                            <div className="p-4 border-b border-slate-100 dark:border-white/5 flex justify-between items-start">
                                <div>
                                    <h3 className="font-bold text-slate-900 dark:text-white truncate max-w-[200px]">{ad.title}</h3>
                                    <a href={ad.linkUrl} target="_blank" rel="noreferrer" className="text-xs text-blue-400 hover:underline flex items-center gap-1">{ad.linkUrl} <ExternalLink className="w-3 h-3" /></a>
                                </div>
                                <span className={`px-2 py-1 rounded text-[10px] font-black uppercase ${ad.active ? 'bg-green-500 text-white' : 'bg-slate-500 text-white'}`}>{ad.active ? 'Active' : 'Inactive'}</span>
                            </div>

                            <div className="p-4 flex-1 bg-slate-50 dark:bg-black/10 overflow-y-auto max-h-60">
                                <h4 className="text-xs font-bold text-slate-500 uppercase mb-2">Creatives ({ad.creatives?.length || 0})</h4>
                                <div className="space-y-2">
                                    {ad.creatives?.map(c => (
                                        <div key={c.id} className="flex items-center gap-3 p-2 bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-white/5">
                                            <img src={c.imageUrl} alt="" className="w-10 h-10 object-cover rounded bg-black/10" />
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-center gap-1.5">
                                                    {getIconForType(c.type)}
                                                    <span className="text-xs font-bold text-slate-700 dark:text-slate-300">{c.type}</span>
                                                </div>
                                                <div className="text-[10px] text-slate-500">{c.width}x{c.height}</div>
                                            </div>
                                        </div>
                                    ))}
                                    {(!ad.creatives || ad.creatives.length === 0) && <div className="text-xs text-slate-400 italic">No images uploaded.</div>}
                                </div>
                            </div>

                            <div className="p-4 border-t border-slate-100 dark:border-white/5 flex items-center justify-between gap-4">
                                <div className="flex gap-3 text-xs font-bold text-slate-500">
                                    <span className="flex items-center gap-1"><BarChart2 className="w-3 h-3"/> {ad.views || 0}</span>
                                    <span className="flex items-center gap-1"><ExternalLink className="w-3 h-3"/> {ad.clicks || 0}</span>
                                    {ad.trackingParam && <span className="flex items-center gap-1 text-modtale-accent">?{ad.trackingParam}=...</span>}
                                </div>
                                <div className="flex gap-2">
                                    <button onClick={() => openEdit(ad)} className="p-2 bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 text-slate-600 dark:text-slate-300 rounded-lg"><Edit2 className="w-4 h-4" /></button>
                                    <button onClick={() => handleDelete(ad.id)} className="p-2 bg-red-500/10 hover:bg-red-500/20 text-red-500 rounded-lg"><Trash2 className="w-4 h-4" /></button>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {showModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4 animate-in fade-in duration-200">
                    <div className="bg-white dark:bg-slate-900 rounded-2xl w-full max-w-2xl shadow-2xl border border-slate-200 dark:border-white/10 max-h-[90vh] flex flex-col">
                        <div className="p-6 border-b border-slate-100 dark:border-white/5 flex justify-between items-center">
                            <h3 className="text-lg font-bold text-slate-900 dark:text-white">{editingAd ? 'Edit Campaign' : 'New Campaign'}</h3>
                            <button onClick={() => setShowModal(false)} className="text-slate-500 hover:text-slate-900 dark:hover:text-white"><X className="w-5 h-5" /></button>
                        </div>

                        <div className="p-6 overflow-y-auto flex-1">
                            <form id="adForm" onSubmit={handleSubmit} className="space-y-6">
                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-xs font-bold text-slate-500 uppercase mb-1">Title</label>
                                        <input type="text" required value={formData.title} onChange={e => setFormData({ ...formData, title: e.target.value })} className="w-full px-4 py-2 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent outline-none text-slate-900 dark:text-white" />
                                    </div>
                                    <div>
                                        <label className="block text-xs font-bold text-slate-500 uppercase mb-1">Target URL</label>
                                        <input type="url" required value={formData.linkUrl} onChange={e => setFormData({ ...formData, linkUrl: e.target.value })} className="w-full px-4 py-2 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent outline-none text-slate-900 dark:text-white" />
                                    </div>
                                </div>

                                <div>
                                    <label className="block text-xs font-bold text-slate-500 uppercase mb-1">Tracking Parameter (Optional)</label>
                                    <input
                                        type="text"
                                        value={formData.trackingParam}
                                        onChange={e => setFormData({ ...formData, trackingParam: e.target.value })}
                                        className="w-full px-4 py-2 rounded-lg bg-slate-50 dark:bg-black/20 border border-slate-200 dark:border-white/10 focus:ring-2 focus:ring-modtale-accent outline-none text-slate-900 dark:text-white font-mono text-sm"
                                        placeholder="e.g. r, ref, utm_source"
                                    />
                                    <p className="text-[10px] text-slate-500 mt-1">If set, Modtale will append <code className="bg-slate-100 dark:bg-white/10 px-1 rounded">?{formData.trackingParam || 'param'}=PageSource</code> to the URL.</p>
                                </div>

                                {editingAd && editingAd.creatives && editingAd.creatives.length > 0 && (
                                    <div>
                                        <label className="block text-xs font-bold text-slate-500 uppercase mb-2">Current Creatives</label>
                                        <div className="grid grid-cols-2 gap-3">
                                            {editingAd.creatives.map(c => {
                                                const isDeleted = deletedCreativeIds.includes(c.id);
                                                return (
                                                    <div key={c.id} className={`flex items-center gap-3 p-3 rounded-xl border transition-all ${isDeleted ? 'bg-red-50 dark:bg-red-900/10 border-red-200 dark:border-red-900/30 opacity-60' : 'bg-slate-50 dark:bg-black/20 border-slate-200 dark:border-white/5'}`}>
                                                        <img src={c.imageUrl} alt="" className="w-12 h-12 object-cover rounded-lg bg-black/10" />
                                                        <div className="flex-1 min-w-0">
                                                            <div className="flex items-center gap-1.5 mb-0.5">
                                                                {getIconForType(c.type)}
                                                                <span className="text-xs font-bold text-slate-900 dark:text-white">{c.type}</span>
                                                            </div>
                                                            <div className="text-[10px] text-slate-500">{c.width}x{c.height}</div>
                                                        </div>
                                                        <button type="button" onClick={() => setDeletedCreativeIds(prev => isDeleted ? prev.filter(id => id !== c.id) : [...prev, c.id])} className={`p-2 rounded-lg transition-colors ${isDeleted ? 'bg-slate-200 text-slate-600' : 'hover:bg-red-100 text-red-500'}`}>
                                                            {isDeleted ? <X className="w-4 h-4" /> : <Trash2 className="w-4 h-4" />}
                                                        </button>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </div>
                                )}

                                <div>
                                    <label className="block text-xs font-bold text-slate-500 uppercase mb-2">Upload New Creatives</label>
                                    <div onClick={() => fileInputRef.current?.click()} className="border-2 border-dashed border-slate-300 dark:border-white/10 rounded-xl p-8 flex flex-col items-center justify-center text-slate-400 hover:text-modtale-accent hover:border-modtale-accent hover:bg-slate-50 dark:hover:bg-white/5 transition-all cursor-pointer">
                                        <Upload className="w-8 h-8 mb-2" />
                                        <span className="text-sm font-bold">Click to select images</span>
                                        <span className="text-xs opacity-70 mt-1">Supports multiple files. Aspect ratio is auto-detected.</span>
                                    </div>
                                    <input ref={fileInputRef} type="file" multiple accept="image/*" className="hidden" onChange={handleFileChange} />

                                    {selectedFiles.length > 0 && (
                                        <div className="mt-4 grid grid-cols-4 gap-2">
                                            {previewUrls.map((url, i) => (
                                                <div key={i} className="relative aspect-square rounded-lg overflow-hidden border border-slate-200 dark:border-white/10 group">
                                                    <img src={url} className="w-full h-full object-cover" alt="" />
                                                    <button type="button" onClick={() => {
                                                        const newFiles = [...selectedFiles]; newFiles.splice(i, 1); setSelectedFiles(newFiles);
                                                        const newUrls = [...previewUrls]; URL.revokeObjectURL(newUrls[i]); newUrls.splice(i, 1); setPreviewUrls(newUrls);
                                                    }} className="absolute top-1 right-1 p-1 bg-black/50 text-white rounded-full hover:bg-red-500 transition-colors opacity-0 group-hover:opacity-100">
                                                        <X className="w-3 h-3" />
                                                    </button>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>

                                <div className="flex items-center gap-3">
                                    <button type="button" onClick={() => setFormData({ ...formData, active: !formData.active })} className={`w-12 h-6 rounded-full transition-colors relative ${formData.active ? 'bg-green-500' : 'bg-slate-300 dark:bg-white/10'}`}>
                                        <div className={`absolute top-1 w-4 h-4 rounded-full bg-white transition-transform ${formData.active ? 'left-7' : 'left-1'}`} />
                                    </button>
                                    <span className="text-sm font-bold text-slate-700 dark:text-slate-300">Campaign Active</span>
                                </div>
                            </form>
                        </div>

                        <div className="p-6 border-t border-slate-100 dark:border-white/5 flex gap-3">
                            <button type="button" onClick={() => setShowModal(false)} className="flex-1 py-3 rounded-xl font-bold bg-slate-100 dark:bg-white/5 text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-white/10">Cancel</button>
                            <button type="submit" form="adForm" className="flex-1 py-3 rounded-xl font-bold bg-modtale-accent text-white hover:bg-modtale-accentHover">{editingAd ? 'Save Changes' : 'Create Campaign'}</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};