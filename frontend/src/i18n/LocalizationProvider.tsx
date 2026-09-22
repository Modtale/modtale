import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { I18nextProvider } from 'react-i18next';
import {
    DEFAULT_LOCALE,
    LOCALE_STORAGE_KEY,
    detectPreferredLocale,
    localeMetadata,
    type SupportedLocale
} from './config';
import { createAppI18n, ensureLocaleLoaded } from './i18n';

type LocalizationContextValue = {
    locale: SupportedLocale;
    setLocale: (locale: SupportedLocale) => Promise<void>;
    formatNumber: (value: number, options?: Intl.NumberFormatOptions) => string;
    formatDate: (value: Date | number | string, options?: Intl.DateTimeFormatOptions) => string;
    formatList: (values: string[], options?: Intl.ListFormatOptions) => string;
};

const LocalizationContext = createContext<LocalizationContextValue | null>(null);

export const LocalizationProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
    const [instance] = useState(() => createAppI18n(DEFAULT_LOCALE));
    const [locale, setLocaleState] = useState<SupportedLocale>(DEFAULT_LOCALE);

    const setLocale = useCallback(async (nextLocale: SupportedLocale) => {
        await ensureLocaleLoaded(instance, nextLocale);
        setLocaleState(nextLocale);
        if (typeof document !== 'undefined') {
            document.documentElement.lang = nextLocale;
            document.documentElement.dir = localeMetadata[nextLocale].direction;
        }
        try {
            window.localStorage.setItem(LOCALE_STORAGE_KEY, nextLocale);
        } catch {
            // A locale still applies for this session when persistence is unavailable.
        }
    }, [instance]);

    useEffect(() => {
        void setLocale(detectPreferredLocale());
    }, [setLocale]);

    const value = useMemo<LocalizationContextValue>(() => ({
        locale,
        setLocale,
        formatNumber: (number, options) => new Intl.NumberFormat(locale, options).format(number),
        formatDate: (date, options) => new Intl.DateTimeFormat(locale, options).format(new Date(date)),
        formatList: (values, options) => new Intl.ListFormat(locale, options).format(values)
    }), [locale, setLocale]);

    return (
        <I18nextProvider i18n={instance}>
            <LocalizationContext.Provider value={value}>{children}</LocalizationContext.Provider>
        </I18nextProvider>
    );
};

export const useLocalization = () => {
    const context = useContext(LocalizationContext);
    if (!context) throw new Error('useLocalization must be used within LocalizationProvider');
    return context;
};
