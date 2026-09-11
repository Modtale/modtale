package net.modtale.service.admin.review;

import net.modtale.model.project.*;
import net.modtale.service.security.scan.ScanEvidenceFixtures;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VersionReviewSnapshotTest {
    @Test void tokenTracksArtifactMetadataAndScanButIgnoresDownloadCounters() {
        var version=new ProjectVersion();version.setId("version");version.setHash("a".repeat(64));
        version.setScanResult(ScanEvidenceFixtures.complete(true));
        String original=VersionReviewSnapshot.token(version);
        version.setDownloadCount(50);assertEquals(original,VersionReviewSnapshot.token(version));
        version.setOverrideFileUrl("other.zip");assertNotEquals(original,VersionReviewSnapshot.token(version));
        version.setOverrideFileUrl(null);version.getScanResult().setScanAttempt(2);assertNotEquals(original,VersionReviewSnapshot.token(version));
        version.getScanResult().setScanAttempt(0);version.setHash("b".repeat(64));assertNotEquals(original,VersionReviewSnapshot.token(version));
    }
}
