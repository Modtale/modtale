package net.modtale.launcher.ui.wardrobe;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.modtale.launcher.hytale.HytaleAuthService;
import net.modtale.launcher.hytale.HytaleAuthSession;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.wardrobe.WardrobeApiClient;
import net.modtale.launcher.wardrobe.WardrobeItem;
import net.modtale.launcher.wardrobe.WardrobeStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Offline controller regressions using real FX controls/dialogs; requires a graphical display. */
class LauncherWardrobeControllerTest {
    @TempDir Path directory;
    private static final UUID ALICE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final WardrobeItem CAPE = new WardrobeItem(UUID.fromString("00000000-0000-4000-8000-000000000003"),
            WardrobeItem.Kind.CAPE, "Catalog cape", false, "", "{\"username\":\"OldOwner\",\"cape\":\"Cape_Test.Blue\",\"skin\":{\"cape\":\"Cape_Test.Blue\"}}");
    private static final WardrobeItem SKIN = new WardrobeItem(UUID.fromString("00000000-0000-4000-8000-000000000004"),
            WardrobeItem.Kind.SKIN, "Catalog skin", false, "", "{\"skinId\":\"fixture\",\"skin\":{\"body\":\"fixture\"}}");

    @BeforeAll static void toolkit() throws Exception {
        if (System.getProperty("os.name", "").toLowerCase().contains("linux"))
            assumeTrue(!System.getenv().getOrDefault("DISPLAY", "").isBlank(), "JavaFX controller tests require DISPLAY");
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { /* Shared toolkit. */ }
        catch (UnsupportedOperationException unavailable) {
            org.junit.jupiter.api.Assumptions.abort("JavaFX display unavailable: " + unavailable.getMessage());
        }
        fx(() -> { Platform.setImplicitExit(false); return null; });
    }

    @Test void capePreviewTracksAccountSwitchAndMatchesExplicitApplyTarget() throws Exception {
        try (Harness h = new Harness()) {
            h.store.saveItem(CAPE);
            fx(() -> { button(h.root(), "Saved looks").fire(); return null; });
            await(() -> card(h.root(), "Catalog cape") != null);
            URI alice = h.previews.poll(5, TimeUnit.SECONDS);
            assertNotNull(alice);
            assertEquals("/render/cape/Alice", alice.getPath(), "Cape payload's old username must not determine the fitting-room account");
            assertTrue(alice.getQuery().contains("cape=Cape_Test.Blue"));
            assertEquals("Apply to Alice", fx(() -> button(h.root(), "Apply to Alice").getText()));

            fx(() -> {
                h.settings.set(settings("Bob", BOB));
                h.controller.refresh();
                assertFalse(button(h.root(), "Apply to Bob").isDisabled());
                return null;
            });
            URI bob = h.previews.poll(5, TimeUnit.SECONDS);
            assertNotNull(bob, "Account change must reload the selected cape preview");
            assertEquals("/render/cape/Bob", bob.getPath());
            assertEquals(alice.getQuery(), bob.getQuery(), "Switching accounts must preserve the selected cape");
            fx(() -> { button(h.root(), "Apply to Bob").fire(); return null; });
            Apply applied = h.gateway.applies.poll(5, TimeUnit.SECONDS);
            assertNotNull(applied);
            assertEquals(BOB, applied.expectedProfile());
            assertEquals(BOB.toString(), applied.actualProfile());
            assertEquals("Bob", applied.username());
            assertEquals(CAPE.id(), applied.item().id());
            await(() -> nodes(h.root(), Button.class).stream().anyMatch(b -> "Apply to Bob".equals(b.getText()) && !b.isDisabled()));
        }
    }

    @Test void rediscoveredSavedLookInitializesDialogFromStoredMetadataAndSavesAsynchronously() throws Exception {
        try (Harness h = new Harness()) {
            WardrobeItem saved = new WardrobeItem(SKIN.id(), SKIN.kind(), "My favorite outfit", true, "Adventures", SKIN.payload());
            h.store.saveItem(saved);
            fx(() -> { h.controller.refresh(); return null; });
            await(() -> card(h.root(), saved.name()) != null);
            // Catalog auto-selection retains the raw item; the dialog must independently resolve its saved UUID.
            FutureTask<Void> opened = submitFx(() -> { button(h.root(), "Edit saved look").fire(); return null; });
            await(() -> h.dialog() != null);
            fx(() -> {
                DialogPane dialog = h.dialog();
                List<TextField> fields = nodes(dialog, TextField.class);
                assertEquals(2, fields.size());
                assertEquals(saved.name(), fields.get(0).getText());
                assertEquals(saved.collection(), fields.get(1).getText());
                assertTrue(nodes(dialog, CheckBox.class).getFirst().isSelected());
                fields.get(0).setText("Renamed favorite");
                var save = dialog.getButtonTypes().stream().filter(t -> t.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
                ((Button) dialog.lookupButton(save)).fire();
                return null;
            });
            opened.get(5, TimeUnit.SECONDS);
            assertTrue(h.gateway.hydrationStarted.await(5, TimeUnit.SECONDS));
            // Hydration is deliberately blocked: the FX thread must remain responsive and disk state unchanged.
            assertEquals("responsive", fx(() -> "responsive"));
            assertEquals(saved, h.store.items().getFirst());
            h.gateway.releaseHydration.countDown();
            await(() -> nodes(h.root(), Label.class).stream().anyMatch(l -> "Renamed favorite".equals(l.getText())));
            WardrobeItem persisted = new WardrobeStore(directory).items().getFirst();
            assertEquals("Renamed favorite", persisted.name());
            assertEquals(saved.collection(), persisted.collection());
            assertTrue(persisted.favorite());
            assertEquals(saved.id(), persisted.id());
            assertEquals(saved.payload(), persisted.payload());
        }
    }

    @Test void skinGridFillsAvailableWidthAndRepaginatesWithoutMissingResults() throws Exception {
        try (Harness h = new Harness()) {
            List<WardrobeItem> all = new ArrayList<>();
            for (int i = 0; i < 83; i++) all.add(new WardrobeItem(new UUID(0, i + 100),
                    WardrobeItem.Kind.SKIN, "Grid " + i, false, "", SKIN.payload()));
            h.gateway.skins = List.copyOf(all);
            fx(() -> { h.stage.setWidth(1750); h.controller.refresh(); return null; });
            await(() -> gridReady(h));
            fx(() -> {
                assertFalse(h.root().lookup("#wardrobe-saved-filter").isVisible(), "Skin sort dropdown must be hidden");
                assertFalse(h.root().lookup("#wardrobe-saved-filter").isManaged());
                assertGridWidth(h); return null;
            });
            List<String> seen = new ArrayList<>();
            while (true) {
                List<String> current = fx(() -> gridCards(h).stream().map(Button::getAccessibleText).toList());
                seen.addAll(current);
                if (fx(() -> button(h.root(), "Next").isDisabled())) break;
                int count = fx(() -> ((javafx.scene.layout.GridPane)h.root().lookup("#wardrobe-cards")).getColumnConstraints().size());
                assertEquals(count * 4, current.size(), "Every nonfinal page must have four full rows");
                String first = current.getFirst();
                fx(() -> { button(h.root(), "Next").fire(); return null; });
                await(() -> !gridCards(h).isEmpty() && !gridCards(h).getFirst().getAccessibleText().equals(first)
                        && nodes(h.root(), Label.class).stream().noneMatch(l -> l.getText().equals("Loading looks…")));
            }
            assertEquals(all.stream().map(item -> "Preview " + item.name()).toList(), seen,
                    "Provider page boundaries must not duplicate or skip skins");
            for (int width : new int[]{1100, 1450}) {
                fx(() -> { h.stage.setWidth(width); return null; });
                await(() -> gridReady(h) && gridCards(h).getFirst().getAccessibleText().equals("Preview Grid 0"));
                fx(() -> { assertGridWidth(h); return null; });
            }
        }
    }

    private static List<Button> gridCards(Harness h) {
        return nodes(h.root().lookup("#wardrobe-cards"), Button.class).stream()
                .filter(b -> b.getStyleClass().contains("wardrobe-card")).toList();
    }
    private static boolean gridReady(Harness h) {
        var grid = (javafx.scene.layout.GridPane)h.root().lookup("#wardrobe-cards");
        return gridCards(h).size() == grid.getColumnConstraints().size() * 4 && !button(h.root(), "Next").isDisabled();
    }
    private static void assertGridWidth(Harness h) {
        h.root().applyCss(); ((javafx.scene.Parent) h.root()).layout();
        var grid = (javafx.scene.layout.GridPane)h.root().lookup("#wardrobe-cards");
        int count = grid.getColumnConstraints().size();
        List<Button> cards = gridCards(h);
        assertEquals(0, cards.getFirst().getBoundsInParent().getMinX(), 1);
        assertEquals(grid.getWidth(), cards.get(count - 1).getBoundsInParent().getMaxX(), 1, "Last column must reach the grid edge");
        for (Button card : cards) assertEquals(cards.getFirst().getWidth(), card.getWidth(), 1, "Cards must have equal widths");
    }

    private final class Harness implements AutoCloseable {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final Gateway gateway = new Gateway();
        final WardrobeStore store;
        final AtomicReference<LauncherSettings> settings = new AtomicReference<>(settings("Alice", ALICE));
        final BlockingQueue<URI> previews = new LinkedBlockingQueue<>();
        final LauncherWardrobeController controller;
        final Stage stage;
        Harness() throws Exception {
            store = new WardrobeStore(directory);
            BufferedImage pixel = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(pixel, "png", output);
            byte[] png = output.toByteArray();
            controller = fx(() -> {
                var feedback = new LauncherFeedback(executor, new Label(), new VBox(), new StackPane(), new Label(), new Label(), () -> "Ready");
                var preview = new WardrobePreview(executor, false, (uri, limit) -> {
                    assertFalse(Platform.isFxApplicationThread());
                    previews.add(uri); return png;
                }, uri -> fail("Tests must not open a browser"));
                return new LauncherWardrobeController(gateway, store, settings::get, feedback, executor, preview, item -> "");
            });
            stage = fx(() -> {
                Stage stage = new Stage();
                Scene scene = new Scene(new StackPane(controller.view()), 1100, 800);
                scene.getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                stage.setScene(scene); stage.show(); return stage;
            });
        }
        Node root() { return controller.view(); }
        DialogPane dialog() {
            return Window.getWindows().stream().filter(Window::isShowing)
                    .filter(w -> w instanceof Stage s && s.getOwner() == stage)
                    .flatMap(w -> nodes(w.getScene().getRoot(), DialogPane.class).stream()).findFirst().orElse(null);
        }
        @Override public void close() throws Exception {
            gateway.releaseHydration.countDown();
            fx(() -> {
                DialogPane dialog = dialog();
                if (dialog != null) ((Button)dialog.lookupButton(ButtonType.CANCEL)).fire();
                controller.close(); stage.hide(); return null;
            });
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            fx(() -> null);
        }
    }

    private record Apply(WardrobeItem item, UUID expectedProfile, String actualProfile, String username) {}
    private static final class Gateway extends WardrobeApiClient {
        final BlockingQueue<Apply> applies = new LinkedBlockingQueue<>();
        volatile List<WardrobeItem> skins = List.of(SKIN);
        final CountDownLatch hydrationStarted = new CountDownLatch(1), releaseHydration = new CountDownLatch(1);
        Gateway() { super(new HytaleAuthService(null, null) {
            @Override public String freshSessionToken(LauncherSettings settings) { throw new AssertionError("No real authentication in regression tests"); }
        }); }
        @Override public List<WardrobeItem> browseSkins(int page, String sort) { assertFalse(Platform.isFxApplicationThread()); assertEquals("user_count", sort); return skins.stream().skip((long)(page - 1) * 20).limit(20).toList(); }
        @Override public List<WardrobeItem> capes() { assertFalse(Platform.isFxApplicationThread()); return List.of(CAPE); }
        @Override public WardrobeItem hydrate(WardrobeItem item) {
            assertFalse(Platform.isFxApplicationThread());
            hydrationStarted.countDown();
            try { if (!releaseHydration.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test did not release hydration"); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
            return item;
        }
        @Override public WardrobeItem currentSkin(LauncherSettings settings) { assertFalse(Platform.isFxApplicationThread()); return SKIN; }
        @Override public void apply(WardrobeItem item, LauncherSettings settings, UUID expectedProfile) {
            assertFalse(Platform.isFxApplicationThread());
            var session = settings.getHytaleAuthSession();
            applies.add(new Apply(item, expectedProfile, session.getUuid(), session.getUsername()));
        }
    }

    private static LauncherSettings settings(String name, UUID profile) {
        HytaleAuthSession session = new HytaleAuthSession();
        session.setUsername(name); session.setUuid(profile.toString()); session.setAccountOwnerId(profile.toString());
        LauncherSettings settings = new LauncherSettings(); settings.setHytaleAuthSession(session); return settings;
    }
    private static Button card(Node root, String name) { return nodes(root, Button.class).stream().filter(b -> ("Preview " + name).equals(b.getAccessibleText())).findFirst().orElse(null); }
    private static ButtonBase button(Node root, String text) { return nodes(root, ButtonBase.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow(); }
    private static <T> List<T> nodes(Node node, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(node)) result.add(type.cast(node));
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) result.addAll(nodes(child, type));
        return result;
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) { if (fx(condition)) return; Thread.sleep(10); }
        fail("Controller did not reach expected state");
    }
    private static <T> FutureTask<T> submitFx(Callable<T> action) { FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task; }
    private static <T> T fx(Callable<T> action) throws Exception { return submitFx(action).get(5, TimeUnit.SECONDS); }
}
