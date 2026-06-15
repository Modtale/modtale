import React, { useEffect, useMemo, useRef } from 'react';
import { UploadCloud, Trash2, Image as ImageIcon } from 'lucide-react';
import { theme } from '@/styles/theme';
import { BACKEND_URL } from '@/utils/api';
import { Spinner } from '@/components/ui/Spinner';
import type { Project } from '@/types';
import { Permission } from '@/modules/permissions/permissions';
import { resolveGalleryImages } from '../utils/galleryImages';

interface GalleryProps {
    projectData: Project | null;
    readOnly: boolean;
    hasProjectPermission: (perm: Permission) => boolean;
    handleGalleryDelete: (url: string) => Promise<void>;
    handleGalleryCaptionChange: (url: string, caption: string) => Promise<void>;
    handleGallerySelect: (file: File) => void;
    isLoading: boolean;
}

const resolveImageUrl = (url: string) => (url.startsWith('/api') ? `${BACKEND_URL}${url}` : url);

export const Gallery: React.FC<GalleryProps> = ({ projectData, readOnly, hasProjectPermission, handleGalleryDelete, handleGalleryCaptionChange, handleGallerySelect, isLoading }) => {
    const fileInputRef = useRef<HTMLInputElement>(null);
    const resolvedGalleryImages = useMemo(
        () => resolveGalleryImages(projectData?.galleryImages || [], projectData?.galleryImageCaptions || {}),
        [projectData?.galleryImageCaptions, projectData?.galleryImages]
    );

    useEffect(() => {
        if (typeof window === 'undefined' || resolvedGalleryImages.length === 0) return;
        const warmup = resolvedGalleryImages.slice(1, 5);
        const preloaded = warmup.map((src) => {
            const image = new Image();
            image.decoding = 'async';
            image.src = resolveImageUrl(src.url);
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
                {resolvedGalleryImages.map((item, idx) => (
                    <div key={`${item.url}-${item.caption}`} className={`overflow-hidden rounded-xl border ${theme.colors.border} ${theme.colors.bgSurface}`}>
                        <div className="relative group aspect-video bg-slate-100 dark:bg-slate-950 overflow-hidden">
                            <img
                                src={resolveImageUrl(item.url)}
                                alt=""
                                className="w-full h-full object-cover"
                                loading={idx < 2 ? 'eager' : 'lazy'}
                                fetchPriority={idx === 0 ? 'high' : 'auto'}
                                decoding="async"
                            />
                            {!readOnly && hasProjectPermission(Permission.PROJECT_GALLERY_REMOVE) && (
                                <div className="absolute inset-0 bg-blue-950/60 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                                    <button type="button" onClick={() => handleGalleryDelete(item.url)} disabled={isLoading} className="p-2 bg-red-500 hover:bg-red-600 text-white rounded-lg shadow-lg transform scale-90 group-hover:scale-100 transition-transform"><Trash2 className="w-5 h-5" /></button>
                                </div>
                            )}
                        </div>
                        {!readOnly && hasProjectPermission(Permission.PROJECT_GALLERY_ADD) ? (
                            <div className="p-3">
                                <label className={`text-[10px] font-black uppercase ${theme.colors.textMuted} tracking-widest px-1 mb-1 block`}>Caption</label>
                                <input
                                    key={`${item.url}-${item.caption || 'empty'}`}
                                    defaultValue={item.caption}
                                    maxLength={240}
                                    disabled={isLoading}
                                    onBlur={(event) => {
                                        const nextCaption = event.currentTarget.value.trim();
                                        if (nextCaption !== item.caption) {
                                            handleGalleryCaptionChange(item.url, nextCaption);
                                        }
                                    }}
                                    className={`w-full ${theme.colors.bgBase} border ${theme.colors.border} rounded-lg px-3 py-2 text-xs ${theme.colors.textPrimary} focus:border-modtale-accent focus:ring-1 focus:ring-modtale-accent outline-none transition-all`}
                                    placeholder="Optional caption"
                                />
                            </div>
                        ) : item.caption ? (
                            <p className={`p-3 text-xs font-semibold ${theme.colors.textSecondary}`}>{item.caption}</p>
                        ) : null}
                    </div>
                ))}

                {!readOnly && hasProjectPermission(Permission.PROJECT_GALLERY_ADD) && (
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
