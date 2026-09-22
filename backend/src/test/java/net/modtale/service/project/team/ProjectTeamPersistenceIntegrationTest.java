package net.modtale.service.project.team;
import com.mongodb.client.*;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import net.modtale.model.project.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class ProjectTeamPersistenceIntegrationTest {
    MongoClient client; MongoTemplate mongo; String database; ProjectTeamPersistence writes;
    @BeforeEach void setup() {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");
        if(!Set.of("27029","27030","27031","27032").contains(port))throw new IllegalArgumentException("Unexpected test port");
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");
        database="warden_team_backport_"+UUID.randomUUID().toString().replace("-","");mongo=new MongoTemplate(client,database);writes=new ProjectTeamPersistence(mongo);
        var p=new Project();p.setId("project");p.setAuthorId("owner");p.setPendingTransferTo("recipient");p.setPendingTransferRequestId("request");p.setPendingTransferOwnerId("owner");p.setPendingTransferExpiresAt(System.currentTimeMillis()+60000);mongo.insert(p);
        mongo.getCollection("projects").updateOne(new Document("_id","project"),new Document("$set",new Document("unknownEvidence","retained")));
    }
    @AfterEach void cleanup(){if(client!=null){client.getDatabase(database).drop();client.close();}}
    Document raw(){return mongo.getCollection("projects").find(new Document("_id","project")).first();}
    ProjectTeamPersistence.Snapshot capture(){var p=mongo.findById("project",Project.class);return writes.capture("project",ProjectTeamSnapshot.token(p));}
    @Test void teamWritesRetainUnrelatedStoredDataAndRejectConcurrentChanges() {
        var s=capture();var before=raw();s.project().setAuthorId("new-owner");assertTrue(writes.applyTeam(s));assertEquals("retained",raw().get("unknownEvidence"));assertEquals(before.get("versions"),raw().get("versions"));
        assertFalse(writes.applyTeam(s));assertEquals("new-owner",raw().get("authorId"));
    }
    @Test void transferCannotAcceptExpiredOrReplacedRequests() {
        var s=capture();s.project().setAuthorId("recipient");s.project().setPendingTransferTo(null);
        mongo.getCollection("projects").updateOne(new Document("_id","project"),new Document("$set",new Document("pendingTransferRequestId","replacement")));
        assertFalse(writes.resolveTransfer(s,"request"));assertEquals("owner",raw().get("authorId"));
        mongo.getCollection("projects").updateOne(new Document("_id","project"),new Document("$set",new Document("pendingTransferExpiresAt",1L)));
        var expired=capture();expired.project().setAuthorId("recipient");assertFalse(writes.resolveTransfer(expired,"replacement"));assertEquals("owner",raw().get("authorId"));
    }
    @Test void tokenRejectsChangedAuthorityBeforeMutation() {
        var p=mongo.findById("project",Project.class);String token=ProjectTeamSnapshot.token(p);
        mongo.getCollection("projects").updateOne(new Document("_id","project"),new Document("$set",new Document("authorId","new-owner")));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->writes.capture("project",token));
    }
}
