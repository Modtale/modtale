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

export const OptimizedImage: React.FC<OptimizedImageProps> = ({
                                                                  src, alt, className, baseWidth, priority = false, aspectRatio
                                                              }) => {
    const [isLoaded, setIsLoaded] = useState(false);
    const hasFallenBack = useRef(false);

    const { downlink, isSaveData } = useMemo(() => {
        const conn = typeof navigator !== 'undefined' ? (navigator as any).connection : null;
        return {
            downlink: conn?.downlink ?? 10,
            isSaveData: conn?.saveData ?? false,
        };
    }, []);

    const config = useMemo(() => {
        const ultraSlow = downlink < 1 || isSaveData;
        const fast = priority || (downlink >= 10 && !isSaveData);
        return {
            placeholder: getCloudflareUrl(src, 32, 10),
            res1x: getCloudflareUrl(src, baseWidth, ultraSlow ? 50 : 80),
            res2x: getCloudflareUrl(src, baseWidth * 2, 80),
            isUltraSlow: ultraSlow,
            isFast: fast,
        };
    }, [src, baseWidth, priority, downlink, isSaveData]);

    useEffect(() => {
        setIsLoaded(false);
        hasFallenBack.current = false;
    }, [src]);

    useEffect(() => {
        if (config.isFast) {
            setIsLoaded(true);
            return;
        }

        const img = new Image();
        img.src = config.res2x;
        img.onload = () => setIsLoaded(true);

        return () => {
            img.onload = null;
        };
    }, [config.res2x, config.isFast]);

    const srcSet = useMemo(() => {
        if (!isLoaded) return undefined;
        return config.isUltraSlow
            ? `${config.res1x} 1x`
            : `${config.res1x} 1x, ${config.res2x} 2x`;
    }, [isLoaded, config.isUltraSlow, config.res1x, config.res2x]);

    return (
        <div
            className={`relative overflow-hidden bg-slate-200 dark:bg-slate-800 ${className}`}
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
                className={`w-full h-full object-cover transition-all duration-700 ${
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