package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import net.modtale.launcher.model.install.InstalledProject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LibraryProviderBadgeTest {
    @BeforeAll
    static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { /* Shared toolkit. */ }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CURSEFORGE", "curseforge", "MODTALE", "LOCAL", ""})
    void providerBadgesFollowSourceAndKeepCompactModpackStyling(String source) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            var renderer = new LibraryWorldRenderer(null, null, null, null, null, null, null,
                    null, null, null, null, null, null);
            var projectCopy = LibraryWorldRenderer.class.getDeclaredMethod("projectCopy", LibraryWorldProjectModel.class);
            projectCopy.setAccessible(true);
            var contentRow = LibraryWorldRenderer.class.getDeclaredMethod("compactContentRow",
                    LibraryWorldContentItem.class, List.class);
            contentRow.setAccessible(true);
            for (String classification : List.of("PLUGIN", "MODPACK")) {
                var installed = new InstalledProject("example", "example", "Example", classification,
                        "1.0", "v1", "", null, null, List.of(), List.of(), List.of(), source,
                        "", false, List.of());
                var model = new LibraryWorldProjectModel(installed, null, null, null, false,
                        List.of(), 0, 0, List.of());
                Node project = (Node) projectCopy.invoke(renderer, model);
                var item = new LibraryWorldContentItem("child", "Included mod", "1.0", "PLUGIN",
                        "", "", List.of(), 0, 0, false, source);
                Node child = (Node) contentRow.invoke(renderer, item, List.of());
                VBox root = new VBox(project, child);
                Scene scene = new Scene(root);
                scene.getStylesheets().add(getClass().getResource(
                        "/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                root.applyCss();
                boolean curseForge = InstalledProject.SOURCE_CURSEFORGE.equalsIgnoreCase(source);
                assertEquals(curseForge ? 1 : 0, project.lookupAll(".library-badge-curseforge").size());
                assertEquals(curseForge ? 1 : 0, child.lookupAll(".library-badge-curseforge").size());
                assertEquals("Included", ((Label) child.lookup(".library-version-pill")).getText());
                Label packBadge = (Label) project.lookup(".library-badge-modpack");
                assertEquals(installed.isModpack(), packBadge != null);
                if (curseForge) {
                    for (Node entry : List.of(project, child)) {
                        Label badge = (Label) entry.lookup(".library-badge-curseforge");
                        assertEquals("CurseForge", badge.getText());
                        assertTrue(badge.getStyleClass().contains("library-badge"));
                        assertEquals(10, badge.getFont().getSize());
                        if (packBadge != null) {
                            assertEquals(packBadge.getPadding(), badge.getPadding());
                            assertEquals(packBadge.getBackground().getFills().getFirst().getRadii(),
                                    badge.getBackground().getFills().getFirst().getRadii());
                        }
                    }
                }
            }
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
