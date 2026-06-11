package net.modtale.service.admin;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectReviewQueueService {

    private final MongoTemplate mongoTemplate;

    public ProjectReviewQueueService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Project> getVerificationQueue() {
        Query pendingProjectsQuery = new Query(Criteria.where("status").is(ProjectStatus.PENDING));
        pendingProjectsQuery.fields().exclude("about", "comments", "galleryImages");
        List<Project> pendingProjects = mongoTemplate.find(pendingProjectsQuery, Project.class);

        Query pendingVersionsQuery = new Query(Criteria.where("status").is(ProjectStatus.PUBLISHED).and("versions.reviewStatus").is("PENDING"));
        pendingVersionsQuery.fields().exclude("about", "comments", "galleryImages");
        List<Project> pendingVersions = mongoTemplate.find(pendingVersionsQuery, Project.class);

        Set<Project> combined = new HashSet<>(pendingProjects);
        combined.addAll(pendingVersions);

        List<Project> result = new ArrayList<>(combined.stream()
                .filter(this::hasReviewReadyVersion)
                .toList());

        result.sort(Comparator.comparing(project -> project.getUpdatedAt() == null ? "" : project.getUpdatedAt()));
        return result;
    }

    private boolean hasReviewReadyVersion(Project project) {
        if (project == null) {
            return false;
        }

        List<ProjectVersion> versions = project.getVersions();
        if (versions == null || versions.isEmpty()) {
            return project.getStatus() == ProjectStatus.PENDING;
        }

        boolean hasScanningVersion = versions.stream().anyMatch(version ->
                version != null
                        && version.getScanResult() != null
                        && version.getScanResult().getStatus() == ScanStatus.SCANNING
        );

        if (hasScanningVersion) {
            return false;
        }

        if (project.getStatus() == ProjectStatus.PENDING) {
            return true;
        }

        return versions.stream().anyMatch(version -> {
            if (version == null || version.getReviewStatus() != ProjectVersion.ReviewStatus.PENDING) {
                return false;
            }
            if (version.getScanResult() == null) {
                return true;
            }
            return version.getScanResult().getStatus() != ScanStatus.SCANNING;
        });
    }
}
