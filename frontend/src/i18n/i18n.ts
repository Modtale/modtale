import { createInstance, type i18n } from 'i18next';
import { initReactI18next } from 'react-i18next';
import { DEFAULT_LOCALE, supportedLocales, type SupportedLocale } from './config';
import { defaultResources, loadLocale } from './resources';

export const createAppI18n = (locale: SupportedLocale = DEFAULT_LOCALE): i18n => {
    const instance = createInstance();
    void instance.use(initReactI18next).init({
        lng: locale,
        fallbackLng: DEFAULT_LOCALE,
        supportedLngs: supportedLocales,
        resources: defaultResources,
        defaultNS: 'common',
        fallbackNS: 'common',
        load: 'languageOnly',
        cleanCode: true,
        nonExplicitSupportedLngs: true,
        returnNull: false,
        interpolation: { escapeValue: false },
        react: { useSuspense: false },
        showSupportNotice: false,
        initImmediate: false
    });
    return instance;
};

export const ensureLocaleLoaded = async (instance: i18n, locale: SupportedLocale) => {
    if (!instance.hasResourceBundle(locale, 'common')) {
        const resources = await loadLocale(locale);
        Object.entries(resources).forEach(([namespace, messages]) => {
            instance.addResourceBundle(locale, namespace, messages, true, true);
        });
    }
    await instance.changeLanguage(locale);
};
