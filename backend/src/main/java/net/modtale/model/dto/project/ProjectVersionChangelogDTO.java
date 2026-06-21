package net.modtale.model.dto.project;

public record ProjectVersionChangelogDTO(
        String id,
        String versionNumber,
        String changelog
) {}
