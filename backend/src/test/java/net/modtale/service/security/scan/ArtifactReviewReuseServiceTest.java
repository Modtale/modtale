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
        prior.setHash(result.getSecurityEvidence().artifactSha256());
        prior.setApprovedReviewOrigins(Map.of());
        prior.setSecurityApprovalProjectId("p");
        prior.setSecurityApprovedAt(System.currentTimeMillis() - 1000);
        prior.setApprovedSecurityContextSha256(ArtifactReviewContext.fingerprint(prior));
        ProjectVersion current = new ProjectVersion(); current.setId("new"); current.setVersionNumber("1.1");
        current.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        current.setHash(result.getSecurityEvidence().artifactSha256());
        Project project = new Project(); project.setId("p"); project.setVersions(List.of(prior, current)); return project;
    }
    @Test void freshAdverseEvidenceCannotBeHiddenByAnIdenticalHistoricalApproval() {
        var result=ScanEvidenceFixtures.complete(false);
        var project=project(result);
        var evidence=result.getSecurityEvidence();
        result.setSecurityEvidence(new ScanResult.SecurityEvidence(evidence.policyVersion(),evidence.artifactSha256(),
                evidence.contentSha256(),true,false,"NEW_SECURITY_EVIDENCE",evidence.entryHashes()));
        service.annotate(project,"new",result);
        assertNull(result.getReusedReviewVersion());
        result.setReusedReviewVersion("forged-stale-reuse");
        assertFalse(ArtifactClearancePolicy.cleared(result));
    }
    @Test void recordedDecisionsPreventImplicitReuseUntilExplicitlyEvaluated() {
        for (int index : List.of(0, 1)) {
            var result = ScanEvidenceFixtures.complete(false);
            var project = project(result);
            project.getVersions().get(index).setFindingReviewHead("decision-history");
            service.annotate(project, "new", result);
            assertNull(result.getReusedReviewVersion());
        }
    }
    @Test void unchangedFullyInspectedArtifactReusesReview() {
        ScanResult result = ScanEvidenceFixtures.complete(false);
        Project project = project(result);
        assertTrue(service.hasPotentialExactSource(project, "new", result.getSecurityEvidence().policyVersion()));
        service.annotate(project, "new", result);
        assertEquals("1.0", result.getReusedReviewVersion());
        assertEquals(project.getVersions().getFirst().getSecurityApprovedAt(), result.getReusedReviewApprovedAt());
    }
    @Test void preflightHintExcludesChangedOrAdverseApprovalScope() {
        for (String scenario : List.of("bytes", "policy", "context", "history", "block", "expired", "missing")) {
            var result = ScanEvidenceFixtures.complete(false);
            var project = project(result);
            var prior = project.getVersions().getFirst();
            var target = project.getVersions().get(1);
            String policy = result.getSecurityEvidence().policyVersion();
            switch (scenario) {
                case "bytes" -> target.setHash("f".repeat(64));
                case "policy" -> policy = "warden-3.0.0:" + "e".repeat(64);
                case "context" -> target.setGameVersions(List.of("changed-runtime"));
                case "history" -> prior.setFindingReviewHead("later-adverse-history");
                case "block" -> target.setReplacementSecurityHold("retained-block");
                case "expired" -> prior.setSecurityApprovedAt(System.currentTimeMillis() - Duration.ofDays(31).toMillis());
                case "missing" -> prior.setApprovedSecurityEvidence(null);
            }
            assertFalse(service.hasPotentialExactSource(project, "new", policy), scenario);
        }
    }
    @Test void equalLogicalEntriesCannotCarryApprovalAcrossDifferentArchiveBytes() {
        ScanResult result = ScanEvidenceFixtures.complete(false);
        Project project = project(result);
        var prior = result.getSecurityEvidence();
        String changedArchive = "f".repeat(64);
        assertNotEquals(prior.artifactSha256(), changedArchive);
        result.setSecurityEvidence(new ScanResult.SecurityEvidence(prior.policyVersion(), changedArchive,
                prior.contentSha256(), prior.complete(), prior.clearanceGranted(), prior.reviewState(), prior.entryHashes()));
        project.getVersions().get(1).setHash(changedArchive);

        service.annotate(project, "new", result);
        assertNull(result.getReusedReviewVersion());
        assertNull(result.getReusedReviewOrigins());
    }
    @Test void targetArtifactIdentityMustMatchTheCurrentScan() {
        ScanResult result = ScanEvidenceFixtures.complete(false);
        Project project = project(result);
        project.getVersions().get(1).setHash("f".repeat(64));
        service.annotate(project, "new", result);
        assertNull(result.getReusedReviewVersion());
    }
    @Test void retainedReplacementBlockPreventsReuseAsSourceOrTarget() {
        for(int index:List.of(0,1)) {
            var result=ScanEvidenceFixtures.complete(false);var project=project(result);
            project.getVersions().get(index).setReplacementSecurityHold(UUID.randomUUID().toString());
            service.annotate(project,"new",result);assertNull(result.getReusedReviewVersion());
        }
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
    @Test void changedContextOrSupplementalContentCannotReuse() {
        for (String scenario : List.of("games", "dependency", "override", "missing-snapshot", "edited-prior")) {
            ScanResult result = ScanEvidenceFixtures.complete(false);
            Project project = project(result);
            ProjectVersion current = project.getVersions().get(1);
            switch (scenario) {
                case "games" -> current.setGameVersions(List.of("changed-runtime"));
                case "dependency" -> current.setDependencies(List.of(new ProjectDependency("dependency", "Dependency", "1.0")));
                case "override" -> current.setOverrideFileUrl("storage/supplement.zip");
                case "missing-snapshot" -> project.getVersions().getFirst().setApprovedSecurityContextSha256(null);
                case "edited-prior" -> project.getVersions().getFirst().setGameVersions(List.of("edited-after-approval"));
            }
            service.annotate(project, "new", result);
            assertNull(result.getReusedReviewVersion(), scenario);
        }
    }
    @Test void compactContentDigestIsSufficientWithoutRetainingEveryPriorEntry() {
        ScanResult result = ScanEvidenceFixtures.complete(false);
        Project project = project(result);
        var evidence = result.getSecurityEvidence();
        project.getVersions().getFirst().setApprovedSecurityEvidence(new ScanResult.SecurityEvidence(
                evidence.policyVersion(), evidence.artifactSha256(), evidence.contentSha256(), true, false, "COMPLETED", Map.of()));
        service.annotate(project, "new", result);
        assertEquals("1.0", result.getReusedReviewVersion());
    }
    @Test void v2ApprovalDoesNotSupplyV3Evidence() {
        ScanResult result = ScanEvidenceFixtures.complete(false);
        Project project = project(result);
        project.getVersions().getFirst().setApprovedSecurityEvidence(null);
        service.annotate(project, "new", result);
        assertNull(result.getReusedReviewVersion());
    }
}
