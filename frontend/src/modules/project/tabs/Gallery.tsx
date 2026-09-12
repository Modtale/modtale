import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Check, Copy, GripVertical, UploadCloud, Trash2, X, Image as ImageIcon, PlayCircle, Video } from 'lucide-react';
import { theme } from '@/styles/theme';
import { BACKEND_URL } from '@/utils/api';
import { Spinner } from '@/components/ui/Spinner';
import { ModalPortal } from '@/components/ui/ModalPortal';
import type { Project } from '@/types';
import { Permission } from '@/modules/permissions/permissions';
import { getGalleryEmbedSnippet, resolveGalleryImages, type ResolvedGalleryImage } from '../utils/galleryImages';
import {
    IMAGE_ACCEPT,
    IMAGE_DIMENSION_LABEL,
    IMAGE_FORMAT_LABEL,
    MAX_GALLERY_CAPTION_CHARACTERS,
    MAX_GALLERY_IMAGES,
    MAX_IMAGE_UPLOAD_BYTES,
    MAX_YOUTUBE_URL_CHARACTERS,
    isSupportedImageFile
} from '@/utils/siteLimits';

interface GalleryProps {
    projectData: Project | null;
    readOnly: boolean;
    hasProjectPermission: (perm: Permission) => boolean;
    handleGalleryDelete: (url: string) => Promise<void>;
    handleGalleryCaptionChange: (url: string, caption: string) => Promise<void>;
    handleGallerySelect: (files: File[]) => void | Promise<void>;
    handleGalleryReorder: (imageUrls: string[]) => Promise<void>;
    handleGalleryVideoAdd: (url: string) => Promise<void>;
    galleryUploadProgress: { percent: number; current: number; total: number } | null;
    isLoading: boolean;
}

const resolveImageUrl = (url: string) => (url.startsWith('/api') ? `${BACKEND_URL}${url}` : url);

export const Gallery: React.FC<GalleryProps> = ({ projectData, readOnly, hasProjectPermission, handleGalleryDelete, handleGalleryCaptionChange, handleGallerySelect, handleGalleryReorder, handleGalleryVideoAdd, galleryUploadProgress, isLoading }) => {
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [youtubeUrl, setYoutubeUrl] = useState('');
    const [copiedEmbedUrl, setCopiedEmbedUrl] = useState<string | null>(null);
    const [pendingFiles, setPendingFiles] = useState<File[]>([]);
    const [selectionError, setSelectionError] = useState<string | null>(null);
    const [isCommittingFiles, setIsCommittingFiles] = useState(false);
    const [draggedUrl, setDraggedUrl] = useState<string | null>(null);
    const [draftOrder, setDraftOrder] = useState<string[]>([]);
    const resolvedGalleryImages = useMemo(
        () => resolveGalleryImages(projectData?.galleryImages || [], projectData?.galleryImageCaptions || {}),
        [projectData?.galleryImageCaptions, projectData?.galleryImages]
    );
    const galleryByUrl = useMemo(() => new Map(resolvedGalleryImages.map(item => [item.url, item])), [resolvedGalleryImages]);
    const displayedGalleryImages = useMemo(() => {
        const source = draftOrder.length > 0 ? draftOrder : resolvedGalleryImages.map(item => item.url);
        return source.map(url => galleryByUrl.get(url)).filter((item): item is ResolvedGalleryImage => Boolean(item));
    }, [draftOrder, galleryByUrl, resolvedGalleryImages]);

    useEffect(() => {
        if (!draggedUrl) setDraftOrder([]);
    }, [projectData?.galleryImages, draggedUrl]);

    const addPendingFiles = (files: File[]) => {
        const supportedFiles = files.filter(isSupportedImageFile);
        const oversizedFiles = supportedFiles.filter(file => file.size > MAX_IMAGE_UPLOAD_BYTES);
        const validFiles = supportedFiles.filter(file => file.size <= MAX_IMAGE_UPLOAD_BYTES);
        const remaining = Math.max(0, MAX_GALLERY_IMAGES - resolvedGalleryImages.length - pendingFiles.length);
        const accepted = validFiles.slice(0, remaining).map(file => {
            (file as File & { __preview?: string }).__preview = URL.createObjectURL(file);
            return file;
        });
        const errors: string[] = [];
        if (files.length !== supportedFiles.length) errors.push(`Only ${IMAGE_FORMAT_LABEL} images are supported.`);
        if (oversizedFiles.length > 0) errors.push('Each gallery image must be 10 MB or smaller.');
        if (validFiles.length > remaining) errors.push(`You can add ${remaining} more image${remaining === 1 ? '' : 's'} (maximum ${MAX_GALLERY_IMAGES} total).`);
        setSelectionError(errors.length ? errors.join(' ') : null);
        setPendingFiles(current => [...current, ...accepted]);
    };

    const removePendingFile = (file: File) => {
        const preview = (file as File & { __preview?: string }).__preview;
        if (preview) URL.revokeObjectURL(preview);
        setPendingFiles(current => current.filter(item => item !== file));
    };

    const cancelPendingFiles = () => {
        pendingFiles.forEach(file => {
            const preview = (file as File & { __preview?: string }).__preview;
            if (preview) URL.revokeObjectURL(preview);
        });
        setPendingFiles([]);
        setSelectionError(null);
    };

    const commitPendingFiles = async () => {
        if (pendingFiles.length === 0 || isCommittingFiles) return;
        const files = pendingFiles;
        setIsCommittingFiles(true);
        try {
            await handleGallerySelect(files);
        } finally {
            setPendingFiles([]);
            setIsCommittingFiles(false);
            files.forEach(file => {
                const preview = (file as File & { __preview?: string }).__preview;
                if (preview) URL.revokeObjectURL(preview);
            });
        }
    };

    const moveDraggedItem = (targetUrl: string) => {
        if (!draggedUrl || draggedUrl === targetUrl) return;
        const current = draftOrder.length > 0 ? [...draftOrder] : resolvedGalleryImages.map(item => item.url);
        const from = current.indexOf(draggedUrl);
        const to = current.indexOf(targetUrl);
        if (from < 0 || to < 0) return;
        current.splice(from, 1);
        current.splice(to, 0, draggedUrl);
        setDraftOrder(current);
    };

    const finishDrag = async () => {
        const nextOrder = draftOrder.length > 0 ? draftOrder : resolvedGalleryImages.map(item => item.url);
        setDraggedUrl(null);
        setDraftOrder([]);
        if (nextOrder.join('\n') !== resolvedGalleryImages.map(item => item.url).join('\n')) {
            await handleGalleryReorder(nextOrder);
        }
    };

    useEffect(() => {
        if (typeof window === 'undefined' || resolvedGalleryImages.length === 0) return;
        const warmup = resolvedGalleryImages.slice(1, 5);
        const preloaded = warmup.map((item) => {
            const image = new Image();
            image.decoding = 'async';
            image.src = item.type === 'youtube' && item.thumbnailUrl
                ? item.thumbnailUrl
                : resolveImageUrl(item.url);
            return image;
        });
        return () => {
            preloaded.forEach((image) => {
                image.src = '';
            });
        };
    }, [resolvedGalleryImages]);

    const handleYoutubeSubmit = async (event: { preventDefault: () => void }) => {
        event.preventDefault();
        const trimmedUrl = youtubeUrl.trim();
        if (!trimmedUrl) return;
        await handleGalleryVideoAdd(trimmedUrl);
        setYoutubeUrl('');
    };

    const copyGalleryEmbed = async (item: ResolvedGalleryImage) => {
        if (!navigator.clipboard) return;

        const fallbackTitle = `${projectData?.title || 'Project'} gallery ${item.type === 'youtube' ? 'video' : 'image'}`;
        try {
            await navigator.clipboard.writeText(getGalleryEmbedSnippet(item, fallbackTitle));
            setCopiedEmbedUrl(item.url);
            window.setTimeout(() => {
                setCopiedEmbedUrl(current => current === item.url ? null : current);
            }, 1800);
        } catch {
            return;
        }
    };

    return (
        <div className="space-y-6">
            <div className={`flex items-center justify-between mb-4 pb-2 border-b ${theme.colors.borderFaint}`}>
                <h3 className={`text-xs font-bold ${theme.colors.textMuted} uppercase tracking-widest flex items-center gap-2`}><ImageIcon className="w-3 h-3"/> Gallery</h3>
            </div>

            {galleryUploadProgress?.total === 1 && (
                <div className={`rounded-xl border ${theme.colors.border} ${theme.colors.bgSurface} p-4 shadow-sm`} role="status" aria-live="polite">
                    <div className="mb-2 flex items-center justify-between gap-4">
                        <span className={`text-xs font-bold ${theme.colors.textPrimary}`}>
                            {galleryUploadProgress.total === 1
                                ? 'Uploading image'
                                : `Uploading image ${galleryUploadProgress.current} of ${galleryUploadProgress.total}`}
                        </span>
                        <span className={`text-xs font-black tabular-nums ${theme.colors.textSecondary}`}>{galleryUploadProgress.percent}%</span>
                    </div>
                    <div className="h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800">
                        <div
                            className="h-full rounded-full bg-modtale-accent transition-[width] duration-150 ease-out"
                            style={{ width: `${galleryUploadProgress.percent}%` }}
                        />
                    </div>
                </div>
            )}

            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                {displayedGalleryImages.map((item, idx) => (
                    <div
                        key={`${item.url}-${item.caption}`}
                        draggable={!readOnly && hasProjectPermission(Permission.PROJECT_GALLERY_ADD) && !isLoading}
                        onDragStart={() => setDraggedUrl(item.url)}
                        onDragEnter={(event) => { event.preventDefault(); moveDraggedItem(item.url); }}
                        onDragOver={(event) => event.preventDefault()}
                        onDragEnd={finishDrag}
                        className={`overflow-hidden rounded-xl border ${theme.colors.border} ${theme.colors.bgSurface} ${draggedUrl === item.url ? 'opacity-60 ring-2 ring-modtale-accent' : ''}`}
                    >
                        <div className="relative group aspect-video bg-slate-100 dark:bg-slate-950 overflow-hidden">
                            {!readOnly && hasProjectPermission(Permission.PROJECT_GALLERY_ADD) && (
                                <div className="absolute left-2 top-2 z-20 flex h-8 w-8 cursor-grab items-center justify-center rounded-lg border border-white/20 bg-blue-950/80 text-white shadow-lg backdrop-blur-sm active:cursor-grabbing" title="Drag to reorder">
                                    <GripVertical className="h-4 w-4" aria-hidden="true" />
                                </div>
                            )}
                            {idx === 0 && (
                                <div className="absolute left-11 top-2 z-20 rounded-md bg-modtale-accent px-2 py-1 text-[9px] font-black uppercase tracking-wider text-white shadow">Cover</div>
                            )}
                            <img
                                src={item.type === 'youtube' && item.thumbnailUrl ? item.thumbnailUrl : resolveImageUrl(item.url)}
                                alt=""
                                className="w-full h-full object-cover"
                                loading={idx < 2 ? 'eager' : 'lazy'}
                                fetchPriority={idx === 0 ? 'high' : 'auto'}
                                decoding="async"
                            />
                            {item.type === 'youtube' && (
                                <div className="absolute inset-0 flex items-center justify-center bg-blue-950/25 text-white">
                                    <PlayCircle className="w-12 h-12 drop-shadow-lg" aria-hidden="true" />
                                </div>
                            )}
                            <div className="absolute right-2 top-2 z-10 opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100">
                                <button
                                    type="button"
                                    onClick={() => copyGalleryEmbed(item)}
                                    className="flex h-9 w-9 items-center justify-center rounded-lg border border-white/20 bg-blue-950/80 text-white shadow-lg backdrop-blur-sm transition-colors hover:bg-blue-900 focus:outline-none focus:ring-2 focus:ring-modtale-accent"
                                    aria-label={`Copy gallery ${item.type === 'youtube' ? 'video' : 'image'} embed snippet`}
                                    title="Copy embed"
                                >
                                    {copiedEmbedUrl === item.url ? <Check className="h-4 w-4 text-green-300" aria-hidden="true" /> : <Copy className="h-4 w-4" aria-hidden="true" />}
                                </button>
                            </div>
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
                                    maxLength={MAX_GALLERY_CAPTION_CHARACTERS}
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
                                <p className={`mt-1 px-1 text-[10px] ${theme.colors.textMuted}`}>Maximum {MAX_GALLERY_CAPTION_CHARACTERS} characters.</p>
                            </div>
                        ) : item.caption ? (
                            <p className={`p-3 text-xs font-semibold ${theme.colors.textSecondary}`}>{item.caption}</p>
                        ) : null}
                    </div>
                ))}

                {!readOnly && hasProjectPermission(Permission.PROJECT_GALLERY_ADD) && (
                    <>
                        <div className="col-span-full">
                            <p className={`text-xs ${theme.colors.textMuted}`}>Gallery limits: {MAX_GALLERY_IMAGES} images total · {IMAGE_FORMAT_LABEL} · maximum 10 MB per image · {IMAGE_DIMENSION_LABEL}.</p>
                            {selectionError && <p role="alert" className={`mt-2 text-xs font-bold ${theme.colors.dangerText}`}>{selectionError}</p>}
                        </div>
                        <div
                            onClick={() => fileInputRef.current?.click()}
                            className={`aspect-video rounded-xl border-2 border-dashed flex flex-col items-center justify-center cursor-pointer transition-all ${theme.colors.border} ${theme.colors.bgSurfaceAlt} hover:border-modtale-accent hover:${theme.colors.bgSurfaceHover}`}
                        >
                            <input
                                type="file"
                                accept={IMAGE_ACCEPT}
                                multiple
                                className="hidden"
                                ref={fileInputRef}
                                onChange={(e) => {
                                    if (e.target.files && e.target.files.length > 0) {
                                        addPendingFiles(Array.from(e.target.files));
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
                                    <span className="text-xs font-bold text-slate-500 uppercase">Add Images</span>
                                    <span className="mt-1 text-[10px] font-semibold text-slate-400">Select several at once</span>
                                </>
                            )}
                        </div>

                        <form
                            onSubmit={handleYoutubeSubmit}
                            className={`aspect-video rounded-xl border-2 border-dashed ${theme.colors.border} ${theme.colors.bgSurfaceAlt} p-4 flex flex-col justify-center gap-3 transition-all focus-within:border-modtale-accent`}
                        >
                            <div className="flex items-center gap-2 text-slate-500">
                                <Video className="w-5 h-5 text-red-500" aria-hidden="true" />
                                <span className="text-xs font-bold uppercase tracking-widest">YouTube Video</span>
                            </div>
                            <input
                                type="url"
                                value={youtubeUrl}
                                onChange={(event) => setYoutubeUrl(event.target.value)}
                                placeholder="https://youtu.be/..."
                                maxLength={MAX_YOUTUBE_URL_CHARACTERS}
                                disabled={isLoading}
                                className={`w-full ${theme.colors.bgBase} border ${theme.colors.border} rounded-lg px-3 py-2 text-xs ${theme.colors.textPrimary} focus:border-modtale-accent focus:ring-1 focus:ring-modtale-accent outline-none transition-all`}
                            />
                            <button
                                type="submit"
                                disabled={isLoading || !youtubeUrl.trim()}
                                className="h-9 rounded-lg bg-modtale-accent px-3 text-xs font-black uppercase tracking-wider text-white transition-colors hover:bg-modtale-accentHover disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-500 dark:disabled:bg-slate-800"
                            >
                                Add Video
                            </button>
                        </form>
                    </>
                )}
            </div>
            {pendingFiles.length > 0 && (
                <ModalPortal>
                    <div
                        className="fixed inset-0 z-[300] flex items-center justify-center bg-black/75 p-4 backdrop-blur-sm animate-in fade-in duration-200"
                        onClick={() => {
                            if (!isCommittingFiles) cancelPendingFiles();
                        }}
                    >
                        <div
                            role="dialog"
                            aria-modal="true"
                            aria-labelledby="gallery-bulk-upload-title"
                            className={`w-full max-w-2xl max-h-[88vh] overflow-hidden rounded-2xl border ${theme.colors.border} ${theme.colors.bgSurface} shadow-2xl`}
                            onClick={(event) => event.stopPropagation()}
                        >
                            <div className={`flex items-center justify-between gap-4 border-b ${theme.colors.border} px-6 py-5`}>
                                <div>
                                    <p id="gallery-bulk-upload-title" className={`text-base font-black ${theme.colors.textPrimary}`}>
                                        {isCommittingFiles
                                            ? `Uploading ${pendingFiles.length} image${pendingFiles.length === 1 ? '' : 's'}`
                                            : `Add ${pendingFiles.length} image${pendingFiles.length === 1 ? '' : 's'}`}
                                    </p>
                                    {isCommittingFiles && (
                                        <p className={`mt-1 text-xs font-semibold ${theme.colors.textMuted}`}>
                                            Image {galleryUploadProgress?.current ?? 1} of {galleryUploadProgress?.total ?? pendingFiles.length}
                                        </p>
                                    )}
                                </div>
                                {!isCommittingFiles && (
                                    <button
                                        type="button"
                                        onClick={cancelPendingFiles}
                                        className={`flex h-9 w-9 items-center justify-center rounded-lg border ${theme.colors.border} ${theme.colors.textSecondary} transition-colors hover:bg-slate-100 dark:hover:bg-white/5`}
                                        aria-label="Close bulk image upload"
                                    >
                                        <X className="h-4 w-4" />
                                    </button>
                                )}
                            </div>

                            {isCommittingFiles && (
                                <div className={`border-b ${theme.colors.border} px-5 py-4`} role="status" aria-live="polite">
                                    <div className="mb-2 flex items-center justify-between gap-4">
                                        <span className={`text-xs font-bold ${theme.colors.textSecondary}`}>Uploading gallery</span>
                                        <span className={`text-xs font-black tabular-nums ${theme.colors.textPrimary}`}>{galleryUploadProgress?.percent ?? 0}%</span>
                                    </div>
                                    <div className="h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800">
                                        <div
                                            className="h-full rounded-full bg-modtale-accent transition-[width] duration-150 ease-out"
                                            style={{ width: `${galleryUploadProgress?.percent ?? 0}%` }}
                                        />
                                    </div>
                                </div>
                            )}

                            <div className="max-h-[62vh] overflow-y-auto p-6">
                                <div className="grid grid-cols-2 gap-4 sm:grid-cols-2 md:grid-cols-3">
                                    {pendingFiles.map(file => (
                                        <div key={`${file.name}-${file.lastModified}`} className={`relative overflow-hidden rounded-xl border ${theme.colors.border} ${theme.colors.bgBase}`}>
                                            <img src={(file as File & { __preview?: string }).__preview} alt="" className="aspect-[4/3] w-full object-cover" />
                                            {!isCommittingFiles && <button type="button" onClick={() => removePendingFile(file)} className="absolute right-1.5 top-1.5 flex h-7 w-7 items-center justify-center rounded-md bg-blue-950/80 text-white shadow" aria-label={`Remove ${file.name}`}><X className="h-3.5 w-3.5" /></button>}
                                            <p className={`truncate px-2 py-2 text-[10px] font-semibold ${theme.colors.textMuted}`}>{file.name}</p>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            {!isCommittingFiles && (
                                <div className={`flex items-center justify-end gap-2 border-t ${theme.colors.border} px-6 py-5`}>
                                    <button type="button" onClick={cancelPendingFiles} className={`rounded-lg border ${theme.colors.border} px-4 py-2.5 text-xs font-bold ${theme.colors.textSecondary}`}>Cancel</button>
                                    <button type="button" onClick={commitPendingFiles} className="rounded-lg bg-modtale-accent px-4 py-2.5 text-xs font-black text-white hover:bg-modtale-accentHover">Add {pendingFiles.length} image{pendingFiles.length === 1 ? '' : 's'}</button>
                                </div>
                            )}
                        </div>
                    </div>
                </ModalPortal>
            )}
            {projectData?.galleryImages?.length === 0 && readOnly && <div className={`text-center py-12 ${theme.colors.textMuted} italic`}>No media in gallery.</div>}
        </div>
    );
};
