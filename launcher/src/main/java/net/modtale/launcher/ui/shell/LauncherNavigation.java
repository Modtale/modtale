package net.modtale.launcher.ui.shell;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import net.modtale.launcher.ui.common.LauncherView;

public final class LauncherNavigation {

    private Consumer<LauncherView> showHandler = this::activate;
    private LauncherView currentView = LauncherView.defaultView();
    private final Deque<Runnable> history = new ArrayDeque<>();
    private Runnable currentPage = () -> show(LauncherView.defaultView());
    private Runnable pendingPage;
    private boolean goingBack;

    public LauncherView currentView() {
        return currentView;
    }

    public void show(LauncherView view) {
        showHandler.accept(view == null ? LauncherView.defaultView() : view);
    }

    public void show(LauncherView view, Runnable restorePage) {
        pendingPage = restorePage;
        try {
            show(view);
        } finally {
            pendingPage = null;
        }
    }

    public void show(String viewId) {
        show(LauncherView.fromId(viewId));
    }

    public void back() {
        if (history.isEmpty()) {
            return;
        }
        goingBack = true;
        try {
            history.pop().run();
        } finally {
            goingBack = false;
        }
    }

    public void activate(LauncherView view) {
        LauncherView nextView = view == null ? LauncherView.defaultView() : view;
        if (nextView == currentView && pendingPage == null) {
            return;
        }
        if (!goingBack) {
            history.push(currentPage);
        }
        currentView = nextView;
        currentPage = pendingPage == null ? () -> show(nextView) : pendingPage;
    }

    public void bind(Consumer<LauncherView> showHandler) {
        this.showHandler = showHandler == null ? this::activate : showHandler;
    }
}
