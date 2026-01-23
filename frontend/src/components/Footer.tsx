import React from 'react';
import { Github, FileText, Shield, Layers, Box, Database, Palette, Save, Code, Activity } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';

const DiscordIcon = ({ className }: { className?: string }) => (
    <svg className={className} viewBox="0 0 127.14 96.36" fill="currentColor">
        <path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83A72.37,72.37,0,0,0,45.64,0A105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36A77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19a77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c2.36-24.44-4.2-48.62-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z"/>
    </svg>
);

const XIcon = ({ className }: { className?: string }) => (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor">
        <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" />
    </svg>
);

const BlueskyIcon = ({ className }: { className?: string }) => (
    <svg className={className} viewBox="0 -3.268 64 68.414" fill="currentColor">
        <path d="M13.873 3.805C21.21 9.332 29.103 20.537 32 26.55v15.882c0-.338-.13.044-.41.867-1.512 4.456-7.418 21.847-20.923 7.944-7.111-7.32-3.819-14.64 9.125-16.85-7.405 1.264-15.73-.825-18.014-9.015C1.12 23.022 0 8.51 0 6.55 0-3.268 8.579-.182 13.873 3.805zm36.254 0C42.79 9.332 34.897 20.537 32 26.55v15.882c0-.338.13.044.41.867 1.512 4.456 7.418 21.847 20.923 7.944 7.111-7.32 3.819-14.64-9.125-16.85 7.405 1.264 15.73-.825 18.014-9.015C62.88 23.022 64 8.51 64 6.55c0-9.818-8.578-6.732-13.873-2.745z"/>
    </svg>
);

interface FooterProps {
    isDarkMode: boolean;
}

export const Footer: React.FC<FooterProps> = ({ isDarkMode }) => {
    const location = useLocation();
    const path = location.pathname;

    const getFooterDescription = () => {
        switch (path) {
            case '/plugins':
                return "The premier community repository for Hytale server plugins. Discover, download, and share Hytale plugins, admin tools, gameplay scripts, and libraries.";
            case '/modpacks':
                return "The premier community repository for Hytale modpacks. Discover, download, and share curated Hytale modpacks, collections, and tech packs.";
            case '/worlds':
                return "The premier community repository for Hytale worlds. Discover, download, and share Hytale maps, lobbies, schematics, and spawns.";
            case '/art':
                return "The premier community repository for Hytale art assets. Discover, download, and share Hytale models, textures, animations, and resources.";
            case '/data':
                return "The premier community repository for Hytale data assets. Discover, download, and share Hytale configs, loot tables, and data packs.";
            default:
                return "The premier community repository for Hytale. Discover, download, and share Hytale mods, worlds, server plugins, art + data assets, and modpacks.";
        }
    };

    const linkClass = "flex items-center text-sm font-medium text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition-colors";

    const homeLikePages = ['/', '/plugins', '/modpacks', '/worlds', '/art', 'data'];
    const isHomeLayout = homeLikePages.includes(path);

    const containerClasses = isHomeLayout
        ? "max-w-7xl min-[1800px]:max-w-[112rem] px-4 sm:px-6 lg:px-8"
        : "max-w-[112rem] px-4 sm:px-12 md:px-16 lg:px-28";

    return (
        <footer className="bg-slate-50 dark:bg-slate-900 border-t border-slate-200 dark:border-white/5 py-12 mt-auto relative z-50">
            <div className={`${containerClasses} mx-auto`}>
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
                        <h4 className="text-xs font-bold uppercase text-slate-400 tracking-wider mb-4">Discover</h4>
                        <div className="flex flex-col space-y-3">
                            <Link to="/modpacks" className={linkClass}>
                                <Layers className="w-3.5 h-3.5 mr-2 opacity-70" /> Modpacks
                            </Link>
                            <Link to="/plugins" className={linkClass}>
                                <Box className="w-3.5 h-3.5 mr-2 opacity-70" /> Plugins
                            </Link>
                            <Link to="/worlds" className={linkClass}>
                                <Save className="w-3.5 h-3.5 mr-2 opacity-70" /> Worlds
                            </Link>
                            <Link to="/art" className={linkClass}>
                                <Palette className="w-3.5 h-3.5 mr-2 opacity-70" /> Art Assets
                            </Link>
                            <Link to="/data" className={linkClass}>
                                <Database className="w-3.5 h-3.5 mr-2 opacity-70" /> Data Assets
                            </Link>
                        </div>
                    </div>

                    <div className="col-span-1">
                        <h4 className="text-xs font-bold uppercase text-slate-400 tracking-wider mb-4">Resources</h4>
                        <div className="flex flex-col space-y-3">
                            <Link to="/api-docs" className={linkClass}>
                                <Code className="w-4 h-4 mr-2 opacity-70" /> API Docs
                            </Link>
                            <div className="h-px bg-slate-100 dark:bg-white/5 my-1"></div>
                            <Link to="/terms" className={linkClass}>
                                <FileText className="w-4 h-4 mr-2 opacity-70" /> Terms of Service
                            </Link>
                            <Link to="/privacy" className={linkClass}>
                                <Shield className="w-4 h-4 mr-2 opacity-70" /> Privacy Policy
                            </Link>
                        </div>
                    </div>

                    <div className="col-span-1">
                        <h4 className="text-xs font-bold uppercase text-slate-400 tracking-wider mb-4">Community</h4>
                        <div className="flex flex-col space-y-3">
                            <a href="https://discord.gg/PcFaDVYqVe" target="_blank" rel="noopener noreferrer" className={linkClass}>
                                <DiscordIcon className="w-4 h-4 mr-2" /> Discord
                            </a>
                            <a href="https://x.com/modtalenet" target="_blank" rel="noopener noreferrer" className={linkClass}>
                                <XIcon className="w-3.5 h-3.5 mr-2.5" /> X (Twitter)
                            </a>
                            <a href="https://bsky.app/profile/modtale.net" target="_blank" rel="noopener noreferrer" className={linkClass}>
                                <BlueskyIcon className="w-4 h-4 mr-2" /> Bluesky
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