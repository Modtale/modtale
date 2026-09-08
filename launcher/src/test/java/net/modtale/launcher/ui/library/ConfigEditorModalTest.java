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
    void editsSettingsWithoutExposingFilesAndProtectsUnsavedChanges() throws Exception {
        Path config = directory.resolve("mods/com.azuredoom_levelingcore/levelingcore.json");
        Files.createDirectories(config.getParent());
        String original = """
                {"EnableDefaultXPGainSystem":true,"DefaultXPGainPercentage":0.5,
                 "StatsPerLevel":5,"EnableLevelRewardsConfig":true,
                 "EnableXPLossOnDeath":false,"XPLossPercentage":0.1,
                 "EnableStatLeveling":true,"HealthLevelUpMultiplier":2.2,
                 "ShowXPAmountInHUD":true,"LevelUpSound":"SFX_Divine_Respawn"}
                """;
        Files.writeString(config, original);
        var file = new ConfigFile(directory, config, "World mods / com.azuredoom:levelingcore / levelingcore.json", "com.azuredoom:levelingcore");
        StackPane host = fx(() -> {
            var root = new StackPane();
            net.modtale.launcher.ui.common.LauncherFonts.load();
            root.getStyleClass().add("app-root");
            var scene = new Scene(root, 1120, 800);
            scene.getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
            root.setStyle("-fx-background-color: #080f1b;");
            new ConfigEditorModal(root, Runnable::run, () -> {}).show(java.util.List.of(file), "LevelingCore");
            root.applyCss(); root.layout(); return root;
        });
        fx(() -> {
            host.applyCss(); host.layout();
            assertNull(find(host, TextArea.class)); assertNull(find(host, ListView.class));
            assertTrue(host.lookupAll(".label").stream().map(node -> ((javafx.scene.control.Label) node).getText())
                    .noneMatch(text -> text.contains(".json") || text.contains("com.azuredoom")));
            if (System.getenv("MODTALE_CONFIG_EDITOR_SNAPSHOT") != null) {
                var parameters = new javafx.scene.SnapshotParameters();
                parameters.setFill(javafx.scene.paint.Color.web("#0b1120"));
                var image = host.snapshot(parameters, null);
                var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
                    output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                javax.imageio.ImageIO.write(output, "png", Path.of(System.getenv("MODTALE_CONFIG_EDITOR_SNAPSHOT")).toFile());
            }
            var toggle = host.lookupAll(".library-toggle-box").stream().filter(node -> "Default XP gain system".equals(node.getAccessibleText())).findFirst().orElseThrow();
            toggle.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                    javafx.scene.input.KeyCode.SPACE, false, false, false, false));
            input(host, "Stats per level").setText("wrong");
            assertTrue(button(host, "Save changes").isDisabled());
            button(host, "Done").fire(); assertFalse(host.getChildren().isEmpty());
            input(host, "Stats per level").setText("8");
            button(host, "Display & sounds   2").fire();
            button(host, "Save changes").fire(); return null;
        });
        fx(() -> {
            var saved = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(config));
            assertEquals(8, saved.path("StatsPerLevel").asInt());
            assertFalse(saved.path("EnableDefaultXPGainSystem").asBoolean());
            assertEquals("SFX_Divine_Respawn", saved.path("LevelUpSound").asText());
            assertTrue(button(host, "Save changes").isDisabled());
            button(host, "All settings   10").fire();
            input(host, "Stats per level").setText("9");
            button(host, "Reset changes").fire();
            assertEquals("8", input(host, "Stats per level").getText());
            button(host, "Done").fire(); assertTrue(host.getChildren().isEmpty()); return null;
        });
        try (var backups = Files.list(config.getParent())) {
            assertTrue(backups.anyMatch(path -> path.toString().endsWith(".bak")));
        }
    }

    private static javafx.scene.control.TextField input(Parent root, String name) {
        return root.lookupAll(".text-field").stream().filter(javafx.scene.control.TextField.class::isInstance)
                .map(javafx.scene.control.TextField.class::cast).filter(field -> name.equals(field.getAccessibleText())).findFirst().orElseThrow();
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
