package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleInstalledMod;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.project.ArtifactIdentity;
import net.modtale.launcher.model.worldlist.WorldListConfig;
import net.modtale.launcher.settings.LauncherSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibrarySymlinkIdentityTest {
    @TempDir Path root;

    private Path[] eyeSpy() throws Exception {
        Path real = Files.createDirectories(root.resolve("UserData/Mods")).resolve("EyeSpy.jar");
        Files.writeString(real, "one artifact");
        Path alias = root.resolve(".var/app/com.hypixel.HytaleLauncher/data/Hytale/UserData");
        Files.createDirectories(alias.getParent());
        Files.createSymbolicLink(alias, root.resolve("UserData"));
        return new Path[]{real, alias.resolve("Mods/EyeSpy.jar")};
    }

    private InstalledProject project(String id, String source, Path file) {
        return new InstalledProject(id, "eyespy", "EyeSpy", "PLUGIN", "1.0", "file-99", "2026.1",
                null, null, List.of(file.toString()), List.of("dependency"), List.of(), source,
                InstalledProject.INSTALL_DIRECT, false, List.of(),
                List.of(new WorldListConfig("GLOBAL", "EyeSpy/config.json", "{}")));
    }

    @Test
    void oneEyeSpyJarKeepsItsCurseForgeRecordAcrossBothSymlinkDirections() throws Exception {
        Path[] files = eyeSpy();
        for (int direction = 0; direction < 2; direction++) {
            InstalledProject cf = project("curseforge:42", "CURSEFORGE", files[direction]);
            var scan = new HytaleInstalledMod("author:EyeSpy", "EyeSpy", "1.0", "", files[1-direction]);
            var result = LibraryLocalInstallRecovery.recover(new LauncherSettings(), List.of(cf), List.of(scan));
            assertEquals(0, result.recoveredCount());
            assertEquals(List.of(cf), result.projects());
            var item = LibraryWorldSnapshotMapper.itemsFor(Set.of(scan.id()), result.projects(), List.of(scan)).getFirst();
            assertEquals("CURSEFORGE", item.source());
            assertEquals("curseforge:42", item.externalId());
        }
    }

    @Test
    void removesAlreadySavedLocalAliasButRetainsDifferentSameNamedArtifact() throws Exception {
        Path[] files = eyeSpy();
        InstalledProject cf = project("curseforge:42", "CURSEFORGE", files[0]);
        InstalledProject local = project("local:eyespy", "LOCAL", files[1]);
        Path other = Files.createDirectories(root.resolve("other")).resolve("EyeSpy.jar");
        Files.writeString(other, "different artifact");
        InstalledProject distinct = project("local:other", "LOCAL", other);
        var scan = new HytaleInstalledMod("author:EyeSpy", "EyeSpy", "1.0", "", files[1]);
        for (var records : List.of(List.of(local, cf, distinct), List.of(cf, local, distinct))) {
            var result = LibraryLocalInstallRecovery.recover(new LauncherSettings(), records, List.of(scan));
            assertEquals(List.of(cf, distinct), result.projects());
            assertEquals(0, result.recoveredCount());
            assertEquals(result, LibraryLocalInstallRecovery.recover(new LauncherSettings(), result.projects(), List.of(scan)));
        }
    }

    @Test
    void verifiedIdentityMatchesAliasAndPreservesInstallConfiguration() throws Exception {
        Path[] files = eyeSpy();
        InstalledProject cf = project("curseforge:42", "CURSEFORGE", files[0]);
        var match = new ArtifactIdentity.Match(files[1].toString(), "MODTALE", "verified-id", "eyespy",
                "EyeSpy", "PLUGIN", "1.0", "verified-version", "sha256", 100);
        var result = LibraryArtifactIdentityReconciler.reconcile(List.of(cf), List.of(match));
        assertEquals(1, result.resolvedCount());
        var linked = result.projects().getFirst();
        assertEquals("verified-id", linked.projectId());
        assertEquals("verified-version", linked.installedVersionId());
        assertEquals(cf.universeConfigs(), linked.universeConfigs());
        assertEquals(cf.dependencyProjectIds(), linked.dependencyProjectIds());
        assertEquals(cf.files(), linked.files());
    }

    @Test
    void missingAndInvalidPathsHaveStableKeys() {
        assertEquals(root.resolve("missing.jar").toString(), LibraryFileIdentity.key(root.resolve("mods/../missing.jar")));
        assertEquals("bad\0path", LibraryFileIdentity.key("bad\0path"));
        assertEquals("", LibraryFileIdentity.key(""));
    }
}
