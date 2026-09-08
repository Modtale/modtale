package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.*;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.model.project.*;
import net.modtale.launcher.ui.browse.card.*;
import net.modtale.launcher.ui.common.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LauncherSkeletonTest {
    @BeforeAll
    static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { /* Shared toolkit. */ }
        catch (UnsupportedOperationException unavailable) {
            org.junit.jupiter.api.Assumptions.abort("JavaFX display unavailable: " + unavailable.getMessage());
        }
    }

    @Test
    void preservesRendererGeometryAndPositionsAtBothWidths() throws Exception {
        fx(() -> {
            LauncherFonts.load();
            String asset = getClass().getResource("/net/modtale/launcher/ui/nativefx/assets/project-placeholder.png").toExternalForm();
            ProjectCardFactory factory = new ProjectCardFactory(url -> {
                assertFalse(url.startsWith("http"), "Skeleton must not request remote assets");
                return asset;
            }, command -> fail("Skeleton must not schedule work"));
            for (ProjectCardViewStyle style : ProjectCardViewStyle.values()) {
                for (double width : new double[]{360, 460}) {
                    Pane source = (Pane) factory.create(LauncherSkeletonContent.project(), style, "2026.1", false,
                            p -> fail(), p -> fail(), p -> fail(), p -> fail(), width, style == ProjectCardViewStyle.COMPACT ? 90 : 380);
                    StackPane root = scene(source, width, 400);
                    Map<Node, List<Double>> before = new IdentityHashMap<>();
                    remember(source, before);
                    LauncherSkeleton.of(source);
                    layout(root);
                    before.forEach((node, geometry) -> assertEquals(geometry, geometry(node),
                            style + " " + node.getStyleClass() + " must keep its layout"));
                    assertTrue(source.isDisabled());
                    assertTrue(source.isMouseTransparent());
                    for (Node button : source.lookupAll(".button")) assertTrue(button.isDisabled());
                    if (style == ProjectCardViewStyle.GRID) {
                        Node badge = source.lookup(".classification-badge");
                        assertNotNull(badge);
                        Bounds slot = badge.localToScene(badge.getLayoutBounds());
                        assertTrue(source.lookupAll(".loading-skeleton-block").stream().anyMatch(bar -> {
                            Bounds painted = bar.localToScene(bar.getLayoutBounds());
                            return painted.getWidth() < slot.getWidth() && painted.getHeight() < slot.getHeight()
                                    && slot.contains(painted.getCenterX(), painted.getCenterY());
                        }), "Banner media must not erase the badge slot");
                        assertFalse(source.lookupAll(".loading-skeleton-media").isEmpty());
                    }
                }
            }
            return null;
        });
    }

    @Test
    void wrappedTextUsesSeparateGlyphLinesAndTracksResizing() throws Exception {
        fx(() -> {
            Label copy = new Label("A community project with improvements for your world and your friends.");
            copy.setWrapText(true);
            VBox content = LauncherSkeleton.of(new VBox(copy));
            StackPane root = scene(content, 260, 200);
            int wide = content.lookupAll(".loading-skeleton-block").size();
            assertTrue(wide >= 2, "Wrapped text needs multiple line bars");
            root.resize(140, 200);
            layout(root);
            long narrow = content.lookupAll(".loading-skeleton-block").stream().filter(Node::isVisible).count();
            assertTrue(narrow > wide, "Narrow layout must follow the new text wrapping");
            return null;
        });
    }

    @Test
    void initialDetailAndErrorFallbackDoNotStartPlaceholderRequests() throws Exception {
        fx(() -> {
            String asset = getClass().getResource("/net/modtale/launcher/ui/nativefx/assets/project-placeholder.png").toExternalForm();
            java.util.concurrent.Executor noWork = command -> fail("Rendering placeholders must not schedule API work");
            CachedImageLoader images = new CachedImageLoader(url -> asset, noWork);
            ProjectPageController controller = new ProjectPageController(new ModtaleApiClient("http://localhost:1"), noWork,
                    images, new ProjectCardFactory(url -> asset, noWork), p -> {}, (p,v,g) -> {}, () -> {}, () -> {},
                    (t,m) -> {}, () -> "2026.1", () -> null, u -> {}, () -> {}, id -> false, p -> {}, null);
            Method render = ProjectPageController.class.getDeclaredMethod("projectPage", ProjectSummary.class, ProjectDetail.class, boolean.class);
            render.setAccessible(true);
            Node loading = (Node) render.invoke(controller, LauncherSkeletonContent.project(), null, true);
            scene(loading, 1440, 1600);
            assertFalse(loading.lookupAll(".loading-skeleton").isEmpty());
            Node fallback = (Node) render.invoke(controller, LauncherSkeletonContent.project(), null, false);
            scene(fallback, 1440, 1600);
            assertTrue(fallback.lookupAll(".loading-skeleton").isEmpty(), "Failed detail must not leave perpetual comment skeletons");
            NativeWikiView wiki = new NativeWikiView(new NativeMarkdownRenderer(images, url -> {}), url -> {});
            Node errorSidebar = wiki.sidebar(null, "", Map.of(), slug -> {}, slug -> {}, () -> {}, false, false);
            scene(errorSidebar, 320, 600);
            assertTrue(errorSidebar.lookupAll(".loading-skeleton").isEmpty(), "Wiki errors must stop navigation skeletons");
            return null;
        });
    }

    private StackPane scene(Node content, double width, double height) {
        StackPane root = new StackPane(content);
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
        root.resize(width, height);
        layout(root);
        return root;
    }
    private static void layout(Parent root) {
        for (int i = 0; i < 3; i++) { root.applyCss(); root.layout(); }
    }
    private static List<Double> geometry(Node node) {
        Bounds bounds = node.getLayoutBounds();
        return List.of(node.getLayoutX(), node.getLayoutY(), bounds.getWidth(), bounds.getHeight());
    }
    private static void remember(Node node, Map<Node, List<Double>> geometry) {
        geometry.put(node, geometry(node));
        if (node instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(child -> remember(child, geometry));
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }
}
