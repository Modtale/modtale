package net.modtale.service.security.issue;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import net.modtale.model.project.ScanResult;
import net.modtale.service.security.scan.ArtifactClearancePolicy;

/** Exact local evidence comparison only; it does not establish cross-file safety or clearance. */
final class IssueEvidenceIdentity {
    private final String policy;
    private final Map<String, String> entries;

    private IssueEvidenceIdentity(String policy, Map<String, String> entries) {
        this.policy = policy;
        this.entries = entries;
    }

    static IssueEvidenceIdentity from(ScanResult scan) {
        if (!ArtifactClearancePolicy.complete(scan)) return new IssueEvidenceIdentity(null, Map.of());
        return new IssueEvidenceIdentity(scan.getSecurityEvidence().policyVersion(),
                Map.copyOf(scan.getSecurityEvidence().entryHashes()));
    }

    String identify(ScanResult.ScanIssue issue) {
        if (policy == null || issue == null || issue.getFilePath() == null) return null;
        String fileHash = entries.get(issue.getFilePath());
        if (fileHash == null) return null;
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (String value : new String[]{"finding-evidence-v1", policy, issue.getFilePath(), fileHash,
                    issue.getType(), issue.getCategory(), issue.getDescription(), issue.getSeverity(),
                    Integer.toString(issue.getLineStart()), Integer.toString(issue.getLineEnd()),
                    Integer.toString(issue.getScoreImpact()), Integer.toString(issue.getConfidence()),
                    issue.getEvidenceLevel(), issue.getReviewCadence(), issue.getReviewPriority(),
                    Boolean.toString(issue.isNoiseSuppressed())}) {
                append(hash, value);
            }
            hash.update(ByteBuffer.allocate(4).putInt(issue.getTactics().size()).array());
            for (String tactic : issue.getTactics()) append(hash, tactic);
            return "ie1:" + HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void append(MessageDigest hash, String value) {
        hash.update(ByteBuffer.allocate(4).putInt(value == null ? -1 : value.length()).array());
        if (value == null) return;
        // Preserve exact Java strings, including unpaired surrogates, without replacement encoding.
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            hash.update((byte) (character >>> 8));
            hash.update((byte) character);
        }
    }
}
