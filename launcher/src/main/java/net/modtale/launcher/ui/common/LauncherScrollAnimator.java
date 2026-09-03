package net.modtale.launcher.ui.common;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import javafx.animation.AnimationTimer;
import javafx.geometry.Bounds;
import javafx.scene.control.ScrollPane;

/**
 * Mouse-wheel scroll animation matching Chromium's inverse-delta scroll curve.
 * Precise touchpad input never enters this animator; the platform handles it directly.
 */
final class LauncherScrollAnimator {

    static final double MIN_DURATION_SECONDS = 0.100;
    static final double MAX_DURATION_SECONDS = 0.200;
    private static final double RAMP_START_PIXELS = 120;
    private static final double RAMP_END_PIXELS = 480;
    private static final double EPSILON = 0.01;
    private static final double FIRST_CONTROL_X = 0.42;
    private static final double SECOND_CONTROL_X = 0.58;
    private static final double PROGRAMMATIC_FIRST_CONTROL_X = 0.4;
    private static final double PROGRAMMATIC_SECOND_CONTROL_X = 0;
    private static final double PROGRAMMATIC_MAX_DURATION_SECONDS = 1.5;

    private final Map<ScrollPane, AnimationState> states = new WeakHashMap<>();
    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            tick(now);
        }
    };
    private boolean timerRunning;

    void animate(ScrollPane pane, double deltaX, double deltaY, long now) {
        ScrollMetrics metrics = metrics(pane);
        animate(pane, metrics, deltaX, deltaY, now);
    }

    void animate(ScrollPane pane, ScrollMetrics metrics, double deltaX, double deltaY, long now) {
        if (!metrics.scrollable()) return;

        AnimationState state = states.get(pane);
        Point actual = new Point(horizontalOffset(pane, metrics), verticalOffset(pane, metrics));
        if (state == null || state.finished(now) || state.divergedFrom(actual)) {
            state = AnimationState.stopped(actual);
        } else {
            state = state.at(now);
        }

        Point target = new Point(
                clamp(state.target.x + deltaX, 0, metrics.maxX),
                clamp(state.target.y + deltaY, 0, metrics.maxY)
        );
        if (target.distanceMaximum(state.current) < EPSILON) {
            states.remove(pane);
            stopTimerIfIdle();
            return;
        }

        AnimationState retargeted = state.retarget(target, now);
        states.put(pane, retargeted);
        apply(pane, metrics, retargeted.current);
        startTimer();
    }

    void cancel(ScrollPane pane) {
        states.remove(pane);
        stopTimerIfIdle();
    }

    void animateTo(ScrollPane pane, double horizontalValue, double verticalValue, long now) {
        ScrollMetrics metrics = metrics(pane);
        if (!metrics.scrollable()) return;
        Point initial = new Point(horizontalOffset(pane, metrics), verticalOffset(pane, metrics));
        Point target = new Point(
                Double.isFinite(horizontalValue)
                        ? pixelOffset(horizontalValue, pane.getHmin(), pane.getHmax(), metrics.maxX)
                        : initial.x,
                Double.isFinite(verticalValue)
                        ? pixelOffset(verticalValue, pane.getVmin(), pane.getVmax(), metrics.maxY)
                        : initial.y
        );
        double delta = target.distanceMaximum(initial);
        if (delta < EPSILON) {
            apply(pane, metrics, target);
            states.remove(pane);
            stopTimerIfIdle();
            return;
        }
        double duration = programmaticDuration(delta);
        states.put(pane, AnimationState.programmatic(initial, target, now, duration));
        startTimer();
    }

    double desiredHorizontalOffset(ScrollPane pane, ScrollMetrics metrics) {
        AnimationState state = states.get(pane);
        return state == null ? horizontalOffset(pane, metrics) : state.target.x;
    }

    double desiredVerticalOffset(ScrollPane pane, ScrollMetrics metrics) {
        AnimationState state = states.get(pane);
        return state == null ? verticalOffset(pane, metrics) : state.target.y;
    }

    private void tick(long now) {
        Iterator<Map.Entry<ScrollPane, AnimationState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ScrollPane, AnimationState> entry = iterator.next();
            ScrollPane pane = entry.getKey();
            AnimationState state = entry.getValue().at(now);
            ScrollMetrics metrics = metrics(pane);
            apply(pane, metrics, state.current);
            if (state.finished(now) || !metrics.scrollable()) {
                iterator.remove();
            } else {
                entry.setValue(state);
            }
        }
        stopTimerIfIdle();
    }

    private void startTimer() {
        if (timerRunning) return;
        timerRunning = true;
        timer.start();
    }

    private void stopTimerIfIdle() {
        if (!timerRunning || !states.isEmpty()) return;
        timer.stop();
        timerRunning = false;
    }

    private static void apply(ScrollPane pane, ScrollMetrics metrics, Point value) {
        if (metrics.maxX > EPSILON) {
            pane.setHvalue(normalized(value.x, metrics.maxX, pane.getHmin(), pane.getHmax()));
        }
        if (metrics.maxY > EPSILON) {
            pane.setVvalue(normalized(value.y, metrics.maxY, pane.getVmin(), pane.getVmax()));
        }
    }

    private static double normalized(double pixels, double maximum, double minimumValue, double maximumValue) {
        if (maximum <= EPSILON || maximumValue <= minimumValue) return minimumValue;
        return minimumValue + clamp(pixels / maximum, 0, 1) * (maximumValue - minimumValue);
    }

    private static double horizontalOffset(ScrollPane pane, ScrollMetrics metrics) {
        return pixelOffset(pane.getHvalue(), pane.getHmin(), pane.getHmax(), metrics.maxX);
    }

    private static double verticalOffset(ScrollPane pane, ScrollMetrics metrics) {
        return pixelOffset(pane.getVvalue(), pane.getVmin(), pane.getVmax(), metrics.maxY);
    }

    private static double pixelOffset(double value, double minimumValue, double maximumValue, double maximumPixels) {
        double range = maximumValue - minimumValue;
        if (range <= EPSILON || maximumPixels <= EPSILON) return 0;
        return clamp((value - minimumValue) / range, 0, 1) * maximumPixels;
    }

    static ScrollMetrics metrics(ScrollPane pane) {
        if (pane == null || pane.getContent() == null) return new ScrollMetrics(0, 0);
        Bounds content = pane.getContent().getLayoutBounds();
        Bounds viewport = pane.getViewportBounds();
        return new ScrollMetrics(
                Math.max(0, content.getWidth() - viewport.getWidth()),
                Math.max(0, content.getHeight() - viewport.getHeight())
        );
    }

    static double inverseDeltaDuration(double pixels) {
        double slope = (MIN_DURATION_SECONDS - MAX_DURATION_SECONDS)
                / (RAMP_END_PIXELS - RAMP_START_PIXELS);
        double duration = MAX_DURATION_SECONDS + (Math.abs(pixels) - RAMP_START_PIXELS) * slope;
        return clamp(duration, MIN_DURATION_SECONDS, MAX_DURATION_SECONDS);
    }

    static double programmaticDuration(double pixels) {
        return Math.min(Math.sqrt(Math.abs(pixels)) / 60.0, PROGRAMMATIC_MAX_DURATION_SECONDS);
    }

    static double easeInOut(double progress) {
        return cubicBezier(progress, FIRST_CONTROL_X, 0, SECOND_CONTROL_X, 1).value;
    }

    static double wheelTraceOffset(
            double firstDelta,
            double retargetAtMillis,
            double secondDelta,
            double sampleAtMillis
    ) {
        Point origin = new Point(0, 0);
        AnimationState first = AnimationState.stopped(origin)
                .retarget(new Point(0, firstDelta), 0);
        long retargetNanos = Math.round(retargetAtMillis * 1_000_000.0);
        AnimationState atRetarget = first.at(retargetNanos);
        AnimationState second = atRetarget.retarget(
                new Point(0, atRetarget.target.y + secondDelta), retargetNanos);
        return second.at(Math.round(sampleAtMillis * 1_000_000.0)).current.y;
    }

    private static CurveSample cubicBezier(double x, double x1, double y1, double x2, double y2) {
        double target = clamp(x, 0, 1);
        double parameter = target;
        for (int iteration = 0; iteration < 8; iteration++) {
            double error = bezier(parameter, x1, x2) - target;
            double derivative = bezierDerivative(parameter, x1, x2);
            if (Math.abs(error) < 1e-7 || Math.abs(derivative) < 1e-7) break;
            parameter = clamp(parameter - error / derivative, 0, 1);
        }
        double dx = bezierDerivative(parameter, x1, x2);
        double dy = bezierDerivative(parameter, y1, y2);
        return new CurveSample(bezier(parameter, y1, y2), Math.abs(dx) < 1e-7 ? 0 : dy / dx);
    }

    private static double bezier(double t, double first, double second) {
        double inverse = 1 - t;
        return 3 * inverse * inverse * t * first + 3 * inverse * t * t * second + t * t * t;
    }

    private static double bezierDerivative(double t, double first, double second) {
        double inverse = 1 - t;
        return 3 * inverse * inverse * first
                + 6 * inverse * t * (second - first)
                + 3 * t * t * (1 - second);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    record ScrollMetrics(double maxX, double maxY) {
        boolean scrollable() {
            return maxX > EPSILON || maxY > EPSILON;
        }
    }

    private record Point(double x, double y) {
        Point interpolate(Point other, double progress) {
            return new Point(x + (other.x - x) * progress, y + (other.y - y) * progress);
        }

        double distanceMaximum(Point other) {
            return Math.max(Math.abs(x - other.x), Math.abs(y - other.y));
        }
    }

    private record CurveSample(double value, double slope) {
    }

    private record AnimationState(
            Point initial,
            Point current,
            Point target,
            long startNanos,
            long durationNanos,
            double firstControlX,
            double firstControlY,
            double secondControlX,
            double secondControlY,
            double velocityX,
            double velocityY
    ) {
        static AnimationState stopped(Point point) {
            return new AnimationState(point, point, point, 0, 0,
                    FIRST_CONTROL_X, 0, SECOND_CONTROL_X, 1, 0, 0);
        }

        static AnimationState programmatic(Point initial, Point target, long now, double duration) {
            return new AnimationState(initial, initial, target, now,
                    Math.round(duration * 1_000_000_000.0),
                    PROGRAMMATIC_FIRST_CONTROL_X, 0, PROGRAMMATIC_SECOND_CONTROL_X, 1, 0, 0);
        }

        AnimationState at(long now) {
            if (durationNanos <= 0 || now >= startNanos + durationNanos) {
                return stopped(target);
            }
            double elapsed = clamp((double) (now - startNanos) / durationNanos, 0, 1);
            CurveSample curve = cubicBezier(
                    elapsed,
                    firstControlX,
                    firstControlY,
                    secondControlX,
                    secondControlY
            );
            Point value = initial.interpolate(target, curve.value);
            double seconds = durationNanos / 1_000_000_000.0;
            double nextVelocityX = curve.slope * (target.x - initial.x) / seconds;
            double nextVelocityY = curve.slope * (target.y - initial.y) / seconds;
            return new AnimationState(initial, value, target, startNanos, durationNanos,
                    firstControlX, firstControlY, secondControlX, secondControlY,
                    nextVelocityX, nextVelocityY);
        }

        AnimationState retarget(Point newTarget, long now) {
            Point delta = new Point(newTarget.x - current.x, newTarget.y - current.y);
            double maximumDelta = Math.abs(delta.x) >= Math.abs(delta.y) ? delta.x : delta.y;
            double duration = inverseDeltaDuration(maximumDelta);
            double velocity = Math.abs(delta.x) >= Math.abs(delta.y) ? velocityX : velocityY;
            if (Math.abs(velocity) >= EPSILON && maximumDelta / velocity > 0) {
                duration = Math.min(duration, maximumDelta / velocity * 2.5);
            }
            duration = Math.max(0, duration);
            double slope = Math.abs(maximumDelta) < EPSILON ? 0 : velocity * duration / maximumDelta;
            return new AnimationState(current, current, newTarget, now,
                    Math.round(duration * 1_000_000_000.0),
                    FIRST_CONTROL_X,
                    FIRST_CONTROL_X * clamp(slope, -1000, 1000),
                    SECOND_CONTROL_X,
                    1,
                    velocityX,
                    velocityY);
        }

        boolean finished(long now) {
            return durationNanos <= 0 || now >= startNanos + durationNanos;
        }

        boolean divergedFrom(Point actual) {
            return actual.distanceMaximum(current) > 1;
        }
    }
}
