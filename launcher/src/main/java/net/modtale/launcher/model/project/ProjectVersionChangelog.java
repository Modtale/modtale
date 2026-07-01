package net.modtale.launcher.model.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectVersionChangelog(
        String id,
        String versionNumber,
        String changelog
) {
}
