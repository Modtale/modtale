package net.modtale.launcher.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherCacheServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void clearsCacheRootContentsWithoutDeletingRoot() throws Exception {
        Path cacheRoot = tempDir.resolve("cache");
        Path apiFile = cacheRoot.resolve(Path.of("api", "response.json"));
        Path imageFile = cacheRoot.resolve(Path.of("images", "icon.img"));
        Files.createDirectories(apiFile.getParent());
        Files.createDirectories(imageFile.getParent());
        Files.writeString(apiFile, "{}");
        Files.writeString(imageFile, "image");

        LauncherCacheService.ClearResult result = new LauncherCacheService(cacheRoot).clear();

        assertEquals(4, result.deletedEntries());
        assertTrue(Files.isDirectory(cacheRoot));
        assertFalse(Files.exists(apiFile));
        assertFalse(Files.exists(imageFile));
    }

    @Test
    void missingCacheRootClearsAsEmpty() throws Exception {
        LauncherCacheService.ClearResult result = new LauncherCacheService(tempDir.resolve("missing")).clear();

        assertEquals(0, result.deletedEntries());
    }
}
