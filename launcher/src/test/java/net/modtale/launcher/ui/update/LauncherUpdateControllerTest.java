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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".+")
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
            var feedback = new LauncherFeedback(jobs::add, new Label(), new VBox(), new StackPane(),
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
            assertTrue(labels(settings.view()).stream().anyMatch(text -> text.startsWith("No update available on develop.")));
            assertFalse(labels(settings.view()).stream().anyMatch(text -> text.contains("Stale stable failure")));
            return null;
        });
        assertEquals("develop", new SettingsStore(directory.resolve("settings.json")).load().getLauncherChannel());
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
