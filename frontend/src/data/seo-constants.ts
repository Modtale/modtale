import type { Classification } from './categories';

export interface SeoFaq {
    question: string;
    answer: string;
}

export interface SeoContentBlock {
    title: string;
    body: string;
}

export interface SeoRelatedLink {
    href: string;
    label: string;
    description: string;
}

export interface RouteSeoEntry {
    title: string;
    description: string;
    h1: string;
    keywords: string;
    intro: string;
    contentBlocks: SeoContentBlock[];
    faq: SeoFaq[];
    relatedLinks?: SeoRelatedLink[];
}

export const BROWSE_ROUTE_PATHS = new Set([
    '/mods',
    '/plugins',
    '/modpacks',
    '/worlds',
    '/art',
    '/data',
]);

export const UTILITY_NOINDEX_EXACT_PATHS = new Set([
    '/upload',
    '/login',
    '/verify',
    '/reset-password',
    '/mfa',
    '/terms',
    '/privacy',
    '/status',
    '/api-docs',
    '/api-docs/swagger',
    '/admin',
]);

export const UTILITY_NOINDEX_PREFIXES = ['/dashboard'];

export const DEFAULT_SEO = {
    title: 'Hytale Mods, Plugins & Modpacks | Modtale',
    description: 'Discover Hytale mods, Hytale plugins, modpacks, worlds, and creator tools. Modtale is the community hub for browsing, downloading, and publishing Hytale modpacks.',
    keywords: 'hytale mods, hytale mod, hytale modding, hytale plugins, hytale modpacks, hytale worlds, hytale assets, modtale',
};

export const ROUTE_SEO: Record<string, RouteSeoEntry> = {
    '/': {
        title: DEFAULT_SEO.title,
        h1: 'Hytale Mods, Plugins, and Modpacks',
        description: DEFAULT_SEO.description,
        keywords: DEFAULT_SEO.keywords,
        intro: 'Modtale helps Hytale players discover new mods and helps creators publish server plugins, save files, art assets, data assets, and curated project collections in one searchable community repository.',
        contentBlocks: [
            {
                title: 'Browse Hytale Mods and Plugins Faster',
                body: 'Jump straight into trending Hytale mods, server plugins, curated modpacks, save-file worlds, and creator tools without hunting across disconnected communities.',
            },
            {
                title: 'Built for Hytale Modding Creators',
                body: 'Creators can publish release notes, manage versions, share screenshots, document dependencies, and give players a cleaner way to install and follow projects.',
            },
            {
                title: 'A Home for Every Hytale Project Type',
                body: 'Modtale is not limited to one content type. Players can discover gameplay mods, server plugins, worlds and prefabs, art assets, data assets, and complete modpack collections from the same place.',
            },
        ],
        relatedLinks: [
            {
                href: '/mods',
                label: 'Hytale Mods',
                description: 'Browse every kind of Hytale modding project.',
            },
            {
                href: '/plugins',
                label: 'Hytale Plugins',
                description: 'Find server-side Hytale plugins and gameplay scripts.',
            },
            {
                href: '/modpacks',
                label: 'Hytale Modpacks',
                description: 'Explore curated collections of compatible Hytale projects.',
            },
        ],
        faq: [
            {
                question: 'Where can I find Hytale mods?',
                answer: 'Modtale organizes Hytale mods, server plugins, worlds, modpacks, and creator-made assets into dedicated browse pages so players can discover projects by category, popularity, and recent updates.',
            },
            {
                question: 'Does Modtale support Hytale plugin discovery too?',
                answer: 'Yes. Plugins are a core category on Modtale, with dedicated browsing for server plugins, admin tools, minigame systems, commands, and supporting libraries.',
            },
            {
                question: 'Is Modtale useful for Hytale modding creators?',
                answer: 'Yes. Modtale is designed for both players and creators, with upload flows, versioned releases, project pages, changelogs, and sharable links for Hytale modpacks.',
            },
        ],
    },
    '/mods': {
        title: 'Hytale Mods | Browse Hytale Mods and Modpacks | Modtale',
        h1: 'Hytale Mods',
        description: 'Browse Hytale mods and related modpacks on Modtale. Discover gameplay changes, server plugins, data assets, art assets, save files, and fresh community releases.',
        keywords: 'hytale mods, hytale mod, download hytale mods, best hytale mods, hytale modding, hytale addons, modtale mods',
        intro: 'Browse Hytale mods and modpacks in one place. Modtale brings together gameplay packs, creator tools, server plugins, save files, and asset-driven releases for every kind of Hytale player.',
        contentBlocks: [
            {
                title: 'More Than a Single Hytale Mod List',
                body: 'The mods page is the broadest view of Modtale. It helps players compare different kinds of Hytale projects, from gameplay overhauls and utility releases to server plugins, art assets, world downloads, and experimental creator work.',
            },
            {
                title: 'Discover Trending and Recently Updated Projects',
                body: 'Sort by popularity, trending momentum, updates, or new releases to surface the Hytale mods that are actively moving the community instead of relying on stale download pages.',
            },
            {
                title: 'Follow the Full Hytale Modding Ecosystem',
                body: 'Many Hytale players search for a single mod, but the best discoveries often come from adjacent categories like server plugins, modpacks, data assets, and community-made worlds linked from the same browse flow.',
            },
        ],
        relatedLinks: [
            {
                href: '/plugins',
                label: 'Hytale Plugins',
                description: 'Server-side and script-driven Hytale extensions.',
            },
            {
                href: '/modpacks',
                label: 'Hytale Modpacks',
                description: 'Curated collections of compatible Hytale projects.',
            },
            {
                href: '/data',
                label: 'Hytale Data Assets',
                description: 'Explore configs, recipes, rules, and creator-facing data releases.',
            },
        ],
        faq: [
            {
                question: 'What counts as a Hytale mod on Modtale?',
                answer: 'On Modtale, Hytale mods are used as a broad discovery label. Hytale’s official November 20, 2025 modding status update describes the main technical categories as server plugins, data assets, art assets, and save files, and Modtale helps players browse across all of them.',
            },
            {
                question: 'Can I browse Hytale mods by popularity or freshness?',
                answer: 'Yes. Modtale supports trending, popular, newest, and recently updated views so you can find established favorites as well as brand-new Hytale mods.',
            },
            {
                question: 'Why does the mods page include multiple project types?',
                answer: 'Players often search broadly for Hytale mods before narrowing into plugins, worlds, or modpacks. The main mods page is meant to be the widest entry point into the Hytale modding ecosystem.',
            },
        ],
    },
    '/plugins': {
        title: 'Hytale Plugins | Server Plugins and Extensions | Modtale',
        h1: 'Hytale Plugins',
        description: 'Browse Hytale plugins for servers and communities. Find admin tools, gameplay extensions, economy systems, minigames, moderation helpers, and reusable plugin libraries.',
        keywords: 'hytale plugins, hytale plugin, hytale server plugins, java plugins, hytale admin tools, hytale modding plugins, server automation',
        intro: 'Browse Hytale plugins built for server operators, creators, and communities. Discover gameplay extensions, admin tooling, utility libraries, and server-side Java plugin projects from the Modtale ecosystem.',
        contentBlocks: [
            {
                title: 'Hytale Plugins for Real Server Needs',
                body: 'Use the plugins category to find moderation helpers, economy systems, permission workflows, quality-of-life tools, event logic, and other releases that are built around multiplayer Hytale experiences.',
            },
            {
                title: 'Server-Focused Hytale Modding',
                body: 'Plugins are one of the most practical corners of Hytale modding. They power administration, gameplay rules, automation, and community features without forcing players to sift through unrelated asset types.',
            },
            {
                title: 'Discover Libraries Alongside Finished Plugins',
                body: 'Not every valuable plugin page is an end-user package. Modtale also helps creators surface reusable libraries and foundations that support the wider Hytale plugin ecosystem.',
            },
        ],
        relatedLinks: [
            {
                href: '/mods',
                label: 'All Hytale Mods',
                description: 'Return to the broadest Hytale browse page.',
            },
            {
                href: '/data',
                label: 'Hytale Data Assets',
                description: 'Browse configs, rules, and supporting data-driven releases.',
            },
            {
                href: '/modpacks',
                label: 'Hytale Modpacks',
                description: 'See how server plugins can connect with larger curated collections.',
            },
        ],
        faq: [
            {
                question: 'What is a Hytale plugin?',
                answer: 'A Hytale plugin is a server-focused extension, typically packaged as a Java plugin, that adds new multiplayer features, moderation tools, game systems, or automation to a Hytale server environment.',
            },
            {
                question: 'How are Hytale plugins different from general Hytale mods?',
                answer: 'Plugins usually focus on server behavior, administration, and shared gameplay systems, while broader Hytale mods can also include asset packs, worlds, standalone content releases, or client-facing gameplay changes.',
            },
            {
                question: 'Can I use Modtale to publish Hytale plugins?',
                answer: 'Yes. Plugin creators can publish releases, versions, descriptions, changelogs, and supporting files so players and server operators can discover and follow their work on Modtale.',
            },
        ],
    },
    '/modpacks': {
        title: 'Hytale Modpacks | Curated Hytale Collections | Modtale',
        h1: 'Hytale Modpacks',
        description: 'Discover curated Hytale modpacks on Modtale. Explore themed collections of mods, plugins, worlds, assets, and supporting files built to work together.',
        keywords: 'hytale modpacks, hytale packs, hytale mod collections, hytale mods bundle, hytale modding collections',
        intro: 'Browse Hytale modpacks that combine multiple projects into a single curated experience. Modtale makes it easier to discover packs built around themes, progression styles, and compatible feature sets.',
        contentBlocks: [
            {
                title: 'Find Compatible Hytale Project Collections',
                body: 'Modpacks help players move beyond one-off downloads by bundling compatible Hytale projects into a more complete experience with shared direction and purpose.',
            },
            {
                title: 'Great for Themed Hytale Playthroughs',
                body: 'Whether you want survival, exploration, server event setups, or creator-curated feature bundles, modpacks are one of the easiest ways to browse Hytale content with a clear theme.',
            },
            {
                title: 'See the Building Blocks Behind the Pack',
                body: 'Because Modtale also hosts the broader ecosystem, players can move from a modpack page into individual projects and better understand the Hytale modding stack behind each collection.',
            },
        ],
        relatedLinks: [
            {
                href: '/mods',
                label: 'Hytale Mods',
                description: 'Explore the individual projects that power many packs.',
            },
            {
                href: '/plugins',
                label: 'Hytale Plugins',
                description: 'Find server-side releases that can appear inside curated packs.',
            },
        ],
        faq: [
            {
                question: 'What is a Hytale modpack?',
                answer: 'A Hytale modpack is a curated collection of compatible projects, such as mods, plugins, worlds, and supporting assets, designed to work together as a larger experience.',
            },
            {
                question: 'Why use a Hytale modpack instead of single downloads?',
                answer: 'Modpacks help players discover combinations that were intentionally assembled around a playstyle or theme, which can save time compared with building a collection from scratch.',
            },
            {
                question: 'Can creators share custom Hytale modpacks on Modtale?',
                answer: 'Yes. Modtale supports curated pack-style releases so creators can present a fuller Hytale setup instead of only individual project pages.',
            },
        ],
    },
    '/worlds': {
        title: 'Hytale Worlds and Maps | Download Community Worlds | Modtale',
        h1: 'Hytale Worlds and Maps',
        description: 'Download Hytale worlds, maps, lobbies, spawns, and creator-built environments on Modtale. Explore custom experiences made for play, events, and community servers.',
        keywords: 'hytale worlds, hytale maps, hytale spawn, hytale lobby, hytale world download, hytale custom map',
        intro: 'Browse Hytale worlds, maps, and server-ready environments from the Modtale community. Discover save files, adventure spaces, lobbies, spawn builds, and creator-made exploration experiences.',
        contentBlocks: [
            {
                title: 'Custom Worlds for Every Hytale Play Style',
                body: 'From handcrafted showcase maps to practical server lobbies and spawn areas, the worlds category helps players and communities discover downloadable Hytale save files and environments with clear intent.',
            },
            {
                title: 'Worlds Connect Naturally with Hytale Mods',
                body: 'Maps and worlds often work best when paired with the right mods, plugins, and assets. Modtale keeps those related project types close together for easier discovery.',
            },
            {
                title: 'Great for Creators, Servers, and Events',
                body: 'World downloads are useful beyond solo exploration. Server owners can browse Hytale worlds for hubs, mini-events, showcases, and community-made build spaces.',
            },
        ],
        relatedLinks: [
            {
                href: '/mods',
                label: 'Hytale Mods',
                description: 'Return to the main Hytale browse page.',
            },
            {
                href: '/art',
                label: 'Hytale Art Assets',
                description: 'Find complementary creator-made models and visuals.',
            },
        ],
        faq: [
            {
                question: 'Can I download Hytale maps and worlds on Modtale?',
                answer: 'Yes. The worlds category is built for Hytale maps, save-style uploads, spawn builds, lobbies, and other downloadable creator-made environments.',
            },
            {
                question: 'Who should use the Hytale worlds category?',
                answer: 'It is useful for players looking for custom experiences, server owners building hubs or events, and creators who want to share maps and showcase environments.',
            },
            {
                question: 'Do Hytale worlds work alongside mods and plugins?',
                answer: 'Many do. Modtale keeps worlds close to other Hytale project categories so players can discover supporting mods, assets, and plugins that complement a specific map or experience.',
            },
        ],
    },
    '/art': {
        title: 'Hytale Art Assets | Models, Textures, and Visual Resources | Modtale',
        h1: 'Hytale Art Assets',
        description: 'Browse Hytale art assets on Modtale, including models, textures, animation-ready resources, particles, and other creator-made visual content for Hytale projects.',
        keywords: 'hytale art assets, hytale models, hytale textures, hytale animations, hytale resource assets, hytale creator resources',
        intro: 'Browse the visual side of Hytale modding with art assets, models, textures, particles, and creator-made resources that support polished Hytale experiences.',
        contentBlocks: [
            {
                title: 'Visual Building Blocks for Hytale Modding',
                body: 'Art assets are a core part of Hytale creation. They help creators shape project identity, improve presentation, and build custom experiences beyond default visuals.',
            },
            {
                title: 'Useful for Creators and Teams',
                body: 'The art category is designed for collaborators as much as players. Teams can discover reusable Hytale visual resources alongside worlds, data assets, and other project components.',
            },
            {
                title: 'Connected to the Rest of the Ecosystem',
                body: 'Modtale keeps visual assets close to the mods and worlds they support, which makes it easier to move from a finished project into the resources that helped create it.',
            },
        ],
        relatedLinks: [
            {
                href: '/data',
                label: 'Hytale Data Assets',
                description: 'Pair visual resources with data-driven project components.',
            },
            {
                href: '/worlds',
                label: 'Hytale Worlds',
                description: 'See how art assets connect to maps, save files, and environments.',
            },
        ],
        faq: [
            {
                question: 'What are Hytale art assets?',
                answer: 'Hytale art assets can include models, textures, particles, animations, and other creator-made visual resources used to build or enhance Hytale projects.',
            },
            {
                question: 'Who uses Hytale art assets on Modtale?',
                answer: 'They are useful for individual creators, collaborative teams, and players who want to explore the visual side of the Hytale modding community.',
            },
            {
                question: 'Are art assets separate from Hytale mods?',
                answer: 'They are their own category, but they often support larger Hytale mods, worlds, and modpacks by providing the visuals that make those projects feel distinct.',
            },
        ],
    },
    '/data': {
        title: 'Hytale Data Assets | Configs, Rules, and Data-Driven Releases | Modtale',
        h1: 'Hytale Data Assets',
        description: 'Browse Hytale data assets on Modtale. Discover configs, rulesets, loot setups, recipes, scripting support, and data-driven releases for Hytale creators and servers.',
        keywords: 'hytale data assets, hytale data packs, hytale configs, hytale scripting, hytale recipes, hytale rulesets, hytale creator tools',
        intro: 'Browse the data-driven side of Hytale modding with configs, rules, recipes, gameplay definitions, and utility releases that power flexible project behavior.',
        contentBlocks: [
            {
                title: 'Data-Driven Hytale Modding',
                body: 'Not every Hytale project is a traditional mod or plugin. Data assets give creators a structured way to share behavior, rules, balance decisions, and supporting logic through reusable files.',
            },
            {
                title: 'Useful for Creators and Server Operators',
                body: 'Data assets are practical for creators building systems and for server owners who need configurable Hytale content without browsing unrelated categories.',
            },
            {
                title: 'Built to Work with the Rest of Modtale',
                body: 'Because data assets often complement plugins, worlds, or broader modpacks, Modtale keeps them discoverable as a first-class category instead of burying them under generic downloads.',
            },
        ],
        relatedLinks: [
            {
                href: '/plugins',
                label: 'Hytale Plugins',
                description: 'Pair data assets with server-side gameplay systems.',
            },
            {
                href: '/mods',
                label: 'Hytale Mods',
                description: 'Return to the widest Hytale browse page.',
            },
        ],
        faq: [
            {
                question: 'What are Hytale data assets?',
                answer: 'Hytale data assets are creator-made files such as configs, rules, recipes, and other data-driven resources that support project behavior and customization.',
            },
            {
                question: 'Why would I browse Hytale data assets separately?',
                answer: 'They solve a different problem than art assets or full mods. The data category helps creators and server operators find configurable Hytale resources more quickly.',
            },
            {
                question: 'Do Hytale data assets work with other Modtale categories?',
                answer: 'Yes. Data assets often complement plugins, worlds, and broader Hytale modpacks, which is why Modtale keeps the category tightly connected to the rest of the platform.',
            },
        ],
    },
    '/upload': {
        title: 'Upload Hytale Mods and Projects | Modtale',
        h1: 'Upload Hytale Projects',
        description: 'Publish Hytale mods, plugins, worlds, and creator resources on Modtale.',
        keywords: 'upload hytale mod, publish hytale plugin, share hytale project, hytale creator upload',
        intro: 'Publish your Hytale work on Modtale.',
        contentBlocks: [],
        faq: [],
    },
};

export const getCategorySEO = (classification: Classification | 'All') => {
    switch (classification) {
        case 'PLUGIN':
            return ROUTE_SEO['/plugins'];
        case 'MODPACK':
            return ROUTE_SEO['/modpacks'];
        case 'SAVE':
            return ROUTE_SEO['/worlds'];
        case 'ART':
            return ROUTE_SEO['/art'];
        case 'DATA':
            return ROUTE_SEO['/data'];
        default:
            return ROUTE_SEO['/mods'];
    }
};

export const generateDynamicSEO = (
    baseSEO: { title: string; description: string },
    page: number,
    sort: string,
    view: string,
    query: string,
) => {
    let dynamicTitle = baseSEO.title;
    let dynamicDesc = baseSEO.description;

    let prefix = '';
    if (sort === 'popular' || view === 'popular') prefix = 'Popular ';
    else if (sort === 'trending' || view === 'trending') prefix = 'Trending ';
    else if (sort === 'downloads') prefix = 'Most Downloaded ';
    else if (sort === 'favorites') prefix = 'Most Favorited ';
    else if (sort === 'newest') prefix = 'Newest ';
    else if (sort === 'updated') prefix = 'Recently Updated ';
    else if (view === 'hidden_gems') prefix = 'Hidden Gems: ';

    if (prefix) {
        dynamicTitle = `${prefix}${dynamicTitle.replace(' | Modtale', '')} | Modtale`;
    }

    if (query) {
        dynamicTitle = `${dynamicTitle.replace(' | Modtale', '')} for "${query}" | Modtale`;
        dynamicDesc = `Search results for "${query}" on Modtale. ${dynamicDesc}`;
    }

    if (page > 0) {
        dynamicTitle = `${dynamicTitle} - Page ${page + 1}`;
        dynamicDesc = `${dynamicDesc} Page ${page + 1}.`;
    }

    return { title: dynamicTitle, description: dynamicDesc };
};
