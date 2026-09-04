import { beforeEach, describe, expect, it } from 'vitest';
import {
    DEFAULT_LOCALE,
    LOCALE_STORAGE_KEY,
    detectPreferredLocale,
    normalizeLocale,
    supportedLocales
} from '@/i18n/config';
import { createAppI18n } from '@/i18n/i18n';

describe('localization configuration', () => {
    beforeEach(() => {
        window.localStorage.clear();
    });

    it('normalizes regional and underscore locale identifiers', () => {
        expect(normalizeLocale('en-US')).toBe('en');
        expect(normalizeLocale('EN_us')).toBe('en');
        expect(normalizeLocale('not-supported')).toBeNull();
    });

    it('prefers a persisted supported locale and safely falls back', () => {
        window.localStorage.setItem(LOCALE_STORAGE_KEY, 'en-US');
        expect(detectPreferredLocale()).toBe('en');
        window.localStorage.setItem(LOCALE_STORAGE_KEY, 'xx');
        expect(detectPreferredLocale()).toBe(DEFAULT_LOCALE);
    });

    it('provides namespaced translations and interpolation synchronously for SSR', () => {
        const instance = createAppI18n();
        expect(instance.isInitialized).toBe(true);
        expect(instance.t('navigation:allProjects')).toBe('All Projects');
        expect(instance.t('footer:copyright', { year: '2026' })).toBe('© 2026 Modtale.');
        expect(instance.options.supportedLngs).toEqual(expect.arrayContaining(supportedLocales));
    });
});
