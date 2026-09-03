package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
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
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.transform.Scale;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectPage;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.model.project.WikiBundle;
import net.modtale.launcher.model.user.CreatorProfile;
import net.modtale.launcher.model.user.ProfileBadge;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;
import net.modtale.launcher.ui.browse.card.ProjectCardViewStyle;
import net.modtale.launcher.ui.browse.render.ProjectBrowserRenderer;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherScrollSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;

class LauncherUiPerformanceProfileTest {

    private static final String RUN_PROPERTY = "modtale.launcher.perfTests";
    private static final String SNAPSHOT_PROPERTY = "modtale.launcher.snapshotDirectory";
    private static final String SNAPSHOT_ENVIRONMENT = "MODTALE_LAUNCHER_SNAPSHOT_DIRECTORY";
    private static final int WARMUPS = 80;
    private static final int SAMPLES = 400;
    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final String LOCAL_PROFILE_IMAGE = Path.of(
            "src/main/resources/net/modtale/launcher/ui/nativefx/assets/favicon.png"
    ).toUri().toString();

    @BeforeAll
    static void startJavaFx() throws Exception {
        if (!Boolean.getBoolean(RUN_PROPERTY) && snapshotDirectory().isBlank()) {
            return;
        }
        System.setProperty("javafx.animation.framerate", "165");
        System.setProperty("javafx.animation.pulse", "165");
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX did not start");
    }

    @Test
    void projectAndBrowseVisualParitySnapshots() throws Exception {
        String snapshotDirectory = snapshotDirectory();
        assumeTrue(!snapshotDirectory.isBlank(), "Enable with -D" + SNAPSHOT_PROPERTY + "=/path");
        Path directory = Path.of(snapshotDirectory);
        Files.createDirectories(directory);
        callOnFx(() -> {
            snapshot(buildProjectPageNode(1440, 1400, false), 1440, 1400, directory.resolve("launcher-project-desktop.png"));
            snapshot(buildProjectPageNode(760, 1800, true), 760, 1800, directory.resolve("launcher-project-compact.png"));
            snapshot(buildProjectPageNode(1440, 1400, false, true), 1440, 1400, directory.resolve("launcher-wiki-desktop.png"));
            snapshot(buildProjectPageNode(760, 1800, true, true), 760, 1800, directory.resolve("launcher-wiki-compact.png"));
            snapshot(buildBrowsePageNode(1440, 900, ProjectCardViewStyle.GRID), 1440, 900,
                    directory.resolve("launcher-browse-grid.png"));
            snapshot(buildBrowsePageNode(980, 900, ProjectCardViewStyle.COMPACT), 980, 900,
                    directory.resolve("launcher-browse-compact.png"));
            snapshot(buildCurseForgeBrowsePageNode(1440, 900), 1440, 900,
                    directory.resolve("launcher-browse-curseforge.png"));
            return null;
        });
    }

    private static String snapshotDirectory() {
        String property = System.getProperty(SNAPSHOT_PROPERTY, "");
        return property.isBlank() ? System.getenv().getOrDefault(SNAPSHOT_ENVIRONMENT, "") : property;
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

    @Test
    void profileWorstCaseInteractiveWorkload() throws Exception {
        assumeTrue(Boolean.getBoolean(RUN_PROPERTY), "Enable with -D" + RUN_PROPERTY + "=true");
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<ProfileResult> scrollWork = new AtomicReference<>();
        AtomicReference<ProfileResult> scrollIntervals = new AtomicReference<>();
        AtomicReference<ProfileResult> warmScrollIntervals = new AtomicReference<>();
        AtomicReference<ProfileResult> animationWork = new AtomicReference<>();
        AtomicReference<ProfileResult> animationIntervals = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                VBox browse = (VBox) buildBrowsePageNode(1440, 1800, ProjectCardViewStyle.COMPACT, 45);
                NativeGalleryCarousel carousel = new NativeGalleryCarousel(imageLoader(), url -> {});
                Node gallery = carousel.render(galleryItems(24), 7, NativeGalleryCarousel.Variant.INLINE);
                VBox document = new VBox(24, browse, gallery);
                ScrollPane scroll = new ScrollPane(document);
                scroll.setFitToWidth(true);
                StackPane deck = new StackPane(scroll);
                new LauncherScrollSupport(() -> deck).configure(scroll, false);
                Scene scene = new Scene(deck, 1440, 900, javafx.scene.paint.Color.web("#0B1120"));
                scene.getStylesheets().add(getClass().getResource(
                        "/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                Stage stage = new Stage();
                stageReference.set(stage);
                stage.setScene(scene);
                stage.show();
                deck.applyCss();
                deck.layout();
                List<Node> cards = new ArrayList<>(deck.lookupAll(".project-card"));
                long[] scrollingWork = new long[240];
                long[] scrollingIntervals = new long[239];
                long[] animationsWork = new long[240];
                long[] animationsIntervals = new long[239];

                new AnimationTimer() {
                    private long previousPulse;
                    private int pulse;

                    @Override
                    public void handle(long now) {
                        int measuredPulse = pulse - 120;
                        if (previousPulse != 0 && measuredPulse > 0 && measuredPulse < 240) {
                            scrollingIntervals[measuredPulse - 1] = now - previousPulse;
                        } else if (previousPulse != 0 && measuredPulse > 240) {
                            animationsIntervals[measuredPulse - 241] = now - previousPulse;
                        }
                        previousPulse = now;
                        long started = System.nanoTime();
                        if (measuredPulse >= 0 && measuredPulse < 240) {
                            double phase = (measuredPulse % 120) / 119.0;
                            scroll.setVvalue(measuredPulse < 120 ? phase : 1.0 - phase);
                            scroll.fireEvent(scrollEvent(measuredPulse % 2 == 0 ? -72 : 72));
                        } else if (measuredPulse >= 240 && !cards.isEmpty() && measuredPulse % 12 == 0) {
                            int cardIndex = (pulse / 12) % cards.size();
                            cards.get(cardIndex).fireEvent(mouseEvent(MouseEvent.MOUSE_ENTERED));
                            cards.get((cardIndex + cards.size() - 1) % cards.size())
                                    .fireEvent(mouseEvent(MouseEvent.MOUSE_EXITED));
                        }
                        long elapsed = System.nanoTime() - started;
                        if (measuredPulse >= 0 && measuredPulse < 240) {
                            scrollingWork[measuredPulse] = elapsed;
                        } else if (measuredPulse >= 240 && measuredPulse < 480) {
                            animationsWork[measuredPulse - 240] = elapsed;
                        }
                        pulse++;
                        if (pulse >= 600) {
                            stop();
                            scrollWork.set(ProfileResult.from("continuous scrolling UI work", scrollingWork, scrollingWork.length));
                            scrollIntervals.set(ProfileResult.from("continuous scrolling pulse interval", scrollingIntervals,
                                    scrollingIntervals.length));
                            warmScrollIntervals.set(ProfileResult.from("warm-cache scrolling pulse interval",
                                    scrollingIntervals, 120, 119));
                            animationWork.set(ProfileResult.from("hover/gallery animation UI work", animationsWork,
                                    animationsWork.length));
                            animationIntervals.set(ProfileResult.from("hover/gallery animation pulse interval",
                                    animationsIntervals, animationsIntervals.length));
                            complete.countDown();
                        }
                    }
                }.start();
            } catch (Throwable throwable) {
                failure.set(throwable);
                complete.countDown();
            }
        });

        assertTrue(complete.await(15, TimeUnit.SECONDS), "Worst-case JavaFX workload timed out");
        if (stageReference.get() != null) Platform.runLater(stageReference.get()::close);
        if (failure.get() != null) throw new AssertionError(failure.get());
        printReport("scroll-work", scrollWork.get());
        printReport("scroll-cadence", scrollIntervals.get());
        printReport("warm-scroll-cadence", warmScrollIntervals.get());
        printReport("animation-work", animationWork.get());
        printReport("animation-cadence", animationIntervals.get());
        assertTrue(scrollWork.get().avgMicros() < 300 && scrollWork.get().p99Micros() < 1_000,
                "Continuous scrolling mutations should average below 0.3ms and remain below 1ms at p99");
        assertTrue(animationWork.get().avgMicros() < 100 && animationWork.get().p99Micros() < 1_000,
                "Hover and gallery mutations should average below 0.1ms and remain below 1ms at p99");
        assertTrue(warmScrollIntervals.get().p95Micros() < 25_000
                        && warmScrollIntervals.get().p99Micros() < 40_000,
                "Warm-cache scrolling should not develop repeated long-tail stalls");
        assertTrue(animationIntervals.get().p99Micros() < 25_000,
                "Hover and gallery animations should deliver every p99 pulse within one 60 Hz frame plus scheduling tolerance");
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
        buildProjectPageNode(1320, 880, false);
    }

    private Node buildProjectPageNode(double width, double height, boolean compact) throws Exception {
        return buildProjectPageNode(width, height, compact, false);
    }

    private Node buildProjectPageNode(double width, double height, boolean compact, boolean wiki) throws Exception {
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
        content.resize(width, height);
        Field compactLayout = ProjectPageController.class.getDeclaredField("compactLayout");
        compactLayout.setAccessible(true);
        compactLayout.setBoolean(controller, compact);
        if (wiki) {
            WikiBundle bundle = new ObjectMapper().readValue("""
                    {
                      "metadata": {"mod": {
                        "id": "levelingcore",
                        "name": "LevelingCore",
                        "index": {"slug": "home-1"},
                        "pages": [
                          {"id":"home", "slug":"home-1", "title":"Home"},
                          {"id":"guides", "slug":"guides", "title":"Guides", "children":[
                            {"id":"install", "slug":"guides/install", "title":"Installation"},
                            {"id":"configuration", "slug":"guides/configuration", "title":"Configuration"}
                          ]},
                          {"id":"reference", "slug":"reference", "title":"Reference", "children":[
                            {"id":"commands", "slug":"reference/commands", "title":"Commands"},
                            {"id":"permissions", "slug":"reference/permissions", "title":"Permissions"}
                          ]}
                        ]
                      }},
                      "page": {
                        "title":"Getting Started",
                        "content":"Welcome to **LevelingCore**. This wiki covers installation, configuration, and commands.\\n\\n## Quick start\\n\\n1. Download the latest release.\\n2. Place it in your `mods` folder.\\n3. Restart Hytale.\\n\\n```json\\n{ \\"enabled\\": true, \\"maxLevel\\": 100 }\\n```\\n\\n> Changes are applied when the server restarts."
                      },
                      "pageSlug":"home-1"
                    }
                    """, WikiBundle.class);
            setField(controller, "currentWikiBundle", bundle);
            setField(controller, "currentWikiSlug", "home-1");
            setField(controller, "wikiMode", true);
            Field cache = ProjectPageController.class.getDeclaredField("wikiPageCache");
            cache.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, WikiBundle.WikiPage> pages = (Map<String, WikiBundle.WikiPage>) cache.get(controller);
            pages.put("home-1", bundle.content());
        }
        Method projectPage = ProjectPageController.class.getDeclaredMethod(
                "projectPage",
                ProjectSummary.class,
                ProjectDetail.class,
                boolean.class
        );
        projectPage.setAccessible(true);
        Node page = (Node) projectPage.invoke(controller, project(1), detail(), false);
        if (page instanceof Region region) {
            region.resize(width, Math.max(height, region.prefHeight(width)));
        }
        return page;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Node buildBrowsePageNode(double width, double height, ProjectCardViewStyle style) {
        return buildBrowsePageNode(width, height, style, style == ProjectCardViewStyle.COMPACT ? 12 : 8);
    }

    private Node buildBrowsePageNode(double width, double height, ProjectCardViewStyle style, int count) {
        StackPane results = new StackPane();
        StackPane deck = new StackPane();
        VBox body = new VBox(results);
        ProjectBrowserRenderer renderer = new ProjectBrowserRenderer(
                results, deck, () -> body, new ProjectCardFactory(url -> url, DIRECT_EXECUTOR), id -> false,
                () -> "2026.1", project -> {}, project -> {}, project -> {}, project -> {}
        );
        renderer.render(projects(count), style, count);
        body.resize(width, height);
        return body;
    }

    private Node buildCurseForgeBrowsePageNode(double width, double height) {
        StackPane results = new StackPane();
        StackPane deck = new StackPane();
        VBox body = new VBox(results);
        ProjectBrowserRenderer renderer = new ProjectBrowserRenderer(
                results, deck, () -> body, new ProjectCardFactory(url -> url, DIRECT_EXECUTOR), id -> false,
                () -> "2026.09", project -> {}, project -> {}, project -> {}, project -> {}
        );
        List<ProjectSummary> projects = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            projects.add(new ProjectSummary(
                    "curseforge:" + (1450386 + index), "curseforge:" + (1450386 + index),
                    index == 0 ? "Simple Compost" : "CurseForge Project " + index,
                    "A provider-backed Hytale mod with verified release metadata.", null, "CurseForge Creator",
                    "", "", "MOD", 321_000 + index, 0, "2026-09-01T12:00:00Z",
                    List.of(new ProjectVersion("822781" + index, "1.2." + index, List.of("2026.09"),
                            "https://www.curseforge.com/hytale/mods/example/files/822781" + index,
                            100 + index, "2026-09-01T12:00:00Z", null, List.of(), "RELEASE")),
                    "CURSEFORGE", "https://www.curseforge.com/hytale/mods/example", index != 1
            ));
        }
        renderer.render(projects, ProjectCardViewStyle.GRID);
        body.resize(width, height);
        return body;
    }

    private void snapshot(Node node, int width, int height, Path destination) throws Exception {
        StackPane root = new StackPane(node);
        Scene scene = new Scene(root, width, height, javafx.scene.paint.Color.web("#0B1120"));
        scene.getStylesheets().add(getClass().getResource(
                "/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
        root.resize(width, height);
        root.applyCss();
        root.layout();
        WritableImage image = root.snapshot(new SnapshotParameters(), new WritableImage(width, height));
        PixelReader pixels = image.getPixelReader();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) output.setRGB(x, y, pixels.getArgb(x, y));
        }
        ImageIO.write(output, "png", destination.toFile());
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

    private MouseEvent mouseEvent(javafx.event.EventType<MouseEvent> type) {
        return new MouseEvent(type, 20, 20, 20, 20, javafx.scene.input.MouseButton.NONE,
                0, false, false, false, false, false, false, false, false, false, false, null);
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
                true,
                "levelingcore",
                List.of(
                        version(1),
                        new ProjectVersion("version-2", "1.2.0", List.of("0.6.1", "0.6.0", "0.5.4", "0.5.3"),
                                "https://example.invalid/file-2.zip", 200, "2026-08-01T12:00:00Z", "Grouped release.", List.of(), "RELEASE")
                ),
                List.of(new ProjectDetail.ProjectRole("maintainer", "Maintainer", "#3b82f6", java.util.Set.of())),
                List.of(new ProjectDetail.ProjectMember("team-1", "maintainer", "Teammate", ""))
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
                List.of(ProfileBadge.legacy("VERIFIED")),
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

        private static ProfileResult from(String name, long[] timings, int count) {
            return from(name, timings, 0, count);
        }

        private static ProfileResult from(String name, long[] timings, int offset, int count) {
            long[] sorted = java.util.Arrays.copyOfRange(timings, offset, offset + count);
            java.util.Arrays.sort(sorted);
            return new ProfileResult(
                    name,
                    count,
                    java.util.Arrays.stream(timings, offset, offset + count).average().orElse(0) / 1_000.0,
                    percentile(sorted, 0.95) / 1_000.0,
                    percentile(sorted, 0.99) / 1_000.0,
                    sorted[count - 1] / 1_000.0
            );
        }

        private static long percentile(List<Long> sorted, double percentile) {
            int index = Math.max(0, Math.min(sorted.size() - 1,
                    (int) Math.ceil(sorted.size() * percentile) - 1));
            return sorted.get(index);
        }


        private static long percentile(long[] sorted, double percentile) {
            int index = Math.max(0, Math.min(sorted.length - 1,
                    (int) Math.ceil(sorted.length * percentile) - 1));
            return sorted[index];
        }
    }
}
