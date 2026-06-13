package net.modtale.service.project.lifecycle;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.project.query.ProjectService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class ProjectDeletionService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final TrackingService trackingService;
    private final ScoringService scoringService;
    private final ProjectArtifactDeletionService projectArtifactDeletionService;
    private final MongoTemplate mongoTemplate;

    public ProjectDeletionService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            TrackingService trackingService,
            ScoringService scoringService,
            ProjectArtifactDeletionService projectArtifactDeletionService,
            MongoTemplate mongoTemplate
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.trackingService = trackingService;
        this.scoringService = scoringService;
        this.projectArtifactDeletionService = projectArtifactDeletionService;
        this.mongoTemplate = mongoTemplate;
    }

    public void softDelete(Project project) {
        ProjectStatus oldStatus = project.getStatus();
        project.setStatus(ProjectStatus.DELETED);
        project.setDeletedAt(LocalDateTime.now());
        scoringService.markProjectRankingDirty(project);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
        if (oldStatus == ProjectStatus.PUBLISHED || oldStatus == ProjectStatus.UNLISTED || oldStatus == ProjectStatus.ARCHIVED) {
            trackingService.logDeletedProject(project.getId());
        }
    }

    public void restore(Project project, ProjectStatus targetStatus) {
        project.setStatus(targetStatus);
        project.setDeletedAt(null);
        scoringService.markProjectRankingDirty(project);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void hardDelete(Project project) {
        if (!projectRepository.findByDependency(project.getId()).isEmpty()) {
            scrubProjectForDependencyResolution(project);
            projectRepository.save(project);
            projectService.evictProjectCache(project);
            return;
        }

        trackingService.deleteProjectAnalytics(project.getId());
        Set<String> dependencyIds = new HashSet<>();
        if (project.getVersions() != null) {
            project.getVersions().forEach(version -> {
                projectArtifactDeletionService.deleteVersionFile(version);
                if (version.getDependencies() != null) {
                    version.getDependencies().forEach(dependency -> {
                        if (!dependency.isExternal()) {
                            dependencyIds.add(dependency.getProjectId());
                        }
                    });
                }
            });
        }
        if (project.getChildProjectIds() != null) {
            dependencyIds.addAll(project.getChildProjectIds());
        }

        projectArtifactDeletionService.deleteProjectMedia(project);

        mongoTemplate.updateMulti(new Query(Criteria.where("likedModIds").is(project.getId())), new Update().pull("likedModIds", project.getId()), net.modtale.model.user.User.class);
        scoringService.markProjectRankingDirty(project.getId());
        projectRepository.delete(project);
        projectService.evictProjectCache(project);
        dependencyIds.forEach(this::cleanupOrphanedDependency);
    }

    public void deleteVersionFile(ProjectVersion version) {
        projectArtifactDeletionService.deleteVersionFile(version);
    }

    public void deleteVersionFile(String fileUrl) {
        projectArtifactDeletionService.deleteVersionFile(fileUrl);
    }

    public void deleteStoredFile(String fileUrl) {
        projectArtifactDeletionService.deleteStoredFile(fileUrl);
    }

    private void scrubProjectForDependencyResolution(Project project) {
        project.setTitle("Deleted Project");
        project.setDescription("This project has been deleted.");
        project.setAbout("This project was deleted by the author but is retained for dependency resolution.");
        project.setSlug(null);
        projectArtifactDeletionService.deleteProjectMedia(project);
        project.setTeamMembers(new ArrayList<>());
        project.setTeamInvites(new ArrayList<>());
        project.setProjectRoles(new ArrayList<>());
        project.setComments(new ArrayList<>());
        project.setTags(new ArrayList<>());
        project.setDeletedAt(null);
    }

    private void cleanupOrphanedDependency(String id) {
        Project project = projectService.getRawProjectById(id);
        if (project != null && project.getStatus() == ProjectStatus.DELETED && projectRepository.findByDependency(id).isEmpty()) {
            hardDelete(project);
        }
    }
}
