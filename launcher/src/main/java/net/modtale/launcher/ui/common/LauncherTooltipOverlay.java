package net.modtale.launcher.ui.common;

import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

/** Draws help in the owner scene without creating a native popup window. */
public final class LauncherTooltipOverlay {
    private final Scene scene;
    private final Pane host;
    private final Label bubble = new Label();
    private final PauseTransition delay = new PauseTransition();
    private final PauseTransition expiry = new PauseTransition();
    private Node owner;
    private Tooltip help;
    private double x, y;

    public static void install(Scene scene, Pane host) {
        if (scene.getProperties().containsKey(LauncherTooltipOverlay.class)) return;
        scene.getProperties().put(LauncherTooltipOverlay.class, new LauncherTooltipOverlay(scene, host));
    }

    private LauncherTooltipOverlay(Scene scene, Pane host) {
        this.scene = scene; this.host = host;
        bubble.getStyleClass().add("tooltip");
        bubble.setId("launcher-tooltip-overlay");
        bubble.setManaged(false); bubble.setVisible(false);
        bubble.setMouseTransparent(true); bubble.setFocusTraversable(false);
        bubble.setWrapText(true);
        host.getChildren().add(bubble);
        delay.setOnFinished(event -> show());
        expiry.setOnFinished(event -> hide());
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, this::move);
        scene.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
            if (event.getTarget() == scene || event.getTarget() == host) clear();
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> clear());
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> clear());
        scene.addEventFilter(ScrollEvent.ANY, event -> clear());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> clear());
        scene.widthProperty().addListener(observable -> clear());
        scene.heightProperty().addListener(observable -> clear());
        ChangeListener<Boolean> windowState = (observable, before, after) -> { if (!after) clear(); };
        scene.windowProperty().addListener((observable, oldWindow, window) -> {
            clear();
            if (oldWindow != null) {
                oldWindow.focusedProperty().removeListener(windowState);
                oldWindow.showingProperty().removeListener(windowState);
            }
            if (window != null) {
                window.focusedProperty().addListener(windowState);
                window.showingProperty().addListener(windowState);
            }
        });
        if (scene.getWindow() != null) {
            scene.getWindow().focusedProperty().addListener(windowState);
            scene.getWindow().showingProperty().addListener(windowState);
        }
    }

    private void move(MouseEvent event) {
        Node target = event.getTarget() instanceof Node node ? node : null;
        Node nextOwner = null;
        Tooltip nextHelp = null;
        for (Node node = target; node != null; node = node.getParent()) {
            Tooltip tooltip = node instanceof Control control ? control.getTooltip() : null;
            if (tooltip == null) tooltip = LauncherTooltips.attached(node);
            if (tooltip == null) continue;
            // Stop JavaFX's native popup handler before this move reaches the control.
            Tooltip.uninstall(node, tooltip);
            if (nextHelp == null && tooltip.getText() != null && !tooltip.getText().isBlank()) {
                nextOwner = node; nextHelp = tooltip;
            }
        }
        x = event.getSceneX(); y = event.getSceneY();
        if (owner == nextOwner && help == nextHelp) return;
        clear(); owner = nextOwner; help = nextHelp;
        if (help != null) {
            delay.setDuration(duration(help.getShowDelay(), Duration.millis(1000)));
            delay.playFromStart();
        }
    }

    void show() {
        if (owner == null || owner.getScene() != scene || scene.getWindow() == null || !scene.getWindow().isShowing()) {
            clear(); return;
        }
        for (Node node = owner; node != null; node = node.getParent()) {
            if (!node.isVisible()) { clear(); return; }
        }
        bubble.textProperty().bind(help.textProperty());
        bubble.setNodeOrientation(owner.getEffectiveNodeOrientation());
        bubble.setMaxWidth(Math.max(1, Math.min(360, host.getWidth() - 16)));
        bubble.toFront(); bubble.setVisible(true); bubble.applyCss(); bubble.autosize();
        Point2D point = host.sceneToLocal(x, y);
        double left = Math.max(8, Math.min(point.getX() + 12, host.getWidth() - bubble.getWidth() - 8));
        double top = point.getY() + 20;
        if (top + bubble.getHeight() > host.getHeight() - 8) top = point.getY() - bubble.getHeight() - 12;
        bubble.relocate(left, Math.max(8, top));
        Duration lifetime = duration(help.getShowDuration(), Duration.seconds(5));
        if (!lifetime.isIndefinite()) { expiry.setDuration(lifetime); expiry.playFromStart(); }
    }

    private static Duration duration(Duration value, Duration fallback) {
        return value == null || value.isUnknown() || value.lessThan(Duration.ZERO) ? fallback : value;
    }

    private void hide() {
        delay.stop(); expiry.stop(); bubble.setVisible(false); bubble.textProperty().unbind();
    }

    private void clear() { hide(); owner = null; help = null; }
}
