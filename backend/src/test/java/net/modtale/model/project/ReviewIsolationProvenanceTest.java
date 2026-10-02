package net.modtale.model.project;

import org.junit.jupiter.api.Test;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

class ReviewIsolationProvenanceTest {
    @Test void provenanceDoesNotExposeMutableDates() {
        var date=new Date(1000);var record=new ProjectVersion.ReviewIsolation("11111111-1111-1111-1111-111111111111","actor","a".repeat(64),date);
        date.setTime(2000);record.isolatedAt().setTime(3000);assertEquals(1000,record.isolatedAt().getTime());
    }
    @Test void publicJsonNeitherExposesNorAcceptsProvenance()throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var version=new ProjectVersion();version.setVersionNumber("v");
        version.setReviewIsolation(new ProjectVersion.ReviewIsolation("11111111-1111-1111-1111-111111111111","actor","a".repeat(64),new Date(1000)));
        String json=mapper.writeValueAsString(version);assertFalse(json.contains("reviewIsolation"));assertFalse(json.contains("actor"));
        var incoming=mapper.readValue("{\"versionNumber\":\"v\",\"reviewIsolation\":{\"operationId\":\"forged\",\"actorId\":\"forged\"}}",ProjectVersion.class);
        assertNull(incoming.getReviewIsolation());assertEquals("v",incoming.getVersionNumber());
    }
}
