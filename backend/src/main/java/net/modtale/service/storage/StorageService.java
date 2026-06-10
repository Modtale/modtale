package net.modtale.service.storage;

import net.coobird.thumbnailator.Thumbnails;
import net.modtale.config.properties.AppR2Properties;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.StorageDownloadException;
import net.modtale.exception.StorageUploadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class StorageService {

    private static final Logger logger = LoggerFactory.getLogger(StorageService.class);

    private final S3Client s3Client;
    private final String bucketName;
    private final String publicDomain;

    private static final String DEFAULT_IMAGE = "default.png";

    private static final String CACHE_CONTROL_HEADER = "public, max-age=31536000, immutable";
    private static final long MAX_UPLOAD_BYTES = 100L * 1024 * 1024;
    private static final String MAX_UPLOAD_ERROR_MESSAGE = "File exceeds 100MB limit. Cloudflare only supports uploads up to 100MB.";

    private static final Map<String, String> MIME_TYPES = new HashMap<>();
    static {
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("webp", "image/webp");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("zip", "application/zip");
        MIME_TYPES.put("jar", "application/java-archive");
    }

    public StorageService(
            S3Client s3Client,
            AppR2Properties r2Properties
    ) {
        this.s3Client = s3Client;
        this.bucketName = r2Properties.bucket();
        this.publicDomain = r2Properties.publicDomain();
    }

    public String upload(MultipartFile file, String pathPrefix) {
        validateUploadSize(file);
        if (bucketName == null || bucketName.isEmpty()) {
            throw new StorageUploadException("Storage configuration error: Bucket name is not set.", null);
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) originalName = "unknown";

        String extension = getExtension(originalName);
        String safeContentType = MIME_TYPES.getOrDefault(extension.toLowerCase(), "application/octet-stream");

        String sanitizedName = sanitizeFilename(originalName);
        String storageKey = pathPrefix + "/" + UUID.randomUUID() + "-" + sanitizedName;

        String contentDisposition = "attachment; filename=\"" + originalName.replace("\"", "") + "\"";

        try {
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .contentType(safeContentType)
                    .contentDisposition(contentDisposition)
                    .cacheControl(CACHE_CONTROL_HEADER)
                    .build();

            s3Client.putObject(putOb, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            logger.info("Successfully uploaded {} to bucket {}", storageKey, bucketName);
        } catch (S3Exception | IOException e) {
            logger.error("Failed to upload to S3/R2. Bucket: {}, Key: {}. Error: {}", bucketName, storageKey, e.getMessage());
            throw StorageUploadException.from(e, "Failed to upload the file to cloud storage.");
        }

        return storageKey;
    }

    public String uploadAndResize(MultipartFile file, String pathPrefix, int targetWidth) {
        try {
            validateUploadSize(file);
            String fileName = pathPrefix + "/" + UUID.randomUUID() + ".jpg";

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Thumbnails.of(file.getInputStream())
                    .size(targetWidth, targetWidth)
                    .outputQuality(0.8)
                    .outputFormat("jpg")
                    .toOutputStream(outputStream);

            byte[] resizedBytes = outputStream.toByteArray();

            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType("image/jpeg")
                    .cacheControl(CACHE_CONTROL_HEADER)
                    .build();

            s3Client.putObject(putOb, RequestBody.fromBytes(resizedBytes));

            return fileName;
        } catch (IOException | SdkException ex) {
            throw StorageUploadException.from(ex, "Failed to resize and upload the file.");
        }
    }

    public void uploadDirect(String path, byte[] data, String contentType) {
        PutObjectRequest putOb = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(path)
                .contentType(contentType)
                .cacheControl(CACHE_CONTROL_HEADER)
                .build();

        s3Client.putObject(putOb, RequestBody.fromBytes(data));
    }

    public void deleteFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) return;

        if (fileName.startsWith("/api/files/proxy/")) {
            fileName = fileName.replace("/api/files/proxy/", "");
        } else if (publicDomain != null && fileName.startsWith(publicDomain)) {
            fileName = fileName.replace(publicDomain + "/", "");
        }

        if (fileName.contains(DEFAULT_IMAGE) || fileName.contains("placeholder.png")) {
            return;
        }

        try {
            DeleteObjectRequest deleteReq = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();
            s3Client.deleteObject(deleteReq);
            logger.info("R2: Deleted file " + fileName + " from bucket " + bucketName);
        } catch (SdkException e) {
            logger.error("R2 ERROR: Failed to delete " + fileName + " from bucket " + bucketName, e);
        }
    }

    public byte[] download(String fileName) {
        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getReq);
            return response.readAllBytes();
        } catch (NoSuchKeyException e) {
            throw new StorageDownloadException("The requested file is not available in storage.", e);
        } catch (IOException | SdkException e) {
            throw StorageDownloadException.from(e, "Failed to download the requested file.");
        }
    }

    public InputStream getStream(String fileName) {
        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();
            return s3Client.getObject(getReq);
        } catch (NoSuchKeyException e) {
            throw new StorageDownloadException("The requested file is not available in storage.", e);
        } catch (SdkException e) {
            throw StorageDownloadException.from(e, "Failed to stream the requested file.");
        }
    }

    public String getContentType(String fileName) {
        try {
            HeadObjectRequest headReq = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();
            HeadObjectResponse response = s3Client.headObject(headReq);
            return response.contentType();
        } catch (NoSuchKeyException e) {
            logger.warn("R2 object not found while resolving content type for {}", fileName);
            return "application/octet-stream";
        } catch (SdkException e) {
            logger.warn("Falling back to default content type for {} due to storage error", fileName, e);
            return "application/octet-stream";
        }
    }

    public String getPublicUrl(String fileName) {
        if (publicDomain != null && !publicDomain.isBlank()) {
            return publicDomain + "/" + fileName;
        }
        return "/api/files/proxy/" + fileName;
    }

    public void validateUploadSize(MultipartFile file) {
        if (file != null && file.getSize() > MAX_UPLOAD_BYTES) {
            throw new InvalidProjectRequestException(MAX_UPLOAD_ERROR_MESSAGE);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "unknown";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String getExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
