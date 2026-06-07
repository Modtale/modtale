package net.modtale.service.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileValidationService {

    private static final byte[] ZIP_HEADER = new byte[]{0x50, 0x4B, 0x03, 0x04};
    private static final byte[] PNG_HEADER = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPEG_HEADER = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF_HEADER = new byte[]{0x52, 0x49, 0x46, 0x46};

    private static final List<String> STRICT_BLOCKLIST = Arrays.asList(
            ".exe", ".dll", ".so", ".dylib",
            ".sh", ".bat", ".cmd", ".ps1", ".vbs",
            ".js", ".jsp", ".php", ".py", ".pl",
            ".html", ".htm", ".svg", ".hta"
    );

    private static final List<String> ARCHIVE_EXTENSIONS = Arrays.asList(".jar", ".zip", ".rar", ".7z", ".tar", ".gz");

    private static final long MAX_UNCOMPRESSED_SIZE = 5L * 1024 * 1024 * 1024;
    private static final int MAX_FILE_COUNT = 100000;
    private static final double MAX_COMPRESSION_RATIO = 100.0;
    private static final long MIN_RATIO_CHECK_SIZE = 100L * 1024 * 1024;
    private static final String PLUGIN_MANIFEST_PATH = "manifest.json";
    private static final long MAX_IMAGE_FILE_SIZE = 10L * 1024 * 1024; // 10MB
    private static final List<String> MUTABLE_CLASSIFICATIONS = Arrays.asList("PLUGIN", "DATA", "ART");
    private static final Pattern SVG_TAG_PATTERN = Pattern.compile("<svg\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_VIEWBOX_PATTERN = Pattern.compile("viewBox\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_WIDTH_PATTERN = Pattern.compile("width\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_HEIGHT_PATTERN = Pattern.compile("height\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ManifestInspection validateProjectFile(MultipartFile file, String classification) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A project file is required.");
        }

        String effectiveClassification = resolveUploadClassification(classification, file);
        String name = file.getOriginalFilename();
        String lowerName = name == null ? "" : name.toLowerCase();

        if ("PLUGIN".equals(effectiveClassification)) {
            if (!lowerName.endsWith(".jar")) throw new IllegalArgumentException("Server Plugins must be .jar files.");
        } else if (!lowerName.endsWith(".zip")) {
            throw new IllegalArgumentException(effectiveClassification + " projects must be uploaded as .zip archives.");
        }

        validateMagicNumber(file, ZIP_HEADER);

        try {
            return validateZipContents(file, effectiveClassification);
        } catch (IOException e) {
            throw new IllegalArgumentException("Archive contents could not be inspected. Ensure the upload is a valid, readable ZIP or JAR file.", e);
        }
    }

    public String resolveUploadClassification(String classification, MultipartFile file) {
        if (classification == null) return null;
        if (file == null || file.isEmpty()) return classification;

        if ("MODPACK".equals(classification) || "SAVE".equals(classification)) {
            return classification;
        }
        if (!MUTABLE_CLASSIFICATIONS.contains(classification)) {
            return classification;
        }

        String name = file.getOriginalFilename();
        if (name == null) return classification;
        String lowerName = name.toLowerCase();

        if (lowerName.endsWith(".jar")) return "PLUGIN";
        if (lowerName.endsWith(".zip") && "PLUGIN".equals(classification)) return "DATA";
        return classification;
    }

    private void validateMagicNumber(MultipartFile file, byte[] expectedHeader) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[4];
            if (is.read(header) != 4) throw new IllegalArgumentException("File is too short or corrupted.");
            if (!Arrays.equals(header, expectedHeader)) {
                throw new IllegalArgumentException("Invalid file format header.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("File header could not be read. The upload may be truncated or corrupted.", e);
        }
    }

    private ManifestInspection validateZipContents(MultipartFile file, String classification) throws IOException {
        long totalSize = 0;
        int fileCount = 0;
        boolean manifestFound = false;
        long compressedSize = file.getSize();
        byte[] buffer = new byte[8192];
        ManifestInspection manifestInspection = null;

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                fileCount++;
                if (fileCount > MAX_FILE_COUNT) {
                    throw new IllegalArgumentException("Archive contains too many files (Zip Bomb protection).");
                }

                String entryName = entry.getName();
                String entryNameLower = entryName.toLowerCase();

                if (entryNameLower.contains("..") || entryNameLower.contains(":/") || entryNameLower.startsWith("/")) {
                    throw new IllegalArgumentException("Archive contains malicious path traversal: " + entryName);
                }

                if ("PLUGIN".equals(classification) && entryName.equals(PLUGIN_MANIFEST_PATH)) {
                    manifestFound = true;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int bytesRead;
                    while ((bytesRead = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                        totalSize += bytesRead;
                        if (totalSize > MAX_UNCOMPRESSED_SIZE) {
                            throw new IllegalArgumentException("Archive uncompressed size exceeds limit.");
                        }
                        if (compressedSize > 0 && totalSize > MIN_RATIO_CHECK_SIZE) {
                            if ((double) totalSize / compressedSize > MAX_COMPRESSION_RATIO) {
                                throw new IllegalArgumentException("High compression ratio detected (Zip Bomb protection).");
                            }
                        }
                    }
                    manifestInspection = validatePluginManifest(new ByteArrayInputStream(baos.toByteArray()));
                } else if (!entry.isDirectory()) {
                    long claimedSize = entry.getSize();
                    if (claimedSize > MAX_UNCOMPRESSED_SIZE) {
                        throw new IllegalArgumentException("Single file in archive is too large.");
                    }

                    int bytesRead;
                    while ((bytesRead = zis.read(buffer)) != -1) {
                        totalSize += bytesRead;
                        if (totalSize > MAX_UNCOMPRESSED_SIZE) {
                            throw new IllegalArgumentException("Archive uncompressed size exceeds limit.");
                        }
                        if (compressedSize > 0 && totalSize > MIN_RATIO_CHECK_SIZE) {
                            if ((double) totalSize / compressedSize > MAX_COMPRESSION_RATIO) {
                                throw new IllegalArgumentException("High compression ratio detected (Zip Bomb protection).");
                            }
                        }
                    }
                }

                switch (classification) {
                    case "DATA":
                    case "ART":
                    case "SAVE":
                        for (String ext : STRICT_BLOCKLIST) {
                            if (entryNameLower.endsWith(ext)) {
                                throw new IllegalArgumentException("Security Violation: " + classification + " projects cannot contain " + ext + " files (" + entryName + ")");
                            }
                        }
                        for (String ext : ARCHIVE_EXTENSIONS) {
                            if (entryNameLower.endsWith(ext)) {
                                throw new IllegalArgumentException("Security Violation: Nested archives (" + ext + ") are not allowed in " + classification);
                            }
                        }
                        break;
                    case "MODPACK":
                        break;
                }
            }
        }
        if ("PLUGIN".equals(classification) && !manifestFound) {
            throw new IllegalArgumentException("Plugin archive must include manifest.json at the archive root.");
        }
        return manifestInspection;
    }

    private ManifestInspection validatePluginManifest(InputStream is) {
        try {
            JsonNode root = objectMapper.readTree(is);

            String[] requiredFields = {"Group", "Name", "Version", "ServerVersion", "Main"};
            for (String field : requiredFields) {
                if (!root.has(field) || root.get(field).asText().isEmpty()) {
                    throw new IllegalArgumentException("Plugin manifest.json is missing required field: " + field);
                }
            }

            if (!root.has("Authors") || !root.get("Authors").isArray() || root.get("Authors").isEmpty()) {
                throw new IllegalArgumentException("Plugin manifest.json must contain at least one Author.");
            }

            for (JsonNode author : root.get("Authors")) {
                if (!author.has("Name") || author.get("Name").asText().isBlank()) {
                    throw new IllegalArgumentException("Plugin manifest.json Authors entries must include Name.");
                }
            }

            List<ManifestDependency> dependencies = new ArrayList<>();
            readManifestDependencies(root.get("Dependencies"), false, dependencies);
            readManifestDependencies(root.get("OptionalDependencies"), true, dependencies);

            return new ManifestInspection(
                    root.get("Group").asText(),
                    root.get("Name").asText(),
                    root.get("Version").asText(),
                    root.get("ServerVersion").asText(),
                    dependencies
            );
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse manifest.json. Ensure it is valid JSON.");
        }
    }

    private void readManifestDependencies(JsonNode node, boolean optional, List<ManifestDependency> dependencies) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (!node.isObject()) {
            throw new IllegalArgumentException((optional ? "OptionalDependencies" : "Dependencies") + " must be a JSON object.");
        }

        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (key == null || key.isBlank()) return;
            if (key.regionMatches(true, 0, "Hytale:", 0, "Hytale:".length())) return;

            String version = entry.getValue().isTextual() ? entry.getValue().asText() : entry.getValue().toString();
            dependencies.add(new ManifestDependency(key, version, optional));
        });
    }

    public static class ManifestInspection {
        private final String group;
        private final String name;
        private final String version;
        private final String serverVersion;
        private final List<ManifestDependency> dependencies;

        public ManifestInspection(String group, String name, String version, String serverVersion, List<ManifestDependency> dependencies) {
            this.group = group;
            this.name = name;
            this.version = version;
            this.serverVersion = serverVersion;
            this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
        }

        public String getGroup() { return group; }
        public String getName() { return name; }
        public String getVersion() { return version; }
        public String getServerVersion() { return serverVersion; }
        public List<ManifestDependency> getDependencies() { return dependencies; }
    }

    public static class ManifestDependency {
        private final String key;
        private final String version;
        private final boolean optional;

        public ManifestDependency(String key, String version, boolean optional) {
            this.key = key;
            this.version = version;
            this.optional = optional;
        }

        public String getKey() { return key; }
        public String getVersion() { return version; }
        public boolean isOptional() { return optional; }
        public String getNamePart() {
            int separator = key.indexOf(':');
            return separator >= 0 && separator < key.length() - 1 ? key.substring(separator + 1) : key;
        }
    }

    public void validateIcon(MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        validateImage(file, 1.0, "Icon", "1:1");
    }

    public void validateBanner(MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        validateImage(file, 3.0, "Banner", "3:1");
    }

    public void validateGalleryImage(MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        validateImage(file, 16.0 / 9.0, "Gallery", "16:9");
    }

    private void validateImage(MultipartFile file, double targetRatio, String type, String ratioLabel) {
        if (file.getSize() > MAX_IMAGE_FILE_SIZE) {
            throw new IllegalArgumentException(type + " image size must not exceed 10MB.");
        }

        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            is.mark(13);
            if (is.read(header) < 12) throw new IllegalArgumentException("Invalid image file.");
            is.reset();

            boolean isPng = Arrays.equals(Arrays.copyOfRange(header, 0, 4), PNG_HEADER);
            boolean isJpeg = header[0] == JPEG_HEADER[0] && header[1] == JPEG_HEADER[1] && header[2] == JPEG_HEADER[2];

            boolean isRiff = Arrays.equals(Arrays.copyOfRange(header, 0, 4), RIFF_HEADER);
            boolean isWebP = isRiff && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            boolean isSvg = isLikelySvg(file);

            if (!isPng && !isJpeg && !isWebP && !isSvg) {
                throw new IllegalArgumentException("Image must be a valid PNG, JPEG, WebP, or SVG file.");
            }

            if (isSvg) {
                validateSvgAspectRatio(file, targetRatio, type, ratioLabel);
                return;
            }

            BufferedImage image = ImageIO.read(is);
            if (image == null) throw new IllegalArgumentException("Could not decode image data.");

            if (image.getWidth() > 3840 || image.getHeight() > 2160) {
                throw new IllegalArgumentException(type + " image dimensions cannot exceed 4K (3840x2160).");
            }

            double actualRatio = (double) image.getWidth() / image.getHeight();
            if (Math.abs(actualRatio - targetRatio) > 0.05) {
                throw new IllegalArgumentException(String.format("%s image must have an aspect ratio of %s (Uploaded: %.2f).", type, ratioLabel, actualRatio));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(type + " image could not be read. Ensure the file is a valid PNG, JPEG, WebP, or SVG and is not corrupted.", e);
        }
    }

    private boolean isLikelySvg(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("svg")) {
            return true;
        }

        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            return true;
        }

        String sample = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        return sample.startsWith("<?xml") || sample.contains("<svg");
    }

    private void validateSvgAspectRatio(MultipartFile file, double targetRatio, String type, String ratioLabel) throws IOException {
        String svgText = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        Matcher svgTagMatcher = SVG_TAG_PATTERN.matcher(svgText);
        if (!svgTagMatcher.find()) {
            throw new IllegalArgumentException("Invalid SVG file.");
        }

        String svgTag = svgTagMatcher.group(0);
        Double width = null;
        Double height = null;

        Matcher viewBoxMatcher = SVG_VIEWBOX_PATTERN.matcher(svgTag);
        if (viewBoxMatcher.find()) {
            String[] parts = viewBoxMatcher.group(1).trim().split("[\\s,]+");
            if (parts.length == 4) {
                width = parseSvgNumber(parts[2]);
                height = parseSvgNumber(parts[3]);
            }
        }

        if (width == null || height == null || width <= 0 || height <= 0) {
            Matcher widthMatcher = SVG_WIDTH_PATTERN.matcher(svgTag);
            Matcher heightMatcher = SVG_HEIGHT_PATTERN.matcher(svgTag);
            if (widthMatcher.find() && heightMatcher.find()) {
                width = parseSvgNumber(widthMatcher.group(1));
                height = parseSvgNumber(heightMatcher.group(1));
            }
        }

        if (width == null || height == null || width <= 0 || height <= 0) {
            throw new IllegalArgumentException(type + " SVG must define a valid viewBox or width/height.");
        }

        double actualRatio = width / height;
        if (Math.abs(actualRatio - targetRatio) > 0.05) {
            throw new IllegalArgumentException(String.format("%s image must have an aspect ratio of %s (Uploaded: %.2f).", type, ratioLabel, actualRatio));
        }
    }

    private Double parseSvgNumber(String value) {
        if (value == null) return null;
        Matcher m = Pattern.compile("^\\s*([-+]?\\d*\\.?\\d+)").matcher(value);
        if (!m.find()) return null;
        try {
            return Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
