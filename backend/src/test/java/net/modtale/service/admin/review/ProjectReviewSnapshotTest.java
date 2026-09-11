package net.modtale.service.admin.review;

import net.modtale.model.project.*;
import net.modtale.service.security.scan.ScanEvidenceFixtures;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProjectReviewSnapshotTest {
    @Test void coversMetadataAndInternalArtifactVerificationWithoutChurningOnPopularity() {
        var project = new Project(); project.setId("project"); project.setTitle("reviewed");
        var version = new ProjectVersion(); version.setId("version");
        version.setScanResult(ScanEvidenceFixtures.complete(true)); project.setVersions(List.of(version));
        String token = ProjectReviewSnapshot.token(project);
        project.setDownloadCount(42); project.setFavoriteCount(7); project.setRankingDirty(true);
        assertEquals(token, ProjectReviewSnapshot.token(project));
        project.setTitle("changed"); assertNotEquals(token, ProjectReviewSnapshot.token(project));
        project.setTitle("reviewed"); version.getScanResult().setArtifactVerified(false);
        assertNotEquals(token, ProjectReviewSnapshot.token(project));
    }
}
