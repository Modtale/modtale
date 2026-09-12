package net.modtale.service.security.scan;

import net.modtale.model.project.*;
import java.util.*;

public final class ArtifactClearancePolicy {
    private ArtifactClearancePolicy() {}
    public static boolean complete(ScanResult result) {
        if (result == null || !result.isArtifactVerified() || !"COMPLETED".equals(result.getScanState())) return false;
        var summary = result.getSummary();
        var evidence = result.getSecurityEvidence();
        if (summary == null || summary.getRecoverableErrors() != 0 || summary.getOversizedEntriesSkipped() != 0
                || summary.getNestedArchiveReadFailures() != 0 || summary.getFilesScanned() <= 0
                || evidence == null || !evidence.complete() || (evidence.policyVersion() == null || !evidence.policyVersion().matches("warden-3\\.0\\.0:[0-9a-f]{64}"))
                || !digest(evidence.artifactSha256()) || !digest(evidence.contentSha256())
                || !SecurityManifest.valid(evidence.entryHashes(), false)) return false;
        return SecurityManifest.identity(evidence.entryHashes()).equals(evidence.contentSha256());
    }

    public static boolean cleared(ScanResult result) {
        if (!complete(result) || "NEW_SECURITY_EVIDENCE".equals(result.getSecurityEvidence().reviewState()) || "BLOCK".equals(result.getVerdict()) || result.getStatus() == ScanStatus.INFECTED) return false;
        return ("AUTO_APPROVE".equals(result.getVerdict()) && result.getStatus() == ScanStatus.CLEAN && result.getSecurityEvidence().clearanceGranted())
                || result.getReusedReviewVersion() != null;
    }
    public static boolean boundToVersion(ProjectVersion version) {
        if (version == null || version.getFindingReviewHead() != null || !cleared(version.getScanResult())) return false;
        String context = ArtifactReviewContext.automaticallyReviewableFingerprint(version);
        return context != null && context.equals(version.getScanResult().getReviewedContextSha256())
                && Objects.equals(version.getHash(), version.getScanResult().getSecurityEvidence().artifactSha256());
    }
    private static boolean digest(String value) { return SecurityManifest.digest(value); }
}
