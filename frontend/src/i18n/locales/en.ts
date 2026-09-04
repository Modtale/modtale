const en = {
    common: {
        actions: {
            back: 'Back',
            close: 'Close',
            create: 'Create',
            download: 'Download',
            save: 'Save',
            search: 'Search',
            signIn: 'Sign in',
            signOut: 'Sign out'
        },
        language: {
            label: 'Language'
        },
        theme: 'Theme'
    },
    navigation: {
        browse: 'Browse',
        mods: 'Mods',
        allProjects: 'All Projects',
        plugins: 'Plugins',
        modpacks: 'Modpacks',
        worlds: 'Worlds',
        artAssets: 'Art Assets',
        dataAssets: 'Data Assets',
        api: 'API',
        dashboard: 'Dashboard',
        createProject: 'Create Project',
        yourProfile: 'Your Profile',
        profile: 'Profile',
        following: 'Following',
        userDashboard: 'User Dashboard',
        adminPanel: 'Admin Panel',
        signedInAs: 'Signed in as',
        openMenu: 'Open navigation menu',
        closeMenu: 'Close navigation menu'
    },
    footer: {
        discover: 'Discover',
        resources: 'Resources',
        community: 'Community',
        apiDocs: 'API Docs',
        status: 'Status',
        terms: 'Terms of Service',
        privacy: 'Privacy Policy',
        copyright: '© {{year}} Modtale.',
        description: {
            default: 'The premier community repository for Hytale. Discover, download, and share Hytale mods, server plugins, worlds, art assets, data assets, and modpacks.',
            plugins: 'The premier community repository for Hytale plugins. Discover, download, and share server plugins, admin tools, gameplay extensions, and supporting libraries.',
            modpacks: 'The premier community repository for Hytale modpacks. Discover, download, and share curated Hytale modpacks, collections, and bundled project setups.',
            worlds: 'The premier community repository for Hytale worlds. Discover, download, and share Hytale save files, maps, lobbies, schematics, and spawns.',
            art: 'The premier community repository for Hytale art assets. Discover, download, and share Hytale models, textures, animations, and creator resources.',
            data: 'The premier community repository for Hytale data assets. Discover, download, and share Hytale configs, loot tables, recipes, and data-driven files.'
        }
    },
    status: {
        title: 'Opening Modtale Status',
        redirecting: 'Redirecting to {{url}}.',
        open: 'Open Status'
    },
    project: {
        bannerAlt: 'Project Banner',
        iconAlt: 'Icon',
        changeBanner: 'Change Banner',
        uploadBanner: 'Upload Banner',
        changeIcon: 'Change Icon',
        recommendedBannerSize: 'Recommended: 1920x640',
        shortRecommendedBannerSize: 'Rec: 1920x640',
        recommendedIconSize: 'Rec: 512x512'
    },
    errors: {
        notFound: {
            documentTitle: '404 - Page Not Found | Modtale',
            title: 'Page Not Found',
            description: "The mod, modpack, or page you are looking for doesn't exist or has been removed.",
            returnHome: 'Return Home'
        }
    }
} as const;

export default en;
