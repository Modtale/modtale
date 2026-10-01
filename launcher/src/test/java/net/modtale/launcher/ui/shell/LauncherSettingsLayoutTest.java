package net.modtale.launcher.ui.shell;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.settings.SettingsStore;
import net.modtale.launcher.ui.common.LauncherLayout;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.ui.settings.LauncherSettingsController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherSettingsLayoutTest {
    @TempDir Path directory;

    @BeforeAll static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { }
    }

    @Test void categoriesStayWithinNavbarInsetsAfterCssAndInteraction() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            var controller = new LauncherSettingsController(
                    new SettingsStore(directory.resolve("settings.json")),
                    new ModtaleApiClient("http://localhost", directory.resolve("session.json")),
                    () -> null, () -> LauncherView.SETTINGS);
            var view = controller.view();
            var deck = new StackPane(view);
            deck.setMinWidth(0);
            var body = new VBox(deck);
            body.getStyleClass().add("body");
            body.setMinWidth(0);
            body.setPadding(LauncherShell.contentBodyInsetsFor(LauncherView.SETTINGS));
            var scroll = new ScrollPane(body);
            scroll.getStyleClass().add("content-scroll");
            scroll.setFitToWidth(true);
            scroll.setFitToHeight(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            var content = new VBox(scroll);
            content.setMinWidth(0);
            VBox.setVgrow(scroll, Priority.ALWAYS);
            HBox.setHgrow(content, Priority.ALWAYS);
            var workspace = new HBox(content);
            workspace.setPadding(LauncherShell.workspaceInsetsFor(LauncherView.SETTINGS));
            var host = new StackPane(workspace);
            host.getStyleClass().add("app-root");
            var scene = new Scene(host, 1500, 1000);
            scene.getStylesheets().add(getClass().getResource(
                    "/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
            for (int width : new int[] {1280, 1920, 1500}) {
                host.resize(width, 1000);
                host.applyCss();
                host.layout();
                var categories = view.lookupAll(".settings-category").stream()
                        .map(ToggleButton.class::cast).toList();
                for (int round = 0; round < 2; round++) {
                    for (var category : categories) {
                        category.fire();
                        category.requestFocus();
                        for (int pass = 0; pass < 5; pass++) {
                            host.applyCss();
                            host.layout();
                        }
                        for (String selector : new String[] {".settings-detail", ".settings-save-actions"}) {
                            var node = view.lookup(selector);
                            var bounds = node.localToScene(node.getLayoutBounds());
                            assertTrue(bounds.getMaxX() <= width - LauncherLayout.navbarRightInset() + 1,
                                    selector + " overflows after selecting " + category.getText());
                            assertTrue(bounds.getMinX() >= LauncherLayout.navbarLeftInset() - 1);
                        }
                    }
                }
            }
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }

    @Test void maintenanceCardsCanScrollIntoViewInShortWindow() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            var controller = new LauncherSettingsController(
                    new SettingsStore(directory.resolve("short-window-settings.json")),
                    new ModtaleApiClient("http://localhost", directory.resolve("short-window-session.json")),
                    () -> null, () -> LauncherView.SETTINGS);
            var view = controller.view();
            var deck = new StackPane(view);
            deck.setMinHeight(Region.USE_PREF_SIZE);
            var body = new VBox(deck);
            body.setPadding(LauncherShell.contentBodyInsetsFor(LauncherView.SETTINGS));
            body.setMinHeight(Region.USE_PREF_SIZE);
            var scroll = new ScrollPane(body);
            scroll.setFitToWidth(true);
            scroll.setFitToHeight(!LauncherShell.usesDocumentHeight(LauncherView.SETTINGS));
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            var host = new StackPane(scroll);
            host.getStyleClass().add("app-root");
            var scene = new Scene(host, 900, 600);
            scene.getStylesheets().add(getClass().getResource(
                    "/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());

            var maintenance = view.lookupAll(".settings-category").stream()
                    .map(ToggleButton.class::cast)
                    .filter(button -> button.getText().equals("Launcher Maintenance"))
                    .findFirst().orElseThrow();
            maintenance.fire();
            for (int pass = 0; pass < 5; pass++) {
                host.applyCss();
                host.layout();
            }
            assertTrue(body.getLayoutBounds().getHeight() > scroll.getViewportBounds().getHeight());
            scroll.setVvalue(scroll.getVmax());
            host.layout();
            var cache = view.lookupAll(".settings-action-card").stream()
                    .filter(card -> card.lookup(".btn") != null
                            && card.lookup(".btn") instanceof javafx.scene.control.Button button
                            && button.getText().equals("Clear Cache"))
                    .findFirst().orElseThrow();
            var cacheBounds = cache.localToScene(cache.getLayoutBounds());
            var viewport = scroll.lookup(".viewport");
            var viewportBounds = viewport.localToScene(viewport.getLayoutBounds());
            assertTrue(cacheBounds.getMaxY() <= viewportBounds.getMaxY() + 1,
                    "Cache card should be reachable by scrolling to the bottom");
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
