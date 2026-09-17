package net.modtale.launcher.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.modtale.launcher.cache.LauncherCachePaths;
import net.modtale.launcher.http.ResponseCache;
import net.modtale.launcher.io.AtomicJsonFile;

public final class ApiResponseCache {

    private static final Duration STALE_FALLBACK_TTL = Duration.ofDays(7);

    private final Path cacheDirectory;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ResponseCache<URI, String> memory;

    <T> T withRequestLock(URI uri, java.util.function.Supplier<T> request) {
        return memory.withRequestLock(uri, request);
    }

    public ApiResponseCache() {
        this(LauncherCachePaths.cacheDirectory("api"));
    }

    public ApiResponseCache(Path cacheDirectory) {
        this(cacheDirectory, Clock.systemUTC());
    }

    public ApiResponseCache(Path cacheDirectory, Clock clock) {
        this.clock = clock;
        this.memory = new ResponseCache<>(1024, clock);
        this.cacheDirectory = cacheDirectory;
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Optional<String> getFresh(URI uri, Duration ttl) {
        return get(uri, ttl);
    }

    public Optional<String> getStaleFallback(URI uri) {
        return get(uri, STALE_FALLBACK_TTL);
    }

    public void put(URI uri, String body) {
        if (body == null) {
            return;
        }
        CacheEntry entry = new CacheEntry(body, clock.instant());
        memory.put(uri, body, entry.writtenAt());
        if (cacheDirectory == null) return;
        try {
            AtomicJsonFile.write(cacheFile(uri), mapper.writer(), CachedBody.from(entry));
        } catch (IOException ignored) {
            // The in-memory cache is still useful if the disk cache cannot be written.
        }
    }

    void invalidate(URI uri) {
        memory.invalidate(uri);
        if (cacheDirectory == null) return;
        try {
            Files.deleteIfExists(cacheFile(uri));
        } catch (IOException ignored) {
            // Cache cleanup is best-effort.
        }
    }

    public void clear() {
        memory.clear();
        if (cacheDirectory == null || !Files.exists(cacheDirectory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(cacheDirectory)) {
            List<Path> paths = stream
                    .filter(path -> !cacheDirectory.equals(path))
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Cache cleanup is best-effort.
        }
    }

    private Optional<String> get(URI uri, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return Optional.empty();
        }

        Optional<String> cached = memory.get(uri, ttl);
        if (cached.isPresent() || memory.contains(uri)) return cached;
        readDisk(uri).ifPresent(entry -> memory.put(uri, entry.body(), entry.writtenAt()));
        return memory.get(uri, ttl);
    }

    private Optional<CacheEntry> readDisk(URI uri) {
        if (cacheDirectory == null) return Optional.empty();
        Path file = cacheFile(uri);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            CachedBody cached = mapper.readValue(file.toFile(), CachedBody.class);
            if (cached.body() == null || cached.writtenAtEpochMilli() <= 0) {
                invalidate(uri);
                return Optional.empty();
            }
            return Optional.of(new CacheEntry(cached.body(), Instant.ofEpochMilli(cached.writtenAtEpochMilli())));
        } catch (IOException ex) {
            invalidate(uri);
            return Optional.empty();
        }
    }

    private Path cacheFile(URI uri) {
        return cacheDirectory.resolve(sha256(uri.toString()) + ".json");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private record CacheEntry(String body, Instant writtenAt) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CachedBody(long writtenAtEpochMilli, String body) {

        static CachedBody from(CacheEntry entry) {
            return new CachedBody(entry.writtenAt().toEpochMilli(), entry.body());
        }
    }
}
