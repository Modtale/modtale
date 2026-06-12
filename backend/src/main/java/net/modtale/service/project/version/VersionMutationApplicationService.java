package net.modtale.service.project.version;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.modtale.model.dto.project.ManifestInspectionResult;
import net.modtale.model.dto.request.project.DependencyReferenceRequest;
import net.modtale.model.dto.request.project.CreateVersionRequest;
import net.modtale.model.dto.request.project.UpdateVersionRequest;
import net.modtale.model.user.User;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VersionMutationApplicationService {

    private final VersionService versionService;

    public VersionMutationApplicationService(VersionService versionService) {
        this.versionService = versionService;
    }

    public void addVersion(String projectId, CreateVersionRequest requestPayload, User currentUser) {
        versionService.addVersion(
                projectId,
                requestPayload.getVersionNumber(),
                requestPayload.getGameVersions(),
                requestPayload.getFile(),
                requestPayload.getChangelog(),
                normalizeDependencies(requestPayload.getDependencies()),
                normalizeProjectIds(requestPayload.getIncompatibleProjectIds()),
                requestPayload.getChannel(),
                currentUser
        );
    }

    public ManifestInspectionResult inspectManifest(String projectId, MultipartFile file, User currentUser) {
        return versionService.inspectManifest(projectId, file, currentUser);
    }

    public void updateVersion(String projectId, String versionId, UpdateVersionRequest requestPayload, User currentUser) {
        versionService.updateVersion(
                projectId,
                versionId,
                normalizeDependencies(requestPayload.getDependencies()),
                normalizeProjectIds(requestPayload.getIncompatibleProjectIds()),
                requestPayload.getGameVersions(),
                requestPayload.getChangelog(),
                requestPayload.getChannel(),
                currentUser
        );
    }

    public void deleteVersion(String projectId, String versionId, User currentUser) {
        versionService.deleteVersion(projectId, versionId, currentUser);
    }

    private List<DependencyReferenceRequest> normalizeDependencies(List<DependencyReferenceRequest> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return dependencies;
        }
        return dependencies.stream()
                .filter(dependency -> dependency != null)
                .collect(Collectors.toList());
    }

    private List<String> normalizeProjectIds(List<String> rawEntries) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return rawEntries;
        }
        return rawEntries.stream()
                .flatMap(entry -> Arrays.stream(entry.split(",")))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toList());
    }

}
