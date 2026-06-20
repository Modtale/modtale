package net.modtale.launcher.ui.shell;

import java.util.function.Consumer;
import net.modtale.launcher.ui.common.LauncherView;

public final class LauncherNavigation {

    private Consumer<LauncherView> showHandler = this::activate;
    private LauncherView currentView = LauncherView.defaultView();

    public LauncherView currentView() {
        return currentView;
    }

    public void show(LauncherView view) {
        showHandler.accept(view == null ? LauncherView.defaultView() : view);
    }

    public void show(String viewId) {
        show(LauncherView.fromId(viewId));
    }

    public void activate(LauncherView view) {
        currentView = view == null ? LauncherView.defaultView() : view;
    }

    public void bind(Consumer<LauncherView> showHandler) {
        this.showHandler = showHandler == null ? this::activate : showHandler;
    }
}
