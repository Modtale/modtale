import React from 'react';
import { Helmet } from 'react-helmet-async';
import { useLocation } from 'react-router-dom';

export const SEOHead: React.FC = () => {
    const location = useLocation();
    const path = location.pathname.replace(/\/$/, "");

    let title = "Modtale - Hytale Mods & Plugins";
    let description = "The premier community repository for Hytale. Discover, download, and share Hytale mods, worlds, server plugins, art + data assets, and modpacks.";

    switch (path) {
        case '/plugins':
            title = "Hytale Plugins - Modtale";
            description = "Browse and download thousands of Hytale server plugins. Enhance your server with new mechanics, administration tools, and minigames.";
            break;
        case '/modpacks':
            title = "Hytale Modpacks - Modtale";
            description = "Discover curated Hytale modpacks. The easiest way to install collections of mods, plugins, and configuration files in one click.";
            break;
        case '/worlds':
            title = "Hytale Worlds - Modtale";
            description = "Download Hytale worlds, saves, and schematics. Explore custom maps, spawns, and structures created by the community.";
            break;
        case '/art':
            title = "Hytale Art Assets - Modtale";
            description = "Find Hytale models, textures, and art assets. Customize the look of your game with community-created resource bundles.";
            break;
        case '/data':
            title = "Hytale Data Assets - Modtale";
            description = "Browse Data Assets for Hytale. Find scripts, configurations, and data packs to customize your gameplay experience.";
            break;
        case '/upload':
            title = "Upload Project - Modtale";
            description = "Share your Hytale creations with the community. Upload mods, plugins, art, and worlds.";
            break;
        case '/analytics':
            title = "Creator Analytics - Modtale";
            description = "Track the performance of your Hytale projects. View downloads, views, and growth trends.";
            break;
    }

    return (
        <Helmet>
            <title>{title}</title>
            <meta name="description" content={description} />
            <meta property="og:title" content={title} />
            <meta property="og:description" content={description} />
            <link rel="canonical" href={`https://modtale.net${path === '' ? '/' : path}`} />
        </Helmet>
    );
};