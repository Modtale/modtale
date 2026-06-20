package net.modtale.launcher.ui.common;

import java.util.function.Supplier;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;
import net.modtale.launcher.LauncherPerformanceProbe;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;

public final class LauncherScrollSupport {

    private static final String DROPDOWN_POPOVER_STYLE_CLASS = "filter-popover";
    private static final double LINE_PIXELS = 48;
    private static final double WHEEL_VELOCITY_GAIN = 8.6;
    private static final double PIXEL_VELOCITY_GAIN = 0.42;
    private static final double INERTIA_VELOCITY_GAIN = 0.12;
    private static final double VELOCITY_CARRY = 0.28;
    private static final double REVERSE_VELOCITY_CARRY = 0.08;
    private static final double FRICTION_PER_SECOND = 0.035;
    private static final double FRAME_SECONDS_CAP = 0.032;
    private static final double FIRST_INPUT_SECONDS = 1.0 / 60.0;
    private static final double INPUT_SECONDS_MIN = 1.0 / 180.0;
    private static final double INPUT_SECONDS_MAX = 1.0 / 24.0;
    private static final double MAX_VELOCITY = 8200;
    private static final double STOP_VELOCITY = 10;
    private static final double SMOOTH_PIXELS_EPSILON = 0.35;
    private static final double WHEEL_SMOOTHING_SECONDS = 0.052;
    private static final double PENDING_PIXELS_CAP = 2100;
    private static final PseudoClass SCROLLING = PseudoClass.getPseudoClass("scrolling");

    private final Supplier<Node> rootSupplier;
    private final PauseTransition interactionCooldown = new PauseTransition(Duration.millis(120));

    public LauncherScrollSupport(Supplier<Node> rootSupplier) {
        this.rootSupplier = rootSupplier;
        interactionCooldown.setOnFinished(event -> clearScrollInteraction());
    }

    public void configure(ScrollPane scrollPane, boolean horizontal) {
        new MomentumScrollController(scrollPane, horizontal).install();
    }

    private final class MomentumScrollController {

        private final ScrollPane scrollPane;
        private final boolean horizontal;
        private double directPixels;
        private double smoothPixels;
        private double velocity;
        private long previousInput;
        private long previousFrame;

        private final AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                animate(now);
            }
        };

        private MomentumScrollController(ScrollPane scrollPane, boolean horizontal) {
            this.scrollPane = scrollPane;
            this.horizontal = horizontal;
        }

        private void install() {
            scrollPane.addEventFilter(ScrollEvent.SCROLL, this::handleScroll);
        }

        private void handleScroll(ScrollEvent event) {
            long operationStart = LauncherPerformanceProbe.operationStartNanos();
            try {
                if (event.isControlDown() || scrollPane.getContent() == null) {
                    return;
                }
                if (eventTargetInsideStyleClass(event, DROPDOWN_POPOVER_STYLE_CLASS)
                        || eventTargetInsideNestedScrollPane(event, scrollPane)) {
                    stopMomentum();
                    return;
                }
                double eventPixels = scrollPixels(event, horizontal, viewportSize(scrollPane, horizontal));
                if (Math.abs(eventPixels) < 0.5) {
                    return;
                }
                double movementPixels = -eventPixels;
                if (!canScrollByPixels(scrollPane, horizontal, movementPixels)) {
                    stopMomentum();
                    return;
                }
                activateScrollInteraction();
                ScrollInputKind inputKind = inputKind(event, horizontal);
                if (inputKind == ScrollInputKind.PIXEL) {
                    directPixels = clamp(directPixels + movementPixels, -PENDING_PIXELS_CAP, PENDING_PIXELS_CAP);
                } else {
                    smoothPixels = clamp(smoothPixels + movementPixels, -PENDING_PIXELS_CAP, PENDING_PIXELS_CAP);
                }
                long inputNow = System.nanoTime();
                double inputVelocity = inputVelocity(event, inputKind, movementPixels, inputNow);
                previousInput = inputNow;
                velocity = nextVelocity(velocity, inputVelocity);
                timer.start();
                event.consume();
            } finally {
                LauncherPerformanceProbe.recordOperation("scroll.input", operationStart);
            }
        }

        private double inputVelocity(ScrollEvent event, ScrollInputKind inputKind, double movementPixels, long inputNow) {
            if (inputKind == ScrollInputKind.PIXEL) {
                double seconds = previousInput == 0
                        ? FIRST_INPUT_SECONDS
                        : clamp((inputNow - previousInput) / 1_000_000_000.0, INPUT_SECONDS_MIN, INPUT_SECONDS_MAX);
                double gain = event.isInertia() ? INERTIA_VELOCITY_GAIN : PIXEL_VELOCITY_GAIN;
                return movementPixels / seconds * gain;
            }
            return movementPixels * WHEEL_VELOCITY_GAIN;
        }

        private void animate(long now) {
            long operationStart = LauncherPerformanceProbe.operationStartNanos();
            try {
                if (previousFrame == 0) {
                    previousFrame = now - Math.round(FIRST_INPUT_SECONDS * 1_000_000_000.0);
                }
                double deltaSeconds = Math.min(FRAME_SECONDS_CAP, (now - previousFrame) / 1_000_000_000.0);
                previousFrame = now;

                double movementPixels = directPixels + drainSmoothPixels(deltaSeconds);
                directPixels = 0;
                boolean hasPendingPixels = Math.abs(movementPixels) >= 0.25 || Math.abs(smoothPixels) >= SMOOTH_PIXELS_EPSILON;
                if (!hasPendingPixels && Math.abs(velocity) >= STOP_VELOCITY) {
                    movementPixels += velocity * deltaSeconds;
                }
                if (!hasPendingPixels && Math.abs(movementPixels) < 0.25 && Math.abs(velocity) < STOP_VELOCITY) {
                    stopMomentum();
                    return;
                }
                if (Math.abs(movementPixels) >= 0.25 && !scrollByPixels(scrollPane, horizontal, movementPixels)) {
                    stopMomentum();
                    return;
                }
                velocity *= Math.pow(FRICTION_PER_SECOND, deltaSeconds);
            } finally {
                LauncherPerformanceProbe.recordOperation("scroll.animate", operationStart);
            }
        }

        private double drainSmoothPixels(double deltaSeconds) {
            if (Math.abs(smoothPixels) < SMOOTH_PIXELS_EPSILON) {
                double pixels = smoothPixels;
                smoothPixels = 0;
                return pixels;
            }
            double pixels = smoothPixels * smoothingFactor(deltaSeconds);
            smoothPixels -= pixels;
            return pixels;
        }

        private void stopMomentum() {
            directPixels = 0;
            smoothPixels = 0;
            velocity = 0;
            previousInput = 0;
            previousFrame = 0;
            timer.stop();
            scheduleScrollInteractionIdle();
        }
    }

    private enum ScrollInputKind {
        PIXEL,
        WHEEL
    }

    private void activateScrollInteraction() {
        interactionCooldown.stop();
        Node root = rootSupplier.get();
        if (root != null
                && !Boolean.TRUE.equals(root.getProperties().get(ProjectCardFactory.SCROLL_ACTIVE_PROPERTY))) {
            root.getProperties().put(ProjectCardFactory.SCROLL_ACTIVE_PROPERTY, Boolean.TRUE);
            root.pseudoClassStateChanged(SCROLLING, true);
        }
    }

    private void scheduleScrollInteractionIdle() {
        interactionCooldown.playFromStart();
    }

    private void clearScrollInteraction() {
        Node root = rootSupplier.get();
        if (root != null
                && Boolean.TRUE.equals(root.getProperties().get(ProjectCardFactory.SCROLL_ACTIVE_PROPERTY))) {
            root.getProperties().remove(ProjectCardFactory.SCROLL_ACTIVE_PROPERTY);
            root.pseudoClassStateChanged(SCROLLING, false);
        }
    }

    private static boolean canScrollByPixels(ScrollPane scrollPane, boolean horizontal, double pixels) {
        double scrollable = scrollablePixels(scrollPane, horizontal);
        if (scrollable <= 1) {
            return false;
        }
        double previous = scrollValue(scrollPane, horizontal);
        double next = clamp(previous + pixels / scrollable, scrollMin(scrollPane, horizontal), scrollMax(scrollPane, horizontal));
        return Math.abs(next - previous) >= 0.0001;
    }

    private static boolean scrollByPixels(ScrollPane scrollPane, boolean horizontal, double pixels) {
        double scrollable = scrollablePixels(scrollPane, horizontal);
        if (scrollable <= 1) {
            return false;
        }
        double previous = scrollValue(scrollPane, horizontal);
        double next = clamp(previous + pixels / scrollable, scrollMin(scrollPane, horizontal), scrollMax(scrollPane, horizontal));
        if (Math.abs(next - previous) < 0.0001) {
            return false;
        }
        setScrollValue(scrollPane, horizontal, next);
        return true;
    }

    private static boolean eventTargetInsideStyleClass(ScrollEvent event, String styleClass) {
        if (!(event.getTarget() instanceof Node node)) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current.getStyleClass().contains(styleClass)) {
                return true;
            }
        }
        return false;
    }

    private static boolean eventTargetInsideNestedScrollPane(ScrollEvent event, ScrollPane outerScrollPane) {
        if (!(event.getTarget() instanceof Node node)) {
            return false;
        }
        for (Node current = node; current != null && current != outerScrollPane; current = current.getParent()) {
            if (current instanceof ScrollPane nestedScrollPane
                    && nestedScrollPane != outerScrollPane
                    && nestedScrollPane.getContent() != null
                    && nestedScrollPaneCanHandle(nestedScrollPane, event)) {
                return true;
            }
        }
        return false;
    }

    private static boolean nestedScrollPaneCanHandle(ScrollPane scrollPane, ScrollEvent event) {
        double verticalPixels = scrollPixels(event, false, viewportSize(scrollPane, false));
        if (Math.abs(verticalPixels) >= 0.5 && canScrollByPixels(scrollPane, false, -verticalPixels)) {
            return true;
        }
        double horizontalPixels = scrollPixels(event, true, viewportSize(scrollPane, true));
        return Math.abs(horizontalPixels) >= 0.5 && canScrollByPixels(scrollPane, true, -horizontalPixels);
    }

    private static double scrollablePixels(ScrollPane scrollPane, boolean horizontal) {
        return horizontal
                ? scrollPane.getContent().getLayoutBounds().getWidth() - scrollPane.getViewportBounds().getWidth()
                : scrollPane.getContent().getLayoutBounds().getHeight() - scrollPane.getViewportBounds().getHeight();
    }

    private static double viewportSize(ScrollPane scrollPane, boolean horizontal) {
        return horizontal ? scrollPane.getViewportBounds().getWidth() : scrollPane.getViewportBounds().getHeight();
    }

    private static double scrollPixels(ScrollEvent event, boolean horizontal, double viewportSize) {
        if (horizontal && Math.abs(event.getDeltaX()) > 0.5) {
            return event.getDeltaX();
        }
        return switch (event.getTextDeltaYUnits()) {
            case LINES -> event.getTextDeltaY() * LINE_PIXELS;
            case PAGES -> event.getTextDeltaY() * Math.max(120, viewportSize * 0.86);
            case NONE -> event.getDeltaY();
        };
    }

    private static ScrollInputKind inputKind(ScrollEvent event, boolean horizontal) {
        if (event.isInertia() || (horizontal && Math.abs(event.getDeltaX()) > 0.5)) {
            return ScrollInputKind.PIXEL;
        }
        return event.getTextDeltaYUnits() == ScrollEvent.VerticalTextScrollUnits.NONE
                ? ScrollInputKind.PIXEL
                : ScrollInputKind.WHEEL;
    }

    private static double nextVelocity(double currentVelocity, double inputVelocity) {
        double carry = Math.signum(currentVelocity) != 0
                && Math.signum(inputVelocity) != 0
                && Math.signum(currentVelocity) != Math.signum(inputVelocity)
                ? REVERSE_VELOCITY_CARRY
                : VELOCITY_CARRY;
        return clamp(currentVelocity * carry + inputVelocity, -MAX_VELOCITY, MAX_VELOCITY);
    }

    public static double smoothingFactor(double deltaSeconds) {
        if (deltaSeconds <= 0) {
            return 0;
        }
        return 1 - Math.exp(-deltaSeconds / WHEEL_SMOOTHING_SECONDS);
    }

    private static double scrollValue(ScrollPane scrollPane, boolean horizontal) {
        return horizontal ? scrollPane.getHvalue() : scrollPane.getVvalue();
    }

    private static void setScrollValue(ScrollPane scrollPane, boolean horizontal, double value) {
        if (horizontal) {
            scrollPane.setHvalue(value);
        } else {
            scrollPane.setVvalue(value);
        }
    }

    private static double scrollMin(ScrollPane scrollPane, boolean horizontal) {
        return horizontal ? scrollPane.getHmin() : scrollPane.getVmin();
    }

    private static double scrollMax(ScrollPane scrollPane, boolean horizontal) {
        return horizontal ? scrollPane.getHmax() : scrollPane.getVmax();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
