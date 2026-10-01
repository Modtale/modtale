package net.modtale.launcher.ui.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import javafx.animation.AnimationTimer;
import javafx.beans.value.ChangeListener;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;

/** Browser-style middle-click autoscroll for JavaFX scroll surfaces. */
final class LauncherAutoScroll {
    private static final double DEAD_ZONE = 15;
    // Chromium's panning curve is 0.000008 * distance^2.2 pixels/ms.
    private static final double CHROMIUM_PIXELS_PER_SECOND = 0.008;
    private static final double MAX_PIXELS_PER_SECOND = 1800;
    private static final double MAX_FRAME_SECONDS = 0.05;
    private static final double INDICATOR_SIZE = 32;

    private final LauncherScrollAnimator animator;
    private final Runnable onScroll;
    private final Consumer<ScrollPane> revealScrollbars;
    private final Map<String, Image> images = new HashMap<>();
    private final Map<String, ImageCursor> cursors = new HashMap<>();
    private final Set<Node> installedRoots = Collections.newSetFromMap(new WeakHashMap<>());
    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            tick(now);
        }
    };
    private ScrollPane pane;
    private Window window;
    private Scene cursorScene;
    private Cursor previousCursor;
    private ChangeListener<Boolean> focusListener;
    private ImageView indicator;
    private StackPane indicatorHost;
    private double anchorX;
    private double anchorY;
    private double pointerX;
    private double pointerY;
    private long lastFrame;
    private MouseButton suppressedClick;

    LauncherAutoScroll(LauncherScrollAnimator animator, Runnable onScroll,
                       Consumer<ScrollPane> revealScrollbars) {
        this.animator = animator;
        this.onScroll = onScroll;
        this.revealScrollbars = revealScrollbars;
    }

    void install(Node root) {
        if (!installedRoots.add(root)) return;
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, this::pressed);
        root.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == suppressedClick) {
                suppressedClick = null;
                event.consume();
            }
        });
        root.addEventFilter(MouseEvent.MOUSE_MOVED, this::moved);
        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::moved);
        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (pane != null && event.getCode() == KeyCode.ESCAPE) {
                stop();
                event.consume();
            }
        });
    }

    private void pressed(MouseEvent event) {
        suppressedClick = null;
        if (pane != null) {
            stop();
            // The dismissal click should not activate a control underneath it.
            suppressedClick = event.getButton();
            event.consume();
            return;
        }
        if (event.getButton() != MouseButton.MIDDLE || !(event.getTarget() instanceof Node target)) return;
        if (insideScrollbar(target)) return;
        ScrollPane candidate = scrollableAncestor(target);
        if (candidate == null && target.getScene() != null) {
            candidate = mainScrollPane(target.getScene().getRoot());
        }
        if (candidate == null || candidate.getScene() == null || candidate.getScene().getWindow() == null) return;
        pane = candidate;
        window = candidate.getScene().getWindow();
        cursorScene = candidate.getScene();
        previousCursor = cursorScene.getCursor();
        anchorX = pointerX = event.getScreenX();
        anchorY = pointerY = event.getScreenY();
        lastFrame = 0;
        animator.cancel(pane);
        revealScrollbars.accept(pane);
        showIndicator(event);
        updateCursor();
        focusListener = (observable, oldValue, focused) -> {
            if (!focused) stop();
        };
        window.focusedProperty().addListener(focusListener);
        timer.start();
        suppressedClick = MouseButton.MIDDLE;
        event.consume();
    }

    private void moved(MouseEvent event) {
        if (pane == null) return;
        pointerX = event.getScreenX();
        pointerY = event.getScreenY();
        updateCursor();
    }

    void tick(long now) {
        if (pane == null || pane.getScene() == null || pane.getScene().getWindow() != window
                || !pane.isVisible()) {
            stop();
            return;
        }
        if (lastFrame == 0) {
            lastFrame = now;
            return;
        }
        double seconds = Math.min((now - lastFrame) / 1_000_000_000.0, MAX_FRAME_SECONDS);
        lastFrame = now;
        LauncherScrollAnimator.ScrollMetrics metrics = LauncherScrollAnimator.metrics(pane);
        double dx = metrics.maxX() > 0 && LauncherScrollSupport.horizontalScrollingEnabled(pane)
                ? velocity(pointerX - anchorX) * seconds : 0;
        double dy = metrics.maxY() > 0 ? velocity(pointerY - anchorY) * seconds : 0;
        if (dx == 0 && dy == 0) return;
        animator.scrollBy(pane, metrics, dx, dy);
        onScroll.run();
    }

    private void showIndicator(MouseEvent event) {
        if (!(pane.getScene().getRoot() instanceof StackPane host)) return;
        indicator = new ImageView(image(middleCursorName()));
        indicator.setMouseTransparent(true);
        indicator.setManaged(false);
        indicator.relocate(Math.round(event.getSceneX() - INDICATOR_SIZE / 2),
                Math.round(event.getSceneY() - INDICATOR_SIZE / 2));
        indicatorHost = host;
        host.getChildren().add(indicator);
    }

    void stop() {
        timer.stop();
        if (cursorScene != null) cursorScene.setCursor(previousCursor);
        cursorScene = null;
        previousCursor = null;
        if (window != null && focusListener != null) window.focusedProperty().removeListener(focusListener);
        focusListener = null;
        if (indicatorHost != null) indicatorHost.getChildren().remove(indicator);
        indicator = null;
        indicatorHost = null;
        pane = null;
        window = null;
        lastFrame = 0;
    }

    static double velocity(double displacement) {
        double distance = Math.abs(displacement);
        if (distance <= DEAD_ZONE) return 0;
        return Math.copySign(Math.min(Math.pow(distance, 2.2) * CHROMIUM_PIXELS_PER_SECOND,
                MAX_PIXELS_PER_SECOND), displacement);
    }

    private static ScrollPane scrollableAncestor(Node target) {
        for (Node node = target; node != null; node = node.getParent()) {
            if (node instanceof ScrollPane scrollPane
                    && LauncherScrollAnimator.metrics(scrollPane).scrollable()) return scrollPane;
        }
        return null;
    }

    private static ScrollPane mainScrollPane(Node root) {
        if (root == null || !root.isVisible()) return null;
        ScrollPane best = root instanceof ScrollPane pane
                && LauncherScrollAnimator.metrics(pane).scrollable() ? pane : null;
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                ScrollPane candidate = mainScrollPane(child);
                if (candidate != null && (best == null || viewportArea(candidate) > viewportArea(best))) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static double viewportArea(ScrollPane pane) {
        return pane.getViewportBounds().getWidth() * pane.getViewportBounds().getHeight();
    }

    private void updateCursor() {
        if (cursorScene == null || pane == null) return;
        LauncherScrollAnimator.ScrollMetrics metrics = LauncherScrollAnimator.metrics(pane);
        boolean horizontal = metrics.maxX() > 0 && LauncherScrollSupport.horizontalScrollingEnabled(pane);
        boolean vertical = metrics.maxY() > 0;
        int x = horizontal ? (int) Math.signum(velocity(pointerX - anchorX)) : 0;
        int y = vertical ? (int) Math.signum(velocity(pointerY - anchorY)) : 0;
        String name = y < 0 ? "pan_north" : y > 0 ? "pan_south" : "pan_middle";
        if (x != 0) {
            String direction = x < 0 ? "west" : "east";
            name = y == 0 ? "pan_" + direction : name + "_" + direction;
        } else if (y == 0) {
            name = middleCursorName();
        }
        String cursorName = name;
        cursorScene.setCursor(cursors.computeIfAbsent(cursorName,
                key -> new ImageCursor(image(key), 16, 16)));
    }

    private String middleCursorName() {
        LauncherScrollAnimator.ScrollMetrics metrics = LauncherScrollAnimator.metrics(pane);
        boolean horizontal = metrics.maxX() > 0 && LauncherScrollSupport.horizontalScrollingEnabled(pane);
        boolean vertical = metrics.maxY() > 0;
        return horizontal && vertical ? "pan_middle" : horizontal
                ? "pan_middle_horizontal" : "pan_middle_vertical";
    }

    private Image image(String name) {
        return images.computeIfAbsent(name, key -> new Image(
                LauncherAutoScroll.class.getResource("autoscroll/" + key + ".png").toExternalForm()));
    }

    private static boolean insideScrollbar(Node target) {
        for (Node node = target; node != null; node = node.getParent()) {
            if (node instanceof ScrollBar) return true;
        }
        return false;
    }
}
