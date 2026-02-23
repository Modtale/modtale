import React, { useState } from 'react';
import { ChevronLeft, ImageIcon, Plus } from 'lucide-react';
import { BACKEND_URL } from '@/utils/api';
import { ImageCropperModal } from '@/components/ui/ImageCropperModal';

interface JamLayoutProps {
    bannerUrl?: string | null;
    iconUrl?: string | null;
    isEditing?: boolean;
    onBannerUpload?: (file: File, preview: string) => void;
    onIconUpload?: (file: File, preview: string) => void;
    titleContent: React.ReactNode;
    hostContent?: React.ReactNode;
    actionContent?: React.ReactNode;
    tabsAndTimers?: React.ReactNode;
    mainContent: React.ReactNode;
    onBack?: () => void;
}

export const JamLayout: React.FC<JamLayoutProps> = ({
                                                        bannerUrl, iconUrl, isEditing, onBannerUpload, onIconUpload,
                                                        titleContent, hostContent, actionContent, tabsAndTimers, mainContent, onBack
                                                    }) => {
    const [cropperOpen, setCropperOpen] = useState(false);
    const [tempImage, setTempImage] = useState<string | null>(null);
    const [cropType, setCropType] = useState<'icon' | 'banner'>('icon');

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
        <div className="min-h-screen bg-slate-50 dark:bg-modtale-dark pb-32">
            {cropperOpen && tempImage && (
                <ImageCropperModal
                    imageSrc={tempImage}
                    aspect={cropType === 'banner' ? 3 : 1}
                    onCancel={() => { setCropperOpen(false); setTempImage(null); }}
                    onCropComplete={handleCropComplete}
                />
            )}

            <div className="relative w-full aspect-[2/1] md:aspect-[3/1] bg-slate-900 overflow-hidden shrink-0 shadow-sm z-10">
                <div className="absolute inset-0 z-0">
                    {finalBanner ? (
                        <img
                            src={finalBanner}
                            alt=""
                            fetchPriority="high"
                            loading="eager"
                            decoding="sync"
                            className="w-full h-full object-cover transition-opacity duration-300 opacity-100"
                        />
                    ) : (
                        <div className="w-full h-full bg-gradient-to-br from-slate-800 to-slate-900" />
                    )}
                </div>
                <div className="absolute inset-0 bg-gradient-to-t from-slate-50 dark:from-modtale-dark via-slate-50/20 dark:via-modtale-dark/20 to-transparent z-10" />

                {onBack && (
                    <div className="absolute top-0 left-0 right-0 z-40 max-w-[112rem] mx-auto px-4 sm:px-12 md:px-16 lg:px-28 h-full pointer-events-none">
                        <div className="pt-6 md:pt-8 pointer-events-auto w-fit">
                            <button type="button" onClick={onBack} className="flex items-center text-white/90 font-bold bg-black/40 hover:bg-black/60 backdrop-blur-md px-4 py-2 rounded-xl transition-all group border border-white/10">
                                <ChevronLeft className="w-4 h-4 group-hover:-translate-x-1 transition-transform" />
                                Back
                            </button>
                        </div>
                    </div>
                )}

                {isEditing && (
                    <div className="absolute inset-0 z-10 pointer-events-none flex flex-col items-center justify-center p-8">
                        <label className={`pointer-events-auto cursor-pointer transition-all duration-300 ${
                            finalBanner
                                ? "absolute top-6 md:top-8 right-6 md:right-8 z-30 bg-black/60 hover:bg-black/80 text-white px-4 py-2 rounded-xl text-xs font-bold border border-white/20 backdrop-blur-sm shadow-lg hover:scale-105"
                                : "w-full h-full rounded-2xl border-2 border-dashed border-white/20 hover:border-white/40 bg-white/5 hover:bg-white/10 flex flex-col items-center justify-center group/banner"
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
                                    <span className="text-lg font-bold text-white/90">Upload Jam Banner</span>
                                    <span className="text-xs font-medium text-white/50 mt-1">Recommended: 1920x640</span>
                                </div>
                            )}
                        </label>
                    </div>
                )}

                <div className="absolute inset-0 flex flex-col justify-end pb-0 max-w-[112rem] w-full mx-auto px-4 sm:px-12 md:px-16 lg:px-28 z-30 pointer-events-none">
                    <div className="flex flex-col md:flex-row md:items-end gap-6 pointer-events-auto pb-0">
                        <div className="relative shrink-0 z-40">
                            <label className={`block w-36 h-36 md:w-48 md:h-48 rounded-2xl md:rounded-3xl bg-white dark:bg-slate-800 backdrop-blur-xl shadow-2xl border-[4px] border-white dark:border-slate-800 overflow-hidden relative ${isEditing ? 'cursor-pointer group' : ''}`}>
                                <input type="file" disabled={!isEditing} accept="image/*" onChange={e => handleFileSelect(e, 'icon')} className="hidden" />
                                {finalIcon ? (
                                    <img src={finalIcon} alt="" className="w-full h-full object-cover" />
                                ) : (
                                    <div className="w-full h-full flex flex-col items-center justify-center text-slate-500 gap-2">
                                        <ImageIcon className="w-8 h-8 md:w-10 md:h-10 opacity-40" />
                                        <span className="text-[10px] font-black uppercase tracking-widest opacity-40">512x512</span>
                                    </div>
                                )}
                                {isEditing && (
                                    <div className="absolute inset-0 bg-black/50 flex flex-col items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200 backdrop-blur-[2px]">
                                        <ImageIcon className="w-6 h-6 md:w-8 md:h-8 text-white mb-2" />
                                        <span className="text-xs font-bold text-white">Change Icon</span>
                                    </div>
                                )}
                            </label>
                        </div>

                        <div className="flex flex-col xl:flex-row xl:items-end justify-between flex-1 min-w-0 gap-4 mb-2 xl:mb-6">
                            <div className="flex-1 min-w-0 relative z-30">
                                {titleContent}
                                {hostContent && <div className="mt-2">{hostContent}</div>}
                            </div>

                            <div className="flex flex-wrap items-center gap-2 md:gap-3 shrink-0 relative z-30">
                                {actionContent}
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div className="max-w-[112rem] w-full mx-auto px-4 sm:px-12 md:px-16 lg:px-28 relative z-50 pt-1.5 md:pt-3 mt-6 md:mt-10">
                {tabsAndTimers && <div className="mb-4 md:mb-6">{tabsAndTimers}</div>}

                <div className="w-full">
                    {mainContent}
                </div>
            </div>
        </div>
    );
};