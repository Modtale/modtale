import React, { useState } from 'react';
import { ChevronLeft, ImageIcon, Plus, ChevronDown, ChevronUp } from 'lucide-react';
import { BACKEND_URL } from '../../utils/api.ts';
import { ImageCropperModal } from '@/components/ui/ImageCropperModal.tsx';

export const SidebarSection = ({
                                   title,
                                   icon: Icon,
                                   children,
                                   defaultOpen = true,
                                   className = ""
                               }: {
    title: string;
    icon?: React.ElementType;
    children: React.ReactNode;
    defaultOpen?: boolean;
    className?: string;
}) => {
    const [isOpen, setIsOpen] = useState(defaultOpen);

    const MOBILE_VISIBLE_KEYWORDS = ['Downloads', 'Ratings', 'Dependencies'];
    const isVisibleOnMobile = MOBILE_VISIBLE_KEYWORDS.some(keyword =>
        title.toLowerCase().includes(keyword.toLowerCase())
    );
    const visibilityClass = isVisibleOnMobile ? "" : "hidden md:block";

    return (
        <div className={`border-b border-slate-200 dark:border-white/5 last:border-0 pb-4 mb-4 last:mb-0 last:pb-0 ${visibilityClass} ${className}`}>
            <button
                onClick={() => setIsOpen(!isOpen)}
                className="w-full flex items-center justify-between text-xs font-bold text-slate-500 uppercase tracking-widest mb-3 hover:text-slate-900 dark:hover:text-white transition-colors"
            >
                <span className="flex items-center gap-2">{Icon && <Icon className="w-3 h-3" />} {title}</span>
                {isOpen ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
            </button>
            {isOpen && <div className="animate-in fade-in slide-in-from-top-1 duration-200">{children}</div>}
        </div>
    );
};

interface ProjectLayoutProps {
    bannerUrl?: string | null;
    iconUrl?: string | null;
    isEditing?: boolean;
    onBannerUpload?: (file: File, preview: string) => void;
    onIconUpload?: (file: File, preview: string) => void;

    headerContent: React.ReactNode;
    headerActions?: React.ReactNode;
    actionBar?: React.ReactNode;
    tabs?: React.ReactNode;
    mainContent: React.ReactNode;
    sidebarContent: React.ReactNode;
    onBack?: () => void;
}

export const ProjectLayout: React.FC<ProjectLayoutProps> = ({
                                                                bannerUrl,
                                                                iconUrl,
                                                                isEditing,
                                                                onBannerUpload,
                                                                onIconUpload,
                                                                headerContent,
                                                                headerActions,
                                                                actionBar,
                                                                tabs,
                                                                mainContent,
                                                                sidebarContent,
                                                                onBack
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

    const bgStyle = finalBanner
        ? { backgroundImage: `url(${finalBanner})` }
        : { backgroundImage: 'linear-gradient(to bottom right, #1e293b, #0f172a)' };

    const containerClasses = "max-w-[112rem] px-4 sm:px-12 md:px-16 lg:px-28";

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

            <div className="relative w-full aspect-[3/1] bg-slate-800 overflow-hidden group z-10">
                <div className={`w-full h-full bg-cover bg-center transition-opacity duration-300 ${finalBanner ? 'opacity-100' : 'opacity-0'}`} style={bgStyle}></div>
                <div className="absolute inset-0 bg-gradient-to-t from-slate-50/90 dark:from-slate-950/90 to-transparent" />

                {onBack && (
                    <div className={`absolute top-0 left-0 right-0 z-40 mx-auto ${containerClasses} h-full pointer-events-none transition-[max-width,padding] duration-300`}>
                        <div className="pt-6 pointer-events-auto w-fit">
                            <button onClick={onBack} className="flex items-center text-white/90 font-bold transition-all bg-black/30 hover:bg-black/50 backdrop-blur-md border border-white/10 p-2 md:px-4 md:py-2 rounded-full md:rounded-xl shadow-lg group/back">
                                <ChevronLeft className="w-5 h-5 md:w-4 md:h-4 md:mr-1 group-hover/back:-translate-x-1 transition-transform" /> <span className="hidden md:inline">Back</span>
                            </button>
                        </div>
                    </div>
                )}

                {isEditing && (
                    <label className={`cursor-pointer transition-all duration-300 ${
                        finalBanner
                            ? "absolute top-6 right-6 z-30 bg-black/60 hover:bg-black/80 text-white px-4 py-2 rounded-xl text-xs font-bold border border-white/20 backdrop-blur-sm shadow-lg hover:scale-105"
                            : "absolute inset-0 z-30 flex flex-col items-center justify-center m-6 rounded-2xl border-2 border-dashed border-white/10 hover:border-white/30 bg-white/5 hover:bg-white/10 group/banner"
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
                                <span className="text-lg font-bold text-white/80">Upload Banner</span>
                                <span className="text-xs font-medium text-white/40 mt-1">Recommended: 1920x640</span>
                            </div>
                        )}
                    </label>
                )}
            </div>

            <div className={`${containerClasses} mx-auto relative z-50 -mt-2 md:-mt-32 transition-[max-width,padding] duration-300`}>
                <div className={`bg-white/90 dark:bg-slate-900/90 backdrop-blur-2xl border border-slate-200 dark:border-white/10 rounded-3xl shadow-2xl min-h-[80vh]`}>
                    <div className="relative md:p-12 md:pb-6 border-b border-slate-200 dark:border-white/5 p-4 pt-0">
                        <div className="md:hidden flex justify-between items-end -mt-16 mb-6 relative z-50">
                            <div className="flex-shrink-0">
                                <label className={`block w-32 h-32 rounded-2xl bg-slate-100 dark:bg-slate-800 shadow-2xl overflow-hidden border-4 border-slate-950 group relative ${isEditing ? 'cursor-pointer' : ''}`}>
                                    <input type="file" disabled={!isEditing} accept="image/*" onChange={e => handleFileSelect(e, 'icon')} className="hidden" />
                                    {finalIcon ? (
                                        <img src={finalIcon} alt="Icon" className="w-full h-full object-cover" />
                                    ) : (
                                        <div className="w-full h-full flex flex-col items-center justify-center text-slate-500">
                                            <ImageIcon className="w-8 h-8 opacity-50" />
                                        </div>
                                    )}
                                </label>
                            </div>
                            <div className="flex gap-2 mb-1">
                                {headerActions}
                            </div>
                        </div>

                        <div className="flex flex-col md:flex-row gap-8 items-start relative z-10">
                            <div className="hidden md:block flex-shrink-0 relative z-50 -mt-24 ml-2">
                                <label className={`block w-56 h-56 rounded-[2.5rem] bg-slate-100 dark:bg-slate-800 shadow-2xl overflow-hidden border-[8px] border-white dark:border-slate-800 group relative ${isEditing ? 'cursor-pointer' : ''}`}>
                                    <input type="file" disabled={!isEditing} accept="image/*" onChange={e => handleFileSelect(e, 'icon')} className="hidden" />
                                    {finalIcon ? (
                                        <img src={finalIcon} alt="Icon" className="w-full h-full object-cover" />
                                    ) : (
                                        <div className="w-full h-full flex flex-col items-center justify-center text-slate-500 gap-2">
                                            <ImageIcon className="w-10 h-10 opacity-50" />
                                            <span className="text-[10px] font-bold uppercase tracking-widest opacity-50">512x512</span>
                                        </div>
                                    )}
                                    {isEditing && (
                                        <div className="absolute inset-0 bg-black/50 flex flex-col items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200 backdrop-blur-[2px]">
                                            <ImageIcon className="w-8 h-8 text-white mb-2" />
                                            <span className="text-xs font-bold text-white">Change Icon</span>
                                            <span className="text-[10px] font-medium text-white/70">Rec: 512x512</span>
                                        </div>
                                    )}
                                </label>
                            </div>

                            <div className="flex-1 min-w-0 flex flex-col justify-end pt-2 w-full">
                                <div className="flex flex-col xl:flex-row items-start justify-between gap-4">
                                    <div className="w-full flex-1 min-w-0">
                                        {headerContent}
                                    </div>

                                    {headerActions && (
                                        <div className="hidden md:flex items-center gap-2 flex-shrink-0 mt-2 xl:mt-0">
                                            {headerActions}
                                        </div>
                                    )}
                                </div>

                                {actionBar && (
                                    <div className="mt-8 pt-8 border-t border-slate-200 dark:border-white/5 w-full">
                                        {actionBar}
                                    </div>
                                )}

                                {tabs && <div className="mt-6 border-t border-slate-200 dark:border-white/5 pt-1">{tabs}</div>}
                            </div>
                        </div>
                    </div>

                    <div className="flex flex-col lg:grid lg:grid-cols-12 min-h-[500px]">
                        <div className="lg:col-span-8 xl:col-span-9 p-6 md:p-12 md:border-r md:border-slate-200 md:dark:border-white/5 order-2 lg:order-1">
                            {mainContent}
                        </div>

                        <div className="lg:col-span-4 xl:col-span-3 p-3 md:p-6 space-y-6 bg-transparent border-t md:border-t-0 border-slate-200 dark:border-white/5 order-1 lg:order-2">
                            {sidebarContent}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};