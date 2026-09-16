package net.modtale.launcher.ui.library;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleInstalledMod;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.worldlist.WorldModList;

/** Preserve the shared list's verified project identities when registering its files. */
final class LibrarySharedListRecords {
    private LibrarySharedListRecords() {}

    static List<InstalledProject> merge(List<InstalledProject> existing, WorldModList list,
            List<HytaleInstalledMod> scanned, List<Path> installedFiles) {
        var result = new ArrayList<>(existing);
        var paths = installedFiles.stream().map(LibraryFileIdentity::key).toList();
        for (var item : list.mods()) {
            String projectId = item.projectId().isBlank() && "CURSEFORGE".equalsIgnoreCase(item.source())
                    ? item.externalId() : item.projectId();
            if ("CURSEFORGE".equalsIgnoreCase(item.source()) && projectId.matches("[1-9][0-9]*"))
                projectId = "curseforge:" + projectId;
            if (projectId.isBlank() || item.modId().isBlank()) continue;
            final String identity = projectId;
            var files = scanned.stream().filter(mod -> item.modId().equalsIgnoreCase(mod.id()))
                    .map(HytaleInstalledMod::file).filter(file -> paths.contains(LibraryFileIdentity.key(file)))
                    .map(Path::toString).toList();
            if (files.isEmpty()) continue;
            var keys = files.stream().map(LibraryFileIdentity::key).toList();
            result.removeIf(project -> project.projectId().equals(identity)
                    || (!project.files().isEmpty() && project.files().stream()
                    .map(LibraryFileIdentity::key).allMatch(keys::contains)));
            String fileId = "";
            if ("CURSEFORGE".equalsIgnoreCase(item.source())) {
                var matcher = java.util.regex.Pattern.compile("/files/([1-9][0-9]*)(?:[/?#]|$)").matcher(item.externalUrl());
                if (matcher.find()) fileId = matcher.group(1);
            }
            result.add(new InstalledProject(identity, item.slug(), item.title(), item.classification(),
                    item.versionNumber(), fileId, "", Instant.now(), Instant.now(), files, List.of(), List.of(),
                    item.source(), InstalledProject.INSTALL_DIRECT, false, List.of()));
        }
        return List.copyOf(result);
    }
}
