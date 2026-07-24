package net.modtale.launcher.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;

public final class LauncherLogging {

    private static final Logger LOG = LogManager.getLogger(LauncherLogging.class);
    private static final Duration MAX_ACTIVE_LOG_AGE = Duration.ofHours(24);
    private static final Path LATEST_LOG_PATH = Path.of(
            System.getProperty("user.home", "."),
            ".modtale",
            "launcher",
            "logs",
            "latest.log"
    );
    private static final Object LOCK = new Object();

    private static ScheduledExecutorService rolloverExecutor;
    private static Instant activeLogStartedAt;
    private static boolean systemErrorMirrorInstalled;

    private LauncherLogging() {
    }

    public static Path latestLogPath() {
        return LATEST_LOG_PATH;
    }

    public static void initialize() {
        synchronized (LOCK) {
            if (activeLogStartedAt == null) {
                activeLogStartedAt = Instant.now();
                installGlobalExceptionHandler();
                installSystemErrorMirror();
                startRolloverScheduler();
            }
        }
        LOG.info("Logging to {}", LATEST_LOG_PATH.toAbsolutePath());
    }

    private static void installGlobalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                LOG.error("Unhandled exception on thread {}", thread.getName(), throwable));
    }

    private static void startRolloverScheduler() {
        if (rolloverExecutor != null) {
            return;
        }
        rolloverExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "modtale-log-rollover");
            thread.setDaemon(true);
            return thread;
        });
        rolloverExecutor.scheduleAtFixedRate(LauncherLogging::rollIfExpired, 1, 1, TimeUnit.MINUTES);
    }

    private static void rollIfExpired() {
        synchronized (LOCK) {
            Instant startedAt = activeLogStartedAt;
            if (startedAt == null || Duration.between(startedAt, Instant.now()).compareTo(MAX_ACTIVE_LOG_AGE) < 0) {
                return;
            }
            if (Files.notExists(LATEST_LOG_PATH)) {
                activeLogStartedAt = Instant.now();
                return;
            }
            if (rollActiveLog()) {
                activeLogStartedAt = Instant.now();
                LOG.info("Started a new latest.log after the previous log was active for 24 hours.");
            }
        }
    }

    private static boolean rollActiveLog() {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            RollingFileAppender appender = context.getConfiguration().getAppender("LatestFile");
            if (appender == null) {
                return false;
            }
            appender.getManager().rollover();
            return true;
        } catch (RuntimeException ex) {
            LOG.warn("Could not rotate launcher log.", ex);
            return false;
        }
    }

    private static void installSystemErrorMirror() {
        if (systemErrorMirrorInstalled) {
            return;
        }
        PrintStream originalError = System.err;
        System.setErr(new PrintStream(
                new ErrorMirrorOutputStream(originalError),
                true,
                StandardCharsets.UTF_8
        ));
        systemErrorMirrorInstalled = true;
    }

    private static final class ErrorMirrorOutputStream extends OutputStream {

        private final PrintStream delegate;
        private final java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream();

        private ErrorMirrorOutputStream(PrintStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void write(int value) throws IOException {
            delegate.write(value);
            if (value == '\n') {
                flushLine();
                return;
            }
            if (value != '\r') {
                line.write(value);
            }
        }

        @Override
        public synchronized void write(byte[] buffer, int offset, int length) throws IOException {
            delegate.write(buffer, offset, length);
            for (int index = offset; index < offset + length; index++) {
                int value = buffer[index] & 0xff;
                if (value == '\n') {
                    flushLine();
                } else if (value != '\r') {
                    line.write(value);
                }
            }
        }

        @Override
        public synchronized void flush() throws IOException {
            delegate.flush();
            flushLine();
        }

        private void flushLine() {
            if (line.size() == 0) {
                return;
            }
            String message = line.toString(Charset.defaultCharset());
            line.reset();
            if (!message.isBlank()) {
                LOG.error("System.err: {}", message);
            }
        }
    }
}
