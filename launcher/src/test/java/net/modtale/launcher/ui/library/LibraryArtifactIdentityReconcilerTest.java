package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import java.util.List;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.project.ArtifactIdentity;
import org.junit.jupiter.api.Test;

class LibraryArtifactIdentityReconcilerTest {
    @Test
    void turnsAHighConfidenceLocalArtifactIntoAManagedInstall() {
        InstalledProject local = new InstalledProject("local:author-mod", "author:mod", "My Mod", "PLUGIN",
                "1.0", "", "2026.1", Instant.EPOCH, Instant.EPOCH, List.of("mods/my-mod.jar"),
                List.of(), List.of(), InstalledProject.SOURCE_LOCAL, InstalledProject.INSTALL_DIRECT, false, List.of());
        ArtifactIdentity.Match match = new ArtifactIdentity.Match("mods/my-mod.jar", "CURSEFORGE",
                "curseforge:42", "my-mod", "My Mod", "PLUGIN", "1.0", "99", "curseforge-fingerprint", 100);

        LibraryArtifactIdentityReconciler.Result result = LibraryArtifactIdentityReconciler.reconcile(List.of(local), List.of(match));

        assertEquals(1, result.resolvedCount());
        assertEquals("curseforge:42", result.projects().getFirst().projectId());
        assertEquals("99", result.projects().getFirst().installedVersionId());
        assertEquals(InstalledProject.SOURCE_CURSEFORGE, result.projects().getFirst().source());
    }

    @Test
    void refusesWeakMatches() {
        InstalledProject local = new InstalledProject("local:x", "x", "Same Name", "PLUGIN", "1", "", "",
                Instant.EPOCH, Instant.EPOCH, List.of("mods/x.jar"), List.of(), List.of(),
                InstalledProject.SOURCE_LOCAL, InstalledProject.INSTALL_DIRECT, false, List.of());
        ArtifactIdentity.Match weak = new ArtifactIdentity.Match("mods/x.jar", "MODTALE", "x", "x", "Same Name",
                "PLUGIN", "1", "", "name", 50);
        assertEquals(0, LibraryArtifactIdentityReconciler.reconcile(List.of(local), List.of(weak)).resolvedCount());
    }

    @Test
    void canonicalizesACurseForgeInstallToTheSameModtaleBinary() {
        InstalledProject curseForge = new InstalledProject("curseforge:42", "my-mod", "My Mod", "PLUGIN", "1.0",
                "99", "", Instant.EPOCH, Instant.EPOCH, List.of("mods/my-mod.jar"), List.of(), List.of(),
                InstalledProject.SOURCE_CURSEFORGE, InstalledProject.INSTALL_DIRECT, false, List.of());
        ArtifactIdentity.Match canonical = new ArtifactIdentity.Match("mods/my-mod.jar", "MODTALE", "modtale-id",
                "my-mod", "My Mod", "PLUGIN", "1.0", "modtale-version", "sha256", 100);

        var result = LibraryArtifactIdentityReconciler.reconcile(List.of(curseForge), List.of(canonical));

        assertEquals(1, result.resolvedCount());
        assertEquals("modtale-id", result.projects().getFirst().projectId());
        assertEquals(InstalledProject.SOURCE_MODTALE, result.projects().getFirst().source());
    }
}
