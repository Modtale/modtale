package net.modtale.service.security;

import net.modtale.exception.InvalidProjectRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProjectImageValidationService {

    private static final byte[] PNG_HEADER = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPEG_HEADER = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF_HEADER = new byte[]{0x52, 0x49, 0x46, 0x46};
    private static final long MAX_IMAGE_FILE_SIZE = 10L * 1024 * 1024;
    private static final Pattern SVG_TAG_PATTERN = Pattern.compile("<svg\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_VIEWBOX_PATTERN = Pattern.compile("viewBox\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_WIDTH_PATTERN = Pattern.compile("width\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_HEIGHT_PATTERN = Pattern.compile("height\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);

    public void validateImage(MultipartFile file, double targetRatio, String type, String ratioLabel) {
        if (file.getSize() > MAX_IMAGE_FILE_SIZE) {
            throw new InvalidProjectRequestException(type + " image size must not exceed 10MB.");
        }

        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 12) {
                throw new InvalidProjectRequestException("Invalid image file.");
            }
            byte[] header = Arrays.copyOf(bytes, 12);

            boolean isPng = Arrays.equals(Arrays.copyOfRange(header, 0, 4), PNG_HEADER);
            boolean isJpeg = header[0] == JPEG_HEADER[0] && header[1] == JPEG_HEADER[1] && header[2] == JPEG_HEADER[2];
            boolean isRiff = Arrays.equals(Arrays.copyOfRange(header, 0, 4), RIFF_HEADER);
            boolean isWebP = isRiff && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            boolean isSvg = isLikelySvg(file, bytes);

            if (!isPng && !isJpeg && !isWebP && !isSvg) {
                throw new InvalidProjectRequestException("Image must be a valid PNG, JPEG, WebP, or SVG file.");
            }

            if (isSvg) {
                validateSvgAspectRatio(bytes, type, ratioLabel, targetRatio);
                return;
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new InvalidProjectRequestException("Could not decode image data.");
            }
            if (image.getWidth() > 3840 || image.getHeight() > 2160) {
                throw new InvalidProjectRequestException(type + " image dimensions cannot exceed 4K (3840x2160).");
            }

            double actualRatio = (double) image.getWidth() / image.getHeight();
            if (Math.abs(actualRatio - targetRatio) > 0.05) {
                throw new InvalidProjectRequestException(
                        String.format("%s image must have an aspect ratio of %s (Uploaded: %.2f).", type, ratioLabel, actualRatio)
                );
            }
        } catch (IOException e) {
            throw new InvalidProjectRequestException(
                    type + " image could not be read. Ensure the file is a valid PNG, JPEG, WebP, or SVG and is not corrupted."
            );
        }
    }

    private boolean isLikelySvg(MultipartFile file, byte[] bytes) {
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("svg")) {
            return true;
        }

        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            return true;
        }

        String sample = new String(bytes, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        return sample.startsWith("<?xml") || sample.contains("<svg");
    }

    private void validateSvgAspectRatio(byte[] bytes, String type, String ratioLabel, double targetRatio) {
        String svgText = new String(bytes, StandardCharsets.UTF_8);
        Matcher svgTagMatcher = SVG_TAG_PATTERN.matcher(svgText);
        if (!svgTagMatcher.find()) {
            throw new InvalidProjectRequestException("Invalid SVG file.");
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
            throw new InvalidProjectRequestException(type + " SVG must define a valid viewBox or width/height.");
        }

        double actualRatio = width / height;
        if (Math.abs(actualRatio - targetRatio) > 0.05) {
            throw new InvalidProjectRequestException(
                    String.format("%s image must have an aspect ratio of %s (Uploaded: %.2f).", type, ratioLabel, actualRatio)
            );
        }
    }

    private Double parseSvgNumber(String value) {
        if (value == null) {
            return null;
        }

        Matcher matcher = Pattern.compile("^\\s*([-+]?\\d*\\.?\\d+)").matcher(value);
        if (!matcher.find()) {
            return null;
        }

        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
