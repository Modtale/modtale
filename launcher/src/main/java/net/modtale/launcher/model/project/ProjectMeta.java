package net.modtale.launcher.model.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectMeta(
        String title,
        String description,
        String icon,
        String author,
        String classification,
        int downloads,
        String repositoryUrl,
        String slug
) {
}
