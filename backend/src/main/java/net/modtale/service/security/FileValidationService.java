package net.modtale.service.security;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileValidationService {

    private static final byte[] ZIP_HEADER = new byte[]{0x50, 0x4B, 0x03, 0x04};
    private static final byte[] PNG_HEADER = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPEG_HEADER = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF_HEADER = new byte[]{0x52, 0x49, 0x46, 0x46};

    private static final List<String> STRICT_BLOCKLIST = Arrays.asList(
            ".exe", ".dll", ".so", ".dylib", ".bin",
            ".sh", ".bat", ".cmd", ".ps1", ".vbs",
            ".js", ".jsp", ".php", ".py", ".pl",
            ".html", ".htm", ".svg", ".hta"
    );

    private static final List<String> ARCHIVE_EXTENSIONS = Arrays.asList(".jar", ".zip", ".rar", ".7z", ".tar", ".gz");

    private static final long MAX_UNCOMPRESSED_SIZE = 2L * 1024 * 1024 * 1024;
    private static final int MAX_FILE_COUNT = 10000;

    public void validateProjectFile(MultipartFile file, String classification) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A project file is required.");
        }

        String name = file.getOriginalFilename().toLowerCase();

        if ("PLUGIN".equals(classification)) {
            if (!name.endsWith(".jar")) throw new IllegalArgumentException("Server Plugins must be .jar files.");
            validateMagicNumber(file, ZIP_HEADER);
            return;
        }

        if (!name.endsWith(".zip")) {
            throw new IllegalArgumentException(classification + " projects must be uploaded as .zip archives.");
        }

        validateMagicNumber(file, ZIP_HEADER);

        try {
            validateZipContents(file, classification);
        } catch (IOException e) {
            throw new RuntimeException("Failed to inspect archive contents.", e);
        }
    }

    private void validateMagicNumber(MultipartFile file, byte[] expectedHeader) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[4];
            if (is.read(header) != 4) throw new IllegalArgumentException("File is too short or corrupted.");
            if (!Arrays.equals(header, expectedHeader)) {
                throw new IllegalArgumentException("Invalid file format header.");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file header.", e);
        }
    }

    private void validateZipContents(MultipartFile file, String classification) throws IOException {
        long totalSize = 0;
        int fileCount = 0;

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                fileCount++;
                if (fileCount > MAX_FILE_COUNT) {
                    throw new IllegalArgumentException("Archive contains too many files (Zip Bomb protection).");
                }

                if (!entry.isDirectory()) {
                    long size = entry.getSize();
                    if (size > MAX_UNCOMPRESSED_SIZE) {
                        throw new IllegalArgumentException("Single file in archive is too large.");
                    }
                    if (size != -1) {
                        totalSize += size;
                    }
                }

                if (totalSize > MAX_UNCOMPRESSED_SIZE) {
                    throw new IllegalArgumentException("Archive uncompressed size exceeds limit (Zip Bomb protection).");
                }

                String entryName = entry.getName().toLowerCase();

                if (entryName.contains("..") || entryName.contains(":/") || entryName.startsWith("/")) {
                    throw new IllegalArgumentException("Archive contains malicious path traversal: " + entryName);
                }

                switch (classification) {
                    case "DATA":
                    case "ART":
                    case "SAVE":
                        for (String ext : STRICT_BLOCKLIST) {
                            if (entryName.endsWith(ext)) {
                                throw new IllegalArgumentException("Security Violation: " + classification + " projects cannot contain " + ext + " files (" + entryName + ")");
                            }
                        }
                        for (String ext : ARCHIVE_EXTENSIONS) {
                            if (entryName.endsWith(ext)) {
                                throw new IllegalArgumentException("Security Violation: Nested archives (" + ext + ") are not allowed in " + classification);
                            }
                        }
                        break;
                    case "MODPACK":
                }
            }
        }
    }

    public void validateIcon(MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        validateImageStructure(file);
        validateDimensions(file, 1.0, "Icon", "1:1");
    }

    public void validateBanner(MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        validateImageStructure(file);
        validateDimensions(file, 3.0, "Banner", "3:1");
    }

    private void validateImageStructure(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            if (is.read(header) < 12) throw new IllegalArgumentException("Invalid image file.");

            boolean isPng = Arrays.equals(Arrays.copyOfRange(header, 0, 4), PNG_HEADER);
            boolean isJpeg = header[0] == JPEG_HEADER[0] && header[1] == JPEG_HEADER[1] && header[2] == JPEG_HEADER[2];

            boolean isRiff = Arrays.equals(Arrays.copyOfRange(header, 0, 4), RIFF_HEADER);
            boolean isWebP = isRiff && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';

            if (!isPng && !isJpeg && !isWebP) {
                throw new IllegalArgumentException("Image must be a valid PNG, JPEG, or WebP file.");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to inspect image header.", e);
        }
    }

    private void validateDimensions(MultipartFile file, double targetRatio, String type, String ratioLabel) {
        try (InputStream is = file.getInputStream()) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) throw new IllegalArgumentException("Could not decode image data.");

            double actualRatio = (double) image.getWidth() / image.getHeight();
            if (Math.abs(actualRatio - targetRatio) > 0.05) {
                throw new IllegalArgumentException(String.format("%s image must have an aspect ratio of %s (Uploaded: %.2f).", type, ratioLabel, actualRatio));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to validate image dimensions.", e);
        }
    }
}