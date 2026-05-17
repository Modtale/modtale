import React from 'react';
import { UploadCloud, Trash2, ImageIcon } from 'lucide-react';
import { theme } from '@/styles/theme';
import { BACKEND_URL } from '@/utils/api';
import { Spinner } from '@/components/ui/Spinner';
import type { Project } from '@/types';

interface GalleryProps {
    projectData: Project | null;
    readOnly: boolean;
    hasProjectPermission: (perm: string) => boolean;
    handleGalleryDelete: (url: string) => Promise<void>;
    getGalleryRootProps: any;
    getGalleryInputProps: any;
    isGalleryDragActive: boolean;
    isLoading: boolean;
}

export const Gallery: React.FC<GalleryProps> = ({ projectData, readOnly, hasProjectPermission, handleGalleryDelete, getGalleryRootProps, getGalleryInputProps, isGalleryDragActive, isLoading }) => {
    return (
        <div className="space-y-6">
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                {projectData?.galleryImages?.map((img, idx) => (
                    <div key={idx} className={`relative group aspect-video bg-black/20 rounded-xl overflow-hidden border ${theme.colors.border}`}>
                        <img src={img.startsWith('/api') ? `${BACKEND_URL}${img}` : img} alt="" className="w-full h-full object-cover" />
                        {!readOnly && hasProjectPermission('PROJECT_GALLERY_REMOVE') && (
                            <div className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                                <button type="button" onClick={() => handleGalleryDelete(img)} disabled={isLoading} className="p-2 bg-red-500 hover:bg-red-600 text-white rounded-lg shadow-lg transform scale-90 group-hover:scale-100 transition-transform"><Trash2 className="w-5 h-5" /></button>
                            </div>
                        )}
                    </div>
                ))}
                {!readOnly && hasProjectPermission('PROJECT_GALLERY_ADD') && (
                    <div {...getGalleryRootProps()} className={`aspect-video rounded-xl border-2 border-dashed flex flex-col items-center justify-center cursor-pointer transition-all ${isGalleryDragActive ? 'border-modtale-accent bg-modtale-accent/5' : `${theme.colors.border} ${theme.colors.bgSurfaceAlt} hover:border-modtale-accent hover:${theme.colors.bgSurfaceHover}`}`}>
                        <input {...getGalleryInputProps()} />
                        {isLoading ? <Spinner className="w-6 h-6 text-modtale-accent" fullScreen={false} /> : <><UploadCloud className="w-8 h-8 text-slate-400 mb-2" /><span className="text-xs font-bold text-slate-500 uppercase">Upload Image</span></>}
                    </div>
                )}
            </div>
            {projectData?.galleryImages?.length === 0 && readOnly && <div className={`text-center py-12 ${theme.colors.textMuted} italic`}>No images in gallery.</div>}
        </div>
    );
};