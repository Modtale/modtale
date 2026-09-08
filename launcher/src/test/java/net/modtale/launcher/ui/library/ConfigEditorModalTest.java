package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.config.HytaleConfigFiles.ConfigFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigEditorModalTest {
    @TempDir Path directory;

    @BeforeAll
    static void toolkit() throws Exception {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { /* Shared toolkit. */ }
        catch (UnsupportedOperationException unavailable) {
            org.junit.jupiter.api.Assumptions.abort("JavaFX display unavailable: " + unavailable.getMessage());
        }
    }

    @Test
    void editsSavesAndProtectsUnsavedChanges() throws Exception {
        Path world = directory.resolve("Saves/My World");
        Path config = world.resolve("mods/Example_Plugin/config.json");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "{\"enabled\":true}");
        StackPane host = fx(() -> {
            StackPane root = new StackPane();
            Scene scene = new Scene(root, 1100, 750);
            scene.getStylesheets().add(ConfigEditorModal.class.getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
            new ConfigEditorModal(root, Runnable::run, () -> {}).show(directory.resolve("Mods"), world, "My World");
            root.applyCss();
            root.layout();
            return root;
        });
        fx(() -> {
            ListView<ConfigFile> list = find(host, ListView.class);
            assertEquals(1, list.getItems().size());
            list.getSelectionModel().selectFirst();
            return null;
        });
        fx(() -> {
            TextArea editor = find(host, TextArea.class);
            assertEquals("{\"enabled\":true}", editor.getText());
            if (System.getenv("MODTALE_CONFIG_EDITOR_SNAPSHOT") != null) {
                host.applyCss();
                host.layout();
                var image = host.snapshot(null, null);
                var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < output.getHeight(); y++) {
                    for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
                javax.imageio.ImageIO.write(output, "png", Path.of(System.getenv("MODTALE_CONFIG_EDITOR_SNAPSHOT")).toFile());
            }
            editor.setText("{broken");
            button(host, "Close").fire();
            assertFalse(host.getChildren().isEmpty());
            assertTrue(find(host, ListView.class).isDisabled());
            button(host, "Save").fire();
            return null;
        });
        fx(() -> {
            assertEquals("{\"enabled\":true}", Files.readString(config));
            assertFalse(button(host, "Discard edits").isDisabled());
            find(host, TextArea.class).setText("{\"enabled\":false}");
            button(host, "Save").fire();
            return null;
        });
        fx(() -> {
            assertEquals("{\"enabled\":false}", Files.readString(config));
            assertTrue(button(host, "Save").isDisabled());
            assertFalse(find(host, ListView.class).isDisabled());
            find(host, TextArea.class).setText("unsaved");
            button(host, "Discard edits").fire();
            assertEquals("{\"enabled\":false}", find(host, TextArea.class).getText());
            button(host, "Close").fire();
            assertTrue(host.getChildren().isEmpty());
            return null;
        });
    }

    @Test
    void sharingOnlyIncludesExplicitlySelectedConfigs() throws Exception {
        fx(() -> {
            StackPane host = new StackPane();
            new Scene(host, 900, 700);
            var file = new ConfigFile(directory, directory.resolve("Example/config.json"), "World mods / Example/config.json");
            var result = new java.util.concurrent.atomic.AtomicReference<java.util.List<ConfigFile>>();
            ShareConfigSelectionModal.show(host, java.util.List.of(file), result::set);
            host.applyCss();
            host.layout();
            var choice = find(host, javafx.scene.control.CheckBox.class);
            assertFalse(choice.isSelected());
            button(host, "Create shared list").fire();
            assertEquals(java.util.List.of(), result.get());
            ShareConfigSelectionModal.show(host, java.util.List.of(file), result::set);
            host.applyCss();
            host.layout();
            find(host, javafx.scene.control.CheckBox.class).setSelected(true);
            button(host, "Create shared list").fire();
            assertEquals(java.util.List.of(file), result.get());
            assertTrue(host.getChildren().isEmpty());
            return null;
        });
    }

    @Test
    void modButtonsRequireAttributedConfigsAndKeepBundledModsSeparate() throws Exception {
        fx(() -> {
            var received = new java.util.concurrent.atomic.AtomicReference<java.util.List<ConfigFile>>();
            var renderer = new LibraryWorldRenderer(null, ignored -> {}, ignored -> {}, (installed, detail, version) -> {},
                    ignored -> {}, ignored -> {}, ignored -> {}, (world, ids, enabled) -> {},
                    ignored -> {}, ignored -> {}, (title, files) -> received.set(files), () -> {}, () -> {});
            var world = new net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld(directory, "World",
                    directory.resolve("config.json"), "", "", 0, 0, java.time.Instant.EPOCH);
            var mod = installed("First mod", "PLUGIN");
            var pack = installed("Pack", "MODPACK");
            var child = new LibraryWorldContentItem("child", "Bundled mod", "", "PLUGIN", "", "",
                    java.util.List.of("Author:Second"), 0, 1, true);
            var model = new LibraryWorldModel(world, "", 0, 2, java.util.List.of(
                    new LibraryWorldProjectModel(mod, null, null, null, false, java.util.List.of("Author:First"), 0, 1, java.util.List.of()),
                    new LibraryWorldProjectModel(pack, null, null, null, false, java.util.List.of("Author:Second"), 0, 1, java.util.List.of(child))));
            var root = new javafx.scene.layout.VBox();
            new Scene(root, 1200, 800);
            root.getChildren().setAll(renderer.worldDetail(model));
            root.applyCss(); root.layout();
            assertNull(button(root, "Config"));
            assertNull(button(root, "Configs"));
            var first = new ConfigFile(directory, directory.resolve("Author_First/config.json"), "First", "Author:First");
            var second = new ConfigFile(directory, directory.resolve("Author_Second/config.json"), "Second", "Author:Second");
            var unknown = new ConfigFile(directory, directory.resolve("Custom/config.json"), "Unattributed");
            root.getChildren().setAll(renderer.worldDetail(model, java.util.List.of(first, second, unknown)));
            root.applyCss(); root.layout();
            var buttons = root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b -> "Config".equals(b.getText())).toList();
            assertEquals(2, buttons.size());
            buttons.stream().filter(b -> "Config for First mod".equals(b.getAccessibleText())).findFirst().orElseThrow().fire();
            assertEquals(java.util.List.of(first), received.get());
            buttons.stream().filter(b -> "Config for Bundled mod".equals(b.getAccessibleText())).findFirst().orElseThrow().fire();
            assertEquals(java.util.List.of(second), received.get());
            root.getChildren().setAll(renderer.worldDetail(model, java.util.List.of(unknown)));
            assertNull(button(root, "Config"));
            return null;
        });
    }

    @Test
    void modEditorShowsOnlyTheFilesPassedFromItsButton() throws Exception {
        var selected = new ConfigFile(directory, directory.resolve("Author_First/config.json"), "First", "Author:First");
        Path other = directory.resolve("Author_Second/config.json");
        Files.createDirectories(other.getParent());
        Files.writeString(other, "{}");
        StackPane host = fx(() -> {
            var root = new StackPane();
            new Scene(root, 1000, 700);
            new ConfigEditorModal(root, Runnable::run, () -> {}).show(java.util.List.of(selected), "First mod");
            return root;
        });
        fx(() -> {
            host.applyCss(); host.layout();
            assertEquals(java.util.List.of(selected), find(host, ListView.class).getItems());
            button(host, "Close").fire();
            return null;
        });
    }

    private static net.modtale.launcher.model.install.InstalledProject installed(String title, String classification) {
        return new net.modtale.launcher.model.install.InstalledProject(title, title, title, classification,
                "1", "", "", java.time.Instant.EPOCH, java.time.Instant.EPOCH, java.util.List.of(), java.util.List.of(), java.util.List.of());
    }

    private static Button button(Parent root, String text) {
        if (root instanceof Button button && text.equals(button.getText())) return button;
        for (Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof Parent parent) {
                Button found = button(parent, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T find(Parent root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        for (Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof Parent parent) {
                T found = find(parent, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
