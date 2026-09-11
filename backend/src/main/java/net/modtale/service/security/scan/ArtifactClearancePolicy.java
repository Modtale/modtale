package net.modtale.service.security.scan;

import net.modtale.model.project.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class ArtifactClearancePolicy {
    private ArtifactClearancePolicy() {}
    public static boolean complete(ScanResult result) {
        if (result == null || !result.isArtifactVerified() || !"COMPLETED".equals(result.getScanState())) return false;
        var summary = result.getSummary();
        var evidence = result.getSecurityEvidence();
        if (summary == null || summary.getRecoverableErrors() != 0 || summary.getOversizedEntriesSkipped() != 0
                || summary.getNestedArchiveReadFailures() != 0 || summary.getFilesScanned() <= 0
                || evidence == null || !evidence.complete() || !"warden-3.0.0".equals(evidence.policyVersion())
                || !digest(evidence.artifactSha256()) || !digest(evidence.contentSha256())
                || evidence.entryHashes() == null || evidence.entryHashes().isEmpty() || evidence.entryHashes().size() > 20_000) return false;
        StringBuilder canonical = new StringBuilder();
        for (var entry : new TreeMap<>(evidence.entryHashes()).entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || !digest(entry.getValue())) return false;
            canonical.append(entry.getKey().length()).append(':').append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
        }
        try {
            String content = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
            return content.equals(evidence.contentSha256());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static boolean cleared(ScanResult result) {
        if (!complete(result) || "BLOCK".equals(result.getVerdict()) || result.getStatus() == ScanStatus.INFECTED) return false;
        return ("AUTO_APPROVE".equals(result.getVerdict()) && result.getStatus() == ScanStatus.CLEAN && result.getSecurityEvidence().clearanceGranted())
                || result.getReusedReviewVersion() != null;
    }
    public static boolean boundToVersion(ProjectVersion version) {
        if (version == null || !cleared(version.getScanResult())) return false;
        return Objects.equals(version.getHash(), version.getScanResult().getSecurityEvidence().artifactSha256());
    }
    private static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
}
