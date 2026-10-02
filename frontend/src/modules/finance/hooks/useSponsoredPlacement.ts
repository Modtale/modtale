import { useEffect, useRef, useState } from 'react';
import { financeClient } from '@/modules/finance/api/financeClient';
import type { SponsoredPlacement } from '@/modules/finance/api/financeTypes';
import { BACKEND_URL } from '@/utils/api';

export function useSponsoredPlacement(projectId: string, placement: string) {
    const containerRef = useRef<HTMLDivElement>(null);
    const [result, setResult] = useState<{ key: string; ad: SponsoredPlacement } | null>(null);
    const key = `${projectId}:${placement}`;
    const ad = result?.key === key ? result.ad : null;
    useEffect(() => {
        let active = true;
        setResult(null);
        if (!projectId) return;
        financeClient.getAdSlot(projectId, placement)
            .then((payload: SponsoredPlacement) => { if (active && payload?.enabled) setResult({ key, ad: payload }); })
            .catch(() => { if (active) setResult(null); });
        return () => { active = false; };
    }, [projectId, placement, key]);

    useEffect(() => {
        if (!ad?.campaignId || ad.testCampaign || !containerRef.current || typeof IntersectionObserver === 'undefined') return;
        let timer: ReturnType<typeof setTimeout> | undefined;
        let tracked = false;
        const observer = new IntersectionObserver(entries => {
            const visible = entries.some(entry => entry.isIntersecting && entry.intersectionRatio >= 0.5);
            if (!visible) { clearTimeout(timer); timer = undefined; return; }
            if (timer || tracked) return;
            timer = setTimeout(() => {
                tracked = true;
                financeClient.trackAdImpression(ad.campaignId!, projectId).catch(() => {});
                observer.disconnect();
            }, 1000);
        }, { threshold: [0, 0.5] });
        observer.observe(containerRef.current);
        return () => { clearTimeout(timer); observer.disconnect(); };
    }, [ad, projectId]);

    let clickHref: string | null = null;
    if (ad?.campaignId && ad.clickUrl?.startsWith('/api/v1/finance/ads/click/')) {
        const url = new URL(ad.clickUrl, BACKEND_URL);
        if (url.origin === new URL(BACKEND_URL).origin) clickHref = url.href;
    }
    return { ad, clickHref, containerRef };
}
