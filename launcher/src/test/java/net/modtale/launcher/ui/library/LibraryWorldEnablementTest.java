package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleInstalledMod;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorldConfig;
import net.modtale.launcher.model.install.InstalledProject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LibraryWorldEnablementTest {
    @BeforeAll static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { }
    }

    @Test void togglesUseManifestIdsAndNeverProviderIds() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            var controller = new LauncherLibraryController(null, null, null, null, null, null, null,
                    Runnable::run, null, () -> null);
            Path artifact = Path.of("mods/renamed-download.jar").toAbsolutePath();
            var mods = LauncherLibraryController.class.getDeclaredField("installedMods");
            mods.setAccessible(true);
            mods.set(controller, List.of(new HytaleInstalledMod("Author:Actual Name", "Actual Name", "1", "", artifact)));
            var modelMethod = LauncherLibraryController.class.getDeclaredMethod("worldProjectModel",
                    InstalledProject.class, HytaleWorldConfig.class);
            modelMethod.setAccessible(true);
            for (String source : List.of("MODTALE", "CURSEFORGE", "LOCAL")) {
                var project = new InstalledProject("curseforge:123", "web-slug", "Title", "PLUGIN", "1", "", "",
                        null, null, List.of(artifact.toString()), List.of(), List.of(), source,
                        InstalledProject.INSTALL_DIRECT, false, List.of());
                var enabled = new HytaleWorldConfig(null, Map.of("Author:Actual Name", true, "curseforge:123", false));
                var model = (LibraryWorldProjectModel) modelMethod.invoke(controller, project, enabled);
                assertEquals(List.of("Author:Actual Name"), model.modIds());
                assertEquals(1, model.enabledCount());
                var disabled = new HytaleWorldConfig(null, Map.of("Author:Actual Name", false, "curseforge:123", true));
                assertEquals(0, ((LibraryWorldProjectModel) modelMethod.invoke(controller, project, disabled)).enabledCount());
                mods.set(controller, List.of());
                var unresolved = (LibraryWorldProjectModel) modelMethod.invoke(controller, project, enabled);
                assertTrue(unresolved.modIds().isEmpty());
                assertFalse(unresolved.toggleable());
                mods.set(controller, List.of(new HytaleInstalledMod("Author:Actual Name", "Actual Name", "1", "", artifact)));
            }
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
