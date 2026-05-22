package net.modtale.model.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminProjectReviewDTO(
        AdminProjectDTO mod,
        AdminAuthorStatsDTO authorStats
) {}
