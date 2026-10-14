package net.modtale.service.security.scan;

import com.mongodb.client.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class RemoteReviewDiscoveryTest {
    MongoClient client;MongoDatabase db;MongoCollection<Document> projects;RemoteReviewDiscovery discovery;
    @BeforeEach void setup() {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException();
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");db=client.getDatabase("warden_discovery_"+UUID.randomUUID().toString().replace("-",""));projects=db.getCollection("projects");
        discovery=new RemoteReviewDiscovery(new MongoTemplate(client,db.getName()));
    }
    @AfterEach void cleanup(){db.drop();client.close();}
    Document version(String id) {return new Document("_id",id).append("reviewStatus","PENDING").append("scanResult",new Document("status","SCANNING").append("scanState","QUEUED").append("scanAttempt",1).append("scanRequestId",UUID.randomUUID().toString()));}
    void insert(Object id,List<Document> versions){projects.insertOne(new Document("_id",id).append("versions",versions));}
    List<RemoteReviewDiscovery.Candidate> walk(int size) {
        var found=new ArrayList<RemoteReviewDiscovery.Candidate>();RemoteReviewDiscovery.Cursor cursor=null;
        for(int i=0;i<100;i++){var page=discovery.page(cursor,size);assertTrue(page.examined()<=size);found.addAll(page.candidates());cursor=page.next();if(cursor==null)return found;}
        fail("Discovery cursor did not terminate");return found;
    }
    @Test void everyVersionIsVisitedAcrossBoundedPagesAndMixedIdTypes() {
        var versions=new ArrayList<Document>();for(int i=0;i<71;i++)versions.add(version("v"+i));insert("string-project",versions);var objectId=new ObjectId();insert(objectId,List.of(version("last")));
        var first=discovery.page(null,16);assertEquals(16,first.examined());assertEquals(16,first.candidates().size());assertFalse(first.next().afterProject());assertEquals(16,first.next().offset());
        var all=walk(16);assertEquals(72,all.size());assertEquals(72,all.stream().map(c->c.projectId()+":"+c.versionId()).distinct().count());assertEquals(objectId.toHexString(),all.getLast().projectId());
    }
    @Test void activeLeasesAndFutureRetryDatesAreSkippedUsingDatabaseTime() {
        var now=db.runCommand(new Document("hello",1)).getDate("localTime").getTime();var versions=new ArrayList<Document>();
        for(int i=0;i<4;i++){var v=version("v"+i);var scan=v.get("scanResult",Document.class);scan.put("scanState","REMOTE_REVIEW");
            scan.put("remotePoll",new Document("token",i==2?null:UUID.randomUUID().toString()).append("leaseUntil",new Date(i==0?now+60000:0)).append("nextPollAt",new Date(i==1?now+60000:0)));versions.add(v);}
        insert("p",versions);var found=walk(2);assertEquals(List.of("v2","v3"),found.stream().map(RemoteReviewDiscovery.Candidate::versionId).toList());
    }
    @Test void emptyCandidatePageStillAdvancesPastBusyVersions() {
        var busy=version("busy");busy.get("scanResult",Document.class).put("scanState","REMOTE_REVIEW");busy.get("scanResult",Document.class).put("remotePoll",new Document("token",null).append("leaseUntil",new Date(Long.MAX_VALUE)).append("nextPollAt",new Date(0)));
        insert("p",List.of(busy,version("ready")));var first=discovery.page(null,1);assertTrue(first.candidates().isEmpty());assertEquals(1,first.next().offset());
        assertEquals("ready",discovery.page(first.next(),1).candidates().getFirst().versionId());
    }
    @Test void deletedCursorProjectDoesNotApplyItsOffsetToTheNextProject() {
        insert("a",List.of(version("one"),version("two")));insert("b",List.of(version("next")));var first=discovery.page(null,1);
        projects.deleteOne(new Document("_id","a"));var next=discovery.page(first.next(),1);assertEquals("next",next.candidates().getFirst().versionId());assertTrue(next.next().afterProject());
    }
    @Test void malformedMetadataAndPollsCannotBecomeCandidates() {
        var badRequest=version("bad-request");badRequest.get("scanResult",Document.class).put("scanRequestId","x".repeat(100000));
        var badAttempt=version("bad-attempt");badAttempt.get("scanResult",Document.class).put("scanAttempt",Long.MAX_VALUE);
        var malformed=version("bad-poll");malformed.get("scanResult",Document.class).put("remotePoll",List.of("invalid"));
        var nullPoll=version("null-poll");nullPoll.get("scanResult",Document.class).put("remotePoll",null);
        var objectPoll=version("bad-dates");objectPoll.get("scanResult",Document.class).put("scanState","REMOTE_REVIEW");objectPoll.get("scanResult",Document.class).put("remotePoll",new Document("token",null).append("leaseUntil","bad").append("nextPollAt",new Date(0)));
        insert("p",List.of(badRequest,badAttempt,malformed,nullPoll,objectPoll,version("ready")));assertEquals(List.of("ready"),walk(2).stream().map(RemoteReviewDiscovery.Candidate::versionId).toList());
    }
    @Test void nonScanningProjectsAndUnsupportedRootIdsCannotStarveValidProjects() {
        insert(1,List.of(version("numeric")));insert("x".repeat(129),List.of(version("long-root")));
        var finished=version("done");finished.get("scanResult",Document.class).put("status","CLEAN");insert("a",List.of(finished));insert("b",List.of(version("ready")));
        assertEquals(List.of("ready"),walk(1).stream().map(RemoteReviewDiscovery.Candidate::versionId).toList());
    }
    @Test void shrinkingVersionArrayAdvancesRatherThanRepeatingItsLastPage() {
        insert("a",List.of(version("one"),version("two")));insert("b",List.of(version("next")));var first=discovery.page(null,1);
        projects.updateOne(new Document("_id","a"),new Document("$set",new Document("versions",List.of(version("one")))));
        var exhausted=discovery.page(first.next(),1);assertEquals(0,exhausted.examined());assertTrue(exhausted.next().afterProject());assertEquals("next",discovery.page(exhausted.next(),1).candidates().getFirst().versionId());
    }
    @Test void pageAndCursorLimitsAreValidated() {
        assertThrows(IllegalArgumentException.class,()->discovery.page(null,0));assertThrows(IllegalArgumentException.class,()->discovery.page(null,65));
        assertThrows(IllegalArgumentException.class,()->new RemoteReviewDiscovery.Cursor("p",-1,false));assertThrows(IllegalArgumentException.class,()->new RemoteReviewDiscovery.Cursor("p",1,true));
        assertThrows(IllegalArgumentException.class,()->new RemoteReviewDiscovery.Cursor(1,0,true));assertNull(discovery.page(null,1).next());
    }
}
