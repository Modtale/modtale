package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.VBox;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.project.ProjectMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LibraryCardInteractionTest {
    @BeforeAll
    static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { }
    }

    @Test
    void providerRoutesPreserveIdentityAndRejectLocalFiles() {
        var modtale = LibraryWorldRenderer.navigationTarget(model("project-id", "MODTALE"));
        assertEquals("provider-slug", modtale.routeKey());
        assertEquals("Creator", modtale.author());
        assertFalse(modtale.isCurseForge());
        for (String id : List.of("123", "curseforge:123")) {
            var cf = LibraryWorldRenderer.navigationTarget(model(id, "CURSEFORGE"));
            assertEquals("curseforge:123", cf.routeKey());
            assertEquals(123, cf.curseForgeProjectId());
            assertEquals("Creator", cf.author());
        }
        assertNull(LibraryWorldRenderer.navigationTarget(model("local:file", "")));
        assertNull(LibraryWorldRenderer.navigationTarget(model("file", "LOCAL")));
        assertNull(LibraryWorldRenderer.navigationTarget(model("", "MODTALE")));
        assertNull(LibraryWorldRenderer.navigationTarget(model("file", "OTHER")));
    }

    @Test
    void cardAndAuthorRouteSeparatelyAndNestedActionsStayInLibrary() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            AtomicInteger pages = new AtomicInteger();
            AtomicInteger creators = new AtomicInteger();
            AtomicInteger deletes = new AtomicInteger();
            AtomicInteger versions = new AtomicInteger();
            var renderer = new LibraryWorldRenderer(null, ignored -> {}, ignored -> versions.incrementAndGet(),
                    null, ignored -> deletes.incrementAndGet(), null, ignored -> {}, null,
                    null, null, null, null, null);
            renderer.setNavigationActions(ignored -> pages.incrementAndGet(), ignored -> creators.incrementAndGet());
            var method = LibraryWorldRenderer.class.getDeclaredMethod("projectRow", HytaleWorld.class,
                    LibraryWorldProjectModel.class, List.class);
            method.setAccessible(true);
            VBox card = (VBox) method.invoke(renderer, null, model("id", "MODTALE"), List.of());
            assertNotNull(card.getOnMouseEntered());
            assertNotNull(card.getOnMouseExited());
            click(card.lookup(".library-world-project-title"), MouseButton.PRIMARY);
            assertEquals(1, pages.get());
            click(card.lookup(".author-link"), MouseButton.PRIMARY);
            assertEquals(1, creators.get());
            assertEquals(1, pages.get());
            click(card, MouseButton.SECONDARY);
            assertEquals(1, pages.get());
            for (Node button : card.lookupAll(".library-icon-action")) {
                assertTrue(LibraryWorldRenderer.isNestedControl(button, card));
                click(button, MouseButton.PRIMARY);
                ((Button) button).fire();
            }
            assertEquals(1, deletes.get());
            assertEquals(1, versions.get());
            assertEquals(1, pages.get());
            var toggle = (LibraryToggleBox) card.lookup(".library-toggle-box");
            assertTrue(LibraryWorldRenderer.isNestedControl(toggle, card));
            AtomicInteger toggles = new AtomicInteger();
            toggle.setDisable(false);
            toggle.setOnAction(toggles::incrementAndGet);
            click(toggle, MouseButton.PRIMARY);
            assertEquals(1, toggles.get());
            assertEquals(1, pages.get());
            ComboBox<String> combo = new ComboBox<>();
            VBox controls = new VBox(combo);
            controls.getStyleClass().add("library-world-version-row");
            VBox contents = new VBox();
            contents.getStyleClass().add("library-world-content-card");
            card.getChildren().addAll(controls, contents);
            for (Node nested : List.of(combo, controls, contents)) {
                click(nested, MouseButton.PRIMARY);
                assertEquals(1, pages.get());
            }
            VBox local = (VBox) method.invoke(renderer, null, model("local:file", "LOCAL"), List.of());
            assertNull(local.lookup(".author-link"));
            click(local.lookup(".library-world-project-title"), MouseButton.PRIMARY);
            assertEquals(1, pages.get());
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }

    private static void click(Node node, MouseButton button) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, button, 1,
                false, false, false, false, false, false, false, false, false, true,
                new PickResult(node, 0, 0)));
    }

    private static LibraryWorldProjectModel model(String id, String source) {
        var installed = new InstalledProject(id, "installed-slug", "Example", "PLUGIN",
                "1.0", "v1", "", null, null, List.of(), List.of(), List.of(), source,
                "", false, List.of());
        var meta = new ProjectMeta("Example", "Description", "", "Creator", "PLUGIN", 0, "", "provider-slug");
        return new LibraryWorldProjectModel(installed, null, meta, null, false, List.of(), 0, 0, List.of());
    }
}
