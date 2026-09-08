package net.modtale.launcher.model.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

public record ProfileBadge(String id, String label, String tooltip, String imageUrl, String darkImageUrl, String legacyType) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ProfileBadge fromJson(JsonNode value) {
        if (value == null || value.isNull()) return legacy("");
        if (value.isTextual()) return legacy(value.asText(""));
        return new ProfileBadge(text(value, "id"), text(value, "label"), text(value, "tooltip"),
                text(value, "imageUrl"), text(value, "darkImageUrl"), "");
    }

    public static ProfileBadge legacy(String type) {
        String safeType = type == null ? "" : type;
        return new ProfileBadge(safeType, safeType, "", "", "", safeType);
    }

    public boolean legacy() {
        return legacyType != null && !legacyType.isBlank();
    }

    public String displayLabel() {
        return label == null || label.isBlank() ? id == null ? "" : id : label;
    }

    public String displayTooltip() {
        return tooltip == null || tooltip.isBlank() ? displayLabel() : tooltip;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }
}
