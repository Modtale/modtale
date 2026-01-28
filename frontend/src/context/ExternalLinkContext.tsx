import React, { createContext, useContext, useState, useEffect } from 'react';
import { AlertTriangle, ArrowRight } from 'lucide-react';

interface ExternalLinkContextType {
    openExternalLink: (url: string) => void;
}

const ExternalLinkContext = createContext<ExternalLinkContextType | undefined>(undefined);

export const useExternalLink = () => {
    const context = useContext(ExternalLinkContext);
    if (!context) throw new Error("useExternalLink must be used within an ExternalLinkProvider");
    return context;
};

export const ExternalLinkProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [targetUrl, setTargetUrl] = useState<string | null>(null);
    const [dontAskAgain, setDontAskAgain] = useState(false);

    const normalizeUrl = (url: string) => {
        if (!url) return '';
        if (url.startsWith('http://') || url.startsWith('https://')) return url;
        return `https://${url}`;
    };

    const cleanUrl = (url: string) => url.replace(/\/$/, '').toLowerCase();

    const openExternalLink = (url: string) => {
        const fullUrl = normalizeUrl(url);
        const skipWarning = localStorage.getItem('modtale_skip_external_warning') === 'true';

        if (skipWarning) {
            window.open(fullUrl, '_blank', 'noopener,noreferrer');
        } else {
            setTargetUrl(fullUrl);
        }
    };

    const confirmNavigation = () => {
        if (!targetUrl) return;

        if (dontAskAgain) {
            localStorage.setItem('modtale_skip_external_warning', 'true');
        }

        window.open(targetUrl, '_blank', 'noopener,noreferrer');
        setTargetUrl(null);
    };

    useEffect(() => {
        const handleGlobalClick = (e: MouseEvent) => {
            const anchor = (e.target as HTMLElement).closest('a');

            if (!anchor || !anchor.href || anchor.href.startsWith('javascript:')) return;

            if (!anchor.href.startsWith('http')) return;

            if (anchor.getAttribute('rel')?.includes('nofollow')) {
                return;
            }

            try {
                const url = new URL(anchor.href);
                const currentHost = window.location.hostname;
                const targetHost = url.hostname;

                if (targetHost === currentHost || targetHost.endsWith('modtale.net')) {
                    return;
                }

                const trustedUrls = [
                    'https://discord.gg/pcfadvyqve',
                    'https://x.com/modtalenet',
                    'https://bsky.app/profile/modtale.net',
                    'https://github.com/modtale/modtale',
                    'https://github.com/modtale/modtale-example'
                ];

                const targetClean = cleanUrl(anchor.href);
                const isWhitelisted = trustedUrls.some(trusted => cleanUrl(trusted) === targetClean);

                if (isWhitelisted) {
                    return;
                }

                e.preventDefault();
                e.stopPropagation();
                openExternalLink(anchor.href);

            } catch (err) {
            }
        };

        document.addEventListener('click', handleGlobalClick, true);
        return () => document.removeEventListener('click', handleGlobalClick, true);
    }, []);

    return (
        <ExternalLinkContext.Provider value={{ openExternalLink }}>
            {children}

            {targetUrl && (
                <div className="fixed inset-0 z-[9999] flex items-center justify-center p-4 bg-slate-900/80 backdrop-blur-sm animate-in fade-in duration-200">
                    <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/10 rounded-2xl shadow-2xl max-w-lg w-full overflow-hidden scale-100 animate-in zoom-in-95 duration-200">

                        <div className="bg-amber-500/10 border-b border-amber-500/20 p-6 flex items-start gap-4">
                            <div className="p-3 bg-amber-500/20 rounded-xl text-amber-600 dark:text-amber-500">
                                <AlertTriangle className="w-8 h-8" />
                            </div>
                            <div>
                                <h2 className="text-xl font-black text-slate-900 dark:text-white uppercase tracking-tight">Leaving Modtale</h2>
                                <p className="text-slate-600 dark:text-slate-400 text-sm mt-1 leading-relaxed">
                                    You are about to visit an external website. We cannot verify the safety of external content.
                                </p>
                            </div>
                        </div>

                        <div className="p-6">
                            <div className="bg-slate-100 dark:bg-black/30 p-4 rounded-xl border border-slate-200 dark:border-white/5 break-all font-mono text-sm text-modtale-accent">
                                {targetUrl}
                            </div>

                            <div className="mt-6 flex items-center gap-3">
                                <input
                                    type="checkbox"
                                    id="dont-ask"
                                    checked={dontAskAgain}
                                    onChange={(e) => setDontAskAgain(e.target.checked)}
                                    className="w-5 h-5 rounded border-slate-300 text-modtale-accent focus:ring-modtale-accent"
                                />
                                <label htmlFor="dont-ask" className="text-sm font-bold text-slate-600 dark:text-slate-300 select-none cursor-pointer">
                                    Don't show this warning again
                                </label>
                            </div>
                        </div>

                        <div className="p-4 bg-slate-50 dark:bg-white/5 border-t border-slate-200 dark:border-white/5 flex gap-3 justify-end">
                            <button
                                onClick={() => setTargetUrl(null)}
                                className="px-5 py-2.5 rounded-xl font-bold text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-white/10 transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={confirmNavigation}
                                className="px-6 py-2.5 rounded-xl font-bold bg-modtale-accent hover:bg-modtale-accentHover text-white shadow-lg shadow-modtale-accent/20 flex items-center gap-2 transition-transform active:scale-95"
                            >
                                Continue <ArrowRight className="w-4 h-4" />
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </ExternalLinkContext.Provider>
    );
};