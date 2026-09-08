package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleInstalledMod;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.InstalledProjectReference;
import org.junit.jupiter.api.Test;

class LibraryModpackUnlockConverterTest {

    @Test
    void convertsModpackReferencesIntoDirectInstalledProjectsAndRemovesParent() {
        Path leveling = Path.of("/mods/leveling-core.jar");
        Path quests = Path.of("/mods/quests.jar");
        InstalledProjectReference levelingReference = new InstalledProjectReference(
                "leveling-reference",
                "leveling-project",
                "leveling-core",
                "Leveling Core",
                "PLUGIN",
                "0.5.5",
                "INCLUDED",
                "MODTALE",
                "",
                "",
                "",
                "leveling-core.jar",
                "",
                "",
                false,
                true
        );
        InstalledProjectReference questsReference = new InstalledProjectReference(
                "quests-reference",
                "quests-project",
                "quests",
                "Quests",
                "PLUGIN",
                "1.2.0",
                "INCLUDED",
                "MODTALE",
                "",
                "",
                "",
                "quests.jar",
                "",
                "",
                false,
                true
        );
        InstalledProject pack = modpack(List.of(leveling.toString(), quests.toString()),
                List.of(levelingReference, questsReference));
        InstalledProject existing = new InstalledProject(
                "existing-project",
                "existing",
                "Existing",
                "PLUGIN",
                "1.0.0",
                "",
                "1.1.2",
                Instant.EPOCH,
                Instant.EPOCH,
                List.of("/mods/existing.jar"),
                List.of(),
                List.of(),
                InstalledProject.SOURCE_MODTALE,
                InstalledProject.INSTALL_DIRECT,
                false,
                List.of()
        );

        LibraryModpackUnlockConverter.Result result = LibraryModpackUnlockConverter.convert(
                List.of(existing, pack),
                pack,
                List.of(
                        new HytaleInstalledMod("group:leveling-core", "Leveling Core", "0.5.5", "", leveling),
                        new HytaleInstalledMod("group:quests", "Quests", "1.2.0", "", quests)
                )
        );

        assertEquals(2, result.convertedCount());
        assertEquals(3, result.projects().size());
        assertEquals("existing-project", result.projects().get(0).projectId());
        InstalledProject levelingProject = result.projects().get(1);
        InstalledProject questsProject = result.projects().get(2);
        assertEquals("leveling-project", levelingProject.projectId());
        assertEquals("Leveling Core", levelingProject.title());
        assertEquals(List.of(leveling.toString()), levelingProject.files());
        assertEquals(InstalledProject.INSTALL_DIRECT, levelingProject.installType());
        assertFalse(levelingProject.modpackUnlocked());
        assertEquals("quests-project", questsProject.projectId());
        assertEquals(List.of(quests.toString()), questsProject.files());
        assertFalse(result.projects().stream().anyMatch(project -> "pack-project".equals(project.projectId())));
    }

    @Test
    void convertsUnmatchedModpackFilesIntoLocalInstalledProjects() {
        Path local = Path.of("/mods/local-plugin.jar");
        InstalledProject pack = modpack(List.of(local.toString()), List.of());

        LibraryModpackUnlockConverter.Result result = LibraryModpackUnlockConverter.convert(
                List.of(pack),
                pack,
                List.of(new HytaleInstalledMod("creator:local-plugin", "Local Plugin", "3.0.0", "", local))
        );

        assertEquals(1, result.convertedCount());
        assertEquals(1, result.projects().size());
        InstalledProject child = result.projects().getFirst();
        assertEquals("creator:local-plugin", child.projectId());
        assertEquals("Local Plugin", child.title());
        assertEquals("3.0.0", child.installedVersion());
        assertEquals(InstalledProject.SOURCE_LOCAL, child.source());
        assertEquals(InstalledProject.INSTALL_DIRECT, child.installType());
    }

    private static InstalledProject modpack(List<String> files, List<InstalledProjectReference> references) {
        return new InstalledProject(
                "pack-project",
                "pack",
                "Modpack",
                "MODPACK",
                "1.0.0",
                "pack-version",
                "1.1.2",
                Instant.EPOCH,
                Instant.EPOCH,
                files,
                references.stream().map(InstalledProjectReference::projectId).filter(id -> !id.isBlank()).toList(),
                List.of(),
                InstalledProject.SOURCE_MODTALE,
                InstalledProject.INSTALL_MODPACK,
                false,
                references
        );
    }
}
