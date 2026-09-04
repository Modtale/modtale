package net.modtale.launcher.ui.common;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Window;
import javafx.util.Duration;
import net.modtale.launcher.LauncherPerformanceProbe;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;

/**
 * Gives every launcher scroll surface the same input behavior as the web app while
 * keeping expensive hover animations quiet during a gesture.
 */
public final class LauncherScrollSupport {

    private static final String INSTALLED_PROPERTY = LauncherScrollSupport.class.getName() + ".installed";
    private static final String SCROLLBAR_DISCOVERY_PROPERTY = LauncherScrollSupport.class.getName() + ".scrollbarDiscovery";
    private static final String HORIZONTAL_SCROLL_PROPERTY = LauncherScrollSupport.class.getName() + ".horizontal";
    private static final String HORIZONTAL_LOCK_PROPERTY = LauncherScrollSupport.class.getName() + ".horizontalLock";
    private static final PseudoClass SCROLLING = PseudoClass.getPseudoClass("scrolling");
    private static final Duration INTERACTION_IDLE_DELAY = Duration.millis(220);
    private static final Duration SCROLLBAR_IDLE_DELAY = Duration.millis(750);
    private static final Duration SCROLLBAR_FADE_DURATION = Duration.millis(180);
    private static final double JAVAFX_DISCRETE_WHEEL_UNIT = 40;
    private static final double BROWSER_DISCRETE_WHEEL_UNIT = 120;
    private static final double WHEEL_UNIT_TOLERANCE = 0.01;

    private final Supplier<Node> rootSupplier;
    private final InteractionIdleTimer interactionIdleTimer;
    private final LauncherScrollAnimator animator = new LauncherScrollAnimator();
    private final EventHandler<ScrollEvent> scrollHandler = this::observeNativeScroll;
    private final Set<Node> configuredNodes = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<Node> installedRoots = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<ScrollBar, ScrollbarVisibility> scrollbarVisibility = new WeakHashMap<>();
    private final ListChangeListener<Window> windowListener = change -> {
        while (change.next()) {
            if (change.wasAdded()) change.getAddedSubList().forEach(this::installWindow);
        }
    };
    private boolean observingWindows;

    public LauncherScrollSupport(Supplier<Node> rootSupplier) {
        this.rootSupplier = rootSupplier;
        PauseTransition transition = new PauseTransition(INTERACTION_IDLE_DELAY);
        transition.setOnFinished(event -> clearScrollInteraction());
        this.interactionIdleTimer = new InteractionIdleTimer() {
            @Override
            public void restart() {
                transition.playFromStart();
            }

            @Override
            public void stop() {
                transition.stop();
            }
        };
    }

    LauncherScrollSupport(Supplier<Node> rootSupplier, InteractionIdleTimer interactionIdleTimer) {
        this.rootSupplier = rootSupplier;
        this.interactionIdleTimer = interactionIdleTimer;
    }

    public void configure(ScrollPane scrollPane, boolean horizontal) {
        scrollPane.getProperties().put(HORIZONTAL_SCROLL_PROPERTY, horizontal);
        if (!horizontal) {
            lockHorizontalPosition(scrollPane);
        }
        configureScrollbarDiscovery(scrollPane);
        Node root = rootSupplier.get();
        if (root != null) {
            install(root);
        } else if (installedRoots.isEmpty()) {
            configureNode(scrollPane);
        }
    }

    private void configureScrollbarDiscovery(ScrollPane scrollPane) {
        if (Boolean.TRUE.equals(scrollPane.getProperties().get(SCROLLBAR_DISCOVERY_PROPERTY))) return;
        scrollPane.getProperties().put(SCROLLBAR_DISCOVERY_PROPERTY, Boolean.TRUE);
        scrollPane.skinProperty().addListener((observable, previous, current) ->
                Platform.runLater(() -> configureScrollbars(scrollPane)));
        Platform.runLater(() -> configureScrollbars(scrollPane));
    }

    public void install(Node root) {
        if (root == null || installedRoots.contains(root)) return;
        if (installedRoots.isEmpty()) {
            for (Node configuredNode : configuredNodes) {
                configuredNode.removeEventFilter(ScrollEvent.SCROLL, scrollHandler);
                configuredNode.getProperties().remove(INSTALLED_PROPERTY);
            }
            configuredNodes.clear();
        }
        configureNode(root);
        installedRoots.add(root);
        Platform.runLater(() -> configureScrollbars(root));
        if (!observingWindows) {
            observingWindows = true;
            Window.getWindows().addListener(windowListener);
            Window.getWindows().forEach(this::installWindow);
        }
    }

    private void installWindow(Window window) {
        if (window == null) return;
        Scene scene = window.getScene();
        if (scene != null && scene.getRoot() != null) {
            install(scene.getRoot());
            return;
        }
        window.sceneProperty().addListener((observable, previous, current) -> {
            if (current != null && current.getRoot() != null) install(current.getRoot());
        });
    }

    public void smoothScrollTo(ScrollPane pane, double horizontalValue, double verticalValue) {
        if (pane == null) return;
        revealScrollbars(pane);
        activateScrollInteraction();
        interactionIdleTimer.restart();
        animator.animateTo(pane, horizontalValue, verticalValue, System.nanoTime());
    }

    void configureNode(Node scrollNode) {
        if (scrollNode == null || Boolean.TRUE.equals(scrollNode.getProperties().get(INSTALLED_PROPERTY))) {
            return;
        }
        scrollNode.getProperties().put(INSTALLED_PROPERTY, Boolean.TRUE);
        scrollNode.addEventFilter(ScrollEvent.SCROLL, scrollHandler);
        configuredNodes.add(scrollNode);
    }

    private void observeNativeScroll(ScrollEvent event) {
        long operationStart = LauncherPerformanceProbe.operationStartNanos();
        try {
            if (event.isControlDown()) {
                return;
            }
            activateScrollInteraction();
            interactionIdleTimer.restart();
            ScrollRequest request = scrollRequest(event);
            if (request == null) return;
            revealScrollbars(request.pane());
            if (isPreciseScroll(event)) {
                animator.cancel(request.pane());
                return;
            }
            animator.animate(request.pane(), request.metrics(), request.deltaX(), request.deltaY(), System.nanoTime());
            event.consume();
        } finally {
            LauncherPerformanceProbe.recordOperation("scroll.input", operationStart);
        }
    }

    private ScrollRequest scrollRequest(ScrollEvent event) {
        if (!(event.getTarget() instanceof Node target)) return null;

        double deltaX = -browserDelta(event, event.getDeltaX());
        double deltaY = -browserDelta(event, event.getDeltaY());
        if (event.isShiftDown() && Math.abs(deltaX) < 0.01) {
            deltaX = deltaY;
            deltaY = 0;
        }

        Node candidate = target;
        while (candidate != null) {
            if (candidate instanceof ScrollPane pane) {
                LauncherScrollAnimator.ScrollMetrics metrics = LauncherScrollAnimator.metrics(pane);
                boolean horizontal = horizontalScrollingEnabled(pane);
                double requestedX = horizontalDelta(horizontal, deltaX);
                double requestedY = deltaY;
                if (horizontal && Math.abs(requestedY) >= Math.abs(requestedX)
                        && metrics.maxY() <= 0 && metrics.maxX() > 0) {
                    requestedX = requestedY;
                    requestedY = 0;
                }
                if (canConsume(pane, metrics, requestedX, requestedY)) {
                    return new ScrollRequest(pane, metrics, requestedX, requestedY);
                }
            }
            candidate = candidate.getParent();
        }
        return null;
    }

    private static boolean horizontalScrollingEnabled(ScrollPane pane) {
        return !Boolean.FALSE.equals(pane.getProperties().get(HORIZONTAL_SCROLL_PROPERTY));
    }

    static double horizontalDelta(boolean horizontalScrollingEnabled, double requestedDelta) {
        return horizontalScrollingEnabled ? requestedDelta : 0;
    }

    private static void lockHorizontalPosition(ScrollPane pane) {
        if (Boolean.TRUE.equals(pane.getProperties().get(HORIZONTAL_LOCK_PROPERTY))) {
            resetHorizontalPosition(pane);
            return;
        }
        pane.getProperties().put(HORIZONTAL_LOCK_PROPERTY, Boolean.TRUE);
        pane.hvalueProperty().addListener((observable, oldValue, newValue) -> resetHorizontalPosition(pane));
        pane.hminProperty().addListener((observable, oldValue, newValue) -> resetHorizontalPosition(pane));
        resetHorizontalPosition(pane);
    }

    static void resetHorizontalPosition(ScrollPane pane) {
        if (pane != null && Math.abs(pane.getHvalue() - pane.getHmin()) > 0.0001) {
            pane.setHvalue(pane.getHmin());
        }
    }

    private boolean canConsume(
            ScrollPane pane,
            LauncherScrollAnimator.ScrollMetrics metrics,
            double deltaX,
            double deltaY
    ) {
        boolean horizontal = canMove(animator.desiredHorizontalOffset(pane, metrics), metrics.maxX(), deltaX);
        boolean vertical = canMove(animator.desiredVerticalOffset(pane, metrics), metrics.maxY(), deltaY);
        return horizontal || vertical;
    }

    private static boolean canMove(double current, double maximum, double delta) {
        if (maximum <= 0 || Math.abs(delta) < 0.01) return false;
        return delta < 0 ? current > 0.01 : current < maximum - 0.01;
    }

    static boolean isPreciseScroll(ScrollEvent event) {
        return isPreciseScroll(event, eventOutputScale(event));
    }

    static boolean isPreciseScroll(ScrollEvent event, double outputScale) {
        return event.isInertia()
                || event.getTouchCount() > 0
                || event.isDirect()
                || (hasPixelUnits(event) && !looksLikeJavaFxDiscreteWheel(event, outputScale));
    }

    static double browserDelta(ScrollEvent event, double delta) {
        return browserDelta(event, delta, eventOutputScale(event));
    }

    static double browserDelta(ScrollEvent event, double delta, double outputScale) {
        if (!looksLikeJavaFxDiscreteWheel(event, outputScale)) return delta;
        return delta / JAVAFX_DISCRETE_WHEEL_UNIT * BROWSER_DISCRETE_WHEEL_UNIT;
    }

    private static boolean hasPixelUnits(ScrollEvent event) {
        return event.getTextDeltaXUnits() == ScrollEvent.HorizontalTextScrollUnits.NONE
                && event.getTextDeltaYUnits() == ScrollEvent.VerticalTextScrollUnits.NONE;
    }

    private static boolean looksLikeJavaFxDiscreteWheel(ScrollEvent event, double outputScale) {
        if (!hasPixelUnits(event) || event.isDirect() || event.isInertia() || event.getTouchCount() > 0) {
            return false;
        }
        double dominantDelta = Math.max(Math.abs(event.getDeltaX()), Math.abs(event.getDeltaY()));
        double logicalWheelUnit = JAVAFX_DISCRETE_WHEEL_UNIT / normalizedScale(outputScale);
        if (dominantDelta < logicalWheelUnit - WHEEL_UNIT_TOLERANCE) return false;
        double wheelSteps = dominantDelta / logicalWheelUnit;
        return Math.abs(wheelSteps - Math.rint(wheelSteps)) <= WHEEL_UNIT_TOLERANCE;
    }

    private static double eventOutputScale(ScrollEvent event) {
        if (!(event.getTarget() instanceof Node target)
                || target.getScene() == null
                || target.getScene().getWindow() == null) {
            return 1;
        }
        return normalizedScale(Math.max(
                target.getScene().getWindow().getOutputScaleX(),
                target.getScene().getWindow().getOutputScaleY()
        ));
    }

    private static double normalizedScale(double scale) {
        return Double.isFinite(scale) && scale > 0 ? scale : 1;
    }

    private void activateScrollInteraction() {
        interactionIdleTimer.stop();
        Node root = rootSupplier.get();
        if (root != null) {
            root.getProperties().put(ProjectCardFactory.SCROLL_ACTIVE_PROPERTY, Boolean.TRUE);
            root.pseudoClassStateChanged(SCROLLING, true);
        }
    }

    private void clearScrollInteraction() {
        Node root = rootSupplier.get();
        if (root != null) {
            root.getProperties().remove(ProjectCardFactory.SCROLL_ACTIVE_PROPERTY);
            root.pseudoClassStateChanged(SCROLLING, false);
        }
    }

    private void configureScrollbars(Node root) {
        root.lookupAll(".scroll-bar").stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .forEach(this::configureScrollbar);
    }

    private void revealScrollbars(ScrollPane pane) {
        configureScrollbars(pane);
        pane.lookupAll(".scroll-bar").stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .filter(Node::isVisible)
                .forEach(bar -> scrollbarVisibility.get(bar).reveal());
    }

    private void configureScrollbar(ScrollBar bar) {
        if (scrollbarVisibility.containsKey(bar)) return;
        ScrollbarVisibility visibility = new ScrollbarVisibility(bar);
        scrollbarVisibility.put(bar, visibility);
        bar.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> visibility.reveal());
        bar.addEventHandler(MouseEvent.MOUSE_EXITED, event -> visibility.scheduleFade());
        bar.valueProperty().addListener((observable, previous, value) -> visibility.reveal());
        bar.setOpacity(0);
    }

    private static final class ScrollbarVisibility {
        private final ScrollBar bar;
        private final PauseTransition idle = new PauseTransition(SCROLLBAR_IDLE_DELAY);
        private final FadeTransition fade;

        private ScrollbarVisibility(ScrollBar bar) {
            this.bar = bar;
            fade = new FadeTransition(SCROLLBAR_FADE_DURATION, bar);
            idle.setOnFinished(event -> fade());
        }

        private void reveal() {
            fade.stop();
            idle.stop();
            bar.setOpacity(1);
            scheduleFade();
        }

        private void scheduleFade() {
            idle.playFromStart();
        }

        private void fade() {
            if (bar.isHover()) {
                scheduleFade();
                return;
            }
            fade.setFromValue(bar.getOpacity());
            fade.setToValue(0);
            fade.playFromStart();
        }
    }

    interface InteractionIdleTimer {
        void restart();

        void stop();
    }

    private record ScrollRequest(
            ScrollPane pane,
            LauncherScrollAnimator.ScrollMetrics metrics,
            double deltaX,
            double deltaY
    ) {
    }
}
