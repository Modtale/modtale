import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, PlayCircle } from 'lucide-react';

import { BACKEND_URL } from '@/utils/api';
import { getCloudflareUrl } from '@/utils/images';
import { resolveGalleryImages, type GalleryImageInput, type ResolvedGalleryImage } from '../utils/galleryImages';

interface GalleryCarouselViewerProps {
    images?: GalleryImageInput[];
    captions?: Record<string, string>;
    title: string;
    activeIndex?: number;
    defaultActiveIndex?: number;
    onActiveIndexChange?: (index: number) => void;
    autoAdvance?: boolean;
    keyboardNavigationDirection?: 'standard' | 'inverted';
    className?: string;
    mediaClassName?: string;
    emptyFallback?: React.ReactNode;
}

const AUTO_ADVANCE_MS = 8000;

const DEFAULT_CONTAINER_CLASS_NAME = 'mb-8 overflow-hidden rounded-2xl border border-blue-200 bg-slate-50 shadow-xl shadow-blue-950/10 dark:border-blue-400/20 dark:bg-[#0B1120]';
const DEFAULT_MEDIA_CLASS_NAME = 'relative aspect-video bg-slate-200 outline-none dark:bg-slate-950';

const clampIndex = (index: number, count: number) => (
    count > 0 ? Math.min(Math.max(index, 0), count - 1) : 0
);

const resolveGalleryUrl = (url: string) => {
    if (!url) return '';
    if (url.startsWith('http') || url.startsWith('blob:') || url.startsWith('data:')) return url;
    return url.startsWith('/api') ? `${BACKEND_URL}${url}` : url;
};

const getGalleryImageUrl = (url: string, width: number, quality: number) => (
    url.startsWith('data:') ? url : getCloudflareUrl(url, width, quality)
);

const getGalleryPreviewUrl = (image: ResolvedGalleryImage, width: number, quality: number) => (
    image.type === 'youtube' && image.thumbnailUrl
        ? image.thumbnailUrl
        : getGalleryImageUrl(image.url, width, quality)
);

export const GalleryCarouselViewer: React.FC<GalleryCarouselViewerProps> = ({
    images = [],
    captions = {},
    title,
    activeIndex,
    defaultActiveIndex = 0,
    onActiveIndexChange,
    autoAdvance = true,
    keyboardNavigationDirection = 'standard',
    className = DEFAULT_CONTAINER_CLASS_NAME,
    mediaClassName = DEFAULT_MEDIA_CLASS_NAME,
    emptyFallback = null
}) => {
    const [localActiveIndex, setLocalActiveIndex] = useState(() => Math.max(0, defaultActiveIndex));
    const thumbnailRefs = useRef<Array<HTMLButtonElement | null>>([]);

    const resolvedImages = useMemo(
        () => resolveGalleryImages(images, captions).map((image) => ({
            ...image,
            url: resolveGalleryUrl(image.url)
        })),
        [captions, images]
    );

    const imageCount = resolvedImages.length;
    const isControlled = typeof activeIndex === 'number';
    const rawActiveIndex = isControlled ? activeIndex : localActiveIndex;
    const safeActiveIndex = clampIndex(rawActiveIndex, imageCount);
    const activeImage = resolvedImages[safeActiveIndex] || resolvedImages[0];

    const setActiveIndex = useCallback((nextValue: number | ((current: number) => number)) => {
        const nextIndex = clampIndex(
            typeof nextValue === 'function' ? nextValue(safeActiveIndex) : nextValue,
            imageCount
        );

        if (isControlled) {
            onActiveIndexChange?.(nextIndex);
            return;
        }

        setLocalActiveIndex(nextIndex);
        onActiveIndexChange?.(nextIndex);
    }, [imageCount, isControlled, onActiveIndexChange, safeActiveIndex]);

    useEffect(() => {
        if (imageCount === 0 || rawActiveIndex === safeActiveIndex) return;

        if (isControlled) {
            onActiveIndexChange?.(safeActiveIndex);
            return;
        }

        setLocalActiveIndex(safeActiveIndex);
    }, [imageCount, isControlled, onActiveIndexChange, rawActiveIndex, safeActiveIndex]);

    useEffect(() => {
        if (!autoAdvance || imageCount <= 1) return;

        const timer = window.setTimeout(() => {
            setActiveIndex((prev) => (prev + 1) % imageCount);
        }, AUTO_ADVANCE_MS);

        return () => window.clearTimeout(timer);
    }, [autoAdvance, imageCount, safeActiveIndex, setActiveIndex]);

    useEffect(() => {
        if (imageCount <= 1) return;

        const warmup = [
            resolvedImages[(safeActiveIndex + 1) % imageCount],
            resolvedImages[(safeActiveIndex - 1 + imageCount) % imageCount]
        ];

        const preloaded = warmup.filter((image): image is ResolvedGalleryImage => Boolean(image)).map((galleryImage) => {
            const preloadedImage = new Image();
            preloadedImage.decoding = 'async';
            preloadedImage.src = getGalleryPreviewUrl(galleryImage, 1280, 82);
            return preloadedImage;
        });

        return () => {
            preloaded.forEach((image) => {
                image.src = '';
            });
        };
    }, [imageCount, resolvedImages, safeActiveIndex]);

    useEffect(() => {
        thumbnailRefs.current[safeActiveIndex]?.scrollIntoView({
            block: 'nearest',
            inline: 'nearest',
            behavior: 'smooth'
        });
    }, [safeActiveIndex]);

    if (imageCount === 0 || !activeImage?.url) return <>{emptyFallback}</>;

    const showControls = imageCount > 1;
    const previousImage = () => setActiveIndex((prev) => (prev - 1 + imageCount) % imageCount);
    const nextImage = () => setActiveIndex((prev) => (prev + 1) % imageCount);

    return (
        <section
            aria-label={`${title} gallery`}
            className={className}
        >
            <div
                className={mediaClassName}
                tabIndex={showControls ? 0 : -1}
                onKeyDown={(event) => {
                    if (!showControls) return;
                    if (event.key === 'ArrowLeft') {
                        event.preventDefault();
                        event.stopPropagation();
                        if (keyboardNavigationDirection === 'inverted') {
                            nextImage();
                        } else {
                            previousImage();
                        }
                    }
                    if (event.key === 'ArrowRight') {
                        event.preventDefault();
                        event.stopPropagation();
                        if (keyboardNavigationDirection === 'inverted') {
                            previousImage();
                        } else {
                            nextImage();
                        }
                    }
                }}
            >
                {activeImage.type === 'youtube' && activeImage.embedUrl ? (
                    <iframe
                        key={activeImage.url}
                        src={activeImage.embedUrl}
                        title={`${title} gallery video ${safeActiveIndex + 1}`}
                        className="h-full w-full"
                        style={{ animation: 'gallery-carousel-media 420ms ease both' }}
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                        allowFullScreen
                    />
                ) : (
                    <img
                        key={activeImage.url}
                        src={getGalleryImageUrl(activeImage.url, 1280, 86)}
                        srcSet={`${getGalleryImageUrl(activeImage.url, 1280, 86)} 1x, ${getGalleryImageUrl(activeImage.url, 1920, 86)} 2x`}
                        alt={`${title} gallery image ${safeActiveIndex + 1}`}
                        className="h-full w-full object-contain"
                        style={{ animation: 'gallery-carousel-media 420ms ease both' }}
                        loading="eager"
                        fetchPriority="high"
                        decoding="async"
                    />
                )}

                {showControls && (
                    <>
                        <button
                            type="button"
                            aria-label="Previous gallery image"
                            onClick={previousImage}
                            className="absolute left-3 top-1/2 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full border border-blue-200/50 bg-blue-950/75 text-white shadow-lg backdrop-blur-sm transition-colors hover:bg-blue-900 focus:outline-none focus:ring-2 focus:ring-modtale-accent sm:left-4"
                        >
                            <ChevronLeft className="h-6 w-6" aria-hidden="true" />
                        </button>
                        <button
                            type="button"
                            aria-label="Next gallery image"
                            onClick={nextImage}
                            className="absolute right-3 top-1/2 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full border border-blue-200/50 bg-blue-950/75 text-white shadow-lg backdrop-blur-sm transition-colors hover:bg-blue-900 focus:outline-none focus:ring-2 focus:ring-modtale-accent sm:right-4"
                        >
                            <ChevronRight className="h-6 w-6" aria-hidden="true" />
                        </button>
                        <div className="absolute bottom-3 left-1/2 -translate-x-1/2 rounded-full bg-blue-950/80 px-3 py-1 text-xs font-black tracking-wider text-white shadow-lg sm:hidden">
                            {safeActiveIndex + 1} / {imageCount}
                        </div>
                        {autoAdvance && (
                            <div className="absolute bottom-0 left-0 right-0 h-1 bg-blue-950/40" aria-hidden="true">
                                <div
                                    key={`${activeImage.url}-${safeActiveIndex}`}
                                    className="h-full origin-left bg-modtale-accent"
                                    style={{ animation: `gallery-carousel-progress ${AUTO_ADVANCE_MS}ms linear forwards` }}
                                />
                            </div>
                        )}
                    </>
                )}
            </div>

            {activeImage.caption && (
                <div className="border-t border-blue-200 bg-blue-50 px-4 py-3 text-sm font-semibold text-slate-700 dark:border-blue-400/20 dark:bg-slate-900 dark:text-slate-200">
                    {activeImage.caption}
                </div>
            )}

            {showControls && (
                <div className="flex items-center gap-3 border-t border-blue-200 bg-slate-100 px-3 py-3 dark:border-blue-400/20 dark:bg-slate-900 sm:px-5">
                    <div className="flex min-w-0 flex-1 gap-3 overflow-x-auto pb-1 [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
                        {resolvedImages.map((image, index) => {
                            const isActive = index === safeActiveIndex;
                            return (
                                <button
                                    key={`${image.url}-${index}`}
                                    ref={(node) => {
                                        thumbnailRefs.current[index] = node;
                                    }}
                                    type="button"
                                    aria-label={`Show gallery image ${index + 1}`}
                                    aria-current={isActive ? 'true' : undefined}
                                    onClick={() => setActiveIndex(index)}
                                    className={`relative h-16 w-28 shrink-0 overflow-hidden rounded-md border-2 bg-slate-900 transition-all focus:outline-none focus:ring-2 focus:ring-modtale-accent sm:h-20 sm:w-36 ${
                                        isActive
                                            ? 'border-modtale-accent opacity-100 shadow-[0_0_0_1px_rgba(37,99,235,0.25)]'
                                            : 'border-blue-200 opacity-70 hover:border-blue-400 hover:opacity-100 dark:border-blue-400/20'
                                    }`}
                                >
                                    <img
                                        src={getGalleryPreviewUrl(image, 256, 74)}
                                        alt=""
                                        className="h-full w-full object-cover"
                                        loading={index < 5 ? 'eager' : 'lazy'}
                                        decoding="async"
                                    />
                                    {image.type === 'youtube' && (
                                        <span className="absolute inset-0 flex items-center justify-center bg-blue-950/20 text-white">
                                            <PlayCircle className="h-7 w-7 drop-shadow" aria-hidden="true" />
                                        </span>
                                    )}
                                </button>
                            );
                        })}
                    </div>
                </div>
            )}
        </section>
    );
};
