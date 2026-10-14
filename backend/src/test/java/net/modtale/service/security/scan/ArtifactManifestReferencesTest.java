package net.modtale.service.security.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.modtale.config.db.*;
import net.modtale.model.project.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArtifactManifestReferencesTest {
    private static class MemoryStore implements ArtifactManifestStore {
        final Map<String,Map<String,String>> records = new HashMap<>(); int reads, writes;
        public String put(Map<String,String> entries) { writes++; String id = SecurityManifest.identity(entries); records.putIfAbsent(id, Map.copyOf(entries)); return id; }
        public Map<String,String> get(String id) { reads++; return records.get(id); }
    }
    @Test void referencesPreserveFullEvidenceAndPublicJsonWithoutDuplicatingProjectPayloads() throws Exception {
        var store = new MemoryStore(); var original = ScanEvidenceFixtures.complete(true);
        var writer = new SecurityEvidenceConverters.Write(store); var reader = new SecurityEvidenceConverters.Read(store);
        Document stored = writer.convert(original.getSecurityEvidence());
        assertFalse(stored.containsKey("entryHashes")); assertTrue(stored.containsKey("manifestRef"));
        var restored = reader.convert(stored); assertEquals(0, store.reads);
        assertEquals(stored, writer.convert(restored)); assertEquals(0, store.reads); assertEquals(1, store.writes);
        original.setSecurityEvidence(restored);
        assertTrue(ArtifactClearancePolicy.complete(original)); assertEquals(1, store.reads);
        String json = new ObjectMapper().writeValueAsString(restored);
        assertTrue(json.contains("entryHashes")); assertFalse(json.contains("manifestRef"));
        assertEquals(1, store.reads);
        assertThrows(UnsupportedOperationException.class, () -> restored.entryHashes().put("other", "a".repeat(64)));
    }
    @Test void missingOrSubstitutedRecordsCannotGrantClearance() {
        for (boolean missing : List.of(true, false)) {
            var store = new MemoryStore(); var result = ScanEvidenceFixtures.complete(true);
            Document stored = new SecurityEvidenceConverters.Write(store).convert(result.getSecurityEvidence());
            String id = stored.getString("manifestRef");
            if (missing) store.records.clear(); else store.records.put(id, Map.of("different.class", "f".repeat(64)));
            result.setSecurityEvidence(new SecurityEvidenceConverters.Read(store).convert(stored));
            assertFalse(ArtifactClearancePolicy.complete(result)); assertFalse(ArtifactClearancePolicy.cleared(result));
        }
    }
    @Test void legacyEvidenceMigratesWhenWrittenAndAmbiguousReferencePayloadsAreRejected() {
        var store = new MemoryStore(); var evidence = ScanEvidenceFixtures.complete(true).getSecurityEvidence();
        Document legacy = new SecurityEvidenceConverters.Write().convert(evidence);
        var restored = new SecurityEvidenceConverters.Read(store).convert(legacy);
        assertEquals(evidence.entryHashes(), restored.entryHashes());
        Document migrated = new SecurityEvidenceConverters.Write(store).convert(restored);
        assertTrue(migrated.containsKey("manifestRef"));
        migrated.put("entryHashes", List.of());
        assertThrows(Exception.class, () -> new SecurityEvidenceConverters.Read(store).convert(migrated));
    }
    @Test void productionConfigurationCanConstructStoreAwareConvertersWithoutDatabaseMappingCycles() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MongoDatabaseFactory.class, () -> mock(MongoDatabaseFactory.class));
            context.register(MongoConfig.class); context.refresh();
            assertInstanceOf(MongoArtifactManifestStore.class, context.getBean(ArtifactManifestStore.class));
            assertNotNull(context.getBean(org.springframework.data.mongodb.core.convert.MongoCustomConversions.class));
        }
    }
}
