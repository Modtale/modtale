import type { Resource } from 'i18next';
import type { SupportedLocale } from './config';
import en from './locales/en';

export const defaultResources: Resource = { en };

const localeLoaders: Record<SupportedLocale, () => Promise<{ default: typeof en }>> = {
    en: () => Promise.resolve({ default: en })
};

export const loadLocale = async (locale: SupportedLocale) => (await localeLoaders[locale]()).default;
