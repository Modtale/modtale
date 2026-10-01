import React, { useState, useEffect, useRef } from 'react';
import { ChevronLeft, ImageIcon, Plus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { BACKEND_URL } from '@/utils/api';
import { ImageCropperModal } from '@/components/ui/ImageCropperModal';
import { OptimizedImage } from '@/components/ui/OptimizedImage';
import { StatusModal } from '@/components/ui/StatusModal';
import { IMAGE_ACCEPT, IMAGE_FORMAT_LABEL, isSupportedImageFile, MAX_IMAGE_UPLOAD_BYTES } from '@/utils/siteLimits';

const supportsNativeScrollLinkedBanner = () => typeof CSS !== 'undefined' && typeof CSS.supports === 'function' && CSS.supports('animation-timeline: scroll(root block)');

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
    showTabsDivider?: boolean;
    actionVerticalAlign?: 'center' | 'start';
    mainContent: React.ReactNode;
    backTo?: string;
    onBack?: () => void;
}

export const JamLayout: React.FC<JamLayoutProps> = ({
    bannerUrl,
    iconUrl,
    isEditing,
    onBannerUpload,
    onIconUpload,
    titleContent,
    hostContent,
    actionContent,
    tabsAndTimers,
    showTabsDivider = true,
    actionVerticalAlign = 'center',
    mainContent,
    backTo,
    onBack
}) => {
    const [cropperOpen, setCropperOpen] = useState(false);
    const [tempImage, setTempImage] = useState<string | null>(null);
    const [sourceFile, setSourceFile] = useState<File | null>(null);
    const [uploadError, setUploadError] = useState<string | null>(null);
    const [cropType, setCropType] = useState<'icon' | 'banner'>('icon');
    const bannerParallaxRef = useRef<HTMLDivElement>(null);
    const bannerFadeRef = useRef<HTMLDivElement>(null);

    useEffect(() => () => { if (tempImage) URL.revokeObjectURL(tempImage); }, [tempImage]);

    useEffect(() => {
        if (isEditing || supportsNativeScrollLinkedBanner()) return;
        let rafId: number | null = null;
        const applyParallax = () => {
            const scrollY = Math.min(Math.max(0, window.scrollY), 1500);
            const parallaxOffset = 500 * (1 - Math.exp(-scrollY / 600));
            if (bannerParallaxRef.current) {
                bannerParallaxRef.current.style.transform = `translate3d(0, ${parallaxOffset}px, 0)`;
            }
            if (bannerFadeRef.current) {
                bannerFadeRef.current.style.height = `calc(var(--fade-base) + ${parallaxOffset}px)`;
            }
        };

        const handleScroll = () => {
            if (rafId !== null) return;
            rafId = window.requestAnimationFrame(() => {
                applyParallax();
                rafId = null;
            });
        };

        const handleResize = () => {
            if (rafId !== null) {
                window.cancelAnimationFrame(rafId);
                rafId = null;
            }
            applyParallax();
        };

        window.addEventListener('scroll', handleScroll, { passive: true });
        window.addEventListener('resize', handleResize, { passive: true });
        applyParallax();

        return () => {
            window.removeEventListener('scroll', handleScroll);
            window.removeEventListener('resize', handleResize);
            if (rafId !== null) {
                window.cancelAnimationFrame(rafId);
            }
        };
    }, [isEditing]);

    const resolveUrl = (url?: string | null) => {
        if (!url) return null;
        if (url.startsWith('blob:')) return url;
        return url.startsWith('http') ? url : `${BACKEND_URL}${url}`;
    };

    const finalBanner = resolveUrl(bannerUrl);
    const finalIcon = resolveUrl(iconUrl);

    const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>, type: 'icon' | 'banner') => {
        if (!isEditing || !e.target.files?.[0]) return;
        const file = e.target.files[0];
        e.target.value = '';
        if (file.size > MAX_IMAGE_UPLOAD_BYTES) { setUploadError('Images must be 10 MB or smaller.'); return; }
        if (!isSupportedImageFile(file)) { setUploadError(`Unsupported image type. Use ${IMAGE_FORMAT_LABEL}.`); return; }
        setUploadError(null);
        setSourceFile(file);
        setTempImage(URL.createObjectURL(file));
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
        setSourceFile(null);
    };

    const containerClasses = 'max-w-[112rem] mx-auto px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28';
    const bannerHeight = 'h-[clamp(140px,25vw,360px)]';
    return (
        <div className="min-h-screen bg-slate-50 dark:bg-[#0B1120] relative pb-20 overflow-x-hidden z-0 transition-colors duration-300">
            {uploadError && <StatusModal type="error" title="Upload Failed" message={uploadError} onClose={() => setUploadError(null)} />}
            {cropperOpen && tempImage && (
                <ImageCropperModal
                    imageSrc={tempImage}
                    sourceFile={sourceFile}
                    aspect={cropType === 'banner' ? 3 : 1}
                    onCancel={() => {
                        setCropperOpen(false);
                        setTempImage(null);
                        setSourceFile(null);
                    }}
                    onCropComplete={handleCropComplete}
                />
            )}

            <div
                ref={bannerParallaxRef}
                className={`${!isEditing ? 'modtale-project-banner-parallax' : ''} absolute top-0 left-0 right-0 w-full ${bannerHeight} z-0 will-change-transform ${finalBanner ? 'bg-transparent' : 'bg-slate-200 dark:bg-slate-800'}`}

            >
                <div className="absolute inset-0 z-0">
                    {finalBanner ? (
                        <OptimizedImage
                            src={finalBanner}
                            alt="Jam Banner"
                            baseWidth={1920}
                            priority={true}
                            className="w-full h-full object-cover opacity-100"
                        />
                    ) : (
                        <div className="absolute inset-0 bg-gradient-to-t from-slate-50 via-slate-50/20 dark:from-[#0B1120] dark:via-[#0B1120]/20 to-transparent pointer-events-none" />
                    )}
                </div>

                <div ref={bannerFadeRef} className={`${!isEditing ? 'modtale-project-banner-fade' : 'h-12 md:h-20'} absolute bottom-0 left-0 right-0 bg-gradient-to-t from-slate-50 dark:from-[#0B1120] to-transparent z-10 pointer-events-none [--fade-base:3rem] md:[--fade-base:5rem]`} />

                {isEditing && (
                    <label className={`cursor-pointer transition-all duration-300 pointer-events-auto ${
                        finalBanner
                            ? 'absolute top-6 right-6 z-30 bg-black/60 hover:bg-black/80 text-white px-4 py-2 rounded-xl text-xs font-bold border border-white/20 backdrop-blur-sm shadow-lg hover:scale-105'
                            : 'absolute inset-0 z-30 flex flex-col items-center justify-center m-6 rounded-2xl border-2 border-dashed border-white/10 hover:border-white/30 bg-white/5 hover:bg-white/10 group/banner'
                    }`}>
                        <input type="file" accept={IMAGE_ACCEPT} onChange={(e) => handleFileSelect(e, 'banner')} className="sr-only" aria-label="Upload jam banner" />
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

            <div className={`w-full ${bannerHeight} pointer-events-none relative z-0`} />

            {(backTo || onBack) && (
                <div className={`absolute top-0 left-0 right-0 z-40 ${containerClasses} h-full pointer-events-none transition-[max-width,padding] duration-300`}>
                    <div className="pt-6 pointer-events-auto w-fit">
                        {backTo ? (
                            <Link to={backTo} aria-label="Back to jams" className="flex items-center text-white/90 font-bold transition-all bg-black/30 hover:bg-black/50 backdrop-blur-md border border-white/10 p-2 md:px-4 md:py-2 rounded-full md:rounded-xl shadow-lg group/back">
                                <ChevronLeft className="w-5 h-5 md:w-4 md:h-4 md:mr-1 group-hover/back:-translate-x-1 transition-transform" aria-hidden="true" /> <span className="hidden md:inline">Back</span>
                            </Link>
                        ) : (
                            <button type="button" aria-label="Go back" onClick={onBack} className="flex items-center text-white/90 font-bold transition-all bg-black/30 hover:bg-black/50 backdrop-blur-md border border-white/10 p-2 md:px-4 md:py-2 rounded-full md:rounded-xl shadow-lg group/back">
                                <ChevronLeft className="w-5 h-5 md:w-4 md:h-4 md:mr-1 group-hover/back:-translate-x-1 transition-transform" aria-hidden="true" /> <span className="hidden md:inline">Back</span>
                            </button>
                        )}
                    </div>
                </div>
            )}

            <div className={`${containerClasses} relative z-50 -mt-6 md:-mt-16 transition-[max-width,padding] duration-300`}>
                <div className="bg-white dark:bg-slate-900 border border-slate-300 dark:border-white/20 rounded-2xl md:rounded-3xl shadow-xl min-h-[60vh]">
                    <div className="relative md:p-8 md:pb-4 border-b border-slate-200 dark:border-white/10 p-4 pt-0">
                        <div className="md:hidden flex flex-col items-start gap-3 -mt-10 mb-4 relative z-50">
                            <div className="flex-shrink-0">
                                <label className={`block w-24 h-24 rounded-2xl bg-transparent backdrop-blur-md shadow-md border-4 border-white dark:border-slate-800 ring-1 ring-black/5 dark:ring-white/10 overflow-hidden relative group ${isEditing ? 'cursor-pointer' : ''}`}>
                                    <div className="absolute inset-0 bg-white/40 dark:bg-slate-900/40 z-0 backdrop-blur-md" />
                                    <input type="file" disabled={!isEditing} accept={IMAGE_ACCEPT} onChange={(e) => handleFileSelect(e, 'icon')} className="sr-only" aria-label="Upload jam icon" />
                                    {finalIcon ? (
                                        <OptimizedImage
                                            src={finalIcon}
                                            alt="Icon"
                                            baseWidth={128}
                                            className="w-full h-full bg-transparent object-cover relative z-10"
                                        />
                                    ) : (
                                        <div className="w-full h-full bg-transparent flex flex-col items-center justify-center text-slate-400 relative z-10">
                                            <ImageIcon className="w-8 h-8 opacity-50" aria-hidden="true" />
                                        </div>
                                    )}
                                </label>
                            </div>
                            {actionContent && (
                                <div className="flex flex-wrap gap-2 w-full">
                                    {actionContent}
                                </div>
                            )}
                        </div>

                        <div className="flex flex-col md:flex-row gap-6 items-start relative z-10">
                            <div className="hidden md:block flex-shrink-0 relative z-50 -mt-14">
                                <label className={`block w-36 h-36 lg:w-40 lg:h-40 rounded-2xl bg-transparent backdrop-blur-md shadow-xl border-[6px] border-white dark:border-slate-800 ring-1 ring-black/5 dark:ring-white/10 overflow-hidden group relative ${isEditing ? 'cursor-pointer' : ''}`}>
                                    <div className="absolute inset-0 bg-white/40 dark:bg-slate-900/40 z-0 backdrop-blur-md" />
                                    <input type="file" disabled={!isEditing} accept={IMAGE_ACCEPT} onChange={(e) => handleFileSelect(e, 'icon')} className="sr-only" aria-label="Upload jam icon" />
                                    {finalIcon ? (
                                        <OptimizedImage
                                            src={finalIcon}
                                            alt="Icon"
                                            baseWidth={160}
                                            priority={true}
                                            className="w-full h-full bg-transparent object-cover relative z-10"
                                        />
                                    ) : (
                                        <div className="w-full h-full bg-transparent flex flex-col items-center justify-center text-slate-400 gap-2 relative z-10">
                                            <ImageIcon className="w-10 h-10 opacity-50" aria-hidden="true" />
                                            <span className="text-[10px] font-bold uppercase tracking-widest text-slate-400">512x512</span>
                                        </div>
                                    )}
                                    {isEditing && (
                                        <div className="absolute inset-0 bg-black/50 flex flex-col items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200 backdrop-blur-[2px] z-30">
                                            <ImageIcon className="w-8 h-8 text-white mb-2" aria-hidden="true" />
                                            <span className="text-xs font-bold text-white">Change Icon</span>
                                            <span className="text-[10px] font-medium text-white/70">Rec: 512x512</span>
                                        </div>
                                    )}
                                </label>
                            </div>

                            <div className="flex-1 min-w-0 flex flex-col justify-end w-full">
                                <div className={`flex flex-col xl:flex-row items-start justify-between gap-4 ${actionVerticalAlign === 'start' ? 'xl:items-start' : 'xl:items-center'}`}>
                                    <div className="w-full flex-1 min-w-0">
                                        {titleContent}
                                        {hostContent && <div className="mt-2">{hostContent}</div>}
                                    </div>
                                    {actionContent && (
                                        <div className="hidden md:flex flex-wrap items-center gap-2 flex-shrink-0">
                                            {actionContent}
                                        </div>
                                    )}
                                </div>

                                {tabsAndTimers && <div className={`mt-4 ${showTabsDivider ? 'border-t border-slate-200 dark:border-white/10' : ''}`}>{tabsAndTimers}</div>}
                            </div>
                        </div>
                    </div>

                    <div className="px-4 pb-8 pt-4 sm:px-6 md:px-8 md:pb-8 md:pt-5 overflow-visible">
                        {mainContent}
                    </div>
                </div>
            </div>
        </div>
    );
};
