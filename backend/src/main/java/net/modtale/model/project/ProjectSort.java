package net.modtale.model.project;

import java.util.Locale;

public enum ProjectSort {
    RELEVANCE("relevance"),
    DOWNLOADS("downloads"),
    FAVORITES("favorites"),
    NEWEST("newest"),
    UPDATED("updated"),
    POPULAR("popular"),
    TRENDING("trending");

    private final String queryValue;

    ProjectSort(String queryValue) {
        this.queryValue = queryValue;
    }

    public String getQueryValue() {
        return queryValue;
    }

    public static ProjectSort fromQueryValue(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "downloads" -> DOWNLOADS;
            case "favorites" -> FAVORITES;
            case "new", "newest" -> NEWEST;
            case "updated" -> UPDATED;
            case "popular" -> POPULAR;
            case "trending" -> TRENDING;
            default -> RELEVANCE;
        };
    }
}
