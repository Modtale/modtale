package net.modtale.launcher.model.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectVersion(
        String id,
        String versionNumber,
        List<String> gameVersions,
        String fileUrl,
        int downloadCount,
        String releaseDate,
        String changelog,
        List<ProjectDependency> dependencies,
        String channel,
        List<String> incompatibleProjectIds
) {
    public ProjectVersion {
        gameVersions = gameVersions == null ? List.of() : List.copyOf(gameVersions);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        incompatibleProjectIds = incompatibleProjectIds == null ? List.of() : List.copyOf(incompatibleProjectIds);
    }

    public ProjectVersion(
            String id,
            String versionNumber,
            List<String> gameVersions,
            String fileUrl,
            int downloadCount,
            String releaseDate,
            String changelog,
            List<ProjectDependency> dependencies,
            String channel
    ) {
        this(id, versionNumber, gameVersions, fileUrl, downloadCount, releaseDate, changelog, dependencies, channel, List.of());
    }

    public boolean supportsGameVersion(String gameVersion) {
        return gameVersion == null || gameVersion.isBlank() || gameVersions.contains(gameVersion);
    }
}
