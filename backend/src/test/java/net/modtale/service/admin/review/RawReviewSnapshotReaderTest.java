package net.modtale.service.admin.review;

import com.mongodb.client.*;
import org.bson.*;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class RawReviewSnapshotReaderTest {
    MongoClient client;MongoTemplate mongo;RawReviewSnapshotReader reader;String database;
    @BeforeEach void setup() {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException();
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");database="warden_raw_capture_"+UUID.randomUUID().toString().replace("-","");mongo=new MongoTemplate(client,database);reader=new RawReviewSnapshotReader(mongo);
    }
    @AfterEach void cleanup(){mongo.getDb().drop();client.close();}
    Document version(){return new Document("_id","v").append("scanResult","invalid typed state").append("unknown",new Document("date",new Date(123)).append("number",-0.0));}
    void insert(Object id,List<?> versions){mongo.getCollection("projects").insertOne(new Document("_id",id).append("versions",versions));}
    byte[] bytes(Document document){var buffer=new RawBsonDocument(document,new org.bson.codecs.DocumentCodec()).getByteBuffer().asNIO();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;}
    @Test void sameTextBsonRootsRemainDistinctAndRawBytesAreArchived() {
        String hex="abcdefabcdefabcdefabcdef";var v=version();insert(hex,List.of(v));insert(new ObjectId(hex),List.of(new Document(v).append("different",true)));
        var captured=reader.capture(hex,0,"v");assertArrayEquals(bytes(v),captured.versionBytes());assertTrue(reader.isCurrent(captured));
        assertNotEquals(captured.sha256(),reader.capture(new ObjectId(hex),0,"v").sha256());
        var archive=new ReviewSnapshotArchive(mongo,"test",Map.of("test",new byte[32]));var saved=archive.retain(captured.forArchive(UUID.randomUUID().toString(),"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW,1000,2000));
        assertArrayEquals(captured.versionBytes(),saved.versionBytes());assertEquals(hex,saved.projectId());assertTrue(reader.isCurrent(captured));
        captured.versionBytes()[0]++;assertArrayEquals(bytes(v),captured.versionBytes());
    }
    @ParameterizedTest @ValueSource(strings={"changed","shifted","duplicate","removed"})
    void laterChangesPreventCurrentSnapshotClaim(String mutation) {
        insert("p",List.of(version()));var captured=reader.capture("p",0,"v");var collection=mongo.getCollection("projects");
        switch(mutation) {
            case "changed"->collection.updateOne(new Document("_id","p"),new Document("$set",new Document("versions.0.unknown.number",1.0)));
            case "shifted"->collection.updateOne(new Document("_id","p"),new Document("$push",new Document("versions",new Document("$each",List.of(new Document("_id","other"))).append("$position",0))));
            case "duplicate"->collection.updateOne(new Document("_id","p"),new Document("$push",new Document("versions",new Document("_id","v").append("different",true))));
            default->collection.deleteOne(new Document("_id","p"));
        }
        assertFalse(reader.isCurrent(captured));
    }
    @Test void unrelatedMalformedSlotsDoNotNormalizeOrReplaceSelectedRecord() {
        insert("p",List.of("bad sibling",new Document("_id",42),version()));
        assertArrayEquals(bytes(version()),reader.capture("p",2,"v").versionBytes());
        assertThrows(IllegalStateException.class,()->reader.capture("p",0,"v"));assertThrows(IllegalStateException.class,()->reader.capture("p",1,"v"));
        assertThrows(IllegalStateException.class,()->reader.capture("p",3,"v"));assertThrows(IllegalStateException.class,()->reader.capture("p",2,"other"));
    }
    @Test void duplicateIdentityKeysCannotBeCaptured() {
        var output=new org.bson.io.BasicOutputBuffer();try(var writer=new BsonBinaryWriter(output)) {
            writer.writeStartDocument();writer.writeString("_id","p");writer.writeStartArray("versions");writer.writeStartDocument();
            writer.writeString("_id","v");writer.writeString("_id","v");writer.writeEndDocument();writer.writeEndArray();writer.writeEndDocument();
        }
        mongo.getCollection("projects").withDocumentClass(RawBsonDocument.class).insertOne(new RawBsonDocument(output.toByteArray()));
        assertThrows(IllegalStateException.class,()->reader.capture("p",0,"v"));
    }
    @Test void databaseCollationCannotSelectAnotherRoot() {
        mongo.getDb().createCollection("projects",new com.mongodb.client.model.CreateCollectionOptions().collation(com.mongodb.client.model.Collation.builder().locale("en").collationStrength(com.mongodb.client.model.CollationStrength.SECONDARY).build()));
        insert("PROJECT",List.of(version()));assertThrows(IllegalStateException.class,()->reader.capture("project",0,"v"));assertTrue(reader.isCurrent(reader.capture("PROJECT",0,"v")));
    }
    @Test void invalidPositionsFailBeforeReading() {
        assertThrows(IllegalArgumentException.class,()->reader.capture(42,0,"v"));assertThrows(IllegalArgumentException.class,()->reader.capture("p",-1,"v"));
        assertThrows(IllegalArgumentException.class,()->reader.capture("p",0,"bad\n"));assertThrows(IllegalArgumentException.class,()->reader.capture("p",0,null));
    }
}
