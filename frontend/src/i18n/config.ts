export const DEFAULT_LOCALE = 'en' as const;
export const LOCALE_STORAGE_KEY = 'modtale-locale';

export const localeMetadata = {
    en: { nativeName: 'English', direction: 'ltr' }
} as const;

export type SupportedLocale = keyof typeof localeMetadata;
export type TextDirection = (typeof localeMetadata)[SupportedLocale]['direction'];

export const supportedLocales = Object.keys(localeMetadata) as SupportedLocale[];

export const normalizeLocale = (candidate?: string | null): SupportedLocale | null => {
    if (!candidate) return null;
    const normalized = candidate.trim().replace('_', '-').toLowerCase();
    if (!normalized) return null;
    const exact = supportedLocales.find(locale => locale.toLowerCase() === normalized);
    if (exact) return exact;
    const base = normalized.split('-')[0];
    return supportedLocales.find(locale => locale.toLowerCase() === base) ?? null;
};

export const detectPreferredLocale = (): SupportedLocale => {
    if (typeof window === 'undefined') return DEFAULT_LOCALE;

    try {
        const stored = normalizeLocale(window.localStorage.getItem(LOCALE_STORAGE_KEY));
        if (stored) return stored;
    } catch {
        // Storage can be unavailable in privacy-restricted browsers.
    }

    const candidates = [
        ...(window.navigator.languages ?? []),
        window.navigator.language,
        window.document.documentElement.lang
    ];
    for (const candidate of candidates) {
        const locale = normalizeLocale(candidate);
        if (locale) return locale;
    }
    return DEFAULT_LOCALE;
};
