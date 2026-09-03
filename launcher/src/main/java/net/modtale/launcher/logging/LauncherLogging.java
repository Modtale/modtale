package net.modtale.launcher.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public final class LauncherLogging {

    private static final LauncherLogger LOG = LauncherLog.getLogger(LauncherLogging.class);
    private static final Duration MAX_ACTIVE_LOG_AGE = Duration.ofHours(24);
    private static final long MAX_ACTIVE_LOG_BYTES = 5L * 1024 * 1024;
    private static final Path LATEST_LOG_PATH = Path.of(
            System.getProperty("user.home", "."), ".modtale", "launcher", "logs", "latest.log");
    private static final Object LOCK = new Object();

    private static RollingLogHandler handler;
    private static boolean systemErrorMirrorInstalled;

    private LauncherLogging() {
    }

    public static Path latestLogPath() {
        return LATEST_LOG_PATH;
    }

    public static void initialize() {
        synchronized (LOCK) {
            if (handler == null) {
                configureLogging();
                installGlobalExceptionHandler();
                installSystemErrorMirror();
            }
        }
        LOG.info("Logging to {}", LATEST_LOG_PATH.toAbsolutePath());
    }

    private static void configureLogging() {
        try {
            Files.createDirectories(LATEST_LOG_PATH.getParent());
            archiveExistingLog();
            handler = new RollingLogHandler(LATEST_LOG_PATH);
            Logger root = LogManager.getLogManager().getLogger("");
            for (Handler existing : root.getHandlers()) root.removeHandler(existing);
            root.setLevel(Level.INFO);
            root.addHandler(handler);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not initialize launcher logging", ex);
        }
    }

    private static void installGlobalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                LOG.error("Unhandled exception on thread {}", thread.getName(), throwable));
    }

    private static void archiveExistingLog() throws IOException {
        if (Files.notExists(LATEST_LOG_PATH) || Files.size(LATEST_LOG_PATH) == 0) return;
        Path archived = nextArchivePath();
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(archived,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            Files.copy(LATEST_LOG_PATH, output);
        }
        Files.deleteIfExists(LATEST_LOG_PATH);
    }

    private static Path nextArchivePath() {
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                .withZone(ZoneId.systemDefault()).format(Instant.now());
        for (int index = 1; ; index++) {
            Path candidate = LATEST_LOG_PATH.resolveSibling(timestamp + "-" + index + ".log.gz");
            Path uncompressed = candidate.resolveSibling(candidate.getFileName().toString().replaceFirst("\\.gz$", ""));
            if (Files.notExists(candidate) && Files.notExists(uncompressed)) return candidate;
        }
    }

    private static void installSystemErrorMirror() {
        if (systemErrorMirrorInstalled) return;
        PrintStream originalError = System.err;
        System.setErr(new PrintStream(new ErrorMirrorOutputStream(originalError), true, StandardCharsets.UTF_8));
        systemErrorMirrorInstalled = true;
    }

    private static final class RollingLogHandler extends Handler {

        private final Path path;
        private final Formatter formatter = new LauncherLogFormatter();
        private BufferedWriter writer;
        private Instant startedAt;
        private long bytesWritten;

        private RollingLogHandler(Path path) throws IOException {
            this.path = path;
            open();
            setLevel(Level.INFO);
        }

        @Override
        public synchronized void publish(LogRecord record) {
            if (!isLoggable(record)) return;
            try {
                String rendered = formatter.format(record);
                byte[] bytes = rendered.getBytes(StandardCharsets.UTF_8);
                if (bytesWritten + bytes.length > MAX_ACTIVE_LOG_BYTES
                        || Duration.between(startedAt, Instant.now()).compareTo(MAX_ACTIVE_LOG_AGE) >= 0) roll();
                writer.write(rendered);
                writer.flush();
                bytesWritten += bytes.length;
            } catch (IOException ex) {
                reportError("Could not write launcher log", ex, java.util.logging.ErrorManager.WRITE_FAILURE);
            }
        }

        private void open() throws IOException {
            writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            bytesWritten = Files.size(path);
            startedAt = Instant.now();
        }

        private void roll() throws IOException {
            writer.close();
            Path compressed = nextArchivePath();
            Path plainArchive = compressed.resolveSibling(compressed.getFileName().toString().replaceFirst("\\.gz$", ""));
            Files.move(path, plainArchive, StandardCopyOption.REPLACE_EXISTING);
            open();
            Thread.ofVirtual().name("modtale-log-compress").start(() -> compressArchive(plainArchive, compressed));
        }

        private void compressArchive(Path plainArchive, Path compressed) {
            try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(compressed,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
                Files.copy(plainArchive, output);
                Files.deleteIfExists(plainArchive);
            } catch (IOException ex) {
                try {
                    Files.deleteIfExists(compressed);
                } catch (IOException ignored) {
                }
                reportError("Could not compress launcher log archive", ex,
                        java.util.logging.ErrorManager.GENERIC_FAILURE);
            }
        }

        @Override
        public synchronized void flush() {
            try {
                if (writer != null) writer.flush();
            } catch (IOException ignored) {
            }
        }

        @Override
        public synchronized void close() throws SecurityException {
            try {
                if (writer != null) writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
    }

    private static final class LauncherLogFormatter extends Formatter {

        private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneId.systemDefault());

        @Override
        public String format(LogRecord record) {
            StringBuilder line = new StringBuilder(192)
                    .append(TIMESTAMP.format(record.getInstant())).append(' ')
                    .append(String.format("%-7s", record.getLevel().getName())).append(" [")
                    .append(Thread.currentThread().getName()).append("] ")
                    .append(record.getLoggerName()).append(" - ")
                    .append(formatMessage(record)).append(System.lineSeparator());
            if (record.getThrown() != null) {
                java.io.StringWriter trace = new java.io.StringWriter();
                record.getThrown().printStackTrace(new java.io.PrintWriter(trace));
                line.append(trace);
            }
            return line.toString();
        }
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
            if (value == '\n') flushLine();
            else if (value != '\r') line.write(value);
        }

        @Override
        public synchronized void write(byte[] buffer, int offset, int length) throws IOException {
            delegate.write(buffer, offset, length);
            for (int index = offset; index < offset + length; index++) {
                int value = buffer[index] & 0xff;
                if (value == '\n') flushLine();
                else if (value != '\r') line.write(value);
            }
        }

        @Override
        public synchronized void flush() throws IOException {
            delegate.flush();
            flushLine();
        }

        private void flushLine() {
            if (line.size() == 0) return;
            String message = line.toString(Charset.defaultCharset());
            line.reset();
            if (!message.isBlank()) LOG.error("System.err: {}", message);
        }
    }
}
