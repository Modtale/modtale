import React from 'react';
import {Activity, FileText, Shield, Layers, Box, Database, Palette, Save, Code, Layout} from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { BlueskyBrandIcon, DiscordBrandIcon, GitHubBrandIcon, XBrandIcon } from '@/components/ui/icons/BrandIcons';
import { SiteRoutes } from '@/utils/routes';
import { STATUS_PAGE_URL } from '@/utils/status';
import { LanguageSelector } from '@/i18n';
import { useTranslation } from 'react-i18next';

interface FooterProps {
    isDarkMode: boolean;
}

export const Footer: React.FC<FooterProps> = ({ isDarkMode }) => {
    const { t } = useTranslation(['footer', 'navigation']);
    const location = useLocation();
    const path = location.pathname;

    const getFooterDescription = () => {
        switch (path) {
            case SiteRoutes.browse('PLUGIN'):
                return t('footer:description.plugins');
            case SiteRoutes.browse('MODPACK'):
                return t('footer:description.modpacks');
            case SiteRoutes.browse('SAVE'):
                return t('footer:description.worlds');
            case SiteRoutes.browse('ART'):
                return t('footer:description.art');
            case SiteRoutes.browse('DATA'):
                return t('footer:description.data');
            default:
                return t('footer:description.default');
        }
    };

    const linkClass = "flex items-center text-sm font-medium text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition-colors";

    return (
        <footer className="bg-slate-50 dark:bg-slate-900 border-t border-slate-200 dark:border-white/5 py-12 mt-auto relative z-50">
            <div className="max-w-[112rem] px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28 mx-auto">
                <div className="grid grid-cols-2 md:grid-cols-4 gap-8 mb-8">

                    <div className="col-span-2 md:col-span-1">
                        <p className="text-sm text-slate-500 dark:text-slate-400 leading-relaxed max-w-xs">
                            {getFooterDescription()}
                        </p>
                        <p className="text-xs text-slate-400 mt-4 font-mono">
                            {t('footer:copyright', { year: String(new Date().getFullYear()) })}
                        </p>
                        <LanguageSelector className="mt-4 text-slate-500 dark:text-slate-400" />
                    </div>

                    <div className="col-span-1">
                        <div className="text-xs font-bold uppercase text-slate-400 tracking-wider mb-4">{t('footer:discover')}</div>
                        <div className="flex flex-col space-y-3">
                            <Link to={SiteRoutes.browse()} className={linkClass}>
                                <Layout className="w-3.5 h-3.5 mr-2 opacity-70" /> {t('navigation:mods')}
                            </Link>
                            <Link to={SiteRoutes.browse('MODPACK')} className={linkClass}>
                                <Layers className="w-3.5 h-3.5 mr-2 opacity-70" /> {t('navigation:modpacks')}
                            </Link>
                            <Link to={SiteRoutes.browse('PLUGIN')} className={linkClass}>
                                <Box className="w-3.5 h-3.5 mr-2 opacity-70" /> {t('navigation:plugins')}
                            </Link>
                            <Link to={SiteRoutes.browse('SAVE')} className={linkClass}>
                                <Save className="w-3.5 h-3.5 mr-2 opacity-70" /> {t('navigation:worlds')}
                            </Link>
                            <Link to={SiteRoutes.browse('ART')} className={linkClass}>
                                <Palette className="w-3.5 h-3.5 mr-2 opacity-70" /> {t('navigation:artAssets')}
                            </Link>
                            <Link to={SiteRoutes.browse('DATA')} className={linkClass}>
                                <Database className="w-3.5 h-3.5 mr-2 opacity-70" /> {t('navigation:dataAssets')}
                            </Link>
                        </div>
                    </div>

                    <div className="col-span-1">
                        <div className="text-xs font-bold uppercase text-slate-400 tracking-wider mb-4">{t('footer:resources')}</div>
                        <div className="flex flex-col space-y-3">
                            <Link to={SiteRoutes.apiDocs()} className={linkClass}>
                                <Code className="w-4 h-4 mr-2 opacity-70" /> {t('footer:apiDocs')}
                            </Link>
                            <a href={STATUS_PAGE_URL} className={linkClass}>
                                <Activity className="w-4 h-4 mr-2 opacity-70" /> {t('footer:status')}
                            </a>
                            <div className="h-px bg-slate-100 dark:bg-white/5 my-1"></div>
                            <Link to={SiteRoutes.terms()} className={linkClass}>
                                <FileText className="w-4 h-4 mr-2 opacity-70" /> {t('footer:terms')}
                            </Link>
                            <Link to={SiteRoutes.privacy()} className={linkClass}>
                                <Shield className="w-4 h-4 mr-2 opacity-70" /> {t('footer:privacy')}
                            </Link>
                        </div>
                    </div>

                    <div className="col-span-1">
                        <div className="text-xs font-bold uppercase text-slate-400 tracking-wider mb-4">{t('footer:community')}</div>
                        <div className="flex flex-col space-y-3">
                            <a href="https://discord.gg/PcFaDVYqVe" target="_blank" rel="noopener noreferrer" className={linkClass}>
                                <DiscordBrandIcon className="w-4 h-4 mr-2" /> Discord
                            </a>
                            <a href="https://x.com/modtalenet" target="_blank" rel="noopener noreferrer" className={linkClass}>
                                <XBrandIcon className="w-3.5 h-3.5 mr-2.5" /> X (Twitter)
                            </a>
                            <a href="https://bsky.app/profile/modtale.net" target="_blank" rel="noopener noreferrer" className={linkClass}>
                                <BlueskyBrandIcon className="w-4 h-4 mr-2" /> Bluesky
                            </a>
                            <a href="https://github.com/Modtale/modtale" target="_blank" rel="noopener noreferrer" className={linkClass}>
                                <GitHubBrandIcon className="w-4 h-4 mr-2" /> GitHub
                            </a>
                        </div>
                    </div>
                </div>
            </div>
        </footer>
    );
};
