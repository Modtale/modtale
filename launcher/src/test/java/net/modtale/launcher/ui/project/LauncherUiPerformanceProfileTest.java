package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectPage;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.model.user.CreatorProfile;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;
import net.modtale.launcher.ui.browse.card.ProjectCardViewStyle;
import net.modtale.launcher.ui.browse.render.ProjectBrowserRenderer;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherScrollSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LauncherUiPerformanceProfileTest {

    private static final String RUN_PROPERTY = "modtale.launcher.perfTests";
    private static final int WARMUPS = 80;
    private static final int SAMPLES = 400;
    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final String LOCAL_PROFILE_IMAGE = Path.of(
            "src/main/resources/net/modtale/launcher/ui/nativefx/assets/favicon.png"
    ).toUri().toString();

    @BeforeAll
    static void startJavaFx() throws Exception {
        if (!Boolean.getBoolean(RUN_PROPERTY)) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX did not start");
    }

    @Test
    void profileBrowseProjectAndCreatorSurfaces() throws Exception {
        assumeTrue(Boolean.getBoolean(RUN_PROPERTY), "Enable with -D" + RUN_PROPERTY + "=true");
        callOnFx(() -> {
            ProfileResult banner = profileBannerScrollEffect();
            ProfileResult scrollInput = profileScrollInput();
            ProfileResult browseGrid = profileBrowseRender(ProjectCardViewStyle.GRID);
            ProfileResult browseCompact = profileBrowseRender(ProjectCardViewStyle.COMPACT);
            ProfileResult project = profile("project page build", 12, 80, this::buildProjectPage);
            ProfileResult creator = profile("creator page build", 12, 80, this::buildCreatorPage);
            ProfileResult galleryBuild = profile("gallery carousel build", 12, 80, this::buildGalleryCarousel);
            ProfileResult galleryProgress = profileGalleryProgressTransform();

            printReport("animation", banner);
            printReport("scroll", scrollInput);
            printReport("browse", browseGrid);
            printReport("browse", browseCompact);
            printReport("project", project);
            printReport("creator", creator);
            printReport("gallery", galleryBuild);
            printReport("gallery", galleryProgress);

            assertTrue(banner.p99Micros() < 250, "Banner scroll-effect p99 should be far below 1ms");
            assertTrue(scrollInput.p99Micros() < 1000, "Scroll input p99 should stay below 1ms");
            assertTrue(galleryProgress.p99Micros() < 100, "Gallery progress updates should not trigger layout-heavy work");
            return null;
        });
    }

    private ProfileResult profileBannerScrollEffect() throws Exception {
        Region media = new Region();
        Region fade = new Region();
        SimpleDoubleProperty scrollPixels = new SimpleDoubleProperty();
        NativeBannerScrollEffect.bind(media, fade, scrollPixels, 128);
        int[] index = {0};
        return profile("project+creator banner scroll effect", WARMUPS, SAMPLES,
                () -> scrollPixels.set((index[0]++ * 37) % 1800));
    }

    private ProfileResult profileScrollInput() throws Exception {
        Region content = new Region();
        content.setMinSize(900, 4800);
        content.setPrefSize(900, 4800);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.resize(900, 620);
        StackPane root = new StackPane(scrollPane);
        new Scene(root, 900, 620);
        root.applyCss();
        root.layout();

        LauncherScrollSupport support = new LauncherScrollSupport(() -> root);
        support.configure(scrollPane, false);
        return profile("browse/project/creator scroll input", WARMUPS, SAMPLES,
                () -> scrollPane.fireEvent(scrollEvent(-72)));
    }

    private ProfileResult profileBrowseRender(ProjectCardViewStyle style) throws Exception {
        ProjectBrowserRenderer renderer = browseRenderer();
        List<ProjectSummary> projects = projects(style == ProjectCardViewStyle.COMPACT ? 45 : 12);
        renderer.render(projects, style);
        return profile("browse " + style.name().toLowerCase(java.util.Locale.ROOT) + " cached render", 12, 80,
                () -> renderer.render(projects, style));
    }

    private ProjectBrowserRenderer browseRenderer() {
        StackPane results = new StackPane();
        results.resize(1320, 680);
        StackPane deck = new StackPane();
        deck.resize(1320, 680);
        VBox body = new VBox(results);
        body.resize(1320, 680);
        ProjectCardFactory factory = new ProjectCardFactory(url -> url, DIRECT_EXECUTOR);
        return new ProjectBrowserRenderer(
                results,
                deck,
                () -> body,
                factory,
                id -> false,
                () -> "2026.1",
                project -> {
                },
                project -> {
                },
                project -> {
                },
                project -> {
                }
        );
    }

    private void buildProjectPage() throws Exception {
        ProjectPageController controller = new ProjectPageController(
                new ModtaleApiClient("http://localhost:1"),
                DIRECT_EXECUTOR,
                imageLoader(),
                new ProjectCardFactory(url -> url, DIRECT_EXECUTOR),
                project -> {
                },
                (project, version, gameVersion) -> {
                },
                () -> {
                },
                () -> {
                },
                (title, message) -> {
                },
                () -> "2026.1",
                () -> null,
                user -> {
                },
                () -> {
                },
                id -> false,
                project -> {
                }
        );
        Node content = controller.view();
        content.resize(1320, 880);
        Method projectPage = ProjectPageController.class.getDeclaredMethod(
                "projectPage",
                ProjectSummary.class,
                ProjectDetail.class,
                boolean.class
        );
        projectPage.setAccessible(true);
        Node page = (Node) projectPage.invoke(controller, project(1), detail(), false);
        if (page instanceof Region region) {
            region.resize(1320, Math.max(880, region.prefHeight(1320)));
        }
    }

    private void buildCreatorPage() {
        NativeCreatorProfileView view = new NativeCreatorProfileView(
                imageLoader(),
                new ProjectCardFactory(url -> url, DIRECT_EXECUTOR),
                () -> "2026.1",
                () -> null,
                id -> false,
                project -> {
                },
                project -> {
                },
                project -> {
                },
                project -> {
                },
                () -> {
                },
                () -> {
                },
                () -> {
                },
                profile -> {
                },
                url -> {
                },
                new SimpleDoubleProperty()
        );
        Node page = view.render(creator(), new ProjectPage(projects(12), 1, 12, 0, true), List.of(), false, false);
        if (page instanceof Region region) {
            region.resize(1568, Math.max(880, region.prefHeight(1568)));
        }
    }

    private void buildGalleryCarousel() {
        NativeGalleryCarousel carousel = new NativeGalleryCarousel(imageLoader(), url -> {
        });
        Node node = carousel.render(galleryItems(20), 7, NativeGalleryCarousel.Variant.INLINE);
        StackPane root = new StackPane(node);
        new Scene(root, 1080, 720);
        root.resize(1080, 720);
        root.applyCss();
        root.layout();
    }

    private ProfileResult profileGalleryProgressTransform() throws Exception {
        NativeGalleryCarousel carousel = new NativeGalleryCarousel(imageLoader(), url -> {
        });
        Node node = carousel.render(galleryItems(12), 0, NativeGalleryCarousel.Variant.INLINE);
        StackPane root = new StackPane(node);
        new Scene(root, 1080, 720);
        root.resize(1080, 720);
        root.applyCss();
        root.layout();

        Node fill = root.lookup(".project-gallery-carousel-progress-fill");
        assertTrue(fill != null, "Gallery progress fill should exist");
        Scale scale = fill.getTransforms().stream()
                .filter(Scale.class::isInstance)
                .map(Scale.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Gallery progress should use a transform scale"));
        int[] index = {0};
        return profile("gallery carousel progress transform", WARMUPS, SAMPLES,
                () -> scale.setX((index[0]++ % 100) / 100.0));
    }

    private CachedImageLoader imageLoader() {
        return new CachedImageLoader(url -> url, DIRECT_EXECUTOR);
    }

    private ScrollEvent scrollEvent(double deltaY) {
        return new ScrollEvent(
                ScrollEvent.SCROLL,
                20,
                20,
                20,
                20,
                false,
                false,
                false,
                false,
                false,
                false,
                0,
                deltaY,
                0,
                deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE,
                0,
                ScrollEvent.VerticalTextScrollUnits.NONE,
                deltaY,
                0,
                null
        );
    }

    private ProfileResult profile(String name, int warmups, int samples, ThrowingRunnable action) throws Exception {
        for (int i = 0; i < warmups; i++) {
            action.run();
        }
        List<Long> timings = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            long start = System.nanoTime();
            action.run();
            timings.add(System.nanoTime() - start);
        }
        return ProfileResult.from(name, timings);
    }

    private static void printReport(String phase, ProfileResult result) {
        System.out.printf(
                "%s %s samples=%d avgUs=%.3f p95Us=%.3f p99Us=%.3f maxUs=%.3f at=%s%n",
                phase,
                result.name(),
                result.samples(),
                result.avgMicros(),
                result.p95Micros(),
                result.p99Micros(),
                result.maxMicros(),
                Instant.now()
        );
    }

    private List<ProjectSummary> projects(int count) {
        List<ProjectSummary> projects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            projects.add(project(i));
        }
        return projects;
    }

    private ProjectSummary project(int index) {
        return new ProjectSummary(
                "project-" + index,
                "project-" + index,
                "Project " + index,
                "A polished sample project used to exercise launcher page layout and card rendering.",
                "creator-1",
                "Creator",
                "",
                "",
                index % 3 == 0 ? "MOD" : "WORLD",
                12_000 + index,
                900 + index,
                "2026-06-01T12:00:00Z",
                List.of(version(index))
        );
    }

    private ProjectVersion version(int index) {
        return new ProjectVersion(
                "version-" + index,
                "1." + index,
                List.of("2026.1"),
                "https://example.invalid/file-" + index + ".zip",
                100 + index,
                "2026-06-01T12:00:00Z",
                "Fast release notes.",
                List.of(),
                "RELEASE"
        );
    }

    private ProjectDetail detail() {
        String about = """
                # Overview

                This project detail page has enough markdown to exercise headings, paragraphs, lists, sidebar chips,
                gallery markers, and the banner scroll effect without depending on external assets.

                - Fast install path
                - Dense metadata panel
                - Smooth native scrolling
                """;
        return new ProjectDetail(
                "project-1",
                "project-1",
                "Project 1",
                about,
                "A polished sample project used for performance profiling.",
                "creator-1",
                "Creator",
                "",
                "",
                "MOD",
                12_000,
                900,
                "2026-06-01T12:00:00Z",
                "MIT",
                "https://example.invalid/repo",
                Map.of("discord", "https://example.invalid/discord"),
                List.of("utility", "multiplayer", "release"),
                List.of(),
                Map.of(),
                true,
                false,
                "",
                List.of(version(1))
        );
    }

    private List<NativeGalleryCarousel.ImageItem> galleryItems(int count) {
        List<NativeGalleryCarousel.ImageItem> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(new NativeGalleryCarousel.ImageItem(
                    LOCAL_PROFILE_IMAGE,
                    "Gallery image " + (i + 1),
                    "",
                    i % 3 == 0 ? "Caption " + i : ""
            ));
        }
        return items;
    }

    private CreatorProfile creator() {
        return new CreatorProfile(
                "creator-1",
                "Creator",
                "",
                "",
                "Building fast, polished Hytale projects.",
                "2026-01-01T00:00:00Z",
                "CREATOR",
                List.of(),
                "USER",
                List.of("VERIFIED"),
                List.of("fan-1", "fan-2", "fan-3"),
                List.of(),
                List.of(new CreatorProfile.ConnectedAccount("github", "creator", "creator",
                        "https://example.invalid/creator", true)),
                List.of(),
                List.of()
        );
    }

    private static <T> T callOnFx(Callable<T> callable) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return callable.call();
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(callable.call());
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Timed out waiting for JavaFX");
        if (error.get() != null) {
            if (error.get() instanceof Exception exception) {
                throw exception;
            }
            throw new AssertionError(error.get());
        }
        return result.get();
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record ProfileResult(
            String name,
            int samples,
            double avgMicros,
            double p95Micros,
            double p99Micros,
            double maxMicros
    ) {

        private static ProfileResult from(String name, List<Long> timings) {
            List<Long> sorted = new ArrayList<>(timings);
            Collections.sort(sorted);
            return new ProfileResult(
                    name,
                    timings.size(),
                    timings.stream().mapToDouble(value -> value / 1_000.0).average().orElse(0),
                    percentile(sorted, 0.95) / 1_000.0,
                    percentile(sorted, 0.99) / 1_000.0,
                    sorted.getLast() / 1_000.0
            );
        }

        private static long percentile(List<Long> sorted, double percentile) {
            int index = Math.max(0, Math.min(sorted.size() - 1,
                    (int) Math.ceil(sorted.size() * percentile) - 1));
            return sorted.get(index);
        }
    }
}
