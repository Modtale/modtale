import React, { useEffect, useState, useMemo } from 'react';
import { api } from '../../utils/api';
import type { AffiliateAd } from '../../types';
import { ExternalLink, X } from 'lucide-react';

interface AdUnitProps {
    className?: string;
    variant: 'card' | 'sidebar' | 'banner' | 'sticky-banner';
    trackingSource?: string;
}

export const AdUnit: React.FC<AdUnitProps> = ({ className, variant, trackingSource }) => {
    const [ad, setAd] = useState<AffiliateAd | null>(null);
    const [loading, setLoading] = useState(true);
    const [useExternal, setUseExternal] = useState(false);
    const [isDismissed, setIsDismissed] = useState(false);

    useEffect(() => {
        let mounted = true;
        const fetchAd = async () => {
            try {
                const placement = variant === 'sticky-banner' ? 'banner' : variant;
                const res = await api.get<AffiliateAd>(`/ads/serve?placement=${placement}`);

                if (mounted) {
                    if (res.status === 204) {
                        setUseExternal(true);
                    } else {
                        const chosenCreative = res.data.creatives?.[0];
                        if (chosenCreative) {
                            setAd({
                                ...res.data,
                                imageUrl: chosenCreative.imageUrl
                            });
                        } else {
                            setUseExternal(true);
                        }
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
    }, [variant]);

    const handleClick = () => {
        if (ad) {
            api.post(`/ads/${ad.id}/click`).catch(() => {});
        }
    };

    const finalUrl = useMemo(() => {
        if (!ad || !ad.linkUrl) return '';
        if (!ad.trackingParam || !trackingSource) return ad.linkUrl;

        const cleanSource = encodeURIComponent(trackingSource);
        const separator = ad.linkUrl.includes('?') ? '&' : '?';
        return `${ad.linkUrl}${separator}${ad.trackingParam}=${cleanSource}`;
    }, [ad, trackingSource]);

    if (isDismissed) return null;
    if (loading) return null;

    if (variant === 'sticky-banner') {
        if (!ad || !ad.imageUrl) return null;
        return (
            <div className={`fixed bottom-0 left-0 right-0 z-[100] flex justify-center pointer-events-none animate-in slide-in-from-bottom-full duration-700 ${className}`}>
                <div className="relative pointer-events-auto group">
                    <button
                        onClick={() => setIsDismissed(true)}
                        className="absolute -top-3 -right-3 p-1 bg-white dark:bg-slate-800 text-slate-400 hover:text-white hover:bg-red-500 rounded-full shadow-md border border-slate-200 dark:border-white/10 transition-all z-20 opacity-0 group-hover:opacity-100"
                        title="Dismiss"
                    >
                        <X className="w-3 h-3" />
                    </button>

                    <a href={finalUrl} target="_blank" rel="nofollow noreferrer" onClick={handleClick} className="block relative overflow-hidden rounded-t-xl shadow-[0_-4px_20px_-5px_rgba(0,0,0,0.3)]">
                        <img
                            src={ad.imageUrl}
                            alt={ad.title}
                            className="h-auto max-h-[120px] w-auto max-w-full object-contain bg-slate-900"
                        />

                        <div className="absolute inset-0 bg-black/0 group-hover:bg-black/40 transition-colors duration-300 flex items-center justify-center">
                            <span className="text-white font-bold text-sm flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity duration-300 drop-shadow-md">
                                Visit Site <ExternalLink className="w-3 h-3" />
                            </span>
                        </div>

                        <div className="absolute bottom-1 right-1 px-1 py-0.5 bg-black/40 rounded text-[8px] font-bold text-white/50 uppercase">
                            Ad
                        </div>
                    </a>
                </div>
            </div>
        );
    }

    if (useExternal) {
        return (
            <div className={`bg-slate-100 dark:bg-white/5 border border-slate-200 dark:border-white/5 flex items-center justify-center text-xs text-slate-400 uppercase tracking-widest rounded-xl ${variant === 'card' ? 'h-full min-h-[154px]' : ''} ${variant === 'sidebar' ? 'min-h-[200px]' : ''} ${className}`}>
                <span>Advertisement</span>
            </div>
        );
    }

    if (!ad || !ad.imageUrl) return null;

    if (variant === 'banner') {
        return (
            <a href={finalUrl} target="_blank" rel="nofollow noreferrer" onClick={handleClick} className={`block w-full rounded-xl overflow-hidden group relative ${className}`}>
                <img src={ad.imageUrl} alt={ad.title} className="w-full h-auto object-contain bg-slate-950/20" />
                <div className="absolute top-2 right-2 px-1.5 py-0.5 bg-black/60 backdrop-blur rounded text-[10px] font-bold text-white/80 uppercase">Ad</div>
            </a>
        );
    }

    if (variant === 'card') {
        return (
            <a href={finalUrl} target="_blank" rel="nofollow noreferrer" onClick={handleClick} className={`group relative block h-full min-h-[154px] bg-slate-100 dark:bg-white/5 rounded-xl border border-slate-200 dark:border-white/5 overflow-hidden transition-all hover:ring-2 hover:ring-modtale-accent hover:ring-offset-2 hover:ring-offset-slate-50 dark:hover:ring-offset-slate-900 ${className}`}>
                <img src={ad.imageUrl} alt={ad.title} className="absolute inset-0 w-full h-full object-cover transition-transform duration-700 group-hover:scale-105" />

                <div className="absolute top-2 right-2 px-1.5 py-0.5 bg-black/40 backdrop-blur-md rounded text-[10px] font-bold text-white/90 uppercase tracking-wider border border-white/10">
                    Ad
                </div>

                <div className="absolute inset-0 bg-black/0 group-hover:bg-black/40 transition-colors duration-300 flex items-center justify-center">
                    <span className="text-white font-bold text-sm flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity duration-300 drop-shadow-md">
                        Visit Site <ExternalLink className="w-3 h-3" />
                    </span>
                </div>
            </a>
        );
    }

    return (
        <a href={finalUrl} target="_blank" rel="nofollow noreferrer" onClick={handleClick} className={`block rounded-xl overflow-hidden group relative border border-slate-200 dark:border-white/5 bg-white dark:bg-slate-900/50 hover:shadow-md transition-all ${className}`}>
            <div className="relative w-full">
                <img src={ad.imageUrl} alt={ad.title} className="w-full h-auto object-contain block" />
                <div className="absolute top-2 right-2 px-1.5 py-0.5 bg-black/60 backdrop-blur rounded text-[10px] font-bold text-white/80 uppercase">Ad</div>

                <div className="absolute inset-0 bg-black/0 group-hover:bg-black/40 transition-colors duration-300 flex items-center justify-center">
                    <span className="text-white font-bold text-xs flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity duration-300 drop-shadow-md">
                        Visit <ExternalLink className="w-3 h-3" />
                    </span>
                </div>
            </div>
            <div className="p-3 border-t border-slate-100 dark:border-white/5 flex justify-between items-center">
                <h4 className="text-sm font-bold text-slate-900 dark:text-white group-hover:text-modtale-accent truncate max-w-[80%]">{ad.title}</h4>
                <ExternalLink className="w-3 h-3 text-slate-400 group-hover:text-modtale-accent" />
            </div>
        </a>
    );
};