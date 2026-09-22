package net.modtale.launcher.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class LauncherCacheService {

    private final Path cacheRoot;

    public LauncherCacheService() {
        this(LauncherCachePaths.rootDirectory());
    }

    LauncherCacheService(Path cacheRoot) {
        this.cacheRoot = cacheRoot;
    }

    public ClearResult clear() throws IOException {
        if (!Files.exists(cacheRoot)) {
            return new ClearResult(0);
        }

        try (Stream<Path> stream = Files.walk(cacheRoot)) {
            List<Path> paths = stream
                    .filter(path -> !cacheRoot.equals(path))
                    .sorted(Comparator.reverseOrder())
                    .toList();
            int deletedEntries = 0;
            for (Path path : paths) {
                if (Files.deleteIfExists(path)) {
                    deletedEntries++;
                }
            }
            return new ClearResult(deletedEntries);
        }
    }

    public record ClearResult(int deletedEntries) {
    }
}
