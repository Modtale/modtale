package net.modtale.service.project.lifecycle;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.admin.review.ProjectReviewPersistence;
import net.modtale.service.admin.review.ProjectReviewSnapshot;
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

    private final ProjectReviewPersistence reviewPersistence;
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
            MongoTemplate mongoTemplate,
            ProjectReviewPersistence reviewPersistence
    ) {
        this.projectRepository = projectRepository;
        this.reviewPersistence = reviewPersistence;
        this.projectService = projectService;
        this.trackingService = trackingService;
        this.scoringService = scoringService;
        this.projectArtifactDeletionService = projectArtifactDeletionService;
        this.mongoTemplate = mongoTemplate;
    }

    public void softDelete(Project project) {
        var snapshot = reviewPersistence.capture(project.getId(), ProjectReviewSnapshot.token(project));
        project = snapshot.project();
        ProjectStatus oldStatus = project.getStatus();
        project.setStatus(ProjectStatus.DELETED);
        project.setDeletedAt(LocalDateTime.now());
        scoringService.markProjectRankingDirty(project);
        if (!reviewPersistence.applyDeletionState(snapshot, false)) throw ProjectReviewSnapshot.conflict();
        projectService.evictProjectCache(project);
        if (oldStatus == ProjectStatus.PUBLISHED || oldStatus == ProjectStatus.UNLISTED || oldStatus == ProjectStatus.ARCHIVED) {
            trackingService.logDeletedProject(project.getId());
        }
    }

    public void restore(Project project, ProjectStatus targetStatus) {
        var snapshot = reviewPersistence.capture(project.getId(), ProjectReviewSnapshot.token(project));
        project = snapshot.project();
        project.setStatus(targetStatus);
        project.setDeletedAt(null);
        scoringService.markProjectRankingDirty(project);
        if (!reviewPersistence.applyDeletionState(snapshot, false)) throw ProjectReviewSnapshot.conflict();
        projectService.evictProjectCache(project);
    }

    public void hardDelete(Project project) {
        var snapshot = reviewPersistence.capture(project.getId(), ProjectReviewSnapshot.token(project));
        project = snapshot.project();
        if (!projectRepository.findByDependency(project.getId()).isEmpty()) {
            var removedMedia = mediaSnapshot(project);
            scrubProjectForDependencyResolution(project);
            if (!reviewPersistence.applyDeletionState(snapshot, true)) throw ProjectReviewSnapshot.conflict();
            projectService.evictProjectCache(project);
            projectArtifactDeletionService.deleteProjectMedia(removedMedia);
            return;
        }

        if (!reviewPersistence.deleteProject(snapshot)) throw ProjectReviewSnapshot.conflict();
        projectService.evictProjectCache(project);
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
        dependencyIds.forEach(this::cleanupOrphanedDependency);
    }

    public void deleteVersionFile(ProjectVersion version) {
        projectArtifactDeletionService.deleteVersionFile(version);
    }

    public void deleteVersionFile(String fileUrl) {
        projectArtifactDeletionService.deleteVersionFile(fileUrl);
    }

    public void deleteProjectMediaFile(Project project, String location) {
        projectArtifactDeletionService.deleteProjectMediaFile(project, location);
    }

    public void deleteStoredFile(String fileUrl) {
        projectArtifactDeletionService.deleteStoredFile(fileUrl);
    }

    private Project mediaSnapshot(Project project) {
        var media = new Project(); media.setId(project.getId());
        media.setImageUrl(project.getImageUrl()); media.setBannerUrl(project.getBannerUrl());
        media.setGalleryImages(project.getGalleryImages() == null ? new ArrayList<>() : new ArrayList<>(project.getGalleryImages()));
        return media;
    }

    private void scrubProjectForDependencyResolution(Project project) {
        project.setTitle("Deleted Project");
        project.setDescription("This project has been deleted.");
        project.setAbout("This project was deleted by the author but is retained for dependency resolution.");
        project.setSlug(null);
        project.setImageUrl(null); project.setBannerUrl(null);
        project.setGalleryImages(new ArrayList<>()); project.setGalleryImageCaptions(new java.util.HashMap<>());
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
