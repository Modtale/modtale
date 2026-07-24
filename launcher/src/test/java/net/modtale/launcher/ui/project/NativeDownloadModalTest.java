package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.List;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.model.project.GameVersionCatalog;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectVersion;
import org.junit.jupiter.api.Test;

class NativeDownloadModalTest {

    @Test
    void defaultsToLatestCatalogGameVersionInsteadOfPreferredOrGroupedFamily() throws Exception {
        NativeDownloadModal modal = new NativeDownloadModal(
                () -> null,
                () -> "0.5.3",
                ignored -> {
                },
                ignored -> {
                }
        );

        modal.show(project(List.of(
                version("v53", "1.0.0", "0.5.3"),
                version("v54", "1.1.0", "0.5.4"),
                version("v49", "0.9.0", "0.4.9")
        )), catalog("0.5.4", "0.5.3", "0.4.9"));

        assertEquals(List.of("0.5.4"), selectedGameVersions(modal));
    }

    @Test
    void refreshPromotesEmptySelectionToLatestCatalogGameVersion() throws Exception {
        NativeDownloadModal modal = new NativeDownloadModal(
                () -> null,
                () -> "",
                ignored -> {
                },
                ignored -> {
                }
        );
        ProjectDetail emptyProject = project(List.of());
        ProjectDetail hydratedProject = project(List.of(
                version("v53", "1.0.0", "0.5.3"),
                version("v54", "1.1.0", "0.5.4")
        ));

        modal.showLoading(emptyProject, catalog("0.5.4", "0.5.3"));
        assertEquals(List.of(), selectedGameVersions(modal));
        markShowing(modal);

        modal.refresh(hydratedProject, catalog("0.5.4", "0.5.3"));

        assertEquals(List.of("0.5.4"), selectedGameVersions(modal));
    }

    private static ProjectDetail project(List<ProjectVersion> versions) {
        return new ProjectDetail(
                "project-id",
                "skyforge",
                "Skyforge",
                "A test project",
                "Modtale",
                "PLUGIN",
                "2026-01-01T00:00:00Z",
                "MIT",
                null,
                List.of("Magic"),
                versions
        );
    }

    private static ProjectVersion version(String id, String versionNumber, String gameVersion) {
        return new ProjectVersion(
                id,
                versionNumber,
                List.of(gameVersion),
                "/files/" + id + ".jar",
                0,
                "2026-01-01T00:00:00Z",
                "",
                List.of(),
                "RELEASE"
        );
    }

    private static GameVersionCatalog catalog(String... versions) {
        return GameVersionCatalog.fromVersions(List.of(versions));
    }

    @SuppressWarnings("unchecked")
    private static List<String> selectedGameVersions(NativeDownloadModal modal) throws Exception {
        Field field = NativeDownloadModal.class.getDeclaredField("selectedGameVersions");
        field.setAccessible(true);
        return (List<String>) field.get(modal);
    }

    private static void markShowing(NativeDownloadModal modal) throws Exception {
        Field field = NativeDownloadModal.class.getDeclaredField("overlay");
        field.setAccessible(true);
        field.set(modal, new StackPane());
    }
}
