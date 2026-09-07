package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javax.imageio.ImageIO;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.hytale.*;
import net.modtale.launcher.model.project.*;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.ui.account.LauncherAccountController;
import net.modtale.launcher.ui.activity.*;
import net.modtale.launcher.ui.browse.card.*;
import net.modtale.launcher.ui.browse.render.ProjectBrowserRenderer;
import net.modtale.launcher.ui.common.*;
import net.modtale.launcher.ui.play.LauncherPlayController;
import org.junit.jupiter.api.Test;

/** Opt in with MODTALE_LAUNCHER_SKELETON_REVIEW=/absolute/output/path. No HTTP or launcher services. */
class LauncherSkeletonVisualTest {
    private CachedImageLoader images;
    private ProjectCardFactory cards;
    private final List<String> manifest = new ArrayList<>();

    @Test
    void captureAllLoadingSurfaces() throws Exception {
        String output = System.getenv().getOrDefault("MODTALE_LAUNCHER_SKELETON_REVIEW", "");
        assumeTrue(!output.isBlank(), "Opt-in native skeleton review");
        Path directory = Path.of(output);
        Files.createDirectories(directory);
        CountDownLatch startup = new CountDownLatch(1);
        try { Platform.startup(startup::countDown); }
        catch (IllegalStateException started) { startup.countDown(); }
        assertTrue(startup.await(10, TimeUnit.SECONDS));
        onFx(() -> {
            LauncherFonts.load();
            String asset = getClass().getResource("/net/modtale/launcher/ui/nativefx/assets/favicon.png").toExternalForm();
            images = new CachedImageLoader(url -> asset, Runnable::run);
            cards = new ProjectCardFactory(url -> asset, Runnable::run);
            for (int width : new int[]{980, 1440}) {
                for (ProjectCardViewStyle style : ProjectCardViewStyle.values()) {
                    pair(directory, "browse-" + style.name().toLowerCase() + "-" + width,
                            browse(width, style, true), browse(width, style, false), width, 900);
                }
            }
            LauncherUiPerformanceProfileTest pages = new LauncherUiPerformanceProfileTest();
            for (boolean compact : new boolean[]{false, true}) {
                int width = compact ? 760 : 1440;
                for (boolean wiki : new boolean[]{false, true}) {
                    pair(directory, (wiki ? "wiki" : "project-initial") + "-" + width,
                            pages.buildProjectPageNode(width, 1800, compact, wiki, true),
                            pages.buildProjectPageNode(width, 1800, compact, wiki, false), width, 1800);
                }
                var projects = new ProjectPage(Collections.nCopies(compact ? 3 : 8, LauncherSkeletonContent.project()), 1, 8, 0, true);
                pair(directory, "creator-" + width,
                        pages.creatorProfileView().render(null, null, List.of(), true, compact),
                        pages.creatorProfileView().render(LauncherSkeletonContent.creator(), projects, List.of(), false, compact),
                        width, 1800);
            }
            for (boolean external : new boolean[]{false, true}) {
                pair(directory, external ? "comments-curseforge" : "comments",
                        comments(true, external), comments(false, external), 900, 750);
            }
            pair(directory, "downloads", download(true), download(false), 760, 650);
            pair(directory, "changelog", changelog(true), changelog(false), 800, 1000);
            pair(directory, "changelog-more", changelog(true, true), changelog(false, true), 800, 700);
            pair(directory, "following", following(true), following(false), 520, 600);
            pair(directory, "notifications", notifications(true), notifications(false), 500, 550);
            pair(directory, "friends", play(true, false), play(false, false), 320, 260);
            pair(directory, "news", play(true, true), play(false, true), 320, 800);
            pair(directory, "library-releases", library(true, false), library(false, false), 760, 200);
            pair(directory, "library-world-releases", library(true, true), library(false, true), 760, 160);
            Files.write(directory.resolve("pairs.txt"), manifest);
            return null;
        });
    }

    private Node browse(int width, ProjectCardViewStyle style, boolean loading) {
        StackPane results = new StackPane();
        VBox body = new VBox(results);
        body.resize(width, 900);
        results.resize(width, 900);
        ProjectBrowserRenderer renderer = new ProjectBrowserRenderer(results, new StackPane(), () -> body,
                cards, id -> false, () -> "2026.1", p -> {}, p -> {}, p -> {}, p -> {});
        // Match the number of placeholders; source content and layout are otherwise identical.
        int count = style == ProjectCardViewStyle.LIST ? 4 : width >= (style == ProjectCardViewStyle.GRID ? 1320 : 1120) ? 6 : 4;
        if (loading) renderer.renderLoading(style, count);
        else {
            List<ProjectSummary> projects = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ProjectSummary p = LauncherSkeletonContent.project();
                projects.add(new ProjectSummary("fixture-" + i, "fixture-" + i, p.title(), p.description(),
                        p.authorId(), p.author(), p.imageUrl(), p.bannerUrl(), p.classification(),
                        p.downloadCount(), p.favoriteCount(), p.updatedAt(), p.versions()));
            }
            renderer.render(projects, style, count);
        }
        return body;
    }

    private Node comments(boolean loading, boolean external) {
        NativeCommentSection section = new NativeCommentSection(images, new NativeMarkdownRenderer(images, url -> {}),
                () -> null, () -> {}, (id, name) -> {}, null, () -> {});
        ProjectSummary base = LauncherSkeletonContent.project();
        ProjectSummary summary = external ? new ProjectSummary("curseforge:1", "curseforge:1", base.title(),
                base.description(), "", base.author(), "", "", "MOD", 12000, 0, base.updatedAt(), base.versions(),
                "CURSEFORGE", "", true) : base;
        return section.render(summary, LauncherSkeletonContent.detail(),
                loading ? List.of() : Collections.nCopies(3, LauncherSkeletonContent.comment()), Map.of(),
                loading, false, 3, false, false);
    }

    private Node download(boolean loading) throws Exception {
        NativeDownloadModal modal = new NativeDownloadModal(null, () -> "2026.1", selection -> {}, p -> {});
        field(modal, "project", loading ? LauncherSkeletonContent.detail().withVersions(List.of()) : LauncherSkeletonContent.detail());
        field(modal, "loading", loading);
        field(modal, "selectedGameVersions", loading ? List.of() : List.of("2026.1"));
        return (Node) invoke(modal, "modal", new StackPane());
    }

    private ProjectPageController projectController() {
        return new ProjectPageController(new ModtaleApiClient("http://localhost:1"), command -> {}, images, cards,
                p -> {}, (p,v,g) -> {}, () -> {}, () -> {}, (t,m) -> {}, () -> "2026.1", () -> null,
                u -> {}, () -> {}, id -> false, p -> {}, null);
    }

    private Node changelog(boolean loading) throws Exception {
        return changelog(loading, false);
    }

    private Node changelog(boolean loading, boolean more) throws Exception {
        Object controller = projectController();
        Class<?> entry = Class.forName(ProjectPageController.class.getName() + "$ChangelogEntry");
        Method from = entry.getDeclaredMethod("from", ProjectVersion.class, String.class);
        from.setAccessible(true);
        Object version = from.invoke(null, LauncherSkeletonContent.version(), LauncherSkeletonContent.version().changelog());
        return (Node) invoke(controller, "changelogModal", more ? Collections.nCopies(loading ? 1 : 2, version)
                : loading ? List.of() : Collections.nCopies(3, version), loading);
    }

    private Node following(boolean loading) throws Exception {
        var account = new LauncherAccountController(null, null);
        var controller = new LauncherFollowingController(null, account, null, images);
        field(controller, "loading", loading);
        field(controller, "users", loading ? List.of() : Collections.nCopies(4, LauncherSkeletonContent.user()));
        Node result = controller.modal();
        result.setVisible(true);
        result.setManaged(true);
        return result;
    }

    private Node notifications(boolean loading) throws Exception {
        var account = new LauncherAccountController(null, null);
        var controller = new LauncherNotificationsMenu(null, account, null, images);
        VBox result = controller.panel();
        field(controller, "loading", loading);
        field(controller, "notifications", loading ? List.of() : Collections.nCopies(3, LauncherSkeletonContent.notification()));
        invoke(controller, "renderNotifications");
        result.setVisible(true);
        return result;
    }

    private Node play(boolean loading, boolean news) throws Exception {
        var controller = new LauncherPlayController(null, cards, null, null, null, null, null,
                command -> {}, null, null, null, null, null, null);
        VBox list = (VBox) field(controller, news ? "newsList" : "friendsList");
        list.getStyleClass().add(news ? "play-news-list" : "play-friends-list");
        if (loading) invoke(controller, news ? "renderNewsLoading" : "renderFriendsLoading");
        else if (news) {
            for (int i = 0; i < 3; i++) list.getChildren().add((Node) invoke(controller, "blogPostRow",
                    new HytaleBlogPost("The latest news from Hytale", "", "", Instant.parse(LauncherSkeletonContent.DATE))));
        } else invoke(controller, "renderFriends", Collections.nCopies(3,
                new HytaleFriend("", "Community friend", "Online", "", true)));
        return list;
    }

    private Node library(boolean loading, boolean world) throws Exception {
        String base = "net.modtale.launcher.ui.library.";
        Class<?> rendererType = Class.forName(base + (world ? "LibraryWorldRenderer" : "LibraryProjectRenderer"));
        Constructor<?> constructor = rendererType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] args = new Object[constructor.getParameterCount()];
        if (world) args[0] = images;
        Object renderer = constructor.newInstance(args);
        InstalledProject installed = new InstalledProject("loading-project", "loading-project", "Community Project", "MOD",
                "1.0.0", "installed", "2026.1", Instant.EPOCH, Instant.EPOCH, List.of(), List.of(), List.of());
        Class<?> modelType = Class.forName(base + (world ? "LibraryWorldProjectModel" : "LibraryDetailModel"));
        Constructor<?> modelConstructor = Arrays.stream(modelType.getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == (world ? 11 : 7)).findFirst().orElseThrow();
        modelConstructor.setAccessible(true);
        Object detail = loading ? null : LauncherSkeletonContent.detail();
        Object model = world ? modelConstructor.newInstance(installed, detail, null, null, loading, List.of(), 0, 0, List.of(), null, false)
                : modelConstructor.newInstance(installed, detail, null, loading, new LauncherSettings(), List.of(), List.of());
        return (Node) invoke(renderer, world ? "versionControls" : "versionSection", model);
    }

    private void pair(Path directory, String name, Node skeleton, Node loaded, int width, int height) throws Exception {
        capture(skeleton, width, height, directory.resolve(name + "-skeleton.png"));
        capture(loaded, width, height, directory.resolve(name + "-loaded.png"));
        assertFalse(skeleton.lookupAll(".loading-skeleton-block").isEmpty(), name + " must actually paint skeleton bars");
        manifest.add(name + "-skeleton.png  " + name + "-loaded.png");
    }

    private void capture(Node content, int width, int height, Path destination) throws Exception {
        StackPane root = new StackPane(content);
        root.setAlignment(Pos.TOP_LEFT);
        root.setStyle("-fx-background-color: #0b1120;");
        if (content instanceof Region region) region.setMaxHeight(Region.USE_PREF_SIZE);
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
        root.resize(width, height);
        // Skin creation and then layout of the unmanaged mask, just as on consecutive FX pulses.
        for (int i = 0; i < 3; i++) {
            root.applyCss();
            if (!content.isManaged()) content.autosize(); // Popovers are positioned/autosized by their production host.
            root.layout();
        }
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setViewport(new javafx.geometry.Rectangle2D(0, 0, width, height));
        WritableImage image = root.snapshot(parameters, new WritableImage(width, height));
        BufferedImage png = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) png.setRGB(x, y, image.getPixelReader().getArgb(x,y));
        ImageIO.write(png, "png", destination.toFile());
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        Method method = Arrays.stream(target.getClass().getDeclaredMethods())
                .filter(m -> m.getName().equals(name) && m.getParameterCount() == args.length).findFirst().orElseThrow();
        method.setAccessible(true);
        return method.invoke(target, args);
    }
    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static <T> T onFx(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { result.set(action.call()); } catch (Throwable failure) { error.set(failure); }
            finally { latch.countDown(); }
        });
        assertTrue(latch.await(60, TimeUnit.SECONDS), "Snapshot harness timed out");
        if (error.get() != null) throw new AssertionError(error.get());
        return result.get();
    }
}
