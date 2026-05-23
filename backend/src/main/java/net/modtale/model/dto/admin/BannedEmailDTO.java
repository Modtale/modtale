package net.modtale.model.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BannedEmailDTO(
        String id,
        String email,
        String reason,
        String bannedBy,
        LocalDateTime bannedAt
) {}
