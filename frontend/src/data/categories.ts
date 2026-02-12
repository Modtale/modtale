import { Layout, FileCode, Database, Palette, Save, Layers, Globe, Star, Flame, Zap, Clock, Gem, Heart } from 'lucide-react';

export type Classification = 'PLUGIN' | 'DATA' | 'ART' | 'SAVE' | 'MODPACK';

export const BROWSE_VIEWS = [
    { id: 'all', label: 'All Projects', icon: Globe, defaultSort: 'relevance' },
    { id: 'popular', label: 'Popular', icon: Star, defaultSort: 'popular' },
    { id: 'trending', label: 'Trending', icon: Flame, defaultSort: 'trending' },
    { id: 'new', label: 'New Releases', icon: Zap, defaultSort: 'newest' },
    { id: 'updated', label: 'Recently Updated', icon: Clock, defaultSort: 'updated' },
    { id: 'hidden_gems', label: 'Hidden Gems', icon: Gem, defaultSort: 'rating' },
    { id: 'favorites', label: 'My Favorites', icon: Heart, defaultSort: 'relevance' },
];

export const GLOBAL_TAGS = [
    'Adventure', 'RPG', 'Sci-Fi', 'Fantasy', 'Survival', 'Magic', 'Tech', 'Exploration',
    'Minigame', 'PvP', 'Parkour', 'Hardcore', 'Skyblock', 'Puzzle', 'Quests',
    'Economy', 'Protection', 'Admin Tools', 'Chat', 'Anti-Cheat', 'Performance',
    'Library', 'API', 'Mechanics', 'World Gen', 'Recipes', 'Loot Tables', 'Functions',
    'Decoration', 'Vanilla+', 'Kitchen Sink', 'City', 'Landscape', 'Spawn', 'Lobby',
    'Medieval', 'Modern', 'Futuristic', 'Models', 'Textures', 'Animations', 'Particles'
].sort();

export const PROJECT_TYPES = [
    { id: 'All', label: 'All Projects', icon: Layout },
    { id: 'PLUGIN', label: 'Plugins', icon: FileCode },
    { id: 'DATA', label: 'Data Assets', icon: Database },
    { id: 'ART', label: 'Art Assets', icon: Palette },
    { id: 'SAVE', label: 'Worlds', icon: Save },
    { id: 'MODPACK', label: 'Modpacks', icon: Layers }
];

export const LICENSES = [
    { id: 'ARR', name: 'All Rights Reserved' },
    { id: 'MIT', name: 'MIT License' },
    { id: 'Apache-2.0', name: 'Apache 2.0' },
    { id: 'GPL-3.0', name: 'GNU GPL v3' },
    { id: 'LGPL-3.0', name: 'GNU LGPL v3' },
    { id: 'AGPL-3.0', name: 'GNU AGPL v3' },
    { id: 'MPL-2.0', name: 'Mozilla Public License 2.0' },
    { id: 'CC0-1.0', name: 'CC0 1.0 Universal' },
    { id: 'CC-BY-4.0', name: 'CC BY 4.0' },
    { id: 'CC-BY-SA-4.0', name: 'CC BY-SA 4.0' },
    { id: 'Unlicense', name: 'The Unlicense' },
];