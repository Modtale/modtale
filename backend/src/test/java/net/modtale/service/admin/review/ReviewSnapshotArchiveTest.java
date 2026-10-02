package net.modtale.service.admin.review;

import com.mongodb.client.*;
import com.mongodb.*;
import org.bson.Document;
import org.bson.types.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class ReviewSnapshotArchiveTest {
    MongoClient client;MongoTemplate mongo;String database;ReviewSnapshotArchive archive;
    byte[] key=new byte[32];
    @BeforeEach void setup() {
        Arrays.fill(key,(byte)19);String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException();
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");database="warden_snapshot_"+UUID.randomUUID().toString().replace("-","");mongo=new MongoTemplate(client,database);
        archive=new ReviewSnapshotArchive(mongo,"one",Map.of("one",key));
    }
    @AfterEach void cleanup(){mongo.getDb().drop();client.close();}
    ReviewSnapshotArchive.Snapshot snapshot(Object project,int size) {
        byte[] bytes=new byte[size];new Random(17).nextBytes(bytes);
        return new ReviewSnapshotArchive.Snapshot(UUID.randomUUID().toString(),project,3,"operator",ReviewSnapshotArchive.Action.ISOLATE_REVIEW,1000,2000,bytes);
    }
    @Test void boundedChunksRetainExactOpaqueBytesAndBsonIdentityAcrossRestart() {
        String id="abcdefabcdefabcdefabcdef";
        for(Object project:List.of(id,new ObjectId(id))) {
            var source=snapshot(project,ReviewSnapshotArchive.CHUNK_BYTES+17);var retained=archive.retain(source);
            assertArrayEquals(source.versionBytes(),retained.versionBytes());assertEquals(project,retained.projectId());
            var restarted=new ReviewSnapshotArchive(mongo,"one",Map.of("one",key));assertArrayEquals(source.versionBytes(),restarted.load(source.id()).versionBytes());
            assertArrayEquals(source.versionBytes(),restarted.retain(source).versionBytes());
        }
        assertEquals(2,mongo.getCollection(ReviewSnapshotArchive.METADATA).countDocuments());assertEquals(4,mongo.getCollection(ReviewSnapshotArchive.CHUNKS).countDocuments());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={5,262144,16777216})
    void byteBoundariesRoundTripWithoutTruncation(int size) {
        var source=snapshot("project",size);assertArrayEquals(source.versionBytes(),archive.retain(source).versionBytes());
        assertEquals((size+ReviewSnapshotArchive.CHUNK_BYTES-1)/ReviewSnapshotArchive.CHUNK_BYTES,mongo.getCollection(ReviewSnapshotArchive.CHUNKS).countDocuments());
    }
    @Test void originalBsonIncludingUnknownFieldsAndMalformedTypedStateIsPreserved() {
        var original=new Document("_id","v").append("scanResult","malformed typed state").append("unknown",new Document("date",new Date(123))
                .append("binary",new Binary((byte)0x80,new byte[]{1,2,3})).append("decimal",new Decimal128(new java.math.BigDecimal("123.4500"))));
        var encoded=new org.bson.RawBsonDocument(original,new org.bson.codecs.DocumentCodec()).getByteBuffer().asNIO();byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);
        var source=new ReviewSnapshotArchive.Snapshot(UUID.randomUUID().toString(),new ObjectId(),0,"operator",ReviewSnapshotArchive.Action.REPLACE_REVIEW,1000,2000,bytes);
        var restored=archive.retain(source);assertArrayEquals(bytes,restored.versionBytes());
        var decoded=new org.bson.RawBsonDocument(restored.versionBytes()).decode(new org.bson.codecs.DocumentCodec());assertEquals(original,decoded);
    }
    @ParameterizedTest @ValueSource(strings={"actorId","projectId","versionIndex","action","expiresAt","sha256","keyId","extra"})
    void changedMetadataCannotAuthenticate(String field) {
        var source=snapshot("project",17);archive.retain(source);
        Object value=switch(field){case "versionIndex"->4;case "expiresAt"->3000L;case "action"->"REPLACE_REVIEW";case "projectId"->new ObjectId();default->"changed";};
        mongo.getCollection(ReviewSnapshotArchive.METADATA).updateOne(new Document("_id",source.id()),new Document("$set",new Document(field,value)));
        assertThrows(IllegalStateException.class,()->archive.load(source.id()));
    }
    @ParameterizedTest @ValueSource(strings={"missing","changed","extra","wrongType"})
    void missingOrAlteredChunksCannotRestoreSnapshot(String mutation) {
        var source=snapshot("project",ReviewSnapshotArchive.CHUNK_BYTES+7);archive.retain(source);var chunks=mongo.getCollection(ReviewSnapshotArchive.CHUNKS);
        var query=new Document("_id",source.id()+":0");
        switch(mutation) {
            case "missing"->chunks.deleteOne(query);
            case "changed"->{byte[] bytes=new byte[ReviewSnapshotArchive.CHUNK_BYTES];chunks.updateOne(query,new Document("$set",new Document("payload",new Binary(bytes))));}
            case "extra"->chunks.updateOne(query,new Document("$set",new Document("extra",1)));
            default->chunks.updateOne(query,new Document("$set",new Document("payload","not bytes")));
        }
        assertThrows(IllegalStateException.class,()->archive.load(source.id()));
    }
    @Test void reusedIdentityCannotReplaceOriginalSnapshot() {
        var source=snapshot("project",17);archive.retain(source);
        var different=new ReviewSnapshotArchive.Snapshot(source.id(),"other",source.versionIndex(),source.actorId(),source.action(),source.createdAt(),source.expiresAt(),source.versionBytes());
        assertThrows(IllegalStateException.class,()->archive.retain(different));assertEquals("project",archive.load(source.id()).projectId());
    }
    @Test void rotationRetainsReadAccessOnlyWithOriginalVerificationKey() {
        var source=snapshot("project",17);archive.retain(source);byte[] newer=new byte[32];Arrays.fill(newer,(byte)23);
        var rotated=new ReviewSnapshotArchive(mongo,"two",Map.of("one",key,"two",newer));assertArrayEquals(source.versionBytes(),rotated.load(source.id()).versionBytes());
        assertArrayEquals(source.versionBytes(),rotated.retain(source).versionBytes());
        assertThrows(IllegalStateException.class,()->new ReviewSnapshotArchive(mongo,"two",Map.of("two",newer)).load(source.id()));
        assertThrows(IllegalStateException.class,()->new ReviewSnapshotArchive(mongo,"one",Map.of("one",newer)).load(source.id()));
    }
    @ParameterizedTest @ValueSource(strings={ReviewSnapshotArchive.METADATA,ReviewSnapshotArchive.CHUNKS})
    void committedInsertWithLostAcknowledgementRecoversWithoutReplacingBytes(String collection) {
        var configured=mongo.getCollection(collection).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY).withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        var intercepted=spy(configured);var template=spy(mongo);doReturn(intercepted).when(template).getCollection(collection);
        doReturn(intercepted).when(intercepted).withReadPreference(any());doReturn(intercepted).when(intercepted).withReadConcern(any());doReturn(intercepted).when(intercepted).withWriteConcern(any());
        doAnswer(i->{i.callRealMethod();throw new MongoException("Lost acknowledgement");}).when(intercepted).insertOne(any(Document.class));
        var source=snapshot("project",ReviewSnapshotArchive.CHUNK_BYTES+3);var result=new ReviewSnapshotArchive(template,"one",Map.of("one",key)).retain(source);
        assertArrayEquals(source.versionBytes(),result.versionBytes());assertEquals(1,mongo.getCollection(ReviewSnapshotArchive.METADATA).countDocuments());assertEquals(2,mongo.getCollection(ReviewSnapshotArchive.CHUNKS).countDocuments());
    }
    @Test void conflictingPartialUploadNeverCreatesAuthenticatedMetadata() {
        var source=snapshot("project",17);mongo.getCollection(ReviewSnapshotArchive.CHUNKS).insertOne(new Document("_id",source.id()+":0").append("payload",new Binary(new byte[17])));
        assertThrows(IllegalStateException.class,()->archive.retain(source));assertEquals(0,mongo.getCollection(ReviewSnapshotArchive.METADATA).countDocuments());assertThrows(IllegalStateException.class,()->archive.load(source.id()));
    }
    @Test void limitsAndDefensiveCopiesPreventMutableSnapshotOrKeyInputs() {
        assertThrows(IllegalArgumentException.class,()->snapshot("project",ReviewSnapshotArchive.MAX_BYTES+1));
        assertThrows(IllegalArgumentException.class,()->new ReviewSnapshotArchive(mongo,"missing",Map.of("one",key)));
        assertThrows(IllegalArgumentException.class,()->new ReviewSnapshotArchive(mongo,"one",Map.of("one",new byte[3])));
        var source=snapshot("project",17);var before=source.versionBytes();source.versionBytes()[0]++;assertArrayEquals(before,source.versionBytes());
        Arrays.fill(key,(byte)0);archive.retain(source);assertArrayEquals(before,archive.load(source.id()).versionBytes());assertFalse(source.toString().contains("operator"));
    }
}
