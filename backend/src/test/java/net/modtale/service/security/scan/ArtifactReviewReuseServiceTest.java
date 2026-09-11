package net.modtale.service.security.scan;

import net.modtale.model.project.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactReviewReuseServiceTest {
    private final ArtifactReviewReuseService service = new ArtifactReviewReuseService();
    private Project project(ScanResult result) {
        ProjectVersion prior = new ProjectVersion();
        prior.setId("prior"); prior.setVersionNumber("1.0");
        prior.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        prior.setApprovedSecurityEvidence(result.getSecurityEvidence());
        prior.setSecurityApprovedAt(System.currentTimeMillis() - 1000);
        Project project = new Project(); project.setVersions(List.of(prior)); return project;
    }
    @Test void unchangedFullyInspectedArtifactReusesReview() {
        ScanResult result = ScanEvidenceFixtures.complete(false);
        Project project = project(result);
        service.annotate(project, "new", result);
        assertEquals("1.0", result.getReusedReviewVersion());
        assertEquals(project.getVersions().getFirst().getSecurityApprovedAt(), result.getReusedReviewApprovedAt());
    }
    @Test void staleRejectedUnverifiedOrChangedArtifactsCannotReuse() {
        for (String scenario : List.of("stale", "rejected", "unverified", "changed", "policy", "blocked", "incomplete")) {
            ScanResult result = ScanEvidenceFixtures.complete(false);
            Project project = project(result);
            ProjectVersion prior = project.getVersions().getFirst();
            var evidence = result.getSecurityEvidence();
            switch (scenario) {
                case "stale" -> prior.setSecurityApprovedAt(System.currentTimeMillis() - Duration.ofDays(31).toMillis());
                case "rejected" -> prior.setReviewStatus(ProjectVersion.ReviewStatus.REJECTED);
                case "unverified" -> result.setArtifactVerified(false);
                case "changed" -> prior.setApprovedSecurityEvidence(new ScanResult.SecurityEvidence(evidence.policyVersion(), evidence.artifactSha256(), "c".repeat(64), true, false, "COMPLETED", Map.of("Mod.class", "d".repeat(64))));
                case "policy" -> prior.setApprovedSecurityEvidence(new ScanResult.SecurityEvidence("older", evidence.artifactSha256(), evidence.contentSha256(), true, false, "COMPLETED", evidence.entryHashes()));
                case "blocked" -> result.setVerdict("BLOCK");
                case "incomplete" -> result.getSummary().setRecoverableErrors(1);
            }
            service.annotate(project, "new", result);
            assertNull(result.getReusedReviewVersion(), scenario);
        }
    }
    @Test void v2ApprovalDoesNotSupplyV3Evidence() {
        ScanResult result = ScanEvidenceFixtures.complete(false);
        Project project = project(result);
        project.getVersions().getFirst().setApprovedSecurityEvidence(null);
        service.annotate(project, "new", result);
        assertNull(result.getReusedReviewVersion());
    }
}
