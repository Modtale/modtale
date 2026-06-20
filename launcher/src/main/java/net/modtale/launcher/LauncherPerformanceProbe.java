package net.modtale.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javafx.animation.AnimationTimer;
import javafx.scene.Scene;

public final class LauncherPerformanceProbe {

    public static final String PERF_LOG_PROPERTY = "modtale.launcher.perfLog";
    public static final String PERF_REFRESH_RATE_PROPERTY = "modtale.launcher.perfRefreshRate";

    private static final long REPORT_INTERVAL_NANOS = 1_000_000_000L;
    private static final double DEFAULT_REFRESH_RATE = 60.0;
    private static final ConcurrentMap<String, OperationStats> OPERATION_STATS = new ConcurrentHashMap<>();
    private static volatile boolean operationTimingEnabled;

    private LauncherPerformanceProbe() {
    }

    public static void install(Scene scene) {
        String outputPath = System.getProperty(PERF_LOG_PROPERTY);
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }

        operationTimingEnabled = true;
        FrameTimer timer = new FrameTimer(Path.of(outputPath), targetFrameMillis());
        scene.windowProperty().addListener((observable, oldWindow, newWindow) -> {
            if (newWindow == null) {
                timer.stop();
            } else {
                timer.start();
            }
        });
        if (scene.getWindow() != null) {
            timer.start();
        }
    }

    public static long operationStartNanos() {
        return operationTimingEnabled ? System.nanoTime() : 0;
    }

    public static void recordOperation(String name, long startNanos) {
        if (!operationTimingEnabled || startNanos == 0 || name == null || name.isBlank()) {
            return;
        }
        OPERATION_STATS.computeIfAbsent(name, ignored -> new OperationStats()).add(System.nanoTime() - startNanos);
    }

    private static double targetFrameMillis() {
        String configured = System.getProperty(PERF_REFRESH_RATE_PROPERTY,
                System.getProperty(LauncherRenderSettings.REFRESH_RATE_OVERRIDE_PROPERTY));
        if (configured != null && !configured.isBlank()) {
            try {
                double refreshRate = Double.parseDouble(configured.trim());
                if (Double.isFinite(refreshRate) && refreshRate > 0) {
                    return 1000.0 / refreshRate;
                }
            } catch (NumberFormatException ignored) {
                // Fall back to the default below.
            }
        }
        return 1000.0 / DEFAULT_REFRESH_RATE;
    }

    private static final class FrameTimer extends AnimationTimer {

        private final Path outputPath;
        private final double targetFrameMillis;
        private final List<Double> intervals = new ArrayList<>(256);
        private long previousFrame;
        private long windowStart;
        private boolean running;

        private FrameTimer(Path outputPath, double targetFrameMillis) {
            this.outputPath = outputPath;
            this.targetFrameMillis = targetFrameMillis;
            write("start targetFrameMillis=" + format(targetFrameMillis) + " at=" + Instant.now());
        }

        @Override
        public void start() {
            if (running) {
                return;
            }
            running = true;
            previousFrame = 0;
            windowStart = 0;
            super.start();
        }

        @Override
        public void stop() {
            if (!running) {
                return;
            }
            report("stop");
            running = false;
            previousFrame = 0;
            windowStart = 0;
            super.stop();
        }

        @Override
        public void handle(long now) {
            if (previousFrame != 0) {
                intervals.add((now - previousFrame) / 1_000_000.0);
            }
            previousFrame = now;
            if (windowStart == 0) {
                windowStart = now;
            }
            if (now - windowStart >= REPORT_INTERVAL_NANOS) {
                report("window");
                windowStart = now;
            }
        }

        private void report(String reason) {
            if (intervals.isEmpty()) {
                reportOperations(reason);
                return;
            }

            List<Double> sorted = new ArrayList<>(intervals);
            Collections.sort(sorted);
            double avg = intervals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double p95 = percentile(sorted, 0.95);
            double p99 = percentile(sorted, 0.99);
            double max = sorted.get(sorted.size() - 1);
            double missedFrameThreshold = targetFrameMillis * 1.35;
            long missed = intervals.stream().filter(value -> value > missedFrameThreshold).count();
            long overTwoFrames = intervals.stream().filter(value -> value > targetFrameMillis * 2.0).count();
            write("%s frames=%d avgMs=%s p95Ms=%s p99Ms=%s maxMs=%s missed=%d overTwoFrames=%d thresholdMs=%s at=%s"
                    .formatted(
                            reason,
                            intervals.size(),
                            format(avg),
                            format(p95),
                            format(p99),
                            format(max),
                            missed,
                            overTwoFrames,
                            format(missedFrameThreshold),
                            Instant.now()
                    ));
            intervals.clear();
            reportOperations(reason);
        }

        private void reportOperations(String reason) {
            List<OperationReport> reports = OPERATION_STATS.entrySet().stream()
                    .map(entry -> OperationReport.from(entry.getKey(), entry.getValue().drain()))
                    .filter(OperationReport::hasSamples)
                    .sorted(Comparator.comparing(OperationReport::name))
                    .toList();
            for (OperationReport report : reports) {
                write("operation %s name=%s samples=%d avgUs=%s p95Us=%s p99Us=%s maxUs=%s over1Ms=%d at=%s"
                        .formatted(
                                reason,
                                report.name(),
                                report.samples(),
                                format(report.avgMicros()),
                                format(report.p95Micros()),
                                format(report.p99Micros()),
                                format(report.maxMicros()),
                                report.overOneMillisecond(),
                                Instant.now()
                        ));
            }
        }

        private double percentile(List<Double> sorted, double percentile) {
            if (sorted.isEmpty()) {
                return 0;
            }
            int index = Math.max(0, Math.min(sorted.size() - 1,
                    (int) Math.ceil(sorted.size() * percentile) - 1));
            return sorted.get(index);
        }

        private void write(String line) {
            try {
                Path parent = outputPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(outputPath, line + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // Diagnostics must never affect launcher behavior.
            }
        }

        private static String format(double value) {
            return String.format(java.util.Locale.US, "%.3f", value);
        }
    }

    private static final class OperationStats {

        private final List<Long> samples = new ArrayList<>(1024);

        private synchronized void add(long nanos) {
            samples.add(Math.max(0, nanos));
        }

        private synchronized List<Long> drain() {
            if (samples.isEmpty()) {
                return List.of();
            }
            List<Long> drained = new ArrayList<>(samples);
            samples.clear();
            return drained;
        }
    }

    private record OperationReport(
            String name,
            int samples,
            double avgMicros,
            double p95Micros,
            double p99Micros,
            double maxMicros,
            long overOneMillisecond
    ) {

        private boolean hasSamples() {
            return samples > 0;
        }

        private static OperationReport from(String name, List<Long> samples) {
            if (samples.isEmpty()) {
                return new OperationReport(name, 0, 0, 0, 0, 0, 0);
            }
            List<Long> sorted = new ArrayList<>(samples);
            Collections.sort(sorted);
            double avgMicros = samples.stream().mapToDouble(value -> value / 1_000.0).average().orElse(0);
            double p95Micros = percentile(sorted, 0.95) / 1_000.0;
            double p99Micros = percentile(sorted, 0.99) / 1_000.0;
            double maxMicros = sorted.getLast() / 1_000.0;
            long overOneMillisecond = samples.stream().filter(value -> value > 1_000_000).count();
            return new OperationReport(name, samples.size(), avgMicros, p95Micros, p99Micros, maxMicros,
                    overOneMillisecond);
        }

        private static long percentile(List<Long> sorted, double percentile) {
            int index = Math.max(0, Math.min(sorted.size() - 1,
                    (int) Math.ceil(sorted.size() * percentile) - 1));
            return sorted.get(index);
        }
    }
}
