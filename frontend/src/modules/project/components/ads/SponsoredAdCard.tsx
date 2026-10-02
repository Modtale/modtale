import React from 'react';
import { useSponsoredPlacement } from '@/modules/finance/hooks/useSponsoredPlacement';

interface SponsoredAdCardProps {
    projectId: string;
}

export const SponsoredAdCard: React.FC<SponsoredAdCardProps> = ({ projectId }) => {
    const { ad, clickHref, containerRef } = useSponsoredPlacement(projectId, 'SIDEBAR_CARD');

    if (!ad?.enabled || !clickHref) return null;

    return (
        <div ref={containerRef} className="rounded-xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900/50 p-4 shadow-sm">
            <p className="mb-3 text-[10px] font-black uppercase tracking-widest text-slate-500 dark:text-slate-400">{ad.testCampaign ? 'Test placement' : 'Sponsored'}{ad.sponsorName ? ` · ${ad.sponsorName}` : ''}</p>
            {ad.imageUrl && (
                <a
                    href={clickHref}
                    target="_blank"
                    rel="noopener noreferrer sponsored nofollow"
                    referrerPolicy="no-referrer"
                    className="mb-3 block"
                    aria-label={ad.callToAction || ad.headline || 'Open sponsored link'}
                >
                    <img
                        src={ad.imageUrl}
                        alt={ad.creativeAltText || ad.sponsorName || ad.headline || 'Sponsored Ad'}
                        className="h-auto w-full rounded-lg border border-slate-200 object-contain dark:border-white/10"
                        loading="lazy"
                        referrerPolicy="no-referrer"
                    />
                </a>
            )}

            <h4 className="text-sm font-black text-slate-900 dark:text-white leading-tight">{ad.headline}</h4>
            <p className="text-xs text-slate-600 dark:text-slate-300 mt-1 leading-relaxed">{ad.body || ''}</p>
            <a href={clickHref} target="_blank" rel="noopener noreferrer sponsored nofollow" referrerPolicy="no-referrer" className="mt-3 inline-flex text-sm font-bold text-modtale-accent hover:underline">{ad.callToAction || 'Learn more'}<span className="sr-only"> (opens in a new tab)</span></a>

        </div>
    );
};
