package net.modtale.service.security.validation;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.modtale.exception.InvalidProjectRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class ProjectImageValidationServiceTest {
    private final ProjectImageValidationService validator = new ProjectImageValidationService();

    @Test
    void acceptsOriginalAnimatedGifForIconsBannersAndGalleryWithoutChangingFrames() throws Exception {
        byte[] bytes = animatedGif();
        var file = file(bytes);
        validator.validateImage(file, 1.0, "Icon", "1:1");
        validator.validateImage(file, 3.0, "Banner", "3:1");
        validator.validateImage(file, "Gallery");
        assertArrayEquals(bytes, file.getBytes());
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
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
    void acceptsGif87a() throws Exception {
        byte[] bytes = animatedGif();
        bytes[4] = '7';
        assertDoesNotThrow(() -> validator.validateImage(file(bytes), "Gallery"));
    }

    @Test
    void rejectsCorruptGifAndSpoofedFileType() throws Exception {
        assertThrows(InvalidProjectRequestException.class, () -> validator.validateImage(file(Arrays.copyOf(animatedGif(), 12)), "Gallery"));
        assertThrows(InvalidProjectRequestException.class, () -> validator.validateImage(file(new byte[100]), "Gallery"));
    }

    @Test
    void enforcesFileSizeAndLogicalCanvasLimits() throws Exception {
        assertThrows(InvalidProjectRequestException.class, () -> validator.validateImage(file(new byte[10 * 1024 * 1024 + 1]), "Gallery"));
        byte[] bytes = animatedGif();
        bytes[6] = (byte) 0xff;
        bytes[7] = (byte) 0xff;
        assertThrows(InvalidProjectRequestException.class, () -> validator.validateImage(file(bytes), "Icon"));
    }

    @Test
    void stillImagesRetainAspectRatioValidation() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", output);
        var png = new MockMultipartFile("file", "image.png", "image/png", output.toByteArray());
        assertThrows(InvalidProjectRequestException.class, () -> validator.validateImage(png, 1.0, "Icon", "1:1"));
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "animation.gif", "image/gif", bytes);
    }

    private byte[] animatedGif() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int color : new int[]{0xff4476c4, 0xff0f172a}) {
                var frame = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
                frame.setRGB(0, 0, color);
                writer.writeToSequence(new IIOImage(frame, null, null), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
