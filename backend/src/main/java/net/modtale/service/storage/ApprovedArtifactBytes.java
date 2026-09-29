package net.modtale.service.storage;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;

public final class ApprovedArtifactBytes {

    private ApprovedArtifactBytes() {}

    public static byte[] requireExact(ProjectVersion version, byte[] bytes) throws IOException {
        if (version == null || version.getReviewStatus() != ProjectVersion.ReviewStatus.APPROVED
                || version.getHash() == null || !version.getHash().matches("(?i)[0-9a-f]{64}")
                || bytes == null || bytes.length == 0 || bytes.length > StorageService.MAX_REVIEW_ARTIFACT_BYTES) {
            throw new IOException("The approved artifact cannot be verified for download.");
        }
        ScanResult.SecurityEvidence approvedEvidence = version.getApprovedSecurityEvidence();
        if (approvedEvidence != null && !version.getHash().equalsIgnoreCase(approvedEvidence.artifactSha256())) {
            throw new IOException("The approved artifact identity conflicts with its review evidence.");
        }
        try {
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!version.getHash().equalsIgnoreCase(actual)) {
                throw new IOException("The stored artifact no longer matches the approved bytes.");
            }
            return bytes;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", impossible);
        }
    }
}
