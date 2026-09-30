import React from 'react';
import { useSponsoredPlacement } from '@/modules/finance/hooks/useSponsoredPlacement';

interface BrowseAdPlacementProps {
    projectId: string;
    placement: 'WIDE_BANNER' | 'TALL_BANNER';
    className?: string;
}

export const BrowseAdPlacement: React.FC<BrowseAdPlacementProps> = ({ projectId, placement, className }) => {
    const { ad, clickHref, containerRef } = useSponsoredPlacement(projectId, placement);

    if (!ad?.enabled || !ad?.imageUrl || !clickHref) return null;

    const imageClasses = 'block w-full h-auto';

    return (
        <div ref={containerRef} className={className || ''}>
            <a
                href={clickHref}
                target="_blank"
                rel="noopener noreferrer sponsored nofollow"
                    referrerPolicy="no-referrer"
                aria-label={ad.callToAction || ad.headline || 'Open sponsored link'}
                className="block rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900/50 p-2 shadow-sm"
            >
                <p className="mb-2 text-[10px] font-black uppercase tracking-widest text-slate-500 dark:text-slate-400">Sponsored</p>
                <img
                    src={ad.imageUrl}
                    alt={ad.creativeAltText || ad.sponsorName || ad.headline || 'Sponsored Ad'}
                    className={imageClasses + ' rounded-xl'}
                    loading="lazy"
                        referrerPolicy="no-referrer"
                />
            </a>
        </div>
    );
};
