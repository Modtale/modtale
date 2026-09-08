package net.modtale.launcher.model.project;

import java.util.Arrays;
import java.util.Optional;

public enum ProjectClassification {
    MODPACK("MODPACK", "Modpack", "Modpack", "modpack"),
    PLUGIN("PLUGIN", "Plugin", "Plugin", "mod"),
    SAVE("SAVE", "World", "World", "world"),
    ART("ART", "Art Asset", "Art", "mod"),
    DATA("DATA", "Data Asset", "Data", "mod");

    private final String apiValue;
    private final String label;
    private final String compactLabel;
    private final String routePrefix;

    ProjectClassification(String apiValue, String label, String compactLabel, String routePrefix) {
        this.apiValue = apiValue;
        this.label = label;
        this.compactLabel = compactLabel;
        this.routePrefix = routePrefix;
    }

    public String apiValue() {
        return apiValue;
    }

    public String label() {
        return label;
    }

    public String compactLabel() {
        return compactLabel;
    }

    public String routePrefix() {
        return routePrefix;
    }

    public boolean isModpack() {
        return this == MODPACK;
    }

    public static Optional<ProjectClassification> fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(classification -> classification.apiValue.equalsIgnoreCase(normalized))
                .findFirst();
    }

    public static boolean isModpack(String value) {
        return fromApiValue(value).map(ProjectClassification::isModpack).orElse(false);
    }

    public static String labelFor(String value) {
        return fromApiValue(value)
                .map(ProjectClassification::label)
                .orElseGet(() -> fallback(value));
    }

    public static String compactLabelFor(String value) {
        return fromApiValue(value)
                .map(ProjectClassification::compactLabel)
                .orElseGet(() -> fallback(value));
    }

    public static String routePrefixFor(String value) {
        return fromApiValue(value)
                .map(ProjectClassification::routePrefix)
                .orElse(PLUGIN.routePrefix);
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "Project" : value;
    }
}
