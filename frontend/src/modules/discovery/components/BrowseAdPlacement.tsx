import React, { useEffect, useMemo, useState } from 'react';
import { BACKEND_URL } from '@/utils/api';
import { financeClient } from '@/modules/finance/api/financeClient';

interface BrowseAdPlacementProps {
    projectId: string;
    placement: 'WIDE_BANNER' | 'TALL_BANNER';
    className?: string;
}

export const BrowseAdPlacement: React.FC<BrowseAdPlacementProps> = ({ projectId, placement, className }) => {
    const [ad, setAd] = useState<any>(null);

    useEffect(() => {
        let mounted = true;
        if (!projectId) return;

        financeClient.getAdSlot(projectId, placement)
            .then((payload) => {
                if (!mounted || !payload?.enabled) return;
                setAd(payload);
                if (payload?.campaignId) {
                    financeClient.trackAdImpression(payload.campaignId, projectId).catch(() => {});
                }
            })
            .catch(() => {});

        return () => {
            mounted = false;
        };
    }, [projectId, placement]);

    const clickHref = useMemo(() => {
        if (!ad?.clickUrl) return '#';
        if (ad.clickUrl.startsWith('http')) return ad.clickUrl;
        return `${BACKEND_URL}${ad.clickUrl.startsWith('/') ? '' : '/'}${ad.clickUrl}`;
    }, [ad]);

    if (!ad?.enabled || !ad?.imageUrl) return null;

    const imageClasses = 'block w-full h-auto';

    return (
        <div className={className || ''}>
            <a
                href={clickHref}
                target="_blank"
                rel="noopener noreferrer sponsored nofollow"
                aria-label={ad.callToAction || ad.headline || 'Open sponsored link'}
                className="block rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900/50 p-2 shadow-sm"
            >
                <p className="mb-2 text-[10px] font-black uppercase tracking-widest text-slate-500 dark:text-slate-400">Sponsored</p>
                <img
                    src={ad.imageUrl}
                    alt={ad.creativeAltText || ad.sponsorName || ad.headline || 'Sponsored Ad'}
                    className={imageClasses + ' rounded-xl'}
                    loading="lazy"
                />
            </a>
        </div>
    );
};
