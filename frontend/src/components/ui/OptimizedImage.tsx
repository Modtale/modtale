import React, { useState, useEffect, useMemo, useRef } from 'react';
import { getCloudflareUrl } from '../../utils/images';

interface OptimizedImageProps {
    src: string;
    alt: string;
    className?: string;
    baseWidth: number;
    priority?: boolean;
    aspectRatio?: string;
    initialQuality?: 'placeholder' | 'standard';
    onFirstLoad?: () => void;
}

const FALLBACK_SRC = '/assets/favicon.svg';

const loadedImageCache = new Set<string>();

export const OptimizedImage: React.FC<OptimizedImageProps> = ({
                                                                  src, alt, className, baseWidth, priority = false, aspectRatio, initialQuality = 'placeholder', onFirstLoad
                                                              }) => {
    const hasFallenBack = useRef(false);
    const hasReportedLoad = useRef(false);
    const imgRef = useRef<HTMLImageElement | null>(null);

    const { downlink, effectiveType, isSaveData } = useMemo(() => {
        const conn = typeof navigator !== 'undefined' ? (navigator as any).connection : null;
        return {
            downlink: conn?.downlink,
            effectiveType: conn?.effectiveType,
            isSaveData: conn?.saveData ?? false,
        };
    }, []);

    const config = useMemo(() => {
        const currentDownlink = downlink ?? 10;
        const currentEffectiveType = effectiveType ?? '4g';

        const ultraSlow = isSaveData || currentEffectiveType === 'slow-2g' || currentEffectiveType === '2g' || currentDownlink < 1;

        const fast = priority || (!isSaveData && currentEffectiveType === '4g' && currentDownlink >= 3);

        return {
            placeholder: getCloudflareUrl(src, 32, 10),
            res1x: getCloudflareUrl(src, baseWidth, ultraSlow ? 50 : 80),
            res2x: getCloudflareUrl(src, baseWidth * 2, 80),
            isUltraSlow: ultraSlow,
            isFast: fast,
        };
    }, [src, baseWidth, priority, downlink, effectiveType, isSaveData]);

    const [isLoaded, setIsLoaded] = useState(() => loadedImageCache.has(config.res1x) || loadedImageCache.has(config.res2x));

    const wasLoadedInitially = useRef(isLoaded);

    const reportImageReady = (img: HTMLImageElement) => {
        const currentSrc = img.currentSrc || img.src;
        if (currentSrc) {
            loadedImageCache.add(currentSrc);
            loadedImageCache.add(config.res1x);
            loadedImageCache.add(config.res2x);
        }

        setIsLoaded(true);

        if (!hasReportedLoad.current) {
            hasReportedLoad.current = true;
            onFirstLoad?.();
        }
    };

    useEffect(() => {
        const shouldBeLoaded = loadedImageCache.has(config.res1x) || loadedImageCache.has(config.res2x);
        setIsLoaded(shouldBeLoaded);
        hasFallenBack.current = false;
        hasReportedLoad.current = false;
        wasLoadedInitially.current = shouldBeLoaded;
    }, [src, config.res1x, config.res2x]);

    useEffect(() => {
        const img = imgRef.current;
        if (!img || hasReportedLoad.current) return;

        if (img.complete && img.naturalWidth > 0) {
            reportImageReady(img);
        }
    }, [config.res2x, onFirstLoad, src]);

    const srcSet = useMemo(() => (
        config.isUltraSlow
            ? `${config.res1x} 1x`
            : `${config.res1x} 1x, ${config.res2x} 2x`
    ), [config.isUltraSlow, config.res1x, config.res2x]);

    const initialSrc = initialQuality === 'standard' || priority ? config.res1x : config.placeholder;

    return (
        <div
            className={`relative overflow-hidden ${className || ''}`}
            style={{ aspectRatio }}
        >
            <img
                ref={imgRef}
                key={src}
                src={initialSrc}
                srcSet={srcSet}
                alt={alt}
                loading={priority ? 'eager' : 'lazy'}
                fetchPriority={priority ? 'high' : 'auto'}
                decoding="async"
                className={`w-full h-full object-cover ${
                    !wasLoadedInitially.current ? 'transition-all duration-700' : ''
                } ${
                    isLoaded || initialQuality === 'standard' ? 'blur-0 scale-100' : 'blur-xl scale-[1.03]'
                }`}
                onLoad={(e) => {
                    reportImageReady(e.currentTarget);
                }}
                onError={(e) => {
                    if (hasFallenBack.current) return;
                    hasFallenBack.current = true;
                    if (!hasReportedLoad.current) {
                        hasReportedLoad.current = true;
                        onFirstLoad?.();
                    }
                    e.currentTarget.src = FALLBACK_SRC;
                    e.currentTarget.srcset = '';
                }}
            />
        </div>
    );
};
