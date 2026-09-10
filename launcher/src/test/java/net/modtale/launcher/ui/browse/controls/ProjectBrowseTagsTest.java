package net.modtale.launcher.ui.browse.controls;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProjectBrowseTagsTest {
    @BeforeAll
    static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { /* Shared toolkit. */ }
    }

    @Test
    void embeddedTagsKeepSelectionThroughSearchAndResetWithOtherFilters() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            AtomicInteger searches = new AtomicInteger();
            var tags = new ProjectBrowseTags(searches::incrementAndGet, () -> {});
            var filters = new ProjectBrowseFilterOptions(searches::incrementAndGet, () -> {}, tags);
            VBox popover = filters.popover();
            popover.setVisible(true);
            popover.setManaged(true);
            popover.setMaxHeight(450);
            var root = new StackPane(popover);
            var scene = new Scene(root, 400, 500);
            scene.getStylesheets().add(getClass().getResource(
                    "/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
            root.resize(400, 500);
            root.applyCss();
            root.layout();
            ScrollPane options = (ScrollPane) popover.getChildren().getFirst();
            assertSame(tags.section(), ((VBox) options.getContent()).getChildren().getFirst());
            assertTrue(popover.getHeight() <= 450);
            Button first = tags.section().lookupAll(".tag-chip").stream().map(Button.class::cast)
                    .filter(b -> b.getText().equals(BrowseOptions.GLOBAL_TAGS.get(0))).findFirst().orElseThrow();
            Button second = tags.section().lookupAll(".tag-chip").stream().map(Button.class::cast)
                    .filter(b -> b.getText().equals(BrowseOptions.GLOBAL_TAGS.get(1))).findFirst().orElseThrow();
            first.fire();
            second.fire();
            assertEquals(2, searches.get());
            assertEquals(2, tags.selectedCount());
            assertEquals(1, filters.activeFilterCount(), "Tags count as one category");
            String selected = tags.selectedQuery();
            TextField search = (TextField) tags.section().getChildren().get(1);
            search.setText("no-such-tag");
            assertFalse(first.isManaged());
            assertEquals(selected, tags.selectedQuery());
            assertEquals(2, searches.get(), "Tag search only filters choices");
            ((Button) tags.section().lookup(".tag-clear-button")).fire();
            assertTrue(tags.isEmpty());
            assertTrue(search.getText().isEmpty());
            assertTrue(first.isManaged());
            first.fire();
            ((Button) popover.lookup(".filter-toggle-button")).fire();
            assertEquals(2, filters.activeFilterCount());
            int beforeReset = searches.get();
            ((Button) popover.lookup(".filter-reset-button")).fire();
            assertNull(tags.selectedQuery());
            assertEquals(0, filters.activeFilterCount());
            assertEquals(beforeReset + 1, searches.get());
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
