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

    @Test void missingHytaleAssetsShowOnlyGuidanceAndSettingsAction() throws Exception {
        try (Harness h = new Harness()) {
            var opened = new java.util.concurrent.atomic.AtomicBoolean();
            fx(() -> {
                h.settings.get().setHytaleGamePath(directory.resolve("missing-game").toString());
                h.controller.setOpenSettingsAction(() -> opened.set(true));
                h.controller.open();
                assertTrue(nodes(h.root(), Label.class).stream()
                        .anyMatch(label -> label.getText().equals("Wardrobe needs Hytale game files")));
                assertFalse(nodes(h.root(), ButtonBase.class).stream()
                        .anyMatch(button -> "Apply".equals(button.getText()) || "Customize".equals(button.getText())));
                button(h.root(), "Open Settings").fire();
                return null;
            });
            assertTrue(opened.get());
        }
    }

    @BeforeAll static void toolkit() throws Exception {
        if (System.getProperty("os.name", "").toLowerCase().contains("linux"))
            assumeTrue(!System.getenv().getOrDefault("WAYLAND_DISPLAY", "").isBlank(), "JavaFX controller tests require WAYLAND_DISPLAY");
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { /* Shared toolkit. */ }
        catch (UnsupportedOperationException unavailable) {
            org.junit.jupiter.api.Assumptions.abort("JavaFX display unavailable: " + unavailable.getMessage());
        }
        fx(() -> { Platform.setImplicitExit(false); return null; });
    }

    @Test void loadingCardsReplaceEmptyStateAndDisappearWhenSavedLooksArrive() throws Exception {
        try (Harness h = new Harness()) {
            h.store.saveItem(SKIN);
            fx(() -> {
                button(h.root(), "Saved looks").fire();
                var grid = (javafx.scene.layout.GridPane) h.root().lookup("#wardrobe-cards");
                assertEquals(grid.getColumnConstraints().size() * 4, grid.getChildren().size());
                assertTrue(grid.getChildren().stream().allMatch(n -> n.getStyleClass().contains("wardrobe-skeleton-card") && n.isMouseTransparent()));
                assertTrue(gridCards(h).isEmpty());
                return null;
            });
            await(() -> {
                h.root().applyCss(); ((javafx.scene.Parent) h.root()).layout();
                return card(h.root(), "Catalog skin") != null
                        && h.root().lookupAll(".wardrobe-skeleton-card").isEmpty();
            });
        }
    }

    @Test void capePreviewTracksAccountSwitchAndMatchesExplicitApplyTarget() throws Exception {
        try (Harness h = new Harness()) {
            h.store.saveItem(CAPE);
            fx(() -> { button(h.root(), "Saved looks").fire(); return null; });
            await(() -> card(h.root(), "Catalog cape") != null);
            assertEquals("Apply to Alice", fx(() -> button(h.root(), "Apply to Alice").getText()));

            fx(() -> {
                h.settings.set(settings("Bob", BOB));
                h.controller.refresh();
                assertFalse(button(h.root(), "Apply to Bob").isDisabled());
                return null;
            });
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

    @Test void selectingALookKeepsLoadedThumbnailNodes() throws Exception {
        try (Harness h = new Harness()) {
            fx(() -> { button(h.root(), "Popular skins").fire(); return null; });
            await(() -> h.root().lookup("#wardrobe-look-" + SKIN.id()) != null);
            fx(() -> {
                Button original = (Button) h.root().lookup("#wardrobe-look-" + SKIN.id());
                Node artwork = original.getGraphic();
                original.fire();
                assertSame(original, h.root().lookup("#wardrobe-look-" + SKIN.id()));
                assertSame(artwork, original.getGraphic());
                assertTrue(original.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("selected")));
                return null;
            });
        }
    }

    @Test void savedTabDoesNotKeepAnUnsavedCatalogSkinSelected() throws Exception {
        try (Harness h = new Harness()) {
            h.store.saveItem(CAPE);
            fx(() -> { button(h.root(), "Popular skins").fire(); return null; });
            await(() -> h.root().lookup("#wardrobe-look-" + SKIN.id()) != null);
            fx(() -> { button(h.root(), "Saved looks").fire(); return null; });
            await(() -> card(h.root(), "Catalog cape") != null);
            fx(() -> { button(h.root(), "Apply to Alice").fire(); return null; });
            Apply applied = h.gateway.applies.poll(5, TimeUnit.SECONDS);
            assertNotNull(applied);
            assertEquals(CAPE.id(), applied.item().id(), "Saved looks must apply the displayed saved selection, not a stale catalog skin");
        }
    }

    @Test void rediscoveredSavedLookInitializesDialogFromStoredMetadataAndSavesAsynchronously() throws Exception {
        try (Harness h = new Harness()) {
            WardrobeItem saved = new WardrobeItem(SKIN.id(), SKIN.kind(), "My favorite outfit", true, "Adventures", SKIN.payload());
            h.store.saveItem(saved);
            fx(() -> { button(h.root(), "Popular skins").fire(); h.controller.refresh(); return null; });
            await(() -> h.root().lookup("#wardrobe-look-" + saved.id()) != null);
            // Catalog auto-selection retains the raw item; the dialog must independently resolve its saved UUID.
            FutureTask<Void> opened = submitFx(() -> { button(h.root(), "Edit saved look").fire(); return null; });
            await(() -> h.dialog() != null);
            fx(() -> {
                Node dialog = h.dialog();
                List<TextField> fields = nodes(dialog, TextField.class);
                assertEquals(2, fields.size());
                assertEquals(saved.name(), fields.get(0).getText());
                assertEquals(saved.collection(), fields.get(1).getText());
                assertTrue(nodes(dialog, CheckBox.class).getFirst().isSelected());
                fields.get(0).setText("   ");
                assertTrue(((Button) dialog.lookup(".status-modal-primary")).isDisabled());
                fields.get(0).setText("Renamed favorite");
                ((Button) dialog.lookup(".status-modal-primary")).fire();
                return null;
            });
            opened.get(5, TimeUnit.SECONDS);
            assertTrue(h.gateway.hydrationStarted.await(5, TimeUnit.SECONDS));
            // Hydration is deliberately blocked: the FX thread must remain responsive and disk state unchanged.
            assertEquals("responsive", fx(() -> "responsive"));
            assertEquals(saved, h.store.items().getFirst());
            h.gateway.releaseHydration.countDown();
            await(() -> h.store.items().getFirst().name().equals("Renamed favorite"));
            WardrobeItem persisted = new WardrobeStore(directory).items().getFirst();
            assertEquals("Renamed favorite", persisted.name());
            assertEquals(saved.collection(), persisted.collection());
            assertTrue(persisted.favorite());
            assertEquals(saved.id(), persisted.id());
            assertEquals(saved.payload(), persisted.payload());

            FutureTask<Void> dismissed = submitFx(() -> { button(h.root(), "Edit saved look").fire(); return null; });
            await(() -> h.dialog() != null);
            fx(() -> {
                nodes(h.dialog(), TextField.class).getFirst().setText("Unsaved change");
                ((Button) h.dialog().lookup(".status-modal-close")).fire();
                return null;
            });
            dismissed.get(5, TimeUnit.SECONDS);
            assertEquals("Renamed favorite", h.store.items().getFirst().name());

            FutureTask<Void> removed = submitFx(() -> { button(h.root(), "Edit saved look").fire(); return null; });
            await(() -> h.dialog() != null);
            fx(() -> {
                Button remove = (Button) h.dialog().lookup(".status-modal-secondary");
                assertEquals("Remove saved look", remove.getText());
                remove.fire();
                return null;
            });
            removed.get(5, TimeUnit.SECONDS);
            await(() -> h.store.items().isEmpty());
        }
    }

    @Test void skinGridFillsAvailableWidthAndRepaginatesWithoutMissingResults() throws Exception {
        try (Harness h = new Harness()) {
            List<WardrobeItem> all = new ArrayList<>();
            for (int i = 0; i < 83; i++) all.add(new WardrobeItem(new UUID(0, i + 100),
                    WardrobeItem.Kind.SKIN, "Grid " + i, false, "", SKIN.payload()));
            h.gateway.skins = List.copyOf(all);
            fx(() -> { h.root().setManaged(false); ((javafx.scene.layout.Region) h.root()).resize(1750, 800); button(h.root(), "Popular skins").fire(); h.controller.refresh(); return null; });
            await(() -> gridReady(h));
            fx(() -> {
                assertNull(h.root().lookup("#wardrobe-saved-filter"), "Look filter dropdown must be absent");
                assertGridWidth(h); return null;
            });
            // The explicit layout above can trigger responsive repagination.
            await(() -> gridReady(h));
            List<String> seen = new ArrayList<>();
            while (true) {
                List<String> current = fx(() -> gridCards(h).stream().map(Button::getId).toList());
                seen.addAll(current);
                if (fx(() -> button(h.root(), "Next Page").isDisabled())) break;
                int count = fx(() -> ((javafx.scene.layout.GridPane)h.root().lookup("#wardrobe-cards")).getColumnConstraints().size());
                assertEquals(count * 4, current.size(), "Every nonfinal page must have four full rows");
                String first = current.getFirst();
                fx(() -> { button(h.root(), "Next Page").fire(); return null; });
                await(() -> !gridCards(h).isEmpty() && !gridCards(h).getFirst().getId().equals(first)
                        && nodes(h.root(), Label.class).stream().noneMatch(l -> l.getText().equals("Loading looks…")));
            }
            assertEquals(all.stream().map(item -> "wardrobe-look-" + item.id()).toList(), seen,
                    "Provider page boundaries must not duplicate or skip skins");
            for (int width : new int[]{1100, 1450}) {
                fx(() -> { ((javafx.scene.layout.Region) h.root()).resize(width, 800); return null; });
                await(() -> gridReady(h) && gridCards(h).getFirst().getId().equals("wardrobe-look-" + all.getFirst().id()));
                fx(() -> { assertGridWidth(h); return null; });
            }
        }
    }

    @Test void sharedPagerHidesSinglePagesAndNavigatesSavedLooks() throws Exception {
        try (Harness h = new Harness()) {
            h.store.saveItem(CAPE);
            fx(() -> { button(h.root(), "Saved looks").fire(); return null; });
            await(() -> card(h.root(), "Catalog cape") != null);
            fx(() -> {
                Node pager = h.root().lookup("#wardrobe-pagination");
                assertTrue(pager instanceof WardrobePagination);
                assertFalse(pager.isVisible()); assertFalse(pager.isManaged());
                return null;
            });
            for (int i = 0; i < 65; i++) h.store.saveItem(new WardrobeItem(new UUID(0, i + 100),
                    WardrobeItem.Kind.SKIN, "Saved " + i, false, "", SKIN.payload()));
            fx(() -> { h.controller.refresh(); return null; });
            await(() -> h.root().lookup("#wardrobe-pagination").isVisible()
                    && !h.root().lookup("#wardrobe-pagination").isDisabled());
            String first = fx(() -> gridCards(h).getFirst().getAccessibleText());
            fx(() -> { button(h.root(), "Page 2").fire(); return null; });
            await(() -> !gridCards(h).isEmpty() && !gridCards(h).getFirst().getAccessibleText().equals(first)
                    && !h.root().lookup("#wardrobe-pagination").isDisabled());
            fx(() -> {
                var input = (TextField) h.root().lookup("#wardrobe-pagination").lookup(".pagination-jump-input");
                input.setText("1"); button(h.root(), "Go to page").fire(); return null;
            });
            await(() -> !gridCards(h).isEmpty() && gridCards(h).getFirst().getAccessibleText().equals(first));
            h.gateway.skins = List.of(SKIN);
            fx(() -> { button(h.root(), "Popular skins").fire(); return null; });
            await(() -> h.root().lookup("#wardrobe-look-" + SKIN.id()) != null);
            fx(() -> {
                assertFalse(h.root().lookup("#wardrobe-pagination").isVisible());
                assertFalse(h.root().lookup("#wardrobe-pagination").isManaged());
                return null;
            });
        }
    }

    @Test void popularFeedIsLazyAndDownloadedDefinitionIsAppliedWithoutProviderIdentity() throws Exception {
        try (Harness h = new Harness()) {
            h.popular.useArchivedEntry = true;
            assertTrue(fx(() -> nodes(h.root(), javafx.scene.control.ToggleButton.class).stream()
                    .map(javafx.scene.control.ToggleButton::getText).toList()
                    .containsAll(List.of("Customize", "Popular skins", "Saved looks"))));
            assertTrue(fx(() -> nodes(h.root(), javafx.scene.control.ToggleButton.class).stream()
                    .noneMatch(b -> b.getText().equals("Local outfits"))));
            h.store.saveItem(SKIN);
            fx(() -> { button(h.root(), "Saved looks").fire(); return null; });
            await(() -> h.root().lookup("#wardrobe-look-" + SKIN.id()) != null);
            assertEquals(0, h.popular.pageCalls.get()); assertEquals(0, h.popular.thumbnailCalls.get());
            fx(() -> { button(h.root(), "Popular skins").fire(); return null; });
            await(() -> h.popular.downloadCalls.get() == 1 && buttonEnabled(h.root(), "Save look"));
            assertTrue(h.popular.thumbnailCalls.get() > 0);
            assertTrue(fx(() -> nodes(h.root(), Label.class).stream().anyMatch(l -> l.isVisible() && l.getText().contains("Images by Hyvatar"))));
            fx(() -> { button(h.root(), "Apply to Alice").fire(); return null; });
            Apply applied = h.gateway.applies.poll(5, TimeUnit.SECONDS);
            assertNotNull(applied);
            assertTrue(applied.item().payload().contains("bodyCharacteristic"));
            assertFalse(applied.item().payload().contains("username"));
            await(() -> buttonEnabled(h.root(), "Apply to Alice"));
            h.store.saveItem(applied.item());
            int thumbnails = h.popular.thumbnailCalls.get(), downloads = h.popular.downloadCalls.get();
            fx(() -> { button(h.root(), "Saved looks").fire(); return null; });
            await(() -> h.root().lookup("#wardrobe-look-" + applied.item().id()) != null);
            assertEquals(thumbnails, h.popular.thumbnailCalls.get()); assertEquals(downloads, h.popular.downloadCalls.get());
            assertTrue(fx(() -> nodes(h.root(), Label.class).stream().noneMatch(l -> l.isVisible() && l.getText().contains("Images by Hyvatar"))));
        }
    }

    @Test void leavingPopularFeedDiscardsAnInFlightDownload() throws Exception {
        try (Harness h = new Harness()) {
            h.store.saveItem(SKIN);
            h.popular.useArchivedEntry = true;
            h.popular.blockDownloads = true;
            fx(() -> { button(h.root(), "Popular skins").fire(); return null; });
            await(() -> h.popular.downloadCalls.get() == 1);
            assertTrue(fx(() -> button(h.root(), "Save look").isDisabled()));
            fx(() -> { button(h.root(), "Saved looks").fire(); return null; });
            await(() -> h.root().lookup("#wardrobe-look-" + SKIN.id()) != null);
            h.popular.release.countDown();
            fx(() -> { button(h.root(), "Apply to Alice").fire(); return null; });
            Apply applied = h.gateway.applies.poll(5, TimeUnit.SECONDS);
            assertNotNull(applied); assertEquals(SKIN.id(), applied.item().id());
        }
    }

    private static class PopularFixture extends net.modtale.launcher.wardrobe.PopularSkinClient {
        final java.util.concurrent.atomic.AtomicInteger pageCalls = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger thumbnailCalls = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger downloadCalls = new java.util.concurrent.atomic.AtomicInteger();
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean blockDownloads, useArchivedEntry;
        final java.util.function.Supplier<List<WardrobeItem>> skins;
        PopularFixture(java.util.function.Supplier<List<WardrobeItem>> skins) { this.skins = skins; }
        final WardrobeItem entry = new WardrobeItem(UUID.randomUUID(), WardrobeItem.Kind.SKIN, "Popular fixture", false, "",
                "{\"skinId\":\"aa6a8b217c26ee19a02791eb2064b513\"}");
        @Override public Page page(int page) {
            pageCalls.incrementAndGet(); var items = useArchivedEntry ? List.of(entry) : skins.get();
            int offset = (page - 1) * 20;
            return new Page(items.stream().skip(offset).limit(20).toList(), items.size() > offset + 20);
        }
        @Override public String thumbnail(String hash) { thumbnailCalls.incrementAndGet(); return ""; }
        @Override public WardrobeItem download(String hash) {
            assertFalse(Platform.isFxApplicationThread()); downloadCalls.incrementAndGet();
            if (blockDownloads) try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            return new WardrobeItem(entry.id(), entry.kind(), entry.name(), false, "",
                    "{\"skinId\":\"" + hash + "\",\"skin\":{\"bodyCharacteristic\":\"Default.01\"}}");
        }
    }

    private static List<Button> gridCards(Harness h) {
        return nodes(h.root().lookup("#wardrobe-cards"), Button.class).stream()
                .filter(b -> b.getStyleClass().contains("wardrobe-card")).toList();
    }
    private static boolean gridReady(Harness h) {
        h.root().applyCss(); ((javafx.scene.Parent) h.root()).layout();
        var grid = (javafx.scene.layout.GridPane)h.root().lookup("#wardrobe-cards");
        return gridCards(h).size() == grid.getColumnConstraints().size() * 4 && !button(h.root(), "Next Page").isDisabled();
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
        final PopularFixture popular = new PopularFixture(() -> gateway.skins);
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
                var feedback = new LauncherFeedback(executor, new Label(), new StackPane(), new Label(), new Label(), () -> "Ready");
                var preview = new WardrobePreview(executor, false);
                return new LauncherWardrobeController(gateway, store, settings::get, feedback, executor, preview, item -> "", popular);
            });
            stage = fx(() -> {
                Stage stage = new Stage();
                Scene scene = new Scene(new StackPane(controller.view()), 1100, 800);
                scene.getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                stage.setScene(scene); stage.show();
                assertTrue(((ToggleButton) button(controller.view(), "Customize")).isSelected());
                assertNotNull(button(controller.view(), "Load current look"));
                return stage;
            });
        }
        Node root() { return controller.view(); }
        Node dialog() {
            return stage.getScene().getRoot().lookup(".status-modal");
        }
        @Override public void close() throws Exception {
            popular.release.countDown();
            gateway.releaseHydration.countDown();
            fx(() -> {
                Node dialog = dialog();
                if (dialog != null) ((Button)dialog.lookup(".status-modal-close")).fire();
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
    private static boolean buttonEnabled(Node root, String text) {
        return nodes(root, ButtonBase.class).stream().anyMatch(b ->
                (text.equals(b.getText()) || text.equals(b.getAccessibleText())) && !b.isDisabled());
    }
    private static ButtonBase button(Node root, String text) { return nodes(root, ButtonBase.class).stream().filter(b -> (text.equals(b.getText()) || text.equals(b.getAccessibleText()))).findFirst().orElseThrow(); }
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
