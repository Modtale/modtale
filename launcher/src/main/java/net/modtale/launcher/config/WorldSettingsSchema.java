package net.modtale.launcher.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.util.List;

final class WorldSettingsSchema {
    private WorldSettingsSchema() {}

    static final List<Spec> SETTINGS = List.of(
            new Spec("/DisplayName", "World display name", TextNode.valueOf(""), "", true, false,
                    List.of(), null, null, "Leave blank to use the world's default name. The save folder keeps its name."),
            new Spec("/IsPvpEnabled", "PvP", BooleanNode.FALSE, "false", false, false, List.of(), null, null, ""),
            new Spec("/IsFallDamageEnabled", "Fall damage", BooleanNode.TRUE, "true", false, false, List.of(), null, null, ""),
            new Spec("/IsGameTimePaused", "Pause day / night cycle", BooleanNode.FALSE, "false", false, false, List.of(), null, null, ""),
            duration("DaytimeDurationSeconds", "Day duration (seconds)"),
            duration("NighttimeDurationSeconds", "Night duration (seconds)"),
            new Spec("/Death/ItemsLossMode", "Inventory penalty on death", TextNode.valueOf(""), "", true, false,
                    List.of("", "None", "Configured", "All"), null, null, "Partial drop applies to resources marked to drop on death."),
            percentage("ItemsAmountLossPercentage", "Resource loss on death (%)"),
            percentage("ItemsDurabilityLossPercentage", "Durability loss on death (%)"),
            new Spec("/ChunkStorage/Type", "Storage type", TextNode.valueOf(""), "Hytale", false, true,
                    List.of(), null, null, "Storage changes may require chunk migration. Manage storage in Hytale.")
    );

    private static Spec duration(String field, String name) {
        return new Spec("/" + field, name, IntNode.valueOf(1), "", true, false, List.of(),
                BigDecimal.ONE, BigDecimal.valueOf(Integer.MAX_VALUE), "Leave blank to use the gameplay default. Longer durations slow the cycle.");
    }

    private static Spec percentage(String field, String name) {
        return new Spec("/Death/" + field, name, DecimalNode.valueOf(BigDecimal.ZERO), "", true, false,
                List.of(), BigDecimal.ZERO, BigDecimal.valueOf(100), "Leave blank to use the gameplay default.");
    }

    record Spec(String pointer, String name, JsonNode type, String fallback, boolean optional, boolean readOnly,
                List<String> choices, BigDecimal min, BigDecimal max, String hint) {
        boolean accepts(JsonNode value) {
            return type.isBoolean() ? value.isBoolean() : type.isIntegralNumber() ? value.isIntegralNumber()
                    : type.isNumber() ? value.isNumber() : value.isTextual();
        }

        void validate(String value) {
            if (optional && value.isBlank()) return;
            if (!choices.isEmpty() && !choices.contains(value)) throw new IllegalArgumentException("Choose an available option.");
            if (min != null) {
                try {
                    BigDecimal number = new BigDecimal(value.trim());
                    if (number.compareTo(min) < 0 || number.compareTo(max) > 0)
                        throw new IllegalArgumentException("Enter a value from " + min + " to " + max + ".");
                } catch (NumberFormatException ex) { throw new IllegalArgumentException("Enter a number."); }
            }
            if (pointer.equals("/DisplayName") && (value.length() > 128 || value.chars().anyMatch(Character::isISOControl)))
                throw new IllegalArgumentException("Use a name of up to 128 characters without control characters.");
        }
    }
}
