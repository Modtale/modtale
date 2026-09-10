package net.modtale.launcher.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.ui.browse.ProjectBrowseController;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;
import net.modtale.launcher.ui.browse.controls.ProjectBrowseSort;
import net.modtale.launcher.ui.common.LauncherScrollSupport;
import net.modtale.launcher.ui.common.LauncherView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".+")
class LauncherBrowseMenuTest {
    @BeforeAll
    static void toolkit() throws Exception {
        var started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    void curseForgeShortcutsNavigateAndSearchWithoutAComboBoxSkin() throws Exception {
        var test = new FutureTask<Void>(() -> {
            Platform.setImplicitExit(false);
            var currentView = new AtomicReference<>(LauncherView.WARDROBE);
            var searches = new ArrayList<String>();
            var layer = new StackPane();
            var controller = new ProjectBrowseController(
                    null, job -> {}, new ProjectCardFactory(value -> value, job -> {}),
                    layer, VBox::new, new LauncherScrollSupport(() -> layer),
                    () -> {}, searches::add, () -> "Ready", message -> {}, (title, message) -> {},
                    () -> currentView.set(LauncherView.DISCOVER), currentView::get,
                    id -> false, () -> "2026.1", project -> {}, project -> {}, project -> {}, project -> {});
            var source = (MenuButton) controller.view().lookup(".provider-picker");
            source.getItems().stream().filter(item -> item.getText().equals("CurseForge"))
                    .findFirst().orElseThrow().fire();
            var menu = new LauncherBrowseMenu(controller, () -> layer, currentView::get);
            assertEquals(ProjectBrowseSort.DOWNLOADS, controller.selectedBrowseSort());
            for (var sort : ProjectBrowseSort.curseForgeSorts()) {
                currentView.set(LauncherView.WARDROBE);
                var item = (Button) menu.panel().getChildren().stream()
                        .filter(node -> node instanceof Button button && button.getText().equals(sort.curseForgeLabel()))
                        .findFirst().orElseThrow();
                item.fire();
                assertEquals(LauncherView.DISCOVER, currentView.get());
                assertEquals(sort, controller.selectedBrowseSort());
                assertEquals(sort.browseView(), controller.activeBrowseView());
                assertTrue(controller.isCurseForgeSource());
            }
            assertEquals(7, searches.stream().filter(s -> s.equals("Searching CurseForge projects...")).count());
            return null;
        });
        Platform.runLater(test);
        test.get(20, TimeUnit.SECONDS);
    }
}
