import React, { useState } from 'react';
import { ChevronLeft, ImageIcon, Plus, Save, AlertCircle, CheckCircle2, Rocket } from 'lucide-react';
import { BACKEND_URL } from '@/utils/api';
import { ImageCropperModal } from '@/components/ui/ImageCropperModal';
import { Spinner } from '@/components/ui/Spinner';

interface JamLayoutProps {
    bannerUrl?: string | null;
    iconUrl?: string | null;
    isEditing?: boolean;
    onBannerUpload?: (file: File, preview: string) => void;
    onIconUpload?: (file: File, preview: string) => void;
    headerContent: React.ReactNode;
    headerActions?: React.ReactNode;
    tabs?: React.ReactNode;
    mainContent: React.ReactNode;
    onBack?: () => void;
    isSaving: boolean;
    isSaved: boolean;
    hasUnsavedChanges: boolean;
    onSave: () => void;
    onPublish: () => void;
    publishChecklist: { label: string; met: boolean }[];
}

export const JamLayout: React.FC<JamLayoutProps> = ({
                                                        bannerUrl, iconUrl, isEditing, onBannerUpload, onIconUpload,
                                                        headerContent, headerActions, tabs, mainContent, onBack,
                                                        isSaving, isSaved, hasUnsavedChanges, onSave, onPublish, publishChecklist
                                                    }) => {
    const [cropperOpen, setCropperOpen] = useState(false);
    const [tempImage, setTempImage] = useState<string | null>(null);
    const [cropType, setCropType] = useState<'icon' | 'banner'>('icon');

    const isReadyToPublish = publishChecklist.every(c => c.met) && !hasUnsavedChanges;

    const resolveUrl = (url?: string | null) => {
        if (!url) return null;
        if (url.startsWith('blob:')) return url;
        return url.startsWith('http') ? url : `${BACKEND_URL}${url}`;
    };

    const finalBanner = resolveUrl(bannerUrl);
    const finalIcon = resolveUrl(iconUrl);

    const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>, type: 'icon' | 'banner') => {
        if (!isEditing || !e.target.files?.[0]) return;
        setTempImage(URL.createObjectURL(e.target.files[0]));
        setCropType(type);
        setCropperOpen(true);
        e.target.value = '';
    };

    const handleCropComplete = (croppedFile: File) => {
        const preview = URL.createObjectURL(croppedFile);
        if (cropType === 'icon' && onIconUpload) onIconUpload(croppedFile, preview);
        if (cropType === 'banner' && onBannerUpload) onBannerUpload(croppedFile, preview);
        setCropperOpen(false);
        setTempImage(null);
    };

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 flex flex-col pb-32 overflow-x-hidden">
            {cropperOpen && tempImage && (
                <ImageCropperModal
                    imageSrc={tempImage}
                    aspect={cropType === 'banner' ? 3 : 1}
                    onCancel={() => { setCropperOpen(false); setTempImage(null); }}
                    onCropComplete={handleCropComplete}
                />
            )}

            <div className="relative w-full aspect-[3/1] bg-slate-900 group z-10 border-b border-slate-200 dark:border-white/10">
                <div className="absolute inset-0 z-0">
                    {finalBanner ? (
                        <img src={finalBanner} alt="" className="w-full h-full object-cover opacity-70" />
                    ) : (
                        <div className="w-full h-full bg-gradient-to-br from-indigo-900 to-slate-900 opacity-60" />
                    )}
                </div>
                <div className="absolute inset-0 bg-gradient-to-t from-slate-50 dark:from-slate-950 to-transparent z-10" />

                {onBack && (
                    <div className="absolute top-0 left-0 right-0 z-40 max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 h-full pointer-events-none">
                        <div className="pt-8 pointer-events-auto w-fit">
                            <button onClick={onBack} className="flex items-center text-white/90 font-bold bg-black/40 hover:bg-black/60 backdrop-blur-md px-4 py-2 rounded-xl transition-all group">
                                <ChevronLeft className="w-4 h-4 group-hover:-translate-x-1 transition-transform" />
                                Back
                            </button>
                        </div>
                    </div>
                )}

                {isEditing && (
                    <label className={`cursor-pointer transition-all duration-300 ${
                        finalBanner
                            ? "absolute top-8 right-8 z-30 bg-black/60 hover:bg-black/80 text-white px-4 py-2 rounded-xl text-xs font-bold border border-white/20 backdrop-blur-sm shadow-lg hover:scale-105"
                            : "absolute inset-0 z-30 flex flex-col items-center justify-center m-8 rounded-3xl border-2 border-dashed border-white/10 hover:border-white/30 bg-white/5 hover:bg-white/10 group/banner"
                    }`}>
                        <input type="file" accept="image/*" onChange={e => handleFileSelect(e, 'banner')} className="hidden" />
                        {finalBanner ? (
                            <div className="flex flex-col items-end">
                                <div className="flex items-center gap-2"><ImageIcon className="w-4 h-4" /> Change Banner</div>
                                <span className="text-[10px] font-medium text-white/50">Rec: 1920x640</span>
                            </div>
                        ) : (
                            <div className="flex flex-col items-center">
                                <Plus className="w-8 h-8 text-white/50 mb-2" />
                                <span className="text-lg font-bold text-white/80">Upload Jam Banner</span>
                                <span className="text-xs font-medium text-white/40 mt-1">Recommended: 1920x640</span>
                            </div>
                        )}
                    </label>
                )}
            </div>

            <div className="max-w-[112rem] w-full mx-auto px-4 sm:px-12 md:px-16 lg:px-28 relative z-50 -mt-20 md:-mt-24">
                <div className="flex flex-col lg:grid lg:grid-cols-12 gap-8 items-start">
                    <div className="lg:col-span-8 xl:col-span-9 w-full">
                        <div className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-[2.5rem] shadow-2xl flex flex-col">
                            <div className="relative p-6 md:p-12 pb-0 border-b border-slate-100 dark:border-white/5">
                                <div className="flex flex-col md:flex-row gap-8 items-start relative z-10">
                                    <div className="flex-shrink-0 relative z-50 -mt-16 md:-mt-28 ml-2">
                                        <label className={`block w-32 h-32 md:w-48 md:h-48 rounded-[2rem] bg-slate-100 dark:bg-slate-800 shadow-2xl overflow-hidden border-[6px] border-white dark:border-slate-900 group relative ${isEditing ? 'cursor-pointer' : ''}`}>
                                            <input type="file" disabled={!isEditing} accept="image/*" onChange={e => handleFileSelect(e, 'icon')} className="hidden" />
                                            {finalIcon ? (
                                                <img src={finalIcon} alt="Icon" className="w-full h-full object-cover" />
                                            ) : (
                                                <div className="w-full h-full flex flex-col items-center justify-center text-slate-500 gap-2">
                                                    <ImageIcon className="w-8 h-8 md:w-10 md:h-10 opacity-50" />
                                                    <span className="text-[10px] font-bold uppercase tracking-widest opacity-50">512x512</span>
                                                </div>
                                            )}
                                            {isEditing && (
                                                <div className="absolute inset-0 bg-black/50 flex flex-col items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200 backdrop-blur-[2px]">
                                                    <ImageIcon className="w-6 h-6 md:w-8 md:h-8 text-white mb-2" />
                                                    <span className="text-xs font-bold text-white">Change Icon</span>
                                                    <span className="text-[10px] font-medium text-white/70">Rec: 512x512</span>
                                                </div>
                                            )}
                                        </label>
                                    </div>

                                    <div className="flex-1 min-w-0 flex flex-col justify-end pt-2 w-full">
                                        <div className="flex flex-col xl:flex-row items-start justify-between gap-6">
                                            <div className="w-full flex-1 min-w-0">
                                                {headerContent}
                                            </div>
                                            {headerActions && (
                                                <div className="flex items-center gap-3 flex-shrink-0 mt-4 xl:mt-0">
                                                    {headerActions}
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </div>
                                {tabs && <div className="mt-8 pt-2">{tabs}</div>}
                            </div>
                            <div className="p-6 md:p-12 min-h-[500px]">
                                {mainContent}
                            </div>
                        </div>
                    </div>

                    <div className="lg:col-span-4 xl:col-span-3 w-full sticky top-28 space-y-6">
                        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 rounded-[2.5rem] p-8 shadow-xl">
                            <h3 className="text-xs font-black uppercase text-slate-500 tracking-widest mb-6">Launch Checklist</h3>
                            <div className="space-y-4 mb-8">
                                {publishChecklist.map((req, i) => (
                                    <div key={i} className="flex items-start gap-3">
                                        <div className={`mt-0.5 shrink-0 ${req.met ? 'text-green-500' : 'text-slate-300 dark:text-slate-700'}`}>
                                            {req.met ? <CheckCircle2 className="w-4 h-4" /> : <AlertCircle className="w-4 h-4" />}
                                        </div>
                                        <span className={`text-sm font-bold ${req.met ? 'text-slate-900 dark:text-slate-200' : 'text-slate-400'}`}>{req.label}</span>
                                    </div>
                                ))}
                                <div className="flex items-start gap-3 pt-4 border-t border-slate-100 dark:border-white/5 mt-4">
                                    <div className={`mt-0.5 shrink-0 ${!hasUnsavedChanges ? 'text-green-500' : 'text-amber-500'}`}>
                                        {!hasUnsavedChanges ? <CheckCircle2 className="w-4 h-4" /> : <AlertCircle className="w-4 h-4" />}
                                    </div>
                                    <span className={`text-sm font-bold ${!hasUnsavedChanges ? 'text-slate-900 dark:text-slate-200' : 'text-amber-500'}`}>All changes saved</span>
                                </div>
                            </div>

                            <div className="space-y-3">
                                <button
                                    type="button"
                                    onClick={(e) => { e.preventDefault(); onSave(); }}
                                    disabled={isSaving}
                                    className={`w-full h-14 rounded-2xl font-black text-sm transition-all flex items-center justify-center gap-2 border-2 ${
                                        isSaved ? 'bg-green-500/10 border-green-500 text-green-500' :
                                            'bg-slate-100 dark:bg-white/5 text-slate-700 dark:text-slate-300 border-transparent hover:bg-slate-200 dark:hover:bg-white/10'
                                    }`}
                                >
                                    {isSaving ? <Spinner className="w-4 h-4" fullScreen={false} /> : isSaved ? <CheckCircle2 className="w-4 h-4" /> : <Save className="w-4 h-4" />}
                                    {isSaved ? 'Changes Saved' : 'Save Draft'}
                                </button>

                                <button
                                    type="button"
                                    onClick={(e) => { e.preventDefault(); onPublish(); }}
                                    disabled={!isReadyToPublish || isSaving}
                                    className="w-full h-14 bg-modtale-accent hover:bg-modtale-accentHover disabled:bg-slate-100 dark:disabled:bg-slate-800 disabled:text-slate-400 text-white rounded-2xl font-black text-sm transition-all flex items-center justify-center gap-2 shadow-lg shadow-modtale-accent/20 enabled:hover:scale-[1.02]"
                                >
                                    <Rocket className="w-4 h-4" />
                                    Publish Jam
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};