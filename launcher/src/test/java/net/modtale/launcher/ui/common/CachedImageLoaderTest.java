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
    void centerCoversWideAndTallImagesWithoutStretching() {
        assertEquals(new javafx.geometry.Rectangle2D(100, 0, 100, 100),
                CachedImageLoader.coverViewport(300, 100, 64, 64));
        assertEquals(new javafx.geometry.Rectangle2D(0, 100, 300, 100),
                CachedImageLoader.coverViewport(300, 300, 600, 200));
        assertNull(CachedImageLoader.coverViewport(0, 100, 64, 64));
    }

    @Test
    void preservesEveryGifFrameInTheDiskCache() throws Exception {
        Path cached = directory.resolve("animation.img");
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (var output = ImageIO.createImageOutputStream(cached.toFile())) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int color : new int[]{0xff4476c4, 0xff0f172a}) {
                var frame = new java.awt.image.BufferedImage(20, 10, java.awt.image.BufferedImage.TYPE_INT_RGB);
                frame.setRGB(0, 0, color);
                writer.writeToSequence(new javax.imageio.IIOImage(frame, null, null), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        byte[] original = Files.readAllBytes(cached);
        CachedImageLoader.prepareCachedImage(cached);
        assertArrayEquals(original, Files.readAllBytes(cached));
        try (var input = ImageIO.createImageInputStream(cached.toFile())) {
            var reader = ImageIO.getImageReaders(input).next();
            try {
                reader.setInput(input);
                assertEquals(2, reader.getNumImages(true));
            } finally {
                reader.dispose();
            }
        }
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

    @Test
    void transparentImageHidesFallbackAndMissingOrFailedImageRestoresIt() {
        var fallback = new javafx.scene.image.ImageView();
        var foreground = new javafx.scene.image.ImageView();
        CachedImageLoader.showFallbackUntilLoaded(fallback, foreground);
        assertTrue(fallback.isVisible());
        var transparent = new javafx.scene.image.WritableImage(4, 4);
        foreground.setImage(transparent);
        assertFalse(fallback.isVisible());
        assertEquals(0, foreground.getImage().getPixelReader().getArgb(0, 0));
        foreground.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(new byte[] {0})));
        assertTrue(foreground.getImage().isError());
        assertTrue(fallback.isVisible());
        foreground.setImage(transparent);
        assertFalse(fallback.isVisible());
        foreground.setImage(null);
        assertTrue(fallback.isVisible());
        foreground.setImage(transparent);
        var cachedFallback = new javafx.scene.image.ImageView();
        CachedImageLoader.showFallbackUntilLoaded(cachedFallback, foreground);
        assertFalse(cachedFallback.isVisible());
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
