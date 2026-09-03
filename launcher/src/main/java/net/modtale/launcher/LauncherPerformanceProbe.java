package net.modtale.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.AnimationTimer;
import javafx.scene.Scene;

public final class LauncherPerformanceProbe {

    public static final String PERF_LOG_PROPERTY = "modtale.launcher.perfLog";
    public static final String PERF_REFRESH_RATE_PROPERTY = "modtale.launcher.perfRefreshRate";

    private static final long REPORT_INTERVAL_NANOS = 1_000_000_000L;
    private static final double DEFAULT_REFRESH_RATE = 60.0;
    private static final ConcurrentMap<String, OperationStats> OPERATION_STATS = new ConcurrentHashMap<>();
    private static final ExecutorService REPORTER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "launcher-performance-reporter");
        thread.setDaemon(true);
        return thread;
    });
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
        return targetFrameMillis(System.getProperties());
    }

    static double targetFrameMillis(Properties properties) {
        String configured = firstNonBlank(
                properties.getProperty(PERF_REFRESH_RATE_PROPERTY),
                properties.getProperty(LauncherRenderSettings.REFRESH_RATE_OVERRIDE_PROPERTY),
                properties.getProperty(LauncherRenderSettings.JAVAFX_PULSE_PROPERTY)
        );
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static final class FrameTimer extends AnimationTimer {

        private final Path outputPath;
        private final double targetPulseMillis;
        private long[] intervals = new long[512];
        private int intervalCount;
        private long previousFrame;
        private long windowStart;
        private boolean running;

        private FrameTimer(Path outputPath, double targetPulseMillis) {
            this.outputPath = outputPath;
            this.targetPulseMillis = targetPulseMillis;
            write("start targetPulseCadenceMs=" + format(targetPulseMillis)
                    + " note=interval-is-display-cadence-not-active-work at=" + Instant.now());
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
                if (intervalCount == intervals.length) intervals = Arrays.copyOf(intervals, intervals.length * 2);
                intervals[intervalCount++] = now - previousFrame;
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
            long[] sample = Arrays.copyOf(intervals, intervalCount);
            intervalCount = 0;
            REPORTER.execute(() -> reportOffThread(reason, sample));
        }

        private void reportOffThread(String reason, long[] sample) {
            if (sample.length == 0) {
                reportOperations(reason);
                return;
            }
            long[] sorted = sample.clone();
            Arrays.sort(sorted);
            double avg = Arrays.stream(sample).average().orElse(0) / 1_000_000.0;
            double p95 = percentile(sorted, 0.95) / 1_000_000.0;
            double p99 = percentile(sorted, 0.99) / 1_000_000.0;
            double max = sorted[sorted.length - 1] / 1_000_000.0;
            double latePulseThreshold = targetPulseMillis * 1.35;
            long latePulses = Arrays.stream(sample).filter(value -> value / 1_000_000.0 > latePulseThreshold).count();
            long overTwoPulses = Arrays.stream(sample).filter(value -> value / 1_000_000.0 > targetPulseMillis * 2.0).count();
            write("%s pulses=%d cadenceAvgMs=%s cadenceP95Ms=%s cadenceP99Ms=%s cadenceMaxMs=%s latePulses=%d overTwoPulses=%d thresholdMs=%s at=%s"
                    .formatted(
                            reason,
                            sample.length,
                            format(avg),
                            format(p95),
                            format(p99),
                            format(max),
                            latePulses,
                            overTwoPulses,
                            format(latePulseThreshold),
                            Instant.now()
                    ));
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

        private long percentile(long[] sorted, double percentile) {
            if (sorted.length == 0) {
                return 0;
            }
            int index = Math.max(0, Math.min(sorted.length - 1,
                    (int) Math.ceil(sorted.length * percentile) - 1));
            return sorted[index];
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

        private long[] samples = new long[1024];
        private int size;

        private synchronized void add(long nanos) {
            if (size == samples.length) samples = Arrays.copyOf(samples, samples.length * 2);
            samples[size++] = Math.max(0, nanos);
        }

        private synchronized long[] drain() {
            if (size == 0) return new long[0];
            long[] drained = Arrays.copyOf(samples, size);
            size = 0;
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

        private static OperationReport from(String name, long[] samples) {
            if (samples.length == 0) {
                return new OperationReport(name, 0, 0, 0, 0, 0, 0);
            }
            long[] sorted = samples.clone();
            Arrays.sort(sorted);
            double avgMicros = Arrays.stream(samples).average().orElse(0) / 1_000.0;
            double p95Micros = percentile(sorted, 0.95) / 1_000.0;
            double p99Micros = percentile(sorted, 0.99) / 1_000.0;
            double maxMicros = sorted[sorted.length - 1] / 1_000.0;
            long overOneMillisecond = Arrays.stream(samples).filter(value -> value > 1_000_000).count();
            return new OperationReport(name, samples.length, avgMicros, p95Micros, p99Micros, maxMicros,
                    overOneMillisecond);
        }

        private static long percentile(long[] sorted, double percentile) {
            int index = Math.max(0, Math.min(sorted.length - 1,
                    (int) Math.ceil(sorted.length * percentile) - 1));
            return sorted[index];
        }
    }
}
