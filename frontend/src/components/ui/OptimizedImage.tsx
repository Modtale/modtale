import React, { useState, useEffect, useMemo, useRef } from 'react';
import { getCloudflareUrl } from '../../utils/images';

interface OptimizedImageProps {
    src: string;
    alt: string;
    className?: string;
    baseWidth: number;
    priority?: boolean;
    aspectRatio?: string;
}

const FALLBACK_SRC = '/assets/favicon.svg';

const loadedImageCache = new Set<string>();

export const OptimizedImage: React.FC<OptimizedImageProps> = ({
                                                                  src, alt, className, baseWidth, priority = false, aspectRatio
                                                              }) => {
    const hasFallenBack = useRef(false);

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

    const [isLoaded, setIsLoaded] = useState(() => {
        return config.isFast || loadedImageCache.has(config.res2x);
    });

    const wasLoadedInitially = useRef(isLoaded);

    useEffect(() => {
        const shouldBeLoaded = config.isFast || loadedImageCache.has(config.res2x);
        setIsLoaded(shouldBeLoaded);
        hasFallenBack.current = false;
        wasLoadedInitially.current = shouldBeLoaded;
    }, [src, config.res2x, config.isFast]);

    useEffect(() => {
        if (isLoaded) return;

        const img = new Image();
        img.src = config.res2x;
        img.onload = () => {
            loadedImageCache.add(config.res2x);
            setIsLoaded(true);
        };

        return () => {
            img.onload = null;
        };
    }, [config.res2x, isLoaded]);

    const srcSet = useMemo(() => {
        if (!isLoaded) return undefined;
        return config.isUltraSlow
            ? `${config.res1x} 1x`
            : `${config.res1x} 1x, ${config.res2x} 2x`;
    }, [isLoaded, config.isUltraSlow, config.res1x, config.res2x]);

    return (
        <div
            className={`relative overflow-hidden ${className || ''}`}
            style={{ aspectRatio }}
        >
            <img
                key={src}
                src={isLoaded ? config.res2x : config.placeholder}
                srcSet={srcSet}
                alt={alt}
                loading={priority ? 'eager' : 'lazy'}
                fetchPriority={priority ? 'high' : 'auto'}
                decoding="async"
                className={`w-full h-full object-cover ${
                    !wasLoadedInitially.current ? 'transition-all duration-700' : ''
                } ${
                    isLoaded ? 'blur-0 scale-100' : 'blur-2xl scale-110'
                }`}
                onError={(e) => {
                    if (hasFallenBack.current) return;
                    hasFallenBack.current = true;
                    e.currentTarget.src = FALLBACK_SRC;
                    e.currentTarget.srcset = '';
                }}
            />
        </div>
    );
};