package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleInstalledMod;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.InstalledProjectReference;
import net.modtale.launcher.model.worldlist.CreateWorldModListRequest;
import org.junit.jupiter.api.Test;

class LibraryWorldSnapshotMapperTest {

    @Test
    void mapsBundledModpackFileToReferencedModtaleProject() {
        Path leveling = Path.of("/mods/leveling-core.jar");
        InstalledProjectReference reference = new InstalledProjectReference(
                "leveling-reference",
                "leveling-project",
                "leveling-core",
                "LevelingCore",
                "PLUGIN",
                "0.5.5",
                "INCLUDED",
                "MODTALE",
                "",
                "",
                "",
                "leveling-core.jar",
                "",
                "/icons/leveling.png",
                false,
                true
        );
        InstalledProject pack = modpack(List.of(leveling.toString()), List.of(reference));

        List<CreateWorldModListRequest.Item> items = LibraryWorldSnapshotMapper.itemsFor(
                Set.of("AzureDoom:LevelingCore"),
                List.of(pack),
                List.of(new HytaleInstalledMod("AzureDoom:LevelingCore", "LevelingCore", "0.5.5", "", leveling))
        );

        CreateWorldModListRequest.Item item = items.getFirst();
        assertEquals("AzureDoom:LevelingCore", item.modId());
        assertEquals("leveling-project", item.projectId());
        assertEquals("leveling-core", item.slug());
        assertEquals("LevelingCore", item.title());
        assertEquals("0.5.5", item.versionNumber());
        assertEquals("PLUGIN", item.classification());
        assertEquals("MODTALE", item.source());
        assertEquals("/icons/leveling.png", item.icon());
    }

    @Test
    void keepsUnmatchedLocalModExternal() {
        Path local = Path.of("/mods/local-only.jar");

        List<CreateWorldModListRequest.Item> items = LibraryWorldSnapshotMapper.itemsFor(
                Set.of("maker:local-only"),
                List.of(),
                List.of(new HytaleInstalledMod("maker:local-only", "Local Only", "1.0.0", "", local))
        );

        CreateWorldModListRequest.Item item = items.getFirst();
        assertEquals("", item.projectId());
        assertEquals("Local Only", item.title());
        assertEquals("1.0.0", item.versionNumber());
        assertEquals("OTHER", item.source());
        assertEquals("maker:local-only", item.externalId());
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
