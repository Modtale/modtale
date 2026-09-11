package net.modtale.service.project.lifecycle;

import java.time.LocalDateTime;
import java.util.*;
import net.modtale.model.project.*;
import net.modtale.service.communication.ProjectNotificationService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.issue.SecurityIssueAnalysisService;
import net.modtale.service.security.scan.ArtifactClearancePolicy;
import net.modtale.service.security.scan.ArtifactReviewContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;

@Service
public class ScheduledReleaseExecutionService {
    private final MongoTemplate mongo;
    private final ProjectService projectService;
    private final ProjectNotificationService notifications;
    private final SecurityIssueAnalysisService issueAnalysis;

    public ScheduledReleaseExecutionService(MongoTemplate mongo, ProjectService projectService,
            ProjectNotificationService notifications, SecurityIssueAnalysisService issueAnalysis) {
        this.mongo = mongo; this.projectService = projectService;
        this.notifications = notifications; this.issueAnalysis = issueAnalysis;
    }
    public List<String> publishDueVersions(Project project, LocalDateTime publishTime) {
        List<String> released = new ArrayList<>();
        if (project.getVersions() == null) return released;
        for (ProjectVersion version : project.getVersions()) {
            if (!due(version, publishTime)) continue;
            ScanResult scan = version.getScanResult();
            Criteria versionMatch = Criteria.where("_id").is(version.getId())
                    .and("reviewStatus").is(ProjectVersion.ReviewStatus.SCHEDULED)
                    .and("scheduledPublishDate").is(version.getScheduledPublishDate())
                    .and("hash").is(version.getHash())
                    .and("scanResult.scanAttempt").is(scan == null ? null : scan.getScanAttempt());
            ArtifactReviewContext.bindSnapshot(versionMatch, version);
            long now = System.currentTimeMillis();
            boolean valid = ArtifactClearancePolicy.boundToVersion(version)
                    && scan.getScanTimestamp() > 0
                    && scan.getScanTimestamp() <= now
                    && now - scan.getScanTimestamp() < java.time.Duration.ofDays(30).toMillis();
            Update update = new Update().set("versions.$.scheduledPublishDate", null)
                    .set("updatedAt", publishTime.toString());
            if (valid) {
                versionMatch.and("scanResult.securityEvidence.artifactSha256").is(scan.getSecurityEvidence().artifactSha256())
                        .and("scanResult.securityEvidence.contentSha256").is(scan.getSecurityEvidence().contentSha256())
                        .and("scanResult.reviewedContextSha256").is(scan.getReviewedContextSha256())
                        .and("scanResult.securityEvidence.policyVersion").is(scan.getSecurityEvidence().policyVersion())
                        .and("scanResult.scanTimestamp").is(scan.getScanTimestamp())
                        .and("scanResult.verdict").is(scan.getVerdict())
                        .and("scanResult.status").is(scan.getStatus());
                issueAnalysis.markIssuesAcceptedForApprovedVersion(version);
                update.set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.APPROVED)
                        .set("versions.$.approvedSecurityEvidence", version.getApprovedSecurityEvidence())
                        .set("versions.$.approvedSecurityContextSha256", version.getApprovedSecurityContextSha256())
                        .set("versions.$.securityApprovedAt", version.getSecurityApprovedAt())
                        .set("versions.$.approvedIssueBaselines", version.getApprovedIssueBaselines())
                        .set("versions.$.scanResult", null);
            } else {
                update.set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING);
            }
            Query query = new Query(Criteria.where("_id").is(project.getId()).and("versions").elemMatch(versionMatch));
            if (mongo.updateFirst(query, update, Project.class).getModifiedCount() == 0) continue;
            projectService.evictProjectCache(project);
            if (valid) {
                released.add(version.getVersionNumber());
                notifications.notifyUpdates(project, version.getVersionNumber());
                notifications.notifyDependents(project, version.getVersionNumber());
            }
        }
        return released;
    }
    private boolean due(ProjectVersion version, LocalDateTime time) {
        if (version == null || version.getReviewStatus() != ProjectVersion.ReviewStatus.SCHEDULED || version.getScheduledPublishDate() == null) return false;
        try { return !LocalDateTime.parse(version.getScheduledPublishDate()).isAfter(time); }
        catch (java.time.format.DateTimeParseException ignored) { return true; }
    }
}
