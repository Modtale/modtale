package net.modtale.model.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;
import net.modtale.model.user.NotificationType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationDTO(
        String id,
        String title,
        String message,
        String link,
        String iconUrl,
        boolean read,
        NotificationType type,
        Map<String, String> metadata,
        LocalDateTime createdAt
) {}
