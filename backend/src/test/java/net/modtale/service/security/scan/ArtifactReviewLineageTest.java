package net.modtale.service.security.scan;

import java.util.*;
import net.modtale.model.project.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactReviewLineageTest {
    private ProjectVersion source(String id) {
        var v = new ProjectVersion(); v.setId(id); v.setVersionNumber(id);
        v.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED); v.setSecurityApprovalProjectId("p");
        var evidence = ScanEvidenceFixtures.complete(false).getSecurityEvidence();
        v.setHash(evidence.artifactSha256()); v.setApprovedSecurityEvidence(evidence);
        v.setApprovedSecurityContextSha256(ArtifactReviewContext.fingerprint(v));
        v.setSecurityApprovedAt(System.currentTimeMillis() - 1000); v.setApprovedReviewOrigins(Map.of()); return v;
    }
    @Test void copiedApprovalRetainsEveryOriginalSource() {
        var root = source("root"); var copy = source("copy"); var project = new Project(); project.setId("p"); project.setVersions(List.of(root, copy));
        copy.setApprovedReviewOrigins(ArtifactReviewLineage.extend(project, root));
        var lineage = ArtifactReviewLineage.extend(project, copy);
        assertEquals(Set.of("root", "copy"), lineage.keySet()); assertTrue(ArtifactReviewLineage.valid(project, lineage));
        root.setReviewStatus(ProjectVersion.ReviewStatus.REJECTED);
        assertFalse(ArtifactReviewLineage.valid(project, lineage)); assertNull(ArtifactReviewLineage.extend(project, copy));
    }
    @Test void removingAnAncestorCannotLaunderAStoredProof() {
        var root = source("root"); var copy = source("copy"); var project = new Project(); project.setId("p"); project.setVersions(List.of(root, copy));
        copy.setApprovedReviewOrigins(ArtifactReviewLineage.extend(project, root));
        var lineage = ArtifactReviewLineage.extend(project, copy);
        assertFalse(ArtifactReviewLineage.valid(project, Map.of("copy", lineage.get("copy"))));
        copy.setApprovedReviewOrigins(Map.of());
        assertFalse(ArtifactReviewLineage.valid(project, lineage));
    }
    @Test void changedRevokedExpiredOrLegacySourceCannotBeReused() {
        for (String change : List.of("hash", "context", "decision", "expiry", "legacy", "missing")) {
            var root = source("root"); var project = new Project(); project.setId("p"); project.setVersions(List.of(root));
            var lineage = ArtifactReviewLineage.extend(project, root);
            switch (change) {
                case "hash" -> root.setHash("f".repeat(64));
                case "context" -> root.setGameVersions(List.of("changed"));
                case "decision" -> root.setFindingReviewHead("revocation");
                case "expiry" -> root.setSecurityApprovedAt(System.currentTimeMillis() - 31L * 86400000);
                case "legacy" -> root.setApprovedReviewOrigins(null);
                case "missing" -> project.setVersions(List.of());
            }
            assertFalse(ArtifactReviewLineage.valid(project, lineage), change);
        }
    }
    @Test void copyingVersionsToAnotherProjectDoesNotCopyApprovalAuthority() {
        var root = source("root"); var project = new Project(); project.setId("p"); project.setVersions(List.of(root));
        var origins = ArtifactReviewLineage.extend(project, root);
        project.setId("copied-project");
        assertFalse(ArtifactReviewLineage.valid(project, origins));
        assertNull(ArtifactReviewLineage.extend(project, root));
    }
    @Test void networkPayloadCannotInjectOriginsAndSnapshotsBindServerOrigins() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var result=json.readValue("{\"reusedReviewOrigins\":{\"source\":\""+"a".repeat(64)+"\"}}",ScanResult.class);
        assertNull(result.getReusedReviewOrigins());
        var current=new ProjectVersion();current.setScanResult(result);
        String before=net.modtale.service.admin.review.VersionReviewSnapshot.token(current);
        result.setReusedReviewOrigins(Map.of("source","a".repeat(64)));
        assertNotEquals(before,net.modtale.service.admin.review.VersionReviewSnapshot.token(current));
        assertFalse(json.writeValueAsString(result).contains("reusedReviewOrigins"));
    }
    @Test void lineageIsBoundedAndRejectsUnsafeStorageKeys() {
        var entries=new HashMap<String,String>();
        for(int i=0;i<32;i++) entries.put("version-"+i,"a".repeat(64));
        assertTrue(ArtifactReviewLineage.wellFormed(entries));
        entries.put("overflow","a".repeat(64)); assertFalse(ArtifactReviewLineage.wellFormed(entries));
        for(String id:List.of("version.with.dot","$operator","", "a".repeat(129)))
            assertFalse(ArtifactReviewLineage.wellFormed(Map.of(id,"a".repeat(64))));
        assertFalse(ArtifactReviewLineage.wellFormed(null));
    }
    @Test void freshIndependentClearanceDoesNotAcquireHistoricalDependencies() {
        var root = source("root"); var current = new ProjectVersion(); current.setId("current");
        var project = new Project(); project.setId("p"); project.setVersions(List.of(root,current));
        var result = ScanEvidenceFixtures.complete(true);
        new ArtifactReviewReuseService().annotate(project, "current", result);
        assertNull(result.getReusedReviewVersion()); assertNull(result.getReusedReviewOrigins());
        assertTrue(ArtifactClearancePolicy.cleared(result));
    }
}
