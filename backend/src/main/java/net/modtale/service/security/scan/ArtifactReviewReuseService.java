package net.modtale.service.security.scan;

import net.modtale.model.project.*;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.*;

@Service
public class ArtifactReviewReuseService {
    private static final long MAX_AGE_MS = Duration.ofDays(30).toMillis();
    public void annotate(Project project, String currentVersionId, ScanResult result) {
        result.setReusedReviewVersion(null);
        result.setReusedReviewApprovedAt(0);
        var current = result.getSecurityEvidence();
        if (!ArtifactClearancePolicy.complete(result) || current == null || !current.complete()
                || "NEW_SECURITY_EVIDENCE".equals(current.reviewState())
                || "BLOCK".equals(result.getVerdict()) || result.getStatus() == ScanStatus.INFECTED
                || project == null || project.getVersions() == null) return;
        ProjectVersion target = project.getVersions().stream().filter(Objects::nonNull)
                .filter(version -> Objects.equals(currentVersionId, version.getId())).findFirst().orElse(null);
        String context = ArtifactReviewContext.fingerprint(target);
        if (context == null || target.getFindingReviewHead() != null) return;
        long now = System.currentTimeMillis();
        for (ProjectVersion version : project.getVersions()) {
            if (version == null || version.getFindingReviewHead() != null || Objects.equals(version.getId(), currentVersionId)
                    || version.getReviewStatus() != ProjectVersion.ReviewStatus.APPROVED
                    || version.getSecurityApprovedAt() <= 0 || version.getSecurityApprovedAt() > now
                    || now - version.getSecurityApprovedAt() > MAX_AGE_MS) continue;
            var prior = version.getApprovedSecurityEvidence();
            if (prior == null || !prior.complete() || current.policyVersion() == null
                    || !current.policyVersion().equals(prior.policyVersion())
                    || current.contentSha256() == null || !current.contentSha256().equals(prior.contentSha256())
                    || current.entryHashes() == null || current.entryHashes().isEmpty()
                    || !context.equals(version.getApprovedSecurityContextSha256())
                    || !context.equals(ArtifactReviewContext.fingerprint(version))) continue;
            result.setReusedReviewVersion(version.getVersionNumber());
            result.setReusedReviewApprovedAt(version.getSecurityApprovedAt());
            for (var issue : result.getIssues()) {
                if (issue == null) continue;
                issue.setKnownIssue(true);
                issue.setEscalated(false);
                issue.setResolved(true);
                issue.setBaselineVersion(version.getVersionNumber());
            }
            result.setKnownIssueCount(result.getIssues().size());
            result.setNewIssueCount(0);
            result.setEscalatedIssueCount(0);
            return;
        }
    }
}
