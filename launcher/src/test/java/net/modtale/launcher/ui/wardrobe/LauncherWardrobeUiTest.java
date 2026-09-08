package net.modtale.launcher.ui.wardrobe;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javax.imageio.ImageIO;
import net.modtale.launcher.hytale.HytaleAuthService;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.ui.common.LauncherFonts;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.wardrobe.WardrobeApiClient;
import net.modtale.launcher.wardrobe.WardrobeItem;
import net.modtale.launcher.wardrobe.WardrobeStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in real JavaFX controller interaction/capture harness. Run from launcher:
 * MODTALE_WARDROBE_SCREENSHOTS=~/Pictures ./gradlew test --tests '*LauncherWardrobeUiTest' --rerun-tasks
 * MODTALE_WARDROBE_LIVE_SCREENSHOTS=~/Pictures ./gradlew test --tests '*LauncherWardrobeUiTest' --rerun-tasks
 * A graphical display (or Xvfb) is required. The environment variable is an output directory,
 * not a boolean. A leading ~/ is expanded explicitly, including when the value was quoted.
 * The deterministic mode uses fictional names, IDs, and schematic local artwork.
 * The separate live mode uses public HyTags/Hyvatar reads and records source/load results.
 * Neither mode uses an account, credentials, settings files, or remote writes.
 * Exercises native dialogs and asynchronous UI updates, then captures the actual controller
 * hosted in a real shown Stage using the launcher's stylesheet.
 */
class LauncherWardrobeUiTest {
    @TempDir Path directory;
    @BeforeAll static void startFx() throws Exception {
        if (System.getenv().getOrDefault("MODTALE_WARDROBE_SCREENSHOTS", "").isBlank()
                && System.getenv().getOrDefault("MODTALE_WARDROBE_LIVE_SCREENSHOTS", "").isBlank()) return;
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS), "JavaFX startup timed out");
        fx(() -> { Platform.setImplicitExit(false); LauncherFonts.load(); return null; });
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MODTALE_WARDROBE_SCREENSHOTS", matches = ".+")
    void nativeWardrobeInteractionsAndScreenshots() throws Exception {
        Path output = outputDirectory();
        Files.createDirectories(output);
        FixtureGateway gateway = new FixtureGateway();
        WardrobeStore store = new WardrobeStore(directory.resolve("state"));
        store.saveItem(gateway.skins.get(1));
        store.saveItem(gateway.skins.get(2));
        store.saveItem(gateway.capeItems.get(1));
        Map<UUID, Path> thumbnails = new java.util.HashMap<>();
        for (WardrobeItem item : gateway.allItems()) {
            Path image = directory.resolve(item.id() + ".png");
            Files.write(image, fixturePng(item.kind() == WardrobeItem.Kind.CAPE, item.id().hashCode()));
            thumbnails.put(item.id(), image);
        }
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            Harness harness = fx(() -> {
                var feedback = new LauncherFeedback(executor, new Label(), new VBox(), new StackPane(),
                        new Label(), new Label(), () -> "Fixture ready");
                var preview = new WardrobePreview(executor, false, (uri, limit) -> {
                    assertFalse(Platform.isFxApplicationThread(), "Preview fetch must be asynchronous");
                    var matcher = java.util.regex.Pattern.compile("(?:^|&)skin_id=([^&]+)").matcher(uri.getQuery());
                    assertTrue(matcher.find(), "Fixture preview must retain the selected item's ID");
                    UUID itemId = UUID.fromString(matcher.group(1));
                    return Files.readAllBytes(thumbnails.get(itemId));
                }, uri -> fail("Screenshot harness must not open a browser"));
                var controller = new LauncherWardrobeController(gateway, store, LauncherSettings::new,
                        feedback, executor, preview, item -> thumbnails.get(item.id()).toUri().toString());
                var scroll = new ScrollPane(controller.view());
                scroll.setFitToWidth(true);
                scroll.setStyle("-fx-background: #0B1120; -fx-background-color: #0B1120; -fx-padding: 24;");
                var root = new StackPane(scroll);
                root.getStyleClass().add("app-root");
                var scene = new Scene(root, 1440, 1000);
                scene.getStylesheets().add(getClass().getResource(
                        "/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                var stage = new Stage(StageStyle.UNDECORATED);
                stage.setTitle("Wardrobe fictional fixtures — no connected account");
                stage.setScene(scene);
                stage.show();
                controller.refresh();
                return new Harness(controller, stage, scroll);
            });
            try {
                await("initial skin catalog", () -> cards(harness).size() == 3);
                click(harness, "Preview " + gateway.skins.getFirst().name());
                awaitPreview(harness);
                assertTrue(fx(() -> button(harness.root(), "Link a Hytale account").isDisabled()));
                capturePair(harness, output, "skins");

                // Drive the real modal save dialog without blocking the test on showAndWait().
                FutureTask<Void> opened = submitFx(() -> { button(harness.root(), "Save look").fire(); return null; });
                await("native save dialog", () -> dialog() != null);
                fx(() -> {
                    DialogPane dialog = dialog();
                    List<TextField> fields = nodes(dialog, TextField.class);
                    assertEquals(2, fields.size());
                    fields.get(0).setText("Fixture Ember Favorite");
                    fields.get(1).setText("Fixture Adventures");
                    nodes(dialog, CheckBox.class).getFirst().fire();
                    var saveType = dialog.getButtonTypes().stream()
                            .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
                    ((Button) dialog.lookupButton(saveType)).fire();
                    return null;
                });
                opened.get(10, TimeUnit.SECONDS);
                await("asynchronous save and hydration", () -> store.items().size() == 4
                        && nodes(harness.root(), Button.class).stream().anyMatch(button -> button.getText().equals("Edit saved look")));
                assertEquals(4, new WardrobeStore(directory.resolve("state")).items().size());
                var saved = store.items().stream().filter(item -> item.id().equals(gateway.skins.getFirst().id())).findFirst().orElseThrow();
                assertEquals("Fixture Ember Favorite", saved.name());
                assertTrue(saved.favorite());
                assertEquals("Fixture Adventures", saved.collection());

                click(harness, "Saved looks");
                await("saved collection", () -> cards(harness).size() == 4);
                fx(() -> { combo(harness.root()).getSelectionModel().select("Favorites"); return null; });
                await("favorites filter", () -> cards(harness).size() == 1);
                assertEquals("Preview Fixture Ember Favorite", fx(() -> cards(harness).getFirst().getAccessibleText()));
                fx(() -> { combo(harness.root()).getSelectionModel().select("Capes"); return null; });
                await("cape-only saved filter", () -> cards(harness).size() == 1
                        && cards(harness).getFirst().getAccessibleText().contains("Cape"));
                fx(() -> {
                    combo(harness.root()).getSelectionModel().select("All looks");
                    textField(harness.root(), "Search names and collections").setText("ADVENTURES");
                    button(harness.root(), "Search").fire();
                    return null;
                });
                await("collection text search", () -> cards(harness).size() == 1
                        && cards(harness).getFirst().getAccessibleText().equals("Preview Fixture Ember Favorite"));
                fx(() -> {
                    textField(harness.root(), "Search names and collections").clear();
                    button(harness.root(), "Search").fire();
                    return null;
                });
                await("restored saved collection", () -> cards(harness).size() == 4);
                click(harness, "Preview Fixture Ember Favorite");
                awaitPreview(harness);
                capturePair(harness, output, "saved");

                assertEquals(0, gateway.applyCalls.get());
                Files.writeString(output.resolve("wardrobe-fixtures.txt"), """
                        Native LauncherWardrobeController screenshots, generated by LauncherWardrobeUiTest.
                        Four PNG captures: skins and saved; desktop 1440x1000 and compact 1000x900.
                        All displayed names, UUIDs, cosmetics, and schematic PNG artwork are fictional
                        test fixtures. The pictures are real JavaFX scene snapshots, not artwork of a proposed UI.
                        The preview deliberately exercises the supported static-image fallback with local test images.
                        No credentials are loaded. Gateway operations and preview fetches are stubbed; thumbnails
                        are local files. Account application and external-browser actions fail if invoked.
                        Actions exercised: selection, native Save dialog, favorite/type/search filters.
                        Run: MODTALE_WARDROBE_SCREENSHOTS=~/Pictures ./gradlew test --tests '*LauncherWardrobeUiTest' --rerun-tasks
                        """);
            } finally {
                fx(() -> {
                    // A failed assertion in a modal must not strand the FX nested event loop.
                    for (Window window : List.copyOf(Window.getWindows())) {
                        if (window instanceof Stage dialogStage && dialogStage.getOwner() == harness.stage()) window.hide();
                    }
                    harness.controller().close();
                    harness.stage().close();
                    return null;
                });
            }
        }
    }

    /** Live public catalog/Hyvatar reads only. Failures are recorded alongside any successful captures. */
    @Test
    @EnabledIfEnvironmentVariable(named = "MODTALE_WARDROBE_LIVE_SCREENSHOTS", matches = ".+")
    void livePublicWardrobeScreenshots() throws Exception {
        Path output = outputDirectory("MODTALE_WARDROBE_LIVE_SCREENSHOTS");
        Files.createDirectories(output);
        List<String> notes = new java.util.concurrent.CopyOnWriteArrayList<>();
        notes.add("Actual LauncherWardrobeController scenes using public HyTags catalogs and real Hyvatar imagery.");
        notes.add("Captured at " + java.time.Instant.now() + ". No sign-in, settings-file reads, or account writes.");
        notes.add("Catalog pages are displayed using the real responsive grid; no synthetic cards are added.");
        var auth = new HytaleAuthService(null, null) {
            @Override public String freshSessionToken(LauncherSettings settings) { throw new AssertionError("Live screenshots cannot request credentials"); }
        };
        var gateway = new WardrobeApiClient(auth) {
            @Override public List<WardrobeItem> browseSkins(int page, String sort) {
                List<WardrobeItem> items = super.browseSkins(page, sort);
                notes.add("Public skin catalog: " + items.size() + " items from page " + page + ", sort=" + sort);
                return items;
            }
            @Override public List<WardrobeItem> capes() { throw new AssertionError("Capes belong to the installed Customize catalog"); }
            @Override public WardrobeItem currentSkin(LauncherSettings settings) { throw new AssertionError("No account reads in live screenshots"); }
            @Override public void apply(WardrobeItem item, LauncherSettings settings) { throw new AssertionError("No account writes in live screenshots"); }
            @Override public void apply(WardrobeItem item, LauncherSettings settings, UUID target) { throw new AssertionError("No account writes in live screenshots"); }
        };
        WardrobeStore store = new WardrobeStore(directory.resolve("live-state"));
        List<String> failures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            Harness harness = fx(() -> {
                var feedback = new LauncherFeedback(executor, new Label(), new VBox(), new StackPane(),
                        new Label(), new Label(), () -> "Read-only public catalog");
                var controller = new LauncherWardrobeController(gateway, store, LauncherSettings::new, feedback, executor);
                var scroll = new ScrollPane(controller.view());
                scroll.setFitToWidth(true);
                scroll.setStyle("-fx-background: #0B1120; -fx-background-color: #0B1120; -fx-padding: 24;");
                var root = new StackPane(scroll);
                root.getStyleClass().add("app-root");
                var scene = new Scene(root, 1440, 1000);
                scene.getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                var stage = new Stage(StageStyle.UNDECORATED);
                stage.setTitle("Wardrobe public catalog — read only, no linked account");
                stage.setScene(scene); stage.show();
                controller.refresh();
                return new Harness(controller, stage, scroll);
            });
            try {
                for (String tab : List.of("Skins")) {
                    try {
                        await("live " + tab + " catalog", 180, () -> (cards(harness).size() >= 6
                                && nodes(harness.root(), Label.class).stream().noneMatch(label -> label.getText().equals("Loading looks…"))
                                && cards(harness).stream().allMatch(card -> card.getAccessibleText().startsWith("Preview skin ")))
                                || nodes(harness.root(), Label.class).stream().anyMatch(label -> label.getText().contains("Try Search again.")));
                        String catalogError = fx(() -> nodes(harness.root(), Label.class).stream()
                                .map(Label::getText).filter(text -> text.contains("Try Search again.")).findFirst().orElse(""));
                        assertTrue(catalogError.isEmpty(), catalogError);
                        assertTrue(fx(() -> cards(harness).size() >= 6), "Need at least six real " + tab + " cards");
                        assertTrue(fx(() -> nodes(harness.root(), Label.class).stream()
                                .noneMatch(label -> label.isVisible() && label.getText().matches("(?i).*Skin #[0-9a-f]+.*"))));
                        assertTrue(fx(() -> cards(harness).stream().allMatch(card -> card.getTooltip() == null)));
                        String selection = fx(() -> cards(harness).getFirst().getAccessibleText());
                        click(harness, selection);
                        await("live preview response", 100, () -> {
                            String status = ((Label) harness.root().lookup("#wardrobe-preview-status")).getText();
                            return status.startsWith("Live 3D preview") || status.startsWith("Static PNG preview")
                                    || status.startsWith("Rendered cape preview") || status.contains("could not be loaded");
                        });
                        String previewStatus = fx(() -> ((Label) harness.root().lookup("#wardrobe-preview-status")).getText());
                        notes.add(tab + " selection: " + selection + "; " + previewStatus);
                        await("live thumbnails", 100, () -> cards(harness).stream().allMatch(card -> nodes(card, ImageView.class).stream()
                                .allMatch(view -> view.getImage() != null && view.getImage().getProgress() == 1)));
                        long rendered = fx(() -> cards(harness).stream().filter(card -> nodes(card, ImageView.class).stream()
                                .anyMatch(view -> view.getImage() != null && !view.getImage().isError())).count());
                        notes.add(tab + ": " + rendered + " thumbnails loaded successfully");
                        assertTrue(rendered >= 6, "Only " + rendered + " real thumbnails loaded for " + tab);
                        assertFalse(previewStatus.contains("could not be loaded"), "Hyvatar preview failed for " + selection);
                        assertTrue(fx(() -> button(harness.root(), "Link a Hytale account").isDisabled()));
                        capturePair(harness, output, "live-" + tab.toLowerCase(java.util.Locale.ROOT));
                    } catch (Exception | AssertionError failure) {
                        String detail = tab + " capture failed: " + failure;
                        failures.add(detail); notes.add(detail);
                        System.err.println(detail);
                    }
                }
            } finally {
                fx(() -> { harness.controller().close(); harness.stage().close(); return null; });
                executor.shutdownNow();
                Files.write(output.resolve("wardrobe-live-provenance.txt"), notes);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static void capturePair(Harness harness, Path output, String name) throws Exception {
        for (int[] size : List.of(new int[]{1440, 1000}, new int[]{1000, 900})) {
            fx(() -> {
                harness.stage().setWidth(size[0]); harness.stage().setHeight(size[1]);
                harness.scroll().setVvalue(0);
                return null;
            });
            await("scene resize", () -> Math.round(harness.stage().getScene().getWidth()) == size[0]
                    && Math.round(harness.stage().getScene().getHeight()) == size[1]);
            if (name.startsWith("live-")) await("full responsive rows", 100, () -> {
                var grid = (javafx.scene.layout.GridPane)harness.root().lookup("#wardrobe-cards");
                return cards(harness).size() == grid.getColumnConstraints().size() * 4
                        && !button(harness.root(), "Next").isDisabled();
            });
            await("local thumbnail loading", 100, () -> nodes(harness.root(), ImageView.class).stream()
                    .allMatch(view -> view.getImage() == null || view.getImage().getProgress() == 1));
            WritableImage image = fx(() -> {
                harness.root().applyCss(); harness.root().layout();
                assertTrue(nodes(harness.root(), ImageView.class).stream()
                        .allMatch(view -> view.getImage() == null || !view.getImage().isError()), "Local fixture images must render successfully");
                return harness.stage().getScene().snapshot(null);
            });
            assertEquals(size[0], (int) image.getWidth()); assertEquals(size[1], (int) image.getHeight());
            var png = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
            int[] pixels = new int[size[0] * size[1]];
            image.getPixelReader().getPixels(0, 0, size[0], size[1], javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, size[0]);
            png.setRGB(0, 0, size[0], size[1], pixels, 0, size[0]);
            Path file = output.resolve("launcher-wardrobe-" + name + (size[0] == 1440 ? "-desktop.png" : "-compact.png"));
            assertTrue(ImageIO.write(png, "png", file.toFile()));
            assertTrue(Files.size(file) > 10_000, "Screenshot should contain the rendered controller");
            System.out.println("Wardrobe screenshot: " + file.toAbsolutePath());
        }
    }

    private static void awaitPreview(Harness harness) throws Exception {
        await("fixture preview", () -> ((Label) harness.root().lookup("#wardrobe-preview-status")).getText().startsWith("Static PNG"));
    }

    private static void click(Harness harness, String label) throws Exception {
        fx(() -> { ButtonBase button = button(harness.root(), label); assertFalse(button.isDisabled(), label); button.fire(); return null; });
    }

    private static ButtonBase button(Node root, String text) {
        return nodes(root, ButtonBase.class).stream()
                .filter(button -> text.equals(button.getText()) || text.equals(button.getAccessibleText()))
                .findFirst().orElseThrow(() -> new AssertionError("Button not found: " + text));
    }

    private static List<Button> cards(Harness harness) {
        return nodes(harness.root(), Button.class).stream().filter(button -> button.getStyleClass().contains("wardrobe-card")).toList();
    }

    @SuppressWarnings("unchecked") private static ComboBox<String> combo(Node root) {
        return (ComboBox<String>) nodes(root, ComboBox.class).getFirst();
    }

    private static TextField textField(Node root, String prompt) {
        return nodes(root, TextField.class).stream().filter(field -> prompt.equals(field.getPromptText())).findFirst().orElseThrow();
    }

    private static DialogPane dialog() {
        for (Window window : List.copyOf(Window.getWindows())) {
            if (window.isShowing() && window.getScene() != null) {
                var panes = nodes(window.getScene().getRoot(), DialogPane.class);
                if (!panes.isEmpty()) return panes.getFirst();
            }
        }
        return null;
    }

    private static <T> List<T> nodes(Node node, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(node)) found.add(type.cast(node));
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) found.addAll(nodes(child, type));
        return found;
    }

    private static void await(String description, Callable<Boolean> condition) throws Exception {
        await(description, 10, condition);
    }

    private static void await(String description, int seconds, Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (fx(condition)) return;
            Thread.sleep(25);
        }
        fail("Timed out awaiting " + description);
    }

    private static <T> FutureTask<T> submitFx(Callable<T> action) {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task;
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.call();
        return submitFx(action).get(10, TimeUnit.SECONDS);
    }

    private static Path outputDirectory() {
        return outputDirectory("MODTALE_WARDROBE_SCREENSHOTS");
    }

    private static Path outputDirectory(String variable) {
        String value = System.getenv(variable);
        if (value.equals("~")) value = System.getProperty("user.home");
        else if (value.startsWith("~/")) value = System.getProperty("user.home") + value.substring(1);
        return Path.of(value).toAbsolutePath().normalize();
    }

    private record Harness(LauncherWardrobeController controller, Stage stage, ScrollPane scroll) {
        Parent root() { return stage.getScene().getRoot(); }
    }

    private static class FixtureGateway extends WardrobeApiClient {
        final List<WardrobeItem> skins = List.of(item("Fixture Ember Scout", WardrobeItem.Kind.SKIN),
                item("Fixture Moss Ranger", WardrobeItem.Kind.SKIN), item("Fixture Frost Walker", WardrobeItem.Kind.SKIN));
        final List<WardrobeItem> capeItems = List.of(item("Fixture Sun Cape", WardrobeItem.Kind.CAPE), item("Fixture Dusk Cape", WardrobeItem.Kind.CAPE));
        final AtomicInteger applyCalls = new AtomicInteger();

        FixtureGateway() { super(new HytaleAuthService(null, null) {
            @Override public String freshSessionToken(LauncherSettings settings) { throw new AssertionError("No fixture auth requests"); }
        }); }
        List<WardrobeItem> allItems() { var items = new ArrayList<>(skins); items.addAll(capeItems); return items; }
        @Override public List<WardrobeItem> browseSkins(int page, String sort) { return skins; }
        @Override public List<WardrobeItem> capes() { return capeItems; }
        @Override public WardrobeItem lookupSkin(String name) { return skins.getFirst(); }
        @Override public WardrobeItem hydrate(WardrobeItem item) { return item; }
        @Override public WardrobeItem currentSkin(LauncherSettings settings) { throw new AssertionError("No fixture account reads"); }
        @Override public void apply(WardrobeItem item, LauncherSettings settings) { applyCalls.incrementAndGet(); fail("No fixture account writes"); }
        @Override public void apply(WardrobeItem item, LauncherSettings settings, UUID expectedProfile) {
            applyCalls.incrementAndGet(); fail("No fixture account writes");
        }
    }

    private static WardrobeItem item(String name, WardrobeItem.Kind kind) {
        try {
            UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
            String payload = new ObjectMapper().writeValueAsString(Map.of("username", "FixtureAvatar", "skinId", id.toString(),
                    "cape", kind == WardrobeItem.Kind.CAPE ? "FixtureCape" : "", "skin", Map.of("fixture", true)));
            return new WardrobeItem(id, kind, name, false, "Fixture Collection", payload);
        } catch (Exception e) { throw new AssertionError(e); }
    }

    /** Schematic fixture art, deliberately distinct from a real player's skin or a production render. */
    private static byte[] fixturePng(boolean cape, int seed) throws java.io.IOException {
        var image = new BufferedImage(256, 384, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color accent = Color.getHSBColor(Math.floorMod(seed, 360) / 360f, .48f, .85f);
            g.setColor(new Color(0, 0, 0, 55)); g.fillOval(45, 344, 166, 22);
            if (cape) {
                g.setColor(accent.darker()); g.fillPolygon(new int[]{91, 165, 201, 55}, new int[]{119, 119, 320, 320}, 4);
                g.setColor(accent); g.fillPolygon(new int[]{102, 154, 177, 79}, new int[]{119, 119, 300, 300}, 4);
                g.setColor(new Color(246, 221, 142)); g.fillPolygon(new int[]{128, 147, 128, 109}, new int[]{166, 198, 230, 198}, 4);
            } else {
                g.setColor(new Color(48, 60, 78)); g.fillRect(90, 246, 33, 96); g.fillRect(134, 246, 33, 96);
                g.setColor(accent.darker()); g.fillRect(59, 139, 32, 109); g.fillRect(166, 139, 32, 109);
                g.setColor(accent); g.fillRect(88, 131, 82, 124);
                g.setColor(new Color(233, 202, 147)); g.fillRect(91, 211, 76, 12); g.fillRect(121, 209, 17, 16);
            }
            g.setColor(new Color(216, 172, 135)); g.fillRoundRect(93, 59, 71, 75, 8, 8);
            g.setColor(new Color(70, 46, 43)); g.fillRect(90, 53, 77, 22); g.fillRect(90, 69, 12, 27);
            if (!cape) { g.setColor(new Color(37, 47, 61)); g.fillRect(109, 88, 8, 9); g.fillRect(144, 88, 8, 9); }
        } finally { g.dispose(); }
        var output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output); return output.toByteArray();
    }
}
