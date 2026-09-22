package net.modtale.launcher.ui.play;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.hytale.*;
import net.modtale.launcher.settings.*;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.ui.common.TransferLoadingModal;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.ui.settings.LauncherSettingsController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherGameUpdateModalTest {
    @TempDir Path temp;

    @BeforeAll static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { }
    }

    @Test
    void playShowsAnimatedDownloadModalUpdatesProgressAndDismissesOnFailure() throws Exception {
        CountDownLatch downloading = new CountDownLatch(1);
        CountDownLatch failDownload = new CountDownLatch(1);
        AtomicInteger launches = new AtomicInteger();
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Object[] ui = fx(() -> {
                LauncherSettings settings = new LauncherSettings();
                SettingsStore store = new SettingsStore(temp.resolve("settings.json")) {
                    @Override public LauncherSettings load() { return settings; }
                    @Override public void save(LauncherSettings value) { }
                };
                var api = new ModtaleApiClient("http://127.0.0.1:1/api/v1");
                var controller = new LauncherSettingsController(store, api, () -> null, () -> LauncherView.PLAY);
                var host = new StackPane();
                new Scene(host, 800, 600);
                var feedback = new LauncherFeedback(worker, new Label(), new StackPane(), new Label(), new Label(), () -> "Ready");
                var auth = new HytaleAuthService(null, store);
                var game = new HytaleGameLauncher(auth) {
                    @Override public HytaleLaunchResult launch(LauncherSettings ignored, Consumer<String> progress) {
                        launches.incrementAndGet();
                        progress.accept("Downloading Hytale release build 29 (50%)...");
                        downloading.countDown();
                        try { assertTrue(failDownload.await(10, TimeUnit.SECONDS)); }
                        catch (InterruptedException ex) { throw new RuntimeException(ex); }
                        throw new HytaleApiException("Test update failed");
                    }
                };
                var play = new LauncherPlayController(api, null, auth, game, null, controller, feedback, worker,
                        null, null, null, null, null, null);
                play.setOverlayHost(() -> host);
                play.launchHytale();
                play.launchHytale(); // A second click must not start another update.
                assertInstanceOf(TransferLoadingModal.class, host.getChildren().getFirst());
                return new Object[]{host, play};
            });
            try {
                assertTrue(downloading.await(10, TimeUnit.SECONDS));
                fx(() -> {
                    StackPane host = (StackPane) ui[0];
                    TransferLoadingModal modal = (TransferLoadingModal) host.getChildren().getFirst();
                    assertFalse(modal.lookupAll("Canvas").isEmpty());
                    assertEquals(0.5, modal.getProgress());
                    assertTrue(modal.lookupAll(".status-modal-message").stream()
                            .map(node -> ((Label) node).getText())
                            .anyMatch(text -> text.equals("Downloading Hytale release build 29")));
                    return null;
                });
            } finally { failDownload.countDown(); }
            worker.submit(() -> {}).get(10, TimeUnit.SECONDS);
            fx(() -> {
                assertTrue(((StackPane) ui[0]).getChildren().isEmpty());
                assertEquals(1, launches.get());
                return null;
            });
        }
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
