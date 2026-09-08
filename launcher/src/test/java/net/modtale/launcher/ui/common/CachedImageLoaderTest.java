package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CachedImageLoaderTest {
    @TempDir Path directory;

    @Test
    void convertsExistingWebpCacheRegardlessOfExtensionAndPreservesTransparency() throws Exception {
        Path cached = fixture("transparent.webp", "existing-cache.img");
        CachedImageLoader.prepareCachedImage(cached);
        byte[] png = Files.readAllBytes(cached);
        assertArrayEquals(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10},
                java.util.Arrays.copyOf(png, 8));
        var decoded = ImageIO.read(cached.toFile());
        assertEquals(4, decoded.getWidth());
        assertEquals(3, decoded.getHeight());
        assertEquals(0x802378dc, decoded.getRGB(0, 0));
        CachedImageLoader.prepareCachedImage(cached);
        assertArrayEquals(png, Files.readAllBytes(cached));
    }

    @Test
    void convertsLossyWebpToAJavaFxReadableImage() throws Exception {
        Path cached = fixture("lossy.webp", "download.tmp");
        CachedImageLoader.prepareCachedImage(cached);
        try (InputStream input = Files.newInputStream(cached)) {
            var image = new javafx.scene.image.Image(input);
            assertFalse(image.isError());
            assertEquals(4, image.getWidth());
            assertEquals(3, image.getHeight());
        }
    }

    @Test
    void leavesGifBytesUntouched() throws Exception {
        byte[] gif = java.util.Base64.getDecoder().decode(
                "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");
        Path cached = directory.resolve("image.gif");
        Files.write(cached, gif);
        CachedImageLoader.prepareCachedImage(cached);
        assertArrayEquals(gif, Files.readAllBytes(cached));
    }

    @Test
    void failedConversionPreservesOriginalFileAndLeavesNoTemporaryFiles() throws Exception {
        Path cached = directory.resolve("broken.img");
        byte[] broken = "RIFFxxxxWEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        Files.write(cached, broken);
        assertThrows(java.io.IOException.class, () -> CachedImageLoader.prepareCachedImage(cached));
        assertArrayEquals(broken, Files.readAllBytes(cached));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    private Path fixture(String name, String destination) throws Exception {
        Path path = directory.resolve(destination);
        try (InputStream input = getClass().getResourceAsStream("/images/" + name)) {
            assertNotNull(input);
            Files.copy(input, path);
        }
        return path;
    }
}
