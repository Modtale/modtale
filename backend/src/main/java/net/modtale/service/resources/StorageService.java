package net.modtale.service.resources;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
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

    @Autowired
    private S3Client s3Client;

    @Value("${app.r2.bucket}")
    private String bucketName;

    @Value("${app.r2.public-domain:#{null}}")
    private String publicDomain;

    private static final String DEFAULT_IMAGE = "default.png";

    private static final String CACHE_CONTROL_HEADER = "public, max-age=31536000, immutable";

    private static final Map<String, String> MIME_TYPES = new HashMap<>();
    static {
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("webp", "image/webp");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("zip", "application/zip");
        MIME_TYPES.put("jar", "application/java-archive");
    }

    public String upload(MultipartFile file, String pathPrefix) throws IOException {
        if (bucketName == null || bucketName.isEmpty()) {
            throw new IOException("Storage configuration error: Bucket name is not set.");
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
        } catch (S3Exception e) {
            logger.error("Failed to upload to S3/R2. Bucket: {}, Key: {}. Error: {}", bucketName, storageKey, e.getMessage());
            throw new IOException("Cloud storage error: " + e.getMessage(), e);
        }

        return storageKey;
    }

    public String uploadAndResize(MultipartFile file, String pathPrefix, int targetWidth) throws IOException {
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
        } catch (Exception e) {
            logger.error("R2 ERROR: Failed to delete " + fileName + " from bucket " + bucketName, e);
        }
    }

    public byte[] download(String fileName) throws IOException {
        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getReq);
            return response.readAllBytes();
        } catch (NoSuchKeyException e) {
            throw new IOException("File not found in bucket " + bucketName + ": " + fileName);
        }
    }

    public InputStream getStream(String fileName) throws IOException {
        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();
            return s3Client.getObject(getReq);
        } catch (NoSuchKeyException e) {
            throw new IOException("File not found in bucket " + bucketName + ": " + fileName);
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
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    public String getPublicUrl(String fileName) {
        if (publicDomain != null && !publicDomain.isBlank()) {
            return publicDomain + "/" + fileName;
        }
        return "/api/files/proxy/" + fileName;
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