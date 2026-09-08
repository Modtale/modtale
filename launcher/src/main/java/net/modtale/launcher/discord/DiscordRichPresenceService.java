package net.modtale.launcher.discord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import net.modtale.launcher.settings.LauncherConfig;

public final class DiscordRichPresenceService {

    private static final int DISCORD_IPC_PIPE_COUNT = 10;
    private static final int OPCODE_HANDSHAKE = 0;
    private static final int OPCODE_FRAME = 1;
    private static final String OPEN_MODTALE_URL = "https://modtale.net";
    private static final String MODTALE_FAVICON_URL = "https://modtale.net/assets/favicon.svg";

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object connectionLock = new Object();
    private final String clientId;
    private final ExecutorService executor;
    private final long launcherStartedAt;

    private DiscordIpcConnection connection;

    public DiscordRichPresenceService(String clientId) {
        this.clientId = LauncherConfig.normalizeDiscordClientId(clientId).orElse(null);
        this.launcherStartedAt = Instant.now().getEpochSecond();
        this.executor = Executors.newSingleThreadExecutor(daemonThreadFactory());
    }

    public static DiscordRichPresenceService fromConfig() {
        return new DiscordRichPresenceService(LauncherConfig.discordClientId().orElse(null));
    }

    public boolean isEnabled() {
        return clientId != null;
    }

    public void start() {
        showLauncher();
    }

    public void showLauncher() {
        if (!isEnabled()) {
            return;
        }
        submit(() -> setActivity(launcherActivity(mapper, launcherStartedAt)));
    }

    public void showPlayingHytale(String buildLabel, long startedAtMillis) {
        if (!isEnabled()) {
            return;
        }
        long startedAt = Math.max(0, startedAtMillis / 1000);
        submit(() -> setActivity(hytaleActivity(mapper, buildLabel, startedAt)));
    }

    public void shutdown() {
        closed.set(true);
        executor.shutdownNow();
        synchronized (connectionLock) {
            closeConnection();
        }
    }

    private void submit(Runnable task) {
        if (closed.get()) {
            return;
        }
        try {
            executor.execute(() -> {
                if (!closed.get()) {
                    task.run();
                }
            });
        } catch (RejectedExecutionException ignored) {
            // The launcher is shutting down; Discord presence is best effort.
        }
    }

    private void setActivity(ObjectNode activity) {
        ObjectNode command = setActivityCommand(mapper, ProcessHandle.current().pid(), activity, UUID.randomUUID().toString());
        sendCommand(command);
    }

    private void sendCommand(ObjectNode command) {
        byte[] payload;
        try {
            payload = mapper.writeValueAsBytes(command);
        } catch (IOException ignored) {
            return;
        }

        synchronized (connectionLock) {
            for (int attempt = 0; attempt < 2 && !closed.get(); attempt++) {
                try {
                    DiscordIpcConnection activeConnection = ensureConnection();
                    activeConnection.write(frame(OPCODE_FRAME, payload));
                    return;
                } catch (IOException | UnsupportedOperationException ignored) {
                    closeConnection();
                }
            }
        }
    }

    private DiscordIpcConnection ensureConnection() throws IOException {
        if (connection != null) {
            return connection;
        }

        DiscordIpcConnection opened = DiscordIpcConnection.open();
        ObjectNode handshake = mapper.createObjectNode();
        handshake.put("v", 1);
        handshake.put("client_id", clientId);
        opened.write(frame(OPCODE_HANDSHAKE, mapper.writeValueAsBytes(handshake)));
        connection = opened;
        return opened;
    }

    private void closeConnection() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (IOException ignored) {
            // Discord may have closed the IPC pipe first.
        } finally {
            connection = null;
        }
    }

    static ObjectNode launcherActivity(ObjectMapper mapper, long startedAt) {
        ObjectNode activity = baseActivity(mapper, startedAt);
        activity.put("details", "Browsing Modtale");
        activity.put("state", "Managing Hytale mods");
        return activity;
    }

    static ObjectNode hytaleActivity(ObjectMapper mapper, String buildLabel, long startedAt) {
        ObjectNode activity = baseActivity(mapper, startedAt);
        activity.put("details", "Playing Hytale");
        String label = buildLabel == null || buildLabel.isBlank() || "Unset".equals(buildLabel)
                ? "Launched from Modtale"
                : buildLabel.trim();
        activity.put("state", label);
        return activity;
    }

    static ObjectNode setActivityCommand(ObjectMapper mapper, long pid, ObjectNode activity, String nonce) {
        ObjectNode command = mapper.createObjectNode();
        command.put("cmd", "SET_ACTIVITY");
        ObjectNode args = command.putObject("args");
        args.put("pid", pid);
        if (activity == null) {
            args.putNull("activity");
        } else {
            args.set("activity", activity);
        }
        command.put("nonce", nonce);
        return command;
    }

    static byte[] frame(int opcode, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(opcode);
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    private static ObjectNode baseActivity(ObjectMapper mapper, long startedAt) {
        ObjectNode activity = mapper.createObjectNode();
        ObjectNode timestamps = activity.putObject("timestamps");
        timestamps.put("start", startedAt);
        ObjectNode assets = activity.putObject("assets");
        assets.put("large_image", MODTALE_FAVICON_URL);
        assets.put("large_text", "Modtale");
        activity.putArray("buttons")
                .addObject()
                .put("label", "Open Modtale")
                .put("url", OPEN_MODTALE_URL);
        return activity;
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "modtale-discord-rpc");
            thread.setDaemon(true);
            return thread;
        };
    }

    private interface DiscordIpcConnection extends AutoCloseable {

        void write(byte[] bytes) throws IOException;

        @Override
        void close() throws IOException;

        static DiscordIpcConnection open() throws IOException {
            IOException lastError = null;
            for (String path : ipcPaths()) {
                try {
                    return isWindows() ? WindowsPipeConnection.open(path) : UnixSocketConnection.open(path);
                } catch (IOException ex) {
                    lastError = ex;
                }
            }
            throw lastError == null ? new IOException("Discord IPC pipe was not found.") : lastError;
        }

        private static List<String> ipcPaths() {
            Set<String> paths = new LinkedHashSet<>();
            for (int index = 0; index < DISCORD_IPC_PIPE_COUNT; index++) {
                if (isWindows()) {
                    paths.add("\\\\.\\pipe\\discord-ipc-" + index);
                } else {
                    for (Path root : unixIpcRoots()) {
                        paths.add(root.resolve("discord-ipc-" + index).toString());
                    }
                }
            }
            return List.copyOf(paths);
        }

        private static List<Path> unixIpcRoots() {
            Set<Path> roots = new LinkedHashSet<>();
            addRoot(roots, System.getenv("XDG_RUNTIME_DIR"));
            addRoot(roots, System.getProperty("java.io.tmpdir"));
            addRoot(roots, System.getenv("TMPDIR"));
            addRoot(roots, System.getenv("TEMP"));
            addRoot(roots, System.getenv("TMP"));
            String xdgRuntime = System.getenv("XDG_RUNTIME_DIR");
            if (xdgRuntime != null && !xdgRuntime.isBlank()) {
                Path runtimeRoot = Path.of(xdgRuntime);
                roots.add(runtimeRoot.resolve(Path.of("app", "com.discordapp.Discord")));
                roots.add(runtimeRoot.resolve("snap.discord"));
            }
            roots.add(Path.of("/tmp"));
            return List.copyOf(roots);
        }

        private static void addRoot(Set<Path> roots, String rawPath) {
            if (rawPath != null && !rawPath.isBlank()) {
                roots.add(Path.of(rawPath));
            }
        }

        private static boolean isWindows() {
            return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        }
    }

    private static final class UnixSocketConnection implements DiscordIpcConnection {

        private final SocketChannel channel;

        private UnixSocketConnection(SocketChannel channel) {
            this.channel = channel;
        }

        static UnixSocketConnection open(String path) throws IOException {
            SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            try {
                channel.connect(UnixDomainSocketAddress.of(Path.of(path)));
                return new UnixSocketConnection(channel);
            } catch (IOException | UnsupportedOperationException ex) {
                channel.close();
                throw ex;
            }
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    private static final class WindowsPipeConnection implements DiscordIpcConnection {

        private final RandomAccessFile pipe;

        private WindowsPipeConnection(RandomAccessFile pipe) {
            this.pipe = pipe;
        }

        static WindowsPipeConnection open(String path) throws IOException {
            return new WindowsPipeConnection(new RandomAccessFile(path, "rw"));
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            pipe.write(bytes);
        }

        @Override
        public void close() throws IOException {
            pipe.close();
        }
    }
}
