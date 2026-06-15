import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, PlayCircle } from 'lucide-react';

import { BACKEND_URL } from '@/utils/api';
import { getCloudflareUrl } from '@/utils/images';
import { resolveGalleryImages, type GalleryImageInput, type ResolvedGalleryImage } from '../utils/galleryImages';

interface GalleryCarouselProps {
    images?: GalleryImageInput[];
    captions?: Record<string, string>;
    title: string;
}

const AUTO_ADVANCE_MS = 8000;

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

export const GalleryCarousel: React.FC<GalleryCarouselProps> = ({ images = [], captions = {}, title }) => {
    const [activeIndex, setActiveIndex] = useState(0);
    const thumbnailRefs = useRef<Array<HTMLButtonElement | null>>([]);

    const resolvedImages = useMemo(
        () => resolveGalleryImages(images, captions).map((image) => ({
            ...image,
            url: resolveGalleryUrl(image.url)
        })),
        [captions, images]
    );

    const imageCount = resolvedImages.length;
    const activeImage = resolvedImages[activeIndex] || resolvedImages[0];

    useEffect(() => {
        if (activeIndex <= imageCount - 1) return;
        setActiveIndex(Math.max(0, imageCount - 1));
    }, [activeIndex, imageCount]);

    useEffect(() => {
        if (imageCount <= 1) return;

        const timer = window.setTimeout(() => {
            setActiveIndex((prev) => (prev + 1) % imageCount);
        }, AUTO_ADVANCE_MS);

        return () => window.clearTimeout(timer);
    }, [activeIndex, imageCount]);

    useEffect(() => {
        if (imageCount <= 1) return;

        const warmup = [
            resolvedImages[(activeIndex + 1) % imageCount],
            resolvedImages[(activeIndex - 1 + imageCount) % imageCount]
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
    }, [activeIndex, imageCount, resolvedImages]);

    useEffect(() => {
        thumbnailRefs.current[activeIndex]?.scrollIntoView({
            block: 'nearest',
            inline: 'nearest',
            behavior: 'smooth'
        });
    }, [activeIndex]);

    if (imageCount === 0 || !activeImage?.url) return null;

    const showControls = imageCount > 1;
    const previousImage = () => setActiveIndex((prev) => (prev - 1 + imageCount) % imageCount);
    const nextImage = () => setActiveIndex((prev) => (prev + 1) % imageCount);

    return (
        <section
            aria-label={`${title} gallery`}
            className="mb-8 overflow-hidden rounded-2xl border border-blue-200 bg-slate-50 shadow-xl shadow-blue-950/10 dark:border-blue-400/20 dark:bg-[#0B1120]"
        >
            <div
                className="relative aspect-video bg-slate-200 outline-none dark:bg-slate-950"
                tabIndex={showControls ? 0 : -1}
                onKeyDown={(event) => {
                    if (!showControls) return;
                    if (event.key === 'ArrowLeft') {
                        event.preventDefault();
                        previousImage();
                    }
                    if (event.key === 'ArrowRight') {
                        event.preventDefault();
                        nextImage();
                    }
                }}
            >
                {activeImage.type === 'youtube' && activeImage.embedUrl ? (
                    <iframe
                        key={activeImage.url}
                        src={activeImage.embedUrl}
                        title={`${title} gallery video ${activeIndex + 1}`}
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
                        alt={`${title} gallery image ${activeIndex + 1}`}
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
                            {activeIndex + 1} / {imageCount}
                        </div>
                        <div className="absolute bottom-0 left-0 right-0 h-1 bg-blue-950/40" aria-hidden="true">
                            <div
                                key={`${activeImage.url}-${activeIndex}`}
                                className="h-full origin-left bg-modtale-accent"
                                style={{ animation: `gallery-carousel-progress ${AUTO_ADVANCE_MS}ms linear forwards` }}
                            />
                        </div>
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
                            const isActive = index === activeIndex;
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
