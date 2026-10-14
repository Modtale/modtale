package net.modtale.service.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import org.junit.jupiter.api.Test;

class ApprovedArtifactBytesTest {

    @Test
    void rejectsStoredBytesThatChangedAfterApproval() throws Exception {
        byte[] approved = "approved artifact".getBytes(StandardCharsets.UTF_8);
        ProjectVersion version = version(approved);

        assertArrayEquals(approved, ApprovedArtifactBytes.requireExact(version, approved));
        assertThrows(IOException.class, () -> ApprovedArtifactBytes.requireExact(version,
                "replaced artifact".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void refusesMissingOrConflictingApprovalIdentity() throws Exception {
        byte[] approved = "approved artifact".getBytes(StandardCharsets.UTF_8);
        ProjectVersion version = version(approved);
        version.setHash(null);
        assertThrows(IOException.class, () -> ApprovedArtifactBytes.requireExact(version, approved));
        version.setHash(sha256(approved));
        version.setApprovedSecurityEvidence(new ScanResult.SecurityEvidence("v3", sha256("other".getBytes(StandardCharsets.UTF_8)),
                sha256(approved), true, true, "CLEAN", null));
        assertThrows(IOException.class, () -> ApprovedArtifactBytes.requireExact(version, approved));
    }

    private static ProjectVersion version(byte[] bytes) throws Exception {
        ProjectVersion version = new ProjectVersion();
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        version.setHash(sha256(bytes));
        return version;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
