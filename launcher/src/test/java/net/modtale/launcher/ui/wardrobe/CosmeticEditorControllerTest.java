package net.modtale.launcher.ui.wardrobe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Offline slot workflows through real FX controls and modal dialogs; requires a display. */
class CosmeticEditorControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID PLAYER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String ACTIVE = "00000000-0000-4000-8000-000000000011";
    private static final String OTHER = "00000000-0000-4000-8000-000000000012";
    private static final String CREATED = "00000000-0000-4000-8000-000000000013";
    private static final String STALE = "{\"bodyCharacteristic\":\"Default.01\",\"haircut\":\"Short.Black\",\"cape\":null}";
    private static final String FRESH = "{\"bodyCharacteristic\":\"Default.02\",\"haircut\":\"Long.Red.Variant\",\"cape\":\"Cape_Fresh.Blue\"}";
    @TempDir Path directory;

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

    @Test void lateCurrentSkinAndHydrationCannotReplaceCapeEdits() throws Exception {
        for (boolean hydrate : List.of(false, true)) {
            try (Harness h = new Harness()) {
                fx(() -> { h.controller.edit(new WardrobeItem(PLAYER, WardrobeItem.Kind.SKIN,
                        "Saved look", false, "", "{\"skin\":" + STALE + "}")); return null; });
                await(() -> h.controller.draftSnapshot().equals(json(STALE)));
                h.gateway.delayLoads = true;
                fx(() -> {
                    if (hydrate) h.controller.edit(new WardrobeItem(PLAYER, WardrobeItem.Kind.SKIN,
                            "Incoming", false, "", "{\"skin\":" + FRESH + "}"));
                    else button(h.root(), "Load current look").fire();
                    return null;
                });
                assertTrue(h.gateway.loadStarted.await(5, TimeUnit.SECONDS), "Load must be in flight before editing");
                await(() -> !"Ready".equals(h.status.getText()));
                fx(() -> { h.controller.editCape("Cape_UserEdit.Green"); return null; });
                JsonNode expected = json(STALE);
                ((com.fasterxml.jackson.databind.node.ObjectNode) expected).put("cape", "Cape_UserEdit.Green");
                assertEquals(expected, fx(h.controller::draftSnapshot));
                h.gateway.releaseLoad.countDown();
                // Feedback resets status in the same FX callback that delivers the loaded skin.
                await(() -> "Ready".equals(h.status.getText()));
                assertEquals(expected, fx(h.controller::draftSnapshot),
                        (hydrate ? "Hydration" : "Current skin") + " must not overwrite edits made while loading");
            }
        }
    }

    @Test void createUsesLoadedLookAndFullSlotsPreventAnotherCreateDialogOrWrite() throws Exception {
        try (Harness h = new Harness()) {
            fx(() -> { button(h.root(), "Load current look").fire(); return null; });
            await(() -> h.controller.draftSnapshot().equals(json(FRESH)));
            h.accept(() -> saveToHytale(h.root()).fire(), "My outfit", "New outfit");
            Mutation create = h.mutation("create");
            assertEquals("New outfit", create.name());
            assertEquals(json(FRESH), create.skin());
            await(() -> "Ready".equals(h.status.getText()));
            fx(() -> {
                assertFalse(saveToHytale(h.root()).isDisable());
                saveToHytale(h.root()).fire();
                assertNull(h.dialog(), "Capacity rejection must happen before asking for a name");
                return null;
            });
            assertNull(h.gateway.mutations.poll(200, TimeUnit.MILLISECONDS), "Full slots must not issue another create");
        }
    }

    @Test void editorHasNoHytaleOutfitsSection() throws Exception {
        try (Harness h = new Harness()) {
            fx(() -> {
                assertTrue(nodes(h.root(), Label.class).stream().noneMatch(label ->
                        List.of("Hytale outfits", "Wearing", "Main", "Adventure").contains(label.getText())));
                assertEquals(List.of("Save"), nodes(h.root(), MenuButton.class).stream().map(MenuButton::getText).toList());
                return null;
            });
        }
    }

    private final class Harness implements AutoCloseable {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final Gateway gateway = new Gateway(directory);
        final CosmeticEditorController controller;
        final Stage stage;
        final Label status;
        Harness() throws Exception {
            LauncherSettings settings = new LauncherSettings();
            HytaleAuthSession session = new HytaleAuthSession();
            session.setUsername("Alice"); session.setUuid(PLAYER.toString()); session.setAccountOwnerId(PLAYER.toString());
            settings.setHytaleAuthSession(session);
            settings.setHytaleGamePath(directory.resolve("missing-game").toString());
            WardrobeStore store = new WardrobeStore(directory);
            status = fx(Label::new);
            controller = fx(() -> new CosmeticEditorController(gateway, store, () -> settings,
                    new LauncherFeedback(executor, status, new VBox(), new StackPane(), new Label(), new Label(), () -> "Ready"), executor));
            stage = fx(() -> {
                Stage result = new Stage();
                Scene scene = new Scene(new StackPane(controller.view()), 1100, 800);
                scene.getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                result.setScene(scene); result.show(); controller.refresh(); return result;
            });
            await(() -> ((CheckBox) button(root(), "Owned only")).isSelected());
        }
        Node root() { return controller.view(); }
        DialogPane dialog() {
            return Window.getWindows().stream().filter(Window::isShowing)
                    .filter(w -> w instanceof Stage s && s.getOwner() == stage)
                    .flatMap(w -> nodes(w.getScene().getRoot(), DialogPane.class).stream()).findFirst().orElse(null);
        }
        void accept(Runnable trigger, String initial, String replacement) throws Exception {
            FutureTask<Void> opened = submitFx(() -> { trigger.run(); return null; });
            await(() -> dialog() != null);
            fx(() -> {
                if (initial != null) {
                    TextField field = nodes(dialog(), TextField.class).getFirst();
                    assertEquals(initial, field.getText());
                    field.setText(replacement);
                }
                ((Button) dialog().lookupButton(ButtonType.OK)).fire(); return null;
            });
            opened.get(5, TimeUnit.SECONDS);
        }
        Mutation mutation(String action) throws Exception {
            Mutation value = gateway.mutations.poll(5, TimeUnit.SECONDS);
            assertNotNull(value, "Expected " + action + " API call");
            assertEquals(action, value.action());
            assertEquals(PLAYER, value.expected());
            assertEquals(PLAYER.toString(), value.actual());
            return value;
        }
        @Override public void close() throws Exception {
            gateway.releaseLoad.countDown();
            fx(() -> {
                if (dialog() != null) ((Button) dialog().lookupButton(ButtonType.CANCEL)).fire();
                controller.close(); stage.hide(); return null;
            });
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            fx(() -> null);
        }
    }

    private record Mutation(String action, String id, String name, JsonNode skin, UUID expected,
                            String actual, List<WardrobeItem> backups) {}
    private static final class Gateway extends WardrobeApiClient {
        final Path directory;
        volatile boolean delayLoads;
        final CountDownLatch loadStarted = new CountDownLatch(1), releaseLoad = new CountDownLatch(1);
        final BlockingQueue<Mutation> mutations = new LinkedBlockingQueue<>();
        volatile SkinSlots snapshot = new SkinSlots(ACTIVE, 3,
                List.of(new SkinSlot(ACTIVE, "Main", STALE), new SkinSlot(OTHER, "Adventure", STALE)));
        Gateway(Path directory) {
            super(new HytaleAuthService(null, null) {
                @Override public String freshAccessToken(LauncherSettings settings) { throw new AssertionError("No real auth in slot tests"); }
                @Override public String freshSessionToken(LauncherSettings settings) { throw new AssertionError("No real auth in slot tests"); }
            });
            this.directory = directory;
        }
        @Override public SkinSlots slots(LauncherSettings settings) { assertFalse(Platform.isFxApplicationThread()); return snapshot; }
        @Override public Map<String, Set<String>> unlockedCosmetics(LauncherSettings settings) { assertFalse(Platform.isFxApplicationThread()); return Map.of(); }
        private void delayLoad() {
            assertFalse(Platform.isFxApplicationThread());
            if (!delayLoads) return;
            loadStarted.countDown();
            try { assertTrue(releaseLoad.await(5, TimeUnit.SECONDS), "Test must release the delayed load"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        }
        @Override public WardrobeItem hydrate(WardrobeItem item) { delayLoad(); return item; }
        @Override public WardrobeItem currentSkin(LauncherSettings settings) {
            delayLoad();
            return new WardrobeItem(PLAYER, WardrobeItem.Kind.SKIN, "Current", false, "", "{\"skin\":" + FRESH + "}");
        }
        @Override public void createSkin(LauncherSettings settings, String name, JsonNode skin, UUID expected) {
            record("create", CREATED, name, skin, settings, expected);
            List<SkinSlot> next = new ArrayList<>(snapshot.slots()); next.add(new SkinSlot(CREATED, name, skin.toString()));
            snapshot = new SkinSlots(snapshot.activeId(), snapshot.max(), next);
        }
        private void record(String action, String id, String name, JsonNode skin, LauncherSettings settings, UUID expected) {
            assertFalse(Platform.isFxApplicationThread(), "Slot I/O must stay off the FX thread");
            try {
                // Reopen from disk at the write boundary: the controller's in-memory store alone is insufficient.
                mutations.add(new Mutation(action, id, name, skin == null ? null : skin.deepCopy(), expected,
                        settings.getHytaleAuthSession().getUuid(), new WardrobeStore(directory).items()));
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
    }

    private static MenuItem saveToHytale(Node root) {
        return nodes(root, MenuButton.class).stream().flatMap(menu -> menu.getItems().stream())
                .filter(item -> "Save to Hytale".equals(item.getText())).findFirst().orElseThrow();
    }
    private static ButtonBase button(Node root, String text) {
        return nodes(root, ButtonBase.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
    }
    private static JsonNode json(String text) {
        try { return JSON.readTree(text); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
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
