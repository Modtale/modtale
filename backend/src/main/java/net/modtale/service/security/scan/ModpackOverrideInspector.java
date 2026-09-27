package net.modtale.service.security.scan;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.modtale.model.project.ModpackConfigReference;
import net.modtale.model.project.ProjectDependency;
import net.modtale.service.storage.ModpackOverrideArchive;
import net.modtale.service.storage.StorageService;

/** Bounded observation of a stored override archive and its saved config associations, with no approval authority. */
public final class ModpackOverrideInspector {
    public enum State { MATCHED, HASH_MISMATCH, INVALID_ARCHIVE, CONFIG_MISMATCH, OWNER_MISMATCH,
        UNAVAILABLE, BYTE_LIMIT, TIME_LIMIT, BUSY }
    public record File(String path, String sha256, int bytes, String source, String projectId) {}
    public record Result(State state, String observedArchiveSha256, long archiveBytes, List<File> files) {
        public Result { files = List.copyOf(files); }
    }
    @FunctionalInterface interface Open { InputStream open(String reference) throws IOException; }
    private static final Semaphore CAPACITY = new Semaphore(2);
    private static final long MAX_ARCHIVE_BYTES = 100L * 1024 * 1024;
    private final Open open;
    private final Semaphore capacity;

    public ModpackOverrideInspector(StorageService storage) { this(storage::getStream, CAPACITY); }
    ModpackOverrideInspector(Open open, Semaphore capacity) {
        this.open = Objects.requireNonNull(open);
        this.capacity = Objects.requireNonNull(capacity);
    }

    public Result inspect(String reference, String expectedSha256, List<ModpackConfigReference> savedConfigs,
            List<ProjectDependency> dependencies) {
        return inspect(reference, expectedSha256, savedConfigs, dependencies, Duration.ofSeconds(15));
    }
    Result inspect(String reference, String expectedSha256, List<ModpackConfigReference> savedConfigs,
            List<ProjectDependency> dependencies, Duration timeout) {
        if(reference == null || reference.isBlank() || expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid override archive identity");
        long nanos = timeout.toNanos();
        if(nanos < 1 || nanos > TimeUnit.SECONDS.toNanos(15)) throw new IllegalArgumentException("Invalid inspection deadline");
        var configs = savedConfigs == null ? List.<ModpackConfigReference>of() : List.copyOf(savedConfigs);
        var owners = dependencies == null ? List.<ProjectDependency>of() : List.copyOf(dependencies);
        if(!capacity.tryAcquire()) return incomplete(State.BUSY, 0);
        long started = System.nanoTime();
        var future = new CompletableFuture<Result>();
        Thread worker;
        try {
            worker = Thread.ofVirtual().name("modpack-override-inspection").start(() -> {
                try { future.complete(read(reference, expectedSha256, configs, owners, started, nanos)); }
                catch(Exception failure) { future.complete(incomplete(State.UNAVAILABLE, 0)); }
                finally { capacity.release(); }
            });
        } catch(RuntimeException failure) { capacity.release(); throw failure; }
        try {
            var result = future.get(Math.max(1, nanos - (System.nanoTime() - started)), TimeUnit.NANOSECONDS);
            return expired(started, nanos) ? incomplete(State.TIME_LIMIT, 0) : result;
        } catch(TimeoutException failure) {
            worker.interrupt(); return incomplete(State.TIME_LIMIT, 0);
        } catch(InterruptedException failure) {
            worker.interrupt(); Thread.currentThread().interrupt(); return incomplete(State.TIME_LIMIT, 0);
        } catch(ExecutionException failure) {
            return incomplete(State.UNAVAILABLE, 0);
        }
    }

    private Result read(String reference, String expected, List<ModpackConfigReference> saved,
            List<ProjectDependency> dependencies, long started, long nanos) {
        InputStream stream = null;
        var counted = new BoundedInputStream(started, nanos);
        try {
            stream = open.open(reference);
            counted.source = stream;
            var digest = MessageDigest.getInstance("SHA-256");
            var hashing = new DigestInputStream(counted, digest);
            // The ZIP parser closes its input. Keep the digest stream open so trailing ZIP bytes are also hashed.
            var bundle = ModpackOverrideArchive.readBundle(new FilterInputStream(hashing) {
                @Override public void close() {}
            });
            hashing.transferTo(OutputStream.nullOutputStream());
            String observed = HexFormat.of().formatHex(digest.digest());
            if(!expected.equals(observed)) return new Result(State.HASH_MISMATCH, observed, counted.bytes, List.of());
            if(!bundle.configs().equals(saved)) return new Result(State.CONFIG_MISMATCH, observed, counted.bytes, List.of());
            try { ModpackOverrideArchive.validateOwners(bundle.configs(), dependencies); }
            catch(IOException invalidOwner) { return new Result(State.OWNER_MISMATCH, observed, counted.bytes, List.of()); }
            var files = new ArrayList<File>();
            for(var file : bundle.files()) {
                var config = bundle.configs().stream().filter(candidate -> candidate.path().equals(file.path())).findFirst()
                        .orElseThrow();
                files.add(new File(file.path(), config.sha256(), file.bytes().length, config.source(), config.projectId()));
            }
            return new Result(State.MATCHED, observed, counted.bytes, files);
        } catch(ByteLimit failure) { return incomplete(State.BYTE_LIMIT, counted.bytes); }
        catch(TimeLimit failure) { return incomplete(State.TIME_LIMIT, counted.bytes); }
        catch(StorageFailure failure) { return incomplete(State.UNAVAILABLE, counted.bytes); }
        catch(IOException failure) { return incomplete(stream == null ? State.UNAVAILABLE : State.INVALID_ARCHIVE, counted.bytes); }
        catch(Exception failure) { return incomplete(State.UNAVAILABLE, counted.bytes); }
        finally {
            if(stream != null) try {
                if(stream instanceof software.amazon.awssdk.core.ResponseInputStream<?> response) response.abort();
                else stream.close();
            } catch(IOException ignored) { /* The observation is already bounded or failed. */ }
        }
    }
    private static Result incomplete(State state, long bytes) { return new Result(state, null, bytes, List.of()); }
    private static boolean expired(long started, long nanos) {
        return Thread.currentThread().isInterrupted() || System.nanoTime() - started >= nanos;
    }
    private static final class ByteLimit extends IOException {}
    private static final class TimeLimit extends IOException {}
    private static final class StorageFailure extends IOException {
        private StorageFailure(IOException cause) { super(cause); }
    }
    private static final class BoundedInputStream extends InputStream {
        private final long started, nanos;
        private InputStream source;
        private long bytes;
        private BoundedInputStream(long started, long nanos) { this.started = started; this.nanos = nanos; }
        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            if(length == 0) return 0;
            if(expired(started, nanos)) throw new TimeLimit();
            int allowed = (int) Math.min(length, MAX_ARCHIVE_BYTES - bytes + 1);
            int read;
            try { read = source.read(buffer, offset, allowed); }
            catch(IOException failure) { throw new StorageFailure(failure); }
            if(expired(started, nanos)) throw new TimeLimit();
            if(read == 0) throw new IOException("Stored override stream made no progress");
            if(read > 0 && (bytes += read) > MAX_ARCHIVE_BYTES) throw new ByteLimit();
            return read;
        }
    }
}
