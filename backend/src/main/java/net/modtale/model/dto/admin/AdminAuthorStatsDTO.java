package net.modtale.model.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminAuthorStatsDTO(
        String accountAge,
        String tier,
        String avatarUrl,
        long totalProjects
) {}
