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

    const getResUrl = (width: number, quality: number = 85) => {
        if (!src || src.includes('favicon') || src.startsWith('blob:')) return src;
        const separator = src.includes('?') ? '&' : '?';
        return `${src}${separator}w=${width}&q=${quality}`;
    };

    useEffect(() => {
        const lowRes = getResUrl(20, 30);
        setCurrentSrc(lowRes);

        if (priority) {
            const img = new Image();
            img.src = getResUrl(baseWidth * 2);
            img.onload = () => setIsLoaded(true);
        }
    }, [src, baseWidth, priority]);

    const highResUrl = getResUrl(baseWidth);
    const retinaUrl = getResUrl(baseWidth * 2);
    const superRetinaUrl = getResUrl(baseWidth * 3);

    return (
        <div
            className={`relative overflow-hidden bg-slate-200 dark:bg-slate-800 ${className}`}
            style={{ aspectRatio }}
        >
            <img
                src={isLoaded ? highResUrl : currentSrc}
                srcSet={isLoaded ? `${highResUrl} 1x, ${retinaUrl} 2x, ${superRetinaUrl} 3x` : undefined}
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