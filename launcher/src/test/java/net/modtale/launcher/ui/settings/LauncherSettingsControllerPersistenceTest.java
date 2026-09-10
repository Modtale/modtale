package net.modtale.launcher.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.settings.SettingsStore;
import net.modtale.launcher.ui.common.LauncherView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherSettingsControllerPersistenceTest {
    @TempDir Path root;

    @BeforeAll
    static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { /* Shared toolkit. */ }
    }

    @Test
    void categoryNavigationRetainsUnsavedEditsAndAlwaysKeepsOneSelection() throws Exception {
        onFx(() -> {
            var controller = new LauncherSettingsController(new SettingsStore(root.resolve("navigation.json")),
                    new ModtaleApiClient("http://localhost"), () -> null, () -> LauncherView.SETTINGS);
            javafx.scene.Parent view = (javafx.scene.Parent) controller.view();
            javafx.scene.Scene scene = new javafx.scene.Scene(view, 1200, 800);
            scene.getStylesheets().add(getClass().getResource(
                    "/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
            view.applyCss();
            view.layout();
            var buttons = view.lookupAll(".settings-category").stream()
                    .map(node -> (javafx.scene.control.ToggleButton) node).toList();
            assertEquals(4, buttons.size());
            controller.form().gameVersionField().setText("0.6.0");
            buttons.forEach(button -> { button.fire(); button.fire(); });
            assertEquals(1, buttons.stream().filter(javafx.scene.control.ToggleButton::isSelected).count());
            assertEquals("0.6.0", controller.form().gameVersionField().getText());
            controller.saveFromFields(false);
            assertEquals("0.6.0", controller.settings().getGameVersion());
        });
    }

    @Test
    void earlySavePreservesExplicitPathsBeforeViewsReloadControls() throws Exception {
        onFx(() -> {
            SettingsStore store = new SettingsStore(root.resolve("settings.json"));
            LauncherSettings settings = new LauncherSettings();
            String userData = root.resolve("custom/UserData").toString();
            String mods = root.resolve("separate/Mods").toString();
            settings.setHytaleUserDataPath(userData);
            settings.setHytaleModsPath(mods);
            store.save(settings);
            for (LauncherView view : List.of(LauncherView.PLAY, LauncherView.SETTINGS)) {
                var controller = new LauncherSettingsController(store,
                        new ModtaleApiClient("http://localhost"), () -> null, () -> view);
                controller.saveFromFields(false);
                assertEquals(userData, store.load().getHytaleUserDataPath());
                assertEquals(mods, store.load().getHytaleModsPath());
            }
        });
    }

    @Test
    void reconciledIdentityStaysRemovedAfterRegistryMergeAndReload() throws Exception {
        Path jar = Files.writeString(root.resolve("EyeSpy.jar"), "one artifact");
        onFx(() -> {
            SettingsStore store = new SettingsStore(root.resolve("settings.json"));
            LauncherSettings settings = new LauncherSettings();
            InstalledProject local = project("local:eyespy", "LOCAL", jar);
            InstalledProject managed = project("curseforge:42", "CURSEFORGE", jar);
            settings.setInstalledProjects(List.of(local, managed));
            store.save(settings);
            var controller = new LauncherSettingsController(store, null, () -> null, () -> LauncherView.PLAY);
            controller.saveReconciledInstalledProjects(List.of(managed));
            assertEquals(List.of(managed), store.load().getInstalledProjects());
            controller.saveCurrentSettings();
            assertEquals(List.of(managed), store.load().getInstalledProjects());
        });
    }

    private InstalledProject project(String id, String source, Path jar) {
        return new InstalledProject(id, "eyespy", "EyeSpy", "PLUGIN", "1", "99", "", null, null,
                List.of(jar.toString()), List.of(), List.of(), source, "DIRECT", false, List.of());
    }

    private static void onFx(Runnable work) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { work.run(); return null; });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
