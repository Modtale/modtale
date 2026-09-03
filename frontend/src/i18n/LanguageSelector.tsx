import React from 'react';
import { useTranslation } from 'react-i18next';
import { localeMetadata, supportedLocales, type SupportedLocale } from './config';
import { useLocalization } from './LocalizationProvider';

export const LanguageSelector: React.FC<{ className?: string }> = ({ className = '' }) => {
    const { t } = useTranslation('common');
    const { locale, setLocale } = useLocalization();

    if (supportedLocales.length < 2) return null;

    return (
        <label className={`inline-flex items-center gap-2 text-sm ${className}`}>
            <span>{t('language.label')}</span>
            <select
                value={locale}
                onChange={event => void setLocale(event.target.value as SupportedLocale)}
                aria-label={t('language.label')}
                className="rounded-lg border border-slate-200 bg-white px-2 py-1 text-slate-700 dark:border-white/10 dark:bg-slate-900 dark:text-slate-200"
            >
                {supportedLocales.map(value => (
                    <option key={value} value={value}>{localeMetadata[value].nativeName}</option>
                ))}
            </select>
        </label>
    );
};
