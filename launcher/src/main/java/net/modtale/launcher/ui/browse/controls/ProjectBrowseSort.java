package net.modtale.launcher.ui.browse.controls;

import java.util.Arrays;
import java.util.Locale;

public enum ProjectBrowseSort {
    RELEVANCE("relevance", "Relevance", ""),
    POPULAR("popular", "Popular", "Popular"),
    TRENDING("trending", "Trending", "Trending"),
    DOWNLOADS("downloads", "Downloads", "Most Downloaded"),
    FAVORITES("favorites", "Favorites", "Most Favorited"),
    NEWEST("newest", "Newest", "New Releases"),
    UPDATED("updated", "Updated", "Recently Updated"),
    NAME("name", "Name", "Name"),
    AUTHOR("author", "Author", "Author");

    private final String apiValue;
    private final String label;
    private final String title;

    ProjectBrowseSort(String apiValue, String label, String title) {
        this.apiValue = apiValue;
        this.label = label;
        this.title = title;
    }

    public static java.util.List<ProjectBrowseSort> curseForgeSorts() {
        return java.util.List.of(DOWNLOADS, UPDATED, NEWEST, POPULAR, RELEVANCE, AUTHOR, NAME);
    }

    public String curseForgeLabel() {
        return switch (this) {
            case RELEVANCE -> "Relevancy";
            case POPULAR -> "Popularity";
            case UPDATED -> "Latest Update";
            case NEWEST -> "Creation Date";
            case DOWNLOADS -> "Total Downloads";
            default -> label;
        };
    }

    public String apiValue() {
        return apiValue;
    }

    public String label() {
        return label;
    }

    public String title() {
        return title;
    }

    public BrowseOptions.BrowseViewOption browseView() {
        return switch (this) {
            case POPULAR -> BrowseOptions.BrowseViewOption.POPULAR;
            case TRENDING -> BrowseOptions.BrowseViewOption.TRENDING;
            case NEWEST -> BrowseOptions.BrowseViewOption.NEW;
            case UPDATED -> BrowseOptions.BrowseViewOption.UPDATED;
            case RELEVANCE, DOWNLOADS, FAVORITES, NAME, AUTHOR -> BrowseOptions.BrowseViewOption.ALL;
        };
    }

    public static ProjectBrowseSort defaultSort() {
        return RELEVANCE;
    }

    public static ProjectBrowseSort fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return defaultSort();
        }
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(sort -> sort.label.toLowerCase(Locale.ROOT).equals(normalized)
                        || sort.title.toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .orElse(defaultSort());
    }
}
