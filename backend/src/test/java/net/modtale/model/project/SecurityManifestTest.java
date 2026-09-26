package net.modtale.model.project;

import net.modtale.config.db.SecurityEvidenceConverters;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SecurityManifestTest {
    @Test void streamedIdentityMatchesCanonicalOrderingAndUtf16PathLengths() throws Exception {
        var entries = new LinkedHashMap<String,String>();
        entries.put("z/😀.class", "a".repeat(64)); entries.put("a.json", "b".repeat(64));
        StringBuilder canonical = new StringBuilder();
        new TreeMap<>(entries).forEach((path, hash) -> canonical.append(path.length()).append(':').append(path).append(':').append(hash).append('\n'));
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, SecurityManifest.identity(entries));
    }
    @Test void aggregatePathBudgetAppliesToValidationAndPersistence() {
        var entries = new LinkedHashMap<String,String>();
        for (int i = 0; i < 126; i++) entries.put(i + "x".repeat(8000), "a".repeat(64));
        assertFalse(SecurityManifest.valid(entries, false));
        assertThrows(Exception.class, () -> SecurityManifest.identity(entries));
        var evidence = new ScanResult.SecurityEvidence("policy", "a".repeat(64), "b".repeat(64), true, true, "COMPLETED", entries);
        assertThrows(Exception.class, () -> new SecurityEvidenceConverters.Write().convert(evidence));
        assertThrows(Exception.class, () -> new SecurityEvidenceConverters.Read().convert(new Document("entryHashes", entries)));
    }
    @Test void exactAggregateBoundaryIsAcceptedAndOneMorePathIsRejected() {
        var entries = new LinkedHashMap<String,String>();
        for (int i = 0; i < 125; i++) entries.put(String.format("%04d", i) + "x".repeat(7996), "a".repeat(64));
        assertTrue(SecurityManifest.valid(entries, false));
        entries.put("overflow", "a".repeat(64));
        assertFalse(SecurityManifest.valid(entries, false));
    }
    @Test void compactApprovalSummariesAllowEmptyManifestsButCannotStandInForFullEvidence() {
        assertTrue(SecurityManifest.valid(Map.of(), true));
        assertFalse(SecurityManifest.valid(Map.of(), false));
        assertFalse(SecurityManifest.valid(Map.of("manifest.json", "invalid"), false));
    }
}
