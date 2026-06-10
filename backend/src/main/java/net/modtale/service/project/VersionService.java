package net.modtale.service.project;

import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.exception.VersionNotFoundException;
import net.modtale.model.dto.project.ManifestInspectionResult;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
public class VersionService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ProjectAccessService projectAccessService;
    private final ProjectMutationGuard projectMutationGuard;
    private final ProjectVersionAccessService projectVersionAccessService;
    private final MongoTemplate mongoTemplate;
    private final VersionManifestService versionManifestService;
    private final ProjectDeletionService projectDeletionService;
    private final VersionCreationCommandHandler versionCreationCommandHandler;
    private final VersionUpdateCommandHandler versionUpdateCommandHandler;

    public VersionService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            ProjectAccessService projectAccessService,
            ProjectMutationGuard projectMutationGuard,
            ProjectVersionAccessService projectVersionAccessService,
            MongoTemplate mongoTemplate,
            VersionManifestService versionManifestService,
            ProjectDeletionService projectDeletionService,
            VersionCreationCommandHandler versionCreationCommandHandler,
            VersionUpdateCommandHandler versionUpdateCommandHandler
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.projectAccessService = projectAccessService;
        this.projectMutationGuard = projectMutationGuard;
        this.projectVersionAccessService = projectVersionAccessService;
        this.mongoTemplate = mongoTemplate;
        this.versionManifestService = versionManifestService;
        this.projectDeletionService = projectDeletionService;
        this.versionCreationCommandHandler = versionCreationCommandHandler;
        this.versionUpdateCommandHandler = versionUpdateCommandHandler;
    }

    public ProjectVersion findVersion(Project pack, String versionNumber) {
        if ("latest".equalsIgnoreCase(versionNumber)) {
            return pack.getVersions().isEmpty() ? null : pack.getVersions().getFirst();
        }
        return pack.getVersions().stream()
                .filter(version -> version.getVersionNumber().equalsIgnoreCase(versionNumber))
                .findFirst()
                .orElse(null);
    }

    public ProjectVersion findVersion(Project project, String versionNumber, String gameVersion) {
        return projectVersionAccessService.findByVersionNumber(project, versionNumber, gameVersion);
    }

    public Optional<ProjectVersion> getVersionByHash(String hash) {
        Query query = new Query(Criteria.where("versions.hash").is(hash));
        query.fields().include("versions.$");
        Project project = mongoTemplate.findOne(query, Project.class);
        return project != null && !project.getVersions().isEmpty() ? Optional.of(project.getVersions().get(0)) : Optional.empty();
    }

    public void updateVersion(String id, String versionId, List<String> projectIds, List<String> gameVersions, String changelog, ProjectVersion.Channel channel, User user) {
        versionUpdateCommandHandler.updateVersion(id, versionId, projectIds, gameVersions, changelog, channel, user);
    }

    public void addVersion(
            String id,
            String versionNumber,
            List<String> gameVersions,
            MultipartFile file,
            String changelog,
            List<String> projectIds,
            ProjectVersion.Channel channel,
            User user
    ) {
        versionCreationCommandHandler.addVersion(id, versionNumber, gameVersions, file, changelog, projectIds, channel, user);
    }

    public ManifestInspectionResult inspectManifest(String id, MultipartFile file, User user) {
        Project project = projectAccessService.requireVersionPermission(id, user, "VERSION_CREATE",
                "You do not have permission to inspect versions for this project.");
        return versionManifestService.inspectManifest(project, file);
    }

    public void deleteVersion(String id, String versionId, User user) {
        Project project = projectAccessService.requireVersionPermission(id, user, "VERSION_DELETE",
                "You do not have permission to delete this version.");
        projectMutationGuard.ensureEditable(project);
        if (project.getStatus() != ProjectStatus.DRAFT
                && project.getStatus() != ProjectStatus.PRIVATE
                && project.getVersions().size() <= 1) {
            throw new InvalidVersionRequestException("Published projects must keep at least one version.");
        }

        ProjectVersion version = projectVersionAccessService.requireById(project, versionId,
                () -> new VersionNotFoundException("We couldn't find that project version."));
        projectDeletionService.deleteVersionFile(version);
        project.getVersions().removeIf(existing -> existing.getId().equals(versionId));
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

}
