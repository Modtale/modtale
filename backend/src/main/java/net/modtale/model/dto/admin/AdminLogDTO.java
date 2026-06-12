package net.modtale.model.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminLogDTO(
        String id,
        String adminUsername,
        String action,
        String targetId,
        String targetType,
        String details,
        LocalDateTime timestamp
) {}
