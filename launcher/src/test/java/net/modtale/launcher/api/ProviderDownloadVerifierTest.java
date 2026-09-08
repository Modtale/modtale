package net.modtale.launcher.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProviderDownloadVerifierTest {

    @TempDir
    Path tempDir;

    @Test
    void acceptsMatchingSizeAndSha1() throws Exception {
        Path file = tempDir.resolve("mod.jar");
        Files.writeString(file, "curseforge-mod");
        assertDoesNotThrow(() -> ProviderDownloadVerifier.verify(file, Files.size(file),
                Map.of("sha1", "f6fbd17789ec325a7f0b93f14d75ec7cbaff16f9")));
    }

    @Test
    void rejectsTruncatedAndTamperedDownloads() throws Exception {
        Path file = tempDir.resolve("mod.jar");
        Files.writeString(file, "curseforge-mod");
        assertThrows(Exception.class, () -> ProviderDownloadVerifier.verify(file, Files.size(file) + 1, Map.of()));
        assertThrows(Exception.class, () -> ProviderDownloadVerifier.verify(file, Files.size(file),
                Map.of("sha1", "0".repeat(40))));
    }
}
