package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleInstalledMod;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.InstalledProjectReference;
import org.junit.jupiter.api.Test;

class LibraryBundledInstallNormalizerTest {

    @Test
    void promotesBundledDependencyToNormalInstalledProject() {
        Path main = Path.of("/mods/main.jar");
        Path dependency = Path.of("/mods/dependency.jar");
        InstalledProjectReference reference = new InstalledProjectReference(
                "dependency-reference",
                "dependency-project",
                "dependency",
                "Dependency Project",
                "PLUGIN",
                "2.0.0",
                "REQUIRED",
                "MODTALE",
                "",
                "",
                "",
                "dependency.jar",
                "",
                "",
                false,
                false
        );
        InstalledProject parent = new InstalledProject(
                "main-project",
                "main",
                "Main Project",
                "PLUGIN",
                "1.0.0",
                "version-1",
                "2026.1",
                Instant.EPOCH,
                Instant.EPOCH,
                List.of(main.toString(), dependency.toString()),
                List.of("dependency-project"),
                List.of(),
                InstalledProject.SOURCE_MODTALE,
                InstalledProject.INSTALL_BUNDLE,
                false,
                List.of(reference)
        );

        LibraryBundledInstallNormalizer.Result result = LibraryBundledInstallNormalizer.normalize(
                List.of(parent),
                List.of(new HytaleInstalledMod("dependency", "Dependency Project", "2.0.0", "", dependency))
        );

        assertEquals(1, result.addedChildren());
        assertEquals(2, result.projects().size());
        InstalledProject normalizedParent = result.projects().getFirst();
        InstalledProject child = result.projects().get(1);
        assertEquals(List.of(main.toString()), normalizedParent.files());
        assertTrue(normalizedParent.bundledProjects().isEmpty());
        assertTrue(normalizedParent.dependencyProjectIds().isEmpty());
        assertEquals(InstalledProject.INSTALL_DIRECT, normalizedParent.installType());
        assertEquals("dependency-project", child.projectId());
        assertEquals("Dependency Project", child.title());
        assertEquals(List.of(dependency.toString()), child.files());
        assertEquals(InstalledProject.INSTALL_DIRECT, child.installType());
        assertTrue(child.bundledProjects().isEmpty());
    }
}
