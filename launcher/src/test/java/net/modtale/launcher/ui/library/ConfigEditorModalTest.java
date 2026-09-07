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

    private static Button button(Parent root, String text) {
        if (root instanceof Button button && button.getText().equals(text)) return button;
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
