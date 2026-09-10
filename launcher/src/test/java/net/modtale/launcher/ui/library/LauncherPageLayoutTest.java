package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld;
import net.modtale.launcher.settings.SettingsStore;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;
import net.modtale.launcher.ui.common.LauncherFonts;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.ui.play.LauncherPlayController;
import net.modtale.launcher.ui.settings.LauncherSettingsController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherPageLayoutTest {
    @TempDir Path directory;

    @BeforeAll static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { }
    }

    @Test void playKeepsLaunchVisibleAndBothShelvesReachable() throws Exception {
        fx(() -> {
            java.util.concurrent.Executor paused = command -> {};
            var api = new ModtaleApiClient("http://localhost", directory.resolve("session.json"));
            var settings = new LauncherSettingsController(new SettingsStore(directory.resolve("settings.json")),
                    api, () -> null, () -> LauncherView.PLAY);
            var feedback = new LauncherFeedback(paused, new Label(), new VBox(), new StackPane(),
                    new Label(), new Label(), () -> "");
            var installs = new java.util.concurrent.atomic.AtomicInteger();
            var favorites = new java.util.concurrent.atomic.AtomicInteger();
            var controller = new LauncherPlayController(api, new ProjectCardFactory(url -> getClass().getResource("/net/modtale/launcher/ui/nativefx/assets/project-placeholder.png").toExternalForm(), paused),
                    new net.modtale.launcher.hytale.HytaleAuthService(null, new SettingsStore(directory.resolve("auth.json"))), null, null, settings, feedback, paused, id -> false, () -> "", project -> installs.incrementAndGet(), null, null, project -> favorites.incrementAndGet());
            Node view = controller.view();
            for (String fieldName : List.of("identityTitle", "identitySubtitle")) {
                var field = LauncherPlayController.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                ((Label) field.get(controller)).setText(fieldName.equals("identityTitle") ? "Villagers654" : "2 profiles");
            }
            var shelfType = java.util.Arrays.stream(LauncherPlayController.class.getDeclaredClasses())
                    .filter(type -> type.getSimpleName().equals("CatalogShelf")).findFirst().orElseThrow();
            var render = LauncherPlayController.class.getDeclaredMethod("renderCatalogShelf", shelfType, List.class);
            render.setAccessible(true);
            var projects = java.util.stream.IntStream.range(0, 24).mapToObj(index ->
                    new net.modtale.launcher.model.project.ProjectSummary("fixture-" + index, "fixture-" + index,
                            List.of("Adventure Essentials", "Wild Horizons", "Creative Tools", "Better Worlds", "Inventory Plus", "Waypoints").get(index % 6),
                            "New possibilities for your Hytale worlds.", "creator", "Community creator", "", "", "PLUGIN",
                            12400, 820, "2026-09-01T12:00:00Z", List.of())).toList();
            for (String name : List.of("trendingShelf", "newReleasesShelf")) {
                var field = LauncherPlayController.class.getDeclaredField(name);
                field.setAccessible(true);
                render.invoke(controller, field.get(controller), name.equals("trendingShelf") ? projects.subList(0, 12) : projects.subList(12, 24));
            }
            var newsField = LauncherPlayController.class.getDeclaredField("newsList");
            newsField.setAccessible(true);
            var news = (VBox) newsField.get(controller);
            var blogRow = LauncherPlayController.class.getDeclaredMethod("blogPostRow", net.modtale.launcher.news.LauncherNewsPost.class);
            blogRow.setAccessible(true);
            news.getChildren().clear();
            String articleImage = Path.of("../frontend/public/assets/news/fresh-feature-showcase-og.png").toAbsolutePath().toUri().toString();
            var cacheField = LauncherPlayController.class.getDeclaredField("imageCache");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var cache = (java.util.Map<String, javafx.scene.image.Image>) cacheField.get(controller);
            cache.put(articleImage + "|0.0x0.0|true|false", new javafx.scene.image.Image(articleImage));
            for (String title : List.of("Freshly Added: A Visual Tour of Modtale Features", "Building together")) {
                boolean modtale = title.startsWith("Freshly");
                news.getChildren().add((Node) blogRow.invoke(controller,
                        new net.modtale.launcher.news.LauncherNewsPost(title, "https://example.com", modtale ? articleImage : "",
                                Instant.parse("2026-09-01T12:00:00Z"), modtale ? "Modtale" : "Hytale")));
            }
            String placeholder = getClass().getResource("/net/modtale/launcher/ui/nativefx/assets/project-placeholder.png").toExternalForm();
            cache.put(placeholder + "|0.0x0.0|true|false", new javafx.scene.image.Image(placeholder));
            for (String name : List.of("trendingShelf", "newReleasesShelf")) {
                var field = LauncherPlayController.class.getDeclaredField(name);
                field.setAccessible(true);
                render.invoke(controller, field.get(controller), name.equals("trendingShelf") ? projects.subList(0, 12) : projects.subList(12, 24));
            }
            var host = host(view);
            for (int width : new int[] {1280, 1500, 1800}) {
                layout(host, width, width == 1280 ? 800 : 1000);
                assertSame(news, view.lookup(".play-sidebar").lookup(".play-news-grid"));
                Node title = view.lookup(".play-identity-title");
                Node subtitle = view.lookup(".play-identity-subtitle");
                Node avatar = view.lookup(".play-identity-avatar");
                double copyCenter = (title.localToScene(title.getBoundsInLocal()).getMinY()
                        + subtitle.localToScene(subtitle.getBoundsInLocal()).getMaxY()) / 2;
                var avatarBounds = avatar.localToScene(avatar.getBoundsInLocal());
                assertEquals((avatarBounds.getMinY() + avatarBounds.getMaxY()) / 2, copyCenter, 1);
                Node launch = view.lookup(".play-launch-main");
                assertTrue(launch.localToScene(launch.getBoundsInLocal()).getMaxY() < (width == 1280 ? 800 : 1000));
                var scroll = (ScrollPane) view.lookup(".play-stage-scroll");
                assertTrue(scroll.getViewportBounds().getHeight() > 0);
                int count = view.lookupAll(".project-card-compact").size();
                assertTrue(count >= 2);
                assertTrue(view.lookupAll(".project-card-grid").isEmpty());
                if (width == 1280) assertTrue(count < 6);
                if (width >= 1500) assertEquals(6, count);
                Node catalog = view.lookup(".play-catalog-stage");
                assertTrue(catalog.getLayoutBounds().getHeight() < scroll.getViewportBounds().getHeight() - 30,
                        "The dynamic catalog should leave breathing room above Play");
                assertEquals(2, view.lookupAll(".play-catalog-section").size());
                view.lookupAll(".play-catalog-section").forEach(section -> assertTrue(section.isManaged()));
                assertTrue(view.lookup(".play-sidebar-frame").localToScene(
                        view.lookup(".play-sidebar-frame").getBoundsInLocal()).getMaxX() <= width + 1);
                for (int refresh = 0; refresh < 3; refresh++) {
                    news.getChildren().clear();
                    for (int index = 0; index < 8; index++) {
                        news.getChildren().add((Node) blogRow.invoke(controller,
                                new net.modtale.launcher.news.LauncherNewsPost("News update " + index,
                                        "https://example.com", articleImage, Instant.parse("2026-09-01T12:00:00Z"), "Modtale")));
                    }
                    layout(host, width, width == 1280 ? 800 : 1000);
                    for (Node row : news.getChildren()) {
                        Node articleTitle = row.lookup(".play-news-title");
                        assertTrue(articleTitle.localToScene(articleTitle.getBoundsInLocal()).getMaxY()
                                <= row.localToScene(row.getLayoutBounds()).getMaxY() + 1,
                                "The refreshed card must contain its article title");
                    }
                    for (Node node : news.lookupAll(".play-news-thumbnail")) {
                        var thumbnail = (StackPane) node;
                        assertEquals(thumbnail.getWidth() * 9 / 16, thumbnail.getHeight(), 1,
                                "Cached news images must retain their height after refreshing a full feed");
                    }
                }
                Node card = view.lookup(".project-card-compact");
                Node byline = card.lookup(".compact-byline");
                Node stats = card.lookup(".compact-stats");
                assertTrue(stats.localToScene(stats.getBoundsInLocal()).getMinY()
                        >= byline.localToScene(byline.getBoundsInLocal()).getMaxY());
                snapshot(host, "play-" + width);
                var launchBefore = launch.localToScene(launch.getBoundsInLocal());
                scroll.setVvalue(1);
                host.layout();
                assertEquals(launchBefore, launch.localToScene(launch.getBoundsInLocal()));
                assertTrue(view.lookupAll(".launcher-page-eyebrow").isEmpty());
                assertTrue(view.lookupAll(".launcher-page-subtitle").isEmpty());
                snapshot(host, "play-catalog-" + width);
                scroll.setVvalue(0);
            }
            Node compact = view.lookup(".project-card-compact");
            ((javafx.scene.control.Button) compact.lookup(".project-install-button")).fire();
            ((javafx.scene.control.Button) compact.lookup(".favorite-stat")).fire();
            assertEquals(1, installs.get());
            assertEquals(1, favorites.get());
        });
    }

    @Test void libraryLongNamesFitAndSelectionStillWorks() throws Exception {
        fx(() -> {
            var worlds = new VBox(10);
            var detail = new VBox(20);
            var selected = new java.util.concurrent.atomic.AtomicReference<HytaleWorld>();
            var renderer = new LibraryWorldListRenderer(null, selected::set);
            var world = new HytaleWorld(directory, "Explorers of the Forgotten Kingdom", directory.resolve("world.json"),
                    "release", "", 4, 6, Instant.now());
            var row = renderer.worldRow(new LibraryWorldListItem(world, "Release", 4, 6), true);
            worlds.getChildren().add(row);
            var second = new HytaleWorld(directory, "Creative Valley", directory.resolve("other.json"), "release", "", 0, 0, Instant.now());
            worlds.getChildren().add(renderer.worldRow(new LibraryWorldListItem(second, "Release", 0, 0), false));
            var worldRenderer = new LibraryWorldRenderer(null, u -> {}, p -> {}, null, p -> {}, null,
                    p -> {}, null, w -> {}, w -> {}, (s, c) -> {}, () -> {}, () -> {});
            var installed = new net.modtale.launcher.model.install.InstalledProject(
                    "mod", "mod", "Adventure Essentials", "MODPACK", "0.1.1", "v1", "2026.02.17-255364b8e", null, null, null, null, null);
            var display = new LibraryWorldProjectDisplay("Adventure Essentials", "Community creator", "MODPACK", "", "0.1.1", "", false, false, false);
            var project = new LibraryWorldProjectModel(installed, null, null, null, false,
                    List.of(), 0, 0, List.of(), display);
            detail.getChildren().setAll(worldRenderer.worldDetail(new LibraryWorldModel(world, "Release", 1, 1, List.of(project))));
            Node view = new LibraryShellView(worlds, detail).build();
            var host = host(view);
            for (int width : new int[] {1280, 1500, 1800}) {
                layout(host, width, width == 1280 ? 800 : 1000);
                assertTrue(detail.localToScene(detail.getBoundsInLocal()).getMaxX() <= width + 1);
                var title = (Label) row.lookup(".library-project-title");
                assertTrue(title.getWidth() < row.getWidth());
                snapshot(host, "library-" + width);
            }
            row.fire();
            assertEquals(world, selected.get());
            worlds.getChildren().removeLast();
        });
    }

    private StackPane host(Node view) {
        LauncherFonts.load();
        var body = new StackPane(view);
        body.setPadding(net.modtale.launcher.ui.common.LauncherLayout.WORKSPACE_INSETS);
        var shell = new javafx.scene.layout.BorderPane(body);
        shell.setTop(navbar(view.getUserData()));
        var host = new StackPane(shell);
        host.getStyleClass().add("app-root");
        var scene = new Scene(host, 1500, 800);
        scene.getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
        return host;
    }

    private Node navbar(Object selected) {
        var buttons = new java.util.ArrayList<javafx.scene.control.Button>();
        for (String key : List.of("play", "library", "wardrobe")) {
            var glyph = switch (key) {
                case "play" -> net.modtale.launcher.ui.common.LauncherIcons.Glyph.ZAP;
                case "library" -> net.modtale.launcher.ui.common.LauncherIcons.Glyph.SAVE;
                default -> net.modtale.launcher.ui.common.LauncherIcons.Glyph.PALETTE;
            };
            var button = net.modtale.launcher.ui.shell.LauncherNavbar.navigation("nav." + key, glyph, () -> {});
            button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"),
                    selected != null && key.equals(selected.toString().toLowerCase()));
            buttons.add(button);
        }
        var browse = net.modtale.launcher.ui.shell.LauncherNavbar.navigation("nav.browse",
                net.modtale.launcher.ui.common.LauncherIcons.Glyph.GRID, () -> {});
        buttons.add(browse);
        var notifications = new javafx.scene.control.Button(null,
                net.modtale.launcher.ui.common.LauncherIcons.icon(net.modtale.launcher.ui.common.LauncherIcons.Glyph.BELL, 18));
        notifications.getStyleClass().add("notification-menu-button");
        var initial = new Label("?");
        initial.getStyleClass().add("avatar-initial");
        var avatar = new StackPane(initial);
        avatar.getStyleClass().add("avatar");
        var account = new javafx.scene.control.Button(null, avatar);
        account.getStyleClass().add("profile-menu-button");
        return net.modtale.launcher.ui.shell.LauncherNavbar.build(
                net.modtale.launcher.ui.shell.LauncherNavbar.brand(() -> {}), buttons, notifications, account);
    }

    private void layout(StackPane host, double width, double height) {
        host.resize(width, height);
        for (int pass = 0; pass < 5; pass++) { host.applyCss(); host.layout(); }
    }

    private void snapshot(StackPane host, String name) throws Exception {
        String output = System.getenv("MODTALE_PAGE_SNAPSHOTS");
        if (output == null) return;
        java.nio.file.Files.createDirectories(Path.of(output));
        var image = host.snapshot(null, null);
        var buffer = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffer.getHeight(); y++) for (int x = 0; x < buffer.getWidth(); x++)
            buffer.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(buffer, "png", Path.of(output, name + ".png").toFile());
    }

    private void fx(ThrowingRunnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
    interface ThrowingRunnable { void run() throws Exception; }
}
