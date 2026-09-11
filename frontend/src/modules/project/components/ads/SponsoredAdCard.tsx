import React, { useEffect, useMemo, useState } from 'react';
import { BACKEND_URL } from '@/utils/api';
import { financeClient } from '@/modules/finance/api/financeClient';

interface SponsoredAdCardProps {
    projectId: string;
}

export const SponsoredAdCard: React.FC<SponsoredAdCardProps> = ({ projectId }) => {
    const [ad, setAd] = useState<any>(null);

    useEffect(() => {
        let mounted = true;

        financeClient.getAdSlot(projectId, 'SIDEBAR_CARD')
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
    }, [projectId]);

    const clickHref = useMemo(() => {
        if (!ad?.clickUrl) return '#';
        if (ad.clickUrl.startsWith('http')) return ad.clickUrl;
        return `${BACKEND_URL}${ad.clickUrl.startsWith('/') ? '' : '/'}${ad.clickUrl}`;
    }, [ad]);

    if (!ad?.enabled) return null;

    return (
        <div className="rounded-xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900/50 p-4 shadow-sm">
            {ad.imageUrl && (
                <a
                    href={clickHref}
                    target="_blank"
                    rel="noopener noreferrer sponsored nofollow"
                    className="mb-3 block"
                    aria-label={ad.callToAction || ad.headline || 'Open sponsored link'}
                >
                    <img
                        src={ad.imageUrl}
                        alt={ad.creativeAltText || ad.sponsorName || ad.headline || 'Sponsored Ad'}
                        className="h-auto w-full rounded-lg border border-slate-200 object-contain dark:border-white/10"
                        loading="lazy"
                    />
                </a>
            )}

            <h4 className="text-sm font-black text-slate-900 dark:text-white leading-tight">{ad.headline}</h4>
            <p className="text-xs text-slate-600 dark:text-slate-300 mt-1 leading-relaxed">{ad.body || ''}</p>

        </div>
    );
};
