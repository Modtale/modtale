import React, { useEffect, useState, useRef } from 'react';
import { api } from '../../utils/api';
import type { AffiliateAd } from '../../types';
import { ExternalLink } from 'lucide-react';

interface AdUnitProps {
    className?: string;
    variant: 'card' | 'sidebar';
}

export const AdUnit: React.FC<AdUnitProps> = ({ className, variant }) => {
    const [ad, setAd] = useState<AffiliateAd | null>(null);
    const [loading, setLoading] = useState(true);
    const [useExternal, setUseExternal] = useState(false);
    const adRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        let mounted = true;

        const fetchAd = async () => {
            try {
                const res = await api.get<AffiliateAd>('/ads/serve');
                if (mounted) {
                    if (res.status === 204) {
                        setUseExternal(true);
                    } else {
                        setAd(res.data);
                    }
                    setLoading(false);
                }
            } catch (error) {
                if (mounted) {
                    setUseExternal(true);
                    setLoading(false);
                }
            }
        };

        fetchAd();
        return () => { mounted = false; };
    }, []);

    const handleClick = () => {
        if (ad) {
            api.post(`/ads/${ad.id}/click`).catch(() => {});
        }
    };

    if (loading) return null;

    if (useExternal) {
        return (
            <div className={`bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 flex items-center justify-center text-xs text-slate-400 uppercase tracking-widest ${className}`}>
                <div id={`ad-slot-${Math.random().toString(36).substr(2, 9)}`} className="w-full h-full flex items-center justify-center">
                    <span>Advertisement</span>
                </div>
            </div>
        );
    }

    if (!ad) return null;

    if (variant === 'card') {
        return (
            <a
                href={ad.linkUrl}
                target="_blank"
                rel="nofollow noreferrer"
                onClick={handleClick}
                className={`group relative block h-[154px] bg-slate-900 rounded-xl border border-slate-200 dark:border-white/5 overflow-hidden hover:shadow-lg transition-all ${className}`}
            >
                <img src={ad.imageUrl} alt={ad.title} className="absolute inset-0 w-full h-full object-cover opacity-80 group-hover:opacity-100 transition-opacity" />
                <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/20 to-transparent p-4 flex flex-col justify-end">
                    <span className="text-[10px] font-bold text-white/60 uppercase tracking-widest mb-1 flex items-center gap-1">
                        Sponsored <ExternalLink className="w-3 h-3" />
                    </span>
                    <h3 className="text-white font-bold truncate group-hover:underline">{ad.title}</h3>
                </div>
            </a>
        );
    }

    return (
        <a
            href={ad.linkUrl}
            target="_blank"
            rel="nofollow noreferrer"
            onClick={handleClick}
            className={`block rounded-xl overflow-hidden group relative border border-slate-200 dark:border-white/5 ${className}`}
        >
            <div className="aspect-video relative">
                <img src={ad.imageUrl} alt={ad.title} className="w-full h-full object-cover" />
                <div className="absolute top-2 right-2 px-1.5 py-0.5 bg-black/60 backdrop-blur rounded text-[10px] font-bold text-white/80 uppercase">Ad</div>
            </div>
            <div className="p-3 bg-white dark:bg-slate-900/50">
                <h4 className="text-sm font-bold text-slate-900 dark:text-white group-hover:text-modtale-accent truncate">{ad.title}</h4>
                <p className="text-xs text-slate-500 mt-1 flex items-center gap-1">Visit Site <ExternalLink className="w-3 h-3" /></p>
            </div>
        </a>
    );
};