package net.modtale.launcher.sync;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.model.sync.LauncherSettingsSnapshot;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.settings.SettingsStore;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.ui.settings.LauncherSettingsController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherSettingsSyncServiceTest {
    @TempDir Path directory;

    @Test void savedReleaseUsesItsOwnSupportedDownloadVersion() {
        var version = new net.modtale.launcher.model.project.ProjectVersion("release-id", "1.10.2",
                java.util.List.of("0.5.3", "0.5.1"), null, 0, null, null, java.util.List.of(), "RELEASE");
        assertEquals("0.5.3", LauncherSettingsSyncService.downloadGameVersion(version, "0.5.5"));
        assertEquals("0.5.1", LauncherSettingsSyncService.downloadGameVersion(version, "0.5.1"));
    }

    @BeforeAll static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { }
        catch (UnsupportedOperationException unavailable) {
            org.junit.jupiter.api.Assumptions.abort("JavaFX unavailable: " + unavailable.getMessage());
        }
    }

    @Test void savesOfflineAndUploadsEditsThatArriveDuringAnUpload() throws Exception {
        SettingsStore store = new SettingsStore(directory.resolve("settings.json"));
        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleModsPath(directory.resolve("Mods").toString());
        settings.setHytaleUserDataPath(directory.resolve("UserData").toString());
        store.save(settings);
        Path config = directory.resolve("Mods/Example/config.json");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "{\"value\":1}");
        AtomicBoolean signedIn = new AtomicBoolean();
        AtomicInteger preferenceUploads = new AtomicInteger();
        var uploads = new ArrayList<LauncherSettingsSnapshot>();
        ModtaleApiClient api = new ModtaleApiClient("https://example.invalid") {
            @Override public LauncherSettingsSnapshot updateLauncherSettings(LauncherSettingsSnapshot snapshot) {
                uploads.add(snapshot);
                return snapshot;
            }
            @Override public LauncherSettingsSnapshot updateLauncherSettingsPreferences(LauncherSettingsSnapshot snapshot) {
                preferenceUploads.incrementAndGet();
                uploads.add(snapshot);
                return snapshot;
            }
        };
        LinkedBlockingQueue<Runnable> work = new LinkedBlockingQueue<>();
        LauncherSettingsSyncService service = fx(() -> {
            var controller = new LauncherSettingsController(store, api, () -> null, () -> LauncherView.LIBRARY);
            var feedback = new LauncherFeedback(work::add, new Label(), new StackPane(), new Label(), new Label(), () -> "");
            return new LauncherSettingsSyncService(api, store, controller, null, feedback, signedIn::get, StackPane::new);
        });
        fx(() -> { service.syncAfterLocalChange(); return null; });
        runWork(work);
        assertEquals("{\"value\":1}", store.load().getConfigs().getFirst().content());
        assertTrue(uploads.isEmpty());

        signedIn.set(true);
        fx(() -> { service.syncAfterLocalChange(); return null; });
        runWork(work); // capture, then queue the first upload
        Files.writeString(config, "{\"value\":2}");
        fx(() -> { service.syncAfterLocalChange(); return null; });
        runWork(work); // first upload completes, then queue a fresh capture
        runWork(work); // capture latest file, then queue a preferences-only upload
        runWork(work);
        assertEquals(2, uploads.size());
        assertEquals(1, preferenceUploads.get());
        assertEquals("{\"value\":2}", uploads.getLast().getConfigs().getFirst().content());
        assertEquals("{\"value\":2}", store.load().getConfigs().getFirst().content());
        assertTrue(work.isEmpty());
    }

    @Test void advancesStatusBeforeMetadataLookupEvenWhenPreviousRestoreFails() throws Exception {
        SettingsStore store = new SettingsStore(directory.resolve("settings.json"));
        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleModsPath(directory.resolve("Mods").toString());
        settings.setHytaleUserDataPath(directory.resolve("UserData").toString());
        store.save(settings);
        var progress = fx(() -> new net.modtale.launcher.ui.common.TransferLoadingModal("Syncing", "Starting"));
        var statuses = new ArrayList<String>();
        ModtaleApiClient api = new ModtaleApiClient("https://example.invalid") {
            @Override public net.modtale.launcher.model.project.ProjectDetail getProject(String id) {
                try {
                    statuses.add(fx(() -> {
                        VBox card = (VBox) progress.getChildren().getFirst();
                        return ((Label) card.getChildren().getLast()).getText() + ":" + progress.getProgress();
                    }));
                } catch (Exception ex) { throw new AssertionError(ex); }
                throw new IllegalStateException("Provider temporarily unavailable");
            }
        };
        var service = fx(() -> new LauncherSettingsSyncService(api, store,
                new LauncherSettingsController(store, api, () -> null, () -> LauncherView.LIBRARY), null,
                new LauncherFeedback(Runnable::run, new Label(), new StackPane(), new Label(), new Label(), () -> ""),
                () -> true, StackPane::new));
        var first = new LauncherSettingsSnapshot.InstalledProjectSnapshot();
        first.setProjectId("first");
        first.setTitle("First mod");
        var second = new LauncherSettingsSnapshot.InstalledProjectSnapshot();
        second.setProjectId("second");
        second.setTitle("Second mod");
        var snapshot = new LauncherSettingsSnapshot();
        snapshot.setInstalledProjects(java.util.List.of(first, new LauncherSettingsSnapshot.InstalledProjectSnapshot(), second));
        var restore = LauncherSettingsSyncService.class.getDeclaredMethod("restore", LauncherSettingsSnapshot.class,
                net.modtale.launcher.ui.common.TransferLoadingModal.class);
        restore.setAccessible(true);
        restore.invoke(service, snapshot, progress);
        assertEquals(java.util.List.of("Checking First mod:0.0", "Checking Second mod:0.5"), statuses);
    }

    private static void runWork(LinkedBlockingQueue<Runnable> work) throws Exception {
        Runnable task = work.poll(5, TimeUnit.SECONDS);
        assertNotNull(task, "Expected queued sync work");
        task.run();
        fx(() -> null);
    }
    private static <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
