package net.modtale.launcher.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicReference;
import net.modtale.launcher.ui.common.LauncherView;
import org.junit.jupiter.api.Test;

class LauncherNavigationTest {
    @Test
    void backReturnsToThePageThatOpenedAProject() {
        for (LauncherView source : new LauncherView[] {
                LauncherView.PLAY, LauncherView.LIBRARY, LauncherView.DISCOVER, LauncherView.NOTIFICATIONS}) {
            LauncherNavigation navigation = new LauncherNavigation();
            navigation.bind(navigation::activate);
            navigation.show(source);
            navigation.show(LauncherView.PROJECT);
            navigation.back();
            assertEquals(source, navigation.currentView());
        }
    }

    @Test
    void backRestoresNestedPagesWithoutAddingHistory() {
        LauncherNavigation navigation = new LauncherNavigation();
        AtomicReference<String> page = new AtomicReference<>();
        navigation.show(LauncherView.LIBRARY);
        openPage(navigation, page, "project");
        navigation.activate(LauncherView.PROJECT);
        openPage(navigation, page, "creator");
        openPage(navigation, page, "another project");
        navigation.back();
        assertEquals("creator", page.get());
        navigation.back();
        assertEquals("project", page.get());
        navigation.back();
        assertEquals(LauncherView.LIBRARY, navigation.currentView());
        navigation.back();
        assertEquals(LauncherView.PLAY, navigation.currentView());
        navigation.back();
        assertEquals(LauncherView.PLAY, navigation.currentView());
    }

    @Test
    void refreshingAViewPreservesItsRestorationAction() {
        LauncherNavigation navigation = new LauncherNavigation();
        AtomicReference<String> page = new AtomicReference<>();
        openPage(navigation, page, "project");
        navigation.activate(LauncherView.PROJECT);
        navigation.show(LauncherView.SETTINGS);
        page.set(null);
        navigation.back();
        assertEquals("project", page.get());
        navigation.back();
        assertEquals(LauncherView.PLAY, navigation.currentView());
    }

    private void openPage(LauncherNavigation navigation, AtomicReference<String> page, String name) {
        page.set(name);
        navigation.show(LauncherView.PROJECT, () -> openPage(navigation, page, name));
    }
}
