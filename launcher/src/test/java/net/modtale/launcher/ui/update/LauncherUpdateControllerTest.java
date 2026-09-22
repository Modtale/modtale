package net.modtale.launcher.ui.update;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.settings.SettingsStore;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.ui.settings.LauncherSettingsController;
import net.modtale.launcher.update.LauncherUpdateCandidate;
import net.modtale.launcher.update.LauncherUpdateService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherUpdateControllerTest {
    @TempDir Path directory;

    @BeforeAll
    static void toolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        fx(() -> { Platform.setImplicitExit(false); return null; });
    }

    @Test
    void savesChannelAndDiscardsThePreviousChannelsPendingResult() throws Exception {
        var jobs = new ArrayDeque<Runnable>();
        List<String> checkedChannels = new ArrayList<>();
        var service = new LauncherUpdateService() {
            @Override public boolean canInstallUpdates() { return true; }
            @Override public Optional<LauncherUpdateCandidate> latestUpdate(String version, String channel) {
                checkedChannels.add(channel);
                if (channel.equals("stable")) throw new IllegalStateException("Stale stable failure");
                return Optional.empty();
            }
        };
        var settings = fx(() -> {
            var controller = new LauncherSettingsController(new SettingsStore(directory.resolve("settings.json")),
                    new ModtaleApiClient("http://localhost", directory.resolve("session.json")), () -> null, () -> LauncherView.SETTINGS);
            controller.reloadControls();
            // Update status lives in the Maintenance category after the settings redesign.
            var categories = ((Parent) controller.view()).lookupAll(".settings-category");
            categories.stream().map(javafx.scene.control.ToggleButton.class::cast)
                    .filter(button -> button.getText().equals(net.modtale.launcher.i18n.LauncherI18n.get().text("settings.maintenance.section")))
                    .findFirst().orElseThrow().fire();
            var feedback = new LauncherFeedback(jobs::add, new Label(), new StackPane(),
                    new Label(), new Label(), () -> "Channel test");
            var updater = new LauncherUpdateController(service, controller, feedback, jobs::add, () -> null);
            updater.checkOnStartup();
            assertEquals(List.of("stable", "develop"), controller.form().launcherChannelCombo().getItems());
            controller.form().launcherChannelCombo().setValue("develop");
            controller.saveFromFields(false);
            return controller;
        });
        jobs.remove().run();
        fx(() -> null); // Drain the stale completion and enqueue the selected channel check.
        assertEquals(1, jobs.size());
        jobs.remove().run();
        fx(() -> {
            assertEquals(List.of("stable", "develop"), checkedChannels);
            assertTrue(labels(settings.view()).stream().anyMatch(text -> text.startsWith("No automatic update available on develop.")));
            assertFalse(labels(settings.view()).stream().anyMatch(text -> text.contains("Stale stable failure")));
            return null;
        });
        assertEquals("develop", new SettingsStore(directory.resolve("settings.json")).load().getLauncherChannel());
    }

    @Test
    void usesTransferOverlayPreventsDuplicateUpdatesAndExitsOnlyAfterSuccessfulHandoff() throws Exception {
        for (boolean fail : List.of(false, true)) {
            var jobs = new ArrayDeque<Runnable>();
            var events = new ArrayList<String>();
            var update = new LauncherUpdateCandidate("1.2.0", "launcher-v1.2.0", "", "",
                    "launcher-linux-x86_64-update.zip", "https://example.invalid/update", false, 0, null);
            var service = new LauncherUpdateService() {
            @Override public boolean canInstallUpdates() { return true; }
                @Override public Optional<LauncherUpdateCandidate> latestUpdate(String version, String channel) {
                    events.add("check");
                    return Optional.of(update);
                }
                @Override public Optional<String> consumeUpdateFailure() { return Optional.empty(); }
                @Override public Path downloadInstaller(LauncherUpdateCandidate candidate, java.util.function.DoubleConsumer progress) {
                    events.add("download");
                    progress.accept(0.5);
                    return directory.resolve("update.zip");
                }
                @Override public void installUpdate(Path path, LauncherUpdateCandidate candidate) {
                    events.add("install");
                    if (fail) throw new IllegalStateException("Download could not be installed");
                }
            };
            StackPane host = fx(StackPane::new);
            var updater = fx(() -> {
                var settings = new LauncherSettingsController(new SettingsStore(directory.resolve("auto-" + fail + ".json")),
                        new ModtaleApiClient("http://localhost", directory.resolve("session.json")), () -> null, () -> LauncherView.SETTINGS);
                settings.settings().setLauncherAutoUpdates(true);
                settings.reloadControls();
                var feedback = new LauncherFeedback(jobs::add, new Label(), new StackPane(), new Label(), new Label(), () -> "Idle");
                return new LauncherUpdateController(service, settings, feedback, jobs::add, () -> host, () -> events.add("exit"));
            });
            fx(() -> { updater.checkOnStartup(); return null; });
            jobs.remove().run();
            fx(() -> {
                assertEquals(1, host.getChildren().size());
                assertInstanceOf(net.modtale.launcher.ui.common.TransferLoadingModal.class, host.getChildren().getFirst());
                updater.checkOnStartup();
                assertFalse(events.contains("exit"));
                return null;
            });
            assertEquals(1, jobs.size());
            jobs.remove().run();
            fx(() -> {
                assertTrue(host.getChildren().isEmpty());
                assertEquals(fail ? List.of("check", "download", "install") : List.of("check", "download", "install", "exit"), events);
                if (fail) updater.checkOnStartup();
                return null;
            });
            assertEquals(fail ? 1 : 0, jobs.size());
        }
    }

    @Test
    void sourceSessionsAndReleasesWithoutPayloadNeverOpenAnUpdateModal() throws Exception {
        for (boolean installed : List.of(false, true)) {
            var jobs = new ArrayDeque<Runnable>();
            var events = new ArrayList<String>();
            StackPane host = fx(StackPane::new);
            var service = new LauncherUpdateService() {
                @Override public boolean canInstallUpdates() { return installed; }
                @Override public Optional<String> consumeUpdateFailure() { return Optional.empty(); }
                @Override public Optional<LauncherUpdateCandidate> latestUpdate(String version, String channel) {
                    events.add("check");
                    return Optional.of(new LauncherUpdateCandidate("0.2.145", "launcher-v0.2.145", "", "",
                            null, null, false, 0, null));
                }
            };
            var updater = fx(() -> {
                var settings = new LauncherSettingsController(new SettingsStore(directory.resolve("source-" + installed + ".json")),
                        new ModtaleApiClient("http://localhost", directory.resolve("session.json")), () -> null, () -> LauncherView.SETTINGS);
                settings.reloadControls();
                var feedback = new LauncherFeedback(jobs::add, new Label(), new StackPane(), new Label(), new Label(), () -> "Idle");
                return new LauncherUpdateController(service, settings, feedback, jobs::add, () -> host,
                        () -> fail("Must not exit without an installable update"));
            });
            fx(() -> { updater.checkOnStartup(); return null; });
            if (installed) jobs.remove().run();
            fx(() -> {
                assertTrue(host.getChildren().isEmpty());
                assertEquals(installed ? List.of("check") : List.of(), events);
                assertTrue(jobs.isEmpty());
                return null;
            });
        }
    }

    private static List<String> labels(Node node) {
        List<String> result = new ArrayList<>();
        if (node instanceof Label label) result.add(label.getText());
        if (node instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(child -> result.addAll(labels(child)));
        return result;
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
