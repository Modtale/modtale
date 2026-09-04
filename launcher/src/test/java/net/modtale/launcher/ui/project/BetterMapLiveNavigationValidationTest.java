package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.api.ProjectSearchQuery;
import net.modtale.launcher.model.project.ProjectComment;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;
import net.modtale.launcher.ui.common.CachedImageLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.io.TempDir;

class BetterMapLiveNavigationValidationTest {

    private static final long BETTERMAP_PROJECT_ID = 1_430_352L;
    private static final long COMMENTS_RENDER_BUDGET_MILLIS = 8_000;

    @BeforeAll
    static void startJavaFx() throws Exception {
        if (!liveTestsEnabled()) return;
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS), "JavaFX did not start");
    }

    @RepeatedTest(3)
    void betterMapBrowseCardNavigatesToAProjectPageAndRendersLiveCurseForgeComments(@TempDir Path tempDir)
            throws Exception {
        assumeTrue(liveTestsEnabled(),
                "Set CURSEFORGE_LIVE_TESTS=true to run the BetterMap launcher navigation contract.");
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            ModtaleApiClient client = new ModtaleApiClient(ModtaleApiClient.DEFAULT_API_BASE_URL);
            long searchStarted = System.nanoTime();
            ProjectSummary betterMap = client.searchCurseForgeMods(new ProjectSearchQuery(
                            "BetterMap", "mods", null, "relevance", 0, 20,
                            null, null, null, null, null, null))
                    .content().stream()
                    .filter(project -> project.curseForgeProjectId() == BETTERMAP_PROJECT_ID)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("BetterMap was not present in CurseForge browse results"));
            long searchMillis = elapsedMillis(searchStarted);
            assertEquals("BetterMap", betterMap.title());
            assertEquals("curseforge:" + BETTERMAP_PROJECT_ID, betterMap.routeKey());

            AtomicInteger navigations = new AtomicInteger();
            AtomicReference<String> toast = new AtomicReference<>();
            ProjectPageController controller = callOnFx(() -> {
                CachedImageLoader images = new CachedImageLoader(url -> url, executor, tempDir.resolve("images"));
                ProjectPageController value = new ProjectPageController(
                        client,
                        executor,
                        images,
                        new ProjectCardFactory(url -> url, executor),
                        project -> {},
                        (project, version, gameVersion) -> {},
                        () -> {},
                        navigations::incrementAndGet,
                        (title, message) -> toast.set(title + ": " + message),
                        () -> "Early Access",
                        () -> null,
                        user -> {},
                        () -> {},
                        id -> false,
                        project -> {},
                        null
                );
                Node view = value.view();
                StackPane root = new StackPane(view);
                new Scene(root, 1_280, 1_200);
                root.applyCss();
                root.layout();
                return value;
            });

            long navigationStarted = System.nanoTime();
            callOnFx(() -> {
                controller.openProject(betterMap);
                return null;
            });

            UiState state = awaitRenderedComments(controller, COMMENTS_RENDER_BUDGET_MILLIS);
            long navigationMillis = elapsedMillis(navigationStarted);

            assertEquals(1, navigations.get(), "Selecting the BetterMap card should navigate to the project view");
            assertNotNull(state.detail());
            assertEquals("BetterMap", state.detail().title());
            assertFalse(state.comments().isEmpty(), "BetterMap should render at least one CurseForge comment");
            assertTrue(state.renderedCommentCards() > 0, "The project page should contain rendered comment cards");
            assertTrue(state.commentsHeading().endsWith(" CurseForge Comments"));
            assertTrue(state.directNoticeVisible(), "The project page should identify comments as directly loaded");
            assertTrue(navigationMillis < COMMENTS_RENDER_BUDGET_MILLIS,
                    "BetterMap comments took " + navigationMillis + "ms to render");
            assertTrue(toast.get() == null, "Navigation raised a launcher error: " + toast.get());

            System.out.printf("BetterMap live launcher validation: search=%dms, navigate-to-comments=%dms, "
                            + "renderedRoots=%d, reportedTotal=%s%n",
                    searchMillis, navigationMillis, state.comments().size(), state.commentsHeading());
        } finally {
            executor.shutdownNow();
        }
    }

    private static UiState awaitRenderedComments(ProjectPageController controller, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        UiState latest = null;
        while (System.nanoTime() < deadline) {
            latest = callOnFx(() -> state(controller));
            if (latest.detail() != null && !latest.commentsLoading() && !latest.comments().isEmpty()
                    && latest.renderedCommentCards() > 0) {
                return latest;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("BetterMap comments did not render within " + timeoutMillis
                + "ms; latest state=" + latest);
    }

    private static UiState state(ProjectPageController controller) throws Exception {
        ProjectDetail detail = field(controller, "currentDetail", ProjectDetail.class);
        @SuppressWarnings("unchecked")
        List<ProjectComment> comments = (List<ProjectComment>) field(controller, "currentComments", List.class);
        boolean loading = field(controller, "commentsLoading", Boolean.class);
        Node commentsSection = controller.view().lookup("#comments");
        int cards = commentsSection == null ? 0 : commentsSection.lookupAll(".project-comment-card").size();
        String heading = commentsSection == null ? "" : commentsSection.lookupAll(".project-comments-title").stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .map(Label::getText)
                .findFirst().orElse("");
        boolean directNotice = commentsSection != null && commentsSection.lookupAll(".project-comments-disabled-text")
                .stream().filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText)
                .anyMatch(text -> text.contains("directly from CurseForge"));
        return new UiState(detail, List.copyOf(comments), loading, cards, heading, directNotice);
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(target);
        return type.cast(value);
    }

    private static <T> T callOnFx(Callable<T> task) throws Exception {
        if (Platform.isFxApplicationThread()) return task.call();
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                value.set(task.call());
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                complete.countDown();
            }
        });
        assertTrue(complete.await(10, TimeUnit.SECONDS), "JavaFX operation timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
        return value.get();
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static boolean liveTestsEnabled() {
        return "true".equalsIgnoreCase(System.getenv("CURSEFORGE_LIVE_TESTS"));
    }

    private record UiState(
            ProjectDetail detail,
            List<ProjectComment> comments,
            boolean commentsLoading,
            int renderedCommentCards,
            String commentsHeading,
            boolean directNoticeVisible
    ) {}
}
