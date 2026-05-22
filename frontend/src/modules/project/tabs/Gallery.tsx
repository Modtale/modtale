import React, { useEffect, useMemo, useRef } from 'react';
import { UploadCloud, Trash2, Image as ImageIcon } from 'lucide-react';
import { theme } from '@/styles/theme';
import { BACKEND_URL } from '@/utils/api';
import { Spinner } from '@/components/ui/Spinner';
import type { Project } from '@/types';

interface GalleryProps {
    projectData: Project | null;
    readOnly: boolean;
    hasProjectPermission: (perm: string) => boolean;
    handleGalleryDelete: (url: string) => Promise<void>;
    handleGallerySelect: (file: File) => void;
    isLoading: boolean;
}

export const Gallery: React.FC<GalleryProps> = ({ projectData, readOnly, hasProjectPermission, handleGalleryDelete, handleGallerySelect, isLoading }) => {
    const fileInputRef = useRef<HTMLInputElement>(null);
    const resolvedGalleryImages = useMemo(
        () => (projectData?.galleryImages || []).map((img) => (img.startsWith('/api') ? `${BACKEND_URL}${img}` : img)),
        [projectData?.galleryImages]
    );

    useEffect(() => {
        if (typeof window === 'undefined' || resolvedGalleryImages.length === 0) return;
        // Warm a few upcoming images after initial paint without preloading the entire gallery.
        const warmup = resolvedGalleryImages.slice(1, 5);
        const preloaded = warmup.map((src) => {
            const image = new Image();
            image.decoding = 'async';
            image.src = src;
            return image;
        });
        return () => {
            preloaded.forEach((image) => {
                image.src = '';
            });
        };
    }, [resolvedGalleryImages]);

    return (
        <div className="space-y-6">
            <div className={`flex items-center justify-between mb-4 pb-2 border-b ${theme.colors.borderFaint}`}>
                <h3 className={`text-xs font-bold ${theme.colors.textMuted} uppercase tracking-widest flex items-center gap-2`}><ImageIcon className="w-3 h-3"/> Gallery</h3>
            </div>

            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                {resolvedGalleryImages.map((img, idx) => (
                    <div key={idx} className={`relative group aspect-video bg-black/20 rounded-xl overflow-hidden border ${theme.colors.border}`}>
                        <img
                            src={img}
                            alt=""
                            className="w-full h-full object-cover"
                            loading={idx < 2 ? 'eager' : 'lazy'}
                            fetchPriority={idx === 0 ? 'high' : 'auto'}
                            decoding="async"
                        />
                        {!readOnly && hasProjectPermission('PROJECT_GALLERY_REMOVE') && (
                            <div className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                                <button type="button" onClick={() => handleGalleryDelete(projectData?.galleryImages?.[idx] || img)} disabled={isLoading} className="p-2 bg-red-500 hover:bg-red-600 text-white rounded-lg shadow-lg transform scale-90 group-hover:scale-100 transition-transform"><Trash2 className="w-5 h-5" /></button>
                            </div>
                        )}
                    </div>
                ))}

                {!readOnly && hasProjectPermission('PROJECT_GALLERY_ADD') && (
                    <div
                        onClick={() => fileInputRef.current?.click()}
                        className={`aspect-video rounded-xl border-2 border-dashed flex flex-col items-center justify-center cursor-pointer transition-all ${theme.colors.border} ${theme.colors.bgSurfaceAlt} hover:border-modtale-accent hover:${theme.colors.bgSurfaceHover}`}
                    >
                        <input
                            type="file"
                            accept="image/png, image/jpeg, image/webp"
                            className="hidden"
                            ref={fileInputRef}
                            onChange={(e) => {
                                if (e.target.files && e.target.files.length > 0) {
                                    handleGallerySelect(e.target.files[0]);
                                    e.target.value = '';
                                }
                            }}
                            disabled={isLoading}
                        />
                        {isLoading ? (
                            <Spinner className="w-6 h-6 text-modtale-accent" fullScreen={false} />
                        ) : (
                            <>
                                <UploadCloud className="w-8 h-8 text-slate-400 mb-2" />
                                <span className="text-xs font-bold text-slate-500 uppercase">Upload Image</span>
                            </>
                        )}
                    </div>
                )}
            </div>
            {projectData?.galleryImages?.length === 0 && readOnly && <div className={`text-center py-12 ${theme.colors.textMuted} italic`}>No images in gallery.</div>}
        </div>
    );
};
