import React, { createContext, useContext, useState, useEffect } from 'react';
import { AlertTriangle, ArrowRight, ShieldAlert, X } from 'lucide-react';
import { useScrollLock } from '@/hooks/useScrollLock';
import { theme } from '@/styles/theme';
import { ModalPortal } from '@/components/ui/ModalPortal';

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

    useScrollLock(Boolean(targetUrl));

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
                <ModalPortal>
                <div className={`${theme.components.modalOverlay} z-[9999]`}>
                    <div className={`${theme.components.modalContent} relative w-full max-w-2xl animate-in zoom-in-95 duration-200`}>
                        <div className={theme.components.modalHeader}>
                            <div className="flex items-start gap-4 pr-4">
                                <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-amber-500/10 text-amber-600 dark:bg-amber-500/15 dark:text-amber-400">
                                    <ShieldAlert className="w-6 h-6" />
                                </div>
                                <div>
                                    <div className="mb-2 flex items-center gap-2">
                                        <span className={`${theme.components.badge} border-amber-300/60 bg-amber-500/10 text-amber-700 dark:border-amber-500/30 dark:bg-amber-500/15 dark:text-amber-300`}>
                                            External Link
                                        </span>
                                    </div>
                                    <h2 className={`text-xl font-black ${theme.colors.textPrimary}`}>Leaving Modtale</h2>
                                    <p className={`mt-1 text-sm ${theme.colors.textSecondary}`}>
                                        You are about to open a site outside Modtale. Double-check the destination before continuing.
                                    </p>
                                </div>
                            </div>
                            <button
                                type="button"
                                onClick={() => setTargetUrl(null)}
                                className={theme.components.iconButton}
                                aria-label="Close warning"
                            >
                                <X className="w-5 h-5" />
                            </button>
                        </div>

                        <div className={`${theme.components.modalBody} space-y-5`}>
                            <div className={`rounded-2xl border ${theme.colors.border} ${theme.colors.bgSurface} p-4 text-sm font-medium ${theme.colors.textSecondary}`}>
                                <div className={`mb-2 flex items-center gap-2 text-[11px] font-black uppercase tracking-[0.2em] ${theme.colors.textMuted}`}>
                                    <AlertTriangle className="w-3.5 h-3.5" />
                                    Destination
                                </div>
                                <div className="break-all font-mono text-[13px] text-modtale-accent">
                                    {targetUrl}
                                </div>
                            </div>

                            <label htmlFor="dont-ask" className={`flex cursor-pointer items-center gap-3 rounded-2xl border ${theme.colors.border} ${theme.colors.bgSurface} px-4 py-3 transition-colors hover:border-modtale-accent/30 hover:bg-slate-100 dark:hover:bg-white/10`}>
                                <input
                                    type="checkbox"
                                    id="dont-ask"
                                    checked={dontAskAgain}
                                    onChange={(e) => setDontAskAgain(e.target.checked)}
                                    className="themed-checkbox shrink-0"
                                />
                                <span className={`text-sm font-bold ${theme.colors.textPrimary}`}>
                                    Don&apos;t show this warning again
                                </span>
                            </label>
                        </div>

                        <div className={`${theme.components.modalFooter} flex-col-reverse gap-3 sm:flex-row sm:justify-end`}>
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
                </ModalPortal>
            )}
        </ExternalLinkContext.Provider>
    );
};
