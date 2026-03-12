import React, { useState, useEffect } from 'react';

interface OptimizedImageProps {
    src: string;
    alt: string;
    className?: string;
    baseWidth: number;
    priority?: boolean;
    aspectRatio?: string;
}

export const OptimizedImage: React.FC<OptimizedImageProps> = ({
                                                                  src,
                                                                  alt,
                                                                  className,
                                                                  baseWidth,
                                                                  priority = false,
                                                                  aspectRatio
                                                              }) => {
    const [isLoaded, setIsLoaded] = useState(false);
    const [currentSrc, setCurrentSrc] = useState<string>('');

    const getSteppedWidth = (width: number) => {
        if (width <= 64) return 64;
        if (width <= 128) return 128;
        if (width <= 256) return 256;
        if (width <= 640) return 640;
        if (width <= 1280) return 1280;
        return 1920;
    };

    const getCloudflareUrl = (url: string, width: number, quality: number = 80) => {
        if (!url || url.includes('.svg') || url.startsWith('blob:') || url.includes('localhost')) {
            return url;
        }

        const steppedWidth = getSteppedWidth(width);
        const origin = typeof window !== 'undefined' ? window.location.origin : '';
        const absoluteUrl = url.startsWith('http') ? url : `${origin}${url}`;

        return `${origin}/cdn-cgi/image/width=${steppedWidth},quality=${quality},format=auto,onerror=redirect/${absoluteUrl}`;
    };

    useEffect(() => {
        const lowRes = getCloudflareUrl(src, 64, 10);
        setCurrentSrc(lowRes);

        if (priority) {
            const img = new Image();
            img.src = getCloudflareUrl(src, baseWidth * 2);
            img.onload = () => setIsLoaded(true);
        }
    }, [src, baseWidth, priority]);

    const res1x = getCloudflareUrl(src, baseWidth);
    const res2x = getCloudflareUrl(src, baseWidth * 2);

    return (
        <div
            className={`relative overflow-hidden bg-slate-200 dark:bg-slate-800 ${className}`}
            style={{ aspectRatio }}
        >
            <img
                src={isLoaded ? res2x : currentSrc}
                srcSet={isLoaded ? `${res1x} 1x, ${res2x} 2x` : undefined}
                alt={alt}
                loading={priority ? 'eager' : 'lazy'}
                fetchPriority={priority ? 'high' : 'auto'}
                decoding={priority ? 'sync' : 'async'}
                onLoad={() => setIsLoaded(true)}
                className={`w-full h-full object-cover transition-all duration-700 ${isLoaded ? 'blur-0 scale-100' : 'blur-md scale-110'}`}
                onError={(e) => {
                    if (!src.includes('favicon')) {
                        e.currentTarget.src = '/assets/favicon.svg';
                    }
                }}
            />
        </div>
    );
};