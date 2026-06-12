package net.modtale.service.project.version;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.exception.StorageArtifactOperationException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.service.security.validation.FileValidationService;
import net.modtale.service.storage.StorageService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VersionArtifactService {

    private static final List<ProjectClassification> MUTABLE_CLASSIFICATIONS = List.of(
            ProjectClassification.PLUGIN,
            ProjectClassification.DATA,
            ProjectClassification.ART
    );

    private final StorageService storageService;
    private final FileValidationService fileValidationService;
    private final MongoTemplate mongoTemplate;

    public VersionArtifactService(
            StorageService storageService,
            FileValidationService fileValidationService,
            MongoTemplate mongoTemplate
    ) {
        this.storageService = storageService;
        this.fileValidationService = fileValidationService;
        this.mongoTemplate = mongoTemplate;
    }

    public PreparedVersionArtifact prepareVersionArtifact(Project project, MultipartFile file) {
        ProjectClassification effectiveClassification = resolveClassificationForUpload(project, file);
        boolean isModpack = effectiveClassification == ProjectClassification.MODPACK;

        storageService.validateUploadSize(file);
        if (file != null && !isModpack) {
            fileValidationService.validateProjectFile(file, effectiveClassification.name());
        }

        String filePath = null;
        String fileHash = null;
        if (file != null) {
            if (!isModpack) {
                fileHash = calculateSha256(file);
                Query duplicateQuery = new Query(Criteria.where("versions.hash").is(fileHash).and("deletedAt").is(null));
                if (mongoTemplate.exists(duplicateQuery, Project.class)) {
                    throw new InvalidVersionRequestException("This file has already been uploaded to Modtale.");
                }
            }
            filePath = storageService.upload(file, "files/" + effectiveClassification.name().toLowerCase());
        }

        return new PreparedVersionArtifact(effectiveClassification, filePath, fileHash);
    }

    private ProjectClassification resolveClassificationForUpload(Project project, MultipartFile file) {
        ProjectClassification current = project.getClassification();
        if (current == null || file == null || file.isEmpty()) return current;
        if (current == ProjectClassification.MODPACK || current == ProjectClassification.SAVE) return current;
        if (!MUTABLE_CLASSIFICATIONS.contains(current)) return current;

        String name = file.getOriginalFilename();
        if (name == null) return current;

        String lowerName = name.toLowerCase();
        ProjectClassification next = current;
        if (lowerName.endsWith(".jar")) {
            next = ProjectClassification.PLUGIN;
        } else if (lowerName.endsWith(".zip") && current == ProjectClassification.PLUGIN) {
            next = ProjectClassification.DATA;
        }

        if (next != current) {
            project.setClassification(next);
        }
        return next;
    }

    private String calculateSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }

            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.io.IOException ex) {
            throw StorageArtifactOperationException.from(ex, "Failed to read the uploaded file while calculating its checksum.");
        } catch (NoSuchAlgorithmException ex) {
            throw new StorageArtifactOperationException("SHA-256 hashing is not available on this server.", ex);
        }
    }

    public record PreparedVersionArtifact(ProjectClassification classification, String filePath, String fileHash) {
    }
}
