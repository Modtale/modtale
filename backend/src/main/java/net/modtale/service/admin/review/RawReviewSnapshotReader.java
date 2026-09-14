package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import org.bson.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Exact stored bytes for a unique version. Capture is evidence, not permission to repair. */
public final class RawReviewSnapshotReader {
    public record Captured(Object projectId,int versionIndex,String versionId,byte[] versionBytes,String sha256) {
        public Captured {versionBytes=versionBytes.clone();}
        @Override public byte[] versionBytes(){return versionBytes.clone();}
        @Override public String toString(){return "CapturedReviewSnapshot["+sha256+"]";}
        public ReviewSnapshotArchive.Snapshot forArchive(String id,String actor,ReviewSnapshotArchive.Action action,long createdAt,long expiresAt) {
            return new ReviewSnapshotArchive.Snapshot(id,projectId,versionIndex,actor,action,createdAt,expiresAt,versionBytes);
        }
    }
    private final MongoCollection<RawBsonDocument> projects;
    public RawReviewSnapshotReader(MongoTemplate mongo) {
        projects=mongo.getCollection("projects").withDocumentClass(RawBsonDocument.class).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY);
    }
    public Captured capture(Object projectId,int versionIndex,String versionId) {return capture(null,projectId,versionIndex,versionId);}
    public Captured capture(com.mongodb.client.ClientSession session,Object projectId,int versionIndex,String versionId) {
        if(!(projectId instanceof ObjectId || projectId instanceof String s && !s.isEmpty() && s.length()<=128
                && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(s)) || versionIndex<0 || versionIndex>16*1024*1024
                || versionId==null || versionId.isBlank() || versionId.length()>128 || versionId.chars().anyMatch(Character::isISOControl)
                || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(versionId))throw new IllegalArgumentException("Invalid review snapshot position");
        var query=new Document("_id",projectId);
        var root=(session==null?projects.find(query):projects.find(session,query)).collation(Collation.builder().locale("simple").build()).maxTime(5,TimeUnit.SECONDS).first();
        if(root==null)throw conflict();
        var buffer=root.getByteBuffer().asNIO();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);
        if(bytes.length>ReviewSnapshotArchive.MAX_BYTES)throw conflict();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);byte[] selected=null;int matches=0,arrays=0,rootIds=0;
        try(var reader=new BsonBinaryReader(ByteBuffer.wrap(bytes))) {
            reader.readStartDocument();
            while(reader.readBsonType()!=BsonType.END_OF_DOCUMENT) {
                checkDeadline(deadline);String field=reader.readName();
                if(field.equals("_id")) {
                    rootIds++;Object found=reader.getCurrentBsonType()==BsonType.OBJECT_ID?reader.readObjectId():reader.getCurrentBsonType()==BsonType.STRING?reader.readString():null;
                    if(!projectId.equals(found))throw conflict();
                } else if(field.equals("versions")) {
                    if(++arrays!=1 || reader.getCurrentBsonType()!=BsonType.ARRAY)throw conflict();
                    reader.readStartArray();int index=0;
                    while(reader.readBsonType()!=BsonType.END_OF_DOCUMENT) {
                        checkDeadline(deadline);int start=reader.getBsonInput().getPosition();boolean document=reader.getCurrentBsonType()==BsonType.DOCUMENT;
                        reader.skipValue();int end=reader.getBsonInput().getPosition();
                        if(document) {
                            String id=versionId(bytes,start,end,deadline);
                            if(versionId.equals(id))matches++;
                            if(index==versionIndex) {
                                if(!versionId.equals(id))throw conflict();
                                selected=Arrays.copyOfRange(bytes,start,end);
                            }
                        } else if(index==versionIndex)throw conflict();
                        index++;
                    }
                    reader.readEndArray();
                } else reader.skipValue();
            }
            reader.readEndDocument();
        } catch(RuntimeException malformed) {throw conflict();}
        if(rootIds!=1 || arrays!=1 || matches!=1 || selected==null)throw conflict();
        try {return new Captured(projectId,versionIndex,versionId,selected,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(selected)));}
        catch(java.security.NoSuchAlgorithmException unavailable){throw new IllegalStateException(unavailable);}
    }
    public boolean isCurrent(Captured snapshot) {
        try {var current=capture(snapshot.projectId(),snapshot.versionIndex(),snapshot.versionId());return Arrays.equals(snapshot.versionBytes(),current.versionBytes());}
        catch(IllegalStateException changed){return false;}
    }
    private static String versionId(byte[] bytes,int start,int end,long deadline) {
        String id=null;int count=0;
        try(var reader=new BsonBinaryReader(ByteBuffer.wrap(bytes,start,end-start).slice())) {
            reader.readStartDocument();
            while(reader.readBsonType()!=BsonType.END_OF_DOCUMENT) {
                checkDeadline(deadline);String field=reader.readName();
                if(field.equals("_id")) {count++;if(reader.getCurrentBsonType()==BsonType.STRING)id=reader.readString();else reader.skipValue();}
                else reader.skipValue();
            }
            reader.readEndDocument();
        }
        // Duplicate identity fields are ambiguous even if they happen to contain the same value.
        if(count>1)throw conflict();return id;
    }
    private static void checkDeadline(long deadline){if(System.nanoTime()>deadline)throw conflict();}
    private static IllegalStateException conflict(){return new IllegalStateException("Review snapshot changed, is ambiguous, or is unavailable");}
}
