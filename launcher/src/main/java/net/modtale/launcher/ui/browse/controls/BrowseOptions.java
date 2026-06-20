package net.modtale.launcher.ui.browse.controls;

import java.util.Arrays;
import java.util.List;
import net.modtale.launcher.model.project.ProjectClassification;
import net.modtale.launcher.ui.common.LauncherIcons;

public final class BrowseOptions {

    public static final List<BrowseViewOption> BROWSE_VIEWS = List.of(BrowseViewOption.values());

    public static final List<ClassificationOption> PROJECT_TYPES = List.of(
            ClassificationOption.ALL,
            ClassificationOption.MODPACKS,
            ClassificationOption.PLUGINS,
            ClassificationOption.WORLDS,
            ClassificationOption.ART,
            ClassificationOption.DATA
    );

    public static final List<Integer> BROWSE_ITEMS_PER_PAGE_OPTIONS = List.of(6, 12, 24, 48, 96);

    public static final int DEFAULT_ITEMS_PER_PAGE = 12;

    public static final List<String> GLOBAL_TAGS = List.of(
            "Adventure", "RPG", "Sci-Fi", "Fantasy", "Survival", "Magic", "Tech", "Exploration",
            "Minigame", "PvP", "Parkour", "Hardcore", "Skyblock", "Puzzle", "Quests", "Mobs",
            "Economy", "Protection", "Admin Tools", "Chat", "Anti-Cheat", "Performance", "NPCs",
            "Library", "API", "Mechanics", "World Gen", "Recipes", "Loot Tables", "Functions",
            "Decoration", "Vanilla+", "Kitchen Sink", "City", "Landscape", "Spawn", "Lobby",
            "Medieval", "Modern", "Futuristic", "Models", "Textures", "Animations", "Particles"
    );

    private BrowseOptions() {
    }

    public static BrowseViewOption browseView(BrowseViewOption view) {
        return view == null ? BrowseViewOption.defaultOption() : view;
    }

    public static int itemsPerPage(Integer value) {
        return BROWSE_ITEMS_PER_PAGE_OPTIONS.contains(value) ? value : DEFAULT_ITEMS_PER_PAGE;
    }

    public static ClassificationOption classification(String classification) {
        if (classification == null || classification.isBlank()) {
            return ClassificationOption.defaultOption();
        }
        return Arrays.stream(ClassificationOption.values())
                .filter(option -> option.apiValue().equalsIgnoreCase(classification.trim()))
                .findFirst()
                .orElse(ClassificationOption.defaultOption());
    }

    public enum BrowseViewOption {
        ALL("All Projects", null, ProjectBrowseSort.RELEVANCE, LauncherIcons.Glyph.GLOBE),
        POPULAR("Popular", null, ProjectBrowseSort.POPULAR, LauncherIcons.Glyph.STAR),
        TRENDING("Trending", null, ProjectBrowseSort.TRENDING, LauncherIcons.Glyph.FLAME),
        NEW("New Releases", null, ProjectBrowseSort.NEWEST, LauncherIcons.Glyph.ZAP),
        UPDATED("Recently Updated", null, ProjectBrowseSort.UPDATED, LauncherIcons.Glyph.CLOCK),
        FAVORITES("My Favorites", "favorites", ProjectBrowseSort.RELEVANCE, LauncherIcons.Glyph.HEART);

        private final String label;
        private final String category;
        private final ProjectBrowseSort defaultSort;
        private final LauncherIcons.Glyph icon;

        BrowseViewOption(String label, String category, ProjectBrowseSort defaultSort, LauncherIcons.Glyph icon) {
            this.label = label;
            this.category = category;
            this.defaultSort = defaultSort;
            this.icon = icon;
        }

        public String label() {
            return label;
        }

        public String category() {
            return category;
        }

        public ProjectBrowseSort defaultSort() {
            return defaultSort;
        }

        public LauncherIcons.Glyph icon() {
            return icon;
        }

        public boolean isDefault() {
            return this == ALL;
        }

        public static BrowseViewOption defaultOption() {
            return ALL;
        }
    }

    public enum ClassificationOption {
        ALL("All Projects", null, "All Projects", LauncherIcons.Glyph.LAYOUT),
        PLUGINS("Plugins", ProjectClassification.PLUGIN, "Plugins", LauncherIcons.Glyph.FILE_CODE),
        DATA("Data", ProjectClassification.DATA, "Data Assets", LauncherIcons.Glyph.DATABASE),
        ART("Art", ProjectClassification.ART, "Art Assets", LauncherIcons.Glyph.PALETTE),
        WORLDS("Worlds", ProjectClassification.SAVE, "Worlds", LauncherIcons.Glyph.SAVE),
        MODPACKS("Modpacks", ProjectClassification.MODPACK, "Modpacks", LauncherIcons.Glyph.LAYERS);

        private final String label;
        private final ProjectClassification projectClassification;
        private final String browseMenuLabel;
        private final LauncherIcons.Glyph icon;

        ClassificationOption(
                String label,
                ProjectClassification projectClassification,
                String browseMenuLabel,
                LauncherIcons.Glyph icon
        ) {
            this.label = label;
            this.projectClassification = projectClassification;
            this.browseMenuLabel = browseMenuLabel;
            this.icon = icon;
        }

        public String label() {
            return label;
        }

        public String classification() {
            return apiValue();
        }

        public String apiValue() {
            return projectClassification == null ? "" : projectClassification.apiValue();
        }

        public String browseMenuLabel() {
            return browseMenuLabel;
        }

        public LauncherIcons.Glyph icon() {
            return icon;
        }

        public boolean isDefault() {
            return this == ALL;
        }

        public static ClassificationOption defaultOption() {
            return ALL;
        }
    }
}
