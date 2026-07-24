package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.InstalledProjectReference;
import net.modtale.launcher.model.project.ProjectDependency;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.model.worldlist.WorldModList;
import net.modtale.launcher.model.worldlist.WorldModListItem;
import org.junit.jupiter.api.Test;

class InstallDuplicateWarningTest {

    @Test
    void detectsDirectInstallOfDifferentVersion() {
        InstalledProject installed = installed("mod-1", "cool-mod", "Cool Mod", "1.0.0");
        ProjectDetail project = project("mod-1", "cool-mod", "Cool Mod");

        InstallDuplicateWarning.Result result = InstallDuplicateWarning.forProject(
                List.of(installed),
                project,
                version("2.0.0"),
                List.of(),
                true
        );

        assertTrue(result.hasDuplicates());
        assertEquals(1, result.count());
        assertEquals("Cool Mod", result.duplicates().getFirst().displayName());
    }

    @Test
    void replacementFlowsCanIgnoreTheProjectBeingReplaced() {
        InstalledProject installed = installed("mod-1", "cool-mod", "Cool Mod", "1.0.0");
        InstalledProject other = installed("other-mod", "other-mod", "Other Mod", "1.0.0");
        ProjectDetail project = project("mod-1", "cool-mod", "Cool Mod");

        List<InstalledProject> candidates = LauncherLibraryController.installedProjectsExcept(
                List.of(installed, other),
                installed
        );
        InstallDuplicateWarning.Result result = InstallDuplicateWarning.forProject(
                candidates,
                project,
                version("2.0.0"),
                List.of(),
                true
        );

        assertEquals(List.of(other), candidates);
        assertFalse(result.hasDuplicates());
    }

    @Test
    void matchesRecoveredModtaleSlugAgainstProjectSlug() {
        InstalledProject recovered = installed("cool-mod", "cool-mod", "Cool Mod", "installed");
        ProjectDetail project = project("mongo-project-id", "cool-mod", "Cool Mod");

        InstallDuplicateWarning.Result result = InstallDuplicateWarning.forProject(
                List.of(recovered),
                project,
                version("1.2.0"),
                List.of(),
                true
        );

        assertTrue(result.hasDuplicates());
        assertEquals("Cool Mod", result.duplicates().getFirst().displayName());
    }

    @Test
    void detectsSelectedDependencyAlreadyInstalledByBundleDownload() {
        InstalledProject installedDependency = installed("dependency-project", "dependency", "Dependency Project", "2.0.0");
        ProjectDetail project = project("main-project", "main", "Main Project");

        InstallDuplicateWarning.Result result = InstallDuplicateWarning.forProject(
                List.of(installedDependency),
                project,
                version("1.0.0"),
                List.of(dependency("dependency-project", "dependency", "Dependency Project", false)),
                true
        );

        assertTrue(result.hasDuplicates());
        assertEquals(1, result.count());
        assertEquals("Dependency Project", result.duplicates().getFirst().displayName());
    }

    @Test
    void ignoresOptionalDependencyWhenItWillNotBeInstalled() {
        InstalledProject installedDependency = installed("optional-project", "optional", "Optional Project", "2.0.0");
        ProjectDetail project = project("main-project", "main", "Main Project");

        InstallDuplicateWarning.Result result = InstallDuplicateWarning.forProject(
                List.of(installedDependency),
                project,
                version("1.0.0"),
                List.of(dependency("optional-project", "optional", "Optional Project", true)),
                false
        );

        assertFalse(result.hasDuplicates());
    }

    @Test
    void detectsInstalledBundledReference() {
        InstalledProject bundle = new InstalledProject(
                "main-project",
                "main",
                "Main Project",
                "PLUGIN",
                "1.0.0",
                "version-1",
                "2026.1",
                Instant.EPOCH,
                Instant.EPOCH,
                List.of(),
                List.of("dependency-project"),
                List.of(),
                InstalledProject.SOURCE_MODTALE,
                InstalledProject.INSTALL_BUNDLE,
                false,
                List.of(reference("dependency-project", "dependency", "Dependency Project"))
        );
        ProjectDetail dependency = project("dependency-project", "dependency", "Dependency Project");

        InstallDuplicateWarning.Result result = InstallDuplicateWarning.forProject(
                List.of(bundle),
                dependency,
                version("2.0.0"),
                List.of(),
                true
        );

        assertTrue(result.hasDuplicates());
        assertEquals("Dependency Project", result.duplicates().getFirst().displayName());
    }

    @Test
    void detectsWorldModListItemAlreadyInstalled() {
        InstalledProject installed = installed("mod-1", "cool-mod", "Cool Mod", "1.0.0");
        WorldModList list = new WorldModList(
                "list-1",
                "World Mods",
                "World",
                "2026.1",
                "creator",
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH,
                0,
                0,
                2,
                1,
                "",
                "",
                "",
                List.of(
                        item("mod-1", "cool-mod", "Cool Mod", true),
                        item("missing-project", "missing", "Missing Mod", false)
                )
        );

        InstallDuplicateWarning.Result result = InstallDuplicateWarning.forWorldModList(List.of(installed), list);

        assertTrue(result.hasDuplicates());
        assertEquals(1, result.count());
        assertEquals("Cool Mod", result.duplicates().getFirst().displayName());
    }

    private static InstalledProject installed(String projectId, String slug, String title, String version) {
        return new InstalledProject(
                projectId,
                slug,
                title,
                "PLUGIN",
                version,
                version,
                "2026.1",
                Instant.EPOCH,
                Instant.EPOCH,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static ProjectDetail project(String projectId, String slug, String title) {
        return new ProjectDetail(
                projectId,
                slug,
                title,
                null,
                "",
                null,
                "Creator",
                "",
                "",
                "PLUGIN",
                0,
                0,
                "",
                "",
                "",
                Map.of(),
                List.of(),
                List.of(),
                Map.of(),
                null,
                false,
                null,
                List.of()
        );
    }

    private static ProjectVersion version(String number) {
        return new ProjectVersion(
                "version-" + number,
                number,
                List.of("2026.1"),
                "",
                0,
                "",
                "",
                List.of(),
                "RELEASE"
        );
    }

    private static ProjectDependency dependency(String projectId, String slug, String title, boolean optional) {
        return new ProjectDependency(
                projectId,
                projectId,
                title,
                "1.0.0",
                optional ? "OPTIONAL" : "REQUIRED",
                "MODTALE",
                "",
                "",
                "",
                "",
                "",
                false,
                "",
                title,
                "PLUGIN",
                slug,
                optional,
                false
        );
    }

    private static InstalledProjectReference reference(String projectId, String slug, String title) {
        return new InstalledProjectReference(
                projectId,
                projectId,
                slug,
                title,
                "PLUGIN",
                "2.0.0",
                "REQUIRED",
                "MODTALE",
                "",
                "",
                "",
                "",
                "",
                "",
                false,
                false
        );
    }

    private static WorldModListItem item(String projectId, String slug, String title, boolean downloadable) {
        return new WorldModListItem(
                projectId,
                projectId,
                projectId,
                slug,
                title,
                "1.0.0",
                "PLUGIN",
                "MODTALE",
                "",
                "",
                "",
                downloadable,
                downloadable ? "" : "Unavailable"
        );
    }
}
