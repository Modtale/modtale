package net.modtale.launcher.model.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LauncherNotification(
        String id,
        String title,
        String message,
        String link,
        String iconUrl,
        boolean read,
        String type,
        Map<String, String> metadata,
        LocalDateTime createdAt
) {
    public LauncherNotification {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean actionable() {
        return actionType().isPresent();
    }

    public Optional<ActionType> actionType() {
        return ActionType.fromType(type);
    }

    public enum ActionType {
        TRANSFER_REQUEST("TRANSFER_REQUEST"),
        ORG_INVITE("ORG_INVITE"),
        CONTRIBUTOR_INVITE("CONTRIBUTOR_INVITE");

        private final String type;

        ActionType(String type) {
            this.type = type;
        }

        public String type() {
            return type;
        }

        static Optional<ActionType> fromType(String type) {
            if (type == null || type.isBlank()) {
                return Optional.empty();
            }
            return Arrays.stream(values())
                    .filter(actionType -> actionType.type.equals(type.trim()))
                    .findFirst();
        }
    }
}
