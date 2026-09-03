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
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import net.modtale.launcher.cache.LauncherCachePaths;

final class ApiResponseCache {

    private static final Duration STALE_FALLBACK_TTL = Duration.ofDays(7);

    private final Path cacheDirectory;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, CacheEntry> memory = new ConcurrentHashMap<>();

    ApiResponseCache() {
        this(LauncherCachePaths.cacheDirectory("api"));
    }

    ApiResponseCache(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    Optional<String> getFresh(URI uri, Duration ttl) {
        return get(uri, ttl);
    }

    Optional<String> getStaleFallback(URI uri) {
        return get(uri, STALE_FALLBACK_TTL);
    }

    void put(URI uri, String body) {
        if (body == null) {
            return;
        }
        String key = uri.toString();
        CacheEntry entry = new CacheEntry(body, Instant.now());
        memory.put(key, entry);
        try {
            Files.createDirectories(cacheDirectory);
            mapper.writeValue(cacheFile(uri).toFile(), CachedBody.from(entry));
        } catch (IOException ignored) {
            // The in-memory cache is still useful if the disk cache cannot be written.
        }
    }

    void invalidate(URI uri) {
        memory.remove(uri.toString());
        try {
            Files.deleteIfExists(cacheFile(uri));
        } catch (IOException ignored) {
            // Cache cleanup is best-effort.
        }
    }

    void clear() {
        memory.clear();
        if (!Files.exists(cacheDirectory)) {
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

        String key = uri.toString();
        CacheEntry memoryEntry = memory.get(key);
        if (memoryEntry != null && memoryEntry.isFresh(ttl)) {
            return Optional.of(memoryEntry.body());
        }

        Optional<CacheEntry> diskEntry = readDisk(uri);
        diskEntry.ifPresent(entry -> memory.put(key, entry));
        return diskEntry.filter(entry -> entry.isFresh(ttl)).map(CacheEntry::body);
    }

    private Optional<CacheEntry> readDisk(URI uri) {
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

    private record CacheEntry(String body, Instant writtenAt) {

        boolean isFresh(Duration ttl) {
            return writtenAt != null && writtenAt.plus(ttl).isAfter(Instant.now());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CachedBody(long writtenAtEpochMilli, String body) {

        static CachedBody from(CacheEntry entry) {
            return new CachedBody(entry.writtenAt().toEpochMilli(), entry.body());
        }
    }
}
