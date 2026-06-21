package net.modtale.model.project;

import java.util.Locale;

public enum ProjectViewCategory {
    ALL("all"),
    FAVORITES("favorites"),
    YOUR_PROJECTS("your_projects");

    private final String queryValue;

    ProjectViewCategory(String queryValue) {
        this.queryValue = queryValue;
    }

    public String getQueryValue() {
        return queryValue;
    }

    public boolean isPersonalView() {
        return this == FAVORITES || this == YOUR_PROJECTS;
    }

    public static ProjectViewCategory fromQueryValue(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return switch (normalized) {
            case "favorites" -> FAVORITES;
            case "your_projects", "yourprojects" -> YOUR_PROJECTS;
            default -> ALL;
        };
    }
}
