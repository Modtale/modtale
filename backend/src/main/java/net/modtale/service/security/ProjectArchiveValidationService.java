package net.modtale.service.security;

import net.modtale.exception.InvalidProjectRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ProjectArchiveValidationService {

    private static final byte[] ZIP_HEADER = new byte[]{0x50, 0x4B, 0x03, 0x04};
    private static final List<String> STRICT_BLOCKLIST = List.of(
            ".exe", ".dll", ".so", ".dylib",
            ".sh", ".bat", ".cmd", ".ps1", ".vbs",
            ".js", ".jsp", ".php", ".py", ".pl",
            ".html", ".htm", ".svg", ".hta"
    );
    private static final List<String> ARCHIVE_EXTENSIONS = List.of(".jar", ".zip", ".rar", ".7z", ".tar", ".gz");
    private static final long MAX_UNCOMPRESSED_SIZE = 5L * 1024 * 1024 * 1024;
    private static final int MAX_FILE_COUNT = 100000;
    private static final double MAX_COMPRESSION_RATIO = 100.0;
    private static final long MIN_RATIO_CHECK_SIZE = 100L * 1024 * 1024;
    private static final String PLUGIN_MANIFEST_PATH = "manifest.json";

    private final PluginManifestValidationService pluginManifestValidationService;

    public ProjectArchiveValidationService(PluginManifestValidationService pluginManifestValidationService) {
        this.pluginManifestValidationService = pluginManifestValidationService;
    }

    public FileValidationService.ManifestInspection validateProjectArchive(MultipartFile file, String classification) {
        String name = file.getOriginalFilename();
        String lowerName = name == null ? "" : name.toLowerCase();

        if ("PLUGIN".equals(classification)) {
            if (!lowerName.endsWith(".jar")) {
                throw new InvalidProjectRequestException("Server Plugins must be .jar files.");
            }
        } else if (!lowerName.endsWith(".zip")) {
            throw new InvalidProjectRequestException(classification + " projects must be uploaded as .zip archives.");
        }

        validateMagicNumber(file, ZIP_HEADER);

        try {
            return validateZipContents(file, classification);
        } catch (IOException e) {
            throw new InvalidProjectRequestException(
                    "Archive contents could not be inspected. Ensure the upload is a valid, readable ZIP or JAR file."
            );
        }
    }

    private void validateMagicNumber(MultipartFile file, byte[] expectedHeader) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[4];
            if (inputStream.read(header) != 4) {
                throw new InvalidProjectRequestException("File is too short or corrupted.");
            }
            if (!Arrays.equals(header, expectedHeader)) {
                throw new InvalidProjectRequestException("Invalid file format header.");
            }
        } catch (IOException e) {
            throw new InvalidProjectRequestException("File header could not be read. The upload may be truncated or corrupted.");
        }
    }

    private FileValidationService.ManifestInspection validateZipContents(MultipartFile file, String classification) throws IOException {
        long totalSize = 0;
        int fileCount = 0;
        boolean manifestFound = false;
        long compressedSize = file.getSize();
        byte[] buffer = new byte[8192];
        FileValidationService.ManifestInspection manifestInspection = null;

        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                fileCount++;
                if (fileCount > MAX_FILE_COUNT) {
                    throw new InvalidProjectRequestException("Archive contains too many files (Zip Bomb protection).");
                }

                String entryName = entry.getName();
                String entryNameLower = entryName.toLowerCase();

                if (entryNameLower.contains("..") || entryNameLower.contains(":/") || entryNameLower.startsWith("/")) {
                    throw new InvalidProjectRequestException("Archive contains malicious path traversal: " + entryName);
                }

                if ("PLUGIN".equals(classification) && entryName.equals(PLUGIN_MANIFEST_PATH)) {
                    manifestFound = true;
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    int bytesRead;
                    while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalSize = accumulateArchiveSize(totalSize, compressedSize, bytesRead);
                    }
                    manifestInspection = pluginManifestValidationService.validatePluginManifest(
                            new ByteArrayInputStream(outputStream.toByteArray())
                    );
                } else if (!entry.isDirectory()) {
                    long claimedSize = entry.getSize();
                    if (claimedSize > MAX_UNCOMPRESSED_SIZE) {
                        throw new InvalidProjectRequestException("Single file in archive is too large.");
                    }

                    int bytesRead;
                    while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                        totalSize = accumulateArchiveSize(totalSize, compressedSize, bytesRead);
                    }
                }

                enforceClassificationRules(classification, entryName, entryNameLower);
            }
        }

        if ("PLUGIN".equals(classification) && !manifestFound) {
            throw new InvalidProjectRequestException("Plugin archive must include manifest.json at the archive root.");
        }

        return manifestInspection;
    }

    private long accumulateArchiveSize(long totalSize, long compressedSize, int bytesRead) {
        long nextTotal = totalSize + bytesRead;
        if (nextTotal > MAX_UNCOMPRESSED_SIZE) {
            throw new InvalidProjectRequestException("Archive uncompressed size exceeds limit.");
        }
        if (compressedSize > 0 && nextTotal > MIN_RATIO_CHECK_SIZE
                && (double) nextTotal / compressedSize > MAX_COMPRESSION_RATIO) {
            throw new InvalidProjectRequestException("High compression ratio detected (Zip Bomb protection).");
        }
        return nextTotal;
    }

    private void enforceClassificationRules(String classification, String entryName, String entryNameLower) {
        switch (classification) {
            case "DATA", "ART", "SAVE" -> {
                for (String extension : STRICT_BLOCKLIST) {
                    if (entryNameLower.endsWith(extension)) {
                        throw new InvalidProjectRequestException(
                                "Security Violation: " + classification + " projects cannot contain " + extension + " files (" + entryName + ")"
                        );
                    }
                }
                for (String extension : ARCHIVE_EXTENSIONS) {
                    if (entryNameLower.endsWith(extension)) {
                        throw new InvalidProjectRequestException(
                                "Security Violation: Nested archives (" + extension + ") are not allowed in " + classification
                        );
                    }
                }
            }
            case "MODPACK" -> {
                // Intentionally permissive for now.
            }
            default -> {
            }
        }
    }
}
