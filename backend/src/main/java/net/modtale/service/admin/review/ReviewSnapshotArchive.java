package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Immutable evidence storage only. A retained snapshot never authorizes a version mutation. */
public final class ReviewSnapshotArchive {
    public static final String METADATA="review_snapshot_archives", CHUNKS="review_snapshot_chunks";
    public static final int MAX_BYTES=16*1024*1024, CHUNK_BYTES=256*1024;
    public enum Action { ISOLATE_REVIEW, REPLACE_REVIEW, CANCEL_REMOTE_REVIEW, REPLACEMENT_INTENT, REPLACEMENT_ACTIVATION, VERSION_MUTATION_BEFORE, VERSION_MUTATION_AFTER, VERSION_MUTATION_INTENT, PROJECT_MUTATION_BEFORE, PROJECT_MUTATION_AFTER, PROJECT_MUTATION_INTENT }
    public record Snapshot(String id,Object projectId,int versionIndex,String actorId,Action action,long createdAt,long expiresAt,byte[] versionBytes) {
        public Snapshot {
            if(!uuid(id) || !(projectId instanceof ObjectId || projectId instanceof String s && !s.isEmpty() && s.length()<=128
                    && StandardCharsets.UTF_8.newEncoder().canEncode(s)) || versionIndex<0 || versionIndex>16*1024*1024
                    || actorId==null || actorId.isBlank() || actorId.length()>256 || !StandardCharsets.UTF_8.newEncoder().canEncode(actorId)
                    || action==null || createdAt<=0 || expiresAt<=createdAt || expiresAt-createdAt>900000
                    || versionBytes==null || versionBytes.length<5 || versionBytes.length>MAX_BYTES)throw new IllegalArgumentException("Invalid review snapshot");
            versionBytes=versionBytes.clone();
        }
        @Override public byte[] versionBytes(){return versionBytes.clone();}
        @Override public String toString(){return "ReviewSnapshot["+id+"]";}
    }
    private final MongoCollection<Document> metadata,chunks;
    private final Map<String,byte[]> keys;
    private final String activeKey;
    public ReviewSnapshotArchive(MongoTemplate mongo,String activeKey,Map<String,byte[]> signingKeys) {
        if(activeKey==null || signingKeys==null || signingKeys.isEmpty() || signingKeys.size()>8)throw new IllegalArgumentException("Snapshot signing keys are required");
        var copy=new HashMap<String,byte[]>();
        signingKeys.forEach((id,key)->{if(id==null || !id.matches("[A-Za-z0-9_-]{1,64}") || key==null || key.length<32 || key.length>128)throw new IllegalArgumentException("Invalid snapshot signing key");copy.put(id,key.clone());});
        if(!copy.containsKey(activeKey))throw new IllegalArgumentException("Active snapshot signing key is unavailable");
        this.activeKey=activeKey;keys=Map.copyOf(copy);
        metadata=collection(mongo,METADATA);chunks=collection(mongo,CHUNKS);
    }
    private static MongoCollection<Document> collection(MongoTemplate mongo,String name) {
        return mongo.getCollection(name).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY)
                .withWriteConcern(WriteConcern.MAJORITY.withJournal(true).withWTimeout(10,TimeUnit.SECONDS));
    }
    public Snapshot retain(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        var existing=read(metadata,snapshot.id());
        if(existing!=null) {var restored=load(snapshot.id());if(!same(snapshot,restored))throw unavailable();return restored;}
        byte[] bytes=snapshot.versionBytes();int count=(bytes.length+CHUNK_BYTES-1)/CHUNK_BYTES;
        for(int i=0;i<count;i++) {
            var payload=Arrays.copyOfRange(bytes,i*CHUNK_BYTES,Math.min(bytes.length,(i+1)*CHUNK_BYTES));
            insertExact(chunks,new Document("_id",snapshot.id()+":"+i).append("payload",new Binary(payload)));
        }
        var record=new Document("_id",snapshot.id()).append("schema",1).append("keyId",activeKey).append("projectId",snapshot.projectId())
                .append("versionIndex",snapshot.versionIndex()).append("actorId",snapshot.actorId()).append("action",snapshot.action().name())
                .append("createdAt",snapshot.createdAt()).append("expiresAt",snapshot.expiresAt()).append("length",bytes.length).append("chunks",count)
                .append("sha256",digest(bytes));
        record.append("tag",tag(record,keys.get(activeKey)));insertExact(metadata,record);
        var restored=load(snapshot.id());if(!same(snapshot,restored))throw unavailable();return restored;
    }
    public Snapshot find(String id) {
        if(!uuid(id))throw new IllegalArgumentException("Invalid snapshot identity");
        return read(metadata,id)==null?null:load(id);
    }
    public Snapshot load(String id) {
        if(!uuid(id))throw new IllegalArgumentException("Invalid snapshot identity");
        var record=read(metadata,id);if(record==null)throw unavailable();
        try {
            if(record.size()!=13 || !Integer.valueOf(1).equals(record.get("schema")) || !id.equals(record.get("_id")))throw unavailable();
            String keyId=record.getString("keyId");byte[] key=keys.get(keyId);if(key==null)throw unavailable();
            String storedTag=record.getString("tag");if(storedTag==null || !storedTag.matches("[0-9a-f]{64}")
                    || !MessageDigest.isEqual(HexFormat.of().parseHex(storedTag),HexFormat.of().parseHex(tag(record,key))))throw unavailable();
            int length=record.getInteger("length"),count=record.getInteger("chunks");
            if(length<5 || length>MAX_BYTES || count!=(length+CHUNK_BYTES-1)/CHUNK_BYTES)throw unavailable();
            var bytes=new ByteArrayOutputStream(length);
            for(int i=0;i<count;i++) {
                var chunk=read(chunks,id+":"+i);if(chunk==null || chunk.size()!=2 || !(chunk.get("payload") instanceof Binary binary) || binary.getType()!=0)throw unavailable();
                byte[] payload=binary.getData();if(payload.length!=Math.min(CHUNK_BYTES,length-i*CHUNK_BYTES))throw unavailable();bytes.writeBytes(payload);
            }
            byte[] payload=bytes.toByteArray();if(!digest(payload).equals(record.getString("sha256")))throw unavailable();
            return new Snapshot(id,record.get("projectId"),record.getInteger("versionIndex"),record.getString("actorId"),Action.valueOf(record.getString("action")),
                    record.getLong("createdAt"),record.getLong("expiresAt"),payload);
        } catch(RuntimeException invalid) {throw unavailable();}
    }
    private static void insertExact(MongoCollection<Document> collection,Document record) {
        try {ReviewRepairIo.collection(collection).insertOne(record);}
        catch(MongoException uncertain) {
            // An acknowledged exact read can recover an inserted record, including lost write acknowledgements.
            var restored=read(collection,record.getString("_id"));if(!record.equals(restored))throw unavailable();
        }
    }
    private static Document read(MongoCollection<Document> collection,String id) {
        return ReviewRepairIo.collection(collection).find(new Document("_id",id)).collation(Collation.builder().locale("simple").build()).maxTime(5,TimeUnit.SECONDS).first();
    }
    private static String tag(Document record,byte[] key) {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeUTF("modtale-review-snapshot-v1");
            out.writeUTF(record.getString("_id"));out.writeInt(record.getInteger("schema"));out.writeUTF(record.getString("keyId"));
            Object id=record.get("projectId");out.writeBoolean(id instanceof ObjectId);
            if(id instanceof ObjectId objectId)out.write(objectId.toByteArray());else out.writeUTF((String)id);
            out.writeInt(record.getInteger("versionIndex"));out.writeUTF(record.getString("actorId"));out.writeUTF(record.getString("action"));
            out.writeLong(record.getLong("createdAt"));out.writeLong(record.getLong("expiresAt"));out.writeInt(record.getInteger("length"));out.writeInt(record.getInteger("chunks"));out.writeUTF(record.getString("sha256"));
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(bytes.toByteArray()));
        } catch(Exception invalid) {throw unavailable();}
    }
    private static String digest(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception invalid){throw unavailable();}
    }
    private static boolean same(Snapshot a,Snapshot b){return a.id().equals(b.id()) && a.projectId().equals(b.projectId()) && a.versionIndex()==b.versionIndex()
            && a.actorId().equals(b.actorId()) && a.action()==b.action() && a.createdAt()==b.createdAt() && a.expiresAt()==b.expiresAt() && Arrays.equals(a.versionBytes(),b.versionBytes());}
    private static boolean uuid(String id){return id!=null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    private static IllegalStateException unavailable(){return new IllegalStateException("Review snapshot is unavailable or inconsistent");}
}
