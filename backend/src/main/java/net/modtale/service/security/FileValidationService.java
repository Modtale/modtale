package net.modtale.service.security;

import net.modtale.exception.InvalidProjectRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class FileValidationService {

    private static final List<String> MUTABLE_CLASSIFICATIONS = Arrays.asList("PLUGIN", "DATA", "ART");

    private final ProjectArchiveValidationService projectArchiveValidationService;
    private final ProjectImageValidationService projectImageValidationService;

    public FileValidationService(
            ProjectArchiveValidationService projectArchiveValidationService,
            ProjectImageValidationService projectImageValidationService
    ) {
        this.projectArchiveValidationService = projectArchiveValidationService;
        this.projectImageValidationService = projectImageValidationService;
    }

    public ManifestInspection validateProjectFile(MultipartFile file, String classification) {
        if (file == null || file.isEmpty()) {
            throw new InvalidProjectRequestException("A project file is required.");
        }

        String effectiveClassification = resolveUploadClassification(classification, file);
        return projectArchiveValidationService.validateProjectArchive(file, effectiveClassification);
    }

    public String resolveUploadClassification(String classification, MultipartFile file) {
        if (classification == null) {
            return null;
        }
        if (file == null || file.isEmpty()) {
            return classification;
        }

        if ("MODPACK".equals(classification) || "SAVE".equals(classification)) {
            return classification;
        }
        if (!MUTABLE_CLASSIFICATIONS.contains(classification)) {
            return classification;
        }

        String name = file.getOriginalFilename();
        if (name == null) {
            return classification;
        }
        String lowerName = name.toLowerCase();

        if (lowerName.endsWith(".jar")) {
            return "PLUGIN";
        }
        if (lowerName.endsWith(".zip") && "PLUGIN".equals(classification)) {
            return "DATA";
        }
        return classification;
    }

    public void validateIcon(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        projectImageValidationService.validateImage(file, 1.0, "Icon", "1:1");
    }

    public void validateBanner(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        projectImageValidationService.validateImage(file, 3.0, "Banner", "3:1");
    }

    public void validateGalleryImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        projectImageValidationService.validateImage(file, 16.0 / 9.0, "Gallery", "16:9");
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
}
