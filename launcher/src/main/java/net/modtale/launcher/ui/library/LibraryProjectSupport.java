package net.modtale.launcher.ui.library;

import static net.modtale.launcher.ui.common.LauncherUi.classificationLabel;
import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld;
import net.modtale.launcher.install.UpdateService;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.InstalledProjectReference;
import net.modtale.launcher.model.project.ProjectVersion;

final class LibraryProjectSupport {

    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy")
            .withZone(ZoneId.systemDefault());

    private LibraryProjectSupport() {
    }

    static String projectRowMeta(InstalledProject project) {
        List<String> parts = new ArrayList<>();
        parts.add(classificationLabel(project.classification()));
        if (!project.gameVersion().isBlank()) {
            parts.add(project.gameVersion());
        }
        if (project.isModpack()) {
            parts.add("Grouped");
        }
        return String.join(" - ", parts);
    }

    static String childMeta(InstalledProjectReference reference) {
        List<String> parts = new ArrayList<>();
        parts.add(reference.isModtaleProject() ? "Modtale" : value(reference.source(), "External"));
        if (!reference.versionNumber().isBlank()) {
            parts.add(reference.versionNumber());
        }
        if (Boolean.TRUE.equals(reference.optional())) {
            parts.add("optional");
        }
        return String.join(" - ", parts);
    }

    static int contentCount(InstalledProject installed) {
        if (!installed.bundledProjects().isEmpty()) {
            return installed.bundledProjects().size();
        }
        int metadataCount = installed.dependencyProjectIds().size() + installed.externalDependencies().size();
        if (metadataCount > 0) {
            return metadataCount;
        }
        return installed.isModpack() ? installed.files().size() : 0;
    }

    static List<String> projectWorldModIds(InstalledProject installed) {
        if (installed.isModpack()) {
            return modpackChildren(installed);
        }
        String id = projectModId(installed);
        return id.isBlank() ? List.of() : List.of(id);
    }

    static List<String> modpackChildren(InstalledProject modpack) {
        LinkedHashSet<String> children = new LinkedHashSet<>();
        if (!modpack.bundledProjects().isEmpty()) {
            for (InstalledProjectReference reference : modpack.bundledProjects()) {
                String id = referenceWorldModId(reference);
                if (!id.isBlank()) {
                    children.add(id);
                }
            }
        } else {
            children.addAll(modpack.dependencyProjectIds());
            children.addAll(modpack.externalDependencies());
        }
        if (children.isEmpty()) {
            String fallback = projectModId(modpack);
            if (!fallback.isBlank()) {
                children.add(fallback);
            }
        }
        return List.copyOf(children);
    }

    static String routeKey(InstalledProject installed) {
        return installed.slug() == null || installed.slug().isBlank() ? installed.projectId() : installed.slug();
    }

    static boolean isModtaleProject(InstalledProject installed) {
        if (installed == null) {
            return false;
        }
        String source = installed.source() == null ? "" : installed.source().trim();
        return source.isBlank() || InstalledProject.SOURCE_MODTALE.equalsIgnoreCase(source);
    }

    static String dateLabel(Instant instant) {
        if (instant == null || Instant.EPOCH.equals(instant)) {
            return "Unknown";
        }
        return SHORT_TIME.format(instant);
    }

    static String worldMeta(HytaleWorld world) {
        List<String> parts = new ArrayList<>();
        if (!world.patchline().isBlank()) {
            parts.add(world.patchline());
        }
        if (!world.updatedAt().equals(Instant.EPOCH)) {
            parts.add(SHORT_TIME.format(world.updatedAt()));
        }
        return parts.isEmpty() ? "World save" : String.join(" - ", parts);
    }

    static List<LibraryVersionChoice> versionChoices(
            List<ProjectVersion> versions,
            InstalledProject installed,
            String fallbackGameVersion
    ) {
        String gameVersion = value(installed == null ? "" : installed.gameVersion(), fallbackGameVersion);
        return versions.stream()
                .sorted(Comparator
                        .comparing(LibraryProjectSupport::releaseInstant, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProjectVersion::versionNumber, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(version -> new LibraryVersionChoice(version, versionLabel(version, installed, gameVersion)))
                .toList();
    }

    static String installGameVersion(ProjectVersion version, InstalledProject installed, String fallbackGameVersion) {
        String preferred = value(installed == null ? "" : installed.gameVersion(), fallbackGameVersion);
        if (version == null) {
            return preferred;
        }
        if (!preferred.isBlank() && version.supportsGameVersion(preferred)) {
            return preferred;
        }
        return version.gameVersions().stream()
                .filter(gameVersion -> gameVersion != null && !gameVersion.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse(preferred);
    }

    static boolean sameVersion(InstalledProject installed, ProjectVersion version) {
        return installed != null && version != null && UpdateService.sameVersion(installed, version);
    }

    static String plural(int count) {
        return count == 1 ? "" : "s";
    }

    static String referenceWorldModId(InstalledProjectReference reference) {
        if (reference.slug() != null && reference.slug().contains(":")) {
            return reference.slug();
        }
        if (reference.projectId() != null && reference.projectId().contains(":")) {
            return reference.projectId();
        }
        if (reference.externalId() != null && !reference.externalId().isBlank()) {
            return reference.externalId();
        }
        return value(reference.slug(), value(reference.projectId(), "")).trim();
    }

    private static String projectModId(InstalledProject project) {
        if (project.slug() != null && project.slug().contains(":")) {
            return project.slug();
        }
        if (project.projectId() != null && project.projectId().contains(":")) {
            return project.projectId();
        }
        return value(project.slug(), value(project.projectId(), "")).trim();
    }

    private static String versionLabel(ProjectVersion version, InstalledProject installed, String gameVersion) {
        List<String> parts = new ArrayList<>();
        parts.add(value(version.versionNumber(), "Untitled"));
        if (sameVersion(installed, version)) {
            parts.add("installed");
        }
        if (version.channel() != null && !version.channel().isBlank()) {
            parts.add(version.channel().toLowerCase(java.util.Locale.ROOT));
        }
        String supportedGameVersions = supportedGameVersions(version, gameVersion);
        if (!supportedGameVersions.isBlank()) {
            parts.add(supportedGameVersions);
        }
        return String.join(" - ", parts);
    }

    private static String supportedGameVersions(ProjectVersion version, String preferredGameVersion) {
        if (version == null || version.gameVersions().isEmpty()) {
            return "";
        }
        if (preferredGameVersion != null
                && !preferredGameVersion.isBlank()
                && version.supportsGameVersion(preferredGameVersion)) {
            return preferredGameVersion;
        }
        return String.join(", ", version.gameVersions());
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
