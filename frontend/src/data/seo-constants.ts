import type { Classification } from './categories';

export const DEFAULT_SEO = {
    title: "Modtale - Hytale Mods, Maps & Plugins",
    description: "The premier community repository for Hytale. Discover, download, and share Hytale mods, plugins, art + data assets, modpacks, and worlds.",
    keywords: "hytale mods, hytale modding, download hytale mods, hytale plugins, hytale maps, hytale modpacks, hytale servers, modtale"
};

export const ROUTE_SEO: Record<string, { title: string, description: string, h1?: string, keywords: string }> = {
    '/plugins': {
        title: "Hytale Plugins & Server Scripts | Modtale",
        h1: "Hytale Plugins",
        description: "Browse and download thousands of Hytale plugins. Enhance your server with new mechanics, administration tools, and minigames.",
        keywords: "hytale plugins, server scripts, hytale server mods, admin tools, hytale minigames, server management, economy plugins, anti-cheat, permission systems"
    },
    '/modpacks': {
        title: "Hytale Modpacks & Collections | Modtale",
        h1: "Hytale Modpacks",
        description: "Discover curated Hytale modpacks. The easiest way to install collections of mods, plugins, and configuration files in one click.",
        keywords: "hytale modpacks, mod collections, hytale mod bundles, rpg packs, tech mods, magic mods, best hytale modpacks, modpack launcher"
    },
    '/worlds': {
        title: "Hytale Maps, Worlds & Spawns | Modtale",
        h1: "Hytale Worlds & Maps",
        description: "Download Hytale worlds, saves, and schematics. Explore custom maps, spawns, and structures created by the community.",
        keywords: "hytale maps, hytale worlds, download maps, hytale schematics, server spawns, adventure maps, pvp maps, parkour maps, hytale blueprints, custom structures"
    },
    '/art': {
        title: "Hytale Models & Art Assets | Modtale",
        h1: "Hytale Models & Art",
        description: "Find Hytale models, textures, and art assets. Customize the look of your game with community-created resource bundles.",
        keywords: "hytale models, hytale textures, art assets, hytale model maker, hmm assets, 3d models, custom items, entity models, resource packs, animations"
    },
    '/data': {
        title: "Hytale Data Packs & Configs | Modtale",
        h1: "Hytale Data Assets",
        description: "Browse Data Assets for Hytale. Find scripts, configurations, and data packs to customize your gameplay experience.",
        keywords: "hytale data packs, configs, loot tables, functions, behavior packs, hytale scripting, game rules, server configuration, custom recipes"
    },
    '/upload': {
        title: "Upload Project - Modtale",
        h1: "Upload",
        description: "Share your Hytale creations with the community. Upload mods, plugins, art, and worlds.",
        keywords: "upload hytale mod, share hytale content, publish hytale projects, mod hosting, hytale creator tools, distribute mods"
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