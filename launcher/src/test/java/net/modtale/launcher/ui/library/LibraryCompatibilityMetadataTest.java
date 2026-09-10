package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleInstalledMod;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorldConfig;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.InstalledProjectReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryCompatibilityMetadataTest {
    @TempDir Path temp;

    @BeforeAll static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { }
    }

    @Test void controllerAndCardsUseActualManifestForAllProvidersAndPackChildren() throws Exception {
        Path mod = Files.createDirectory(temp.resolve("mod"));
        Files.writeString(mod.resolve("manifest.json"), "{\"ServerVersion\":\">=0.6.0-pre.0 <0.7.0\"}");
        FutureTask<Void> task = new FutureTask<>(() -> {
            var controller = new LauncherLibraryController(null, null, null, null, null, null, null,
                    Runnable::run, null, () -> null);
            var renderer = new LibraryWorldRenderer(null, null, null, null, null, null, null,
                    null, null, null, null, null, null);
            var makeModel = LauncherLibraryController.class.getDeclaredMethod("worldProjectModel",
                    InstalledProject.class, HytaleWorldConfig.class);
            makeModel.setAccessible(true);
            var copy = LibraryWorldRenderer.class.getDeclaredMethod("projectCopy", LibraryWorldProjectModel.class);
            copy.setAccessible(true);
            var config = new HytaleWorldConfig(null, Map.of());
            for (String source : List.of("MODTALE", "LOCAL", "CURSEFORGE")) {
                var installed = LibraryManifestCompatibilityTest.installed(source, mod);
                var model = (LibraryWorldProjectModel) makeModel.invoke(controller, installed, config);
                Node node = (Node) copy.invoke(renderer, model);
                Label compatibility = (Label) node.lookup(".library-version-metadata-build");
                assertEquals("0.6.x", compatibility.getText());
                assertTrue(compatibility.getTooltip().getText().contains("Manifest"));
                assertEquals("mod-1.2.3.jar", ((Label) node.lookup(".library-version-metadata-version")).getText());
            }
            var childModel = LauncherLibraryController.class.getDeclaredMethod("manifestContentItem",
                    HytaleInstalledMod.class, InstalledProjectReference.class, HytaleWorldConfig.class);
            childModel.setAccessible(true);
            var child = (LibraryWorldContentItem) childModel.invoke(controller,
                    new HytaleInstalledMod("group:mod", "Mod", "1.2.3", "", mod), null, config);
            var childRow = LibraryWorldRenderer.class.getDeclaredMethod("compactContentRow", LibraryWorldContentItem.class, List.class);
            childRow.setAccessible(true);
            Node node = (Node) childRow.invoke(renderer, child, List.of());
            assertEquals("0.6.x", ((Label) node.lookup(".library-version-metadata-build")).getText());

            Files.delete(mod.resolve("manifest.json"));
            var model = (LibraryWorldProjectModel) makeModel.invoke(controller,
                    LibraryManifestCompatibilityTest.installed("MODTALE", mod), config);
            assertTrue(model.display().hytaleCompatibility().isEmpty());
            assertNull(((Node) copy.invoke(renderer, model)).lookup(".library-version-metadata-build"),
                    "The recorded install build is not evidence of manifest compatibility");
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
