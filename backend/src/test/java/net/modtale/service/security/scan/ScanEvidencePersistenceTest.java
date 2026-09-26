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
        var remote=new net.modtale.model.project.RemoteReviewBinding("project","version",java.util.UUID.randomUUID().toString(),1,
                "original.zip","a".repeat(64),"b".repeat(64),"warden-3.0.0:"+"c".repeat(64),"d".repeat(64),null,false,new net.modtale.model.project.RemoteReviewOrigin("11111111-1111-1111-1111-111111111111","e".repeat(64)));
        original.setManualRescan(true);
        original.setRemoteReview(remote);
        original.setRemoteStatus(new ScanResult.RemoteReviewStatus(java.util.UUID.randomUUID().toString(),"QUEUED",true,1000,2000,null));
        original.setRemotePoll(new ScanResult.RemoteReviewPoll(java.util.UUID.randomUUID().toString(),new java.util.Date(1000),new java.util.Date(2000)));
        original.setReviewedContextSha256("c".repeat(64));
        var stored = new Document();
        converter.write(original, stored);
        var restored = converter.read(ScanResult.class, stored);
        assertTrue(ArtifactClearancePolicy.complete(restored));
        assertEquals(remote,restored.getRemoteReview());assertEquals(remote.origin(),restored.getRemoteReview().origin());
        assertTrue(restored.isManualRescan());
        assertEquals(original.getRemoteStatus(),restored.getRemoteStatus());
        assertEquals(original.getRemotePoll(),restored.getRemotePoll());
        assertEquals(original.getReviewedContextSha256(), restored.getReviewedContextSha256());
        var mapper = new ObjectMapper();
        var forged = mapper.readValue("{\"artifactVerified\":true,\"reviewedContextSha256\":\"forged\"}", ScanResult.class);
        assertFalse(forged.isArtifactVerified());
        assertNull(forged.getReviewedContextSha256());
        String json = mapper.writeValueAsString(original);
        assertFalse(json.contains("remoteReview"));
        assertFalse(json.contains("remotePoll"));
        assertFalse(json.contains("remoteStatus"));
        assertFalse(json.contains("manualRescan"));
        assertFalse(mapper.readValue("{\"manualRescan\":true}",ScanResult.class).isManualRescan());
        assertNull(mapper.readValue("{\"remoteStatus\":{\"state\":\"COMPLETED\"}}",ScanResult.class).getRemoteStatus());
        assertNull(mapper.readValue("{\"remotePoll\":{\"token\":\"forged\"}}",ScanResult.class).getRemotePoll());
        assertNull(mapper.readValue("{\"remoteReview\":"+mapper.writeValueAsString(remote)+"}",ScanResult.class).getRemoteReview());
        assertFalse(json.contains("artifactVerified"));
        assertFalse(json.contains("reviewedContextSha256"));
    }
}
