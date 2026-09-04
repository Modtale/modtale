package net.modtale.service.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.project.query.ProjectService;
import org.springframework.web.multipart.MultipartFile;

final class DownloadArchiveSupport {

    private final ProjectService projectService;
    private final StorageService storageService;

    DownloadArchiveSupport(ProjectService projectService, StorageService storageService) {
        this.projectService = projectService;
        this.storageService = storageService;
    }

    ResolvedDependency resolveDependency(ProjectDependency dependency) {
        if (dependency == null || dependency.isExternal()) {
            return null;
        }

        Project project = projectService.getRawProjectById(dependency.getProjectId());
        if (project == null) {
            return null;
        }

        ProjectVersion version = findVersion(project, dependency.getVersionNumber());
        if (version == null) {
            return null;
        }

        return new ResolvedDependency(project, version);
    }

    byte[] download(String fileUrl) {
        return storageService.download(fileUrl);
    }

    String upload(MultipartFile file, String directory) {
        return storageService.upload(file, directory);
    }

    MultipartFile newZipMultipartFile(String name, byte[] content) {
        return new InMemoryMultipartFile(name, content);
    }

    String extractOriginalFilename(String fileUrl) {
        String originalFilename = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
        if (originalFilename.length() > 37 && originalFilename.charAt(36) == '-') {
            return originalFilename.substring(37);
        }
        return originalFilename;
    }

    private ProjectVersion findVersion(Project project, String versionNumber) {
        if (project.getVersions() == null) {
            return null;
        }
        return project.getVersions().stream()
                .filter(candidate -> candidate.getVersionNumber().equals(versionNumber))
                .findFirst()
                .orElse(null);
    }

    record ResolvedDependency(Project project, ProjectVersion version) {}

    private static class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final byte[] content;

        private InMemoryMultipartFile(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return name;
        }

        @Override
        public String getContentType() {
            return "application/zip";
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            org.springframework.util.FileCopyUtils.copy(content, dest);
        }
    }
}
