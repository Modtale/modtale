import type { Classification } from './categories';

export const DEFAULT_SEO = {
    title: "Modtale - Hytale Mods & Plugins",
    description: "The premier community repository for Hytale. Discover, download, and share Hytale mods, worlds, server plugins, art + data assets, and modpacks.",
    keywords: "hytale, mods, plugins, assets, modpacks, worlds, modtale, hytale marketplace"
};

export const ROUTE_SEO: Record<string, { title: string, description: string, h1?: string, keywords: string }> = {
    '/plugins': {
        title: "Hytale Plugins & Server Scripts | Modtale",
        h1: "Hytale Plugins",
        description: "Browse and download thousands of Hytale server plugins. Enhance your server with new mechanics, administration tools, and minigames.",
        keywords: "hytale plugins, server scripts, admin tools, hytale server"
    },
    '/modpacks': {
        title: "Hytale Modpacks & Collections | Modtale",
        h1: "Hytale Modpacks",
        description: "Discover curated Hytale modpacks. The easiest way to install collections of mods, plugins, and configuration files in one click.",
        keywords: "hytale modpacks, mod collections, rpg packs, tech mods"
    },
    '/worlds': {
        title: "Hytale Maps, Worlds & Spawns | Modtale",
        h1: "Hytale Worlds & Maps",
        description: "Download Hytale worlds, saves, and schematics. Explore custom maps, spawns, and structures created by the community.",
        keywords: "hytale maps, worlds, schematics, builds, spawns"
    },
    '/art': {
        title: "Hytale Models & Art Assets | Modtale",
        h1: "Hytale Models & Art",
        description: "Find Hytale models, textures, and art assets. Customize the look of your game with community-created resource bundles.",
        keywords: "hytale models, textures, art assets, animations, resource packs"
    },
    '/data': {
        title: "Hytale Data Packs & Configs | Modtale",
        h1: "Hytale Data Assets",
        description: "Browse Data Assets for Hytale. Find scripts, configurations, and data packs to customize your gameplay experience.",
        keywords: "hytale data packs, configs, loot tables, functions"
    },
    '/upload': {
        title: "Upload Project - Modtale",
        h1: "Upload",
        description: "Share your Hytale creations with the community. Upload mods, plugins, art, and worlds.",
        keywords: "upload hytale mod, share hytale content"
    }
};

export const getCategorySEO = (classification: Classification | 'All') => {
    switch (classification) {
        case 'PLUGIN': return ROUTE_SEO['/plugins'];
        case 'MODPACK': return ROUTE_SEO['/modpacks'];
        case 'SAVE': return ROUTE_SEO['/worlds'];
        case 'ART': return ROUTE_SEO['/art'];
        case 'DATA': return ROUTE_SEO['/data'];
        default: return { ...DEFAULT_SEO, h1: null };
    }
};