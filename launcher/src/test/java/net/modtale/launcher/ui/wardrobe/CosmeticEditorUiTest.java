package net.modtale.launcher.ui.wardrobe;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javax.imageio.ImageIO;
import net.modtale.launcher.hytale.HytaleAuthService;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.ui.common.LauncherFonts;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.wardrobe.CosmeticCatalogClient;
import net.modtale.launcher.wardrobe.CosmeticOption;
import net.modtale.launcher.wardrobe.WardrobeApiClient;
import net.modtale.launcher.wardrobe.WardrobeItem;
import net.modtale.launcher.wardrobe.WardrobeStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in integration of the real editor, installed catalog and local native renderer.
 * MODTALE_COSMETIC_EDITOR_SCREENSHOTS=~/Pictures WARDROBE_ASSETS_ZIP=/path/to/Assets.zip
 * ./gradlew test --tests '*CosmeticEditorUiTest' --rerun-tasks
 * Requires a graphical display/native3D. No saved launcher settings or account are read.
 * Outfit geometry/textures are local; the editor's catalog card thumbnails use public Hyvatar reads.
 */
@EnabledIfEnvironmentVariable(named = "MODTALE_COSMETIC_EDITOR_SCREENSHOTS", matches = ".+")
class CosmeticEditorUiTest {
    @TempDir Path directory;

    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        fx(() -> { Platform.setImplicitExit(false); LauncherFonts.load(); return null; });
    }

    @Test void editsRealInstalledCosmeticsAndCapturesLocalComposition() throws Exception {
        Path assets = assets();
        assertTrue(Files.isRegularFile(assets), "Set WARDROBE_ASSETS_ZIP to the installed Assets.zip");
        Path output = expand(System.getenv("MODTALE_COSMETIC_EDITOR_SCREENSHOTS"));
        Files.createDirectories(output);
        var catalog = new CosmeticCatalogClient(assets);
        var store = new WardrobeStore(directory);
        var settings = new LauncherSettings(); settings.setHytaleGamePath(assets.getParent().toString());
        assertNull(settings.getHytaleAuthSession());
        var gateway = new WardrobeApiClient(new HytaleAuthService(null, null) {
            @Override public String freshSessionToken(LauncherSettings value) { throw new AssertionError("No authentication in editor screenshots"); }
        }) {
            @Override public WardrobeItem hydrate(WardrobeItem item) { return item; }
            @Override public WardrobeItem currentSkin(LauncherSettings value) { throw new AssertionError("No account reads"); }
            @Override public void apply(WardrobeItem item, LauncherSettings value) { throw new AssertionError("No account writes"); }
            @Override public void apply(WardrobeItem item, LauncherSettings value, UUID target) { throw new AssertionError("No account writes"); }
        };
        try (var executor = Executors.newFixedThreadPool(4)) {
            Harness harness = fx(() -> {
                var feedback = new LauncherFeedback(executor, new Label(), new VBox(), new StackPane(), new Label(), new Label(), () -> "Local editor test");
                var controller = new CosmeticEditorController(gateway, store, () -> settings, feedback, executor);
                var scroll = new ScrollPane(controller.view()); scroll.setFitToWidth(true);
                scroll.setStyle("-fx-background: #0B1120; -fx-background-color: #0B1120; -fx-padding: 24;");
                var root = new StackPane(scroll); root.getStyleClass().add("app-root");
                var scene = new Scene(root, 1440, 1000);
                scene.getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                var stage = new Stage(StageStyle.UNDECORATED); stage.setTitle("Hytale cosmetic editor — local draft, no linked account");
                stage.setScene(scene); stage.show(); controller.refresh();
                return new Harness(controller, stage, scroll);
            });
            try {
                showAllCosmetics(harness);
                await("catalog/default draft", () -> harness.controller().draftSnapshot() != null && cards(harness).size() > 0);
                JsonNode baseline = fx(() -> harness.controller().draftSnapshot());
                assertEquals(catalog.defaultSkin(), baseline);
                awaitPreview(harness);
                assertTrue(fx(() -> button(harness.root(), "Apply").isDisabled()));
                assertFalse(fx(() -> button(harness.root(), "Save").isDisabled()));

                verifyPagination(harness, catalog);
                CosmeticOption hair = selectFirst(harness, catalog, "haircut");
                chooseColor(harness, catalog, hair, "Blond");
                JsonNode colored = fx(() -> harness.controller().draftSnapshot());
                assertNotEquals(baseline, colored);
                click(harness, "Undo");
                await("undo palette", () -> !harness.controller().draftSnapshot().equals(colored));
                click(harness, "Redo");
                await("redo palette", () -> harness.controller().draftSnapshot().equals(colored));
                click(harness, "Reset");
                await("reset draft", () -> harness.controller().draftSnapshot().equals(baseline));
                click(harness, "Undo");
                await("undo reset", () -> harness.controller().draftSnapshot().equals(colored));
                click(harness, "Redo");
                await("redo reset", () -> harness.controller().draftSnapshot().equals(baseline));
                CosmeticOption body = selectFirst(harness, catalog, "bodyCharacteristic");
                chooseColor(harness, catalog, body, "15");
                hair = selectFirst(harness, catalog, "haircut"); chooseColor(harness, catalog, hair, "Blond");
                CosmeticOption underwear = selectFirst(harness, catalog, "underwear"); chooseColor(harness, catalog, underwear, "Blue");
                CosmeticOption shirt = selectFirst(harness, catalog, "undertop"); chooseColor(harness, catalog, shirt, "Blue");
                selectFirst(harness, catalog, "pants"); selectFirst(harness, catalog, "shoes");
                CosmeticOption cape = selectFirst(harness, catalog, "cape");
                JsonNode complete = fx(() -> harness.controller().draftSnapshot());
                for (String key : List.of("bodyCharacteristic", "haircut", "underwear", "undertop", "pants", "shoes", "cape")) {
                    assertTrue(complete.path(key).isTextual(), key + " must be in the composed draft");
                    assertNotNull(catalog.resolve(key, complete.path(key).asText()));
                }
                assertEquals(cape.assetId(), complete.path("cape").asText().split("\\.")[0]);
                awaitPreview(harness);
                // A final local render must represent the complete selected outfit, not a remote NPC.
                assertEquals(complete, fx(() -> harness.controller().draftSnapshot()));
                assertTrue(fx(() -> nodes(harness.root(), javafx.scene.SubScene.class).size() == 1));
                capture(harness, output, "cape", 1440, 1000);
                capture(harness, output, "cape", 1000, 900);
                openCategory(harness, catalog, "haircut"); awaitPreview(harness);
                capture(harness, output, "hair", 1440, 1000);
                capture(harness, output, "hair", 1000, 900);
                assertEquals(complete, fx(() -> harness.controller().draftSnapshot()), "Changing category must preserve the composition");
                captureIntegrated(harness, gateway, store, settings, executor, catalog, complete, output);
                assertTrue(store.items().isEmpty(), "Editing alone must not write a saved outfit");
                assertNull(settings.getHytaleAuthSession());
                Files.writeString(output.resolve("cosmetic-editor-provenance.txt"), "Actual CosmeticEditorController with installed catalog " + assets
                        + "\nReal local outfit geometry and textures; no GLB/NPC substitute and no account/session/settings-file reads.\n"
                        + "Catalog card thumbnails are public Hyvatar reads.\nVerified category choices, palette changes, undo/redo/reset and draft preservation.\n"
                        + "Draft (not applied or saved): " + complete + "\n");
            } finally {
                fx(() -> { harness.controller().close(); harness.stage().close(); return null; });
                executor.shutdownNow();
            }
        }
    }

    private static void verifyPagination(Harness harness, CosmeticCatalogClient catalog) throws Exception {
        openCategory(harness, catalog, "haircut");
        int total = catalog.browseAssets("haircut", "", 1, 100).total();
        await("four full rows", () -> {
            var grid = (javafx.scene.layout.GridPane) harness.root().lookup("#cosmetic-cards");
            return cards(harness).size() == Math.min(total, grid.getColumnConstraints().size() * 4);
        });
        fx(() -> {
            var grid = (javafx.scene.layout.GridPane) harness.root().lookup("#cosmetic-cards");
            harness.root().applyCss(); harness.root().layout();
            int columns = grid.getColumnConstraints().size();
            var last = cards(harness).get(columns - 1);
            assertEquals(grid.getWidth(), last.getBoundsInParent().getMaxX(), 2, "Cards fill the results width");
            Node pagination = harness.root().lookup("#cosmetic-pagination");
            assertEquals(total > columns * 4, pagination.isVisible());
            assertTrue(pagination.getStyleClass().contains("pagination-nav"));
            return null;
        });
        String first = fx(() -> cards(harness).getFirst().getAccessibleText());
        click(harness, "Next Page");
        await("next cosmetic page", () -> !cards(harness).getFirst().getAccessibleText().equals(first));
        click(harness, "Previous Page");
        await("previous cosmetic page", () -> cards(harness).getFirst().getAccessibleText().equals(first));
        openCategory(harness, catalog, "ears");
        assertFalse(fx(() -> harness.root().lookup("#cosmetic-pagination").isManaged()), "Single page has no pagination");
        openCategory(harness, catalog, "haircut");
    }

    private static void showAllCosmetics(Harness harness) throws Exception {
        fx(() -> {
            CheckBox owned = (CheckBox) button(harness.root(), "Owned only");
            assertTrue(owned.isSelected(), "Owned only defaults on");
            for (String action : List.of("Undo", "Redo")) {
                Button control = (Button) button(harness.root(), action);
                assertEquals("", control.getText());
                assertNotNull(control.getGraphic());
                assertEquals(action, control.getTooltip().getText());
                assertSame(control.getParent(), owned.getParent());
            }
            owned.fire(); // This unlinked harness explicitly browses the complete installed catalog.
            return null;
        });
    }

    private static void captureIntegrated(Harness standalone, WardrobeApiClient gateway, WardrobeStore store,
            LauncherSettings settings, java.util.concurrent.Executor executor, CosmeticCatalogClient catalog,
            JsonNode composition, Path output) throws Exception {
        LauncherWardrobeController wardrobe = fx(() -> {
            var feedback = new LauncherFeedback(executor, new Label(), new VBox(), new StackPane(), new Label(), new Label(), () -> "Local editor test");
            var controller = new LauncherWardrobeController(gateway, store, () -> settings, feedback, executor);
            standalone.scroll().setContent(controller.view());
            button(standalone.root(), "Customize").fire();
            return controller;
        });
        try {
            var hero = new Harness(wardrobe.editorForTesting(), standalone.stage(), standalone.scroll());
            showAllCosmetics(hero);
            await("integrated catalog", () -> hero.controller().draftSnapshot().hasNonNull("bodyCharacteristic") && !cards(hero).isEmpty());
            var payload = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().set("skin", composition);
            fx(() -> { hero.controller().edit(new WardrobeItem(UUID.randomUUID(), WardrobeItem.Kind.SKIN,
                    "Local composition example", false, "", payload.toString())); return null; });
            await("integrated composition", () -> hero.controller().draftSnapshot().equals(composition));
            openCategory(hero, catalog, "haircut"); awaitPreview(hero);
            fx(() -> {
                var animations = (javafx.scene.control.ComboBox<?>) hero.root().lookup("#wardrobe-preview-animation");
                assertEquals("Idle", animations.getValue().toString());
                int walk = java.util.stream.IntStream.range(0, animations.getItems().size())
                        .filter(index -> animations.getItems().get(index).toString().equals("Walk")).findFirst().orElseThrow();
                animations.getSelectionModel().select(walk);
                return null;
            });
            awaitPreview(hero);
            fx(() -> {
                assertNull(hero.root().lookup("#wardrobe-preview-pause"));
                var animations = (javafx.scene.control.ComboBox<?>) hero.root().lookup("#wardrobe-preview-animation");
                button(hero.root(), "Reset view").fire();
                assertEquals("Idle", animations.getValue().toString());
                assertEquals(composition, hero.controller().draftSnapshot(), "Preview motion cannot change the outfit");
                return null;
            });
            awaitPreview(hero);
            assertNotNull(button(hero.root(), "Saved looks"));
            capture(hero, output, "customize", 1440, 1000);
            capture(hero, output, "customize", 1000, 900);
            openCategory(hero, catalog, "pants"); awaitPreview(hero);
            capture(hero, output, "customize-pants", 1000, 900);
            openCategory(hero, catalog, "cape"); awaitPreview(hero);
            capture(hero, output, "customize-cape", 1440, 1000);
            capture(hero, output, "customize-cape", 1000, 900);
            assertEquals(composition, fx(() -> hero.controller().draftSnapshot()));
            FutureTask<Void> saveDialog = new FutureTask<>(() -> { button(hero.root(), "Save").fire(); return null; });
            Platform.runLater(saveDialog);
            await("save modal", () -> hero.stage().getScene().lookup(".status-modal-primary") != null);
            try {
                capture(hero, output, "save", 1440, 1000);
                capture(hero, output, "save", 1000, 900);
            } finally {
                fx(() -> { ((Button) hero.stage().getScene().lookup(".status-modal-secondary")).fire(); return null; });
                saveDialog.get(5, TimeUnit.SECONDS);
            }
        } finally { fx(() -> { wardrobe.close(); return null; }); }
    }

    private static CosmeticOption selectFirst(Harness harness, CosmeticCatalogClient catalog, String category) throws Exception {
        openCategory(harness, catalog, category);
        CosmeticOption option = catalog.browseAssets(category, "", 1, 12).options().getFirst();
        click(harness, "Choose " + option.label());
        await("selection " + option.id(), () -> harness.controller().draftSnapshot().path(category).asText().equals(option.id()));
        await("palette options", () -> nodes(harness.root(), Button.class).stream().anyMatch(button -> button.getStyleClass().contains("cosmetic-swatch")));
        return option;
    }

    private static void openCategory(Harness harness, CosmeticCatalogClient catalog, String category) throws Exception {
        String label = catalog.categories().stream().filter(value -> value.key().equals(category)).findFirst().orElseThrow().label();
        var before = fx(() -> harness.controller().draftSnapshot());
        click(harness, CosmeticFraming.forCategory(category).group());
        fx(() -> {
            var choice = nodes(harness.root(), Button.class).stream().filter(button -> button.getStyleClass().contains("cosmetic-category")
                    && button.getText().equals(label)).findFirst().orElseThrow();
            assertTrue(choice.getParent().isVisible(), "Group must reveal the selected submenu");
            choice.fire(); return null;
        });
        assertEquals(before, fx(() -> harness.controller().draftSnapshot()), "Navigation must preserve the outfit");
        String expected = "Choose " + catalog.browseAssets(category, "", 1, 12).options().getFirst().label();
        await("category " + category, () -> cards(harness).stream().anyMatch(button -> expected.equals(button.getAccessibleText()))
                && nodes(harness.root(), Label.class).stream().noneMatch(node -> node.getText().equals("Loading cosmetics…")));
    }

    private static void chooseColor(Harness harness, CosmeticCatalogClient catalog, CosmeticOption asset, String preferred) throws Exception {
        var options = catalog.options(asset.category(), asset.assetId());
        String current = fx(() -> harness.controller().draftSnapshot().path(asset.category()).asText());
        var choice = options.stream().filter(option -> option.colorId().equals(preferred)).findFirst()
                .orElseGet(() -> options.stream().filter(option -> !option.id().equals(current)).findFirst().orElseThrow());
        await("target palette", () -> nodes(harness.root(), Button.class).stream().anyMatch(button -> ("Color " + choice.colorId()).equals(button.getAccessibleText())));
        awaitPreview(harness);
        await("palette ready", () -> !button(harness.root(), "Color " + choice.colorId()).isDisabled());
        fx(() -> {
            var before = nodes(harness.root(), javafx.scene.SubScene.class).getFirst();
            var swatch = button(harness.root(), "Color " + choice.colorId());
            var palette = swatch.getParent();
            swatch.fire();
            assertSame(before, nodes(harness.root(), javafx.scene.SubScene.class).getFirst(),
                    "Keep the current avatar while its replacement loads");
            assertTrue(palette.isVisible() && palette.isManaged(), "Palette must not collapse during selection");
            return null;
        });
        await("color applied", () -> harness.controller().draftSnapshot().path(asset.category()).asText().equals(choice.id()));
    }

    private static void awaitPreview(Harness harness) throws Exception {
        await("local native outfit preview", () -> ((Label) harness.root().lookup("#wardrobe-preview-status")).getText().startsWith("Local outfit preview"));
    }

    private static void capture(Harness harness, Path output, String name, int width, int height) throws Exception {
        fx(() -> { harness.stage().setWidth(width); harness.stage().setHeight(height); harness.scroll().setVvalue(0); harness.root().applyCss(); harness.root().layout(); return null; });
        await("resize", () -> Math.round(harness.stage().getScene().getWidth()) == width && Math.round(harness.stage().getScene().getHeight()) == height);
        Thread.sleep(250); // Allow the 150 ms resize debounce to schedule its catalog replacement.
        await("responsive page settled", () -> {
            var grid = (javafx.scene.layout.GridPane) harness.root().lookup("#cosmetic-cards");
            return !cards(harness).isEmpty() && cards(harness).size() <= grid.getColumnConstraints().size() * 4
                    && nodes(harness.root(), Label.class).stream().noneMatch(label -> label.getText().equals("Loading cosmetics…"));
        });
        await("catalog thumbnail response", () -> cards(harness).stream().allMatch(card -> nodes(card, ImageView.class).stream()
                .allMatch(view -> view.getImage() != null && view.getImage().getProgress() == 1)));
        WritableImage image = fx(() -> {
            harness.root().applyCss(); harness.root().layout();
            assertNull(harness.root().lookup("#wardrobe-preview-pause"));
            return harness.stage().getScene().snapshot(null);
        });
        var outputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        outputImage.setRGB(0, 0, width, height, pixels, 0, width);
        Path path = output.resolve("launcher-cosmetic-editor-" + name + (width == 1440 ? "-desktop.png" : "-compact.png"));
        assertTrue(ImageIO.write(outputImage, "png", path.toFile()));
        System.out.println("Cosmetic editor screenshot: " + path);
    }

    private static Path assets() {
        String env = System.getenv("WARDROBE_ASSETS_ZIP");
        return env == null || env.isBlank() ? Path.of(System.getProperty("user.home"), ".var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest/Assets.zip") : expand(env);
    }
    private static Path expand(String value) { return Path.of(value.startsWith("~/") ? System.getProperty("user.home") + value.substring(1) : value).toAbsolutePath(); }
    private static void click(Harness harness, String text) throws Exception { fx(() -> { var button = button(harness.root(), text); assertFalse(button.isDisabled(), text); button.fire(); return null; }); }
    private static ButtonBase button(Node root, String text) { return nodes(root, ButtonBase.class).stream().filter(button -> text.equals(button.getText()) || text.equals(button.getAccessibleText())).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + text)); }
    private static List<Button> cards(Harness harness) { return nodes(harness.root(), Button.class).stream().filter(button -> button.getStyleClass().contains("wardrobe-card")).toList(); }
    private static <T> List<T> nodes(Node root, Class<T> type) { var result = new ArrayList<T>(); if (type.isInstance(root)) result.add(type.cast(root)); if (root instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) result.addAll(nodes(child, type)); return result; }
    private static void await(String description, Callable<Boolean> condition) throws Exception { long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(40); while (System.nanoTime() < end) { if (fx(condition)) return; Thread.sleep(25); } fail("Timed out: " + description); }
    private static <T> T fx(Callable<T> work) throws Exception { if (Platform.isFxApplicationThread()) return work.call(); var task = new FutureTask<T>(work); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS); }
    private record Harness(CosmeticEditorController controller, Stage stage, ScrollPane scroll) { Parent root() { return stage.getScene().getRoot(); } }
}
