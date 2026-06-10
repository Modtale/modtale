package net.modtale.service.project;

import net.modtale.model.dto.project.ManifestInspectionResult;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.service.security.FileValidationService;
import net.modtale.service.security.FileValidationService.ManifestInspection;
import net.modtale.service.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class VersionManifestInspectionService {

    private final ValidationService validationService;
    private final StorageService storageService;
    private final FileValidationService fileValidationService;
    private final VersionManifestCandidateQueryService versionManifestCandidateQueryService;
    private final VersionManifestMatchingService versionManifestMatchingService;

    public VersionManifestInspectionService(
            ValidationService validationService,
            StorageService storageService,
            FileValidationService fileValidationService,
            VersionManifestCandidateQueryService versionManifestCandidateQueryService,
            VersionManifestMatchingService versionManifestMatchingService
    ) {
        this.validationService = validationService;
        this.storageService = storageService;
        this.fileValidationService = fileValidationService;
        this.versionManifestCandidateQueryService = versionManifestCandidateQueryService;
        this.versionManifestMatchingService = versionManifestMatchingService;
    }

    public ManifestInspectionResult inspectManifest(Project project, MultipartFile file) {
        if (project.getClassification() != ProjectClassification.PLUGIN) {
            return new ManifestInspectionResult(null, null, List.of());
        }

        storageService.validateUploadSize(file);
        ManifestInspection manifest = fileValidationService.validateProjectFile(file, project.getClassification().name());
        if (manifest == null) {
            return new ManifestInspectionResult(null, null, List.of());
        }

        return new ManifestInspectionResult(
                versionManifestMatchingService.resolveManifestGameVersion(
                        manifest.getServerVersion(),
                        validationService.getAllowedGameVersions()
                ),
                manifest.getVersion(),
                versionManifestMatchingService.suggestDependencies(
                        manifest.getDependencies(),
                        versionManifestCandidateQueryService.findPublishedPluginCandidates(project.getId())
                )
        );
    }
}
