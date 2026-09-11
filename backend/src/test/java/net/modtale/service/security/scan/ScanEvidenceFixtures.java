package net.modtale.service.security.scan;

import net.modtale.model.project.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class ScanEvidenceFixtures {
    public static ScanResult complete(boolean granted) {
        ScanResult result = new ScanResult();
        result.setStatus(granted ? ScanStatus.CLEAN : ScanStatus.SUSPICIOUS);
        result.setVerdict(granted ? "AUTO_APPROVE" : "REVIEW");
        result.setScanState("COMPLETED");
        result.setArtifactVerified(true);
        result.setScanTimestamp(System.currentTimeMillis());
        ScanResult.ScanSummary summary = new ScanResult.ScanSummary();
        summary.setFilesScanned(1);
        result.setSummary(summary);
        String hash = "a".repeat(64);
        result.setSecurityEvidence(new ScanResult.SecurityEvidence("warden-3.0.0", "b".repeat(64),
                digest("9:Mod.class:" + hash + "\n"), true, granted, "COMPLETED", Map.of("Mod.class", hash)));
        return result;
    }
    static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
