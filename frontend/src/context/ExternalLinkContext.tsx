import React, { createContext, useContext, useState, useEffect } from 'react';
import { AlertTriangle, ArrowRight, ShieldAlert, X } from 'lucide-react';
import { theme } from '@/styles/theme';

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
                <div className={`${theme.components.modalOverlay} z-[9999]`}>
                    <div className={`relative w-full max-w-xl overflow-hidden rounded-[28px] border border-amber-300/30 bg-white/95 shadow-[0_40px_120px_-40px_rgba(15,23,42,0.65)] backdrop-blur-xl dark:border-amber-500/20 dark:bg-slate-950/95 animate-in zoom-in-95 duration-200`}>
                        <button
                            type="button"
                            onClick={() => setTargetUrl(null)}
                            className={`absolute right-4 top-4 z-10 ${theme.components.iconButton}`}
                            aria-label="Close warning"
                        >
                            <X className="w-5 h-5" />
                        </button>

                        <div className={`relative overflow-hidden border-b border-amber-300/20 px-6 py-6 sm:px-7 ${theme.components.warningPanel}`}>
                            <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-amber-400/60 to-transparent" />
                            <div className="flex items-start gap-4">
                                <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-amber-500/15 text-amber-600 shadow-inner shadow-amber-500/10 dark:text-amber-400">
                                    <ShieldAlert className="w-7 h-7" />
                                </div>
                                <div className="pr-8">
                                    <div className="mb-2 flex items-center gap-2">
                                        <span className="rounded-full border border-amber-400/30 bg-amber-500/15 px-2.5 py-1 text-[10px] font-black uppercase tracking-[0.22em] text-amber-700 dark:text-amber-300">
                                            External Link
                                        </span>
                                    </div>
                                    <h2 className="text-2xl font-black tracking-tight text-slate-900 dark:text-white">Leaving Modtale</h2>
                                    <p className="mt-2 max-w-md text-sm leading-relaxed text-slate-600 dark:text-slate-300">
                                        You are about to open a site outside Modtale. Double-check the destination before continuing.
                                    </p>
                                </div>
                            </div>
                        </div>

                        <div className="space-y-5 p-6 sm:p-7">
                            <div className="rounded-2xl border border-slate-200/80 bg-slate-50/80 p-4 text-sm font-medium text-slate-600 shadow-inner shadow-slate-900/[0.02] dark:border-white/10 dark:bg-black/30 dark:text-slate-300">
                                <div className="mb-2 flex items-center gap-2 text-[11px] font-black uppercase tracking-[0.2em] text-slate-400 dark:text-slate-500">
                                    <AlertTriangle className="w-3.5 h-3.5" />
                                    Destination
                                </div>
                                <div className="break-all font-mono text-[13px] text-modtale-accent">
                                    {targetUrl}
                                </div>
                            </div>

                            <label htmlFor="dont-ask" className="flex cursor-pointer items-center gap-3 rounded-2xl border border-slate-200/80 bg-slate-50/70 px-4 py-3 transition-colors hover:border-modtale-accent/30 hover:bg-slate-100/80 dark:border-white/10 dark:bg-white/5 dark:hover:bg-white/10">
                                <input
                                    type="checkbox"
                                    id="dont-ask"
                                    checked={dontAskAgain}
                                    onChange={(e) => setDontAskAgain(e.target.checked)}
                                    className="themed-checkbox shrink-0"
                                />
                                <span className="text-sm font-bold text-slate-700 dark:text-slate-200">
                                    Don&apos;t show this warning again
                                </span>
                            </label>
                        </div>

                        <div className="flex flex-col-reverse gap-3 border-t border-slate-200/80 bg-slate-50/80 p-4 sm:flex-row sm:items-center sm:justify-end dark:border-white/10 dark:bg-white/5 sm:px-7 sm:py-5">
                            <button
                                onClick={() => setTargetUrl(null)}
                                className={`${theme.components.buttonGhost} w-full justify-center sm:w-auto`}
                            >
                                Stay Here
                            </button>
                            <button
                                onClick={confirmNavigation}
                                className={`${theme.components.buttonPrimary} w-full px-6 sm:w-auto`}
                            >
                                Continue Anyway <ArrowRight className="w-4 h-4" />
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </ExternalLinkContext.Provider>
    );
};
