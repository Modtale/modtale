import React from 'react';
import {Activity, Github, FileText, Shield, Layers, Box, Database, Palette, Save, Code, Layout} from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { BlueskyBrandIcon, DiscordBrandIcon, XBrandIcon } from '@/components/ui/icons/BrandIcons';
import { SiteRoutes } from '@/utils/routes';

interface FooterProps {
    isDarkMode: boolean;
}

export const Footer: React.FC<FooterProps> = ({ isDarkMode }) => {
    const location = useLocation();
    const path = location.pathname;

    const getFooterDescription = () => {
        switch (path) {
            case SiteRoutes.browse('PLUGIN'):
                return 'The premier community repository for Hytale plugins. Discover, download, and share server plugins, admin tools, gameplay extensions, and supporting libraries.';
            case SiteRoutes.browse('MODPACK'):
                return 'The premier community repository for Hytale modpacks. Discover, download, and share curated Hytale modpacks, collections, and bundled project setups.';
            case SiteRoutes.browse('SAVE'):
                return 'The premier community repository for Hytale worlds. Discover, download, and share Hytale save files, maps, lobbies, schematics, and spawns.';
            case SiteRoutes.browse('ART'):
                return 'The premier community repository for Hytale art assets. Discover, download, and share Hytale models, textures, animations, and creator resources.';
            case SiteRoutes.browse('DATA'):
                return 'The premier community repository for Hytale data assets. Discover, download, and share Hytale configs, loot tables, recipes, and data-driven files.';
            default:
                return 'The premier community repository for Hytale. Discover, download, and share Hytale mods, server plugins, worlds, art assets, data assets, and modpacks.';
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
                            &copy; {new Date().getFullYear()} Modtale.
                        </p>
                    </div>

                    <div className="col-span-1">
                        <div className="text-xs font-bold uppercase text-slate-400 tracking-wider mb-4">Discover</div>
                        <div className="flex flex-col space-y-3">
                            <Link to={SiteRoutes.browse()} className={linkClass}>
                                <Layout className="w-3.5 h-3.5 mr-2 opacity-70" /> Mods
                            </Link>
                            <Link to={SiteRoutes.browse('MODPACK')} className={linkClass}>
                                <Layers className="w-3.5 h-3.5 mr-2 opacity-70" /> Modpacks
                            </Link>
                            <Link to={SiteRoutes.browse('PLUGIN')} className={linkClass}>
                                <Box className="w-3.5 h-3.5 mr-2 opacity-70" /> Plugins
                            </Link>
                            <Link to={SiteRoutes.browse('SAVE')} className={linkClass}>
                                <Save className="w-3.5 h-3.5 mr-2 opacity-70" /> Worlds
                            </Link>
                            <Link to={SiteRoutes.browse('ART')} className={linkClass}>
                                <Palette className="w-3.5 h-3.5 mr-2 opacity-70" /> Art Assets
                            </Link>
                            <Link to={SiteRoutes.browse('DATA')} className={linkClass}>
                                <Database className="w-3.5 h-3.5 mr-2 opacity-70" /> Data Assets
                            </Link>
                        </div>
                    </div>

                    <div className="col-span-1">
                        <div className="text-xs font-bold uppercase text-slate-400 tracking-wider mb-4">Resources</div>
                        <div className="flex flex-col space-y-3">
                            <Link to={SiteRoutes.apiDocs()} className={linkClass}>
                                <Code className="w-4 h-4 mr-2 opacity-70" /> API Docs
                            </Link>
                            <Link to={SiteRoutes.status()} className={linkClass}>
                                <Activity className="w-4 h-4 mr-2 opacity-70" /> Status
                            </Link>
                            <div className="h-px bg-slate-100 dark:bg-white/5 my-1"></div>
                            <Link to={SiteRoutes.terms()} className={linkClass}>
                                <FileText className="w-4 h-4 mr-2 opacity-70" /> Terms of Service
                            </Link>
                            <Link to={SiteRoutes.privacy()} className={linkClass}>
                                <Shield className="w-4 h-4 mr-2 opacity-70" /> Privacy Policy
                            </Link>
                        </div>
                    </div>

                    <div className="col-span-1">
                        <div className="text-xs font-bold uppercase text-slate-400 tracking-wider mb-4">Community</div>
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
                                <Github className="w-4 h-4 mr-2" /> GitHub
                            </a>
                        </div>
                    </div>
                </div>
            </div>
        </footer>
    );
};
