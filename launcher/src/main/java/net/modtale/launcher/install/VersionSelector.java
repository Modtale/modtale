package net.modtale.launcher.install;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectVersion;

public final class VersionSelector {

    private VersionSelector() {
    }

    public static Optional<ProjectVersion> latestCompatible(ProjectDetail project, String gameVersion) {
        if (project == null) {
            return Optional.empty();
        }
        return latestCompatible(project.versions(), gameVersion);
    }

    public static Optional<ProjectVersion> latestCompatible(List<ProjectVersion> versions, String gameVersion) {
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        return versions.stream()
                .filter(version -> version.supportsGameVersion(gameVersion))
                .max(Comparator
                        .comparingInt(VersionSelector::channelRank)
                        .thenComparing(VersionSelector::releaseInstant, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ProjectVersion::versionNumber, Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private static int channelRank(ProjectVersion version) {
        String channel = version.channel();
        if (channel == null || channel.isBlank() || "RELEASE".equalsIgnoreCase(channel)) {
            return 3;
        }
        if ("BETA".equalsIgnoreCase(channel)) {
            return 2;
        }
        if ("ALPHA".equalsIgnoreCase(channel)) {
            return 1;
        }
        return 0;
    }

    private static Instant releaseInstant(ProjectVersion version) {
        if (version.releaseDate() == null || version.releaseDate().isBlank()) {
            return null;
        }
        try {
            return Instant.parse(version.releaseDate());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
