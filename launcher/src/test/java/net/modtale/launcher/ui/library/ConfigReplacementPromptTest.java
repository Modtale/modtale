package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld;
import net.modtale.launcher.model.worldlist.WorldListConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigReplacementPromptTest {
    @TempDir Path directory;

    @BeforeAll static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { }
    }

    @Test void replaceKeepAndCloseReturnIndependentDecisions() throws Exception {
        StackPane host = fx(StackPane::new);
        var controller = fx(() -> new LauncherLibraryController(null, null, null, null, null, null, null,
                Runnable::run, null, () -> host));
        var confirm = LauncherLibraryController.class.getDeclaredMethod("confirmConfigReplacement", HytaleWorld.class, List.class);
        confirm.setAccessible(true);
        var world = new HytaleWorld(directory, "My universe", directory.resolve("config.json"), "", "", 0, 0, Instant.EPOCH);
        for (String action : List.of("Replace configs", "Keep existing", "close")) {
            var decision = CompletableFuture.supplyAsync(() -> {
                try {
                    return (Boolean) confirm.invoke(controller, world,
                            List.of(new WorldListConfig("WORLD", "Example/config.json", "{}")));
                } catch (ReflectiveOperationException ex) { throw new RuntimeException(ex); }
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (fx(() -> host.lookup(".status-modal") == null) && System.nanoTime() < deadline) Thread.sleep(10);
            fx(() -> {
                assertNotNull(host.lookup(".status-modal"));
                assertTrue(host.lookupAll(".label").stream().map(node -> ((Label) node).getText())
                        .anyMatch(text -> text.contains("My universe") && text.contains("Example")));
                Button button = action.equals("close") ? (Button) host.lookup(".status-modal-close")
                        : host.lookupAll(".button").stream().map(node -> (Button) node)
                                .filter(node -> action.equals(node.getText())).findFirst().orElseThrow();
                button.fire();
                return null;
            });
            assertEquals(action.equals("Replace configs"), decision.get(5, TimeUnit.SECONDS));
            assertTrue(fx(() -> host.getChildren().isEmpty()));
        }
    }

    private static <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        var task = new FutureTask<T>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
