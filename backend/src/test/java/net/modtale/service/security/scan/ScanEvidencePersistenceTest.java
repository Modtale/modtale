package net.modtale.service.security.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.modtale.model.project.ScanResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import static org.junit.jupiter.api.Assertions.*;

class ScanEvidencePersistenceTest {
    @Test void internalTrustSurvivesDatabaseRoundTripButCannotBeSuppliedOverJson() throws Exception {
        var context = new MongoMappingContext();
        context.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(new net.modtale.config.db.MongoConfig().mongoCustomConversions());
        converter.afterPropertiesSet();
        var original = ScanEvidenceFixtures.complete(true);
        original.setReviewedContextSha256("c".repeat(64));
        var stored = new Document();
        converter.write(original, stored);
        var restored = converter.read(ScanResult.class, stored);
        assertTrue(ArtifactClearancePolicy.complete(restored));
        assertEquals(original.getReviewedContextSha256(), restored.getReviewedContextSha256());
        var mapper = new ObjectMapper();
        var forged = mapper.readValue("{\"artifactVerified\":true,\"reviewedContextSha256\":\"forged\"}", ScanResult.class);
        assertFalse(forged.isArtifactVerified());
        assertNull(forged.getReviewedContextSha256());
        String json = mapper.writeValueAsString(original);
        assertFalse(json.contains("artifactVerified"));
        assertFalse(json.contains("reviewedContextSha256"));
    }
}
