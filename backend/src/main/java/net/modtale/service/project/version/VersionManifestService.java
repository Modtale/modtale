package net.modtale.service.project.version;

import net.modtale.model.dto.project.ManifestInspectionResult;
import net.modtale.model.project.Project;
import net.modtale.service.project.validation.ValidationService;
import net.modtale.service.security.validation.FileValidationService;
import net.modtale.service.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VersionManifestService {

    private final VersionManifestInspectionService versionManifestInspectionService;

    @Autowired
    public VersionManifestService(VersionManifestInspectionService versionManifestInspectionService) {
        this.versionManifestInspectionService = versionManifestInspectionService;
    }

    public VersionManifestService(
            ValidationService validationService,
            StorageService storageService,
            FileValidationService fileValidationService,
            MongoTemplate mongoTemplate
    ) {
        VersionManifestMatchingService matchingService = new VersionManifestMatchingService();
        this.versionManifestInspectionService = new VersionManifestInspectionService(
                validationService,
                storageService,
                fileValidationService,
                new VersionManifestCandidateQueryService(mongoTemplate),
                matchingService
        );
    }

    public ManifestInspectionResult inspectManifest(Project project, MultipartFile file) {
        return versionManifestInspectionService.inspectManifest(project, file);
    }
}
